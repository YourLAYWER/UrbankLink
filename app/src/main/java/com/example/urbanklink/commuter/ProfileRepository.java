package com.example.urbanklink.commuter;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.lifecycle.MutableLiveData;

import com.example.urbanklink.database.LocalUser;
import com.example.urbanklink.database.UrbanLinkDatabase;
import com.example.urbanklink.network.ApiClient;
import com.example.urbanklink.network.UrbanLinkApiService;
import com.example.urbanklink.response.UserProfileResponse;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ProfileRepository {
    private UrbanLinkDatabase db;
    private UrbanLinkApiService apiService;
    private SharedPreferences prefs;
    private ExecutorService executor;

    public ProfileRepository(Application application) {
        db = UrbanLinkDatabase.getInstance(application);
        apiService = ApiClient.getClient(application).create(UrbanLinkApiService.class);
        prefs = application.getSharedPreferences("UrbanLinkPrefs", Context.MODE_PRIVATE);
        executor = Executors.newSingleThreadExecutor();
    }

    /**
     * Fetches the user profile. It instantly posts the local cached version to the UI,
     * then silently checks the server for updates. If the server has new data, it updates Room and posts again.
     */
    public void fetchUserProfile(MutableLiveData<LocalUser> userLiveData) {
        // 1. Immediately load the offline profile from Room
        executor.execute(() -> {
            LocalUser localUser = db.userDao().getUser();
            if (localUser != null) {
                userLiveData.postValue(localUser);
            }
        });

        // 2. Poll the server for fresh data
        String token = prefs.getString("JWT_TOKEN", null);
        if (token == null) return;

        String authHeader = "Bearer " + token.replace("\"", "").trim();

        apiService.getUserProfile(authHeader).enqueue(new Callback<UserProfileResponse>() {
            @Override
            public void onResponse(Call<UserProfileResponse> call, Response<UserProfileResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    UserProfileResponse data = response.body();

                    // Create the updated user entity
                    LocalUser freshUser = new LocalUser(data.getId(), data.getName(), data.getEmail(), data.getCommuterProfile());

                    // Save to Room and notify the UI
                    executor.execute(() -> {
                        db.userDao().insertUser(freshUser);
                        userLiveData.postValue(freshUser);
                    });
                }
            }

            @Override
            public void onFailure(Call<UserProfileResponse> call, Throwable t) {
                // Silent failure: the UI is already displaying the offline Room data
            }
        });
    }

    /**
     * Clears all local data and preferences to securely log the user out.
     * @param onComplete Callback triggered when database operations finish
     */
    public void logoutUser(Runnable onComplete) {
        // Remove JWT Token
        prefs.edit().remove("JWT_TOKEN").apply();

        // Wipe local database records securely on a background thread
        executor.execute(() -> {
            db.userDao().clearUser();
            // Optional: db.ticketDao().clearAllTickets();
            onComplete.run();
        });
    }
}