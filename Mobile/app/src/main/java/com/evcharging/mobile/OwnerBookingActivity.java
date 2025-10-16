package com.evcharging.mobile;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.evcharging.mobile.model.Station;
import com.evcharging.mobile.model.SlotItem;
import com.evcharging.mobile.model.TimeSlotItem;
import com.evcharging.mobile.network.ApiClient;
import com.evcharging.mobile.network.ApiResponse;
import com.evcharging.mobile.session.SessionManager;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.datepicker.CalendarConstraints;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.json.JSONArray;
import org.json.JSONObject;

import java.lang.reflect.Type;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.Executors;

public class OwnerBookingActivity extends AppCompatActivity {

    private Spinner spnType, spnStation, spnSlot, spnTimeSlot;
    private com.google.android.material.button.MaterialButton btnSelectDate, btnConfirmBooking;
    private TextView tvSelectedDate, tvHints;
    private Date selectedDate;

    private SessionManager sessionManager;
    private ApiClient apiClient;
    private final Gson gson = new Gson();

    private String selectedType = "AC";
    private String selectedStationId;
    private String selectedSlotId;
    private String selectedTimeSlotId;
    private String selectedDateStr; // yyyy-MM-dd

    private List<Station> stations = new ArrayList<>();
    private List<SlotItem> slots = new ArrayList<>();
    private List<TimeSlotItem> timeSlots = new ArrayList<>();

    // Location handling from new version
    private static final double DEFAULT_LAT = 6.9908661;
    private static final double DEFAULT_LON = 79.9395566;
    private static final double DEFAULT_RADIUS = 10.0;

    private String preselectedStationId;
    private String preselectedStationName;
    private double preselectedLat;
    private double preselectedLng;
    private String preselectedLocation;
    private double currentLat = 0.0;
    private double currentLng = 0.0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_owner_booking);

        // Setup Material Toolbar
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> onBackPressed());

        sessionManager = new SessionManager(this);
        apiClient = new ApiClient(sessionManager);

        // Get location data from new version
        Intent intent = getIntent();
        if (intent != null) {
            if (intent.hasExtra("current_lat") && intent.hasExtra("current_lng")) {
                currentLat = intent.getDoubleExtra("current_lat", DEFAULT_LAT);
                currentLng = intent.getDoubleExtra("current_lng", DEFAULT_LON);
            }

            if (intent.hasExtra("selected_station_id")) {
                preselectedStationId = intent.getStringExtra("selected_station_id");
                preselectedStationName = intent.getStringExtra("selected_station_name");
                preselectedLat = intent.getDoubleExtra("selected_station_lat", 0);
                preselectedLng = intent.getDoubleExtra("selected_station_lng", 0);
                preselectedLocation = intent.getStringExtra("selected_station_location");

                Log.d("OwnerBookingActivity",
                        "Selected station: " + preselectedStationName + " (" + preselectedLocation + ")");
            }
        }

        bindViews();
        setupTypeSpinner();
        setupDatePicker();
        setupConfirm();
        setupFooterNavigation();
        highlightActiveTab("home");
        setupEnhancedUI();
    }

    // ---------------- Footer Navigation Setup ----------------
    private void setupFooterNavigation() {
        LinearLayout navHome = findViewById(R.id.navHome);
        LinearLayout navBookings = findViewById(R.id.navBookings);
        LinearLayout navProfile = findViewById(R.id.navProfile);

        if (navHome == null || navBookings == null || navProfile == null)
            return;

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

    private void bindViews() {
        spnType = findViewById(R.id.spnType);
        spnStation = findViewById(R.id.spnStation);
        spnSlot = findViewById(R.id.spnSlot);
        spnTimeSlot = findViewById(R.id.spnTimeSlot);
        btnSelectDate = findViewById(R.id.btnSelectDate);
        btnConfirmBooking = findViewById(R.id.btnConfirmBooking);
        tvSelectedDate = findViewById(R.id.tvSelectedDate);
        tvHints = findViewById(R.id.tvHints);
    }

    private void setupEnhancedUI() {
        setupSelectionValidation();
        updateConfirmButtonState();
    }

    private void setupSelectionValidation() {
        spnType.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                selectedType = (String) parent.getItemAtPosition(pos);
                loadStationsByType(selectedType);
                updateConfirmButtonState();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        spnStation.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (stations != null && position < stations.size()) {
                    selectedStationId = stations.get(position).getStationId();
                    clearSlots();
                    clearTimeSlots();
                    updateConfirmButtonState();

                    if (selectedDateStr != null) {
                        loadSlotsForStation(selectedStationId);
                    } else {
                        showHint("Please select a date to see available slots");
                    }
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        spnSlot.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (slots != null && position < slots.size()) {
                    selectedSlotId = slots.get(position).slotId;
                    if (selectedStationId != null && selectedDateStr != null) {
                        loadTimeslotsFor(selectedStationId, selectedSlotId, selectedDateStr);
                    }
                    updateConfirmButtonState();
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
                    updateConfirmButtonState();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    private void updateConfirmButtonState() {
        boolean isComplete = selectedStationId != null &&
                selectedDateStr != null &&
                selectedSlotId != null &&
                selectedTimeSlotId != null;

        btnConfirmBooking.setEnabled(isComplete);
        btnConfirmBooking.setAlpha(isComplete ? 1.0f : 0.6f);

        if (isComplete) {
            btnConfirmBooking
                    .setBackgroundTintList(ColorStateList.valueOf(getResources().getColor(R.color.primary_color)));
        } else {
            btnConfirmBooking.setBackgroundTintList(ColorStateList.valueOf(getResources().getColor(R.color.gray)));
        }

        if (isComplete) {
            showHint("All selections complete! Ready to confirm your booking.");
        } else {
            showHint("Complete all selections to proceed with booking");
        }
    }

    private void showHint(String message) {
        tvHints.setText(message);
        tvHints.animate().alpha(1f).setDuration(300).start();
    }

    private void setupTypeSpinner() {
        List<String> types = Arrays.asList("AC", "DC");
        ArrayAdapter<String> adapter = createEnhancedSpinnerAdapter(types);
        adapter.setDropDownViewResource(R.layout.grid_spinner_dropdown_item);
        spnType.setAdapter(adapter);
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

    private void loadStationsByType(String selectedType) {
        Toast.makeText(this, "Fetching nearby " + selectedType + " stations...", Toast.LENGTH_SHORT).show();

        Executors.newSingleThreadExecutor().execute(() -> {
            ApiResponse res = null;
            try {
                // Use current location from new version, fallback to default
                double lat = currentLat != 0.0 ? currentLat : DEFAULT_LAT;
                double lng = currentLng != 0.0 ? currentLng : DEFAULT_LON;
                res = apiClient.getNearbyStationsByType(selectedType, lat, lng, DEFAULT_RADIUS);
            } catch (Exception e) {
                Log.e("OwnerBooking", "Error fetching stations", e);
            }

            ApiResponse finalRes = res;
            runOnUiThread(() -> {
                if (finalRes == null || !finalRes.isSuccess()) {
                    toast("Failed to fetch stations");
                    return;
                }

                try {
                    JSONArray arr = new JSONArray(finalRes.getData());
                    stations.clear();

                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject obj = arr.getJSONObject(i);
                        Station s = new Station();
                        s.setStationId(obj.optString("stationId"));
                        s.setName(obj.optString("name"));
                        s.setLocation(obj.optString("location"));
                        s.setLatitude(obj.optDouble("latitude"));
                        s.setLongitude(obj.optDouble("longitude"));
                        s.setType(obj.optString("type"));
                        stations.add(s);
                    }

                    // Include preselected station if not already present
                    if (preselectedStationId != null) {
                        boolean exists = false;
                        for (Station s : stations) {
                            if (s.getStationId().equals(preselectedStationId)) {
                                exists = true;
                                break;
                            }
                        }
                        if (!exists) {
                            Station pre = new Station();
                            pre.setStationId(preselectedStationId);
                            pre.setName(preselectedStationName + " (Selected)");
                            pre.setLocation(preselectedLocation);
                            pre.setLatitude(preselectedLat);
                            pre.setLongitude(preselectedLng);
                            pre.setType(selectedType);
                            stations.add(0, pre);
                        }
                    }

                    if (stations.isEmpty()) {
                        toast("No nearby " + selectedType + " stations found");
                        return;
                    }

                    List<String> stationNames = new ArrayList<>();
                    for (Station station : stations) {
                        stationNames.add(station.getName());
                    }

                    ArrayAdapter<String> stnAdapter = createEnhancedSpinnerAdapter(stationNames);
                    stnAdapter.setDropDownViewResource(R.layout.grid_spinner_dropdown_item);
                    spnStation.setAdapter(stnAdapter);

                    // Preselect the station that came from the intent
                    if (preselectedStationId != null) {
                        for (int i = 0; i < stations.size(); i++) {
                            if (stations.get(i).getStationId().equals(preselectedStationId)) {
                                spnStation.setSelection(i);
                                selectedStationId = preselectedStationId;
                                break;
                            }
                        }
                    }

                    toast(arr.length() + " stations found");

                } catch (Exception e) {
                    Log.e("OwnerBooking", "JSON parse error", e);
                    toast("Error parsing station data");
                }
            });
        });
    }

    private void setupDatePicker() {
        btnSelectDate.setOnClickListener(v -> {
            // Calculate date range (today to today + 6 days)
            Calendar today = Calendar.getInstance();
            Calendar maxDate = Calendar.getInstance();
            maxDate.add(Calendar.DAY_OF_MONTH, 6); // 7 days total (today + 6 next days)

            // Set time to beginning of day
            today.set(Calendar.HOUR_OF_DAY, 0);
            today.set(Calendar.MINUTE, 0);
            today.set(Calendar.SECOND, 0);
            today.set(Calendar.MILLISECOND, 0);

            maxDate.set(Calendar.HOUR_OF_DAY, 23);
            maxDate.set(Calendar.MINUTE, 59);
            maxDate.set(Calendar.SECOND, 59);
            maxDate.set(Calendar.MILLISECOND, 999);

            MaterialDatePicker<Long> datePicker = MaterialDatePicker.Builder.datePicker()
                    .setTitleText("Select Reservation Date")
                    .setSelection(MaterialDatePicker.todayInUtcMilliseconds())
                    .setCalendarConstraints(
                            new CalendarConstraints.Builder()
                                    .setStart(today.getTimeInMillis()) // today as start date
                                    .setEnd(maxDate.getTimeInMillis()) // 6 days from today as max
                                    .setValidator(new DateValidatorWeekAhead())
                                    .build())
                    .build();

            // Enable/Disable Dates and ensure the dates are visible
            datePicker.addOnPositiveButtonClickListener(selection -> {
                selectedDate = new Date(selection);
                selectedDateStr = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(selectedDate);
                String displayDate = new SimpleDateFormat("EEE, MMM d, yyyy", Locale.getDefault()).format(selectedDate);

                tvSelectedDate.setText(displayDate);
                tvSelectedDate.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.text_primary));

                if (selectedStationId == null) {
                    showHint("Please select a station first");
                    return;
                }

                loadSlotsForStation(selectedStationId);
                updateConfirmButtonState();
            });

            datePicker.show(getSupportFragmentManager(), "DATE_PICKER");
        });
    }

    private static class DateValidatorWeekAhead implements CalendarConstraints.DateValidator {

        @Override
        public boolean isValid(long date) {
            Calendar selected = Calendar.getInstance();
            selected.setTimeInMillis(date);

            Calendar today = Calendar.getInstance();
            today.set(Calendar.HOUR_OF_DAY, 0);
            today.set(Calendar.MINUTE, 0);
            today.set(Calendar.SECOND, 0);
            today.set(Calendar.MILLISECOND, 0);

            Calendar maxDate = Calendar.getInstance();
            maxDate.set(Calendar.HOUR_OF_DAY, 23);
            maxDate.set(Calendar.MINUTE, 59);
            maxDate.set(Calendar.SECOND, 59);
            maxDate.set(Calendar.MILLISECOND, 999);
            maxDate.add(Calendar.DAY_OF_MONTH, 6); // 6 days from today

            return !selected.before(today) && !selected.after(maxDate);
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            // Required for parcelable
        }

        public static final Creator<DateValidatorWeekAhead> CREATOR = new Creator<DateValidatorWeekAhead>() {
            @Override
            public DateValidatorWeekAhead createFromParcel(Parcel in) {
                return new DateValidatorWeekAhead();
            }

            @Override
            public DateValidatorWeekAhead[] newArray(int size) {
                return new DateValidatorWeekAhead[size];
            }
        };
    }

    private void loadSlotsForStation(String stationId) {
        clearSlots();

        new AsyncTask<Void, Void, ApiResponse>() {
            @Override
            protected void onPreExecute() {
                Toast.makeText(OwnerBookingActivity.this, "Fetching slots...", Toast.LENGTH_SHORT).show();
                showHint("Loading available slots...");
            }

            @Override
            protected ApiResponse doInBackground(Void... voids) {
                try {
                    ApiResponse res = apiClient.getSlotsByStation(stationId);
                    if (res != null && res.isSuccess())
                        return res;
                    return apiClient.getStationPublic(stationId);
                } catch (Exception e) {
                    Log.e("OwnerBooking", "Error fetching slots", e);
                    return null;
                }
            }

            @Override
            protected void onPostExecute(ApiResponse res) {
                if (res == null) {
                    toast("Failed to fetch slots");
                    showHint("Failed to load slots. Please try again.");
                    return;
                }

                List<SlotItem> slotList = new ArrayList<>();
                try {
                    JSONArray arr;
                    if (res.getData().trim().startsWith("[")) {
                        arr = new JSONArray(res.getData());
                    } else {
                        JSONObject stationObj = new JSONObject(res.getData());
                        arr = stationObj.optJSONArray("slots");
                    }

                    if (arr == null) {
                        tvHints.setText("No slots available for this station");
                        return;
                    }

                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject o = arr.getJSONObject(i);
                        SlotItem s = new SlotItem();
                        s.slotId = o.optString("slotId");
                        s.number = o.optString("number");
                        s.status = o.optString("status");
                        s.connectorType = o.optString("connectorType");
                        slotList.add(s);
                    }

                    if (slotList.isEmpty()) {
                        showHint("No slots found for selected date.");
                        return;
                    }

                    slots = slotList;

                    List<String> slotStrings = new ArrayList<>();
                    for (SlotItem slot : slots) {
                        slotStrings.add(slot.toString());
                    }

                    ArrayAdapter<String> slotAdapter = createEnhancedSpinnerAdapter(slotStrings);
                    slotAdapter.setDropDownViewResource(R.layout.grid_spinner_dropdown_item);
                    spnSlot.setAdapter(slotAdapter);

                    showHint(slotList.size() + " slots available. Select a slot to continue.");
                } catch (Exception e) {
                    Log.e("OwnerBooking", "Parse slots failed", e);
                    showHint("Error parsing slot data.");
                }
            }
        }.execute();
    }

    private void loadTimeslotsFor(String stationId, String slotId, String dateYmd) {
        clearTimeSlots();

        new AsyncTask<Void, Void, ApiResponse>() {
            @Override
            protected void onPreExecute() {
                Toast.makeText(OwnerBookingActivity.this, "Fetching time slots...", Toast.LENGTH_SHORT).show();
                showHint("Loading available time slots...");
            }

            @Override
            protected ApiResponse doInBackground(Void... voids) {
                try {
                    String endpoint = String.format("/timeslot?stationId=%s&slotId=%s&date=%s", stationId, slotId,
                            dateYmd);
                    return apiClient.get(endpoint);
                } catch (Exception e) {
                    Log.e("OwnerBooking", "Error fetching timeslots", e);
                    return null;
                }
            }

            @Override
            protected void onPostExecute(ApiResponse res) {
                if (res == null) {
                    toast("Failed to fetch timeslots");
                    showHint("Failed to load time slots. Please try again.");
                    return;
                }

                if (!res.isSuccess()) {
                    toast("No timeslots available");
                    showHint("No time slots available for selected slot and date.");
                    return;
                }

                try {
                    Type t = new TypeToken<List<TimeSlotItem>>() {
                    }.getType();
                    List<TimeSlotItem> fetched = gson.fromJson(res.getData(), t);
                    if (fetched == null || fetched.isEmpty()) {
                        toast("No available time slots for this date");
                        showHint("No time slots available. Please select a different date or slot.");
                        return;
                    }

                    timeSlots = fetched;

                    List<String> timeSlotStrings = new ArrayList<>();
                    for (TimeSlotItem timeSlot : timeSlots) {
                        timeSlotStrings.add(timeSlot.toString());
                    }

                    ArrayAdapter<String> tsAdapter = createEnhancedSpinnerAdapter(timeSlotStrings);
                    tsAdapter.setDropDownViewResource(R.layout.grid_spinner_dropdown_item);
                    spnTimeSlot.setAdapter(tsAdapter);

                    showHint(timeSlots.size() + " time slots available. Select your preferred time.");
                } catch (Exception e) {
                    Log.e("OwnerBooking", "Failed to parse timeslots", e);
                    toast("Timeslot parse error");
                    showHint("Error loading time slots. Please try again.");
                }
            }
        }.execute();
    }

    private void setupConfirm() {
        btnConfirmBooking.setOnClickListener(v -> {
            if (selectedStationId == null || selectedDateStr == null || selectedSlotId == null
                    || selectedTimeSlotId == null) {
                toast("Please complete all selections");
                return;
            }

            new AsyncTask<Void, Void, ApiResponse>() {
                @Override
                protected void onPreExecute() {
                    Toast.makeText(OwnerBookingActivity.this, "Creating booking...", Toast.LENGTH_SHORT).show();
                    btnConfirmBooking.setEnabled(false);
                    btnConfirmBooking.setText("PROCESSING...");
                }

                @Override
                protected ApiResponse doInBackground(Void... voids) {
                    try {
                        return apiClient.createBooking(selectedStationId, selectedTimeSlotId, selectedSlotId);
                    } catch (Exception e) {
                        Log.e("OwnerBooking", "Booking error", e);
                        return null;
                    }
                }

                @Override
                protected void onPostExecute(ApiResponse res) {
                    btnConfirmBooking.setEnabled(true);
                    btnConfirmBooking.setText("CONFIRM BOOKING");

                    if (res == null) {
                        toast("Network error while creating booking");
                        return;
                    }
                    if (!res.isSuccess()) {
                        toast("Booking failed: " + res.getMessage());
                        return;
                    }

                    try {
                        JSONObject bookingObj = new JSONObject(res.getData());
                        String qrBase64 = bookingObj.optString("qrImageBase64");

                        if (qrBase64 != null && !qrBase64.isEmpty()) {
                            Intent intent = new Intent(OwnerBookingActivity.this, BookingConfirmationActivity.class);
                            intent.putExtra("qrBitmap", qrBase64);
                            startActivity(intent);
                            toast("✅ Booking created successfully!");
                        } else {
                            toast("Booking created, but no QR found");
                        }
                    } catch (Exception e) {
                        Log.e("BookingConfirm", "QR decode error", e);
                        toast("Error showing QR code");
                    }
                }
            }.execute();
        });
    }

    private void clearSlots() {
        slots.clear();
        spnSlot.setAdapter(null);
        selectedSlotId = null;
        updateConfirmButtonState();
    }

    private void clearTimeSlots() {
        timeSlots.clear();
        spnTimeSlot.setAdapter(null);
        selectedTimeSlotId = null;
        updateConfirmButtonState();
    }

    private void toast(String m) {
        Toast.makeText(this, m, Toast.LENGTH_SHORT).show();
    }
}