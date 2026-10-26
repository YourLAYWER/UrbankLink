package com.example.urbanklink.network;

import com.example.urbanklink.requests.BookTicketRequest;
import com.example.urbanklink.response.BusListResponse;
import com.example.urbanklink.requests.CashSaleRequest;
import com.example.urbanklink.response.CashSaleResponse;
import com.example.urbanklink.response.EndShiftResponse;
import com.example.urbanklink.requests.IncidentReportRequest;
import com.example.urbanklink.JourneyHistory;
import com.example.urbanklink.response.JourneyResponse;
import com.example.urbanklink.database.LocalRoute;
import com.example.urbanklink.requests.LoginRequest;
import com.example.urbanklink.commuter.ProviderSummary;
import com.example.urbanklink.requests.RegistrationRequest;
import com.example.urbanklink.requests.RouteCalculateRequest;
import com.example.urbanklink.response.RouteCalculateResponse;
import com.example.urbanklink.response.RouteListResponse;
import com.example.urbanklink.RouteResponse;
import com.example.urbanklink.response.RouteStatusResponse;
import com.example.urbanklink.requests.ScanTicketRequest;
import com.example.urbanklink.response.ScanTicketResponse;
import com.example.urbanklink.response.ShiftInfoResponse;
import com.example.urbanklink.requests.StartShiftRequest;
import com.example.urbanklink.response.StartShiftResponse;
import com.example.urbanklink.database.Stop;
import com.example.urbanklink.response.UserProfileResponse;
import com.example.urbanklink.auth.AuthModels;
import com.example.urbanklink.auth.AuthResponse;

import java.util.List;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.POST;
import retrofit2.http.Path;

public interface UrbanLinkApiService {
    @POST("api/auth/login")
    Call<AuthResponse> loginUser(@Body LoginRequest request);

    @POST("api/auth/register")
    Call<AuthResponse> registerUser(@Body RegistrationRequest request);

    @POST("api/Auth/refresh")
    Call<AuthModels.AuthResponse> refreshTokens(@Body AuthModels.RefreshRequest request);

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

    @GET("api/TransitRoute/routes")
    Call<RouteListResponse> getAllRoutes();

    @POST("api/ConductorShift/start")
    Call<StartShiftResponse> startShift(@Body StartShiftRequest request);

    @POST("api/ConductorShift/scan")
    Call<ScanTicketResponse> scanTicket(@Body ScanTicketRequest request);

    @POST("api/ConductorShift/cash-sale")
    Call<CashSaleResponse> recordCashSale(@Body CashSaleRequest request);

    @GET("api/ConductorShift/summary")
    Call<EndShiftResponse> getShiftSummary(@Header("Authorization") String token);

    @POST("api/ConductorShift/end")
    Call<EndShiftResponse> endShift(@Header("Authorization") String token);

    @POST("api/incident/report")
    Call<ResponseBody> logIncident(
            @Header("Authorization") String token,
            @Body IncidentReportRequest request
    );

    @GET("api/ConductorShift/active-shift-info")
    Call<ShiftInfoResponse> getActiveShiftInfo();

    @GET("api/Incident/routes/{routeId}/status")
    Call<RouteStatusResponse> getRouteStatus(@Path("routeId") int routeId);

    @GET("api/Journey/tickets/{ticketReference}/status")
    Call<RouteStatusResponse> getTicketStatus(
            @Header("Authorization") String token,
            @Path("ticketReference") String ticketReference
    );

    @GET("api/TransitRoute/all-map-routes")
    Call<List<LocalRoute>> getAllMapRoutes();
}
