package com.courseschedule.app.ui;

import android.animation.ValueAnimator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.SystemClock;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.OvershootInterpolator;
import android.view.animation.DecelerateInterpolator;

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
 * - Win8 磁贴质感：每块色块覆盖一层均匀平面玻璃罩（无渐变无高光，完全平面）
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
    private final TextPaint namePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint subPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint dayPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint timePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    private Map<String, Course> courseById = new LinkedHashMap<>();

    private float pad, timeAxisW, headerH, colW;
    private float rowH, bandH;
    private float nonCourseReveal = 0f; // 0 隐藏，1 全展开
    private ValueAnimator animator;

    /** 磁贴入场动画：每次数据刷新时课程色块从右下向左上对角错开落下（Windows 磁贴感） */
    private static final long ENTRANCE_DUR = 650L;
    private static final long ENTRANCE_STAGGER = 60L;
    private static final long ENTRANCE_STAGGER_COL = 30L;
    private static final float ENTRANCE_DROP_DP = 26f;
    private long entranceStart = -1L;
    private final OvershootInterpolator entranceInterp = new OvershootInterpolator(3f);

    private float downX, downY;
    private boolean moved;
    private boolean peekNotified;
    private final int touchSlop;

    // ---------- 磁贴按压效果（Win8：按在磁贴不同位置，翻起方向不同） ----------
    /** 按压区域：0=中心 1=上 2=右 3=下 4=左 5=右上 6=左上 7=右下 8=左下 */
    private static final int PRESS_C = 0, PRESS_N = 1, PRESS_E = 2, PRESS_S = 3,
            PRESS_W = 4, PRESS_NE = 5, PRESS_NW = 6, PRESS_SE = 7, PRESS_SW = 8;
    private String pressedKey;      // 当前按下的磁贴（day|startMin|refId）
    private int pressRegion = PRESS_C;
    private float pressScale = 1f;  // 当前按压缩放（1=无）
    private ValueAnimator pressAnimator;
    private final RectF pressedRect = new RectF();

    // ---------- 磁贴翻转详情（Win8：点击磁贴翻转到背面显示课程详情） ----------
    private String flipKey;         // 正在翻转/已翻转的磁贴（day|startMin|refId）
    private float flipProgress;     // 0~1 翻转进度（180°）
    private String flipTime;        // 背面显示的时间文本
    private ValueAnimator flipAnimator;
    private final RectF flipRect = new RectF();
    private final android.graphics.Camera flipCamera = new android.graphics.Camera();
    private final android.graphics.Matrix flipMatrix = new android.graphics.Matrix();

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
        resetFlip(); // 数据刷新/切换课程表时复位翻转磁贴
        invalidate();
    }

    /** 手动触发磁贴入场动画（如课程表切换滑入完成后调用） */
    public void playEntrance() {
        entranceStart = -1L;
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
        // 磁贴入场动画：首次绘制时启动，磁贴按右下→左上错开（对角波浪）
        if (entranceStart < 0L) entranceStart = SystemClock.uptimeMillis();
        long entranceNow = SystemClock.uptimeMillis();
        boolean entranceRunning = entranceNow < entranceStart + ENTRANCE_DUR
                + (long) rows.size() * ENTRANCE_STAGGER + (long) days.size() * ENTRANCE_STAGGER_COL;
        int visibleCount = 0;
        for (Row r2 : rows) {
            if ((isBandRow(r2) ? bandH : rowH) > 0) visibleCount++;
        }
        int rowIdx = 0;
        for (Row row : rows) {
            boolean band = isBandRow(row);
            float rh = band ? bandH : rowH;
            if (rh <= 0) continue; // 非课程收起时无高度

            // 分隔线：动画期间隐藏（磁贴错位落下时会露出横线），结束后再显示
            if (!entranceRunning) {
                canvas.drawLine(dayAreaX, y, w - pad, y, linePaint);
            }
            timePaint.setColor(0xFF666666);
            canvas.drawText(fmt(row.start), timeAxisW / 2f, y + rh / 2f + timePaint.getTextSize() * 0.35f, timePaint);

            if (band) {
                RenderedCell bandCell = null;
                for (RenderedCell cell : row.dayCells.values()) {
                    if (cell.type == TimetableEngine.TYPE_NONCOURSE) { bandCell = cell; break; }
                }
                // 横贯带：取列中间位置参与对角错开
                float yDraw = y + cellDropOffset(rowIdx, (days.size() - 1) / 2, visibleCount, days.size());
                // 随收起进度淡出，消除收起最后一瞬的文字闪烁
                float fade = reveal;
                int bandAlpha = (int) (255 * Math.min(1f, fade));
                rect.set(dayAreaX + 2, yDraw + 1, w - pad - 2, yDraw + rh - 1);
                cellPaint.setColor(adjustAlpha(ColorUtil.NONCOURSE_BG, bandAlpha));
                canvas.drawRect(rect, cellPaint); // Metro：直角扁平块
                if (rh >= dp(14) && fade > 0.05f) {
                    drawTileGlass(canvas, rect, bandAlpha); // 平面玻璃罩层
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
                    float yDraw = y + cellDropOffset(rowIdx, idx, visibleCount, days.size());
                    rect.set(x + 2, yDraw + 1, x + colW - 2, yDraw + rh - 1);
                    // 按压/翻转中的磁贴：跳过常规绘制，循环结束后单独绘制（盖在其余磁贴之上）
                    String key = day + "|" + cell.startMin + "|" + cell.refId;
                    if ((pressedKey != null && pressedKey.equals(key))
                            || (flipKey != null && flipKey.equals(key))) {
                        continue;
                    }
                    cellPaint.setColor(c.bgColor);
                    canvas.drawRect(rect, cellPaint); // Metro：直角扁平磁贴
                    drawTileGlass(canvas, rect, 255); // 平面玻璃罩层
                    drawCellText(canvas, rect, c.name, c.teacher == null ? "" : c.teacher, c.textColor, false);
                }
            }
            rowIdx++;
            y += rh;
        }
        if (!entranceRunning) canvas.drawLine(dayAreaX, y, w - pad, y, linePaint); // 底边线同动画期间隐藏
        drawPressedCell(canvas); // 按压磁贴最后绘制，盖在其余内容之上
        drawFlippedCell(canvas); // 翻转磁贴最后绘制（3D 翻转显示背面详情）
        if (entranceRunning) postInvalidateOnAnimation(); // 动画期间持续重绘
    }

    /** 翻转磁贴：Camera 绕 Y 轴翻转 180°，前半正面、后半背面（背面正读显示详情） */
    private void drawFlippedCell(Canvas canvas) {
        if (flipKey == null || flipProgress <= 0.001f) return;
        String[] parts = flipKey.split("\\|");
        if (parts.length < 3) return;
        Course c = courseById.get(parts[2]);
        if (c == null) return;
        RectF r = flipRect;
        if (r.width() <= 0 || r.height() <= 0) return;
        float deg = flipProgress * 180f;

        canvas.save();
        flipCamera.save();
        if (deg <= 90f) {
            flipCamera.rotateY(deg); // 正面：0→90
        } else {
            flipCamera.rotateY(deg - 180f); // 背面：-90→0，正读显示
        }
        flipCamera.getMatrix(flipMatrix);
        flipMatrix.preTranslate(-r.centerX(), -r.centerY());
        flipMatrix.postTranslate(r.centerX(), r.centerY());
        canvas.concat(flipMatrix);

        cellPaint.setColor(c.bgColor);
        canvas.drawRect(r, cellPaint); // 直角扁平磁贴
        drawTileGlass(canvas, r, 255); // 平面玻璃罩层

        if (deg <= 90f) {
            // 正面：课程名 + 教师
            drawCellText(canvas, r, c.name, c.teacher == null ? "" : c.teacher, c.textColor, false);
        } else {
            // 背面：课程名 + 教师·时间 + 点击编辑
            String meta = (c.teacher != null && !c.teacher.isEmpty() ? c.teacher + " · " : "") + flipTime;
            drawFlipBack(canvas, r, c.name, meta, c.textColor);
        }
        canvas.restore();
        flipCamera.restore();
    }

    /** 翻转背面排版：课程名（可两行） + 教师·时间 + 底部"点击编辑" */
    private void drawFlipBack(Canvas canvas, RectF r, String name, String meta, int color) {
        float availH = r.height() * 0.62f;
        String[] nameLines = name.length() >= 4
                ? new String[]{name.substring(0, (name.length() + 1) / 2), name.substring((name.length() + 1) / 2)}
                : new String[]{name};
        float fs = Math.min(nameBaseSize * 0.92f, availH / (nameLines.length * 1.18f));
        namePaint.setColor(color);
        namePaint.setTextSize(fs);
        float lineH = fs * 1.18f;
        float y = r.top + (availH - nameLines.length * lineH) / 2f + fs * 0.36f;
        for (String ln : nameLines) {
            canvas.drawText(ln, r.centerX(), y, namePaint);
            y += lineH;
        }
        subPaint.setColor(adjustAlpha(color, 235));
        subPaint.setTextSize(subBaseSize * 0.95f);
        canvas.drawText(meta, r.centerX(), r.top + availH + subBaseSize * 1.35f, subPaint);
        subPaint.setTextSize(subBaseSize * 0.78f);
        subPaint.setColor(adjustAlpha(color, 175));
        canvas.drawText("点击编辑", r.centerX(), r.bottom - subBaseSize * 0.9f, subPaint);
        namePaint.setTextSize(nameBaseSize);
        subPaint.setTextSize(subBaseSize);
    }

    /** 按压磁贴：按按压区域以对侧为锚点放大 + 轻微旋转（Win8 磁贴按压效果，2D 近似） */
    private void drawPressedCell(Canvas canvas) {
        if (pressedKey == null || pressScale <= 1.001f) return;
        String[] parts = pressedKey.split("\\|");
        if (parts.length < 3) return;
        Course c = courseById.get(parts[2]);
        if (c == null) return;
        RectF r = pressedRect;
        float scale = pressScale;
        float cx = r.centerX(), cy = r.centerY();
        float pivotX, pivotY, rot, sx, sy;
        switch (pressRegion) {
            case PRESS_N: pivotX = cx; pivotY = r.bottom; rot = 0; sx = 1f; sy = scale; break;
            case PRESS_S: pivotX = cx; pivotY = r.top; rot = 0; sx = 1f; sy = scale; break;
            case PRESS_W: pivotX = r.right; pivotY = cy; rot = 0; sx = scale; sy = 1f; break;
            case PRESS_E: pivotX = r.left; pivotY = cy; rot = 0; sx = scale; sy = 1f; break;
            case PRESS_NE: pivotX = r.left; pivotY = r.bottom; rot = -2f; sx = scale; sy = scale; break;
            case PRESS_NW: pivotX = r.right; pivotY = r.bottom; rot = 2f; sx = scale; sy = scale; break;
            case PRESS_SE: pivotX = r.left; pivotY = r.top; rot = 2f; sx = scale; sy = scale; break;
            case PRESS_SW: pivotX = r.right; pivotY = r.top; rot = -2f; sx = scale; sy = scale; break;
            default: pivotX = cx; pivotY = cy; rot = 0; sx = scale; sy = scale; break;
        }
        canvas.save();
        canvas.rotate(rot, pivotX, pivotY);
        canvas.scale(sx, sy, pivotX, pivotY);
        cellPaint.setColor(c.bgColor);
        canvas.drawRect(r, cellPaint); // 直角扁平磁贴（随 canvas 变换放大）
        drawTileGlass(canvas, r, 255); // 平面玻璃罩层
        drawCellText(canvas, r, c.name, c.teacher == null ? "" : c.teacher, c.textColor, false);
        canvas.restore();
    }

    /** 磁贴入场偏移：延迟 = (总行-行) × 行间隔 + (总列-列) × 列间隔，右下先掉、左上后掉，Overshoot 回弹 */
    private float cellDropOffset(int rowIdx, int colIdx, int totalRows, int totalCols) {
        float dropPx = ENTRANCE_DROP_DP * getResources().getDisplayMetrics().density;
        long delay = (long) (totalRows - 1 - rowIdx) * ENTRANCE_STAGGER
                + (long) (totalCols - 1 - colIdx) * ENTRANCE_STAGGER_COL;
        long elapsed = SystemClock.uptimeMillis() - entranceStart - delay;
        if (elapsed <= 0L) return -dropPx;
        float p = Math.min(1f, elapsed / (float) ENTRANCE_DUR);
        float t = entranceInterp.getInterpolation(p);
        return (t - 1f) * dropPx;
    }

    /** 平面玻璃罩层：整块色块被一层均匀的极淡白玻璃覆盖，无渐变、无高光、无边缘线，保持完全平面 */
    private void drawTileGlass(Canvas canvas, RectF r, int alpha) {
        if (r.height() <= 0 || alpha <= 0) return;
        int veil = (int) (0x10 * alpha / 255f); // 均匀白罩 ≈6%
        glassPaint.setShader(null);
        glassPaint.setColor(0xFFFFFFFF);
        glassPaint.setAlpha(veil);
        canvas.drawRect(r, glassPaint);
    }

    private void drawHeader(Canvas canvas, float dayAreaX) {
        for (int idx = 0; idx < days.size(); idx++) {
            int day = days.get(idx);
            float x = dayAreaX + idx * colW;
            rect.set(x + 2, pad, x + colW - 2, pad + headerH);
            boolean today = isToday(day);
            cellPaint.setColor(today ? 0xFF0078D7 : 0xFFE5F1FB);
            canvas.drawRect(rect, cellPaint); // Metro：直角扁平标题块
            drawTileGlass(canvas, rect, 255); // 平面玻璃罩层
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
                pressDown(event.getX(), event.getY());
                break;
            case MotionEvent.ACTION_MOVE:
                if (!moved) {
                    float dx = event.getX() - downX;
                    float dy = event.getY() - downY;
                    if (dx * dx + dy * dy > touchSlop * touchSlop) {
                        moved = true;
                        pressRelease(true); // 转为滑动/下拉，取消按压
                        resetFlip();        // 布局将变化，同时复位翻转磁贴
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
                } else {
                    pressRelease(false); // 点击：磁贴回弹动画
                    if (listener != null && data != null && event.getActionMasked() == MotionEvent.ACTION_UP) {
                        handleTap(event.getX(), event.getY());
                    }
                }
                break;
        }
        return true;
    }

    /** 按下：命中磁贴则按位置记录按压态（中心/四边/四角），立即放大变形 */
    private void pressDown(float x, float y) {
        pressRelease(true);
        PressHit hit = hitTest(x, y);
        if (hit == null) return;
        pressedKey = hit.key;
        pressedRect.set(hit.rect);
        pressRegion = regionOf(hit.rect, x, y);
        pressScale = targetPressScale(pressRegion);
        invalidate();
    }

    /** 松手/取消：回弹动画（immediate=true 时直接复位，如转为滑动） */
    private void pressRelease(boolean immediate) {
        if (pressedKey == null) return;
        if (immediate) {
            pressScale = 1f;
            pressedKey = null;
            invalidate();
            return;
        }
        if (pressAnimator != null) pressAnimator.cancel();
        float from = pressScale;
        pressAnimator = ValueAnimator.ofFloat(from, 1f);
        pressAnimator.setDuration(160);
        pressAnimator.setInterpolator(new DecelerateInterpolator(2.2f));
        pressAnimator.addUpdateListener(a -> {
            pressScale = (float) a.getAnimatedValue();
            invalidate();
        });
        pressAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                pressedKey = null;
                invalidate();
            }
        });
        pressAnimator.start();
    }

    /** 命中检测：返回磁贴 key、矩形与按下坐标；非课程带/空白返回 null */
    private PressHit hitTest(float x, float y) {
        float dayAreaX = timeAxisW + pad;
        float w = getWidth();
        if (w <= 0 || days.isEmpty() || colW <= 0 || rows.isEmpty()) return null;
        if (y < headerH + pad) return null;
        float acc = headerH + pad;
        for (Row row : rows) {
            float rh = isBandRow(row) ? bandH : rowH;
            if (rh <= 0) { continue; }
            if (y >= acc && y < acc + rh) {
                int col = (int) ((x - dayAreaX) / colW);
                if (col < 0 || col >= days.size()) return null;
                int day = days.get(col);
                RenderedCell cell = row.dayCells.get(day);
                if (cell == null || cell.type == TimetableEngine.TYPE_NONCOURSE) return null;
                float gx = dayAreaX + col * colW;
                PressHit h = new PressHit();
                h.key = day + "|" + cell.startMin + "|" + cell.refId;
                h.rect.set(gx + 2, acc + 1, gx + colW - 2, acc + rh - 1);
                return h;
            }
            acc += rh;
        }
        return null;
    }

    /** 9 宫格区域：x<1/4 左、x>3/4 右，y<1/4 上、y>3/4 下 */
    private int regionOf(RectF r, float x, float y) {
        int hz = x < r.left + r.width() * 0.25f ? 1 : (x > r.right - r.width() * 0.25f ? 2 : 0);
        int vt = y < r.top + r.height() * 0.25f ? 1 : (y > r.bottom - r.height() * 0.25f ? 2 : 0);
        // 水平：1=左 2=右 0=中；垂直：1=上 2=下 0=中
        if (vt == 0 && hz == 0) return PRESS_C;
        if (vt == 1 && hz == 0) return PRESS_N;
        if (vt == 2 && hz == 0) return PRESS_S;
        if (vt == 0 && hz == 1) return PRESS_W;
        if (vt == 0 && hz == 2) return PRESS_E;
        if (vt == 1 && hz == 2) return PRESS_NE;
        if (vt == 1 && hz == 1) return PRESS_NW;
        if (vt == 2 && hz == 2) return PRESS_SE;
        return PRESS_SW;
    }

    /** 目标缩放（对齐 Win8 磁贴按压缩放：中心 1.92、边 2.0、角 2.04） */
    private float targetPressScale(int region) {
        return region == PRESS_C ? 1.92f : (region == PRESS_NE || region == PRESS_NW
                || region == PRESS_SE || region == PRESS_SW ? 2.04f : 2.0f);
    }

    private static class PressHit {
        String key;
        final RectF rect = new RectF();
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
        if (cell == null) return;
        if (cell.type == TimetableEngine.TYPE_NONCOURSE) {
            // 非课程带：保持原逻辑（弹出编辑）
            listener.onCellClick(day, cell.type, cell.refId, cell.startMin, cell.endMin);
            return;
        }
        String key = day + "|" + cell.startMin + "|" + cell.refId;
        if (key.equals(flipKey) && flipProgress >= 1f) {
            // 已翻转到背面：翻回并弹出编辑
            flipBack();
            listener.onCellClick(day, cell.type, cell.refId, cell.startMin, cell.endMin);
        } else {
            // 课程磁贴：翻转到背面显示详情
            pressRelease(true);
            startFlip(key, cell, day);
        }
    }

    /** 开始翻转指定磁贴（400ms，正面→背面显示详情） */
    private void startFlip(String key, RenderedCell cell, int day) {
        resetFlip();
        flipKey = key;
        flipTime = fmt(cell.startMin) + "-" + fmt(cell.endMin);
        // 磁贴矩形：布局坐标（与绘制一致）
        float acc = headerH + pad;
        float dayAreaX = timeAxisW + pad;
        for (Row row : rows) {
            float rh = isBandRow(row) ? bandH : rowH;
            if (rh <= 0) { acc += rh; continue; }
            for (int idx = 0; idx < days.size(); idx++) {
                int d = days.get(idx);
                RenderedCell c = row.dayCells.get(d);
                if (c == null) continue;
                if (d == day && c.startMin == cell.startMin && c.refId.equals(cell.refId)) {
                    float x = dayAreaX + idx * colW;
                    flipRect.set(x + 2, acc + 1, x + colW - 2, acc + rh - 1);
                }
            }
            acc += rh;
        }
        flipProgress = 0f;
        flipAnimator = ValueAnimator.ofFloat(0f, 1f);
        flipAnimator.setDuration(400);
        flipAnimator.setInterpolator(new DecelerateInterpolator(2.0f));
        flipAnimator.addUpdateListener(a -> {
            flipProgress = (float) a.getAnimatedValue();
            invalidate();
        });
        flipAnimator.start();
    }

    /** 翻回正面（从背面） */
    private void flipBack() {
        if (flipKey == null) return;
        if (flipAnimator != null) flipAnimator.cancel();
        float from = flipProgress;
        flipAnimator = ValueAnimator.ofFloat(from, 0f);
        flipAnimator.setDuration(400);
        flipAnimator.setInterpolator(new DecelerateInterpolator(2.0f));
        flipAnimator.addUpdateListener(a -> {
            flipProgress = (float) a.getAnimatedValue();
            invalidate();
        });
        flipAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                flipKey = null;
                invalidate();
            }
        });
        flipAnimator.start();
    }

    /** 复位翻转状态（数据刷新/滑动/下拉时） */
    private void resetFlip() {
        if (flipAnimator != null) { flipAnimator.cancel(); flipAnimator = null; }
        flipKey = null;
        flipProgress = 0f;
    }

    private int adjustAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }
}
