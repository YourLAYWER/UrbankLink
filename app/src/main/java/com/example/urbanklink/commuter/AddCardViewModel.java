package com.example.urbanklink.commuter;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.urbanklink.requests.AddCardRequest;

public class AddCardViewModel extends AndroidViewModel {
    private WalletRepository repository;

    private MutableLiveData<PaymentCard> saveSuccess = new MutableLiveData<>();
    private MutableLiveData<String> errorMessage = new MutableLiveData<>();
    private MutableLiveData<Boolean> isLoading = new MutableLiveData<>();

    public AddCardViewModel(@NonNull Application application) {
        super(application);
        repository = new WalletRepository(application);
    }

    public LiveData<PaymentCard> getSaveSuccess() { return saveSuccess; }
    public LiveData<String> getErrorMessage() { return errorMessage; }
    public LiveData<Boolean> getIsLoading() { return isLoading; }

    public void saveNewCard(String cardHolderName, String fullCardNumber, String expiryDate, boolean isDefault) {
        if (cardHolderName.isEmpty() || fullCardNumber.length() < 15 || expiryDate.length() < 5) {
            android.util.Log.d("CARD_DEBUG", "2. Validation Failed! Missing fields.");
            errorMessage.setValue("Please fill out all fields correctly.");
            return;
        }

        isLoading.setValue(true);

        // Generate the masked number expected by your C# backend ("**** **** **** 1234")
        String digits = fullCardNumber.replaceAll("\\D", "");
        String last4 = digits.substring(digits.length() - 4);
        String maskedNumber = "**** **** **** " + last4;

        android.util.Log.d("CARD_DEBUG", "2. Validation Passed. Masked Number: " + maskedNumber);

        AddCardRequest request = new AddCardRequest(cardHolderName, maskedNumber, expiryDate, isDefault);
        repository.addCard(request, saveSuccess, errorMessage);
    }
}