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

    private LatLng userCurrentCoord;
    private Polyline userToStartPolyline;
    private FusedLocationProviderClient fusedLocationClient;
    private ActivityResultLauncher<String[]> locationPermissionRequest;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private String currentTicketStatus = "ACTIVE";


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

//        String ticketRef = getIntent().getStringExtra("TICKET_REF");
//        String ticketStatus = getIntent().getStringExtra("TICKET_STATUS");
//        String ticketRoute = getIntent().getStringExtra("TICKET_ROUTE");

//        if (ticketRef != null && !ticketRef.isEmpty()) {
//            if (ticketStatus == null) ticketStatus = "ACTIVE";
//            generateQRCode(ticketRef, ticketStatus);
//
//            if (ticketRoute != null) {
//                TextView txtCurrentLeg = findViewById(R.id.txtCurrentLeg);
//                if (txtCurrentLeg != null) txtCurrentLeg.setText(ticketRoute);
//            }
//
//            loadSpecificTicketFromDatabase(ticketRef);
//        } else {
//            loadActiveTicketFromDatabase();
//            showSnackbar("Offline Ticket Loaded");
//        }

        // 1. Assign and immediately check for null before anything else
        currentTicketStatus = getIntent().getStringExtra("TICKET_STATUS");
        if (currentTicketStatus == null) {
            currentTicketStatus = "ACTIVE";
        }

        String ticketRef = getIntent().getStringExtra("TICKET_REF");
        String ticketRoute = getIntent().getStringExtra("TICKET_ROUTE");
        int historyRouteId = getIntent().getIntExtra("TICKET_ROUTE_ID", -1);



        // 2. Use it safely in your existing logic
        if (ticketRef != null && !ticketRef.isEmpty()) {
            generateQRCode(ticketRef, currentTicketStatus);

            if (ticketRoute != null) {
                TextView txtCurrentLeg = findViewById(R.id.txtCurrentLeg);
                if (txtCurrentLeg != null) txtCurrentLeg.setText(ticketRoute);
            }

            if (historyRouteId != -1 && "ACTIVE".equalsIgnoreCase(currentTicketStatus)) {
                fetchLiveRouteStatus(historyRouteId); // Proactive fetch from History
            }

            loadSpecificTicketFromDatabase(ticketRef);
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

    private void fetchWalkingRoute(LatLng origin, LatLng destination) {
        executor.execute(() -> {
            // Replace with your actual Google Maps API Key or retrieve from strings.xml / BuildConfig
            String apiKey = getString(R.string.google_maps_key);

            String cleanedUrl = "https://maps.googleapis.com/maps/api/directions/json"
                    + "?origin=" + origin.latitude + "," + origin.longitude
                    + "&destination=" + destination.latitude + "," + destination.longitude
                    + "&mode=walking"
                    + "&key=" + apiKey;

            OkHttpClient client = new OkHttpClient();
            Request request = new Request.Builder().url(cleanedUrl).build();

            try {
                okhttp3.Response response = client.newCall(request).execute();
                String bodyString = response.body() != null ? response.body().string() : "";
                Log.d("DIRECTIONS_API", "Code: " + response.code() + " | Body: " + bodyString);

                if (response.isSuccessful() && !bodyString.isEmpty()) {
                    JSONObject jsonObject = new JSONObject(bodyString);
                    String status = jsonObject.optString("status");

                    if ("OK".equals(status)) {
                        JSONArray routes = jsonObject.getJSONArray("routes");
                        if (routes.length() > 0) {
                            JSONObject route = routes.getJSONObject(0);
                            JSONObject overviewPolyline = route.getJSONObject("overview_polyline");
                            String encodedPoints = overviewPolyline.getString("points");

                            List<LatLng> decodedPath = PolyUtil.decode(encodedPoints);
                            runOnUiThread(() -> drawWalkingPathOnMap(decodedPath));
                        }
                    } else {
                        Log.e("DIRECTIONS_API", "API Error Status: " + status + " - " + jsonObject.optString("error_message"));
                    }
                }
            } catch (Exception e) {
                Log.e("DIRECTIONS_API", "Exception: " + e.getMessage(), e);
            }
        });
    }

    private void drawWalkingPathOnMap(List<LatLng> pathPoints) {
        if (mMap == null || pathPoints == null || pathPoints.isEmpty()) return;

        if (userToStartPolyline != null) {
            userToStartPolyline.remove();
        }

        userToStartPolyline = mMap.addPolyline(new PolylineOptions()
                .addAll(pathPoints)
                .width(12f)
                .color(Color.parseColor("#00E5FF"))
                .geodesic(false));
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
            drawStopsOnMap();
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

    private void enableUserLocationAndDrawPath() {
        if (mMap == null) return;

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {

            mMap.setMyLocationEnabled(true);
            mMap.getUiSettings().setMyLocationButtonEnabled(true);

            fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
                if (location != null) {
                    userCurrentCoord = new LatLng(location.getLatitude(), location.getLongitude());
                    drawStopsOnMap();
                }
            });
        }
    }

    private void drawStopsOnMap() {
        if (mMap == null || startStopCoord == null || destStopCoord == null) return;

        mMap.clear();

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

        if (userCurrentCoord != null) {
            builder.include(userCurrentCoord);
            // Request real road-snapped walking directions from Google
            fetchWalkingRoute(userCurrentCoord, startStopCoord);
        }

        LatLngBounds bounds = builder.build();
        int padding = 120;
        mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, padding));
    }

    private void loadSpecificTicketFromDatabase(String specificTicketRef) {
        executor.execute(() -> {
            UrbanLinkDatabase db = UrbanLinkDatabase.getInstance(TicketScreen.this);
            LocalTicket ticket = db.ticketDao().getActiveTicket();

            if (ticket != null && specificTicketRef.equals(ticket.ticketReference)) {
                Stop startStop = db.stopDao().getStopById(ticket.startStopId);
                Stop destStop = db.stopDao().getStopById(ticket.destStopId);

                runOnUiThread(() -> {
                    if (startStop != null && destStop != null) {
                        TextView txtCurrentLeg = findViewById(R.id.txtCurrentLeg);
                        TextView txtArrivalTime = findViewById(R.id.txtEstimated);
                        txtCurrentLeg.setText(startStop.getName() + " to " + destStop.getName());
                        txtArrivalTime.setText(ticket.estimatedArrivalTime);

                        startStopCoord = new LatLng(startStop.getLatitude(), startStop.getLongitude());
                        destStopCoord = new LatLng(destStop.getLatitude(), destStop.getLongitude());

                        if (mMap != null) {
                            drawStopsOnMap();
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

                runOnUiThread(() -> {
                    generateQRCode(activeTicket.ticketReference, "ACTIVE");

                    if (startStop != null && destStop != null) {
                        TextView txtCurrentLeg = findViewById(R.id.txtCurrentLeg);
                        TextView txtArrivalTime = findViewById(R.id.txtEstimated);
                        txtCurrentLeg.setText(startStop.getName() + " to " + destStop.getName());
                        txtArrivalTime.setText(activeTicket.estimatedArrivalTime);

                        startStopCoord = new LatLng(startStop.getLatitude(), startStop.getLongitude());
                        destStopCoord = new LatLng(destStop.getLatitude(), destStop.getLongitude());

                        if (mMap != null) {
                            drawStopsOnMap();
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
        LocalBroadcastManager.getInstance(this).registerReceiver(
                routeAlertReceiver,
                new IntentFilter("FCM_ROUTE_STATUS_UPDATE")
        );
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

            String routeIdStr = intent.getStringExtra("routeId");
            if (routeIdStr != null) {
                int routeId = Integer.parseInt(routeIdStr);
                fetchLiveRouteStatus(routeId);
                showErrorSnackbar("Route update! Fetching latest delay info...");
            }
        }
    };

    public void fetchLiveRouteStatus(int routeId) {
        // FIX 1: Reverted 'this' to 'null' to match your ApiClient's expected parameter
        UrbanLinkApiService apiService = ApiClient.getClient(null).create(UrbanLinkApiService.class);

        apiService.getRouteStatus(routeId).enqueue(new Callback<RouteStatusResponse>() {
            @Override
            public void onResponse(Call<RouteStatusResponse> call, Response<RouteStatusResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    RouteStatusResponse status = response.body();

                    TextView txtArrivalTitle = findViewById(R.id.txtArrivalTitle);

                    // FIX 2: Added a strict null check for the alert object before calling getMessage()
                    if (status.isHasDelay() && status.getAlert() != null) {

                        txtArrivalTitle.setText(status.getAlert().getMessage());
                        txtArrivalTitle.setTextColor(android.graphics.Color.parseColor("#FF5252"));
                        showErrorSnackbar("Route delayed. UI updated.");

                    } else {
                        txtArrivalTitle.setText("Bus is on Route.");
                        txtArrivalTitle.setTextColor(android.graphics.Color.parseColor("#E0FFFF"));
                    }
                }
            }

            @Override
            public void onFailure(Call<RouteStatusResponse> call, Throwable t) {
                Log.e("API_ERROR", "Failed to fetch live status: " + t.getMessage());
            }
        });
    }
}