package com.evcharging.mobile;

import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.evcharging.mobile.model.User;
import com.evcharging.mobile.network.ApiClient;
import com.evcharging.mobile.network.ApiResponse;
import com.evcharging.mobile.session.SessionManager;
import com.evcharging.mobile.utils.DialogUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;

public class OperatorHomeActivity extends AppCompatActivity {

    private ApiClient apiClient;
    private SessionManager session;
    private ImageView ivProfile;
    private TextView tvWelcomeOperator, tvStationInfo, tvOperatorId;
    private Button btnViewProfile, btnUpdateSlots, btnViewBookings;
    private ImageButton btnLogout;
    private ListView lvTodayReservations;
    private SwipeRefreshLayout srTodayReservations;
    private LinearLayout emptyTodayReservations; // Add this

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_operator_home);
        setTitle("Operator Dashboard");
        FooterHelper.setupFooter(this);

        session = new SessionManager(this);
        apiClient = new ApiClient(session);
        bindViews();
        loadOperatorBasics();
        wireClicks();

        // pull-to-refresh
        srTodayReservations.setOnRefreshListener(this::loadTodayBookings);

        // Show empty state initially
        showEmptyTodayReservations();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // auto-refresh when returning to this screen
        loadTodayBookings();
    }

    private void bindViews() {
        ivProfile = findViewById(R.id.ivProfile);
        tvWelcomeOperator = findViewById(R.id.tvWelcomeOperator);
        tvStationInfo = findViewById(R.id.tvStationInfo);
        tvOperatorId = findViewById(R.id.tvOperatorId);
        btnViewProfile = findViewById(R.id.btnViewProfile);
        btnUpdateSlots = findViewById(R.id.btnUpdateSlots);
        btnViewBookings = findViewById(R.id.btnViewBookings);
        btnLogout = findViewById(R.id.btnLogout);
        lvTodayReservations = findViewById(R.id.lvTodayReservations);
        srTodayReservations = findViewById(R.id.srTodayReservations);
        emptyTodayReservations = findViewById(R.id.emptyTodayReservations); // Initialize this
    }

    private void loadOperatorBasics() {
        User user = session.getLoggedInUser();
        if (user == null) {
            Toast.makeText(this, "Session expired, please log in again.", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, LoginActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            finish();
            return;
        }

        tvWelcomeOperator.setText("Welcome, " + user.getFullName());
        tvOperatorId.setText("Operator ID: " + user.getUserId());

        String stationName = (user.getStationName() != null && !user.getStationName().equals("string"))
                ? user.getStationName() : "Pending";
        tvStationInfo.setText("Station: " + stationName);
    }

    private void wireClicks() {
        ivProfile.setOnClickListener(v -> startActivity(new Intent(this, OperatorProfileActivity.class)));
        btnViewProfile.setOnClickListener(v -> startActivity(new Intent(this, OperatorProfileActivity.class)));

        btnLogout.setOnClickListener(v -> attemptLogout());

        btnUpdateSlots.setOnClickListener(v -> {
            Intent intent = new Intent(this, OperatorUpdateSlotsActivity.class);
            startActivity(intent);
        });

        btnViewBookings.setOnClickListener(v -> startActivity(new Intent(this, AllBookingsActivity.class)));
    }

    private void showEmptyTodayReservations() {
        if (lvTodayReservations != null && emptyTodayReservations != null) {
            lvTodayReservations.setVisibility(View.GONE);
            emptyTodayReservations.setVisibility(View.VISIBLE);
        }
    }

    private void showTodayReservationsList() {
        if (lvTodayReservations != null && emptyTodayReservations != null) {
            lvTodayReservations.setVisibility(View.VISIBLE);
            emptyTodayReservations.setVisibility(View.GONE);
        }
    }

    private void loadTodayBookings() {
        User user = session.getLoggedInUser();

        if (user == null || user.getStationId() == null || user.getStationId().equals("string")) {
            String[] msg = {"No station assigned yet"};
            lvTodayReservations.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, msg));
            srTodayReservations.setRefreshing(false);
            showEmptyTodayReservations();
            return;
        }

        srTodayReservations.setRefreshing(true);

        new AsyncTask<Void, Void, ApiResponse>() {
            @Override
            protected ApiResponse doInBackground(Void... voids) {
                ApiClient apiClient = new ApiClient(session);
                return apiClient.get("/bookings/station/" + user.getStationId() + "/today");
            }

            @Override
            protected void onPostExecute(ApiResponse response) {
                srTodayReservations.setRefreshing(false);

                if (response == null || !response.isSuccess() || response.getData() == null) {
                    showEmptyTodayReservations();
                    return;
                }

                try {
                    JSONArray jsonArray = new JSONArray(response.getData());
                    ArrayList<JSONObject> reservations = new ArrayList<>();

                    for (int i = 0; i < jsonArray.length(); i++) {
                        JSONObject obj = jsonArray.getJSONObject(i);

                        // show only "Approved" or "Charging"
                        String status = obj.optString("status", "");
                        if ("Approved".equalsIgnoreCase(status) || "Charging".equalsIgnoreCase(status)) {
                            reservations.add(obj);
                        }
                    }

                    if (reservations.isEmpty()) {
                        showEmptyTodayReservations();
                        return;
                    }

                    showTodayReservationsList();
                    TodayReservationAdapter adapter =
                            new TodayReservationAdapter(OperatorHomeActivity.this, reservations);
                    lvTodayReservations.setAdapter(adapter);

                    lvTodayReservations.setOnItemClickListener((parent, view, position, id) -> {
                        JSONObject obj = reservations.get(position);
                        Intent intent = new Intent(OperatorHomeActivity.this, BookingDetailsActivity.class);
                        intent.putExtra("bookingId", obj.optString("bookingId"));
                        intent.putExtra("status", obj.optString("status"));
                        intent.putExtra("startTime", obj.optString("formattedStartTime", obj.optString("startTime")));
                        intent.putExtra("endTime", obj.optString("formattedEndTime", obj.optString("endTime")));
                        intent.putExtra("qrImageBase64", obj.optString("qrImageBase64"));
                        intent.putExtra("qrCode", obj.optString("qrCode"));
                        intent.putExtra("ownerName", obj.optString("ownerName"));
                        intent.putExtra("slotNumber", obj.optInt("slotNumber"));
                        startActivity(intent);
                    });

                } catch (Exception e) {
                    Log.e("BOOKINGS", "Error parsing bookings: " + e.getMessage());
                    showEmptyTodayReservations();
                }
            }

        }.execute();
    }

    private void attemptLogout() {
        DialogUtils.showDialog(
                this,
                "🚪 Logout Confirmation",
                "Are you sure you want to logout?",
                "Yes, Logout",
                () -> {
                    // Perform logout safely
                    ApiResponse response = apiClient.logout();
                    String message = (response != null && response.getMessage() != null)
                            ? response.getMessage()
                            : "Logged out successfully";

                    DialogUtils.showToast(this, message);

                    // Redirect to login screen
                    Intent intent = new Intent(this, LoginActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                }
        );
    }
}