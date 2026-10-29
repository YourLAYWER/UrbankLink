package com.example.urbanklink.payment;

import android.content.Context;

public class ProviderCardStrategy implements PaymentStrategy{

    @Override
    public void processPaymentUI(Context context, double amount, PaymentReadyCallback callback) {
        // Here you would pop up a dialog asking the user for their 4-digit transit PIN.
        // For now, we simulate immediate success.
        callback.onPaymentReady(getBackendPaymentMethodIdentifier(), null);
    }

    @Override
    public String getBackendPaymentMethodIdentifier() {
        return "ProviderCard";
    }
}
