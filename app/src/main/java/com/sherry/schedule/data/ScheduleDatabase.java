package com.sherry.schedule.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


public final class ScheduleDatabase extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "schedule.db";
    private static final int DATABASE_VERSION = 5;
    private static volatile ScheduleDatabase instance;

    public static ScheduleDatabase get(Context context) {
        if (instance == null) {
            synchronized (ScheduleDatabase.class) {
                if (instance == null) {
                    instance = new ScheduleDatabase(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    private ScheduleDatabase(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE events (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "title TEXT NOT NULL," +
                "notes TEXT NOT NULL DEFAULT ''," +
                "event_date TEXT NOT NULL," +
                "start_minutes INTEGER NOT NULL," +
                "end_minutes INTEGER NOT NULL DEFAULT -1," +
                "reminder_minutes INTEGER NOT NULL DEFAULT 10," +
                "alert_mode TEXT NOT NULL DEFAULT 'SOUND'," +
                "ringtone_uri TEXT NOT NULL DEFAULT ''," +
                "is_anniversary INTEGER NOT NULL DEFAULT 0," +
                "habit_frequency TEXT NOT NULL DEFAULT 'NONE'," +
                "enabled INTEGER NOT NULL DEFAULT 1," +
                "is_completed INTEGER NOT NULL DEFAULT 0," +
                "created_at INTEGER NOT NULL" +
                ")");
        db.execSQL("CREATE INDEX idx_events_date ON events(event_date)");
        db.execSQL("CREATE INDEX idx_events_enabled ON events(enabled)");
        createMemoTable(db);
        createPrivateTables(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE events ADD COLUMN is_completed INTEGER NOT NULL DEFAULT 0");
        }
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE events ADD COLUMN habit_frequency TEXT NOT NULL DEFAULT 'NONE'");
        }
        if (oldVersion < 4) createMemoTable(db);
        if (oldVersion < 5) createPrivateTables(db);
    }

    public synchronized long save(ScheduleEvent event) {
        ContentValues values = toValues(event);
        if (event.id == 0) {
            event.id = getWritableDatabase().insertOrThrow("events", null, values);
        } else {
            getWritableDatabase().update(
                    "events", values, "id = ?", new String[]{String.valueOf(event.id)});
        }
        return event.id;
    }

    public synchronized void delete(long id) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues unlink = new ContentValues();
        unlink.put("linked_event_id", -1L);
        db.update("memos", unlink, "linked_event_id = ?", new String[]{String.valueOf(id)});
        db.delete("events", "id = ?", new String[]{String.valueOf(id)});
    }

    public synchronized ScheduleEvent getById(long id) {
        try (Cursor cursor = getReadableDatabase().query(
                "events", null, "id = ?", new String[]{String.valueOf(id)},
                null, null, null)) {
            if (cursor.moveToFirst()) return fromCursor(cursor);
        }
        return null;
    }

    public synchronized List<ScheduleEvent> eventsForDate(LocalDate selectedDate) {
        List<ScheduleEvent> result = new ArrayList<>();
        for (ScheduleEvent event : allEvents(true)) {
            if (occursOn(event, selectedDate)) result.add(event);
        }
        result.sort((left, right) -> {
            int time = Integer.compare(left.startMinutes, right.startMinutes);
            return time != 0 ? time : left.title.compareToIgnoreCase(right.title);
        });
        return result;
    }

    public synchronized Set<Integer> eventDaysForMonth(YearMonth month) {
        Set<Integer> result = new HashSet<>();
        for (ScheduleEvent event : allEvents(true)) {
            for (int day = 1; day <= month.lengthOfMonth(); day++) {
                LocalDate date = month.atDay(day);
                if (occursOn(event, date)) result.add(day);
            }
        }
        return result;
    }

    public synchronized List<ScheduleEvent> allActiveEvents() {
        return allEvents(true);
    }

    public synchronized List<ScheduleEvent> allEvents() {
        return allEvents(false);
    }

    public synchronized void setCompleted(long id, boolean completed) {
        ContentValues values = new ContentValues();
        values.put("is_completed", completed ? 1 : 0);
        getWritableDatabase().update(
                "events", values, "id = ?", new String[]{String.valueOf(id)});
    }

    public synchronized long saveMemo(Memo memo) {
        long now = System.currentTimeMillis();
        if (memo.createdAt == 0L) memo.createdAt = now;
        memo.updatedAt = now;
        ContentValues values = memoValues(memo);
        if (memo.id == 0L) {
            memo.id = getWritableDatabase().insertOrThrow("memos", null, values);
        } else {
            getWritableDatabase().update(
                    "memos", values, "id = ?", new String[]{String.valueOf(memo.id)});
        }
        return memo.id;
    }

    public synchronized Memo getMemoById(long id) {
        try (Cursor cursor = getReadableDatabase().query(
                "memos", null, "id = ?", new String[]{String.valueOf(id)},
                null, null, null)) {
            if (cursor.moveToFirst()) return memoFromCursor(cursor);
        }
        return null;
    }

    public synchronized List<Memo> allMemos(boolean archived) {
        List<Memo> result = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query(
                "memos", null, "is_archived = ?",
                new String[]{archived ? "1" : "0"}, null, null,
                "is_pinned DESC, updated_at DESC")) {
            while (cursor.moveToNext()) result.add(memoFromCursor(cursor));
        }
        return result;
    }

    public synchronized void setMemoCompleted(long id, boolean completed) {
        updateMemoFlag(id, "is_completed", completed);
    }

    public synchronized void setMemoPinned(long id, boolean pinned) {
        updateMemoFlag(id, "is_pinned", pinned);
    }

    public synchronized void setMemoArchived(long id, boolean archived) {
        updateMemoFlag(id, "is_archived", archived);
    }

    public synchronized void deleteMemo(long id) {
        getWritableDatabase().delete("memos", "id = ?", new String[]{String.valueOf(id)});
    }

    private void updateMemoFlag(long id, String column, boolean enabled) {
        ContentValues values = new ContentValues();
        values.put(column, enabled ? 1 : 0);
        values.put("updated_at", System.currentTimeMillis());
        getWritableDatabase().update(
                "memos", values, "id = ?", new String[]{String.valueOf(id)});
    }

    private List<ScheduleEvent> allEvents(boolean onlyEnabled) {
        List<ScheduleEvent> result = new ArrayList<>();
        String selection = onlyEnabled ? "enabled = 1" : null;
        try (Cursor cursor = getReadableDatabase().query(
                "events", null, selection, null, null, null,
                "event_date ASC, start_minutes ASC")) {
            while (cursor.moveToNext()) result.add(fromCursor(cursor));
        }
        return result;
    }

    private static ContentValues toValues(ScheduleEvent event) {
        ContentValues values = new ContentValues();
        values.put("title", event.title.trim());
        values.put("notes", event.notes == null ? "" : event.notes.trim());
        values.put("event_date", event.date.toString());
        values.put("start_minutes", event.startMinutes);
        values.put("end_minutes", event.endMinutes);
        values.put("reminder_minutes", event.reminderMinutes);
        values.put("alert_mode", event.alertMode.name());
        values.put("ringtone_uri", event.ringtoneUri == null ? "" : event.ringtoneUri);
        values.put("is_anniversary", event.anniversary ? 1 : 0);
        values.put("habit_frequency", event.habitFrequency.name());
        values.put("enabled", event.enabled ? 1 : 0);
        values.put("is_completed", event.completed ? 1 : 0);
        values.put("created_at", System.currentTimeMillis());
        return values;
    }

    private static ContentValues memoValues(Memo memo) {
        ContentValues values = new ContentValues();
        values.put("title", memo.title.trim());
        values.put("content", memo.content == null ? "" : memo.content.trim());
        values.put("is_pinned", memo.pinned ? 1 : 0);
        values.put("is_completed", memo.completed ? 1 : 0);
        values.put("is_archived", memo.archived ? 1 : 0);
        values.put("linked_event_id", memo.linkedEventId);
        values.put("created_at", memo.createdAt);
        values.put("updated_at", memo.updatedAt);
        return values;
    }

    private static ScheduleEvent fromCursor(Cursor cursor) {
        ScheduleEvent event = new ScheduleEvent();
        event.id = cursor.getLong(cursor.getColumnIndexOrThrow("id"));
        event.title = cursor.getString(cursor.getColumnIndexOrThrow("title"));
        event.notes = cursor.getString(cursor.getColumnIndexOrThrow("notes"));
        event.date = LocalDate.parse(cursor.getString(cursor.getColumnIndexOrThrow("event_date")));
        event.startMinutes = cursor.getInt(cursor.getColumnIndexOrThrow("start_minutes"));
        event.endMinutes = cursor.getInt(cursor.getColumnIndexOrThrow("end_minutes"));
        event.reminderMinutes = cursor.getInt(cursor.getColumnIndexOrThrow("reminder_minutes"));
        String mode = cursor.getString(cursor.getColumnIndexOrThrow("alert_mode"));
        try {
            event.alertMode = ScheduleEvent.AlertMode.valueOf(mode);
        } catch (IllegalArgumentException ignored) {
            event.alertMode = ScheduleEvent.AlertMode.SOUND;
        }
        event.ringtoneUri = cursor.getString(cursor.getColumnIndexOrThrow("ringtone_uri"));
        event.anniversary = cursor.getInt(cursor.getColumnIndexOrThrow("is_anniversary")) == 1;
        int habitColumn = cursor.getColumnIndex("habit_frequency");
        if (habitColumn >= 0) {
            try {
                event.habitFrequency = ScheduleEvent.HabitFrequency.valueOf(
                        cursor.getString(habitColumn));
            } catch (IllegalArgumentException | NullPointerException ignored) {
                event.habitFrequency = ScheduleEvent.HabitFrequency.NONE;
            }
        }
        event.enabled = cursor.getInt(cursor.getColumnIndexOrThrow("enabled")) == 1;
        int completedColumn = cursor.getColumnIndex("is_completed");
        event.completed = completedColumn >= 0 && cursor.getInt(completedColumn) == 1;
        return event;
    }

    private static boolean occursOn(ScheduleEvent event, LocalDate selectedDate) {
        if (event.anniversary) {
            int anniversaryDay = Math.min(
                    event.date.getDayOfMonth(), YearMonth.from(selectedDate).lengthOfMonth());
            return event.date.getMonthValue() == selectedDate.getMonthValue()
                    && anniversaryDay == selectedDate.getDayOfMonth();
        }
        if (selectedDate.isBefore(event.date)) return false;
        if (event.habitFrequency == ScheduleEvent.HabitFrequency.DAILY) return true;
        if (event.habitFrequency == ScheduleEvent.HabitFrequency.WEEKLY) {
            return event.date.getDayOfWeek() == selectedDate.getDayOfWeek();
        }
        return event.date.equals(selectedDate);
    }

    private static void createMemoTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS memos (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "title TEXT NOT NULL," +
                "content TEXT NOT NULL DEFAULT ''," +
                "is_pinned INTEGER NOT NULL DEFAULT 0," +
                "is_completed INTEGER NOT NULL DEFAULT 0," +
                "is_archived INTEGER NOT NULL DEFAULT 0," +
                "linked_event_id INTEGER NOT NULL DEFAULT -1," +
                "created_at INTEGER NOT NULL," +
                "updated_at INTEGER NOT NULL" +
                ")");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_memos_archived " +
                "ON memos(is_archived, is_pinned, updated_at)");
    }

    private static Memo memoFromCursor(Cursor cursor) {
        Memo memo = new Memo();
        memo.id = cursor.getLong(cursor.getColumnIndexOrThrow("id"));
        memo.title = cursor.getString(cursor.getColumnIndexOrThrow("title"));
        memo.content = cursor.getString(cursor.getColumnIndexOrThrow("content"));
        memo.pinned = cursor.getInt(cursor.getColumnIndexOrThrow("is_pinned")) == 1;
        memo.completed = cursor.getInt(cursor.getColumnIndexOrThrow("is_completed")) == 1;
        memo.archived = cursor.getInt(cursor.getColumnIndexOrThrow("is_archived")) == 1;
        memo.linkedEventId = cursor.getLong(cursor.getColumnIndexOrThrow("linked_event_id"));
        memo.createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("created_at"));
        memo.updatedAt = cursor.getLong(cursor.getColumnIndexOrThrow("updated_at"));
        return memo;
    }

    public synchronized long saveDiary(PrivateDiary diary) {
        ContentValues values = new ContentValues();
        values.put("entry_date", diary.entryDate);
        values.put("encrypted_content", diary.encryptedContent);
        values.put("created_at", diary.createdAt == 0L ? System.currentTimeMillis() : diary.createdAt);
        values.put("updated_at", diary.updatedAt == 0L ? System.currentTimeMillis() : diary.updatedAt);
        if (diary.id == 0L) {
            diary.id = getWritableDatabase().insertOrThrow("diary_entries", null, values);
        } else {
            getWritableDatabase().update("diary_entries", values, "id = ?",
                    new String[]{String.valueOf(diary.id)});
        }
        return diary.id;
    }

    public synchronized List<PrivateDiary> allDiaries() {
        List<PrivateDiary> result = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query("diary_entries", null, null, null,
                null, null, "entry_date DESC, updated_at DESC")) {
            while (cursor.moveToNext()) result.add(diaryFromCursor(cursor));
        }
        return result;
    }

    public synchronized PrivateDiary getDiaryById(long id) {
        try (Cursor cursor = getReadableDatabase().query("diary_entries", null, "id = ?",
                new String[]{String.valueOf(id)}, null, null, null)) {
            if (cursor.moveToFirst()) return diaryFromCursor(cursor);
        }
        return null;
    }

    public synchronized void deleteDiary(long id) {
        getWritableDatabase().delete("diary_entries", "id = ?", new String[]{String.valueOf(id)});
    }

    public synchronized long saveCredential(PrivateCredential credential) {
        ContentValues values = new ContentValues();
        values.put("encrypted_name", credential.encryptedName);
        values.put("encrypted_account", credential.encryptedAccount);
        values.put("encrypted_password", credential.encryptedPassword);
        values.put("is_pinned", credential.pinned ? 1 : 0);
        values.put("created_at", credential.createdAt == 0L ? System.currentTimeMillis() : credential.createdAt);
        values.put("updated_at", credential.updatedAt == 0L ? System.currentTimeMillis() : credential.updatedAt);
        if (credential.id == 0L) {
            credential.id = getWritableDatabase().insertOrThrow("vault_entries", null, values);
        } else {
            getWritableDatabase().update("vault_entries", values, "id = ?",
                    new String[]{String.valueOf(credential.id)});
        }
        return credential.id;
    }

    public synchronized List<PrivateCredential> allCredentials() {
        List<PrivateCredential> result = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query("vault_entries", null, null, null,
                null, null, "is_pinned DESC, updated_at DESC")) {
            while (cursor.moveToNext()) result.add(credentialFromCursor(cursor));
        }
        return result;
    }

    public synchronized PrivateCredential getCredentialById(long id) {
        try (Cursor cursor = getReadableDatabase().query("vault_entries", null, "id = ?",
                new String[]{String.valueOf(id)}, null, null, null)) {
            if (cursor.moveToFirst()) return credentialFromCursor(cursor);
        }
        return null;
    }

    public synchronized void deleteCredential(long id) {
        getWritableDatabase().delete("vault_entries", "id = ?", new String[]{String.valueOf(id)});
    }

    private static PrivateDiary diaryFromCursor(Cursor cursor) {
        PrivateDiary diary = new PrivateDiary();
        diary.id = cursor.getLong(cursor.getColumnIndexOrThrow("id"));
        diary.entryDate = cursor.getString(cursor.getColumnIndexOrThrow("entry_date"));
        diary.encryptedContent = cursor.getString(cursor.getColumnIndexOrThrow("encrypted_content"));
        diary.createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("created_at"));
        diary.updatedAt = cursor.getLong(cursor.getColumnIndexOrThrow("updated_at"));
        return diary;
    }

    private static PrivateCredential credentialFromCursor(Cursor cursor) {
        PrivateCredential credential = new PrivateCredential();
        credential.id = cursor.getLong(cursor.getColumnIndexOrThrow("id"));
        credential.encryptedName = cursor.getString(cursor.getColumnIndexOrThrow("encrypted_name"));
        credential.encryptedAccount = cursor.getString(cursor.getColumnIndexOrThrow("encrypted_account"));
        credential.encryptedPassword = cursor.getString(cursor.getColumnIndexOrThrow("encrypted_password"));
        credential.pinned = cursor.getInt(cursor.getColumnIndexOrThrow("is_pinned")) == 1;
        credential.createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("created_at"));
        credential.updatedAt = cursor.getLong(cursor.getColumnIndexOrThrow("updated_at"));
        return credential;
    }

    private static void createPrivateTables(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS diary_entries (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "entry_date TEXT NOT NULL," +
                "encrypted_content TEXT NOT NULL," +
                "created_at INTEGER NOT NULL," +
                "updated_at INTEGER NOT NULL" +
                ")");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_diary_date ON diary_entries(entry_date, updated_at)");
        db.execSQL("CREATE TABLE IF NOT EXISTS vault_entries (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "encrypted_name TEXT NOT NULL," +
                "encrypted_account TEXT NOT NULL," +
                "encrypted_password TEXT NOT NULL," +
                "is_pinned INTEGER NOT NULL DEFAULT 0," +
                "created_at INTEGER NOT NULL," +
                "updated_at INTEGER NOT NULL" +
                ")");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_vault_pinned ON vault_entries(is_pinned, updated_at)");
    }
}
