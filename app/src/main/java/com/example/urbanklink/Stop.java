package com.example.urbanklink;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

import com.google.gson.annotations.SerializedName;

@Entity(tableName = "stops_table")
public class Stop {
    @SerializedName("id")
    @PrimaryKey(autoGenerate = false)
    @ColumnInfo(name = "stop_id")
    private int stopId;

    @SerializedName("name")
    @ColumnInfo(name = "name")
    private String name;

    @SerializedName("latitude")
    @ColumnInfo(name = "latitude")
    private double latitude;

    @SerializedName("longitude")
    @ColumnInfo(name = "longitude")
    private double longitude;

    // 1. Room loves an empty constructor! It will use this to create the object.
    public Stop() {
    }

    // 2. We keep your original constructor but tell Room to @Ignore it
    // so it doesn't get confused between the two constructors.
    @Ignore
    public Stop(int stopId, String name, double latitude, double longitude) {
        this.stopId = stopId;
        this.name = name;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    // 3. Getters (Room uses these to read data and save it to the DB)
    public int getStopId() { return stopId; }
    public String getName() { return name; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }

    // 4. Setters (Room MUST have these to fill the empty constructor)
    public void setStopId(int stopId) { this.stopId = stopId; }
    public void setName(String name) { this.name = name; }
    public void setLatitude(double latitude) { this.latitude = latitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }

    // 5. Still crucial for your Dropdown UI!
    @Override
    public String toString() {
        return name;
    }
}
