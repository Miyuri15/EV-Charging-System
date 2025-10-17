package com.evcharging.mobile;

import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.evcharging.mobile.model.User;
import com.evcharging.mobile.network.ApiClient;
import com.evcharging.mobile.network.ApiResponse;
import com.evcharging.mobile.session.SessionManager;

import org.json.JSONObject;

public class OperatorProfileActivity extends AppCompatActivity {

    private TextView tvName, tvEmail, tvRole, tvUserId, tvStation, tvStationLoc, tvActive;
    private Button btnEditProfile;
    private EditText etName, etEmail;
    private SessionManager session;
    private ApiClient apiClient;
    private User user;
    private boolean editMode = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_operator_profile);
        setTitle("Operator Profile");

        FooterHelper.setupFooter(this);

        session = new SessionManager(this);
        apiClient = new ApiClient(session);

        bindViews();
        loadUserData();
        setupButtons();
        ImageButton btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }


    }

    private void bindViews() {
        tvName = findViewById(R.id.tvName);
        tvEmail = findViewById(R.id.tvEmail);
        tvRole = findViewById(R.id.tvRole);
        tvUserId = findViewById(R.id.tvUserId);
        tvStation = findViewById(R.id.tvStation);
        tvStationLoc = findViewById(R.id.tvStationLoc);
        tvActive = findViewById(R.id.tvActive);
        btnEditProfile = findViewById(R.id.btnEditProfile);

        // Dynamically create hidden editable fields for edit mode
        etName = new EditText(this);
        etEmail = new EditText(this);

        etName.setTextColor(getResources().getColor(android.R.color.black));
        etEmail.setTextColor(getResources().getColor(android.R.color.black));
        etName.setTextSize(18);
        etEmail.setTextSize(16);
        etName.setVisibility(View.GONE);
        etEmail.setVisibility(View.GONE);

        ((LinearLayout) tvName.getParent()).addView(etName, 1);
        ((LinearLayout) tvEmail.getParent()).addView(etEmail, 1);
    }

    private void loadUserData() {
        user = session.getLoggedInUser();
        if (user == null) {
            Toast.makeText(this, "Session expired. Please login again.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        tvName.setText(user.getFullName());
        tvEmail.setText(user.getEmail());
        tvRole.setText(user.getRoleDisplayName());
        tvUserId.setText(user.getUserId());

        tvStation.setText(
                (user.getStationName() != null && !user.getStationName().equals("string"))
                        ? user.getStationName()
                        : "Pending"
        );
        tvStationLoc.setText(
                (user.getStationLocation() != null && !user.getStationLocation().equals("string"))
                        ? user.getStationLocation()
                        : "Pending"
        );

        tvActive.setText(user.isActive() ? "Active" : "Inactive");
        tvActive.setTextColor(user.isActive() ? 0xFF43A047 : 0xFFE53935);
    }

    private void setupButtons() {
        btnEditProfile.setOnClickListener(v -> toggleEditMode(true));
    }

    private void toggleEditMode(boolean enable) {
        editMode = enable;
        if (enable) {
            etName.setText(tvName.getText().toString());
            etEmail.setText(tvEmail.getText().toString());

            tvName.setVisibility(View.GONE);
            tvEmail.setVisibility(View.GONE);
            etName.setVisibility(View.VISIBLE);
            etEmail.setVisibility(View.VISIBLE);

            btnEditProfile.setText("Save");
            btnEditProfile.setBackgroundColor(getResources().getColor(android.R.color.holo_green_dark));
            btnEditProfile.setOnClickListener(v -> saveProfileChanges());
        } else {
            tvName.setVisibility(View.VISIBLE);
            tvEmail.setVisibility(View.VISIBLE);
            etName.setVisibility(View.GONE);
            etEmail.setVisibility(View.GONE);

            btnEditProfile.setText("Edit Profile");
            btnEditProfile.setBackgroundColor(getResources().getColor(android.R.color.holo_blue_dark));
            btnEditProfile.setOnClickListener(v -> toggleEditMode(true));
        }
    }

    private void saveProfileChanges() {
        String newName = etName.getText().toString().trim();
        String newEmail = etEmail.getText().toString().trim();

        if (newName.isEmpty() || newEmail.isEmpty()) {
            Toast.makeText(this, "Please enter both name and email.", Toast.LENGTH_SHORT).show();
            return;
        }

        new UpdateProfileTask(newName, newEmail).execute();
    }

    /**
     * ✅ Background task to update operator profile
     * Calls: PUT /api/users/{userId}
     */
    private class UpdateProfileTask extends AsyncTask<Void, Void, ApiResponse> {
        private final String fullName, email;

        UpdateProfileTask(String fullName, String email) {
            this.fullName = fullName;
            this.email = email;
        }

        @Override
        protected void onPreExecute() {
            Toast.makeText(OperatorProfileActivity.this, "Saving changes...", Toast.LENGTH_SHORT).show();
        }

        @Override
        protected ApiResponse doInBackground(Void... voids) {
            try {
                JSONObject data = new JSONObject();
                data.put("fullName", fullName);
                data.put("email", email);
                Log.d("PROFILE_UPDATE", "Updating user with ID: " + user.getUserId());


                // ✅ Correct backend route: PUT /api/users/{userId}
                return apiClient.put("/users/" + user.getUserId(), data);

            } catch (Exception e) {
                e.printStackTrace();
                return new ApiResponse(false, "Error building request", null);
            }
        }

        @Override
        protected void onPostExecute(ApiResponse response) {
            if (response != null && response.isSuccess()) {
                user.setFullName(fullName);
                user.setEmail(email);
                session.saveLoggedInUser(user);

                tvName.setText(fullName);
                tvEmail.setText(email);

                Toast.makeText(OperatorProfileActivity.this, "Profile updated successfully!", Toast.LENGTH_SHORT).show();
                toggleEditMode(false);
            } else {
                String msg = (response != null) ? response.getMessage() : "Failed to update profile.";
                Toast.makeText(OperatorProfileActivity.this, msg, Toast.LENGTH_SHORT).show();
            }
        }
    }
}
