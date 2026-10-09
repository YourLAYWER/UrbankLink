package com.example.urbanklink;

import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.ToneGenerator;
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

public class PosTelemetry extends AppCompatActivity {

    // UI Elements mapped to the tablet XML
    private TextView txtQuantity, txtTotalAmount, txtBaseFare;
    private MaterialButton btnMinus, btnPlus;
    private MaterialButton btnFast1, btnFast2, btnFast3, btnFast5;
    private MaterialButton btnClear, btnConfirmIssue;

    // Transaction State
    private int quantity = 1;
    private double dynamicBaseFare = 15.00;
    private String authHeader;
    private ToneGenerator toneGen;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_pos_telemetry);

        // 1. Tablet Sidebar Navigation (Crucial for routing)
        SideNavUtils.setupSideNavigation(this, R.id.navTabCash);

        View rootView = findViewById(android.R.id.content);

        // Automatically fetches and populates the header info across all screens!
        //HeaderManager.loadHeaderData(this, rootView);
        HeaderManager.loadHeaderData(this, rootView, null);

        // 2. Grab JWT Token
        SharedPreferences prefs = getSharedPreferences("UrbanLinkPrefs", MODE_PRIVATE);
        String token = prefs.getString("JWT_TOKEN", null);
        if (token != null) {
            authHeader = "Bearer " + token.replace("\"", "").trim();
        }

        // Load route-specific base fare saved by HeaderManager (defaults to 15.00 if not found)
        dynamicBaseFare = prefs.getFloat("ACTIVE_BASE_FARE", 15.00f);

        toneGen = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);

        // 3. Link UI Components
        txtQuantity = findViewById(R.id.txtQuantity);
        txtTotalAmount = findViewById(R.id.txtTotalAmount);
        txtBaseFare = findViewById(R.id.txtBaseFare);

        btnMinus = findViewById(R.id.btnMinus);
        btnPlus = findViewById(R.id.btnPlus);

        btnFast1 = findViewById(R.id.btnFast1);
        btnFast2 = findViewById(R.id.btnFast2);
        btnFast3 = findViewById(R.id.btnFast3);
        btnFast5 = findViewById(R.id.btnFast5);

        btnClear = findViewById(R.id.btnClear);
        btnConfirmIssue = findViewById(R.id.btnConfirmIssue);

        // 4. Setup Initial UI State
        updateTotalDisplay();

        // 5. Standard +/- Controls
        btnMinus.setOnClickListener(v -> {
            if (quantity > 1) {
                quantity--;
                updateTotalDisplay();
            }
        });

        btnPlus.setOnClickListener(v -> {
            if (quantity < 10) {
                quantity++;
                updateTotalDisplay();
            } else {
                Toast.makeText(this, "Maximum 10 tickets per transaction", Toast.LENGTH_SHORT).show();
            }
        });

        // 6. Fast Select Modifiers
        btnFast1.setOnClickListener(v -> setFastQuantity(1));
        btnFast2.setOnClickListener(v -> setFastQuantity(2));
        btnFast3.setOnClickListener(v -> setFastQuantity(3));
        btnFast5.setOnClickListener(v -> setFastQuantity(5));

        // 7. Bottom Action Buttons
        btnClear.setOnClickListener(v -> setFastQuantity(1)); // Resets form back to default 1
        btnConfirmIssue.setOnClickListener(v -> processTransaction());

    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh base fare in case it updated or loaded slightly after onCreate
        SharedPreferences prefs = getSharedPreferences("UrbanLinkPrefs", MODE_PRIVATE);
        dynamicBaseFare = prefs.getFloat("ACTIVE_BASE_FARE", (float) dynamicBaseFare);
        updateTotalDisplay();
    }

    private void setFastQuantity(int q) {
        quantity = q;
        updateTotalDisplay();
    }

    private void updateTotalDisplay() {

        // B) Update the SINGLE FARE AMOUNT display (txtBaseFare)
        // This guarantees the driver sees the correct rate for the current route
        if (txtBaseFare != null) {
            txtBaseFare.setText(String.format("R%.2f", dynamicBaseFare));
        }
        // Update digit block
        txtQuantity.setText(String.valueOf(quantity));

        // Calculate new totals
        double total = quantity * dynamicBaseFare;
        txtTotalAmount.setText(String.format("R%.2f", total));

        // Dynamically inject the calculated total into the confirmation button text
        btnConfirmIssue.setText(String.format("CONFIRM & ISSUE TICKET (R%.2f)", total));
    }

    private void processTransaction() {
        btnConfirmIssue.setEnabled(false);
        btnConfirmIssue.setText("PROCESSING...");

        CashSaleRequest request = new CashSaleRequest(quantity);
        UrbanLinkApiService apiService = ApiClient.getClient(this).create(UrbanLinkApiService.class);

        apiService.recordCashSale(request).enqueue(new Callback<CashSaleResponse>() {
            @Override
            public void onResponse(Call<CashSaleResponse> call, Response<CashSaleResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 150);
                    Toast.makeText(PosTelemetry.this, "Sale Successful: R" + response.body().amountAdded, Toast.LENGTH_SHORT).show();

                    // 1. Grab the current passenger count from SharedPreferences
                    SharedPreferences prefs = getSharedPreferences("UrbanLinkPrefs", MODE_PRIVATE);
                    int currentCount = prefs.getInt("ACTIVE_PASSENGER_COUNT", 0); // Defaults to 45 or your baseline

                    // 2. Add the newly sold ticket quantity
                    int newCount = currentCount + quantity;

                    // 3. Save the updated count back persistently
                    prefs.edit().putInt("ACTIVE_PASSENGER_COUNT", newCount).apply();

                    // 4. Handle navigation exit vs continuous selling
                    if (getCallingActivity() != null) {
                        // Opened via Scanner Button -> Send result back and finish
                        Intent resultIntent = new Intent();
                        resultIntent.putExtra("PASSENGERS_ADDED", quantity);
                        setResult(RESULT_OK, resultIntent);
                        finish();
                    } else {
                        // Opened via Side Navigation -> Reset UI so driver can keep selling tickets
                        setFastQuantity(1);
                        btnConfirmIssue.setEnabled(true);
                        updateTotalDisplay();
                        Toast.makeText(PosTelemetry.this, "Passenger count updated!", Toast.LENGTH_SHORT).show();
                    }

                } else {
                    btnConfirmIssue.setEnabled(true);
                    updateTotalDisplay();
                    Toast.makeText(PosTelemetry.this, "Transaction Failed. Try again.", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<CashSaleResponse> call, Throwable t) {
                btnConfirmIssue.setEnabled(true);
                updateTotalDisplay();
                Toast.makeText(PosTelemetry.this, "Network Error", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (toneGen != null) toneGen.release();
    }
}