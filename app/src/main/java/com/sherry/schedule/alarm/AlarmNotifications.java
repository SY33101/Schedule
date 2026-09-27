package com.sherry.schedule.alarm;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.media.AudioAttributes;
import android.os.Build;
import android.os.Bundle;

import com.sherry.schedule.R;
import com.sherry.schedule.data.ScheduleEvent;

public final class AlarmNotifications {
    // A new channel is intentional: Android keeps the old channel's lock-screen
    // and pop-up state across upgrades, even when the app requests HIGH again.
    static final String CHANNEL_ID = "schedule_alarm_v2";

    private AlarmNotifications() {
    }

    public static void ensureChannel(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        NotificationChannel channel = manager.getNotificationChannel(CHANNEL_ID);
        if (channel != null) return;

        channel = new NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription(context.getString(R.string.notification_channel_description));
        channel.setSound(null, new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .build());
        channel.enableVibration(false);
        channel.enableLights(true);
        channel.setLightColor(Color.rgb(83, 109, 254));
        channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        manager.createNotificationChannel(channel);
    }

    static Notification build(Context context, ScheduleEvent event, String phase) {
        ensureChannel(context);
        PendingIntent fullScreenPending = fullScreenPendingIntent(context, event, phase);

        Intent stop = new Intent(context, AlarmSoundService.class)
                .setAction(AlarmSoundService.ACTION_STOP)
                .putExtra(AlarmScheduler.EXTRA_EVENT_ID, event.id)
                .putExtra(AlarmScheduler.EXTRA_ALERT_PHASE, phase);
        PendingIntent stopPending = PendingIntent.getService(
                context,
                AlarmScheduler.requestCode(event.id, phase) + 9000,
                stop,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(context, CHANNEL_ID)
                : new Notification.Builder(context);
        String phaseTitle = AlarmScheduler.PHASE_REMINDER.equals(phase)
                ? event.reminderText() : "日程开始";
        builder.setSmallIcon(R.drawable.ic_stat_schedule)
                .setContentTitle(phaseTitle + " · " + event.timeText())
                .setContentText(event.title)
                .setStyle(new Notification.BigTextStyle().bigText(
                        event.notes.isEmpty() ? event.title : event.title + "\n" + event.notes))
                .setCategory(Notification.CATEGORY_ALARM)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setPriority(Notification.PRIORITY_MAX)
                .setColor(Color.rgb(83, 109, 254))
                .setOngoing(true)
                .setAutoCancel(false)
                .setContentIntent(fullScreenPending)
                .setFullScreenIntent(fullScreenPending, true)
                .setTimeoutAfter(60 * 1000L)
                .addAction(new Notification.Action.Builder(
                        null, context.getString(R.string.alarm_stop), stopPending).build());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE);
        }
        return builder.build();
    }

    static void launchFullScreen(Context context, ScheduleEvent event, String phase) {
        PendingIntent pendingIntent = fullScreenPendingIntent(context, event, phase);
        try {
            if (Build.VERSION.SDK_INT >= 35) {
                ActivityOptions options = ActivityOptions.makeBasic();
                options.setPendingIntentBackgroundActivityStartMode(
                        backgroundActivityStartMode());
                pendingIntent.send(context, 0, null, null, null, null, options.toBundle());
            } else {
                pendingIntent.send();
            }
        } catch (PendingIntent.CanceledException ignored) {
        }
    }

    private static PendingIntent fullScreenPendingIntent(
            Context context, ScheduleEvent event, String phase) {
        Intent fullScreen = new Intent(context, AlarmActivity.class)
                .putExtra(AlarmScheduler.EXTRA_EVENT_ID, event.id)
                .putExtra(AlarmScheduler.EXTRA_ALERT_PHASE, phase)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        if (Build.VERSION.SDK_INT >= 35) {
            ActivityOptions options = ActivityOptions.makeBasic();
            options.setPendingIntentCreatorBackgroundActivityStartMode(
                    backgroundActivityStartMode());
            Bundle bundle = options.toBundle();
            return PendingIntent.getActivity(
                    context,
                    AlarmScheduler.requestCode(event.id, phase) + 8000,
                    fullScreen,
                    flags,
                    bundle);
        }
        return PendingIntent.getActivity(
                context,
                AlarmScheduler.requestCode(event.id, phase) + 8000,
                fullScreen,
                flags);
    }

    private static int backgroundActivityStartMode() {
        if (Build.VERSION.SDK_INT >= 36) {
            return ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS;
        }
        return ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED;
    }

    static int notificationId(long eventId) {
        return 20_000 + Math.floorMod(AlarmScheduler.requestCode(eventId), 10_000);
    }
}
