package com.sherry.schedule.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

import com.sherry.schedule.data.ScheduleEvent;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class WeekTimelineView extends View {
    public interface Listener {
        void onEventClick(ScheduleEvent event);
    }

    private static final int HOURS = 24;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<HitRegion> hitRegions = new ArrayList<>();
    private final Map<LocalDate, List<ScheduleEvent>> eventsByDate = new HashMap<>();
    private LocalDate weekStart = LocalDate.now();
    private LocalDate selectedDate = LocalDate.now();
    private Listener listener;
    private float downX;
    private float downY;

    public WeekTimelineView(Context context) {
        super(context);
        setBackgroundColor(Color.WHITE);
        textPaint.setTypeface(android.graphics.Typeface.create(
                android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL));
        setMinimumHeight(dp(24 * 56 + 66));
    }

    public void setData(
            LocalDate weekStart,
            LocalDate selectedDate,
            Map<LocalDate, List<ScheduleEvent>> events,
            Listener listener) {
        this.weekStart = weekStart;
        this.selectedDate = selectedDate;
        this.listener = listener;
        eventsByDate.clear();
        eventsByDate.putAll(events);
        requestLayout();
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int desiredHeight = dp(24 * 56 + 66);
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), desiredHeight);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        hitRegions.clear();
        float timeWidth = dp(50);
        float headerHeight = dp(66);
        float hourHeight = dp(56);
        float dayWidth = (getWidth() - timeWidth) / 7f;

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(250, 251, 254));
        canvas.drawRect(0, 0, getWidth(), headerHeight, paint);

        LocalDate today = LocalDate.now();
        DateTimeFormatter weekday = DateTimeFormatter.ofPattern("E", Locale.CHINA);
        for (int day = 0; day < 7; day++) {
            LocalDate date = weekStart.plusDays(day);
            float left = timeWidth + day * dayWidth;
            float center = left + dayWidth / 2f;

            if (date.equals(selectedDate)) {
                paint.setColor(0x15536DFE);
                canvas.drawRoundRect(new RectF(left + dp(3), dp(4),
                        left + dayWidth - dp(3), headerHeight - dp(5)),
                        dp(11), dp(11), paint);
            }
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setTextSize(sp(11));
            textPaint.setColor(UiUtils.TEXT_SECONDARY);
            canvas.drawText(date.format(weekday), center, dp(20), textPaint);

            textPaint.setTextSize(sp(18));
            textPaint.setFakeBoldText(date.equals(today) || date.equals(selectedDate));
            textPaint.setColor(date.equals(today) ? UiUtils.PRIMARY : UiUtils.TEXT);
            canvas.drawText(String.valueOf(date.getDayOfMonth()), center, dp(45), textPaint);
            textPaint.setFakeBoldText(false);

            List<ScheduleEvent> dayEvents = eventsByDate.get(date);
            if (dayEvents != null && !dayEvents.isEmpty()) {
                paint.setColor(UiUtils.PRIMARY);
                canvas.drawCircle(center, dp(56), dp(2.2f), paint);
            }
        }

        paint.setStrokeWidth(dp(1));
        for (int hour = 0; hour <= HOURS; hour++) {
            float y = headerHeight + hour * hourHeight;
            paint.setColor(hour % 6 == 0
                    ? Color.rgb(218, 222, 232) : Color.rgb(232, 235, 242));
            canvas.drawLine(timeWidth, y, getWidth(), y, paint);
            if (hour < HOURS) {
                textPaint.setTextAlign(Paint.Align.RIGHT);
                textPaint.setTextSize(sp(10));
                textPaint.setColor(UiUtils.TEXT_SECONDARY);
                canvas.drawText(String.format(Locale.CHINA, "%02d:00", hour),
                        timeWidth - dp(6), y + dp(4), textPaint);
            }
        }
        for (int day = 0; day <= 7; day++) {
            float x = timeWidth + day * dayWidth;
            paint.setColor(Color.rgb(232, 235, 242));
            canvas.drawLine(x, 0, x, getHeight(), paint);
        }

        for (int day = 0; day < 7; day++) {
            LocalDate date = weekStart.plusDays(day);
            List<ScheduleEvent> events = eventsByDate.get(date);
            if (events == null) continue;
            for (int index = 0; index < events.size(); index++) {
                ScheduleEvent event = events.get(index);
                int end = event.endMinutes >= 0
                        ? event.endMinutes : Math.min(24 * 60, event.startMinutes + 30);
                boolean conflict = conflicts(event, events);
                int lane = conflictLane(event, events, index);
                int laneCount = conflict ? 2 : 1;
                float columnLeft = timeWidth + day * dayWidth;
                float usable = dayWidth - dp(4);
                float laneWidth = usable / laneCount;
                float left = columnLeft + dp(2) + Math.min(lane, 1) * laneWidth;
                float right = left + laneWidth - dp(2);
                float top = headerHeight + event.startMinutes / 60f * hourHeight + dp(1);
                float bottom = headerHeight + end / 60f * hourHeight - dp(1);
                bottom = Math.max(bottom, top + dp(22));
                RectF rect = new RectF(left, top, right, bottom);

                paint.setStyle(Paint.Style.FILL);
                paint.setColor(event.completed ? 0xFFE7E9EF
                        : event.anniversary ? 0xFFFFE5DF : 0xFFDDE4FF);
                canvas.drawRoundRect(rect, dp(6), dp(6), paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(dp(conflict ? 2 : 1));
                paint.setColor(conflict ? UiUtils.DANGER
                        : event.anniversary ? UiUtils.ANNIVERSARY : UiUtils.PRIMARY);
                canvas.drawRoundRect(rect, dp(6), dp(6), paint);
                paint.setStyle(Paint.Style.FILL);

                canvas.save();
                canvas.clipRect(rect);
                textPaint.setTextAlign(Paint.Align.LEFT);
                textPaint.setFakeBoldText(true);
                textPaint.setTextSize(sp(10));
                textPaint.setColor(event.completed ? UiUtils.TEXT_SECONDARY : UiUtils.TEXT);
                canvas.drawText(event.title, rect.left + dp(4), rect.top + dp(13), textPaint);
                textPaint.setFakeBoldText(false);
                textPaint.setTextSize(sp(8));
                canvas.drawText(event.timeText(), rect.left + dp(4), rect.top + dp(25), textPaint);
                if (conflict && rect.height() > dp(38)) {
                    textPaint.setColor(UiUtils.DANGER);
                    canvas.drawText("时间冲突", rect.left + dp(4), rect.top + dp(37), textPaint);
                }
                canvas.restore();
                hitRegions.add(new HitRegion(rect, event));
            }
        }

        float nowY = headerHeight
                + (java.time.LocalTime.now().getHour() * 60
                + java.time.LocalTime.now().getMinute()) / 60f * hourHeight;
        if (!today.isBefore(weekStart) && !today.isAfter(weekStart.plusDays(6))) {
            int day = today.getDayOfWeek().getValue() - 1;
            float left = timeWidth + day * dayWidth;
            paint.setColor(Color.rgb(236, 77, 77));
            paint.setStrokeWidth(dp(1.5f));
            canvas.drawLine(left, nowY, left + dayWidth, nowY, paint);
            canvas.drawCircle(left, nowY, dp(3), paint);
        }
    }

    private boolean conflicts(ScheduleEvent event, List<ScheduleEvent> events) {
        int eventEnd = event.endMinutes >= 0 ? event.endMinutes : event.startMinutes + 30;
        for (ScheduleEvent other : events) {
            if (other.id == event.id) continue;
            int otherEnd = other.endMinutes >= 0 ? other.endMinutes : other.startMinutes + 30;
            if (event.startMinutes < otherEnd && other.startMinutes < eventEnd) return true;
        }
        return false;
    }

    private int conflictLane(ScheduleEvent event, List<ScheduleEvent> events, int eventIndex) {
        int eventEnd = event.endMinutes >= 0 ? event.endMinutes : event.startMinutes + 30;
        for (int i = 0; i < eventIndex; i++) {
            ScheduleEvent earlier = events.get(i);
            int earlierEnd = earlier.endMinutes >= 0
                    ? earlier.endMinutes : earlier.startMinutes + 30;
            if (event.startMinutes < earlierEnd && earlier.startMinutes < eventEnd) return 1;
        }
        return 0;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            downX = event.getX();
            downY = event.getY();
            return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_UP) {
            if (Math.abs(event.getX() - downX) < dp(8)
                    && Math.abs(event.getY() - downY) < dp(8)) {
                for (int i = hitRegions.size() - 1; i >= 0; i--) {
                    HitRegion region = hitRegions.get(i);
                    if (region.rect.contains(event.getX(), event.getY())) {
                        if (listener != null) listener.onEventClick(region.event);
                        performClick();
                        return true;
                    }
                }
            }
            performClick();
            return true;
        }
        return true;
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    private int dp(float value) {
        return UiUtils.dp(getContext(), value);
    }

    private float sp(float value) {
        return value * getResources().getDisplayMetrics().scaledDensity;
    }

    private static final class HitRegion {
        final RectF rect;
        final ScheduleEvent event;

        HitRegion(RectF rect, ScheduleEvent event) {
            this.rect = new RectF(rect);
            this.event = event;
        }
    }
}
