package com.evcharging.mobile.session;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.evcharging.mobile.database.DatabaseHelper;
import com.evcharging.mobile.model.User;
import com.evcharging.mobile.utils.JwtUtils;

/**
 * SessionManager - Manages user session and authentication state
 *
 * Purpose: Handle token storage, user data caching, and remember-me
 * functionality
 * Now integrated with SQLite database for persistent user data storage
 *
 * Author: System (Enhanced)
 * Updated: 2025-10-06
 */
public class SessionManager {
    private static final String TAG = "SessionManager";
    private static final String PREF_NAME = "auth_prefs";
    private static final String KEY_TOKEN = "auth_token";
    private static final String KEY_REMEMBER_ME = "remember_me";
    private static final String KEY_SAVED_EMAIL = "saved_email";
    private static final String KEY_SAVED_PASSWORD = "saved_password";

    private SharedPreferences prefs;
    private DatabaseHelper dbHelper;
    private Context context;

    public SessionManager(Context context) {
        this.context = context;
        this.prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        this.dbHelper = DatabaseHelper.getInstance(context);
    }

    /**
     * Save authentication token and extract user data to database
     *
     * @param token JWT token from backend
     */
    public void saveToken(String token) {
        // Save token to SharedPreferences
        prefs.edit().putString(KEY_TOKEN, token).apply();
        Log.d(TAG, "Token saved successfully");

        // Extract user data from JWT and save to SQLite database
        saveUserFromToken(token);
    }

    /**
     * Extract user information from JWT token and save to database
     *
     * @param token JWT token string
     */
    private void saveUserFromToken(String token) {
        try {
            // Extract user data from JWT
            User user = JwtUtils.getUserFromToken(token);

            if (user != null) {
                Log.d("LOGIN_SUCCESS", "----------------------------------------");
                Log.d("LOGIN_SUCCESS", "User Full Name : " + user.getFullName());
                Log.d("LOGIN_SUCCESS", "Email          : " + user.getEmail());
                Log.d("LOGIN_SUCCESS", "Role           : " + user.getRole());
                Log.d("LOGIN_SUCCESS", "Is Active      : " + user.isActive());
                Log.d("LOGIN_SUCCESS", "Station ID     : " + user.getStationId());
                Log.d("LOGIN_SUCCESS", "Station Name   : " + user.getStationName());
                Log.d("LOGIN_SUCCESS", "Station Loc.   : " + user.getStationLocation());
                Log.d("LOGIN_SUCCESS", "Phone          : " + user.getPhone()); // ✅ Add phone logging
                Log.d("LOGIN_SUCCESS", "----------------------------------------");
            }

            if (user != null) {
                // Save user to SQLite database
                boolean saved = dbHelper.saveUser(user);

                if (saved) {
                    Log.d(TAG, "User data saved to database successfully");
                    Log.d(TAG, "User: " + user.getEmail() + " | Role: " + user.getRole() + " | Phone: " + user.getPhone());

                    // Check if operator has station assigned
                    if (user.isOperator()) {
                        if (user.hasStationAssigned()) {
                            Log.d(TAG, "Operator with station: " + user.getStationName());
                        } else {
                            Log.d(TAG, "Operator without station assignment");
                        }
                    }
                } else {
                    Log.e(TAG, "Failed to save user data to database");
                }
            } else {
                Log.e(TAG, "Failed to extract user from token");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error saving user from token: " + e.getMessage(), e);
        }
    }

    /**
     * Get JWT token from SharedPreferences
     *
     * @return JWT token string or null if not found
     */
    public String getToken() {
        return prefs.getString(KEY_TOKEN, null);
    }

    /**
     * Clear authentication token from SharedPreferences
     */
    public void clearToken() {
        prefs.edit().remove(KEY_TOKEN).apply();
        Log.d(TAG, "Token cleared");
    }

    /**
     * Check if user is logged in
     * Checks both token existence and database user record
     *
     * @return true if user is logged in, false otherwise
     */
    public boolean isLoggedIn() {
        String token = getToken();
        boolean hasToken = token != null;
        boolean hasUserInDb = dbHelper.isUserLoggedIn();

        Log.d(TAG, "Login status check - Has token: " + hasToken + ", Has user in DB: " + hasUserInDb);

        return hasToken && hasUserInDb;
    }

    /**
     * Get logged-in user from database
     *
     * @return User object or null if not found
     */
    public User getLoggedInUser() {
        return dbHelper.getLoggedInUser();
    }

    /**
     * Check if logged-in user is an operator
     *
     * @return true if operator, false otherwise
     */
    public boolean isOperator() {
        User user = getLoggedInUser();
        return user != null && user.isOperator();
    }

    /**
     * Check if logged-in user is an EV owner
     *
     * @return true if owner, false otherwise
     */
    public boolean isOwner() {
        User user = getLoggedInUser();
        return user != null && user.isOwner();
    }

    /**
     * Check if operator has station assigned
     *
     * @return true if operator has station, false otherwise
     */
    public boolean hasStationAssigned() {
        User user = getLoggedInUser();
        return user != null && user.hasStationAssigned();
    }

    /**
     * Get user's station ID (for operators)
     *
     * @return Station ID or null if not assigned
     */
    public String getStationId() {
        User user = getLoggedInUser();
        return user != null ? user.getStationId() : null;
    }

    /**
     * Update operator's station information
     * Used when admin assigns station to operator
     *
     * @param stationId       Station ID
     * @param stationName     Station name
     * @param stationLocation Station location
     * @return true if successful, false otherwise
     */
    public boolean updateStationInfo(String stationId, String stationName, String stationLocation) {
        User user = getLoggedInUser();
        if (user == null) {
            Log.e(TAG, "Cannot update station info: No user logged in");
            return false;
        }

        boolean updated = dbHelper.updateUserStation(user.getUserId(), stationId, stationName, stationLocation);
        if (updated) {
            Log.d(TAG, "Station info updated successfully");
        }
        return updated;
    }

    /**
     * Save login credentials for remember-me functionality
     *
     * @param email      User email
     * @param password   User password
     * @param rememberMe Whether to remember credentials
     */
    public void saveCredentials(String email, String password, boolean rememberMe) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(KEY_REMEMBER_ME, rememberMe);

        if (rememberMe) {
            editor.putString(KEY_SAVED_EMAIL, email);
            editor.putString(KEY_SAVED_PASSWORD, password);
            Log.d(TAG, "Credentials saved for remember me");
        } else {
            editor.remove(KEY_SAVED_EMAIL);
            editor.remove(KEY_SAVED_PASSWORD);
            Log.d(TAG, "Credentials cleared");
        }

        editor.apply();
    }

    /**
     * Check if remember-me is enabled
     *
     * @return true if enabled, false otherwise
     */
    public boolean isRememberMeEnabled() {
        return prefs.getBoolean(KEY_REMEMBER_ME, false);
    }

    /**
     * Get saved email from remember-me
     *
     * @return Saved email or empty string
     */
    public String getSavedEmail() {
        return prefs.getString(KEY_SAVED_EMAIL, "");
    }

    /**
     * Get saved password from remember-me
     *
     * @return Saved password or empty string
     */
    public String getSavedPassword() {
        return prefs.getString(KEY_SAVED_PASSWORD, "");
    }

    /**
     * Clear all session data including token and database records
     * Used for complete logout
     */
    public void clearAll() {
        // Clear SharedPreferences
        prefs.edit().clear().apply();
        Log.d(TAG, "All session preferences cleared");

        // Clear database
        dbHelper.deleteUser();
        Log.d(TAG, "User data cleared from database");

        Log.d(TAG, "Complete session data cleared");
    }

    /**
     * Logout user - clear token and optionally clear remember-me
     *
     * @param clearRememberMe Whether to also clear saved credentials
     */
    public void logout(boolean clearRememberMe) {
        // Clear token
        clearToken();

        // Clear user from database
        dbHelper.deleteUser();

        // Clear remember-me if requested
        if (clearRememberMe) {
            prefs.edit()
                    .remove(KEY_REMEMBER_ME)
                    .remove(KEY_SAVED_EMAIL)
                    .remove(KEY_SAVED_PASSWORD)
                    .apply();
            Log.d(TAG, "Logout complete with credentials cleared");
        } else {
            Log.d(TAG, "Logout complete, credentials preserved");
        }
    }

    /**
     * Save or update the logged-in user in the database
     *
     * @param user User object to save
     * @return true if successfully saved/updated, false otherwise
     */
    public boolean saveLoggedInUser(User user) {
        if (user == null) {
            Log.e(TAG, "Cannot save null user");
            return false;
        }

        boolean saved = dbHelper.saveUser(user);
        if (saved) {
            Log.d(TAG, "User saved/updated in database: " + user.getEmail());
        } else {
            Log.e(TAG, "Failed to save user in database: " + user.getEmail());
        }
        return saved;
    }
}
