package com.example.urbanklink.requests;

public class StartShiftRequest {
    public int busId;
    public int routeId;

    public StartShiftRequest(int busId, int routeId) {
        this.busId = busId;
        this.routeId = routeId;
    }
}
