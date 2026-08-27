package com.example.urbanklink;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.POST;
import retrofit2.http.Path;

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

    @GET("api/transitprovider/summary")
    Call<List<ProviderSummary>> getProviderSummaries();

    @GET("api/transitprovider/type/{transportType}")
    Call<List<ProviderSummary>> getProvidersByType(@Path("transportType") String transportType);

    @GET("api/transitroute/provider/{providerId}")
    Call<RouteResponse> getRoutesByProvider(@Path("providerId") int providerId);

    @GET("api/Fleet/buses/available")
    Call<BusListResponse> getAvailableBuses();

    @GET("api/Fleet/routes")
    Call<RouteListResponse> getAllRoutes();

    @POST("api/ConductorShift/start")
    Call<StartShiftResponse> startShift(@Body StartShiftRequest request);
}
