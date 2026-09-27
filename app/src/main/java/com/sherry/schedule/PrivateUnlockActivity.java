package com.sherry.schedule;

import android.app.Activity;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.sherry.schedule.privatearea.PrivatePasswordManager;
import com.sherry.schedule.privatearea.PrivateSession;
import com.sherry.schedule.ui.UiUtils;

public final class PrivateUnlockActivity extends Activity {
    private TextView passwordView;
    private TextView titleView;
    private LinearLayout keyboardHost;
    private StringBuilder password = new StringBuilder();
    private String firstPassword;
    private boolean setupMode;
    private boolean letters;
    private ScaleGestureDetector scaleDetector;
    private float accumulatedScale = 1f;
    private boolean scaleToggled;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setupMode = !PrivatePasswordManager.isConfigured(this);
        buildContent();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (scaleDetector != null) scaleDetector.onTouchEvent(event);
        return super.dispatchTouchEvent(event);
    }

    @Override
    public void onBackPressed() {
        PrivateSession.lock();
        super.onBackPressed();
    }

    private void buildContent() {
        getWindow().setStatusBarColor(UiUtils.BACKGROUND);
        getWindow().setNavigationBarColor(UiUtils.BACKGROUND);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setBackgroundColor(UiUtils.BACKGROUND);
        root.setPadding(dp(28), dp(16), dp(28), dp(18));
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
            view.setPadding(dp(28), top + dp(18), dp(28), bottom + dp(18));
            return insets;
        });

        TextView brand = UiUtils.text(this, "Schedule", 25, UiUtils.TEXT, true);
        brand.setGravity(Gravity.CENTER);
        root.addView(brand, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(55)));
        titleView = UiUtils.text(this, setupMode ? "设置隐私密码" : "输入隐私密码",
                16, UiUtils.TEXT_SECONDARY, false);
        titleView.setGravity(Gravity.CENTER);
        root.addView(titleView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(36)));

        passwordView = UiUtils.text(this, "", 29, UiUtils.TEXT, true);
        passwordView.setGravity(Gravity.CENTER);
        passwordView.setLetterSpacing(0.14f);
        LinearLayout.LayoutParams displayParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(60));
        displayParams.topMargin = dp(12);
        root.addView(passwordView, displayParams);

        keyboardHost = new LinearLayout(this);
        keyboardHost.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams keyboardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1);
        keyboardParams.topMargin = dp(18);
        root.addView(keyboardHost, keyboardParams);

        Button action = new Button(this);
        action.setText(setupMode ? "继续" : "进入");
        action.setTextSize(16);
        action.setTextColor(Color.WHITE);
        action.setAllCaps(false);
        action.setBackground(UiUtils.rounded(UiUtils.PRIMARY, 16, this));
        action.setOnClickListener(v -> submit());
        root.addView(action, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));
        setContentView(root);
        applyBars();
        scaleDetector = new ScaleGestureDetector(this,
                new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    @Override public boolean onScaleBegin(ScaleGestureDetector detector) {
                        accumulatedScale = 1f;
                        scaleToggled = false;
                        return true;
                    }

                    @Override public boolean onScale(ScaleGestureDetector detector) {
                        accumulatedScale *= detector.getScaleFactor();
                        if (!scaleToggled && (accumulatedScale > 1.22f || accumulatedScale < 0.82f)) {
                            letters = !letters;
                            scaleToggled = true;
                            rebuildKeyboard();
                        }
                        return true;
                    }
                });
        rebuildKeyboard();
    }

    private void rebuildKeyboard() {
        keyboardHost.removeAllViews();
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(letters ? 4 : 3);
        grid.setRowCount(letters ? 7 : 4);
        grid.setUseDefaultMargins(false);
        if (letters) {
            for (char c = 'A'; c <= 'Z'; c++) addKey(grid, String.valueOf(c), 1f);
        } else {
            for (int i = 1; i <= 9; i++) addKey(grid, String.valueOf(i), 1f);
            addKeySpacer(grid);
            addKey(grid, "0", 1f);
            addKeySpacer(grid);
        }
        keyboardHost.addView(grid, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button delete = keyButton("删除");
        delete.setOnClickListener(v -> {
            if (password.length() > 0) password.deleteCharAt(password.length() - 1);
            updatePasswordDisplay();
        });
        actions.addView(delete, new LinearLayout.LayoutParams(0, dp(50), 1));
        Button clear = keyButton("清空");
        clear.setOnClickListener(v -> {
            password.setLength(0);
            updatePasswordDisplay();
        });
        actions.addView(clear, new LinearLayout.LayoutParams(0, dp(50), 1));
        keyboardHost.addView(actions);
        updatePasswordDisplay();
    }

    private void addKey(GridLayout grid, String value, float weight) {
        Button button = keyButton(value);
        button.setOnClickListener(v -> {
            if (password.length() < 64) password.append(value);
            updatePasswordDisplay();
        });
        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = 0;
        params.height = dp(50);
        params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1, weight);
        params.rowSpec = GridLayout.spec(GridLayout.UNDEFINED, 1);
        params.setMargins(dp(4), dp(4), dp(4), dp(4));
        grid.addView(button, params);
    }

    private void addKeySpacer(GridLayout grid) {
        View spacer = new View(this);
        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = 0;
        params.height = dp(50);
        params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1, 1f);
        params.rowSpec = GridLayout.spec(GridLayout.UNDEFINED, 1);
        params.setMargins(dp(4), dp(4), dp(4), dp(4));
        grid.addView(spacer, params);
    }

    private Button keyButton(String value) {
        Button button = new Button(this);
        button.setText(value);
        button.setTextSize(17);
        button.setTextColor(UiUtils.TEXT);
        button.setAllCaps(false);
        button.setBackground(UiUtils.rounded(Color.WHITE, 13, this));
        return button;
    }

    private void updatePasswordDisplay() {
        StringBuilder masked = new StringBuilder();
        for (int i = 0; i < password.length(); i++) masked.append('•');
        passwordView.setText(masked.toString());
    }

    private void submit() {
        if (password.length() < 4) {
            Toast.makeText(this, "密码至少需要4位", Toast.LENGTH_SHORT).show();
            return;
        }
        char[] chars = password.toString().toCharArray();
        try {
            if (setupMode) {
                if (firstPassword == null) {
                    firstPassword = password.toString();
                    password.setLength(0);
                    titleView.setText("再次输入隐私密码");
                    updatePasswordDisplay();
                } else if (firstPassword.equals(password.toString())) {
                    PrivatePasswordManager.setPassword(this, chars);
                    openPrivateSpace();
                } else {
                    firstPassword = null;
                    password.setLength(0);
                    titleView.setText("设置隐私密码");
                    updatePasswordDisplay();
                    Toast.makeText(this, "两次密码不一致", Toast.LENGTH_SHORT).show();
                }
            } else {
                long locked = PrivatePasswordManager.remainingLockMillis(this);
                if (locked > 0L) {
                    Toast.makeText(this, "请稍后再试", Toast.LENGTH_SHORT).show();
                } else if (PrivatePasswordManager.verify(this, chars)) {
                    openPrivateSpace();
                } else {
                    password.setLength(0);
                    updatePasswordDisplay();
                    Toast.makeText(this, "密码错误", Toast.LENGTH_SHORT).show();
                }
            }
        } catch (Exception failure) {
            Toast.makeText(this, "隐私空间暂时不可用", Toast.LENGTH_SHORT).show();
        } finally {
            java.util.Arrays.fill(chars, '\0');
        }
    }

    private void openPrivateSpace() {
        startActivity(new android.content.Intent(this, PrivateSpaceActivity.class));
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

    private int dp(int value) { return UiUtils.dp(this, value); }
}
