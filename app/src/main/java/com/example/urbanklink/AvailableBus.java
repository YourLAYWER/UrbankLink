package com.example.urbanklink;

public class AvailableBus {
    public int id;
    public String fleetNumber;
    public String label;

    // This tells the dropdown what text to actually display on screen
    @Override
    public String toString() {
        return label;
    }
}
