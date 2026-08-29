package com.example.urbanklink;

import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.widget.ImageView;
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

public class QuickSaleActivity extends AppCompatActivity {

    private TextView btnClose, txtQuantity, txtTotalAmount, txtBaseFare;
    private ImageView btnMinus, btnPlus;
    private MaterialButton btnConfirmSale;

    private int quantity = 1;
    private final double BASE_FARE = 15.00; // In a full production app, you'd pass this via Intent from the Route data
    private int activeShiftId = -1;
    private ToneGenerator toneGen;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_quick_sale);

        // 1. Get the Shift ID
        SharedPreferences prefs = getSharedPreferences("UrbanLinkPrefs", MODE_PRIVATE);
        activeShiftId = prefs.getInt("ACTIVE_SHIFT_ID", -1);

        toneGen = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);

        // 2. Link UI
        btnClose = findViewById(R.id.btnClose);
        txtQuantity = findViewById(R.id.txtQuantity);
        txtTotalAmount = findViewById(R.id.txtTotalAmount);
        txtBaseFare = findViewById(R.id.txtBaseFare);
        btnMinus = findViewById(R.id.btnMinus);
        btnPlus = findViewById(R.id.btnPlus);
        btnConfirmSale = findViewById(R.id.btnConfirmSale);

        // 3. Setup Initial UI State
        updateTotalDisplay();
        txtBaseFare.setText(String.format("R%.2f", BASE_FARE));

        // 4. Button Click Listeners
        btnClose.setOnClickListener(v -> finish()); // Closes the POS and goes back to Scanner

        btnMinus.setOnClickListener(v -> {
            if (quantity > 1) {
                quantity--;
                updateTotalDisplay();
            }
        });

        btnPlus.setOnClickListener(v -> {
            if (quantity < 10) { // Reasonable limit for a single walk-on group
                quantity++;
                updateTotalDisplay();
            } else {
                Toast.makeText(this, "Maximum 10 tickets per transaction", Toast.LENGTH_SHORT).show();
            }
        });

        btnConfirmSale.setOnClickListener(v -> processTransaction());
    }

    private void updateTotalDisplay() {
        txtQuantity.setText(String.valueOf(quantity));
        double total = quantity * BASE_FARE;
        txtTotalAmount.setText(String.format("R%.2f", total));
    }

    private void processTransaction() {
        // Disable button to prevent double-charging
        btnConfirmSale.setEnabled(false);
        btnConfirmSale.setText("Processing...");

        CashSaleRequest request = new CashSaleRequest(activeShiftId, quantity);

        UrbanLinkApiService apiService = ApiClient.getClient(QuickSaleActivity.this).create(UrbanLinkApiService.class);

        apiService.recordCashSale(request).enqueue(new Callback<CashSaleResponse>() {
            @Override
            public void onResponse(Call<CashSaleResponse> call, Response<CashSaleResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    // Success! Beep and close the screen
                    toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 150);
                    Toast.makeText(QuickSaleActivity.this, "Sale Successful: R" + response.body().amountAdded, Toast.LENGTH_SHORT).show();

                    // NEW: Package the quantity and tell Android this activity was a SUCCESS
                    Intent resultIntent = new Intent();
                    resultIntent.putExtra("PASSENGERS_ADDED", quantity);
                    setResult(RESULT_OK, resultIntent);

                    finish(); // Returns the user immediately to the Scanner camera
                } else {
                    btnConfirmSale.setEnabled(true);
                    btnConfirmSale.setText("Confirm & Issue Ticket");
                    Toast.makeText(QuickSaleActivity.this, "Transaction Failed. Try again.", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<CashSaleResponse> call, Throwable t) {
                btnConfirmSale.setEnabled(true);
                btnConfirmSale.setText("Confirm & Issue Ticket");
                Toast.makeText(QuickSaleActivity.this, "Network Error", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (toneGen != null) toneGen.release();
    }
}