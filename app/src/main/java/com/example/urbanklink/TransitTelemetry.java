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
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

public class TransitTelemetry extends AppCompatActivity {

    // UI Cards mapped from your XML
    private MaterialCardView cardPreviousStop, cardCurrentStop, cardNextStop;
    private TextView txtPrevName, txtCurrentName, txtNextName, txtNextPax;
    private MaterialButton btnArrived;

    // Real-time Queue state
    private List<DestStop> cachedServerQueue = new ArrayList<>();
    private DestStop previousStop, currentStop, nextStop;

    private int currentQueueIndex = 0;

    // SignalR Hub Connection
    private HubConnection hubConnection;
    private final Gson gson = new Gson();
    private int activeBusId;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_transit_telemetry);

        SideNavUtils.setupSideNavigation(this, R.id.navTabStops);

        // Inside TransitTelemetry.java, instead of a hardcoded "1":
        SharedPreferences prefs = getSharedPreferences("UrbanLinkPrefs", MODE_PRIVATE);
        int activeShiftId = prefs.getInt("ACTIVE_SHIFT_ID", -1);
        activeBusId = prefs.getInt("ACTIVE_BUS_ID", -1);

        // Debug log to verify if preferences are loading correctly
        Log.d("BusDebug", "Active Shift ID: " + activeShiftId + ", Active Bus ID: " + activeBusId);

        initializeViews();
        setupListeners();
        connectSignalR();
    }

    private void initializeViews() {
        cardPreviousStop = findViewById(R.id.cardPreviousStop);
        cardCurrentStop = findViewById(R.id.cardCurrentStop);
        cardNextStop = findViewById(R.id.cardNextStop);

        // Bind text views using root findViewById so they are never null
        txtPrevName = findViewById(R.id.txtPrevName);
        txtCurrentName = findViewById(R.id.txtCurrentName);
        txtNextName = findViewById(R.id.txtNextName);
        txtNextPax = findViewById(R.id.txtNextPax);

        // Bind the ARRIVED button inside the current stop card container
        btnArrived = cardCurrentStop.findViewById(R.id.btnArrived);
    }

    private void setupListeners() {
        // Driver taps "ARRIVED!" to progress to the next stop in sequence
        btnArrived.setOnClickListener(v -> {
            btnArrived.setEnabled(false);
            btnArrived.setText("CLEARING...");

            // Trigger animation and data shift
            cardCurrentStop.postDelayed(this::animateRouteProgression, 500);
        });
    }

    private void connectSignalR() {
        String hubUrl = BuildConfig.BASE_URL + "transithub";
        hubConnection = HubConnectionBuilder.create(hubUrl).build();

        // Listen for live queue broadcasts from the backend
        hubConnection.on("QueueUpdated", (queueJson) -> {
            runOnUiThread(() -> {
                try {
                    Type listType = new TypeToken<List<DestStop>>(){}.getType();
                    List<DestStop> freshQueue = gson.fromJson(queueJson, listType);

                    updateQueueData(freshQueue);
                } catch (Exception e) {
                    Log.e("SignalR_Error", "Failed to parse queue: " + e.getMessage(), e);
                    Toast.makeText(this, "Error syncing route queue", Toast.LENGTH_SHORT).show();
                }
            });
        }, String.class);

        new Thread(() -> {
            try {
                hubConnection.start().blockingAwait();
                hubConnection.send("JoinBusGroup", String.valueOf(activeBusId));
                Log.d("SignalR_Test", "Connected and joined group for bus: " + activeBusId);
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Real-time sync offline", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void updateQueueData(List<DestStop> newServerQueue) {
        if (newServerQueue == null || newServerQueue.isEmpty()) {
            return;
        }

        // 1. Cache the fresh server list
        cachedServerQueue = newServerQueue;

        // 2. Re-align position or initialize if first load
        if (txtCurrentName.getText().toString().equals("BOARDWALK 1") && currentQueueIndex == 0 && cachedServerQueue.size() > 0) {
            // Keep index 0 on startup
        } else if (currentStopNameIsSet()) {
            boolean found = false;
            String activeName = txtCurrentName.getText().toString();
            for (int i = 0; i < cachedServerQueue.size(); i++) {
                if (cachedServerQueue.get(i).getName() != null && cachedServerQueue.get(i).getName().equalsIgnoreCase(activeName)) {
                    currentQueueIndex = i;
                    found = true;
                    break;
                }
            }
            if (!found && currentQueueIndex >= cachedServerQueue.size()) {
                currentQueueIndex = Math.max(0, cachedServerQueue.size() - 1);
            }
        }

        refreshUIState();
    }

    private boolean currentStopNameIsSet() {
        return txtCurrentName != null && !txtCurrentName.getText().toString().isEmpty();
    }

    private void refreshUIState() {
        if (cachedServerQueue.isEmpty()) return;

        // 1. Previous Stop
        if (currentQueueIndex > 0 && currentQueueIndex - 1 < cachedServerQueue.size()) {
            cardPreviousStop.setVisibility(View.VISIBLE);
            DestStop prev = cachedServerQueue.get(currentQueueIndex - 1);
            txtPrevName.setText(prev.getName() != null ? prev.getName() : "");
        } else {
            cardPreviousStop.setVisibility(View.INVISIBLE);
        }

        // 2. Current Target Stop
        if (currentQueueIndex < cachedServerQueue.size()) {
            cardCurrentStop.setVisibility(View.VISIBLE);
            DestStop current = cachedServerQueue.get(currentQueueIndex);
            txtCurrentName.setText(current.getName() != null ? current.getName() : "");
        } else {
            cardCurrentStop.setVisibility(View.INVISIBLE);
            txtCurrentName.setText("ROUTE COMPLETE");
        }

        // 3. Subsequent Stop (Next)
        if (currentQueueIndex + 1 < cachedServerQueue.size()) {
            cardNextStop.setVisibility(View.VISIBLE);
            DestStop next = cachedServerQueue.get(currentQueueIndex + 1);
            txtNextName.setText(next.getName() != null ? next.getName() : "");
            txtNextPax.setText("EXPECTED: ~" + next.getPassengersDroppingOff() + " PAX");
        } else {
            cardNextStop.setVisibility(View.INVISIBLE);
        }
    }

    private void animateRouteProgression() {
        // 1. Increment index to move to the next stop in sequence
        currentQueueIndex++;

        int animDuration = 250;

        // 2. Slide cards up and fade out
        cardPreviousStop.animate().translationY(-40f).alpha(0f).setDuration(animDuration);
        cardCurrentStop.animate().translationY(-40f).alpha(0f).setDuration(animDuration);
        cardNextStop.animate().translationY(-40f).alpha(0f).setDuration(animDuration).withEndAction(() -> {

            // 3. Update Text Content mid-animation via index re-evaluation
            refreshUIState();

            // 4. Reset vertical offsets
            cardPreviousStop.setTranslationY(40f);
            cardCurrentStop.setTranslationY(40f);
            cardNextStop.setTranslationY(40f);

            // 5. Fade back in smoothly
            cardPreviousStop.animate().translationY(0f).alpha(1f).setDuration(animDuration);
            cardCurrentStop.animate().translationY(0f).alpha(1f).setDuration(animDuration);

            if (currentQueueIndex + 1 < cachedServerQueue.size()) {
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (hubConnection != null && hubConnection.getConnectionState() == HubConnectionState.CONNECTED) {
            hubConnection.stop();
        }
    }
}