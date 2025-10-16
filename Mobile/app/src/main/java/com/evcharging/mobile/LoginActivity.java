package com.evcharging.mobile;

import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.evcharging.mobile.model.User;
import com.evcharging.mobile.network.ApiClient;
import com.evcharging.mobile.network.ApiResponse;
import com.evcharging.mobile.session.SessionManager;
import com.evcharging.mobile.utils.DialogUtils;
import com.evcharging.mobile.utils.JwtUtils;

import java.lang.ref.WeakReference;
import java.util.concurrent.Executors;

public class LoginActivity extends AppCompatActivity {

    private EditText etEmail, etPassword;
    private Button btnLogin;
    private TextView tvRegister;
    private CheckBox cbRememberMe;
    private ImageView ivTogglePassword;
    private ProgressBar progressBar;
    private ApiClient apiClient;
    private SessionManager sessionManager;

    private boolean isPasswordVisible = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        btnLogin = findViewById(R.id.btnLogin);
        tvRegister = findViewById(R.id.tvRegister);
        cbRememberMe = findViewById(R.id.cbRememberMe);
        ivTogglePassword = findViewById(R.id.ivTogglePassword);
        progressBar = findViewById(R.id.progressBar);

        sessionManager = new SessionManager(this);
        apiClient = new ApiClient(sessionManager);

        // Load saved credentials if remember me was enabled
        loadSavedCredentials();

        if (sessionManager.isLoggedIn()) {
            redirectToRoleHome(sessionManager.getToken());
            finish();
        }

        btnLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                attemptLogin();
            }
        });

        tvRegister.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(LoginActivity.this, RegistrationActivity.class));
            }
        });

        ivTogglePassword.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                togglePasswordVisibility();
            }
        });
    }

    private void togglePasswordVisibility() {
        if (isPasswordVisible) {
            etPassword.setTransformationMethod(PasswordTransformationMethod.getInstance());
            ivTogglePassword.setImageResource(R.drawable.ic_visibility_off);
            isPasswordVisible = false;
        } else {
            etPassword.setTransformationMethod(HideReturnsTransformationMethod.getInstance());
            ivTogglePassword.setImageResource(R.drawable.ic_visibility);
            isPasswordVisible = true;
        }

        etPassword.setSelection(etPassword.getText().length());
    }

    private void loadSavedCredentials() {
        if (sessionManager.isRememberMeEnabled()) {
            etEmail.setText(sessionManager.getSavedEmail());
            etPassword.setText(sessionManager.getSavedPassword());
            cbRememberMe.setChecked(true);
        }
    }

    private void attemptLogin() {
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();
        boolean rememberMe = cbRememberMe.isChecked();

        boolean isValid = true;

        if (TextUtils.isEmpty(email)) {
            etEmail.setError("Email is required");
            etEmail.requestFocus();
            isValid = false;
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEmail.setError("Please enter a valid email");
            etEmail.requestFocus();
            isValid = false;
        }

        if (TextUtils.isEmpty(password)) {
            etPassword.setError("Password is required");
            etPassword.requestFocus();
            isValid = false;
        }

        if (password.length() < 6) {
            etPassword.setError("Password must be at least 6 characters");
            etPassword.requestFocus();
            isValid = false;
        }

        if (!isValid) {
            return;
        }

        new LoginTask().execute(email, password, String.valueOf(rememberMe));
    }

    private class LoginTask extends AsyncTask<String, Void, ApiResponse> {
        private String email;
        private String password;
        private boolean rememberMe;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            btnLogin.setEnabled(false);
            progressBar.setVisibility(View.VISIBLE);
        }

        @Override
        protected ApiResponse doInBackground(String... params) {
            email = params[0];
            password = params[1];
            rememberMe = Boolean.parseBoolean(params[2]);
            return apiClient.login(email, password);
        }

        @Override
        protected void onPostExecute(ApiResponse response) {
            super.onPostExecute(response);

            btnLogin.setEnabled(true);
            progressBar.setVisibility(View.GONE);

            if (response.isSuccess() && response.getData() != null) {
                sessionManager.saveCredentials(email, password, rememberMe);
                DialogUtils.showToast(LoginActivity.this, response.getMessage());

                // Verify token role before navigating
                String token = response.getData();
                String role = JwtUtils.getRoleFromToken(token);

                if (role == null || role.equals("Unknown")) {
                    DialogUtils.showToast(LoginActivity.this, "Invalid or unsupported login token");
                    sessionManager.clearToken();
                    return;
                }

                loadAndSaveUser(token);

                if (role.equalsIgnoreCase("operator") || role.equalsIgnoreCase("owner")) {
                    redirectToRoleHome(token);
                    finish();
                } else {
                    // Unauthorized role (like Admin)
                    sessionManager.clearToken();
                    DialogUtils.showToast(LoginActivity.this,
                            "Access denied: This role cannot log in from the mobile app.");
                }
            } else {
                DialogUtils.showToast(LoginActivity.this, response.getMessage());
            }
        }
    }

    private void redirectToRoleHome(String token) {
        String role = JwtUtils.getRoleFromToken(token);
        if ("owner".equalsIgnoreCase(role)) {
            startActivity(new Intent(this, OwnerHomeActivity.class));
        } else if ("operator".equalsIgnoreCase(role)) {
            startActivity(new Intent(this, OperatorHomeActivity.class));
        } else {
            DialogUtils.showToast(this, "Unknown role!");
        }
    }

    private void loadAndSaveUser(String token) {
        Executors.newSingleThreadExecutor().execute(() -> {
            // This runs on a background thread
            ApiResponse response = apiClient.getUser();
            if (response.isSuccess() && response.getData() != null) {
                Log.d("LOGIN", "Profile Loaded");
                User user = apiClient.parseLoggedOwner(response.getData());

                // Save user on main thread
                runOnUiThread(() -> sessionManager.saveLoggedInUser(user));
            } else {
                runOnUiThread(() ->
                        DialogUtils.showToast(LoginActivity.this,
                                "Failed to load user profile after login")
                );
            }
        });
    }

}