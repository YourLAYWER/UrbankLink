package com.example.urbanklink;

import com.google.gson.annotations.SerializedName;

public class BookTicketRequest {
    @SerializedName("startStopId")
    private int startStopId;

    @SerializedName("endStopId")
    private int endStopId;

//    @SerializedName("finalAmountPaid")
//    private double finalAmountPaid;
//
//    @SerializedName("isStudentPassApplied")
//    private boolean isStudentPassApplied;

    public BookTicketRequest(int startStopId, int endStopId) {
        this.startStopId = startStopId;
        this.endStopId = endStopId;
//        this.finalAmountPaid = finalAmountPaid;
//        this.isStudentPassApplied = isStudentPassApplied;
    }
}
