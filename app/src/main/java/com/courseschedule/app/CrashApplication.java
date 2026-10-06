package com.courseschedule.app;

import android.app.Application;
import android.content.ContentValues;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileWriter;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** 全局未捕获异常处理：崩溃堆栈写入手机 Download 目录（无需打开 App，文件管理器直接可看）+ 私有目录兜底 */
public class CrashApplication extends Application {

    /** Download 目录下的日志文件名（固定名，多次崩溃追加写入） */
    public static final String PUBLIC_LOG = "课程表崩溃日志.txt";
    /** 私有目录兜底日志（MainActivity 启动时可弹窗展示） */
    public static final String CRASH_LOG = "crash.log";
    private static final long MAX_PUBLIC = 1024 * 1024; // 公共日志上限 1MB，超限重写最新一条

    @Override
    public void onCreate() {
        super.onCreate();
        final Thread.UncaughtExceptionHandler def = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            try {
                logCrash(t, e);
            } catch (Throwable ignored) {
            }
            if (def != null) def.uncaughtException(t, e);
        });
    }

    private void logCrash(Thread t, Throwable e) {
        String stamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        String content = "=== " + stamp + " ===\n线程: " + t.getName() + "\n\n"
                + sw + "\n----------------------------------------\n";

        // 1) 公共 Download 目录（用户可直接用文件管理器查看）
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                // 分区存储：MediaStore 写公共 Download，无需任何权限
                ContentValues cv = new ContentValues();
                cv.put(MediaStore.Downloads.DISPLAY_NAME, PUBLIC_LOG);
                cv.put(MediaStore.Downloads.MIME_TYPE, "text/plain");
                cv.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                Uri uri = getContentResolver().insert(
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
                if (uri != null) {
                    try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                        if (os != null) os.write(content.getBytes());
                    }
                }
            } else {
                File dir = Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_DOWNLOADS);
                if (dir != null && (dir.exists() || dir.mkdirs())) {
                    File f = new File(dir, PUBLIC_LOG);
                    boolean overwrite = f.exists() && f.length() > MAX_PUBLIC;
                    try (FileWriter fw = new FileWriter(f, !overwrite)) {
                        fw.write(content);
                    }
                }
            }
        } catch (Throwable ignored) {
        }

        // 2) 私有目录兜底（MainActivity 启动时弹窗展示，供开发定位）
        try {
            File f = new File(getFilesDir(), CRASH_LOG);
            try (FileWriter fw = new FileWriter(f, true)) {
                fw.write(content);
            }
        } catch (Throwable ignored) {
        }
    }
}
