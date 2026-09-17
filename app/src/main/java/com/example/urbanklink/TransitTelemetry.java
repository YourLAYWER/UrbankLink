package com.example.urbanklink;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
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
    private int activeBusId;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_transit_telemetry);

        // Inside TransitTelemetry.java, instead of a hardcoded "1":
        SharedPreferences prefs = getSharedPreferences("UrbanLinkPrefs", MODE_PRIVATE);
        int activeShiftId = prefs.getInt("ACTIVE_SHIFT_ID", -1);
        activeBusId = prefs.getInt("ACTIVE_BUS_ID", -1);
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

        // Bind text views directly from the activity's layout hierarchy
        txtPrevName = findViewById(R.id.txtPrevName);
        txtCurrentName = findViewById(R.id.txtCurrentName);
        txtNextName = findViewById(R.id.txtNextName);
        txtNextPax = findViewById(R.id.txtNextPax);

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
                    Log.e("UI_Crash_Debug", "Failed to update UI: " + e.getMessage(), e); // <-- Add this
                    Toast.makeText(this, "Error syncing route queue", Toast.LENGTH_SHORT).show();
                }
            });
        }, String.class);

        // Use the bus ID for SignalI group connection
        String busGroupKey = String.valueOf(activeBusId);
        // Start connection on a background thread
        new Thread(() -> {
            try {
                hubConnection.start().blockingAwait();
                // Correct method for calling a hub function without a return value
                hubConnection.send("JoinBusGroup", String.valueOf(activeBusId));
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Real-time sync offline", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

//    private void updateQueueData(List<DestStop> newServerQueue) {
//        upcomingStopsQueue.clear();
//        upcomingStopsQueue.addAll(newServerQueue);
//
//        // If current stop is empty, pull initial items
//        if (currentStop == null && !upcomingStopsQueue.isEmpty()) {
//            currentStop = upcomingStopsQueue.poll();
//        }
//        if (nextStop == null && !upcomingStopsQueue.isEmpty()) {
//            nextStop = upcomingStopsQueue.poll();
//        }
//
//        refreshUIState();
//    }

//    private void updateQueueData(List<DestStop> newServerQueue) {
//        if (newServerQueue == null || newServerQueue.isEmpty()) {
//            return;
//        }
//
//        // The server sends the complete ordered queue of pending stops.
//        // Let's map them safely to our 3 visual driver cards:
//
//        // 1. If we don't have a current stop yet, grab the first one from the server
//        if (currentStop == null) {
//            currentStop = newServerQueue.get(0);
//
//            if (newServerQueue.size() > 1) {
//                nextStop = newServerQueue.get(1);
//            }
//        } else {
//            // If we already have a current stop active, check if the server queue
//            // still contains our current stop or if we should sync up with the server's head
//            // (We preserve currentStop until the driver taps "ARRIVED!")
//
//            // Find where our currentStop is in the new server list, or update nextStop dynamically
//            boolean foundCurrent = false;
//            for (int i = 0; i < newServerQueue.size(); i++) {
//                DestStop stop = newServerQueue.get(i);
//                if (stop.getName().equals(currentStop.getName())) {
//                    foundCurrent = true;
//                    // Look ahead to see if there's a next stop in the fresh list
//                    if (i + 1 < newServerQueue.size()) {
//                        nextStop = newServerQueue.get(i + 1);
//                    } else {
//                        nextStop = null;
//                    }
//                    break;
//                }
//            }
//
//            // If the current stop was completely cleared or isn't in the list anymore, advance to head
//            if (!foundCurrent && !newServerQueue.isEmpty()) {
//                currentStop = newServerQueue.get(0);
//                nextStop = newServerQueue.size() > 1 ? newServerQueue.get(1) : null;
//            }
//        }
//
//        // Redraw the UI cards with the fresh synchronized data
//        runOnUiThread(this::refreshUIState);
//    }

    private void updateQueueData(List<DestStop> newServerQueue) {
        if (newServerQueue == null || newServerQueue.isEmpty()) {
            Log.d("SignalR_Test", "Received empty queue from server.");
            return;
        }

        Log.d("SignalR_Test", "Updating queue data with " + newServerQueue.size() + " stops.");

        // 1. If we don't have a current stop yet, set it to the first item safely
        if (currentStop == null) {
            currentStop = newServerQueue.get(0);
        }

        // 2. Find the index of our current stop in the fresh server list safely
        int currentIndex = -1;
        for (int i = 0; i < newServerQueue.size(); i++) {
            DestStop stop = newServerQueue.get(i);
            // Null-safe comparison using Objects.equals or checking for null first
            if (stop != null && stop.getName() != null && currentStop != null && stop.getName().equals(currentStop.getName())) {
                currentIndex = i;
                break;
            }
        }

        // 3. Update nextStop based on the index position
        if (currentIndex != -1 && currentIndex + 1 < newServerQueue.size()) {
            nextStop = newServerQueue.get(currentIndex + 1);
        } else if (currentIndex == -1 && !newServerQueue.isEmpty()) {
            // Fallback: take the head of the queue
            currentStop = newServerQueue.get(0);
            nextStop = newServerQueue.size() > 1 ? newServerQueue.get(1) : null;
        } else {
            nextStop = null;
        }

        // 4. Force UI refresh on the main thread immediately
        runOnUiThread(this::refreshUIState);
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