package com.example.urbanklink;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

@Dao
public interface TicketDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertTicket(LocalTicket ticket);

    // NEW: Grabs the single newest ticket that is still valid
    @Query("SELECT * FROM tickets WHERE isClaimed = 0 ORDER BY purchaseDate DESC LIMIT 1")
    LocalTicket getActiveTicket();

    // NEW: Call this when the C# server tells the app the ticket was scanned
    @Query("UPDATE tickets SET isClaimed = 1 WHERE ticketReference = :ref")
    void markTicketAsClaimed(String ref);
}
