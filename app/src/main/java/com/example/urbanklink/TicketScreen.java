package com.example.urbanklink;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.snackbar.Snackbar;
import com.google.zxing.BarcodeFormat;
import com.journeyapps.barcodescanner.BarcodeEncoder;

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

import org.w3c.dom.Text;

public class TicketScreen extends AppCompatActivity implements OnMapReadyCallback{

    private ImageView imgQrCode;
    private GoogleMap mMap; // The live map object
    private LatLng startStopCoord, destStopCoord; // Hold coords

    private LatLng userCurrentCoord;
    private Polyline userToStartPolyline;
    private FusedLocationProviderClient fusedLocationClient;
    private ActivityResultLauncher<String[]> locationPermissionRequest;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

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

        String ticketRef = getIntent().getStringExtra("TICKET_REF");
        String ticketStatus = getIntent().getStringExtra("TICKET_STATUS");
        String ticketRoute = getIntent().getStringExtra("TICKET_ROUTE");

        if (ticketRef != null && !ticketRef.isEmpty()) {
            if (ticketStatus == null) ticketStatus = "ACTIVE";
            generateQRCode(ticketRef, ticketStatus);

            if (ticketRoute != null) {
                TextView txtCurrentLeg = findViewById(R.id.txtCurrentLeg);
                if (txtCurrentLeg != null) txtCurrentLeg.setText(ticketRoute);
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

            if (userToStartPolyline != null) {
                userToStartPolyline.remove();
            }

            // Draw a path from the user's live position to the boarding stop
            userToStartPolyline = mMap.addPolyline(new PolylineOptions()
                    .add(userCurrentCoord, startStopCoord)
                    .width(10f)
                    .color(Color.parseColor("#00E5FF"))
                    .geodesic(true));
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
}