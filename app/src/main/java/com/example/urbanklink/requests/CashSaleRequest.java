package com.example.urbanklink.requests;

public class CashSaleRequest {
    public int shiftId;
    public int passengerCount;

    public CashSaleRequest(int passengerCount) {
        this.passengerCount = passengerCount;
    }
}
