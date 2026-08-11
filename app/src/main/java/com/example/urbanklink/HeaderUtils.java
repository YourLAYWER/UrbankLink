package com.example.urbanklink;

import android.app.Activity;
import android.view.View;
import android.widget.ImageView;

public class HeaderUtils {
    /**
     * @param activity The current activity
     * @param showBackButton True if you want the back button visible (e.g., Ticket Details), False for Home screen
     */
    public static void setupHeader(Activity activity, boolean showBackButton) {
        ImageView btnBack = activity.findViewById(R.id.btnHeaderBack);
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
