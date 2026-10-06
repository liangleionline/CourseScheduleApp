package com.courseschedule.app.ui;

import android.content.Intent;
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

import androidx.appcompat.app.AppCompatActivity;

import com.courseschedule.app.data.AppData;
import com.courseschedule.app.data.ColorUtil;
import com.courseschedule.app.data.Course;
import com.courseschedule.app.data.NonCourseItem;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;

/** 设置页：课程项/非课程项管理、课时设置、周末开关、清空课表 */
public class SettingsActivity extends AppCompatActivity {

    private AppData data;
    private LinearLayout root;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        data = AppData.get(this);
        data.ensureTimetable();
        data.preseedCoursesIfEmpty();
        data.preseedNonCoursesIfEmpty();

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFFF2F2F2);
        setContentView(root);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout inner = new LinearLayout(this);
        inner.setOrientation(LinearLayout.VERTICAL);
        inner.setPadding(dp(16), dp(16), dp(16), dp(24));
        scroll.addView(inner);

        TextView title = new TextView(this);
        title.setText("设置");
        title.setTextColor(0xFF1A1A1A);
        title.setTextSize(20);
        title.setTypeface(null, Typeface.BOLD);
        inner.addView(title);

        // 课程表选择器：显示「课程表名+设置 ▾」，点击可下拉切换当前设置的课程表
        titleSelector(inner);

        sectionTitle(inner, "课程表管理");
        timetableSection(inner);

        sectionTitle(inner, "课时设置");
        inner.addView(lessonCard());

        sectionTitle(inner, "课程项设置");
        courseSection(inner);

        sectionTitle(inner, "非课程项设置");
        nonCourseSection(inner);

        sectionTitle(inner, "课表显示");
        inner.addView(weekendCard());

        sectionTitle(inner, "数据");
        inner.addView(clearCard());
        inner.addView(backupCard());
    }

    private void timetableSection(LinearLayout parent) {
        LinearLayout c = card();
        parent.addView(c);

        TextView hint = new TextView(this);
        hint.setText("多孩家庭可为每个孩子各建一个课程表，首页左右滑动切换，顶部显示当前课程表名称。");
        hint.setTextColor(0xFF666666);
        hint.setTextSize(13);
        c.addView(hint);

        MaterialButton add = new MaterialButton(this);
        add.setText("＋ 添加课程表");
        add.setTextColor(Color.WHITE);
        add.setBackgroundColor(0xFF0078D7);
        add.setOnClickListener(v -> askTimetableName("添加课程表", "", name -> {
            data.addTimetable(name);
            render();
        }));
        c.addView(add, lpTop(6));

        for (AppData.Timetable t : data.timetables) {
            c.addView(timetableRow(t));
        }
    }

    private View timetableRow(AppData.Timetable t) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(2), dp(6), dp(2), dp(6));
        row.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView tv = new TextView(this);
        boolean active = t.id.equals(data.activeTimetableId);
        tv.setText((active ? "● " : "") + t.name);
        tv.setTextColor(active ? 0xFF0078D7 : 0xFF1A1A1A);
        tv.setTextSize(15);
        tv.setTypeface(null, active ? Typeface.BOLD : Typeface.NORMAL);
        tv.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        row.addView(tv);

        MaterialButton rename = smallBtn("改名");
        rename.setOnClickListener(v -> askTimetableName("修改课程表名称", t.name, name -> {
            data.renameTimetable(t.id, name);
            render();
        }));
        row.addView(rename, lpWrap());

        MaterialButton del = smallBtn("删");
        del.setTextColor(0xFFEF5350);
        del.setOnClickListener(v -> {
            if (data.timetables.size() <= 1) { toast("至少保留一个课程表"); return; }
            new MaterialAlertDialogBuilder(this)
                    .setTitle("删除「" + t.name + "」？")
                    .setMessage("将删除本课表的全部数据，包括课表布局与课程设置。\n删除后不可恢复。")
                    .setPositiveButton("删除", (d, w) -> {
                        data.deleteTimetable(t.id);
                        render();
                    })
                    .setNegativeButton("取消", null)
                    .show();
        });
        row.addView(del, lpWrap());
        return row;
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

    /** 顶部课程表选择器：显示「课程表名+设置 ▾」，点击弹出下拉菜单切换当前设置的课程表 */
    private void titleSelector(LinearLayout parent) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(4), 0, dp(8));
        row.setOnClickListener(v -> showTimetablePicker());
        parent.addView(row);

        AppData.Timetable active = data.activeTimetable();
        // 名称+▾作为一个整体居左，▾紧贴文字右侧，不撑满不靠右
        LinearLayout textRow = new LinearLayout(this);
        textRow.setOrientation(LinearLayout.HORIZONTAL);
        textRow.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(textRow);

        TextView title = new TextView(this);
        title.setText((active != null ? active.name : "我的课程表") + "设置");
        title.setTextColor(0xFF1A1A1A);
        title.setTextSize(20);
        title.setTypeface(null, Typeface.BOLD);
        textRow.addView(title);

        TextView arrow = new TextView(this);
        arrow.setText("▾");
        arrow.setTextColor(0xFF0078D7);
        arrow.setTextSize(20);
        arrow.setPadding(dp(4), 0, 0, 0); // 仅留极小间隙，紧贴文字
        textRow.addView(arrow);
    }

    /** 弹出课程表选择下拉菜单：点击任一项即切换到该课程表的设置 */
    private void showTimetablePicker() {
        if (data.timetables.isEmpty()) return;
        final String[] names = new String[data.timetables.size()];
        for (int i = 0; i < data.timetables.size(); i++) {
            AppData.Timetable t = data.timetables.get(i);
            names[i] = t.name + (t.id.equals(data.activeTimetableId) ? "（当前）" : "");
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle("选择课程表设置")
                .setItems(names, (d, which) -> {
                    data.setActiveTimetable(data.timetables.get(which).id);
                    render();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void sectionTitle(LinearLayout parent, String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(0xFF0078D7);
        t.setTextSize(15);
        t.setTypeface(null, Typeface.BOLD);
        t.setPadding(0, dp(18), 0, dp(6));
        parent.addView(t);
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(12), dp(6), dp(12), dp(6));
        c.setBackgroundColor(0xFFFFFFFF);
        parentCard = c;
        return c;
    }
    private LinearLayout parentCard;

    private View lessonCard() {
        LinearLayout c = card();
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        c.addView(row);

        TextView label = new TextView(this);
        label.setText("每节课时长（分钟）");
        label.setTextColor(0xFF1A1A1A);
        label.setTextSize(15);
        row.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        EditText et = new EditText(this);
        et.setInputType(InputType.TYPE_CLASS_NUMBER);
        et.setText(String.valueOf(data.lessonDurationMin));
        et.setTextColor(0xFF1A1A1A);
        et.setGravity(Gravity.END);
        row.addView(et, new LinearLayout.LayoutParams(dp(120), ViewGroup.LayoutParams.WRAP_CONTENT));

        MaterialButton save = smallBtn("保存");
        save.setOnClickListener(v -> {
            try {
                int val = Integer.parseInt(et.getText().toString().trim());
                if (val > 0) { data.lessonDurationMin = val; data.persist(); }
            } catch (Exception ignored) {}
        });
        c.addView(save, lpEndTop(4));
        return c;
    }

    private void courseSection(LinearLayout parent) {
        LinearLayout c = card();
        parent.addView(c);
        MaterialButton add = new MaterialButton(this);
        add.setText("＋ 添加课程");
        add.setTextColor(Color.WHITE);
        add.setBackgroundColor(0xFF0078D7);
        add.setOnClickListener(v -> showCourseEditDialog(null));
        c.addView(add, lpTop(6));

        if (data.courses.isEmpty()) {
            c.addView(hint("暂无课程"));
        }
        for (Course course : data.courses) {
            c.addView(courseRow(course));
        }
    }

    private View courseRow(Course course) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(2), dp(6), dp(2), dp(6));
        row.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        View dot = new View(this);
        dot.setBackgroundColor(course.bgColor);
        row.addView(dot, new LinearLayout.LayoutParams(dp(16), dp(16)));

        TextView tv = new TextView(this);
        tv.setText(course.teacher == null || course.teacher.isEmpty() ? course.name : course.name + " · " + course.teacher);
        tv.setTextColor(0xFF1A1A1A);
        tv.setTextSize(15);
        tv.setPadding(dp(10), 0, 0, 0);
        row.addView(tv, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        MaterialButton edit = smallBtn("改");
        edit.setOnClickListener(v -> showCourseEditDialog(course));
        row.addView(edit, lpWrap());

        MaterialButton del = smallBtn("删");
        del.setTextColor(0xFFEF5350);
        del.setOnClickListener(v -> {
            data.deleteCourse(course.id);
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
        wrap.addView(name, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        EditText teacher = new EditText(this);
        teacher.setHint("任课教师（选填）");
        teacher.setSingleLine(true);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tl.topMargin = dp(8);
        wrap.addView(teacher, tl);

        if (existing != null) { name.setText(existing.name); teacher.setText(existing.teacher); }

        new MaterialAlertDialogBuilder(this)
                .setTitle(existing == null ? "添加课程" : "修改课程")
                .setView(wrap)
                .setPositiveButton("保存", (d, w) -> {
                    String n = name.getText().toString().trim();
                    if (n.isEmpty()) { toast("课程名称不能为空"); return; }
                    if (existing == null) data.addCourse(n, teacher.getText().toString().trim());
                    else { existing.name = n; existing.teacher = teacher.getText().toString().trim(); data.persist(); }
                    render();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void nonCourseSection(LinearLayout parent) {
        LinearLayout c = card();
        parent.addView(c);
        MaterialButton add = new MaterialButton(this);
        add.setText("＋ 添加非课程项");
        add.setTextColor(Color.WHITE);
        add.setBackgroundColor(0xFF0078D7);
        add.setOnClickListener(v -> showNonCourseEditDialog(null));
        c.addView(add, lpTop(6));

        for (NonCourseItem n : data.nonCourses) {
            c.addView(nonCourseRow(n));
        }
    }

    private View nonCourseRow(NonCourseItem n) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(2), dp(6), dp(2), dp(6));
        row.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        View dot = new View(this);
        dot.setBackgroundColor(ColorUtil.NONCOURSE_BG);
        row.addView(dot, new LinearLayout.LayoutParams(dp(16), dp(16)));

        TextView tv = new TextView(this);
        tv.setText(n.name + "（" + n.durationMin + "分钟）");
        tv.setTextColor(0xFF1A1A1A);
        tv.setTextSize(15);
        tv.setPadding(dp(10), 0, 0, 0);
        row.addView(tv, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        MaterialButton edit = smallBtn("改");
        edit.setOnClickListener(v -> showNonCourseEditDialog(n));
        row.addView(edit, lpWrap());

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
        wrap.addView(name, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        EditText dur = new EditText(this);
        dur.setHint("时长（分钟）");
        dur.setInputType(InputType.TYPE_CLASS_NUMBER);
        dur.setSingleLine(true);
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
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

    private View weekendCard() {
        LinearLayout c = card();
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        c.addView(row);

        TextView label = new TextView(this);
        label.setText("显示周六、周日");
        label.setTextColor(0xFF1A1A1A);
        label.setTextSize(15);
        row.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        MaterialSwitch sw = new MaterialSwitch(this);
        sw.setChecked(data.showWeekend);
        sw.setOnCheckedChangeListener((buttonView, isChecked) -> {
            data.showWeekend = isChecked;
            data.persist();
        });
        row.addView(sw);
        return c;
    }

    private View clearCard() {
        LinearLayout c = card();
        TextView hint = new TextView(this);
        hint.setText("仅删除课程表的每日排布（布局），课程、课时、非课程项等其他全部数据保留。");
        hint.setTextColor(0xFF666666);
        hint.setTextSize(13);
        c.addView(hint);

        // 仅删除当前课程表的每日排布，保留课程/非课程/课时等设置
        MaterialButton clear = new MaterialButton(this);
        clear.setText("删除当前课程表布局");
        clear.setTextColor(Color.WHITE);
        clear.setBackgroundColor(0xFFEF9A9A);
        clear.setOnClickListener(v -> {
            AppData.Timetable t = data.activeTimetable();
            String nm = t != null ? t.name : "当前课程表";
            new MaterialAlertDialogBuilder(this)
                    .setTitle("删除「" + nm + "」的布局？")
                    .setMessage("将仅删除「" + nm + "」的每日排布，课程、课时、非课程项等设置全部保留。")
                    .setPositiveButton("删除", (d, w) -> {
                        data.clearSchedule();
                        toast("已删除「" + nm + "」的布局");
                    })
                    .setNegativeButton("取消", null)
                    .show();
        });
        c.addView(clear, lpTop(8));

        // 仅删除全部课程表的每日排布，保留各课表设置
        MaterialButton clearAllT = new MaterialButton(this);
        clearAllT.setText("删除全部课程表布局");
        clearAllT.setTextColor(Color.WHITE);
        clearAllT.setBackgroundColor(0xFFEF5350);
        clearAllT.setOnClickListener(v -> new MaterialAlertDialogBuilder(this)
                .setTitle("删除全部课程表的布局？")
                .setMessage("将仅删除全部课程表的每日排布，各课表的课程、课时、非课程项等设置全部保留。")
                .setPositiveButton("删除", (d, w) -> {
                    data.clearAllSchedules();
                    toast("已删除全部课程表的布局");
                })
                .setNegativeButton("取消", null)
                .show());
        c.addView(clearAllT, lpTop(8));
        return c;
    }

    private View backupCard() {
        LinearLayout c = card();
        TextView hint = new TextView(this);
        hint.setText("导出为单个文件，可在本机或换机时导入恢复全部数据（课程、非课程、课表排布与全局设置）。");
        hint.setTextColor(0xFF666666);
        hint.setTextSize(13);
        c.addView(hint);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        c.addView(row, lpTop(10));

        MaterialButton exp = new MaterialButton(this);
        exp.setText("导出数据");
        exp.setTextColor(Color.WHITE);
        exp.setBackgroundColor(0xFF0078D7);
        exp.setOnClickListener(v -> exportData());
        row.addView(exp, lpWrap());

        MaterialButton imp = new MaterialButton(this);
        imp.setText("导入数据");
        imp.setTextColor(Color.WHITE);
        imp.setBackgroundColor(0xFF26A69A);
        LinearLayout.LayoutParams impLp = lpWrap();
        impLp.leftMargin = dp(12);
        imp.setOnClickListener(v -> importData());
        row.addView(imp, impLp);
        return c;
    }

    private static final int REQ_EXPORT = 1001;
    private static final int REQ_IMPORT = 1002;

    private void exportData() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        String stamp = new java.text.SimpleDateFormat("yyyyMMdd_HHmm", java.util.Locale.US)
                .format(new java.util.Date());
        intent.putExtra(Intent.EXTRA_TITLE, "课程表备份_" + stamp + ".json");
        startActivityForResult(intent, REQ_EXPORT);
    }

    private void importData() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        startActivityForResult(intent, REQ_IMPORT);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent dataIntent) {
        super.onActivityResult(requestCode, resultCode, dataIntent);
        if (resultCode != RESULT_OK || dataIntent == null || dataIntent.getData() == null) return;
        android.net.Uri uri = dataIntent.getData();
        try {
            if (requestCode == REQ_EXPORT) {
                java.io.OutputStream os = getContentResolver().openOutputStream(uri);
                if (os != null) {
                    os.write(data.exportJson().getBytes("UTF-8"));
                    os.flush();
                    os.close();
                    toast("导出成功");
                }
            } else if (requestCode == REQ_IMPORT) {
                java.io.InputStream is = getContentResolver().openInputStream(uri);
                if (is != null) {
                    String json = readAll(is);
                    is.close();
                    if (data.importJson(json)) {
                        toast("导入成功，数据已恢复");
                        render();
                    } else {
                        toast("导入失败：文件格式不正确");
                    }
                }
            }
        } catch (Exception e) {
            toast("操作失败：" + e.getMessage());
        }
    }

    private String readAll(java.io.InputStream is) throws Exception {
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = is.read(buf)) != -1) bos.write(buf, 0, n);
        return bos.toString("UTF-8");
    }

    private void render() {
        // 数据变化后重建界面
        data = AppData.get(this);
        Intent i = getIntent();
        finish();
        startActivity(i);
    }

    private MaterialButton smallBtn(String text) {
        MaterialButton b = new MaterialButton(this);
        b.setText(text);
        b.setMinimumWidth(dp(44));
        b.setTextSize(13);
        b.setBackgroundColor(0xFFE5F1FB);
        b.setTextColor(0xFF0078D7);
        return b;
    }

    private TextView hint(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(0xFF666666);
        t.setTextSize(13);
        t.setPadding(0, dp(6), 0, dp(6));
        return t;
    }

    private void toast(String s) {
        android.widget.Toast.makeText(this, s, android.widget.Toast.LENGTH_SHORT).show();
    }

    private LinearLayout.LayoutParams lpWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams lpTop(int top) {
        LinearLayout.LayoutParams p = lpWrap();
        p.topMargin = dp(top);
        return p;
    }

    private LinearLayout.LayoutParams lpEndTop(int top) {
        LinearLayout.LayoutParams p = lpWrap();
        p.gravity = Gravity.END;
        p.topMargin = dp(top);
        return p;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }
}
