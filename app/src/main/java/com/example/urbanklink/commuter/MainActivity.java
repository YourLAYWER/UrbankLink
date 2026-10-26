package com.example.urbanklink.commuter;

import static android.app.ProgressDialog.show;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.example.urbanklink.database.LocalRoute;
import com.example.urbanklink.R;
import com.example.urbanklink.database.Stop;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.maps.android.PolyUtil;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private MainViewModel viewModel;

    // UI Components
    private TextView txtGreeting;
    //private MaterialAutoCompleteTextView etStartLocation, etDestination;
    private AutoCompleteTextView etStartLocation, etDestination;
    private MaterialButton findRouteBtn;

    private int selectedStartId = -1;
    private int selectedDestinationId = -1;
    private List<Stop> allStops = new ArrayList<>();

    // Map Components
    private FusedLocationProviderClient fusedLocationClient;
    private ActivityResultLauncher<String[]> locationPermissionRequest;
    private GoogleMap mMap;
    private com.google.android.gms.maps.model.Polyline currentlySelectedPolyline = null;
    private java.util.List<com.google.android.gms.maps.model.Marker> currentRouteMarkers = new java.util.ArrayList<>();

    //Permissions
    private ActivityResultLauncher<String[]> appPermissionRequest;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        // 1. Initialize ViewModel
        viewModel = new ViewModelProvider(this, ViewModelProvider.AndroidViewModelFactory.getInstance(this.getApplication())).get(MainViewModel.class);

        // 2. Initialize Location Client
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // 3. REGISTER THE PERMISSION LAUNCHER FIRST
        appPermissionRequest = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> {
                    Boolean fineGranted = result.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false);
                    Boolean coarseGranted = result.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false);

                    if ((fineGranted != null && fineGranted) || (coarseGranted != null && coarseGranted)) {
                        enableUserLocation();
                    } else {
                        Toast.makeText(this, "Location permission denied.", Toast.LENGTH_SHORT).show();
                    }

                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        Boolean notifyGranted = result.getOrDefault(Manifest.permission.POST_NOTIFICATIONS, false);
                        if (notifyGranted != null && !notifyGranted) {
                            Toast.makeText(this, "Notifications disabled. You will miss route updates.", Toast.LENGTH_SHORT).show();
                        }
                    }
                }
        );

        // 4. NOW YOU CAN SAFELY CHECK PERMISSIONS
        checkAppPermissions();

        // 5. Setup standard UI
        HeaderUtils.setupHeader(this, false, false, true);
        txtGreeting = findViewById(R.id.greetingTitle);
        etStartLocation = findViewById(R.id.etStartLocation);
        etDestination = findViewById(R.id.etEndLocation);
        findRouteBtn = findViewById(R.id.findRouteBtn);

        setupNavigationAndCards();
        setupFindRouteButton();

        // 6. Setup Observers
        setupObservers();

        // 7. Initialize Map
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.mapFragment);
        if (mapFragment != null) {
            mapFragment.getMapAsync(googleMap -> {
                mMap = googleMap;
                enableUserLocation();

                // Once map is ready, observe the routes to draw them
                viewModel.getMapRoutes().observe(this, this::drawRoutesOnMap);
            });
        }

    }

    private void setupObservers() {
        // Observe Greeting Name
        viewModel.getUserName().observe(this, firstName -> {
            if (firstName != null) txtGreeting.setText("Hello, " + firstName);
        });

        // Observe Stops for Dropdown
        viewModel.getAllStops().observe(this, stops -> {
            if (stops == null || stops.isEmpty()) return;
            this.allStops = stops; // Save for validation

            List<String> stopNames = new ArrayList<>();
            for (Stop stop : stops) stopNames.add(stop.getName());

            ArrayAdapter<String> adapter = new ArrayAdapter<>(this, R.layout.item_dropdown, stopNames);
            etStartLocation.setAdapter(adapter);
            etDestination.setAdapter(adapter);
            setupDropdownListeners(stops);
        });

        // Observe Stops for clicked Route Pins
        viewModel.getSelectedRouteStops().observe(this, routeStops -> {
            if (mMap == null || routeStops == null) return;

            // Draw Pins
            for (Stop stop : routeStops) {
                LatLng position = new LatLng(stop.getLatitude(), stop.getLongitude());
                com.google.android.gms.maps.model.Marker m = mMap.addMarker(
                        new com.google.android.gms.maps.model.MarkerOptions()
                                .position(position)
                                .title(stop.getName())
                                .icon(com.google.android.gms.maps.model.BitmapDescriptorFactory.defaultMarker(com.google.android.gms.maps.model.BitmapDescriptorFactory.HUE_ORANGE))
                );
                if (m != null) currentRouteMarkers.add(m);
            }
        });
    }

    private void drawRoutesOnMap(List<LocalRoute> routes) {
        if (mMap == null || routes == null || routes.isEmpty()) return;

        mMap.clear();
        currentlySelectedPolyline = null;
        currentRouteMarkers.clear();

        for (LocalRoute route : routes) {
            if (route.encodedPolyline != null && !route.encodedPolyline.isEmpty()) {
                try {
                    // Strip any accidental whitespace or newlines from the backend
                    String cleanPolyline = route.encodedPolyline.trim().replace("\n", "").replace("\r", "");
                    List<LatLng> decodedPath = PolyUtil.decode(cleanPolyline);

                    int routeColor = ("Train".equalsIgnoreCase(route.transportType) || "Rail".equalsIgnoreCase(route.transportType))
                            ? Color.parseColor("#4CAF50") : Color.parseColor("#2196F3");

                    PolylineOptions polylineOptions = new PolylineOptions()
                            .addAll(decodedPath).color(routeColor).width(12f).geodesic(true).clickable(true);

                    com.google.android.gms.maps.model.Polyline polyline = mMap.addPolyline(polylineOptions);
                    polyline.setTag(route);

                } catch (Exception e) {
                    // Logs the error but allows the app to keep running and draw the other valid routes
                    Log.e("MAP_ERROR", "Failed to decode polyline for route: " + route.routeName, e);
                }
            }
        }
        mMap.setOnPolylineClickListener(polyline -> {
            // Revert previous polyline
            if (currentlySelectedPolyline != null) {
                LocalRoute prevRoute = (LocalRoute) currentlySelectedPolyline.getTag();
                if (prevRoute != null) {
                    int originalColor = ("Train".equalsIgnoreCase(prevRoute.transportType) || "Rail".equalsIgnoreCase(prevRoute.transportType))
                            ? Color.parseColor("#4CAF50") : Color.parseColor("#2196F3");
                    currentlySelectedPolyline.setColor(originalColor);
                    currentlySelectedPolyline.setWidth(12f);
                    currentlySelectedPolyline.setZIndex(0);
                }
            }

            // Clear old pins
            for (com.google.android.gms.maps.model.Marker marker : currentRouteMarkers) marker.remove();
            currentRouteMarkers.clear();

            // Highlight new polyline
            polyline.setColor(Color.parseColor("#FF9800"));
            polyline.setWidth(22f);
            polyline.setZIndex(1);
            currentlySelectedPolyline = polyline;

            // Trigger ViewModel to fetch stops! (Triggers getSelectedRouteStops() observer above)
            LocalRoute clickedRoute = (LocalRoute) polyline.getTag();
            if (clickedRoute != null) {
                showRouteBottomSheet(clickedRoute);
                viewModel.selectRoute(clickedRoute.routeId);
            }
        });
    }

    // --- The rest are just UI Helper methods extracted for cleanliness ---

    private void setupDropdownListeners(List<Stop> localStops) {
        etStartLocation.setOnItemClickListener((parent, view, position, id) -> {
            String selectedName = (String) parent.getItemAtPosition(position);
            for (Stop stop : localStops) {
                if (stop.getName().equals(selectedName)) {
                    selectedStartId = stop.getStopId();
                    break;
                }
            }
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
            etStartLocation.clearFocus();
        });

        etDestination.setOnItemClickListener((parent, view, position, id) -> {
            String selectedName = (String) parent.getItemAtPosition(position);
            for (Stop stop : localStops) {
                if (stop.getName().equals(selectedName)) {
                    selectedDestinationId = stop.getStopId();
                    break;
                }
            }
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
            etDestination.clearFocus();
        });
    }

    private void setupFindRouteButton() {
        findRouteBtn.setOnClickListener(v -> {
            String startInput = etStartLocation.getText().toString().trim().toLowerCase();
            String destInput = etDestination.getText().toString().trim().toLowerCase();

            selectedStartId = -1;
            selectedDestinationId = -1;

            for (Stop stop : allStops) {
                String dbStopName = stop.getName().toLowerCase();
                if (selectedStartId == -1 && dbStopName.startsWith(startInput) && !startInput.isEmpty()) {
                    selectedStartId = stop.getStopId();
                    etStartLocation.setText(stop.getName(), false);
                }
                if (selectedDestinationId == -1 && dbStopName.startsWith(destInput) && !destInput.isEmpty()) {
                    selectedDestinationId = stop.getStopId();
                    etDestination.setText(stop.getName(), false);
                }
            }

            if (selectedStartId == -1 || selectedDestinationId == -1) {
                Toast.makeText(this, "Please select valid stops from the list", Toast.LENGTH_SHORT).show();
                return;
            }
            if (selectedStartId == selectedDestinationId) {
                Toast.makeText(this, "Start and destination cannot be the same", Toast.LENGTH_SHORT).show();
                return;
            }

            Intent intent = new Intent(MainActivity.this, Ticket_details.class);
            intent.putExtra("START_ID", selectedStartId);
            intent.putExtra("DEST_ID", selectedDestinationId);
            startActivity(intent);
        });
    }

    private void setupNavigationAndCards() {
        MaterialCardView busCard = findViewById(R.id.cardBusSchedules);
        MaterialCardView railCard = findViewById(R.id.cardRailSchedules);

        busCard.setOnClickListener(v -> {
            Intent intent = new Intent(this, ProviderListActivity.class);
            intent.putExtra("TRANSPORT_TYPE", "Bus");
            startActivity(intent);
        });

        railCard.setOnClickListener(v -> {
            Intent intent = new Intent(this, ProviderListActivity.class);
            intent.putExtra("TRANSPORT_TYPE", "Train");
            startActivity(intent);
        });

        BottomNavigationView bottomNav = findViewById(R.id.bottomNav);
        NavigationUtils.setupBottomNavigation(this, bottomNav, R.id.nav_home);
    }

    private void checkAppPermissions() {
        List<String> permissionsToRequest = new ArrayList<>();

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        }

        // Notifications require explicit permission on Android 13 (API 33) and above
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS);
            }
        }

        if (!permissionsToRequest.isEmpty()) {
            appPermissionRequest.launch(permissionsToRequest.toArray(new String[0]));
        }
    }

    private void showRouteBottomSheet(LocalRoute clickedRoute) {
        // 1. Initialize the Bottom Sheet
        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(MainActivity.this);

        // 2. Inflate your custom XML layout
        View sheetView = getLayoutInflater().inflate(R.layout.bottom_sheet_route_details, null);
        bottomSheetDialog.setContentView(sheetView);

        // 3. Link the XML views
        TextView txtRouteName = sheetView.findViewById(R.id.sheetRouteName);
        TextView txtTransportType = sheetView.findViewById(R.id.sheetTransportType);
        MaterialButton btnAction = sheetView.findViewById(R.id.sheetActionBtn);

        // 4. Populate the data
        txtRouteName.setText(clickedRoute.routeName);
        txtTransportType.setText(clickedRoute.transportType + " Service");

        // 5. Handle the button click
        btnAction.setOnClickListener(v -> {
            bottomSheetDialog.dismiss();

            // Fire an Intent here to navigate the user to the ticket purchasing screen
            // passing the specific route ID so the next screen knows what to load.
            Toast.makeText(MainActivity.this,
                    "Opening details for " + clickedRoute.routeName,
                    Toast.LENGTH_SHORT).show();

        /* Example Intent:
        Intent intent = new Intent(MainActivity.this, ProviderListActivity.class);
        intent.putExtra("TRANSPORT_TYPE", clickedRoute.transportType);
        intent.putExtra("ROUTE_ID", clickedRoute.routeId);
        startActivity(intent);
        */
        });

        // 6. Show the sheet smoothly sliding up from the bottom
        bottomSheetDialog.show();
    }

    private void enableUserLocation() {
        if (mMap == null) return;

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {

            mMap.setMyLocationEnabled(true);
            mMap.getUiSettings().setMyLocationButtonEnabled(true);

            // First, center immediately on Gqeberha as a clean default view so it never shows all of Africa
            LatLng gqeberhaDefault = new LatLng(-33.9608, 25.6022);
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(gqeberhaDefault, 30f));

            // Then, animate to the user's actual GPS coordinates once retrieved
            fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
                if (location != null && mMap != null) {
                    LatLng currentLatLng = new LatLng(location.getLatitude(), location.getLongitude());
                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 15f));
                }
            });
        }
    }


    // showRouteBottomSheet(), enableUserLocation(), and checkLocationPermissions() remain identical to your original code.
}