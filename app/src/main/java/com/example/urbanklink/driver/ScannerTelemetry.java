package com.example.urbanklink.driver;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.urbanklink.response.EndShiftResponse;
import com.example.urbanklink.auth.LoginActivity;
import com.example.urbanklink.R;
import com.example.urbanklink.requests.ScanTicketRequest;
import com.example.urbanklink.response.ScanTicketResponse;
import com.example.urbanklink.network.UrbanLinkApiService;
import com.example.urbanklink.network.ApiClient;
import com.google.android.material.card.MaterialCardView;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;

import java.util.concurrent.ExecutorService;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ScannerTelemetry extends AppCompatActivity {

    // UI Elements
    private PreviewView viewFinder;
    private MaterialCardView panelSuccessOverlay;
    private TextView txtOccupancyCount;
    private ImageView btnToggleFlash;
    private View btnCashSaleTab;

    // Camera & Scanner Services
    private ExecutorService cameraExecutor;
    private BarcodeScanner scanner;
    private ToneGenerator toneGen;
    private Camera camera; // Needed to control the flash
    private boolean isFlashOn = false;

    // State Variables
    private ActivityResultLauncher<Intent> quickSaleLauncher;
    private boolean isProcessing = false;
    private String authHeader;
    private int activeShiftId = -1;
    private int currentPassengers = 0; // Starting demo capacity based on your UI

    private final int MAX_CAPACITY = 60;
    private final int SEATING_CAPACITY = 40;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_scanner_telemetry);

        // 1. Setup Tablet Side Navigation (Highlights SCAN tab)
        SideNavUtils.setupSideNavigation(this, R.id.navTabScan);

        View rootView = findViewById(android.R.id.content);

        // Automatically fetches and populates the header info across all screens!
        //HeaderManager.loadHeaderData(this, rootView);
        HeaderManager.loadHeaderData(this, rootView, null);

        // 2. Link UI Elements
        viewFinder = findViewById(R.id.viewFinder);
        panelSuccessOverlay = findViewById(R.id.panelSuccessOverlay);
        txtOccupancyCount = findViewById(R.id.txtOccupancyCount);
        btnToggleFlash = findViewById(R.id.btnToggleFlash);
        btnCashSaleTab = findViewById(R.id.btnCashSaleTab);

        // Hide overlay initially
        if (panelSuccessOverlay != null) panelSuccessOverlay.setVisibility(View.GONE);

        // 3. Initialize Services
        cameraExecutor = java.util.concurrent.Executors.newSingleThreadExecutor();
        scanner = com.google.mlkit.vision.barcode.BarcodeScanning.getClient();
        toneGen = new ToneGenerator(android.media.AudioManager.STREAM_MUSIC, 100);

        // 4. Auth & Shift Setup
        SharedPreferences prefs = getSharedPreferences("UrbanLinkPrefs", MODE_PRIVATE);
        String token = prefs.getString("JWT_TOKEN", null);

        if (token == null) {
            performLogout();
            return;
        }
        authHeader = "Bearer " + token.replace("\"", "").trim();
        fetchActiveShift();

        // 5. Cash Sale Launcher Setup
        quickSaleLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        int addedPassengers = result.getData().getIntExtra("PASSENGERS_ADDED", 0);
                        updatePassengerCount(currentPassengers + addedPassengers);
                        if (txtOccupancyCount != null) {
                            txtOccupancyCount.setText(String.valueOf(currentPassengers));
                        }
                    }
                }
        );

        // 6. Button Listeners
        if (btnCashSaleTab != null) {
            btnCashSaleTab.setOnClickListener(v -> {
                Intent intent = new Intent(ScannerTelemetry.this, PosTelemetry.class);
                quickSaleLauncher.launch(intent);
            });
        }

        if (btnToggleFlash != null) {
            btnToggleFlash.setOnClickListener(v -> {
                if (camera != null && camera.getCameraInfo().hasFlashUnit()) {
                    isFlashOn = !isFlashOn;
                    camera.getCameraControl().enableTorch(isFlashOn);
                    // Optional: Change tint to indicate it's on
                    btnToggleFlash.setColorFilter(isFlashOn ?
                            ContextCompat.getColor(this, android.R.color.holo_orange_light) :
                            ContextCompat.getColor(this, android.R.color.white));
                }
            });
        }

        // 7. Start Camera
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 100);
        } else {
            startCamera();
        }



        // Load saved count, defaulting to 45 if nothing is stored yet
        currentPassengers = prefs.getInt("ACTIVE_PASSENGER_COUNT", 0);
        updateCabinOccupancy(currentPassengers);

        if (txtOccupancyCount != null) {
            txtOccupancyCount.setText(String.valueOf(currentPassengers));
        }
    }

    private void updateCabinOccupancy(int currentPassengers) {
        // 1. Find the views
        TextView txtOccupancyCount = findViewById(R.id.txtOccupancyCount);
        TextView txtOccupancyPercentage = findViewById(R.id.txtOccupancyPercentage);
        TextView txtSeatsRemaining = findViewById(R.id.txtSeatsRemaining);
        TextView txtSeatedStanding = findViewById(R.id.txtSeatedStanding);
        ProgressBar progressOccupancy = findViewById(R.id.progressOccupancy);
        com.google.android.material.switchmaterial.SwitchMaterial switchBusFull = findViewById(R.id.switchBusFull); // If you gave the switch an ID

        // 2. Prevent overflow
        if (currentPassengers > MAX_CAPACITY) currentPassengers = MAX_CAPACITY;
        if (currentPassengers < 0) currentPassengers = 0;

        // 3. Calculate the math
        int percentage = (int) (((float) currentPassengers / MAX_CAPACITY) * 100);
        int remaining = MAX_CAPACITY - currentPassengers;

        // Fill seats first, then standing room
        int seated = Math.min(currentPassengers, SEATING_CAPACITY);
        int standing = Math.max(0, currentPassengers - SEATING_CAPACITY);

        // 4. Update the text fields
        txtOccupancyCount.setText(String.valueOf(currentPassengers));
        txtOccupancyPercentage.setText(percentage + "% FULL");
        txtSeatedStanding.setText("SEATED: " + seated + " | STANDING: " + standing);
        progressOccupancy.setProgress(currentPassengers);

        // 5. Dynamic Colors & Logic
        if (percentage >= 100) {
            // Bus is completely full (Red warning)
            txtSeatsRemaining.setText("BUS FULL");
            txtSeatsRemaining.setTextColor(android.graphics.Color.parseColor("#FF5252")); // Red
            txtOccupancyPercentage.setTextColor(android.graphics.Color.parseColor("#FF5252"));
            progressOccupancy.setProgressTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#FF5252")));

            if (switchBusFull != null) switchBusFull.setChecked(true); // Auto-toggle the "Mark Full" switch!

        } else if (percentage >= 80) {
            // Bus is getting crowded (Yellow/Orange warning)
            txtSeatsRemaining.setText(remaining + " SPOTS REMAINING");
            txtSeatsRemaining.setTextColor(android.graphics.Color.parseColor("#FFB74D")); // Orange
            txtOccupancyPercentage.setTextColor(android.graphics.Color.parseColor("#FFB74D"));
            progressOccupancy.setProgressTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#FFB74D")));

        } else {
            // Normal operation (Cyan/Green)
            txtSeatsRemaining.setText(remaining + " SPOTS REMAINING");
            txtSeatsRemaining.setTextColor(android.graphics.Color.parseColor("#00FF7F")); // Green
            txtOccupancyPercentage.setTextColor(android.graphics.Color.parseColor("#00E5FF")); // Cyan
            progressOccupancy.setProgressTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#00E5FF")));
        }
    }
    private void updatePassengerCount(int newCount) {
        currentPassengers = newCount;
        updateCabinOccupancy(currentPassengers);

        if (txtOccupancyCount != null) {
            txtOccupancyCount.setText(String.valueOf(currentPassengers));
        }

        // Save persistently so other screens or re-opened activities remember it
        SharedPreferences prefs = getSharedPreferences("UrbanLinkPrefs", MODE_PRIVATE);
        prefs.edit().putInt("ACTIVE_PASSENGER_COUNT", currentPassengers).apply();
    }

    @Override
    protected void onResume() {
        super.onResume();
        SharedPreferences prefs = getSharedPreferences("UrbanLinkPrefs", MODE_PRIVATE);
        currentPassengers = prefs.getInt("ACTIVE_PASSENGER_COUNT", 0);
        if (txtOccupancyCount != null) {
            txtOccupancyCount.setText(String.valueOf(currentPassengers));
        }
    }

    private void fetchActiveShift() {
        UrbanLinkApiService apiService = ApiClient.getClient(this).create(UrbanLinkApiService.class);
        apiService.getShiftSummary(authHeader).enqueue(new Callback<EndShiftResponse>() {
            @Override
            public void onResponse(Call<EndShiftResponse> call, Response<EndShiftResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    EndShiftResponse data = response.body();
                    activeShiftId = data.shiftSummary.shiftId;
                    Toast.makeText(ScannerTelemetry.this, "Active Shift Loaded", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(ScannerTelemetry.this, "No active shift found!", Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(Call<EndShiftResponse> call, Throwable t) {
                Toast.makeText(ScannerTelemetry.this, "Failed to connect to server", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void performLogout() {
        SharedPreferences.Editor editor = getSharedPreferences("UrbanLinkPrefs", MODE_PRIVATE).edit();
        editor.clear().apply();

        Intent intent = new Intent(ScannerTelemetry.this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 100 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            Toast.makeText(this, "Camera permission is required to scan tickets", Toast.LENGTH_SHORT).show();
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(viewFinder.getSurfaceProvider());

                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                imageAnalysis.setAnalyzer(cameraExecutor, this::processImageProxy);

                cameraProvider.unbindAll();

                // Bind to lifecycle and capture the Camera object to control the flash
                camera = cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalysis);
            } catch (Exception e) {
                Log.e("CameraX", "Binding failed", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    @androidx.annotation.OptIn(markerClass = androidx.camera.core.ExperimentalGetImage.class)
    private void processImageProxy(ImageProxy imageProxy) {
        if (isProcessing || imageProxy.getImage() == null) {
            imageProxy.close();
            return;
        }

        InputImage image = InputImage.fromMediaImage(imageProxy.getImage(), imageProxy.getImageInfo().getRotationDegrees());

        scanner.process(image)
                .addOnSuccessListener(barcodes -> {
                    for (Barcode barcode : barcodes) {
                        String rawValue = barcode.getRawValue();
                        if (rawValue != null) {
                            validateTicketWithServer(rawValue);
                            break;
                        }
                    }
                })
                .addOnCompleteListener(task -> imageProxy.close());
    }

    private void validateTicketWithServer(String qrData) {
        isProcessing = true; // Lock the scanner

        if (!qrData.toLowerCase().startsWith("urbanlink-")) {
            Toast.makeText(this, "Invalid QR: Not an UrbanLink Ticket", Toast.LENGTH_SHORT).show();
            viewFinder.postDelayed(() -> isProcessing = false, 1500);
            return;
        }

        String cleanQrData = qrData.replaceAll("(?i)urbanlink-", "").trim();
        ScanTicketRequest request = new ScanTicketRequest(cleanQrData);

        UrbanLinkApiService apiService = ApiClient.getClient(ScannerTelemetry.this).create(UrbanLinkApiService.class);

        apiService.scanTicket(request).enqueue(new Callback<ScanTicketResponse>() {
            @Override
            public void onResponse(Call<ScanTicketResponse> call, Response<ScanTicketResponse> response) {
                if (response.isSuccessful() && response.body() != null) {

                    //Increment Passenger Count
                    updatePassengerCount(currentPassengers + 1);

                    // TICKET IS VALID
                    toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 150);

                    // Show tablet success overlay
                    if (panelSuccessOverlay != null) panelSuccessOverlay.setVisibility(View.VISIBLE);

                    // Update Capacity UI
//                    currentPassengers++;
//                    if (txtOccupancyCount != null) {
//                        txtOccupancyCount.setText(String.valueOf(currentPassengers));
//                    }

                    // Hide overlay and unlock scanner after 2 seconds
                    viewFinder.postDelayed(() -> {
                        if (panelSuccessOverlay != null) panelSuccessOverlay.setVisibility(View.GONE);
                        isProcessing = false;
                    }, 2000);

                } else {
                    // TICKET IS INVALID
                    toneGen.startTone(ToneGenerator.TONE_CDMA_ABBR_ALERT, 300);
                    Toast.makeText(ScannerTelemetry.this, "INVALID OR USED TICKET!", Toast.LENGTH_LONG).show();
                    viewFinder.postDelayed(() -> isProcessing = false, 2500);
                }
            }

            @Override
            public void onFailure(Call<ScanTicketResponse> call, Throwable t) {
                Toast.makeText(ScannerTelemetry.this, "Network Error", Toast.LENGTH_SHORT).show();
                viewFinder.postDelayed(() -> isProcessing = false, 2000);
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
        scanner.close();
        if (toneGen != null) toneGen.release();
    }
}