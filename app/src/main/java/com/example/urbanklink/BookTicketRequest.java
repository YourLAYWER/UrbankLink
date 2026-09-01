package com.example.urbanklink;

import com.google.gson.annotations.SerializedName;

public class BookTicketRequest {
    @SerializedName("startStopId")
    private int startStopId;

    @SerializedName("endStopId")
    private int endStopId;
    public BookTicketRequest(int startStopId, int endStopId) {
        this.startStopId = startStopId;
        this.endStopId = endStopId;
    }
}
