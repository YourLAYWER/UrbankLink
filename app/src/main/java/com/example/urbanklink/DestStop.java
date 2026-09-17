package com.example.urbanklink;

import com.google.gson.annotations.SerializedName;

public class DestStop {

    @SerializedName("Name")
    private String name;

    @SerializedName("SequenceNumber")
    private int sequenceNumber;

    @SerializedName("PassengersDroppingOff")
    private int passengersDroppingOff;

    @SerializedName("Status")
    private String status;

    // Getters and Setters
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public int getSequenceNumber() { return sequenceNumber; }
    public void setSequenceNumber(int sequenceNumber) { this.sequenceNumber = sequenceNumber; }

    public int getPassengersDroppingOff() { return passengersDroppingOff; }
    public void setPassengersDroppingOff(int passengersDroppingOff) { this.passengersDroppingOff = passengersDroppingOff; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    // Constructor, Getters, and Setters....
}
