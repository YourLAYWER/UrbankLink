package com.example.urbanklink;

import com.google.gson.annotations.SerializedName;

public class RouteStatusResponse {
    @SerializedName("hasDelay")
    private boolean hasDelay;

    @SerializedName("alert")
    private AlertDetail alert;

    public boolean isHasDelay() {
        return hasDelay;
    }

    public AlertDetail getAlert() {
        return alert;
    }

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
