package com.example.urbanklink.commuter;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.List;

public class ManageCardsViewModel extends AndroidViewModel {
    private WalletRepository repository;

    private MutableLiveData<List<PaymentCard>> savedCards = new MutableLiveData<>();
    private MutableLiveData<Boolean> actionSuccess = new MutableLiveData<>();
    private MutableLiveData<String> errorMessage = new MutableLiveData<>();

    public ManageCardsViewModel(@NonNull Application application) {
        super(application);
        repository = new WalletRepository(application);
    }

    public LiveData<List<PaymentCard>> getSavedCards() { return savedCards; }
    public LiveData<Boolean> getActionSuccess() { return actionSuccess; }
    public LiveData<String> getErrorMessage() { return errorMessage; }

    public void loadCards() {
        repository.fetchCards(savedCards, errorMessage);
    }

    public void setCardAsDefault(String cardId) {
        repository.setDefaultCard(cardId, actionSuccess, errorMessage);
    }

    public void deleteCard(String cardId) {
        repository.deleteCard(cardId, actionSuccess, errorMessage);
    }
}