package com.example.urbanklink;

import com.google.gson.annotations.SerializedName;

public class IncidentReportRequest {
    @SerializedName("isEmergency")
    private boolean isEmergency;

    @SerializedName("category")
    private int category;

    @SerializedName("description")
    private String description;

    @SerializedName("latitude")
    private double latitude;

    @SerializedName("longitude")
    private double longitude;

    public IncidentReportRequest(boolean isEmergency, int category, String description, double latitude, double longitude) {
        this.isEmergency = isEmergency;
        this.category = category;
        this.description = description;
        this.latitude = latitude;
        this.longitude = longitude;
    }
}
