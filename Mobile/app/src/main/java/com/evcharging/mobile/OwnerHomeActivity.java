package com.evcharging.mobile;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.util.Log;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

import com.evcharging.mobile.model.Notification;
import com.evcharging.mobile.model.User;
import com.evcharging.mobile.network.ApiClient;
import com.evcharging.mobile.network.ApiResponse;
import com.evcharging.mobile.session.SessionManager;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MarkerOptions;
import android.location.Location;
import com.evcharging.mobile.model.Station;
import com.evcharging.mobile.service.StationService;

import java.util.ArrayList;
import java.util.List;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.material.card.MaterialCardView;

public class OwnerHomeActivity extends AppCompatActivity
                implements OnMapReadyCallback {

        private static final String MAP_VIEW_BUNDLE_KEY = "MapViewBundleKey";
        private static final String CHANNEL_ID = "ev_notifications";
        private FusedLocationProviderClient fusedLocationClient;

        private MapView mapView;
        private MaterialCardView btnReserve, btnBookings, btnHistory;
        private MaterialCardView btnMyBookings, btnChargingHistory;
        private ImageButton btnLogout, btnNotifications;
        private ImageView ivProfile;
        private TextView tvWelcomeOwner, tvOwnerId;

        private ApiClient apiClient;
        private TextView tvNotificationCount;
        private int notificationCount = 0;

        private GoogleMap googleMap;
        private StationService stationService;
        private Location cachedLocation;
        private AutoCompleteTextView searchStations;
        private Spinner spinnerStationType;
        private String selectedStationType = "DC";
        private Station selectedStation;

        @Override
        protected void onCreate(Bundle savedInstanceState) {
                super.onCreate(savedInstanceState);

                // Initialize services
                apiClient = new ApiClient(new SessionManager(this));
                stationService = new StationService(apiClient);

                setContentView(R.layout.activity_owner_home);

                MyApp app = (MyApp) getApplication();
                app.getNotificationCountLiveData().observe(this, count -> {
                        if (count != null) {
                                notificationCount = count;
                                updateNotificationCount();
                        }
                });

                // --- Initialize UI components ---
                mapView = findViewById(R.id.mapView);
                btnReserve = findViewById(R.id.btnReserveSlot);
                btnBookings = findViewById(R.id.btnMyBookings);
                btnHistory = findViewById(R.id.btnChargingHistory);
                ivProfile = findViewById(R.id.ivProfileOwner);
                btnLogout = findViewById(R.id.btnLogoutOwner);
                btnNotifications = findViewById(R.id.btnNotifications);
                tvNotificationCount = findViewById(R.id.tvNotificationCount);
                searchStations = findViewById(R.id.searchStations);
                spinnerStationType = findViewById(R.id.spinnerStationType);

                // --- Welcome text ---
                tvWelcomeOwner = findViewById(R.id.tvWelcomeOwner);
                tvOwnerId = findViewById(R.id.tvOwnerId);
                tvWelcomeOwner.setText("Welcome, " + getOwnerName() + "!");
                tvOwnerId.setText("Owner ID: " + getOwnerId());

                // --- Location ---
                fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

                // --- MapView ---
                Bundle mapViewBundle = null;
                if (savedInstanceState != null) {
                        mapViewBundle = savedInstanceState.getBundle(MAP_VIEW_BUNDLE_KEY);
                }
                mapView.onCreate(mapViewBundle);
                mapView.getMapAsync(this);

                btnMyBookings = findViewById(R.id.btnMyBookings);
                btnChargingHistory = findViewById(R.id.btnChargingHistory);

                // --- Notification channel ---
                createNotificationChannel();

                // --- Button actions ---
                setupButtonActions();
                setupStationSearch();
                setupTypeSelection();
                setupFooterNavigation();
                highlightActiveTab("home");

        }

        // ---------------- Footer Navigation Setup ----------------
        private void setupFooterNavigation() {
                LinearLayout navHome = findViewById(R.id.navHome);
                LinearLayout navBookings = findViewById(R.id.navBookings);
                LinearLayout navProfile = findViewById(R.id.navProfile);

                if (navHome == null || navBookings == null || navProfile == null)
                        return; // Footer not included on this layout

//                navHome.setOnClickListener(v -> {
//                        Intent i = new Intent(this, OwnerHomeActivity.class);
//                        i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
//                        startActivity(i);
//                });
                navHome.setEnabled(false);

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

        private void setupTypeSelection() {
                // Define the types of stations
                String[] stationTypes = new String[] { "DC", "AC" };

                // Create ArrayAdapter for the spinner
                ArrayAdapter<String> typeAdapter = new ArrayAdapter<>(
                                this,
                                android.R.layout.simple_spinner_item,
                                stationTypes);
                typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

                // Attach adapter to spinner
                spinnerStationType.setAdapter(typeAdapter);

                // Optional: handle selection events
                spinnerStationType.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                        @Override
                        public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                                String selectedType = stationTypes[position];
                                // You can trigger a search update here if needed
                                // fetchStationSuggestions(searchStations.getText().toString().trim());
                        }

                        @Override
                        public void onNothingSelected(AdapterView<?> parent) {
                                // Do nothing
                        }
                });
        }

        private void setupStationSearch() {
                searchStations.addTextChangedListener(new TextWatcher() {
                        @Override
                        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                        }

                        @Override
                        public void onTextChanged(CharSequence s, int start, int before, int count) {
                        }

                        @Override
                        public void afterTextChanged(Editable s) {
                                String query = s.toString().trim();
                                if (!query.isEmpty()) {
                                        fetchStationSuggestions(query);
                                }
                        }
                });

                searchStations.setOnItemClickListener((parent, view, position, id) -> {
                        selectedStation = (Station) parent.getItemAtPosition(position);
                        showStationOnMap(selectedStation);
                });
        }

        private void fetchStationSuggestions(String query) {
                new Thread(() -> {
                        String type = spinnerStationType.getSelectedItem() != null
                                        ? spinnerStationType.getSelectedItem().toString()
                                        : "";
                        List<Station> stations = stationService.searchStations(type, query);

                        if (stations != null && !stations.isEmpty()) {
                                runOnUiThread(() -> {
                                        ArrayAdapter<Station> adapter = new ArrayAdapter<>(
                                                        this,
                                                        android.R.layout.simple_dropdown_item_1line,
                                                        stations);

                                        searchStations.setAdapter(adapter);
                                        adapter.notifyDataSetChanged();

                                        searchStations.setAdapter(adapter);
                                        adapter.notifyDataSetChanged();
                                });
                        }
                }).start();
        }

        private void showStationOnMap(Station station) {
                if (googleMap != null) {
                        googleMap.clear(); // Clear previous markers
                        LatLng stationLatLng = new LatLng(station.getLatitude(), station.getLongitude());

                        // Add marker for the selected station
                        googleMap.addMarker(new MarkerOptions()
                                        .position(stationLatLng)
                                        .title(station.getName())
                                        .snippet(station.getLocation()));

                        // Move camera to the station
                        googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(stationLatLng, 15));

                        // Add marker click listener for navigation
                        googleMap.setOnMarkerClickListener(marker -> {
                                LatLng dest = marker.getPosition();

                                if (cachedLocation != null) {
                                        // Build Google Maps navigation URL
                                        String uri = "http://maps.google.com/maps?saddr=" +
                                                        cachedLocation.getLatitude() + ","
                                                        + cachedLocation.getLongitude() +
                                                        "&daddr=" + dest.latitude + "," + dest.longitude;

                                        // Launch Google Maps
                                        Intent intent = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(uri));
                                        intent.setPackage("com.google.android.apps.maps");
                                        if (intent.resolveActivity(getPackageManager()) != null) {
                                                startActivity(intent);
                                        } else {
                                                Toast.makeText(this, "Google Maps app not found", Toast.LENGTH_SHORT)
                                                                .show();
                                        }
                                } else {
                                        Toast.makeText(this, "Current location not available", Toast.LENGTH_SHORT)
                                                        .show();
                                }

                                return true; // Consume the click
                        });
                }
        }

        private String getOwnerName() {
                SessionManager sessionManager = new SessionManager(this);

                User loggedInUser = sessionManager.getLoggedInUser();

                String ownerName = "EV Owner";

                if (loggedInUser != null && loggedInUser.getFullName() != null) {
                        String fullName = loggedInUser.getFullName().trim();
                        // Split by space and take the first part
                        String[] nameParts = fullName.split("\\s+");
                        ownerName = nameParts.length > 0 ? nameParts[0] : fullName;
                }
                return ownerName;
        }

        private String getOwnerId() {
                SessionManager sessionManager = new SessionManager(this);
                User loggedInUser = sessionManager.getLoggedInUser();

                String ownerId = "OW001";

                if (loggedInUser != null && loggedInUser.getUserId() != null
                                && !loggedInUser.getUserId().trim().isEmpty()) {
                        ownerId = "OW" + loggedInUser.getUserId().trim();
                }

                return ownerId;
        }

        private void setupButtonActions() {
                btnNotifications.setOnClickListener(v -> {
                        startActivity(new Intent(this, NotificationActivity.class));
                        MyApp app = (MyApp) getApplication();
                        app.resetNotificationCount();
                });

                btnReserve.setOnClickListener(v -> {
                        Intent intent = new Intent(this, OwnerBookingActivity.class);

                        if (selectedStation != null) {
                                intent.putExtra("selected_station_id", selectedStation.getStationId());
                                intent.putExtra("selected_station_name", selectedStation.getName());
                                intent.putExtra("selected_station_lat", selectedStation.getLatitude());
                                intent.putExtra("selected_station_lng", selectedStation.getLongitude());
                                intent.putExtra("selected_station_location", selectedStation.getLocation());
                        }

                        if (cachedLocation != null) {
                                intent.putExtra("current_lat", cachedLocation.getLatitude());
                                intent.putExtra("current_lng", cachedLocation.getLongitude());
                        }

                        startActivity(intent);
                });

                btnMyBookings.setOnClickListener(v -> startActivity(new Intent(this, OwnerBookingsActivity.class)));

                btnChargingHistory.setOnClickListener(
                                v -> startActivity(new Intent(this, ChargingHistoryActivity.class)));

                ivProfile.setOnClickListener(v -> startActivity(new Intent(this, OwnerProfileActivity.class)));

                btnLogout.setOnClickListener(v -> attemptLogout());
        }

        @Override
        public void onMapReady(@NonNull GoogleMap map) {
                this.googleMap = map;

                if (ActivityCompat.checkSelfPermission(this,
                                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                        ActivityCompat.requestPermissions(this,
                                        new String[] { Manifest.permission.ACCESS_FINE_LOCATION }, 1001);
                        return;
                }

                googleMap.setMyLocationEnabled(true);
                googleMap.getUiSettings().setZoomControlsEnabled(true);
                googleMap.getUiSettings().setCompassEnabled(true);
                googleMap.getUiSettings().setMyLocationButtonEnabled(true);

                if (cachedLocation != null) {
                        // Use cached location
                        moveCameraToLocation(cachedLocation);
                } else {
                        // Fetch location
                        fusedLocationClient.getCurrentLocation(
                                        com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY, null)
                                        .addOnSuccessListener(this, location -> {
                                                if (location != null) {
                                                        cachedLocation = location; // Cache the location
                                                        moveCameraToLocation(location);
                                                } else {
                                                        Toast.makeText(this, "Unable to get location.",
                                                                        Toast.LENGTH_SHORT).show();
                                                }
                                        }).addOnFailureListener(e -> {
                                                Toast.makeText(this, "Error getting location", Toast.LENGTH_SHORT)
                                                                .show();
                                        });
                }
        }

        @Override
        public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                        @NonNull int[] grantResults) {
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
                if (requestCode == 1001) {
                        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                                // Try again now that permission is granted
                                onMapReady(googleMap);
                        } else {
                                Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show();
                        }
                }
        }

        // Lifecycle methods for MapView and SignalR
        @Override
        protected void onStart() {
                super.onStart();
                mapView.onStart();
        }

        @Override
        protected void onResume() {
                super.onResume();
                mapView.onResume();
        }

        @Override
        protected void onPause() {
                // Pause MapView before calling super
                mapView.onPause();
                super.onPause();
        }

        @Override
        protected void onStop() {
                mapView.onStop();
                super.onStop();
        }

        @Override
        protected void onDestroy() {
                mapView.onDestroy();
                super.onDestroy();
        }

        @Override
        public void onLowMemory() {
                super.onLowMemory();
                mapView.onLowMemory();
        }

        @Override
        protected void onSaveInstanceState(@NonNull Bundle outState) {
                super.onSaveInstanceState(outState);
                Bundle mapViewBundle = outState.getBundle(MAP_VIEW_BUNDLE_KEY);
                if (mapViewBundle == null) {
                        mapViewBundle = new Bundle();
                        outState.putBundle(MAP_VIEW_BUNDLE_KEY, mapViewBundle);
                }
                mapView.onSaveInstanceState(mapViewBundle);
        }

        private void attemptLogout() {
                new androidx.appcompat.app.AlertDialog.Builder(this)
                                .setTitle("Logout Confirmation")
                                .setMessage("Are you sure you want to logout?")
                                .setPositiveButton("Yes", (dialog, which) -> {
                                        // Perform logout
                                        ApiResponse response = apiClient.logout();
                                        Toast.makeText(this, response.getMessage(), Toast.LENGTH_SHORT).show();

                                        // Redirect to login screen
                                        Intent intent = new Intent(this, LoginActivity.class);
                                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                                                        | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                        startActivity(intent);
                                        finish();
                                })
                                .setNegativeButton("No", (dialog, which) -> dialog.dismiss()) // Dismiss dialog if user
                                                                                              // cancels
                                .show();
        }

        private void createNotificationChannel() {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        CharSequence name = "EV Charging Notifications";
                        String description = "Notifications for EV charging system";
                        int importance = NotificationManager.IMPORTANCE_DEFAULT;

                        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
                        channel.setDescription(description);

                        NotificationManager notificationManager = getSystemService(NotificationManager.class);
                        if (notificationManager != null) {
                                notificationManager.createNotificationChannel(channel);
                        }
                }
        }

        private void updateNotificationCount() {
                if (notificationCount > 0) {
                        tvNotificationCount.setVisibility(View.VISIBLE);
                        tvNotificationCount.setText(String.valueOf(notificationCount));
                } else {
                        tvNotificationCount.setVisibility(View.GONE);
                }
        }

        private void moveCameraToLocation(Location location) {
                LatLng userLatLng = new LatLng(location.getLatitude(), location.getLongitude());
                googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(userLatLng, 15));
                googleMap.addMarker(new MarkerOptions().position(userLatLng).title("You are here"));

                // Fetch nearby stations in background
                new Thread(() -> {
                        List<Station> stations = stationService.getNearbyStations(location.getLatitude(),
                                        location.getLongitude(), 5);
                        runOnUiThread(() -> {
                                if (stations != null && !stations.isEmpty()) {
                                        LatLngBounds.Builder builder = new LatLngBounds.Builder();
                                        builder.include(userLatLng);

                                        for (Station s : stations) {
                                                LatLng stationLatLng = new LatLng(s.getLatitude(), s.getLongitude());
                                                googleMap.addMarker(new MarkerOptions()
                                                                .position(stationLatLng)
                                                                .title(s.getName())
                                                                .snippet(s.getLocation()));
                                                builder.include(stationLatLng);
                                        }

                                        LatLngBounds bounds = builder.build();
                                        int padding = 120;
                                        googleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, padding));
                                } else {
                                        Toast.makeText(this, "No nearby stations found", Toast.LENGTH_SHORT).show();
                                }
                        });
                }).start();
        }
}
