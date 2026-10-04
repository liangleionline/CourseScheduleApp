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
import android.view.ViewConfiguration;

import com.courseschedule.app.data.AppData;
import com.courseschedule.app.data.ColorUtil;
import com.courseschedule.app.data.Course;
import com.courseschedule.app.data.RenderedCell;
import com.courseschedule.app.data.TimetableEngine;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 周课程表视图：统一行高网格。
 * 每一行对应一个「开始时间」，课程与非课程各占一行、行高一致、行与行首尾相接。
 * 非课程项（贯穿全周）横向铺满整行；课程按天显示在对应列。
 * 这样既不按时长撑高、无空洞，也不会重叠。
 */
public class TimetableView extends View {

    public interface Listener {
        void onCellClick(int day, int type, String refId);
    }

    private static final String[] DAY_NAMES = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};

    /** 一行 = 一个开始时间；dayCells: 该开始时间下各天的格子 */
    private static class Row {
        final int start;
        final Map<Integer, RenderedCell> dayCells = new LinkedHashMap<>();
        Row(int start) { this.start = start; }
    }

    private AppData data;
    private Listener listener;
    private List<Integer> days = new ArrayList<>();
    private List<Row> rows = new ArrayList<>();

    private final Paint cellPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint namePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint subPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint dayPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint timePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    private Map<String, Course> courseById = new LinkedHashMap<>();

    private float pad, timeAxisW, headerH, colW, rowH;

    public TimetableView(Context c, AttributeSet a) { super(c, a); init(); }

    private float nameBaseSize, subBaseSize;

    private void init() {
        float d = getResources().getDisplayMetrics().density;
        pad = 8 * d;
        timeAxisW = 44 * d;
        headerH = 44 * d;

        linePaint.setColor(0xFFE6EAF0);
        linePaint.setStrokeWidth(1f * d);

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
        nameBaseSize = namePaint.getTextSize();
        subBaseSize = subPaint.getTextSize();
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

        // 按开始时间建行：把各天的格子归入对应行
        TreeMap<Integer, Row> rowMap = new TreeMap<>();
        for (int day : days) {
            List<RenderedCell> cells = TimetableEngine.computeDay(data, day);
            for (RenderedCell cell : cells) {
                Row r = rowMap.get(cell.startMin);
                if (r == null) { r = new Row(cell.startMin); rowMap.put(cell.startMin, r); }
                r.dayCells.put(day, cell);
            }
        }
        rows.clear();
        rows.addAll(rowMap.values());
        invalidate();
    }

    private String fmt(int m) {
        return String.format("%02d:%02d", m / 60, m % 60);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth(), h = getHeight();
        canvas.drawColor(0xFFF6F8FC);

        if (days.isEmpty()) return;
        colW = (w - timeAxisW - 2 * pad) / days.size();
        float dayAreaX = timeAxisW + pad;

        int n = rows.size();
        // 依据屏幕高度按比例预算课表区域，适配不同机型，保证一屏放下全部课程
        float screenH = getResources().getDisplayMetrics().heightPixels;
        float viewAvail = h - headerH - 2 * pad;
        float budgetAvail = screenH * 0.85f - headerH - 2 * pad;
        float avail = Math.min(viewAvail, budgetAvail);
        rowH = n == 0 ? dp(44) : avail / n;

        drawHeader(canvas, dayAreaX);

        if (n == 0) {
            timePaint.setColor(0xFF8A94A6);
            canvas.drawText("暂无课表", (dayAreaX + w - pad) / 2f, headerH + pad + dp(20), timePaint);
            return;
        }

        float y = headerH + pad;
        for (Row row : rows) {
            canvas.drawLine(dayAreaX, y, w - pad, y, linePaint);
            timePaint.setColor(0xFF8A94A6);
            canvas.drawText(fmt(row.start), timeAxisW / 2f, y + rowH / 2f + timePaint.getTextSize() * 0.35f, timePaint);

            // 非课程带：整行铺满（贯穿全周）
            boolean hasBand = false;
            for (RenderedCell cell : row.dayCells.values()) {
                if (cell.type == TimetableEngine.TYPE_NONCOURSE) { hasBand = true; break; }
            }
            if (hasBand) {
                RenderedCell band = null;
                for (RenderedCell cell : row.dayCells.values()) {
                    if (cell.type == TimetableEngine.TYPE_NONCOURSE) { band = cell; break; }
                }
                // 非课程格子压矮（约占行的 55%），居中显示，视觉上更窄
                float bandH = Math.max(dp(18), rowH * 0.55f);
                float by = y + (rowH - bandH) / 2f;
                rect.set(dayAreaX + 2, by + 1, w - pad - 2, by + bandH - 1);
                cellPaint.setColor(ColorUtil.NONCOURSE_BG);
                canvas.drawRoundRect(rect, 8 * getResources().getDisplayMetrics().density,
                        8 * getResources().getDisplayMetrics().density, cellPaint);
                drawCellText(canvas, rect, cellName(band), "", ColorUtil.NONCOURSE_TEXT, true);
            }

            // 各天课程格
            for (int idx = 0; idx < days.size(); idx++) {
                int day = days.get(idx);
                RenderedCell cell = row.dayCells.get(day);
                if (cell == null || cell.type == TimetableEngine.TYPE_NONCOURSE) continue;
                Course c = courseById.get(cell.refId);
                if (c == null) continue;
                float x = dayAreaX + idx * colW;
                rect.set(x + 2, y + 1, x + colW - 2, y + rowH - 1);
                cellPaint.setColor(c.bgColor);
                canvas.drawRoundRect(rect, 8 * getResources().getDisplayMetrics().density,
                        8 * getResources().getDisplayMetrics().density, cellPaint);
                drawCellText(canvas, rect, c.name, c.teacher == null ? "" : c.teacher, c.textColor, false);
            }
            y += rowH;
        }
        canvas.drawLine(dayAreaX, y, w - pad, y, linePaint);
    }

    private void drawHeader(Canvas canvas, float dayAreaX) {
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
        // 长课程名（≥4字）拆成两行，避免挤在一行
        String[] nameLines = (!isNon && name.length() >= 4)
                ? new String[]{name.substring(0, (name.length() + 1) / 2), name.substring((name.length() + 1) / 2)}
                : new String[]{name};
        boolean hasSub = sub != null && !sub.isEmpty();
        int totalLines = nameLines.length + (hasSub ? 1 : 0);
        float base = nameBaseSize;
        float spacing = base * 1.18f;
        float maxH = r.height() * 0.92f;
        float needH = totalLines * spacing;
        float scale = Math.min(1f, maxH / needH);
        float fs = base * scale;
        namePaint.setTextSize(fs);
        subPaint.setTextSize(subBaseSize * scale);
        float lineH = fs * 1.18f;
        float y = r.centerY() - (totalLines - 1) * lineH / 2f + fs * 0.36f;
        for (String ln : nameLines) {
            canvas.drawText(ln, r.centerX(), y, namePaint);
            y += lineH;
        }
        if (hasSub) {
            canvas.drawText(sub, r.centerX(), y, subPaint);
        }
        // 恢复字号，避免影响后续绘制
        namePaint.setTextSize(nameBaseSize);
        subPaint.setTextSize(subBaseSize);
    }

    private float downX, downY;
    private boolean moved;
    private final int touchSlop = ViewConfiguration.get(getContext()).getScaledTouchSlop();

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = event.getX();
                downY = event.getY();
                moved = false;
                break;
            case MotionEvent.ACTION_MOVE:
                if (!moved) {
                    float dx = event.getX() - downX;
                    float dy = event.getY() - downY;
                    if (dx * dx + dy * dy > touchSlop * touchSlop) moved = true;
                }
                break;
            case MotionEvent.ACTION_UP:
                // 只有真正的轻点（未移动超过阈值）才触发编辑，滑动抬起不误触
                if (!moved && listener != null && data != null) {
                    float x = event.getX(), y = event.getY();
                    float dayAreaX = timeAxisW + pad;
                    float w = getWidth();
                    if (w <= 0 || days.isEmpty() || colW <= 0 || rows.isEmpty()) break;
                    if (y < headerH + pad) break;
                    int rowIndex = (int) ((y - headerH - pad) / rowH);
                    if (rowIndex < 0 || rowIndex >= rows.size()) break;
                    int col = (int) ((x - dayAreaX) / colW);
                    if (col < 0 || col >= days.size()) break;
                    int day = days.get(col);
                    RenderedCell cell = rows.get(rowIndex).dayCells.get(day);
                    if (cell != null) {
                        listener.onCellClick(day, cell.type, cell.refId);
                    }
                }
                break;
        }
        return true;
    }

    private int adjustAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }
}
