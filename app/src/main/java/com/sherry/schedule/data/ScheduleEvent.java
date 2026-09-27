package com.sherry.schedule.data;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class ScheduleEvent {
    public enum AlertMode {
        SOUND,
        VIBRATE,
        SILENT
    }

    public enum HabitFrequency {
        NONE,
        DAILY,
        WEEKLY
    }

    public long id;
    public String title = "";
    public String notes = "";
    public LocalDate date = LocalDate.now();
    public int startMinutes = 9 * 60;
    public int endMinutes = -1;
    public int reminderMinutes = 10;
    public AlertMode alertMode = AlertMode.SOUND;
    public String ringtoneUri = "";
    public boolean anniversary;
    public HabitFrequency habitFrequency = HabitFrequency.NONE;
    public boolean enabled = true;
    public boolean completed;

    public String timeText() {
        String start = formatMinutes(startMinutes);
        if (endMinutes >= 0) {
            return start + " – " + formatMinutes(endMinutes);
        }
        return start;
    }

    public String dateText() {
        if (anniversary) {
            return date.format(DateTimeFormatter.ofPattern("每年 M月d日", Locale.CHINA));
        }
        if (habitFrequency == HabitFrequency.DAILY) {
            return date.format(DateTimeFormatter.ofPattern(
                    "每日 · 从yyyy年M月d日开始", Locale.CHINA));
        }
        if (habitFrequency == HabitFrequency.WEEKLY) {
            return "每" + date.format(DateTimeFormatter.ofPattern("EEEE", Locale.CHINA))
                    + date.format(DateTimeFormatter.ofPattern(
                    " · 从yyyy年M月d日开始", Locale.CHINA));
        }
        return date.format(DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.CHINA));
    }

    public boolean isHabit() {
        return habitFrequency != HabitFrequency.NONE;
    }

    public boolean isRecurring() {
        return anniversary || isHabit();
    }

    public String reminderText() {
        if (reminderMinutes == 0) return "准时提醒";
        if (reminderMinutes == 24 * 60) return "提前1天提醒";
        if (reminderMinutes >= 60 && reminderMinutes % 60 == 0) {
            return "提前" + (reminderMinutes / 60) + "小时提醒";
        }
        return "提前" + reminderMinutes + "分钟提醒";
    }

    public String alertModeText() {
        switch (alertMode) {
            case VIBRATE:
                return "仅震动";
            case SILENT:
                return "静默";
            default:
                return "响铃和震动";
        }
    }

    public static String formatMinutes(int minutes) {
        int normalized = Math.max(0, Math.min(23 * 60 + 59, minutes));
        return String.format(Locale.CHINA, "%02d:%02d", normalized / 60, normalized % 60);
    }
}
