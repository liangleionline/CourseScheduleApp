package com.courseschedule.app.widget;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

/**
 * 每 15 分钟刷新一次小组件 UI（复刻开源项目 WidgetUiUpdateWorker）。
 */
public class WidgetUpdateWorker extends Worker {

    public WidgetUpdateWorker(@NonNull Context appContext, @NonNull WorkerParameters workerParams) {
        super(appContext, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            TimetableWidgetProvider.refreshAll(getApplicationContext());
            return Result.success();
        } catch (Exception e) {
            return Result.retry();
        }
    }
}
