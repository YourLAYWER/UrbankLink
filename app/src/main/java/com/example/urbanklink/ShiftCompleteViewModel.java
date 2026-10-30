package com.example.urbanklink;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.urbanklink.response.EndShiftResponse;

public class ShiftCompleteViewModel extends AndroidViewModel {
    private final ShiftCompleteRepository repository;

    // UI State Streams
    private final MutableLiveData<String> routeName = new MutableLiveData<>("Loading...");
    private final MutableLiveData<String> ticketsScanned = new MutableLiveData<>("-");
    private final MutableLiveData<String> totalCash = new MutableLiveData<>("Calculating...");

    // Action State Streams
    private final MutableLiveData<Boolean> isSummaryLoaded = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> isClosingShift = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> logoutEvent = new MutableLiveData<>();
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();

    public ShiftCompleteViewModel(@NonNull Application application) {
        super(application);
        repository = new ShiftCompleteRepository(application);
    }

    // Getters for Activity Observers
    public LiveData<String> getRouteName() { return routeName; }
    public LiveData<String> getTicketsScanned() { return ticketsScanned; }
    public LiveData<String> getTotalCash() { return totalCash; }
    public LiveData<Boolean> getIsSummaryLoaded() { return isSummaryLoaded; }
    public LiveData<Boolean> getIsClosingShift() { return isClosingShift; }
    public LiveData<Boolean> getLogoutEvent() { return logoutEvent; }
    public LiveData<String> getErrorMessage() { return errorMessage; }

    // LINK: Called by ShiftCompleteActivity.onCreate()
    public void loadSummary() {
        String authHeader = repository.getAuthHeader();
        if (authHeader == null) {
            triggerLogout();
            return;
        }

        MutableLiveData<EndShiftResponse> summaryData = new MutableLiveData<>();
        repository.fetchShiftSummary(authHeader, summaryData, errorMessage);

        // Process the raw data from the repository into UI-friendly strings
        summaryData.observeForever(data -> {
            if (data != null && data.shiftSummary != null) {
                routeName.postValue(data.shiftSummary.routeName);
                ticketsScanned.postValue(String.valueOf(data.shiftSummary.digitalTicketsScanned));
                totalCash.postValue(String.format("R%.2f", data.shiftSummary.totalCashHandover));
                isSummaryLoaded.postValue(true);
            }
        });
    }

    // LINK: Called by ShiftCompleteActivity when button is clicked
    public void finalizeShift() {
        String authHeader = repository.getAuthHeader();
        if (authHeader == null) {
            triggerLogout();
            return;
        }

        isClosingShift.setValue(true);
        MutableLiveData<Boolean> closeSuccess = new MutableLiveData<>();

        repository.closeShift(authHeader, closeSuccess, errorMessage);

        closeSuccess.observeForever(success -> {
            if (success != null && success) {
                triggerLogout();
            }
        });
    }

    private void triggerLogout() {
        repository.clearSession();
        logoutEvent.postValue(true);
    }

    public void resetErrorState() {
        isClosingShift.setValue(false);
    }
}
