package com.evcharging.mobile;

import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.evcharging.mobile.adapter.OwnerBookingAdapter;
import com.evcharging.mobile.model.BookingItem;
import com.evcharging.mobile.model.User;
import com.evcharging.mobile.network.ApiClient;
import com.evcharging.mobile.network.ApiResponse;
import com.evcharging.mobile.session.SessionManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class ChargingHistoryActivity extends AppCompatActivity {

    private SwipeRefreshLayout swipeRefreshLayout;
    private RecyclerView recyclerViewHistory;
    private LinearLayout emptyStateLayout;


    private SessionManager session;
    private ApiClient apiClient;
    private OwnerBookingAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_charging_history);



        // Initialize
        session = new SessionManager(this);
        apiClient = new ApiClient(session);
// --- Setup Header Back Button ---
        ImageButton btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> onBackPressed());
        }
        swipeRefreshLayout = findViewById(R.id.swipe);
        recyclerViewHistory = findViewById(R.id.rvHistory);
        emptyStateLayout = findViewById(R.id.emptyStateLayout);


        // Setup Adapter + RecyclerView
        adapter = new OwnerBookingAdapter(new ArrayList<>(), item -> {
            Intent i = new Intent(this, OwnerBookingDetailsActivity.class);
            i.putExtra("bookingId", item.getBookingId());
            i.putExtra("stationId", item.getStationId());
            i.putExtra("stationName", item.getStationName());
            i.putExtra("slotNumber", item.getSlotNumber());
            i.putExtra("start", item.getStartTime());
            i.putExtra("end", item.getEndTime());
            i.putExtra("status", item.getStatus());
            i.putExtra("qrBase64", item.getQrImageBase64());
            startActivity(i);
        });

        recyclerViewHistory.setLayoutManager(new LinearLayoutManager(this));
        recyclerViewHistory.setAdapter(adapter);

        swipeRefreshLayout.setOnRefreshListener(this::loadData);

        setupFooterNavigation();
        highlightActiveTab("bookings");
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
        loadData();
    }

    /**
     * Load past (Finalized/Expired/Canceled) bookings
     */
    private void loadData() {
        swipeRefreshLayout.setRefreshing(true);

        new AsyncTask<Void, Void, List<BookingItem>>() {
            @Override
            protected List<BookingItem> doInBackground(Void... voids) {
                try {
                    // Fetch owner ID
                    User loggedUser = session.getLoggedInUser();
                    String ownerId = (loggedUser != null) ? loggedUser.getUserId() : null;
                    if (ownerId == null || ownerId.isEmpty()) return null;

                    ApiResponse res = apiClient.getBookingsByOwner(ownerId);
                    if (res == null || !res.isSuccess()) return null;

                    JSONArray arr = new JSONArray(res.getData());
                    List<BookingItem> historyList = new ArrayList<>();

                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject o = arr.getJSONObject(i);
                        BookingItem b = new BookingItem();

                        b.setBookingId(o.optString("bookingId", o.optString("_id", null)));
                        b.setStationId(o.optString("stationId", o.optString("StationId", null)));
                        b.setStationName(o.optString("stationName", ""));
                        b.setSlotId(o.optString("slotId"));
                        b.setSlotNumber(o.optString("slotNumber", o.optString("slotNo", "")));
                        b.setTimeSlotId(o.optString("timeSlotId"));
                        b.setOwnerId(o.optString("ownerId"));
                        b.setStatus(o.optString("status"));
                        b.setStartTime(o.optString("startTime"));
                        b.setEndTime(o.optString("endTime"));
                        b.setQrImageBase64(o.optString("qrImageBase64", null));

                        // Include only past bookings
                        if ("Finalized".equalsIgnoreCase(b.getStatus()) ||
                                "Cancelled".equalsIgnoreCase(b.getStatus()) ||
                                "Expired".equalsIgnoreCase(b.getStatus())) {
                            historyList.add(b);
                        }
                    }
                    return historyList;

                } catch (Exception e) {
                    e.printStackTrace();
                    return null;
                }
            }

            @Override
            protected void onPostExecute(List<BookingItem> data) {
                swipeRefreshLayout.setRefreshing(false);

                if (data == null || data.isEmpty()) {
                    showEmptyState(
                            "No Charging History Found",
                            "You haven't completed or cancelled any bookings yet.",
                            R.drawable.ic_history // use a relevant icon, or keep ic_ev_station
                    );
                    adapter.setData(new ArrayList<>());
                } else {
                    hideEmptyState();
                    adapter.setData(data);
                }

            }
            private void showEmptyState(String title, String subtitle, int iconRes) {
                if (emptyStateLayout != null) {
                    emptyStateLayout.setVisibility(View.VISIBLE);
                    recyclerViewHistory.setVisibility(View.GONE);

                    TextView tvTitle = emptyStateLayout.findViewById(R.id.tvEmptyTitle);
                    TextView tvSubtitle = emptyStateLayout.findViewById(R.id.tvEmptySubtitle);
                    ImageView ivIcon = emptyStateLayout.findViewById(R.id.ivEmptyIcon);

                    if (tvTitle != null) tvTitle.setText(title);
                    if (tvSubtitle != null) tvSubtitle.setText(subtitle);
                    if (ivIcon != null && iconRes != 0) ivIcon.setImageResource(iconRes);
                }
            }

            private void hideEmptyState() {
                if (emptyStateLayout != null) {
                    emptyStateLayout.setVisibility(View.GONE);
                    recyclerViewHistory.setVisibility(View.VISIBLE);
                }
            }

        }.execute();
    }
}
