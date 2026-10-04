package com.courseschedule.app.ui;

import android.app.TimePickerDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.TimePicker;

import androidx.appcompat.app.AppCompatActivity;

import com.courseschedule.app.data.AppData;
import com.courseschedule.app.data.ColorUtil;
import com.courseschedule.app.data.Course;
import com.courseschedule.app.data.NonCourseItem;
import com.courseschedule.app.data.ScheduleEntry;
import com.courseschedule.app.data.TimetableEngine;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 首次引导：4 步配置
 * 1 课程项库 -> 2 全局课时时长 -> 3 非课程项库 -> 4 排布周一~周五课表
 */
public class SetupActivity extends AppCompatActivity {

    private static final String[] DAY_NAMES = {"周一", "周二", "周三", "周四", "周五"};

    private AppData data;
    private LinearLayout root;
    private LinearLayout body;
    private int step = 1;
    private int scheduleDay = 1; // 当前排布的天

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        data = AppData.get(this);
        data.preseedCoursesIfEmpty();
        data.preseedNonCoursesIfEmpty();

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFFF6F8FC);
        setContentView(root);
        render();
    }

    private void render() {
        root.removeAllViews();
        root.setPadding(dp(16), dp(18), dp(16), dp(16));
        TextView stepTitle = new TextView(this);
        stepTitle.setText(stepTitle());
        stepTitle.setTextColor(0xFF3A4151);
        stepTitle.setTextSize(20);
        stepTitle.setTypeface(null, Typeface.BOLD);
        root.addView(stepTitle);

        TextView stepSub = new TextView(this);
        stepSub.setText(stepSub());
        stepSub.setTextColor(0xFF8A94A6);
        stepSub.setTextSize(13);
        stepSub.setPadding(0, dp(4), 0, dp(8));
        root.addView(stepSub);

        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);

        // 固定操作栏：仅步骤④使用，保持在顶部、不随内容滚动
        LinearLayout actionBar = null;
        if (step == 4) {
            actionBar = new LinearLayout(this);
            actionBar.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            alp.bottomMargin = dp(8);
            root.addView(actionBar, alp);
        }

        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        sv.addView(body);
        root.addView(sv);

        if (step == 1) renderStep1();
        else if (step == 2) renderStep2();
        else if (step == 3) renderStep3();
        else renderStep4(actionBar);

        // 底部操作
        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        bottom.setGravity(Gravity.END);
        bottom.setPadding(0, dp(8), 0, 0);
        root.addView(bottom);

        if (step == 4) {
            MaterialButton prev = new MaterialButton(this);
            prev.setText("返回");
            prev.setBackgroundColor(0xFFEDF1F8);
            prev.setTextColor(0xFF5C6BC0);
            prev.setOnClickListener(v -> { scheduleDay--; render(); });
            prev.setEnabled(scheduleDay > 1);
            bottom.addView(prev, lpMargins(0, 0, dp(8), 0));
        }

        MaterialButton next = new MaterialButton(this);
        if (step == 4 && scheduleDay >= 5) {
            next.setText("结束");
        } else if (step == 4) {
            next.setText("下一天");
        } else {
            next.setText("下一步");
        }
        next.setTextColor(Color.WHITE);
        next.setBackgroundColor(0xFF5C6BC0);
        next.setOnClickListener(v -> onNext());
        bottom.addView(next, lpWrap());
    }

    private String stepTitle() {
        switch (step) {
            case 1: return "① 设置课程";
            case 2: return "② 设置每节课时长";
            case 3: return "③ 设置非课程项";
            default: return "④ 排布" + DAY_NAMES[scheduleDay - 1] + "课表";
        }
    }

    private String stepSub() {
        switch (step) {
            case 1: return "课程名称必填，任课教师选填；可在设置中随时增删改";
            case 2: return "全局通用单节时长，所有课程共用（分钟）";
            case 3: return "非课程项为贯穿全周项目，周一排布一次，整周同一时段自动显示，无需每天重复";
            default:
                if (scheduleDay == 1) return "首个项目需选择开始时间，其后时间自动接续";
                return "时间自动复用周一起始，非课程项已自动贯穿，仅需选择课程";
        }
    }

    // ---------- 步骤1：课程库 ----------
    private void renderStep1() {
        MaterialButton add = new MaterialButton(this);
        add.setText("＋ 添加课程");
        add.setTextColor(Color.WHITE);
        add.setBackgroundColor(0xFF5C6BC0);
        add.setOnClickListener(v -> showCourseEditDialog(null));
        body.addView(add, lpMargins(0, 0, 0, dp(10)));

        if (data.courses.isEmpty()) {
            body.addView(hintText("暂无课程，请点击上方按钮添加"));
            return;
        }
        for (Course c : data.courses) {
            body.addView(courseRow(c));
        }
    }

    private View courseRow(Course c) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(10), dp(8), dp(10));
        row.setBackgroundColor(0xFFFFFFFF);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.bottomMargin = dp(6);
        row.setLayoutParams(rlp);

        View dot = new View(this);
        dot.setBackgroundColor(c.bgColor);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(dp(18), dp(18));
        row.addView(dot, dlp);

        TextView tv = new TextView(this);
        tv.setText(c.teacher == null || c.teacher.isEmpty() ? c.name : c.name + "\n" + c.teacher);
        tv.setTextColor(0xFF3A4151);
        tv.setTextSize(15);
        tv.setPadding(dp(10), 0, 0, 0);
        row.addView(tv, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        MaterialButton edit = smallBtn("改");
        edit.setOnClickListener(v -> showCourseEditDialog(c));
        row.addView(edit, lpMargins(0, 0, dp(4), 0));

        MaterialButton del = smallBtn("删");
        del.setTextColor(0xFFEF5350);
        del.setOnClickListener(v -> {
            data.deleteCourse(c.id);
            render();
        });
        row.addView(del, lpWrap());
        return row;
    }

    private void showCourseEditDialog(final Course existing) {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(dp(20), dp(6), dp(20), 0);

        EditText name = new EditText(this);
        name.setHint("课程名称（必填）");
        name.setSingleLine(true);
        wrap.addView(name, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        EditText teacher = new EditText(this);
        teacher.setHint("任课教师（选填）");
        teacher.setSingleLine(true);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tl.topMargin = dp(8);
        wrap.addView(teacher, tl);

        if (existing != null) { name.setText(existing.name); teacher.setText(existing.teacher); }

        new MaterialAlertDialogBuilder(this)
                .setTitle(existing == null ? "添加课程" : "修改课程")
                .setView(wrap)
                .setPositiveButton("保存", (d, w) -> {
                    String n = name.getText().toString().trim();
                    if (n.isEmpty()) {
                        toast("课程名称不能为空");
                        return;
                    }
                    if (existing == null) data.addCourse(n, teacher.getText().toString().trim());
                    else { existing.name = n; existing.teacher = teacher.getText().toString().trim(); data.persist(); }
                    render();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // ---------- 步骤2：全局课时时长 ----------
    private void renderStep2() {
        LinearLayout card = card();
        EditText et = new EditText(this);
        et.setInputType(InputType.TYPE_CLASS_NUMBER);
        et.setText(String.valueOf(data.lessonDurationMin));
        et.setHint("每节课时长（分钟）");
        et.setTextColor(0xFF3A4151);
        et.setTextSize(18);
        card.addView(et, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView note = hintText("所有课程统一使用该时长，例如 45 分钟");
        body.addView(note);
        pendingEt = et;
    }

    private EditText pendingEt;

    // ---------- 步骤3：非课程库 ----------
    private void renderStep3() {
        TextView tip = new TextView(this);
        tip.setText("💡 提示：非课程项为贯穿全周项目，周一排布一次即可，周二至周五自动显示。");
        tip.setTextColor(0xFF5C6BC0);
        tip.setTextSize(13);
        tip.setPadding(0, 0, 0, dp(8));
        body.addView(tip);

        MaterialButton add = new MaterialButton(this);
        add.setText("＋ 添加非课程项");
        add.setTextColor(Color.WHITE);
        add.setBackgroundColor(0xFF5C6BC0);
        add.setOnClickListener(v -> showNonCourseEditDialog(null));
        body.addView(add, lpMargins(0, 0, 0, dp(10)));

        if (data.nonCourses.isEmpty()) {
            body.addView(hintText("暂无非课程项，请点击上方按钮添加"));
            return;
        }
        for (NonCourseItem n : data.nonCourses) {
            body.addView(nonCourseRow(n));
        }
    }

    private View nonCourseRow(NonCourseItem n) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(10), dp(8), dp(10));
        row.setBackgroundColor(0xFFFFFFFF);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.bottomMargin = dp(6);
        row.setLayoutParams(rlp);

        View dot = new View(this);
        dot.setBackgroundColor(ColorUtil.NONCOURSE_BG);
        row.addView(dot, new LinearLayout.LayoutParams(dp(18), dp(18)));

        TextView tv = new TextView(this);
        tv.setText(n.name + "（" + n.durationMin + "分钟）");
        tv.setTextColor(0xFF3A4151);
        tv.setTextSize(15);
        tv.setPadding(dp(10), 0, 0, 0);
        row.addView(tv, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        MaterialButton edit = smallBtn("改");
        edit.setOnClickListener(v -> showNonCourseEditDialog(n));
        row.addView(edit, lpMargins(0, 0, dp(4), 0));

        MaterialButton del = smallBtn("删");
        del.setTextColor(0xFFEF5350);
        del.setOnClickListener(v -> {
            data.deleteNonCourse(n.id);
            render();
        });
        row.addView(del, lpWrap());
        return row;
    }

    private void showNonCourseEditDialog(final NonCourseItem existing) {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(dp(20), dp(6), dp(20), 0);

        EditText name = new EditText(this);
        name.setHint("项目名称");
        name.setSingleLine(true);
        wrap.addView(name, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        EditText dur = new EditText(this);
        dur.setHint("时长（分钟）");
        dur.setInputType(InputType.TYPE_CLASS_NUMBER);
        dur.setSingleLine(true);
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dl.topMargin = dp(8);
        wrap.addView(dur, dl);

        if (existing != null) { name.setText(existing.name); dur.setText(String.valueOf(existing.durationMin)); }

        new MaterialAlertDialogBuilder(this)
                .setTitle(existing == null ? "添加非课程项" : "修改非课程项")
                .setView(wrap)
                .setPositiveButton("保存", (d, w) -> {
                    String n = name.getText().toString().trim();
                    if (n.isEmpty()) { toast("项目名称不能为空"); return; }
                    int mins;
                    try { mins = Integer.parseInt(dur.getText().toString().trim()); }
                    catch (Exception e) { mins = 10; }
                    if (mins <= 0) mins = 10;
                    if (existing == null) data.addNonCourse(n, mins);
                    else { existing.name = n; existing.durationMin = mins; data.persist(); }
                    render();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // ---------- 步骤4：排布课表 ----------
    private void renderStep4(LinearLayout actionBar) {
        // 固定操作栏：添加按钮，不随内容滚动，始终可点
        boolean firstMonday = scheduleDay == 1 && mondayEmpty();
        MaterialButton add = new MaterialButton(this);
        if (scheduleDay == 1 && firstMonday) {
            add.setText("＋ 添加今日首个项目（需选开始时间）");
        } else if (scheduleDay == 1) {
            add.setText("＋ 继续添加剩余项目");
        } else {
            add.setText("＋ 添加课程");
        }
        add.setTextColor(Color.WHITE);
        add.setBackgroundColor(0xFF5C6BC0);
        add.setOnClickListener(v -> showAddItemDialog());
        actionBar.addView(add, lpWrap());

        // 周一模板预览（贯穿全周的非课程项说明）
        if (scheduleDay > 1) {
            TextView auto = new TextView(this);
            auto.setText("自动贯穿全周：");
            auto.setTextColor(0xFF8A94A6);
            auto.setTextSize(13);
            body.addView(auto);
            StringBuilder sb = new StringBuilder();
            for (ScheduleEntry e : data.entries) {
                if (e.day == 1 && e.type == TimetableEngine.TYPE_NONCOURSE) {
                    NonCourseItem n = data.getNonCourse(e.refId);
                    if (n != null) { if (sb.length() > 0) sb.append("、"); sb.append(n.name); }
                }
            }
            if (sb.length() == 0) sb.append("（无）");
            TextView autoList = new TextView(this);
            autoList.setText(sb.toString());
            autoList.setTextColor(0xFF3A4151);
            autoList.setTextSize(14);
            autoList.setPadding(0, dp(2), 0, dp(10));
            body.addView(autoList);
        }

        List<ScheduleEntry> dayEntries = new ArrayList<>();
        for (ScheduleEntry e : data.entries) {
            if ((scheduleDay == 1 && e.day == 1) || (scheduleDay > 1 && e.day == scheduleDay && e.type == TimetableEngine.TYPE_COURSE)) {
                dayEntries.add(e);
            }
        }
        if (dayEntries.isEmpty()) {
            body.addView(hintText(scheduleDay == 1
                    ? "本日暂无项目，点击上方按钮添加，可从课程项或非课程项开始"
                    : "本日暂无课程，点击上方按钮添加"));
            return;
        }
        for (int i = 0; i < dayEntries.size(); i++) {
            body.addView(scheduleRow(dayEntries.get(i), i));
        }
    }

    private View scheduleRow(ScheduleEntry e, int index) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(10), dp(8), dp(10));
        row.setBackgroundColor(0xFFFFFFFF);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.bottomMargin = dp(6);
        row.setLayoutParams(rlp);

        String label;
        int bg, tc;
        if (e.type == TimetableEngine.TYPE_COURSE) {
            Course c = data.getCourse(e.refId);
            label = c == null ? "课程" : c.name;
            bg = c == null ? 0xFFCCCCCC : c.bgColor;
            tc = c == null ? 0xFF000000 : c.textColor;
        } else {
            NonCourseItem n = data.getNonCourse(e.refId);
            label = (n == null ? "项目" : n.name) + "（贯穿全周）";
            bg = ColorUtil.NONCOURSE_BG;
            tc = ColorUtil.NONCOURSE_TEXT;
        }
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextColor(tc);
        tv.setTextSize(15);
        tv.setBackgroundColor(bg);
        tv.setPadding(dp(10), dp(6), dp(10), dp(6));
        row.addView(tv, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        MaterialButton del = smallBtn("删");
        del.setTextColor(0xFFEF5350);
        del.setOnClickListener(v -> {
            data.entries.remove(e);
            data.persist();
            render();
        });
        row.addView(del, lpMargins(dp(6), 0, 0, 0));
        return row;
    }

    private void showAddItemDialog() {
        if (scheduleDay == 1) {
            String[] types = {"课程项", "非课程项"};
            new MaterialAlertDialogBuilder(this)
                    .setTitle("选择项目类型")
                    .setItems(types, (d, which) -> {
                        if (which == 0) pickCourseOrTime(TimetableEngine.TYPE_COURSE);
                        else pickCourseOrTime(TimetableEngine.TYPE_NONCOURSE);
                    })
                    .show();
        } else {
            pickCourseOrTime(TimetableEngine.TYPE_COURSE);
        }
    }

    private void pickCourseOrTime(int type) {
        if (type == TimetableEngine.TYPE_COURSE) {
            if (data.courses.isEmpty()) { toast("请先在设置中添加课程"); return; }
            String[] names = new String[data.courses.size()];
            for (int i = 0; i < data.courses.size(); i++) {
                Course c = data.courses.get(i);
                names[i] = c.teacher == null || c.teacher.isEmpty() ? c.name : c.name + " · " + c.teacher;
            }
            new MaterialAlertDialogBuilder(this)
                    .setTitle(scheduleDay == 1 && mondayEmpty() ? "选择课程（下一步选开始时间）" : "选择课程")
                    .setItems(names, (d, which) -> addEntryAfterTimeChoice(TimetableEngine.TYPE_COURSE, data.courses.get(which).id))
                    .show();
        } else {
            if (data.nonCourses.isEmpty()) { toast("请先在设置中添加非课程项"); return; }
            String[] names = new String[data.nonCourses.size()];
            for (int i = 0; i < data.nonCourses.size(); i++) {
                names[i] = data.nonCourses.get(i).name;
            }
            new MaterialAlertDialogBuilder(this)
                    .setTitle("选择非课程项")
                    .setItems(names, (d, which) -> addEntryAfterTimeChoice(TimetableEngine.TYPE_NONCOURSE, data.nonCourses.get(which).id))
                    .show();
        }
    }

    private boolean mondayEmpty() {
        for (ScheduleEntry e : data.entries) if (e.day == 1) return false;
        return true;
    }

    /** 周一首个项目：选完项目后再选开始时间；否则直接追加 */
    private void addEntryAfterTimeChoice(int type, String refId) {
        boolean isFirstMonday = scheduleDay == 1 && mondayEmpty();
        if (!isFirstMonday) {
            data.entries.add(new ScheduleEntry(scheduleDay, type, refId));
            data.persist();
            render();
            return;
        }
        // 周一首个：选开始时间
        int cur = data.firstStartMin;
        new TimePickerDialog(this, (view, hourOfDay, minute) -> {
            int start = hourOfDay * 60 + minute;
            data.firstStartMin = start;
            data.entries.add(new ScheduleEntry(1, type, refId));
            data.persist();
            render();
        }, cur / 60, cur % 60, true).show();
    }

    private void onNext() {
        if (step == 2 && pendingEt != null) {
            try {
                int v = Integer.parseInt(pendingEt.getText().toString().trim());
                if (v <= 0) v = 45;
                data.lessonDurationMin = v;
                data.persist();
            } catch (Exception ignored) {
            }
        }
        if (step == 4) {
            if (scheduleDay < 5) {
                scheduleDay++;
                render();
                return;
            } else {
                // 完成
                setResult(RESULT_OK);
                finish();
                return;
            }
        }
        step++;
        render();
    }

    private MaterialButton smallBtn(String text) {
        MaterialButton b = new MaterialButton(this);
        b.setText(text);
        b.setMinimumWidth(dp(48));
        b.setTextSize(13);
        b.setBackgroundColor(0xFFEDF1F8);
        b.setTextColor(0xFF5C6BC0);
        return b;
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(14), dp(8), dp(14), dp(8));
        c.setBackgroundColor(0xFFFFFFFF);
        body.addView(c, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return c;
    }

    private TextView hintText(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(0xFF8A94A6);
        t.setTextSize(13);
        t.setPadding(0, dp(8), 0, dp(4));
        return t;
    }

    private void toast(String s) {
        android.widget.Toast.makeText(this, s, android.widget.Toast.LENGTH_SHORT).show();
    }

    private LinearLayout.LayoutParams lpWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams lpMargins(int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = lpWrap();
        p.setMargins(dp(l), dp(t), dp(r), dp(b));
        return p;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }
}
