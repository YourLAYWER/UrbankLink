package com.example.urbanklink.driver;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.example.urbanklink.R;
import com.example.urbanklink.response.ShiftInfoResponse;
import com.example.urbanklink.network.UrbanLinkApiService;
import com.example.urbanklink.network.ApiClient;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class HeaderManager {

    public static void loadHeaderData(Activity activity, View rootView, Runnable onComplete) {
        // 1. Find tablet header views (Driver POS)
        TextView txtBusTag = rootView.findViewById(R.id.txtHeaderBusTag);
        TextView txtRouteName = rootView.findViewById(R.id.txtHeaderRouteName);
        TextView txtRouteDirection = rootView.findViewById(R.id.txtHeaderRouteDirection);

        // 2. Find phone header views (Conductor ScannerActivity)
        //TextView txtRouteInfo = rootView.findViewById(R.id.txtRouteInfo);

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

                    // Populate Tablet UI fields
                    if (txtBusTag != null) txtBusTag.setText("BUS #" + info.getBusFleetNumber());
                    if (txtRouteName != null) txtRouteName.setText(info.getRouteName().toUpperCase());
                    if (txtRouteDirection != null) txtRouteDirection.setText(info.getRouteDisplay());

                    // Populate Phone UI field (ScannerActivity)

                    // Save EVERYTHING locally for the QuickSale and Incident screens
                    SharedPreferences prefs = activity.getSharedPreferences("UrbanLinkPrefs", Context.MODE_PRIVATE);
                    prefs.edit()
                            .putInt("ACTIVE_ROUTE_ID", info.getRouteId())
                            .putFloat("ACTIVE_BASE_FARE", (float) info.getBaseFare())
                            .putString("ACTIVE_ROUTE_NAME", info.getRouteName())
                            .putString("ACTIVE_BUS_FLEET", info.getBusFleetNumber())
                            .apply();
                    if (onComplete != null) {
                        onComplete.run();
                    }
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