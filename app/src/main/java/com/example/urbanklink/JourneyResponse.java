package com.example.urbanklink;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class JourneyResponse {
    @SerializedName("message")
    private String message;

    @SerializedName("journeyId")
    private Integer journeyId;

    @SerializedName("ticketReference")
    private String ticketReference;

    @SerializedName("totalCost")
    private Double totalCost;

    @SerializedName("estimatedDurationMinutes")
    private int estimatedDurationMinutes;

    @SerializedName("estimatedArrivalTime")
    private String estimatedArrivalTime;

    @SerializedName("ticketStatus")
    private String ticketStatus;

    @SerializedName("routeIds")
    private List<Integer> routeIds;

    // --- GETTERS ---

    public String getMessage() {
        return message;
    }

    // THIS is the method your Ticket_details screen is looking for!
    public Integer getJourneyId() {
        return journeyId;
    }

    public String getTicketReference() {
        return ticketReference;
    }

    public Double getTotalCost() {
        return totalCost;
    }

    public Integer getEstimatedDurationMinutes() {
        return estimatedDurationMinutes;
    }

    public String getEstimatedArrivalTime() {return estimatedArrivalTime;}

    public String getTicketStatus() {return ticketStatus;}

    public List<Integer> getRouteIds() {
        return routeIds;
    }
}
