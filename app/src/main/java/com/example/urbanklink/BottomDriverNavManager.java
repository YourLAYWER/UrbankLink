package com.example.urbanklink;

import android.app.Activity;
import android.content.Intent;
import android.view.Menu;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

public class BottomDriverNavManager {
    public static void setup(Activity activity, BottomNavigationView bottomNav, FloatingActionButton fabScan, int currentMenuItemId) {

        Menu menu = bottomNav.getMenu();

        if (currentMenuItemId != -1) {
            // Re-enable checkable behavior and highlight the current screen
            menu.setGroupCheckable(0, true, true);
            bottomNav.setSelectedItemId(currentMenuItemId);
        } else {
            // Temporarily disable exclusive checking to uncheck all items for the Scanner view
            menu.setGroupCheckable(0, false, true);
            for (int i = 0; i < menu.size(); i++) {
                menu.getItem(i).setChecked(false);
            }
            menu.setGroupCheckable(0, true, true);
        }

        // Center FAB always routes to ScannerActivity
        fabScan.setOnClickListener(v -> {
            if (!(activity instanceof ScannerActivity)) {
                Intent intent = new Intent(activity, ScannerActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                activity.startActivity(intent);
                activity.overridePendingTransition(0, 0);
            }
        });

        // Handle side menu clicks
        bottomNav.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();

            if (itemId == currentMenuItemId) return true;

            if (itemId == R.id.nav_shift) {
                Intent intent = new Intent(activity, ShiftCompleteActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                activity.startActivity(intent);
                activity.overridePendingTransition(0, 0);
                return true;
            } else if (itemId == R.id.nav_reports) {
                Intent intent = new Intent(activity, IncidentReportActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                activity.startActivity(intent);
                activity.overridePendingTransition(0, 0);
                return true;
            }
            return false;
        });
    }
}
