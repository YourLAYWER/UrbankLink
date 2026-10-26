package com.example.urbanklink.response;

import com.example.urbanklink.database.RouteSegmentDto;
import com.google.gson.annotations.SerializedName;

import java.util.List;

public class RouteCalculateResponse {
    @SerializedName("baseCost")
    private double baseCost;

    @SerializedName("estimatedDurationMinutes")
    private int estimatedDurationMinutes;

    @SerializedName("segments")
    private List<RouteSegmentDto> segments;

    public double getBaseCost() { return baseCost; }
    public int getEstimatedDurationMinutes() { return estimatedDurationMinutes; }
    public List<RouteSegmentDto> getSegments() { return segments; }
}
