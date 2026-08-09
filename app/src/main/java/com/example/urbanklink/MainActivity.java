package com.example.urbanklink;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MainActivity extends AppCompatActivity {
    private TextView txtGreeting;
    // 1. Declare your UI components
    private MaterialAutoCompleteTextView etStartLocation, etDestination;
    private MaterialButton findRouteBtn;

    // 2. Declare variables to secretly hold the integer IDs for your Dijkstra algorithm
    private int selectedStartId = -1;
    private int selectedDestinationId = -1;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        txtGreeting = findViewById(R.id.greetingTitle);
        fetchUserData();

        // 3. Link them to the IDs in your XML file
        etStartLocation = findViewById(R.id.etStartLocation);
        etDestination = findViewById(R.id.etEndLocation);
        findRouteBtn = findViewById(R.id.findRouteBtn);
        checkAndSyncStops();
    }
    private void loadStopsIntoUI() {
        // 1. Create a background thread to read from Room
        ExecutorService executor = Executors.newSingleThreadExecutor();

        executor.execute(() -> {
            // 2. Fetch the alphabetical list of stops from the local database
            UrbanLinkDatabase db = UrbanLinkDatabase.getInstance(getApplicationContext());
            List<Stop> localStops = db.stopDao().getAllStops();

            // 3. Switch back to the Main UI Thread to update the screen
            runOnUiThread(() -> {

                // 4. Build the Adapter using your custom dark-themed layout
                ArrayAdapter<Stop> adapter = new ArrayAdapter<>(
                        this, // Use 'this' if in MainActivity
                        R.layout.item_dropdown,      // The XML file we created earlier
                        localStops
                );

                // 5. Attach the adapter to the Material UI components
                etStartLocation.setAdapter(adapter);
                etDestination.setAdapter(adapter);

                // 6. Capture the hidden Stop ID when the user selects a Start Location
                etStartLocation.setOnItemClickListener((parent, view, position, id) -> {
                    Stop selectedStop = (Stop) parent.getItemAtPosition(position);
                    selectedStartId = selectedStop.getStopId();
                });

                // 7. Capture the hidden Stop ID when the user selects a Destination
                etDestination.setOnItemClickListener((parent, view, position, id) -> {
                    Stop selectedStop = (Stop) parent.getItemAtPosition(position);
                    selectedDestinationId = selectedStop.getStopId();
                });
            });
        });
    }
    private void checkAndSyncStops() {
        SharedPreferences prefs = getSharedPreferences("UrbanLinkPrefs", Context.MODE_PRIVATE);
        long lastSyncTime = prefs.getLong("LAST_STOPS_SYNC", 0); // 0 means it has never synced
        long currentTime = System.currentTimeMillis();

        // Calculate time difference in milliseconds
        long timeDifference = currentTime - lastSyncTime;

        // Let's set the threshold to 7 days (in milliseconds)
        // 7 days * 24 hours * 60 minutes * 60 seconds * 1000 milliseconds
        long sevenDaysInMillis = 7L * 24 * 60 * 60 * 1000;

        if (timeDifference > sevenDaysInMillis || lastSyncTime == 0) {
            // It's been over a week, or it's the user's first time opening the app.
            // Go fetch fresh data from C#!
            syncStopsFromBackend();
        } else {
            // It hasn't been a week yet. Do nothing!
            // The app will silently load the UI using the fast local Room database.
            loadStopsIntoUI();
        }
    }
    private void syncStopsFromBackend() {
        UrbanLinkApiService apiService = ApiClient.getClient(null).create(UrbanLinkApiService.class);

        apiService.getAllStops().enqueue(new Callback<List<Stop>>() {
            @Override
            public void onResponse(Call<List<Stop>> call, Response<List<Stop>> response) {
                if (response.isSuccessful() && response.body() != null) {

                    List<Stop> stopsFromApi = response.body();

                    // Create a background thread to save the data to Room
                    ExecutorService executor = Executors.newSingleThreadExecutor();
                    executor.execute(() -> {
                        UrbanLinkDatabase db = UrbanLinkDatabase.getInstance(getApplicationContext());
                        db.stopDao().insertStops(stopsFromApi);

                        // ---> ADD THIS: Save the exact time the sync succeeded
                        SharedPreferences prefs = getSharedPreferences("UrbanLinkPrefs", Context.MODE_PRIVATE);
                        prefs.edit().putLong("LAST_STOPS_SYNC", System.currentTimeMillis()).apply();

                        runOnUiThread(() -> {
                            loadStopsIntoUI(); // Load them into the dropdowns
                        });
                    });

                } else {
                    Log.e("SYNC_ERROR", "Failed to fetch stops. Code: " + response.code());
                }
            }

            @Override
            public void onFailure(Call<List<Stop>> call, Throwable t) {
                Log.e("SYNC_ERROR", "Network error: " + t.getMessage());
                // It's okay if this fails! The app will just use the offline Room data from yesterday.
            }
        });
    }

    // The method below fetches user information and processes it for tailored greeting message
    private void fetchUserData() {
        // 1. Get the saved JWT token from SharedPreferences
        SharedPreferences prefs = getSharedPreferences("UrbanLinkPrefs", Context.MODE_PRIVATE);
        String token = prefs.getString("JWT_TOKEN", null);

        // 2. Safely check if the user is actually logged in
        if (token == null) {
            Toast.makeText(this, "No token found! Please log in again.", Toast.LENGTH_LONG).show();
            // (Optional: You could automatically redirect to LoginActivity here)
            return;
        }

        // 3. Strip out any stray quotes, spaces, or newlines to prevent the 400 Bad Request
        String cleanToken = token.replace("\"", "").trim();

        // 4. Build the perfect header
        String authHeader = "Bearer " + cleanToken;

        // 5. Initialize the Retrofit service instance
        UrbanLinkApiService apiService = ApiClient.getClient(null).create(UrbanLinkApiService.class);

        // 6. Make the API Call using the INSTANCE (apiService), not the Interface
        apiService.getUserProfile(authHeader).enqueue(new Callback<UserProfileResponse>() {
            @Override
            public void onResponse(Call<UserProfileResponse> call, Response<UserProfileResponse> response) {
                if (response.isSuccessful() && response.body() != null) {

                    // Extract the name and update the UI
                    String fullName = response.body().getFullName();

                    // Extract just the first name for a friendly greeting
                    String firstName = fullName;
                    if (fullName != null && fullName.contains(" ")) {
                        firstName = fullName.substring(0, fullName.indexOf(" "));
                    }

                    txtGreeting.setText("Hello, " + firstName);

                } else {
                    try {
                        // This extracts the exact error message your C# server sent back
                        String serverError = response.errorBody() != null ? response.errorBody().string() : "Unknown error";

                        // Print it to the Logcat (Search for "API_ERROR" in the bottom Logcat tab)
                        Log.e("API_ERROR", "Code: " + response.code() + " | Body: " + serverError);

                        // Show a quick toast with the code
                        Toast.makeText(MainActivity.this, "Error Code: " + response.code(), Toast.LENGTH_LONG).show();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }

            @Override
            public void onFailure(Call<UserProfileResponse> call, Throwable t) {
                Toast.makeText(MainActivity.this, "Network error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }
}