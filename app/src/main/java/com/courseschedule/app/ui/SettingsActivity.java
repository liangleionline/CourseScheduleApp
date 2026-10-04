package com.courseschedule.app.ui;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
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
        data.preseedCoursesIfEmpty();
        data.preseedNonCoursesIfEmpty();

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFFF6F8FC);
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
        title.setTextColor(0xFF3A4151);
        title.setTextSize(20);
        title.setTypeface(null, Typeface.BOLD);
        inner.addView(title);

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

    private void sectionTitle(LinearLayout parent, String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(0xFF5C6BC0);
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
        label.setTextColor(0xFF3A4151);
        label.setTextSize(15);
        row.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        EditText et = new EditText(this);
        et.setInputType(InputType.TYPE_CLASS_NUMBER);
        et.setText(String.valueOf(data.lessonDurationMin));
        et.setTextColor(0xFF3A4151);
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
        add.setBackgroundColor(0xFF5C6BC0);
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
        tv.setTextColor(0xFF3A4151);
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
        add.setBackgroundColor(0xFF5C6BC0);
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
        tv.setTextColor(0xFF3A4151);
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
        label.setTextColor(0xFF3A4151);
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
        hint.setText("清空课表排布数据（保留课程库、非课程项库与全局设置），清空后可重新排布。");
        hint.setTextColor(0xFF8A94A6);
        hint.setTextSize(13);
        c.addView(hint);

        MaterialButton clear = new MaterialButton(this);
        clear.setText("清空课表");
        clear.setTextColor(Color.WHITE);
        clear.setBackgroundColor(0xFFEF5350);
        clear.setOnClickListener(v -> new MaterialAlertDialogBuilder(this)
                .setTitle("确认清空课表？")
                .setMessage("将删除全部每日排布数据，课程库与非课程项保留。")
                .setPositiveButton("清空", (d, w) -> {
                    data.clearSchedule();
                    toast("已清空课表");
                })
                .setNegativeButton("取消", null)
                .show());
        c.addView(clear, lpTop(8));
        return c;
    }

    private View backupCard() {
        LinearLayout c = card();
        TextView hint = new TextView(this);
        hint.setText("导出为单个文件，可在本机或换机时导入恢复全部数据（课程、非课程、课表排布与全局设置）。");
        hint.setTextColor(0xFF8A94A6);
        hint.setTextSize(13);
        c.addView(hint);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        c.addView(row, lpTop(10));

        MaterialButton exp = new MaterialButton(this);
        exp.setText("导出数据");
        exp.setTextColor(Color.WHITE);
        exp.setBackgroundColor(0xFF5C6BC0);
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
        b.setBackgroundColor(0xFFEDF1F8);
        b.setTextColor(0xFF5C6BC0);
        return b;
    }

    private TextView hint(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(0xFF8A94A6);
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
