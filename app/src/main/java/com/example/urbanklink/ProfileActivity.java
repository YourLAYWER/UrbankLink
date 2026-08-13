package com.example.urbanklink;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.bottomnavigation.BottomNavigationView;

public class ProfileActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_profile);

        // Set up your views
        BottomNavigationView bottomNav = findViewById(R.id.bottomNav);

        // Use the utility class, telling it that "Tickets" is the active tab
        NavigationUtils.setupBottomNavigation(this, bottomNav, R.id.nav_profile);
    }
}