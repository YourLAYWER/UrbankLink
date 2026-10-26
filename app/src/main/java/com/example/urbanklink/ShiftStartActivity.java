package com.example.urbanklink;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.example.urbanklink.auth.LoginActivity;
import com.example.urbanklink.commuter.HeaderUtils;
import com.example.urbanklink.conductor.ScannerActivity;
import com.example.urbanklink.driver.ScannerTelemetry;
import com.example.urbanklink.network.ApiClient;
import com.example.urbanklink.network.UrbanLinkApiService;
import com.example.urbanklink.requests.StartShiftRequest;
import com.example.urbanklink.response.BusListResponse;
import com.example.urbanklink.response.RouteListResponse;
import com.example.urbanklink.response.StartShiftResponse;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ShiftStartActivity extends AppCompatActivity {
    private AutoCompleteTextView etVehicleSelection;
    private AutoCompleteTextView etRouteSelection;
    private MaterialButton btnStartShift;

    // Add these right below your UI component declarations
    private List<AvailableBus> allBuses = new ArrayList<>();
    private List<ActiveRoute> allRoutes = new ArrayList<>();

    // Variables to hold the IDs the conductor actually selects
    private int selectedBusId = -1;
    private int selectedRouteId = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_shift_start);

        etVehicleSelection = findViewById(R.id.etVehicleSelection);
        etRouteSelection = findViewById(R.id.etRouteSelection);
        btnStartShift = findViewById(R.id.btnStartShift);

        // Fetch live data from SQL Server via .NET API
        fetchAvailableBuses();
        fetchRoutes();

        btnStartShift.setOnClickListener(v -> {
            if (selectedBusId == -1 || selectedRouteId == -1) {
                Toast.makeText(this, "Please select both a vehicle and a route.", Toast.LENGTH_SHORT).show();
                return;
            }

            // Disable button to prevent double-tapping
            btnStartShift.setEnabled(false);
            btnStartShift.setText("STARTING...");

            startConductorShift(selectedBusId, selectedRouteId);
        });


        // 1. Initialize the top header (Show back button, hide standard logout button)
        // Initialize the top header (Show back button, hide standard logout button, hide profile button)
        HeaderUtils.setupHeader(this, true, false, false);

        // 2. Override the UI Back Button to force a logout instead of a standard finish()
        android.view.View btnBack = findViewById(R.id.btnHeaderBack);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> performLogout());
        }

        // 3. Override the physical Android hardware Back Button to also force a logout
        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                performLogout();
            }
        });
    }

    private void fetchAvailableBuses() {
        // Initialize the API service
        UrbanLinkApiService apiService = ApiClient.getClient(this).create(UrbanLinkApiService.class);

        apiService.getAvailableBuses().enqueue(new Callback<BusListResponse>() {
            @Override
            public void onResponse(Call<BusListResponse> call, Response<BusListResponse> response) {

                if (response.isSuccessful() && response.body() != null) {
                    allBuses = response.body().data;

                    // ADD THIS TOAST TO VERIFY DATA IS ACTUALLY ARRIVING
                    Toast.makeText(ShiftStartActivity.this, "Loaded " + allBuses.size() + " buses", Toast.LENGTH_SHORT).show();

                    List<String> busNames = new ArrayList<>();
                    for (AvailableBus bus : allBuses) {
                        busNames.add(bus.label);
                    }

                    ArrayAdapter<String> adapter = new ArrayAdapter<>(
                            ShiftStartActivity.this,
                            androidx.appcompat.R.layout.support_simple_spinner_dropdown_item,
                            busNames
                    );
                    etVehicleSelection.setThreshold(1);
                    etVehicleSelection.setAdapter(adapter);

                    etVehicleSelection.setOnClickListener(v -> etVehicleSelection.showDropDown());
                    etVehicleSelection.setOnFocusChangeListener((v, hasFocus) -> {
                        if (hasFocus) etVehicleSelection.showDropDown();
                    });

                    etVehicleSelection.setOnItemClickListener((parent, view, position, id) -> {
                        String selectedName = (String) parent.getItemAtPosition(position);
                        for (AvailableBus bus : allBuses) {
                            if (bus.label.equals(selectedName)) {
                                selectedBusId = bus.id;
                                break;
                            }
                        }
                        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                        imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
                        etVehicleSelection.clearFocus();
                    });
                } else {
                    // Catch Server-Side Errors (e.g., 400 Bad Request, 500 Internal Server Error)
                    Toast.makeText(ShiftStartActivity.this, "Server Error: " + response.code(), Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(Call<BusListResponse> call, Throwable t) {
                // Catch complete network failures (e.g., no internet, wrong IP, server offline)
                Toast.makeText(ShiftStartActivity.this, "API Failure: " + t.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void fetchRoutes() {
        UrbanLinkApiService apiService = ApiClient.getClient(ShiftStartActivity.this).create(UrbanLinkApiService.class);

        apiService.getAllRoutes().enqueue(new Callback<RouteListResponse>() {
            @Override
            public void onResponse(Call<RouteListResponse> call, Response<RouteListResponse> response) {

                if (response.isSuccessful() && response.body() != null) {
                    // NOTE: If you changed your interface to return Call<List<ActiveRoute>> directly,
                    // change this next line to: allRoutes = response.body();
                    allRoutes = response.body().data;

                    // 1. Verify we have data
                    Toast.makeText(ShiftStartActivity.this, "Found " + allRoutes.size() + " routes", Toast.LENGTH_LONG).show();

                    List<String> routeNames = new ArrayList<>();
                    for (ActiveRoute route : allRoutes) {
                        routeNames.add(route.name);
                    }

                    // 2. Use the safe default Android layout
                    ArrayAdapter<String> adapter = new ArrayAdapter<>(
                            ShiftStartActivity.this,
                            android.R.layout.simple_dropdown_item_1line,
                            routeNames
                    );

                    // 3. Fix the character threshold
                    etRouteSelection.setThreshold(1);
                    etRouteSelection.setAdapter(adapter);

                    // Force the dropdown to show when clicked
                    etRouteSelection.setOnClickListener(v -> etRouteSelection.showDropDown());
                    etRouteSelection.setOnFocusChangeListener((v, hasFocus) -> {
                        if (hasFocus) etRouteSelection.showDropDown();
                    });

                    // 4. Capture the ID when the user taps an option
                    etRouteSelection.setOnItemClickListener((parent, view, position, id) -> {
                        String selectedName = (String) parent.getItemAtPosition(position);
                        for (ActiveRoute route : allRoutes) {
                            if (route.name.equals(selectedName)) {
                                selectedRouteId = route.id;
                                break;
                            }
                        }

                        // Hide the keyboard
                        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                        if (imm != null) {
                            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
                        }
                        etRouteSelection.clearFocus();
                    });

                } else {
                    Toast.makeText(ShiftStartActivity.this, "Routes Server Error: " + response.code(), Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(Call<RouteListResponse> call, Throwable t) {
                Toast.makeText(ShiftStartActivity.this, "API Failure: " + t.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void performLogout() {
        // Clear the session data
        SharedPreferences prefs = getSharedPreferences("UrbanLinkPrefs", Context.MODE_PRIVATE);
        prefs.edit().clear().apply();

        Toast.makeText(this, "Logged out successfully", Toast.LENGTH_SHORT).show();

        // Route back to Login and clear the backstack
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void startConductorShift(int busId, int routeId) {
        StartShiftRequest request = new StartShiftRequest(busId, routeId);
        UrbanLinkApiService apiService = ApiClient.getClient(ShiftStartActivity.this).create(UrbanLinkApiService.class);

        apiService.startShift(request).enqueue(new Callback<StartShiftResponse>() {
            @Override
            public void onResponse(Call<StartShiftResponse> call, Response<StartShiftResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    // 1. Save the Shift ID securely in SharedPreferences
                    SharedPreferences prefs = getSharedPreferences("UrbanLinkPrefs", MODE_PRIVATE);
                    prefs.edit()
                            .putInt("ACTIVE_SHIFT_ID", response.body().shiftId)
                            .putInt("ACTIVE_BUS_ID", selectedBusId) // <-- Save the bus ID here
                            .apply();

                    Toast.makeText(ShiftStartActivity.this, "Shift Started Successfully!", Toast.LENGTH_SHORT).show();

                    // 2. Retrieve the role stored during the login process (e.g., "Driver" or "Conductor")
                    String userRole = prefs.getString("USER_ROLE", "Conductor"); // Defaults to Conductor if not found

                    Intent intent;

                    if ("Driver".equalsIgnoreCase(userRole)) {
                        // Drivers go straight to the real-time non-scrolling tablet dashboard
                        intent = new Intent(ShiftStartActivity.this, ScannerTelemetry.class);
                    } else {
                        // Conductors go to the Zebra handheld ticket scanning interface
                        intent = new Intent(ShiftStartActivity.this, ScannerActivity.class);
                    }
                    startActivity(intent);
                    finish(); // Close this setup screen so they can't press 'Back' to return to it
                } else {
                    btnStartShift.setEnabled(true);
                    btnStartShift.setText("START SHIFT");
                    Toast.makeText(ShiftStartActivity.this, "Failed to start shift. Already active?", Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(Call<StartShiftResponse> call, Throwable t) {
                btnStartShift.setEnabled(true);
                btnStartShift.setText("START SHIFT");
                Toast.makeText(ShiftStartActivity.this, "API Connection Failed", Toast.LENGTH_SHORT).show();
            }
        });



    }
}