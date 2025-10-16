package com.evcharging.mobile;

import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.evcharging.mobile.model.User;
import com.evcharging.mobile.network.ApiClient;
import com.evcharging.mobile.network.ApiResponse;
import com.evcharging.mobile.session.SessionManager;

public class OwnerProfileActivity extends AppCompatActivity {

    private TextView tvName, tvEmail, tvNic, tvAccountStatus;
    private ImageView ivProfilePic;
    private Button btnEditProfile, btnForgetUser, btnDeactivate, btnRequestReactivation;
    private ImageButton btnBack;
    private SessionManager sessionManager;
    private ApiClient apiClient;
    private SwipeRefreshLayout swipeRefresh;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_owner_profile);

        sessionManager = new SessionManager(this);
        apiClient = new ApiClient(sessionManager);

        // Initialize swipe refresh
        swipeRefresh = findViewById(R.id.swipeRefresh);

        tvName = findViewById(R.id.tvOwnerName);
        tvEmail = findViewById(R.id.tvOwnerEmail);
        tvNic = findViewById(R.id.tvOwnerNic);
        tvAccountStatus = findViewById(R.id.tvAccountStatus);
        ivProfilePic = findViewById(R.id.ivProfilePic);
        btnEditProfile = findViewById(R.id.btnEditProfile);
        btnDeactivate = findViewById(R.id.btnDeactivate);
        btnRequestReactivation = findViewById(R.id.btnRequestReactivation);
        btnBack = findViewById(R.id.btnBack);
        btnForgetUser = findViewById(R.id.btnForgetUser);

        btnBack.setOnClickListener(v -> finish());
        btnEditProfile.setOnClickListener(v -> {
            // Start edit profile activity
            Intent intent = new Intent(this, OwnerEditProfileActivity.class);
            startActivity(intent);
        });

        showLocalUserProfile();

        // Set up swipe refresh
        swipeRefresh.setOnRefreshListener(() -> {
            new LoadProfileTask().execute();
        });

        // Deactivate button
        btnDeactivate.setOnClickListener(v -> new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Deactivate Account")
                .setMessage("Are you sure you want to deactivate your account?")
                .setPositiveButton("Yes", (dialog, which) -> new DeactivateTask().execute())
                .setNegativeButton("No", (dialog, which) -> dialog.dismiss())
                .show());

        // Reactivation button
        btnRequestReactivation.setOnClickListener(v -> new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Request Reactivation")
                .setMessage("Do you want to request reactivation of your account?")
                .setPositiveButton("Yes", (dialog, which) -> new ReactivationTask().execute())
                .setNegativeButton("No", (dialog, which) -> dialog.dismiss())
                .show());

        btnForgetUser.setOnClickListener(v -> new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Forget User")
                .setMessage("Do you want to forget your user data?")
                .setPositiveButton("Yes", (dialog, which) -> {
                    sessionManager.clearAll();
                    Toast.makeText(this, "User data cleared", Toast.LENGTH_SHORT).show();
                    startActivity(new Intent(this, LoginActivity.class));
                    finish();
                })
                .setNegativeButton("No", (dialog, which) -> dialog.dismiss())
                .show());

        setupFooterNavigation();
        highlightActiveTab("profile");
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh profile data every time the activity becomes visible
        refreshProfileData();
    }

    private void refreshProfileData() {
        // Force refresh from API to get updated data
        new LoadProfileTask().execute();
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

    /**
     * Load user profile from API
     */
    private class LoadProfileTask extends AsyncTask<Void, Void, User> {
        @Override
        protected User doInBackground(Void... voids) {
            ApiResponse response = apiClient.getUser();
            if (response.isSuccess() && response.getData() != null) {
                return apiClient.parseLoggedOwner(response.getData());
            }
            return null;
        }

        @Override
        protected void onPostExecute(User user) {
            swipeRefresh.setRefreshing(false); // Stop refresh animation
            if (user != null) {
                // Update the UI with the fresh data from API
                updateUIWithUserData(user);
                Log.d("OwnerProfile", "Profile data refreshed from API: " + user.getFullName());
            } else {
                Toast.makeText(OwnerProfileActivity.this,
                        "Failed to load profile", Toast.LENGTH_SHORT).show();
                // Fallback to local data
                showLocalUserProfile();
            }
        }
    }

    // Add method to update UI with fresh user data
    private void updateUIWithUserData(User user) {
        if (user != null) {
            tvName.setText(user.getFullName() != null ? user.getFullName() : "N/A");
            tvEmail.setText(user.getEmail() != null ? user.getEmail() : "N/A");
            tvNic.setText(user.getUserId() != null ? "NIC: " + user.getUserId() : "NIC: N/A");

            // ✅ ADD: Display phone number if available
            // If you have a TextView for phone in your layout, use it
            // tvPhone.setText(user.getPhone() != null ? "Phone: " + user.getPhone() : "Phone: Not set");

            // Or log it for debugging:
            Log.d("OwnerProfile", "Displaying user - Phone: '" + user.getPhone() + "'");

            if (user.isActive()) {
                tvAccountStatus.setText("Active");
                tvAccountStatus.setTextColor(getResources().getColor(R.color.green));
                btnDeactivate.setVisibility(View.VISIBLE);
                btnRequestReactivation.setVisibility(View.GONE);
            } else {
                tvAccountStatus.setText("Deactivated");
                tvAccountStatus.setTextColor(getResources().getColor(R.color.red));
                btnDeactivate.setVisibility(View.GONE);
                btnRequestReactivation.setVisibility(View.VISIBLE);
            }
        }
    }

    private String getOwnerNic() {
        User loggedInUser = sessionManager.getLoggedInUser();
        return (loggedInUser != null && loggedInUser.getUserId() != null)
                ? loggedInUser.getUserId().trim()
                : "";
    }

    private class DeactivateTask extends AsyncTask<Void, Void, ApiResponse> {
        private final String nic = getOwnerNic();

        @Override
        protected ApiResponse doInBackground(Void... voids) {
            return apiClient.deactivateEvOwner(nic);
        }

        @Override
        protected void onPostExecute(ApiResponse response) {
            Toast.makeText(OwnerProfileActivity.this, response.getMessage(), Toast.LENGTH_SHORT).show();
            if (response.isSuccess()) {
                new LoadProfileTask().execute(); // Refresh profile after deactivation
            }
        }
    }

    private class ReactivationTask extends AsyncTask<Void, Void, ApiResponse> {
        private final String nic;

        public ReactivationTask() {
            this.nic = getOwnerNic();
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            if (nic == null || nic.isEmpty()) {
                Toast.makeText(OwnerProfileActivity.this, "NIC is empty!", Toast.LENGTH_SHORT).show();
                cancel(true); // Stop the AsyncTask from executing
            }
        }

        @Override
        protected ApiResponse doInBackground(Void... voids) {
            if (isCancelled())
                return null; // In case task was cancelled in onPreExecute
            return apiClient.requestReactivation(nic);
        }

        @Override
        protected void onPostExecute(ApiResponse response) {
            if (response == null)
                return; // Task was cancelled
            Toast.makeText(OwnerProfileActivity.this, response.getMessage(), Toast.LENGTH_SHORT).show();
            if (response.isSuccess()) {
                new LoadProfileTask().execute(); // Refresh profile after reactivation
            }
        }
    }

    private void showLocalUserProfile() {
        User localUser = sessionManager.getLoggedInUser();
        updateUIWithUserData(localUser);
    }
}