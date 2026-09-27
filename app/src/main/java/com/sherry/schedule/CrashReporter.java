package com.sherry.schedule;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDateTime;

final class CrashReporter {
    private static final String PREFS = "crash_diagnostics";
    private static final String KEY_REPORT = "pending_report";

    private CrashReporter() {
    }

    static void install(Context context) {
        Context appContext = context.getApplicationContext();
        Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            record(appContext, throwable, "未捕获异常 · " + thread.getName());
            if (previous != null) {
                previous.uncaughtException(thread, throwable);
            } else {
                android.os.Process.killProcess(android.os.Process.myPid());
                System.exit(10);
            }
        });
    }

    static void record(Context context, Throwable throwable, String stage) {
        try {
            StringWriter stack = new StringWriter();
            throwable.printStackTrace(new PrintWriter(stack));
            String report = "Schedule 启动诊断\n"
                    + "阶段：" + stage + "\n"
                    + "时间：" + LocalDateTime.now() + "\n"
                    + "Android：" + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")\n"
                    + "设备：" + Build.MANUFACTURER + " " + Build.MODEL + "\n\n"
                    + stack;
            preferences(context).edit().putString(KEY_REPORT, report).commit();
        } catch (Throwable ignored) {
            // The crash reporter must never hide the original failure.
        }
    }

    static String consume(Context context) {
        SharedPreferences preferences = preferences(context);
        String report = preferences.getString(KEY_REPORT, null);
        if (report != null) preferences.edit().remove(KEY_REPORT).commit();
        return report;
    }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}

