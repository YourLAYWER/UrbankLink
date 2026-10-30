package com.example.urbanklink.driver;

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
import androidx.lifecycle.ViewModelProvider;

import com.example.urbanklink.requests.IncidentReportRequest;
import com.example.urbanklink.R;
import com.example.urbanklink.network.UrbanLinkApiService;
import com.example.urbanklink.network.ApiClient;
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

    private IncidentViewModel viewModel;

    // UI Elements
    private MaterialButton btnTriggerSOS, btnSubmitReport, btnClearForm;
    private EditText etIncidentDetails;
    private MaterialCardView[] categoryCards;
    private final int[] categoryEnums = {1, 2, 3, 6, 4, 5, 7}; // Matches the card initialization order

    // Location
    private FusedLocationProviderClient fusedLocationClient;
    private ActivityResultLauncher<String[]> locationPermissionRequest;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_incident_telemetry);

        // 1. Initialize ViewModel
        viewModel = new ViewModelProvider(this).get(IncidentViewModel.class);

        // 2. Tablet Sidebar Navigation & Header
        SideNavUtils.setupSideNavigation(this, R.id.navTabReport);
        View rootView = findViewById(android.R.id.content);
        HeaderManager.loadHeaderData(this, rootView, null);

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

        // 6. Button Listeners -> Hardware Acquisition -> ViewModel execution
        btnTriggerSOS.setOnClickListener(v -> triggerEmergencySOS());
        btnSubmitReport.setOnClickListener(v -> submitRoutineReport());
        btnClearForm.setOnClickListener(v -> resetForm());

        // 7. Observe ViewModel State
        setupObservers();
    }

    private void setupObservers() {
        // LINK: Updates card UI based on ViewModel state
        viewModel.getSelectedCategory().observe(this, selectedEnum -> {
            for (int i = 0; i < categoryCards.length; i++) {
                if (categoryCards[i] != null) {
                    if (categoryEnums[i] == selectedEnum) {
                        categoryCards[i].setStrokeColor(Color.parseColor("#00E5FF")); // Cyan
                    } else {
                        categoryCards[i].setStrokeColor(Color.parseColor("#2A2B33")); // Dim gray
                    }
                }
            }
        });

        // LINK: Handles successful routine report
        viewModel.getReportSuccess().observe(this, success -> {
            if (success != null && success) {
                Toast.makeText(this, "Report submitted successfully.", Toast.LENGTH_LONG).show();
                resetForm();
                resetButtons();
                viewModel.resetProcessingState();
            }
        });

        // LINK: Handles successful SOS dispatch
        viewModel.getSosSuccess().observe(this, success -> {
            if (success != null && success) {
                Toast.makeText(this, "EMERGENCY SOS LOGGED!", Toast.LENGTH_LONG).show();
                btnTriggerSOS.postDelayed(this::resetButtons, 3000);
                viewModel.resetProcessingState();
            }
        });

        // LINK: Handles API or validation errors (including session expiry)
        viewModel.getErrorMessage().observe(this, error -> {
            if (error != null) {
                Toast.makeText(this, error, Toast.LENGTH_LONG).show();
                if (error.equals("Session expired")) finish();
                resetButtons();
                viewModel.resetProcessingState();
            }
        });
    }

    private void setupCategoryCards() {
        categoryCards = new MaterialCardView[]{
                findViewById(R.id.cardEngine),
                findViewById(R.id.cardTire),
                findViewById(R.id.cardScanner),
                findViewById(R.id.cardMedical),
                findViewById(R.id.cardDispute),
                findViewById(R.id.cardDelay),
                findViewById(R.id.cardOther)
        };

        for (int i = 0; i < categoryCards.length; i++) {
            final int enumValue = categoryEnums[i];
            if (categoryCards[i] != null) {
                // LINK: Pass the selection event straight to the ViewModel
                categoryCards[i].setOnClickListener(v -> viewModel.selectCategory(enumValue));
            }
        }
    }

    private void resetForm() {
        viewModel.resetCategory();
        etIncidentDetails.setText("");
        etIncidentDetails.clearFocus();
    }

    private void triggerEmergencySOS() {
        btnTriggerSOS.setEnabled(false);
        btnTriggerSOS.setText("ACQUIRING GPS...");

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Permission denied. Sending SOS without GPS.", Toast.LENGTH_SHORT).show();
            viewModel.submitEmergencySOS(0.0, 0.0);
            return;
        }

        CancellationTokenSource cancellationTokenSource = new CancellationTokenSource();
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellationTokenSource.getToken())
                .addOnSuccessListener(this, location -> {
                    double lat = location != null ? location.getLatitude() : 0.0;
                    double lng = location != null ? location.getLongitude() : 0.0;
                    viewModel.submitEmergencySOS(lat, lng);
                })
                .addOnFailureListener(e -> {
                    viewModel.submitEmergencySOS(0.0, 0.0);
                });
    }

    private void submitRoutineReport() {
        String details = etIncidentDetails.getText() != null ? etIncidentDetails.getText().toString().trim() : "";

        // Pre-validate UI state before locking buttons
        if (viewModel.getSelectedCategory().getValue() == null || viewModel.getSelectedCategory().getValue() == -1) {
            Toast.makeText(this, "Please select an issue category", Toast.LENGTH_SHORT).show();
            return;
        }

        btnSubmitReport.setEnabled(false);
        btnSubmitReport.setText("ACQUIRING GPS...");

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            viewModel.submitRoutineReport(details, 0.0, 0.0);
            return;
        }

        CancellationTokenSource cancellationTokenSource = new CancellationTokenSource();
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellationTokenSource.getToken())
                .addOnSuccessListener(this, location -> {
                    double lat = location != null ? location.getLatitude() : 0.0;
                    double lng = location != null ? location.getLongitude() : 0.0;
                    viewModel.submitRoutineReport(details, lat, lng);
                })
                .addOnFailureListener(e -> {
                    viewModel.submitRoutineReport(details, 0.0, 0.0);
                });
    }

    private void resetButtons() {
        btnTriggerSOS.setEnabled(true);
        btnTriggerSOS.setText("TRIGGER INSTANT SOS");

        btnSubmitReport.setEnabled(true);
        btnSubmitReport.setText("SUBMIT REPORT");
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