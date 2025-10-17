package com.evcharging.mobile;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.evcharging.mobile.model.BookingItem;
import com.evcharging.mobile.model.SlotItem;
import com.evcharging.mobile.model.TimeSlotItem;
import com.evcharging.mobile.network.ApiClient;
import com.evcharging.mobile.network.ApiResponse;
import com.evcharging.mobile.session.SessionManager;
import com.evcharging.mobile.utils.DialogUtils;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.json.JSONArray;
import org.json.JSONObject;

import java.lang.reflect.Type;
import java.text.SimpleDateFormat;
import java.util.*;

public class UpdateBookingActivity extends AppCompatActivity {

    private Spinner spnSlot, spnTimeSlot;
    private com.google.android.material.button.MaterialButton btnConfirmUpdate, btnCancelUpdate;
    private TextView tvStationInfo, tvCurrentBooking, tvHints;
    private LinearLayout selectedTimeLayout;
    private TextView tvSelectedTime;

    private ApiClient apiClient;
    private SessionManager sessionManager;
    private Gson gson = new Gson();

    private BookingItem currentBooking;
    private String selectedSlotId;
    private String selectedTimeSlotId;
    private String selectedDateStr;

    private List<SlotItem> slots = new ArrayList<>();
    private List<TimeSlotItem> timeSlots = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_update_booking);

        sessionManager = new SessionManager(this);
        apiClient = new ApiClient(sessionManager);

        // Get booking from intent
        String bookingJson = getIntent().getStringExtra("booking");
        if (bookingJson != null) {
            currentBooking = gson.fromJson(bookingJson, BookingItem.class);
        }

        if (currentBooking == null) {
            Toast.makeText(this, "Booking data not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initViews();
        setupUI();
        setupEnhancedUI();
        loadAvailableSlots();
    }

    private void initViews() {
        spnSlot = findViewById(R.id.spnSlot);
        spnTimeSlot = findViewById(R.id.spnTimeSlot);
        btnConfirmUpdate = findViewById(R.id.btnConfirmUpdate);
        btnCancelUpdate = findViewById(R.id.btnCancelUpdate);
        tvStationInfo = findViewById(R.id.tvStationInfo);
        tvCurrentBooking = findViewById(R.id.tvCurrentBooking);
        tvHints = findViewById(R.id.tvHints);
        selectedTimeLayout = findViewById(R.id.selectedTimeLayout);
        tvSelectedTime = findViewById(R.id.tvSelectedTime);
    }

    private void setupUI() {
        // Display current booking info
        tvStationInfo.setText("Station: " + currentBooking.getStationName());

        try {
            // Parse API times in UTC and convert to local
            SimpleDateFormat utcFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault());
            utcFormat.setTimeZone(TimeZone.getTimeZone("UTC"));

            SimpleDateFormat displayFormat = new SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", Locale.getDefault());
            displayFormat.setTimeZone(TimeZone.getTimeZone("Asia/Colombo"));

            Date startDate = utcFormat.parse(currentBooking.getStartTime());
            Date endDate = utcFormat.parse(currentBooking.getEndTime());

            String currentBookingInfo = String.format(
                    "Current: %s - %s (Slot %s)",
                    displayFormat.format(startDate),
                    displayFormat.format(endDate),
                    currentBooking.getSlotNumber()
            );

            tvCurrentBooking.setText(currentBookingInfo);

            // Extract date (local date)
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            dateFormat.setTimeZone(TimeZone.getTimeZone("Asia/Colombo"));
            selectedDateStr = dateFormat.format(startDate);

        } catch (Exception e) {
            e.printStackTrace();
            tvCurrentBooking.setText("Current booking info not available");
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            selectedDateStr = dateFormat.format(new Date());
        }

        // Setup buttons
        btnConfirmUpdate.setOnClickListener(v -> confirmUpdate());
        btnCancelUpdate.setOnClickListener(v -> finish());
    }

    private void setupEnhancedUI() {
        setupSelectionValidation();
        updateConfirmButtonState();
    }

    private void setupSelectionValidation() {
        spnSlot.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (slots != null && position < slots.size()) {
                    selectedSlotId = slots.get(position).slotId;
                    clearTimeSlots();
                    updateConfirmButtonState();

                    if (selectedDateStr != null) {
                        loadTimeSlotsForSlot(selectedSlotId);
                    } else {
                        showHint("Date information not available");
                    }
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        spnTimeSlot.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (timeSlots != null && position < timeSlots.size()) {
                    selectedTimeSlotId = timeSlots.get(position).timeSlotId;

                    // Show selected time in the display area
                    TimeSlotItem selectedTimeSlot = timeSlots.get(position);
                    String displayTime = formatTimeForDisplay(selectedTimeSlot.getStartTime(), selectedTimeSlot.getEndTime());
                    tvSelectedTime.setText("Selected: " + displayTime);
                    selectedTimeLayout.setVisibility(View.VISIBLE);

                    updateConfirmButtonState();
                } else {
                    selectedTimeLayout.setVisibility(View.GONE);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                selectedTimeLayout.setVisibility(View.GONE);
            }
        });
    }

    private void updateConfirmButtonState() {
        boolean isComplete = selectedSlotId != null && selectedTimeSlotId != null;

        btnConfirmUpdate.setEnabled(isComplete);
        btnConfirmUpdate.setAlpha(isComplete ? 1.0f : 0.6f);

        if (isComplete) {
            btnConfirmUpdate.setBackgroundTintList(ColorStateList.valueOf(getResources().getColor(R.color.primary_color)));
            showHint("Ready to update your booking!");
        } else {
            btnConfirmUpdate.setBackgroundTintList(ColorStateList.valueOf(getResources().getColor(R.color.gray)));
            showHint("Select both slot and time slot to update booking");
        }
    }

    private void showHint(String message) {
        if (tvHints != null) {
            tvHints.setText(message);
            tvHints.animate().alpha(1f).setDuration(300).start();
        }
    }

    private ArrayAdapter<String> createEnhancedSpinnerAdapter(List<String> items) {
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, R.layout.spinner_item, items) {
            @NonNull
            @Override
            public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                if (view instanceof TextView) {
                    TextView textView = (TextView) view;
                    textView.setTextColor(getResources().getColor(R.color.text_primary));
                    textView.setTextSize(14);
                }
                return view;
            }
        };
        adapter.setDropDownViewResource(R.layout.grid_spinner_dropdown_item);
        return adapter;
    }

    private void loadAvailableSlots() {
        new AsyncTask<Void, Void, ApiResponse>() {
            @Override
            protected void onPreExecute() {
                showHint("Loading available slots...");
            }

            @Override
            protected ApiResponse doInBackground(Void... voids) {
                try {
                    return apiClient.getSlotsByStation(currentBooking.getStationId());
                } catch (Exception e) {
                    Log.e("UpdateBooking", "Error loading slots", e);
                    return null;
                }
            }

            @Override
            protected void onPostExecute(ApiResponse res) {
                if (res != null && res.isSuccess()) {
                    try {
                        JSONArray arr = new JSONArray(res.getData());
                        slots.clear();

                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject obj = arr.getJSONObject(i);
                            SlotItem slot = new SlotItem();
                            slot.slotId = obj.optString("slotId");
                            slot.number = obj.optString("number");
                            slot.status = obj.optString("status");
                            slot.connectorType = obj.optString("connectorType");

                            // Only add available slots
                            if ("Available".equals(slot.status)) {
                                slots.add(slot);
                            }
                        }

                        if (slots.isEmpty()) {
                            showHint("No available slots found for this station");
                            return;
                        }

                        // Setup slot spinner with enhanced adapter
                        List<String> slotStrings = new ArrayList<>();
                        for (SlotItem slot : slots) {
                            slotStrings.add(slot.toString());
                        }

                        ArrayAdapter<String> slotAdapter = createEnhancedSpinnerAdapter(slotStrings);
                        spnSlot.setAdapter(slotAdapter);

                        showHint(slots.size() + " slots available. Select a slot to continue.");

                    } catch (Exception e) {
                        Log.e("UpdateBooking", "Error parsing slots", e);
                        showHint("Error loading slots");
                    }
                } else {
                    showHint("Failed to load slots. Please try again.");
                }
            }
        }.execute();
    }

    private void loadTimeSlotsForSlot(String slotId) {
        new AsyncTask<Void, Void, ApiResponse>() {
            @Override
            protected void onPreExecute() {
                showHint("Loading available time slots...");
            }

            @Override
            protected ApiResponse doInBackground(Void... voids) {
                try {
                    String endpoint = String.format("/timeslot/available?stationId=%s&slotId=%s&date=%s",
                            currentBooking.getStationId(), slotId, selectedDateStr);
                    return apiClient.get(endpoint);
                } catch (Exception e) {
                    Log.e("UpdateBooking", "Error fetching timeslots", e);
                    return null;
                }
            }

            @Override
            protected void onPostExecute(ApiResponse res) {
                if (res == null) {
                    showHint("Failed to load time slots. Please try again.");
                    return;
                }

                if (!res.isSuccess()) {
                    showHint("No time slots available for selected slot and date.");
                    return;
                }

                try {
                    Type listType = new TypeToken<List<TimeSlotItem>>() {}.getType();
                    List<TimeSlotItem> fetched = gson.fromJson(res.getData(), listType);

                    if (fetched == null || fetched.isEmpty()) {
                        showHint("No available time slots for this slot");
                        return;
                    }

                    timeSlots = fetched;

                    List<String> timeSlotStrings = new ArrayList<>();
                    for (TimeSlotItem timeSlot : timeSlots) {
                        timeSlotStrings.add(timeSlot.toString());
                    }

                    ArrayAdapter<String> timeSlotAdapter = createEnhancedSpinnerAdapter(timeSlotStrings);
                    spnTimeSlot.setAdapter(timeSlotAdapter);

                    showHint(timeSlots.size() + " time slots available. Select your preferred time.");

                } catch (Exception e) {
                    Log.e("UpdateBooking", "Failed to parse timeslots", e);
                    showHint("Error loading time slots. Please try again.");
                }
            }
        }.execute();
    }

    private String formatTimeForDisplay(String startTimeUtc, String endTimeUtc) {
        try {
            // Input: UTC time from API
            SimpleDateFormat utcFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault());
            utcFormat.setTimeZone(TimeZone.getTimeZone("UTC"));

            // Output: Local display format
            SimpleDateFormat localFormat = new SimpleDateFormat("hh:mm a", Locale.getDefault());
            localFormat.setTimeZone(TimeZone.getTimeZone("Asia/Colombo"));

            Date start = utcFormat.parse(startTimeUtc);
            Date end = utcFormat.parse(endTimeUtc);

            return localFormat.format(start) + " - " + localFormat.format(end);
        } catch (Exception e) {
            return startTimeUtc + " - " + endTimeUtc;
        }
    }


    private void clearTimeSlots() {
        timeSlots.clear();
        spnTimeSlot.setAdapter(null);
        selectedTimeSlotId = null;
        selectedTimeLayout.setVisibility(View.GONE);
        updateConfirmButtonState();
    }

    private void confirmUpdate() {
        if (selectedSlotId == null || selectedTimeSlotId == null) {
            Toast.makeText(this, "Please select both slot and time slot", Toast.LENGTH_SHORT).show();
            return;
        }

        new AsyncTask<Void, Void, ApiResponse>() {
            @Override
            protected void onPreExecute() {
                Toast.makeText(UpdateBookingActivity.this, "Updating booking...", Toast.LENGTH_SHORT).show();
                btnConfirmUpdate.setEnabled(false);
                btnConfirmUpdate.setText("UPDATING...");
            }

            @Override
            protected ApiResponse doInBackground(Void... voids) {
                try {
                    return apiClient.updateBooking(
                            currentBooking.getBookingId(),
                            selectedTimeSlotId,
                            selectedSlotId
                    );
                } catch (Exception e) {
                    Log.e("UpdateBooking", "Error updating booking", e);
                    return null;
                }
            }

            @Override
            protected void onPostExecute(ApiResponse res) {
                btnConfirmUpdate.setEnabled(true);
                btnConfirmUpdate.setText("UPDATE BOOKING");

                if (res == null || !res.isSuccess()) {
                    String errorMsg = res != null ? res.getMessage() : "Network error";
                    Toast.makeText(UpdateBookingActivity.this, "Update failed: " + errorMsg, Toast.LENGTH_SHORT).show();
                } else {
                    DialogUtils.showToast(UpdateBookingActivity.this, "Booking updated successfully");

                    // Fetch the updated booking details and return it
                    fetchUpdatedBooking();
                }
            }
        }.execute();
    }

    private void fetchUpdatedBooking() {
        new AsyncTask<Void, Void, ApiResponse>() {
            @Override
            protected ApiResponse doInBackground(Void... voids) {
                try {
                    return apiClient.get("/bookings/" + currentBooking.getBookingId());
                } catch (Exception e) {
                    Log.e("UpdateBooking", "Error fetching updated booking", e);
                    return null;
                }
            }

            @Override
            protected void onPostExecute(ApiResponse res) {
                if (res != null && res.isSuccess()) {
                    try {
                        JSONObject bookingObj = new JSONObject(res.getData());
                        BookingItem updatedBooking = gson.fromJson(bookingObj.toString(), BookingItem.class);

                        Intent resultIntent = new Intent();
                        resultIntent.putExtra("updatedBooking", gson.toJson(updatedBooking));
                        resultIntent.putExtra("bookingId", currentBooking.getBookingId());
                        setResult(RESULT_OK, resultIntent);
                        finish();

                    } catch (Exception e) {
                        Log.e("UpdateBooking", "Error parsing updated booking", e);
                        setResult(RESULT_OK);
                        finish();
                    }
                } else {
                    setResult(RESULT_OK);
                    finish();
                }
            }
        }.execute();
    }
}