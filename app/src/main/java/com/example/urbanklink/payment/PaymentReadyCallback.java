package com.example.urbanklink.payment;

// 2. The Callback Interface
public interface PaymentReadyCallback {
    void onPaymentReady(String paymentMethod, Integer cardId);
    void onPaymentCancelled(String reason);
}
