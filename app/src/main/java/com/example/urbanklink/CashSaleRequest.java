package com.example.urbanklink;

public class CashSaleRequest {
    public int shiftId;
    public int passengerCount;

    public CashSaleRequest(int shiftId, int passengerCount) {
        this.shiftId = shiftId;
        this.passengerCount = passengerCount;
    }
}
