package com.salesdairy.shelfarapp.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.salesdairy.shelfarapp.models.Shelf;
import com.salesdairy.shelfarapp.models.ShelfAuditStatus;
import com.salesdairy.shelfarapp.utils.Constants;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AuditRepository {
    private final DBHelper dbHelper;

    public AuditRepository(Context context) {
        dbHelper = new DBHelper(context);
    }

    public long getOrCreateActiveSession(int outletId, int storeReferenceId) {
        long active = getLatestInProgressSessionId(outletId, storeReferenceId);
        return active > 0L ? active : startFreshSession(outletId, storeReferenceId);
    }

    public long getLatestInProgressSessionId(int outletId, int storeReferenceId) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = db.query(Constants.TABLE_AUDIT_SESSIONS, new String[]{Constants.COL_ID},
                Constants.COL_OUTLET_ID + "=? AND " + Constants.COL_STORE_REFERENCE_ID + "=? AND " + Constants.COL_STATUS + "=?",
                new String[]{String.valueOf(outletId), String.valueOf(storeReferenceId), "IN_PROGRESS"},
                null, null, Constants.COL_ID + " DESC", "1");
        long sessionId = -1L;
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                sessionId = cursor.getLong(cursor.getColumnIndexOrThrow(Constants.COL_ID));
            }
            cursor.close();
        }
        return sessionId;
    }

    public long getLatestSessionIdForStoreReference(int outletId, int storeReferenceId) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = db.query(Constants.TABLE_AUDIT_SESSIONS, new String[]{Constants.COL_ID},
                Constants.COL_OUTLET_ID + "=? AND " + Constants.COL_STORE_REFERENCE_ID + "=?",
                new String[]{String.valueOf(outletId), String.valueOf(storeReferenceId)},
                null, null, Constants.COL_ID + " DESC", "1");
        long sessionId = -1L;
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                sessionId = cursor.getLong(cursor.getColumnIndexOrThrow(Constants.COL_ID));
            }
            cursor.close();
        }
        return sessionId;
    }

    public long startFreshSession(int outletId, int storeReferenceId) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.beginTransaction();
        try {
            ContentValues abandon = new ContentValues();
            abandon.put(Constants.COL_STATUS, "ABANDONED");
            abandon.put(Constants.COL_AUDITED_AT, now());
            db.update(Constants.TABLE_AUDIT_SESSIONS, abandon,
                    Constants.COL_OUTLET_ID + "=? AND " + Constants.COL_STORE_REFERENCE_ID + "=? AND " + Constants.COL_STATUS + "=?",
                    new String[]{String.valueOf(outletId), String.valueOf(storeReferenceId), "IN_PROGRESS"});

            ContentValues values = new ContentValues();
            values.put(Constants.COL_OUTLET_ID, outletId);
            values.put(Constants.COL_STORE_REFERENCE_ID, storeReferenceId);
            values.put(Constants.COL_STATUS, "IN_PROGRESS");
            values.put(Constants.COL_CREATED_AT, now());
            long sessionId = db.insert(Constants.TABLE_AUDIT_SESSIONS, null, values);
            db.setTransactionSuccessful();
            return sessionId;
        } finally {
            db.endTransaction();
        }
    }

    public void markSessionCompleted(long sessionId) {
        updateSessionState(sessionId, "COMPLETED");
    }

    public void markSessionAbandoned(long sessionId) {
        updateSessionState(sessionId, "ABANDONED");
    }

    private void updateSessionState(long sessionId, String status) {
        if (sessionId <= 0L) {
            return;
        }
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(Constants.COL_STATUS, status);
        values.put(Constants.COL_AUDITED_AT, now());
        db.update(Constants.TABLE_AUDIT_SESSIONS, values, Constants.COL_ID + "=?", new String[]{String.valueOf(sessionId)});
    }

    public long createOrReuseShelfAudit(long sessionId, int shelfId) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        Cursor cursor = db.query(Constants.TABLE_SHELF_AUDITS, null,
                Constants.COL_SESSION_ID + "=? AND " + Constants.COL_SHELF_ID + "=?",
                new String[]{String.valueOf(sessionId), String.valueOf(shelfId)},
                null, null, Constants.COL_ID + " DESC", "1");
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                long id = cursor.getLong(cursor.getColumnIndexOrThrow(Constants.COL_ID));
                cursor.close();
                ContentValues values = new ContentValues();
                values.put(Constants.COL_STATUS, "IN_PROGRESS");
                values.put(Constants.COL_REMARKS, "");
                db.update(Constants.TABLE_SHELF_AUDITS, values, Constants.COL_ID + "=?", new String[]{String.valueOf(id)});
                return id;
            }
            cursor.close();
        }
        ContentValues values = new ContentValues();
        values.put(Constants.COL_SESSION_ID, sessionId);
        values.put(Constants.COL_SHELF_ID, shelfId);
        values.put(Constants.COL_STATUS, "IN_PROGRESS");
        values.put(Constants.COL_CREATED_AT, now());
        return db.insert(Constants.TABLE_SHELF_AUDITS, null, values);
    }

    public void replaceAuditImages(long shelfAuditId, List<String> imagePaths) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.delete(Constants.TABLE_AUDIT_IMAGES, Constants.COL_SHELF_AUDIT_ID + "=?", new String[]{String.valueOf(shelfAuditId)});
        int order = 1;
        for (String path : imagePaths) {
            ContentValues values = new ContentValues();
            values.put(Constants.COL_SHELF_AUDIT_ID, shelfAuditId);
            values.put(Constants.COL_IMAGE_PATH, path);
            values.put(Constants.COL_CAPTURE_ORDER, order++);
            values.put(Constants.COL_CREATED_AT, now());
            db.insert(Constants.TABLE_AUDIT_IMAGES, null, values);
        }
    }

    public void completeShelfAudit(long shelfAuditId, String remarks) {
        updateShelfAuditState(shelfAuditId, "AUDITED", remarks);
    }

    private void updateShelfAuditState(long shelfAuditId, String status, String remarks) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(Constants.COL_STATUS, status);
        values.put(Constants.COL_AUDITED_AT, now());
        values.put(Constants.COL_REMARKS, remarks);
        db.update(Constants.TABLE_SHELF_AUDITS, values, Constants.COL_ID + "=?", new String[]{String.valueOf(shelfAuditId)});
    }

    public Map<Integer, ShelfAuditStatus> getShelfAuditStatusMapForSession(long sessionId) {
        Map<Integer, ShelfAuditStatus> map = new HashMap<>();
        if (sessionId <= 0L) {
            return map;
        }
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        String sql = "SELECT sa.*,(SELECT COUNT(*) FROM " + Constants.TABLE_AUDIT_IMAGES + " ai WHERE ai." + Constants.COL_SHELF_AUDIT_ID + "=sa." + Constants.COL_ID + ") AS img_count "
                + "FROM " + Constants.TABLE_SHELF_AUDITS + " sa WHERE sa." + Constants.COL_SESSION_ID + "=?";
        Cursor cursor = db.rawQuery(sql, new String[]{String.valueOf(sessionId)});
        if (cursor != null) {
            while (cursor.moveToNext()) {
                int shelfId = cursor.getInt(cursor.getColumnIndexOrThrow(Constants.COL_SHELF_ID));
                map.put(shelfId, mapStatus(cursor));
            }
            cursor.close();
        }
        return map;
    }

    public Map<Integer, ShelfAuditStatus> getLatestShelfAuditStatusMap() {
        Map<Integer, ShelfAuditStatus> map = new HashMap<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        String sql = "SELECT sa.*,(SELECT COUNT(*) FROM " + Constants.TABLE_AUDIT_IMAGES + " ai WHERE ai." + Constants.COL_SHELF_AUDIT_ID + "=sa." + Constants.COL_ID + ") AS img_count "
                + "FROM " + Constants.TABLE_SHELF_AUDITS + " sa "
                + "INNER JOIN (SELECT " + Constants.COL_SHELF_ID + ", MAX(" + Constants.COL_ID + ") AS max_id FROM " + Constants.TABLE_SHELF_AUDITS + " GROUP BY " + Constants.COL_SHELF_ID + ") latest "
                + "ON latest.max_id = sa." + Constants.COL_ID;
        Cursor cursor = db.rawQuery(sql, null);
        if (cursor != null) {
            while (cursor.moveToNext()) {
                int shelfId = cursor.getInt(cursor.getColumnIndexOrThrow(Constants.COL_SHELF_ID));
                map.put(shelfId, mapStatus(cursor));
            }
            cursor.close();
        }
        return map;
    }

    public ShelfAuditStatus getLatestShelfAuditStatusForSession(long sessionId, int shelfId) {
        if (sessionId <= 0L || shelfId <= 0) {
            return null;
        }
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        String sql = "SELECT sa.*,(SELECT COUNT(*) FROM " + Constants.TABLE_AUDIT_IMAGES + " ai WHERE ai." + Constants.COL_SHELF_AUDIT_ID + "=sa." + Constants.COL_ID + ") AS img_count "
                + "FROM " + Constants.TABLE_SHELF_AUDITS + " sa WHERE sa." + Constants.COL_SESSION_ID + "=? AND sa." + Constants.COL_SHELF_ID + "=? ORDER BY sa." + Constants.COL_ID + " DESC LIMIT 1";
        Cursor cursor = db.rawQuery(sql, new String[]{String.valueOf(sessionId), String.valueOf(shelfId)});
        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    return mapStatus(cursor);
                }
            } finally {
                cursor.close();
            }
        }
        return null;
    }

    public ShelfAuditStatus getLatestShelfAuditStatus(int shelfId) {
        if (shelfId <= 0) {
            return null;
        }
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        String sql = "SELECT sa.*,(SELECT COUNT(*) FROM " + Constants.TABLE_AUDIT_IMAGES + " ai WHERE ai." + Constants.COL_SHELF_AUDIT_ID + "=sa." + Constants.COL_ID + ") AS img_count "
                + "FROM " + Constants.TABLE_SHELF_AUDITS + " sa WHERE sa." + Constants.COL_SHELF_ID + "=? ORDER BY sa." + Constants.COL_ID + " DESC LIMIT 1";
        Cursor cursor = db.rawQuery(sql, new String[]{String.valueOf(shelfId)});
        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    return mapStatus(cursor);
                }
            } finally {
                cursor.close();
            }
        }
        return null;
    }

    public int findNextPendingShelfId(List<Shelf> shelves, int currentShelfId) {
        return findNextPendingShelfIdForSession(shelves, currentShelfId, -1L);
    }

    public int findNextPendingShelfIdForSession(List<Shelf> shelves, int currentShelfId, long sessionId) {
        if (shelves == null || shelves.isEmpty()) {
            return -1;
        }

        Map<Integer, ShelfAuditStatus> latestStatusMap = sessionId > 0L
                ? getShelfAuditStatusMapForSession(sessionId)
                : getLatestShelfAuditStatusMap();

        Shelf currentShelf = null;
        for (Shelf shelf : shelves) {
            if (shelf.getId() == currentShelfId) {
                currentShelf = shelf;
                break;
            }
        }

        if (currentShelf != null && currentShelf.getRouteOrder() > 0) {
            Shelf nextByRoute = null;
            int bestRouteOrder = Integer.MAX_VALUE;
            for (Shelf candidate : shelves) {
                if (candidate.getId() == currentShelfId || isDone(candidate, latestStatusMap)) {
                    continue;
                }
                int routeOrder = candidate.getRouteOrder() > 0 ? candidate.getRouteOrder() : Integer.MAX_VALUE;
                if (routeOrder > currentShelf.getRouteOrder() && routeOrder < bestRouteOrder) {
                    bestRouteOrder = routeOrder;
                    nextByRoute = candidate;
                }
            }
            if (nextByRoute != null) {
                return nextByRoute.getId();
            }
        }

        Shelf firstPendingByRoute = null;
        int bestRouteOrder = Integer.MAX_VALUE;
        for (Shelf shelf : shelves) {
            if (isDone(shelf, latestStatusMap)) {
                continue;
            }
            int routeOrder = shelf.getRouteOrder() > 0 ? shelf.getRouteOrder() : Integer.MAX_VALUE;
            if (routeOrder < bestRouteOrder) {
                bestRouteOrder = routeOrder;
                firstPendingByRoute = shelf;
            }
        }
        if (firstPendingByRoute != null) {
            return firstPendingByRoute.getId();
        }

        for (Shelf shelf : shelves) {
            if (!isDone(shelf, latestStatusMap) && shelf.getId() != currentShelfId) {
                return shelf.getId();
            }
        }
        return -1;
    }

    public List<String> getAuditImages(long shelfAuditId) {
        List<String> list = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = db.query(Constants.TABLE_AUDIT_IMAGES, null,
                Constants.COL_SHELF_AUDIT_ID + "=?", new String[]{String.valueOf(shelfAuditId)}, null, null,
                Constants.COL_CAPTURE_ORDER + " ASC");
        if (cursor != null) {
            while (cursor.moveToNext()) {
                list.add(cursor.getString(cursor.getColumnIndexOrThrow(Constants.COL_IMAGE_PATH)));
            }
            cursor.close();
        }
        return list;
    }

    public List<String> getLatestAuditImagesForShelf(int shelfId) {
        ShelfAuditStatus status = getLatestShelfAuditStatus(shelfId);
        if (status == null || status.getShelfAuditId() <= 0L || !status.isAudited()) {
            return new ArrayList<>();
        }
        return getAuditImages(status.getShelfAuditId());
    }

    private ShelfAuditStatus mapStatus(Cursor cursor) {
        ShelfAuditStatus status = new ShelfAuditStatus();
        status.setShelfAuditId(cursor.getLong(cursor.getColumnIndexOrThrow(Constants.COL_ID)));
        status.setSessionId(cursor.getLong(cursor.getColumnIndexOrThrow(Constants.COL_SESSION_ID)));
        status.setStatus(cursor.getString(cursor.getColumnIndexOrThrow(Constants.COL_STATUS)));
        status.setAuditedAt(getString(cursor, Constants.COL_AUDITED_AT));
        status.setImageCount(cursor.getInt(cursor.getColumnIndexOrThrow("img_count")));
        return status;
    }

    private boolean isDone(Shelf shelf, Map<Integer, ShelfAuditStatus> latestStatusMap) {
        if (shelf == null) {
            return false;
        }
        ShelfAuditStatus latest = latestStatusMap == null ? null : latestStatusMap.get(shelf.getId());
        if (latest != null && latest.isAudited()) {
            return true;
        }
        return "AUDITED".equalsIgnoreCase(shelf.getAuditStatus());
    }

    private String getString(Cursor cursor, String column) {
        int index = cursor.getColumnIndex(column);
        return index >= 0 ? cursor.getString(index) : null;
    }

    private String now() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
    }
}
