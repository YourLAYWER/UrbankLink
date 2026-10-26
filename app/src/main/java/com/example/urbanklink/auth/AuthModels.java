package com.example.urbanklink.auth;

import com.google.gson.annotations.SerializedName;

public class AuthModels {
    public static class AuthResponse {
        @SerializedName("token")
        public String token;

        @SerializedName("refreshToken")
        public String refreshToken;

        @SerializedName("role")
        public String role;
    }

    // Use this to send the payload to /refresh
    public static class RefreshRequest {
        @SerializedName("token")
        public String token;

        @SerializedName("refreshToken")
        public String refreshToken;

        public RefreshRequest(String token, String refreshToken) {
            this.token = token;
            this.refreshToken = refreshToken;
        }
    }
}
