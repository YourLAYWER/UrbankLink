package com.example.urbanklink.requests;

import com.google.gson.annotations.SerializedName;

public class AddCardRequest {
    @SerializedName("cardHolderName")
    private String cardHolderName;

    @SerializedName("maskedCardNumber")
    private String maskedCardNumber;

    @SerializedName("expiryDate")
    private String expiryDate;

    @SerializedName("setAsDefault")
    private boolean setAsDefault;

    public AddCardRequest(String cardHolderName, String maskedCardNumber, String expiryDate, boolean setAsDefault) {
        this.cardHolderName = cardHolderName;
        this.maskedCardNumber = maskedCardNumber;
        this.expiryDate = expiryDate;
        this.setAsDefault = setAsDefault;
    }
}