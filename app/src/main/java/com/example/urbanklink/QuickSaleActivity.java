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

import com.google.android.material.button.MaterialButton;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class QuickSaleActivity extends AppCompatActivity {

    private TextView btnClose, txtQuantity, txtTotalAmount, txtBaseFare, txtRouteName;
    private ImageView btnMinus, btnPlus;
    private MaterialButton btnConfirmSale;

    private int quantity = 1;
    // We remove the hardcoded BASE_FARE and make dynamicBaseFare accessible globally
    private double dynamicBaseFare = 15.00;

    private ToneGenerator toneGen;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_quick_sale);

        HeaderUtils.setupHeader(this, true, false, false);


        // 1. Grab SharedPreferences variables
        SharedPreferences prefs = getSharedPreferences("UrbanLinkPrefs", MODE_PRIVATE);
        dynamicBaseFare = prefs.getFloat("ACTIVE_BASE_FARE", 15.00f);
        String routeName = prefs.getString("ACTIVE_ROUTE_NAME", "Unknown Route");

        toneGen = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);

        // 2. Link UI
       //btnClose = findViewById(R.id.btnClose);
        txtQuantity = findViewById(R.id.txtQuantity);
        txtTotalAmount = findViewById(R.id.txtTotalAmount);
        txtBaseFare = findViewById(R.id.txtBaseFare);
        txtRouteName = findViewById(R.id.txtRouteName);
        btnMinus = findViewById(R.id.btnMinus);
        btnPlus = findViewById(R.id.btnPlus);
        btnConfirmSale = findViewById(R.id.btnConfirmSale);

        // 3. Inject the dynamic values into the UI
        if (txtRouteName != null) txtRouteName.setText(routeName);
        if (txtBaseFare != null) txtBaseFare.setText(String.format("R%.2f", dynamicBaseFare));

        // 4. Calculate initial total
        updateTotalDisplay();

        // 5. Button Click Listeners
        //btnClose.setOnClickListener(v -> finish());

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

        btnConfirmSale.setOnClickListener(v -> processTransaction());
    }

    private void updateTotalDisplay() {
        txtQuantity.setText(String.valueOf(quantity));
        // FIX: Now we multiply by the dynamic fare we loaded from memory!
        double total = quantity * dynamicBaseFare;
        txtTotalAmount.setText(String.format("R%.2f", total));
    }

    private void processTransaction() {
        btnConfirmSale.setEnabled(false);
        btnConfirmSale.setText("Processing...");

        CashSaleRequest request = new CashSaleRequest(quantity);

        UrbanLinkApiService apiService = ApiClient.getClient(QuickSaleActivity.this).create(UrbanLinkApiService.class);

        apiService.recordCashSale(request).enqueue(new Callback<CashSaleResponse>() {
            @Override
            public void onResponse(Call<CashSaleResponse> call, Response<CashSaleResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 150);
                    Toast.makeText(QuickSaleActivity.this, "Sale Successful: R" + response.body().amountAdded, Toast.LENGTH_SHORT).show();

                    Intent resultIntent = new Intent();
                    resultIntent.putExtra("PASSENGERS_ADDED", quantity);
                    setResult(RESULT_OK, resultIntent);

                    finish();
                } else {
                    btnConfirmSale.setEnabled(true);
                    btnConfirmSale.setText("CONFIRM & ISSUE TICKET");
                    Toast.makeText(QuickSaleActivity.this, "Transaction Failed. Try again.", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<CashSaleResponse> call, Throwable t) {
                btnConfirmSale.setEnabled(true);
                btnConfirmSale.setText("CONFIRM & ISSUE TICKET");
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