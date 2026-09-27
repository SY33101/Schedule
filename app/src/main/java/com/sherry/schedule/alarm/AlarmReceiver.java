package com.sherry.schedule.alarm;

import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.PowerManager;

import com.sherry.schedule.data.ScheduleDatabase;
import com.sherry.schedule.data.ScheduleEvent;

public final class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        long eventId = intent.getLongExtra(AlarmScheduler.EXTRA_EVENT_ID, -1L);
        ScheduleEvent event = ScheduleDatabase.get(context).getById(eventId);
        if (event == null || !event.enabled || event.completed) return;

        // A pre-1.1.0 alarm has no phase and must never be allowed to create a
        // second alert after the user has already dismissed the new reminder.
        if (AlarmScheduler.isLegacyAlarmIntent(intent)) {
            return;
        }

        String phase = intent.getStringExtra(AlarmScheduler.EXTRA_ALERT_PHASE);
        if (phase == null) phase = AlarmScheduler.PHASE_START;
        if (AlarmDismissals.wasRecentlyDismissed(context, eventId, phase)) return;

        boolean lockedOrScreenOff = isLockedOrScreenOff(context);
        if (lockedOrScreenOff) wakeScreen(context);

        Intent serviceIntent = new Intent(context, AlarmSoundService.class)
                .setAction(AlarmSoundService.ACTION_START)
                .putExtra(AlarmScheduler.EXTRA_EVENT_ID, eventId)
                .putExtra(AlarmScheduler.EXTRA_ALERT_PHASE, phase);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent);
        } else {
            context.startService(serviceIntent);
        }

        if (lockedOrScreenOff) {
            AlarmNotifications.launchFullScreen(context, event, phase);
        }

        if (event.isRecurring() && AlarmScheduler.PHASE_START.equals(phase)) {
            AlarmScheduler.schedule(context, event);
        }
    }

    private static boolean isLockedOrScreenOff(Context context) {
        PowerManager powerManager = context.getSystemService(PowerManager.class);
        KeyguardManager keyguardManager = context.getSystemService(KeyguardManager.class);
        return !powerManager.isInteractive() || keyguardManager.isKeyguardLocked();
    }

    @SuppressWarnings("deprecation")
    private static void wakeScreen(Context context) {
        PowerManager powerManager = context.getSystemService(PowerManager.class);
        PowerManager.WakeLock wakeLock = powerManager.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK
                        | PowerManager.ACQUIRE_CAUSES_WAKEUP
                        | PowerManager.ON_AFTER_RELEASE,
                "Schedule:ScreenWakeLock");
        wakeLock.acquire(10_000L);
    }
}
