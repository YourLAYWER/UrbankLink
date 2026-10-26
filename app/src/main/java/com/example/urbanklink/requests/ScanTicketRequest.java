package com.example.urbanklink.requests;

public class ScanTicketRequest {

    public String qrCodeData;
    public ScanTicketRequest(String qrCodeData) {
        this.qrCodeData = qrCodeData;
    }
}
