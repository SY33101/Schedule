package com.sherry.schedule;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.sherry.schedule.data.PrivateCredential;
import com.sherry.schedule.data.PrivateDiary;
import com.sherry.schedule.data.ScheduleDatabase;
import com.sherry.schedule.privatearea.PrivateCrypto;
import com.sherry.schedule.privatearea.PrivateSession;
import com.sherry.schedule.ui.UiUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class PrivateSpaceActivity extends Activity {
    private ScheduleDatabase database;
    private LinearLayout content;
    private EditText credentialSearch;
    private Button diaryTab;
    private Button credentialTab;
    private boolean showingCredentials;
    private LinearLayout selectionBar;
    private TextView selectionCount;
    private LinearLayout selectionActions;
    private final Set<Long> selectedItemIds = new HashSet<>();
    private boolean selectionMode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!PrivateSession.isUnlocked()) {
            finish();
            return;
        }
        database = ScheduleDatabase.get(this);
        buildContent();
        showDiary();
    }

    @Override
    public void onBackPressed() {
        if (selectionMode) {
            clearSelection();
            refreshCurrent();
            return;
        }
        PrivateSession.lock();
        super.onBackPressed();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (content != null && PrivateSession.isUnlocked()) refreshCurrent();
    }

    private void buildContent() {
        getWindow().setStatusBarColor(UiUtils.BACKGROUND);
        getWindow().setNavigationBarColor(UiUtils.BACKGROUND);
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
        TextView brand = UiUtils.text(this, "Schedule", 27, UiUtils.TEXT, true);
        header.addView(brand, new LinearLayout.LayoutParams(0, dp(52), 1));
        TextView lock = UiUtils.text(this, "锁定", 14, UiUtils.TEXT_SECONDARY, false);
        lock.setGravity(Gravity.CENTER);
        lock.setOnClickListener(v -> {
            PrivateSession.lock();
            finish();
        });
        header.addView(lock, new LinearLayout.LayoutParams(dp(52), dp(52)));
        root.addView(header, matchWrap());

        LinearLayout tabs = row();
        diaryTab = tabButton("日记");
        credentialTab = tabButton("密码");
        tabs.addView(diaryTab, new LinearLayout.LayoutParams(0, dp(46), 1));
        tabs.addView(credentialTab, new LinearLayout.LayoutParams(0, dp(46), 1));
        root.addView(tabs, matchWrap());

        selectionBar = row();
        selectionBar.setPadding(dp(12), dp(6), dp(6), dp(6));
        selectionBar.setBackground(UiUtils.rounded(0xFFE8ECFF, 14, this));
        selectionCount = UiUtils.text(this, "", 14, UiUtils.PRIMARY_DARK, true);
        selectionBar.addView(selectionCount, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        selectionActions = row();
        selectionBar.addView(selectionActions);
        selectionBar.setVisibility(View.GONE);
        LinearLayout.LayoutParams selectionParams = matchWrap();
        selectionParams.topMargin = dp(6);
        root.addView(selectionBar, selectionParams);

        credentialSearch = new EditText(this);
        credentialSearch.setSingleLine(true);
        credentialSearch.setTextSize(15);
        credentialSearch.setTextColor(UiUtils.TEXT);
        credentialSearch.setPadding(dp(13), 0, dp(13), 0);
        credentialSearch.setBackground(UiUtils.outlined(Color.WHITE, UiUtils.BORDER, 15, this));
        credentialSearch.setVisibility(View.GONE);
        credentialSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (showingCredentials) refreshCredentials();
            }
            @Override public void afterTextChanged(Editable s) { }
        });
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        searchParams.topMargin = dp(10);
        searchParams.bottomMargin = dp(4);
        root.addView(credentialSearch, searchParams);

        ScrollView scroll = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        Button add = new Button(this);
        add.setText("＋  新建");
        add.setTextSize(16);
        add.setTextColor(Color.WHITE);
        add.setAllCaps(false);
        add.setBackground(UiUtils.rounded(UiUtils.PRIMARY, 17, this));
        add.setOnClickListener(v -> {
            if (showingCredentials) startActivity(new android.content.Intent(
                    this, CredentialEditorActivity.class));
            else startActivity(new android.content.Intent(this, DiaryEditorActivity.class));
        });
        LinearLayout.LayoutParams addParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(54));
        addParams.topMargin = dp(8);
        root.addView(add, addParams);
        setContentView(root);
        diaryTab.setOnClickListener(v -> showDiary());
        credentialTab.setOnClickListener(v -> showCredentials());
        applyBars();
    }

    private Button tabButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(15);
        button.setTextColor(UiUtils.TEXT_SECONDARY);
        button.setAllCaps(false);
        button.setBackgroundColor(Color.TRANSPARENT);
        return button;
    }

    private void showDiary() {
        clearSelection();
        showingCredentials = false;
        credentialSearch.setVisibility(View.GONE);
        diaryTab.setTextColor(UiUtils.PRIMARY);
        credentialTab.setTextColor(UiUtils.TEXT_SECONDARY);
        refreshDiary();
    }

    private void showCredentials() {
        clearSelection();
        showingCredentials = true;
        credentialSearch.setVisibility(View.VISIBLE);
        diaryTab.setTextColor(UiUtils.TEXT_SECONDARY);
        credentialTab.setTextColor(UiUtils.PRIMARY);
        refreshCredentials();
    }

    private void refreshCurrent() {
        if (showingCredentials) refreshCredentials(); else refreshDiary();
    }

    private void refreshDiary() {
        content.removeAllViews();
        List<PrivateDiary> diaries = database.allDiaries();
        if (diaries.isEmpty()) {
            addEmpty("还没有日记");
            return;
        }
        String lastDate = null;
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy年M月d日 EEEE", Locale.CHINA);
        for (PrivateDiary diary : diaries) {
            if (!diary.entryDate.equals(lastDate)) {
                TextView date = UiUtils.text(this, formatDate(diary.entryDate, formatter),
                        15, UiUtils.TEXT_SECONDARY, true);
                date.setPadding(dp(5), dp(14), dp(5), dp(7));
                content.addView(date, matchWrap());
                lastDate = diary.entryDate;
            }
            String body;
            try {
                body = PrivateCrypto.decrypt(diary.encryptedContent);
            } catch (Exception failure) {
                body = "无法读取此条内容";
            }
            TextView card = UiUtils.text(this, body.isEmpty() ? "（空白日记）" : body,
                    16, UiUtils.TEXT, false);
            card.setMaxLines(4);
            card.setPadding(dp(14), dp(13), dp(14), dp(13));
            card.setBackground(selectedItemIds.contains(diary.id)
                    ? UiUtils.outlined(0xFFF0F2FF, UiUtils.PRIMARY, 16, this)
                    : UiUtils.rounded(Color.WHITE, 16, this));
            card.setOnClickListener(v -> {
                if (selectionMode) toggleSelection(diary.id);
                else openDiary(diary.id);
            });
            card.setOnLongClickListener(v -> {
                toggleSelection(diary.id);
                return true;
            });
            LinearLayout.LayoutParams params = matchWrap();
            params.bottomMargin = dp(8);
            content.addView(card, params);
        }
    }

    private void refreshCredentials() {
        content.removeAllViews();
        String query = credentialSearch == null ? "" : credentialSearch.getText().toString()
                .trim().toLowerCase(Locale.CHINA);
        List<PrivateCredential> credentials = database.allCredentials();
        int shown = 0;
        for (PrivateCredential credential : credentials) {
            String name;
            String account;
            try {
                name = PrivateCrypto.decrypt(credential.encryptedName);
                account = PrivateCrypto.decrypt(credential.encryptedAccount);
            } catch (Exception failure) {
                continue;
            }
            String haystack = (name + " " + account).toLowerCase(Locale.CHINA);
            if (!query.isEmpty() && !haystack.contains(query)) continue;
            shown++;
            LinearLayout card = row();
            card.setPadding(dp(14), dp(11), dp(8), dp(11));
            card.setBackground(selectedItemIds.contains(credential.id)
                    ? UiUtils.outlined(0xFFF0F2FF, UiUtils.PRIMARY, 16, this)
                    : UiUtils.rounded(Color.WHITE, 16, this));
            LinearLayout details = new LinearLayout(this);
            details.setOrientation(LinearLayout.VERTICAL);
            details.addView(UiUtils.text(this, name, 16, UiUtils.TEXT, true));
            details.addView(UiUtils.text(this, account, 13, UiUtils.TEXT_SECONDARY, false));
            card.addView(details, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            TextView pin = UiUtils.text(this, credential.pinned ? "★" : "☆", 23,
                    credential.pinned ? UiUtils.PRIMARY : UiUtils.TEXT_SECONDARY, false);
            pin.setGravity(Gravity.CENTER);
            pin.setOnClickListener(v -> {
                if (selectionMode) {
                    toggleSelection(credential.id);
                    return;
                }
                credential.pinned = !credential.pinned;
                try {
                    database.saveCredential(credential);
                    refreshCredentials();
                } catch (Exception failure) {
                    Toast.makeText(this, "保存失败", Toast.LENGTH_SHORT).show();
                }
            });
            card.addView(pin, new LinearLayout.LayoutParams(dp(42), dp(50)));
            card.setOnClickListener(v -> {
                if (selectionMode) toggleSelection(credential.id);
                else openCredential(credential.id);
            });
            card.setOnLongClickListener(v -> {
                toggleSelection(credential.id);
                return true;
            });
            LinearLayout.LayoutParams params = matchWrap();
            params.bottomMargin = dp(8);
            content.addView(card, params);
        }
        if (shown == 0) addEmpty(query.isEmpty() ? "还没有密码" : "没有匹配项");
    }

    private void addEmpty(String message) {
        TextView empty = UiUtils.text(this, message, 15, UiUtils.TEXT_SECONDARY, false);
        empty.setGravity(Gravity.CENTER);
        content.addView(empty, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(180)));
    }

    private void toggleSelection(long id) {
        if (!selectedItemIds.add(id)) selectedItemIds.remove(id);
        selectionMode = !selectedItemIds.isEmpty();
        refreshCurrent();
        updateSelectionBar();
    }

    private void clearSelection() {
        selectedItemIds.clear();
        selectionMode = false;
        updateSelectionBar();
    }

    private void updateSelectionBar() {
        if (selectionBar == null) return;
        selectionBar.setVisibility(selectionMode ? View.VISIBLE : View.GONE);
        if (!selectionMode) return;
        selectionCount.setText("已选择 " + selectedItemIds.size() + " 项");
        selectionActions.removeAllViews();
        if (showingCredentials) {
            selectionActions.addView(selectionAction("置顶", v -> pinSelectedCredentials()));
        }
        selectionActions.addView(selectionAction("删除", v -> confirmDeleteSelected()));
        selectionActions.addView(selectionAction("取消", v -> {
            clearSelection();
            refreshCurrent();
        }));
    }

    private TextView selectionAction(String label, View.OnClickListener listener) {
        TextView action = UiUtils.text(this, label, 14, UiUtils.PRIMARY_DARK, true);
        action.setGravity(Gravity.CENTER);
        action.setPadding(dp(9), dp(6), dp(9), dp(6));
        action.setOnClickListener(listener);
        return action;
    }

    private void pinSelectedCredentials() {
        for (PrivateCredential credential : database.allCredentials()) {
            if (selectedItemIds.contains(credential.id)) {
                credential.pinned = true;
                database.saveCredential(credential);
            }
        }
        clearSelection();
        refreshCredentials();
    }

    private void confirmDeleteSelected() {
        int count = selectedItemIds.size();
        new AlertDialog.Builder(this)
                .setTitle("删除所选的 " + count + " 项？")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (dialog, which) -> {
                    for (long id : new HashSet<>(selectedItemIds)) {
                        if (showingCredentials) database.deleteCredential(id);
                        else database.deleteDiary(id);
                    }
                    clearSelection();
                    refreshCurrent();
                })
                .show();
    }

    private void openDiary(long id) {
        android.content.Intent intent = new android.content.Intent(this, DiaryEditorActivity.class);
        intent.putExtra(DiaryEditorActivity.EXTRA_DIARY_ID, id);
        startActivity(intent);
    }

    private void openCredential(long id) {
        android.content.Intent intent = new android.content.Intent(this, CredentialEditorActivity.class);
        intent.putExtra(CredentialEditorActivity.EXTRA_CREDENTIAL_ID, id);
        startActivity(intent);
    }

    private String formatDate(String value, DateTimeFormatter formatter) {
        try { return LocalDate.parse(value).format(formatter); }
        catch (DateTimeParseException ignored) { return value; }
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

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) { return UiUtils.dp(this, value); }
}
