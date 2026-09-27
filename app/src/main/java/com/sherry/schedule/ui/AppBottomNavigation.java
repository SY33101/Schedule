package com.sherry.schedule.ui;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.sherry.schedule.MainActivity;
import com.sherry.schedule.MemoActivity;

public final class AppBottomNavigation {
    private AppBottomNavigation() {
    }

    public static LinearLayout create(Activity activity, boolean memoSelected) {
        LinearLayout bar = new LinearLayout(activity);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setPadding(0, UiUtils.dp(activity, 7), 0, 0);
        bar.setBackgroundColor(UiUtils.BACKGROUND);

        TextView schedule = tab(activity, "日程", !memoSelected);
        TextView memo = tab(activity, "备忘", memoSelected);
        bar.addView(schedule, new LinearLayout.LayoutParams(0,
                UiUtils.dp(activity, 44), 1));
        bar.addView(memo, new LinearLayout.LayoutParams(0,
                UiUtils.dp(activity, 44), 1));

        schedule.setOnClickListener(v -> {
            if (!memoSelected) return;
            Intent intent = new Intent(activity, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            activity.startActivity(intent);
            activity.finish();
        });
        memo.setOnClickListener(v -> {
            if (memoSelected) return;
            activity.startActivity(new Intent(activity, MemoActivity.class));
        });
        return bar;
    }

    private static TextView tab(Activity activity, String value, boolean selected) {
        TextView tab = new TextView(activity);
        tab.setText(value);
        tab.setTextSize(14);
        tab.setTextColor(selected ? UiUtils.PRIMARY_DARK : UiUtils.TEXT_SECONDARY);
        tab.setGravity(Gravity.CENTER);
        if (selected) tab.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        tab.setBackground(selected
                ? UiUtils.rounded(0xFFE8ECFF, 14, activity)
                : UiUtils.rounded(Color.TRANSPARENT, 14, activity));
        tab.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, UiUtils.dp(activity, 44)));
        return tab;
    }
}
