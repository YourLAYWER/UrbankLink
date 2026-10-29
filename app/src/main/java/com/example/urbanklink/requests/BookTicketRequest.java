package com.example.urbanklink.requests;

import com.google.gson.annotations.SerializedName;

public class BookTicketRequest {
    @SerializedName("startStopId")
    private int startStopId;

    @SerializedName("endStopId")
    private int endStopId;

    @SerializedName("paymentMethod")
    private String paymentMethod; // "ProviderCard" or "SavedCard"

    @SerializedName("cardId")
    private Integer cardId; // Use Integer so it can be gracefully null
    public BookTicketRequest(int startStopId, int endStopId, String paymentMethod, Integer cardId) {
        this.startStopId = startStopId;
        this.endStopId = endStopId;
        this.paymentMethod = paymentMethod;
        this.cardId = cardId;
    }
}
