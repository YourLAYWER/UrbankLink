package com.example.urbanklink.conductor;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.example.urbanklink.driver.IncidentViewModel;
import com.example.urbanklink.R;
import com.example.urbanklink.commuter.HeaderUtils;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationTokenSource;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;

public class IncidentReportActivity extends AppCompatActivity {

    private IncidentViewModel viewModel;

    private MaterialButton btnEmergencySOS, btnSubmitReport;
    private ChipGroup chipGroupIssues;
    private TextInputEditText inputDetails;

    private FusedLocationProviderClient fusedLocationClient;
    private ActivityResultLauncher<String[]> locationPermissionRequest;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_incident_report);

        // 1. Initialize Reusable ViewModel
        viewModel = new ViewModelProvider(this).get(IncidentViewModel.class);

        // 2. Setup Header & Location Client
        HeaderUtils.setupHeader(this, true, false, false);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        setupLocationPermissions();
        requestLocationPermissions();

        // 3. Link Views
        btnEmergencySOS = findViewById(R.id.btnEmergencySOS);
        btnSubmitReport = findViewById(R.id.btnSubmitReport);
        chipGroupIssues = findViewById(R.id.chipGroupIssues);
        inputDetails = findViewById(R.id.inputDetails);

        // 4. Setup Button Listeners
        btnEmergencySOS.setOnClickListener(v -> triggerEmergencySOS());
        btnSubmitReport.setOnClickListener(v -> submitRoutineReport());

        // 5. Observe ViewModel State
        setupObservers();
    }

    private void setupObservers() {
        viewModel.getReportSuccess().observe(this, success -> {
            if (success != null && success) {
                Toast.makeText(this, "Report submitted successfully.", Toast.LENGTH_LONG).show();
                chipGroupIssues.clearCheck();
                inputDetails.setText("");
                inputDetails.clearFocus();
                resetButtons();
                viewModel.resetProcessingState();
            }
        });

        viewModel.getSosSuccess().observe(this, success -> {
            if (success != null && success) {
                Toast.makeText(this, "EMERGENCY SOS LOGGED!", Toast.LENGTH_LONG).show();
                btnEmergencySOS.postDelayed(this::resetButtons, 3000);
                viewModel.resetProcessingState();
            }
        });

        viewModel.getErrorMessage().observe(this, error -> {
            if (error != null) {
                Toast.makeText(this, error, Toast.LENGTH_LONG).show();
                if (error.equals("Session expired")) finish();
                resetButtons();
                viewModel.resetProcessingState();
            }
        });
    }

    private void triggerEmergencySOS() {
        btnEmergencySOS.setEnabled(false);
        btnEmergencySOS.setText("ACQUIRING GPS...");

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
                .addOnFailureListener(e -> viewModel.submitEmergencySOS(0.0, 0.0));
    }

    private void submitRoutineReport() {
        int selectedChipId = chipGroupIssues.getCheckedChipId();
        String details = inputDetails.getText() != null ? inputDetails.getText().toString().trim() : "";

        if (selectedChipId == -1) {
            Toast.makeText(this, "Please select an issue category", Toast.LENGTH_SHORT).show();
            return;
        }

        // Map UI selection to Backend Enum and update ViewModel state
        viewModel.selectCategory(getCategoryEnumFromChip(selectedChipId));

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
                .addOnFailureListener(e -> viewModel.submitRoutineReport(details, 0.0, 0.0));
    }

    private int getCategoryEnumFromChip(int chipId) {
        if (chipId == R.id.chipEngine) return 1;
        if (chipId == R.id.chipTire) return 2;
        if (chipId == R.id.chipScanner) return 3;
        if (chipId == R.id.chipDispute) return 4;
        if (chipId == R.id.chipDelay) return 5;
        if (chipId == R.id.chipMedical) return 6;
        if (chipId == R.id.chipOther) return 7;
        return 0;
    }

    private void resetButtons() {
        btnEmergencySOS.setEnabled(true);
        btnEmergencySOS.setText("EMERGENCY SOS");
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
                        Toast.makeText(this, "Location permission denied.", Toast.LENGTH_LONG).show();
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