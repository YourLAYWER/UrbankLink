package com.example.urbanklink.database;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "local_routes")
public class LocalRoute {
    @PrimaryKey
    public int routeId;
    public String routeName;
    public String transportType;
    public String encodedPolyline;
}
