package com.example.urbanklink;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class ProviderAdapter extends RecyclerView.Adapter<ProviderAdapter.ProviderViewHolder>{
    private List<ProviderSummary> providerList;
    private OnProviderClickListener listener;

    // 1. Create an interface to handle clicks
    public interface OnProviderClickListener {
        void onProviderClick(ProviderSummary provider);
    }

    // Constructor
    public ProviderAdapter(List<ProviderSummary> providerList, OnProviderClickListener listener) {
        this.providerList = providerList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ProviderViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Inflate the custom dark-mode card XML we created
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_provider, parent, false);
        return new ProviderViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ProviderViewHolder holder, int position) {
        ProviderSummary provider = providerList.get(position);

        // Set the provider's name to the TextView
        holder.tvProviderName.setText(provider.getName());

        // Attach the click listener to the entire card
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onProviderClick(provider);
            }
        });
    }

    @Override
    public int getItemCount() {
        return providerList != null ? providerList.size() : 0;
    }

    // 2. The ViewHolder that holds the UI elements
    public static class ProviderViewHolder extends RecyclerView.ViewHolder {
        TextView tvProviderName;

        public ProviderViewHolder(@NonNull View itemView) {
            super(itemView);
            tvProviderName = itemView.findViewById(R.id.tvProviderName);
        }
    }
}
