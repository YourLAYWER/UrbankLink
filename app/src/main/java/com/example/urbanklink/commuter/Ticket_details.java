package com.example.urbanklink.commuter;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.urbanklink.requests.BookTicketRequest;
import com.example.urbanklink.response.JourneyResponse;
import com.example.urbanklink.database.LocalTicket;
import com.example.urbanklink.R;
import com.example.urbanklink.requests.RouteCalculateRequest;
import com.example.urbanklink.response.RouteCalculateResponse;
import com.example.urbanklink.RouteTimelineAdapter;
import com.example.urbanklink.network.UrbanLinkApiService;
import com.example.urbanklink.database.UrbanLinkDatabase;
import com.example.urbanklink.network.ApiClient;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.firebase.messaging.FirebaseMessaging;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class Ticket_details extends AppCompatActivity {

    private TicketViewModel viewModel;
    private int startId = -1;
    private int destId = -1;
    private double currentFinalCost = 0.0;

    // UI Elements
    private TextView txtTotalTime, txtTotalDue;
    private MaterialButton btnConfirmPay;
    private RecyclerView rvTimeline;
    private RouteTimelineAdapter timelineAdapter;

    // Payment UI Toggles (You will need to add these to your XML)
    // private RadioButton radioProviderCard, radioDebitCard;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_ticket_details);

        // 1. Initialize ViewModel via Custom Factory (Same as MainActivity)
        ViewModelProvider.Factory factory = new ViewModelProvider.Factory() {
            @NonNull
            @Override
            public <T extends androidx.lifecycle.ViewModel> T create(@NonNull Class<T> modelClass) {
                return (T) new TicketViewModel(getApplication());
            }
        };
        viewModel = new ViewModelProvider(this, factory).get(TicketViewModel.class);

        initializeUI();
        HeaderUtils.setupHeader(this, true, false, true);

        // 2. Extract Intent Data
        startId = getIntent().getIntExtra("START_ID", -1);
        destId = getIntent().getIntExtra("DEST_ID", -1);

        if (startId != -1 && destId != -1) {
            viewModel.loadRouteDetails(startId, destId);
        } else {
            showErrorSnackbar("Error: Invalid Stops selected.");
            finish();
        }

        // 3. Setup Observers
        setupObservers();

        // 4. Handle Payment Button Click
        btnConfirmPay.setOnClickListener(v -> {
            viewModel.executePayment(this, startId, destId, currentFinalCost);
        });

        /* 5. Handle Strategy Switching (Example if you add RadioButtons)
        radioProviderCard.setOnCheckedChangeListener((btn, isChecked) -> {
            if (isChecked) viewModel.setPaymentStrategy(new ProviderCardStrategy());
        });
        radioDebitCard.setOnCheckedChangeListener((btn, isChecked) -> {
            if (isChecked) viewModel.setPaymentStrategy(new DebitCardStrategy());
        });
        */
    }

    private void initializeUI() {
        txtTotalTime = findViewById(R.id.txtTotalTime);
        txtTotalDue = findViewById(R.id.txtTotalDue);
        btnConfirmPay = findViewById(R.id.btnConfirmPay);

        rvTimeline = findViewById(R.id.rvTimeline);
        rvTimeline.setLayoutManager(new LinearLayoutManager(this));
        timelineAdapter = new RouteTimelineAdapter();
        rvTimeline.setAdapter(timelineAdapter);

        // Remove txtBaseFare and switchStudentPass from your XML file as they are no longer needed
    }

    /**
     * Binds the UI to the ViewModel's data streams.
     */
    private void setupObservers() {
        // Observe Route Details (Populates the timeline and price)
        viewModel.getRouteDetails().observe(this, data -> {
            if (data != null) {
                currentFinalCost = data.getBaseCost(); // Backend now handles all discounts natively

                String formattedPrice = String.format("R%.2f", currentFinalCost);
                txtTotalDue.setText(formattedPrice);
                btnConfirmPay.setText("Confirm & Pay " + formattedPrice);
                txtTotalTime.setText("Total Time: " + data.getEstimatedDurationMinutes() + " mins");

                if (data.getSegments() != null) {
                    timelineAdapter.setSegments(data.getSegments());
                }
            }
        });

        // Observe Payment Loading State
        viewModel.getIsLoading().observe(this, isLoading -> {
            btnConfirmPay.setEnabled(!isLoading);
            if (isLoading) {
                btnConfirmPay.setText("Processing...");
            } else {
                btnConfirmPay.setText(String.format("Confirm & Pay R%.2f", currentFinalCost));
            }
        });

        // Observe Payment Success (Navigates to QR Screen)
        viewModel.getBookingSuccess().observe(this, data -> {
            if (data != null) {
                showSnackbar("Payment Successful!");
                Intent intent = new Intent(Ticket_details.this, TicketScreen.class);
                intent.putExtra("TICKET_REF", data.getTicketReference());
                intent.putExtra("TICKET_STATUS", data.getTicketStatus());
                intent.putIntegerArrayListExtra("ROUTE_IDS", new java.util.ArrayList<>(data.getRouteIds()));
                startActivity(intent);
                finish();
            }
        });

        // Observe Errors
//        viewModel.getErrorMessage().observe(this, error -> {
//            if (error != null) {
//                viewModel.getIsLoading().setValue(false); // Force stop loading
//                showErrorSnackbar(error);
//            }
//        });
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
}