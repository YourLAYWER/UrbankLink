package com.example.urbanklink.commuter;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.lifecycle.MutableLiveData;

import com.example.urbanklink.network.ApiClient;
import com.example.urbanklink.network.UrbanLinkApiService;
import com.example.urbanklink.requests.AddCardRequest;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class WalletRepository {
    private UrbanLinkApiService apiService;
    private SharedPreferences prefs;

    public WalletRepository(Application application) {
        apiService = ApiClient.getClient(application).create(UrbanLinkApiService.class);
        prefs = application.getSharedPreferences("UrbanLinkPrefs", Context.MODE_PRIVATE);
    }

    private String getAuthHeader() {
        String token = prefs.getString("JWT_TOKEN", null);
        return token != null ? "Bearer " + token.replace("\"", "").trim() : null;
    }

    public void fetchCards(MutableLiveData<List<PaymentCard>> cardsData, MutableLiveData<String> errorData) {
        String auth = getAuthHeader();
        if (auth == null) return;

        apiService.getMyCards(auth).enqueue(new Callback<List<PaymentCard>>() {
            @Override
            public void onResponse(Call<List<PaymentCard>> call, Response<List<PaymentCard>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    cardsData.postValue(response.body());
                } else {
                    errorData.postValue("Failed to load cards.");
                }
            }
            @Override
            public void onFailure(Call<List<PaymentCard>> call, Throwable t) {
                errorData.postValue("Network error while loading cards.");
            }
        });
    }

    public void setDefaultCard(String cardId, MutableLiveData<Boolean> successData, MutableLiveData<String> errorData) {
        String auth = getAuthHeader();

        // Failsafe check
        if (cardId == null) {
            errorData.postValue("Card ID is missing.");
            return;
        }

        apiService.setDefaultCard(auth, cardId).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (response.isSuccessful()) successData.postValue(true);
                else errorData.postValue("Failed to update default card.");
            }
            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                android.util.Log.e("API_ERROR", "Set Default Failed: " + t.getMessage(), t);
                errorData.postValue("Network error: " + t.getMessage());
            }
        });
    }

    public void deleteCard(String cardId, MutableLiveData<Boolean> successData, MutableLiveData<String> errorData) {
        String auth = getAuthHeader();
        apiService.deleteCard(auth, cardId).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (response.isSuccessful()) successData.postValue(true);
                else errorData.postValue("Failed to remove card.");
            }
            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                errorData.postValue("Network error.");
            }
        });
    }

    public void addCard(AddCardRequest request, MutableLiveData<PaymentCard> successData, MutableLiveData<String> errorData) {
        String auth = getAuthHeader();

        android.util.Log.d("CARD_DEBUG", "3. Dispatching Request. Token Present? " + (auth != null));

        if (auth == null) return;

        apiService.addCard(auth, request).enqueue(new Callback<PaymentCard>() {
            @Override
            public void onResponse(Call<PaymentCard> call, Response<PaymentCard> response) {
                if (response.isSuccessful() && response.body() != null) {
                    successData.postValue(response.body());
                } else {
                    errorData.postValue("Failed to save card. Please check your details.");
                }
            }
            @Override
            public void onFailure(Call<PaymentCard> call, Throwable t) {
                errorData.postValue("Network error while saving card.");
            }
        });
    }
}