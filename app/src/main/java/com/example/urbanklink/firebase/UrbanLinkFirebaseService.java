package com.example.urbanklink.firebase;

import android.content.Intent;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

public class UrbanLinkFirebaseService extends FirebaseMessagingService {
    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);

        // Check if the message contains a data payload
        if (remoteMessage.getData().size() > 0) {
            String action = remoteMessage.getData().get("action");

            if ("REFRESH_ROUTE_STATUS".equals(action)) {
                // The backend is nudging us to fetch fresh data.
                // Broadcast an intent to your active UI (like the Route Map or Ticket Screen)
                // so it knows to execute the REST API GET request we built earlier.
                Intent intent = new Intent("FCM_ROUTE_STATUS_UPDATE");
                intent.putExtra("routeId", remoteMessage.getData().get("routeId"));
                LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
            }
        }
    }

    @Override
    public void onNewToken(String token) {
        super.onNewToken(token);
        // Required by Firebase, though not strictly needed for Topic messaging
    }
}
