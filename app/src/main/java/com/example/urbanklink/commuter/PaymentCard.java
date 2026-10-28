package com.example.urbanklink.commuter;

import com.google.gson.annotations.SerializedName;

public class PaymentCard {
    @SerializedName("id")
    private String cardId;
    @SerializedName("cardHolderName")
    private String cardHolderName;
    @SerializedName("maskedCardNumber") // Maps exactly to the C# DTO
    private String maskedCardNumber;

    @SerializedName("expiryDate")
    private String expiryDate;

    @SerializedName("isDefault")
    private boolean isDefault;
    private String brand; // e.g., "Visa", "Mastercard", "UrbanLink"


    public PaymentCard(String cardId, String cardHolderName, String maskedCardNumber, String expiryDate, String brand, boolean isDefault) {
        this.cardId = cardId;
        this.cardHolderName = cardHolderName;
        this.maskedCardNumber = maskedCardNumber;
        this.expiryDate = expiryDate;
        this.brand = brand;
        this.isDefault = isDefault;
    }

    public String getCardId() { return cardId; }
    public String getCardHolderName() { return cardHolderName; }
    public String getMaskedCardNumber() { return maskedCardNumber; }
    public String getExpiryDate() { return expiryDate; }
    public String getBrand() { return brand; }
    public boolean isDefault() { return isDefault; }

    public void setDefault(boolean isDefault) { this.isDefault = isDefault; }
}