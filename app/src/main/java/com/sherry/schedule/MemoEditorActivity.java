package com.sherry.schedule;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
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
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;

import com.sherry.schedule.data.Memo;
import com.sherry.schedule.data.ScheduleDatabase;
import com.sherry.schedule.data.ScheduleEvent;
import com.sherry.schedule.ui.UiUtils;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class MemoEditorActivity extends Activity {
    public static final String EXTRA_MEMO_ID = "memo_id";

    private ScheduleDatabase database;
    private Memo memo;
    private EditText titleInput;
    private EditText contentInput;
    private TextView metadata;
    private TextView pin;
    private boolean deleted;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        database = ScheduleDatabase.get(this);
        long id = getIntent().getLongExtra(EXTRA_MEMO_ID, -1L);
        memo = id < 0 ? new Memo() : database.getMemoById(id);
        if (memo == null) memo = new Memo();
        buildContent();
        updateMetadata();
    }

    @Override
    public void onBackPressed() {
        saveAndFinish();
    }

    private void buildContent() {
        getWindow().setStatusBarColor(Color.WHITE);
        getWindow().setNavigationBarColor(Color.WHITE);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);
        root.setPadding(dp(20), dp(8), dp(20), dp(12));
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
            view.setPadding(dp(20), top + dp(8), dp(20), bottom + dp(12));
            return insets;
        });

        LinearLayout header = row();
        TextView back = text("‹", 34, UiUtils.TEXT, false);
        back.setGravity(Gravity.CENTER);
        back.setOnClickListener(v -> saveAndFinish());
        header.addView(back, new LinearLayout.LayoutParams(dp(48), dp(52)));
        TextView appName = text("Schedule", 17, UiUtils.TEXT, true);
        header.addView(appName, new LinearLayout.LayoutParams(0, dp(52), 1));
        pin = text(memo.pinned ? "★" : "☆", 24,
                memo.pinned ? UiUtils.PRIMARY : UiUtils.TEXT_SECONDARY, false);
        pin.setGravity(Gravity.CENTER);
        pin.setOnClickListener(v -> {
            memo.pinned = !memo.pinned;
            updatePin();
        });
        header.addView(pin, new LinearLayout.LayoutParams(dp(45), dp(52)));
        TextView more = text("⋮", 28, UiUtils.TEXT, true);
        more.setGravity(Gravity.CENTER);
        more.setOnClickListener(this::showMenu);
        header.addView(more, new LinearLayout.LayoutParams(dp(42), dp(52)));
        root.addView(header, matchWrap());

        titleInput = new EditText(this);
        titleInput.setText(memo.title);
        titleInput.setHint("标题");
        titleInput.setHintTextColor(Color.rgb(205, 207, 213));
        titleInput.setTextColor(UiUtils.TEXT);
        titleInput.setTextSize(31);
        titleInput.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        titleInput.setSingleLine(false);
        titleInput.setMaxLines(3);
        titleInput.setPadding(0, dp(20), 0, dp(10));
        titleInput.setBackgroundColor(Color.TRANSPARENT);
        root.addView(titleInput, matchWrap());

        metadata = text("", 13, Color.rgb(155, 160, 171), false);
        LinearLayout.LayoutParams metaParams = matchWrap();
        metaParams.bottomMargin = dp(14);
        root.addView(metadata, metaParams);

        contentInput = new EditText(this);
        contentInput.setText(memo.content);
        contentInput.setTextColor(UiUtils.TEXT);
        contentInput.setTextSize(18);
        contentInput.setGravity(Gravity.TOP);
        contentInput.setLineSpacing(dp(4), 1.08f);
        contentInput.setPadding(0, dp(8), 0, dp(12));
        contentInput.setBackgroundColor(Color.TRANSPARENT);
        contentInput.setSingleLine(false);
        root.addView(contentInput, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateMetadata();
            }
            @Override public void afterTextChanged(Editable s) { }
        };
        titleInput.addTextChangedListener(watcher);
        contentInput.addTextChangedListener(watcher);
        setContentView(root);
        applyBars();
    }

    private void showMenu(View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenu().add(0, 1, 0, "关联日程");
        if (memo.id != 0L) popup.getMenu().add(0, 2, 1, "删除");
        popup.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) chooseLinkedEvent();
            else if (item.getItemId() == 2) confirmDelete();
            return true;
        });
        popup.show();
    }

    private void chooseLinkedEvent() {
        List<Long> ids = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        ids.add(-1L);
        labels.add("无");
        int checked = 0;
        for (ScheduleEvent event : database.allEvents()) {
            ids.add(event.id);
            labels.add(event.title + " · " + event.dateText() + " · " + event.timeText());
            if (event.id == memo.linkedEventId) checked = ids.size() - 1;
        }
        final int initial = checked;
        new AlertDialog.Builder(this)
                .setTitle("关联日程")
                .setSingleChoiceItems(labels.toArray(new String[0]), checked,
                        (dialog, which) -> {
                            memo.linkedEventId = ids.get(which);
                            dialog.dismiss();
                        })
                .setNegativeButton("取消", null)
                .show();
    }

    private void confirmDelete() {
        new AlertDialog.Builder(this)
                .setTitle("删除备忘？")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (dialog, which) -> {
                    database.deleteMemo(memo.id);
                    deleted = true;
                    finish();
                })
                .show();
    }

    private void saveAndFinish() {
        if (!deleted) {
            String title = titleInput.getText().toString().trim();
            String content = contentInput.getText().toString().trim();
            if (!title.isEmpty() || !content.isEmpty()) {
                memo.title = title.isEmpty() ? "未命名备忘" : title;
                memo.content = content;
                database.saveMemo(memo);
            }
        }
        finish();
    }

    private void updateMetadata() {
        if (metadata == null || titleInput == null || contentInput == null) return;
        long time = memo.updatedAt > 0L ? memo.updatedAt : System.currentTimeMillis();
        String date = Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()).format(
                DateTimeFormatter.ofPattern("M月d日 HH:mm", Locale.CHINA));
        int count = titleInput.getText().length() + contentInput.getText().length();
        metadata.setText(date + "  |  " + count + "字");
    }

    private void updatePin() {
        pin.setText(memo.pinned ? "★" : "☆");
        pin.setTextColor(memo.pinned ? UiUtils.PRIMARY : UiUtils.TEXT_SECONDARY);
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
