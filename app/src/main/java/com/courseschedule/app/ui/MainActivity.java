package com.courseschedule.app.ui;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
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
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private AppData data;
    private TimetableView timetable;
    private FrameLayout root;
    private LinearLayout emptyView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        data = AppData.get(this);
        data.preseedCoursesIfEmpty();
        data.preseedNonCoursesIfEmpty();

        root = new FrameLayout(this);
        root.setBackgroundColor(0xFFF6F8FC);
        setContentView(root);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(content);

        // 顶栏
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(16), dp(14), dp(12), dp(10));
        top.setBackgroundColor(0xFF5C6BC0);
        content.addView(top);

        TextView title = new TextView(this);
        title.setText("课程表");
        title.setTextColor(Color.WHITE);
        title.setTextSize(20);
        title.setTypeface(null, Typeface.BOLD);
        title.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        top.addView(title);

        MaterialButton settingsBtn = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        settingsBtn.setText("设置");
        settingsBtn.setTextColor(Color.WHITE);
        settingsBtn.setStrokeColor(android.content.res.ColorStateList.valueOf(0x66FFFFFF));
        settingsBtn.setBackgroundColor(0x22FFFFFF);
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
        footer.setText("点击课程查看详情 · 按住下拉可查看贯穿全周的非课程项");
        footer.setTextColor(0xFF8A94A6);
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

        TextView emptyTitle = new TextView(this);
        emptyTitle.setText("还没有课程表");
        emptyTitle.setTextColor(0xFF3A4151);
        emptyTitle.setTextSize(18);
        emptyTitle.setTypeface(null, Typeface.BOLD);
        emptyTitle.setGravity(Gravity.CENTER);
        emptyView.addView(emptyTitle, lpWrap());

        TextView emptyHint = new TextView(this);
        emptyHint.setText("点击下方按钮，开始创建你的专属课程表");
        emptyHint.setTextColor(0xFF8A94A6);
        emptyHint.setTextSize(13);
        emptyHint.setGravity(Gravity.CENTER);
        emptyHint.setPadding(dp(24), dp(6), dp(24), dp(16));
        emptyView.addView(emptyHint, lpWrap());

        MaterialButton createBtn = new MaterialButton(this);
        createBtn.setText("开始创建课程表");
        createBtn.setTextColor(Color.WHITE);
        createBtn.setBackgroundColor(0xFF5C6BC0);
        createBtn.setOnClickListener(v -> openSetup());
        emptyView.addView(createBtn, lpWrap());

        refresh();
    }

    private LinearLayout.LayoutParams lpWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private void openSetup() {
        startActivityForResult(new Intent(this, SetupActivity.class), 100);
    }

    private void refresh() {
        data = AppData.get(this);
        boolean has = data.hasSchedule();
        timetable.setData(data);
        timetable.setVisibility(has ? View.VISIBLE : View.GONE);
        emptyView.setVisibility(has ? View.GONE : View.VISIBLE);
        hideDetail();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
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
        detailName.setTextColor(0xFF3A4151);
        detailName.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(detailName);

        TextView edit = new TextView(this);
        edit.setText("编辑");
        edit.setTextSize(14);
        edit.setTextColor(0xFF5C6BC0);
        edit.setGravity(Gravity.CENTER);
        edit.setPadding(dp(14), dp(6), dp(14), dp(6));
        edit.setBackground(roundedBg(0xFFEDF1F8));
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
        close.setTextColor(0xFF8A94A6);
        close.setGravity(Gravity.CENTER);
        close.setPadding(dp(10), dp(6), dp(4), dp(6));
        close.setOnClickListener(v -> hideDetail());
        row.addView(close);
        detailPanel.addView(row, lpWrap());

        detailMeta = new TextView(this);
        detailMeta.setTextSize(13);
        detailMeta.setTextColor(0xFF5C6BC0);
        detailMeta.setPadding(0, dp(4), 0, 0);
        detailPanel.addView(detailMeta, lpWrap());

        detailExtra = new TextView(this);
        detailExtra.setTextSize(13);
        detailExtra.setTextColor(0xFF8A94A6);
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

    private android.graphics.drawable.GradientDrawable roundedBg(int color) {
        android.graphics.drawable.GradientDrawable g =
                new android.graphics.drawable.GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(10));
        return g;
    }
}
