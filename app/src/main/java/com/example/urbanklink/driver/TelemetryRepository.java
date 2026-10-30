package com.example.urbanklink.driver;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.lifecycle.MutableLiveData;

import com.example.urbanklink.network.ApiClient;
import com.example.urbanklink.network.UrbanLinkApiService;
import com.example.urbanklink.requests.ScanTicketRequest;
import com.example.urbanklink.response.EndShiftResponse;
import com.example.urbanklink.response.ScanTicketResponse;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class TelemetryRepository {
    private UrbanLinkApiService apiService;
    private SharedPreferences prefs;

    public TelemetryRepository(Application application) {
        apiService = ApiClient.getClient(application).create(UrbanLinkApiService.class);
        prefs = application.getSharedPreferences("UrbanLinkPrefs", Context.MODE_PRIVATE);
    }

    // LINK: Triggered by TelemetryViewModel.loadActiveShift()
    public String getAuthHeader() {
        String token = prefs.getString("JWT_TOKEN", null);
        return token != null ? "Bearer " + token.replace("\"", "").trim() : null;
    }

    // LINK: Triggered by TelemetryViewModel constructor on startup
    public int getSavedPassengerCount() {
        return prefs.getInt("ACTIVE_PASSENGER_COUNT", 0);
    }

    // LINK: Triggered by TelemetryViewModel.updatePassengerCount()
    public void savePassengerCount(int count) {
        prefs.edit().putInt("ACTIVE_PASSENGER_COUNT", count).apply();
    }

    // LINK: Triggered by TelemetryViewModel.loadActiveShift() -> Updates activeShiftId / errorMessage LiveData
    public void fetchActiveShift(String authHeader, MutableLiveData<Integer> shiftIdLiveData, MutableLiveData<String> errorLiveData) {
        apiService.getShiftSummary(authHeader).enqueue(new Callback<EndShiftResponse>() {
            @Override
            public void onResponse(Call<EndShiftResponse> call, Response<EndShiftResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    shiftIdLiveData.postValue(response.body().shiftSummary.shiftId);
                } else {
                    errorLiveData.postValue("No active shift found!");
                }
            }

            @Override
            public void onFailure(Call<EndShiftResponse> call, Throwable t) {
                errorLiveData.postValue("Failed to connect to server");
            }
        });
    }

    // LINK: Triggered by TelemetryViewModel.processScannedQr() -> Updates ticketValidationSuccess / errorMessage LiveData
    public void validateTicket(String qrData, MutableLiveData<Boolean> validationResult, MutableLiveData<String> errorLiveData) {
        String cleanQrData = qrData.replaceAll("(?i)urbanlink-", "").trim();
        ScanTicketRequest request = new ScanTicketRequest(cleanQrData);

        apiService.scanTicket(request).enqueue(new Callback<ScanTicketResponse>() {
            @Override
            public void onResponse(Call<ScanTicketResponse> call, Response<ScanTicketResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    validationResult.postValue(true);
                } else {
                    validationResult.postValue(false); // Ticket is invalid or used
                }
            }

            @Override
            public void onFailure(Call<ScanTicketResponse> call, Throwable t) {
                errorLiveData.postValue("Network Error during scan");
            }
        });
    }
}