package com.salesdairy.shelfarapp.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.util.Log;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class CrashLogRepository {

    private static final String TAG = "ShelfARFlow";
    private static final String PREF_NAME = "shelfar_crash_debug";
    private static final String KEY_CRASH_REPORT = "last_crash_report";
    private static final String KEY_CRASH_AT = "last_crash_at";
    private static final String KEY_BREADCRUMBS = "breadcrumbs";
    private static final int MAX_BREADCRUMBS = 40;

    private CrashLogRepository() {
    }

    public static final class CrashInfo {
        public final long capturedAtMs;
        public final String report;

        CrashInfo(long capturedAtMs, String report) {
            this.capturedAtMs = capturedAtMs;
            this.report = report;
        }

        public boolean isValid() {
            return capturedAtMs > 0L && !TextUtils.isEmpty(report);
        }
    }

    public static void install(Context context) {
        if (context == null) {
            return;
        }

        final Context app = context.getApplicationContext();
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();

        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            try {
                recordCrash(app, thread, throwable);
            } catch (Exception e) {
                Log.e(TAG, "Failed to record uncaught crash", e);
            }

            if (previous != null) {
                previous.uncaughtException(thread, throwable);
            } else {
                System.exit(10);
            }
        });
    }

    public static void noteBreadcrumb(Context context, String message) {
        if (context == null || TextUtils.isEmpty(message)) {
            return;
        }

        SharedPreferences prefs = prefs(context);
        String existing = prefs.getString(KEY_BREADCRUMBS, "");
        List<String> lines = new ArrayList<>();

        if (!TextUtils.isEmpty(existing)) {
            String[] split = existing.split("\\n");
            for (String line : split) {
                if (!TextUtils.isEmpty(line)) {
                    lines.add(line);
                }
            }
        }

        String stamp = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
        lines.add(stamp + " • " + message.trim());

        while (lines.size() > MAX_BREADCRUMBS) {
            lines.remove(0);
        }

        prefs.edit()
                .putString(KEY_BREADCRUMBS, TextUtils.join("\n", lines))
                .apply();
    }

    public static void recordHandledException(Context context, String source, Throwable throwable) {
        if (context == null || throwable == null) {
            return;
        }

        StringWriter sw = new StringWriter();
        throwable.printStackTrace(new PrintWriter(sw));

        noteBreadcrumb(
                context,
                "Handled exception in " + source + ": "
                        + throwable.getClass().getSimpleName()
                        + " - "
                        + safeMessage(throwable)
        );

        prefs(context).edit()
                .putLong(KEY_CRASH_AT, System.currentTimeMillis())
                .putString(KEY_CRASH_REPORT, "Handled exception in " + source + "\n" + sw)
                .apply();
    }

    public static CrashInfo consumeLastCrash(Context context) {
        if (context == null) {
            return null;
        }

        SharedPreferences prefs = prefs(context);
        long at = prefs.getLong(KEY_CRASH_AT, 0L);
        String report = prefs.getString(KEY_CRASH_REPORT, null);

        prefs.edit()
                .remove(KEY_CRASH_AT)
                .remove(KEY_CRASH_REPORT)
                .apply();

        CrashInfo info = new CrashInfo(at, report);
        return info.isValid() ? info : null;
    }

    public static String getBreadcrumbs(Context context) {
        return context == null ? "" : prefs(context).getString(KEY_BREADCRUMBS, "");
    }

    private static void recordCrash(Context context, Thread thread, Throwable throwable) {
        StringWriter sw = new StringWriter();
        throwable.printStackTrace(new PrintWriter(sw));

        StringBuilder report = new StringBuilder();
        report.append("Thread: ")
                .append(thread != null ? thread.getName() : "unknown")
                .append('\n');
        report.append("Type: ")
                .append(throwable.getClass().getName())
                .append('\n');
        report.append("Message: ")
                .append(safeMessage(throwable))
                .append("\n\n");

        String breadcrumbs = getBreadcrumbs(context);
        if (!TextUtils.isEmpty(breadcrumbs)) {
            report.append("Breadcrumbs\n")
                    .append(breadcrumbs)
                    .append("\n\n");
        }

        report.append("Stacktrace\n")
                .append(sw);

        prefs(context).edit()
                .putLong(KEY_CRASH_AT, System.currentTimeMillis())
                .putString(KEY_CRASH_REPORT, report.toString())
                .apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    private static String safeMessage(Throwable throwable) {
        String msg = throwable == null ? null : throwable.getMessage();
        return TextUtils.isEmpty(msg) ? "(no message)" : msg;
    }
}
