package com.sherry.schedule;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.sherry.schedule.alarm.AlarmScheduler;
import com.sherry.schedule.data.Memo;
import com.sherry.schedule.data.ScheduleDatabase;
import com.sherry.schedule.data.ScheduleEvent;
import com.sherry.schedule.ui.AppBottomNavigation;
import com.sherry.schedule.ui.EventEditorDialog;
import com.sherry.schedule.ui.UiUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class MemoActivity extends Activity {
    private ScheduleDatabase database;
    private LinearLayout memoList;
    private EditText search;
    private TextView title;
    private TextView count;
    private Button addButton;
    private boolean showingArchived;
    private EventEditorDialog activeEventEditor;
    private float privateGestureDownY;
    private boolean privateGestureDragging;
    private float privateGestureDistancePulled;
    private boolean privateGestureThresholdReached;

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
        if (memoList != null) refresh();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (activeEventEditor != null
                && activeEventEditor.handleRingtoneResult(requestCode, resultCode, data)) return;
        super.onActivityResult(requestCode, resultCode, data);
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
            view.setPadding(dp(18), top + dp(12), dp(18), bottom + dp(6));
            return insets;
        });

        LinearLayout header = row();
        title = text("Schedule", 27, UiUtils.TEXT, true);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(45), 1));
        count = text("", 13, UiUtils.TEXT_SECONDARY, false);
        count.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        header.addView(count);
        TextView menu = text("⋮", 29, UiUtils.TEXT, true);
        menu.setGravity(Gravity.CENTER);
        menu.setOnClickListener(this::showHeaderMenu);
        header.addView(menu, new LinearLayout.LayoutParams(dp(42), dp(45)));
        LinearLayout.LayoutParams headerParams = matchWrap();
        headerParams.bottomMargin = dp(10);
        root.addView(header, headerParams);

        search = new EditText(this);
        search.setSingleLine(true);
        search.setTextSize(15);
        search.setTextColor(UiUtils.TEXT);
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

        ScrollView scroll = new ScrollView(this);
        memoList = new LinearLayout(this);
        memoList.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(memoList);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setOnTouchListener((view, event) -> {
            if (event.getActionMasked() == android.view.MotionEvent.ACTION_DOWN) {
                privateGestureDownY = event.getRawY();
                privateGestureDragging = scroll.getScrollY() == 0;
                privateGestureDistancePulled = 0f;
                privateGestureThresholdReached = false;
                scroll.animate().cancel();
            } else if (event.getActionMasked() == android.view.MotionEvent.ACTION_MOVE
                    && privateGestureDragging) {
                float pulled = event.getRawY() - privateGestureDownY;
                if (pulled > 0f && scroll.getScrollY() == 0) {
                    privateGestureDistancePulled = pulled;
                    scroll.setTranslationY(privatePullOffset(pulled));
                    boolean reached = pulled >= privateGestureDistance(scroll);
                    if (reached && !privateGestureThresholdReached) {
                        scroll.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                    }
                    privateGestureThresholdReached = reached;
                } else if (pulled < 0f) {
                    privateGestureDragging = false;
                    resetPrivatePull(scroll);
                }
            } else if (event.getActionMasked() == android.view.MotionEvent.ACTION_UP
                    || event.getActionMasked() == android.view.MotionEvent.ACTION_CANCEL) {
                boolean open = event.getActionMasked() == android.view.MotionEvent.ACTION_UP
                        && privateGestureDragging
                        && privateGestureDistancePulled >= privateGestureDistance(scroll);
                privateGestureDragging = false;
                privateGestureDistancePulled = 0f;
                privateGestureThresholdReached = false;
                resetPrivatePull(scroll);
                if (open) scroll.postDelayed(() -> startActivity(
                        new Intent(this, PrivateUnlockActivity.class)), 130L);
            }
            return false;
        });
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        addButton = new Button(this);
        addButton.setText("＋  添加备忘");
        addButton.setTextSize(16);
        addButton.setTextColor(Color.WHITE);
        addButton.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        addButton.setAllCaps(false);
        addButton.setBackground(UiUtils.rounded(UiUtils.PRIMARY, 17, this));
        addButton.setOnClickListener(v -> showMemoEditor(null));
        LinearLayout.LayoutParams addParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(54));
        addParams.topMargin = dp(8);
        root.addView(addButton, addParams);
        root.addView(AppBottomNavigation.create(this, true), matchWrap());

        setContentView(root);
        applyBars();
    }

    private void refresh() {
        String query = search == null ? ""
                : search.getText().toString().trim().toLowerCase(Locale.CHINA);
        List<Memo> filtered = new ArrayList<>();
        for (Memo memo : database.allMemos(showingArchived)) {
            ScheduleEvent linked = memo.linkedEventId < 0
                    ? null : database.getById(memo.linkedEventId);
            String haystack = (memo.title + " " + memo.content + " "
                    + (linked == null ? "" : linked.title)).toLowerCase(Locale.CHINA);
            if (query.isEmpty() || haystack.contains(query)) filtered.add(memo);
        }
        title.setText("Schedule");
        count.setText((showingArchived ? "归档 · " : "") + filtered.size() + "项");
        addButton.setVisibility(showingArchived ? View.GONE : View.VISIBLE);
        memoList.removeAllViews();
        if (filtered.isEmpty()) {
            TextView empty = text(showingArchived ? "没有归档" : "还没有备忘",
                    15, UiUtils.TEXT_SECONDARY, false);
            empty.setGravity(Gravity.CENTER);
            memoList.addView(empty, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(160)));
            return;
        }
        for (Memo memo : filtered) memoList.addView(memoCard(memo));
    }

    private View memoCard(Memo memo) {
        LinearLayout card = row();
        card.setGravity(Gravity.TOP);
        card.setPadding(dp(10), dp(11), dp(8), dp(11));
        card.setBackground(UiUtils.rounded(Color.WHITE, 17, this));
        card.setAlpha(memo.completed ? 0.52f : 1f);
        card.setOnClickListener(v -> showMemoEditor(memo));

        CheckBox completed = new CheckBox(this);
        completed.setChecked(memo.completed);
        completed.setButtonTintList(android.content.res.ColorStateList.valueOf(
                memo.completed ? Color.rgb(88, 164, 117) : UiUtils.PRIMARY));
        completed.setOnCheckedChangeListener((button, checked) -> {
            database.setMemoCompleted(memo.id, checked);
            refresh();
        });
        card.addView(completed, new LinearLayout.LayoutParams(dp(43), dp(43)));

        LinearLayout details = new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);
        TextView memoTitle = text(memo.title, 16,
                memo.completed ? UiUtils.TEXT_SECONDARY : UiUtils.TEXT, true);
        if (memo.completed) memoTitle.setPaintFlags(
                memoTitle.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        details.addView(memoTitle);
        if (!memo.content.isEmpty()) {
            TextView body = text(memo.content, 13, UiUtils.TEXT_SECONDARY, false);
            body.setMaxLines(3);
            details.addView(body);
        }
        ScheduleEvent linked = memo.linkedEventId < 0
                ? null : database.getById(memo.linkedEventId);
        String footer = formatUpdated(memo.updatedAt);
        if (linked != null) footer += " · " + linked.title;
        details.addView(text(footer, 11, UiUtils.TEXT_SECONDARY, false));
        details.setOnClickListener(v -> showMemoEditor(memo));
        card.addView(details, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView pin = text(memo.pinned ? "★" : "☆", 22,
                memo.pinned ? UiUtils.PRIMARY : UiUtils.TEXT_SECONDARY, false);
        pin.setGravity(Gravity.CENTER);
        pin.setOnClickListener(v -> {
            database.setMemoPinned(memo.id, !memo.pinned);
            refresh();
        });
        card.addView(pin, new LinearLayout.LayoutParams(dp(39), dp(42)));

        TextView more = text("⋮", 25, UiUtils.TEXT_SECONDARY, true);
        more.setGravity(Gravity.CENTER);
        more.setOnClickListener(v -> showMemoMenu(v, memo));
        card.addView(more, new LinearLayout.LayoutParams(dp(35), dp(42)));

        LinearLayout.LayoutParams params = matchWrap();
        params.bottomMargin = dp(8);
        card.setLayoutParams(params);
        return card;
    }

    private void showHeaderMenu(View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenu().add(showingArchived ? "返回备忘" : "查看归档");
        popup.setOnMenuItemClickListener(item -> {
            showingArchived = !showingArchived;
            refresh();
            return true;
        });
        popup.show();
    }

    private void showMemoMenu(View anchor, Memo memo) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenu().add(0, 1, 0, "编辑");
        popup.getMenu().add(0, 2, 1, "转为日程");
        popup.getMenu().add(0, 3, 2, memo.archived ? "恢复" : "归档");
        popup.getMenu().add(0, 4, 3, "删除");
        popup.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) showMemoEditor(memo);
            else if (item.getItemId() == 2) convertToSchedule(memo);
            else if (item.getItemId() == 3) {
                database.setMemoArchived(memo.id, !memo.archived);
                refresh();
            } else if (item.getItemId() == 4) confirmDelete(memo);
            return true;
        });
        popup.show();
    }

    private void showMemoEditor(Memo memo) {
        Intent intent = new Intent(this, MemoEditorActivity.class);
        if (memo != null) intent.putExtra(MemoEditorActivity.EXTRA_MEMO_ID, memo.id);
        startActivity(intent);
    }

    private void convertToSchedule(Memo memo) {
        ScheduleEvent draft = new ScheduleEvent();
        draft.title = memo.title;
        draft.notes = memo.content;
        LocalDateTime suggested = LocalDateTime.now().plusMinutes(30);
        draft.date = suggested.toLocalDate();
        draft.startMinutes = suggested.getHour() * 60 + suggested.getMinute();
        activeEventEditor = new EventEditorDialog(this, draft, draft.date,
                new EventEditorDialog.Listener() {
                    @Override public void onSave(ScheduleEvent saved) {
                        database.save(saved);
                        AlarmScheduler.schedule(MemoActivity.this, saved);
                        memo.linkedEventId = saved.id;
                        database.saveMemo(memo);
                        refresh();
                        Toast.makeText(MemoActivity.this,
                                "已转为日程", Toast.LENGTH_SHORT).show();
                    }

                    @Override public void onDelete(ScheduleEvent ignored) { }
                });
        activeEventEditor.show();
    }

    private void confirmDelete(Memo memo) {
        new AlertDialog.Builder(this)
                .setTitle("删除备忘？")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (dialog, which) -> {
                    database.deleteMemo(memo.id);
                    refresh();
                })
                .show();
    }

    private String formatUpdated(long millis) {
        if (millis <= 0L) return "";
        return Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(
                DateTimeFormatter.ofPattern("M月d日 HH:mm", Locale.CHINA));
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

    private float privateGestureDistance(View target) {
        return Math.max(dp(260), target.getHeight() * 0.41f);
    }

    private float privatePullOffset(float distance) {
        float maximum = dp(170);
        return (float) (maximum * (1d - Math.exp(-distance / Math.max(1f, dp(240)))));
    }

    private void resetPrivatePull(View target) {
        target.animate().cancel();
        target.animate().translationY(0f).setDuration(260L)
                .setInterpolator(new android.view.animation.OvershootInterpolator(0.72f))
                .start();
    }
}
