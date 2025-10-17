package com.evcharging.mobile;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;
import org.json.JSONObject;
import java.util.ArrayList;

public class TodayReservationAdapter extends BaseAdapter {

    private final Context context;
    private final ArrayList<JSONObject> reservations;

    public TodayReservationAdapter(Context context, ArrayList<JSONObject> reservations) {
        this.context = context;
        this.reservations = reservations;
    }

    @Override
    public int getCount() {
        return reservations.size();
    }

    @Override
    public Object getItem(int position) {
        return reservations.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = LayoutInflater.from(context)
                    .inflate(R.layout.today_reservation_item, parent, false);
        }

        TextView tvBookingId = convertView.findViewById(R.id.tvBookingId);
        TextView tvStatus = convertView.findViewById(R.id.tvStatus);
        TextView tvStart = convertView.findViewById(R.id.tvStartTime);
        TextView tvEnd = convertView.findViewById(R.id.tvEndTime);
        TextView tvOwnerName = convertView.findViewById(R.id.tvOwnerName);
        TextView tvSlotNumber = convertView.findViewById(R.id.tvSlotNumber);
        View header = convertView.findViewById(R.id.headerContainer);

        JSONObject obj = reservations.get(position);

        String id = obj.optString("bookingId", "N/A");
        String status = obj.optString("status", "N/A");
        int slotNumber = obj.optInt("slotNumber", 0);
        String start = obj.optString("formattedStartTime", obj.optString("startTime", ""));
        String end = obj.optString("formattedEndTime", obj.optString("endTime", ""));
        String ownerName = obj.optString("ownerName", "Unknown");

        tvBookingId.setText("Booking ID: " + id);
        tvStatus.setText(status);
        tvStart.setText("Start: " + start);
        tvEnd.setText("End: " + end);
        tvOwnerName.setText("Owner: " + ownerName);
        tvSlotNumber.setText("Slot: " + (slotNumber > 0 ? "Slot " + slotNumber : "N/A"));

        if (status.equalsIgnoreCase("Approved"))
            header.setBackgroundResource(R.drawable.bg_gradient_green);
        else if (status.equalsIgnoreCase("Pending"))
            header.setBackgroundResource(R.drawable.bg_gradient_orange);
        else if (status.equalsIgnoreCase("Charging"))
            header.setBackgroundResource(R.drawable.bg_gradient_blue);
        else if (status.equalsIgnoreCase("Completed"))
            header.setBackgroundResource(R.drawable.bg_gradient_grey);
        else
            header.setBackgroundResource(R.drawable.bg_gradient_grey);

        return convertView;
    }
}