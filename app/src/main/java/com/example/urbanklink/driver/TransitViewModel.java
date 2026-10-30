package com.example.urbanklink.driver;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.urbanklink.database.DestStop;
import com.example.urbanklink.database.LocalRoute;
import com.example.urbanklink.database.Stop;

import java.util.ArrayList;
import java.util.List;

public class TransitViewModel extends AndroidViewModel {
    private final TransitRepository repository;

    // State Variables
    private List<DestStop> rawServerQueue = new ArrayList<>();
    private int highestClearedSequence = 0;

    // LiveData streams for UI
    private final MutableLiveData<DestStop> lastClearedStop = new MutableLiveData<>();
    private final MutableLiveData<List<DestStop>> activeFilteredQueue = new MutableLiveData<>();
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();

    // LiveData streams for Map
    private final MutableLiveData<LocalRoute> activeMapRoute = new MutableLiveData<>();
    private final MutableLiveData<List<Stop>> activeRouteStops = new MutableLiveData<>();

    public TransitViewModel(@NonNull Application application) {
        super(application);
        repository = new TransitRepository(application);

        // Restore state
        highestClearedSequence = repository.getHighestClearedSequence();
        lastClearedStop.setValue(repository.getSavedLastClearedStop());

        List<DestStop> savedQueue = repository.getSavedServerQueue();
        if (savedQueue != null) {
            rawServerQueue = savedQueue;
            applySequenceFilter();
        }
    }

    // Getters
    public LiveData<DestStop> getLastClearedStop() { return lastClearedStop; }
    public LiveData<List<DestStop>> getActiveFilteredQueue() { return activeFilteredQueue; }
    public LiveData<LocalRoute> getActiveMapRoute() { return activeMapRoute; }
    public LiveData<List<Stop>> getActiveRouteStops() { return activeRouteStops; }
    public LiveData<String> getErrorMessage() { return errorMessage; }

    // LINK: Called by TransitTelemetry.onCreate()
    public void initializeDataAndSockets() {
        // 1. Sync SQL server routes to Room database
        repository.syncStopsAndRoutes(() -> {
            // 2. Fetch the newly synced route data for the map
            int activeRouteId = repository.getActiveRouteId();
            if (activeRouteId != -1) {
                repository.getMapDataFromRoom(activeRouteId, activeMapRoute, activeRouteStops);
            }
        });

        // 3. Open WebSocket for live passenger updates
        int activeBusId = repository.getActiveBusId();
        if (activeBusId != -1) {
            MutableLiveData<List<DestStop>> incomingSignalRQueue = new MutableLiveData<>();
            repository.connectSignalR(activeBusId, incomingSignalRQueue, errorMessage);

            // Observe raw SignalR updates and process them
            incomingSignalRQueue.observeForever(freshQueue -> {
                if (freshQueue != null) {
                    rawServerQueue = freshQueue;
                    applySequenceFilter();
                    saveState();
                }
            });
        }
    }

    // LINK: Called by TransitTelemetry.animateRouteProgression() when driver taps ARRIVED
    public void markArrivedAtCurrentStop() {
        List<DestStop> currentFiltered = activeFilteredQueue.getValue();
        if (currentFiltered != null && !currentFiltered.isEmpty()) {
            DestStop current = currentFiltered.get(0);
            highestClearedSequence = current.getSequenceNumber();
            lastClearedStop.setValue(current);

            applySequenceFilter();
            saveState();
        }
    }

    // The core business logic: No Time Travel
    private void applySequenceFilter() {
        List<DestStop> filtered = new ArrayList<>();
        for (DestStop stop : rawServerQueue) {
            if (stop.getSequenceNumber() > highestClearedSequence) {
                filtered.add(stop);
            }
        }
        activeFilteredQueue.postValue(filtered);
    }

    private void saveState() {
        repository.saveQueueState(highestClearedSequence, lastClearedStop.getValue(), rawServerQueue);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        repository.disconnectSignalR(); // Cleanly close WebSocket when Activity dies
    }
}
