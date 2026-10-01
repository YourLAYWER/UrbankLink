package com.example.urbanklink;

public class AuthResponse {
    private String token;
    private String refreshToken;
    private String role;

    public String getToken() {
        return token;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public String getRole() {
        return role;
    }
}
