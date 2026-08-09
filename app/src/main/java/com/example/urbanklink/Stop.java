package com.example.urbanklink;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "stops_table")
public class Stop {
    @PrimaryKey(autoGenerate = false) // False because C# dictates the ID
    @ColumnInfo(name = "stop_id")
    private int stopId;

    @ColumnInfo(name = "name")
    private String name;

    @ColumnInfo(name = "latitude")
    private double latitude;

    @ColumnInfo(name = "longitude")
    private double longitude;

    // Constructor
    public Stop(int stopId, String name, double latitude, double longitude) {
        this.stopId = stopId;
        this.name = name;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    // Getters (Room needs these to read the data)
    public int getStopId() { return stopId; }
    public String getName() { return name; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }

    // Override toString for the AutoCompleteTextView later
    @Override
    public String toString() {
        return name;
    }
}
