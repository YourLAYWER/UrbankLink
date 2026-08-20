package com.example.urbanklink;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ProfileActivity extends AppCompatActivity {

    private TextView txtUserName, txtUserStatus;
    private MaterialButton btnLogout;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_profile);

        // Set up your views
        BottomNavigationView bottomNav = findViewById(R.id.bottomNav);

        // Use the utility class, telling it that "Tickets" is the active tab
        NavigationUtils.setupBottomNavigation(this, bottomNav, R.id.nav_profile);

        txtUserName = findViewById(R.id.txtUserName);
        txtUserStatus = findViewById(R.id.txtUserStatus);
        btnLogout = findViewById(R.id.btnLogout);

        btnLogout.setOnClickListener(v -> showLogoutConfirmationDialog());

        View btnTransactionHistory = findViewById(R.id.btnTransactionHistory);

        btnTransactionHistory.setOnClickListener(v -> {
            // This pops up the bottom sheet!
            HistoryBottomSheet bottomSheet = new HistoryBottomSheet();
            bottomSheet.show(getSupportFragmentManager(), "HistoryBottomSheet");
        });

        // 1. Instantly load from local database (Offline support + speed)
        loadLocalProfile();

        // 2. Poll the server for fresh data (Sync)
        fetchServerProfile();
    }
    private void loadLocalProfile() {
        executor.execute(() -> {
            LocalUser user = UrbanLinkDatabase.getInstance(this).userDao().getUser();
            if (user != null) {
                runOnUiThread(() -> updateUI(user.name, user.commuterProfile));
            }
        });
    }

    private void fetchServerProfile() {
        SharedPreferences prefs = getSharedPreferences("UrbanLinkPrefs", Context.MODE_PRIVATE);
        String token = prefs.getString("JWT_TOKEN", null);

        if (token == null) return;

        String authHeader = "Bearer " + token.replace("\"", "").trim();
        UrbanLinkApiService apiService = ApiClient.getClient(null).create(UrbanLinkApiService.class);

        apiService.getUserProfile(authHeader).enqueue(new Callback<UserProfileResponse>() {
            @Override
            public void onResponse(@NonNull Call<UserProfileResponse> call, @NonNull Response<UserProfileResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    UserProfileResponse data = response.body();

                    // Update UI
                    updateUI(data.getName(), data.getCommuterProfile());

                    // Save fresh data to local Room database
                    executor.execute(() -> {
                        LocalUser localUser = new LocalUser(data.getId(), data.getName(), data.getEmail(), data.getCommuterProfile());
                        UrbanLinkDatabase.getInstance(ProfileActivity.this).userDao().insertUser(localUser);
                    });
                }
            }

            @Override
            public void onFailure(@NonNull Call call, @NonNull Throwable t) {
                // If it fails (e.g., no internet), do nothing!
                // The user is already looking at the cached UI from loadLocalProfile().
            }
        });
    }

    private void updateUI(String name, String profileType) {
        txtUserName.setText(name);
        // Formats exactly like your XML mockup: "Student Commuter - Gqeberha"
        txtUserStatus.setText(profileType + " Commuter - Gqeberha");
    }

    private void logoutUser() {
        // Clear token
        getSharedPreferences("UrbanLinkPrefs", Context.MODE_PRIVATE).edit().remove("JWT_TOKEN").apply();

        // Wipe local database records
        executor.execute(() -> {
            UrbanLinkDatabase db = UrbanLinkDatabase.getInstance(this);
            db.userDao().clearUser();
            // You may also want to wipe tickets here: db.ticketDao().clearAllTickets();
        });

        // Redirect
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void showLogoutConfirmationDialog() {
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle("Log Out of UrbanLink?")
                .setMessage("Are you sure you want to log out? You will need an active internet connection to log back in and retrieve your commuter passes.")
                // ADDED: A built-in warning triangle icon to draw attention
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton("Log Out", (d, which) -> {
                    logoutUser();
                })
                .setNegativeButton("Cancel", (d, which) -> {
                    d.dismiss();
                })
                .create(); // Create it first

        dialog.show();

        // ADDED: Custom styling for the buttons to match app's theme
        // Makes "Log Out" bright red to indicate a destructive action
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Color.parseColor("#FF8A8A"));

        // Make "Cancel" a subtle grey so it doesn't compete for attention
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(Color.parseColor("#A0A0A0"));
    }
}