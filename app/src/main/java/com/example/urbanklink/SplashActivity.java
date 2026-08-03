package com.example.urbanklink;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_splash);

            // A 1.5-second delay to show off the branding, then route the user
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                checkAuthentication();
            }
        }, 3000);
    }
    private void checkAuthentication() {
        // 1. Open the secure XML file where we saved the token in LoginActivity
        SharedPreferences prefs = getSharedPreferences("UrbanLinkPrefs", Context.MODE_PRIVATE);

        // 2. Look for the "JWT_TOKEN" key. If it doesn't exist, return null.
        String token = prefs.getString("JWT_TOKEN", null);

        Intent intent;

        // 3. The Fork in the Road
        if (token != null && !token.isEmpty()) {
            // Path A: Token exists! Route straight to the Dashboard.
            intent = new Intent(SplashActivity.this, MainActivity.class);
        } else {
            // Path B: No token. Force them to log in.
            intent = new Intent(SplashActivity.this, LoginActivity.class);
        }

        // 4. Start the next activity
        startActivity(intent);

        // 5. Destroy the SplashActivity so they can't press 'Back' to return here
        finish();
    }
}