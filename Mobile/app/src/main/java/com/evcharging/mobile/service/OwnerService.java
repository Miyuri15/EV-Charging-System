//package com.evcharging.mobile.service;
//
//import static com.evcharging.mobile.network.ApiClient.getApiBaseUrl;
//
//import android.util.Log;
//
//import com.evcharging.mobile.network.ApiResponse;
//import com.evcharging.mobile.session.SessionManager;
//
//import org.json.JSONException;
//import org.json.JSONObject;
//
//import java.io.IOException;
//import java.util.Iterator;
//import java.util.concurrent.TimeUnit;
//
//import okhttp3.OkHttpClient;
//import okhttp3.Request;
//import okhttp3.RequestBody;
//import okhttp3.Response;
//
//public class OwnerService {
//
//    private static final String TAG = "OwnerService";
//    private static final okhttp3.MediaType JSON = okhttp3.MediaType.get("application/json; charset=utf-8");
//    private SessionManager sessionManager;
//    private OkHttpClient client;
//
//    public OwnerService() {
//        // Initialize OkHttpClient (unsafe SSL can be added if needed for ngrok/dev)
//        client = new OkHttpClient.Builder()
//                .connectTimeout(30, TimeUnit.SECONDS)
//                .readTimeout(30, TimeUnit.SECONDS)
//                .writeTimeout(30, TimeUnit.SECONDS)
//                .build();
//    }
//
//    public ApiResponse registerOwner(String nic, String fullName, String email, String phone, String password) {
//        try {
//            // Build JSON body
//            JSONObject jsonBody = new JSONObject();
//            jsonBody.put("nic", nic);
//            jsonBody.put("fullName", fullName);
//            jsonBody.put("email", email);
//            jsonBody.put("phone", phone);
//            jsonBody.put("password", password);
//
//            RequestBody body = RequestBody.create(jsonBody.toString(), JSON);
//
//            Request request = new Request.Builder()
//                    .url(getApiBaseUrl() + "/owners/register")
//                    .post(body)
//                    .build();
//
//            Response response = client.newCall(request).execute();
//
//            String responseBody = response.body() != null ? response.body().string() : "";
//            Log.d(TAG, "RegisterOwner Response: " + responseBody);
//
//            if (response.isSuccessful()) {
//                JSONObject jsonResponse = new JSONObject(responseBody);
//                String message = jsonResponse.optString("message", "Registration successful");
//                return new ApiResponse(true, message, null);
//            } else {
//                // Parse backend message even for errors
//                JSONObject jsonResponse = new JSONObject(responseBody);
//                String message = jsonResponse.optString("message", "Registration failed");
//
//                // Optional: parse detailed validation errors if present
//                if (jsonResponse.has("errors")) {
//                    JSONObject errors = jsonResponse.getJSONObject("errors");
//                    StringBuilder errorsMessage = new StringBuilder();
//                    for (Iterator<String> it = errors.keys(); it.hasNext();) {
//                        String key = it.next();
//                        for (int i = 0; i < errors.getJSONArray(key).length(); i++) {
//                            errorsMessage.append(errors.getJSONArray(key).getString(i));
//                            if (i < errors.getJSONArray(key).length() - 1)
//                                errorsMessage.append(" ");
//                        }
//                        errorsMessage.append("\n");
//                    }
//                    message = errorsMessage.toString().trim();
//                }
//
//                return new ApiResponse(false, message, null);
//            }
//
//        } catch (IOException | JSONException e) {
//            Log.e(TAG, "Error during owner registration", e);
//            return new ApiResponse(false, "Network or parsing error: " + e.getMessage(), null);
//        }
//    }
//
//    public ApiResponse updateEvOwner(String nic, String fullName, String email, String phone) {
//        try {
//            // Build JSON body matching backend requirements
//            JSONObject jsonBody = new JSONObject();
//            jsonBody.put("fullName", fullName);
//            jsonBody.put("email", email);
//            jsonBody.put("phone", phone);
//
//            RequestBody body = RequestBody.create(jsonBody.toString(), JSON);
//
//            // Build PUT request (add Authorization if required)
//            Request.Builder requestBuilder = new Request.Builder()
//                    .url(getApiBaseUrl() + "/owners/" + nic)
//                    .put(body)
//                    .addHeader("Accept", "application/json")
//                    .addHeader("Content-Type", "application/json");
//
//            // ✅ If you have a token stored in SessionManager
//            String token = sessionManager.getToken();
//            if (token != null && !token.isEmpty()) {
//                requestBuilder.addHeader("Authorization", "Bearer " + token);
//            }
//
//            Request request = requestBuilder.build();
//
//            // Execute request
//            Response response = client.newCall(request).execute();
//            Log.d(TAG, "UpdateOwner Status Code: " + response.code());
//            String responseBody = response.body() != null ? response.body().string().trim() : "";
//            Log.d(TAG, "UpdateOwner Response: " + responseBody);
//
//            // ✅ Handle unauthorized or empty response safely
//            if (response.code() == 401) {
//                return new ApiResponse(false, "Unauthorized: Please log in again.", null);
//            }
//
//            if (responseBody.isEmpty()) {
//                return new ApiResponse(false, "Empty response from server. Status: " + response.code(), null);
//            }
//
//            // ✅ Parse success or error responses
//            JSONObject jsonResponse = new JSONObject(responseBody);
//
//            if (response.isSuccessful()) {
//                String message = jsonResponse.optString("message", "Owner updated successfully");
//                return new ApiResponse(true, message, null);
//            } else {
//                String message = jsonResponse.optString("message", "Update failed");
//
//                if (jsonResponse.has("errors")) {
//                    JSONObject errors = jsonResponse.getJSONObject("errors");
//                    StringBuilder errorsMessage = new StringBuilder();
//                    for (Iterator<String> it = errors.keys(); it.hasNext();) {
//                        String key = it.next();
//                        for (int i = 0; i < errors.getJSONArray(key).length(); i++) {
//                            errorsMessage.append(errors.getJSONArray(key).getString(i));
//                            if (i < errors.getJSONArray(key).length() - 1)
//                                errorsMessage.append(" ");
//                        }
//                        errorsMessage.append("\n");
//                    }
//                    message = errorsMessage.toString().trim();
//                }
//
//                return new ApiResponse(false, message, null);
//            }
//
//        } catch (IOException | JSONException e) {
//            Log.e(TAG, "Error during owner update", e);
//            return new ApiResponse(false, "Network or parsing error: " + e.getMessage(), null);
//        }
//    }
//
//}


package com.evcharging.mobile.service;

import static com.evcharging.mobile.network.ApiClient.getApiBaseUrl;

import android.content.Context;
import android.util.Log;

import com.evcharging.mobile.network.ApiResponse;
import com.evcharging.mobile.session.SessionManager;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class OwnerService {

    private static final String TAG = "OwnerService";
    private static final okhttp3.MediaType JSON = okhttp3.MediaType.get("application/json; charset=utf-8");
    private SessionManager sessionManager;
    private OkHttpClient client;

    // Add constructor that accepts Context
    public OwnerService(Context context) {
        this.sessionManager = new SessionManager(context);
        initializeHttpClient();
    }

    // Keep existing constructor but initialize sessionManager properly
    public OwnerService() {
        // sessionManager will remain null - handle this in methods
        initializeHttpClient();
    }

    private void initializeHttpClient() {
        client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    public ApiResponse registerOwner(String nic, String fullName, String email, String phone, String password) {
        try {
            // Build JSON body
            JSONObject jsonBody = new JSONObject();
            jsonBody.put("nic", nic);
            jsonBody.put("fullName", fullName);
            jsonBody.put("email", email);
            jsonBody.put("phone", phone);
            jsonBody.put("password", password);

            RequestBody body = RequestBody.create(jsonBody.toString(), JSON);

            Request request = new Request.Builder()
                    .url(getApiBaseUrl() + "/owners/register")
                    .post(body)
                    .build();

            Response response = client.newCall(request).execute();

            String responseBody = response.body() != null ? response.body().string() : "";
            Log.d(TAG, "RegisterOwner Response: " + responseBody);

            if (response.isSuccessful()) {
                JSONObject jsonResponse = new JSONObject(responseBody);
                String message = jsonResponse.optString("message", "Registration successful");
                return new ApiResponse(true, message, null);
            } else {
                // Parse backend message even for errors
                JSONObject jsonResponse = new JSONObject(responseBody);
                String message = jsonResponse.optString("message", "Registration failed");

                // Optional: parse detailed validation errors if present
                if (jsonResponse.has("errors")) {
                    JSONObject errors = jsonResponse.getJSONObject("errors");
                    StringBuilder errorsMessage = new StringBuilder();
                    for (Iterator<String> it = errors.keys(); it.hasNext();) {
                        String key = it.next();
                        for (int i = 0; i < errors.getJSONArray(key).length(); i++) {
                            errorsMessage.append(errors.getJSONArray(key).getString(i));
                            if (i < errors.getJSONArray(key).length() - 1)
                                errorsMessage.append(" ");
                        }
                        errorsMessage.append("\n");
                    }
                    message = errorsMessage.toString().trim();
                }

                return new ApiResponse(false, message, null);
            }

        } catch (IOException | JSONException e) {
            Log.e(TAG, "Error during owner registration", e);
            return new ApiResponse(false, "Network or parsing error: " + e.getMessage(), null);
        }
    }

    public ApiResponse updateEvOwner(String nic, String fullName, String email, String phone) {
        try {
            // Build JSON body matching backend requirements
            JSONObject jsonBody = new JSONObject();
            jsonBody.put("fullName", fullName);
            jsonBody.put("email", email);
            jsonBody.put("phone", phone);

            RequestBody body = RequestBody.create(jsonBody.toString(), JSON);

            // Build PUT request (add Authorization if required)
            Request.Builder requestBuilder = new Request.Builder()
                    .url(getApiBaseUrl() + "/owners/" + nic)
                    .put(body)
                    .addHeader("Accept", "application/json")
                    .addHeader("Content-Type", "application/json");

            // ✅ FIX: Handle case where sessionManager is null
            String token = null;
            if (sessionManager != null) {
                token = sessionManager.getToken();
            } else {
                Log.w(TAG, "SessionManager is null in updateEvOwner - proceeding without token");
                // You might want to return an error here instead:
                // return new ApiResponse(false, "Authentication service not available", null);
            }

            // Only add Authorization header if token is available
            if (token != null && !token.isEmpty()) {
                requestBuilder.addHeader("Authorization", "Bearer " + token);
                Log.d(TAG, "Adding Authorization header with token");
            } else {
                Log.w(TAG, "No token available - making request without authentication");
            }

            Request request = requestBuilder.build();

            // Execute request
            Response response = client.newCall(request).execute();
            Log.d(TAG, "UpdateOwner Status Code: " + response.code());
            String responseBody = response.body() != null ? response.body().string().trim() : "";
            Log.d(TAG, "UpdateOwner Response: " + responseBody);

            // ✅ Handle unauthorized or empty response safely
            if (response.code() == 401) {
                return new ApiResponse(false, "Unauthorized: Please log in again.", null);
            }

            if (responseBody.isEmpty()) {
                return new ApiResponse(false, "Empty response from server. Status: " + response.code(), null);
            }

            // ✅ Parse success or error responses
            JSONObject jsonResponse = new JSONObject(responseBody);

            if (response.isSuccessful()) {
                String message = jsonResponse.optString("message", "Owner updated successfully");
                return new ApiResponse(true, message, null);
            } else {
                String message = jsonResponse.optString("message", "Update failed");

                if (jsonResponse.has("errors")) {
                    JSONObject errors = jsonResponse.getJSONObject("errors");
                    StringBuilder errorsMessage = new StringBuilder();
                    for (Iterator<String> it = errors.keys(); it.hasNext();) {
                        String key = it.next();
                        for (int i = 0; i < errors.getJSONArray(key).length(); i++) {
                            errorsMessage.append(errors.getJSONArray(key).getString(i));
                            if (i < errors.getJSONArray(key).length() - 1)
                                errorsMessage.append(" ");
                        }
                        errorsMessage.append("\n");
                    }
                    message = errorsMessage.toString().trim();
                }

                return new ApiResponse(false, message, null);
            }

        } catch (IOException | JSONException e) {
            Log.e(TAG, "Error during owner update", e);
            return new ApiResponse(false, "Network or parsing error: " + e.getMessage(), null);
        }
    }

    // Optional: Add method to set SessionManager if needed
    public void setSessionManager(SessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }
}