package com.example.urbanklink;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import com.example.urbanklink.auth.LoginActivity;
import com.example.urbanklink.commuter.MainActivity;
import com.example.urbanklink.conductor.ScannerActivity;
import com.example.urbanklink.driver.ScannerTelemetry;

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
        String role = prefs.getString("USER_ROLE", token);

        //Intent intent;
        if (token != null) {
            // The user is already logged in! Now check their role:
            if (role.equalsIgnoreCase("Conductor")) {
                // Send them straight to the Conductor Start Shift / Scanner view
                Intent intent = new Intent(this, ScannerActivity.class); // Or your Conductor home
                startActivity(intent);
                finish();
            } else if (role.equalsIgnoreCase("Driver")) {
                Intent intent = new Intent(this, ScannerTelemetry.class); // Or your Conductor home
                startActivity(intent);
                finish();
            } else {
                // Send them to the standard Passenger dashboard
                Intent intent = new Intent(this, MainActivity.class);
                startActivity(intent);
                finish();
            }
        } else {
            // No token found, send them to the Login screen
            Intent intent = new Intent(this, LoginActivity.class);
            startActivity(intent);
            finish();
        }
    }
}