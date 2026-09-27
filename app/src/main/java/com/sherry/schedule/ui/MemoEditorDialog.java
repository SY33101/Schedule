package com.sherry.schedule.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import com.sherry.schedule.data.Memo;
import com.sherry.schedule.data.ScheduleDatabase;
import com.sherry.schedule.data.ScheduleEvent;

import java.util.ArrayList;
import java.util.List;

public final class MemoEditorDialog {
    public interface Listener {
        void onSave(Memo memo);

        void onDelete(Memo memo);
    }

    private final Activity activity;
    private final Listener listener;
    private final Memo draft;
    private final Dialog dialog;
    private final List<Long> linkedEventIds = new ArrayList<>();
    private EditText titleInput;
    private EditText contentInput;
    private CheckBox pinned;
    private Spinner linkedEventSpinner;

    public MemoEditorDialog(Activity activity, Memo source, Listener listener) {
        this.activity = activity;
        this.listener = listener;
        this.draft = copyOf(source);
        this.dialog = new Dialog(activity);
        build();
    }

    public void show() {
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(
                    (int) (activity.getResources().getDisplayMetrics().widthPixels * 0.94f),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
    }

    private void build() {
        ScrollView scroll = new ScrollView(activity);
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(22), dp(20), dp(22), dp(16));
        content.setBackground(UiUtils.rounded(UiUtils.SURFACE, 24, activity));
        scroll.addView(content);

        content.addView(UiUtils.text(activity,
                draft.id == 0L ? "添加备忘" : "编辑备忘",
                23, UiUtils.TEXT, true), matchWrap(18));

        addLabel(content, "标题");
        titleInput = input(false);
        titleInput.setText(draft.title);
        content.addView(titleInput, matchWrap(14));

        addLabel(content, "正文");
        contentInput = input(true);
        contentInput.setText(draft.content);
        content.addView(contentInput, matchFixed(150, 10));

        pinned = new CheckBox(activity);
        pinned.setText("置顶");
        pinned.setTextColor(UiUtils.TEXT);
        pinned.setTextSize(15);
        pinned.setChecked(draft.pinned);
        pinned.setButtonTintList(android.content.res.ColorStateList.valueOf(UiUtils.PRIMARY));
        content.addView(pinned, matchWrap(12));

        addLabel(content, "关联日程");
        linkedEventSpinner = new Spinner(activity);
        List<String> labels = new ArrayList<>();
        linkedEventIds.add(-1L);
        labels.add("无");
        int selectedIndex = 0;
        List<ScheduleEvent> events = ScheduleDatabase.get(activity).allEvents();
        for (ScheduleEvent event : events) {
            linkedEventIds.add(event.id);
            labels.add(event.title + " · " + event.dateText() + " · " + event.timeText());
            if (event.id == draft.linkedEventId) selectedIndex = linkedEventIds.size() - 1;
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                activity, android.R.layout.simple_spinner_dropdown_item, labels) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                TextView view = (TextView) super.getView(position, convertView, parent);
                view.setTextColor(UiUtils.TEXT);
                view.setTextSize(14);
                view.setPadding(dp(14), 0, dp(10), 0);
                return view;
            }
        };
        linkedEventSpinner.setAdapter(adapter);
        linkedEventSpinner.setSelection(selectedIndex);
        linkedEventSpinner.setBackground(
                UiUtils.outlined(UiUtils.SURFACE, UiUtils.BORDER, 12, activity));
        content.addView(linkedEventSpinner, matchFixed(50, 20));

        LinearLayout actions = horizontal();
        if (draft.id != 0L) {
            Button delete = actionButton("删除", false);
            delete.setTextColor(UiUtils.DANGER);
            delete.setOnClickListener(v -> confirmDelete());
            actions.addView(delete, new LinearLayout.LayoutParams(0, dp(50), 1));
        }
        Button cancel = actionButton("取消", false);
        cancel.setOnClickListener(v -> dialog.dismiss());
        LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(0, dp(50), 1);
        cancelParams.setMarginStart(dp(8));
        actions.addView(cancel, cancelParams);

        Button save = actionButton("保存", true);
        save.setOnClickListener(v -> save());
        LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(0, dp(50), 1.2f);
        saveParams.setMarginStart(dp(8));
        actions.addView(save, saveParams);
        content.addView(actions, matchWrap(0));
        dialog.setContentView(scroll);
    }

    private void save() {
        String title = titleInput.getText().toString().trim();
        if (title.isEmpty()) {
            titleInput.setError("请填写标题");
            titleInput.requestFocus();
            return;
        }
        draft.title = title;
        draft.content = contentInput.getText().toString().trim();
        draft.pinned = pinned.isChecked();
        draft.linkedEventId = linkedEventIds.get(linkedEventSpinner.getSelectedItemPosition());
        listener.onSave(draft);
        dialog.dismiss();
    }

    private void confirmDelete() {
        new AlertDialog.Builder(activity)
                .setTitle("删除备忘？")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (ignored, which) -> {
                    listener.onDelete(draft);
                    dialog.dismiss();
                })
                .show();
    }

    private EditText input(boolean multiline) {
        EditText input = new EditText(activity);
        input.setTextSize(16);
        input.setTextColor(UiUtils.TEXT);
        input.setSingleLine(!multiline);
        input.setGravity(multiline ? Gravity.TOP : Gravity.CENTER_VERTICAL);
        input.setPadding(dp(14), dp(multiline ? 11 : 4), dp(14), dp(multiline ? 11 : 4));
        input.setBackground(UiUtils.outlined(UiUtils.SURFACE, UiUtils.BORDER, 12, activity));
        return input;
    }

    private void addLabel(LinearLayout parent, String value) {
        parent.addView(UiUtils.text(activity, value, 13,
                UiUtils.TEXT_SECONDARY, true), matchWrap(6));
    }

    private Button actionButton(String value, boolean primary) {
        Button button = new Button(activity);
        button.setText(value);
        button.setTextSize(15);
        button.setAllCaps(false);
        button.setTextColor(primary ? Color.WHITE : UiUtils.TEXT);
        button.setBackground(primary
                ? UiUtils.rounded(UiUtils.PRIMARY, 14, activity)
                : UiUtils.outlined(UiUtils.SURFACE, UiUtils.BORDER, 14, activity));
        return button;
    }

    private LinearLayout horizontal() {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        return row;
    }

    private LinearLayout.LayoutParams matchWrap(int bottomDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(bottomDp);
        return params;
    }

    private LinearLayout.LayoutParams matchFixed(int heightDp, int bottomDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(heightDp));
        params.bottomMargin = dp(bottomDp);
        return params;
    }

    private int dp(int value) {
        return UiUtils.dp(activity, value);
    }

    private static Memo copyOf(Memo source) {
        Memo result = new Memo();
        if (source == null) return result;
        result.id = source.id;
        result.title = source.title;
        result.content = source.content;
        result.pinned = source.pinned;
        result.completed = source.completed;
        result.archived = source.archived;
        result.linkedEventId = source.linkedEventId;
        result.createdAt = source.createdAt;
        result.updatedAt = source.updatedAt;
        return result;
    }
}
