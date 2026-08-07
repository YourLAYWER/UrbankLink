package com.example.urbanklink;

public class RegistrationRequest {
    private String fullName;
    private String email;
    private String password;
    private String commuterProfile;

    public RegistrationRequest(String fullName, String email, String password, String commuterProfile)
    {
        this.fullName = fullName;
        this.email = email;
        this.password = password;
        this.commuterProfile = commuterProfile;
    }
}
