package com.sherry.schedule.alarm;

import android.app.Activity;
import android.app.KeyguardManager;
import android.app.NotificationManager;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.sherry.schedule.data.ScheduleDatabase;
import com.sherry.schedule.data.ScheduleEvent;
import com.sherry.schedule.ui.UiUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class AlarmActivity extends Activity {
    private static final long MAX_ALERT_MILLIS = 60 * 1000L;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable autoDismiss = this::stopAlarm;
    private final Runnable clockUpdater = new Runnable() {
        @Override
        public void run() {
            updateClock();
            handler.postDelayed(this, 30_000L);
        }
    };
    private TextView clock;
    private long eventId = -1L;
    private String alertPhase = AlarmScheduler.PHASE_START;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                        | WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
            KeyguardManager keyguard = getSystemService(KeyguardManager.class);
            keyguard.requestDismissKeyguard(this, null);
        } else {
            getWindow().addFlags(
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                            | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                            | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
        }
        showEvent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        showEvent(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.removeCallbacks(clockUpdater);
        handler.post(clockUpdater);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(clockUpdater);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(clockUpdater);
        handler.removeCallbacks(autoDismiss);
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        stopAlarm();
    }

    private void showEvent(Intent intent) {
        handler.removeCallbacks(autoDismiss);
        eventId = intent.getLongExtra(AlarmScheduler.EXTRA_EVENT_ID, -1L);
        alertPhase = intent.getStringExtra(AlarmScheduler.EXTRA_ALERT_PHASE);
        if (alertPhase == null) alertPhase = AlarmScheduler.PHASE_START;
        ScheduleEvent event = ScheduleDatabase.get(this).getById(eventId);
        if (event == null) {
            finish();
            return;
        }
        buildContent(event);
        handler.postDelayed(autoDismiss, MAX_ALERT_MILLIS);
    }

    private void buildContent(ScheduleEvent event) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(28), dp(36), dp(28), dp(30));
        GradientDrawable background = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(65, 88, 220), Color.rgb(102, 91, 215)});
        root.setBackground(background);

        TextView label = text(
                AlarmScheduler.PHASE_REMINDER.equals(alertPhase)
                        ? event.reminderText() : "日程开始",
                16, 0xCCFFFFFF, true);
        label.setGravity(Gravity.CENTER);
        root.addView(label, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        clock = text("", 58, Color.WHITE, true);
        clock.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams clockParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clockParams.topMargin = dp(28);
        root.addView(clock, clockParams);
        updateClock();

        TextView date = text(event.dateText() + "  ·  " + event.timeText(),
                16, 0xDDFFFFFF, false);
        date.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams dateParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dateParams.topMargin = dp(4);
        root.addView(date, dateParams);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding(dp(22), dp(26), dp(22), dp(26));
        card.setBackground(UiUtils.rounded(0xF7FFFFFF, 24, this));
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardParams.topMargin = dp(38);
        root.addView(card, cardParams);

        TextView title = text(event.title, 25, UiUtils.TEXT, true);
        title.setGravity(Gravity.CENTER);
        card.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        if (!event.notes.isEmpty()) {
            TextView notes = text(event.notes, 16, UiUtils.TEXT_SECONDARY, false);
            notes.setGravity(Gravity.CENTER);
            notes.setLineSpacing(0, 1.15f);
            LinearLayout.LayoutParams notesParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            notesParams.topMargin = dp(14);
            card.addView(notes, notesParams);
        }

        LinearLayout.LayoutParams spacer = new LinearLayout.LayoutParams(1, 0, 1);
        root.addView(new android.view.View(this), spacer);

        Button dismiss = new Button(this);
        dismiss.setText("停止提醒");
        dismiss.setAllCaps(false);
        dismiss.setTextColor(UiUtils.PRIMARY_DARK);
        dismiss.setTextSize(17);
        dismiss.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        dismiss.setBackground(UiUtils.rounded(Color.WHITE, 18, this));
        dismiss.setOnClickListener(v -> stopAlarm());
        root.addView(dismiss, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(58)));

        setContentView(root);
    }

    private void updateClock() {
        if (clock == null) return;
        clock.setText(LocalDateTime.now().format(
                DateTimeFormatter.ofPattern("HH:mm", Locale.CHINA)));
    }

    private void stopAlarm() {
        if (eventId >= 0) {
            AlarmDismissals.mark(this, eventId, alertPhase);
        }
        stopService(new Intent(this, AlarmSoundService.class));
        getSystemService(NotificationManager.class)
                .cancel(AlarmNotifications.notificationId(eventId));
        finishAndRemoveTask();
    }

    private TextView text(String value, float size, int color, boolean bold) {
        TextView result = new TextView(this);
        result.setText(value);
        result.setTextSize(size);
        result.setTextColor(color);
        if (bold) result.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return result;
    }

    private int dp(int value) {
        return UiUtils.dp(this, value);
    }
}
