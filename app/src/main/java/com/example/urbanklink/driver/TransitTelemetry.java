package com.example.urbanklink.driver;

import android.Manifest;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.example.urbanklink.BuildConfig;
import com.example.urbanklink.database.DestStop;
import com.example.urbanklink.database.LocalRoute;
import com.example.urbanklink.R;
import com.example.urbanklink.database.Stop;
import com.example.urbanklink.network.UrbanLinkApiService;
import com.example.urbanklink.database.UrbanLinkDatabase;
import com.example.urbanklink.network.ApiClient;
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
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TransitTelemetry extends AppCompatActivity implements OnMapReadyCallback {

    private TransitViewModel viewModel;

    // UI Cards
    private MaterialCardView cardPreviousStop, cardCurrentStop, cardNextStop;
    private TextView txtPrevName, txtCurrentName, txtNextName;
    private MaterialButton btnArrived;

    // Map & Location
    private GoogleMap mMap;
    private FusedLocationProviderClient fusedLocationClient;
    private ActivityResultLauncher<String[]> locationPermissionRequest;

    private final HashMap<Integer, LatLng> stopCoordinateCache = new HashMap<>();
    private final List<com.google.android.gms.maps.model.Marker> activeStopMarkers = new ArrayList<>();
    private boolean isRouteDrawn = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_transit_telemetry);

        // 1. Initialize ViewModel
        viewModel = new ViewModelProvider(this).get(TransitViewModel.class);

        // 2. Setup Location & Permissions
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        locationPermissionRequest = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> {
                    Boolean fineGranted = result.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false);
                    Boolean coarseGranted = result.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false);
                    if ((fineGranted != null && fineGranted) || (coarseGranted != null && coarseGranted)) {
                        enableDriverLocation();
                    }
                }
        );
        checkLocationPermissions();

        // 3. Setup Navigation, Header, & Map
        SideNavUtils.setupSideNavigation(this, R.id.navTabStops);
        View rootView = findViewById(android.R.id.content);
        HeaderManager.loadHeaderData(this, rootView, null);

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.mapFragment);
        if (mapFragment != null) mapFragment.getMapAsync(this);

        // 4. Link UI
        initializeViews();
        setupListeners();
        setupObservers();

        // 5. Fire off the backend sync and socket connection!
        viewModel.initializeDataAndSockets();
    }

    private void checkLocationPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            locationPermissionRequest.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            });
        }
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;
        enableDriverLocation();
    }

    private void enableDriverLocation() {
        if (mMap == null) return;
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {

            mMap.setMyLocationEnabled(true);
            mMap.getUiSettings().setMyLocationButtonEnabled(true);

            LatLng gqeberhaDefault = new LatLng(-33.9608, 25.6022);
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(gqeberhaDefault, 15f));

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

        txtPrevName = findViewById(R.id.txtPrevName);
        txtCurrentName = findViewById(R.id.txtCurrentName);
        txtNextName = findViewById(R.id.txtNextName);

        btnArrived = cardCurrentStop.findViewById(R.id.btnArrived);
    }

    private void setupListeners() {
        btnArrived.setOnClickListener(v -> {
            btnArrived.setEnabled(false);
            btnArrived.setText("CLEARING...");
            cardCurrentStop.postDelayed(this::animateRouteProgression, 500);
        });
    }

    private void setupObservers() {
        // LINK: Draws the cyan polyline route once data syncs from Room
        viewModel.getActiveMapRoute().observe(this, route -> {
            if (route != null && route.encodedPolyline != null && mMap != null && !isRouteDrawn) {
                try {
                    String cleanPolyline = route.encodedPolyline.trim().replace("\n", "").replace("\r", "");
                    List<LatLng> decodedPath = PolyUtil.decode(cleanPolyline);
                    mMap.addPolyline(new PolylineOptions()
                            .addAll(decodedPath)
                            .color(android.graphics.Color.parseColor("#008899"))
                            .width(14f)
                            .geodesic(true));
                    isRouteDrawn = true;
                } catch (Exception e) {
                    android.util.Log.e("MAP", "Failed to decode polyline", e);
                }
            }
        });

        // LINK: Caches the GPS coordinates for all stops to enable instant pin dropping
        viewModel.getActiveRouteStops().observe(this, stops -> {
            if (stops != null) {
                for (Stop stop : stops) {
                    stopCoordinateCache.put(stop.getStopId(), new LatLng(stop.getLatitude(), stop.getLongitude()));
                }
                refreshMapPins(); // Draw initial pins once coordinates load
            }
        });

        // LINK: Updates the Previous Stop card
        viewModel.getLastClearedStop().observe(this, lastCleared -> {
            if (lastCleared != null) {
                cardPreviousStop.setVisibility(View.VISIBLE);
                txtPrevName.setText(lastCleared.getName() != null ? lastCleared.getName() : "");
            } else {
                cardPreviousStop.setVisibility(View.INVISIBLE);
            }
        });

        // LINK: Updates Current & Next cards, and re-draws map pins whenever queue changes
        viewModel.getActiveFilteredQueue().observe(this, filteredQueue -> {
            if (filteredQueue != null) {
                refreshUIState(filteredQueue);
                refreshMapPins(); // Wipe old pins and draw new ones
            }
        });

        viewModel.getErrorMessage().observe(this, error -> {
            if (error != null) Toast.makeText(this, error, Toast.LENGTH_SHORT).show();
        });
    }

    private void refreshUIState(List<DestStop> filteredQueue) {
        // Current Stop
        if (filteredQueue.size() > 0) {
            cardCurrentStop.setVisibility(View.VISIBLE);
            txtCurrentName.setText(filteredQueue.get(0).getName() != null ? filteredQueue.get(0).getName() : "");
            btnArrived.setEnabled(true);
            btnArrived.setText("ARRIVED!");
        } else {
            cardCurrentStop.setVisibility(View.INVISIBLE);
            txtCurrentName.setText("ROUTE COMPLETE");
            btnArrived.setEnabled(false);
        }

        // Next Stop
        if (filteredQueue.size() > 1) {
            cardNextStop.setVisibility(View.VISIBLE);
            txtNextName.setText(filteredQueue.get(1).getName() != null ? filteredQueue.get(1).getName() : "");
        } else {
            cardNextStop.setVisibility(View.INVISIBLE);
        }
    }

    private void refreshMapPins() {
        if (mMap == null || stopCoordinateCache.isEmpty()) return;

        // 1. Wipe old pins
        for (com.google.android.gms.maps.model.Marker marker : activeStopMarkers) {
            marker.remove();
        }
        activeStopMarkers.clear();

        // 2. Drop new pins for the upcoming queue
        List<DestStop> upcoming = viewModel.getActiveFilteredQueue().getValue();
        if (upcoming != null) {
            for (DestStop stop : upcoming) {
                LatLng cachedPos = stopCoordinateCache.get(stop.getStopId());
                if (cachedPos != null) {
                    com.google.android.gms.maps.model.Marker m = mMap.addMarker(new MarkerOptions()
                            .position(cachedPos)
                            .title(stop.getName())
                            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE)));
                    if (m != null) activeStopMarkers.add(m);
                }
            }
        }
    }

    private void animateRouteProgression() {
        int animDuration = 250;
        // 1. Slide cards up and fade out
        cardPreviousStop.animate().translationY(-40f).alpha(0f).setDuration(animDuration);
        cardCurrentStop.animate().translationY(-40f).alpha(0f).setDuration(animDuration);
        cardNextStop.animate().translationY(-40f).alpha(0f).setDuration(animDuration).withEndAction(() -> {

            // 2. Mid-animation, tell ViewModel to advance sequence.
            // The LiveData Observers will instantly update the text content underneath.
            viewModel.markArrivedAtCurrentStop();

            // 3. Reset vertical offsets
            cardPreviousStop.setTranslationY(40f);
            cardCurrentStop.setTranslationY(40f);
            cardNextStop.setTranslationY(40f);

            // 4. Fade back in
            cardPreviousStop.animate().translationY(0f).alpha(1f).setDuration(animDuration);
            cardCurrentStop.animate().translationY(0f).alpha(1f).setDuration(animDuration);
            cardNextStop.animate().translationY(0f).alpha(1f).setDuration(animDuration);
        });
    }
}