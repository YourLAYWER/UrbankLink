package com.example.urbanklink.driver;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.urbanklink.requests.IncidentReportRequest;

public class IncidentViewModel extends AndroidViewModel {
    private final IncidentRepository repository;

    // State Variables
    private final MutableLiveData<Integer> selectedCategory = new MutableLiveData<>(-1);
    private final MutableLiveData<Boolean> isProcessing = new MutableLiveData<>(false);

    // LiveData streams for UI
    private final MutableLiveData<Boolean> reportSuccess = new MutableLiveData<>();
    private final MutableLiveData<Boolean> sosSuccess = new MutableLiveData<>();
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();

    public IncidentViewModel(@NonNull Application application) {
        super(application);
        repository = new IncidentRepository(application);
    }

    // Getters for observers
    public LiveData<Integer> getSelectedCategory() { return selectedCategory; }
    public LiveData<Boolean> getIsProcessing() { return isProcessing; }
    public LiveData<Boolean> getReportSuccess() { return reportSuccess; }
    public LiveData<Boolean> getSosSuccess() { return sosSuccess; }
    public LiveData<String> getErrorMessage() { return errorMessage; }

    // Form logic
    public void selectCategory(int categoryEnum) {
        selectedCategory.setValue(categoryEnum);
    }

    public void resetCategory() {
        selectedCategory.setValue(-1);
    }

    // LINK: Called by IncidentTelemetry after GPS coordinates are acquired
    public void submitEmergencySOS(double lat, double lng) {
        isProcessing.setValue(true);
        IncidentReportRequest request = new IncidentReportRequest(true, 3, "CRITICAL SOS TRIGGERED FROM TABLET", lat, lng);
        repository.submitIncident(request, sosSuccess, errorMessage);
    }

    // LINK: Called by IncidentTelemetry after GPS coordinates are acquired
    public void submitRoutineReport(String details, double lat, double lng) {
        if (selectedCategory.getValue() == null || selectedCategory.getValue() == -1) {
            errorMessage.setValue("Please select an issue category");
            return;
        }

        isProcessing.setValue(true);
        IncidentReportRequest request = new IncidentReportRequest(false, selectedCategory.getValue(), details, lat, lng);
        repository.submitIncident(request, reportSuccess, errorMessage);
    }

    public void resetProcessingState() {
        isProcessing.setValue(false);
    }
}
