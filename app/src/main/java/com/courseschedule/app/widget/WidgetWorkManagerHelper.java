package com.courseschedule.app.widget;

import android.content.Context;

import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

/**
 * 调度和取消小组件定期刷新任务（复刻开源项目 WorkManagerHelper）。
 */
public class WidgetWorkManagerHelper {

    private static final String UI_UPDATE_WORK_NAME = "WidgetUiUpdateWorker_Periodic";

    public static void schedulePeriodicWork(Context context) {
        try {
            PeriodicWorkRequest uiUpdateWorkRequest =
                    new PeriodicWorkRequest.Builder(WidgetUpdateWorker.class, 15, TimeUnit.MINUTES).build();
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    UI_UPDATE_WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    uiUpdateWorkRequest);
        } catch (Exception ignored) {
        }
    }

    public static void cancelAllWork(Context context) {
        try {
            WorkManager.getInstance(context).cancelUniqueWork(UI_UPDATE_WORK_NAME);
        } catch (Exception ignored) {
        }
    }
}
