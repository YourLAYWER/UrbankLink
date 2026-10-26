package com.example.urbanklink.response;

public class EndShiftResponse {
    public String message;
    public ShiftSummary shiftSummary;

    public static class ShiftSummary {
        public int shiftId;
        public String busFleetNumber;
        public String routeName;
        public String startTime;
        public String endTime;
        public double totalCashHandover;
        public int digitalTicketsScanned;
    }
}
