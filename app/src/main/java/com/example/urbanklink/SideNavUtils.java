package com.example.urbanklink;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.material.card.MaterialCardView;

public class SideNavUtils {

    public static void setupSideNavigation(Activity activity, int activeTabId) {

        // 1. Array of all tab IDs to easily loop through them
        int[] tabIds = {
                R.id.navTabScan, R.id.navTabCash, R.id.navTabStops,
                R.id.navTabShift, R.id.navTabReport, R.id.navTabProfile
        };

        // 2. Loop through all tabs to apply styling
        for (int id : tabIds) {
            MaterialCardView tab = activity.findViewById(id);
            if (tab == null) continue;

            boolean isActive = (id == activeTabId);

            // Apply Active/Inactive Styling
            if (isActive) {
                tab.setCardBackgroundColor(Color.parseColor("#12131A"));
                tab.setStrokeColor(Color.parseColor("#00E5FF"));
                tab.setStrokeWidth(4); // 2dp equivalent

                tintTabContents(activity, id, "#00E5FF");

                // Special handling for the STOPS subtext
                if (id == R.id.navTabStops) {
                    View subtext = activity.findViewById(R.id.txtStopsSub);
                    if (subtext != null) subtext.setVisibility(View.VISIBLE);
                }
            } else {
                tab.setCardBackgroundColor(Color.parseColor("#1A1B22"));
                tab.setStrokeWidth(0);

                tintTabContents(activity, id, "#A0A0A0");

                if (id == R.id.navTabStops) {
                    View subtext = activity.findViewById(R.id.txtStopsSub);
                    if (subtext != null) subtext.setVisibility(View.GONE);
                }
            }

            // 3. Attach Routing Listeners
            tab.setOnClickListener(v -> {
                if (id == activeTabId) return; // Do nothing if already on this screen

                Intent intent = null;

                if (id == R.id.navTabScan) {
                    intent = new Intent(activity, scannerTelemetry.class);
                } else if (id == R.id.navTabStops) {
                    intent = new Intent(activity, TransitTelemetry.class); // Or whatever your stops screen is named
                }
                // Add your other routing destinations here as you build them!
                // else if (id == R.id.navTabCash) { intent = new Intent(...); }

                if (intent != null) {
                    activity.startActivity(intent);
                    activity.overridePendingTransition(0, 0); // Removes slide animation for instant tab feel
                    activity.finish();
                }
            });
        }
    }

    // Helper method to color the icon and text inside the card
    private static void tintTabContents(Activity activity, int tabId, String hexColor) {
        int color = Color.parseColor(hexColor);

        ImageView icon = null;
        TextView text = null;

        if (tabId == R.id.navTabScan) {
            icon = activity.findViewById(R.id.icScan);
            text = activity.findViewById(R.id.txtScan);
        } else if (tabId == R.id.navTabCash) {
            icon = activity.findViewById(R.id.icCash);
            text = activity.findViewById(R.id.txtCash);
        } else if (tabId == R.id.navTabStops) {
            icon = activity.findViewById(R.id.icStops);
            text = activity.findViewById(R.id.txtStops);
            TextView subtext = activity.findViewById(R.id.txtStopsSub);
            if (subtext != null) subtext.setTextColor(color);
        } else if (tabId == R.id.navTabShift) {
            icon = activity.findViewById(R.id.icShift);
            text = activity.findViewById(R.id.txtShift);
        } else if (tabId == R.id.navTabReport) {
            icon = activity.findViewById(R.id.icReport);
            text = activity.findViewById(R.id.txtReport);
        } else if (tabId == R.id.navTabProfile) {
            icon = activity.findViewById(R.id.icProfile);
            text = activity.findViewById(R.id.txtProfile);
        }

        if (icon != null) icon.setImageTintList(ColorStateList.valueOf(color));
        if (text != null) text.setTextColor(color);
    }
}