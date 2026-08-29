package com.example.urbanklink;

public class ScanTicketRequest {
    public int shiftId;
    public String qrCodeData;
    public ScanTicketRequest(int shiftId, String qrCodeData) {
        this.shiftId = shiftId;
        this.qrCodeData = qrCodeData;
    }
}
