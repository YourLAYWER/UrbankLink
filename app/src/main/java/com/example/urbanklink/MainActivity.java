package com.example.urbanklink;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MainActivity extends AppCompatActivity {

    private TextView txtGreeting;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        txtGreeting = findViewById(R.id.greetingTitle);
        fetchUserData();
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