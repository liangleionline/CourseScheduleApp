package com.courseschedule.app.widget;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.courseschedule.app.data.AppData;

import java.util.ArrayList;
import java.util.List;

/**
 * 小组件配置页：添加/长按重配小组件时选择要显示的课程表（Metro 风格）。
 * 选择结果按 appWidgetId 保存，Provider 渲染时读取。
 */
public class WidgetConfigureActivity extends Activity {

    private int appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
    private String selectedId;
    private TextView okBtn;
    private final List<TextView> itemViews = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setResult(RESULT_CANCELED);

        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            appWidgetId = extras.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID);
        }
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish();
            return;
        }

        AppData data = AppData.get(this);
        // 默认选中：已有配置 > 当前激活课程表
        selectedId = TimetableWidgetProvider.getWidgetTimetable(this, appWidgetId);
        if (selectedId == null || !data.containsTimetable(selectedId)) {
            AppData.Timetable at = data.activeTimetable();
            selectedId = at != null ? at.id : null;
        }

        buildUi(data);
    }

    private void buildUi(AppData data) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFFF2F2F2);
        setContentView(root);

        // 顶部命令栏（Metro 蓝色）
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(16), dp(14), dp(12), dp(10));
        top.setBackgroundColor(0xFF0078D7);
        root.addView(top);

        TextView title = new TextView(this);
        title.setText("选择课程表");
        title.setTextColor(Color.WHITE);
        title.setTextSize(20);
        title.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        top.addView(title);

        TextView hint = new TextView(this);
        hint.setText("小组件将显示所选课程表的内容");
        hint.setTextColor(0xFF666666);
        hint.setTextSize(12);
        hint.setPadding(dp(16), dp(10), dp(16), dp(4));
        root.addView(hint);

        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        root.addView(sv, new LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(12), dp(6), dp(12), dp(6));
        sv.addView(list);

        if (data.timetables.isEmpty()) {
            TextView none = new TextView(this);
            none.setText("暂无课程表，请先在 App 中创建");
            none.setTextColor(0xFF666666);
            none.setTextSize(14);
            none.setGravity(Gravity.CENTER);
            none.setPadding(0, dp(40), 0, dp(40));
            list.addView(none);
        } else {
            for (AppData.Timetable t : data.timetables) {
                TextView tv = new TextView(this);
                tv.setText(t.name);
                tv.setTextSize(16);
                tv.setPadding(dp(16), dp(14), dp(16), dp(14));
                tv.setTag(t.id);
                tv.setOnClickListener(v -> {
                    selectedId = (String) v.getTag();
                    refreshItems();
                });
                itemViews.add(tv);
                list.addView(tv);
            }
        }

        // 底部确定按钮（Metro 直角命令按钮）
        okBtn = new TextView(this);
        okBtn.setText("确定");
        okBtn.setTextColor(Color.WHITE);
        okBtn.setTextSize(16);
        okBtn.setGravity(Gravity.CENTER);
        okBtn.setPadding(0, dp(14), 0, dp(14));
        okBtn.setBackgroundColor(0xFF0078D7);
        okBtn.setOnClickListener(v -> onConfirm());
        root.addView(okBtn);

        refreshItems();
    }

    private void refreshItems() {
        for (TextView tv : itemViews) {
            boolean sel = tv.getTag() != null && tv.getTag().equals(selectedId);
            tv.setBackgroundColor(sel ? 0xFFE5F1FB : 0xFFFFFFFF);
            tv.setTextColor(sel ? 0xFF0078D7 : 0xFF1A1A1A);
        }
        boolean enabled = selectedId != null && !itemViews.isEmpty();
        okBtn.setEnabled(enabled);
        okBtn.setAlpha(enabled ? 1f : 0.4f);
    }

    private void onConfirm() {
        if (selectedId == null) {
            finish();
            return;
        }
        TimetableWidgetProvider.setWidgetTimetable(this, appWidgetId, selectedId);
        TimetableWidgetProvider.refreshAll(this);

        Intent result = new Intent();
        result.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        setResult(RESULT_OK, result);
        finish();
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }
}
