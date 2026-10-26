package com.example.urbanklink.commuter;

import com.google.gson.annotations.SerializedName;

public class ProviderSummary {
    @SerializedName("id")
    private int id;

    @SerializedName("name")
    private String name;

    // Getters
    public int getId() { return id; }
    public String getName() { return name; }

    // Setters
    public void setId(int id) { this.id = id; }
    public void setName(String name) { this.name = name; }

    // The Spinner uses this to display the text!
    @Override
    public String toString() {
        return name;
    }
}
