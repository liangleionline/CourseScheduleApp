package com.courseschedule.app.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Build;
import android.view.View;
import android.widget.RemoteViews;

import androidx.core.widget.RemoteViewsCompat;

import com.courseschedule.app.R;
import com.courseschedule.app.data.AppData;
import com.courseschedule.app.data.Course;
import com.courseschedule.app.data.RenderedCell;
import com.courseschedule.app.data.TimetableEngine;
import com.courseschedule.app.ui.MainActivity;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * 桌面小组件：今天 / 明天双日课程卡片。
 * 100% 复刻 ShiGuangSchedule double_days 小组件实现：
 * ListView + RemoteViewsCompat.RemoteCollectionItems 渲染、
 * loading 占位布局、WorkManager 每 15 分钟自动刷新、无课/假期覆盖视图。
 * 仅数据源换成我们的课程表数据。
 */
public class TimetableWidgetProvider extends AppWidgetProvider {

    private static final String[] WEEK_DAYS = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};
    private static final java.text.SimpleDateFormat DATE_FMT =
            new java.text.SimpleDateFormat("MM.dd", Locale.US);

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        // 与开源项目一致：异步渲染，避免阻塞广播线程
        final Context ctx = context.getApplicationContext();
        new Thread(() -> {
            for (int id : appWidgetIds) updateWidget(ctx, id);
        }).start();
    }

    @Override
    public void onEnabled(Context context) {
        super.onEnabled(context);
        // 添加第一个小组件时启用每 15 分钟的定期刷新
        WidgetWorkManagerHelper.schedulePeriodicWork(context.getApplicationContext());
    }

    @Override
    public void onDisabled(Context context) {
        super.onDisabled(context);
        // 移除最后一个小组件时清除定期任务
        WidgetWorkManagerHelper.cancelAllWork(context.getApplicationContext());
    }

    /** 刷新所有已添加的小组件（数据变化 / App 打开时调用） */
    public static void refreshAll(Context context) {
        try {
            AppWidgetManager mgr = AppWidgetManager.getInstance(context);
            ComponentName cn = new ComponentName(context, TimetableWidgetProvider.class);
            int[] ids = mgr.getAppWidgetIds(cn);
            for (int id : ids) updateWidget(context, id);
        } catch (Exception ignored) {
        }
    }

    private static void updateWidget(Context context, int appWidgetId) {
        try {
            AppWidgetManager mgr = AppWidgetManager.getInstance(context);
            mgr.updateAppWidget(appWidgetId, render(context, appWidgetId));
        } catch (Exception ignored) {
        }
    }

    /** 渲染（与开源项目 DoubleDaysNativeRenderer.render 一致，单视图树） */
    private static RemoteViews render(Context context, int appWidgetId) {
        RemoteViews rv = new RemoteViews(context.getPackageName(), R.layout.widget_timetable);

        // 1. 彻底重置状态
        resetWidgetState(rv);

        // 2. 根布局点击跳转应用主界面
        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        rv.setOnClickPendingIntent(R.id.widget_root, pendingIntent);

        // 3. 数据准备
        AppData data = AppData.get(context);
        if (data.timetables.isEmpty()) data.ensureTimetable();

        // 4. 假期/无课表处理（无课程表时显示覆盖视图）
        if (data.timetables.isEmpty() || !hasAnyCourse(data)) {
            rv.setViewVisibility(R.id.inner_content_card, View.GONE);
            rv.setViewVisibility(R.id.container_full_status, View.VISIBLE);
            rv.setTextViewText(R.id.tv_full_status_title, "假期中");
            rv.setTextViewText(R.id.tv_full_status_msg, "期待新学期");
            return rv;
        }

        // 显示双列卡片内容
        rv.setViewVisibility(R.id.inner_content_card, View.VISIBLE);
        rv.setViewVisibility(R.id.container_full_status, View.GONE);

        // 5. 数据准备
        Calendar cal = Calendar.getInstance();
        Calendar tomorrow = (Calendar) cal.clone();
        tomorrow.add(Calendar.DAY_OF_YEAR, 1);
        int todayDow = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7 + 1; // 周一=1
        int tomorrowDow = todayDow % 7 + 1;

        // 今日：仅剩余课程（结束时间晚于当前时刻）
        int now = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE);
        List<RenderedCell> remainingToday = new ArrayList<>();
        for (RenderedCell c : coursesOf(data, todayDow)) {
            if (c.endMin > now) remainingToday.add(c);
        }
        List<RenderedCell> tomorrowCourses = coursesOf(data, tomorrowDow);

        // 渲染左侧：今日课程
        renderColumn(context, rv, appWidgetId,
                R.id.list_view_today, R.id.tv_today_date, R.id.tv_today_footer,
                R.id.empty_today_container, R.id.empty_today,
                cal, remainingToday, true, data);

        // 渲染右侧：明日课程
        renderColumn(context, rv, appWidgetId,
                R.id.list_view_tomorrow, R.id.tv_tomorrow_date, R.id.tv_tomorrow_footer,
                R.id.empty_tomorrow_container, R.id.empty_tomorrow,
                tomorrow, tomorrowCourses, false, data);

        return rv;
    }

    /** 重置小组件视图可见性状态（与开源项目一致） */
    private static void resetWidgetState(RemoteViews rv) {
        rv.setViewVisibility(R.id.inner_content_card, View.VISIBLE);
        rv.setViewVisibility(R.id.container_full_status, View.GONE);
        rv.setViewVisibility(R.id.empty_today_container, View.GONE);
        rv.setViewVisibility(R.id.empty_tomorrow_container, View.GONE);
    }

    /** 渲染单侧（今日或明日）课程列（与开源项目 renderColumn 一致） */
    private static void renderColumn(Context context, RemoteViews rv, int appWidgetId,
                                     int listViewId, int titleId, int footerId,
                                     int emptyContainerId, int emptyTextId,
                                     Calendar date, List<RenderedCell> displayCourses,
                                     boolean isToday, AppData data) {
        // 1. 标题拼接（周几用周一=1索引，避免 Calendar.DAY_OF_WEEK 周日=1 错位）
        String prefix = isToday ? "今天" : "明天";
        String dayOfWeekStr = WEEK_DAYS[todayDow(date) - 1];
        String titleText = prefix + " " + fmtDate(date) + " " + dayOfWeekStr;
        rv.setTextViewText(titleId, titleText);

        // 2. 空视图与列表渲染切换
        if (displayCourses.isEmpty()) {
            rv.setViewVisibility(listViewId, View.GONE);
            rv.setViewVisibility(footerId, View.GONE);
            rv.setViewVisibility(emptyContainerId, View.VISIBLE);
            rv.setTextViewText(emptyTextId, "无课程");
        } else {
            rv.setViewVisibility(listViewId, View.VISIBLE);
            rv.setViewVisibility(footerId, View.VISIBLE);
            rv.setViewVisibility(emptyContainerId, View.GONE);

            // 尾部统计：今日为“剩余”，明日为“共有”
            rv.setTextViewText(footerId, String.format(Locale.US,
                    isToday ? "剩余 %d 节" : "共有 %d 节", displayCourses.size()));

            // 3. 构建 RemoteCollectionItems 并绑定至 ListView
            RemoteViewsCompat.RemoteCollectionItems.Builder builder =
                    new RemoteViewsCompat.RemoteCollectionItems.Builder();

            for (RenderedCell cell : displayCourses) {
                RemoteViews itemRv = createCourseItemView(context, data, cell);
                itemRv.setOnClickFillInIntent(R.id.item_root_card, new Intent());
                long itemId = (cell.refId + "_" + cell.startMin).hashCode() & 0x7FFFFFFFL;
                builder.addItem(itemId, itemRv);
            }

            builder.setHasStableIds(true);
            RemoteViewsCompat.RemoteCollectionItems collectionItems = builder.build();
            RemoteViewsCompat.setRemoteAdapter(context, rv, appWidgetId, listViewId, collectionItems);

            // 4. 点击 PendingIntent 模板设置
            Intent clickIntentTemplate = new Intent(context, MainActivity.class);
            clickIntentTemplate.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent pendingIntentTemplate = PendingIntent.getActivity(
                    context, appWidgetId, clickIntentTemplate,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
            rv.setPendingIntentTemplate(listViewId, pendingIntentTemplate);
        }
    }

    /** 创建单条课程条目（与开源项目 CourseItemRenderer.createCourseItemView 一致） */
    private static RemoteViews createCourseItemView(Context context, AppData data, RenderedCell cell) {
        RemoteViews itemRv = new RemoteViews(context.getPackageName(), R.layout.widget_course_item);
        Course course = data.getCourse(cell.refId);

        String name = course != null && course.name != null ? course.name : "课程";
        itemRv.setTextViewText(R.id.tv_course_name, name);

        String timeText = fmt(cell.startMin) + "-" + fmt(cell.endMin);
        itemRv.setTextViewText(R.id.tv_course_time, timeText);

        // 无教室地点 → 隐藏位置行
        itemRv.setViewVisibility(R.id.tv_course_position, View.GONE);

        // 教师：为空则隐藏
        String teacher = course != null && course.teacher != null ? course.teacher : "";
        if (teacher.isBlank()) {
            itemRv.setViewVisibility(R.id.tv_course_teacher, View.GONE);
        } else {
            itemRv.setTextViewText(R.id.tv_course_teacher, teacher);
            itemRv.setViewVisibility(R.id.tv_course_teacher, View.VISIBLE);
        }

        // 指示条颜色 = 课程配色
        int colorInt = course != null ? course.bgColor : 0xFFE91E63;
        setIndicatorTint(itemRv, R.id.course_indicator, colorInt);

        return itemRv;
    }

    /** 指示条着色（与开源项目 setIndicatorTint 一致） */
    private static void setIndicatorTint(RemoteViews rv, int viewId, int colorInt) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            rv.setColorStateList(viewId, "setImageTintList", ColorStateList.valueOf(colorInt));
        } else {
            rv.setInt(viewId, "setColorFilter", colorInt);
        }
    }

    private static int todayDow(Calendar c) {
        return (c.get(Calendar.DAY_OF_WEEK) + 5) % 7 + 1;
    }

    /** 某天的课程格（按开始时间排序） */
    private static List<RenderedCell> coursesOf(AppData data, int day) {
        List<RenderedCell> out = new ArrayList<>();
        for (RenderedCell c : TimetableEngine.computeDay(data, day)) {
            if (c.type == TimetableEngine.TYPE_COURSE) out.add(c);
        }
        return out;
    }

    /** 是否有任何课程（判断是否显示假期覆盖视图） */
    private static boolean hasAnyCourse(AppData data) {
        for (int d = 1; d <= 7; d++) {
            if (!coursesOf(data, d).isEmpty()) return true;
        }
        return false;
    }

    private static String fmtDate(Calendar c) {
        return DATE_FMT.format(c.getTime());
    }

    private static String fmt(int m) {
        return String.format(Locale.US, "%02d:%02d", m / 60, m % 60);
    }
}
