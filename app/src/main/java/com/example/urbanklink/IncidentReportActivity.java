package com.example.urbanklink;

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
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationTokenSource;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class IncidentReportActivity extends AppCompatActivity {

    private MaterialButton btnEmergencySOS, btnSubmitReport;
    private ChipGroup chipGroupIssues;
    private TextInputEditText inputDetails;

    // Location Variables
    private FusedLocationProviderClient fusedLocationClient;
    private ActivityResultLauncher<String[]> locationPermissionRequest;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_incident_report);

        // Initialize Location Client
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // Setup Permission Launcher
        locationPermissionRequest = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> {
                    Boolean fineLocationGranted = result.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false);
                    Boolean coarseLocationGranted = result.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false);

                    if (fineLocationGranted != null && fineLocationGranted) {
                        Toast.makeText(this, "Precise GPS Ready", Toast.LENGTH_SHORT).show();
                    } else if (coarseLocationGranted != null && coarseLocationGranted) {
                        Toast.makeText(this, "Approximate Location Ready", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "Location permission denied. Reports will lack GPS data.", Toast.LENGTH_LONG).show();
                    }
                }
        );

        // Check/Request Permissions immediately when the screen opens
        requestLocationPermissions();

//        // 1. Setup Navigation
//        BottomNavigationView bottomNav = findViewById(R.id.bottomNav);
//        FloatingActionButton fabScan = findViewById(R.id.fabScan);
//        BottomNavManager.setup(this, bottomNav, fabScan, R.id.nav_reports);

        // 2. Link Views
        btnEmergencySOS = findViewById(R.id.btnEmergencySOS);
        btnSubmitReport = findViewById(R.id.btnSubmitReport);
        chipGroupIssues = findViewById(R.id.chipGroupIssues);
        inputDetails = findViewById(R.id.inputDetails);

        // 3. SOS Button Listener
//        btnEmergencySOS.setOnClickListener(v -> {
//            // Disable instantly to prevent spam clicks
//            btnEmergencySOS.setEnabled(false);
//            btnEmergencySOS.setText("SENDING SOS...");
//
//            // TODO: Fetch device GPS coordinates and push to backend
//            Toast.makeText(this, "SOS Activated! Location being sent...", Toast.LENGTH_LONG).show();
//
//            // Re-enable after 3 seconds for demo purposes
//            v.postDelayed(() -> {
//                btnEmergencySOS.setEnabled(true);
//                btnEmergencySOS.setText("EMERGENCY SOS");
//            }, 3000);
//        });

        btnEmergencySOS.setOnClickListener(v -> {
            btnEmergencySOS.setEnabled(false);
            btnEmergencySOS.setText("ACQUIRING GPS...");

            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Permission denied. Sending SOS without GPS.", Toast.LENGTH_SHORT).show();
                sendReportToBackend(true, 3, "SOS Triggered", 0.0, 0.0); // 3 = assuming SOS enum value
                resetSosButton(v);
                return;
            }

            CancellationTokenSource cancellationTokenSource = new CancellationTokenSource();
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellationTokenSource.getToken())
                    .addOnSuccessListener(this, location -> {
                        double lat = location != null ? location.getLatitude() : 0.0;
                        double lng = location != null ? location.getLongitude() : 0.0;

                        sendReportToBackend(true, 3, "SOS Triggered", lat, lng);
                        resetSosButton(v);
                    })
                    .addOnFailureListener(e -> {
                        sendReportToBackend(true, 3, "SOS Triggered", 0.0, 0.0);
                        resetSosButton(v);
                    });
        });

        // 4. Submit Routine Report Listener
//        btnSubmitReport.setOnClickListener(v -> {
//            int selectedChipId = chipGroupIssues.getCheckedChipId();
//            String details = inputDetails.getText() != null ? inputDetails.getText().toString().trim() : "";
//
//            if (selectedChipId == -1) {
//                Toast.makeText(this, "Please select an issue category", Toast.LENGTH_SHORT).show();
//                return;
//            }
//
//            // TODO: Extract text from the selected chip and send to backend
//            Toast.makeText(this, "Report Submitted Successfully", Toast.LENGTH_SHORT).show();
//
//            // Reset form
//            chipGroupIssues.clearCheck();
//            inputDetails.setText("");
//        });

        btnSubmitReport.setOnClickListener(v -> {
            int selectedChipId = chipGroupIssues.getCheckedChipId();
            String details = inputDetails.getText() != null ? inputDetails.getText().toString().trim() : "";

            if (selectedChipId == -1) {
                Toast.makeText(this, "Please select an issue category", Toast.LENGTH_SHORT).show();
                return;
            }

            // Map chip ID to your C# Enum integer (Example logic)
            int categoryEnum = getCategoryEnumFromChip(selectedChipId);

            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                sendReportToBackend(false, categoryEnum, details, 0.0, 0.0);
                return;
            }

            fusedLocationClient.getLastLocation()
                    .addOnSuccessListener(this, location -> {
                        double lat = location != null ? location.getLatitude() : 0.0;
                        double lng = location != null ? location.getLongitude() : 0.0;

                        sendReportToBackend(false, categoryEnum, details, lat, lng);
                    });
        });
    }
    private void resetSosButton(android.view.View v) {
        v.postDelayed(() -> {
            btnEmergencySOS.setEnabled(true);
            btnEmergencySOS.setText("EMERGENCY SOS");
        }, 3000);
    }

    private void sendReportToBackend(boolean isEmergency, int category, String details, double lat, double lng) {
        // Retrieve your stored JWT token (adjust this based on your SharedPreferences implementation)
        String token = "Bearer " + getStoredJwtToken();

        IncidentReportRequest request = new IncidentReportRequest(isEmergency, category, details, lat, lng);

        UrbanLinkApiService apiService = ApiClient.getClient(this).create(UrbanLinkApiService.class);

        apiService.logIncident(token, request).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (response.isSuccessful()) {
                    String msg = isEmergency ? "EMERGENCY SOS LOGGED!" : "Report submitted successfully.";
                    Toast.makeText(IncidentReportActivity.this, msg, Toast.LENGTH_LONG).show();

                    if (!isEmergency) {
                        chipGroupIssues.clearCheck();
                        inputDetails.setText("");
                    }
                } else {
                    Toast.makeText(IncidentReportActivity.this, "Failed to submit: " + response.code(), Toast.LENGTH_SHORT).show();
                    // Optionally re-enable the SOS button here if the network call failed so they can try again
                    if (isEmergency) {
                        btnEmergencySOS.setEnabled(true);
                        btnEmergencySOS.setText("EMERGENCY SOS");
                    }
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                Toast.makeText(IncidentReportActivity.this, "Network error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                if (isEmergency) {
                    btnEmergencySOS.setEnabled(true);
                    btnEmergencySOS.setText("EMERGENCY SOS");
                }
            }
        });
    }

    // Example token retrieval - replace with your actual method
    private String getStoredJwtToken() {
        return getSharedPreferences("UrbanLinkPrefs", MODE_PRIVATE).getString("jwt_token", "");
    }

    private int getCategoryEnumFromChip(int chipId) {
        if (chipId == R.id.chipEngine) return 1; // Assuming 1 = EngineTrouble in C#
        if (chipId == R.id.chipTire) return 2;   // 2 = FlatTire
        if (chipId == R.id.chipScanner) return 3; // 3 = ScannerOffline
        if (chipId == R.id.chipDispute) return 4; // 4 = PassengerDispute
        if (chipId == R.id.chipDelay) return 5;   // 5 = Delay
        if (chipId == R.id.chipMedical) return 6;
        if (chipId == R.id.chipOther) return 7;

        // Add Medical Event or Other based on your specific C# Enum order
        // Default fallback
        return 0;
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