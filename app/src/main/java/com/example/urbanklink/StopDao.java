package com.example.urbanklink;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface StopDao {
    // Overwrites existing stops if the C# ID already exists locally
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertStops(List<Stop> stops);

    // Grabs everything, sorted alphabetically for the UI
    @Query("SELECT * FROM stops_table ORDER BY name ASC")
    List<Stop> getAllStops();

    // The simple search query for your UI
    @Query("SELECT * FROM stops_table WHERE name LIKE '%' || :searchQuery || '%' ORDER BY name ASC")
    List<Stop> searchStopsByName(String searchQuery);

    // Clears the table (useful if you want to wipe and resync)
    @Query("DELETE FROM stops_table")
    void clearAllStops();

    //  Look up for stop base on ID
    @Query("SELECT * FROM stops_table WHERE stop_id = :stopId LIMIT 1")
    Stop getStopById(int stopId);

    @Query("SELECT * FROM stops_table WHERE routeIds LIKE '%,' || :rId || ',%'")
    List<Stop> getStopsByRouteId(int rId);

    @Query("SELECT * FROM stops_table ORDER BY name ASC")
    LiveData<List<Stop>> getAllStopsLiveData();

    @Query("SELECT * FROM stops_table WHERE routeIds LIKE '%,' || :routeId || ',%'")
    LiveData<List<Stop>> getStopsByRouteIdLiveData(int routeId);
}
