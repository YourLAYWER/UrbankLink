package com.example.urbanklink;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.urbanklink.BuildConfig;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.microsoft.signalr.HubConnection;
import com.microsoft.signalr.HubConnectionBuilder;
import com.microsoft.signalr.HubConnectionState;

import java.lang.reflect.Type;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

public class TransitTelemetry extends AppCompatActivity {

    // UI Cards mapped from your XML
    private MaterialCardView cardPreviousStop, cardCurrentStop, cardNextStop;
    private TextView txtPrevName, txtCurrentName, txtNextName, txtNextPax;
    private MaterialButton btnArrived;

    // Real-time Queue state
    private final Queue<DestStop> upcomingStopsQueue = new LinkedList<>();
    private DestStop previousStop, currentStop, nextStop;

    // SignalR Hub Connection
    private HubConnection hubConnection;
    private final String currentShiftId = "1"; // Replace with dynamically resolved shift ID from login token
    private final Gson gson = new Gson();
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_transit_telemetry);

        // Inside TransitTelemetry.java, instead of a hardcoded "1":
        SharedPreferences prefs = getSharedPreferences("UrbanLinkPrefs", MODE_PRIVATE);
        int activeShiftId = prefs.getInt("ACTIVE_SHIFT_ID", -1);
        String currentShiftId = String.valueOf(activeShiftId);

        initializeViews();
        setupListeners();
        connectSignalR();
    }

    private void initializeViews() {
        cardPreviousStop = findViewById(R.id.cardPreviousStop);
        cardCurrentStop = findViewById(R.id.cardCurrentStop);
        cardNextStop = findViewById(R.id.cardNextStop);

        // Note: You can add specific IDs to the text views inside your cards in XML to reference them directly.
        // For demonstration, assuming text views are accessed or updated via container logic.
        //btnArrived = findViewById(R.id.btnSOS); // Or map to your "ARRIVED!" button inside cardCurrentStop

        // Bind card text views
        txtPrevName = cardPreviousStop.findViewById(R.id.txtPrevName);
        txtCurrentName = cardCurrentStop.findViewById(R.id.txtCurrentName);
        txtNextName = cardNextStop.findViewById(R.id.txtNextName);
        txtNextPax = cardNextStop.findViewById(R.id.txtNextPax);

        // Bind the actual ARRIVED button inside the current stop card
        btnArrived = cardCurrentStop.findViewById(R.id.btnArrived);
    }

    private void setupListeners() {
        // Driver taps "ARRIVED!" to manually progress or trigger the 10-second timer cycle
        btnArrived.setOnClickListener(v -> {
            btnArrived.setEnabled(false);
            btnArrived.setText("CLEARING...");

            // Trigger animation and data shift after clearing
            cardCurrentStop.postDelayed(this::animateRouteProgression, 1000);
        });
    }

    private void connectSignalR() {
        // Automatically switches between localhost (debug) and university server (release) via BuildConfig
        String hubUrl = BuildConfig.BASE_URL + "transithub";

        hubConnection = HubConnectionBuilder.create(hubUrl).build();

        // Listen for the distributed observer event pushed from the backend
        hubConnection.on("QueueUpdated", (queueJson) -> {
            runOnUiThread(() -> {
                try {
                    Type listType = new TypeToken<List<DestStop>>(){}.getType();
                    List<DestStop> freshQueue = gson.fromJson(queueJson, listType);

                    updateQueueData(freshQueue);
                } catch (Exception e) {
                    Toast.makeText(this, "Error syncing route queue", Toast.LENGTH_SHORT).show();
                }
            });
        }, String.class);

        // Start connection on a background thread
        new Thread(() -> {
            try {
                hubConnection.start().blockingAwait();
                // Correct method for calling a hub function without a return value
                hubConnection.send("JoinShiftGroup", currentShiftId);
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Real-time sync offline", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void updateQueueData(List<DestStop> newServerQueue) {
        upcomingStopsQueue.clear();
        upcomingStopsQueue.addAll(newServerQueue);

        // If current stop is empty, pull initial items
        if (currentStop == null && !upcomingStopsQueue.isEmpty()) {
            currentStop = upcomingStopsQueue.poll();
        }
        if (nextStop == null && !upcomingStopsQueue.isEmpty()) {
            nextStop = upcomingStopsQueue.poll();
        }

        refreshUIState();
    }

    private void animateRouteProgression() {
        // 1. Shift data states forward
        previousStop = currentStop;
        currentStop = nextStop;
        nextStop = upcomingStopsQueue.poll(); // Pulls the next item from the backend queue

        int animDuration = 250;

        // 2. Slide cards up and fade out
        cardPreviousStop.animate().translationY(-40f).alpha(0f).setDuration(animDuration);
        cardCurrentStop.animate().translationY(-40f).alpha(0f).setDuration(animDuration);
        cardNextStop.animate().translationY(-40f).alpha(0f).setDuration(animDuration).withEndAction(() -> {

            // 3. Update Text Content mid-animation
            refreshUIState();

            // 4. Reset vertical offsets below center
            cardPreviousStop.setTranslationY(40f);
            cardCurrentStop.setTranslationY(40f);
            cardNextStop.setTranslationY(40f);

            // 5. Fade back in smoothly
            cardPreviousStop.animate().translationY(0f).alpha(1f).setDuration(animDuration);
            cardCurrentStop.animate().translationY(0f).alpha(1f).setDuration(animDuration);

            if (nextStop != null) {
                cardNextStop.setVisibility(View.VISIBLE);
                cardNextStop.animate().translationY(0f).alpha(1f).setDuration(animDuration);
            } else {
                cardNextStop.setVisibility(View.INVISIBLE);
            }

            // Re-enable arrival button
            btnArrived.setEnabled(true);
            btnArrived.setText("ARRIVED!");
        });
    }

    private void refreshUIState() {
        // 1. Previous Stop
        if (previousStop != null) {
            cardPreviousStop.setVisibility(View.VISIBLE);
            txtPrevName.setText(previousStop.getName());
        } else {
            cardPreviousStop.setVisibility(View.INVISIBLE);
        }

        // 2. Current Target Stop
        if (currentStop != null) {
            cardCurrentStop.setVisibility(View.VISIBLE);
            txtCurrentName.setText(currentStop.getName());
        } else {
            cardCurrentStop.setVisibility(View.INVISIBLE);
            txtCurrentName.setText("ROUTE COMPLETE");
        }

        // 3. Subsequent Stop
        if (nextStop != null) {
            cardNextStop.setVisibility(View.VISIBLE);
            txtNextName.setText(nextStop.getName());
            txtNextPax.setText("EXPECTED: ~" + nextStop.getPassengersDroppingOff() + " PAX");
        } else {
            cardNextStop.setVisibility(View.INVISIBLE);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (hubConnection != null && hubConnection.getConnectionState() == HubConnectionState.CONNECTED) {
            hubConnection.stop();
        }
    }
}