package com.example.urbanklink;

import android.os.Bundle;
import android.view.View;
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

import org.w3c.dom.Text;

public class TicketScreen extends AppCompatActivity implements OnMapReadyCallback{

    private ImageView imgQrCode;
    private GoogleMap mMap; // The live map object
    private LatLng startStopCoord, destStopCoord; // Hold coords
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_ticket_screen);

        imgQrCode = findViewById(R.id.imgQrCode);
        ImageView btnBack = findViewById(R.id.btnBack);

        // ACTIVATE THE BACK BUTTON! (true = show the button)
        HeaderUtils.setupHeader(this, true, false);

        // 1. Grab the Extra Data from the Intent
        String ticketRef = getIntent().getStringExtra("TICKET_REF");
        String ticketStatus = getIntent().getStringExtra("TICKET_STATUS");
        String ticketRoute = getIntent().getStringExtra("TICKET_ROUTE"); // If you passed it from the adapter

        if (ticketRef != null && !ticketRef.isEmpty()) {

            // SCENARIO A: User clicked from History OR just bought a ticket
            if (ticketStatus == null) ticketStatus = "ACTIVE"; // Default for fresh purchases

            generateQRCode(ticketRef, ticketStatus);

            // Update the text at the top of the screen if we passed it
            if (ticketRoute != null) {
                TextView txtCurrentLeg = findViewById(R.id.txtCurrentLeg);
                if (txtCurrentLeg != null) txtCurrentLeg.setText(ticketRoute);
            }

            // Try to load this specific ticket's map coordinates from the DB
            loadSpecificTicketFromDatabase(ticketRef);

        } else {
            // SCENARIO B: Offline path (User opened the app from the menu)
            loadActiveTicketFromDatabase();
            Toast.makeText(this, "Loading offline ticket...", Toast.LENGTH_SHORT).show();
        }

        // 2. Initialize the Map Fragment
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.mapFragment);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        // REMOVED THE DUPLICATE `loadActiveTicketFromDatabase()` CALL HERE!
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        mMap.getUiSettings().setZoomControlsEnabled(true);
        mMap.getUiSettings().setMapToolbarEnabled(false);

        if (startStopCoord != null && destStopCoord != null) {
            drawStopsOnMap();
        }

        BottomNavigationView bottomNav = findViewById(R.id.bottomNav);
        NavigationUtils.setupBottomNavigation(this, bottomNav, R.id.nav_tickets);
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
        LatLngBounds bounds = builder.build();

        int padding = 120;
        mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, padding));
    }

    // NEW METHOD: Used for History and Fresh Purchases
    private void loadSpecificTicketFromDatabase(String specificTicketRef) {
        executor.execute(() -> {
            UrbanLinkDatabase db = UrbanLinkDatabase.getInstance(TicketScreen.this);

            // Assuming you have a way to fetch all tickets, we'll just try to find the active one
            // NOTE: If you have a specific method in your DAO like getTicketByRef(String), use that instead!
            LocalTicket ticket = db.ticketDao().getActiveTicket();

            // If the specific ticket is in our local DB, plot it!
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

    // ORIGINAL METHOD: Used for Offline Startup
    private void loadActiveTicketFromDatabase() {
        executor.execute(() -> {
            UrbanLinkDatabase db = UrbanLinkDatabase.getInstance(TicketScreen.this);
            LocalTicket activeTicket = db.ticketDao().getActiveTicket();

            if (activeTicket != null && activeTicket.ticketReference != null) {
                Stop startStop = db.stopDao().getStopById(activeTicket.startStopId);
                Stop destStop = db.stopDao().getStopById(activeTicket.destStopId);

                runOnUiThread(() -> {
                    // Generate QR as ACTIVE
                    generateQRCode(activeTicket.ticketReference, "ACTIVE");

                    if (startStop != null && destStop != null) {
                        TextView txtCurrentLeg = findViewById(R.id.txtCurrentLeg);
                        txtCurrentLeg.setText(startStop.getName() + " to " + destStop.getName());
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
                runOnUiThread(() -> {
                    Toast.makeText(TicketScreen.this, "No active tickets found.", Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    // UPDATED: Now takes status to dim the QR code
    private void generateQRCode(String ticketRef, String status) {
        try {
            String ticketData = "URBANLINK-" + ticketRef.toUpperCase();

            BarcodeEncoder barcodeEncoder = new BarcodeEncoder();
            android.graphics.Bitmap bitmap = barcodeEncoder.encodeBitmap(ticketData, BarcodeFormat.QR_CODE, 600, 600);

            imgQrCode.setImageBitmap(bitmap);

            // Visual Watermark: Dim the QR code if it is old so the driver knows instantly
//            if (status.equalsIgnoreCase("REDEEMED") || status.equalsIgnoreCase("EXPIRED")) {
//                imgQrCode.setAlpha(0.3f);
//            } else {
//                imgQrCode.setAlpha(1.0f); // Full brightness for active
//            }

            // Binding Badge
            TextView txtTicketStatus = findViewById(R.id.txtTicketStatus);

            // Ensure status isn't null to prevent crashes
            String safeStatus = (status != null) ? status.toUpperCase() : "UNKNOWN";
            txtTicketStatus.setText(safeStatus);

            // Dynamic UI Logic
            if (safeStatus.equals("EXPIRED")) {
                imgQrCode.setAlpha(0.3f);
                txtTicketStatus.setTextColor(android.graphics.Color.parseColor("#FF5252")); // Red text
                txtTicketStatus.setBackgroundColor(android.graphics.Color.parseColor("#1AFF5252")); // Light red bg

            } else if (safeStatus.equals("REDEEMED")) {
                imgQrCode.setAlpha(0.3f);
                txtTicketStatus.setTextColor(android.graphics.Color.parseColor("#9E9E9E")); // Grey text
                txtTicketStatus.setBackgroundColor(android.graphics.Color.parseColor("#1A9E9E9E")); // Light grey bg

            } else {
                // ACTIVE
                imgQrCode.setAlpha(1.0f);
                txtTicketStatus.setTextColor(android.graphics.Color.parseColor("#00FF7F")); // Green text
                txtTicketStatus.setBackgroundColor(android.graphics.Color.parseColor("#1A00FF7F")); // Light green bg
            }

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to generate QR Code", Toast.LENGTH_SHORT).show();
        }
    }
}