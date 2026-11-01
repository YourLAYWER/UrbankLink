package com.example.urbanklink.commuter;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.lifecycle.MutableLiveData;

import com.example.urbanklink.BuildConfig;
import com.example.urbanklink.database.LocalRoute;
import com.example.urbanklink.database.LocalTicket;
import com.example.urbanklink.database.Stop;
import com.example.urbanklink.database.UrbanLinkDatabase;
import com.example.urbanklink.network.ApiClient;
import com.example.urbanklink.network.UrbanLinkApiService;
import com.example.urbanklink.requests.BusLocationUpdateDto;
import com.example.urbanklink.response.RouteStatusResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.gson.Gson;
import com.microsoft.signalr.HubConnection;
import com.microsoft.signalr.HubConnectionBuilder;
import com.microsoft.signalr.HubConnectionState;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class TicketScreenRepository {
    private UrbanLinkDatabase db;
    private UrbanLinkApiService apiService;
    private SharedPreferences prefs;
    private ExecutorService executor;
    private HubConnection hubConnection;

    public TicketScreenRepository(Application application) {
        db = UrbanLinkDatabase.getInstance(application);
        apiService = ApiClient.getClient(application).create(UrbanLinkApiService.class);
        prefs = application.getSharedPreferences("UrbanLinkPrefs", Context.MODE_PRIVATE);
        executor = Executors.newSingleThreadExecutor();
    }

    public void loadSpecificTicket(String ticketRef, MutableLiveData<TicketDisplayData> ticketData) {
        executor.execute(() -> {
            LocalTicket ticket = db.ticketDao().getTicketByRef(ticketRef);
            if (ticket != null) {
                Stop start = db.stopDao().getStopById(ticket.startStopId);
                Stop dest = db.stopDao().getStopById(ticket.destStopId);
                LocalRoute route = db.routeDao().getRouteById(ticket.routeId);
                ticketData.postValue(new TicketDisplayData(ticket, start, dest, route));
            }
        });
    }

    public void loadActiveTicket(MutableLiveData<TicketDisplayData> ticketData, MutableLiveData<String> errorMsg) {
        executor.execute(() -> {
            LocalTicket ticket = db.ticketDao().getActiveTicket();
            if (ticket != null && ticket.ticketReference != null) {
                Stop start = db.stopDao().getStopById(ticket.startStopId);
                Stop dest = db.stopDao().getStopById(ticket.destStopId);
                LocalRoute route = db.routeDao().getRouteById(ticket.routeId);
                ticketData.postValue(new TicketDisplayData(ticket, start, dest, route));
            } else {
                errorMsg.postValue("No active tickets found.");
            }
        });
    }

    public void loadHistoricalRoute(int routeId, MutableLiveData<LocalRoute> routeData) {
        executor.execute(() -> {
            LocalRoute route = db.routeDao().getRouteById(routeId);
            routeData.postValue(route);
        });
    }

    public void fetchLiveStatus(String ticketRef, MutableLiveData<RouteStatusResponse> liveStatus, MutableLiveData<String> errorMsg) {
        String token = prefs.getString("JWT_TOKEN", null);
        if (token == null) return;

        String authHeader = "Bearer " + token.replace("\"", "").trim();

        apiService.getTicketStatus(authHeader, ticketRef).enqueue(new Callback<RouteStatusResponse>() {
            @Override
            public void onResponse(Call<RouteStatusResponse> call, Response<RouteStatusResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    RouteStatusResponse status = response.body();

                    // Handle FCM lazy unsubscribe here, keeping it out of the UI
                    if (status.getCompletedRouteIds() != null) {
                        for (int routeId : status.getCompletedRouteIds()) {
                            FirebaseMessaging.getInstance().unsubscribeFromTopic("route_" + routeId + "_alerts");
                        }
                    }
                    liveStatus.postValue(status);
                } else {
                    errorMsg.postValue("API Rejected. Code: " + response.code());
                }
            }
            @Override
            public void onFailure(Call<RouteStatusResponse> call, Throwable t) {
                errorMsg.postValue("Network Failure: " + t.getMessage());
            }
        });
    }

    public void subscribeToBusLocation(int routeId, MutableLiveData<BusLocationUpdateDto> liveLocationData) {
        String hubUrl = BuildConfig.BASE_URL + "transithub";
        hubConnection = HubConnectionBuilder.create(hubUrl).build();
        Gson gson = new Gson();

        // 1. Listen for the broadcast from the C# Hub
        hubConnection.on("ReceiveBusLocation", (payloadJson) -> {
            try {
                BusLocationUpdateDto data = gson.fromJson(payloadJson, BusLocationUpdateDto.class);
                liveLocationData.postValue(data);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, String.class);

        // 2. Start connection and join the specific route group
        new Thread(() -> {
            try {
                hubConnection.start().blockingAwait();
                // Matches the C# Hub method: public async Task JoinRouteGroup(int routeId)
                hubConnection.send("JoinRouteGroup", routeId);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    // LINK: Triggered when ticket becomes SCANNED or EXPIRED
    public void unsubscribeFromBusLocation(int routeId) {
        if (hubConnection != null && hubConnection.getConnectionState() == HubConnectionState.CONNECTED) {
            new Thread(() -> {
                try {
                    // 1. Tell the C# Hub to remove this user from the specific route broadcast
                    hubConnection.send("LeaveRouteGroup", routeId);
                    // 2. Kill the WebSocket connection entirely
                    hubConnection.stop();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }).start();
        }
    }
}
