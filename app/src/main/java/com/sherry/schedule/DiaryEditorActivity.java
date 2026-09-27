package com.sherry.schedule;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import com.sherry.schedule.data.PrivateDiary;
import com.sherry.schedule.data.ScheduleDatabase;
import com.sherry.schedule.privatearea.PrivateCrypto;
import com.sherry.schedule.privatearea.PrivateSession;
import com.sherry.schedule.ui.UiUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class DiaryEditorActivity extends Activity {
    public static final String EXTRA_DIARY_ID = "diary_id";
    private ScheduleDatabase database;
    private PrivateDiary diary;
    private EditText contentInput;
    private TextView dateButton;
    private LocalDate entryDate;
    private boolean deleted;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!PrivateSession.isUnlocked()) { finish(); return; }
        database = ScheduleDatabase.get(this);
        long id = getIntent().getLongExtra(EXTRA_DIARY_ID, -1L);
        diary = id < 0 ? new PrivateDiary() : database.getDiaryById(id);
        if (diary == null) diary = new PrivateDiary();
        try {
            entryDate = diary.entryDate.isEmpty() ? LocalDate.now() : LocalDate.parse(diary.entryDate);
        } catch (Exception ignored) { entryDate = LocalDate.now(); }
        buildContent();
    }

    @Override public void onBackPressed() { saveAndFinish(); }

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
                top = bars.top; bottom = bars.bottom;
            } else {
                top = insets.getSystemWindowInsetTop(); bottom = insets.getSystemWindowInsetBottom();
            }
            view.setPadding(dp(20), top + dp(8), dp(20), bottom + dp(12));
            return insets;
        });
        LinearLayout header = row();
        TextView back = text("‹", 34, UiUtils.TEXT, false);
        back.setGravity(Gravity.CENTER);
        back.setOnClickListener(v -> saveAndFinish());
        header.addView(back, new LinearLayout.LayoutParams(dp(48), dp(52)));
        TextView brand = text("Schedule", 17, UiUtils.TEXT, true);
        header.addView(brand, new LinearLayout.LayoutParams(0, dp(52), 1));
        TextView more = text("⋮", 28, UiUtils.TEXT, true);
        more.setGravity(Gravity.CENTER);
        more.setOnClickListener(this::showMenu);
        header.addView(more, new LinearLayout.LayoutParams(dp(42), dp(52)));
        root.addView(header, matchWrap());

        dateButton = text("", 15, UiUtils.TEXT_SECONDARY, false);
        dateButton.setPadding(0, dp(18), 0, dp(13));
        dateButton.setOnClickListener(v -> chooseDate());
        root.addView(dateButton, matchWrap());

        contentInput = new EditText(this);
        contentInput.setTextColor(UiUtils.TEXT);
        contentInput.setTextSize(18);
        contentInput.setGravity(Gravity.TOP);
        contentInput.setLineSpacing(dp(4), 1.08f);
        contentInput.setPadding(0, dp(10), 0, dp(12));
        contentInput.setBackgroundColor(Color.TRANSPARENT);
        contentInput.setSingleLine(false);
        try { contentInput.setText(PrivateCrypto.decrypt(diary.encryptedContent)); }
        catch (Exception ignored) { contentInput.setText(""); }
        root.addView(contentInput, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(root);
        updateDate();
        applyBars();
    }

    private void updateDate() {
        dateButton.setText(entryDate.format(
                DateTimeFormatter.ofPattern("yyyy年M月d日 EEEE", Locale.CHINA)));
    }

    private void chooseDate() {
        new DatePickerDialog(this, (view, year, month, day) -> {
            entryDate = LocalDate.of(year, month + 1, day);
            updateDate();
        }, entryDate.getYear(), entryDate.getMonthValue() - 1, entryDate.getDayOfMonth()).show();
    }

    private void showMenu(View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        if (diary.id != 0L) popup.getMenu().add(0, 1, 0, "删除");
        popup.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) confirmDelete();
            return true;
        });
        popup.show();
    }

    private void confirmDelete() {
        new AlertDialog.Builder(this).setTitle("删除日记？")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (dialog, which) -> {
                    database.deleteDiary(diary.id);
                    deleted = true;
                    finish();
                }).show();
    }

    private void saveAndFinish() {
        if (!deleted && PrivateSession.isUnlocked()) {
            String body = contentInput.getText().toString().trim();
            if (!body.isEmpty()) {
                try {
                    diary.entryDate = entryDate.toString();
                    diary.encryptedContent = PrivateCrypto.encrypt(body);
                    long now = System.currentTimeMillis();
                    if (diary.createdAt == 0L) diary.createdAt = now;
                    diary.updatedAt = now;
                    database.saveDiary(diary);
                } catch (Exception failure) {
                    Toast.makeText(this, "日记保存失败", Toast.LENGTH_SHORT).show();
                }
            }
        }
        finish();
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
        TextView view = UiUtils.text(this, value, size, color, bold);
        return view;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) { return UiUtils.dp(this, value); }
}
