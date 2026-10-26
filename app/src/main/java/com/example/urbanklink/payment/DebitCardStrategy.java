package com.example.urbanklink.payment;

import android.content.Context;

public class DebitCardStrategy implements PaymentStrategy{

    @Override
    public void processPaymentUI(Context context, double amount, PaymentReadyCallback callback) {
        // Here you would normally launch Stripe, Paystack, or a custom Card Entry BottomSheet.
        // Once the user enters valid card details or a token is generated:
        callback.onPaymentReady(getBackendPaymentMethodIdentifier());
    }

    @Override
    public String getBackendPaymentMethodIdentifier() {
        return "DebitCard";
    }
}
