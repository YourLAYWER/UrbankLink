package com.example.urbanklink;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;

import java.util.ArrayList;
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

    // ---> ADD THIS: A global list to hold all stops so the button can search it <---
    private List<Stop> allStops = new ArrayList<>();
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        // Set up the shared top header (false = hides the back button since this is a main tab)
        HeaderUtils.setupHeader(this, false, true);

        txtGreeting = findViewById(R.id.greetingTitle);
        fetchUserData();
        // 3. Link them to the IDs in your XML file
        etStartLocation = findViewById(R.id.etStartLocation);
        etDestination = findViewById(R.id.etEndLocation);
        findRouteBtn = findViewById(R.id.findRouteBtn);
        checkAndSyncStops();


        findRouteBtn.setOnClickListener(v -> {
            // 1. Grab whatever text the user typed in the boxes
            String startInput = etStartLocation.getText().toString().trim().toLowerCase();
            String destInput = etDestination.getText().toString().trim().toLowerCase();

            // 2. Reset the IDs to -1 in case they changed their mind
            selectedStartId = -1;
            selectedDestinationId = -1;

            // 3. Manually scan the list for a match!
            for (Stop stop : allStops) {
                String dbStopName = stop.getName().toLowerCase();

                // If the database name starts with whatever they typed (e.g. "Boardwa" matches "Boardwalk")
                if (selectedStartId == -1 && dbStopName.startsWith(startInput) && !startInput.isEmpty()) {
                    selectedStartId = stop.getStopId();
                    // Optional: Auto-correct the text in the UI to the full name
                    etStartLocation.setText(stop.getName(), false);
                }

                if (selectedDestinationId == -1 && dbStopName.startsWith(destInput) && !destInput.isEmpty()) {
                    selectedDestinationId = stop.getStopId();
                    // Optional: Auto-correct the text in the UI to the full name
                    etDestination.setText(stop.getName(), false);
                }
            }

            // 4. Now run your validation checks
            if (selectedStartId == -1 || selectedDestinationId == -1) {
                Toast.makeText(this, "Please select valid stops from the list", Toast.LENGTH_SHORT).show();
                return;
            }

            if (selectedStartId == selectedDestinationId) {
                Toast.makeText(this, "Start and destination cannot be the same", Toast.LENGTH_SHORT).show();
                return;
            }

            // 5. Fire the Intent to the Ticket Details screen
            Intent intent = new Intent(MainActivity.this, Ticket_details.class);
            intent.putExtra("START_ID", selectedStartId);
            intent.putExtra("DEST_ID", selectedDestinationId);
            startActivity(intent);
        });
        // Set up your views
        BottomNavigationView bottomNav = findViewById(R.id.bottomNav);

        // Use the utility class, telling it that "Home" is the active tab
        NavigationUtils.setupBottomNavigation(this, bottomNav, R.id.nav_home);
    }
    private void loadStopsIntoUI() {
        // 1. Create a background thread to read from Room
        ExecutorService executor = Executors.newSingleThreadExecutor();

        executor.execute(() -> {
            // 2. Fetch the alphabetical list of stops from the local database
            UrbanLinkDatabase db = UrbanLinkDatabase.getInstance(getApplicationContext());
            List<Stop> localStops = db.stopDao().getAllStops();

            // ---> NEW STEP: Create a list of JUST STRINGS for the UI <---
            List<String> stopNames = new ArrayList<>();
            for (Stop stop : localStops) {
                stopNames.add(stop.getName());
            }

            // 3. Switch back to the Main UI Thread to update the screen
            runOnUiThread(() -> {

                // ---> ADD THIS LINE: Save the stops so the button can read them later
                allStops = localStops;

                // 4. Build the Adapter using the STRING list, not the Stop objects
                ArrayAdapter<String> adapter = new ArrayAdapter<>(
                        MainActivity.this,
                        R.layout.item_dropdown,
                        stopNames // <--- Passed the Strings!
                );

                // 5. Attach the adapter to the Material UI components
                etStartLocation.setAdapter(adapter);
                etDestination.setAdapter(adapter);

                // 6. Upgraded Start Location Listener
                etStartLocation.setOnItemClickListener((parent, view, position, id) -> {
                    // Grab the raw String the user tapped
                    String selectedName = (String) parent.getItemAtPosition(position);

                    // Look up that name in our original database list to find the ID
                    for (Stop stop : localStops) {
                        if (stop.getName().equals(selectedName)) {
                            selectedStartId = stop.getStopId();

                            // VISUAL PROOF!
                            Toast.makeText(MainActivity.this, "Start Saved: " + selectedStartId, Toast.LENGTH_SHORT).show();
                            break;
                        }
                    }

                    // UX Magic: Hide the keyboard so they can see the Find Route button
                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
                    etStartLocation.clearFocus();
                });

                // 7. Upgraded Destination Listener
                etDestination.setOnItemClickListener((parent, view, position, id) -> {
                    // Grab the raw String the user tapped
                    String selectedName = (String) parent.getItemAtPosition(position);

                    // Look up that name in our original database list to find the ID
                    for (Stop stop : localStops) {
                        if (stop.getName().equals(selectedName)) {
                            selectedDestinationId = stop.getStopId();

                            // VISUAL PROOF!
                            Toast.makeText(MainActivity.this, "Dest Saved: " + selectedDestinationId, Toast.LENGTH_SHORT).show();
                            break;
                        }
                    }

                    // UX Magic: Hide the keyboard
                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
                    etDestination.clearFocus();
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