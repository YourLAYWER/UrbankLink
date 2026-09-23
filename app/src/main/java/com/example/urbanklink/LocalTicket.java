package com.example.urbanklink;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "tickets")
public class LocalTicket {
    @PrimaryKey
    @NonNull
    public String ticketReference; // The secure GUID

    public int journeyId;
    public double totalCost;
    public long purchaseDate; // Unix timestamp for sorting
    public boolean isClaimed;

    public String estimatedArrivalTime;

    public String ticketStatus;

    public int startStopId;
    public int destStopId;
    public int routeId;

    // Constructor
    public LocalTicket(@NonNull String ticketReference, int journeyId, double totalCost, long purchaseDate, String estimatedArrivalTime, int startStopId, int destStopId, String ticketStatus, int routeId) {
        this.ticketReference = ticketReference;
        this.journeyId = journeyId;
        this.totalCost = totalCost;
        this.purchaseDate = purchaseDate;
        this.isClaimed = false; // Always false when first bought
        this.estimatedArrivalTime = estimatedArrivalTime;
        this.ticketStatus = ticketStatus;
        this.destStopId = destStopId;
        this.startStopId = startStopId;
        this.routeId = routeId;
    }
}
