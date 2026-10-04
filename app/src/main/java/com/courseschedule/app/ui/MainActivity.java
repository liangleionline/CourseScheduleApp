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
        timetable.setListener(this::onCellClick);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0);
        tl.weight = 1;
        content.addView(timetable, tl);

        // 底部提示
        TextView footer = new TextView(this);
        footer.setText("点击任意格可编辑 · 非课程项为贯穿全周项目");
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

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }
}
