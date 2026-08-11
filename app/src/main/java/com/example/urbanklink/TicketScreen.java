package com.example.urbanklink;

import android.os.Bundle;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.zxing.BarcodeFormat;
import com.journeyapps.barcodescanner.BarcodeEncoder;

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
            Toast.makeText(this, "Ticket Reference missing!", Toast.LENGTH_SHORT).show();
        }

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