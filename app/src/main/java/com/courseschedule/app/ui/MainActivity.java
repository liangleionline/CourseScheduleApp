package com.courseschedule.app.ui;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.SparseArray;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

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
    private FrameLayout root;
    private ViewPager2 viewPager;          // 课程表左右滑动容器（ViewPager2：原生跟手/惯性/回弹/边界）
    private TimetablePagerAdapter adapter;
    private final SparseArray<TimetableView> pageViews = new SparseArray<>(); // position → 页视图
    private TimetableView timetable;       // 当前页课表视图（onResume 入场/发光用）
    private LinearLayout emptyView;
    private TextView titleView, emptyTitle, emptyHint;
    private TextView createBtn;

    private final TimetableView.Listener cellListener = new TimetableView.Listener() {
        @Override
        public void onCellClick(int day, int type, String refId, int startMin, int endMin) {
            // 课程：长按触发 → 编辑；非课程：直接编辑
            MainActivity.this.onCellClick(day, type, refId);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        data = AppData.get(this);
        data.preseedCoursesIfEmpty();
        data.preseedNonCoursesIfEmpty();
        showCrashLogIfAny(); // 崩溃诊断：上次运行若闪退，展示堆栈便于定位

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

        // 课表区：ViewPager2 左右滑动切换多个课程表（原生跟手滑动，无抖动）
        viewPager = new ViewPager2(this);
        viewPager.setOrientation(ViewPager2.ORIENTATION_HORIZONTAL);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0);
        tl.weight = 1;
        content.addView(viewPager, tl);
        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                if (position < 0 || position >= data.timetables.size()) return;
                // 同步激活课表、标题、空状态；滑入到位后播放入场波浪 + 当前应上课发光
                data.setActiveTimetable(data.timetables.get(position).id);
                timetable = pageViews.get(position);
                titleView.setText(data.activeTimetable().name);
                updateEmptyState();
                if (timetable != null && timetable.getVisibility() == View.VISIBLE) {
                    timetable.playEntrance();
                    timetable.glowCurrentCourse();
                }
            }
        });

        // 底部提示
        TextView footer = new TextView(this);
        footer.setText("点击课程翻转查看详情 · 长按课程修改 · 按住下拉查看非课程项 · 左右滑动切换课程表");
        footer.setTextColor(0xFF666666);
        footer.setTextSize(12);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(0, dp(6), 0, dp(8));
        content.addView(footer);

        // 空状态（覆盖在课表上方；本体不消费触摸，空课表页仍可左右滑动切换）
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

    /** 若存在上次崩溃日志则展示（用于定位闪退根因） */
    private void showCrashLogIfAny() {
        try {
            java.io.File cf = new java.io.File(getFilesDir(), com.courseschedule.app.CrashApplication.CRASH_LOG);
            if (cf.exists() && cf.length() > 0) {
                StringBuilder sb = new StringBuilder();
                try (java.io.BufferedReader br = new java.io.BufferedReader(
                        new java.io.FileReader(cf))) {
                    String line;
                    int n = 0;
                    while ((line = br.readLine()) != null && n < 4000) {
                        sb.append(line).append('\n');
                        n += line.length() + 1;
                    }
                }
                new MaterialAlertDialogBuilder(this)
                        .setTitle("上次运行发生崩溃（日志已保存）")
                        .setMessage(sb.toString())
                        .setPositiveButton("知道了", null)
                        .show();
            }
        } catch (Exception ignored) {
        }
    }

    /** ViewPager2 适配器：每个课程表一页，页内一个 TimetableView */
    private class TimetablePagerAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        @Override
        public int getItemCount() {
            return data.timetables.size();
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            // ViewPager2 硬性要求：页面根布局必须 match_parent 填满整页（否则抛
            // "Pages must fill the whole ViewPager2" 崩溃），LayoutParams 须为 RecyclerView.LayoutParams
            FrameLayout page = new FrameLayout(parent.getContext());
            page.setLayoutParams(new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            TimetableView tv = new TimetableView(parent.getContext(), null);
            tv.setListener(cellListener);
            page.addView(tv, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            return new RecyclerView.ViewHolder(page) {
            };
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder h, int position) {
            TimetableView tv = (TimetableView) ((FrameLayout) h.itemView).getChildAt(0);
            pageViews.put(position, tv);
            bindPage(tv, position);
        }
    }

    /** 渲染指定课表内容到某页视图（临时指向其 entries，setData 同步计算 rows 后还原） */
    private void bindPage(TimetableView tv, int position) {
        if (position < 0 || position >= data.timetables.size()) return;
        List<ScheduleEntry> saved = data.entries;
        data.entries = data.timetables.get(position).entries;
        tv.setData(data);
        data.entries = saved;
    }

    private void refreshPages() {
        for (int i = 0; i < data.timetables.size(); i++) {
            TimetableView tv = pageViews.get(i);
            if (tv != null) bindPage(tv, i);
        }
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

    /** 按当前页同步空状态（空课表页显示"开始排课"入口，其余正常显示课表） */
    private void updateEmptyState() {
        boolean any = !data.timetables.isEmpty();
        AppData.Timetable active = data.activeTimetable();
        int pos = Math.max(0, viewPager.getCurrentItem());
        boolean has = any && pos < data.timetables.size()
                && !data.timetables.get(pos).entries.isEmpty();
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
        if (adapter == null) {
            adapter = new TimetablePagerAdapter();
            viewPager.setAdapter(adapter);
        } else if (adapter.getItemCount() != data.timetables.size()) {
            pageViews.clear();
            adapter.notifyDataSetChanged();
        } else {
            refreshPages();
        }
        // 定位到激活课表（数量变化后先钳制到合法范围；延后到首帧布局完成后再定位，
        // 避开 setAdapter 后尚未布局就 setCurrentItem 的 ViewPager2 时序崩溃）
        int idx = data.timetableIndex(data.activeTimetableId);
        int cnt = data.timetables.size();
        if (idx < 0 || idx >= cnt) idx = Math.max(0, cnt - 1);
        final int target = idx;
        viewPager.post(() -> {
            if (cnt > 0 && viewPager.getCurrentItem() != target) {
                viewPager.setCurrentItem(target, false);
            }
        });
        AppData.Timetable active = data.activeTimetable();
        titleView.setText(active != null ? active.name : "课程表");
        updateEmptyState();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
        // 回到前台（后台切回/设置页返回）时重播当前页磁贴入场动画
        timetable = pageViews.get(viewPager.getCurrentItem());
        if (timetable != null && timetable.getVisibility() == View.VISIBLE) {
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
