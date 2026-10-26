package com.example.urbanklink.commuter;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.lifecycle.LiveData;

import com.example.urbanklink.database.LocalRoute;
import com.example.urbanklink.database.Stop;
import com.example.urbanklink.database.UrbanLinkDatabase;
import com.example.urbanklink.network.ApiClient;
import com.example.urbanklink.network.UrbanLinkApiService;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class TransitRepository {
    private UrbanLinkDatabase db;
    private UrbanLinkApiService apiService;
    private SharedPreferences prefs;
    private ExecutorService executor;

    public TransitRepository(Application application) {
        db = UrbanLinkDatabase.getInstance(application);
        apiService = ApiClient.getClient(application).create(UrbanLinkApiService.class);
        prefs = application.getSharedPreferences("UrbanLinkPrefs", Context.MODE_PRIVATE);
        executor = Executors.newSingleThreadExecutor();
    }

    public LiveData<List<LocalRoute>> getMapRoutes() {
        // Silently sync from the network in the background
        apiService.getAllMapRoutes().enqueue(new Callback<List<LocalRoute>>() {
            @Override
            public void onResponse(Call<List<LocalRoute>> call, Response<List<LocalRoute>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    executor.execute(() -> db.routeDao().updateRouteCache(response.body()));
                }
            }
            @Override
            public void onFailure(Call<List<LocalRoute>> call, Throwable t) {}
        });

        // Immediately return whatever is in Room as an observable stream!
        return db.routeDao().getAllRoutesLiveData();
    }

    public LiveData<List<Stop>> getAllStops() {
        long lastSync = prefs.getLong("LAST_STOPS_SYNC", 0);
        long sevenDaysInMillis = 7L * 24 * 60 * 60 * 1000;

        if (System.currentTimeMillis() - lastSync > sevenDaysInMillis || lastSync == 0) {
            apiService.getAllStops().enqueue(new Callback<List<Stop>>() {
                @Override
                public void onResponse(Call<List<Stop>> call, Response<List<Stop>> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        executor.execute(() -> {
                            db.stopDao().insertStops(response.body());
                            prefs.edit().putLong("LAST_STOPS_SYNC", System.currentTimeMillis()).apply();
                        });
                    }
                }
                @Override
                public void onFailure(Call<List<Stop>> call, Throwable t) {}
            });
        }
        return db.stopDao().getAllStopsLiveData();
    }

    // Used for drawing pins when a user clicks a route
    public LiveData<List<Stop>> getStopsForRoute(int routeId) {
        return db.stopDao().getStopsByRouteIdLiveData(routeId);
    }
}
