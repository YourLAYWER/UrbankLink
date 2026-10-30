package com.example.urbanklink.driver;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

public class PosViewModel extends AndroidViewModel {
    private TelemetryRepository repository;
    private SharedPreferences prefs;

    // State Variables
    private MutableLiveData<Integer> quantity = new MutableLiveData<>(1);
    private MutableLiveData<Float> dynamicBaseFare = new MutableLiveData<>(15.00f);
    private MutableLiveData<Boolean> saleSuccess = new MutableLiveData<>();
    private MutableLiveData<String> errorMessage = new MutableLiveData<>();
    private MutableLiveData<Boolean> isProcessing = new MutableLiveData<>(false);

    public PosViewModel(@NonNull Application application) {
        super(application);
        repository = new TelemetryRepository(application);
        prefs = application.getSharedPreferences("UrbanLinkPrefs", Context.MODE_PRIVATE);
        loadBaseFare();
    }

    // Getters for observers
    public LiveData<Integer> getQuantity() { return quantity; }
    public LiveData<Float> getDynamicBaseFare() { return dynamicBaseFare; }
    public LiveData<Boolean> getSaleSuccess() { return saleSuccess; }
    public LiveData<String> getErrorMessage() { return errorMessage; }
    public LiveData<Boolean> getIsProcessing() { return isProcessing; }

    public void loadBaseFare() {
        dynamicBaseFare.setValue(prefs.getFloat("ACTIVE_BASE_FARE", 15.00f));
    }

    public void setQuantity(int q) {
        quantity.setValue(q);
    }

    public void incrementQuantity() {
        int current = quantity.getValue() != null ? quantity.getValue() : 1;
        if (current < 10) {
            quantity.setValue(current + 1);
        } else {
            errorMessage.setValue("Maximum 10 tickets per transaction");
        }
    }

    public void decrementQuantity() {
        int current = quantity.getValue() != null ? quantity.getValue() : 1;
        if (current > 1) {
            quantity.setValue(current - 1);
        }
    }

    public void processCashSale() {
        isProcessing.setValue(true);
        int currentQuantity = quantity.getValue() != null ? quantity.getValue() : 1;
        repository.processCashSale(currentQuantity, saleSuccess, errorMessage);
    }

    public void finalizeSaleAndUpdateCount() {
        int soldQuantity = quantity.getValue() != null ? quantity.getValue() : 1;
        int currentCount = repository.getSavedPassengerCount();
        repository.savePassengerCount(currentCount + soldQuantity);
    }
}
