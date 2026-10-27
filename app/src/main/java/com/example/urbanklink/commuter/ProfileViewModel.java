package com.example.urbanklink.commuter;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.urbanklink.database.LocalUser;

public class ProfileViewModel extends AndroidViewModel {
    private ProfileRepository repository;

    // Holds the user's data for the UI to observe
    private MutableLiveData<LocalUser> userProfile = new MutableLiveData<>();

    // Triggers navigation when logout is fully completed
    private MutableLiveData<Boolean> logoutComplete = new MutableLiveData<>();

    public ProfileViewModel(@NonNull Application application) {
        super(application);
        repository = new ProfileRepository(application);
    }

    public LiveData<LocalUser> getUserProfile() {
        return userProfile;
    }

    public LiveData<Boolean> getLogoutComplete() {
        return logoutComplete;
    }

    /**
     * Triggers the repository to fetch the profile (local + remote sync)
     */
    public void loadProfile() {
        repository.fetchUserProfile(userProfile);
    }

    /**
     * Executes the logout sequence in the repository and updates the UI state upon completion
     */
    public void performLogout() {
        repository.logoutUser(() -> {
            logoutComplete.postValue(true);
        });
    }
}