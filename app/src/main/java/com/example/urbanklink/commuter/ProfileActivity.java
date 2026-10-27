package com.example.urbanklink.commuter;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.urbanklink.R;
import com.example.urbanklink.auth.LoginActivity;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class ProfileActivity extends AppCompatActivity {

    private ProfileViewModel viewModel;
    private TextView txtUserName, txtUserStatus;
    private MaterialButton btnLogout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_profile);

        initializeUI();

        // 1. Initialize ViewModel using custom factory (Provides Application Context)
        ViewModelProvider.Factory factory = new ViewModelProvider.Factory() {
            @NonNull
            @Override
            public <T extends androidx.lifecycle.ViewModel> T create(@NonNull Class<T> modelClass) {
                return (T) new ProfileViewModel(getApplication());
            }
        };
        viewModel = new ViewModelProvider(this, factory).get(ProfileViewModel.class);

        // 2. Setup Observers
        setupObservers();

        // 3. Load Data
        viewModel.loadProfile();

        // 4. Setup Click Listeners
        btnLogout.setOnClickListener(v -> showLogoutConfirmationDialog());

        View btnTransactionHistory = findViewById(R.id.btnTransactionHistory);
        btnTransactionHistory.setOnClickListener(v -> {
            HistoryBottomSheet bottomSheet = new HistoryBottomSheet();
            bottomSheet.show(getSupportFragmentManager(), "HistoryBottomSheet");
        });
    }

    /**
     * Binds XML elements and sets up the Bottom Navigation
     */
    private void initializeUI() {
        BottomNavigationView bottomNav = findViewById(R.id.bottomNav);
        NavigationUtils.setupBottomNavigation(this, bottomNav, R.id.nav_profile);

        txtUserName = findViewById(R.id.txtUserName);
        txtUserStatus = findViewById(R.id.txtUserStatus);
        btnLogout = findViewById(R.id.btnLogout);
    }

    /**
     * Binds the UI to the data streams coming from the ViewModel
     */
    private void setupObservers() {
        // Observe Profile Data
        viewModel.getUserProfile().observe(this, user -> {
            if (user != null) {
                txtUserName.setText(user.name);
                txtUserStatus.setText(user.commuterProfile + " Commuter - Gqeberha");
            }
        });

        // Observe Logout Event
        viewModel.getLogoutComplete().observe(this, isComplete -> {
            if (isComplete != null && isComplete) {
                navigateToLogin();
            }
        });
    }

    /**
     * Safely redirects the user to the Login screen after data is wiped
     */
    private void navigateToLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    /**
     * Displays a destructive warning dialog before wiping user data
     */
    private void showLogoutConfirmationDialog() {
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle("Log Out of UrbanLink?")
                .setMessage("Are you sure you want to log out? You will need an active internet connection to log back in and retrieve your commuter passes.")
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton("Log Out", (d, which) -> {
                    viewModel.performLogout(); // Tell ViewModel to execute logout
                })
                .setNegativeButton("Cancel", (d, which) -> {
                    d.dismiss();
                })
                .create();

        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Color.parseColor("#FF8A8A"));
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(Color.parseColor("#A0A0A0"));
    }
}