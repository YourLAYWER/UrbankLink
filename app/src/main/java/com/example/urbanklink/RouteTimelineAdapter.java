package com.example.urbanklink;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;

public class RouteTimelineAdapter extends RecyclerView.Adapter<RouteTimelineAdapter.TimelineViewHolder> {

    private List<RouteSegmentDto> segments = new ArrayList<>();

    public void setSegments(List<RouteSegmentDto> segments) {
        this.segments = segments;
        notifyDataSetChanged(); // Tells the UI to refresh with the new data
    }

    @NonNull
    @Override
    public TimelineViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_route_node, parent, false);
        return new TimelineViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TimelineViewHolder holder, int position) {
        RouteSegmentDto segment = segments.get(position);

        holder.txtStopName.setText(segment.getStopName());
        holder.txtTime.setText(segment.getArrivalTime());
        holder.txtTransitLine.setText(segment.getTransitLine());

        // DESIGN LOGIC: Start and End are Cyan, Middle transfers are Grey
        boolean isFirst = (position == 0);
        boolean isLast = (position == segments.size() - 1);

        if (isFirst || isLast) {
            holder.nodeDot.setCardBackgroundColor(Color.parseColor("#00FFFF")); // Bright Cyan
        } else {
            holder.nodeDot.setCardBackgroundColor(Color.parseColor("#333333")); // Dark Grey
        }

        // Hide the connecting line for the very last node so it doesn't trail off into nothing
        if (isLast) {
            holder.nodeLine.setVisibility(View.INVISIBLE);
        } else {
            holder.nodeLine.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public int getItemCount() {
        return segments.size();
    }

    // This static class acts as the blueprint wrapper for your XML layout
    static class TimelineViewHolder extends RecyclerView.ViewHolder {
        MaterialCardView nodeDot;
        View nodeLine;
        TextView txtStopName, txtTime, txtTransitLine;

        public TimelineViewHolder(@NonNull View itemView) {
            super(itemView);
            nodeDot = itemView.findViewById(R.id.nodeDot);
            nodeLine = itemView.findViewById(R.id.nodeLine);
            txtStopName = itemView.findViewById(R.id.txtStopName);
            txtTime = itemView.findViewById(R.id.txtTime);
            txtTransitLine = itemView.findViewById(R.id.txtTransitLine);
        }
    }
}