package com.example.urbanklink;

import android.app.Activity;
import android.util.Log;

import com.example.urbanklink.database.LocalRoute;
import com.example.urbanklink.database.UrbanLinkDatabase;
import com.example.urbanklink.network.UrbanLinkApiService;
import com.google.android.gms.maps.GoogleMap;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class RouteSyncManager {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public void loadAndSyncRoutes(Activity activity, UrbanLinkDatabase db, UrbanLinkApiService apiService, GoogleMap mMap) {
        // 1. INSTANT LOAD: Fetch from local SQLite on a background thread
        executor.execute(() -> {
            List<LocalRoute> cachedRoutes = db.routeDao().getAllRoutes();

            // Draw instantly if we have cached data
            if (!cachedRoutes.isEmpty()) {
                activity.runOnUiThread(() -> drawRoutesOnMap(mMap, cachedRoutes));
            }

            // 2. BACKGROUND SYNC: Call the C# API silently
            apiService.getAllMapRoutes().enqueue(new Callback<List<LocalRoute>>() {
                @Override
                public void onResponse(Call<List<LocalRoute>> call, Response<List<LocalRoute>> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        List<LocalRoute> freshRoutes = response.body();

                        // 3. UPDATE DB: Save new routes to SQLite
                        executor.execute(() -> {
                            db.routeDao().updateRouteCache(freshRoutes);

                            // Optional: Redraw map with fresh data if this is the first install
                            if (cachedRoutes.isEmpty()) {
                                activity.runOnUiThread(() -> drawRoutesOnMap(mMap, freshRoutes));
                            }
                        });
                    }
                }

                @Override
                public void onFailure(Call<List<LocalRoute>> call, Throwable t) {
                    Log.e("RouteSync", "API unreachable. Relying on local cache.", t);
                }
            });
        });
    }

    private void drawRoutesOnMap(GoogleMap mMap, List<LocalRoute> routes) {
        mMap.clear(); // Clear existing lines before drawing
        for (LocalRoute route : routes) {
            // Call your PolyUtil.decode logic here for each route.encodedPolyline
            // e.g., drawRoutePolyline(mMap, route.encodedPolyline);
        }
    }
}
