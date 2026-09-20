package com.example.urbanklink;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class HeaderManager {

    public static void loadHeaderData(Activity activity, View rootView) {
        // Find header views from the included layout root or activity view
        TextView txtBusTag = rootView.findViewById(R.id.txtHeaderBusTag);
        TextView txtRouteName = rootView.findViewById(R.id.txtHeaderRouteName);
        TextView txtRouteDirection = rootView.findViewById(R.id.txtHeaderRouteDirection);

        // Retrieve saved auth token (Bearer token)
        SharedPreferences prefs = activity.getSharedPreferences("UrbanLinkPrefs", Context.MODE_PRIVATE);
        String token = prefs.getString("JWT_TOKEN", "");

        // Call API via Retrofit interface
        UrbanLinkApiService apiService = ApiClient.getClient(activity).create(UrbanLinkApiService.class);
        apiService.getActiveShiftInfo().enqueue(new Callback<ShiftInfoResponse>() {
            @Override
            public void onResponse(Call<ShiftInfoResponse> call, Response<ShiftInfoResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    ShiftInfoResponse info = response.body();

                    // Populate UI fields
                    if (txtBusTag != null) txtBusTag.setText("BUS #" + info.getBusFleetNumber());
                    if (txtRouteName != null) txtRouteName.setText(info.getRouteName().toUpperCase());
                    if (txtRouteDirection != null) txtRouteDirection.setText(info.getRouteDisplay());

                    // Save routeId and baseFare locally for the POS screen
                    SharedPreferences prefs = activity.getSharedPreferences("UrbanLinkPrefs", Context.MODE_PRIVATE);
                    prefs.edit()
                            .putInt("ACTIVE_ROUTE_ID", info.getRouteId())
                            .putFloat("ACTIVE_BASE_FARE", (float) info.getBaseFare()) // Save base fare
                            .apply();
                } else {
                    Toast.makeText(activity, "Failed to load shift metadata", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<ShiftInfoResponse> call, Throwable t) {
                Toast.makeText(activity, "Network error loading header", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
