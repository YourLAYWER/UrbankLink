package com.example.urbanklink;

import android.content.Intent;
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
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
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
    private int activeShiftId = -1;
    private int currentPassengers = 45; // Starting demo capacity
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_scanner);

        btnCashSale = findViewById(R.id.btnCashSale);

        // 1. You must link the TextView here so it's not null!
        txtCurrentCapacity = findViewById(R.id.txtCurrentCapacity);

        // 1. Initialize the listener (Put this inside onCreate, before your button click listener)
        quickSaleLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    // If QuickSaleActivity closed with RESULT_OK, grab the data!
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        int addedPassengers = result.getData().getIntExtra("PASSENGERS_ADDED", 0);

                        // Update the local counter and the UI
                        currentPassengers += addedPassengers;
                        txtCurrentCapacity.setText(String.valueOf(currentPassengers));
                    }
                });

        // 2. button click listener to uses the launcher
        btnCashSale = findViewById(R.id.btnCashSale);
        btnCashSale.setOnClickListener(v -> {
            Intent intent = new Intent(ScannerActivity.this, QuickSaleActivity.class);
            quickSaleLauncher.launch(intent); // <-- Changed this line
        });

        View btnEndShift = findViewById(R.id.btnEndShift);
        btnEndShift.setOnClickListener(v -> {
            Intent intent = new Intent(ScannerActivity.this, ShiftCompleteActivity.class);
            startActivity(intent);
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
        if (!qrData.toLowerCase().startsWith("URBANLINK-")) {
            Toast.makeText(this, "Invalid QR: Not an UrbanLink Ticket", Toast.LENGTH_SHORT).show();

            // Unlock the scanner quickly so they can try again
            viewFinder.postDelayed(() -> isProcessing = false, 1500);
            return;
        }

        // 2. Strip out the prefix to get the pure GUID for the database
        // (?i) makes it case-insensitive just in case!
        String cleanQrData = qrData.replaceAll("(?i)URBANLINK-", "").trim();

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