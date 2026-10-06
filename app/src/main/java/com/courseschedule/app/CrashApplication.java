package com.courseschedule.app;

import android.app.Application;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** 全局未捕获异常处理：把崩溃堆栈写入 crash.log，便于定位闪退根因 */
public class CrashApplication extends Application {

    public static final String CRASH_LOG = "crash.log";

    @Override
    public void onCreate() {
        super.onCreate();
        final Thread.UncaughtExceptionHandler def = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            try {
                File f = new File(getFilesDir(), CRASH_LOG);
                try (PrintWriter pw = new PrintWriter(new FileWriter(f, true))) {
                    pw.println("=== " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                            .format(new Date()) + " ===");
                    e.printStackTrace(pw);
                    pw.println();
                }
            } catch (Throwable ignored) {
            }
            if (def != null) def.uncaughtException(t, e);
        });
    }
}
