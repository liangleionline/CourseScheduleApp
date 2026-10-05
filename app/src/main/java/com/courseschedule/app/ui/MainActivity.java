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

import com.courseschedule.app.R;
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
        timetable = new TimetableView(this, null);
        timetable.setListener(new TimetableView.Listener() {
            @Override
            public void onCellClick(int day, int type, String refId, int startMin, int endMin) {
                showDetail(day, type, refId, startMin, endMin);
            }

            @Override
            public void onPeekStart() {
                hideDetail();
            }

            @Override
            public void onSwipe(int direction) {
                switchTimetable(direction);
            }
        });
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0);
        tl.weight = 1;
        content.addView(timetable, tl);

        // 课程详情面板（底部，默认隐藏）
        detailPanel = new LinearLayout(this);
        detailPanel.setOrientation(LinearLayout.VERTICAL);
        detailPanel.setVisibility(View.GONE);
        detailPanel.setPadding(dp(16), dp(10), dp(16), dp(12));
        detailPanel.setBackgroundColor(0xFFFFFFFF);
        content.addView(detailPanel, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        buildDetailPanel();

        // 底部提示
        TextView footer = new TextView(this);
        footer.setText("点击课程查看详情 · 按住下拉查看非课程项 · 左右滑动切换课程表");
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

    private void switchTimetable(int direction) {
        int idx = data.timetableIndex(data.activeTimetableId);
        if (idx < 0) return;
        int ni = idx + direction;
        if (ni < 0 || ni >= data.timetables.size()) return;
        data.setActiveTimetable(data.timetables.get(ni).id);
        hideDetail();
        timetable.setData(data);
        titleView.setText(data.activeTimetable().name);
        float w = root.getWidth();
        timetable.setTranslationX(direction > 0 ? -w : w);
        // 滑入到位后再播放磁贴入场动画，避免与平移重叠
        timetable.animate().translationX(0).setDuration(240)
                .withEndAction(() -> timetable.playEntrance()).start();
    }

    private void refresh() {
        data = AppData.get(this);
        boolean any = !data.timetables.isEmpty();
        if (any) data.ensureTimetable();
        AppData.Timetable active = data.activeTimetable();
        titleView.setText(any && active != null ? active.name : "课程表");
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
        hideDetail();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
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

    // ---------- 课程详情面板 ----------
    private LinearLayout detailPanel;
    private TextView detailName, detailMeta, detailExtra;
    private int detailDay, detailType;
    private String detailRefId;

    private void buildDetailPanel() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        detailName = new TextView(this);
        detailName.setTextSize(17);
        detailName.setTypeface(null, Typeface.BOLD);
        detailName.setTextColor(0xFF1A1A1A);
        detailName.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(detailName);

        TextView edit = new TextView(this);
        edit.setText("编辑");
        edit.setTextSize(14);
        edit.setTextColor(0xFF0078D7);
        edit.setGravity(Gravity.CENTER);
        edit.setPadding(dp(14), dp(6), dp(14), dp(6));
        edit.setBackground(roundedBg(0xFFE5F1FB));
        edit.setOnClickListener(v -> {
            if (detailType == TimetableEngine.TYPE_COURSE) {
                showCourseEditDialog(detailDay, detailRefId);
            } else {
                showNonCourseEditDialog(detailRefId);
            }
        });
        row.addView(edit);

        TextView close = new TextView(this);
        close.setText("✕");
        close.setTextSize(16);
        close.setTextColor(0xFF666666);
        close.setGravity(Gravity.CENTER);
        close.setPadding(dp(10), dp(6), dp(4), dp(6));
        close.setOnClickListener(v -> hideDetail());
        row.addView(close);
        detailPanel.addView(row, lpWrap());

        detailMeta = new TextView(this);
        detailMeta.setTextSize(13);
        detailMeta.setTextColor(0xFF0078D7);
        detailMeta.setPadding(0, dp(4), 0, 0);
        detailPanel.addView(detailMeta, lpWrap());

        detailExtra = new TextView(this);
        detailExtra.setTextSize(13);
        detailExtra.setTextColor(0xFF666666);
        detailExtra.setPadding(0, dp(4), 0, 0);
        detailPanel.addView(detailExtra, lpWrap());
    }

    private void showDetail(int day, int type, String refId, int startMin, int endMin) {
        detailDay = day;
        detailType = type;
        detailRefId = refId;
        String name = "";
        String meta = "";
        String extra = "";
        if (type == TimetableEngine.TYPE_COURSE) {
            Course c = data.getCourse(refId);
            if (c != null) {
                name = c.name;
                meta = c.teacher == null || c.teacher.isEmpty() ? "暂无教师" : "教师：" + c.teacher;
            }
        } else {
            NonCourseItem n = data.getNonCourse(refId);
            if (n != null) name = n.name;
        }
        String week = WEEK_NAMES[day - 1];
        String time = fmt(startMin) + " - " + fmt(endMin);
        extra = week + " · " + time + "（非课程项为贯穿全周项目）";
        if (type == TimetableEngine.TYPE_COURSE) {
            extra = week + " · " + time;
        }
        detailName.setText(name);
        detailMeta.setText(meta);
        detailExtra.setText(extra);
        detailPanel.setVisibility(View.VISIBLE);
    }

    private void hideDetail() {
        detailPanel.setVisibility(View.GONE);
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

    private static final String[] WEEK_NAMES = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};

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

    private android.graphics.drawable.GradientDrawable roundedBg(int color) {
        android.graphics.drawable.GradientDrawable g =
                new android.graphics.drawable.GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(0); // Metro：直角扁平
        return g;
    }
}
