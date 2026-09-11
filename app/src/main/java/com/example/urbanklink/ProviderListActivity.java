package com.example.urbanklink;

import android.content.Intent;
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

public class ProviderListActivity extends AppCompatActivity {

    private String transportType;
    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private TextView tvEmptyState;
    private ProviderAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_provider_list);
        // Set up the shared top header (false = hides the back button since this is a main tab)
        HeaderUtils.setupHeader(this, true, false);

        // Bind the UI components
        recyclerView = findViewById(R.id.recyclerViewProviders);
        progressBar = findViewById(R.id.progressBar);
        tvEmptyState = findViewById(R.id.tvEmptyState);

        //MaterialToolbar toolbar = findViewById(R.id.toolbar);

        // Setup RecyclerView
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        // Handle the Back Button on the toolbar
        //toolbar.setNavigationOnClickListener(v -> onBackPressed());

        // Grab the "Bus" or "Rail string that wsa passed in
        transportType = getIntent().getStringExtra("TRANSPORT_TYPE");

        // Update the UI title dynamically (e.g., "Bus Providers")
        if (transportType != null) {
            setTitle(transportType + " Providers");
            fetchProviders(transportType);
        }

        BottomNavigationView bottomNav = findViewById(R.id.bottomNav);

        // Use your existing utility class!
        // Pass '0' or a specific menu ID (like R.id.nav_home) depending on what you want highlighted
        NavigationUtils.setupBottomNavigation(this, bottomNav, 0);
    }

    private void fetchProviders(String type) {
        // Show loading spinner
        progressBar.setVisibility(View.VISIBLE);
        recyclerView.setVisibility(View.GONE);
        tvEmptyState.setVisibility(View.GONE);

        UrbanLinkApiService apiService = ApiClient.getClient(null).create(UrbanLinkApiService.class);
        Call<List<ProviderSummary>> call = apiService.getProvidersByType(type);

        call.enqueue(new Callback<List<ProviderSummary>>() {
            @Override
            public void onResponse(Call<List<ProviderSummary>> call, Response<List<ProviderSummary>> response) {
                progressBar.setVisibility(View.GONE);

                if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                    List<ProviderSummary> providers = response.body();

                    // Initialize the adapter and pass the click logic
                    adapter = new ProviderAdapter(providers, selectedProvider -> {
                        // THIS IS LEVEL 3!
                        // The user clicked a provider (e.g., Algoa Bus).
                        // Now we pass the ID to the final Schedule List screen.
                       Intent intent = new Intent(ProviderListActivity.this, RouteScheduleActivity.class);
                       intent.putExtra("PROVIDER_ID", selectedProvider.getId());
                       intent.putExtra("PROVIDER_NAME", selectedProvider.getName());
                       startActivity(intent);
                    });

                    recyclerView.setAdapter(adapter);
                    recyclerView.setVisibility(View.VISIBLE);
                } else {
                    tvEmptyState.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onFailure(Call<List<ProviderSummary>> call, Throwable t) {
                progressBar.setVisibility(View.GONE);
                Toast.makeText(ProviderListActivity.this, "Network error", Toast.LENGTH_SHORT).show();
            }
        });
    }
}