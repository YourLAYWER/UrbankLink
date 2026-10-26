package com.example.urbanklink.commuter;

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

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.example.urbanklink.database.LocalRoute;
import com.example.urbanklink.database.LocalTicket;
import com.example.urbanklink.R;
import com.example.urbanklink.response.RouteStatusResponse;
import com.example.urbanklink.database.Stop;
import com.example.urbanklink.network.UrbanLinkApiService;
import com.example.urbanklink.database.UrbanLinkDatabase;
import com.example.urbanklink.network.ApiClient;
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

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class TicketScreen extends AppCompatActivity implements OnMapReadyCallback {

    private TicketScreenViewModel viewModel;
    private GoogleMap mMap;
    private LatLng startStopCoord, destStopCoord, userCurrentCoord;
    private LocalRoute currentTicketRoute;
    private String currentTicketRef;
    private String currentTicketStatus = "ACTIVE";

    private ImageView imgQrCode;
    private TextView txtTicketStatus, txtCurrentLeg, txtArrivalTime, txtBusBadge, txtArrivalTitle;
    private FusedLocationProviderClient fusedLocationClient;
    private ActivityResultLauncher<String[]> locationPermissionRequest;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_ticket_screen);

        initializeUI();

        viewModel = new ViewModelProvider(this, ViewModelProvider.AndroidViewModelFactory.getInstance(getApplication())).get(TicketScreenViewModel.class);

        setupPermissionsAndLocation();
        setupObservers();

        currentTicketStatus = getIntent().getStringExtra("TICKET_STATUS");
        if (currentTicketStatus == null) currentTicketStatus = "ACTIVE";
        currentTicketRef = getIntent().getStringExtra("TICKET_REF");
        int historyRouteId = getIntent().getIntExtra("TICKET_ROUTE_ID", -1);
        String ticketRouteString = getIntent().getStringExtra("TICKET_ROUTE");

        if (ticketRouteString != null && txtCurrentLeg != null) {
            txtCurrentLeg.setText(ticketRouteString);
        }

        viewModel.initializeTicket(currentTicketRef, currentTicketStatus, historyRouteId);

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.mapFragment);
        if (mapFragment != null) mapFragment.getMapAsync(this);
    }

    private void initializeUI() {
        imgQrCode = findViewById(R.id.ic_qr);
        txtTicketStatus = findViewById(R.id.txtTicketStatus);
        txtCurrentLeg = findViewById(R.id.txtCurrentLeg);
        txtArrivalTime = findViewById(R.id.txtEstimated);
        txtBusBadge = findViewById(R.id.badgeBus);
        txtArrivalTitle = findViewById(R.id.txtArrivalTitle);
        HeaderUtils.setupHeader(this, true, false, true);
        BottomNavigationView bottomNav = findViewById(R.id.bottomNav);
        NavigationUtils.setupBottomNavigation(this, bottomNav, R.id.nav_tickets);
    }

    private void setupObservers() {
        viewModel.getQrCodeBitmap().observe(this, bitmap -> {
            if (bitmap != null) imgQrCode.setImageBitmap(bitmap);
            applyQrStatusStyling(currentTicketStatus);
        });

        viewModel.getActiveTicketData().observe(this, data -> {
            if (data == null) return;
            currentTicketRef = data.ticket.ticketReference;
            currentTicketRoute = data.route;
            startStopCoord = new LatLng(data.startStop.getLatitude(), data.startStop.getLongitude());
            destStopCoord = new LatLng(data.destStop.getLatitude(), data.destStop.getLongitude());

            txtCurrentLeg.setText(data.startStop.getName() + " to " + data.destStop.getName());
            txtArrivalTime.setText(data.ticket.estimatedArrivalTime);

            viewModel.refreshLiveStatus(currentTicketRef);
            if (mMap != null) renderTicketMap();
        });

        viewModel.getHistoricalRouteData().observe(this, route -> {
            currentTicketRoute = route;
            if (mMap != null) renderTicketMap();
        });

        viewModel.getLiveStatusData().observe(this, status -> {
            if ("COMPLETED".equalsIgnoreCase(status.getTicketStatus())) {
                applyQrStatusStyling("COMPLETED");
            }
            if (txtArrivalTitle != null) {
                if (status.isHasDelay() && status.getAlert() != null) {
                    txtBusBadge.setText("\uD83D\uDE8D" + status.getAlert().getBusFleetNumber());
                    txtArrivalTitle.setText("Bus is Delayed: " + status.getAlert().getMessage());
                    txtArrivalTitle.setTextColor(Color.parseColor("#FF5252"));
                } else {
                    txtArrivalTitle.setText("Bus is on Route.");
                    txtArrivalTitle.setTextColor(Color.parseColor("#E0FFFF"));
                }
            }
        });

        viewModel.getErrorMessage().observe(this, error -> {
            if (error != null) showErrorSnackbar(error);
        });
    }

    private void applyQrStatusStyling(String status) {
        String safeStatus = (status != null) ? status.toUpperCase() : "UNKNOWN";
        txtTicketStatus.setText(safeStatus);

        if (safeStatus.equals("EXPIRED")) {
            imgQrCode.setAlpha(0.3f);
            txtTicketStatus.setTextColor(Color.parseColor("#FF5252"));
            txtTicketStatus.setBackgroundColor(Color.parseColor("#1AFF5252"));
        } else if (safeStatus.equals("COMPLETED")) {
            imgQrCode.setAlpha(0.3f);
            txtTicketStatus.setTextColor(Color.parseColor("#9E9E9E"));
            txtTicketStatus.setBackgroundColor(Color.parseColor("#1A9E9E9E"));
        } else {
            imgQrCode.setAlpha(1.0f);
            txtTicketStatus.setTextColor(Color.parseColor("#00FF7F"));
            txtTicketStatus.setBackgroundColor(Color.parseColor("#1A00FF7F"));
        }
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        mMap.getUiSettings().setZoomControlsEnabled(true);
        mMap.getUiSettings().setMapToolbarEnabled(false);
        checkLocationPermissionAndStart();
        if (currentTicketRoute != null) renderTicketMap();
    }

    private void renderTicketMap() {
        if (mMap == null) return;
        mMap.clear();

        if (currentTicketRoute != null && currentTicketRoute.encodedPolyline != null) {
            List<LatLng> decodedPath = PolyUtil.decode(currentTicketRoute.encodedPolyline);
            int routeColor = ("Train".equalsIgnoreCase(currentTicketRoute.transportType) || "Rail".equalsIgnoreCase(currentTicketRoute.transportType))
                    ? Color.parseColor("#4CAF50") : Color.parseColor("#2196F3");

            mMap.addPolyline(new PolylineOptions().addAll(decodedPath).width(14f).color(routeColor).geodesic(true).zIndex(0));

            if (startStopCoord == null && destStopCoord == null) {
                LatLngBounds.Builder routeBuilder = new LatLngBounds.Builder();
                for (LatLng point : decodedPath) routeBuilder.include(point);
                mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(routeBuilder.build(), 120));
            }
        }

        if (startStopCoord != null && destStopCoord != null) {
            mMap.addMarker(new MarkerOptions().position(startStopCoord).title("Boarding Stop").icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_CYAN)));
            mMap.addMarker(new MarkerOptions().position(destStopCoord).title("Destination Stop"));

            LatLngBounds.Builder builder = new LatLngBounds.Builder().include(startStopCoord).include(destStopCoord);
            if (userCurrentCoord != null) builder.include(userCurrentCoord);
            mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), 120));
        }
    }

    // --- Broadcast Receivers and Lifecycle ---

    private BroadcastReceiver routeAlertReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if ("ACTIVE".equalsIgnoreCase(currentTicketStatus) && currentTicketRef != null) {
                viewModel.refreshLiveStatus(currentTicketRef);
                showErrorSnackbar("Route update! Fetching latest delay info...");
            }
        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        LocalBroadcastManager.getInstance(this).registerReceiver(routeAlertReceiver, new IntentFilter("FCM_ROUTE_STATUS_UPDATE"));
        if ("ACTIVE".equalsIgnoreCase(currentTicketStatus) && currentTicketRef != null) {
            viewModel.refreshLiveStatus(currentTicketRef);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(routeAlertReceiver);
    }

    // --- Location Permission Boilerplate ---

    private void setupPermissionsAndLocation() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        locationPermissionRequest = registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
            Boolean fineGranted = result.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false);
            Boolean coarseGranted = result.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false);
            if ((fineGranted != null && fineGranted) || (coarseGranted != null && coarseGranted)) enableUserLocationAndDrawPath();
        });
    }

    private void checkLocationPermissionAndStart() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            enableUserLocationAndDrawPath();
        } else {
            locationPermissionRequest.launch(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION});
        }
    }

    private void enableUserLocationAndDrawPath() {
        if (mMap == null) return;
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            mMap.setMyLocationEnabled(true);
            fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
                if (location != null) {
                    userCurrentCoord = new LatLng(location.getLatitude(), location.getLongitude());
                    renderTicketMap();
                }
            });
        }
    }

    private void showErrorSnackbar(String message) {
        View rootView = findViewById(android.R.id.content);
        Snackbar snackbar = Snackbar.make(rootView, message, Snackbar.LENGTH_LONG);
        snackbar.getView().setBackgroundColor(Color.parseColor("#FF4C4C"));
        snackbar.setTextColor(Color.WHITE);
        snackbar.show();
    }
}