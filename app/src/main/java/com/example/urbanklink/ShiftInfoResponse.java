package com.example.urbanklink;

import com.google.gson.annotations.SerializedName;

public class ShiftInfoResponse {
    @SerializedName("busFleetNumber")
    private String busFleetNumber;

    @SerializedName("routeId")
    private int routeId;

    @SerializedName("routeName")
    private String routeName;

    @SerializedName("startStop")
    private String startStop;

    @SerializedName("endStop")
    private String endStop;

    @SerializedName("baseFare")
    private double baseFare;

    @SerializedName("routeDisplay")
    private String routeDisplay;

    // Getters
    public String getBusFleetNumber() { return busFleetNumber; }
    public int getRouteId() { return routeId; }
    public String getRouteName() { return routeName; }
    public String getStartStop() { return startStop; }
    public String getEndStop() { return endStop; }
    public double getBaseFare() { return baseFare; }
    public String getRouteDisplay() { return routeDisplay; }
}
