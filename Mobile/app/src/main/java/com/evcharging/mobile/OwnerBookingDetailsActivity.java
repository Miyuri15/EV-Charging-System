package com.evcharging.mobile;

import android.animation.ObjectAnimator;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Base64;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.ScaleAnimation;
import android.view.animation.TranslateAnimation;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.content.FileProvider;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.evcharging.mobile.network.ApiClient;
import com.evcharging.mobile.network.ApiResponse;
import com.evcharging.mobile.session.SessionManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Locale;

public class OwnerBookingDetailsActivity extends AppCompatActivity {

    private SwipeRefreshLayout swipeRefresh;
    private TextView tvStatus, tvStation, tvSlot, tvTime, tvBookingId, tvQrNote;
    private ImageView ivQr, ivStatusIcon;
    private Button btnShareQr;
    private LinearLayout qrContainer, statusContainer;
    private CardView cardDetails;
    private ImageButton btnBack;
    private SessionManager session;
    private ApiClient api;

    private String bookingId, stationId, status, qrBase64;
    private int slotNumber;
    private long startMs, endMs;

    private final SimpleDateFormat fmt = new SimpleDateFormat("dd MMM yyyy, h:mm a", Locale.getDefault());
    private Bitmap qrBitmap;
    private com.evcharging.mobile.model.BookingItem currentBooking;
    private com.evcharging.mobile.network.ApiClient apiClient;
    private TextView tvReason;
    private LinearLayout qrSection, cancelReasonSection, timelineContainer;
    private ImageView ivStatusIndicator;
    private CardView cardHeader, cardTimeline;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_owner_booking_details);

        session = new SessionManager(this);
        api = new ApiClient(session);
        apiClient = new ApiClient(session);

        initializeViews();
        initializeEnhancedViews();
        setupEnhancedAnimations();

        // Set up back button click listener
        btnBack.setOnClickListener(v -> finish());


        // --- Handle both JSON and individual extras ---
        String bookingJson = getIntent().getStringExtra("booking");
        if (bookingJson != null) {
            // From OwnerBookingsActivity
            currentBooking = new com.google.gson.Gson().fromJson(bookingJson, com.evcharging.mobile.model.BookingItem.class);
        } else {
            // From ChargingHistoryActivity
            currentBooking = new com.evcharging.mobile.model.BookingItem();
            currentBooking.setBookingId(getIntent().getStringExtra("bookingId"));
            currentBooking.setStationName(getIntent().getStringExtra("stationName"));
            currentBooking.setSlotNumber(getIntent().getStringExtra("slotNumber"));
            currentBooking.setStatus(getIntent().getStringExtra("status"));
            currentBooking.setStartTime(getIntent().getStringExtra("start"));
            currentBooking.setEndTime(getIntent().getStringExtra("end"));
            currentBooking.setQrImageBase64(getIntent().getStringExtra("qrBase64"));
        }

        // Set bookingId for later use
        if (currentBooking != null) {
            bookingId = currentBooking.getBookingId();
        }

        // --- Display Data with enhanced UI ---
        displayBookingData();

        // Swipe refresh
        swipeRefresh.setOnRefreshListener(this::refreshFromServer);

        // Share QR
        btnShareQr.setOnClickListener(v -> shareQr());

        setupFooterNavigation();
        highlightActiveTab("bookings");
    }

    private void initializeViews() {
        swipeRefresh = findViewById(R.id.swipeRefresh);
        tvStatus = findViewById(R.id.tvStatus);
        tvStation = findViewById(R.id.tvStation);
        tvSlot = findViewById(R.id.tvSlot);
        tvTime = findViewById(R.id.tvTime);
        tvBookingId = findViewById(R.id.tvBookingId);
        ivQr = findViewById(R.id.ivQr);
        btnShareQr = findViewById(R.id.btnShareQr);
        tvReason = findViewById(R.id.tvReason);
        tvQrNote = findViewById(R.id.tvQrNote);
        ivStatusIcon = findViewById(R.id.ivStatusIcon);
        qrContainer = findViewById(R.id.qrContainer);
        statusContainer = findViewById(R.id.statusContainer);
        cardDetails = findViewById(R.id.cardDetails);
        btnBack = findViewById(R.id.btnBack);
    }

    private void initializeEnhancedViews() {
        // New enhanced views - FIXED: Correct casting
        qrSection = findViewById(R.id.qrSection);
        cancelReasonSection = findViewById(R.id.cancelReasonSection);
//        cardTimeline = findViewById(R.id.cardTimeline); // This is a CardView
//        timelineContainer = findViewById(R.id.timelineContainer); // This is a LinearLayout
        ivStatusIndicator = findViewById(R.id.ivStatusIndicator);
        cardHeader = findViewById(R.id.cardHeader);
    }

    private void setupEnhancedAnimations() {
        // Enhanced animations
        AlphaAnimation fadeIn = new AlphaAnimation(0.0f, 1.0f);
        fadeIn.setDuration(800);
        if (cardHeader != null) {
            cardHeader.startAnimation(fadeIn);
        }

        ScaleAnimation scaleIn = new ScaleAnimation(0.8f, 1.0f, 0.8f, 1.0f,
                Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
        scaleIn.setDuration(600);
        if (cardDetails != null) {
            cardDetails.startAnimation(scaleIn);
        }
    }

    private void displayBookingData() {
        if (currentBooking != null) {
            String bookingStatus = currentBooking.getStatus() != null ? currentBooking.getStatus() : "Pending";

            // Set status with enhanced styling
            updateStatusUI(bookingStatus);

            // Set other booking details
            tvStation.setText(currentBooking.getStationName() != null ? currentBooking.getStationName() : "-");
            tvSlot.setText(currentBooking.getSlotNumber() != null ? "Slot " + currentBooking.getSlotNumber() : "-");
            tvBookingId.setText(currentBooking.getBookingId() != null ? currentBooking.getBookingId() : "-");

            try {
                String startTime = currentBooking.getStartTimeFormatted();
                String endTime = currentBooking.getEndTimeFormatted();
                if (startTime != null && endTime != null) {
                    tvTime.setText(startTime + " – " + endTime);
                } else {
                    tvTime.setText("Time information not available");
                }
            } catch (Exception e) {
                tvTime.setText("Time: -");
            }

            // Handle QR display based on status
            handleQrDisplay(bookingStatus);
        }
    }

    private void updateStatusUI(String status) {
        int statusColor;
        int statusIcon;
        int statusIndicator;
        String statusText = status;

        switch (status.toLowerCase()) {
            case "approved":
                statusColor = Color.parseColor("#4CAF50");
                statusIcon = android.R.drawable.presence_online;
                statusIndicator = android.R.drawable.ic_menu_upload;
                statusText = "✓ Approved";
                break;
            case "charging":
                statusColor = Color.parseColor("#2196F3");
                statusIcon = android.R.drawable.stat_sys_download;
                statusIndicator = android.R.drawable.star_big_on;
                statusText = "⚡ Charging";
                break;
            case "pending":
                statusColor = Color.parseColor("#FF9800");
                statusIcon = android.R.drawable.ic_popup_sync;
                statusIndicator = android.R.drawable.ic_menu_rotate;
                statusText = "⏳ Pending";
                break;
            case "finalized":
                statusColor = Color.parseColor("#2196F3");
                statusIcon = android.R.drawable.ic_menu_edit;
                statusIndicator = android.R.drawable.ic_menu_upload;
                statusText = "✅ Completed";
                break;
            case "cancelled":
                statusColor = Color.parseColor("#F44336");
                statusIcon = android.R.drawable.ic_delete;
                statusIndicator = android.R.drawable.ic_menu_close_clear_cancel;
                statusText = "❌ Cancelled";
                break;
            case "expired":
                statusColor = Color.parseColor("#9E9E9E");
                statusIcon = android.R.drawable.ic_lock_idle_alarm;
                statusIndicator = android.R.drawable.ic_lock_idle_alarm;
                statusText = "⏰ Expired";
                break;
            default:
                statusColor = Color.parseColor("#757575");
                statusIcon = android.R.drawable.ic_dialog_info;
                statusIndicator = android.R.drawable.ic_dialog_info;
        }

        // Update status container background with gradient
        if (statusContainer != null) {
            GradientDrawable gradient = new GradientDrawable(
                    GradientDrawable.Orientation.LEFT_RIGHT,
                    new int[] {statusColor, darkenColor(statusColor)}
            );
            gradient.setCornerRadius(32);
            statusContainer.setBackground(gradient);
        }

        // Update icons
        if (ivStatusIcon != null) {
            ivStatusIcon.setImageResource(statusIcon);
        }
        if (ivStatusIndicator != null) {
            ivStatusIndicator.setImageResource(statusIndicator);
        }

        if (tvStatus != null) {
            tvStatus.setText(statusText);
        }

        // Add status-specific animations
        if ((status.equalsIgnoreCase("pending") || status.equalsIgnoreCase("charging")) && ivStatusIndicator != null) {
            // Pulsing animation for active statuses
            ObjectAnimator pulse = ObjectAnimator.ofFloat(ivStatusIndicator, "alpha", 0.5f, 1.0f);
            pulse.setDuration(1000);
            pulse.setRepeatCount(ObjectAnimator.INFINITE);
            pulse.setRepeatMode(ObjectAnimator.REVERSE);
            pulse.start();
        }
    }

    private int darkenColor(int color) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[2] *= 0.8f; // value component
        return Color.HSVToColor(hsv);
    }

    private void handleQrDisplay(String status) {
        // Hide all sections first
        if (qrSection != null) qrSection.setVisibility(View.GONE);
        if (cancelReasonSection != null) cancelReasonSection.setVisibility(View.GONE);
        if (cardTimeline != null) cardTimeline.setVisibility(View.GONE);

        switch (status.toLowerCase()) {
            case "approved":
                setupApprovedState();
                break;
            case "charging":
                setupChargingState();
                break;
            case "pending":
                setupPendingState();
                break;
            case "cancelled":
                setupCancelledState();
                break;
            case "finalized":
            case "expired":
                setupCompletedState();
                break;
            default:
                setupDefaultState();
        }
    }

    private void setupApprovedState() {
        if (qrSection != null) {
            qrSection.setVisibility(View.VISIBLE);
            if (currentBooking.getQrImageBase64() != null && !currentBooking.getQrImageBase64().isEmpty()) {
                renderQr(currentBooking.getQrImageBase64());
                tvQrNote.setText("Show this QR code to the station operator for charging access");
                tvQrNote.setTextColor(getResources().getColor(R.color.success_green));
                btnShareQr.setVisibility(View.VISIBLE);
                if (qrContainer != null) qrContainer.setVisibility(View.VISIBLE);
            } else {
                tvQrNote.setText("QR code is being generated...");
                tvQrNote.setTextColor(getResources().getColor(R.color.warning_orange));
                btnShareQr.setVisibility(View.GONE);
                if (qrContainer != null) {
                    qrContainer.setVisibility(View.VISIBLE);
                    ivQr.setImageResource(android.R.drawable.ic_menu_gallery);
                }
            }
            animateSection(qrSection);
        }
    }

    private void setupChargingState() {
        if (qrSection != null) {
            qrSection.setVisibility(View.VISIBLE);
            tvQrNote.setText("⚡ You are currently charging\nQR code is active");
            tvQrNote.setTextColor(getResources().getColor(R.color.primary));
            btnShareQr.setVisibility(View.GONE);
            if (qrContainer != null) qrContainer.setVisibility(View.GONE);
            animateSection(qrSection);
        }
    }

    private void setupPendingState() {
        if (qrSection != null) {
            qrSection.setVisibility(View.VISIBLE);
            tvQrNote.setText("⏳ Your booking is pending approval\nQR code will appear once approved");
            tvQrNote.setTextColor(getResources().getColor(R.color.warning_orange));
            btnShareQr.setVisibility(View.GONE);
            if (qrContainer != null) qrContainer.setVisibility(View.GONE);
            animateSection(qrSection);
        }
    }

    private void setupCancelledState() {
        if (cancelReasonSection != null) {
            cancelReasonSection.setVisibility(View.VISIBLE);
            String reason = currentBooking.getCancellationReason();
            if (reason != null && !reason.isEmpty()) {
                tvReason.setText("Reason: " + reason);
            } else {
                tvReason.setText("Reason: Slot is under Maintenance");
            }
            animateSection(cancelReasonSection);
        }
    }

    private void setupCompletedState() {
        if (qrSection != null) {
            qrSection.setVisibility(View.VISIBLE);
            tvQrNote.setText("✅ This booking has been completed\nQR code is no longer active");
            tvQrNote.setTextColor(getResources().getColor(R.color.text_secondary));
            btnShareQr.setVisibility(View.GONE);
            if (qrContainer != null) qrContainer.setVisibility(View.GONE);
            animateSection(qrSection);
        }
    }

    private void setupDefaultState() {
        if (qrSection != null) {
            qrSection.setVisibility(View.VISIBLE);
            tvQrNote.setText("QR code status unavailable");
            tvQrNote.setTextColor(getResources().getColor(R.color.text_secondary));
            btnShareQr.setVisibility(View.GONE);
            if (qrContainer != null) qrContainer.setVisibility(View.GONE);
            animateSection(qrSection);
        }
    }

    private void animateSection(View section) {
        if (section != null) {
            TranslateAnimation slideUp = new TranslateAnimation(
                    Animation.RELATIVE_TO_SELF, 0f,
                    Animation.RELATIVE_TO_SELF, 0f,
                    Animation.RELATIVE_TO_SELF, 0.5f,
                    Animation.RELATIVE_TO_SELF, 0f
            );
            slideUp.setDuration(500);
            section.startAnimation(slideUp);
        }
    }

    // ---------------- Footer Navigation Setup ----------------
    private void setupFooterNavigation() {
        LinearLayout navHome = findViewById(R.id.navHome);
        LinearLayout navBookings = findViewById(R.id.navBookings);
        LinearLayout navProfile = findViewById(R.id.navProfile);

        if (navHome == null || navBookings == null || navProfile == null)
            return; // Footer not included on this layout

        navHome.setOnClickListener(v -> {
            Intent i = new Intent(this, OwnerHomeActivity.class);
            i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(i);
        });

        navBookings.setOnClickListener(v -> {
            Intent i = new Intent(this, OwnerBookingsActivity.class);
            i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(i);
        });

        navProfile.setOnClickListener(v -> {
            Intent i = new Intent(this, OwnerProfileActivity.class);
            i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(i);
        });
    }

    private void highlightActiveTab(String activeTab) {
        int activeColor = getResources().getColor(R.color.primary_dark);
        int inactiveColor = getResources().getColor(R.color.primary);

        LinearLayout navHome = findViewById(R.id.navHome);
        LinearLayout navBookings = findViewById(R.id.navBookings);
        LinearLayout navProfile = findViewById(R.id.navProfile);

        if (navHome == null || navBookings == null || navProfile == null)
            return;

        ImageView iconHome = navHome.findViewById(R.id.iconHome);
        ImageView iconBookings = navBookings.findViewById(R.id.iconBookings);
        ImageView iconProfile = navProfile.findViewById(R.id.iconProfile);

        TextView txtHome = navHome.findViewById(R.id.txtHome);
        TextView txtBookings = navBookings.findViewById(R.id.txtBookings);
        TextView txtProfile = navProfile.findViewById(R.id.txtProfile);

        iconHome.setColorFilter(inactiveColor);
        iconBookings.setColorFilter(inactiveColor);
        iconProfile.setColorFilter(inactiveColor);

        txtHome.setTextColor(inactiveColor);
        txtBookings.setTextColor(inactiveColor);
        txtProfile.setTextColor(inactiveColor);

        switch (activeTab) {
            case "home":
                iconHome.setColorFilter(activeColor);
                txtHome.setTextColor(activeColor);
                break;
            case "bookings":
                iconBookings.setColorFilter(activeColor);
                txtBookings.setTextColor(activeColor);
                break;
            case "profile":
                iconProfile.setColorFilter(activeColor);
                txtProfile.setTextColor(activeColor);
                break;
        }
    }
    // ----------------------------------------------------------

    @Override
    protected void onResume() {
        super.onResume();
        refreshBookingDetails();
    }

    private void refreshFromServer() {
        swipeRefresh.setRefreshing(true);
        new AsyncTask<Void, Void, JSONObject>() {
            @Override
            protected JSONObject doInBackground(Void... voids) {
                String ownerId = session.getLoggedInUser() != null ? session.getLoggedInUser().getUserId() : null;
                ApiResponse res = api.getBookingsByOwner(ownerId);
                if (res == null || !res.isSuccess()) return null;
                try {
                    JSONArray arr = new JSONArray(res.getData());
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject o = arr.getJSONObject(i);
                        String id = o.optString("bookingId", o.optString("_id", null));
                        if (bookingId != null && bookingId.equals(id)) return o;
                    }
                } catch (Exception ignored) {}
                return null;
            }

            @Override
            protected void onPostExecute(JSONObject o) {
                swipeRefresh.setRefreshing(false);
                if (o == null) return;

                String newStatus = o.optString("status", status);
                updateStatusUI(newStatus);

                String b64 = o.optString("qrImageBase64", null);
                if (b64 != null && !b64.isEmpty()) {
                    renderQr(b64);
                }
                handleQrDisplay(newStatus);
            }
        }.execute();
    }

    private void refreshBookingDetails() {
        if (currentBooking == null || currentBooking.getBookingId() == null) {
            return;
        }

        new android.os.AsyncTask<Void, Void, com.evcharging.mobile.network.ApiResponse>() {
            @Override
            protected com.evcharging.mobile.network.ApiResponse doInBackground(Void... voids) {
                try {
                    String bookingId = currentBooking.getBookingId();
                    return apiClient.get("/bookings/" + bookingId);
                } catch (Exception e) {
                    e.printStackTrace();
                    return null;
                }
            }

            @Override
            protected void onPostExecute(com.evcharging.mobile.network.ApiResponse res) {
                if (res == null || !res.isSuccess()) return;

                try {
                    org.json.JSONObject obj = new org.json.JSONObject(res.getData());
                    String newStatus = obj.optString("status", "Pending");
                    updateStatusUI(newStatus);
                    handleQrDisplay(newStatus);

                    // Update QR code if available
                    String newQrBase64 = obj.optString("qrImageBase64", null);
                    if (newQrBase64 != null && !newQrBase64.isEmpty()) {
                        renderQr(newQrBase64);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }.execute();
    }

    private void renderQr(String base64) {
        try {
            byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
            qrBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            ivQr.setImageBitmap(qrBitmap);

            // Add fade-in animation for QR
            AlphaAnimation fadeIn = new AlphaAnimation(0.0f, 1.0f);
            fadeIn.setDuration(800);
            ivQr.startAnimation(fadeIn);
        } catch (Exception e) {
            e.printStackTrace();
            // Show placeholder if QR rendering fails
            ivQr.setImageResource(android.R.drawable.ic_menu_gallery); // Use system gallery icon as placeholder
        }
    }

    private void shareQr() {
        if (qrBitmap == null) {
            // Show message if no QR available
            android.widget.Toast.makeText(this, "QR code not available for sharing", android.widget.Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            File cachePath = new File(getCacheDir(), "images");
            cachePath.mkdirs();
            File file = new File(cachePath, "qr_share.png");
            try (FileOutputStream stream = new FileOutputStream(file)) {
                qrBitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
            }

            Uri contentUri = FileProvider.getUriForFile(this,
                    getPackageName() + ".fileprovider", file);

            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("image/png");
            shareIntent.putExtra(Intent.EXTRA_STREAM, contentUri);
            shareIntent.putExtra(Intent.EXTRA_TEXT, "EV Charging Booking QR Code\nBooking ID: " + bookingId + "\nStation: " + currentBooking.getStationName());
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(shareIntent, "Share QR Code via"));

        } catch (Exception e) {
            e.printStackTrace();
            android.widget.Toast.makeText(this, "Failed to share QR code", android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Clean up bitmap to avoid memory leaks
        if (qrBitmap != null && !qrBitmap.isRecycled()) {
            qrBitmap.recycle();
            qrBitmap = null;
        }
    }
}