package com.example.urbanklink;

import com.google.gson.annotations.SerializedName;

public class RouteCalculateRequest {
    @SerializedName("startStopId")
    private int startStopId;

    @SerializedName("endStopId")
    private int endStopId;

    public RouteCalculateRequest(int start, int end) {
        this.startStopId = start;
        this.endStopId = end;
    }
}
