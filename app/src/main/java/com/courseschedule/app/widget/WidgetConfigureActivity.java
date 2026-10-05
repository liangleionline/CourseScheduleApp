package com.courseschedule.app.widget;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.courseschedule.app.data.AppData;

/**
 * 添加小组件时的配置页：选择要加载哪一张课程表。
 */
public class WidgetConfigureActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setResult(RESULT_CANCELED, resultIntent()); // 默认取消

        AppData data = AppData.get(this);
        data.ensureTimetable();
        final int widgetId = getIntent().getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID);
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish();
            return;
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFFF6F8FC);
        root.setPadding(dp(16), dp(24), dp(16), dp(16));
        setContentView(root);

        TextView title = new TextView(this);
        title.setText("选择要显示的课程表");
        title.setTextColor(0xFF3A4151);
        title.setTextSize(18);
        title.setTypeface(null, Typeface.BOLD);
        root.addView(title);

        TextView hint = new TextView(this);
        hint.setText("小组件将展示所选课程表的今天 / 明天课程安排。");
        hint.setTextColor(0xFF8A94A6);
        hint.setTextSize(13);
        hint.setPadding(0, dp(6), 0, dp(12));
        root.addView(hint);

        for (AppData.Timetable t : data.timetables) {
            root.addView(ttRow(t, widgetId));
        }
    }

    private View ttRow(AppData.Timetable t, int widgetId) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(14), dp(14), dp(14));
        row.setBackgroundColor(Color.WHITE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(8);
        row.setLayoutParams(lp);

        TextView tv = new TextView(this);
        boolean active = t.id.equals(AppData.get(this).activeTimetableId);
        tv.setText((active ? "● " : "") + t.name);
        tv.setTextColor(active ? 0xFF5C6BC0 : 0xFF3A4151);
        tv.setTextSize(16);
        tv.setTypeface(null, active ? Typeface.BOLD : Typeface.NORMAL);
        tv.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        row.addView(tv);

        TextView arrow = new TextView(this);
        arrow.setText("选择 ›");
        arrow.setTextColor(0xFF5C6BC0);
        arrow.setTextSize(14);
        row.addView(arrow);

        row.setOnClickListener(v -> {
            getSharedPreferences(TimetableWidgetProvider.PREFS, MODE_PRIVATE)
                    .edit().putString(TimetableWidgetProvider.KEY_TT + widgetId, t.id).apply();
            AppWidgetManager mgr = AppWidgetManager.getInstance(this);
            TimetableWidgetProvider.updateWidget(this, mgr, widgetId);
            setResult(RESULT_OK, resultIntent());
            finish();
        });
        return row;
    }

    private Intent resultIntent() {
        Intent it = new Intent();
        it.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,
                getIntent().getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID));
        return it;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }
}
