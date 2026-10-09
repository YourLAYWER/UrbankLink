package com.example.urbanklink;

import android.Manifest;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.urbanklink.BuildConfig;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.maps.android.PolyUtil;
import com.microsoft.signalr.HubConnection;
import com.microsoft.signalr.HubConnectionBuilder;
import com.microsoft.signalr.HubConnectionState;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TransitTelemetry extends AppCompatActivity implements OnMapReadyCallback {

    // UI Cards mapped from your XML
    private MaterialCardView cardPreviousStop, cardCurrentStop, cardNextStop;
    private TextView txtPrevName, txtCurrentName, txtNextName, txtNextPax;
    private MaterialButton btnArrived;

    // Real-time Queue state
    private List<DestStop> cachedServerQueue = new ArrayList<>();
    private DestStop previousStop, currentStop, nextStop;

    //private int currentQueueIndex = 0;

    private int highestClearedSequence = 0;
    private DestStop lastClearedStop = null;
    private List<DestStop> activeFilteredQueue = new ArrayList<>();

    // SignalR Hub Connection
    private HubConnection hubConnection;
    private final Gson gson = new Gson();
    private int activeBusId;

    // Location Variables
    private GoogleMap mMap;
    private FusedLocationProviderClient fusedLocationClient;
    private androidx.activity.result.ActivityResultLauncher<String[]> locationPermissionRequest;
    private java.util.List<Stop> cachedRouteStops = new java.util.ArrayList<>();
    private java.util.List<com.google.android.gms.maps.model.Marker> activeStopMarkers = new java.util.ArrayList<>();
    private boolean isRouteDrawn = false;

    // Fast-lookup memory cache for coordinates
    private java.util.HashMap<Integer, LatLng> stopCoordinateCache = new java.util.HashMap<>();
    // Track active pins so we can delete them without using mMap.clear()

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_transit_telemetry);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        locationPermissionRequest = registerForActivityResult(
                new androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions(),
                result -> {
                    Boolean fineGranted = result.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false);
                    Boolean coarseGranted = result.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false);

                    if ((fineGranted != null && fineGranted) || (coarseGranted != null && coarseGranted)) {
                        Toast.makeText(this, "Location permission granted", Toast.LENGTH_SHORT).show();
                        enableDriverLocation(); // Turn on the blue dot now that we have permission!
                    } else {
                        Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show();
                    }
                }
        );

        //CHECK and ASK for the permission
        checkLocationPermissions();

        // --> 2. Initialize Map Fragment <--
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.mapFragment);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        SideNavUtils.setupSideNavigation(this, R.id.navTabStops);

        View rootView = findViewById(android.R.id.content);
        HeaderManager.loadHeaderData(this, rootView, () -> {
            // Start the chain reaction: Stops -> Routes -> Draw Map
            syncStopsToLocalDatabase();
        });
        // Automatically fetches and populates the header info across all screens!

        // Inside TransitTelemetry.java, instead of a hardcoded "1":
        SharedPreferences prefs = getSharedPreferences("UrbanLinkPrefs", MODE_PRIVATE);
        int activeShiftId = prefs.getInt("ACTIVE_SHIFT_ID", -1);
        activeBusId = prefs.getInt("ACTIVE_BUS_ID", -1);

        // Debug log to verify if preferences are loading correctly
        Log.d("BusDebug", "Active Shift ID: " + activeShiftId + ", Active Bus ID: " + activeBusId);

        initializeViews();
        setupListeners();
        restoreTelemetryState();
        connectSignalR();
    }

    private void syncRoutesToLocalDatabase() {
        UrbanLinkApiService apiService = ApiClient.getClient(getApplicationContext()).create(UrbanLinkApiService.class);

        apiService.getAllMapRoutes().enqueue(new retrofit2.Callback<List<LocalRoute>>() {
            @Override
            public void onResponse(retrofit2.Call<List<LocalRoute>> call, retrofit2.Response<List<LocalRoute>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<LocalRoute> freshRoutes = response.body();

                    ExecutorService executor = Executors.newSingleThreadExecutor();
                    executor.execute(() -> {
                        // 1. Save the routes to the offline Room cache
                        UrbanLinkDatabase db = UrbanLinkDatabase.getInstance(getApplicationContext());
                        db.routeDao().updateRouteCache(freshRoutes);

                        // 2. Now that the database has the polyline, draw the map!
                        runOnUiThread(() -> updateLiveMapFromRoom());
                    });
                } else {
                    Log.e("MAP_SYNC", "Failed to fetch routes. Code: " + response.code());
                }
            }

            @Override
            public void onFailure(retrofit2.Call<List<LocalRoute>> call, Throwable t) {
                Log.e("MAP_SYNC", "Network error fetching routes: " + t.getMessage());
            }
        });
    }

    private void syncStopsToLocalDatabase() {
        UrbanLinkApiService apiService = ApiClient.getClient(getApplicationContext()).create(UrbanLinkApiService.class);

        apiService.getAllStops().enqueue(new retrofit2.Callback<List<Stop>>() {
            @Override
            public void onResponse(retrofit2.Call<List<Stop>> call, retrofit2.Response<List<Stop>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<Stop> freshStops = response.body();

                    ExecutorService executor = Executors.newSingleThreadExecutor();
                    executor.execute(() -> {
                        // 1. Save all stops to the offline Room cache
                        UrbanLinkDatabase db = UrbanLinkDatabase.getInstance(getApplicationContext());
                        db.stopDao().insertStops(freshStops);

                        // 2. Now trigger the route sync
                        runOnUiThread(() -> syncRoutesToLocalDatabase());
                    });
                } else {
                    Log.e("MAP_SYNC", "Failed to fetch stops. Code: " + response.code());
                    syncRoutesToLocalDatabase(); // Fallback to route sync
                }
            }

            @Override
            public void onFailure(retrofit2.Call<List<Stop>> call, Throwable t) {
                Log.e("MAP_SYNC", "Network error fetching stops: " + t.getMessage());
                syncRoutesToLocalDatabase(); // Fallback to route sync
            }
        });
    }
    private void checkLocationPermissions() {
        if (androidx.core.content.ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                androidx.core.content.ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

            // This actually pops up the dialog asking the user!
            locationPermissionRequest.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            });
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        // 1. Reload the latest pointer and queue from SharedPreferences
        // (In case the driver cleared a stop or data synced on another screen)
        restoreTelemetryState();

        // 2. If the heavy lifting (cyan route and RAM cache) is already done,
        // instantly redraw the pins to match the latest state!
        if (isRouteDrawn) {
            refreshMapPins();
        }
    }

    private void updateLiveMapFromRoom() {
        if (mMap == null) return;

        SharedPreferences prefs = getSharedPreferences("UrbanLinkPrefs", MODE_PRIVATE);
        int activeRouteId = prefs.getInt("ACTIVE_ROUTE_ID", -1);
        if (activeRouteId == -1) return;

        // ONLY hit the database and draw the cyan line ONCE
        if (!isRouteDrawn) {
            ExecutorService executor = Executors.newSingleThreadExecutor();
            executor.execute(() -> {
                UrbanLinkDatabase db = UrbanLinkDatabase.getInstance(getApplicationContext());
                LocalRoute activeRoute = db.routeDao().getRouteById(activeRouteId);
                List<Stop> allRouteStops = db.stopDao().getStopsByRouteId(activeRouteId);

                // Build an instant-lookup dictionary of StopId -> Coordinates
                for (Stop stop : allRouteStops) {
                    stopCoordinateCache.put(stop.getStopId(), new LatLng(stop.getLatitude(), stop.getLongitude()));
                }

                runOnUiThread(() -> {
                    if (activeRoute != null && activeRoute.encodedPolyline != null) {
                        List<LatLng> decodedPath = PolyUtil.decode(activeRoute.encodedPolyline);
                        mMap.addPolyline(new PolylineOptions()
                                .addAll(decodedPath)
                                .color(android.graphics.Color.parseColor("#008899"))
                                .width(14f)
                                .geodesic(true));
                        isRouteDrawn = true;
                    }
                    // Initial pin draw
                    refreshMapPins();
                });
            });
        } else {
            // The route is already drawn and coordinates are in RAM. Just update pins!
            refreshMapPins();
        }
    }

    private void refreshMapPins() {
        // 1. Wipe ONLY the old pins, never the cyan route line
        for (com.google.android.gms.maps.model.Marker marker : activeStopMarkers) {
            marker.remove();
        }
        activeStopMarkers.clear();

        // 2. Instantly drop new pins using the fast HashMap
        for (DestStop upcomingDest : activeFilteredQueue) {
            LatLng cachedPos = stopCoordinateCache.get(upcomingDest.getStopId());

            if (cachedPos != null) {
                com.google.android.gms.maps.model.Marker m = mMap.addMarker(new MarkerOptions()
                        .position(cachedPos)
                        .title(upcomingDest.getName())
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE)));

                if (m != null) activeStopMarkers.add(m);
            }
        }
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;
        enableDriverLocation(); // Call the location logic exactly like MainActivity
    }

    private void enableDriverLocation() {
        if (mMap == null) return;

        // Check if we have location permissions
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {

            // Turn on the blue dot
            mMap.setMyLocationEnabled(true);
            mMap.getUiSettings().setMyLocationButtonEnabled(true);

            // 1. Center immediately on Gqeberha as a clean default view
            LatLng gqeberhaDefault = new LatLng(-33.9608, 25.6022);
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(gqeberhaDefault, 15f));

            // 2. Animate to the driver's actual GPS coordinates once retrieved
            fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
                if (location != null && mMap != null) {
                    LatLng currentLatLng = new LatLng(location.getLatitude(), location.getLongitude());
                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 15f));
                }
            });
        }
    }

    private void initializeViews() {
        cardPreviousStop = findViewById(R.id.cardPreviousStop);
        cardCurrentStop = findViewById(R.id.cardCurrentStop);
        cardNextStop = findViewById(R.id.cardNextStop);

        // Bind text views using root findViewById so they are never null
        txtPrevName = findViewById(R.id.txtPrevName);
        txtCurrentName = findViewById(R.id.txtCurrentName);
        txtNextName = findViewById(R.id.txtNextName);
        //txtNextPax = findViewById(R.id.txtNextPax);

        // Bind the ARRIVED button inside the current stop card container
        btnArrived = cardCurrentStop.findViewById(R.id.btnArrived);
    }

    private void saveTelemetryState() {
        SharedPreferences prefs = getSharedPreferences("UrbanLinkPrefs", MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();

        // 1. Save the pointer
        editor.putInt("HIGHEST_CLEARED_SEQ", highestClearedSequence);

        // 2. Save the previous stop object as JSON
        if (lastClearedStop != null) {
            editor.putString("LAST_CLEARED_STOP", gson.toJson(lastClearedStop));
        }

        // 3. Save the entire active queue as JSON
        if (cachedServerQueue != null && !cachedServerQueue.isEmpty()) {
            editor.putString("CACHED_SERVER_QUEUE", gson.toJson(cachedServerQueue));
        }

        editor.apply();
    }

    private void restoreTelemetryState() {
        SharedPreferences prefs = getSharedPreferences("UrbanLinkPrefs", MODE_PRIVATE);

        // 1. Restore the pointer (default to 0 if new)
        highestClearedSequence = prefs.getInt("HIGHEST_CLEARED_SEQ", 0);

        // 2. Restore the previous stop
        String lastStopJson = prefs.getString("LAST_CLEARED_STOP", null);
        if (lastStopJson != null) {
            lastClearedStop = gson.fromJson(lastStopJson, DestStop.class);
        }

        // 3. Restore the active queue and instantly draw the UI
        String queueJson = prefs.getString("CACHED_SERVER_QUEUE", null);
        if (queueJson != null) {
            Type listType = new TypeToken<List<DestStop>>(){}.getType();
            cachedServerQueue = gson.fromJson(queueJson, listType);

            applySequenceFilter();
            refreshUIState();
        }
    }

    private void applySequenceFilter() {
        activeFilteredQueue.clear();
        for (DestStop stop : cachedServerQueue) {
            // Only keep stops that the bus hasn't physically passed yet
            if (stop.getSequenceNumber() > highestClearedSequence) {
                activeFilteredQueue.add(stop);
            }
        }
    }

    private void setupListeners() {
        // Driver taps "ARRIVED!" to progress to the next stop in sequence
        btnArrived.setOnClickListener(v -> {
            btnArrived.setEnabled(false);
            btnArrived.setText("CLEARING...");

            // Trigger animation and data shift
            cardCurrentStop.postDelayed(this::animateRouteProgression, 500);
        });
    }

    private void connectSignalR() {
        String hubUrl = BuildConfig.BASE_URL + "transithub";
        hubConnection = HubConnectionBuilder.create(hubUrl).build();

        // Listen for live queue broadcasts from the backend
        hubConnection.on("QueueUpdated", (queueJson) -> {
            runOnUiThread(() -> {
                try {
                    Type listType = new TypeToken<List<DestStop>>(){}.getType();
                    List<DestStop> freshQueue = gson.fromJson(queueJson, listType);

                    updateQueueData(freshQueue);
                } catch (Exception e) {
                    Log.e("SignalR_Error", "Failed to parse queue: " + e.getMessage(), e);
                    Toast.makeText(this, "Error syncing route queue", Toast.LENGTH_SHORT).show();
                }
            });
        }, String.class);

        new Thread(() -> {
            try {
                hubConnection.start().blockingAwait();
                hubConnection.send("JoinBusGroup", String.valueOf(activeBusId));
                Log.d("SignalR_Test", "Connected and joined group for bus: " + activeBusId);
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Real-time sync offline", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void updateQueueData(List<DestStop> newServerQueue) {
        if (newServerQueue == null) return;

        // Cache the raw list from the server
        cachedServerQueue = newServerQueue;

        // Apply the "No Time Travel" filter
        applySequenceFilter();

        // Redraw the screen
        refreshUIState();

        // Save the fresh queue from the server
        saveTelemetryState();
    }
    private boolean currentStopNameIsSet() {
        return txtCurrentName != null && !txtCurrentName.getText().toString().isEmpty();
    }

    private void refreshUIState() {
        // 1. Previous Stop (The one we just cleared)
        if (lastClearedStop != null) {
            cardPreviousStop.setVisibility(View.VISIBLE);
            txtPrevName.setText(lastClearedStop.getName() != null ? lastClearedStop.getName() : "");
        } else {
            cardPreviousStop.setVisibility(View.INVISIBLE);
        }

        // 2. Current Target Stop (Always index 0 of the filtered queue)
        if (activeFilteredQueue.size() > 0) {
            cardCurrentStop.setVisibility(View.VISIBLE);
            DestStop current = activeFilteredQueue.get(0);
            txtCurrentName.setText(current.getName() != null ? current.getName() : "");
            btnArrived.setEnabled(true);
        } else {
            cardCurrentStop.setVisibility(View.INVISIBLE);
            txtCurrentName.setText("ROUTE COMPLETE");
            btnArrived.setEnabled(false);
        }

        // 3. Subsequent Stop (Always index 1 of the filtered queue)
        if (activeFilteredQueue.size() > 1) {
            cardNextStop.setVisibility(View.VISIBLE);
            DestStop next = activeFilteredQueue.get(1);
            txtNextName.setText(next.getName() != null ? next.getName() : "");
           // txtNextPax.setText("EXPECTED: ~" + next.getPassengersDroppingOff() + " PAX");
        } else {
            cardNextStop.setVisibility(View.INVISIBLE);
        }
    }

    private void animateRouteProgression() {
        // 1. Lock the current stop into the past
        if (activeFilteredQueue.size() > 0) {
            DestStop current = activeFilteredQueue.get(0);
            highestClearedSequence = current.getSequenceNumber(); // Move the pointer forward
            lastClearedStop = current; // Save for the 'Previous' card
        }

        // 2. Re-filter the queue to drop the stop we just cleared
        applySequenceFilter();

        // Save the fact that the bus physically moved
        saveTelemetryState();

        int animDuration = 250;

        // 3. Slide cards up and fade out
        cardPreviousStop.animate().translationY(-40f).alpha(0f).setDuration(animDuration);
        cardCurrentStop.animate().translationY(-40f).alpha(0f).setDuration(animDuration);
        cardNextStop.animate().translationY(-40f).alpha(0f).setDuration(animDuration).withEndAction(() -> {

            // 4. Update Text Content mid-animation
            refreshUIState();

            // 5. Reset vertical offsets
            cardPreviousStop.setTranslationY(40f);
            cardCurrentStop.setTranslationY(40f);
            cardNextStop.setTranslationY(40f);

            // 6. Fade back in smoothly
            if (lastClearedStop != null) {
                cardPreviousStop.animate().translationY(0f).alpha(1f).setDuration(animDuration);
            }
            if (activeFilteredQueue.size() > 0) {
                cardCurrentStop.animate().translationY(0f).alpha(1f).setDuration(animDuration);
            }
            if (activeFilteredQueue.size() > 1) {
                cardNextStop.animate().translationY(0f).alpha(1f).setDuration(animDuration);
            }

            // Re-enable arrival button
            btnArrived.setText("ARRIVED!");
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (hubConnection != null && hubConnection.getConnectionState() == HubConnectionState.CONNECTED) {
            hubConnection.stop();
        }
    }
}