package com.example.urbanklink;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.maps.android.PolyUtil;
import com.google.zxing.BarcodeFormat;
import com.journeyapps.barcodescanner.BarcodeEncoder;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.widget.TextView;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MarkerOptions;

import org.json.JSONArray;
import org.json.JSONObject;
import org.w3c.dom.Text;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;


public class TicketScreen extends AppCompatActivity implements OnMapReadyCallback{

    private ImageView imgQrCode;
    private GoogleMap mMap; // The live map object
    private LatLng startStopCoord, destStopCoord; // Hold coords

    private LocalRoute currentTicketRoute;

    private LatLng userCurrentCoord;
    private Polyline userToStartPolyline;
    private FusedLocationProviderClient fusedLocationClient;
    private ActivityResultLauncher<String[]> locationPermissionRequest;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private String currentTicketStatus = "ACTIVE";

    private String currentTicketRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_ticket_screen);

        imgQrCode = findViewById(R.id.ic_qr);
        ImageView btnBack = findViewById(R.id.btnBack);

        HeaderUtils.setupHeader(this, true, false);

        // Initialize Location Client & Permission Launcher
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        locationPermissionRequest = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> {
                    Boolean fineGranted = result.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false);
                    Boolean coarseGranted = result.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false);

                    if ((fineGranted != null && fineGranted) || (coarseGranted != null && coarseGranted)) {
                        enableUserLocationAndDrawPath();
                    }
                }
        );

        // 1. Assign and immediately check for null before anything else
        currentTicketStatus = getIntent().getStringExtra("TICKET_STATUS");
        if (currentTicketStatus == null) {
            currentTicketStatus = "ACTIVE";
        }

        currentTicketRef = getIntent().getStringExtra("TICKET_REF");
        String ticketRoute = getIntent().getStringExtra("TICKET_ROUTE");
        int historyRouteId = getIntent().getIntExtra("TICKET_ROUTE_ID", -1);



        // 2. Use it safely in your existing logic
        if (currentTicketRef != null && !currentTicketRef.isEmpty()) {
            generateQRCode(currentTicketRef, currentTicketStatus);

            if (ticketRoute != null) {
                TextView txtCurrentLeg = findViewById(R.id.txtCurrentLeg);
                if (txtCurrentLeg != null) txtCurrentLeg.setText(ticketRoute);
            }

            if ("ACTIVE".equalsIgnoreCase(currentTicketStatus)) {
                // It's the active ticket! Grab stops and route from local Room DB
                loadSpecificTicketFromDatabase(currentTicketRef);
            } else {
                // It's a historical ticket from the API!
                // Grab JUST the route polyline using the ID you passed in the Intent
                loadHistoricalRoute(historyRouteId);
            }
        } else {
            loadActiveTicketFromDatabase();
            showSnackbar("Offline Ticket Loaded");
        }

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.mapFragment);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

    }

    private void loadHistoricalRoute(int routeId) {
        if (routeId == -1) return;

        executor.execute(() -> {
            UrbanLinkDatabase db = UrbanLinkDatabase.getInstance(TicketScreen.this);
            // Fetch the polyline string from the locally synced routes
            LocalRoute historyRoute = db.routeDao().getRouteById(routeId);

            runOnUiThread(() -> {
                currentTicketRoute = historyRoute;
                if (mMap != null) {
                    renderTicketMap();
                }
            });
        });
    }

    private void enableUserLocationAndDrawPath() {
        if (mMap == null) return;

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {

            mMap.setMyLocationEnabled(true);
            mMap.getUiSettings().setMyLocationButtonEnabled(true);

            fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
                if (location != null) {
                    userCurrentCoord = new LatLng(location.getLatitude(), location.getLongitude());
                    // Re-render the map to ensure the user's blue dot fits in the camera bounds
                    renderTicketMap();
                }
            });
        }
    }

    private void renderTicketMap() {
        if (mMap == null) return; // Only return if the map itself isn't ready

        mMap.clear();

        // 1. Draw the Route Polyline (Works for BOTH Active and History tickets)
        if (currentTicketRoute != null && currentTicketRoute.encodedPolyline != null && !currentTicketRoute.encodedPolyline.isEmpty()) {
            List<LatLng> decodedPath = PolyUtil.decode(currentTicketRoute.encodedPolyline);

            int routeColor = Color.parseColor("#2196F3");
            if ("Train".equalsIgnoreCase(currentTicketRoute.transportType) || "Rail".equalsIgnoreCase(currentTicketRoute.transportType)) {
                routeColor = Color.parseColor("#4CAF50");
            }

            mMap.addPolyline(new PolylineOptions()
                    .addAll(decodedPath)
                    .width(14f)
                    .color(routeColor)
                    .geodesic(true)
                    .zIndex(0));

            // For history tickets (where stops are null), zoom to fit the whole route
            if (startStopCoord == null && destStopCoord == null) {
                LatLngBounds.Builder routeBuilder = new LatLngBounds.Builder();
                for (LatLng point : decodedPath) {
                    routeBuilder.include(point);
                }
                mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(routeBuilder.build(), 120));
            }
        }

        // 2. Drop the Markers & Zoom (Only executes for Active tickets that have stops)
        if (startStopCoord != null && destStopCoord != null) {
            mMap.addMarker(new MarkerOptions()
                    .position(startStopCoord)
                    .title("Boarding Stop")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_CYAN)));

            mMap.addMarker(new MarkerOptions()
                    .position(destStopCoord)
                    .title("Destination Stop"));

            LatLngBounds.Builder builder = new LatLngBounds.Builder();
            builder.include(startStopCoord);
            builder.include(destStopCoord);

            if (userCurrentCoord != null) builder.include(userCurrentCoord);

            mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), 120));
        }
    }

    private void drawTicketRouteOnMap(LocalRoute route) {
        if (mMap == null || route == null || route.encodedPolyline == null || route.encodedPolyline.isEmpty()) return;

        List<LatLng> decodedPath = PolyUtil.decode(route.encodedPolyline);

        int routeColor = Color.parseColor("#2196F3"); // Bus - Blue
        if ("Train".equalsIgnoreCase(route.transportType) || "Rail".equalsIgnoreCase(route.transportType)) {
            routeColor = Color.parseColor("#4CAF50"); // Train - Green
        }

        mMap.addPolyline(new PolylineOptions()
                .addAll(decodedPath)
                .width(14f) // Slightly thicker so it stands out on the ticket screen
                .color(routeColor)
                .geodesic(true)
                .zIndex(0)); // Maps markers always render above polylines by default
    }

    // ==========================================
    // SNACKBAR HELPER METHODS
    // ==========================================

    private void showSnackbar(String message) {
        View rootView = findViewById(android.R.id.content);
        Snackbar.make(rootView, message, Snackbar.LENGTH_LONG).show();
    }

    private void showErrorSnackbar(String message) {
        View rootView = findViewById(android.R.id.content);
        Snackbar snackbar = Snackbar.make(rootView, message, Snackbar.LENGTH_LONG);
        snackbar.getView().setBackgroundColor(Color.parseColor("#FF4C4C"));
        snackbar.setTextColor(Color.WHITE);
        snackbar.show();
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        mMap.getUiSettings().setZoomControlsEnabled(true);
        mMap.getUiSettings().setMapToolbarEnabled(false);

        checkLocationPermissionAndStart();

        if (startStopCoord != null && destStopCoord != null) {
            renderTicketMap();
        }

        BottomNavigationView bottomNav = findViewById(R.id.bottomNav);
        NavigationUtils.setupBottomNavigation(this, bottomNav, R.id.nav_tickets);
    }

    private void checkLocationPermissionAndStart() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            enableUserLocationAndDrawPath();
        } else {
            locationPermissionRequest.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            });
        }
    }

    private void loadSpecificTicketFromDatabase(String specificTicketRef) {
        executor.execute(() -> {
            UrbanLinkDatabase db = UrbanLinkDatabase.getInstance(TicketScreen.this);

            // 1. THE FIX: Fetch the exact ticket by its reference, NOT just "the active one"
            LocalTicket ticket = db.ticketDao().getTicketByRef(specificTicketRef);

            // 2. We no longer need to check if the references match, because the SQL query did it for us
            if (ticket != null) {
                Stop startStop = db.stopDao().getStopById(ticket.startStopId);
                Stop destStop = db.stopDao().getStopById(ticket.destStopId);
                LocalRoute ticketRoute = db.routeDao().getRouteById(ticket.routeId);

                runOnUiThread(() -> {
                    if (startStop != null && destStop != null) {
                        TextView txtCurrentLeg = findViewById(R.id.txtCurrentLeg);
                        TextView txtArrivalTime = findViewById(R.id.txtEstimated);
                        txtCurrentLeg.setText(startStop.getName() + " to " + destStop.getName());
                        txtArrivalTime.setText(ticket.estimatedArrivalTime);

                        startStopCoord = new LatLng(startStop.getLatitude(), startStop.getLongitude());
                        destStopCoord = new LatLng(destStop.getLatitude(), destStop.getLongitude());

                        // 3. Save the route globally and render the map
                        currentTicketRoute = ticketRoute;

                        if (mMap != null) {
                            renderTicketMap();
                        }
                    }
                });
            }
        });
    }

    private void loadActiveTicketFromDatabase() {
        executor.execute(() -> {
            UrbanLinkDatabase db = UrbanLinkDatabase.getInstance(TicketScreen.this);
            LocalTicket activeTicket = db.ticketDao().getActiveTicket();

            if (activeTicket != null && activeTicket.ticketReference != null) {
                Stop startStop = db.stopDao().getStopById(activeTicket.startStopId);
                Stop destStop = db.stopDao().getStopById(activeTicket.destStopId);

                LocalRoute ticketRoute = db.routeDao().getRouteById(activeTicket.routeId);

                runOnUiThread(() -> {
                    generateQRCode(activeTicket.ticketReference, "ACTIVE");

                    // Proactive fetch for default active ticket
                    fetchLiveRouteStatus(activeTicket.ticketReference);

                    if (startStop != null && destStop != null) {
                        TextView txtCurrentLeg = findViewById(R.id.txtCurrentLeg);
                        TextView txtArrivalTime = findViewById(R.id.txtEstimated);
                        txtCurrentLeg.setText(startStop.getName() + " to " + destStop.getName());
                        txtArrivalTime.setText(activeTicket.estimatedArrivalTime);

                        startStopCoord = new LatLng(startStop.getLatitude(), startStop.getLongitude());
                        destStopCoord = new LatLng(destStop.getLatitude(), destStop.getLongitude());

                        currentTicketRoute = ticketRoute; // Save it globally
                        if (mMap != null) {
                            renderTicketMap(); // Draw everything
                        }
                    }
                });
            } else {
                runOnUiThread(() -> showErrorSnackbar("No active tickets found."));
            }
        });
    }

    private void generateQRCode(String ticketRef, String status) {
        try {
            String ticketData = "URBANLINK-" + ticketRef.toUpperCase();

            BarcodeEncoder barcodeEncoder = new BarcodeEncoder();
            android.graphics.Bitmap bitmap = barcodeEncoder.encodeBitmap(ticketData, BarcodeFormat.QR_CODE, 600, 600);

            imgQrCode.setImageBitmap(bitmap);

            TextView txtTicketStatus = findViewById(R.id.txtTicketStatus);
            String safeStatus = (status != null) ? status.toUpperCase() : "UNKNOWN";
            txtTicketStatus.setText(safeStatus);

            if (safeStatus.equals("EXPIRED")) {
                imgQrCode.setAlpha(0.3f);
                txtTicketStatus.setTextColor(android.graphics.Color.parseColor("#FF5252"));
                txtTicketStatus.setBackgroundColor(android.graphics.Color.parseColor("#1AFF5252"));
            } else if (safeStatus.equals("COMPLETED")) {
                imgQrCode.setAlpha(0.3f);
                txtTicketStatus.setTextColor(android.graphics.Color.parseColor("#9E9E9E"));
                txtTicketStatus.setBackgroundColor(android.graphics.Color.parseColor("#1A9E9E9E"));
            } else {
                imgQrCode.setAlpha(1.0f);
                txtTicketStatus.setTextColor(android.graphics.Color.parseColor("#00FF7F"));
                txtTicketStatus.setBackgroundColor(android.graphics.Color.parseColor("#1A00FF7F"));
            }

        } catch (Exception e) {
            e.printStackTrace();
            showErrorSnackbar("Failed to generate QR Code");
        }
    }

    // Method below is for unsubscribing to Route notifications
    public void onTicketScanned(int routeId) {
        String topic = "route_" + routeId + "_alerts";
        FirebaseMessaging.getInstance().unsubscribeFromTopic(topic);
        // Proceed with updating local Room DB status to "Used"
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 1. Re-register the FCM receiver
        LocalBroadcastManager.getInstance(this).registerReceiver(
                routeAlertReceiver,
                new IntentFilter("FCM_ROUTE_STATUS_UPDATE")
        );

        // 2. NEW: Proactively sync with the server every time the app comes to the foreground
        if (currentTicketRef != null && !currentTicketRef.isEmpty()) {
            Log.w("FCM_DEBUG", "Screen resumed! Fetching latest state for: " + currentTicketRef);
            fetchLiveRouteStatus(currentTicketRef);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(routeAlertReceiver);
    }


    private BroadcastReceiver routeAlertReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Guard clause: Only fetch live updates if the ticket is ACTIVE
            if (!"ACTIVE".equalsIgnoreCase(currentTicketStatus)) {
                return;
            }

            // Ask the server for the status of this exact ticket
            if (currentTicketRef != null) {
                fetchLiveRouteStatus(currentTicketRef);
                showErrorSnackbar("Route update! Fetching latest delay info...");
            }
        }
    };


public void fetchLiveRouteStatus(String currentTicketRef) {

   // Log.w("FCM_DEBUG", "STARTING FETCH FOR TICKET: " + currentTicketRef);
    if (currentTicketRef == null || currentTicketRef.isEmpty()) return;

    // 1. Grab the JWT Token
    android.content.SharedPreferences prefs = getSharedPreferences("UrbanLinkPrefs", android.content.Context.MODE_PRIVATE);
    String token = prefs.getString("JWT_TOKEN", null);
    if (token == null) {
        Log.e("FCM_DEBUG", "No JWT Token found! User is not logged in.");
        return;
    }
    String authHeader = "Bearer " + token.replace("\"", "").trim();

    // 2. Make the API Call WITH the token
    UrbanLinkApiService apiService = ApiClient.getClient(null).create(UrbanLinkApiService.class);

    apiService.getTicketStatus(authHeader, currentTicketRef).enqueue(new Callback<RouteStatusResponse>() {
        @Override
        public void onResponse(Call<RouteStatusResponse> call, Response<RouteStatusResponse> response) {
            if (response.isSuccessful() && response.body() != null) {
                RouteStatusResponse status = response.body();

                // LAZY UNSUBSCRIBE
                if (status.getCompletedRouteIds() != null && !status.getCompletedRouteIds().isEmpty()) {
                    for (int routeId : status.getCompletedRouteIds()) {
                        String topic = "route_" + routeId + "_alerts";
                        FirebaseMessaging.getInstance().unsubscribeFromTopic(topic);
                    }
                }

                // AUTO-UPDATE UI
                if ("COMPLETED".equalsIgnoreCase(status.getTicketStatus())) {
                    TextView txtTicketStatus = findViewById(R.id.txtTicketStatus);
                    if(txtTicketStatus != null) {
                        txtTicketStatus.setText("COMPLETED");
                        txtTicketStatus.setTextColor(android.graphics.Color.parseColor("#9E9E9E"));
                        txtTicketStatus.setBackgroundColor(android.graphics.Color.parseColor("#1A9E9E9E"));
                    }
                    if(imgQrCode != null) imgQrCode.setAlpha(0.3f);
                }

                // HANDLE DELAYS
                TextView txtBusBadge = findViewById(R.id.badgeBus);
                TextView txtArrivalTitle = findViewById(R.id.txtArrivalTitle);
                if (txtArrivalTitle != null) {
                    if (status.isHasDelay() && status.getAlert() != null) {

                        String fleetNo = status.getAlert().getBusFleetNumber();
                        String incidentMsg = status.getAlert().getMessage();
                        String lincensePlate = status.getAlert().getBusLicensePlate();

                        txtBusBadge.setText("\uD83D\uDE8D" + fleetNo);
                        txtArrivalTitle.setText("Bus is Delayed: " + incidentMsg);
                        txtArrivalTitle.setTextColor(android.graphics.Color.parseColor("#FF5252"));
                    } else {
                        String fleetNo = status.getAlert().getBusFleetNumber();
                        txtBusBadge.setText("\uD83D\uDE8D" + fleetNo);
                        txtArrivalTitle.setText("Bus is on Route.");
                        txtArrivalTitle.setTextColor(android.graphics.Color.parseColor("#E0FFFF"));
                    }
                }
            } else {
                // CATCH HTTP ERRORS (401, 404, 500)
                Log.e("FCM_DEBUG", "API REJECTED! HTTP Code: " + response.code());
                try {
                    Log.e("FCM_DEBUG", "Error Body: " + response.errorBody().string());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        @Override
        public void onFailure(Call<RouteStatusResponse> call, Throwable t) {
            Log.e("FCM_DEBUG", "NETWORK FAILURE: " + t.getMessage());
        }
    });
}
}