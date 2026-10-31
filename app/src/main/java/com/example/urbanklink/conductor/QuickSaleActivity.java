package com.example.urbanklink.conductor;

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
import androidx.lifecycle.ViewModelProvider;

import com.example.urbanklink.driver.PosViewModel;
import com.example.urbanklink.R;
import com.example.urbanklink.commuter.HeaderUtils;
import com.google.android.material.button.MaterialButton;


public class QuickSaleActivity extends AppCompatActivity {

    private PosViewModel viewModel;

    private TextView txtQuantity, txtTotalAmount, txtBaseFare, txtRouteName;
    private ImageView btnMinus, btnPlus;
    private MaterialButton btnConfirmSale;
    private ToneGenerator toneGen;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_quick_sale);

        // 1. Initialize Reusable ViewModel
        viewModel = new ViewModelProvider(this).get(PosViewModel.class);

        HeaderUtils.setupHeader(this, true, false, false);
        toneGen = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);

        // 2. Link UI
        txtQuantity = findViewById(R.id.txtQuantity);
        txtTotalAmount = findViewById(R.id.txtTotalAmount);
        txtBaseFare = findViewById(R.id.txtBaseFare);
        txtRouteName = findViewById(R.id.txtRouteName);
        btnMinus = findViewById(R.id.btnMinus);
        btnPlus = findViewById(R.id.btnPlus);
        btnConfirmSale = findViewById(R.id.btnConfirmSale);

        // 3. Inject static Route Name from preferences
        SharedPreferences prefs = getSharedPreferences("UrbanLinkPrefs", MODE_PRIVATE);
        String routeName = prefs.getString("ACTIVE_ROUTE_NAME", "Unknown Route");
        if (txtRouteName != null) txtRouteName.setText(routeName);

        // 4. Button Click Listeners -> Route to ViewModel
        btnMinus.setOnClickListener(v -> viewModel.decrementQuantity());
        btnPlus.setOnClickListener(v -> viewModel.incrementQuantity());
        btnConfirmSale.setOnClickListener(v -> viewModel.processCashSale());

        // 5. Observe State Changes
        setupObservers();
    }

    private void setupObservers() {
        // LINK: Update math when quantity changes
        viewModel.getQuantity().observe(this, q -> {
            if (q != null) updateTotalDisplay();
        });

        // LINK: Update math when base fare loads
        viewModel.getDynamicBaseFare().observe(this, fare -> {
            if (fare != null) {
                if (txtBaseFare != null) txtBaseFare.setText(String.format("R%.2f", fare));
                updateTotalDisplay();
            }
        });

        // LINK: Disable button during API call
        viewModel.getIsProcessing().observe(this, isProcessing -> {
            if (isProcessing != null && isProcessing) {
                btnConfirmSale.setEnabled(false);
                btnConfirmSale.setText("Processing...");
            }
        });

        // LINK: Handle successful sale and return data to ScannerActivity
        viewModel.getSaleSuccess().observe(this, success -> {
            if (success != null && success) {
                toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 150);
                Toast.makeText(this, "Sale Successful", Toast.LENGTH_SHORT).show();

                int soldQuantity = viewModel.getQuantity().getValue() != null ? viewModel.getQuantity().getValue() : 1;

                Intent resultIntent = new Intent();
                resultIntent.putExtra("PASSENGERS_ADDED", soldQuantity);
                setResult(RESULT_OK, resultIntent);
                finish();
            }
        });

        // LINK: Handle API errors
        viewModel.getErrorMessage().observe(this, error -> {
            if (error != null) {
                btnConfirmSale.setEnabled(true);
                btnConfirmSale.setText("CONFIRM & ISSUE TICKET");
                Toast.makeText(this, error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateTotalDisplay() {
        int q = viewModel.getQuantity().getValue() != null ? viewModel.getQuantity().getValue() : 1;
        float fare = viewModel.getDynamicBaseFare().getValue() != null ? viewModel.getDynamicBaseFare().getValue() : 15.00f;

        txtQuantity.setText(String.valueOf(q));
        double total = q * fare;
        txtTotalAmount.setText(String.format("R%.2f", total));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (toneGen != null) toneGen.release();
    }
}