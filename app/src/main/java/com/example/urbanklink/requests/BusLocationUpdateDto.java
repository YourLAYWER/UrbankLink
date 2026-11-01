package com.example.urbanklink.requests;

public class BusLocationUpdateDto {
    public int busId;
    public int routeId;
    public double latitude;
    public double longitude;

    public BusLocationUpdateDto(int busId, int routeId, double latitude, double longitude) {
        this.busId = busId;
        this.routeId = routeId;
        this.latitude = latitude;
        this.longitude = longitude;
    }
}
