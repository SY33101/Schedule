package com.sherry.schedule.alarm;

import android.content.Context;
import android.content.SharedPreferences;

/** Prevents a queued duplicate delivery from restarting an alert just dismissed by the user. */
final class AlarmDismissals {
    private static final String PREFERENCES = "alarm_dismissals";
    private static final long DUPLICATE_BLOCK_MILLIS = 2 * 60 * 1000L;

    private AlarmDismissals() {
    }

    static void mark(Context context, long eventId, String phase) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .edit()
                .putLong(key(eventId, phase), System.currentTimeMillis())
                .apply();
    }

    static boolean wasRecentlyDismissed(Context context, long eventId, String phase) {
        SharedPreferences preferences = context.getSharedPreferences(
                PREFERENCES, Context.MODE_PRIVATE);
        long dismissedAt = preferences.getLong(key(eventId, phase), 0L);
        long elapsed = System.currentTimeMillis() - dismissedAt;
        return dismissedAt > 0L && elapsed >= 0L && elapsed < DUPLICATE_BLOCK_MILLIS;
    }

    private static String key(long eventId, String phase) {
        return eventId + ":" + (phase == null ? AlarmScheduler.PHASE_START : phase);
    }
}
