package com.example.urbanklink.commuter;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.lifecycle.MutableLiveData;

import com.example.urbanklink.database.LocalTicket;
import com.example.urbanklink.database.UrbanLinkDatabase;
import com.example.urbanklink.network.ApiClient;
import com.example.urbanklink.network.UrbanLinkApiService;
import com.example.urbanklink.requests.BookTicketRequest;
import com.example.urbanklink.requests.RouteCalculateRequest;
import com.example.urbanklink.response.JourneyResponse;
import com.example.urbanklink.response.RouteCalculateResponse;
import com.google.firebase.messaging.FirebaseMessaging;

import java.util.concurrent.Executors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class TicketRepository {
    private UrbanLinkApiService apiService;
    private UrbanLinkDatabase db;
    private SharedPreferences prefs;

    public TicketRepository(Application application) {
        apiService = ApiClient.getClient(application).create(UrbanLinkApiService.class);
        db = UrbanLinkDatabase.getInstance(application);
        prefs = application.getSharedPreferences("UrbanLinkPrefs", Context.MODE_PRIVATE);
    }

    /**
     * Fetches the route details, cost, and timeline segments from the backend.
     */
    public void calculateRoute(int startId, int destId, MutableLiveData<RouteCalculateResponse> responseLiveData, MutableLiveData<String> errorLiveData) {
        RouteCalculateRequest request = new RouteCalculateRequest(startId, destId);
        apiService.calculateRoute(request).enqueue(new Callback<RouteCalculateResponse>() {
            @Override
            public void onResponse(Call<RouteCalculateResponse> call, Response<RouteCalculateResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    responseLiveData.postValue(response.body());
                } else {
                    errorLiveData.postValue("Failed to calculate route. Ensure internet connection.");
                }
            }
            @Override
            public void onFailure(Call<RouteCalculateResponse> call, Throwable t) {
                errorLiveData.postValue("Network error!");
            }
        });
    }

    /**
     * Sends the booked journey to the backend, saves it locally, and subscribes to FCM.
     */
    public void bookJourney(int startId, int destId, String paymentMethod, MutableLiveData<JourneyResponse> successData, MutableLiveData<String> errorData) {
        String token = prefs.getString("JWT_TOKEN", null);
        if (token == null) {
            errorData.postValue("Authentication error. Please log in again.");
            return;
        }

        String authHeader = "Bearer " + token.replace("\"", "").trim();

        // IMPORTANT: You will need to update BookTicketRequest in Java and C# to accept the paymentMethod string!
        //BookTicketRequest request = new BookTicketRequest(startId, destId, paymentMethod);
        BookTicketRequest request = new BookTicketRequest(startId, destId);

        apiService.bookJourney(authHeader, request).enqueue(new Callback<JourneyResponse>() {
            @Override
            public void onResponse(Call<JourneyResponse> call, Response<JourneyResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    JourneyResponse data = response.body();

                    // 1. Subscribe to FCM topics
                    if (data.getRouteIds() != null) {
                        for (int routeId : data.getRouteIds()) {
                            FirebaseMessaging.getInstance().subscribeToTopic("route_" + routeId + "_alerts");
                        }
                    }

                    // 2. Save locally
                    int primaryRouteId = (data.getRouteIds() != null && !data.getRouteIds().isEmpty()) ? data.getRouteIds().get(0) : -1;
                    LocalTicket newTicket = new LocalTicket(data.getTicketReference(), data.getJourneyId(), data.getTotalCost(), System.currentTimeMillis(), data.getEstimatedArrivalTime(), startId, destId, data.getTicketStatus(), primaryRouteId);

                    Executors.newSingleThreadExecutor().execute(() -> db.ticketDao().insertTicket(newTicket));

                    // 3. Notify ViewModel
                    successData.postValue(data);
                } else {
                    errorData.postValue("Payment failed (Code: " + response.code() + ").");
                }
            }

            @Override
            public void onFailure(Call<JourneyResponse> call, Throwable t) {
                errorData.postValue("Network error during checkout.");
            }
        });
    }
}
