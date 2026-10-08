package com.example.urbanklink;

import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class RouteScheduleActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private int providerId;
    private String providerName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_route_schedule);

//        //Top Header SetUp
//        HeaderUtils.setupHeader(this, true, false);
//
//        recyclerView = findViewById(R.id.recyclerViewRoutes);
//        progressBar = findViewById(R.id.progressBarSchedule);
//        MaterialToolbar toolbar = findViewById(R.id.toolbarSchedule);
//
//        recyclerView.setLayoutManager(new LinearLayoutManager(this));
//        toolbar.setNavigationOnClickListener(v -> onBackPressed());
//
//        // 1. Unpack the data sent from Level 2
//        providerId = getIntent().getIntExtra("PROVIDER_ID", -1);
//        providerName = getIntent().getStringExtra("PROVIDER_NAME");
//
//        if (providerName != null) {
//            toolbar.setTitle(providerName + " Routes");
//        }
//
//        if (providerId != -1) {
//            fetchRoutes(providerId);
//        }
//
//        // Bottom Navigation SetUp
//        BottomNavigationView bottomNav = findViewById(R.id.bottomNav);
//        NavigationUtils.setupBottomNavigation(this, bottomNav, 0);


        // 1. Top Header SetUp (This automatically handles the Back button!)
        HeaderUtils.setupHeader(this, true, false);

        recyclerView = findViewById(R.id.recyclerViewRoutes);
        progressBar = findViewById(R.id.progressBarSchedule);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        // 2. Unpack the data sent from Level 2
        providerId = getIntent().getIntExtra("PROVIDER_ID", -1);
        providerName = getIntent().getStringExtra("PROVIDER_NAME");

        // 3. Update the custom header title instead of the old Toolbar
        if (providerName != null) {
            // NOTE: Change 'txtAppNameTitle' to whatever ID you used for the title in your layout_top_header.xml
            TextView headerTitle = findViewById(R.id.txtScreenTitle);
            if (headerTitle != null) {
                headerTitle.setText(providerName + " Routes");
            }
        }

        if (providerId != -1) {
            fetchRoutes(providerId);
        }

        // 4. Bottom Navigation SetUp
        BottomNavigationView bottomNav = findViewById(R.id.bottomNav);
        NavigationUtils.setupBottomNavigation(this, bottomNav, 0);
    }

    private void fetchRoutes(int id) {
        progressBar.setVisibility(View.VISIBLE);
        recyclerView.setVisibility(View.GONE);

        // Use your specific ApiClient class name here
        UrbanLinkApiService apiService = ApiClient.getClient(getApplicationContext()).create(UrbanLinkApiService.class);

        apiService.getRoutesByProvider(id).enqueue(new Callback<RouteResponse>() {
            @Override
            public void onResponse(Call<RouteResponse> call, Response<RouteResponse> response) {
                progressBar.setVisibility(View.GONE);

                if (response.isSuccessful() && response.body() != null) {
                    // Extract the List from inside the "data" wrapper
                    // If you want to see the UI, you will pass response.body().data into an adapter here!
                    // Grab the List<TransitRouteData> from the JSON response
                    List<TransitRouteData> routes = response.body().data;

                    // Initialize the adapter and attach it to the list
                    RouteAdapter adapter = new RouteAdapter(routes);
                    recyclerView.setAdapter(adapter);

                    recyclerView.setVisibility(View.VISIBLE);
                    Toast.makeText(RouteScheduleActivity.this, response.body().message, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(RouteScheduleActivity.this, "No routes available.", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<RouteResponse> call, Throwable t) {
                progressBar.setVisibility(View.GONE);
                Toast.makeText(RouteScheduleActivity.this, "Network error", Toast.LENGTH_SHORT).show();
            }
        });
    }
}