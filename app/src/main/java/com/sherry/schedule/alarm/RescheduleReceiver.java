package com.sherry.schedule.alarm;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class RescheduleReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        PendingResult result = goAsync();
        Context appContext = context.getApplicationContext();
        new Thread(() -> {
            try {
                AlarmScheduler.rescheduleAll(appContext);
            } finally {
                result.finish();
            }
        }, "schedule-rescheduler").start();
    }
}

