package com.example.urbanklink;

import com.google.gson.annotations.SerializedName;

public class JourneyResponse {
    @SerializedName("message")
    private String message;

    @SerializedName("journeyId")
    private Integer journeyId;

    @SerializedName("totalCost")
    private Double totalCost;

    @SerializedName("estimatedDurationMinutes")
    private int estimatedDurationMinutes;

    // --- GETTERS ---

    public String getMessage() {
        return message;
    }

    // THIS is the method your Ticket_details screen is looking for!
    public Integer getJourneyId() {
        return journeyId;
    }

    public Double getTotalCost() {
        return totalCost;
    }

    public Integer getEstimatedDurationMinutes() {
        return estimatedDurationMinutes;
    }
}
