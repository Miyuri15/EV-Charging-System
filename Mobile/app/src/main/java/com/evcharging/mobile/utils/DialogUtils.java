package com.evcharging.mobile.utils;

import android.content.Context;
import android.widget.TextView;
import android.widget.Toast;
import android.view.View;
import android.view.Gravity;

import com.evcharging.mobile.R;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class DialogUtils {

    // 🔹 Show any custom Material dialog
    public static void showDialog(Context context, String title, String message, String positive, Runnable onPositive) {
        new MaterialAlertDialogBuilder(context, R.style.CustomAlertDialog)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(positive, (dialog, which) -> {
                    if (onPositive != null) onPositive.run();
                    dialog.dismiss();
                })
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                .show();
    }

    // 🔹 Show toast (auto-styled)
    public static void showToast(Context context, String message) {
        showToast(context, message, R.color.chip_text_selected);
    }

    public static void showToast(Context context, String message, int colorRes) {
        Toast toast = Toast.makeText(context, message, Toast.LENGTH_SHORT);
        View view = toast.getView();

        if (view != null) {
            view.setBackgroundResource(R.drawable.bg_toast_custom);
            view.getBackground().setTint(context.getResources().getColor(colorRes));

            TextView text = view.findViewById(android.R.id.message);
            if (text != null) {
                text.setTextColor(context.getResources().getColor(R.color.white));
                text.setTextSize(15);
                text.setPadding(32, 16, 32, 16);
                text.setGravity(Gravity.CENTER);
            }
        }

        toast.setGravity(Gravity.BOTTOM, 0, 120);
        toast.show();
    }
}
