package com.example.urbanklink;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;

import java.util.concurrent.ExecutorService;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ScannerActivity extends AppCompatActivity {
    private PreviewView viewFinder;
    private MaterialCardView pillScanResult;
    private TextView txtCurrentCapacity;

    private ExecutorService cameraExecutor;
    private BarcodeScanner scanner;
    private ToneGenerator toneGen;

    private MaterialButton btnCashSale;

    private ActivityResultLauncher<Intent> quickSaleLauncher;

    private boolean isProcessing = false;
    private String authHeader;
    private int activeShiftId = -1;
    private int currentPassengers = 0; // Starting demo capacity
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_scanner);

        btnCashSale = findViewById(R.id.btnCashSale);

        // 1. LINK XML VIEWS FIRST (Moved to the top)
        viewFinder = findViewById(R.id.viewFinder);
        pillScanResult = findViewById(R.id.pillScanResult);
        txtCurrentCapacity = findViewById(R.id.txtCurrentCapacity);
        btnCashSale = findViewById(R.id.btnCashSale);

        // 2. INITIALIZE SERVICES FIRST
        cameraExecutor = java.util.concurrent.Executors.newSingleThreadExecutor();
        scanner = com.google.mlkit.vision.barcode.BarcodeScanning.getClient();
        toneGen = new ToneGenerator(android.media.AudioManager.STREAM_MUSIC, 100);

        // 3. AUTH & SHIFT SETUP
        SharedPreferences prefs = getSharedPreferences("UrbanLinkPrefs", MODE_PRIVATE);
        String token = prefs.getString("JWT_TOKEN", null);

        if (token == null) {
            performLogout();
            return;
        }
        authHeader = "Bearer " + token.replace("\"", "").trim();

        currentPassengers = prefs.getInt("ACTIVE_PASSENGER_COUNT", 0);
        txtCurrentCapacity.setText(String.valueOf(currentPassengers));
        fetchActiveShift();

        // 1. Register the launcher to catch the result from QuickSaleActivity
        quickSaleLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        int addedPassengers = result.getData().getIntExtra("PASSENGERS_ADDED", 0);

                        // 1. Update local counter
                        currentPassengers += addedPassengers;
                        txtCurrentCapacity.setText(String.valueOf(currentPassengers));

                        // 2. <-- NEW: Save immediately to SharedPreferences!
                        //SharedPreferences prefs = getSharedPreferences("UrbanLinkPrefs", MODE_PRIVATE);
                        prefs.edit().putInt("ACTIVE_PASSENGER_COUNT", currentPassengers).apply();
                    }
                }
        );

        // 4. BUTTON LISTENERS
        btnCashSale.setOnClickListener(v -> {
            Intent intent = new Intent(ScannerActivity.this, QuickSaleActivity.class);
            quickSaleLauncher.launch(intent); // Must use .launch(), not startActivity()
        });

        View btnEndShift = findViewById(R.id.btnEndShift);
        btnEndShift.setOnClickListener(v -> {
            Intent intent = new Intent(ScannerActivity.this, ShiftCompleteActivity.class);
            startActivity(intent);
        });

        BottomNavigationView bottomNav = findViewById(R.id.bottomNav);
        FloatingActionButton fabScan = findViewById(R.id.fabScan);

        // Pass -1 for ScannerActivity since the active state belongs to the FAB, not the bottom menu
        BottomDriverNavManager.setup(this, bottomNav, fabScan, -1);

        // 5. START CAMERA LAST (Now that views and services are ready)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 100);
        } else {
            startCamera();
        }

    }

    private void fetchActiveShift() {
        UrbanLinkApiService apiService = ApiClient.getClient(this).create(UrbanLinkApiService.class);
        apiService.getShiftSummary(authHeader).enqueue(new Callback<EndShiftResponse>() {
            @Override
            public void onResponse(Call<EndShiftResponse> call, Response<EndShiftResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    EndShiftResponse data = response.body();
                    // Extract the active shift ID directly from the backend response model
                    activeShiftId = data.shiftSummary.shiftId; // Ensure your EndShiftResponse model has this field

                    Toast.makeText(ScannerActivity.this, "Active Shift Loaded", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(ScannerActivity.this, "No active shift found!", Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(Call<EndShiftResponse> call, Throwable t) {
                Toast.makeText(ScannerActivity.this, "Failed to connect to server", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void performLogout() {
        SharedPreferences.Editor editor = getSharedPreferences("UrbanLinkPrefs", MODE_PRIVATE).edit();
        editor.clear();
        editor.apply();

        Intent intent = new Intent(ScannerActivity.this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 100 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            // The user just clicked "Allow", start the camera immediately
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
                cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalysis);
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
                            break; // Only scan one at a time
                        }
                    }
                })
                .addOnCompleteListener(task -> imageProxy.close());
    }

    private void validateTicketWithServer(String qrData) {
        isProcessing = true; // Lock the scanner

        // 1. Verify this is actually an UrbanLink ticket before we even bother the server
        if (!qrData.toLowerCase().startsWith("urbanlink-")) {
            Toast.makeText(this, "Invalid QR: Not an UrbanLink Ticket", Toast.LENGTH_SHORT).show();

            // Unlock the scanner quickly so they can try again
            viewFinder.postDelayed(() -> isProcessing = false, 1500);
            return;
        }

        // 2. Strip out the prefix to get the pure GUID for the database
        // (?i) makes it case-insensitive just in case!
        String cleanQrData = qrData.replaceAll("(?i)urbanlink-", "").trim();

        // 3. Send the clean GUID to the server
        ScanTicketRequest request = new ScanTicketRequest(activeShiftId, cleanQrData);

        UrbanLinkApiService apiService = ApiClient.getClient(ScannerActivity.this).create(UrbanLinkApiService.class);

        apiService.scanTicket(request).enqueue(new Callback<ScanTicketResponse>() {
            @Override
            public void onResponse(Call<ScanTicketResponse> call, Response<ScanTicketResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    // TICKET IS VALID
                    toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 150); // Happy beep

                    // Show the green pill
                    pillScanResult.setVisibility(View.VISIBLE);

                    // Increment capacity
                    currentPassengers++;
                    txtCurrentCapacity.setText(String.valueOf(currentPassengers));

                    // Save immediately to SharedPreferences!
                    SharedPreferences prefs = getSharedPreferences("UrbanLinkPrefs", MODE_PRIVATE);
                    prefs.edit().putInt("ACTIVE_PASSENGER_COUNT", currentPassengers).apply();

                    // Hide the pill and unlock scanner after 2 seconds
                    viewFinder.postDelayed(() -> {
                        pillScanResult.setVisibility(View.GONE);
                        isProcessing = false;
                    }, 2000);

                } else {
                    // TICKET IS INVALID / ALREADY USED
                    toneGen.startTone(ToneGenerator.TONE_CDMA_ABBR_ALERT, 300); // Angry beep
                    Toast.makeText(ScannerActivity.this, "INVALID OR USED TICKET!", Toast.LENGTH_LONG).show();

                    viewFinder.postDelayed(() -> isProcessing = false, 2500);
                }
            }

            @Override
            public void onFailure(Call<ScanTicketResponse> call, Throwable t) {
                Toast.makeText(ScannerActivity.this, "Network Error", Toast.LENGTH_SHORT).show();
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