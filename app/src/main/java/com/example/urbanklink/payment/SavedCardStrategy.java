package com.example.urbanklink.payment;

import android.content.Context;

public class SavedCardStrategy implements PaymentStrategy{

    private Integer selectedCardId;

    public SavedCardStrategy(Integer selectedCardId) {
        this.selectedCardId = selectedCardId;
    }
    @Override
    public void processPaymentUI(Context context, double amount, PaymentReadyCallback callback) {
        // If you want a CVV confirmation dialog, it goes here.
        // For now, we instantly approve the UI step and pass the cardId to the backend.

        callback.onPaymentReady("SavedCard", selectedCardId);
    }

    @Override
    public String getBackendPaymentMethodIdentifier() {
        return "";
    }
}
