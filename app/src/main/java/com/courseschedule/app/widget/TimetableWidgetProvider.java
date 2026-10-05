package com.courseschedule.app.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import com.courseschedule.app.R;
import com.courseschedule.app.data.AppData;
import com.courseschedule.app.data.Course;
import com.courseschedule.app.data.RenderedCell;
import com.courseschedule.app.data.TimetableEngine;
import com.courseschedule.app.ui.MainActivity;

import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * 桌面小组件：4×3，显示所选课程表的今天/明天课程。
 * 添加时通过 WidgetConfigureActivity 选择加载哪一张课程表。
 */
public class TimetableWidgetProvider extends AppWidgetProvider {

    public static final String PREFS = "widget_prefs";
    public static final String KEY_TT = "tt_";

    private static final String[] WEEK = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};
    private static final int MAX_ROWS = 6;

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int id : appWidgetIds) {
            updateWidget(context, appWidgetManager, id);
        }
    }

    /** 刷新所有已添加的小组件 */
    public static void refreshAll(Context context) {
        try {
            AppWidgetManager mgr = AppWidgetManager.getInstance(context);
            ComponentName cn = new ComponentName(context, TimetableWidgetProvider.class);
            int[] ids = mgr.getAppWidgetIds(cn);
            for (int id : ids) updateWidget(context, mgr, id);
        } catch (Exception ignored) {
        }
    }

    public static void updateWidget(Context context, AppWidgetManager mgr, int appWidgetId) {
        AppData data = AppData.get(context);
        String ttId = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_TT + appWidgetId, null);
        AppData.Timetable tt = null;
        for (AppData.Timetable t : data.timetables) {
            if (t.id.equals(ttId)) { tt = t; break; }
        }
        if (tt == null) tt = data.activeTimetable();
        if (tt == null) return;
        RemoteViews views = buildViews(context, data, tt);
        mgr.updateAppWidget(appWidgetId, views);
    }

    private static RemoteViews buildViews(Context ctx, AppData data, AppData.Timetable tt) {
        RemoteViews views = new RemoteViews(ctx.getPackageName(), R.layout.widget_timetable);

        // 打开 App
        Intent open = new Intent(ctx, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(ctx, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_root, pi);

        Calendar cal = Calendar.getInstance();
        int todayDow = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7 + 1; // 周一=1

        // 找未来7天内最近的两个有课日期
        int day1 = -1, day2 = -1;
        int day1Off = -1, day2Off = -1;
        for (int i = 0; i < 7; i++) {
            int dow = (todayDow - 1 + i) % 7 + 1;
            if (countCourses(data, tt, dow) > 0) {
                if (day1 == -1) { day1 = dow; day1Off = i; }
                else { day2 = dow; day2Off = i; break; }
            }
        }
        if (day1 == -1) { day1 = todayDow; day1Off = 0; }

        Calendar d1 = (Calendar) cal.clone();
        d1.add(Calendar.DAY_OF_YEAR, day1Off);
        Calendar d2 = day2 == -1 ? null : ((Calendar) cal.clone());
        if (d2 != null) d2.add(Calendar.DAY_OF_YEAR, day2Off);

        views.setTextViewText(R.id.week_num, "第" + weekOfSchoolYear(cal) + "周");

        fillDay(ctx, views, data, tt, day1, d1, R.id.today_title, R.id.today_count, R.id.today_list, true);
        if (d2 == null) {
            views.setTextViewText(R.id.tomorrow_title, "无课");
            views.setTextViewText(R.id.tomorrow_count, "");
        } else {
            fillDay(ctx, views, data, tt, day2, d2, R.id.tomorrow_title, R.id.tomorrow_count, R.id.tomorrow_list, false);
        }
        return views;
    }

    private static void fillDay(Context ctx, RemoteViews views, AppData data, AppData.Timetable tt,
                                int dow, Calendar date, int titleId, int countId, int listId, boolean isFirst) {
        Calendar cal = Calendar.getInstance();
        int todayDow = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7 + 1;
        int tomorrowDow = todayDow % 7 + 1;

        String prefix;
        if (dow == todayDow) prefix = "今天";
        else if (dow == tomorrowDow) prefix = "明天";
        else prefix = WEEK[dow - 1];

        String dateStr = String.format(Locale.US, "%02d.%02d", date.get(Calendar.MONTH) + 1, date.get(Calendar.DAY_OF_MONTH));
        if (prefix.equals("今天") || prefix.equals("明天")) {
            views.setTextViewText(titleId, prefix + " " + dateStr + " " + WEEK[dow - 1]);
        } else {
            views.setTextViewText(titleId, prefix + " " + dateStr);
        }

        List<RenderedCell> cells = TimetableEngine.computeDay(data, dow, tt.entries);
        List<RenderedCell> courses = new java.util.ArrayList<>();
        for (RenderedCell c : cells) if (c.type == TimetableEngine.TYPE_COURSE) courses.add(c);

        if (isFirst) {
            int now = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE);
            int left = 0;
            for (RenderedCell c : courses) if (c.endMin > now) left++;
            if (left > 0) views.setTextViewText(countId, "剩余" + left + "节");
            else views.setTextViewText(countId, courses.isEmpty() ? "" : "今日已结束");
        } else {
            views.setTextViewText(countId, "共有" + courses.size() + "节");
        }

        int shown = 0;
        for (RenderedCell c : courses) {
            if (shown >= MAX_ROWS) break;
            Course course = data.getCourse(c.refId);
            if (course == null) continue;
            RemoteViews item = new RemoteViews(ctx.getPackageName(), R.layout.widget_course_item);
            item.setInt(R.id.bar, "setBackgroundColor", course.bgColor);
            item.setTextViewText(R.id.course_name, course.name);
            item.setTextViewText(R.id.course_time, fmt(c.startMin) + "-" + fmt(c.endMin));
            views.addView(listId, item);
            shown++;
        }
        if (shown == 0) {
            RemoteViews item = new RemoteViews(ctx.getPackageName(), R.layout.widget_course_item);
            item.setInt(R.id.bar, "setBackgroundColor", 0xFF8A94A6);
            item.setTextViewText(R.id.course_name, "无课程");
            item.setTextViewText(R.id.course_time, "");
            views.addView(listId, item);
        }
    }

    private static int countCourses(AppData data, AppData.Timetable tt, int dow) {
        int n = 0;
        for (RenderedCell c : TimetableEngine.computeDay(data, dow, tt.entries)) {
            if (c.type == TimetableEngine.TYPE_COURSE) n++;
        }
        return n;
    }

    /** 学年周数：以当年9月1日为第1周起点 */
    private static int weekOfSchoolYear(Calendar cal) {
        Calendar start = Calendar.getInstance();
        start.set(Calendar.MONTH, Calendar.SEPTEMBER);
        start.set(Calendar.DAY_OF_MONTH, 1);
        start.set(Calendar.HOUR_OF_DAY, 0);
        start.set(Calendar.MINUTE, 0);
        start.set(Calendar.SECOND, 0);
        start.set(Calendar.MILLISECOND, 0);
        if (cal.before(start)) start.add(Calendar.YEAR, -1);
        long diff = (cal.getTimeInMillis() - start.getTimeInMillis()) / 86400000L;
        return (int) (diff / 7) + 1;
    }

    private static String fmt(int m) {
        return String.format(Locale.US, "%02d:%02d", m / 60, m % 60);
    }
}
