package com.sherry.schedule;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import com.sherry.schedule.alarm.AlarmScheduler;
import com.sherry.schedule.data.ScheduleDatabase;
import com.sherry.schedule.data.ScheduleEvent;
import com.sherry.schedule.ui.EventEditorDialog;

import java.time.LocalDate;
import java.time.LocalDateTime;

public final class QuickAddActivity extends Activity {
    private EventEditorDialog editor;
    private boolean completing;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        showQuickEditor();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (editor != null && editor.handleRingtoneResult(requestCode, resultCode, data)) return;
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void showQuickEditor() {
        LocalDateTime now = LocalDateTime.now();
        ScheduleEvent draft = new ScheduleEvent();
        draft.date = LocalDate.now();
        int minutes = now.getHour() * 60 + now.getMinute();
        draft.startMinutes = Math.min(23 * 60 + 55, ((minutes + 14) / 15) * 15);
        editor = new EventEditorDialog(this, draft, draft.date,
                new EventEditorDialog.Listener() {
                    @Override public void onSave(ScheduleEvent saved) {
                        ScheduleDatabase.get(QuickAddActivity.this).save(saved);
                        AlarmScheduler.schedule(QuickAddActivity.this, saved);
                        completing = true;
                        Toast.makeText(QuickAddActivity.this,
                                saved.anniversary ? "纪念日已保存" : "日程已保存",
                                Toast.LENGTH_SHORT).show();
                        finish();
                    }

                    @Override public void onDelete(ScheduleEvent ignored) { }
                });
        editor.setOnDismissAction(() -> {
            if (!completing && !isFinishing()) finish();
        });
        editor.show();
    }
}
