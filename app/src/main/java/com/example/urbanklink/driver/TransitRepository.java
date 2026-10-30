package com.example.urbanklink.driver;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.lifecycle.MutableLiveData;

import com.example.urbanklink.BuildConfig;
import com.example.urbanklink.database.DestStop;
import com.example.urbanklink.database.LocalRoute;
import com.example.urbanklink.database.Stop;
import com.example.urbanklink.database.UrbanLinkDatabase;
import com.example.urbanklink.network.ApiClient;
import com.example.urbanklink.network.UrbanLinkApiService;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.microsoft.signalr.HubConnection;
import com.microsoft.signalr.HubConnectionBuilder;
import com.microsoft.signalr.HubConnectionState;

import java.lang.reflect.Type;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class TransitRepository {
    private final Application application;
    private final UrbanLinkApiService apiService;
    private final SharedPreferences prefs;
    private final Gson gson = new Gson();

    private HubConnection hubConnection;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public TransitRepository(Application application) {
        this.application = application;
        this.apiService = ApiClient.getClient(application).create(UrbanLinkApiService.class);
        this.prefs = application.getSharedPreferences("UrbanLinkPrefs", Context.MODE_PRIVATE);
    }

    // --- PREFERENCES ---
    public int getActiveBusId() { return prefs.getInt("ACTIVE_BUS_ID", -1); }
    public int getActiveRouteId() { return prefs.getInt("ACTIVE_ROUTE_ID", -1); }
    public int getHighestClearedSequence() { return prefs.getInt("HIGHEST_CLEARED_SEQ", 0); }

    // LINK: Triggered by TransitViewModel.markArrivedAtCurrentStop()
    public void saveQueueState(int highestSequence, DestStop lastCleared, List<DestStop> fullServerQueue) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt("HIGHEST_CLEARED_SEQ", highestSequence);
        if (lastCleared != null) editor.putString("LAST_CLEARED_STOP", gson.toJson(lastCleared));
        if (fullServerQueue != null) editor.putString("CACHED_SERVER_QUEUE", gson.toJson(fullServerQueue));
        editor.apply();
    }

    // LINK: Triggered by TransitViewModel on startup
    public DestStop getSavedLastClearedStop() {
        String json = prefs.getString("LAST_CLEARED_STOP", null);
        return json != null ? gson.fromJson(json, DestStop.class) : null;
    }

    public List<DestStop> getSavedServerQueue() {
        String json = prefs.getString("CACHED_SERVER_QUEUE", null);
        if (json == null) return null;
        Type listType = new TypeToken<List<DestStop>>(){}.getType();
        return gson.fromJson(json, listType);
    }

    // --- ROOM DATABASE MAP DATA ---
    // LINK: Triggered by TransitViewModel -> Pushes map data up to the UI
    public void getMapDataFromRoom(int routeId, MutableLiveData<LocalRoute> routeLiveData, MutableLiveData<List<Stop>> stopsLiveData) {
        executor.execute(() -> {
            UrbanLinkDatabase db = UrbanLinkDatabase.getInstance(application);
            routeLiveData.postValue(db.routeDao().getRouteById(routeId));
            stopsLiveData.postValue(db.stopDao().getStopsByRouteId(routeId));
        });
    }

    // --- RETROFIT SYNC ---
    // LINK: Triggered by TransitViewModel on initialization
    public void syncStopsAndRoutes(Runnable onSyncComplete) {
        apiService.getAllStops().enqueue(new Callback<List<Stop>>() {
            @Override
            public void onResponse(Call<List<Stop>> call, Response<List<Stop>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    executor.execute(() -> {
                        UrbanLinkDatabase.getInstance(application).stopDao().insertStops(response.body());
                        syncRoutes(onSyncComplete);
                    });
                } else syncRoutes(onSyncComplete); // Fallback
            }
            @Override
            public void onFailure(Call<List<Stop>> call, Throwable t) {
                syncRoutes(onSyncComplete); // Fallback
            }
        });
    }

    private void syncRoutes(Runnable onSyncComplete) {
        apiService.getAllMapRoutes().enqueue(new Callback<List<LocalRoute>>() {
            @Override
            public void onResponse(Call<List<LocalRoute>> call, Response<List<LocalRoute>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    executor.execute(() -> {
                        UrbanLinkDatabase.getInstance(application).routeDao().updateRouteCache(response.body());
                        onSyncComplete.run(); // Triggers the map draw sequence
                    });
                }
            }
            @Override
            public void onFailure(Call<List<LocalRoute>> call, Throwable t) { }
        });
    }

    // --- SIGNALR ---
    // LINK: Connects WebSocket and posts incoming raw queues to the ViewModel LiveData
    public void connectSignalR(int busId, MutableLiveData<List<DestStop>> serverQueueLiveData, MutableLiveData<String> errorLiveData) {
        String hubUrl = BuildConfig.BASE_URL + "transithub";
        hubConnection = HubConnectionBuilder.create(hubUrl).build();

        hubConnection.on("QueueUpdated", (queueJson) -> {
            try {
                Type listType = new TypeToken<List<DestStop>>(){}.getType();
                List<DestStop> freshQueue = gson.fromJson(queueJson, listType);
                serverQueueLiveData.postValue(freshQueue);
            } catch (Exception e) {
                errorLiveData.postValue("Failed to parse queue update");
            }
        }, String.class);

        executor.execute(() -> {
            try {
                hubConnection.start().blockingAwait();
                hubConnection.send("JoinBusGroup", String.valueOf(busId));
            } catch (Exception e) {
                errorLiveData.postValue("Real-time sync offline");
            }
        });
    }

    public void disconnectSignalR() {
        if (hubConnection != null && hubConnection.getConnectionState() == HubConnectionState.CONNECTED) {
            hubConnection.stop();
        }
    }
}
