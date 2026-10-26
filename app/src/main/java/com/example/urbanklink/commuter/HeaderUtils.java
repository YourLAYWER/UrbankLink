package com.example.urbanklink.commuter;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import com.example.urbanklink.R;
import com.example.urbanklink.auth.LoginActivity;

public class HeaderUtils {
    /**
     * @param activity The current activity
     * @param showBackButton True if you want the back button visible (e.g., Ticket Details), False for Home screen
     */
    public static void setupHeader(Activity activity, boolean showBackButton, boolean showLogoutButton, boolean showProfileButton) {
        ImageView btnBack = activity.findViewById(R.id.btnHeaderBack);
        ImageView btnLogout = activity.findViewById(R.id.btnHeaderLogout);
        View btnProfile = activity.findViewById(R.id.btnHeaderProfile);

        // Handle Back Button Visibility & Click
        if (btnBack != null) {
            if (showBackButton) {
                btnBack.setVisibility(View.VISIBLE);
                btnBack.setOnClickListener(v -> activity.finish());
            } else {
                btnBack.setVisibility(View.GONE);
            }
        }

        // Handle Logout Button
        if (btnLogout != null) {
            if (showLogoutButton) {
                btnLogout.setVisibility(View.VISIBLE);
                btnLogout.setOnClickListener(v -> {
                    SharedPreferences prefs = activity.getSharedPreferences("UrbanLinkPrefs", Context.MODE_PRIVATE);
                    prefs.edit().clear().apply();

                    Toast.makeText(activity, "Logged out successfully", Toast.LENGTH_SHORT).show();

                    Intent intent = new Intent(activity, LoginActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    activity.startActivity(intent);
                    activity.finish();
                });
            } else {
                btnLogout.setVisibility(View.GONE);
            }
        }

        // Handle Profile Button Visibility & Click
        if (btnProfile != null) {
            if (showProfileButton) {
                btnProfile.setVisibility(View.VISIBLE);
                btnProfile.setOnClickListener(v -> {
                    Intent intent = new Intent(activity, ProfileActivity.class);
                    activity.startActivity(intent);
                });
            } else {
                btnProfile.setVisibility(View.GONE);
            }
        }
    }
}
