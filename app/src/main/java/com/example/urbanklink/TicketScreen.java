package com.example.urbanklink;

import android.os.Bundle;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.zxing.BarcodeFormat;
import com.journeyapps.barcodescanner.BarcodeEncoder;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TicketScreen extends AppCompatActivity {

    private ImageView imgQrCode;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_ticket_screen);

        imgQrCode = findViewById(R.id.imgQrCode);
        ImageView btnBack = findViewById(R.id.btnBack);

        btnBack.setOnClickListener(v -> finish());

        // 1. Grab the GUID String instead of the int
        String ticketRef = getIntent().getStringExtra("TICKET_REF");

        if (ticketRef != null && !ticketRef.isEmpty()) {
            generateQRCode(ticketRef);
        } else {
            // Offline path: User opened the app from the menu, check the local database
            loadActiveTicketFromDatabase();
            Toast.makeText(this, "You are offline (:", Toast.LENGTH_SHORT).show();
        }

        // Set up your views
        BottomNavigationView bottomNav = findViewById(R.id.bottomNav);

        // Use the utility class, telling it that "Tickets" is the active tab
        NavigationUtils.setupBottomNavigation(this, bottomNav, R.id.nav_tickets);

    }
    private void loadActiveTicketFromDatabase() {
        // Create a background thread so we don't freeze the app
        ExecutorService executor = Executors.newSingleThreadExecutor();

        executor.execute(() -> {
            // 1. Ask Room for the latest unclaimed ticket
            UrbanLinkDatabase db = UrbanLinkDatabase.getInstance(TicketScreen.this);
            LocalTicket activeTicket = db.ticketDao().getActiveTicket();

            // 2. Hop back to the Main UI Thread to draw the graphics
            runOnUiThread(() -> {
                if (activeTicket != null && activeTicket.ticketReference != null) {
                    generateQRCode(activeTicket.ticketReference);
                    Toast.makeText(TicketScreen.this, "Loaded offline ticket", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(TicketScreen.this, "No active tickets found.", Toast.LENGTH_LONG).show();
                    // Optional: You could show a placeholder image here instead of a blank square
                }
            });
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