package com.example.urbanklink.payment;


import android.content.Context;

// 1. The Strategy Interface
public interface PaymentStrategy {
    /**
     * Prepares and validates the payment method before sending it to the server.
     * @param context Needed if the strategy needs to launch a BottomSheet or Dialog
     * @param amount The total amount to be charged
     * @param callback Triggers when the user has successfully provided payment details
     */
    void processPaymentUI(Context context, double amount, PaymentReadyCallback callback);

    // Determines what string to send to your C# backend (e.g., "Debit", "TransitCard")
    String getBackendPaymentMethodIdentifier();
}