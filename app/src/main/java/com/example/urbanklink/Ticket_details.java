package com.example.urbanklink;

import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.switchmaterial.SwitchMaterial;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class Ticket_details extends AppCompatActivity {

    private double currentBaseCost = 0.0;
    private final double STUDENT_DISCOUNT = 4.00;

    // UI Elements
    private TextView txtTotalTime, txtTotalDue;
    private MaterialButton btnConfirmPay;
    private SwitchMaterial switchStudentPass;

    // Timeline Elements
    private TextView time1, title1, sub1;
    private TextView time2, title2;
    private TextView time3, title3, sub3;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_ticket_details);

        initializeUI();

        // 1. Get the IDs from the Intent
        int startId = getIntent().getIntExtra("START_ID", -1);
        int destId = getIntent().getIntExtra("DEST_ID", -1);

        if (startId != -1 && destId != -1) {
            fetchRouteDetails(startId, destId);
        } else {
            Toast.makeText(this, "Error: Invalid stops selected", Toast.LENGTH_SHORT).show();
            finish();
        }

        // 2. Make the Back button work
        ImageView btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> finish());

        // 3. Make the Student Pass switch recalculate the price instantly
        switchStudentPass.setOnCheckedChangeListener((buttonView, isChecked) -> {
            updatePricingDisplay(isChecked);
        });
    }

    private void initializeUI() {
        txtTotalTime = findViewById(R.id.txtTotalTime);
        txtTotalDue = findViewById(R.id.txtTotalDue); // Ensure you added this ID to XML!
        btnConfirmPay = findViewById(R.id.btnConfirmPay);
        switchStudentPass = findViewById(R.id.switchStudentPass); // Ensure you added this ID to XML!

        time1 = findViewById(R.id.time1);
        title1 = findViewById(R.id.title1);
        sub1 = findViewById(R.id.sub1);

        time2 = findViewById(R.id.time2);
        title2 = findViewById(R.id.title2);

        time3 = findViewById(R.id.time3);
        title3 = findViewById(R.id.title3);
        sub3 = findViewById(R.id.sub3);
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
                    txtTotalTime.setText("Total Time: " + data.getEstimatedDurationMinutes() + " mins");

                    // Trigger the UI price update based on the default switch state
                    updatePricingDisplay(switchStudentPass.isChecked());

                    // Populate Timeline (Assuming exactly 3 segments for this specific XML layout)
                    if (data.getSegments().size() >= 3) {
                        RouteSegmentDto seg1 = data.getSegments().get(0);
                        time1.setText(seg1.getArrivalTime());
                        title1.setText(seg1.getStopName());
                        sub1.setText(seg1.getTransitLine());

                        RouteSegmentDto seg2 = data.getSegments().get(1);
                        time2.setText(seg2.getArrivalTime());
                        title2.setText(seg2.getStopName());

                        RouteSegmentDto seg3 = data.getSegments().get(2);
                        time3.setText(seg3.getArrivalTime());
                        title3.setText(seg3.getStopName());
                        sub3.setText(seg3.getTransitLine());
                    }
                } else {
                    Toast.makeText(Ticket_details.this, "Failed to calculate route.", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call call, Throwable t) {
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
}