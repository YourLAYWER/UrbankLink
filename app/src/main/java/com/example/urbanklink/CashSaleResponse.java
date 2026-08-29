package com.example.urbanklink;

import java.util.List;

public class CashSaleResponse {
    public String message;
    public double amountAdded;
    public double newTotal;

    public List<PrintedTicket> receipts;

    public static class PrintedTicket {
        public String ticketId;
        public String routeName;
        public double farePaid;
        public String issuedAt;
    }
}
