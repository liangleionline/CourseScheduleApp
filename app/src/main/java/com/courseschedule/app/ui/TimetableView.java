package com.courseschedule.app.ui;

import android.animation.ValueAnimator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.SystemClock;
import android.graphics.RectF;
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

import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import com.courseschedule.app.data.RenderedCell;
import com.courseschedule.app.data.TimetableEngine;


/**
 * 周课程表视图：统一行高网格，按时间行显示课程与非课程。
 * - 非课程项默认隐藏（行高为 0），课程紧凑排列
 * - 按住向下滑动：非课程行从 0 生长到指定高度，松手回弹收回
 * - 点击课程磁贴 3D 翻转显示背面详情；长按课程弹出编辑框
 * - 按压磁贴点亮（盖半透明白层）；滑动/点击用系统阈值区分
 * - Win8 磁贴质感：每块色块覆盖一层均匀平面玻璃罩（无渐变无高光，完全平面）
 */
public class TimetableView extends View {

    public interface Listener {
        void onCellClick(int day, int type, String refId, int startMin, int endMin);
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
    private static final float ENTRANCE_DROP_DP = 6f; // 磁贴入场上移距离（小风吹过的轻柔感）
    private long entranceStart = -1L;

    private float downX, downY;
    private boolean moved;
    private final int touchSlop;

    // ---------- 长按编辑（翻转背面只翻回；修改需长按磁贴弹出编辑框） ----------
    private static final long LONG_PRESS_MS = 500;
    private Runnable longPressRunnable;
    private boolean longPressed;
    private int longPressDay;
    private String longPressRefId;

    // ---------- 磁贴按压点亮（Win8：按压时磁贴盖一层半透明白，像玻璃被照亮） ----------
    private String pressedKey;      // 当前按下的磁贴（day|startMin|refId）
    private float pressAlpha = 0f;  // 点亮强度 0~1（松手淡出）
    private ValueAnimator pressAnimator;

    // ---------- 磁贴翻转详情（Win8：点击磁贴翻转到背面显示课程详情） ----------
    /** 翻转实例：支持多磁贴并行翻转（点击新磁贴时旧磁贴自动翻回） */
    private static class FlipAnim {
        String key;         // day|startMin|refId
        final RectF rect = new RectF();
        String time;        // 背面时间文本
        float progress;     // 0~1
        ValueAnimator anim;
        boolean forward;    // true=翻向背面 false=翻回正面
    }
    private final List<FlipAnim> flips = new ArrayList<>();
    private final android.graphics.Camera flipCamera = new android.graphics.Camera();
    private final android.graphics.Matrix flipMatrix = new android.graphics.Matrix();

    // ---------- 双击上浮（双击课程：课表内所有同课程磁贴上浮漂浮 2 秒后落回） ----------
    private static final int DOUBLE_TAP_MS = 300;   // 双击判定窗口（单击翻转延迟同样时长）
    private static final float FLOAT_DUR_MS = 3000f; // 上浮→漂浮→落回全程（3 秒）
    private long lastTapTime;
    private String lastTapKey;
    private String pendingTapKey;      // 挂起的单击（等待双击判定）
    private RenderedCell pendingTapCell;
    private Runnable pendingTapRunnable;
    private String floatCourseRefId;    // 正在上浮的课程 refId（null=无）
    private float floatProgress;        // 0~1
    private long floatStart;            // 动画起始时间（实时抖动用）
    private ValueAnimator floatAnimator;
    private final Map<String, RectF> floatRects = new LinkedHashMap<>(); // 命中格子的 key→rect

    /** 每格独立的随机摆动参数：方向、相位、频率互不相同 */
    private static class FloatParam {
        float phase;   // 起始相位 0~2π
        int dirX;      // ±1：水平初始摆动方向（先左/先右）
        int dirY;      // ±1：垂直初始摆动方向
        float freq;    // 随机频率
    }
    private final Map<String, FloatParam> floatParams = new LinkedHashMap<>();
    private final java.util.Random floatRand = new java.util.Random();

    // ---------- 当前应上课发光（打开App/回前台时，入场波浪动画结束后发光 2 秒） ----------
    private boolean glowPending;        // 等待入场动画结束后发光
    private String glowPendingKey;
    private String glowKey;             // 正在发光的磁贴 key（day|startMin|refId）
    private final RectF glowRect = new RectF();
    private Course glowCourse;
    private float glowProgress;         // 0~1
    private ValueAnimator glowAnimator;
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

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
        cancelFloat(); // 同时结束上浮
        cancelPendingTap(); // 挂起的单击作废
        cancelGlow(); // 同时结束发光
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
        // 入场波浪动画结束后：对当前应上的课发光（如已请求）
        if (glowPending && !entranceRunning) {
            glowPending = false;
            startGlow(glowPendingKey);
        }
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
                    // 翻转/上浮/发光中的磁贴：跳过常规绘制，循环结束后单独绘制（盖在其余磁贴之上）
                    String key = day + "|" + cell.startMin + "|" + cell.refId;
                    if (isFlipping(key) || isFloating(cell.refId)
                            || (glowKey != null && glowKey.equals(key))) {
                        continue;
                    }
                    cellPaint.setColor(c.bgColor);
                    canvas.drawRect(rect, cellPaint); // Metro：直角扁平磁贴
                    drawTileGlass(canvas, rect, 255); // 平面玻璃罩层
                    drawCellText(canvas, rect, c.name, c.teacher == null ? "" : c.teacher, c.textColor, false);
                    // 按压点亮：磁贴原位盖半透明白层（Win8 按压发光，不变形）
                    if (pressedKey != null && pressedKey.equals(key) && pressAlpha > 0f) {
                        drawPressLight(canvas, rect);
                    }
                }
            }
            rowIdx++;
            y += rh;
        }
        if (!entranceRunning) canvas.drawLine(dayAreaX, y, w - pad, y, linePaint); // 底边线同动画期间隐藏
        drawGlowCell(canvas); // 当前应上课发光（光晕在磁贴后，颜色随磁贴配色）
        drawFlippedCell(canvas); // 翻转磁贴最后绘制（3D 翻转显示背面详情）
        drawFloatingCells(canvas); // 双击上浮的磁贴最后绘制（漂浮在最上层）
        if (entranceRunning) postInvalidateOnAnimation(); // 动画期间持续重绘
    }

    /** 按压点亮：磁贴整体盖一层半透明白（约30%），像玻璃被照亮，不改变形状 */
    private void drawPressLight(Canvas canvas, RectF r) {
        int a = (int) (0x4D * pressAlpha); // 0x4D ≈ 30%
        if (a <= 0) return;
        glassPaint.setShader(null);
        glassPaint.setColor(0xFFFFFFFF);
        glassPaint.setAlpha(a);
        canvas.drawRect(r, glassPaint);
    }

    /** 翻转磁贴：Camera 绕 Y 轴翻转 180°，前半正面、后半背面（背面正读显示详情）；翻转全程带拿起放大→放下缩回 */
    private void drawFlippedCell(Canvas canvas) {
        if (flips.isEmpty()) return;
        for (FlipAnim f : flips) {
            if (f.progress <= 0.001f) continue;
            String[] parts = f.key.split("\\|");
            if (parts.length < 3) continue;
            Course c = courseById.get(parts[2]);
            if (c == null) continue;
            RectF r = f.rect;
            if (r.width() <= 0 || r.height() <= 0) continue;
            float deg = f.progress * 180f;
            float scale = flipScale(f.progress); // 拿起放大 → 放下缩回

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
            canvas.scale(scale, scale, r.centerX(), r.centerY()); // 拿起/放下

            cellPaint.setColor(c.bgColor);
            canvas.drawRect(r, cellPaint); // 直角扁平磁贴
            drawTileGlass(canvas, r, 255); // 平面玻璃罩层

            if (deg <= 90f) {
                // 正面：课程名 + 教师
                drawCellText(canvas, r, c.name, c.teacher == null ? "" : c.teacher, c.textColor, false);
            } else {
                // 背面：课程名 + 老师一行 + 时间一行 + 长按编辑
                drawFlipBack(canvas, r, c.name,
                        c.teacher == null ? "" : c.teacher, f.time, c.textColor);
            }
            canvas.restore();
            flipCamera.restore();
        }
    }

    /** 翻转背面排版：课程名（可两行）+ 老师一行 + 时间一行 */
    private void drawFlipBack(Canvas canvas, RectF r, String name, String teacher, String time, int color) {
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
        // 信息区：老师一行、时间一行（分行避免长文本超出格子宽度）
        float infoY = r.top + availH + subBaseSize * 1.15f;
        subPaint.setColor(adjustAlpha(color, 235));
        subPaint.setTextSize(subBaseSize * 0.9f);
        boolean hasTeacher = teacher != null && !teacher.isEmpty();
        if (hasTeacher) {
            canvas.drawText(teacher, r.centerX(), infoY, subPaint);
            infoY += subBaseSize * 1.2f;
        }
        canvas.drawText(time, r.centerX(), infoY, subPaint);
        namePaint.setTextSize(nameBaseSize);
        subPaint.setTextSize(subBaseSize);
    }

    /** 磁贴入场偏移：延迟 = (总行-行) × 行间隔 + (总列-列) × 列间隔，右下先掉、左上后掉。
     *  连续波浪：正弦曲线从原位上浮到顶点再落下——顶点处速度自然归零平滑转向，无折角；
     *  落回前叠加一个短周期正弦，形成轻微过冲回弹。全程连续，等待期停原位 */
    private float cellDropOffset(int rowIdx, int colIdx, int totalRows, int totalCols) {
        float dropPx = ENTRANCE_DROP_DP * getResources().getDisplayMetrics().density;
        long delay = (long) (totalRows - 1 - rowIdx) * ENTRANCE_STAGGER
                + (long) (totalCols - 1 - colIdx) * ENTRANCE_STAGGER_COL;
        long elapsed = SystemClock.uptimeMillis() - entranceStart - delay;
        if (elapsed <= 0L) return 0f; // 等待期磁贴停在原位置，不瞬移到顶点
        float p = Math.min(1f, elapsed / (float) ENTRANCE_DUR);
        float wave = (float) Math.sin(Math.PI * p); // 0 → 顶点(1) → 0，顶点平滑
        if (p > 0.6f) {
            float q = (p - 0.6f) / 0.4f;
            wave += 0.08f * (float) Math.sin(Math.PI * q); // 落回前轻微过冲回弹
        }
        return -dropPx * wave;
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
                longPressed = false;
                cancelFloat(); // 触摸时结束上浮
                cancelAnim();
                pressDown(event.getX(), event.getY());
                scheduleLongPress(event.getX(), event.getY());
                break;
            case MotionEvent.ACTION_MOVE:
                if (!moved) {
                    float dx = event.getX() - downX;
                    float dy = event.getY() - downY;
                    if (dx * dx + dy * dy > touchSlop * touchSlop) {
                        moved = true;
                        cancelLongPressTimer();
                        cancelPendingTap(); // 转为滑动：挂起的单击作废
                        pressRelease(true); // 转为滑动/下拉，取消按压
                        resetFlip();        // 布局将变化，同时复位翻转磁贴
                        cancelFloat();      // 滑动/下拉时同样结束上浮
                        cancelGlow();       // 布局变化，结束发光
                    }
                }
                if (moved) {
                    // 下拉展开非课程项（0~1），跟随手指
                    float dy = event.getY() - downY;
                    nonCourseReveal = Math.max(0f, Math.min(1f, dy / dp(150)));
                    invalidate();
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                cancelLongPressTimer();
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
                } else if (longPressed) {
                    // 长按已弹出编辑框：本次抬起不再触发点击/翻转
                    pressRelease(true);
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

    /** 长按课程磁贴：500ms 后弹出编辑框（不触发翻转） */
    private void scheduleLongPress(float x, float y) {
        String k = hitTest(x, y);
        if (k == null) return;
        String[] parts = k.split("\\|");
        if (parts.length < 3) return;
        longPressDay = Integer.parseInt(parts[0]);
        longPressRefId = parts[2];
        longPressRunnable = () -> {
            if (moved) return;
            longPressed = true;
            pressRelease(true);
            if (listener != null) listener.onCellClick(longPressDay, TimetableEngine.TYPE_COURSE, longPressRefId, 0, 0);
        };
        postDelayed(longPressRunnable, LONG_PRESS_MS);
    }

    private void cancelLongPressTimer() {
        if (longPressRunnable != null) {
            removeCallbacks(longPressRunnable);
            longPressRunnable = null;
        }
    }

    /** 按下：命中课程磁贴则点亮（盖半透明白层），不改变磁贴形状 */
    private void pressDown(float x, float y) {
        pressRelease(true);
        String key = hitTest(x, y);
        if (key == null) return;
        pressedKey = key;
        pressAlpha = 1f;
        invalidate();
    }

    /** 松手/取消：点亮淡出（immediate=true 直接熄灭，如转为滑动） */
    private void pressRelease(boolean immediate) {
        if (pressedKey == null && pressAlpha <= 0f) return;
        if (immediate) {
            pressedKey = null;
            pressAlpha = 0f;
            invalidate();
            return;
        }
        if (pressAnimator != null) pressAnimator.cancel();
        float from = pressAlpha;
        pressAnimator = ValueAnimator.ofFloat(from, 0f);
        pressAnimator.setDuration(140);
        pressAnimator.setInterpolator(new DecelerateInterpolator(2.0f));
        pressAnimator.addUpdateListener(a -> {
            pressAlpha = (float) a.getAnimatedValue();
            invalidate();
        });
        pressAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                pressedKey = null;
                pressAlpha = 0f;
                invalidate();
            }
        });
        pressAnimator.start();
    }

    /** 命中检测：返回课程磁贴 key；非课程带/空白返回 null */
    private String hitTest(float x, float y) {
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
                return day + "|" + cell.startMin + "|" + cell.refId;
            }
            acc += rh;
        }
        return null;
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
        long now = SystemClock.uptimeMillis();
        boolean isDouble = now - lastTapTime <= DOUBLE_TAP_MS && lastTapKey != null && lastTapKey.equals(key);
        if (isDouble) {
            // 双击同一课程格：取消第一击的延迟翻转，课表内所有同课程磁贴上浮
            cancelPendingTap();
            lastTapTime = 0;
            lastTapKey = null;
            triggerFloat(cell.refId);
            return;
        }
        lastTapTime = now;
        lastTapKey = key;
        if (pendingTapRunnable != null) {
            // 300ms 内点到不同位置：先补执行上一击的单击翻转，再挂起本击
            Runnable prev = pendingTapRunnable;
            cancelPendingTap();
            prev.run();
        }
        // 单击延迟执行：等待 300ms 内的第二击（双击时第一下不翻转）
        pendingTapKey = key;
        pendingTapCell = cell;
        pendingTapRunnable = () -> {
            pendingTapKey = null;
            pendingTapCell = null;
            pendingTapRunnable = null; // 执行后必须清空引用，避免被误判为"挂起中的上一击"
            singleTapAction(key, cell, day);
        };
        postDelayed(pendingTapRunnable, DOUBLE_TAP_MS);
    }

    /** 单击动作：翻转磁贴看详情（已翻转的则翻回；其他已翻转的自动翻回） */
    private void singleTapAction(String key, RenderedCell cell, int day) {
        FlipAnim f = findFlip(key);
        if (f != null && f.forward && f.progress >= 1f) {
            // 已翻转到背面：点击只翻回正面（修改请长按磁贴）
            flipBack(key);
            return;
        }
        // 点击新磁贴：之前已完成翻转的磁贴自动翻回（带动画）
        for (FlipAnim other : new ArrayList<>(flips)) {
            if (other.forward && other.progress >= 1f) flipBack(other.key);
        }
        // 课程磁贴：翻转到背面显示详情（拿起放大 → 翻转 → 放下缩回）
        pressRelease(true);
        startFlip(key, cell, day);
    }

    /** 取消挂起的单击（滑动/切换课表/双击判定成功时） */
    private void cancelPendingTap() {
        if (pendingTapRunnable != null) {
            removeCallbacks(pendingTapRunnable);
            pendingTapRunnable = null;
            pendingTapKey = null;
            pendingTapCell = null;
        }
    }

    /** 开始翻转指定磁贴（400ms：先放大拿起，翻转 180°，再缩小放下） */
    private void startFlip(String key, RenderedCell cell, int day) {
        // 若该磁贴已有翻转实例（如正在翻回中又被点击），先取消移除，避免新旧翻转叠加
        FlipAnim exist = findFlip(key);
        if (exist != null) {
            if (exist.anim != null) exist.anim.cancel();
            flips.remove(exist);
        }
        if (glowKey != null && glowKey.equals(key)) cancelGlow(); // 翻转的磁贴结束发光
        RectF r = new RectF();
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
                    r.set(x + 2, acc + 1, x + colW - 2, acc + rh - 1);
                }
            }
            acc += rh;
        }
        FlipAnim f = new FlipAnim();
        f.key = key;
        f.rect.set(r);
        f.time = fmt(cell.startMin) + "-" + fmt(cell.endMin);
        f.forward = true;
        f.progress = 0f;
        f.anim = ValueAnimator.ofFloat(0f, 1f);
        f.anim.setDuration(400);
        f.anim.setInterpolator(new DecelerateInterpolator(2.0f));
        f.anim.addUpdateListener(a -> {
            f.progress = (float) a.getAnimatedValue();
            invalidate();
        });
        flips.add(f);
        f.anim.start();
    }

    /** 翻回正面（从背面，带动画） */
    private void flipBack(String key) {
        FlipAnim f = findFlip(key);
        if (f == null) return;
        if (f.anim != null) f.anim.cancel();
        f.forward = false;
        float from = f.progress;
        f.anim = ValueAnimator.ofFloat(from, 0f);
        f.anim.setDuration(400);
        f.anim.setInterpolator(new DecelerateInterpolator(2.0f));
        f.anim.addUpdateListener(a -> {
            f.progress = (float) a.getAnimatedValue();
            invalidate();
        });
        f.anim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                flips.remove(f);
                invalidate();
            }
        });
        f.anim.start();
    }

    private FlipAnim findFlip(String key) {
        for (FlipAnim f : flips) if (f.key.equals(key)) return f;
        return null;
    }

    private boolean isFlipping(String key) {
        FlipAnim f = findFlip(key);
        return f != null && f.progress > 0.001f;
    }

    /** 复位翻转状态（数据刷新/滑动/下拉时） */
    private void resetFlip() {
        for (FlipAnim f : flips) if (f.anim != null) f.anim.cancel();
        flips.clear();
    }

    // ---------- 双击上浮：课表内所有同课程磁贴上浮漂浮 2 秒后落回 ----------

    /** 触发上浮：收集所有该课程磁贴的矩形，开始漂浮动画 */
    private void triggerFloat(String courseRefId) {
        if (courseRefId == null) return;
        cancelFloat();
        cancelGlow(); // 上浮时结束发光
        resetFlip(); // 翻转中的磁贴复位
        floatRects.clear();
        floatParams.clear();
        float acc = headerH + pad;
        float dayAreaX = timeAxisW + pad;
        for (Row row : rows) {
            float rh = isBandRow(row) ? bandH : rowH;
            if (rh <= 0) { acc += rh; continue; }
            for (int idx = 0; idx < days.size(); idx++) {
                int d = days.get(idx);
                RenderedCell c = row.dayCells.get(d);
                if (c == null || c.type == TimetableEngine.TYPE_NONCOURSE) continue;
                if (courseRefId.equals(c.refId)) {
                    String k = d + "|" + c.startMin + "|" + c.refId;
                    RectF r = new RectF();
                    r.set(dayAreaX + idx * colW + 2, acc + 1, dayAreaX + idx * colW + colW - 2, acc + rh - 1);
                    floatRects.put(k, r);
                    // 每格独立随机：先左/先右、相位、频率各不相同
                    FloatParam p = new FloatParam();
                    p.phase = floatRand.nextFloat() * (float) (Math.PI * 2);
                    p.dirX = floatRand.nextBoolean() ? 1 : -1;
                    p.dirY = floatRand.nextBoolean() ? 1 : -1;
                    p.freq = 1.8f + floatRand.nextFloat() * 1.2f; // 1.8~3.0 rad/s
                    floatParams.put(k, p);
                }
            }
            acc += rh;
        }
        if (floatRects.isEmpty()) return;
        floatCourseRefId = courseRefId;
        floatProgress = 0f;
        floatStart = SystemClock.uptimeMillis();
        floatAnimator = ValueAnimator.ofFloat(0f, 1f);
        floatAnimator.setDuration((long) FLOAT_DUR_MS);
        floatAnimator.setInterpolator(null); // 线性：上浮/落回由分段曲线控制
        floatAnimator.addUpdateListener(a -> {
            floatProgress = (float) a.getAnimatedValue();
            invalidate();
        });
        floatAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                floatCourseRefId = null;
                floatRects.clear();
                invalidate();
            }
        });
        floatAnimator.start();
    }

    /** 立即结束上浮（触摸开始/数据刷新等） */
    private void cancelFloat() {
        if (floatAnimator != null) {
            floatAnimator.cancel();
            floatAnimator = null;
        }
        floatCourseRefId = null;
        floatRects.clear();
        floatParams.clear();
        invalidate();
    }

    private boolean isFloating(String refId) {
        return floatCourseRefId != null && floatCourseRefId.equals(refId);
    }

    /** 上浮高度系数：0~5% 快速浮起，5%~90% 保持漂浮，90%~100% 落回 */
    private float floatLift(float p) {
        float up = p < 0.05f ? p / 0.05f : 1f;
        float down = p > 0.90f ? (1f - p) / 0.10f : 1f;
        return Math.min(up, down);
    }

    /** 浮起磁贴：所有同课程磁贴最后绘制，上浮放大 + 每格独立随机摆动（幅度轻） + 极淡投影 */
    private void drawFloatingCells(Canvas canvas) {
        if (floatCourseRefId == null || floatRects.isEmpty()) return;
        Course c = courseById.get(floatCourseRefId);
        if (c == null) return;
        float lift = floatLift(floatProgress);
        if (lift <= 0.001f) return;
        float density = getResources().getDisplayMetrics().density;
        float liftPx = 18f * density * lift;              // 上浮高度
        float scale = 1f + 0.08f * lift;                  // 放大 8%
        float t = (SystemClock.uptimeMillis() - floatStart) / 1000f; // 实时时间驱动摆动
        for (Map.Entry<String, RectF> e : floatRects.entrySet()) {
            RectF r = e.getValue();
            FloatParam p = floatParams.get(e.getKey());
            if (p == null) continue;
            float dx = (float) (Math.sin(t * p.freq + p.phase) * 0.5f * density * p.dirX * lift);
            float dy = (float) (Math.cos(t * p.freq * 0.8f + p.phase) * 0.4f * density * p.dirY * lift);
            float rot = (float) (Math.sin(t * p.freq * 1.3f + p.phase) * 0.5f * lift); // 轻微旋转摆动(度)
            canvas.save();
            canvas.translate(r.centerX() + dx, r.centerY() - liftPx + dy);
            canvas.rotate(rot);
            canvas.scale(scale, scale);
            cellPaint.setColor(c.bgColor);
            cellPaint.setShadowLayer(5f * density, 0, 2f * density, 0x33000000); // 极淡投影
            canvas.drawRect(-r.width() / 2f, -r.height() / 2f, r.width() / 2f, r.height() / 2f, cellPaint);
            cellPaint.clearShadowLayer();
            drawTileGlass(canvas, new RectF(-r.width() / 2f, -r.height() / 2f, r.width() / 2f, r.height() / 2f), 255);
            drawCellText(canvas, new RectF(-r.width() / 2f, -r.height() / 2f, r.width() / 2f, r.height() / 2f),
                    c.name, c.teacher == null ? "" : c.teacher, c.textColor, false);
            canvas.restore();
        }
        if (floatAnimator != null && floatAnimator.isRunning()) postInvalidateOnAnimation();
    }

    // ---------- 当前应上课发光 ----------

    /** 按系统当前时间查找"正在上的课程"磁贴 key；无（课间/没课/不显示该天）则返回 null */
    private String findCurrentCourseKey() {
        Calendar cal = Calendar.getInstance();
        int weekday = cal.get(Calendar.DAY_OF_WEEK); // 1=周日 2=周一 ... 7=周六
        int tableDay = weekday - 1;                  // 转课程表 day：1=周一 ... 7=周日
        if (tableDay < 1 || !days.contains(tableDay)) return null; // 不含该天（如未开启周末）
        int minutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE);
        for (Row row : rows) {
            RenderedCell c = row.dayCells.get(tableDay);
            if (c != null && c.type == TimetableEngine.TYPE_COURSE
                    && c.startMin <= minutes && minutes < c.endMin) {
                return tableDay + "|" + c.startMin + "|" + c.refId;
            }
        }
        return null;
    }

    /** 打开App/回前台调用：入场波浪动画结束后，对当前应上的课发光（2 秒） */
    public void glowCurrentCourse() {
        String key = findCurrentCourseKey();
        if (key == null) return; // 当前没有正在上的课程
        glowPending = true;
        glowPendingKey = key;
        invalidate(); // 由 onDraw 检测入场动画结束后启动发光
    }

    /** 启动发光（光晕从磁贴后发出，颜色随磁贴配色） */
    private void startGlow(String key) {
        cancelGlow();
        String[] parts = key.split("\\|");
        if (parts.length < 3) return;
        Course c = courseById.get(parts[2]);
        if (c == null) return;
        RectF r = new RectF();
        float acc = headerH + pad;
        float dayAreaX = timeAxisW + pad;
        for (Row row : rows) {
            float rh = isBandRow(row) ? bandH : rowH;
            if (rh <= 0) { acc += rh; continue; }
            for (int idx = 0; idx < days.size(); idx++) {
                int d = days.get(idx);
                RenderedCell cell = row.dayCells.get(d);
                if (cell == null) continue;
                if (key.equals(d + "|" + cell.startMin + "|" + cell.refId)) {
                    float x = dayAreaX + idx * colW;
                    r.set(x + 2, acc + 1, x + colW - 2, acc + rh - 1);
                }
            }
            acc += rh;
        }
        if (r.isEmpty()) return;
        glowKey = key;
        glowRect.set(r);
        glowCourse = c;
        glowProgress = 0f;
        glowAnimator = ValueAnimator.ofFloat(0f, 1f);
        glowAnimator.setDuration(2000); // 持续 2 秒
        glowAnimator.setInterpolator(null);
        glowAnimator.addUpdateListener(a -> {
            glowProgress = (float) a.getAnimatedValue();
            invalidate();
        });
        glowAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                glowKey = null;
                invalidate();
            }
        });
        glowAnimator.start();
    }

    /** 立即结束发光 */
    private void cancelGlow() {
        if (glowAnimator != null) {
            glowAnimator.cancel();
            glowAnimator = null;
        }
        glowKey = null;
        glowPending = false;
        glowPendingKey = null;
        invalidate();
    }

    /** 发光强度包络：0~12% 快速亮起，12%~85% 保持，85%~100% 淡出 */
    private float glowIntensity(float p) {
        if (p < 0.12f) return p / 0.12f;
        if (p > 0.85f) return (1f - p) / 0.15f;
        return 1f;
    }

    /** 发光磁贴：光晕先画在磁贴后（径向渐变，颜色随磁贴配色），再画磁贴本体（轻微提亮） */
    private void drawGlowCell(Canvas canvas) {
        if (glowKey == null || glowCourse == null || glowRect.isEmpty()) return;
        float intensity = glowIntensity(glowProgress);
        if (intensity <= 0.01f) return;
        float density = getResources().getDisplayMetrics().density;
        RectF r = glowRect;
        float cx = r.centerX(), cy = r.centerY();
        float radius = (float) Math.hypot(r.width(), r.height()) / 2f + 16f * density;
        int base = glowCourse.bgColor;
        int centerColor = (base & 0x00FFFFFF) | ((int) (170 * intensity) << 24);
        int edgeColor = (base & 0x00FFFFFF); // 边缘透明
        glowPaint.setShader(new RadialGradient(cx, cy, radius,
                new int[]{centerColor, centerColor, edgeColor},
                new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, cy, radius, glowPaint);
        glowPaint.setShader(null);
        // 磁贴本体：发光状态轻微提亮（光源从磁贴后发出）
        cellPaint.setColor(glowCourse.bgColor);
        canvas.drawRect(r, cellPaint);
        glassPaint.setShader(null);
        glassPaint.setColor(0xFFFFFFFF);
        glassPaint.setAlpha((int) (38 * intensity));
        canvas.drawRect(r, glassPaint);
        drawTileGlass(canvas, r, 255);
        drawCellText(canvas, r, glowCourse.name,
                glowCourse.teacher == null ? "" : glowCourse.teacher, glowCourse.textColor, false);
    }

    /** 翻转缩放曲线：0~12% 拿起放大(1→1.18)，12%~85% 保持，85%~100% 放下缩回(1.18→1) */
    private float flipScale(float p) {
        if (p <= 0.12f) return 1f + 0.18f * (p / 0.12f);
        if (p >= 0.85f) return 1.18f - 0.18f * ((p - 0.85f) / 0.15f);
        return 1.18f;
    }

    private int adjustAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }
}
