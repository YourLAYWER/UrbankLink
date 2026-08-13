package com.example.urbanklink;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "user_profile")
public class LocalUser {
    @PrimaryKey
    public int id;

    public String name;
    public String email;
    public String commuterProfile;

    public LocalUser(int id, String name, String email, String commuterProfile) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.commuterProfile = commuterProfile;
    }
}
