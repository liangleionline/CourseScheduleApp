package com.courseschedule.app.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import com.courseschedule.app.data.AppData;
import com.courseschedule.app.data.ColorUtil;
import com.courseschedule.app.data.Course;
import com.courseschedule.app.data.RenderedCell;
import com.courseschedule.app.data.TimetableEngine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 周课程表视图：横向星期、纵向时间，非课程项横向贯穿全周 */
public class TimetableView extends View {

    public interface Listener {
        void onCellClick(int day, int type, String refId);
    }

    private static final String[] DAY_NAMES = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};

    private AppData data;
    private Listener listener;
    private List<Integer> days = new ArrayList<>();
    private int minTime, maxTime;

    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cellPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint namePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint subPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint dayPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint timePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    private Map<Integer, List<RenderedCell>> cellCache = new HashMap<>();
    private Map<String, Course> courseById = new HashMap<>();

    private float pad, timeAxisW, headerH, colW, contentH;

    public TimetableView(Context c, AttributeSet a) { super(c, a); init(); }

    private void init() {
        float d = getResources().getDisplayMetrics().density;
        pad = 8 * d;
        timeAxisW = 42 * d;
        headerH = 44 * d;

        bgPaint.setColor(0xFFFFFFFF);
        linePaint.setColor(0xFFE6EAF0);
        linePaint.setStrokeWidth(1f * d);
        gridPaint.setColor(0xFFF2F4F8);
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(1f * d);

        namePaint.setTextSize(15 * d);
        namePaint.setFakeBoldText(true);
        namePaint.setTextAlign(Paint.Align.CENTER);
        subPaint.setTextSize(11 * d);
        subPaint.setTextAlign(Paint.Align.CENTER);
        dayPaint.setTextSize(15 * d);
        dayPaint.setFakeBoldText(true);
        dayPaint.setTextAlign(Paint.Align.CENTER);
        timePaint.setTextSize(10 * d);
        timePaint.setTextAlign(Paint.Align.CENTER);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
    }

    public void setListener(Listener l) { this.listener = l; }

    public void setData(AppData d) {
        this.data = d;
        days.clear();
        for (int i = 1; i <= 5; i++) days.add(i);
        if (data.showWeekend) { days.add(6); days.add(7); }
        courseById.clear();
        for (Course c : data.courses) courseById.put(c.id, c);
        cellCache.clear();
        for (int day : days) cellCache.put(day, TimetableEngine.computeDay(data, day));
        int[] r = TimetableEngine.globalTimeRange(data, days);
        minTime = r[0]; maxTime = Math.max(r[1], r[0] + 1);
        invalidate();
    }

    private float yOf(int minute) {
        return headerH + pad + (minute - minTime) * contentH / (float) (maxTime - minTime);
    }

    private String fmt(int m) {
        return String.format("%02d:%02d", m / 60, m % 60);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth(), h = getHeight();
        canvas.drawColor(0xFFF6F8FC);

        contentH = h - headerH - 2 * pad;
        if (contentH <= 0 || days.isEmpty()) return;
        colW = (w - timeAxisW - 2 * pad) / days.size();

        float dayAreaX = timeAxisW + pad;
        // 时间轴：只显示每个项目（课程/非课程）的开始时间，而非固定每小时
        List<Integer> startTimes = new java.util.ArrayList<>();
        for (int day : days) {
            for (RenderedCell cell : cellCache.get(day)) {
                int s = cell.startMin;
                if (!startTimes.contains(s)) startTimes.add(s);
            }
        }
        java.util.Collections.sort(startTimes);
        for (int m : startTimes) {
            float y = yOf(m);
            canvas.drawLine(dayAreaX, y, w - pad, y, linePaint);
            timePaint.setColor(0xFF8A94A6);
            canvas.drawText(fmt(m), timeAxisW / 2f, y - 3 * getResources().getDisplayMetrics().density, timePaint);
        }

        // 非课程贯穿全周条带 + 各天课程格
        for (int idx = 0; idx < days.size(); idx++) {
            int day = days.get(idx);
            float x = dayAreaX + idx * colW;
            for (RenderedCell cell : cellCache.get(day)) {
                float top = yOf(cell.startMin);
                float bot = yOf(cell.endMin);
                if (cell.type == TimetableEngine.TYPE_NONCOURSE) {
                    // 非课程带最小高度，避免过窄拥挤（在时间中点上下均衡扩展）
                    float minH = 26 * getResources().getDisplayMetrics().density;
                    float bandH = bot - top;
                    if (bandH < minH) {
                        float extra = minH - bandH;
                        top -= extra / 2f;
                        bot += extra / 2f;
                    }
                    rect.set(dayAreaX + 2, top + 1, w - pad - 2, bot - 1);
                    cellPaint.setColor(ColorUtil.NONCOURSE_BG);
                    canvas.drawRoundRect(rect, 8 * getResources().getDisplayMetrics().density,
                            8 * getResources().getDisplayMetrics().density, cellPaint);
                    drawCellText(canvas, rect, cellName(cell), "", ColorUtil.NONCOURSE_TEXT, true);
                } else {
                    Course c = courseById.get(cell.refId);
                    if (c == null) continue;
                    rect.set(x + 2, top + 1, x + colW - 2, bot - 1);
                    cellPaint.setColor(c.bgColor);
                    canvas.drawRoundRect(rect, 8 * getResources().getDisplayMetrics().density,
                            8 * getResources().getDisplayMetrics().density, cellPaint);
                    drawCellText(canvas, rect, c.name, c.teacher == null ? "" : c.teacher, c.textColor, false);
                }
            }
        }

        // 表头
        for (int idx = 0; idx < days.size(); idx++) {
            int day = days.get(idx);
            float x = dayAreaX + idx * colW;
            rect.set(x + 2, pad, x + colW - 2, pad + headerH);
            boolean today = isToday(day);
            cellPaint.setColor(today ? 0xFF5C6BC0 : 0xFFEDF1F8);
            canvas.drawRoundRect(rect, 10 * getResources().getDisplayMetrics().density,
                    10 * getResources().getDisplayMetrics().density, cellPaint);
            dayPaint.setColor(today ? Color.WHITE : 0xFF3A4151);
            String label = DAY_NAMES[day - 1];
            canvas.drawText(label, rect.centerX(), rect.centerY() + dayPaint.getTextSize() * 0.36f, dayPaint);
        }
    }

    private boolean isToday(int day) {
        java.util.Calendar c = java.util.Calendar.getInstance();
        int dow = c.get(java.util.Calendar.DAY_OF_WEEK); // 1=Sun..7=Sat
        int mon = dow == 1 ? 7 : dow - 1; // 转成 1=Mon..7=Sun
        return mon == day;
    }

    private String cellName(RenderedCell cell) {
        if (cell.type == TimetableEngine.TYPE_NONCOURSE) {
            var n = data.getNonCourse(cell.refId);
            return n == null ? "" : n.name;
        }
        Course c = courseById.get(cell.refId);
        return c == null ? "" : c.name;
    }

    private void drawCellText(Canvas canvas, RectF r, String name, String sub, int color, boolean isNon) {
        namePaint.setColor(color);
        subPaint.setColor(adjustAlpha(color, isNon ? 200 : 235));
        float cy = r.centerY();
        boolean hasSub = sub != null && !sub.isEmpty();
        float nameY;
        if (hasSub) {
            nameY = cy - namePaint.getTextSize() * 0.5f;
            canvas.drawText(name, r.centerX(), nameY, namePaint);
            canvas.drawText(sub, r.centerX(), nameY + namePaint.getTextSize() * 0.2f + subPaint.getTextSize(), subPaint);
        } else {
            nameY = cy + namePaint.getTextSize() * 0.35f;
            canvas.drawText(name, r.centerX(), nameY, namePaint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_UP && listener != null && data != null) {
            float x = event.getX(), y = event.getY();
            float dayAreaX = timeAxisW + pad;
            float w = getWidth();
            if (w <= 0 || days.isEmpty() || colW <= 0) return true;
            int col = (int) ((x - dayAreaX) / colW);
            if (col < 0 || col >= days.size()) return true;
            int day = days.get(col);
            // 时间轴区间限制
            if (y < headerH) return true;
            contentH = getHeight() - headerH - 2 * pad;
            int minute = minTime + (int) ((y - headerH - pad) * (maxTime - minTime) / contentH);
            for (RenderedCell cell : cellCache.get(day)) {
                if (minute >= cell.startMin && minute < cell.endMin) {
                    listener.onCellClick(day, cell.type, cell.refId);
                    return true;
                }
            }
        }
        return true;
    }

    private int adjustAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (alpha << 24);
    }
}
