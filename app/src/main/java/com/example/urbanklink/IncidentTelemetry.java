package com.example.urbanklink;

import android.Manifest;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationTokenSource;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class IncidentTelemetry extends AppCompatActivity {

    // UI Elements
    private MaterialButton btnTriggerSOS, btnSubmitReport, btnClearForm;
    private EditText etIncidentDetails;

    // Category Cards Array for dynamic selection styling
    private MaterialCardView[] categoryCards;
    private int selectedCategoryEnum = -1; // -1 means nothing selected

    // Location & Auth
    private FusedLocationProviderClient fusedLocationClient;
    private ActivityResultLauncher<String[]> locationPermissionRequest;
    private String authHeader;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_incident_telemetry);

        // 1. Tablet Sidebar Navigation
        SideNavUtils.setupSideNavigation(this, R.id.navTabReport);

        View rootView = findViewById(android.R.id.content);

        // Automatically fetches and populates the header info across all screens!
        HeaderManager.loadHeaderData(this, rootView);

        // 2. Setup Auth Token
        SharedPreferences prefs = getSharedPreferences("UrbanLinkPrefs", MODE_PRIVATE);
        String token = prefs.getString("JWT_TOKEN", null);
        if (token == null) {
            Toast.makeText(this, "Session expired", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        authHeader = "Bearer " + token.replace("\"", "").trim();

        // 3. Initialize Location Client & Permissions
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        setupLocationPermissions();
        requestLocationPermissions();

        // 4. Bind UI Elements
        btnTriggerSOS = findViewById(R.id.btnTriggerSOS);
        btnSubmitReport = findViewById(R.id.btnSubmitReport);
        btnClearForm = findViewById(R.id.btnClearForm);
        etIncidentDetails = findViewById(R.id.etIncidentDetails);

        // 5. Setup Category Cards
        setupCategoryCards();

        // 6. SOS Button Listener
        btnTriggerSOS.setOnClickListener(v -> triggerEmergencySOS(v));

        // 7. Submit Report Listener
        btnSubmitReport.setOnClickListener(v -> submitRoutineReport());

        // 8. Clear Form Listener
        btnClearForm.setOnClickListener(v -> resetForm());
    }

    private void setupCategoryCards() {
        // Link the XML cards
        MaterialCardView cardEngine = findViewById(R.id.cardEngine);
        MaterialCardView cardTire = findViewById(R.id.cardTire);
        MaterialCardView cardScanner = findViewById(R.id.cardScanner);
        MaterialCardView cardMedical = findViewById(R.id.cardMedical);
        MaterialCardView cardDispute = findViewById(R.id.cardDispute);
        MaterialCardView cardDelay = findViewById(R.id.cardDelay);
        MaterialCardView cardOther = findViewById(R.id.cardOther);

        categoryCards = new MaterialCardView[]{
                cardEngine, cardTire, cardScanner, cardMedical, cardDispute, cardDelay, cardOther
        };

        // Attach click listeners to map to C# Enums
        setupCardListener(cardEngine, 1);
        setupCardListener(cardTire, 2);
        setupCardListener(cardScanner, 3);
        setupCardListener(cardMedical, 6);
        setupCardListener(cardDispute, 4);
        setupCardListener(cardDelay, 5);
        setupCardListener(cardOther, 7);
    }

    private void setupCardListener(MaterialCardView card, int enumValue) {
        if (card == null) return;
        card.setOnClickListener(v -> {
            // Dim all cards back to unselected gray
            for (MaterialCardView c : categoryCards) {
                if (c != null) c.setStrokeColor(Color.parseColor("#2A2B33"));
            }
            // Highlight the tapped card in cyan
            card.setStrokeColor(Color.parseColor("#00E5FF"));
            selectedCategoryEnum = enumValue;
        });
    }

    private void resetForm() {
        for (MaterialCardView c : categoryCards) {
            if (c != null) c.setStrokeColor(Color.parseColor("#2A2B33"));
        }
        selectedCategoryEnum = -1;
        etIncidentDetails.setText("");
        etIncidentDetails.clearFocus();
    }

    private void triggerEmergencySOS(View v) {
        btnTriggerSOS.setEnabled(false);
        btnTriggerSOS.setText("ACQUIRING GPS...");

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Permission denied. Sending SOS without GPS.", Toast.LENGTH_SHORT).show();
            // Assuming Enum 3 is SOS/Critical in your C# backend
            sendReportToBackend(true, 3, "CRITICAL SOS TRIGGERED FROM TABLET", 0.0, 0.0);
            resetSosButton(v);
            return;
        }

        CancellationTokenSource cancellationTokenSource = new CancellationTokenSource();
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellationTokenSource.getToken())
                .addOnSuccessListener(this, location -> {
                    double lat = location != null ? location.getLatitude() : 0.0;
                    double lng = location != null ? location.getLongitude() : 0.0;
                    sendReportToBackend(true, 3, "CRITICAL SOS TRIGGERED FROM TABLET", lat, lng);
                    resetSosButton(v);
                })
                .addOnFailureListener(e -> {
                    sendReportToBackend(true, 3, "CRITICAL SOS TRIGGERED FROM TABLET", 0.0, 0.0);
                    resetSosButton(v);
                });
    }

    private void submitRoutineReport() {
        if (selectedCategoryEnum == -1) {
            Toast.makeText(this, "Please select an issue category", Toast.LENGTH_SHORT).show();
            return;
        }

        String details = etIncidentDetails.getText() != null ? etIncidentDetails.getText().toString().trim() : "";

        // Disable the button and show the user that GPS is being acquired
        btnSubmitReport.setEnabled(false);
        btnSubmitReport.setText("ACQUIRING GPS...");

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            sendReportToBackend(false, selectedCategoryEnum, details, 0.0, 0.0);
            return;
        }

        // Use getCurrentLocation for a forced, live GPS fix instead of relying on the cache
        CancellationTokenSource cancellationTokenSource = new CancellationTokenSource();
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellationTokenSource.getToken())
                .addOnSuccessListener(this, location -> {
                    double lat = location != null ? location.getLatitude() : 0.0;
                    double lng = location != null ? location.getLongitude() : 0.0;
                    sendReportToBackend(false, selectedCategoryEnum, details, lat, lng);
                })
                .addOnFailureListener(e -> {
                    // If the GPS hardware fails to get a lock, send the report anyway with 0.0
                    sendReportToBackend(false, selectedCategoryEnum, details, 0.0, 0.0);
                });
    }

    private void sendReportToBackend(boolean isEmergency, int category, String details, double lat, double lng) {
        IncidentReportRequest request = new IncidentReportRequest(isEmergency, category, details, lat, lng);
        UrbanLinkApiService apiService = ApiClient.getClient(this).create(UrbanLinkApiService.class);

        apiService.logIncident(authHeader, request).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (response.isSuccessful()) {
                    String msg = isEmergency ? "EMERGENCY SOS LOGGED!" : "Report submitted successfully.";
                    Toast.makeText(IncidentTelemetry.this, msg, Toast.LENGTH_LONG).show();

                    if (!isEmergency) {
                        resetForm();
                        btnSubmitReport.setEnabled(true);
                        btnSubmitReport.setText("SUBMIT REPORT");
                    }
                } else {
                    Toast.makeText(IncidentTelemetry.this, "Failed to submit: " + response.code(), Toast.LENGTH_SHORT).show();
                    resetFailedButtons(isEmergency);
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                Toast.makeText(IncidentTelemetry.this, "Network error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                resetFailedButtons(isEmergency);
            }
        });
    }

    private void resetFailedButtons(boolean isEmergency) {
        if (isEmergency) {
            btnTriggerSOS.setEnabled(true);
            btnTriggerSOS.setText("TRIGGER INSTANT SOS");
        } else {
            btnSubmitReport.setEnabled(true);
            btnSubmitReport.setText("SUBMIT REPORT");
        }
    }

    private void resetSosButton(View v) {
        v.postDelayed(() -> {
            btnTriggerSOS.setEnabled(true);
            btnTriggerSOS.setText("TRIGGER INSTANT SOS");
        }, 3000);
    }

    private void setupLocationPermissions() {
        locationPermissionRequest = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> {
                    Boolean fineLoc = result.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false);
                    Boolean coarseLoc = result.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false);
                    if (fineLoc != null && fineLoc) {
                        Toast.makeText(this, "Precise GPS Ready", Toast.LENGTH_SHORT).show();
                    } else if (coarseLoc != null && coarseLoc) {
                        Toast.makeText(this, "Approximate Location Ready", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "Location permission denied. Reports will lack GPS.", Toast.LENGTH_LONG).show();
                    }
                }
        );
    }

    private void requestLocationPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            locationPermissionRequest.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            });
        }
    }
}