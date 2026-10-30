package com.example.urbanklink.driver;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.lifecycle.MutableLiveData;

import com.example.urbanklink.network.ApiClient;
import com.example.urbanklink.network.UrbanLinkApiService;
import com.example.urbanklink.requests.IncidentReportRequest;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class IncidentRepository {
    private final UrbanLinkApiService apiService;
    private final SharedPreferences prefs;

    public IncidentRepository(Application application) {
        this.apiService = ApiClient.getClient(application).create(UrbanLinkApiService.class);
        this.prefs = application.getSharedPreferences("UrbanLinkPrefs", Context.MODE_PRIVATE);
    }

    public String getAuthHeader() {
        String token = prefs.getString("JWT_TOKEN", null);
        return token != null ? "Bearer " + token.replace("\"", "").trim() : null;
    }

    // LINK: Triggered by IncidentViewModel to log the report to the C# backend
    public void submitIncident(IncidentReportRequest request, MutableLiveData<Boolean> successResult, MutableLiveData<String> errorLiveData) {
        String authHeader = getAuthHeader();
        if (authHeader == null) {
            errorLiveData.postValue("Session expired");
            return;
        }

        apiService.logIncident(authHeader, request).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (response.isSuccessful()) {
                    successResult.postValue(true);
                } else {
                    errorLiveData.postValue("Failed to submit: " + response.code());
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                errorLiveData.postValue("Network error: " + t.getMessage());
            }
        });
    }
}
