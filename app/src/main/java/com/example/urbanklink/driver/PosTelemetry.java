package com.example.urbanklink.driver;

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
import androidx.lifecycle.ViewModelProvider;

import com.example.urbanklink.requests.CashSaleRequest;
import com.example.urbanklink.response.CashSaleResponse;
import com.example.urbanklink.R;
import com.example.urbanklink.network.UrbanLinkApiService;
import com.example.urbanklink.network.ApiClient;
import com.google.android.material.button.MaterialButton;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class PosTelemetry extends AppCompatActivity {

    private PosViewModel viewModel;

    // UI Elements
    private TextView txtQuantity, txtTotalAmount, txtBaseFare;
    private MaterialButton btnMinus, btnPlus;
    private MaterialButton btnFast1, btnFast2, btnFast3, btnFast5;
    private MaterialButton btnClear, btnConfirmIssue;

    private ToneGenerator toneGen;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_pos_telemetry);

        // 1. Initialize ViewModel
        viewModel = new ViewModelProvider(this).get(PosViewModel.class);

        // 2. Setup Navigation & Header
        SideNavUtils.setupSideNavigation(this, R.id.navTabCash);
        View rootView = findViewById(android.R.id.content);
        HeaderManager.loadHeaderData(this, rootView, null);

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

        // 4. Setup Button Listeners -> Route directly to ViewModel
        btnMinus.setOnClickListener(v -> viewModel.decrementQuantity());
        btnPlus.setOnClickListener(v -> viewModel.incrementQuantity());

        btnFast1.setOnClickListener(v -> viewModel.setQuantity(1));
        btnFast2.setOnClickListener(v -> viewModel.setQuantity(2));
        btnFast3.setOnClickListener(v -> viewModel.setQuantity(3));
        btnFast5.setOnClickListener(v -> viewModel.setQuantity(5));

        btnClear.setOnClickListener(v -> viewModel.setQuantity(1));

        btnConfirmIssue.setOnClickListener(v -> viewModel.processCashSale());

        // 5. Setup Observers to react to ViewModel state changes
        setupObservers();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh base fare in case it updated while app was in background
        viewModel.loadBaseFare();
    }

    private void setupObservers() {
        // LINK: Update total display whenever the quantity changes
        viewModel.getQuantity().observe(this, q -> {
            if (q != null) updateTotalDisplay();
        });

        // LINK: Update total display whenever the base fare changes
        viewModel.getDynamicBaseFare().observe(this, fare -> {
            if (fare != null) updateTotalDisplay();
        });

        // LINK: Handle processing state (Disable button to prevent double-clicks)
        viewModel.getIsProcessing().observe(this, isProcessing -> {
            if (isProcessing != null && isProcessing) {
                btnConfirmIssue.setEnabled(false);
                btnConfirmIssue.setText("PROCESSING...");
            }
        });

        // LINK: Handle successful transaction
        viewModel.getSaleSuccess().observe(this, success -> {
            if (success == null || !success) return;

            toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 150);

            // Increment the persistent passenger count
            viewModel.finalizeSaleAndUpdateCount();

            int soldQuantity = viewModel.getQuantity().getValue() != null ? viewModel.getQuantity().getValue() : 1;

            if (getCallingActivity() != null) {
                // Opened via Scanner Button -> Send result back to ScannerTelemetry
                Intent resultIntent = new Intent();
                resultIntent.putExtra("PASSENGERS_ADDED", soldQuantity);
                setResult(RESULT_OK, resultIntent);
                finish();
            } else {
                // Opened via Side Navigation -> Keep selling
                Toast.makeText(this, "Sale Successful. Passenger count updated!", Toast.LENGTH_SHORT).show();
                viewModel.setQuantity(1); // Reset UI
                btnConfirmIssue.setEnabled(true);
            }
        });

        // LINK: Handle errors (API failure or max ticket limit)
        viewModel.getErrorMessage().observe(this, error -> {
            if (error != null) {
                Toast.makeText(this, error, Toast.LENGTH_SHORT).show();
                // Re-enable button on API failure
                btnConfirmIssue.setEnabled(true);
                updateTotalDisplay();
            }
        });
    }

    // Helper method to keep UI formatting clean
    private void updateTotalDisplay() {
        int q = viewModel.getQuantity().getValue() != null ? viewModel.getQuantity().getValue() : 1;
        float fare = viewModel.getDynamicBaseFare().getValue() != null ? viewModel.getDynamicBaseFare().getValue() : 15.00f;

        txtBaseFare.setText(String.format("R%.2f", fare));
        txtQuantity.setText(String.valueOf(q));

        double total = q * fare;
        txtTotalAmount.setText(String.format("R%.2f", total));
        btnConfirmIssue.setText(String.format("CONFIRM & ISSUE TICKET (R%.2f)", total));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (toneGen != null) toneGen.release();
    }
}