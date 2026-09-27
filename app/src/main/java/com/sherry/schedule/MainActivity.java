package com.sherry.schedule;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.view.VelocityTracker;
import android.view.ViewConfiguration;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.sherry.schedule.alarm.AlarmNotifications;
import com.sherry.schedule.alarm.AlarmScheduler;
import com.sherry.schedule.data.ScheduleDatabase;
import com.sherry.schedule.data.ScheduleEvent;
import com.sherry.schedule.ui.AppBottomNavigation;
import com.sherry.schedule.ui.EventEditorDialog;
import com.sherry.schedule.ui.UiUtils;
import com.sherry.schedule.ui.WeekTimelineView;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class MainActivity extends Activity {
    private static final int NOTIFICATION_PERMISSION_REQUEST = 4201;
    private static final int MODE_CALENDAR = 0;
    private static final int MODE_DAY = 1;
    private static final int MODE_WEEK = 2;

    private ScheduleDatabase database;
    private LocalDate selectedDate = LocalDate.now();
    private YearMonth displayedMonth = YearMonth.now();
    private int displayMode = MODE_CALENDAR;

    private LinearLayout calendarCard;
    private TextView monthTitle;
    private TextView selectedDateTitle;
    private TextView agendaCount;
    private LinearLayout selectionBar;
    private TextView selectionCount;
    private GridLayout calendarGrid;
    private FrameLayout agendaHost;
    private LinearLayout permissionBanner;
    private TextView permissionText;
    private EventEditorDialog activeEditor;
    private GestureDetector monthTitleDetector;
    private ScaleGestureDetector agendaScaleDetector;
    private int touchSlop;
    private float monthDownX;
    private float monthDownY;
    private boolean monthDragging;
    private boolean monthTransitionRunning;
    private VelocityTracker monthVelocityTracker;
    private float agendaDownX;
    private float agendaDownY;
    private boolean agendaHorizontalDragging;
    private boolean agendaVerticalDragging;
    private boolean agendaStartedAtTop;
    private boolean agendaTransitionRunning;
    private boolean ignoreAgendaRelease;
    private VelocityTracker agendaVelocityTracker;
    private int calendarExpandedHeight;
    private int calendarExpandedBottomMargin;
    private final Set<Long> selectedEventIds = new HashSet<>();
    private boolean eventSelectionMode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String previousCrash = CrashReporter.consume(this);
        if (previousCrash != null) {
            showDiagnosticScreen(previousCrash);
            return;
        }
        try {
            configureWindow();
            database = ScheduleDatabase.get(this);
            AlarmNotifications.ensureChannel(this);
            selectEventFromIntent(getIntent());
            initGestures();
            buildContent();
            refreshAll();
            requestNotificationPermissionIfNeeded();
        } catch (Throwable startupFailure) {
            CrashReporter.record(this, startupFailure, "MainActivity.onCreate");
            String report = CrashReporter.consume(this);
            showDiagnosticScreen(report == null ? startupFailure.toString() : report);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        selectEventFromIntent(intent);
        refreshAll();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (permissionBanner != null) refreshPermissionBanner();
        if (database == null) return;
        refreshAll();
        new Thread(() -> AlarmScheduler.rescheduleAll(getApplicationContext()),
                "schedule-resume-check").start();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (activeEditor != null
                && activeEditor.handleRingtoneResult(requestCode, resultCode, data)) return;
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onBackPressed() {
        if (displayMode == MODE_WEEK) {
            animateAgendaModeChange(MODE_DAY);
            return;
        }
        if (displayMode == MODE_DAY) {
            animateCalendarVisibility(true);
            return;
        }
        super.onBackPressed();
    }

    private void configureWindow() {
        Window window = getWindow();
        window.setStatusBarColor(UiUtils.BACKGROUND);
        window.setNavigationBarColor(UiUtils.SURFACE);
    }

    private void initGestures() {
        touchSlop = ViewConfiguration.get(this).getScaledTouchSlop();

        monthTitleDetector = new GestureDetector(this,
                new GestureDetector.SimpleOnGestureListener() {
                    @Override public boolean onDown(MotionEvent event) { return true; }
                    @Override public boolean onSingleTapConfirmed(MotionEvent event) {
                        showMonthYearPicker();
                        return true;
                    }
                    @Override public boolean onDoubleTap(MotionEvent event) {
                        goToday();
                        return true;
                    }
                });

        agendaScaleDetector = new ScaleGestureDetector(this,
                new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    private float accumulated = 1f;
                    @Override public boolean onScaleBegin(ScaleGestureDetector detector) {
                        accumulated = 1f;
                        ignoreAgendaRelease = true;
                        resetAgendaDragPreview(false);
                        return (displayMode == MODE_DAY || displayMode == MODE_WEEK)
                                && !agendaTransitionRunning;
                    }
                    @Override public boolean onScale(ScaleGestureDetector detector) {
                        accumulated *= detector.getScaleFactor();
                        if (displayMode == MODE_DAY) {
                            float preview = Math.max(0.88f, Math.min(1f, accumulated));
                            agendaHost.setScaleX(preview);
                            agendaHost.setScaleY(preview);
                            agendaHost.setAlpha(0.55f + preview * 0.45f);
                        } else if (displayMode == MODE_WEEK) {
                            float preview = Math.min(1.12f, Math.max(1f, accumulated));
                            agendaHost.setScaleX(preview);
                            agendaHost.setScaleY(preview);
                            agendaHost.setAlpha(Math.max(0.72f,
                                    1f - (preview - 1f) * 1.8f));
                        }
                        return true;
                    }
                    @Override public void onScaleEnd(ScaleGestureDetector detector) {
                        if (displayMode == MODE_DAY && accumulated < 0.82f) {
                            animateAgendaModeChange(MODE_WEEK);
                        } else if (displayMode == MODE_WEEK && accumulated > 1.18f) {
                            animateAgendaModeChange(MODE_DAY);
                        } else {
                            animateAgendaTransformHome();
                        }
                    }
                });
    }

    private void buildContent() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(UiUtils.BACKGROUND);
        root.setPadding(dp(18), dp(10), dp(18), dp(10));
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            int top;
            int bottom;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                top = bars.top;
                bottom = bars.bottom;
            } else {
                top = insets.getSystemWindowInsetTop();
                bottom = insets.getSystemWindowInsetBottom();
            }
            view.setPadding(dp(18), top + dp(12), dp(18), bottom + dp(10));
            return insets;
        });

        LinearLayout header = row();
        LinearLayout heading = new LinearLayout(this);
        heading.setOrientation(LinearLayout.VERTICAL);
        heading.addView(text("Schedule", 27, UiUtils.TEXT, true));
        heading.addView(text(LocalDate.now().format(
                DateTimeFormatter.ofPattern("今天 · M月d日 EEEE", Locale.CHINA)),
                13, UiUtils.TEXT_SECONDARY, false));
        header.addView(heading, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView menu = text("⋮", 29, UiUtils.TEXT, true);
        menu.setGravity(Gravity.CENTER);
        menu.setContentDescription("更多");
        menu.setOnClickListener(this::showMoreMenu);
        header.addView(menu, new LinearLayout.LayoutParams(dp(42), dp(45)));
        LinearLayout.LayoutParams headerParams = matchWrap();
        headerParams.bottomMargin = dp(10);
        root.addView(header, headerParams);

        permissionBanner = row();
        permissionBanner.setPadding(dp(12), dp(8), dp(7), dp(8));
        permissionBanner.setBackground(UiUtils.rounded(0xFFFFF3D8, 13, this));
        permissionText = text("", 13, Color.rgb(122, 84, 13), false);
        permissionBanner.addView(permissionText,
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView permissionAction = text("设置", 13, UiUtils.PRIMARY_DARK, true);
        permissionAction.setGravity(Gravity.CENTER);
        permissionAction.setPadding(dp(10), dp(4), dp(10), dp(4));
        permissionAction.setOnClickListener(v -> openReminderSettings());
        permissionBanner.addView(permissionAction);
        LinearLayout.LayoutParams bannerParams = matchWrap();
        bannerParams.bottomMargin = dp(9);
        root.addView(permissionBanner, bannerParams);

        calendarCard = new LinearLayout(this);
        calendarCard.setOrientation(LinearLayout.VERTICAL);
        calendarCard.setPadding(dp(12), dp(8), dp(12), dp(9));
        calendarCard.setBackground(UiUtils.rounded(UiUtils.SURFACE, 20, this));

        monthTitle = text("", 19, UiUtils.TEXT, true);
        monthTitle.setGravity(Gravity.CENTER);
        monthTitle.setContentDescription("单击选择年月，双击回到今天");
        monthTitle.setOnTouchListener((view, event) -> monthTitleDetector.onTouchEvent(event));
        calendarCard.addView(monthTitle, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(42)));

        GridLayout weekdayGrid = new GridLayout(this);
        weekdayGrid.setColumnCount(7);
        String[] weekdays = {"一", "二", "三", "四", "五", "六", "日"};
        for (int i = 0; i < weekdays.length; i++) {
            TextView day = text(weekdays[i], 12,
                    i == 6 ? UiUtils.ANNIVERSARY : UiUtils.TEXT_SECONDARY, false);
            day.setGravity(Gravity.CENTER);
            GridLayout.LayoutParams params = new GridLayout.LayoutParams(
                    GridLayout.spec(0), GridLayout.spec(i, 1, 1f));
            params.width = 0;
            params.height = dp(27);
            weekdayGrid.addView(day, params);
        }
        calendarCard.addView(weekdayGrid, matchWrap());

        calendarGrid = new GridLayout(this);
        calendarGrid.setColumnCount(7);
        calendarGrid.setRowCount(6);
        calendarCard.addView(calendarGrid, matchWrap());
        LinearLayout.LayoutParams calendarParams = matchWrap();
        calendarParams.bottomMargin = dp(12);
        root.addView(calendarCard, calendarParams);

        LinearLayout agendaHeader = row();
        selectedDateTitle = text("", 18, UiUtils.TEXT, true);
        agendaHeader.addView(selectedDateTitle,
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        agendaCount = text("", 13, UiUtils.TEXT_SECONDARY, false);
        agendaCount.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        agendaHeader.addView(agendaCount);
        LinearLayout.LayoutParams agendaHeaderParams = matchWrap();
        agendaHeaderParams.bottomMargin = dp(7);
        root.addView(agendaHeader, agendaHeaderParams);

        selectionBar = row();
        selectionBar.setPadding(dp(12), dp(6), dp(6), dp(6));
        selectionBar.setBackground(UiUtils.rounded(0xFFE8ECFF, 14, this));
        selectionCount = text("", 14, UiUtils.PRIMARY_DARK, true);
        selectionBar.addView(selectionCount, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        selectionBar.addView(selectionAction("完成", v -> completeSelectedEvents()));
        selectionBar.addView(selectionAction("删除", v -> deleteSelectedEvents()));
        selectionBar.addView(selectionAction("取消", v -> exitEventSelection()));
        selectionBar.setVisibility(View.GONE);
        LinearLayout.LayoutParams selectionParams = matchWrap();
        selectionParams.bottomMargin = dp(7);
        root.addView(selectionBar, selectionParams);

        agendaHost = new FrameLayout(this);
        root.addView(agendaHost, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        Button add = new Button(this);
        add.setText("＋  添加日程");
        add.setTextSize(16);
        add.setTextColor(Color.WHITE);
        add.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        add.setAllCaps(false);
        add.setBackground(UiUtils.rounded(UiUtils.PRIMARY, 17, this));
        add.setOnClickListener(v -> showEditor(null));
        LinearLayout.LayoutParams addParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(54));
        addParams.topMargin = dp(8);
        root.addView(add, addParams);
        root.addView(AppBottomNavigation.create(this, false), matchWrap());

        setContentView(root);
        applySystemBarAppearance();
    }

    private void refreshAll() {
        if (calendarGrid == null) return;
        calendarCard.setVisibility(displayMode == MODE_CALENDAR ? View.VISIBLE : View.GONE);
        refreshCalendar();
        refreshAgenda();
        refreshPermissionBanner();
    }

    private void refreshCalendar() {
        monthTitle.setText(displayedMonth.format(
                DateTimeFormatter.ofPattern("yyyy年M月", Locale.CHINA)));
        calendarGrid.removeAllViews();
        Set<Integer> markedDays = database.eventDaysForMonth(displayedMonth);
        LocalDate first = displayedMonth.atDay(1);
        LocalDate gridStart = first.minusDays(first.getDayOfWeek().getValue() - 1L);
        LocalDate today = LocalDate.now();

        for (int index = 0; index < 42; index++) {
            LocalDate date = gridStart.plusDays(index);
            boolean inMonth = YearMonth.from(date).equals(displayedMonth);
            boolean selected = date.equals(selectedDate);
            boolean isToday = date.equals(today);
            boolean marked = inMonth && markedDays.contains(date.getDayOfMonth());

            LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER);
            cell.setClickable(true);
            cell.setFocusable(true);
            cell.setContentDescription(date + (marked ? "，有日程" : ""));
            cell.setOnTouchListener((view, event) ->
                    handleMonthCellTouch(view, date, event));

            TextView number = text(String.valueOf(date.getDayOfMonth()), 14,
                    inMonth ? UiUtils.TEXT : Color.rgb(187, 192, 203), selected);
            number.setGravity(Gravity.CENTER);
            if (selected) {
                number.setTextColor(Color.WHITE);
                number.setBackground(UiUtils.rounded(UiUtils.PRIMARY, 15, this));
            } else if (isToday) {
                number.setTextColor(UiUtils.PRIMARY_DARK);
                number.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
                number.setBackground(UiUtils.outlined(Color.TRANSPARENT,
                        UiUtils.PRIMARY, 15, this));
            }
            cell.addView(number, new LinearLayout.LayoutParams(dp(30), dp(30)));
            View dot = new View(this);
            dot.setBackground(UiUtils.rounded(
                    selected ? Color.WHITE : (marked ? UiUtils.PRIMARY : Color.TRANSPARENT),
                    3, this));
            LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(dp(5), dp(5));
            dotParams.topMargin = dp(2);
            cell.addView(dot, dotParams);
            cell.setOnClickListener(v -> {
                selectedDate = date;
                displayedMonth = YearMonth.from(date);
                refreshAll();
            });
            GridLayout.LayoutParams params = new GridLayout.LayoutParams(
                    GridLayout.spec(index / 7), GridLayout.spec(index % 7, 1, 1f));
            params.width = 0;
            params.height = dp(43);
            calendarGrid.addView(cell, params);
        }
    }

    private void refreshAgenda() {
        updateSelectionBar();
        agendaHost.removeAllViews();
        if (displayMode == MODE_WEEK) {
            showWeekTimeline();
            return;
        }
        selectedDateTitle.setText(selectedDate.format(
                DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINA)));
        List<ScheduleEvent> events = database.eventsForDate(selectedDate);
        long openCount = events.stream().filter(event -> !event.completed).count();
        agendaCount.setText(events.isEmpty() ? "暂无安排" : openCount + "项待办");
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        if (events.isEmpty()) {
            LinearLayout empty = new LinearLayout(this);
            empty.setOrientation(LinearLayout.VERTICAL);
            empty.setGravity(Gravity.CENTER);
            empty.addView(centerText("○", 38, Color.rgb(188, 194, 208), false));
            empty.addView(centerText("这一天还没有安排",
                    14, UiUtils.TEXT_SECONDARY, false));
            list.addView(empty, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(135)));
        } else {
            for (ScheduleEvent event : events) list.addView(eventCard(event));
        }
        scroll.addView(list);
        attachAgendaGestures(scroll);
        agendaHost.addView(scroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void showWeekTimeline() {
        LocalDate weekStart = selectedDate.minusDays(selectedDate.getDayOfWeek().getValue() - 1L);
        selectedDateTitle.setText(weekStart.format(DateTimeFormatter.ofPattern("M月d日", Locale.CHINA))
                + " – " + weekStart.plusDays(6).format(
                DateTimeFormatter.ofPattern("M月d日", Locale.CHINA)));
        agendaCount.setText("周时间轴");

        Map<LocalDate, List<ScheduleEvent>> data = new HashMap<>();
        for (int i = 0; i < 7; i++) {
            LocalDate date = weekStart.plusDays(i);
            data.put(date, database.eventsForDate(date));
        }
        WeekTimelineView timeline = new WeekTimelineView(this);
        timeline.setData(weekStart, selectedDate, data, this::showEditor);
        attachAgendaGestures(timeline);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(timeline, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        agendaHost.addView(scroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        scroll.post(() -> scroll.scrollTo(0, dp(6 * 56)));
    }

    private void attachAgendaGestures(View view) {
        view.setOnTouchListener(this::handleAgendaTouch);
    }

    private boolean handleMonthCellTouch(View cell, LocalDate date, MotionEvent event) {
        if (monthTransitionRunning) return true;
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            releaseMonthVelocityTracker();
            monthVelocityTracker = VelocityTracker.obtain();
            monthVelocityTracker.addMovement(event);
            monthDownX = event.getRawX();
            monthDownY = event.getRawY();
            monthDragging = false;
            calendarCard.animate().cancel();
            return true;
        }
        if (monthVelocityTracker != null) monthVelocityTracker.addMovement(event);
        float dx = event.getRawX() - monthDownX;
        float dy = event.getRawY() - monthDownY;
        if (action == MotionEvent.ACTION_MOVE) {
            if (!monthDragging && Math.abs(dx) > touchSlop
                    && Math.abs(dx) > Math.abs(dy) * 1.15f) {
                monthDragging = true;
            }
            if (monthDragging) {
                float offset = dx * 0.72f;
                calendarCard.setTranslationX(offset);
                float width = Math.max(1f, calendarCard.getWidth());
                calendarCard.setAlpha(Math.max(0.68f,
                        1f - Math.abs(offset) / width * 0.32f));
            }
            return true;
        }
        if (action == MotionEvent.ACTION_UP) {
            float velocityX = 0f;
            if (monthVelocityTracker != null) {
                monthVelocityTracker.computeCurrentVelocity(1000);
                velocityX = monthVelocityTracker.getXVelocity();
            }
            boolean change = monthDragging && (Math.abs(dx) > calendarCard.getWidth() * 0.16f
                    || Math.abs(velocityX) > 850f);
            releaseMonthVelocityTracker();
            if (change) {
                animateMonthChange(dx < 0 ? 1 : -1);
            } else {
                animateMonthHome();
                if (!monthDragging && Math.abs(dx) < touchSlop
                        && Math.abs(dy) < touchSlop) cell.performClick();
            }
            monthDragging = false;
            return true;
        }
        if (action == MotionEvent.ACTION_CANCEL) {
            releaseMonthVelocityTracker();
            monthDragging = false;
            animateMonthHome();
            return true;
        }
        return true;
    }

    private void animateMonthHome() {
        calendarCard.animate().cancel();
        calendarCard.animate()
                .translationX(0f)
                .alpha(1f)
                .setDuration(180L)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    private void animateMonthChange(int amount) {
        if (monthTransitionRunning) return;
        monthTransitionRunning = true;
        float width = Math.max(calendarCard.getWidth(), dp(280));
        float exit = amount > 0 ? -width : width;
        calendarCard.animate().cancel();
        calendarCard.animate()
                .translationX(exit)
                .alpha(0.2f)
                .setDuration(145L)
                .setInterpolator(new AccelerateInterpolator())
                .withEndAction(() -> {
                    displayedMonth = displayedMonth.plusMonths(amount);
                    selectedDate = displayedMonth.atDay(Math.min(
                            selectedDate.getDayOfMonth(), displayedMonth.lengthOfMonth()));
                    refreshCalendar();
                    refreshAgenda();
                    calendarCard.setTranslationX(amount > 0 ? width : -width);
                    calendarCard.setAlpha(0.2f);
                    calendarCard.animate()
                            .translationX(0f)
                            .alpha(1f)
                            .setDuration(210L)
                            .setInterpolator(new DecelerateInterpolator())
                            .withEndAction(() -> monthTransitionRunning = false)
                            .start();
                })
                .start();
    }

    private void releaseMonthVelocityTracker() {
        if (monthVelocityTracker != null) {
            monthVelocityTracker.recycle();
            monthVelocityTracker = null;
        }
    }

    private boolean handleAgendaTouch(View target, MotionEvent event) {
        agendaScaleDetector.onTouchEvent(event);
        int action = event.getActionMasked();
        if (event.getPointerCount() > 1 || agendaScaleDetector.isInProgress()) {
            if (target.getParent() != null) {
                target.getParent().requestDisallowInterceptTouchEvent(true);
            }
            ignoreAgendaRelease = true;
            releaseAgendaVelocityTracker();
            agendaHorizontalDragging = false;
            agendaVerticalDragging = false;
            return false;
        }
        if (action == MotionEvent.ACTION_DOWN) {
            releaseAgendaVelocityTracker();
            agendaVelocityTracker = VelocityTracker.obtain();
            agendaVelocityTracker.addMovement(event);
            agendaDownX = event.getRawX();
            agendaDownY = event.getRawY();
            agendaHorizontalDragging = false;
            agendaVerticalDragging = false;
            agendaStartedAtTop = !target.canScrollVertically(-1);
            ignoreAgendaRelease = false;
            agendaHost.animate().cancel();
            calendarCard.animate().cancel();
            return false;
        }
        if (agendaVelocityTracker != null) agendaVelocityTracker.addMovement(event);
        if (ignoreAgendaRelease) {
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                ignoreAgendaRelease = false;
                releaseAgendaVelocityTracker();
            }
            return false;
        }
        float dx = event.getRawX() - agendaDownX;
        float dy = event.getRawY() - agendaDownY;
        if (action == MotionEvent.ACTION_MOVE && !agendaTransitionRunning) {
            if (!agendaHorizontalDragging && !agendaVerticalDragging
                    && displayMode == MODE_DAY && Math.abs(dx) > touchSlop
                    && Math.abs(dx) > Math.abs(dy) * 1.12f) {
                agendaHorizontalDragging = true;
            }
            if (!agendaHorizontalDragging && !agendaVerticalDragging
                    && Math.abs(dy) > touchSlop && Math.abs(dy) > Math.abs(dx) * 1.12f) {
                if (displayMode == MODE_CALENDAR && dy < 0) {
                    agendaVerticalDragging = true;
                } else if (displayMode == MODE_DAY && dy > 0 && agendaStartedAtTop) {
                    agendaVerticalDragging = true;
                }
            }
            if (agendaHorizontalDragging) {
                float offset = dx * 0.72f;
                agendaHost.setTranslationX(offset);
                float width = Math.max(1f, agendaHost.getWidth());
                agendaHost.setAlpha(Math.max(0.68f,
                        1f - Math.abs(offset) / width * 0.32f));
            } else if (agendaVerticalDragging && displayMode == MODE_CALENDAR) {
                float overscroll = Math.min(dp(26),
                        Math.max(0f, -dy - touchSlop) * 0.18f);
                calendarCard.setTranslationY(-overscroll);
                calendarCard.setAlpha(1f - overscroll / dp(26) * 0.14f);
            } else if (agendaVerticalDragging && displayMode == MODE_DAY) {
                float overscroll = Math.min(dp(26),
                        Math.max(0f, dy - touchSlop) * 0.18f);
                agendaHost.setTranslationY(overscroll);
                agendaHost.setAlpha(1f - overscroll / dp(26) * 0.10f);
            }
            return false;
        }
        if (action == MotionEvent.ACTION_UP) {
            float velocityX = 0f;
            if (agendaVelocityTracker != null) {
                agendaVelocityTracker.computeCurrentVelocity(1000);
                velocityX = agendaVelocityTracker.getXVelocity();
            }
            releaseAgendaVelocityTracker();
            if (agendaHorizontalDragging && displayMode == MODE_DAY) {
                boolean change = Math.abs(dx) > agendaHost.getWidth() * 0.16f
                        || Math.abs(velocityX) > 900f;
                if (change) animateDayChange(dx < 0 ? 1 : -1);
                else resetAgendaDragPreview(true);
            } else if (agendaVerticalDragging && displayMode == MODE_CALENDAR) {
                boolean collapse = -dy > verticalSwitchDistance(target);
                if (collapse) animateCalendarVisibility(false);
                else resetAgendaDragPreview(true);
            } else if (agendaVerticalDragging && displayMode == MODE_DAY) {
                boolean expand = dy > verticalSwitchDistance(target);
                if (expand) animateCalendarVisibility(true);
                else resetAgendaDragPreview(true);
            }
            agendaHorizontalDragging = false;
            agendaVerticalDragging = false;
            return false;
        }
        if (action == MotionEvent.ACTION_CANCEL) {
            releaseAgendaVelocityTracker();
            agendaHorizontalDragging = false;
            agendaVerticalDragging = false;
            resetAgendaDragPreview(true);
        }
        return false;
    }

    private float verticalSwitchDistance(View target) {
        return Math.max(dp(240), target.getHeight() * 0.42f);
    }

    private void releaseAgendaVelocityTracker() {
        if (agendaVelocityTracker != null) {
            agendaVelocityTracker.recycle();
            agendaVelocityTracker = null;
        }
    }

    private void resetAgendaDragPreview(boolean animated) {
        agendaHost.animate().cancel();
        calendarCard.animate().cancel();
        if (animated) {
            agendaHost.animate()
                    .translationX(0f).translationY(0f).alpha(1f)
                    .setDuration(170L)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
            calendarCard.animate()
                    .translationY(0f).alpha(1f)
                    .setDuration(170L)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
        } else {
            agendaHost.setTranslationX(0f);
            agendaHost.setTranslationY(0f);
            agendaHost.setAlpha(1f);
            calendarCard.setTranslationY(0f);
            calendarCard.setAlpha(1f);
        }
    }

    private void animateDayChange(int amount) {
        if (agendaTransitionRunning) return;
        agendaTransitionRunning = true;
        float width = Math.max(agendaHost.getWidth(), dp(280));
        float exit = amount > 0 ? -width : width;
        agendaHost.animate().cancel();
        agendaHost.animate()
                .translationX(exit)
                .alpha(0.18f)
                .setDuration(145L)
                .setInterpolator(new AccelerateInterpolator())
                .withEndAction(() -> {
                    selectedDate = selectedDate.plusDays(amount);
                    displayedMonth = YearMonth.from(selectedDate);
                    refreshCalendar();
                    refreshAgenda();
                    agendaHost.setTranslationX(amount > 0 ? width : -width);
                    agendaHost.setTranslationY(0f);
                    agendaHost.setAlpha(0.18f);
                    agendaHost.animate()
                            .translationX(0f)
                            .alpha(1f)
                            .setDuration(205L)
                            .setInterpolator(new DecelerateInterpolator())
                            .withEndAction(() -> agendaTransitionRunning = false)
                            .start();
                })
                .start();
    }

    private void animateCalendarVisibility(boolean show) {
        if (agendaTransitionRunning || (show && displayMode != MODE_DAY)
                || (!show && displayMode != MODE_CALENDAR)) return;
        agendaTransitionRunning = true;
        calendarCard.animate().cancel();
        agendaHost.animate().cancel();
        LinearLayout.LayoutParams params =
                (LinearLayout.LayoutParams) calendarCard.getLayoutParams();
        if (!show) {
            int startHeight = calendarCard.getHeight();
            calendarExpandedHeight = startHeight;
            calendarExpandedBottomMargin = params.bottomMargin;
            float startTranslation = calendarCard.getTranslationY();
            params.height = startHeight;
            calendarCard.setLayoutParams(params);
            ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
            animator.setDuration(230L);
            animator.setInterpolator(new DecelerateInterpolator());
            animator.addUpdateListener(valueAnimator -> {
                float progress = (float) valueAnimator.getAnimatedValue();
                params.height = Math.max(0, Math.round(startHeight * (1f - progress)));
                params.bottomMargin = Math.max(0, Math.round(
                        calendarExpandedBottomMargin * (1f - progress)));
                calendarCard.setLayoutParams(params);
                calendarCard.setTranslationY(startTranslation * (1f - progress));
                calendarCard.setAlpha(1f - progress * 0.52f);
            });
            animator.addListener(new AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(Animator animation) {
                    calendarCard.setVisibility(View.GONE);
                    params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                    params.bottomMargin = calendarExpandedBottomMargin;
                    calendarCard.setLayoutParams(params);
                    calendarCard.setTranslationY(0f);
                    calendarCard.setAlpha(1f);
                    displayMode = MODE_DAY;
                    refreshAgenda();
                    agendaTransitionRunning = false;
                }
            });
            animator.start();
        } else {
            displayMode = MODE_CALENDAR;
            displayedMonth = YearMonth.from(selectedDate);
            refreshCalendar();
            int targetHeight = calendarExpandedHeight > 0
                    ? calendarExpandedHeight : dp(342);
            int targetMargin = calendarExpandedBottomMargin > 0
                    ? calendarExpandedBottomMargin : dp(12);
            float startAgendaTranslation = agendaHost.getTranslationY();
            params.height = 0;
            params.bottomMargin = 0;
            calendarCard.setLayoutParams(params);
            calendarCard.setVisibility(View.VISIBLE);
            calendarCard.setAlpha(0.48f);
            ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
            animator.setDuration(250L);
            animator.setInterpolator(new DecelerateInterpolator());
            animator.addUpdateListener(valueAnimator -> {
                float progress = (float) valueAnimator.getAnimatedValue();
                params.height = Math.round(targetHeight * progress);
                params.bottomMargin = Math.round(targetMargin * progress);
                calendarCard.setLayoutParams(params);
                calendarCard.setAlpha(0.48f + progress * 0.52f);
                agendaHost.setTranslationY(startAgendaTranslation * (1f - progress));
                agendaHost.setAlpha(0.9f + progress * 0.1f);
            });
            animator.addListener(new AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(Animator animation) {
                    params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                    params.bottomMargin = targetMargin;
                    calendarCard.setLayoutParams(params);
                    calendarCard.setAlpha(1f);
                    agendaHost.setTranslationY(0f);
                    agendaHost.setAlpha(1f);
                    refreshAgenda();
                    agendaTransitionRunning = false;
                }
            });
            animator.start();
        }
    }

    private void animateAgendaModeChange(int targetMode) {
        if (agendaTransitionRunning || targetMode == displayMode) return;
        agendaTransitionRunning = true;
        float exitScale = targetMode == MODE_WEEK ? 0.78f : 1.18f;
        float enterScale = targetMode == MODE_WEEK ? 1.12f : 0.88f;
        agendaHost.animate().cancel();
        agendaHost.animate()
                .scaleX(exitScale)
                .scaleY(exitScale)
                .alpha(0f)
                .setDuration(150L)
                .setInterpolator(new AccelerateInterpolator())
                .withEndAction(() -> {
                    displayMode = targetMode;
                    refreshAgenda();
                    agendaHost.setScaleX(enterScale);
                    agendaHost.setScaleY(enterScale);
                    agendaHost.setAlpha(0f);
                    agendaHost.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .alpha(1f)
                            .setDuration(220L)
                            .setInterpolator(new DecelerateInterpolator())
                            .withEndAction(() -> agendaTransitionRunning = false)
                            .start();
                })
                .start();
    }

    private void animateAgendaTransformHome() {
        agendaHost.animate().cancel();
        agendaHost.animate()
                .scaleX(1f)
                .scaleY(1f)
                .alpha(1f)
                .setDuration(190L)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    private View eventCard(ScheduleEvent event) {
        LinearLayout card = row();
        card.setGravity(Gravity.TOP);
        card.setPadding(dp(9), dp(11), dp(11), dp(11));
        boolean selectedForBatch = selectedEventIds.contains(event.id);
        card.setBackground(selectedForBatch
                ? UiUtils.outlined(0xFFF0F2FF, UiUtils.PRIMARY, 17, this)
                : UiUtils.rounded(UiUtils.SURFACE, 17, this));
        card.setAlpha(selectedForBatch ? 1f : event.completed ? 0.52f : 1f);
        card.setOnClickListener(v -> {
            if (eventSelectionMode) toggleEventSelection(event.id);
            else showEditor(event);
        });
        card.setOnLongClickListener(v -> {
            toggleEventSelection(event.id);
            return true;
        });

        CheckBox completed = new CheckBox(this);
        completed.setChecked(event.completed);
        completed.setButtonTintList(android.content.res.ColorStateList.valueOf(
                event.completed ? Color.rgb(88, 164, 117) : UiUtils.PRIMARY));
        completed.setContentDescription(event.completed ? "标记为未完成" : "标记为完成");
        completed.setOnCheckedChangeListener((button, checked) -> {
            event.completed = checked;
            database.setCompleted(event.id, checked);
            if (checked) AlarmScheduler.cancel(this, event.id);
            else AlarmScheduler.schedule(this, event);
            refreshAll();
        });
        card.addView(completed, new LinearLayout.LayoutParams(dp(43), dp(43)));

        TextView time = text(event.timeText(), 14,
                event.completed ? UiUtils.TEXT_SECONDARY
                        : event.anniversary ? UiUtils.ANNIVERSARY : UiUtils.PRIMARY_DARK,
                true);
        time.setGravity(Gravity.TOP);
        card.addView(time, new LinearLayout.LayoutParams(dp(92),
                ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout details = new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);
        TextView eventTitle = text(event.title, 16,
                event.completed ? UiUtils.TEXT_SECONDARY : UiUtils.TEXT, true);
        if (event.completed) eventTitle.setPaintFlags(
                eventTitle.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        details.addView(eventTitle);
        if (!event.notes.isEmpty()) {
            TextView notes = text(event.notes, 13, UiUtils.TEXT_SECONDARY, false);
            notes.setMaxLines(2);
            details.addView(notes);
        }
        details.setOnClickListener(v -> {
            if (eventSelectionMode) toggleEventSelection(event.id);
            else showEditor(event);
        });
        details.setOnLongClickListener(v -> {
            toggleEventSelection(event.id);
            return true;
        });
        card.addView(details, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        LinearLayout.LayoutParams params = matchWrap();
        params.bottomMargin = dp(8);
        card.setLayoutParams(params);
        return card;
    }

    private TextView selectionAction(String value, View.OnClickListener listener) {
        TextView action = text(value, 13,
                "删除".equals(value) ? UiUtils.DANGER : UiUtils.PRIMARY_DARK, true);
        action.setGravity(Gravity.CENTER);
        action.setPadding(dp(10), dp(7), dp(10), dp(7));
        action.setOnClickListener(listener);
        return action;
    }

    private void toggleEventSelection(long eventId) {
        eventSelectionMode = true;
        if (!selectedEventIds.add(eventId)) selectedEventIds.remove(eventId);
        if (selectedEventIds.isEmpty()) eventSelectionMode = false;
        refreshAgenda();
    }

    private void updateSelectionBar() {
        if (selectionBar == null) return;
        selectionBar.setVisibility(eventSelectionMode ? View.VISIBLE : View.GONE);
        selectionCount.setText("已选 " + selectedEventIds.size() + " 项");
    }

    private void exitEventSelection() {
        selectedEventIds.clear();
        eventSelectionMode = false;
        refreshAgenda();
    }

    private void completeSelectedEvents() {
        for (long eventId : new HashSet<>(selectedEventIds)) {
            ScheduleEvent event = database.getById(eventId);
            if (event == null) continue;
            event.completed = true;
            database.setCompleted(eventId, true);
            AlarmScheduler.cancel(this, eventId);
        }
        selectedEventIds.clear();
        eventSelectionMode = false;
        refreshAll();
    }

    private void deleteSelectedEvents() {
        if (selectedEventIds.isEmpty()) return;
        new AlertDialog.Builder(this)
                .setTitle("删除所选日程？")
                .setMessage("将删除 " + selectedEventIds.size() + " 项日程。")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (dialog, which) -> {
                    for (long eventId : new HashSet<>(selectedEventIds)) {
                        AlarmScheduler.cancel(this, eventId);
                        database.delete(eventId);
                    }
                    selectedEventIds.clear();
                    eventSelectionMode = false;
                    refreshAll();
                })
                .show();
    }

    private void showMonthYearPicker() {
        LinearLayout pickers = row();
        pickers.setPadding(dp(16), dp(8), dp(16), 0);
        NumberPicker year = new NumberPicker(this);
        year.setMinValue(1970);
        year.setMaxValue(2100);
        year.setValue(displayedMonth.getYear());
        NumberPicker month = new NumberPicker(this);
        month.setMinValue(1);
        month.setMaxValue(12);
        month.setDisplayedValues(new String[]{"1月", "2月", "3月", "4月", "5月", "6月",
                "7月", "8月", "9月", "10月", "11月", "12月"});
        month.setValue(displayedMonth.getMonthValue());
        pickers.addView(year, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        pickers.addView(month, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        new AlertDialog.Builder(this)
                .setTitle("选择年月")
                .setView(pickers)
                .setNegativeButton("取消", null)
                .setPositiveButton("确定", (dialog, which) -> {
                    displayedMonth = YearMonth.of(year.getValue(), month.getValue());
                    selectedDate = displayedMonth.atDay(Math.min(
                            selectedDate.getDayOfMonth(), displayedMonth.lengthOfMonth()));
                    refreshAll();
                })
                .show();
    }

    private void showMoreMenu(View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenu().add(0, 1, 0, "日程搜索");
        popup.getMenu().add(0, 2, 1, "权限设置");
        popup.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) {
                startActivity(new Intent(this, AllEventsActivity.class));
                return true;
            }
            if (item.getItemId() == 2) {
                openReminderSettings();
                return true;
            }
            return false;
        });
        popup.show();
    }

    private void showEditor(ScheduleEvent event) {
        activeEditor = new EventEditorDialog(this, event, selectedDate,
                new EventEditorDialog.Listener() {
                    @Override public void onSave(ScheduleEvent saved) {
                        database.save(saved);
                        AlarmScheduler.schedule(MainActivity.this, saved);
                        selectedDate = displayDate(saved);
                        displayedMonth = YearMonth.from(selectedDate);
                        refreshAll();
                        Toast.makeText(MainActivity.this,
                                saved.anniversary ? "纪念日已保存" : "日程已保存",
                                Toast.LENGTH_SHORT).show();
                    }

                    @Override public void onDelete(ScheduleEvent deleted) {
                        AlarmScheduler.cancel(MainActivity.this, deleted.id);
                        database.delete(deleted.id);
                        refreshAll();
                        Toast.makeText(MainActivity.this,
                                "日程已删除", Toast.LENGTH_SHORT).show();
                    }
                });
        activeEditor.show();
    }

    private void goToday() {
        selectedDate = LocalDate.now();
        displayedMonth = YearMonth.from(selectedDate);
        displayMode = MODE_CALENDAR;
        refreshAll();
        Toast.makeText(this, "已回到今天", Toast.LENGTH_SHORT).show();
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    NOTIFICATION_PERMISSION_REQUEST);
        }
    }

    private void refreshPermissionBanner() {
        String problem = reminderProblem();
        boolean reviewed = getPreferences(MODE_PRIVATE)
                .getBoolean("reminder_setup_reviewed", false);
        if (problem == null && reviewed) {
            permissionBanner.setVisibility(View.GONE);
            return;
        }
        permissionBanner.setVisibility(View.VISIBLE);
        permissionText.setText(problem == null
                ? "请完成澎湃OS后台与自启动设置" : problem);
    }

    private String reminderProblem() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) return "通知权限未开启";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && !getSystemService(AlarmManager.class).canScheduleExactAlarms()) {
            return "精确定时权限未开启";
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
                && !getSystemService(NotificationManager.class).canUseFullScreenIntent()) {
            return "锁屏亮屏提醒权限未开启";
        }
        return null;
    }

    private void openReminderSettings() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(4), dp(4), dp(4), 0);
        content.addView(text(
                "为保证红米/澎湃OS按时震动、响铃和亮屏，请依次确认：",
                14, UiUtils.TEXT_SECONDARY, false), matchBottom(12));
        content.addView(settingsRow("通知与铃声", notificationStatus(),
                v -> openNotificationSettings()), matchBottom(7));
        content.addView(settingsRow("精确定时", exactAlarmStatus(),
                v -> openExactAlarmSettings()), matchBottom(7));
        content.addView(settingsRow("锁屏亮屏提醒", fullScreenStatus(),
                v -> openFullScreenSettings()), matchBottom(7));
        content.addView(settingsRow("电池后台限制", "建议设为无限制",
                v -> openBatterySettings()), matchBottom(7));
        content.addView(settingsRow("锁屏显示与后台弹出", "澎湃OS建议允许",
                v -> openMiuiOtherPermissions()), matchBottom(7));
        content.addView(settingsRow("澎湃OS自启动", "建议允许",
                v -> openMiuiAutostart()), matchBottom(0));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("提醒权限设置")
                .setView(content)
                .setNegativeButton("稍后", null)
                .setPositiveButton("我已设置", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    getPreferences(MODE_PRIVATE).edit()
                            .putBoolean("reminder_setup_reviewed", true).apply();
                    refreshPermissionBanner();
                    dialog.dismiss();
                }));
        dialog.show();
    }

    private View settingsRow(String title, String status, View.OnClickListener click) {
        LinearLayout item = row();
        item.setPadding(dp(12), dp(9), dp(10), dp(9));
        item.setBackground(UiUtils.outlined(UiUtils.SURFACE, UiUtils.BORDER, 12, this));
        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.addView(text(title, 15, UiUtils.TEXT, true));
        labels.addView(text(status, 12,
                status.contains("已开启") ? Color.rgb(48, 145, 94) : UiUtils.TEXT_SECONDARY,
                false));
        item.addView(labels, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        item.addView(text("设置  ›", 13, UiUtils.PRIMARY_DARK, true));
        item.setOnClickListener(click);
        return item;
    }

    private String notificationStatus() {
        boolean allowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
        return allowed ? "已开启" : "未开启";
    }

    private String exactAlarmStatus() {
        boolean allowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.S
                || getSystemService(AlarmManager.class).canScheduleExactAlarms();
        return allowed ? "已开启" : "未开启";
    }

    private String fullScreenStatus() {
        boolean allowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE
                || getSystemService(NotificationManager.class).canUseFullScreenIntent();
        return allowed ? "已开启" : "未开启";
    }

    private void openNotificationSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestNotificationPermissionIfNeeded();
            return;
        }
        startSafely(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()));
    }

    private void openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            startSafely(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    Uri.parse("package:" + getPackageName())));
        }
    }

    private void openFullScreenSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startSafely(new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                    Uri.parse("package:" + getPackageName())));
        } else openNotificationSettings();
    }

    private void openBatterySettings() {
        startSafely(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
    }

    private void openMiuiAutostart() {
        Intent miui = new Intent().setComponent(new ComponentName(
                "com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity"));
        if (miui.resolveActivity(getPackageManager()) != null) startActivity(miui);
        else startSafely(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + getPackageName())));
    }

    private void openMiuiOtherPermissions() {
        Intent miui = new Intent("miui.intent.action.APP_PERM_EDITOR")
                .setComponent(new ComponentName(
                        "com.miui.securitycenter",
                        "com.miui.permcenter.permissions.PermissionsEditorActivity"))
                .putExtra("extra_pkgname", getPackageName());
        if (miui.resolveActivity(getPackageManager()) != null) startActivity(miui);
        else startSafely(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + getPackageName())));
    }

    private void startSafely(Intent intent) {
        try {
            startActivity(intent);
        } catch (Exception unavailable) {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName())));
        }
    }

    private void selectEventFromIntent(Intent intent) {
        if (intent == null || database == null) return;
        long eventId = intent.getLongExtra(AlarmScheduler.EXTRA_EVENT_ID, -1L);
        if (eventId < 0) return;
        ScheduleEvent event = database.getById(eventId);
        if (event == null) return;
        selectedDate = displayDate(event);
        displayedMonth = YearMonth.from(selectedDate);
    }

    private LocalDate displayDate(ScheduleEvent event) {
        if (event.isHabit()) {
            LocalDate today = LocalDate.now();
            if (today.isBefore(event.date)) return event.date;
            if (event.habitFrequency == ScheduleEvent.HabitFrequency.DAILY) return today;
            int daysUntil = Math.floorMod(
                    event.date.getDayOfWeek().getValue() - today.getDayOfWeek().getValue(), 7);
            return today.plusDays(daysUntil);
        }
        if (!event.anniversary) return event.date;
        int year = LocalDate.now().getYear();
        try {
            return LocalDate.of(year, event.date.getMonthValue(), event.date.getDayOfMonth());
        } catch (DateTimeException leapDay) {
            return LocalDate.of(year, event.date.getMonthValue(),
                    YearMonth.of(year, event.date.getMonthValue()).lengthOfMonth());
        }
    }

    private void applySystemBarAppearance() {
        View decor = getWindow().getDecorView();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = decor.getWindowInsetsController();
            if (controller != null) {
                int flags = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                        | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                controller.setSystemBarsAppearance(flags, flags);
                return;
            }
        }
        decor.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
    }

    private void showDiagnosticScreen(String report) {
        LinearLayout diagnostic = new LinearLayout(this);
        diagnostic.setOrientation(LinearLayout.VERTICAL);
        diagnostic.setPadding(rawDp(20), rawDp(48), rawDp(20), rawDp(18));
        diagnostic.setBackgroundColor(UiUtils.BACKGROUND);
        diagnostic.addView(text("Schedule 启动失败", 24, UiUtils.TEXT, true));
        TextView hint = text("请复制下面的诊断信息发给开发者。",
                14, UiUtils.TEXT_SECONDARY, false);
        hint.setPadding(0, rawDp(8), 0, rawDp(12));
        diagnostic.addView(hint);
        ScrollView scroll = new ScrollView(this);
        TextView details = text(report, 12, Color.rgb(45, 51, 65), false);
        details.setTextIsSelectable(true);
        details.setPadding(rawDp(12), rawDp(12), rawDp(12), rawDp(12));
        details.setBackgroundColor(Color.WHITE);
        scroll.addView(details);
        diagnostic.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        Button copy = new Button(this);
        copy.setText("复制诊断信息");
        copy.setAllCaps(false);
        copy.setTextColor(Color.WHITE);
        copy.setBackgroundColor(UiUtils.PRIMARY);
        copy.setOnClickListener(v -> {
            getSystemService(ClipboardManager.class).setPrimaryClip(
                    ClipData.newPlainText("Schedule crash report", report));
            Toast.makeText(this, "诊断信息已复制", Toast.LENGTH_SHORT).show();
        });
        diagnostic.addView(copy, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, rawDp(54)));
        setContentView(diagnostic);
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        return row;
    }

    private TextView text(String value, float size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setFontFeatureSettings("tnum");
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private TextView centerText(String value, float size, int color, boolean bold) {
        TextView view = text(value, size, color, bold);
        view.setGravity(Gravity.CENTER);
        return view;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams matchBottom(int bottomDp) {
        LinearLayout.LayoutParams params = matchWrap();
        params.bottomMargin = dp(bottomDp);
        return params;
    }

    private int dp(int value) {
        return UiUtils.dp(this, value);
    }

    private int rawDp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
