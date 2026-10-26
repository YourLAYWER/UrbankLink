package com.example.urbanklink.database;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

@Dao
public interface UserDao {
    // Replaces the user if their ID already exists
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertUser(LocalUser user);

    // Grabs the single logged-in user
    @Query("SELECT * FROM user_profile LIMIT 1")
    LocalUser getUser();

    // Wipes the profile on Logout
    @Query("DELETE FROM user_profile")
    void clearUser();
}
