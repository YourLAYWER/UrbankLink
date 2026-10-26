package com.example.urbanklink.conductor;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.example.urbanklink.response.EndShiftResponse;
import com.example.urbanklink.R;
import com.example.urbanklink.auth.LoginActivity;
import com.example.urbanklink.commuter.HeaderUtils;
import com.example.urbanklink.network.ApiClient;
import com.example.urbanklink.network.UrbanLinkApiService;
import com.google.android.material.button.MaterialButton;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ShiftCompleteActivity extends AppCompatActivity {

    private TextView txtRouteCompleted, txtTicketsScanned, txtTotalCash;
    private MaterialButton btnConfirmLogout;
    private String authHeader; // Store this globally for the button click

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_shift_complete);

        // Tell HeaderUtils to paint the profile in the top right!
        HeaderUtils.setupHeader(this, true, false, false);

        txtRouteCompleted = findViewById(R.id.txtRouteCompleted);
        txtTicketsScanned = findViewById(R.id.txtTicketsScanned);
        txtTotalCash = findViewById(R.id.txtTotalCash);
        btnConfirmLogout = findViewById(R.id.btnConfirmLogout);

        SharedPreferences prefs = getSharedPreferences("UrbanLinkPrefs", MODE_PRIVATE);
        String token = prefs.getString("JWT_TOKEN", null);

        if (token == null) {
            performLogout();
            return;
        }
        authHeader = "Bearer " + token.replace("\"", "").trim();

        // 1. Fetch preview data immediately when screen opens
        fetchLiveSummary();

        // 2. Wait for user to explicitly click the button before ending the shift
        btnConfirmLogout.setOnClickListener(v -> finalizeShift());
    }

    private void fetchLiveSummary() {
        btnConfirmLogout.setEnabled(false);
        txtTotalCash.setText("Calculating...");

        UrbanLinkApiService apiService = ApiClient.getClient(this).create(UrbanLinkApiService.class);
        apiService.getShiftSummary(authHeader).enqueue(new Callback<EndShiftResponse>() {
            @Override
            public void onResponse(Call<EndShiftResponse> call, Response<EndShiftResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    EndShiftResponse data = response.body();
                    txtRouteCompleted.setText(data.shiftSummary.routeName);
                    txtTicketsScanned.setText(String.valueOf(data.shiftSummary.digitalTicketsScanned));
                    txtTotalCash.setText(String.format("R%.2f", data.shiftSummary.totalCashHandover));
                    btnConfirmLogout.setEnabled(true);
                }
            }
            @Override
            public void onFailure(Call<EndShiftResponse> call, Throwable t) {
                // Handle failure
            }
        });
    }

    private void finalizeShift() {
        // Disable button so they don't click it twice
        btnConfirmLogout.setEnabled(false);
        btnConfirmLogout.setText("Closing Shift...");

        UrbanLinkApiService apiService = ApiClient.getClient(this).create(UrbanLinkApiService.class);
        apiService.endShift(authHeader).enqueue(new Callback<EndShiftResponse>() {
            @Override
            public void onResponse(Call<EndShiftResponse> call, Response<EndShiftResponse> response) {
                if (response.isSuccessful()) {
                    // Shift is officially closed in the DB, now log them out!
                    performLogout();
                } else {
                    btnConfirmLogout.setEnabled(true);
                    btnConfirmLogout.setText("Confirm & Logout");
                }
            }
            @Override
            public void onFailure(Call<EndShiftResponse> call, Throwable t) {
                btnConfirmLogout.setEnabled(true);
                btnConfirmLogout.setText("Confirm & Logout");
            }
        });
    }

    private void performLogout() {
        SharedPreferences.Editor editor = getSharedPreferences("UrbanLinkPrefs", MODE_PRIVATE).edit();
        editor.clear();
        editor.apply();

        Intent intent = new Intent(ShiftCompleteActivity.this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}