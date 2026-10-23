package com.example.urbanklink;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class UserRepository {
    private UrbanLinkApiService apiService;
    private SharedPreferences prefs;

    public UserRepository(Application application) {
        apiService = ApiClient.getClient(application).create(UrbanLinkApiService.class);
        prefs = application.getSharedPreferences("UrbanLinkPrefs", Context.MODE_PRIVATE);
    }

    public LiveData<String> getUserFirstName() {
        MutableLiveData<String> firstNameData = new MutableLiveData<>();
        String token = prefs.getString("JWT_TOKEN", null);

        if (token != null) {
            String authHeader = "Bearer " + token.replace("\"", "").trim();
            apiService.getUserProfile(authHeader).enqueue(new Callback<UserProfileResponse>() {
                @Override
                public void onResponse(Call<UserProfileResponse> call, Response<UserProfileResponse> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        String fullName = response.body().getName();
                        String firstName = fullName != null && fullName.contains(" ")
                                ? fullName.substring(0, fullName.indexOf(" "))
                                : fullName;
                        firstNameData.postValue(firstName);
                    }
                }
                @Override
                public void onFailure(Call<UserProfileResponse> call, Throwable t) {
                    // Leave empty, or post an error state
                }
            });
        }
        return firstNameData;
    }
}
