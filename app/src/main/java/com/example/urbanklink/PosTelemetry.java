package com.example.urbanklink;

import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Bundle;
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
    private TextView txtQuantity, txtTotalAmount;
    private MaterialButton btnMinus, btnPlus;
    private MaterialButton btnFast1, btnFast2, btnFast3, btnFast5;
    private MaterialButton btnClear, btnConfirmIssue;

    // Transaction State
    private int quantity = 1;
    private final double BASE_FARE = 15.00;
    private String authHeader;
    private ToneGenerator toneGen;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_pos_telemetry);

        // 1. Tablet Sidebar Navigation (Crucial for routing)
        SideNavUtils.setupSideNavigation(this, R.id.navTabCash);

        // 2. Grab JWT Token
        SharedPreferences prefs = getSharedPreferences("UrbanLinkPrefs", MODE_PRIVATE);
        String token = prefs.getString("JWT_TOKEN", null);
        if (token != null) {
            authHeader = "Bearer " + token.replace("\"", "").trim();
        }

        toneGen = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);

        // 3. Link UI Components
        txtQuantity = findViewById(R.id.txtQuantity);
        txtTotalAmount = findViewById(R.id.txtTotalAmount);

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

    private void setFastQuantity(int q) {
        quantity = q;
        updateTotalDisplay();
    }

    private void updateTotalDisplay() {
        // Update digit block
        txtQuantity.setText(String.valueOf(quantity));

        // Calculate new totals
        double total = quantity * BASE_FARE;
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

                    // Tablet specific adaptation: Do not call finish().
                    // Drivers stay on this screen to serve the next passenger. Just reset the UI.
                    setFastQuantity(1);
                    btnConfirmIssue.setEnabled(true);
                } else {
                    btnConfirmIssue.setEnabled(true);
                    updateTotalDisplay(); // Re-applies the original price text
                    Toast.makeText(PosTelemetry.this, "Transaction Failed. Try again.", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<CashSaleResponse> call, Throwable t) {
                btnConfirmIssue.setEnabled(true);
                updateTotalDisplay(); // Re-applies the original price text
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