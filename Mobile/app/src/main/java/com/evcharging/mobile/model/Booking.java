package com.evcharging.mobile.model;

public class Booking {
    private String bookingId;
    private String stationId;
    private String slotId;
    private String ownerId;
    private String status;
    private String startTime;
    private String endTime;

    public Booking() {}


    public Booking(String bookingId, String stationId, String slotId, String ownerId,
                   String status, String startTime, String endTime) {
        this.bookingId = bookingId;
        this.stationId = stationId;
        this.slotId = slotId;
        this.ownerId = ownerId;
        this.status = status;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    // Getters
    public String getBookingId() { return bookingId; }
    public String getStationId() { return stationId; }
    public String getSlotId() { return slotId; }
    public String getOwnerId() { return ownerId; }
    public String getStatus() { return status; }
    public String getStartTime() { return startTime; }
    public String getEndTime() { return endTime; }

    // Setters
    public void setBookingId(String bookingId) { this.bookingId = bookingId; }
    public void setStationId(String stationId) { this.stationId = stationId; }
    public void setSlotId(String slotId) { this.slotId = slotId; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }
    public void setStatus(String status) { this.status = status; }
    public void setStartTime(String startTime) { this.startTime = startTime; }
    public void setEndTime(String endTime) { this.endTime = endTime; }

    @Override
    public String toString() {
        return "Booking ID: " + bookingId + "\n" +
               "Status: " + status + "\n" +
               "Start: " + startTime + "\n" +
               "End: " + endTime + "\n" +
               "Slot ID" + slotId;
    }
}
