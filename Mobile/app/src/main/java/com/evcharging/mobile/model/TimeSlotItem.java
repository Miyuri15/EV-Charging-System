//package com.evcharging.mobile.model;
//
//public class TimeSlotItem {
//    public String timeSlotId;     // for /timeslot? returns 'timeSlotId'
//    public String formattedStart; // when available
//    public String formattedEnd;   // when available
//    public String status;         // "Available"/"Booked"
//    public String startTime;      // ISO
//    public String endTime;        // ISO
//
//    @Override public String toString() {
//        if (formattedStart != null && formattedEnd != null) return formattedStart + " → " + formattedEnd;
//        return (startTime != null ? startTime : "?") + " → " + (endTime != null ? endTime : "?");
//    }
//}
package com.evcharging.mobile.model;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class TimeSlotItem {
    public String timeSlotId;
    public String startTime;
    public String endTime;
    public boolean isAvailable;

    @Override
    public String toString() {
        return getFormattedRange();
    }

    public String getStartTime() {
        return startTime;
    }

    public String getEndTime() {
        return endTime;
    }


    private String getFormattedRange() {
        String startFormatted = formatTime(startTime);
        String endFormatted = formatTime(endTime);
        return startFormatted + " - " + endFormatted;
    }

    private String formatTime(String rawTime) {
        if (rawTime == null || rawTime.isEmpty()) return "";
        try {
            // Handle MongoDB-like ISO format (e.g., 2025-10-13T05:15:00Z)
            SimpleDateFormat inFmt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());
            inFmt.setTimeZone(TimeZone.getTimeZone("UTC"));

            // Output format for user readability
            SimpleDateFormat outFmt = new SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault());
            outFmt.setTimeZone(TimeZone.getDefault());

            Date date = inFmt.parse(rawTime);
            return outFmt.format(date);
        } catch (ParseException e) {
            try {
                // Fallback for MongoDB /Date(1697184000000)/
                if (rawTime.contains("/Date(")) {
                    long ms = Long.parseLong(rawTime.replaceAll("[^0-9]", ""));
                    Date date = new Date(ms);
                    SimpleDateFormat outFmt = new SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault());
                    return outFmt.format(date);
                }
            } catch (Exception ignored) {}
            return rawTime;
        }
    }
}
