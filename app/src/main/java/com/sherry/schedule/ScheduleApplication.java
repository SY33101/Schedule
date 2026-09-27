package com.sherry.schedule;

import android.app.Application;

public final class ScheduleApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        CrashReporter.install(this);
    }
}

