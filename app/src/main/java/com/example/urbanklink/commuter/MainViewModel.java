package com.example.urbanklink.commuter;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.urbanklink.database.LocalRoute;
import com.example.urbanklink.database.Stop;

import java.util.List;

public class MainViewModel extends AndroidViewModel {

    private UserRepository userRepo;
    private TransitRepository transitRepo;

    // Data streams the UI will watch
    private LiveData<String> userNameLiveData;
    private LiveData<List<LocalRoute>> mapRoutesLiveData;
    private LiveData<List<Stop>> allStopsLiveData;

    // We use a MutableLiveData here so we can change it when the user taps a different route
    private MutableLiveData<Integer> selectedRouteId = new MutableLiveData<>();
    private LiveData<List<Stop>> selectedRouteStopsLiveData;

    public MainViewModel(@NonNull Application application) {
        super(application);
        userRepo = new UserRepository(application);
        transitRepo = new TransitRepository(application);

        userNameLiveData = userRepo.getUserFirstName();
        mapRoutesLiveData = transitRepo.getMapRoutes();
        allStopsLiveData = transitRepo.getAllStops();

        // This is a special mapping: anytime selectedRouteId changes, it automatically
        // fetches the new stops from the repository! (Requires lifecycle-livedata dependency)
        selectedRouteStopsLiveData = androidx.lifecycle.Transformations.switchMap(
                selectedRouteId,
                id -> transitRepo.getStopsForRoute(id)
        );
    }

    public LiveData<String> getUserName() { return userNameLiveData; }
    public LiveData<List<LocalRoute>> getMapRoutes() { return mapRoutesLiveData; }
    public LiveData<List<Stop>> getAllStops() { return allStopsLiveData; }
    public LiveData<List<Stop>> getSelectedRouteStops() { return selectedRouteStopsLiveData; }

    public void selectRoute(int routeId) {
        selectedRouteId.setValue(routeId);
    }
}
