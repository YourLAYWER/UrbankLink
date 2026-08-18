package com.example.urbanklink;

import androidx.recyclerview.widget.RecyclerView;

import android.content.Intent;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;
import java.util.Locale;
public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder> {

    private final List<JourneyHistory> historyList;

    public HistoryAdapter(List<JourneyHistory> historyList) {
        this.historyList = historyList;
    }
    @NonNull
    @Override
    public HistoryAdapter.HistoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_journey_history, parent, false);
        return new HistoryViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull HistoryAdapter.HistoryViewHolder holder, int position) {

        JourneyHistory item = historyList.get(position);

        holder.txtDate.setText(item.getDate());
        holder.txtRoute.setText(item.getRouteSummary());
        holder.txtStatus.setText(item.getStatus());

        // Format the cost to "R15.00"
        holder.txtCost.setText(String.format(Locale.getDefault(), "R%.2f", item.getCost()));

        // Optional UI Polish: Change text color based on status
        if ("EXPIRED".equalsIgnoreCase(item.getStatus())) {
            holder.txtStatus.setTextColor(Color.parseColor("#FF8A8A")); // Soft red
        } else if ("REDEEMED".equalsIgnoreCase(item.getStatus())) {
            holder.txtStatus.setTextColor(Color.parseColor("#A0A0A0")); // Greyed out
        } else {
            holder.txtStatus.setTextColor(Color.parseColor("#00FF7F")); // Bright green for ACTIVE
        }

        // ==========================================
        // NEW: MAKE THE ITEM CLICKABLE
        // ==========================================
        holder.itemView.setOnClickListener(v -> {
            // If the ticket is expired or redeemed, you might want to prevent opening it,
            // or open it but show a "USED" watermark. For now, let's open them all!

            Intent intent = new Intent(v.getContext(), TicketScreen.class);
            intent.putExtra("TICKET_REF", item.getTicketReference());

            // Start the TicketScreen Activity
            v.getContext().startActivity(intent);
        });
    }

    @Override
    public int getItemCount() {
        return historyList != null ? historyList.size() : 0;
    }
    static class HistoryViewHolder extends RecyclerView.ViewHolder {
        TextView txtDate, txtCost, txtRoute, txtStatus;

        public HistoryViewHolder(@NonNull View itemView) {
            super(itemView);
            txtDate = itemView.findViewById(R.id.txtDate);
            txtCost = itemView.findViewById(R.id.txtCost);
            txtRoute = itemView.findViewById(R.id.txtRoute);
            txtStatus = itemView.findViewById(R.id.txtStatus);
        }
    }
}
