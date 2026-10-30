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
import androidx.lifecycle.ViewModelProvider;

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

    private TelemetryViewModel viewModel;

    // UI Elements
    private PreviewView viewFinder;
    private MaterialCardView panelSuccessOverlay;
    private ImageView btnToggleFlash;
    private View btnCashSaleTab;

    // Camera & Scanner Services
    private ExecutorService cameraExecutor;
    private BarcodeScanner scanner;
    private ToneGenerator toneGen;
    private Camera camera;
    private boolean isFlashOn = false;

    // State Variables
    private ActivityResultLauncher<Intent> quickSaleLauncher;
    private boolean isProcessing = false;

    private final int MAX_CAPACITY = 60;
    private final int SEATING_CAPACITY = 40;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_scanner_telemetry);

        // 1. Initialize ViewModel
        viewModel = new ViewModelProvider(this).get(TelemetryViewModel.class);

        // 2. Setup Navigation & Header
        SideNavUtils.setupSideNavigation(this, R.id.navTabScan);
        View rootView = findViewById(android.R.id.content);
        HeaderManager.loadHeaderData(this, rootView, null);

        // 3. Link UI Elements
        viewFinder = findViewById(R.id.viewFinder);
        panelSuccessOverlay = findViewById(R.id.panelSuccessOverlay);
        btnToggleFlash = findViewById(R.id.btnToggleFlash);
        btnCashSaleTab = findViewById(R.id.btnCashSaleTab);

        if (panelSuccessOverlay != null) panelSuccessOverlay.setVisibility(View.GONE);

        // 4. Initialize Hardware Services
        cameraExecutor = java.util.concurrent.Executors.newSingleThreadExecutor();
        scanner = com.google.mlkit.vision.barcode.BarcodeScanning.getClient();
        toneGen = new ToneGenerator(android.media.AudioManager.STREAM_MUSIC, 100);

        // 5. Setup Observers
        setupObservers();

        // 6. Cash Sale Launcher Setup
        quickSaleLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        int addedPassengers = result.getData().getIntExtra("PASSENGERS_ADDED", 0);
                        viewModel.addPassengers(addedPassengers);
                    }
                }
        );

        // 7. Button Listeners
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
                    btnToggleFlash.setColorFilter(isFlashOn ?
                            ContextCompat.getColor(this, android.R.color.holo_orange_light) :
                            ContextCompat.getColor(this, android.R.color.white));
                }
            });
        }

        // 8. Start Camera & Load Data
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 100);
        } else {
            startCamera();
        }

        viewModel.loadActiveShift();
    }

    // LINK: Binds UI to TelemetryViewModel outputs. Called in onCreate().
    private void setupObservers() {
        // LINK: Observes TelemetryViewModel.getRequiresLogout() -> Calls performLogout()
        viewModel.getRequiresLogout().observe(this, requiresLogout -> {
            if (requiresLogout != null && requiresLogout) performLogout();
        });

        // LINK: Observes TelemetryViewModel.getActiveShiftId() from repository fetchActiveShift()
        viewModel.getActiveShiftId().observe(this, shiftId -> {
            if (shiftId != null) {
                Toast.makeText(this, "Active Shift Loaded", Toast.LENGTH_SHORT).show();
            }
        });

        // LINK: Observes TelemetryViewModel.getCurrentPassengers() -> Updates UI via updateCabinOccupancy()
        viewModel.getCurrentPassengers().observe(this, passengers -> {
            if (passengers != null) {
                updateCabinOccupancy(passengers);
            }
        });

        // LINK: Observes TelemetryViewModel.getTicketValidationSuccess() from repository validateTicket()
        viewModel.getTicketValidationSuccess().observe(this, isValid -> {
            if (isValid == null) return;

            if (isValid) {
                viewModel.addPassengers(1); // Triggers internal passenger count update
                toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 150);
                if (panelSuccessOverlay != null) panelSuccessOverlay.setVisibility(View.VISIBLE);

                viewFinder.postDelayed(() -> {
                    if (panelSuccessOverlay != null) panelSuccessOverlay.setVisibility(View.GONE);
                    isProcessing = false; // Unlocks MLKit camera loop
                }, 2000);
            } else {
                toneGen.startTone(ToneGenerator.TONE_CDMA_ABBR_ALERT, 300);
                Toast.makeText(this, "INVALID OR USED TICKET!", Toast.LENGTH_LONG).show();
                viewFinder.postDelayed(() -> isProcessing = false, 2500);
            }
        });

        // LINK: Observes general errors from TelemetryViewModel.getErrorMessage()
        viewModel.getErrorMessage().observe(this, error -> {
            if (error != null) {
                if (error.contains("Invalid QR")) {
                    Toast.makeText(this, error, Toast.LENGTH_SHORT).show();
                    viewFinder.postDelayed(() -> isProcessing = false, 1500);
                } else {
                    Toast.makeText(this, error, Toast.LENGTH_LONG).show();
                    viewFinder.postDelayed(() -> isProcessing = false, 2000);
                }
            }
        });
    }

    private void updateCabinOccupancy(int currentPassengers) {
        TextView txtOccupancyCount = findViewById(R.id.txtOccupancyCount);
        TextView txtOccupancyPercentage = findViewById(R.id.txtOccupancyPercentage);
        TextView txtSeatsRemaining = findViewById(R.id.txtSeatsRemaining);
        TextView txtSeatedStanding = findViewById(R.id.txtSeatedStanding);
        ProgressBar progressOccupancy = findViewById(R.id.progressOccupancy);
        com.google.android.material.switchmaterial.SwitchMaterial switchBusFull = findViewById(R.id.switchBusFull);

        if (currentPassengers > MAX_CAPACITY) currentPassengers = MAX_CAPACITY;
        if (currentPassengers < 0) currentPassengers = 0;

        int percentage = (int) (((float) currentPassengers / MAX_CAPACITY) * 100);
        int remaining = MAX_CAPACITY - currentPassengers;
        int seated = Math.min(currentPassengers, SEATING_CAPACITY);
        int standing = Math.max(0, currentPassengers - SEATING_CAPACITY);

        txtOccupancyCount.setText(String.valueOf(currentPassengers));
        txtOccupancyPercentage.setText(percentage + "% FULL");
        txtSeatedStanding.setText("SEATED: " + seated + " | STANDING: " + standing);
        progressOccupancy.setProgress(currentPassengers);

        if (percentage >= 100) {
            txtSeatsRemaining.setText("BUS FULL");
            txtSeatsRemaining.setTextColor(android.graphics.Color.parseColor("#FF5252"));
            txtOccupancyPercentage.setTextColor(android.graphics.Color.parseColor("#FF5252"));
            progressOccupancy.setProgressTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#FF5252")));
            if (switchBusFull != null) switchBusFull.setChecked(true);
        } else if (percentage >= 80) {
            txtSeatsRemaining.setText(remaining + " SPOTS REMAINING");
            txtSeatsRemaining.setTextColor(android.graphics.Color.parseColor("#FFB74D"));
            txtOccupancyPercentage.setTextColor(android.graphics.Color.parseColor("#FFB74D"));
            progressOccupancy.setProgressTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#FFB74D")));
        } else {
            txtSeatsRemaining.setText(remaining + " SPOTS REMAINING");
            txtSeatsRemaining.setTextColor(android.graphics.Color.parseColor("#00FF7F"));
            txtOccupancyPercentage.setTextColor(android.graphics.Color.parseColor("#00E5FF"));
            progressOccupancy.setProgressTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#00E5FF")));
        }
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
            Toast.makeText(this, "Camera permission is required", Toast.LENGTH_SHORT).show();
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
                            isProcessing = true; // Lock the scanner immediately
                            // LINK: Passes the raw hardware scan data to the ViewModel for API validation
                            viewModel.processScannedQr(rawValue);
                            break;
                        }
                    }
                })
                .addOnCompleteListener(task -> imageProxy.close());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
        scanner.close();
        if (toneGen != null) toneGen.release();
    }
}