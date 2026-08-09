package com.example.urbanklink;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.POST;
interface UrbanLinkApiService {
    @POST("api/auth/login")
    Call<AuthResponse> loginUser(@Body LoginRequest request);

    @POST("api/auth/register")
    Call<AuthResponse> registerUser(@Body RegistrationRequest request);

    @GET("api/user/me")
    Call<UserProfileResponse> getUserProfile(@Header("Authorization") String authHeader);
}
