package com.example.urbanklink;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class RouteStatusResponse {
    @SerializedName("hasDelay")
    private boolean hasDelay;

    @SerializedName("alert")
    private AlertDetail alert;

    @SerializedName("ticketStatus")
    private String ticketStatus;

    @SerializedName("completedRouteIds")
    private List<Integer> completedRouteIds;

    public boolean isHasDelay() {
        return hasDelay;
    }

    public AlertDetail getAlert() {
        return alert;
    }

    public String getTicketStatus() { return ticketStatus; }
    public List<Integer> getCompletedRouteIds() { return completedRouteIds; }

    // Nested class to map the incident details
    public static class AlertDetail {

        @SerializedName("message")
        private String message;

        @SerializedName("reportedAt")
        private String reportedAt;

        @SerializedName("isEmergency")
        private boolean isEmergency;

        public String getMessage() {
            return message;
        }

        public String getReportedAt() {
            return reportedAt;
        }

        public boolean isEmergency() {
            return isEmergency;
        }
    }
}
