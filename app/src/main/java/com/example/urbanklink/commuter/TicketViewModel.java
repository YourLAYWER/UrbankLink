package com.example.urbanklink.commuter;

import android.app.Application;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.urbanklink.payment.PaymentReadyCallback;
import com.example.urbanklink.payment.PaymentStrategy;
import com.example.urbanklink.payment.ProviderCardStrategy;
import com.example.urbanklink.response.JourneyResponse;
import com.example.urbanklink.response.RouteCalculateResponse;

public class TicketViewModel extends AndroidViewModel {
    private TicketRepository repository;

    // Data streams for the UI
    private MutableLiveData<RouteCalculateResponse> routeDetails = new MutableLiveData<>();
    private MutableLiveData<JourneyResponse> bookingSuccess = new MutableLiveData<>();
    private MutableLiveData<String> errorMessage = new MutableLiveData<>();
    private MutableLiveData<Boolean> isLoading = new MutableLiveData<>();

    // The active payment strategy
    private PaymentStrategy currentPaymentStrategy;

    public TicketViewModel(@NonNull Application application) {
        super(application);
        repository = new TicketRepository(application);
        // Default to a Provider Card if the user hasn't selected anything yet
        currentPaymentStrategy = new ProviderCardStrategy();
    }

    public LiveData<RouteCalculateResponse> getRouteDetails() { return routeDetails; }
    public LiveData<JourneyResponse> getBookingSuccess() { return bookingSuccess; }
    public LiveData<String> getErrorMessage() { return errorMessage; }
    public LiveData<Boolean> getIsLoading() { return isLoading; }

    /**
     * Allows the Activity to switch payment strategies when the user taps a different radio button
     */
    public void setPaymentStrategy(PaymentStrategy strategy) {
        this.currentPaymentStrategy = strategy;
    }

    public void loadRouteDetails(int startId, int destId) {
        repository.calculateRoute(startId, destId, routeDetails, errorMessage);
    }

    /**
     * Executes the payment using the currently selected strategy.
     */
    public void executePayment(Context uiContext, int startId, int destId, double amount) {
        isLoading.setValue(true);

        // Delegate to the strategy to handle any required UI (like a PIN entry dialog)
        currentPaymentStrategy.processPaymentUI(uiContext, amount, new PaymentReadyCallback() {
            @Override
            public void onPaymentReady(String paymentMethodIdentifier) {
                // The strategy finished its validation, now tell the repository to hit the backend
                repository.bookJourney(startId, destId, paymentMethodIdentifier, bookingSuccess, errorMessage);
            }

            @Override
            public void onPaymentCancelled(String reason) {
                isLoading.setValue(false);
                errorMessage.setValue(reason);
            }
        });
    }
}
