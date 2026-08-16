package com.example.urbanklink;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class HistoryBottomSheet extends BottomSheetDialogFragment {
    private RecyclerView recyclerView;
    private HistoryAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.dialog_history_bottom_sheet, container, false);

        recyclerView = view.findViewById(R.id.recyclerHistory);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        fetchHistoryFromApi();

        return view;
    }

    private void fetchHistoryFromApi() {
        // 1. Grab the JWT Token (matching the exact way you did it in Ticket_details)
        SharedPreferences prefs = requireActivity().getSharedPreferences("UrbanLinkPrefs", Context.MODE_PRIVATE);
        String token = prefs.getString("JWT_TOKEN", null);

        if (token == null) {
            Toast.makeText(getContext(), "Authentication error. Please log in again.", Toast.LENGTH_LONG).show();
            dismiss();
            return;
        }

        // Format the token safely
        String authHeader = "Bearer " + token.replace("\"", "").trim();

        // 2. Initialize Retrofit exactly the way your app does it
        UrbanLinkApiService apiService = ApiClient.getClient(null).create(UrbanLinkApiService.class);

        // 3. Execute the network call
        apiService.getJourneyHistory(authHeader).enqueue(new Callback<List<JourneyHistory>>() {
            @Override
            public void onResponse(Call<List<JourneyHistory>> call, Response<List<JourneyHistory>> response) {
                if (response.isSuccessful() && response.body() != null) {

                    // The C# endpoint returned the data! Feed it to the Adapter.
                    List<JourneyHistory> historyData = response.body();

                    // Make sure 'private HistoryAdapter adapter;' is declared at the top of your class!
                    adapter = new HistoryAdapter(historyData);
                    recyclerView.setAdapter(adapter);

                } else {
                    Toast.makeText(getContext(), "Failed to load history. Code: " + response.code(), Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<List<JourneyHistory>> call, Throwable t) {
                Toast.makeText(getContext(), "Network error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }
}
