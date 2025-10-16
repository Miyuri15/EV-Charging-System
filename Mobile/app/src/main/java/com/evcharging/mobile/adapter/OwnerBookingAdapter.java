package com.evcharging.mobile.adapter;

import android.content.res.Resources;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.evcharging.mobile.R;
import com.evcharging.mobile.model.BookingItem;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class OwnerBookingAdapter extends RecyclerView.Adapter<OwnerBookingAdapter.BookingViewHolder> {

    private List<BookingItem> bookings;
    private final OnBookingActionListener listener;

    // Constructor for backward compatibility with OnBookingClickListener
    public OwnerBookingAdapter(List<BookingItem> bookings, OnBookingClickListener listener) {
        this.bookings = bookings;
        // Create a wrapper that implements OnBookingActionListener but only handles onBookingClick
        this.listener = new OnBookingActionListener() {
            @Override
            public void onBookingClick(BookingItem booking) {
                listener.onBookingClick(booking);
            }

            @Override
            public void onUpdateClick(BookingItem booking) {
                // Do nothing for old listeners - actions won't be shown in ChargingHistoryActivity
            }

            @Override
            public void onCancelClick(BookingItem booking) {
                // Do nothing for old listeners - actions won't be shown in ChargingHistoryActivity
            }

            @Override
            public void onTimeRestrictionClick(BookingItem booking) {
                // Do nothing for old listeners
            }
        };
    }

    // Constructor for new functionality with OnBookingActionListener
    public OwnerBookingAdapter(List<BookingItem> bookings, OnBookingActionListener listener) {
        this.bookings = bookings;
        this.listener = listener;
    }

    @NonNull
    @Override
    public BookingViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.owner_booking_item, parent, false);
        return new BookingViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull BookingViewHolder holder, int position) {
        BookingItem booking = bookings.get(position);
        holder.bind(booking, listener);
    }

    @Override
    public int getItemCount() {
        return bookings.size();
    }

    // Add this method to support ChargingHistoryActivity
    public void setData(List<BookingItem> newBookings) {
        this.bookings = newBookings;
        notifyDataSetChanged();
    }

    // Keep the updateList method for backward compatibility
    public void updateList(List<BookingItem> newBookings) {
        this.bookings.clear();
        this.bookings.addAll(newBookings);
        notifyDataSetChanged();
    }

    public static class BookingViewHolder extends RecyclerView.ViewHolder {

        private final TextView tvStationName, tvStatus, tvDate, tvTime, tvSlotNumber, tvDuration, tvProgressPercent;
        private final LinearLayout statusBadge, chargingProgressLayout;
        private final View cardBooking;
        private final LinearProgressIndicator progressCharging;
        private final MaterialButton btnViewDetails;

        public BookingViewHolder(@NonNull View itemView) {
            super(itemView);

            // Initialize all views from the new layout
            tvStationName = itemView.findViewById(R.id.tvStationName);
            tvStatus = itemView.findViewById(R.id.tvStatus);
            tvDate = itemView.findViewById(R.id.tvDate);
            tvTime = itemView.findViewById(R.id.tvTime);
            tvSlotNumber = itemView.findViewById(R.id.tvSlotNumber);
            tvDuration = itemView.findViewById(R.id.tvDuration);
            tvProgressPercent = itemView.findViewById(R.id.tvProgressPercent);

            // FIX: Use the correct ID from your layout
            statusBadge = itemView.findViewById(R.id.statusBadge); // This was the issue

            cardBooking = itemView.findViewById(R.id.cardBooking);
            chargingProgressLayout = itemView.findViewById(R.id.chargingProgressLayout);
            progressCharging = itemView.findViewById(R.id.progressCharging);
            btnViewDetails = itemView.findViewById(R.id.btnViewDetails);
        }

        public void bind(BookingItem booking, OnBookingActionListener listener) {

            // Debug: Check the actual status value
            Log.d("BookingDebug", "Booking status: '" + booking.getStatus() + "'");

            // Set basic info
            tvStationName.setText(booking.getStationName());
            tvStatus.setText(booking.getStatus());

            // Format and set time components separately
            String[] timeParts = formatTimeDisplay(booking.getStartTime(), booking.getEndTime()).split(" • ");
            if (timeParts.length >= 2) {
                tvDate.setText(timeParts[0]);
                tvTime.setText(timeParts[1]);
            } else {
                tvDate.setText("Date not specified");
                tvTime.setText("Time not specified");
            }

            // Format slot info
            String slotText = formatSlotInfo(booking.getSlotNumber());
            tvSlotNumber.setText(slotText);

            // Calculate and set duration
            String durationText = calculateDuration(booking.getStartTime(), booking.getEndTime());
            tvDuration.setText(durationText);

            // Set status style with dynamic background
            setStatusStyle(booking.getStatus());

            // Handle charging progress - show only for Charging status
            if ("Charging".equalsIgnoreCase(booking.getStatus())) {
                chargingProgressLayout.setVisibility(View.VISIBLE);
                int progress = calculateDefaultProgress(booking.getStartTime(), booking.getEndTime());
                progressCharging.setProgress(progress);
                tvProgressPercent.setText(progress + "%");
            } else {
                chargingProgressLayout.setVisibility(View.GONE);
            }

            setupActionButtons(booking, listener);

            // Set click listeners
            btnViewDetails.setOnClickListener(v -> listener.onBookingClick(booking));
            cardBooking.setOnClickListener(v -> listener.onBookingClick(booking));
        }

        private void setupActionButtons(BookingItem booking, OnBookingActionListener listener) {
            MaterialButton btnUpdate = itemView.findViewById(R.id.btnUpdate);
            MaterialButton btnCancel = itemView.findViewById(R.id.btnCancel);

            if (btnUpdate == null || btnCancel == null) {
                Log.e("BookingAdapter", "Action buttons not found in layout");
                return;
            }

            // Reset visibility
            btnUpdate.setVisibility(View.GONE);
            btnCancel.setVisibility(View.GONE);

            String status = booking.getStatus();
            String startTime = booking.getStartTime();

            // Check if within 12 hours
            boolean canModify = canModifyBooking(startTime);

            Log.d("BookingActions", "Status: " + status + ", CanModify: " + canModify + ", StartTime: " + startTime);

            // Only show action buttons for active bookings (Pending/Approved)
            if ("Pending".equalsIgnoreCase(status) || "Approved".equalsIgnoreCase(status)) {
                if ("Pending".equalsIgnoreCase(status)) {
                    // Pending: Can update and cancel
                    if (canModify) {
                        btnUpdate.setVisibility(View.VISIBLE);
                        btnCancel.setVisibility(View.VISIBLE);

                        btnUpdate.setOnClickListener(v -> {
                            listener.onUpdateClick(booking);
                        });
                    } else {
                        btnCancel.setVisibility(View.VISIBLE); // Can still cancel but with time restriction message
                        btnCancel.setAlpha(0.6f);
                    }
                } else if ("Approved".equalsIgnoreCase(status)) {
                    // Approved: Can only cancel
                    if (canModify) {
                        btnCancel.setVisibility(View.VISIBLE);
                    } else {
                        btnCancel.setVisibility(View.VISIBLE);
                        btnCancel.setAlpha(0.6f);
                    }
                }

                // Set cancel button listener
                btnCancel.setOnClickListener(v -> {
                    if (canModify) {
                        listener.onCancelClick(booking);
                    } else {
                        // Show time restriction message
                        listener.onTimeRestrictionClick(booking);
                    }
                });
            }
            // For Charging, Finalized, Cancelled, Expired status - no action buttons shown
        }

        private boolean canModifyBooking(String startTime) {
            try {
                SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());
                Date startDate = format.parse(startTime);
                Date now = new Date();

                long timeDifference = startDate.getTime() - now.getTime();
                long hoursDifference = timeDifference / (1000 * 60 * 60);

                return hoursDifference >= 12;
            } catch (ParseException e) {
                e.printStackTrace();
                return false;
            }
        }

        private void setStatusStyle(String status) {
            if (statusBadge == null) {
                Log.e("StatusDebug", "statusBadge is NULL!");
                return;
            }

            // Clear any previous background first
            statusBadge.setBackground(null);

            int textColor = android.graphics.Color.WHITE;
            String statusLower = status.toLowerCase();

            Log.d("StatusDebug", "Setting status: " + status + " (lower: " + statusLower + ")");

            int backgroundResId;
            switch (statusLower) {
                case "pending":
                    backgroundResId = R.drawable.bg_status_pending;
                    Log.d("StatusDebug", "Using PENDING background: " + backgroundResId);
                    break;
                case "approved":
                    backgroundResId = R.drawable.bg_status_approved;
                    Log.d("StatusDebug", "Using APPROVED background: " + backgroundResId);
                    break;
                case "charging":
                    backgroundResId = R.drawable.bg_status_charging;
                    Log.d("StatusDebug", "Using CHARGING background: " + backgroundResId);
                    break;
                case "finalized":
                    backgroundResId = R.drawable.bg_status_finalized;
                    Log.d("StatusDebug", "Using FINALIZED background: " + backgroundResId);
                    break;
                case "cancelled":
                case "expired":
                    backgroundResId = R.drawable.bg_status_cancelled;
                    Log.d("StatusDebug", "Using CANCELLED background: " + backgroundResId);
                    break;
                default:
                    backgroundResId = R.drawable.bg_status_default;
                    Log.d("StatusDebug", "Using DEFAULT background: " + backgroundResId);
                    break;
            }

            try {
                statusBadge.setBackgroundResource(backgroundResId);
                Log.d("StatusDebug", "Background set successfully for: " + status);
            } catch (Resources.NotFoundException e) {
                Log.e("StatusDebug", "Background resource not found: " + backgroundResId + " for status: " + status);
                // Fallback to default
                statusBadge.setBackgroundResource(R.drawable.bg_status_default);
            }

            if (tvStatus != null) {
                tvStatus.setTextColor(textColor);
            }
        }

        private String formatTimeDisplay(String startTime, String endTime) {
            try {
                if (startTime != null && endTime != null) {
                    // Extract date and time parts for better display
                    String startDate = extractDate(startTime);
                    String startTimePart = extractTime(startTime);
                    String endTimePart = extractTime(endTime);

                    return startDate + " • " + startTimePart + " - " + endTimePart;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            // Fallback
            return "Date not specified • Time not specified";
        }

        private String extractDate(String dateTime) {
            try {
                SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());
                SimpleDateFormat outputFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
                Date date = inputFormat.parse(dateTime);
                return outputFormat.format(date);
            } catch (ParseException e) {
                if (dateTime.contains("T")) {
                    return dateTime.split("T")[0]; // Return just the date part
                }
                return "Invalid Date";
            }
        }

        private String extractTime(String dateTime) {
            try {
                SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());
                SimpleDateFormat outputFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
                Date date = inputFormat.parse(dateTime);
                return outputFormat.format(date);
            } catch (ParseException e) {
                if (dateTime.contains("T")) {
                    String timePart = dateTime.split("T")[1];
                    if (timePart.length() >= 5) {
                        return timePart.substring(0, 5); // Return HH:mm
                    }
                }
                return "Invalid Time";
            }
        }

        private String formatSlotInfo(String slotNumber) {
            if (slotNumber != null && !slotNumber.isEmpty()) {
                return "Slot #" + slotNumber;
            }
            return "Slot info not available";
        }

        private String calculateDuration(String startTime, String endTime) {
            try {
                if (startTime != null && endTime != null) {
                    SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());
                    Date start = format.parse(startTime);
                    Date end = format.parse(endTime);

                    if (start != null && end != null) {
                        long diffInMillis = end.getTime() - start.getTime();
                        long hours = TimeUnit.MILLISECONDS.toHours(diffInMillis);
                        long minutes = TimeUnit.MILLISECONDS.toMinutes(diffInMillis) % 60;

                        if (hours > 0) {
                            return hours + " hour" + (hours > 1 ? "s" : "") +
                                    (minutes > 0 ? " " + minutes + " min" : "");
                        } else {
                            return minutes + " minutes";
                        }
                    }
                }
            } catch (ParseException e) {
                e.printStackTrace();
            }
            return "Duration not available";
        }

        private int calculateDefaultProgress(String startTime, String endTime) {
            try {
                if (startTime != null && endTime != null) {
                    SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());
                    Date start = format.parse(startTime);
                    Date end = format.parse(endTime);
                    Date now = new Date();

                    if (start != null && end != null && now.after(start) && now.before(end)) {
                        long totalDuration = end.getTime() - start.getTime();
                        long elapsed = now.getTime() - start.getTime();
                        return (int) ((elapsed * 100) / totalDuration);
                    }
                }
            } catch (ParseException e) {
                e.printStackTrace();
            }
            return 65; // Default progress
        }
    }

    // New interface with all action callbacks
    public interface OnBookingActionListener {
        void onBookingClick(BookingItem booking);
        void onUpdateClick(BookingItem booking);
        void onCancelClick(BookingItem booking);
        void onTimeRestrictionClick(BookingItem booking);
    }

    // Original interface for backward compatibility
    public interface OnBookingClickListener {
        void onBookingClick(BookingItem booking);
    }
}