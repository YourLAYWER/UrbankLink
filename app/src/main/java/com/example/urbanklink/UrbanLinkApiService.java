package com.example.urbanklink;

import java.util.List;

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

    @GET("api/stops")
    Call<List<Stop>> getAllStops();

    @POST("api/Journey/calculate")
    Call<RouteCalculateResponse> calculateRoute(@Body RouteCalculateRequest request);

    @POST("api/Journey/book")
    Call<JourneyResponse> bookJourney(
            @Header("Authorization") String token,
            @Body BookTicketRequest request
    );

    @GET("api/Journey/history")
    Call<List<JourneyHistory>> getJourneyHistory(@Header("Authorization") String authHeader);
}
