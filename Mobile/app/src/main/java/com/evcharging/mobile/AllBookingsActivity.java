package com.evcharging.mobile;

import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.evcharging.mobile.model.User;
import com.evcharging.mobile.network.ApiClient;
import com.evcharging.mobile.network.ApiResponse;
import com.evcharging.mobile.session.SessionManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;

public class AllBookingsActivity extends AppCompatActivity {

    private SessionManager session;
    private ListView lvAllBookings;
    private SwipeRefreshLayout srAllBookings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_all_bookings);
        setTitle("All Upcoming Bookings");

        // ✅ Setup footer navigation
        FooterHelper.setupFooter(this);

        session = new SessionManager(this);
        lvAllBookings = findViewById(R.id.lvAllBookings);
        srAllBookings = findViewById(R.id.srAllBookings);

        ImageButton btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> finish());

        srAllBookings.setOnRefreshListener(this::loadUpcomingBookings);

        loadUpcomingBookings();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadUpcomingBookings();
    }

    private void loadUpcomingBookings() {
        User user = session.getLoggedInUser();
        if (user == null || user.getStationId() == null) {
            Toast.makeText(this, "No station assigned", Toast.LENGTH_SHORT).show();
            srAllBookings.setRefreshing(false);
            return;
        }

        srAllBookings.setRefreshing(true);

        new AsyncTask<Void, Void, ApiResponse>() {
            @Override
            protected ApiResponse doInBackground(Void... voids) {
                ApiClient apiClient = new ApiClient(session);
                return apiClient.get("/bookings/station/" + user.getStationId() + "/upcoming");
            }

            @Override
            protected void onPostExecute(ApiResponse response) {
                srAllBookings.setRefreshing(false);

                if (response == null || !response.isSuccess() || response.getData() == null) {
                    Toast.makeText(AllBookingsActivity.this, "No upcoming bookings found", Toast.LENGTH_SHORT).show();
                    return;
                }

                try {
                    JSONArray jsonArray = new JSONArray(response.getData());
                    ArrayList<JSONObject> bookings = new ArrayList<>();

                    for (int i = 0; i < jsonArray.length(); i++) {
                        bookings.add(jsonArray.getJSONObject(i));
                    }

                    BookingAdapter adapter = new BookingAdapter(AllBookingsActivity.this, bookings);
                    lvAllBookings.setAdapter(adapter);

                    lvAllBookings.setOnItemClickListener((parent, view, position, id) -> {
                        JSONObject obj = bookings.get(position);
                        Intent intent = new Intent(AllBookingsActivity.this, BookingDetailsActivity.class);
                        intent.putExtra("bookingId", obj.optString("bookingId"));
                        intent.putExtra("status", obj.optString("status"));
                        intent.putExtra("formattedStartTime", obj.optString("formattedStartTime", obj.optString("startTime")));
                        intent.putExtra("formattedEndTime", obj.optString("formattedEndTime", obj.optString("endTime")));
                        intent.putExtra("qrImageBase64", obj.optString("qrImageBase64"));
                        intent.putExtra("qrCode", obj.optString("qrCode"));
                        intent.putExtra("ownerName", obj.optString("ownerName"));
                        intent.putExtra("slotNumber", obj.optInt("slotNumber"));
                        startActivity(intent);
                    });

                } catch (Exception e) {
                    Log.e("ALL_BOOKINGS", "Parse error: " + e.getMessage());
                    Toast.makeText(AllBookingsActivity.this, "Error loading bookings", Toast.LENGTH_SHORT).show();
                }
            }
        }.execute();
    }
}
