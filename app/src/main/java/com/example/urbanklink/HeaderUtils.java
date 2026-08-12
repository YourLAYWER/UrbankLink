package com.example.urbanklink;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

public class HeaderUtils {
    /**
     * @param activity The current activity
     * @param showBackButton True if you want the back button visible (e.g., Ticket Details), False for Home screen
     */
    public static void setupHeader(Activity activity, boolean showBackButton, boolean showLogoutButton) {
        ImageView btnBack = activity.findViewById(R.id.btnHeaderBack);
        ImageView btnLogout = activity.findViewById(R.id.btnHeaderLogout);
        View btnProfile = activity.findViewById(R.id.btnHeaderProfile);

        // Handle Back Button Visibility & Click
        if (btnBack != null) {
            if (showBackButton) {
                btnBack.setVisibility(View.VISIBLE);
                btnBack.setOnClickListener(v -> activity.finish());
            } else {
                // Hide it completely on main hub screens
                btnBack.setVisibility(View.GONE);
            }
        }

        // Handle Logout Button
        if (btnLogout != null) {
            if (showLogoutButton) {
                btnLogout.setVisibility(View.VISIBLE);
                btnLogout.setOnClickListener(v -> {
                    // 1. Wipe the saved JWT token and sync data from SharedPreferences
                    SharedPreferences prefs = activity.getSharedPreferences("UrbanLinkPrefs", Context.MODE_PRIVATE);
                    prefs.edit().clear().apply();

                    Toast.makeText(activity, "Logged out successfully", Toast.LENGTH_SHORT).show();

                    // 2. Redirect to Login Screen and clear the backstack
                    Intent intent = new Intent(activity, LoginActivity.class);
                    // This flag ensures the user can't press the back button to return to the Home screen after logging out!
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    activity.startActivity(intent);
                    activity.finish();
                });
            } else {
                btnLogout.setVisibility(View.GONE);
            }
        }

        // Handle Profile Click
        if (btnProfile != null) {
            btnProfile.setOnClickListener(v -> {
                // Navigate to the profile screen
                // Intent intent = new Intent(activity, ProfileActivity.class);
                // activity.startActivity(intent);
            });
        }
    }
}
