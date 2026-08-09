package com.example.urbanklink;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

// If you add a Tickets table later, it becomes: entities = {Stop.class, Ticket.class}
@Database(entities = {Stop.class}, version = 1, exportSchema = false)
public abstract class UrbanLinkDatabase extends RoomDatabase {
    private static UrbanLinkDatabase instance;

    // Links the DAO to the database
    public abstract StopDao stopDao();

    // Singleton pattern to prevent memory leaks
    public static synchronized UrbanLinkDatabase getInstance(Context context) {
        if (instance == null) {
            instance = Room.databaseBuilder(context.getApplicationContext(),
                            UrbanLinkDatabase.class, "urbanlink_database")
                    .fallbackToDestructiveMigration() // Wipes DB if you change version number
                    .build();
        }
        return instance;
    }
}
