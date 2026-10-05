package com.courseschedule.app.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.OvershootInterpolator;

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
 * 周课程表视图：统一行高网格，按时间行显示课程与非课程。
 * - 非课程项默认隐藏（行高为 0），课程紧凑排列
 * - 按住向下滑动：非课程行从 0 生长到指定高度，松手回弹收回
 * - 轻点课程触发详情回调；滑动与点击用系统阈值区分
 * - Win8 磁贴质感：每块色块自带一道顶部玻璃光泽（单块玻璃感，无整板覆盖）
 */
public class TimetableView extends View {

    public interface Listener {
        void onCellClick(int day, int type, String refId, int startMin, int endMin);
        /** 下拉展开非课程项开始时回调（用于隐藏详情面板等） */
        void onPeekStart();
        /** 左右滑动切换课程表：direction=-1 上一个，+1 下一个 */
        void onSwipe(int direction);
    }

    private static final String[] DAY_NAMES = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};

    /** 非课程格子与课程格子的高度比 */
    private static final float NONCOURSE_RATIO = 0.5f;

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
    private final Paint glassPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glassEdgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint namePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint subPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint dayPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint timePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final RectF glassRect = new RectF();

    private Map<String, Course> courseById = new LinkedHashMap<>();

    private float pad, timeAxisW, headerH, colW;
    private float rowH, bandH;
    private float nonCourseReveal = 0f; // 0 隐藏，1 全展开
    private ValueAnimator animator;

    private float downX, downY;
    private boolean moved;
    private boolean peekNotified;
    private final int touchSlop;

    public TimetableView(Context c, AttributeSet a) {
        super(c, a);
        touchSlop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
        init();
    }

    private void init() {
        float d = getResources().getDisplayMetrics().density;
        pad = 8 * d;
        timeAxisW = 44 * d;
        headerH = 44 * d;

        linePaint.setColor(0xFFE0E0E0);
        linePaint.setStrokeWidth(1f * d);

        namePaint.setTextSize(15 * d);
        namePaint.setTypeface(Typeface.DEFAULT_BOLD); // Metro 磁贴风格：粗体
        namePaint.setTextAlign(Paint.Align.CENTER);
        subPaint.setTextSize(11 * d);
        subPaint.setTextAlign(Paint.Align.CENTER);
        dayPaint.setTextSize(15 * d);
        dayPaint.setTypeface(Typeface.DEFAULT_BOLD);
        dayPaint.setTextAlign(Paint.Align.CENTER);
        timePaint.setTextSize(10 * d);
        timePaint.setTextAlign(Paint.Align.CENTER);
        nameBaseSize = namePaint.getTextSize();
        subBaseSize = subPaint.getTextSize();
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
    }

    private float nameBaseSize, subBaseSize;

    public void setListener(Listener l) { this.listener = l; }

    public void setData(AppData d) {
        this.data = d;
        days.clear();
        for (int i = 1; i <= 5; i++) days.add(i);
        if (data.showWeekend) { days.add(6); days.add(7); }
        courseById.clear();
        for (Course c : data.courses) courseById.put(c.id, c);

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
        cancelAnim();
        nonCourseReveal = 0f;
        invalidate();
    }

    private boolean isBandRow(Row r) {
        for (RenderedCell c : r.dayCells.values()) {
            if (c.type == TimetableEngine.TYPE_NONCOURSE) return true;
        }
        return false;
    }

    private int courseRowCount() {
        int n = 0;
        for (Row r : rows) if (!isBandRow(r)) n++;
        return n;
    }

    private int bandRowCount() {
        int n = 0;
        for (Row r : rows) if (isBandRow(r)) n++;
        return n;
    }

    private float clampedReveal() {
        return Math.max(0f, Math.min(1f, nonCourseReveal));
    }

    private void cancelAnim() {
        if (animator != null) { animator.cancel(); animator = null; }
    }

    private void animateRevealTo(float to) {
        cancelAnim();
        float from = nonCourseReveal;
        animator = ValueAnimator.ofFloat(from, to);
        animator.setDuration(340);
        animator.setInterpolator(new OvershootInterpolator(1.4f));
        animator.addUpdateListener(a -> {
            nonCourseReveal = (float) a.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    private String fmt(int m) {
        return String.format("%02d:%02d", m / 60, m % 60);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth(), h = getHeight();
        canvas.drawColor(0xFFF2F2F2);

        if (days.isEmpty()) return;
        colW = (w - timeAxisW - 2 * pad) / days.size();
        float dayAreaX = timeAxisW + pad;

        int n = rows.size();
        // 依据屏幕高度按比例预算课表区域，适配不同机型，保证一屏放下
        float screenH = getResources().getDisplayMetrics().heightPixels;
        float viewAvail = h - headerH - 2 * pad;
        float budgetAvail = screenH * 0.85f - headerH - 2 * pad;
        float avail = Math.min(viewAvail, budgetAvail);

        float reveal = clampedReveal();
        int cc = courseRowCount();
        int bc = bandRowCount();
        if (cc <= 0 || (bc <= 0 && cc <= 0)) return;
        if (bc <= 0) {
            rowH = avail / cc;
            bandH = 0;
        } else {
            // 非课程与课程格子的高度比（按用户截图比例：非课程约为课程的 0.75）
            float ratio = NONCOURSE_RATIO;
            // 收起（reveal=0）：课程高度铺满；完全展开（reveal=1）：课程压缩到 xFull、非课程为 ratio*xFull
            float x0 = avail / cc;
            float xFull = avail / (cc + bc * ratio);
            // 下拉时：课程随 reveal 从 x0 线性压缩到 xFull，非课程从 0 线性拉伸到 ratio*xFull
            rowH = x0 - (x0 - xFull) * reveal;
            bandH = ratio * xFull * reveal;
        }

        drawHeader(canvas, dayAreaX);

        if (n == 0) {
            timePaint.setColor(0xFF666666);
            canvas.drawText("暂无课表", (dayAreaX + w - pad) / 2f, headerH + pad + dp(20), timePaint);
            return;
        }

        float y = headerH + pad;
        for (Row row : rows) {
            boolean band = isBandRow(row);
            float rh = band ? bandH : rowH;
            if (rh <= 0) continue; // 非课程收起时无高度

            canvas.drawLine(dayAreaX, y, w - pad, y, linePaint);
            timePaint.setColor(0xFF666666);
            canvas.drawText(fmt(row.start), timeAxisW / 2f, y + rh / 2f + timePaint.getTextSize() * 0.35f, timePaint);

            if (band) {
                RenderedCell bandCell = null;
                for (RenderedCell cell : row.dayCells.values()) {
                    if (cell.type == TimetableEngine.TYPE_NONCOURSE) { bandCell = cell; break; }
                }
                // 随收起进度淡出，消除收起最后一瞬的文字闪烁
                float fade = reveal;
                int bandAlpha = (int) (255 * Math.min(1f, fade));
                rect.set(dayAreaX + 2, y + 1, w - pad - 2, y + rh - 1);
                cellPaint.setColor(adjustAlpha(ColorUtil.NONCOURSE_BG, bandAlpha));
                canvas.drawRect(rect, cellPaint); // Metro：直角扁平块
                if (rh >= dp(14) && fade > 0.05f) {
                    drawTileGlass(canvas, rect, bandAlpha); // Win8 磁贴单块玻璃光泽
                    drawCellText(canvas, rect, cellName(bandCell), "",
                            adjustAlpha(ColorUtil.NONCOURSE_TEXT, (int) (255 * fade)), true);
                }
            } else {
                for (int idx = 0; idx < days.size(); idx++) {
                    int day = days.get(idx);
                    RenderedCell cell = row.dayCells.get(day);
                    if (cell == null || cell.type == TimetableEngine.TYPE_NONCOURSE) continue;
                    Course c = courseById.get(cell.refId);
                    if (c == null) continue;
                    float x = dayAreaX + idx * colW;
                    rect.set(x + 2, y + 1, x + colW - 2, y + rh - 1);
                    cellPaint.setColor(c.bgColor);
                    canvas.drawRect(rect, cellPaint); // Metro：直角扁平磁贴
                    drawTileGlass(canvas, rect, 255); // Win8 磁贴单块玻璃光泽
                    drawCellText(canvas, rect, c.name, c.teacher == null ? "" : c.teacher, c.textColor, false);
                }
            }
            y += rh;
        }
        canvas.drawLine(dayAreaX, y, w - pad, y, linePaint);
    }

    /** Win8 磁贴单块玻璃质感：色块顶部一道玻璃反光渐变 + 上缘高光线，直角扁平、无阴影 */
    private void drawTileGlass(Canvas canvas, RectF r, int alpha) {
        if (r.height() <= 0 || alpha <= 0) return;
        float d = getResources().getDisplayMetrics().density;
        float glossH = r.height() * 0.30f; // 反光覆盖顶部约 1/3
        int edgeA = (int) (0x40 * alpha / 255f);
        glassRect.set(r.left, r.top, r.right, r.top + glossH);
        glassPaint.setShader(new LinearGradient(0, r.top, 0, r.top + glossH,
                new int[]{0x30FFFFFF, 0x12FFFFFF, 0x00FFFFFF},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        glassPaint.setAlpha(alpha);
        canvas.drawRect(glassRect, glassPaint);
        glassPaint.setShader(null);
        // 上缘高光线（随 alpha 同步淡出）
        glassEdgePaint.setColor((edgeA << 24) | 0x00FFFFFF);
        glassEdgePaint.setStrokeWidth(1f * d);
        canvas.drawLine(r.left, r.top, r.right, r.top, glassEdgePaint);
    }

    private void drawHeader(Canvas canvas, float dayAreaX) {
        for (int idx = 0; idx < days.size(); idx++) {
            int day = days.get(idx);
            float x = dayAreaX + idx * colW;
            rect.set(x + 2, pad, x + colW - 2, pad + headerH);
            boolean today = isToday(day);
            cellPaint.setColor(today ? 0xFF0078D7 : 0xFFE5F1FB);
            canvas.drawRect(rect, cellPaint); // Metro：直角扁平标题块
            drawTileGlass(canvas, rect, 255); // 磁贴单块玻璃光泽
            dayPaint.setColor(today ? Color.WHITE : 0xFF1A1A1A);
            canvas.drawText(DAY_NAMES[day - 1], rect.centerX(), rect.centerY() + dayPaint.getTextSize() * 0.36f, dayPaint);
        }
    }

    private boolean isToday(int day) {
        java.util.Calendar c = java.util.Calendar.getInstance();
        int dow = c.get(java.util.Calendar.DAY_OF_WEEK);
        int mon = dow == 1 ? 7 : dow - 1;
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
        String[] nameLines = (!isNon && name.length() >= 4)
                ? new String[]{name.substring(0, (name.length() + 1) / 2), name.substring((name.length() + 1) / 2)}
                : new String[]{name};
        boolean hasSub = sub != null && !sub.isEmpty();
        int totalLines = nameLines.length + (hasSub ? 1 : 0);
        float spacing = nameBaseSize * 1.18f;
        float maxH = r.height() * 0.92f;
        float scale = Math.min(1f, maxH / (totalLines * spacing));
        float fs = nameBaseSize * scale;
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
        namePaint.setTextSize(nameBaseSize);
        subPaint.setTextSize(subBaseSize);
    }

    /** 根据像素 y 找到所在行（考虑收起态高度） */
    private Row rowAt(float yPx) {
        float acc = headerH + pad;
        for (Row r : rows) {
            float rh = isBandRow(r) ? bandH : rowH;
            if (rh <= 0) continue;
            if (yPx >= acc && yPx < acc + rh) return r;
            acc += rh;
        }
        return null;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = event.getX();
                downY = event.getY();
                moved = false;
                peekNotified = false;
                cancelAnim();
                break;
            case MotionEvent.ACTION_MOVE:
                if (!moved) {
                    float dx = event.getX() - downX;
                    float dy = event.getY() - downY;
                    if (dx * dx + dy * dy > touchSlop * touchSlop) {
                        moved = true;
                    }
                }
                if (moved) {
                    if (!peekNotified && listener != null) {
                        peekNotified = true;
                        listener.onPeekStart();
                    }
                    // 下拉展开非课程项（0~1），跟随手指
                    float dy = event.getY() - downY;
                    nonCourseReveal = Math.max(0f, Math.min(1f, dy / dp(150)));
                    invalidate();
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (moved) {
                    // 横向滑动切换课程表
                    float dx = event.getX() - downX;
                    float dy = event.getY() - downY;
                    float w = getWidth();
                    float hThresh = Math.max(dp(60), w * 0.2f);
                    if (listener != null && Math.abs(dx) > hThresh && Math.abs(dx) > Math.abs(dy) * 1.2f) {
                        listener.onSwipe(dx > 0 ? -1 : 1);
                    }
                    // 松手回弹：收起非课程项
                    animateRevealTo(0f);
                } else if (listener != null && data != null && event.getActionMasked() == MotionEvent.ACTION_UP) {
                    handleTap(event.getX(), event.getY());
                }
                break;
        }
        return true;
    }

    private void handleTap(float x, float y) {
        float dayAreaX = timeAxisW + pad;
        float w = getWidth();
        if (w <= 0 || days.isEmpty() || colW <= 0 || rows.isEmpty()) return;
        if (y < headerH + pad) return;
        Row row = rowAt(y);
        if (row == null) return;
        int col = (int) ((x - dayAreaX) / colW);
        if (col < 0 || col >= days.size()) return;
        int day = days.get(col);
        RenderedCell cell = row.dayCells.get(day);
        if (cell != null) {
            listener.onCellClick(day, cell.type, cell.refId, cell.startMin, cell.endMin);
        }
    }

    private int adjustAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }
}
