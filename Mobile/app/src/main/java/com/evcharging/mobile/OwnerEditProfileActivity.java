package com.evcharging.mobile;

import android.annotation.SuppressLint;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.evcharging.mobile.model.User;
import com.evcharging.mobile.network.ApiResponse;
import com.evcharging.mobile.service.OwnerService;
import com.evcharging.mobile.session.SessionManager;

public class OwnerEditProfileActivity extends AppCompatActivity {

    private EditText etName, etEmail, etPhone;
    private Button btnSaveChanges;
    private OwnerService ownerService;
    private ImageButton btnBack;
    private SessionManager sessionManager;
    private User currentUser;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_owner_edit_profile);

        sessionManager = new SessionManager(this);

        etName = findViewById(R.id.etName);
        etEmail = findViewById(R.id.etEmail);
        etPhone = findViewById(R.id.etPhone);
        btnSaveChanges = findViewById(R.id.btnSaveChanges);
        btnBack = findViewById(R.id.btnBack);

        ownerService = new OwnerService(this);
        loadCurrentUserData();

        btnBack.setOnClickListener(v -> finish());
        btnSaveChanges.setOnClickListener(v -> new UpdateProfileTask().execute());
    }

    private void loadCurrentUserData() {
        currentUser = sessionManager.getLoggedInUser();
        if (currentUser != null) {
            etName.setText(currentUser.getFullName() != null ? currentUser.getFullName() : "");
            etEmail.setText(currentUser.getEmail() != null ? currentUser.getEmail() : "");

            // ✅ FIX: Properly handle phone - set to empty if null
            String currentPhone = currentUser.getPhone() != null ? currentUser.getPhone() : "";
            etPhone.setText(currentPhone);
            etPhone.setHint(currentPhone.isEmpty() ? "Edit New phone number" : currentPhone);

            Log.d("OwnerEditProfile", "Loaded user - " +
                    "Name: " + currentUser.getFullName() +
                    ", Email: " + currentUser.getEmail() +
                    ", Phone: '" + currentPhone + "'");
        } else {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private class UpdateProfileTask extends AsyncTask<Void, Void, ApiResponse> {
        @Override
        protected ApiResponse doInBackground(Void... voids) {
            @SuppressLint("WrongThread")
            String fullName = etName.getText().toString().trim();
            @SuppressLint("WrongThread")
            String email = etEmail.getText().toString().trim();
            @SuppressLint("WrongThread")
            String phone = etPhone.getText().toString().trim();

            // Get current values for comparison
            String currentFullName = currentUser.getFullName() != null ? currentUser.getFullName() : "";
            String currentEmail = currentUser.getEmail() != null ? currentUser.getEmail() : "";
            String currentPhone = currentUser.getPhone() != null ? currentUser.getPhone() : "";

            // Check if at least one field has been changed
            if (fullName.equals(currentFullName) &&
                    email.equals(currentEmail) &&
                    phone.equals(currentPhone)) {
                return new ApiResponse(false, "No changes made to update", null);
            }

            if (currentUser == null || currentUser.getUserId() == null) {
                return new ApiResponse(false, "User not available", null);
            }

            // ✅ FIX: Use current values for empty fields
            String finalFullName = !fullName.isEmpty() ? fullName : currentFullName;
            String finalEmail = !email.isEmpty() ? email : currentEmail;
            String finalPhone = !phone.isEmpty() ? phone : currentPhone;

            String userNic = currentUser.getUserId();
            Log.d("OwnerEditProfile", "Sending update - " +
                    "NIC: " + userNic +
                    ", Name: " + finalFullName +
                    ", Email: " + finalEmail +
                    ", Phone: '" + finalPhone + "'");

            return ownerService.updateEvOwner(userNic, finalFullName, finalEmail, finalPhone);
        }

        @Override
        protected void onPostExecute(ApiResponse response) {
            Toast.makeText(OwnerEditProfileActivity.this, response.getMessage(), Toast.LENGTH_SHORT).show();
            if (response.isSuccess()) {
                // Update local session with new data AND refresh from API
                refreshUserDataFromApi();
            }
        }
    }

    private void refreshUserDataFromApi() {
        new RefreshUserTask().execute();
    }

    private class RefreshUserTask extends AsyncTask<Void, Void, Boolean> {
        @Override
        protected Boolean doInBackground(Void... voids) {
            try {
                // Force a refresh of the user data from the API
                String token = sessionManager.getToken();
                if (token != null) {
                    sessionManager.saveToken(token); // This re-extracts user data from JWT
                    return true;
                }
                return false;
            } catch (Exception e) {
                Log.e("OwnerEditProfile", "Error refreshing user data", e);
                return false;
            }
        }

        @Override
        protected void onPostExecute(Boolean success) {
            if (success) {
                Log.d("OwnerEditProfile", "User data refreshed from API");
                finish(); // Close this activity and return to profile
            } else {
                // Fallback: update local data only
                updateLocalUserData();
                finish();
            }
        }
    }

    private void updateLocalUserData() {
        if (currentUser != null) {
            // Update user data in the current user object only for changed fields
            String newFullName = etName.getText().toString().trim();
            String newEmail = etEmail.getText().toString().trim();
            String newPhone = etPhone.getText().toString().trim();

            // Only update fields that have actually changed and are not empty
            if (!newFullName.isEmpty() && !newFullName.equals(currentUser.getFullName())) {
                currentUser.setFullName(newFullName);
            }
            if (!newEmail.isEmpty() && !newEmail.equals(currentUser.getEmail())) {
                currentUser.setEmail(newEmail);
            }
            if (!newPhone.isEmpty() && !newPhone.equals(currentUser.getPhone() != null ? currentUser.getPhone() : "")) {
                currentUser.setPhone(newPhone);
            }

            // Save updated user to database
            boolean saved = sessionManager.saveLoggedInUser(currentUser);
            if (saved) {
                Log.d("OwnerEditProfile", "User data updated locally");
            } else {
                Log.e("OwnerEditProfile", "Failed to update local user data");
            }
        }
    }
}