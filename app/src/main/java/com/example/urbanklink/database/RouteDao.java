package com.example.urbanklink.database;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;

import java.util.List;
@Dao
public interface RouteDao {
    @Query("SELECT * FROM local_routes")
    List<LocalRoute> getAllRoutes();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<LocalRoute> routes);

    @Query("DELETE FROM local_routes")
    void deleteAll();

    @Query("SELECT * FROM local_routes WHERE routeId = :rId LIMIT 1")
    LocalRoute getRouteById(int rId);

    // Safely wipes the old cache and saves the new API data
    @Transaction
    default void updateRouteCache(List<LocalRoute> freshRoutes) {
        deleteAll();
        insertAll(freshRoutes);
    }

    @Query("SELECT * FROM local_routes")
    LiveData<List<LocalRoute>> getAllRoutesLiveData();
}
