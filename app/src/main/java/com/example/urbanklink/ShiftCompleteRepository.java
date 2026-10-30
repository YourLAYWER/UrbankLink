package com.example.urbanklink;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.lifecycle.MutableLiveData;

import com.example.urbanklink.network.ApiClient;
import com.example.urbanklink.network.UrbanLinkApiService;
import com.example.urbanklink.response.EndShiftResponse;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ShiftCompleteRepository {
    private final UrbanLinkApiService apiService;
    private final SharedPreferences prefs;

    public ShiftCompleteRepository(Application application) {
        this.apiService = ApiClient.getClient(application).create(UrbanLinkApiService.class);
        this.prefs = application.getSharedPreferences("UrbanLinkPrefs", Context.MODE_PRIVATE);
    }

    public String getAuthHeader() {
        String token = prefs.getString("JWT_TOKEN", null);
        return token != null ? "Bearer " + token.replace("\"", "").trim() : null;
    }

    // LINK: Called after shift is successfully closed on the backend
    public void clearSession() {
        prefs.edit().clear().apply();
    }

    // LINK: Triggered by ShiftCompleteViewModel on screen load
    public void fetchShiftSummary(String authHeader, MutableLiveData<EndShiftResponse> summaryData, MutableLiveData<String> errorData) {
        apiService.getShiftSummary(authHeader).enqueue(new Callback<EndShiftResponse>() {
            @Override
            public void onResponse(Call<EndShiftResponse> call, Response<EndShiftResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    summaryData.postValue(response.body());
                } else {
                    errorData.postValue("Failed to load shift summary.");
                }
            }

            @Override
            public void onFailure(Call<EndShiftResponse> call, Throwable t) {
                errorData.postValue("Network error: " + t.getMessage());
            }
        });
    }

    // LINK: Triggered by ShiftCompleteViewModel when driver confirms logout
    public void closeShift(String authHeader, MutableLiveData<Boolean> successEvent, MutableLiveData<String> errorData) {
        apiService.endShift(authHeader).enqueue(new Callback<EndShiftResponse>() {
            @Override
            public void onResponse(Call<EndShiftResponse> call, Response<EndShiftResponse> response) {
                if (response.isSuccessful()) {
                    successEvent.postValue(true);
                } else {
                    errorData.postValue("Failed to close shift. Try again.");
                }
            }

            @Override
            public void onFailure(Call<EndShiftResponse> call, Throwable t) {
                errorData.postValue("Network error closing shift: " + t.getMessage());
            }
        });
    }
}
