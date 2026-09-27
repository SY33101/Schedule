package com.sherry.schedule.ui;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
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
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.sherry.schedule.data.ScheduleEvent;

import java.time.LocalDate;

public final class EventEditorDialog {
    public static final int RINGTONE_REQUEST_CODE = 4412;

    public interface Listener {
        void onSave(ScheduleEvent event);

        void onDelete(ScheduleEvent event);
    }

    private static final int[] REMINDER_VALUES = {0, 1, 5, 10, 15, 30, 60, 120, 24 * 60};
    private static final String[] REMINDER_LABELS = {
            "准时", "提前1分钟", "提前5分钟", "提前10分钟", "提前15分钟",
            "提前30分钟", "提前1小时", "提前2小时", "提前1天"
    };
    private static final String[] ALERT_LABELS = {"响铃和震动", "仅震动", "静默通知"};

    private final Activity activity;
    private final Listener listener;
    private final ScheduleEvent draft;
    private final Dialog dialog;
    private EditText titleInput;
    private EditText notesInput;
    private TextView dateButton;
    private TextView startButton;
    private TextView endButton;
    private CheckBox anniversary;
    private CheckBox habit;
    private RadioGroup habitFrequencyGroup;
    private RadioButton dailyHabit;
    private RadioButton weeklyHabit;
    private Spinner reminderSpinner;
    private Spinner alertSpinner;
    private TextView ringtoneButton;

    public EventEditorDialog(
            Activity activity, ScheduleEvent source, LocalDate defaultDate, Listener listener) {
        this.activity = activity;
        this.listener = listener;
        this.draft = copyOf(source, defaultDate);
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

    public void setOnDismissAction(Runnable action) {
        dialog.setOnDismissListener(ignored -> action.run());
    }

    public boolean handleRingtoneResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != RINGTONE_REQUEST_CODE) return false;
        if (resultCode == Activity.RESULT_OK && data != null) {
            Uri uri;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                uri = data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri.class);
            } else {
                //noinspection deprecation
                uri = data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
            }
            draft.ringtoneUri = uri == null ? "" : uri.toString();
            updateRingtoneLabel();
        }
        return true;
    }

    private void build() {
        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(true);
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(22), dp(20), dp(22), dp(16));
        content.setBackground(UiUtils.rounded(UiUtils.SURFACE, 24, activity));
        scroll.addView(content);

        TextView heading = UiUtils.text(activity,
                draft.id == 0 ? "添加日程" : "编辑日程", 23, UiUtils.TEXT, true);
        content.addView(heading, matchWrap(bottomMargin(18)));

        addLabel(content, "日程名称");
        titleInput = input(false, "");
        titleInput.setText(draft.title);
        content.addView(titleInput, matchWrap(bottomMargin(14)));

        addLabel(content, "日期");
        dateButton = choiceButton("");
        updateDateLabel();
        dateButton.setOnClickListener(v -> new DatePickerDialog(
                activity,
                (view, year, month, day) -> {
                    draft.date = LocalDate.of(year, month + 1, day);
                    updateDateLabel();
                },
                draft.date.getYear(),
                draft.date.getMonthValue() - 1,
                draft.date.getDayOfMonth()).show());
        content.addView(dateButton, matchWrap(bottomMargin(8)));

        anniversary = new CheckBox(activity);
        anniversary.setText("设为纪念日");
        anniversary.setTextColor(UiUtils.TEXT);
        anniversary.setTextSize(15);
        anniversary.setChecked(draft.anniversary);
        anniversary.setButtonTintList(android.content.res.ColorStateList.valueOf(UiUtils.PRIMARY));
        content.addView(anniversary, matchWrap(bottomMargin(2)));

        habit = new CheckBox(activity);
        habit.setText("设为习惯");
        habit.setTextColor(UiUtils.TEXT);
        habit.setTextSize(15);
        habit.setChecked(!draft.anniversary && draft.isHabit());
        habit.setButtonTintList(android.content.res.ColorStateList.valueOf(UiUtils.PRIMARY));
        content.addView(habit, matchWrap(bottomMargin(2)));

        habitFrequencyGroup = new RadioGroup(activity);
        habitFrequencyGroup.setOrientation(RadioGroup.HORIZONTAL);
        habitFrequencyGroup.setPadding(dp(18), 0, 0, 0);
        dailyHabit = habitChoice("每日");
        weeklyHabit = habitChoice("每周");
        habitFrequencyGroup.addView(dailyHabit,
                new RadioGroup.LayoutParams(0, dp(44), 1));
        habitFrequencyGroup.addView(weeklyHabit,
                new RadioGroup.LayoutParams(0, dp(44), 1));
        if (draft.habitFrequency == ScheduleEvent.HabitFrequency.DAILY) {
            habitFrequencyGroup.check(dailyHabit.getId());
        } else if (draft.habitFrequency == ScheduleEvent.HabitFrequency.WEEKLY) {
            habitFrequencyGroup.check(weeklyHabit.getId());
        }
        habitFrequencyGroup.setVisibility(habit.isChecked() ? View.VISIBLE : View.GONE);
        content.addView(habitFrequencyGroup, matchWrap(bottomMargin(12)));

        anniversary.setOnCheckedChangeListener((button, checked) -> {
            draft.anniversary = checked;
            if (checked && habit.isChecked()) habit.setChecked(false);
            updateDateLabel();
        });
        habit.setOnCheckedChangeListener((button, checked) -> {
            habitFrequencyGroup.setVisibility(checked ? View.VISIBLE : View.GONE);
            if (checked) {
                if (anniversary.isChecked()) anniversary.setChecked(false);
            } else {
                habitFrequencyGroup.clearCheck();
                draft.habitFrequency = ScheduleEvent.HabitFrequency.NONE;
            }
            updateDateLabel();
        });
        habitFrequencyGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == dailyHabit.getId()) {
                draft.habitFrequency = ScheduleEvent.HabitFrequency.DAILY;
            } else if (checkedId == weeklyHabit.getId()) {
                draft.habitFrequency = ScheduleEvent.HabitFrequency.WEEKLY;
            } else {
                draft.habitFrequency = ScheduleEvent.HabitFrequency.NONE;
            }
            updateDateLabel();
        });

        addLabel(content, "时间");
        LinearLayout timeRow = horizontal();
        startButton = choiceButton(ScheduleEvent.formatMinutes(draft.startMinutes));
        startButton.setOnClickListener(v -> showTimePicker(false));
        timeRow.addView(startButton, new LinearLayout.LayoutParams(0, dp(48), 1));

        TextView separator = UiUtils.text(activity, "至", 14, UiUtils.TEXT_SECONDARY, false);
        separator.setGravity(Gravity.CENTER);
        timeRow.addView(separator, new LinearLayout.LayoutParams(dp(34), dp(48)));

        endButton = choiceButton(draft.endMinutes >= 0
                ? ScheduleEvent.formatMinutes(draft.endMinutes) + "  ×" : "结束时间");
        endButton.setOnClickListener(v -> showTimePicker(true));
        endButton.setOnLongClickListener(v -> {
            clearEndTime();
            Toast.makeText(activity, "已清除结束时间", Toast.LENGTH_SHORT).show();
            return true;
        });
        timeRow.addView(endButton, new LinearLayout.LayoutParams(0, dp(48), 1));
        content.addView(timeRow, matchWrap(bottomMargin(14)));

        addLabel(content, "提前提醒");
        reminderSpinner = spinner(REMINDER_LABELS);
        reminderSpinner.setSelection(indexOfReminder(draft.reminderMinutes));
        content.addView(reminderSpinner, matchFixed(50, 14));

        addLabel(content, "提醒方式");
        alertSpinner = spinner(ALERT_LABELS);
        alertSpinner.setSelection(draft.alertMode.ordinal());
        content.addView(alertSpinner, matchFixed(50, 8));

        ringtoneButton = choiceButton("");
        updateRingtoneLabel();
        ringtoneButton.setVisibility(draft.alertMode == ScheduleEvent.AlertMode.SOUND
                ? View.VISIBLE : View.GONE);
        ringtoneButton.setOnClickListener(v -> pickRingtone());
        alertSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                ringtoneButton.setVisibility(position == 0 ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
            }
        });
        content.addView(ringtoneButton, matchWrap(bottomMargin(14)));

        addLabel(content, "备注");
        notesInput = input(true, "");
        notesInput.setText(draft.notes);
        content.addView(notesInput, matchFixed(86, 20));

        LinearLayout actions = horizontal();
        if (draft.id != 0) {
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
        content.addView(actions, matchWrap(bottomMargin(0)));

        dialog.setContentView(scroll);
    }

    private void save() {
        String title = titleInput.getText().toString().trim();
        if (title.isEmpty()) {
            titleInput.setError("请填写日程名称");
            titleInput.requestFocus();
            return;
        }
        if (draft.endMinutes >= 0 && draft.endMinutes <= draft.startMinutes) {
            Toast.makeText(activity, "结束时间需要晚于开始时间", Toast.LENGTH_SHORT).show();
            return;
        }
        draft.title = title;
        draft.notes = notesInput.getText().toString().trim();
        draft.anniversary = anniversary.isChecked();
        if (habit.isChecked()) {
            int checkedHabit = habitFrequencyGroup.getCheckedRadioButtonId();
            if (checkedHabit == dailyHabit.getId()) {
                draft.habitFrequency = ScheduleEvent.HabitFrequency.DAILY;
            } else if (checkedHabit == weeklyHabit.getId()) {
                draft.habitFrequency = ScheduleEvent.HabitFrequency.WEEKLY;
            } else {
                Toast.makeText(activity, "请选择每日或每周", Toast.LENGTH_SHORT).show();
                return;
            }
        } else {
            draft.habitFrequency = ScheduleEvent.HabitFrequency.NONE;
        }
        draft.reminderMinutes = REMINDER_VALUES[reminderSpinner.getSelectedItemPosition()];
        draft.alertMode = ScheduleEvent.AlertMode.values()[alertSpinner.getSelectedItemPosition()];
        listener.onSave(draft);
        dialog.dismiss();
    }

    private void confirmDelete() {
        new android.app.AlertDialog.Builder(activity)
                .setTitle("删除日程？")
                .setMessage("“" + draft.title + "”将被永久删除。")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (dialogInterface, which) -> {
                    listener.onDelete(draft);
                    dialog.dismiss();
                })
                .show();
    }

    private void showTimePicker(boolean end) {
        int value = end && draft.endMinutes >= 0 ? draft.endMinutes : draft.startMinutes;
        new TimePickerDialog(activity, (view, hour, minute) -> {
            int result = hour * 60 + minute;
            if (end) {
                draft.endMinutes = result;
                endButton.setText(ScheduleEvent.formatMinutes(result) + "  ×");
            } else {
                draft.startMinutes = result;
                startButton.setText(ScheduleEvent.formatMinutes(result));
                if (draft.endMinutes >= 0 && draft.endMinutes <= result) {
                    draft.endMinutes = Math.min(23 * 60 + 59, result + 60);
                    endButton.setText(ScheduleEvent.formatMinutes(draft.endMinutes) + "  ×");
                }
            }
        }, value / 60, value % 60, true).show();
    }

    private void clearEndTime() {
        draft.endMinutes = -1;
        endButton.setText("结束时间");
    }

    private void pickRingtone() {
        Intent picker = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true);
        if (!draft.ringtoneUri.isEmpty()) {
            picker.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                    Uri.parse(draft.ringtoneUri));
        }
        activity.startActivityForResult(picker, RINGTONE_REQUEST_CODE);
    }

    private void updateRingtoneLabel() {
        if (ringtoneButton == null) return;
        String label = "铃声：系统默认闹钟";
        if (!draft.ringtoneUri.isEmpty()) {
            try {
                android.media.Ringtone ringtone = RingtoneManager.getRingtone(
                        activity, Uri.parse(draft.ringtoneUri));
                if (ringtone != null) label = "铃声：" + ringtone.getTitle(activity);
            } catch (Exception ignored) {
                label = "铃声：已选择";
            }
        }
        ringtoneButton.setText(label);
    }

    private void updateDateLabel() {
        if (dateButton == null) return;
        if (draft.anniversary) {
            dateButton.setText(String.format(java.util.Locale.CHINA, "每年 %d月%d日",
                    draft.date.getMonthValue(), draft.date.getDayOfMonth()));
        } else if (draft.isHabit()) {
            dateButton.setText(String.format(java.util.Locale.CHINA, "从 %d年%d月%d日开始",
                    draft.date.getYear(), draft.date.getMonthValue(), draft.date.getDayOfMonth()));
        } else {
            dateButton.setText(String.format(java.util.Locale.CHINA, "%d年%d月%d日",
                    draft.date.getYear(), draft.date.getMonthValue(), draft.date.getDayOfMonth()));
        }
    }

    private void addLabel(LinearLayout parent, String value) {
        TextView label = UiUtils.text(activity, value, 13, UiUtils.TEXT_SECONDARY, true);
        parent.addView(label, matchWrap(bottomMargin(6)));
    }

    private EditText input(boolean multiline, String hint) {
        EditText input = new EditText(activity);
        input.setTextSize(16);
        input.setTextColor(UiUtils.TEXT);
        input.setHintTextColor(Color.rgb(160, 166, 178));
        if (!hint.isEmpty()) input.setHint(hint);
        input.setSingleLine(!multiline);
        input.setGravity(multiline ? Gravity.TOP : Gravity.CENTER_VERTICAL);
        input.setPadding(dp(14), dp(multiline ? 11 : 4), dp(14), dp(multiline ? 11 : 4));
        input.setBackground(UiUtils.outlined(UiUtils.SURFACE, UiUtils.BORDER, 12, activity));
        return input;
    }

    private TextView choiceButton(String value) {
        TextView button = UiUtils.text(activity, value, 15, UiUtils.TEXT, false);
        button.setGravity(Gravity.CENTER_VERTICAL);
        button.setPadding(dp(14), 0, dp(14), 0);
        button.setBackground(UiUtils.outlined(UiUtils.SURFACE, UiUtils.BORDER, 12, activity));
        button.setClickable(true);
        button.setFocusable(true);
        return button;
    }

    private Spinner spinner(String[] values) {
        Spinner spinner = new Spinner(activity);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                activity, android.R.layout.simple_spinner_dropdown_item, values) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                TextView view = (TextView) super.getView(position, convertView, parent);
                view.setTextColor(UiUtils.TEXT);
                view.setTextSize(15);
                view.setPadding(dp(14), 0, dp(10), 0);
                return view;
            }
        };
        spinner.setAdapter(adapter);
        spinner.setBackground(UiUtils.outlined(UiUtils.SURFACE, UiUtils.BORDER, 12, activity));
        return spinner;
    }

    private RadioButton habitChoice(String value) {
        RadioButton choice = new RadioButton(activity);
        choice.setId(View.generateViewId());
        choice.setText(value);
        choice.setTextSize(15);
        choice.setTextColor(UiUtils.TEXT);
        choice.setButtonTintList(android.content.res.ColorStateList.valueOf(UiUtils.PRIMARY));
        return choice;
    }

    private Button actionButton(String text, boolean primary) {
        Button button = new Button(activity);
        button.setText(text);
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

    private LinearLayout.LayoutParams matchWrap(int bottomMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = bottomMargin;
        return params;
    }

    private LinearLayout.LayoutParams matchFixed(int heightDp, int bottomDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(heightDp));
        params.bottomMargin = dp(bottomDp);
        return params;
    }

    private int bottomMargin(int dp) {
        return dp(dp);
    }

    private int dp(int value) {
        return UiUtils.dp(activity, value);
    }

    private static int indexOfReminder(int value) {
        for (int i = 0; i < REMINDER_VALUES.length; i++) {
            if (REMINDER_VALUES[i] == value) return i;
        }
        return 2;
    }

    private static ScheduleEvent copyOf(ScheduleEvent source, LocalDate defaultDate) {
        ScheduleEvent result = new ScheduleEvent();
        if (source == null) {
            result.date = defaultDate;
            return result;
        }
        result.id = source.id;
        result.title = source.title;
        result.notes = source.notes;
        result.date = source.date;
        result.startMinutes = source.startMinutes;
        result.endMinutes = source.endMinutes;
        result.reminderMinutes = source.reminderMinutes;
        result.alertMode = source.alertMode;
        result.ringtoneUri = source.ringtoneUri;
        result.anniversary = source.anniversary;
        result.habitFrequency = source.habitFrequency;
        result.enabled = source.enabled;
        result.completed = source.completed;
        return result;
    }
}
