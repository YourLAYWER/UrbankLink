package com.example.urbanklink.database;

import com.google.gson.annotations.SerializedName;

public class RouteSegmentDto {
    @SerializedName("stopId")
    private int stopId;

    @SerializedName("stopName")
    private String stopName;

    @SerializedName("transitLine")
    private String transitLine;

    @SerializedName("arrivalTime")
    private String arrivalTime;

    public String getStopName() { return stopName; }
    public String getTransitLine() { return transitLine; }
    public String getArrivalTime() { return arrivalTime; }
}
