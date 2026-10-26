package com.example.urbanklink.response;

import com.google.gson.annotations.SerializedName;

public class UserProfileResponse {
    @SerializedName("id")
    private int id;

    @SerializedName("name") // Matches C# exactly
    private String name;

    @SerializedName("email")
    private String email;

    @SerializedName("commuterProfile")
    private String commuterProfile;

    public int getId() { return id; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getCommuterProfile() { return commuterProfile; }
}
