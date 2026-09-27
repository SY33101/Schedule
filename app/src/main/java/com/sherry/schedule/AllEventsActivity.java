package com.sherry.schedule;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.sherry.schedule.alarm.AlarmScheduler;
import com.sherry.schedule.data.ScheduleDatabase;
import com.sherry.schedule.data.ScheduleEvent;
import com.sherry.schedule.ui.EventEditorDialog;
import com.sherry.schedule.ui.UiUtils;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class AllEventsActivity extends Activity {
    private ScheduleDatabase database;
    private LinearLayout eventList;
    private EditText search;
    private TextView count;
    private LinearLayout selectionBar;
    private TextView selectionCount;
    private EventEditorDialog activeEditor;
    private final Set<Long> selectedEventIds = new HashSet<>();
    private boolean selectionMode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        database = ScheduleDatabase.get(this);
        buildContent();
        refresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (eventList != null) refresh();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (activeEditor != null
                && activeEditor.handleRingtoneResult(requestCode, resultCode, data)) return;
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onBackPressed() {
        if (selectionMode) {
            exitSelection();
            return;
        }
        super.onBackPressed();
    }

    private void buildContent() {
        getWindow().setStatusBarColor(UiUtils.BACKGROUND);
        getWindow().setNavigationBarColor(UiUtils.SURFACE);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(10), dp(18), dp(10));
        root.setBackgroundColor(UiUtils.BACKGROUND);
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
            view.setPadding(dp(18), top + dp(10), dp(18), bottom + dp(10));
            return insets;
        });

        LinearLayout header = row();
        TextView back = text("‹", 31, UiUtils.TEXT, false);
        back.setGravity(Gravity.CENTER);
        back.setContentDescription("返回");
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(42), dp(44)));
        TextView title = text("所有日程", 24, UiUtils.TEXT, true);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(44), 1));
        count = text("", 13, UiUtils.TEXT_SECONDARY, false);
        count.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        header.addView(count);
        LinearLayout.LayoutParams headerParams = matchWrap();
        headerParams.bottomMargin = dp(10);
        root.addView(header, headerParams);

        search = new EditText(this);
        search.setSingleLine(true);
        search.setTextSize(15);
        search.setTextColor(UiUtils.TEXT);
        search.setHintTextColor(Color.rgb(151, 158, 174));
        search.setHint("");
        search.setCompoundDrawablesWithIntrinsicBounds(
                android.R.drawable.ic_menu_search, 0, 0, 0);
        search.setCompoundDrawablePadding(dp(8));
        search.setPadding(dp(13), 0, dp(13), 0);
        search.setBackground(UiUtils.outlined(Color.WHITE, UiUtils.BORDER, 15, this));
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                refresh();
            }
            @Override public void afterTextChanged(Editable s) { }
        });
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(50));
        searchParams.bottomMargin = dp(12);
        root.addView(search, searchParams);

        selectionBar = row();
        selectionBar.setPadding(dp(12), dp(6), dp(6), dp(6));
        selectionBar.setBackground(UiUtils.rounded(0xFFE8ECFF, 14, this));
        selectionCount = text("", 14, UiUtils.PRIMARY_DARK, true);
        selectionBar.addView(selectionCount, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        selectionBar.addView(selectionAction("完成", v -> completeSelected()));
        selectionBar.addView(selectionAction("删除", v -> deleteSelected()));
        selectionBar.addView(selectionAction("取消", v -> exitSelection()));
        selectionBar.setVisibility(View.GONE);
        LinearLayout.LayoutParams selectionParams = matchWrap();
        selectionParams.bottomMargin = dp(8);
        root.addView(selectionBar, selectionParams);

        ScrollView scroll = new ScrollView(this);
        eventList = new LinearLayout(this);
        eventList.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(eventList);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(root);
        applyBars();
    }

    private void refresh() {
        updateSelectionBar();
        String query = search == null ? "" : search.getText().toString().trim().toLowerCase(Locale.CHINA);
        List<ScheduleEvent> source = new ArrayList<>(database.allEvents());
        source.sort(Comparator
                .comparing(this::sortDate)
                .thenComparingInt(event -> event.startMinutes)
                .thenComparing(event -> event.title));
        List<ScheduleEvent> filtered = new ArrayList<>();
        for (ScheduleEvent event : source) {
            String haystack = (event.title + " " + event.notes + " " + event.dateText()
                    + " " + event.timeText()
                    + (event.anniversary ? " 纪念日" : "")
                    + (event.isHabit() ? " 习惯" : ""))
                    .toLowerCase(Locale.CHINA);
            if (query.isEmpty() || haystack.contains(query)) filtered.add(event);
        }
        count.setText(filtered.size() + "项");
        eventList.removeAllViews();
        if (filtered.isEmpty()) {
            TextView empty = text(query.isEmpty() ? "还没有日程" : "没有找到匹配的日程",
                    15, UiUtils.TEXT_SECONDARY, false);
            empty.setGravity(Gravity.CENTER);
            eventList.addView(empty, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(150)));
            return;
        }
        for (ScheduleEvent event : filtered) eventList.addView(eventCard(event));
    }

    private View eventCard(ScheduleEvent event) {
        LinearLayout card = row();
        card.setGravity(Gravity.TOP);
        card.setPadding(dp(12), dp(12), dp(10), dp(12));
        boolean selected = selectedEventIds.contains(event.id);
        card.setBackground(selected
                ? UiUtils.outlined(0xFFF0F2FF, UiUtils.PRIMARY, 16, this)
                : UiUtils.rounded(Color.WHITE, 16, this));
        card.setAlpha(selected ? 1f : event.completed ? 0.52f : 1f);
        card.setOnClickListener(v -> {
            if (selectionMode) toggleSelection(event.id);
            else showEditor(event);
        });
        card.setOnLongClickListener(v -> {
            toggleSelection(event.id);
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
            refresh();
        });
        card.addView(completed, new LinearLayout.LayoutParams(dp(44), dp(44)));

        LinearLayout details = new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);
        TextView title = text(event.title, 16,
                event.completed ? UiUtils.TEXT_SECONDARY : UiUtils.TEXT, true);
        if (event.completed) title.setPaintFlags(
                title.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        details.addView(title);
        String dateLine = event.dateText() + " · " + event.timeText();
        TextView when = text(dateLine, 13,
                event.anniversary ? UiUtils.ANNIVERSARY : UiUtils.PRIMARY_DARK, false);
        details.addView(when);
        if (!event.notes.isEmpty()) {
            TextView notes = text(event.notes, 12, UiUtils.TEXT_SECONDARY, false);
            notes.setMaxLines(2);
            details.addView(notes);
        }
        details.setOnClickListener(v -> {
            if (selectionMode) toggleSelection(event.id);
            else showEditor(event);
        });
        details.setOnLongClickListener(v -> {
            toggleSelection(event.id);
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

    private void toggleSelection(long eventId) {
        selectionMode = true;
        if (!selectedEventIds.add(eventId)) selectedEventIds.remove(eventId);
        if (selectedEventIds.isEmpty()) selectionMode = false;
        refresh();
    }

    private void updateSelectionBar() {
        if (selectionBar == null) return;
        selectionBar.setVisibility(selectionMode ? View.VISIBLE : View.GONE);
        selectionCount.setText("已选 " + selectedEventIds.size() + " 项");
    }

    private void exitSelection() {
        selectedEventIds.clear();
        selectionMode = false;
        refresh();
    }

    private void completeSelected() {
        for (long eventId : new HashSet<>(selectedEventIds)) {
            database.setCompleted(eventId, true);
            AlarmScheduler.cancel(this, eventId);
        }
        selectedEventIds.clear();
        selectionMode = false;
        refresh();
    }

    private void deleteSelected() {
        if (selectedEventIds.isEmpty()) return;
        new android.app.AlertDialog.Builder(this)
                .setTitle("删除所选日程？")
                .setMessage("将删除 " + selectedEventIds.size() + " 项日程。")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (dialog, which) -> {
                    for (long eventId : new HashSet<>(selectedEventIds)) {
                        AlarmScheduler.cancel(this, eventId);
                        database.delete(eventId);
                    }
                    selectedEventIds.clear();
                    selectionMode = false;
                    refresh();
                })
                .show();
    }

    private LocalDate sortDate(ScheduleEvent event) {
        LocalDate today = LocalDate.now();
        if (event.isHabit()) {
            if (today.isBefore(event.date)) return event.date;
            if (event.habitFrequency == ScheduleEvent.HabitFrequency.DAILY) return today;
            int daysUntil = Math.floorMod(
                    event.date.getDayOfWeek().getValue() - today.getDayOfWeek().getValue(), 7);
            return today.plusDays(daysUntil);
        }
        if (!event.anniversary) return event.date;
        int year = today.getYear();
        LocalDate occurrence = anniversaryInYear(event, year);
        if (occurrence.isBefore(today)) occurrence = anniversaryInYear(event, year + 1);
        return occurrence;
    }

    private LocalDate anniversaryInYear(ScheduleEvent event, int year) {
        int day = Math.min(event.date.getDayOfMonth(),
                YearMonth.of(year, event.date.getMonthValue()).lengthOfMonth());
        return LocalDate.of(year, event.date.getMonthValue(), day);
    }

    private void showEditor(ScheduleEvent event) {
        activeEditor = new EventEditorDialog(this, event, sortDate(event),
                new EventEditorDialog.Listener() {
                    @Override public void onSave(ScheduleEvent saved) {
                        database.save(saved);
                        AlarmScheduler.schedule(AllEventsActivity.this, saved);
                        refresh();
                    }

                    @Override public void onDelete(ScheduleEvent deleted) {
                        AlarmScheduler.cancel(AllEventsActivity.this, deleted.id);
                        database.delete(deleted.id);
                        refresh();
                        Toast.makeText(AllEventsActivity.this,
                                "日程已删除", Toast.LENGTH_SHORT).show();
                    }
                });
        activeEditor.show();
    }

    private void applyBars() {
        View decor = getWindow().getDecorView();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = decor.getWindowInsetsController();
            if (controller != null) {
                int flags = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                        | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                controller.setSystemBarsAppearance(flags, flags);
            }
        }
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
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return UiUtils.dp(this, value);
    }
}
