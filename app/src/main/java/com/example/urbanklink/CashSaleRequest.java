package com.example.urbanklink;

public class CashSaleRequest {
    public int shiftId;
    public int passengerCount;

    public CashSaleRequest(int passengerCount) {
        this.passengerCount = passengerCount;
    }
}
