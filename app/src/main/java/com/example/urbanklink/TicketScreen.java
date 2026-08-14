package com.example.urbanklink;

import android.os.Bundle;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomnavigation.BottomNavigationView;
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

public class TicketScreen extends AppCompatActivity implements OnMapReadyCallback{

    private ImageView imgQrCode;
    private GoogleMap mMap; // NEW: The live map object
    private LatLng startStopCoord, destStopCoord; // Hold coords
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_ticket_screen);

        imgQrCode = findViewById(R.id.imgQrCode);
        ImageView btnBack = findViewById(R.id.btnBack);

        //btnBack.setOnClickListener(v -> finish());
        // ACTIVATE THE BACK BUTTON! (true = show the button)
        HeaderUtils.setupHeader(this, true, false);

        // 1. Grab the GUID String instead of the int
        String ticketRef = getIntent().getStringExtra("TICKET_REF");

        if (ticketRef != null && !ticketRef.isEmpty()) {
            generateQRCode(ticketRef);
        } else {
            // Offline path: User opened the app from the menu, check the local database
            loadActiveTicketFromDatabase();
            Toast.makeText(this, "You are offline (:", Toast.LENGTH_SHORT).show();
        }

        // 2. Initialize the Map Fragment
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.mapFragment);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this); // Tell Android to initialize map when ready
        }

        // 3. Load data (as discussed before, prefer offline path for consistency)
        loadActiveTicketFromDatabase();
    }

    // 4. Implement what happens when the map is finished loading
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        // Optional styling (makes the map dark to fit your theme)
        mMap.getUiSettings().setZoomControlsEnabled(true);
        mMap.getUiSettings().setMapToolbarEnabled(false); // Hide the standard navigation icons

        // If data loaded before map, plot it now
        if (startStopCoord != null && destStopCoord != null) {
            drawStopsOnMap();
        }

        // Set up your views
        BottomNavigationView bottomNav = findViewById(R.id.bottomNav);

        // Use the utility class, telling it that "Tickets" is the active tab
        NavigationUtils.setupBottomNavigation(this, bottomNav, R.id.nav_tickets);

    }

    private void drawStopsOnMap() {
        if (mMap == null || startStopCoord == null || destStopCoord == null) return;

        mMap.clear(); // Clear existing markers

        // 1. Plot Start (Boarding) stop in Cyan (#00FFFF)
        mMap.addMarker(new MarkerOptions()
                .position(startStopCoord)
                .title("Boarding Stop")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_CYAN)));

        // 2. Plot End (Offboarding) stop in default Red
        mMap.addMarker(new MarkerOptions()
                .position(destStopCoord)
                .title("Destination Stop"));

        // 3. AUTO-ZOOM to fit BOTH stops on the screen perfectly
        LatLngBounds.Builder builder = new LatLngBounds.Builder();
        builder.include(startStopCoord);
        builder.include(destStopCoord);
        LatLngBounds bounds = builder.build();

        int padding = 120; // in pixels, space between markers and edge
        mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, padding));
    }
    private void loadActiveTicketFromDatabase() {
        // Create a background thread so we don't freeze the app
        ExecutorService executor = Executors.newSingleThreadExecutor();

        executor.execute(() -> {
            UrbanLinkDatabase db = UrbanLinkDatabase.getInstance(TicketScreen.this);

            // 1. Ask Room for the latest unclaimed ticket
            LocalTicket activeTicket = db.ticketDao().getActiveTicket();

            if (activeTicket != null && activeTicket.ticketReference != null) {
                // 2. Fetch the actual Stop objects using the IDs saved in the ticket
                // (Make sure you added startStopId and destStopId to your LocalTicket class!)
                Stop startStop = db.stopDao().getStopById(activeTicket.startStopId);
                Stop destStop = db.stopDao().getStopById(activeTicket.destStopId);

                // 3. Hop back to the Main UI Thread to draw the graphics
                runOnUiThread(() -> {
                    // A. Draw the QR Code
                    generateQRCode(activeTicket.ticketReference);

                    // B. If we successfully found the stops, update the Map and UI
                    if (startStop != null && destStop != null) {
                        // Dynamically update the Current Leg card text
                        TextView txtCurrentLeg = findViewById(R.id.txtCurrentLeg);
                        txtCurrentLeg.setText(startStop.getName() + " to " + destStop.getName());

                        // Extract coordinates for Google Maps
                        startStopCoord = new LatLng(startStop.getLatitude(), startStop.getLongitude());
                        destStopCoord = new LatLng(destStop.getLatitude(), destStop.getLongitude());

                        // If the Google Map finished initializing before this data loaded, draw the pins now!
                        if (mMap != null) {
                            drawStopsOnMap();
                        }
                    }

                    Toast.makeText(TicketScreen.this, "Loaded offline ticket", Toast.LENGTH_SHORT).show();
                });
            } else {
                runOnUiThread(() -> {
                    Toast.makeText(TicketScreen.this, "No active tickets found.", Toast.LENGTH_LONG).show();
                    // Optional: You could show a placeholder image here instead of a blank square
                });
            }
        });
    }

    // 2. Change the parameter to accept a String
    private void generateQRCode(String ticketRef) {
        try {
            // The string the bus driver's scanner will read (e.g., URBANLINK-A1B2C3D4...)
            String ticketData = "URBANLINK-" + ticketRef.toUpperCase();

            BarcodeEncoder barcodeEncoder = new BarcodeEncoder();
            android.graphics.Bitmap bitmap = barcodeEncoder.encodeBitmap(ticketData, BarcodeFormat.QR_CODE, 600, 600);

            imgQrCode.setImageBitmap(bitmap);

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to generate QR Code", Toast.LENGTH_SHORT).show();
        }
    }
}