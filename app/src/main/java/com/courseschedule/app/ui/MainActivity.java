package com.courseschedule.app.ui;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.courseschedule.app.data.AppData;
import com.courseschedule.app.data.Course;
import com.courseschedule.app.data.NonCourseItem;
import com.courseschedule.app.data.ScheduleEntry;
import com.courseschedule.app.data.TimetableEngine;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private AppData data;
    private TimetableView timetable;
    private FrameLayout root;
    private FrameLayout timetableHost; // 课表容器：左右切换时相邻课程表叠加其中
    private TimetableView neighbor;     // 拖动中的相邻课程表视图
    private int neighborDir = 0;        // 拖动方向：1=下一张(在右)，-1=上一张(在左)
    private boolean neighborEdge = false; // 已到边界（无相邻课表，拖拽带阻尼）
    private boolean dragging = false;
    private LinearLayout emptyView;
    private TextView titleView, emptyTitle, emptyHint;
    private TextView createBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        data = AppData.get(this);
        data.preseedCoursesIfEmpty();
        data.preseedNonCoursesIfEmpty();

        root = new FrameLayout(this);
        root.setBackgroundColor(0xFFF2F2F2);
        setContentView(root);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(content);

        // 顶栏（Metro 风格：蓝色命令栏 + 大号轻量标题）
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(16), dp(14), dp(12), dp(10));
        top.setBackgroundColor(0xFF0078D7);
        content.addView(top);

        TextView title = new TextView(this);
        titleView = title;
        title.setText("课程表");
        title.setTextColor(Color.WHITE);
        title.setTextSize(24);
        title.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL)); // Segoe UI Light 风格
        title.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        top.addView(title);

        TextView settingsBtn = new TextView(this);
        settingsBtn.setText("设置");
        settingsBtn.setTextColor(Color.WHITE);
        settingsBtn.setTextSize(15);
        settingsBtn.setGravity(Gravity.CENTER);
        settingsBtn.setPadding(dp(16), dp(7), dp(16), dp(7));
        settingsBtn.setBackground(metroFlat(0x22FFFFFF)); // 直角扁平按钮
        settingsBtn.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        top.addView(settingsBtn);

        // 课表区（填满可用区域，行高按可用高度均分，尽量一屏放下）
        // host 为课表容器：左右切换时相邻课程表叠加其中，跟随手指进入屏幕
        FrameLayout host = new FrameLayout(this);
        timetableHost = host;
        timetable = new TimetableView(this, null);
        timetable.setListener(new TimetableView.Listener() {
            @Override
            public void onCellClick(int day, int type, String refId, int startMin, int endMin) {
                // 课程：长按触发 → 编辑；非课程：直接编辑
                MainActivity.this.onCellClick(day, type, refId);
            }

            @Override
            public void onHorizontalDrag(float dx) {
                handleDrag(dx);
            }

            @Override
            public void onHorizontalDragEnd(float dx) {
                handleDragEnd(dx);
            }

            @Override
            public void onHorizontalDragCancel() {
                handleDragCancel();
            }
        });
        host.addView(timetable, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0);
        tl.weight = 1;
        content.addView(host, tl);

        // 底部提示
        TextView footer = new TextView(this);
        footer.setText("点击课程翻转查看详情 · 长按课程修改 · 按住下拉查看非课程项 · 左右滑动切换课程表");
        footer.setTextColor(0xFF666666);
        footer.setTextSize(12);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(0, dp(6), 0, dp(8));
        content.addView(footer);

        // 空状态
        emptyView = new LinearLayout(this);
        emptyView.setOrientation(LinearLayout.VERTICAL);
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(emptyView);

        TextView emptyTitleTv = new TextView(this);
        emptyTitle = emptyTitleTv;
        emptyTitleTv.setText("还没有课程表");
        emptyTitleTv.setTextColor(0xFF1A1A1A);
        emptyTitleTv.setTextSize(20);
        emptyTitleTv.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        emptyTitleTv.setGravity(Gravity.CENTER);
        emptyView.addView(emptyTitleTv, lpWrap());

        TextView emptyHintTv = new TextView(this);
        emptyHint = emptyHintTv;
        emptyHintTv.setText("点击下方按钮，开始创建你的专属课程表");
        emptyHintTv.setTextColor(0xFF666666);
        emptyHintTv.setTextSize(13);
        emptyHintTv.setGravity(Gravity.CENTER);
        emptyHintTv.setPadding(dp(24), dp(6), dp(24), dp(16));
        emptyView.addView(emptyHintTv, lpWrap());

        TextView createBtnView = new TextView(this);
        createBtn = createBtnView;
        createBtnView.setText("开始创建课程表");
        createBtnView.setTextColor(Color.WHITE);
        createBtnView.setTextSize(16);
        createBtnView.setGravity(Gravity.CENTER);
        createBtnView.setPadding(dp(28), dp(12), dp(28), dp(12));
        createBtnView.setBackground(metroFlat(0xFF0078D7)); // Metro：直角纯色命令按钮
        createBtnView.setOnClickListener(v -> onEmptyAction());
        emptyView.addView(createBtnView, lpWrap());

        refresh();
    }

    private LinearLayout.LayoutParams lpWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private void openSetup() {
        startActivityForResult(new Intent(this, SetupActivity.class), 100);
    }

    private void onEmptyAction() {
        if (data.timetables.isEmpty()) {
            askTimetableName("创建课程表", "", name -> {
                AppData.Timetable t = data.addTimetable(name);
                data.setActiveTimetable(t.id);
                openSetup();
            });
        } else {
            openSetup();
        }
    }

    private void askTimetableName(String title, String def, java.util.function.Consumer<String> onOk) {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(dp(20), dp(6), dp(20), 0);
        EditText et = new EditText(this);
        et.setHint("例如：张三的课程表");
        et.setSingleLine(true);
        et.setText(def);
        wrap.addView(et, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        new MaterialAlertDialogBuilder(this)
                .setTitle(title)
                .setView(wrap)
                .setPositiveButton("确定", (d, w) -> {
                    String name = et.getText().toString().trim();
                    if (name.isEmpty()) name = "我的课程表";
                    onOk.accept(name);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // ---------- 左右切换课程表：邻表跟随手指进入屏幕（ViewPager 式） ----------

    /** 拖拽中：主表跟随手指平移，相邻课程表同步从屏幕外进入（已进入部分不回退） */
    private void handleDrag(float dx) {
        if (!dragging) {
            dragging = true;
            neighborDir = dx > 0 ? -1 : 1; // 往右拖→看上一张(在左)；往左拖→看下一张(在右)
            createNeighbor();
        }
        float w = root.getWidth();
        if (neighborEdge) {
            timetable.setTranslationX(dx * 0.3f); // 已到边界：阻尼跟随
        } else {
            timetable.setTranslationX(dx);
            neighbor.setTranslationX(dx + (neighborDir > 0 ? w : -w)); // 邻表同步进入
        }
    }

    /** 松手：超过阈值则旧课表继续滑出、邻表继续滑入到位；否则一起回弹 */
    private void handleDragEnd(float dx) {
        if (!dragging) return;
        dragging = false;
        float w = root.getWidth();
        float hThresh = Math.max(dp(40), w * 0.2f);
        if (neighborEdge || Math.abs(dx) <= hThresh) {
            // 未达阈值 / 已到边界：主表与邻表回弹
            if (neighbor != null) {
                final TimetableView n = neighbor;
                n.animate().translationX(neighborDir > 0 ? w : -w).setDuration(200)
                        .withEndAction(() -> removeNeighbor(n)).start();
            }
            timetable.animate().translationX(0).setDuration(200).start();
        } else {
            // 超过阈值：主表滑出，邻表从当前位置继续滑入到位
            final int dir = neighborDir; // 用局部固定方向：withEndAction 延时执行时字段已被重置
            float out = neighborDir > 0 ? w : -w;
            timetable.animate().translationX(out).setDuration(200).start();
            neighbor.animate().translationX(0).setDuration(240)
                    .withEndAction(() -> applyTimetableSwitch(dir, w)).start();
        }
        neighborEdge = false;
        neighborDir = 0;
    }

    /** 手势取消：一律回弹 */
    private void handleDragCancel() {
        if (!dragging) return;
        dragging = false;
        float w = root.getWidth();
        if (neighbor != null) {
            final TimetableView n = neighbor;
            n.animate().translationX(neighborDir > 0 ? w : -w).setDuration(200)
                    .withEndAction(() -> removeNeighbor(n)).start();
        }
        timetable.animate().translationX(0).setDuration(200).start();
        neighborEdge = false;
        neighborDir = 0;
    }

    /** 创建相邻课程表视图（渲染目标课表内容，叠加在课表容器中，位于屏幕外） */
    private void createNeighbor() {
        int idx = data.timetableIndex(data.activeTimetableId);
        int ni = idx + neighborDir;
        if (ni < 0 || ni >= data.timetables.size()) {
            neighborEdge = true;
            return;
        }
        if (neighbor != null) removeNeighbor(neighbor);
        TimetableView v = new TimetableView(this, null);
        // 渲染目标课表内容：临时指向其 entries（setData 同步计算 rows 后还原）
        java.util.List<ScheduleEntry> saved = data.entries;
        data.entries = data.timetables.get(ni).entries;
        v.setData(data);
        data.entries = saved;
        v.setVisibility(View.VISIBLE);
        timetableHost.addView(v, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        float w = root.getWidth();
        v.setTranslationX(neighborDir > 0 ? w : -w); // 初始位于拖拽侧的屏幕外
        neighbor = v;
    }

    private void removeNeighbor(TimetableView v) {
        if (v != null && v.getParent() == timetableHost) timetableHost.removeView(v);
        if (neighbor == v) neighbor = null;
    }

    /** 切换完成：主表内容更新为新课程表并复位，移除邻表，播放入场波浪+发光 */
    private void applyTimetableSwitch(int direction, float w) {
        int idx = data.timetableIndex(data.activeTimetableId);
        int ni = idx + direction;
        if (ni >= 0 && ni < data.timetables.size()) {
            data.setActiveTimetable(data.timetables.get(ni).id);
        }
        timetable.setData(data);
        titleView.setText(data.activeTimetable().name);
        updateEmptyState(); // 同步空状态：新课表若未排课，显示"开始排课"入口
        timetable.setTranslationX(0); // 内容已是新课表，主表放回原位（与邻表一致，无跳变）
        removeNeighbor(neighbor);
        if (timetable.getVisibility() == View.VISIBLE) {
            timetable.playEntrance();
            timetable.glowCurrentCourse(); // 左右切换课程表后：当前应上的课同样发光
        }
    }

    /** 按当前激活课程表同步课表/空状态（refresh 与左右切换共用） */
    private void updateEmptyState() {
        boolean any = !data.timetables.isEmpty();
        AppData.Timetable active = data.activeTimetable();
        boolean has = any && data.hasSchedule();
        timetable.setData(data);
        timetable.setVisibility(has ? View.VISIBLE : View.GONE);
        emptyView.setVisibility(has ? View.GONE : View.VISIBLE);
        if (!has) {
            emptyTitle.setText(any && active != null ? "「" + active.name + "」还没有排课" : "还没有课程表");
            emptyHint.setText(any
                    ? "点击下方按钮，为该课程表开始排课\n多孩家庭可在设置中为每个孩子各建一个课程表"
                    : "点击下方按钮，创建第一个课程表\n多孩家庭可为每个孩子各建一个课程表，左右滑动切换");
            createBtn.setText(any ? "开始排课" : "创建课程表");
        }
    }

    private void refresh() {
        data = AppData.get(this);
        if (!data.timetables.isEmpty()) data.ensureTimetable();
        AppData.Timetable active = data.activeTimetable();
        titleView.setText(active != null ? active.name : "课程表");
        updateEmptyState();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
        // 回到前台（后台切回/设置页返回）时重播磁贴入场动画
        if (timetable.getVisibility() == View.VISIBLE) {
            timetable.playEntrance();
            timetable.glowCurrentCourse(); // 入场波浪动画结束后，对当前应上的课发光 2 秒
        }
        // 打开 App 后主动刷新桌面小组件
        try {
            com.courseschedule.app.widget.TimetableWidgetProvider.refreshAll(this);
        } catch (Exception ignored) {
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent dataIntent) {
        super.onActivityResult(requestCode, resultCode, dataIntent);
        refresh();
    }

    private void onCellClick(int day, int type, String refId) {
        if (type == TimetableEngine.TYPE_COURSE) {
            showCourseEditDialog(day, refId);
        } else {
            showNonCourseEditDialog(refId);
        }
    }

    private String fmt(int m) {
        return String.format("%02d:%02d", m / 60, m % 60);
    }

    private void showCourseEditDialog(int day, String refId) {
        final List<Course> list = new ArrayList<>(data.courses);
        final String[] names = new String[list.size()];
        int selected = 0;
        for (int i = 0; i < list.size(); i++) {
            Course c = list.get(i);
            names[i] = c.teacher == null || c.teacher.isEmpty() ? c.name : c.name + " · " + c.teacher;
            if (c.id.equals(refId)) selected = i;
        }
        if (names.length == 0) return;
        final int[] chosen = {selected};
        new MaterialAlertDialogBuilder(this)
                .setTitle("修改课程（仅可选课程项）")
                .setSingleChoiceItems(names, selected, (d, which) -> chosen[0] = which)
                .setNeutralButton("删除该格", (d, w) -> {
                    removeCourseEntry(day, refId);
                    d.dismiss();
                })
                .setPositiveButton("确定", (d, w) -> {
                    Course c = list.get(chosen[0]);
                    replaceCourseEntry(day, refId, c.id);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void replaceCourseEntry(int day, String oldId, String newId) {
        for (ScheduleEntry e : data.entries) {
            if (e.day == day && e.type == TimetableEngine.TYPE_COURSE && e.refId.equals(oldId)) {
                e.refId = newId;
                break;
            }
        }
        data.persist();
        refresh();
    }

    private void removeCourseEntry(int day, String refId) {
        data.entries.removeIf(e -> e.day == day && e.type == TimetableEngine.TYPE_COURSE && e.refId.equals(refId));
        data.persist();
        refresh();
    }

    private void showNonCourseEditDialog(String refId) {
        final List<NonCourseItem> list = new ArrayList<>(data.nonCourses);
        final String[] names = new String[list.size()];
        int selected = 0;
        for (int i = 0; i < list.size(); i++) {
            NonCourseItem n = list.get(i);
            names[i] = n.name + "（" + n.durationMin + "分钟）";
            if (n.id.equals(refId)) selected = i;
        }
        if (names.length == 0) return;
        final int[] chosen = {selected};
        new MaterialAlertDialogBuilder(this)
                .setTitle("修改非课程项（仅可选非课程项）")
                .setSingleChoiceItems(names, selected, (d, which) -> chosen[0] = which)
                .setNeutralButton("删除该格", (d, w) -> {
                    removeNonCourseEntry(refId);
                    d.dismiss();
                })
                .setPositiveButton("确定", (d, w) -> {
                    NonCourseItem n = list.get(chosen[0]);
                    replaceNonCourseEntry(refId, n.id);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void replaceNonCourseEntry(String oldId, String newId) {
        for (ScheduleEntry e : data.entries) {
            if (e.day == 1 && e.type == TimetableEngine.TYPE_NONCOURSE && e.refId.equals(oldId)) {
                e.refId = newId;
                break;
            }
        }
        data.persist();
        refresh();
    }

    private void removeNonCourseEntry(String refId) {
        data.entries.removeIf(e -> e.day == 1 && e.type == TimetableEngine.TYPE_NONCOURSE && e.refId.equals(refId));
        data.persist();
        refresh();
    }

    private int dp(int v) {        return (int) (v * getResources().getDisplayMetrics().density);
    }

    /** Metro 风格：直角纯色块背景（无圆角） */
    private android.graphics.drawable.GradientDrawable metroFlat(int color) {
        android.graphics.drawable.GradientDrawable g =
                new android.graphics.drawable.GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(0);
        return g;
    }
}
