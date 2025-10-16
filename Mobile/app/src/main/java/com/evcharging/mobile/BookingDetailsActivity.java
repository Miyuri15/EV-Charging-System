package com.evcharging.mobile;

import android.Manifest;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.widget.*;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import android.content.pm.PackageManager;
import android.widget.ImageButton;

import com.evcharging.mobile.network.ApiClient;
import com.evcharging.mobile.network.ApiResponse;
import com.evcharging.mobile.session.SessionManager;
import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;

import org.json.JSONObject;

public class BookingDetailsActivity extends AppCompatActivity {

    private TextView tvBookingId, tvStatus, tvStartTime, tvEndTime;
    private Button btnScanQr, btnFinalize;
    private ImageButton btnBack;
    private SwipeRefreshLayout srBookingDetails;

    private SessionManager session;
    private ApiClient apiClient;
    private String bookingId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_booking_details);
        setTitle("Booking Details");
        FooterHelper.setupFooter(this);

        session = new SessionManager(this);
        apiClient = new ApiClient(session);

        bindViews();

        btnBack.setOnClickListener(v -> finish());

        // camera permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 101);
        }

        // pull-to-refresh
        srBookingDetails.setOnRefreshListener(this::refreshBookingFromServer);

        // initial data (from intent)
        bookingId = getIntent().getStringExtra("bookingId");
        bindFromIntent();

        // refresh buttons
        btnScanQr.setOnClickListener(v -> startQrScanner());
        btnFinalize.setOnClickListener(v -> new FinalizeBookingTask().execute());
    }

    @Override
    protected void onResume() {
        super.onResume();
        // auto-refresh in case status changed while away
        refreshBookingFromServer();
    }

    private void bindViews() {
        tvBookingId = findViewById(R.id.tvBookingId);
        tvStatus = findViewById(R.id.tvStatus);
        tvStartTime = findViewById(R.id.tvStartTime);
        tvEndTime = findViewById(R.id.tvEndTime);
        btnScanQr = findViewById(R.id.btnScanQr);
        btnFinalize = findViewById(R.id.btnFinalize);
        btnBack = findViewById(R.id.btnBack);
        srBookingDetails = findViewById(R.id.srBookingDetails);
    }

    private void bindFromIntent() {
        String status = getIntent().getStringExtra("status");
        String startTime = getIntent().getStringExtra("formattedStartTime");
        if (startTime == null || startTime.isEmpty())
            startTime = getIntent().getStringExtra("startTime");

        String endTime = getIntent().getStringExtra("formattedEndTime");
        if (endTime == null || endTime.isEmpty())
            endTime = getIntent().getStringExtra("endTime");



        tvBookingId.setText(bookingId != null ? bookingId : "-");
        tvStatus.setText("Status: " + (status != null ? status : "-"));
        tvStartTime.setText("Start: " + (startTime != null ? startTime : "-"));
        tvEndTime.setText("End: " + (endTime != null ? endTime : "-"));


    }

    private void startQrScanner() {
        ScanOptions options = new ScanOptions();
        options.setPrompt("Scan Booking QR Code");
        options.setBeepEnabled(true);
        options.setOrientationLocked(true);
        options.setCaptureActivity(com.journeyapps.barcodescanner.CaptureActivity.class);
        qrLauncher.launch(options);
    }

    private final ActivityResultLauncher<ScanOptions> qrLauncher = registerForActivityResult(new ScanContract(),
            result -> {
                if (result.getContents() == null) {
                    Toast.makeText(this, "Scan cancelled", Toast.LENGTH_SHORT).show();
                } else {
                    String scannedCode = result.getContents();
                    Log.d("QR_SCAN", "Scanned QR Code: " + scannedCode);

                    String expectedQr = getIntent().getStringExtra("qrCode");
                    if (expectedQr != null && scannedCode.trim().equalsIgnoreCase(expectedQr.trim())) {
                        Toast.makeText(this, "QR matched! Starting charging...", Toast.LENGTH_SHORT).show();
                        new UpdateBookingStatusTask().execute();
                    } else {
                        Toast.makeText(this, "Invalid QR: does not match this booking", Toast.LENGTH_LONG).show();
                        Log.d("QR_SCAN", "Expected: " + expectedQr + ", Got: " + scannedCode);
                    }
                }
            });

    /** Re-fetch booking from /bookings/{bookingId} and update UI */
    private void refreshBookingFromServer() {
        if (bookingId == null || bookingId.isEmpty()) {
            srBookingDetails.setRefreshing(false);
            return;
        }

        srBookingDetails.setRefreshing(true);

        new AsyncTask<Void, Void, ApiResponse>() {
            @Override
            protected ApiResponse doInBackground(Void... voids) {
                return apiClient.get("/bookings/" + bookingId);
            }

            @Override
            protected void onPostExecute(ApiResponse response) {
                srBookingDetails.setRefreshing(false);
                if (response == null || !response.isSuccess() || response.getData() == null) {
                    Toast.makeText(BookingDetailsActivity.this, "Failed to refresh booking", Toast.LENGTH_SHORT).show();
                    return;
                }

                try {
                    JSONObject o = new JSONObject(response.getData());

                    String status = o.optString("status", "-");
                    String startTime = o.optString("formattedStartTime", o.optString("startTime", "-"));
                    String endTime = o.optString("formattedEndTime", o.optString("endTime", "-"));


                    tvBookingId.setText(bookingId);
                    tvStatus.setText("Status: " + status);
                    tvStartTime.setText("Start: " + startTime);
                    tvEndTime.setText("End: " + endTime);

                    

                } catch (Exception e) {
                    Log.e("BOOKING_DETAILS", "parse error: " + e.getMessage());
                }
            }
        }.execute();
    }

    /** PATCH /bookings/{id}/start */
    private class UpdateBookingStatusTask extends AsyncTask<Void, Void, ApiResponse> {
        @Override
        protected ApiResponse doInBackground(Void... voids) {
            return apiClient.patch("/bookings/" + bookingId + "/start", null);
        }

        @Override
        protected void onPostExecute(ApiResponse response) {
            if (response != null && response.isSuccess()) {
                Toast.makeText(BookingDetailsActivity.this, "Booking marked as Charging", Toast.LENGTH_SHORT).show();
                refreshBookingFromServer();
            } else {
                Toast.makeText(BookingDetailsActivity.this, "Failed to start: " +
                        (response != null ? response.getMessage() : "Unknown"), Toast.LENGTH_SHORT).show();
            }
        }
    }

    /** PATCH /bookings/{id}/finalize */
    private class FinalizeBookingTask extends AsyncTask<Void, Void, ApiResponse> {
        @Override
        protected ApiResponse doInBackground(Void... voids) {
            return apiClient.patch("/bookings/" + bookingId + "/finalize", null);
        }

        @Override
        protected void onPostExecute(ApiResponse response) {
            if (response != null && response.isSuccess()) {
                Toast.makeText(BookingDetailsActivity.this, "Booking finalized", Toast.LENGTH_SHORT).show();
                refreshBookingFromServer();
            } else {
                Toast.makeText(BookingDetailsActivity.this, "Finalize failed: " +
                        (response != null ? response.getMessage() : "Unknown"), Toast.LENGTH_SHORT).show();
            }
        }
    }
}
