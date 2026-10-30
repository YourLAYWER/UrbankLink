package com.example.urbanklink.driver;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

public class TelemetryViewModel extends AndroidViewModel {
    private TelemetryRepository repository;

    // Data Streams observed by ScannerTelemetry.java
    private MutableLiveData<Integer> activeShiftId = new MutableLiveData<>();
    private MutableLiveData<Integer> currentPassengers = new MutableLiveData<>();
    private MutableLiveData<Boolean> ticketValidationSuccess = new MutableLiveData<>();
    private MutableLiveData<String> errorMessage = new MutableLiveData<>();
    private MutableLiveData<Boolean> requiresLogout = new MutableLiveData<>();

    public TelemetryViewModel(@NonNull Application application) {
        super(application);
        repository = new TelemetryRepository(application);

        // Initialize passenger count from persistent storage on startup
        currentPassengers.setValue(repository.getSavedPassengerCount());
    }

    // Getters for Activity observation
    public LiveData<Integer> getActiveShiftId() { return activeShiftId; }
    public LiveData<Integer> getCurrentPassengers() { return currentPassengers; }
    public LiveData<Boolean> getTicketValidationSuccess() { return ticketValidationSuccess; }
    public LiveData<String> getErrorMessage() { return errorMessage; }
    public LiveData<Boolean> getRequiresLogout() { return requiresLogout; }

    // LINK: Called by ScannerTelemetry.onCreate() -> Calls TelemetryRepository.fetchActiveShift()
    public void loadActiveShift() {
        String authHeader = repository.getAuthHeader();
        if (authHeader == null) {
            requiresLogout.postValue(true);
            return;
        }
        repository.fetchActiveShift(authHeader, activeShiftId, errorMessage);
    }

    // LINK: Called by ScannerTelemetry.processImageProxy() -> Calls TelemetryRepository.validateTicket()
    public void processScannedQr(String qrData) {
        if (qrData == null || !qrData.toLowerCase().startsWith("urbanlink-")) {
            errorMessage.postValue("Invalid QR: Not an UrbanLink Ticket");
            return;
        }
        repository.validateTicket(qrData, ticketValidationSuccess, errorMessage);
    }

    // LINK: Called by ScannerTelemetry after valid QR scan OR PosTelemetry cash sale result
    public void addPassengers(int addedCount) {
        int newCount = (currentPassengers.getValue() != null ? currentPassengers.getValue() : 0) + addedCount;
        updatePassengerCount(newCount);
    }

    // LINK: Internal helper -> Calls TelemetryRepository.savePassengerCount()
    private void updatePassengerCount(int newCount) {
        repository.savePassengerCount(newCount);
        currentPassengers.postValue(newCount);
    }
}
