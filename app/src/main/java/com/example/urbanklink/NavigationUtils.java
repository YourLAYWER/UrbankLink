package com.example.urbanklink;

import android.app.Activity;
import android.content.Intent;

import com.google.android.material.bottomnavigation.BottomNavigationView;

public class NavigationUtils {
    public static void setupBottomNavigation(Activity activity, BottomNavigationView bottomNav, int currentMenuItemId) {
        // Highlight the current tab
        bottomNav.setSelectedItemId(currentMenuItemId);

        bottomNav.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();

            // Do nothing if the user clicks the tab they are already on
            if (itemId == currentMenuItemId) return true;

            if (itemId == R.id.nav_home) {
                activity.startActivity(new Intent(activity, MainActivity.class));
                activity.overridePendingTransition(0, 0); // Removes animation for a seamless tab switch
            }
            else if (itemId == R.id.nav_tickets) {
                // Uncomment when you have this screen ready
                activity.startActivity(new Intent(activity, TicketScreen.class));
                activity.overridePendingTransition(0, 0);
            }
            else if (itemId == R.id.nav_profile) {
                // Uncomment when you have this screen ready
                activity.startActivity(new Intent(activity, ProfileActivity.class));
                activity.overridePendingTransition(0, 0);
            }
            return false;
        });
    }
}
