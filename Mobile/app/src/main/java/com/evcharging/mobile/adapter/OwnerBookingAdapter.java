package com.evcharging.mobile.adapter;

import android.content.res.Resources;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
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
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

public class OwnerBookingAdapter extends RecyclerView.Adapter<OwnerBookingAdapter.BookingViewHolder> {

    private List<BookingItem> bookings;
    private final OnBookingActionListener listener;

    // Backward compatible constructor
    public OwnerBookingAdapter(List<BookingItem> bookings, OnBookingClickListener legacyListener) {
        this.bookings = bookings;
        this.listener = new OnBookingActionListener() {
            @Override
            public void onBookingClick(BookingItem booking) {
                legacyListener.onBookingClick(booking);
            }

            @Override
            public void onUpdateClick(BookingItem booking) { }

            @Override
            public void onCancelClick(BookingItem booking) { }

            @Override
            public void onTimeRestrictionClick(BookingItem booking) { }
        };
    }

    // Modern constructor
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
        return bookings != null ? bookings.size() : 0;
    }

    public void setData(List<BookingItem> newBookings) {
        this.bookings = newBookings;
        notifyDataSetChanged();
    }

    public void updateList(List<BookingItem> newBookings) {
        this.bookings.clear();
        this.bookings.addAll(newBookings);
        notifyDataSetChanged();
    }

    // ----------------------------------------------------
    // ViewHolder
    // ----------------------------------------------------
    public static class BookingViewHolder extends RecyclerView.ViewHolder {

        private final TextView tvStationName, tvStatus, tvDate, tvTime, tvSlotNumber, tvDuration;
        private final LinearLayout statusBadge;
        private final View cardBooking;
        private final MaterialButton btnViewDetails;

        public BookingViewHolder(@NonNull View itemView) {
            super(itemView);
            tvStationName = itemView.findViewById(R.id.tvStationName);
            tvStatus = itemView.findViewById(R.id.tvStatus);
            tvDate = itemView.findViewById(R.id.tvDate);
            tvTime = itemView.findViewById(R.id.tvTime);
            tvSlotNumber = itemView.findViewById(R.id.tvSlotNumber);
            tvDuration = itemView.findViewById(R.id.tvDuration);
            statusBadge = itemView.findViewById(R.id.statusBadge);
            cardBooking = itemView.findViewById(R.id.cardBooking);
            btnViewDetails = itemView.findViewById(R.id.btnViewDetails);
        }

        public void bind(BookingItem booking, OnBookingActionListener listener) {
            tvStationName.setText(booking.getStationName());
            tvStatus.setText(booking.getStatus());

            String[] timeParts = formatTimeDisplay(booking.getStartTime(), booking.getEndTime()).split(" • ");
            if (timeParts.length >= 2) {
                tvDate.setText(timeParts[0]);
                tvTime.setText(timeParts[1]);
            }

            tvSlotNumber.setText(formatSlotInfo(booking.getSlotNumber()));
            tvDuration.setText(calculateDuration(booking.getStartTime(), booking.getEndTime()));
            setStatusStyle(booking.getStatus());

            if ("Charging".equalsIgnoreCase(booking.getStatus())) {
                int progress = calculateDefaultProgress(booking.getStartTime(), booking.getEndTime());
            }

            setupActionButtons(booking, listener);
            btnViewDetails.setOnClickListener(v -> listener.onBookingClick(booking));
            cardBooking.setOnClickListener(v -> listener.onBookingClick(booking));
        }

        private void setupActionButtons(BookingItem booking, OnBookingActionListener listener) {
            MaterialButton btnUpdate = itemView.findViewById(R.id.btnUpdate);
            MaterialButton btnCancel = itemView.findViewById(R.id.btnCancel);
            if (btnUpdate == null || btnCancel == null) return;

            btnUpdate.setVisibility(View.GONE);
            btnCancel.setVisibility(View.GONE);

            String status = booking.getStatus();
            boolean canModify = canModifyBooking(booking.getStartTime());

            if ("Pending".equalsIgnoreCase(status) || "Approved".equalsIgnoreCase(status)) {
                if ("Pending".equalsIgnoreCase(status)) {
                    if (canModify) {
                        btnUpdate.setVisibility(View.VISIBLE);
                        btnCancel.setVisibility(View.VISIBLE);
                        btnUpdate.setOnClickListener(v -> listener.onUpdateClick(booking));
                    } else {
                        btnCancel.setVisibility(View.VISIBLE);
                        btnCancel.setAlpha(0.6f);
                    }
                } else if ("Approved".equalsIgnoreCase(status)) {
                    btnCancel.setVisibility(View.VISIBLE);
                    if (!canModify) btnCancel.setAlpha(0.6f);
                }

                btnCancel.setOnClickListener(v -> {
                    if (canModify) listener.onCancelClick(booking);
                    else listener.onTimeRestrictionClick(booking);
                });
            }
        }

        private boolean canModifyBooking(String startTimeUtc) {
            try {
                SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault());
                format.setTimeZone(TimeZone.getTimeZone("UTC"));
                Date startDate = format.parse(startTimeUtc);
                Date now = new Date();
                long diffHours = (startDate.getTime() - now.getTime()) / (1000 * 60 * 60);
                return diffHours >= 12;
            } catch (Exception e) {
                return false;
            }
        }

        private void setStatusStyle(String status) {
            int backgroundRes;
            switch (status.toLowerCase()) {
                case "pending": backgroundRes = R.drawable.bg_status_pending; break;
                case "approved": backgroundRes = R.drawable.bg_status_approved; break;
                case "charging": backgroundRes = R.drawable.bg_status_charging; break;
                case "finalized": backgroundRes = R.drawable.bg_status_finalized; break;
                case "cancelled":
                case "expired": backgroundRes = R.drawable.bg_status_cancelled; break;
                default: backgroundRes = R.drawable.bg_status_default; break;
            }
            statusBadge.setBackgroundResource(backgroundRes);
            tvStatus.setTextColor(android.graphics.Color.WHITE);
        }

        // ----------------------------------------------------
        // Time Handling (UTC → Local)
        // ----------------------------------------------------
        private String formatTimeDisplay(String startTimeUtc, String endTimeUtc) {
            try {
                SimpleDateFormat utcFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault());
                utcFormat.setTimeZone(TimeZone.getTimeZone("UTC"));

                Date startDate = utcFormat.parse(startTimeUtc);
                Date endDate = utcFormat.parse(endTimeUtc);

                SimpleDateFormat dateFmt = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
                SimpleDateFormat timeFmt = new SimpleDateFormat("h:mm a", Locale.getDefault());

                TimeZone colombo = TimeZone.getTimeZone("Asia/Colombo");
                dateFmt.setTimeZone(colombo);
                timeFmt.setTimeZone(colombo);

                return dateFmt.format(startDate) + " • " + timeFmt.format(startDate) + " - " + timeFmt.format(endDate);
            } catch (Exception e) {
                return "Date not specified • Time not specified";
            }
        }

        private String formatSlotInfo(String slotNumber) {
            return (slotNumber != null && !slotNumber.isEmpty())
                    ? "Slot #" + slotNumber
                    : "Slot info not available";
        }

        private String calculateDuration(String startTimeUtc, String endTimeUtc) {
            try {
                SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault());
                fmt.setTimeZone(TimeZone.getTimeZone("UTC"));
                Date start = fmt.parse(startTimeUtc);
                Date end = fmt.parse(endTimeUtc);
                long diffMs = end.getTime() - start.getTime();
                long hours = TimeUnit.MILLISECONDS.toHours(diffMs);
                long mins = TimeUnit.MILLISECONDS.toMinutes(diffMs) % 60;
                return (hours > 0 ? hours + "h " : "") + mins + "m";
            } catch (Exception e) {
                return "Duration not available";
            }
        }

        private int calculateDefaultProgress(String startTimeUtc, String endTimeUtc) {
            try {
                SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault());
                fmt.setTimeZone(TimeZone.getTimeZone("UTC"));
                Date start = fmt.parse(startTimeUtc);
                Date end = fmt.parse(endTimeUtc);
                Date now = new Date();
                if (now.after(start) && now.before(end)) {
                    long total = end.getTime() - start.getTime();
                    long elapsed = now.getTime() - start.getTime();
                    return (int) ((elapsed * 100) / total);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return 65;
        }
    }

    // ----------------------------------------------------
    // Interfaces
    // ----------------------------------------------------
    public interface OnBookingActionListener {
        void onBookingClick(BookingItem booking);
        void onUpdateClick(BookingItem booking);
        void onCancelClick(BookingItem booking);
        void onTimeRestrictionClick(BookingItem booking);
    }

    public interface OnBookingClickListener {
        void onBookingClick(BookingItem booking);
    }
}
