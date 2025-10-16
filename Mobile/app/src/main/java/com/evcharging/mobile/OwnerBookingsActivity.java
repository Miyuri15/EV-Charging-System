package com.evcharging.mobile;

import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

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
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class OwnerBookingsActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private SwipeRefreshLayout swipeRefreshLayout;
    private OwnerBookingAdapter adapter;
    private List<BookingItem> bookings = new ArrayList<>();
    private List<BookingItem> filteredBookings = new ArrayList<>();
    private ApiClient apiClient;
    private SessionManager session;
    private final Gson gson = new Gson();
    private LinearLayout emptyStateLayout;
    private ChipGroup chipGroup;
    private String currentFilter = "All"; // Track current filter

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_owner_bookings);

        // Initialize session FIRST
        session = new SessionManager(this);
        apiClient = new ApiClient(session);

        initializeViews();
        setupRecyclerView();
        setupFilterChips();
        setupClickListeners();
        fetchBookings();
        setupFooterNavigation();
        highlightActiveTab("bookings");
    }

    private void initializeViews() {
        recyclerView = findViewById(R.id.recyclerViewBookings);
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        emptyStateLayout = findViewById(R.id.emptyStateLayout);
        chipGroup = findViewById(R.id.chipGroup);

        Log.d("OwnerBookings", "recyclerView: " + (recyclerView != null));
        Log.d("OwnerBookings", "swipeRefreshLayout: " + (swipeRefreshLayout != null));
        Log.d("OwnerBookings", "emptyStateLayout: " + (emptyStateLayout != null));
        Log.d("OwnerBookings", "chipGroup: " + (chipGroup != null));
        Log.d("OwnerBookings", "session: " + (session != null));
        Log.d("OwnerBookings", "apiClient: " + (apiClient != null));
    }

    private void setupRecyclerView() {
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new OwnerBookingAdapter(filteredBookings, this::openDetails);
        recyclerView.setAdapter(adapter);
    }

    private void setupFilterChips() {
        // Add null check to prevent crash
        if (chipGroup == null) {
            Log.w("OwnerBookings", "ChipGroup is null - filtering will be disabled");
            return;
        }

        // Add individual click listeners for debugging
        Chip chipAll = findViewById(R.id.chipAll);
        Chip chipPending = findViewById(R.id.chipPending);
        Chip chipApproved = findViewById(R.id.chipApproved);
        Chip chipCharging = findViewById(R.id.chipCharging);

        if (chipAll != null) {
            chipAll.setOnClickListener(v -> {
                Log.d("OwnerBookings", "Chip All clicked manually");
                applyFilter("All");
            });
        }
        if (chipPending != null) {
            chipPending.setOnClickListener(v -> {
                Log.d("OwnerBookings", "Chip Pending clicked manually");
                applyFilter("Pending");
            });
        }
        if (chipApproved != null) {
            chipApproved.setOnClickListener(v -> {
                Log.d("OwnerBookings", "Chip Approved clicked manually");
                applyFilter("Approved");
            });
        }
        if (chipCharging != null) {
            chipCharging.setOnClickListener(v -> {
                Log.d("OwnerBookings", "Chip Charging clicked manually");
                applyFilter("Charging");
            });
        }

        chipGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            Log.d("OwnerBookings", "ChipGroup checked state changed: " + checkedIds.size() + " chips checked");

            if (checkedIds.isEmpty()) {
                currentFilter = "All";
                applyFilter("All");
                return;
            }

            int checkedId = checkedIds.get(0);
            if (checkedId == R.id.chipAll) {
                currentFilter = "All";
                applyFilter("All");
            } else if (checkedId == R.id.chipPending) {
                currentFilter = "Pending";
                applyFilter("Pending");
            } else if (checkedId == R.id.chipApproved) {
                currentFilter = "Approved";
                applyFilter("Approved");
            } else if (checkedId == R.id.chipCharging) {
                currentFilter = "Charging";
                applyFilter("Charging");
            }

            Log.d("OwnerBookings", "Filter changed to: " + currentFilter);
        });

        // Select "All" by default
        chipGroup.check(R.id.chipAll);
        Log.d("OwnerBookings", "ChipGroup setup completed");
    }

    private void setupClickListeners() {
        ImageButton btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> onBackPressed());
        }

        swipeRefreshLayout.setOnRefreshListener(this::fetchBookings);
        swipeRefreshLayout.setColorSchemeColors(
                getResources().getColor(R.color.primary)
        );
    }

    private void applyFilter(String status) {
        Log.d("OwnerBookings", "Applying filter: " + status + ", Total bookings: " + bookings.size());

        filteredBookings.clear();

        if ("All".equals(status)) {
            filteredBookings.addAll(bookings);
            Log.d("OwnerBookings", "Showing ALL bookings: " + filteredBookings.size());
        } else {
            for (BookingItem booking : bookings) {
                if (status.equalsIgnoreCase(booking.getStatus())) {
                    filteredBookings.add(booking);
                }
            }
            Log.d("OwnerBookings", "Showing " + status + " bookings: " + filteredBookings.size());

            // Debug: Log all booking statuses
            for (BookingItem booking : bookings) {
                Log.d("OwnerBookings", "Booking status: " + booking.getStatus() + ", Station: " + booking.getStationName());
            }
        }

        adapter.notifyDataSetChanged();
        updateEmptyState();
    }

    private void updateEmptyState() {
        if (emptyStateLayout == null || recyclerView == null) {
            return;
        }

        if (filteredBookings.isEmpty()) {
            emptyStateLayout.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
            Log.d("OwnerBookings", "Empty state shown");
        } else {
            emptyStateLayout.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
            Log.d("OwnerBookings", "Showing " + filteredBookings.size() + " bookings");
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
    private void fetchBookings() {
        swipeRefreshLayout.setRefreshing(true);

        new AsyncTask<Void, Void, ApiResponse>() {
            @Override
            protected ApiResponse doInBackground(Void... voids) {
                try {
                    // Check if session is initialized
                    if (session == null) {
                        Log.e("OwnerBookings", "Session is null in doInBackground");
                        return null;
                    }

                    User loggedUser = session.getLoggedInUser();
                    String ownerId = (loggedUser != null) ? loggedUser.getUserId() : null;

                    if (ownerId == null) {
                        Log.e("OwnerBookings", "Owner ID is null");
                        return null;
                    }

                    Log.d("OwnerBookings", "Fetching bookings for owner: " + ownerId);
                    return apiClient.get("/bookings/owner/" + ownerId);

                } catch (Exception e) {
                    Log.e("OwnerBookings", "Error fetching bookings", e);
                    return null;
                }
            }

            @Override
            protected void onPostExecute(ApiResponse res) {
                swipeRefreshLayout.setRefreshing(false);

                if (res == null || !res.isSuccess()) {
                    String errorMsg = "Failed to load bookings";
                    if (res != null && res.getMessage() != null) {
                        errorMsg = res.getMessage();
                    }
                    Toast.makeText(OwnerBookingsActivity.this, errorMsg, Toast.LENGTH_SHORT).show();
                    updateEmptyState();
                    return;
                }

                try {
                    Type listType = new TypeToken<List<BookingItem>>() {}.getType();
                    List<BookingItem> fetched = gson.fromJson(res.getData(), listType);

                    if (fetched == null) {
                        Log.e("OwnerBookings", "Fetched bookings list is null");
                        Toast.makeText(OwnerBookingsActivity.this, "No bookings data received", Toast.LENGTH_SHORT).show();
                        updateEmptyState();
                        return;
                    }

                    Log.d("OwnerBookings", "Fetched " + fetched.size() + " bookings");

                    // Show only Pending / Approved / Charging
                    bookings.clear();
                    for (BookingItem b : fetched) {
                        if (b.getStatus() != null &&
                                (b.getStatus().equalsIgnoreCase("Pending")
                                        || b.getStatus().equalsIgnoreCase("Approved")
                                        || b.getStatus().equalsIgnoreCase("Charging"))) {
                            bookings.add(b);
                        }
                    }

                    Log.d("OwnerBookings", "Filtered to " + bookings.size() + " active bookings");

                    // Apply the current filter after loading data
                    applyFilter(currentFilter);

                } catch (Exception e) {
                    Log.e("OwnerBookings", "Parse error", e);
                    Toast.makeText(OwnerBookingsActivity.this, "Error parsing bookings", Toast.LENGTH_SHORT).show();
                    updateEmptyState();
                }
            }
        }.execute();
    }

    private void openDetails(BookingItem booking) {
        Intent intent = new Intent(this, OwnerBookingDetailsActivity.class);
        intent.putExtra("booking", gson.toJson(booking));
        startActivity(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh data when returning to this activity
        if (session == null) {
            session = new SessionManager(this);
        }
        if (apiClient == null) {
            apiClient = new ApiClient(session);
        }
    }
}