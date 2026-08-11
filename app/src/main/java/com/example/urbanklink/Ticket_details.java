package com.example.urbanklink;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.switchmaterial.SwitchMaterial;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class Ticket_details extends AppCompatActivity {

    private double currentBaseCost = 0.0;
    private double currentFinalCost = 0.0; // NEW: Tracks the exact amount to charge
    private final double STUDENT_DISCOUNT = 4.00;

    // NEW: Save these globally so the button can access them
    private int startId = -1;
    private int destId = -1;
    // UI Elements
    private TextView txtTotalTime, txtTotalDue, txtBaseFare;
    private MaterialButton btnConfirmPay;
    private SwitchMaterial switchStudentPass;

    // NEW: Timeline Elements (Replaced all the hardcoded time1, title1, etc.)
    private RecyclerView rvTimeline;
    private RouteTimelineAdapter timelineAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_ticket_details);

        initializeUI();

        // 1. Get the IDs from the Intent
        startId = getIntent().getIntExtra("START_ID", -1);
        destId = getIntent().getIntExtra("DEST_ID", -1);

        if (startId != -1 && destId != -1) {
            fetchRouteDetails(startId, destId);
        } else {
            Toast.makeText(this, "Error: Invalid stops selected", Toast.LENGTH_SHORT).show();
            finish();
        }

        // 2. Make the Back button work
        ImageView btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> finish());

        // NEW: Wire up the Pay button
        btnConfirmPay.setOnClickListener(v -> processPayment());

        // 3. Make the Student Pass switch recalculate the price instantly
        switchStudentPass.setOnCheckedChangeListener((buttonView, isChecked) -> {
            updatePricingDisplay(isChecked);
        });
    }

    private void initializeUI() {
        txtTotalTime = findViewById(R.id.txtTotalTime);
        txtTotalDue = findViewById(R.id.txtTotalDue);
        btnConfirmPay = findViewById(R.id.btnConfirmPay);
        txtBaseFare = findViewById(R.id.txtBaseFare);
        switchStudentPass = findViewById(R.id.switchStudentPass);

        // NEW: Setup the RecyclerView
        rvTimeline = findViewById(R.id.rvTimeline);
        rvTimeline.setLayoutManager(new LinearLayoutManager(this));
        timelineAdapter = new RouteTimelineAdapter();
        rvTimeline.setAdapter(timelineAdapter);
    }

    private void fetchRouteDetails(int start, int end) {
        UrbanLinkApiService apiService = ApiClient.getClient(null).create(UrbanLinkApiService.class);
        RouteCalculateRequest request = new RouteCalculateRequest(start, end);

        apiService.calculateRoute(request).enqueue(new Callback<RouteCalculateResponse>() {
            @Override
            public void onResponse(Call<RouteCalculateResponse> call, Response<RouteCalculateResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    RouteCalculateResponse data = response.body();

                    // Populate Cost & Time
                    currentBaseCost = data.getBaseCost();
                    txtBaseFare.setText(String.format("R%.2f", currentBaseCost));
                    txtTotalTime.setText("Total Time: " + data.getEstimatedDurationMinutes() + " mins");

                    // Trigger the UI price update based on the default switch state
                    updatePricingDisplay(switchStudentPass.isChecked());

                    // NEW: Pass the list of segments directly to the RecyclerView adapter
                    if (data.getSegments() != null) {
                        timelineAdapter.setSegments(data.getSegments());
                    }
                } else {
                    Toast.makeText(Ticket_details.this, "Failed to calculate route.", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<RouteCalculateResponse> call, Throwable t) { // Fixed generic type here
                Toast.makeText(Ticket_details.this, "Network error", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updatePricingDisplay(boolean isStudentPassActive) {
        double finalPrice = currentBaseCost;
        if (isStudentPassActive) {
            finalPrice -= STUDENT_DISCOUNT;
        }

        // Prevent negative prices
        if (finalPrice < 0) finalPrice = 0;

        String formattedPrice = String.format("R%.2f", finalPrice);

        txtTotalDue.setText(formattedPrice);
        btnConfirmPay.setText("Confirm & Pay " + formattedPrice);
    }

    // NEW: The method that executes the purchase
    private void processPayment() {
        // 1. Disable button so user doesn't double-click and get charged twice
        btnConfirmPay.setEnabled(false);
        btnConfirmPay.setText("Processing...");

        // 2. Grab the JWT Token
        SharedPreferences prefs = getSharedPreferences("UrbanLinkPrefs", Context.MODE_PRIVATE);
        String token = prefs.getString("JWT_TOKEN", null);

        if (token == null) {
            Toast.makeText(this, "Authentication error. Please log in again.", Toast.LENGTH_LONG).show();
            btnConfirmPay.setEnabled(true);
            return;
        }

        String authHeader = "Bearer " + token.replace("\"", "").trim();

        // 3. Build the Request
//        BookTicketRequest request = new BookTicketRequest(
//                startId,
//                destId,
//                currentFinalCost,
//                switchStudentPass.isChecked()
//        );

        // 3. Build the Request
        BookTicketRequest request = new BookTicketRequest(startId,destId);

        // 4. Send to C#
        UrbanLinkApiService apiService = ApiClient.getClient(null).create(UrbanLinkApiService.class);

        apiService.bookJourney(authHeader, request).enqueue(new Callback<JourneyResponse>() {
            @Override
            public void onResponse(Call<JourneyResponse> call, Response<JourneyResponse> response) {
                btnConfirmPay.setEnabled(true); // Re-enable button

                if (response.isSuccessful() && response.body() != null) {
                    // SUCCESS!
                    Toast.makeText(Ticket_details.this, "Payment Successful!", Toast.LENGTH_LONG).show();
                    Intent intent = new Intent(Ticket_details.this, TicketScreen.class);
                    intent.putExtra("TICKET_REF", response.body().getTicketReference());
                    startActivity(intent);
                    // You would typically navigate to a "Ticket QR Code" screen here
                    finish();

                } else {
                    Toast.makeText(Ticket_details.this, "Payment failed. Please try again.", Toast.LENGTH_LONG).show();
                    try {
                        // Grab the exact error message the C# server sent back
                        String serverError = response.errorBody() != null ? response.errorBody().string() : "No error message";

                        // Print it to the Logcat
                        android.util.Log.e("PAYMENT_ERROR", "HTTP Code: " + response.code() + " | Reason: " + serverError);

                        // Show the HTTP code on the screen
                        Toast.makeText(Ticket_details.this, "Failed! Code: " + response.code(), Toast.LENGTH_LONG).show();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }

            @Override
            public void onFailure(Call<JourneyResponse> call, Throwable t) {
                btnConfirmPay.setEnabled(true);
                Toast.makeText(Ticket_details.this, "Network error during checkout.", Toast.LENGTH_SHORT).show();
            }
        });
    }
}