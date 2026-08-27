package com.example.urbanklink;

public class ActiveRoute {
    public int id;
    public String name;

    // This tells the dropdown what text to actually display on screen
    @Override
    public String toString() {
        return name;
    }
}
