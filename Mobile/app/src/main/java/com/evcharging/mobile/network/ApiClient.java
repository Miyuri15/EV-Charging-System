package com.evcharging.mobile.network;

import android.util.Log;

import com.evcharging.mobile.model.Notification;
import com.evcharging.mobile.model.User;
import com.evcharging.mobile.session.SessionManager;
import com.evcharging.mobile.utils.JwtUtils;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import java.security.cert.X509Certificate;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * ApiClient – Handles all network requests for EV Charging Mobile App
 * 🔹 Supports both EV Owner and Operator functions
 * 🔹 Includes clean logging for request + response (pretty JSON)
 * 🔹 Safe with ngrok SSL (for dev)
 * 🔹 Uses JwtUtils for proper token parsing
 */
public class ApiClient {
    private static final String TAG = "ApiClient";
    private static final String BASE = "https://d43f975b8590.ngrok-free.app";
    private static final String BASE_URL = BASE + "/api";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private OkHttpClient client;
    private SessionManager sessionManager;
    private Gson gson;

    public ApiClient(SessionManager sessionManager) {
        this.sessionManager = sessionManager;
        this.gson = new Gson();

        // ✅ Allow HTTPS (ngrok) connections during development
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .hostnameVerifier((hostname, session) -> true)
                .sslSocketFactory(getUnsafeSslContext().getSocketFactory(), getTrustAllCertsManager())
                .build();
    }

    // ---------------------------------------------------------------------
    // SSL: Disable certificate validation for ngrok (development only)
    // ---------------------------------------------------------------------
    private SSLContext getUnsafeSslContext() {
        try {
            TrustManager[] trustAllCerts = new TrustManager[] { getTrustAllCertsManager() };
            SSLContext sslContext = SSLContext.getInstance("SSL");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
            return sslContext;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private X509TrustManager getTrustAllCertsManager() {
        return new X509TrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType) {
            }

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType) {
            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[] {};
            }
        };
    }

    public static String getBaseUrl() {
        return BASE;
    }

    public static String getApiBaseUrl() {
        return BASE_URL;
    }

    // ---------------------------------------------------------------------
    // AUTHENTICATION & USER MANAGEMENT
    // ---------------------------------------------------------------------
    public ApiResponse login(String email, String password) {
        try {
            JSONObject data = new JSONObject();
            data.put("email", email);
            data.put("password", password);

            RequestBody body = RequestBody.create(data.toString(), JSON);
            Request request = new Request.Builder()
                    .url(BASE_URL + "/auth/login")
                    .addHeader("X-Client-Type", "Mobile")
                    .post(body)
                    .build();

            logRequest("POST", "/auth/login", data);

            Response response = client.newCall(request).execute();
            String responseBody = response.body() != null ? response.body().string() : "";
            logApi("POST", "/auth/login", response, responseBody);

            if (response.code() == 200 && !responseBody.isEmpty()) {
                JSONObject json = new JSONObject(responseBody);
                String token = json.optString("token", null);
                if (token != null) {
                    sessionManager.saveToken(token);

                    // Immediately get and save user info from token
                    User user = JwtUtils.getUserFromToken(token);
                    if (user != null) {
                        sessionManager.saveLoggedInUser(user);
                        Log.d(TAG, "User logged in: " + user.getRole() + " - " + user.getFullName());
                    }

                    return new ApiResponse(true, "Login successful", token);
                }
            }

            return new ApiResponse(false, "Login failed", null);
        } catch (Exception e) {
            Log.e(TAG, "Login error", e);
            return new ApiResponse(false, "Network error", null);
        }
    }

    public ApiResponse registerOwner(String nic, String fullName, String email, String phone, String password) {
        try {
            JSONObject data = new JSONObject();
            data.put("nic", nic);
            data.put("fullName", fullName);
            data.put("email", email);
            data.put("phone", phone);
            data.put("password", password);

            logRequest("POST", "/owners/register", data);

            RequestBody body = RequestBody.create(data.toString(), JSON);
            Request request = new Request.Builder()
                    .url(BASE_URL + "/owners/register")
                    .addHeader("X-Client-Type", "Mobile")
                    .post(body)
                    .build();

            Response response = client.newCall(request).execute();
            String responseBody = response.body() != null ? response.body().string() : "";
            logApi("POST", "/owners/register", response, responseBody);

            if (response.isSuccessful()) {
                return new ApiResponse(true, "Registration successful", responseBody);
            } else {
                JSONObject err = new JSONObject(responseBody);
                return new ApiResponse(false, err.optString("message", "Registration failed"), null);
            }
        } catch (Exception e) {
            Log.e(TAG, "Owner registration error", e);
            return new ApiResponse(false, "Network or parsing error", null);
        }
    }

    public ApiResponse registerOperator(String fullName, String email, String phone, String password) {
        try {
            JSONObject data = new JSONObject();
            data.put("fullName", fullName);
            data.put("email", email);
            data.put("phone", phone);
            data.put("password", password);

            logRequest("POST", "/operators/register", data);

            RequestBody body = RequestBody.create(data.toString(), JSON);
            Request request = new Request.Builder()
                    .url(BASE_URL + "/operators/register")
                    .addHeader("X-Client-Type", "Mobile")
                    .post(body)
                    .build();

            Response response = client.newCall(request).execute();
            String responseBody = response.body() != null ? response.body().string() : "";
            logApi("POST", "/operators/register", response, responseBody);

            if (response.isSuccessful()) {
                return new ApiResponse(true, "Registration successful", responseBody);
            } else {
                JSONObject err = new JSONObject(responseBody);
                return new ApiResponse(false, err.optString("message", "Registration failed"), null);
            }
        } catch (Exception e) {
            Log.e(TAG, "Operator registration error", e);
            return new ApiResponse(false, "Network or parsing error", null);
        }
    }

    // ---------------------------------------------------------------------
    // USER PROFILE MANAGEMENT
    // ---------------------------------------------------------------------
    public ApiResponse getUser() {
        try {
            Request.Builder builder = new Request.Builder()
                    .url(BASE_URL + "/auth/me")
                    .get()
                    .addHeader("X-Client-Type", "Mobile");
            addAuth(builder);

            Response response = client.newCall(builder.build()).execute();
            String responseBody = response.body() != null ? response.body().string() : "";

            // ✅ ADD: Debug logging for raw response
            Log.d(TAG, "🔍 RAW /auth/me RESPONSE: " + responseBody);

            logApi("GET", "/auth/me", response, responseBody);

            if (response.isSuccessful())
                return new ApiResponse(true, "Success", responseBody);
            else {
                JSONObject err = new JSONObject(responseBody);
                return new ApiResponse(false, err.optString("message", "Failed"), null);
            }
        } catch (Exception e) {
            Log.e(TAG, "GET request error", e);
            return new ApiResponse(false, "Network error", null);
        }
    }

    public ApiResponse updateOwnerProfile(String nic, String fullName, String email, String phone) {
        try {
            JSONObject data = new JSONObject();
            data.put("fullName", fullName);
            data.put("email", email);
            data.put("phone", phone);

            return put("/owners/" + nic, data);
        } catch (JSONException e) {
            Log.e(TAG, "Error creating update profile data", e);
            return new ApiResponse(false, "Error updating profile", null);
        }
    }

    public ApiResponse updateOperatorProfile(String operatorId, String fullName, String email, String phone) {
        try {
            JSONObject data = new JSONObject();
            data.put("fullName", fullName);
            data.put("email", email);
            data.put("phone", phone);

            return put("/operators/" + operatorId, data);
        } catch (JSONException e) {
            Log.e(TAG, "Error creating update profile data", e);
            return new ApiResponse(false, "Error updating profile", null);
        }
    }

    public ApiResponse deactivateEvOwner(String nic) {
        return patch("/owners/" + nic + "/deactivate", null);
    }

    public ApiResponse requestReactivation(String nic) {
        return patch("/owners/" + nic + "/request-reactivation", null);
    }

    public ApiResponse deactivateOperator(String operatorId) {
        return patch("/operators/" + operatorId + "/deactivate", null);
    }

    // ---------------------------------------------------------------------
    // STATION MANAGEMENT (Operator & Shared)
    // ---------------------------------------------------------------------
    public ApiResponse getAllStations(boolean onlyActive) {
        String endpoint = onlyActive
                ? "/station/nearby?latitude=6.931960&longitude=79.857750&radiusKm=100&onlyActive=true"
                : "/station/nearby?latitude=6.931960&longitude=79.857750&radiusKm=100";
        return get(endpoint);
    }

    public ApiResponse getStationById(String stationId) {
        return get("/station/" + stationId);
    }

    public ApiResponse getNearbyStations(double latitude, double longitude, double radiusKm) {
        String endpoint = String.format("/station/nearby?latitude=%f&longitude=%f&radiusKm=%f",
                latitude, longitude, radiusKm);
        return get(endpoint);
    }

    public ApiResponse searchStations(String type, String location) {
        String endpoint = "/station/nearby?latitude=6.931960&longitude=79.857750&radiusKm=50";
        return get(endpoint); // We'll filter by type/location in the service layer
    }

    public ApiResponse getStationNameSuggestions(String type, String location) {
        String endpoint = "/station/names";
        if (type != null || location != null) {
            endpoint += "?";
            if (type != null)
                endpoint += "type=" + type + "&";
            if (location != null)
                endpoint += "location=" + location;
        }
        return get(endpoint);
    }

    // ---------------------------------------------------------------------
    // BOOKING MANAGEMENT (EV Owner Functions)
    // ---------------------------------------------------------------------
    public ApiResponse getAvailableTimeSlots(String stationId, String date) {
        String endpoint = "/bookings/stations/" + stationId + "/timeslots?date=" + date;
        return get(endpoint);
    }

    public ApiResponse getAvailableSlotsForTimeSlot(String timeSlotId) {
        String endpoint = "/bookings/timeslots/" + timeSlotId + "/available-slots";
        return get(endpoint);
    }

    public ApiResponse createBooking(String stationId, String timeSlotId, String slotId) {
        try {
            JSONObject data = new JSONObject();
            data.put("stationId", stationId);
            data.put("timeSlotId", timeSlotId);
            data.put("slotId", slotId);

            return post("/bookings", data);
        } catch (JSONException e) {
            Log.e(TAG, "Error creating booking data", e);
            return new ApiResponse(false, "Error creating booking", null);
        }
    }

    public ApiResponse getBookingsByOwner(String ownerId) {
        String endpoint = "/bookings/owner/" + ownerId;
        return get(endpoint);
    }

    public ApiResponse cancelBooking(String bookingId) {
        String endpoint = "/bookings/" + bookingId + "/cancel";
        return patch(endpoint, null);
    }

    public ApiResponse updateBooking(String bookingId, String newTimeSlotId, String newSlotId) {
        try {
            JSONObject data = new JSONObject();
            data.put("newTimeSlotId", newTimeSlotId);
            data.put("newSlotId", newSlotId);

            return put("/bookings/" + bookingId, data);
        } catch (JSONException e) {
            Log.e(TAG, "Error creating update booking data", e);
            return new ApiResponse(false, "Error updating booking", null);
        }
    }

    // ---------------------------------------------------------------------
    // OPERATOR BOOKING MANAGEMENT
    // ---------------------------------------------------------------------
    public ApiResponse getBookingsByStation(String stationId) {
        if (stationId == null || stationId.isEmpty() || stationId.equals("string"))
            return new ApiResponse(false, "No station assigned", null);
        return get("/bookings/station/" + stationId);
    }

    public ApiResponse getTodayBookingsByStation(String stationId) {
        return get("/bookings/station/" + stationId + "/today");
    }

    public ApiResponse getUpcomingBookingsByStation(String stationId) {
        return get("/bookings/station/" + stationId + "/upcoming");
    }

    public ApiResponse approveBooking(String bookingId) {
        String endpoint = "/bookings/" + bookingId + "/approve";
        return patch(endpoint, null);
    }

    public ApiResponse startCharging(String bookingId) {
        String endpoint = "/bookings/" + bookingId + "/start";
        return patch(endpoint, null);
    }

    public ApiResponse finalizeBooking(String bookingId) {
        String endpoint = "/bookings/" + bookingId + "/finalize";
        return patch(endpoint, null);
    }

    public ApiResponse generateQRCode(String bookingId) {
        return get("/bookings/" + bookingId + "/qrcode");
    }

    // ---------------------------------------------------------------------
    // SLOT MANAGEMENT (Operator Functions)
    // ---------------------------------------------------------------------
    public ApiResponse getSlotsByStation(String stationId) {
        return get("/slots/station/" + stationId);
    }

    public ApiResponse toggleSlotStatus(String slotId) {
        String endpoint = "/slots/" + slotId + "/toggle";
        return patch(endpoint, null);
    }

    public ApiResponse updateSlotStatus(String slotId, String status) {
        try {
            JSONObject data = new JSONObject();
            data.put("status", status);
            return patch("/slots/" + slotId + "/status", data);
        } catch (JSONException e) {
            Log.e(TAG, "Error creating slot status data", e);
            return new ApiResponse(false, "Error updating slot status", null);
        }
    }

    // ---------------------------------------------------------------------
    // NOTIFICATIONS
    // ---------------------------------------------------------------------
    public ApiResponse getUserNotifications() {
        return get("/notifications/user");
    }

    public ApiResponse markNotificationAsRead(String id) {
        return patch("/notifications/" + id + "/read", null);
    }

    public ApiResponse deleteNotification(String id) {
        return delete("/notifications/" + id);
    }

    public List<Notification> parseNotifications(String json) {
        try {
            Type listType = new TypeToken<List<Notification>>() {
            }.getType();
            return gson.fromJson(json, listType);
        } catch (Exception e) {
            Log.e(TAG, "Error parsing notifications", e);
            return null;
        }
    }

    // ---------------------------------------------------------------------
    // STATISTICS & COUNTS
    // ---------------------------------------------------------------------
    public ApiResponse countPendingBookings() {
        return get("/bookings/count/pending");
    }

    public ApiResponse countApprovedFutureBookings() {
        return get("/bookings/count/approved");
    }

    // ---------------------------------------------------------------------
    // LOGOUT
    // ---------------------------------------------------------------------
    public ApiResponse logout() {
        try {
            post("/auth/logout", new JSONObject());
        } catch (Exception ignored) {
        }
        sessionManager.clearToken();
        return new ApiResponse(true, "Logged out", null);
    }

    public ApiResponse logoutAndForget() {
        try {
            post("/auth/logout", new JSONObject());
        } catch (Exception ignored) {
        }
        sessionManager.clearAll();
        return new ApiResponse(true, "Logged out & credentials cleared", null);
    }

    // ---------------------------------------------------------------------
    // USER PARSER (Universal for Owner, Operator, Admin)
    // ---------------------------------------------------------------------
    public User parseLoggedOwner(String json) {
        if (json == null || json.isEmpty()) {
            Log.e(TAG, "Cannot parse user: empty JSON");
            return null;
        }

        try {
            JSONObject o = new JSONObject(json);
            Log.d(TAG, "📱 Raw API response for user: " + o.toString(2));

            // Extract ALL data from API response (this is the source of truth)
            String id = o.optString("nic", null);
            if (id == null || id.equals("null")) {
                id = o.optString("id", null);
            }

            String fullName = o.optString("fullName", null);
            String email = o.optString("email", null);
            String phone = o.optString("phone", null); // ✅ Get phone from API response
            boolean isActive = o.optBoolean("isActive", false);
            String createdAt = o.optString("createdAt", null);
            boolean reactivationRequested = o.optBoolean("reactivationRequested", false);

            Log.d(TAG, "📱 Extracted from API - Phone: '" + phone + "'");

            // Get user from JWT token for additional info (role, station info)
            User userFromToken = null;
            String token = sessionManager.getToken();
            if (token != null) {
                userFromToken = JwtUtils.getUserFromToken(token);
            }

            // ✅ FIX: Create user with ALL data from API response
            User user = new User();

            // Use ALL personal info from API response (this is the latest data)
            user.setUserId(id);
            user.setFullName(fullName);
            user.setEmail(email);
            user.setPhone(phone); // ✅ This will now use the phone from API, not JWT
            user.setActive(isActive);
            user.setCreatedAt(createdAt);
            user.setReactivationRequested(reactivationRequested);

            // Use only role and station info from JWT token
            if (userFromToken != null) {
                user.setRole(userFromToken.getRole());
                user.setStationId(userFromToken.getStationId());
                user.setStationName(userFromToken.getStationName());
                user.setStationLocation(userFromToken.getStationLocation());
            } else {
                // Fallback if JWT parsing fails
                user.setRole("Owner");
            }

            Log.d(TAG, "✅ Final parsed user - " +
                    "ID: " + user.getUserId() + ", " +
                    "Name: " + user.getFullName() + ", " +
                    "Email: " + user.getEmail() + ", " +
                    "Phone: '" + user.getPhone() + "', " + // ✅ This should now show the actual phone
                    "Role: " + user.getRole() + ", " +
                    "Active: " + user.isActive() + ", " +
                    "Station: " + (user.getStationId() != null ? user.getStationId() : "None"));

            return user;

        } catch (JSONException e) {
            Log.e(TAG, "Error parsing user JSON", e);
            return null;
        }
    }

    // ---------------------------------------------------------------------
    // GENERIC HTTP METHODS
    // ---------------------------------------------------------------------
    public ApiResponse get(String endpoint) {
        try {
            Request.Builder builder = new Request.Builder()
                    .url(BASE_URL + endpoint)
                    .get()
                    .addHeader("X-Client-Type", "Mobile");
            addAuth(builder);

            Response response = client.newCall(builder.build()).execute();
            String responseBody = response.body() != null ? response.body().string() : "";
            logApi("GET", endpoint, response, responseBody);

            if (response.isSuccessful())
                return new ApiResponse(true, "Success", responseBody);
            else {
                JSONObject err = new JSONObject(responseBody);
                return new ApiResponse(false, err.optString("message", "Failed"), null);
            }
        } catch (Exception e) {
            Log.e(TAG, "GET request error", e);
            return new ApiResponse(false, "Network error", null);
        }
    }

    public ApiResponse post(String endpoint, JSONObject data) {
        try {
            logRequest("POST", endpoint, data);
            RequestBody body = RequestBody.create(data.toString(), JSON);
            Request.Builder builder = new Request.Builder()
                    .url(BASE_URL + endpoint)
                    .post(body)
                    .addHeader("X-Client-Type", "Mobile");
            addAuth(builder);

            Response response = client.newCall(builder.build()).execute();
            String responseBody = response.body() != null ? response.body().string() : "";
            logApi("POST", endpoint, response, responseBody);

            if (response.isSuccessful())
                return new ApiResponse(true, "Success", responseBody);
            else {
                JSONObject err = new JSONObject(responseBody);
                return new ApiResponse(false, err.optString("message", "Request failed"), null);
            }
        } catch (Exception e) {
            Log.e(TAG, "POST request error", e);
            return new ApiResponse(false, "Network error", null);
        }
    }

    public ApiResponse patch(String endpoint, JSONObject data) {
        try {
            if (data != null)
                logRequest("PATCH", endpoint, data);
            RequestBody body = data != null
                    ? RequestBody.create(data.toString(), JSON)
                    : RequestBody.create("", JSON);

            Request.Builder builder = new Request.Builder()
                    .url(BASE_URL + endpoint)
                    .patch(body)
                    .addHeader("X-Client-Type", "Mobile");
            addAuth(builder);

            Response response = client.newCall(builder.build()).execute();
            String responseBody = response.body() != null ? response.body().string() : "";
            logApi("PATCH", endpoint, response, responseBody);

            if (response.isSuccessful())
                return new ApiResponse(true, "Success", responseBody);
            else {
                if (responseBody.isEmpty())
                    return new ApiResponse(false, "Empty error body", null);
                JSONObject err = new JSONObject(responseBody);
                return new ApiResponse(false, err.optString("message", "Failed"), null);
            }
        } catch (Exception e) {
            Log.e(TAG, "PATCH request error", e);
            return new ApiResponse(false, "Network error", null);
        }
    }

    public ApiResponse put(String endpoint, JSONObject data) {
        try {
            logRequest("PUT", endpoint, data);
            RequestBody body = RequestBody.create(data.toString(), JSON);
            Request.Builder builder = new Request.Builder()
                    .url(BASE_URL + endpoint)
                    .put(body)
                    .addHeader("X-Client-Type", "Mobile");
            addAuth(builder);

            Response response = client.newCall(builder.build()).execute();
            String responseBody = response.body() != null ? response.body().string() : "";
            logApi("PUT", endpoint, response, responseBody);

            if (response.isSuccessful())
                return new ApiResponse(true, "Success", responseBody);
            else {
                JSONObject err = new JSONObject(responseBody);
                return new ApiResponse(false, err.optString("message", "Failed"), null);
            }
        } catch (Exception e) {
            Log.e(TAG, "PUT request error", e);
            return new ApiResponse(false, "Network error", null);
        }
    }

    public ApiResponse delete(String endpoint) {
        try {
            Request.Builder builder = new Request.Builder()
                    .url(BASE_URL + endpoint)
                    .delete()
                    .addHeader("X-Client-Type", "Mobile");
            addAuth(builder);

            Response response = client.newCall(builder.build()).execute();
            String responseBody = response.body() != null ? response.body().string() : "";
            logApi("DELETE", endpoint, response, responseBody);

            if (response.isSuccessful())
                return new ApiResponse(true, "Deleted", responseBody);
            else {
                JSONObject err = new JSONObject(responseBody);
                return new ApiResponse(false, err.optString("message", "Failed"), null);
            }
        } catch (Exception e) {
            Log.e(TAG, "DELETE request error", e);
            return new ApiResponse(false, "Network error", null);
        }
    }
    // ---------------------------------------------------------------------
    // 🔹 Owner Functions (Newly added)
    // ---------------------------------------------------------------------

    // ADD: nearby stations filtered by type (Owner flow)
    public ApiResponse getNearbyStationsByType(String type, double latitude, double longitude, double radiusKm) {
        String endpoint = String.format("/station/nearby-by-type?type=%s&latitude=%f&longitude=%f&radiusKm=%f",
                type, latitude, longitude, radiusKm);

        return get(endpoint);
    }

    // OPTIONAL fallback if Owner cannot call /station/{id} yet.
    // This will try slots endpoint first; if server denies, the caller can decide
    // UX.
    public ApiResponse getStationPublic(String stationId) {
        // If you added /station/public/{stationId} in backend, map here.
        return get("/station/" + stationId); // current admin/operator-only; we'll handle 401/403 gracefully in UI
    }

    // ---------------------------------------------------------------------
    // 🔹 HELPERS: AUTH + LOGGING
    // ---------------------------------------------------------------------
    private void addAuth(Request.Builder builder) {
        String token = sessionManager.getToken();
        if (token != null)
            builder.addHeader("Authorization", "Bearer " + token);
    }

    private void logRequest(String method, String endpoint, JSONObject data) {
        Log.d(TAG, "─────────────────────────────────────────────");
        Log.d(TAG, "🚀 " + method + " " + BASE_URL + endpoint);
        Log.d(TAG, "🔑 Token: " + (sessionManager.getToken() != null ? "Present ✅" : "Missing ❌"));
        if (data != null)
            Log.d(TAG, "📤 Request Body:\n" + formatJson(data.toString()));
        else
            Log.d(TAG, "📤 Request Body: (empty)");
        Log.d(TAG, "─────────────────────────────────────────────");
    }

    private void logApi(String method, String endpoint, Response response, String responseBody) {
        int code = response != null ? response.code() : -1;
        Log.d(TAG, "📡 " + method + " " + BASE_URL + endpoint);
        Log.d(TAG, "📦 Status Code: " + code);
        if (responseBody != null && !responseBody.isEmpty())
            Log.d(TAG, "📨 Response Body:\n" + formatJson(responseBody));
        else
            Log.d(TAG, "📨 Response Body: (empty)");
        Log.d(TAG, "─────────────────────────────────────────────");
    }

    private String formatJson(String raw) {
        try {
            if (raw.trim().startsWith("{"))
                return new JSONObject(raw).toString(2);
            else if (raw.trim().startsWith("["))
                return new org.json.JSONArray(raw).toString(2);
            else
                return raw;
        } catch (Exception e) {
            return raw;
        }
    }
}