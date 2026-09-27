package com.sherry.schedule.alarm;

import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;

import com.sherry.schedule.data.ScheduleDatabase;
import com.sherry.schedule.data.ScheduleEvent;

public final class AlarmSoundService extends Service {
    public static final String ACTION_START = "com.sherry.schedule.action.START_ALERT";
    public static final String ACTION_STOP = "com.sherry.schedule.action.STOP_ALERT";
    private static final long MAX_ALERT_MILLIS = 60 * 1000L;

    private MediaPlayer mediaPlayer;
    private Vibrator vibrator;
    private PowerManager.WakeLock wakeLock;
    private long currentEventId = -1L;
    private String currentPhase = AlarmScheduler.PHASE_START;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable timeout = this::stopAlert;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            stopAlert();
            return START_NOT_STICKY;
        }

        if (ACTION_STOP.equals(intent.getAction())) {
            long stoppedEventId = intent.getLongExtra(
                    AlarmScheduler.EXTRA_EVENT_ID, currentEventId);
            String stoppedPhase = intent.getStringExtra(AlarmScheduler.EXTRA_ALERT_PHASE);
            if (stoppedPhase == null) stoppedPhase = currentPhase;
            if (stoppedEventId >= 0) {
                AlarmDismissals.mark(this, stoppedEventId, stoppedPhase);
            }
            stopAlert();
            return START_NOT_STICKY;
        }

        long eventId = intent.getLongExtra(AlarmScheduler.EXTRA_EVENT_ID, -1L);
        String phase = intent.getStringExtra(AlarmScheduler.EXTRA_ALERT_PHASE);
        if (phase == null) phase = AlarmScheduler.PHASE_START;
        if (AlarmDismissals.wasRecentlyDismissed(this, eventId, phase)) {
            stopSelf(startId);
            return START_NOT_STICKY;
        }
        ScheduleEvent event = ScheduleDatabase.get(this).getById(eventId);
        if (event == null) {
            stopSelf();
            return START_NOT_STICKY;
        }

        stopPlaybackOnly();
        currentEventId = event.id;
        currentPhase = phase;
        startForeground(AlarmNotifications.notificationId(event.id),
                AlarmNotifications.build(this, event, phase));
        acquireWakeLock();
        startPlayback(event);
        handler.removeCallbacks(timeout);
        handler.postDelayed(timeout, MAX_ALERT_MILLIS);
        return START_NOT_STICKY;
    }

    private void startPlayback(ScheduleEvent event) {
        if (event.alertMode == ScheduleEvent.AlertMode.SOUND) {
            Uri sound = null;
            if (event.ringtoneUri != null && !event.ringtoneUri.isEmpty()) {
                try {
                    sound = Uri.parse(event.ringtoneUri);
                } catch (Exception ignored) {
                }
            }
            if (sound == null) sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            if (sound == null) sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            try {
                mediaPlayer = new MediaPlayer();
                mediaPlayer.setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build());
                mediaPlayer.setDataSource(this, sound);
                mediaPlayer.setLooping(true);
                mediaPlayer.prepare();
                mediaPlayer.start();
            } catch (Exception error) {
                if (mediaPlayer != null) {
                    mediaPlayer.release();
                    mediaPlayer = null;
                }
            }
        }

        if (event.alertMode != ScheduleEvent.AlertMode.SILENT) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                VibratorManager manager = getSystemService(VibratorManager.class);
                vibrator = manager.getDefaultVibrator();
            } else {
                vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            }
            if (vibrator != null && vibrator.hasVibrator()) {
                long[] timings = {0, 650, 250, 650, 600};
                int[] amplitudes = {0, 255, 0, 255, 0};
                VibrationEffect effect = VibrationEffect.createWaveform(timings, amplitudes, 0);
                AudioAttributes vibrationAudio = new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build();
                vibrator.vibrate(effect, vibrationAudio);
            }
        }
    }

    private void acquireWakeLock() {
        PowerManager powerManager = getSystemService(PowerManager.class);
        wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK, "Schedule:AlarmWakeLock");
        wakeLock.acquire(MAX_ALERT_MILLIS + 5_000L);
    }

    private void stopPlaybackOnly() {
        handler.removeCallbacks(timeout);
        if (mediaPlayer != null) {
            try {
                mediaPlayer.stop();
            } catch (IllegalStateException ignored) {
            }
            mediaPlayer.release();
            mediaPlayer = null;
        }
        if (vibrator != null) {
            vibrator.cancel();
            vibrator = null;
        }
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        wakeLock = null;
    }

    private void stopAlert() {
        if (currentEventId >= 0) {
            AlarmDismissals.mark(this, currentEventId, currentPhase);
        }
        stopPlaybackOnly();
        if (currentEventId >= 0) {
            getSystemService(NotificationManager.class)
                    .cancel(AlarmNotifications.notificationId(currentEventId));
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE);
        } else {
            stopForeground(true);
        }
        stopSelf();
    }

    @Override
    public void onDestroy() {
        stopPlaybackOnly();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
