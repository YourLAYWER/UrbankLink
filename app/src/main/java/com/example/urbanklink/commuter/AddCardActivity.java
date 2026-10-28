package com.example.urbanklink.commuter;

import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.urbanklink.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;

public class AddCardActivity extends AppCompatActivity {

    private AddCardViewModel viewModel;

    // Inputs
    private TextInputEditText inputCardName, inputCardNumber, inputExpiry, inputCvv;
    private SwitchMaterial switchDefault;
    private MaterialButton btnSaveCard;

    // Live Preview Elements (from item_carousel_card.xml)
    private TextView txtPreviewName, txtPreviewNumber, txtPreviewExpiry;

    private boolean isFormatting;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_add_card);

        HeaderUtils.setupHeader(this, true, false, true);

        viewModel = new ViewModelProvider(this, ViewModelProvider.AndroidViewModelFactory.getInstance(getApplication())).get(AddCardViewModel.class);

        initializeUI();
        setupInputFormatters();
        setupObservers();

//        btnSaveCard.setOnClickListener(v -> {
//            String name = inputCardName.getText() != null ? inputCardName.getText().toString().trim() : "";
//            String number = inputCardNumber.getText() != null ? inputCardNumber.getText().toString() : "";
//            String expiry = inputExpiry.getText() != null ? inputExpiry.getText().toString() : "";
//            boolean isDefault = switchDefault.isChecked();
//
//            android.util.Log.d("CARD_DEBUG", "1. Button Clicked. Name: " + name + " | Raw Number Length: " + number.length());
//
//            viewModel.saveNewCard(name, number, expiry, isDefault);
//        });

        btnSaveCard.setOnClickListener(v -> {
            String name = inputCardName.getText() != null ? inputCardName.getText().toString().trim() : "";
            String number = inputCardNumber.getText() != null ? inputCardNumber.getText().toString() : "";
            String expiry = inputExpiry.getText() != null ? inputExpiry.getText().toString() : "";
            boolean isDefault = switchDefault.isChecked();

            android.util.Log.d("CARD_DEBUG", "1. Name: '" + name + "' | Number: '" + number + "' | Expiry: '" + expiry + "'");
            viewModel.saveNewCard(name, number, expiry, isDefault);
        });

    }

    private void initializeUI() {
        inputCardName = findViewById(R.id.inputCardName);
        inputCardNumber = findViewById(R.id.inputCardNumber);
        inputExpiry = findViewById(R.id.inputExpiry);
        inputCvv = findViewById(R.id.inputCvv);
        switchDefault = findViewById(R.id.switchDefault);
        btnSaveCard = findViewById(R.id.btnSaveCard);

        // Bind the preview elements inside the included layout
        View cardPreviewLayout = findViewById(R.id.layoutCardPreview);
        txtPreviewName = cardPreviewLayout.findViewById(R.id.txtCardHolder);
        txtPreviewNumber = cardPreviewLayout.findViewById(R.id.txtCardNumber);
        txtPreviewExpiry = cardPreviewLayout.findViewById(R.id.txtExpDate);

        // Hide bank name for the blank template preview
        TextView txtBankName = cardPreviewLayout.findViewById(R.id.txtBankName);
        txtBankName.setText("NEW CARD");
    }

    private void setupInputFormatters() {
        // Name Watcher
        inputCardName.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                txtPreviewName.setText(s.length() > 0 ? s.toString().toUpperCase() : "CARD HOLDER");
            }
        });

        // Card Number Watcher (Adds spaces every 4 digits)
        inputCardNumber.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                if (isFormatting) return;
                isFormatting = true;

                String digits = s.toString().replaceAll("\\D", "");
                StringBuilder formatted = new StringBuilder();
                for (int i = 0; i < digits.length(); i++) {
                    if (i > 0 && i % 4 == 0) formatted.append(" ");
                    formatted.append(digits.charAt(i));
                }

                s.replace(0, s.length(), formatted.toString());
                txtPreviewNumber.setText(formatted.length() > 0 ? formatted.toString() : "•••• •••• •••• ••••");

                isFormatting = false;
            }
        });

        // Expiry Watcher (Adds slash after MM)
        inputExpiry.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                if (isFormatting) return;
                isFormatting = true;

                String digits = s.toString().replaceAll("\\D", "");
                if (digits.length() >= 2) {
                    digits = digits.substring(0, 2) + "/" + digits.substring(2);
                }

                s.replace(0, s.length(), digits);
                txtPreviewExpiry.setText(digits.length() > 0 ? digits : "MM/YY");

                isFormatting = false;
            }
        });
    }

    private void setupObservers() {
        viewModel.getIsLoading().observe(this, isLoading -> {
            btnSaveCard.setEnabled(!isLoading);
            btnSaveCard.setText(isLoading ? "Saving..." : "Save Card");
        });

        viewModel.getSaveSuccess().observe(this, newCard -> {
            if (newCard != null) {
                // Return to ManageCardsActivity on success
                finish();
            }
        });

//        viewModel.getErrorMessage().observe(this, error -> {
//            if (error != null) {
//                viewModel.getIsLoading().setValue(false);
//                showErrorSnackbar(error);
//            }
//        });
    }

    private void showErrorSnackbar(String message) {
        View rootView = findViewById(android.R.id.content);
        Snackbar snackbar = Snackbar.make(rootView, message, Snackbar.LENGTH_LONG);
        snackbar.getView().setBackgroundColor(Color.parseColor("#FF4C4C"));
        snackbar.setTextColor(Color.WHITE);
        snackbar.show();
    }

    // Abstract class to reduce TextWatcher boilerplate
    private abstract static class SimpleTextWatcher implements TextWatcher {
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
    }
}