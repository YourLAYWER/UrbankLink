package com.example.urbanklink;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

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
    }

    private void fetchAvailableBuses() {
        // Initialize the API service
        UrbanLinkApiService apiService = ApiClient.getClient(this).create(UrbanLinkApiService.class);

        Log.d("API_DEBUG", "Starting fetchAvailableBuses network call...");

        apiService.getAvailableBuses().enqueue(new Callback<BusListResponse>() {
            @Override
            public void onResponse(Call<BusListResponse> call, Response<BusListResponse> response) {
                // Log the exact URL being called and the HTTP status code returned
                Log.d("API_DEBUG", "Buses Call URL: " + call.request().url());
                Log.d("API_DEBUG", "Buses Response Code: " + response.code());

                if (response.isSuccessful() && response.body() != null) {
                    allBuses = response.body().data;
                    Log.d("API_DEBUG", "Buses successfully parsed! Count: " + allBuses.size());

                    List<String> busNames = new ArrayList<>();
                    for (AvailableBus bus : allBuses) {
                        busNames.add(bus.label);
                    }

                    ArrayAdapter<String> adapter = new ArrayAdapter<>(
                            ShiftStartActivity.this,
                            R.layout.item_dropdown,
                            busNames
                    );
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
                    Log.e("API_DEBUG", "Buses Server Error! Code: " + response.code() + ", Message: " + response.message());
                    Toast.makeText(ShiftStartActivity.this, "Server Error: " + response.code(), Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(Call<BusListResponse> call, Throwable t) {
                // Catch complete network failures (e.g., no internet, wrong IP, server offline)
                Log.e("API_DEBUG", "Buses Network Call Failed Entirely!", t);
                Toast.makeText(ShiftStartActivity.this, "API Failure: " + t.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void fetchRoutes() {
        UrbanLinkApiService apiService = ApiClient.getClient(this).create(UrbanLinkApiService.class);


        Log.d("API_DEBUG", "Starting fetchRoutes network call...");

        apiService.getAllRoutes().enqueue(new Callback<RouteListResponse>() {
            @Override
            public void onResponse(Call<RouteListResponse> call, Response<RouteListResponse> response) {
                Log.d("API_DEBUG", "Routes Call URL: " + call.request().url());
                Log.d("API_DEBUG", "Routes Response Code: " + response.code());

                if (response.isSuccessful() && response.body() != null) {
                    allRoutes = response.body().data;
                    Log.d("API_DEBUG", "Routes successfully parsed! Count: " + allRoutes.size());

                    List<String> routeNames = new ArrayList<>();
                    for (ActiveRoute route : allRoutes) {
                        routeNames.add(route.name);
                    }

                    ArrayAdapter<String> adapter = new ArrayAdapter<>(
                            ShiftStartActivity.this,
                            R.layout.item_dropdown,
                            routeNames
                    );
                    etRouteSelection.setAdapter(adapter);

                    etRouteSelection.setOnClickListener(v -> etRouteSelection.showDropDown());
                    etRouteSelection.setOnFocusChangeListener((v, hasFocus) -> {
                        if (hasFocus) etRouteSelection.showDropDown();
                    });

                    etRouteSelection.setOnItemClickListener((parent, view, position, id) -> {
                        String selectedName = (String) parent.getItemAtPosition(position);
                        for (ActiveRoute route : allRoutes) {
                            if (route.name.equals(selectedName)) {
                                selectedRouteId = route.id;
                                break;
                            }
                        }
                        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                        imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
                        etRouteSelection.clearFocus();
                    });
                } else {
                    Log.e("API_DEBUG", "Routes Server Error! Code: " + response.code());
                }
            }

            @Override
            public void onFailure(Call<RouteListResponse> call, Throwable t) {
                Log.e("API_DEBUG", "Routes Network Call Failed Entirely!", t);
            }
        });
    }

    private void startConductorShift(int busId, int routeId) {
        StartShiftRequest request = new StartShiftRequest(busId, routeId);
        UrbanLinkApiService apiService = ApiClient.getClient(null).create(UrbanLinkApiService.class);

        apiService.startShift(request).enqueue(new Callback<StartShiftResponse>() {
            @Override
            public void onResponse(Call<StartShiftResponse> call, Response<StartShiftResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    // 1. Save the Shift ID securely in SharedPreferences
                    SharedPreferences prefs = getSharedPreferences("UrbanLinkPrefs", MODE_PRIVATE);
                    prefs.edit().putInt("ACTIVE_SHIFT_ID", response.body().shiftId).apply();

                    Toast.makeText(ShiftStartActivity.this, "Shift Started Successfully!", Toast.LENGTH_SHORT).show();

                    // 2. Navigate to the Scanner Dashboard
                    Intent intent = new Intent(ShiftStartActivity.this, ScannerActivity.class);
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