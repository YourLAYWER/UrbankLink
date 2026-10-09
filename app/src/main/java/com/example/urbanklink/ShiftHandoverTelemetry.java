package com.example.urbanklink;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ShiftHandoverTelemetry extends AppCompatActivity {

    private TextView txtTotalScans, txtOdometer, txtPerformance, txtCashValue;
    private MaterialButton btnConfirmLogout, btnPrintSlip;
    private String authHeader;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_shift_handover_telemetry);

        // 1. Setup Tablet Side Navigation (Highlights SHIFT tab)
        SideNavUtils.setupSideNavigation(this, R.id.navTabShift);

        View rootView = findViewById(android.R.id.content);

        // Automatically fetches and populates the header info across all screens!
        //HeaderManager.loadHeaderData(this, rootView);
        HeaderManager.loadHeaderData(this, rootView, null);

        // 2. Bind XML Views
        txtTotalScans = findViewById(R.id.txtTotalScans);
        txtOdometer = findViewById(R.id.txtOdometer);
        txtPerformance = findViewById(R.id.txtPerformance);
        txtCashValue = findViewById(R.id.txtCashValue);
        btnConfirmLogout = findViewById(R.id.btnConfirmLogout);
        btnPrintSlip = findViewById(R.id.btnPrintSlip);

        // 3. Authentication Check
        SharedPreferences prefs = getSharedPreferences("UrbanLinkPrefs", MODE_PRIVATE);
        String token = prefs.getString("JWT_TOKEN", null);

        if (token == null) {
            performLogout();
            return;
        }
        authHeader = "Bearer " + token.replace("\"", "").trim();

        // 4. Fetch preview data immediately
        fetchLiveSummary();

        // 5. Button Listeners
        btnConfirmLogout.setOnClickListener(v -> finalizeShift());

        btnPrintSlip.setOnClickListener(v -> {
            Toast.makeText(this, "Connecting to Bluetooth Printer...", Toast.LENGTH_SHORT).show();
        });
    }

    private void fetchLiveSummary() {
        btnConfirmLogout.setEnabled(false);
        txtCashValue.setText("...");

        UrbanLinkApiService apiService = ApiClient.getClient(this).create(UrbanLinkApiService.class);
        apiService.getShiftSummary(authHeader).enqueue(new Callback<EndShiftResponse>() {
            @Override
            public void onResponse(Call<EndShiftResponse> call, Response<EndShiftResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    EndShiftResponse data = response.body();

                    // Populate standard fields available from your EndShiftResponse
                    txtTotalScans.setText(String.valueOf(data.shiftSummary.digitalTicketsScanned));
                    txtCashValue.setText(String.format("R%.2f", data.shiftSummary.totalCashHandover));

                    // NOTE: If your C# API model doesn't return Odometer and Performance yet,
                    // you can comment these two lines out until you update the backend DTO.
                    // txtOdometer.setText(String.format("%.1f km", data.shiftSummary.distanceTraveled));
                    // txtPerformance.setText(String.format("%.1f%%", data.shiftSummary.onTimePerformance));

                    btnConfirmLogout.setEnabled(true);
                } else {
                    Toast.makeText(ShiftHandoverTelemetry.this, "Failed to load summary", Toast.LENGTH_SHORT).show();
                }
            }
            @Override
            public void onFailure(Call<EndShiftResponse> call, Throwable t) {
                Toast.makeText(ShiftHandoverTelemetry.this, "Network Error", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void finalizeShift() {
        // Disable button to prevent duplicate submissions
        btnConfirmLogout.setEnabled(false);
        btnConfirmLogout.setText("Closing...");

        UrbanLinkApiService apiService = ApiClient.getClient(this).create(UrbanLinkApiService.class);
        apiService.endShift(authHeader).enqueue(new Callback<EndShiftResponse>() {
            @Override
            public void onResponse(Call<EndShiftResponse> call, Response<EndShiftResponse> response) {
                if (response.isSuccessful()) {
                    // Shift closed in SQL Server, wipe local session
                    performLogout();
                } else {
                    btnConfirmLogout.setEnabled(true);
                    btnConfirmLogout.setText("Confirm & Logout");
                    Toast.makeText(ShiftHandoverTelemetry.this, "Error closing shift", Toast.LENGTH_SHORT).show();
                }
            }
            @Override
            public void onFailure(Call<EndShiftResponse> call, Throwable t) {
                btnConfirmLogout.setEnabled(true);
                btnConfirmLogout.setText("Confirm & Logout");
                Toast.makeText(ShiftHandoverTelemetry.this, "Network Error", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void performLogout() {
        SharedPreferences.Editor editor = getSharedPreferences("UrbanLinkPrefs", MODE_PRIVATE).edit();
        editor.clear().apply();

        Intent intent = new Intent(ShiftHandoverTelemetry.this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}