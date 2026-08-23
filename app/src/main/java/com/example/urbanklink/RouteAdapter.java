package com.example.urbanklink;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import java.util.Locale;

public class RouteAdapter extends RecyclerView.Adapter<RouteAdapter.RouteViewHolder>{
    private List<TransitRouteData> routeList;

    public RouteAdapter(List<TransitRouteData> routeList) {
        this.routeList = routeList;
    }

    @NonNull
    @Override
    public RouteViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_route, parent, false);
        return new RouteViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RouteViewHolder holder, int position) {
        TransitRouteData route = routeList.get(position);

        // 1. Set Name and format the Fare cleanly as South African Rand
        holder.tvRouteName.setText(route.routeName);
        holder.tvBaseFare.setText(String.format(Locale.getDefault(), "R%.2f", route.baseFare));

        // 2. Dynamically build the schedule text block
        StringBuilder scheduleText = new StringBuilder();

        if (route.schedules != null && !route.schedules.isEmpty()) {
            for (int i = 0; i < route.schedules.size(); i++) {
                RouteScheduleData schedule = route.schedules.get(i);

                // Format: "Weekdays: 06:00 - 20:00 (Every 30 mins)"
                scheduleText.append(schedule.daysOfOperation)
                        .append(": ")
                        .append(schedule.firstDeparture)
                        .append(" - ")
                        .append(schedule.lastDeparture)
                        .append(" (Every ")
                        .append(schedule.frequencyMinutes)
                        .append(" mins)");

                // Add a line break if it's not the last schedule item
                if (i < route.schedules.size() - 1) {
                    scheduleText.append("\n");
                }
            }
        } else {
            scheduleText.append("No active schedules found.");
        }

        // 3. Apply the final string to the UI
        holder.tvSchedules.setText(scheduleText.toString());
    }

    @Override
    public int getItemCount() {
        return routeList != null ? routeList.size() : 0;
    }

    public static class RouteViewHolder extends RecyclerView.ViewHolder {
        TextView tvRouteName, tvBaseFare, tvSchedules;

        public RouteViewHolder(@NonNull View itemView) {
            super(itemView);
            tvRouteName = itemView.findViewById(R.id.tvRouteName);
            tvBaseFare = itemView.findViewById(R.id.tvBaseFare);
            tvSchedules = itemView.findViewById(R.id.tvSchedules);
        }
    }
}
