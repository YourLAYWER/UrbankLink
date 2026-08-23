package com.example.urbanklink;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class RouteResponse {
    @SerializedName("message")
    public String message;

    @SerializedName("data")
    public List<TransitRouteData> data;
}
// 2. The actual Route object
class TransitRouteData {
    @SerializedName("routeId")
    public int routeId;

    @SerializedName("routeName")
    public String routeName;

    @SerializedName("baseFare")
    public double baseFare;

    @SerializedName("schedules")
    public List<RouteScheduleData> schedules;
}

// 3. The Schedule object inside the Route
class RouteScheduleData {
    @SerializedName("daysOfOperation")
    public String daysOfOperation;

    @SerializedName("firstDeparture")
    public String firstDeparture;

    @SerializedName("lastDeparture")
    public String lastDeparture;

    @SerializedName("frequencyMinutes")
    public int frequencyMinutes;
}