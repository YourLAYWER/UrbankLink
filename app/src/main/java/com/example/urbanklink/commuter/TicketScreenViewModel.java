package com.example.urbanklink.commuter;

import android.app.Application;
import android.graphics.Bitmap;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.urbanklink.database.LocalRoute;
import com.example.urbanklink.response.RouteStatusResponse;
import com.google.zxing.BarcodeFormat;
import com.journeyapps.barcodescanner.BarcodeEncoder;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TicketScreenViewModel extends AndroidViewModel {
    private TicketScreenRepository repository;
    private ExecutorService executor = Executors.newSingleThreadExecutor();

    private MutableLiveData<TicketDisplayData> activeTicketData = new MutableLiveData<>();
    private MutableLiveData<LocalRoute> historicalRouteData = new MutableLiveData<>();
    private MutableLiveData<RouteStatusResponse> liveStatusData = new MutableLiveData<>();
    private MutableLiveData<Bitmap> qrCodeBitmap = new MutableLiveData<>();
    private MutableLiveData<String> errorMessage = new MutableLiveData<>();

    public TicketScreenViewModel(@NonNull Application application) {
        super(application);
        repository = new TicketScreenRepository(application);
    }

    public LiveData<TicketDisplayData> getActiveTicketData() { return activeTicketData; }
    public LiveData<LocalRoute> getHistoricalRouteData() { return historicalRouteData; }
    public LiveData<RouteStatusResponse> getLiveStatusData() { return liveStatusData; }
    public LiveData<Bitmap> getQrCodeBitmap() { return qrCodeBitmap; }
    public LiveData<String> getErrorMessage() { return errorMessage; }

    public void initializeTicket(String ticketRef, String status, int historyRouteId) {
        if (ticketRef != null && !ticketRef.isEmpty()) {
            generateQRCode(ticketRef);
            if ("ACTIVE".equalsIgnoreCase(status)) {
                repository.loadSpecificTicket(ticketRef, activeTicketData);
            } else {
                repository.loadHistoricalRoute(historyRouteId, historicalRouteData);
            }
        } else {
            repository.loadActiveTicket(activeTicketData, errorMessage);
        }
    }

    public void refreshLiveStatus(String ticketRef) {
        if (ticketRef != null && !ticketRef.isEmpty()) {
            repository.fetchLiveStatus(ticketRef, liveStatusData, errorMessage);
        }
    }

    private void generateQRCode(String ticketRef) {
        executor.execute(() -> {
            try {
                String ticketData = "URBANLINK-" + ticketRef.toUpperCase();
                BarcodeEncoder barcodeEncoder = new BarcodeEncoder();
                Bitmap bitmap = barcodeEncoder.encodeBitmap(ticketData, BarcodeFormat.QR_CODE, 600, 600);
                qrCodeBitmap.postValue(bitmap);
            } catch (Exception e) {
                errorMessage.postValue("Failed to generate QR Code");
            }
        });
    }
}