package com.sherry.schedule.alarm;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.sherry.schedule.MainActivity;
import com.sherry.schedule.data.ScheduleDatabase;
import com.sherry.schedule.data.ScheduleEvent;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;

public final class AlarmScheduler {
    public static final String EXTRA_EVENT_ID = "event_id";
    public static final String EXTRA_ALERT_PHASE = "alert_phase";
    public static final String PHASE_REMINDER = "reminder";
    public static final String PHASE_START = "start";
    private static final String ACTION_FIRE = "com.sherry.schedule.action.FIRE_ALARM";

    private AlarmScheduler() {
    }

    public static void schedule(Context context, ScheduleEvent event) {
        cancel(context, event.id);
        if (!event.enabled || event.completed) return;

        long now = System.currentTimeMillis();
        long startAt = nextTriggerMillis(event, now, PHASE_START);
        long reminderAt = nextTriggerMillis(event, now, PHASE_REMINDER);
        if (event.reminderMinutes > 0 && reminderAt > 0 && reminderAt != startAt) {
            scheduleOne(context, event, PHASE_REMINDER, reminderAt);
        }
        if (startAt > 0) scheduleOne(context, event, PHASE_START, startAt);
    }

    private static void scheduleOne(
            Context context, ScheduleEvent event, String phase, long triggerAt) {

        AlarmManager alarmManager = context.getSystemService(AlarmManager.class);
        PendingIntent operation = alarmPendingIntent(
                context, event.id, phase, PendingIntent.FLAG_UPDATE_CURRENT);

        Intent showIntent = new Intent(context, MainActivity.class)
                .putExtra(EXTRA_EVENT_ID, event.id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent showPendingIntent = PendingIntent.getActivity(
                context,
                requestCode(event.id, phase) + 4000,
                showIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        try {
            alarmManager.setAlarmClock(
                    new AlarmManager.AlarmClockInfo(triggerAt, showPendingIntent), operation);
        } catch (SecurityException denied) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, operation);
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, operation);
            }
        }
    }

    public static void cancel(Context context, long eventId) {
        AlarmManager alarmManager = context.getSystemService(AlarmManager.class);
        for (String phase : new String[]{PHASE_REMINDER, PHASE_START}) {
            PendingIntent existing = alarmPendingIntent(
                    context, eventId, phase, PendingIntent.FLAG_NO_CREATE);
            if (existing != null) {
                alarmManager.cancel(existing);
                existing.cancel();
            }
        }

        // Versions before 1.1.0 used one alarm with the base request code and
        // ACTION_FIRE. Cancel it during every reschedule so an app update cannot
        // leave both the legacy alarm and the new reminder/start alarms active.
        PendingIntent legacy = legacyAlarmPendingIntent(
                context, eventId, PendingIntent.FLAG_NO_CREATE);
        if (legacy != null) {
            alarmManager.cancel(legacy);
            legacy.cancel();
        }
        PendingIntent legacyShow = legacyShowPendingIntent(
                context, eventId, PendingIntent.FLAG_NO_CREATE);
        if (legacyShow != null) legacyShow.cancel();
    }

    public static void rescheduleAll(Context context) {
        List<ScheduleEvent> events = ScheduleDatabase.get(context).allActiveEvents();
        for (ScheduleEvent event : events) schedule(context, event);
    }

    public static long nextTriggerMillis(ScheduleEvent event, long nowMillis) {
        return nextTriggerMillis(event, nowMillis, PHASE_REMINDER);
    }

    public static long nextTriggerMillis(
            ScheduleEvent event, long nowMillis, String phase) {
        ZoneId zone = ZoneId.systemDefault();
        LocalDate nowDate = LocalDate.now(zone);
        int advance = PHASE_REMINDER.equals(phase) ? event.reminderMinutes : 0;
        if (event.anniversary) {
            LocalDate occurrence = anniversaryDate(event.date, nowDate.getYear());
            long trigger = triggerForDate(event, occurrence, zone, advance);
            if (trigger > nowMillis) return trigger;
            occurrence = anniversaryDate(event.date, nowDate.getYear() + 1);
            return triggerForDate(event, occurrence, zone, advance);
        }

        if (event.isHabit()) {
            LocalDate occurrence = habitOccurrenceOnOrAfter(event, nowDate);
            long trigger = triggerForDate(event, occurrence, zone, advance);
            if (trigger > nowMillis) return trigger;
            occurrence = occurrence.plusDays(
                    event.habitFrequency == ScheduleEvent.HabitFrequency.DAILY ? 1L : 7L);
            return triggerForDate(event, occurrence, zone, advance);
        }

        long eventTime = eventTimeForDate(event, event.date, zone);
        if (eventTime <= nowMillis) return -1L;
        long trigger = triggerForDate(event, event.date, zone, advance);
        if (trigger > nowMillis) return trigger;
        // A missed advance reminder must not be recreated on every activity
        // resume. The still-future start alarm is scheduled independently.
        return -1L;
    }

    private static long triggerForDate(
            ScheduleEvent event, LocalDate occurrence, ZoneId zone, int advanceMinutes) {
        return eventTimeForDate(event, occurrence, zone)
                - advanceMinutes * 60_000L;
    }

    private static long eventTimeForDate(ScheduleEvent event, LocalDate occurrence, ZoneId zone) {
        int hour = event.startMinutes / 60;
        int minute = event.startMinutes % 60;
        return occurrence.atTime(hour, minute)
                .atZone(zone)
                .toInstant()
                .toEpochMilli();
    }

    private static LocalDate anniversaryDate(LocalDate original, int year) {
        try {
            return LocalDate.of(year, original.getMonthValue(), original.getDayOfMonth());
        } catch (DateTimeException invalidLeapDay) {
            YearMonth month = YearMonth.of(year, original.getMonthValue());
            return LocalDate.of(year, original.getMonthValue(), month.lengthOfMonth());
        }
    }

    private static LocalDate habitOccurrenceOnOrAfter(
            ScheduleEvent event, LocalDate candidate) {
        if (candidate.isBefore(event.date)) return event.date;
        if (event.habitFrequency == ScheduleEvent.HabitFrequency.DAILY) return candidate;
        int daysUntil = Math.floorMod(
                event.date.getDayOfWeek().getValue() - candidate.getDayOfWeek().getValue(), 7);
        return candidate.plusDays(daysUntil);
    }

    private static PendingIntent alarmPendingIntent(
            Context context, long eventId, String phase, int flags) {
        Intent intent = new Intent(context, AlarmReceiver.class)
                .setAction(ACTION_FIRE + "." + phase)
                .putExtra(EXTRA_EVENT_ID, eventId)
                .putExtra(EXTRA_ALERT_PHASE, phase);
        return PendingIntent.getBroadcast(
                context,
                requestCode(eventId, phase),
                intent,
                flags | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent legacyAlarmPendingIntent(
            Context context, long eventId, int flags) {
        Intent intent = new Intent(context, AlarmReceiver.class)
                .setAction(ACTION_FIRE)
                .putExtra(EXTRA_EVENT_ID, eventId);
        return PendingIntent.getBroadcast(
                context,
                requestCode(eventId),
                intent,
                flags | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent legacyShowPendingIntent(
            Context context, long eventId, int flags) {
        Intent intent = new Intent(context, MainActivity.class)
                .putExtra(EXTRA_EVENT_ID, eventId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(
                context,
                requestCode(eventId) + 4000,
                intent,
                flags | PendingIntent.FLAG_IMMUTABLE);
    }

    static boolean isLegacyAlarmIntent(Intent intent) {
        return intent != null && ACTION_FIRE.equals(intent.getAction());
    }

    static int requestCode(long eventId) {
        return (int) (eventId ^ (eventId >>> 32));
    }

    static int requestCode(long eventId, String phase) {
        int base = requestCode(eventId);
        return base * 31 + (PHASE_START.equals(phase) ? 2 : 1);
    }
}
