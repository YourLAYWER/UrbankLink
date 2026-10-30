package com.example.urbanklink;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.urbanklink.response.EndShiftResponse;
import com.example.urbanklink.auth.LoginActivity;
import com.example.urbanklink.commuter.HeaderUtils;
import com.example.urbanklink.network.ApiClient;
import com.example.urbanklink.network.UrbanLinkApiService;
import com.google.android.material.button.MaterialButton;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ShiftCompleteActivity extends AppCompatActivity {

    private ShiftCompleteViewModel viewModel;

    private TextView txtRouteCompleted, txtTicketsScanned, txtTotalCash;
    private MaterialButton btnConfirmLogout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_shift_complete);

        // 1. Initialize ViewModel
        viewModel = new ViewModelProvider(this).get(ShiftCompleteViewModel.class);

        // 2. Setup Header
        HeaderUtils.setupHeader(this, true, false, false);

        // 3. Link UI Components
        txtRouteCompleted = findViewById(R.id.txtRouteCompleted);
        txtTicketsScanned = findViewById(R.id.txtTicketsScanned);
        txtTotalCash = findViewById(R.id.txtTotalCash);
        btnConfirmLogout = findViewById(R.id.btnConfirmLogout);

        // 4. Set Button Listeners
        btnConfirmLogout.setOnClickListener(v -> viewModel.finalizeShift());

        // 5. Setup Observers
        setupObservers();

        // 6. Trigger data fetch
        viewModel.loadSummary();
    }

    private void setupObservers() {
        // LINK: Bind text fields to LiveData
        viewModel.getRouteName().observe(this, name -> txtRouteCompleted.setText(name));
        viewModel.getTicketsScanned().observe(this, count -> txtTicketsScanned.setText(count));
        viewModel.getTotalCash().observe(this, cash -> txtTotalCash.setText(cash));

        // LINK: Manage button state while loading summary
        viewModel.getIsSummaryLoaded().observe(this, isLoaded -> {
            if (isLoaded != null && isLoaded && Boolean.FALSE.equals(viewModel.getIsClosingShift().getValue())) {
                btnConfirmLogout.setEnabled(true);
            } else {
                btnConfirmLogout.setEnabled(false);
            }
        });

        // LINK: Manage button state when shift is actively closing
        viewModel.getIsClosingShift().observe(this, isClosing -> {
            if (isClosing != null && isClosing) {
                btnConfirmLogout.setEnabled(false);
                btnConfirmLogout.setText("Closing Shift...");
            } else if (Boolean.TRUE.equals(viewModel.getIsSummaryLoaded().getValue())) {
                btnConfirmLogout.setEnabled(true);
                btnConfirmLogout.setText("Confirm & Logout");
            }
        });

        // LINK: Execute navigation on successful shift close
        viewModel.getLogoutEvent().observe(this, shouldLogout -> {
            if (shouldLogout != null && shouldLogout) {
                performLogout();
            }
        });

        // LINK: Handle network errors gracefully
        viewModel.getErrorMessage().observe(this, error -> {
            if (error != null) {
                Toast.makeText(this, error, Toast.LENGTH_SHORT).show();
                viewModel.resetErrorState(); // Re-enables the button if shift closure fails
            }
        });
    }

    private void performLogout() {
        Intent intent = new Intent(ShiftCompleteActivity.this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}