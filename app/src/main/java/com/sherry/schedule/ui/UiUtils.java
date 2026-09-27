package com.sherry.schedule.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.TextView;

public final class UiUtils {
    public static final int PRIMARY = Color.rgb(83, 109, 254);
    public static final int PRIMARY_DARK = Color.rgb(61, 82, 213);
    public static final int BACKGROUND = Color.rgb(247, 248, 252);
    public static final int SURFACE = Color.WHITE;
    public static final int TEXT = Color.rgb(24, 32, 52);
    public static final int TEXT_SECONDARY = Color.rgb(108, 116, 133);
    public static final int BORDER = Color.rgb(229, 232, 241);
    public static final int DANGER = Color.rgb(221, 76, 76);
    public static final int ANNIVERSARY = Color.rgb(239, 117, 94);

    private UiUtils() {
    }

    public static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    public static GradientDrawable rounded(int color, float radiusDp, Context context) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(context, radiusDp));
        return drawable;
    }

    public static GradientDrawable outlined(
            int fillColor, int strokeColor, float radiusDp, Context context) {
        GradientDrawable drawable = rounded(fillColor, radiusDp, context);
        drawable.setStroke(dp(context, 1), strokeColor);
        return drawable;
    }

    public static TextView text(
            Context context, String value, float sizeSp, int color, boolean bold) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(sizeSp);
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setFontFeatureSettings("tnum");
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    public static void setPadding(View view, Context context, int horizontalDp, int verticalDp) {
        int horizontal = dp(context, horizontalDp);
        int vertical = dp(context, verticalDp);
        view.setPadding(horizontal, vertical, horizontal, vertical);
    }
}

