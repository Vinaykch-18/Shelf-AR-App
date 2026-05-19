package com.salesdairy.shelfarapp.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.text.TextUtils;

import com.salesdairy.shelfarapp.utils.Constants;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class TelemetryRepository {

    private final DBHelper dbHelper;
    private final SimpleDateFormat timestampFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());

    public TelemetryRepository(Context context) {
        dbHelper = new DBHelper(context.getApplicationContext());
    }

    public static final class SessionValidationSummary {
        public long sessionId;
        public int storeReferenceId;
        public String sessionStatus;
        public String createdAt;
        public String auditedAt;
        public int shelvesAudited;
        public int capturesOpened;
        public int recoveryCount;
        public int stageFlapCount;
        public int relockSuggestionCount;
        public int referenceLockFailures;
        public int referenceLockSuccesses;
        public int prolongedTrackingLossCount;
        public float avgReferenceLockMs;
        public float avgReadyMs;

        public String toReportBlock() {
            StringBuilder report = new StringBuilder();
            report.append("Session #").append(sessionId)
                    .append(" • status: ").append(sessionStatus == null ? "UNKNOWN" : sessionStatus).append('\n');
            if (!TextUtils.isEmpty(createdAt)) {
                report.append("Started: ").append(createdAt).append('\n');
            }
            if (!TextUtils.isEmpty(auditedAt)) {
                report.append("Ended: ").append(auditedAt).append('\n');
            }
            report.append("Shelves audited: ").append(shelvesAudited)
                    .append(" | capture opens: ").append(capturesOpened).append('\n');
            report.append("Recoveries: ").append(recoveryCount)
                    .append(" | stage flaps: ").append(stageFlapCount)
                    .append(" | relock suggestions: ").append(relockSuggestionCount).append('\n');
            report.append("Reference lock success/failure: ").append(referenceLockSuccesses)
                    .append('/').append(referenceLockFailures)
                    .append(" | long tracking loss: ").append(prolongedTrackingLossCount).append('\n');
            if (avgReferenceLockMs > 0f) {
                report.append("Avg reference lock: ").append(Math.round(avgReferenceLockMs)).append(" ms\n");
            }
            if (avgReadyMs > 0f) {
                report.append("Avg shelf ready: ").append(Math.round(avgReadyMs)).append(" ms\n");
            }
            return report.toString().trim();
        }
    }

    public List<SessionValidationSummary> getRecentSessionSummaries(int limit) {
        List<SessionValidationSummary> summaries = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        String sql = "SELECT s." + Constants.COL_ID + ", s." + Constants.COL_STORE_REFERENCE_ID + ", s." + Constants.COL_STATUS + ", s." + Constants.COL_CREATED_AT + ", s." + Constants.COL_AUDITED_AT + ", "
                + "(SELECT COUNT(*) FROM " + Constants.TABLE_SHELF_AUDITS + " sa WHERE sa." + Constants.COL_SESSION_ID + "=s." + Constants.COL_ID + " AND sa." + Constants.COL_STATUS + "='AUDITED') AS audited_count, "
                + "(SELECT COUNT(*) FROM " + Constants.TABLE_AUDIT_TELEMETRY + " t WHERE t." + Constants.COL_SESSION_ID + "=s." + Constants.COL_ID + " AND t." + Constants.COL_EVENT_NAME + "='audit_capture_opened') AS capture_count, "
                + "(SELECT COUNT(*) FROM " + Constants.TABLE_AUDIT_TELEMETRY + " t WHERE t." + Constants.COL_SESSION_ID + "=s." + Constants.COL_ID + " AND t." + Constants.COL_EVENT_NAME + "='audit_stage_recover_route') AS recovery_count, "
                + "(SELECT COUNT(*) FROM " + Constants.TABLE_AUDIT_TELEMETRY + " t WHERE t." + Constants.COL_SESSION_ID + "=s." + Constants.COL_ID + " AND t." + Constants.COL_EVENT_NAME + "='audit_stage_flap') AS flap_count, "
                + "(SELECT COUNT(*) FROM " + Constants.TABLE_AUDIT_TELEMETRY + " t WHERE t." + Constants.COL_SESSION_ID + "=s." + Constants.COL_ID + " AND t." + Constants.COL_EVENT_NAME + "='audit_relock_suggested') AS relock_count, "
                + "(SELECT COUNT(*) FROM " + Constants.TABLE_AUDIT_TELEMETRY + " t WHERE t." + Constants.COL_SESSION_ID + "=s." + Constants.COL_ID + " AND t." + Constants.COL_EVENT_NAME + "='audit_reference_lock_failure') AS ref_fail_count, "
                + "(SELECT COUNT(*) FROM " + Constants.TABLE_AUDIT_TELEMETRY + " t WHERE t." + Constants.COL_SESSION_ID + "=s." + Constants.COL_ID + " AND t." + Constants.COL_EVENT_NAME + "='audit_reference_lock_success') AS ref_ok_count, "
                + "(SELECT COUNT(*) FROM " + Constants.TABLE_AUDIT_TELEMETRY + " t WHERE t." + Constants.COL_SESSION_ID + "=s." + Constants.COL_ID + " AND t." + Constants.COL_EVENT_NAME + "='audit_tracking_lost_prolonged') AS track_loss_count, "
                + "COALESCE((SELECT AVG(t." + Constants.COL_EVENT_VALUE + ") FROM " + Constants.TABLE_AUDIT_TELEMETRY + " t WHERE t." + Constants.COL_SESSION_ID + "=s." + Constants.COL_ID + " AND t." + Constants.COL_EVENT_NAME + "='audit_reference_lock_success'), 0), "
                + "COALESCE((SELECT AVG(t." + Constants.COL_EVENT_VALUE + ") FROM " + Constants.TABLE_AUDIT_TELEMETRY + " t WHERE t." + Constants.COL_SESSION_ID + "=s." + Constants.COL_ID + " AND t." + Constants.COL_EVENT_NAME + "='audit_ready_time_ms'), 0) "
                + "FROM " + Constants.TABLE_AUDIT_SESSIONS + " s ORDER BY s." + Constants.COL_ID + " DESC LIMIT " + Math.max(1, limit);
        Cursor cursor = db.rawQuery(sql, null);
        if (cursor != null) {
            try {
                while (cursor.moveToNext()) {
                    SessionValidationSummary summary = new SessionValidationSummary();
                    summary.sessionId = cursor.getLong(0);
                    summary.storeReferenceId = cursor.getInt(1);
                    summary.sessionStatus = cursor.getString(2);
                    summary.createdAt = cursor.getString(3);
                    summary.auditedAt = cursor.getString(4);
                    summary.shelvesAudited = cursor.getInt(5);
                    summary.capturesOpened = cursor.getInt(6);
                    summary.recoveryCount = cursor.getInt(7);
                    summary.stageFlapCount = cursor.getInt(8);
                    summary.relockSuggestionCount = cursor.getInt(9);
                    summary.referenceLockFailures = cursor.getInt(10);
                    summary.referenceLockSuccesses = cursor.getInt(11);
                    summary.prolongedTrackingLossCount = cursor.getInt(12);
                    summary.avgReferenceLockMs = cursor.getFloat(13);
                    summary.avgReadyMs = cursor.getFloat(14);
                    summaries.add(summary);
                }
            } finally {
                cursor.close();
            }
        }
        return summaries;
    }

    public List<String> getRecentEvents(int limit) {
        List<String> events = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = db.query(Constants.TABLE_AUDIT_TELEMETRY,
                new String[]{Constants.COL_CREATED_AT, Constants.COL_EVENT_NAME, Constants.COL_EVENT_VALUE, Constants.COL_EVENT_DETAIL, Constants.COL_SESSION_ID},
                null, null, null, null, Constants.COL_ID + " DESC", String.valueOf(Math.max(1, limit)));
        if (cursor != null) {
            try {
                while (cursor.moveToNext()) {
                    String createdAt = cursor.getString(0);
                    String eventName = cursor.getString(1);
                    float eventValue = cursor.getFloat(2);
                    String eventDetail = cursor.getString(3);
                    long sessionId = cursor.getLong(4);
                    StringBuilder line = new StringBuilder();
                    if (!TextUtils.isEmpty(createdAt)) {
                        line.append(createdAt).append(" • ");
                    }
                    line.append("S").append(sessionId).append(" • ").append(eventName);
                    if (Math.abs(eventValue) > 0.01f) {
                        line.append(" (").append(Math.round(eventValue)).append(")");
                    }
                    if (!TextUtils.isEmpty(eventDetail)) {
                        line.append(" • ").append(eventDetail);
                    }
                    events.add(line.toString());
                }
            } finally {
                cursor.close();
            }
        }
        return events;
    }

    public String buildValidationReport(int sessionLimit, int eventLimit) {
        StringBuilder report = new StringBuilder();
        report.append("Validation report\n\nAcceptance targets\n")
                .append("• Entrance reference lock success: 95%+\n")
                .append("• Audit reaches correct shelf zone: 90%+\n")
                .append("• Wrong-path recovery success: 90%+\n")
                .append("• Final phone match within acceptable time: 85%+\n")
                .append("• Stage flapping: near zero\n")
                .append("• Critical crash / stuck flow: zero\n\nRecent sessions\n");
        List<SessionValidationSummary> summaries = getRecentSessionSummaries(sessionLimit);
        if (summaries.isEmpty()) {
            report.append("• No audit session data yet.\n");
        } else {
            for (SessionValidationSummary summary : summaries) {
                report.append(summary.toReportBlock()).append("\n\n");
            }
        }
        report.append("Recent telemetry\n");
        List<String> events = getRecentEvents(eventLimit);
        if (events.isEmpty()) {
            report.append("• No telemetry events yet.\n");
        } else {
            for (String event : events) {
                report.append("• ").append(event).append('\n');
            }
        }
        return report.toString().trim();
    }

    public long record(String eventName, int storeReferenceId, long shelfId, long sessionId, float value, String detail) {
        if (TextUtils.isEmpty(eventName)) {
            return -1L;
        }
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(Constants.COL_EVENT_NAME, eventName.trim());
        values.put(Constants.COL_EVENT_VALUE, value);
        values.put(Constants.COL_EVENT_DETAIL, detail);
        values.put(Constants.COL_STORE_REFERENCE_ID, storeReferenceId);
        values.put(Constants.COL_SHELF_ID, shelfId);
        values.put(Constants.COL_SESSION_ID, sessionId);
        values.put(Constants.COL_CREATED_AT, timestampFormat.format(new Date()));
        return db.insert(Constants.TABLE_AUDIT_TELEMETRY, null, values);
    }

    public long record(String eventName, int storeReferenceId, long shelfId, long sessionId, String detail) {
        return record(eventName, storeReferenceId, shelfId, sessionId, 0f, detail);
    }

    public long recordStage(String stageName, int storeReferenceId, long shelfId, long sessionId, String detail) {
        return record("audit_stage_" + stageName, storeReferenceId, shelfId, sessionId, 0f, detail);
    }
}
