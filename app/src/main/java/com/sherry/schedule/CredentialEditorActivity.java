package com.sherry.schedule;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import com.sherry.schedule.data.PrivateCredential;
import com.sherry.schedule.data.ScheduleDatabase;
import com.sherry.schedule.privatearea.PrivateCrypto;
import com.sherry.schedule.privatearea.PrivateSession;
import com.sherry.schedule.ui.UiUtils;

public final class CredentialEditorActivity extends Activity {
    public static final String EXTRA_CREDENTIAL_ID = "credential_id";
    private ScheduleDatabase database;
    private PrivateCredential credential;
    private EditText nameInput;
    private EditText accountInput;
    private EditText passwordInput;
    private CheckBox pinBox;
    private boolean deleted;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!PrivateSession.isUnlocked()) { finish(); return; }
        database = ScheduleDatabase.get(this);
        long id = getIntent().getLongExtra(EXTRA_CREDENTIAL_ID, -1L);
        credential = id < 0 ? new PrivateCredential() : database.getCredentialById(id);
        if (credential == null) credential = new PrivateCredential();
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
        TextView save = text("保存", 15, UiUtils.PRIMARY_DARK, true);
        save.setGravity(Gravity.CENTER);
        save.setOnClickListener(v -> saveAndFinish());
        header.addView(save, new LinearLayout.LayoutParams(dp(58), dp(52)));
        TextView more = text("⋮", 28, UiUtils.TEXT, true);
        more.setGravity(Gravity.CENTER);
        more.setOnClickListener(this::showMenu);
        header.addView(more, new LinearLayout.LayoutParams(dp(42), dp(52)));
        root.addView(header, matchWrap());

        nameInput = field("名称");
        accountInput = field("账号");
        passwordInput = field("密码");
        passwordInput.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        passwordInput.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
        passwordInput.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                saveAndFinish();
                return true;
            }
            return false;
        });
        try {
            nameInput.setText(PrivateCrypto.decrypt(credential.encryptedName));
            accountInput.setText(PrivateCrypto.decrypt(credential.encryptedAccount));
            passwordInput.setText(PrivateCrypto.decrypt(credential.encryptedPassword));
        } catch (Exception ignored) { }
        root.addView(nameInput, fieldParams());
        root.addView(accountInput, fieldParams());
        root.addView(passwordInput, fieldParams());
        pinBox = new CheckBox(this);
        pinBox.setText("置顶");
        pinBox.setTextSize(15);
        pinBox.setTextColor(UiUtils.TEXT);
        pinBox.setChecked(credential.pinned);
        root.addView(pinBox, matchWrap());
        setContentView(root);
        applyBars();
    }

    private EditText field(String hint) {
        EditText field = new EditText(this);
        field.setHint(hint);
        field.setHintTextColor(Color.rgb(184, 188, 198));
        field.setTextColor(UiUtils.TEXT);
        field.setTextSize(17);
        field.setSingleLine(true);
        field.setPadding(dp(3), 0, dp(3), 0);
        return field;
    }

    private LinearLayout.LayoutParams fieldParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(60));
        params.bottomMargin = dp(4);
        return params;
    }

    private void showMenu(View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        if (credential.id != 0L) popup.getMenu().add(0, 1, 0, "删除");
        popup.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) confirmDelete();
            return true;
        });
        popup.show();
    }

    private void confirmDelete() {
        new AlertDialog.Builder(this).setTitle("删除密码条目？")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (dialog, which) -> {
                    database.deleteCredential(credential.id);
                    deleted = true;
                    finish();
                }).show();
    }

    private void saveAndFinish() {
        if (deleted) {
            finish();
            return;
        }
        if (!PrivateSession.isUnlocked()) {
            Toast.makeText(this, "隐私空间已锁定，请重新进入", Toast.LENGTH_SHORT).show();
            return;
        }
        String name = nameInput.getText().toString().trim();
        String account = accountInput.getText().toString().trim();
        String password = passwordInput.getText().toString();
        if (name.isEmpty() && account.isEmpty() && password.isEmpty()
                && credential.id == 0L) {
            finish();
            return;
        }
        if (name.isEmpty() || account.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "名称、账号、密码都需要填写", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            credential.encryptedName = PrivateCrypto.encrypt(name);
            credential.encryptedAccount = PrivateCrypto.encrypt(account);
            credential.encryptedPassword = PrivateCrypto.encrypt(password);
            credential.pinned = pinBox.isChecked();
            long now = System.currentTimeMillis();
            if (credential.createdAt == 0L) credential.createdAt = now;
            credential.updatedAt = now;
            database.saveCredential(credential);
            Toast.makeText(this, "已保存", Toast.LENGTH_SHORT).show();
        } catch (Exception failure) {
            Toast.makeText(this, "密码保存失败", Toast.LENGTH_SHORT).show();
            return;
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
        return UiUtils.text(this, value, size, color, bold);
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) { return UiUtils.dp(this, value); }
}
