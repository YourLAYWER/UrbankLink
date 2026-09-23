package com.example.urbanklink;

import com.google.gson.annotations.SerializedName;

public class JourneyHistory {
    private String ticketReference;
    private String date;
    private double cost;
    private String routeSummary;
    private String status;
    @SerializedName("routeId")
    private int routeId;

    // Getters
    public String getTicketReference() { return ticketReference; }
    public String getDate() { return date; }
    public double getCost() { return cost; }
    public String getRouteSummary() { return routeSummary; }
    public String getStatus() { return status; }
    public int getRouteId() {
        return routeId;
    }
}
