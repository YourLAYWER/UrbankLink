package com.example.urbanklink.conductor;

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
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.example.urbanklink.ShiftCompleteActivity;
import com.example.urbanklink.driver.HeaderManager;
import com.example.urbanklink.commuter.HeaderUtils;
import com.example.urbanklink.R;
import com.example.urbanklink.driver.TelemetryViewModel;
import com.example.urbanklink.requests.ScanTicketRequest;
import com.example.urbanklink.response.ScanTicketResponse;
import com.example.urbanklink.network.UrbanLinkApiService;
import com.example.urbanklink.auth.LoginActivity;
import com.example.urbanklink.network.ApiClient;
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

    private TelemetryViewModel viewModel;

    // UI Elements
    private PreviewView viewFinder;
    private MaterialCardView pillScanResult;
    private TextView txtCurrentCapacity;
    private MaterialButton btnCashSale;

    // Camera Services
    private ExecutorService cameraExecutor;
    private BarcodeScanner scanner;
    private ToneGenerator toneGen;

    private ActivityResultLauncher<Intent> quickSaleLauncher;
    private boolean isProcessing = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_scanner);

        // 1. Initialize Reusable ViewModel
        viewModel = new ViewModelProvider(this).get(TelemetryViewModel.class);

        // 2. Link Views
        viewFinder = findViewById(R.id.viewFinder);
        pillScanResult = findViewById(R.id.pillScanResult);
        txtCurrentCapacity = findViewById(R.id.txtCurrentCapacity);
        btnCashSale = findViewById(R.id.btnCashSale);

        // 3. Setup Headers
        View rootView = findViewById(android.R.id.content);
        HeaderManager.loadHeaderData(this, rootView, null);
        HeaderUtils.setupHeader(this, true, false, false);

        // 4. Initialize Hardware
        cameraExecutor = java.util.concurrent.Executors.newSingleThreadExecutor();
        scanner = com.google.mlkit.vision.barcode.BarcodeScanning.getClient();
        toneGen = new ToneGenerator(android.media.AudioManager.STREAM_MUSIC, 100);

        // 5. Setup View Observers
        setupObservers();

        // 6. Setup Cash Sale Return Intent
        quickSaleLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        int addedPassengers = result.getData().getIntExtra("PASSENGERS_ADDED", 0);
                        viewModel.addPassengers(addedPassengers); // ViewModel handles saving
                    }
                }
        );

        // 7. Setup Button Listeners
        btnCashSale.setOnClickListener(v -> {
            Intent intent = new Intent(ScannerActivity.this, QuickSaleActivity.class);
            quickSaleLauncher.launch(intent);
        });

        findViewById(R.id.btnEndShift).setOnClickListener(v -> {
            startActivity(new Intent(ScannerActivity.this, ShiftCompleteActivity.class));
        });

        // 8. Setup Bottom Navigation
        BottomNavigationView bottomNav = findViewById(R.id.bottomNav);
        FloatingActionButton fabScan = findViewById(R.id.fabScan);
        BottomConductorNavManager.setup(this, bottomNav, fabScan, -1);

        // 9. Start Camera
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 100);
        } else {
            startCamera();
        }

        viewModel.loadActiveShift();
    }

    private void setupObservers() {
        // LINK: Auto-updates UI when passengers are added via QR or Cash Sale
        viewModel.getCurrentPassengers().observe(this, passengers -> {
            if (passengers != null) {
                txtCurrentCapacity.setText(String.valueOf(passengers));
            }
        });

        viewModel.getRequiresLogout().observe(this, requiresLogout -> {
            if (requiresLogout != null && requiresLogout) performLogout();
        });

        // LINK: Reacts to MLKit scanner validation results
        viewModel.getTicketValidationSuccess().observe(this, isValid -> {
            if (isValid == null) return;

            if (isValid) {
                viewModel.addPassengers(1);
                toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 150);
                pillScanResult.setVisibility(View.VISIBLE);

                viewFinder.postDelayed(() -> {
                    pillScanResult.setVisibility(View.GONE);
                    isProcessing = false;
                }, 2000);
            } else {
                toneGen.startTone(ToneGenerator.TONE_CDMA_ABBR_ALERT, 300);
                Toast.makeText(this, "INVALID OR USED TICKET!", Toast.LENGTH_LONG).show();
                viewFinder.postDelayed(() -> isProcessing = false, 2500);
            }
        });

        viewModel.getErrorMessage().observe(this, error -> {
            if (error != null) {
                Toast.makeText(this, error, Toast.LENGTH_LONG).show();
                viewFinder.postDelayed(() -> isProcessing = false, error.contains("Invalid QR") ? 1500 : 2000);
            }
        });
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
                            isProcessing = true;
                            // Route the scanned code right back to the shared ViewModel
                            viewModel.processScannedQr(rawValue);
                            break;
                        }
                    }
                })
                .addOnCompleteListener(task -> imageProxy.close());
    }

    private void performLogout() {
        SharedPreferences.Editor editor = getSharedPreferences("UrbanLinkPrefs", MODE_PRIVATE).edit();
        editor.clear().apply();
        Intent intent = new Intent(ScannerActivity.this, LoginActivity.class);
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
        scanner.close();
        if (toneGen != null) toneGen.release();
    }
}