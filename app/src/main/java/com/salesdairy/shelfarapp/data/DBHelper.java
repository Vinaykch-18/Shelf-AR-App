package com.salesdairy.shelfarapp.data;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.salesdairy.shelfarapp.utils.Constants;

public class DBHelper extends SQLiteOpenHelper {

    public DBHelper(Context context) {
        super(context, Constants.DB_NAME, null, Constants.DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        ensureSchema(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        ensureSchema(db);
    }

    @Override
    public void onOpen(SQLiteDatabase db) {
        super.onOpen(db);
        ensureSchema(db);
    }

    private void ensureSchema(SQLiteDatabase db) {
        ensureAllTables(db);
        ensureStoreReferenceColumns(db);
        ensureShelfColumns(db);
        ensureAuditSessionColumns(db);
        ensureShelfAuditColumns(db);
        ensureAuditImageColumns(db);
        ensureRouteCheckpointColumns(db);
        ensureRouteEdgeColumns(db);
        ensureTelemetryColumns(db);
        normalizeCloudAnchorTtl(db);
    }

    private void ensureAllTables(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + Constants.TABLE_STORE_REFERENCES + " ("
                + Constants.COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + Constants.COL_OUTLET_ID + " INTEGER DEFAULT 1, "
                + Constants.COL_REFERENCE_SCOPE_NAME + " TEXT NOT NULL DEFAULT 'Store', "
                + Constants.COL_REFERENCE_NAME + " TEXT NOT NULL DEFAULT '', "
                + Constants.COL_REFERENCE_IMAGE_PATH + " TEXT, "
                + Constants.COL_REFERENCE_HINT + " TEXT, "
                + Constants.COL_CLOUD_ANCHOR_ID + " TEXT, "
                + Constants.COL_CLOUD_ANCHOR_STATUS + " TEXT DEFAULT 'NONE', "
                + Constants.COL_CLOUD_ANCHOR_ERROR + " TEXT, "
                + Constants.COL_CLOUD_ANCHOR_HOSTED_AT + " INTEGER DEFAULT 0, "
                + Constants.COL_CLOUD_ANCHOR_TTL_DAYS + " INTEGER DEFAULT " + Constants.CLOUD_ANCHOR_TTL_DAYS + ", "
                + Constants.COL_CREATED_AT + " TEXT, "
                + Constants.COL_IS_ACTIVE + " INTEGER DEFAULT 1"
                + ")");

        db.execSQL("CREATE TABLE IF NOT EXISTS " + Constants.TABLE_SHELVES + " ("
                + Constants.COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + Constants.COL_OUTLET_ID + " INTEGER DEFAULT 1, "
                + Constants.COL_STORE_REFERENCE_ID + " INTEGER DEFAULT 0, "
                + Constants.COL_ROUTE_LABEL + " TEXT DEFAULT 'Store route', "
                + Constants.COL_ROUTE_ORDER + " INTEGER DEFAULT 0, "
                + Constants.COL_NEAREST_CHECKPOINT_ID + " INTEGER DEFAULT 0, "
                + Constants.COL_SHELF_NAME + " TEXT NOT NULL DEFAULT '', "
                + Constants.COL_IMAGE_PATH + " TEXT, "
                + Constants.COL_ANCHOR_X + " REAL NOT NULL DEFAULT 0, "
                + Constants.COL_ANCHOR_Y + " REAL NOT NULL DEFAULT 0, "
                + Constants.COL_ANCHOR_Z + " REAL NOT NULL DEFAULT 0, "
                + Constants.COL_ROT_X + " REAL NOT NULL DEFAULT 0, "
                + Constants.COL_ROT_Y + " REAL NOT NULL DEFAULT 0, "
                + Constants.COL_ROT_Z + " REAL NOT NULL DEFAULT 0, "
                + Constants.COL_ROT_W + " REAL NOT NULL DEFAULT 1, "
                + Constants.COL_CAMERA_X + " REAL DEFAULT 0, "
                + Constants.COL_CAMERA_Y + " REAL DEFAULT 0, "
                + Constants.COL_CAMERA_Z + " REAL DEFAULT 0, "
                + Constants.COL_CAMERA_ROT_X + " REAL DEFAULT 0, "
                + Constants.COL_CAMERA_ROT_Y + " REAL DEFAULT 0, "
                + Constants.COL_CAMERA_ROT_Z + " REAL DEFAULT 0, "
                + Constants.COL_CAMERA_ROT_W + " REAL DEFAULT 1, "
                + Constants.COL_CREATED_AT + " TEXT, "
                + Constants.COL_CLOUD_ANCHOR_ID + " TEXT, "
                + Constants.COL_CLOUD_ANCHOR_STATUS + " TEXT DEFAULT 'NONE', "
                + Constants.COL_CLOUD_ANCHOR_ERROR + " TEXT, "
                + Constants.COL_CLOUD_ANCHOR_HOSTED_AT + " INTEGER DEFAULT 0, "
                + Constants.COL_CLOUD_ANCHOR_TTL_DAYS + " INTEGER DEFAULT " + Constants.CLOUD_ANCHOR_TTL_DAYS + ", "
                + Constants.COL_GUIDE_ANCHOR_BUNDLE + " TEXT"
                + ")");

        db.execSQL("CREATE TABLE IF NOT EXISTS " + Constants.TABLE_AUDIT_SESSIONS + " ("
                + Constants.COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + Constants.COL_OUTLET_ID + " INTEGER DEFAULT 1, "
                + Constants.COL_STORE_REFERENCE_ID + " INTEGER DEFAULT 0, "
                + Constants.COL_STATUS + " TEXT DEFAULT 'IN_PROGRESS', "
                + Constants.COL_CREATED_AT + " TEXT, "
                + Constants.COL_AUDITED_AT + " TEXT"
                + ")");

        db.execSQL("CREATE TABLE IF NOT EXISTS " + Constants.TABLE_SHELF_AUDITS + " ("
                + Constants.COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + Constants.COL_SESSION_ID + " INTEGER NOT NULL, "
                + Constants.COL_SHELF_ID + " INTEGER NOT NULL, "
                + Constants.COL_STATUS + " TEXT DEFAULT 'PENDING', "
                + Constants.COL_CREATED_AT + " TEXT, "
                + Constants.COL_AUDITED_AT + " TEXT, "
                + Constants.COL_REMARKS + " TEXT"
                + ")");

        db.execSQL("CREATE TABLE IF NOT EXISTS " + Constants.TABLE_AUDIT_IMAGES + " ("
                + Constants.COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + Constants.COL_SHELF_AUDIT_ID + " INTEGER NOT NULL, "
                + Constants.COL_IMAGE_PATH + " TEXT NOT NULL, "
                + Constants.COL_CAPTURE_ORDER + " INTEGER DEFAULT 1, "
                + Constants.COL_CREATED_AT + " TEXT"
                + ")");

        db.execSQL("CREATE TABLE IF NOT EXISTS " + Constants.TABLE_ROUTE_CHECKPOINTS + " ("
                + Constants.COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + Constants.COL_OUTLET_ID + " INTEGER DEFAULT 1, "
                + Constants.COL_STORE_REFERENCE_ID + " INTEGER DEFAULT 0, "
                + Constants.COL_SEQUENCE + " INTEGER DEFAULT 0, "
                + Constants.COL_ROUTE_LABEL + " TEXT DEFAULT 'Store route', "
                + Constants.COL_CHECKPOINT_KIND + " TEXT DEFAULT 'PATH', "
                + Constants.COL_ANCHOR_X + " REAL NOT NULL DEFAULT 0, "
                + Constants.COL_ANCHOR_Y + " REAL NOT NULL DEFAULT 0, "
                + Constants.COL_ANCHOR_Z + " REAL NOT NULL DEFAULT 0, "
                + Constants.COL_YAW_DEGREES + " REAL DEFAULT 0, "
                + Constants.COL_CAPTURE_CONFIDENCE + " REAL DEFAULT 0, "
                + Constants.COL_SCENE_QUALITY_SCORE + " INTEGER DEFAULT 0, "
                + Constants.COL_CREATED_AT + " TEXT"
                + ")");

        db.execSQL("CREATE TABLE IF NOT EXISTS " + Constants.TABLE_AUDIT_TELEMETRY + " ("
                + Constants.COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + Constants.COL_STORE_REFERENCE_ID + " INTEGER DEFAULT 0, "
                + Constants.COL_SHELF_ID + " INTEGER DEFAULT 0, "
                + Constants.COL_SESSION_ID + " INTEGER DEFAULT 0, "
                + Constants.COL_EVENT_NAME + " TEXT NOT NULL, "
                + Constants.COL_EVENT_VALUE + " REAL DEFAULT 0, "
                + Constants.COL_EVENT_DETAIL + " TEXT, "
                + Constants.COL_CREATED_AT + " TEXT"
                + ")");

        db.execSQL("CREATE TABLE IF NOT EXISTS " + Constants.TABLE_ROUTE_EDGES + " ("
                + Constants.COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + Constants.COL_STORE_REFERENCE_ID + " INTEGER DEFAULT 0, "
                + Constants.COL_FROM_CHECKPOINT_ID + " INTEGER NOT NULL, "
                + Constants.COL_TO_CHECKPOINT_ID + " INTEGER NOT NULL, "
                + Constants.COL_DISTANCE_METERS + " REAL DEFAULT 0, "
                + Constants.COL_EDGE_KIND + " TEXT DEFAULT 'PATH', "
                + Constants.COL_CREATED_AT + " TEXT"
                + ")");
    }

    private void ensureStoreReferenceColumns(SQLiteDatabase db) {
        ensureColumn(db, Constants.TABLE_STORE_REFERENCES, Constants.COL_OUTLET_ID, Constants.COL_OUTLET_ID + " INTEGER DEFAULT 1");
        ensureColumn(db, Constants.TABLE_STORE_REFERENCES, Constants.COL_REFERENCE_SCOPE_NAME, Constants.COL_REFERENCE_SCOPE_NAME + " TEXT DEFAULT 'Store'");
        ensureColumn(db, Constants.TABLE_STORE_REFERENCES, Constants.COL_REFERENCE_NAME, Constants.COL_REFERENCE_NAME + " TEXT DEFAULT ''");
        ensureColumn(db, Constants.TABLE_STORE_REFERENCES, Constants.COL_REFERENCE_IMAGE_PATH, Constants.COL_REFERENCE_IMAGE_PATH + " TEXT");
        ensureColumn(db, Constants.TABLE_STORE_REFERENCES, Constants.COL_REFERENCE_HINT, Constants.COL_REFERENCE_HINT + " TEXT");
        ensureColumn(db, Constants.TABLE_STORE_REFERENCES, Constants.COL_CLOUD_ANCHOR_ID, Constants.COL_CLOUD_ANCHOR_ID + " TEXT");
        ensureColumn(db, Constants.TABLE_STORE_REFERENCES, Constants.COL_CLOUD_ANCHOR_STATUS, Constants.COL_CLOUD_ANCHOR_STATUS + " TEXT DEFAULT 'NONE'");
        ensureColumn(db, Constants.TABLE_STORE_REFERENCES, Constants.COL_CLOUD_ANCHOR_ERROR, Constants.COL_CLOUD_ANCHOR_ERROR + " TEXT");
        ensureColumn(db, Constants.TABLE_STORE_REFERENCES, Constants.COL_CLOUD_ANCHOR_HOSTED_AT, Constants.COL_CLOUD_ANCHOR_HOSTED_AT + " INTEGER DEFAULT 0");
        ensureColumn(db, Constants.TABLE_STORE_REFERENCES, Constants.COL_CLOUD_ANCHOR_TTL_DAYS, Constants.COL_CLOUD_ANCHOR_TTL_DAYS + " INTEGER DEFAULT " + Constants.CLOUD_ANCHOR_TTL_DAYS);
        ensureColumn(db, Constants.TABLE_STORE_REFERENCES, Constants.COL_CREATED_AT, Constants.COL_CREATED_AT + " TEXT");
        ensureColumn(db, Constants.TABLE_STORE_REFERENCES, Constants.COL_IS_ACTIVE, Constants.COL_IS_ACTIVE + " INTEGER DEFAULT 1");
    }

    private void ensureShelfColumns(SQLiteDatabase db) {
        ensureColumn(db, Constants.TABLE_SHELVES, Constants.COL_OUTLET_ID, Constants.COL_OUTLET_ID + " INTEGER DEFAULT 1");
        ensureColumn(db, Constants.TABLE_SHELVES, Constants.COL_STORE_REFERENCE_ID, Constants.COL_STORE_REFERENCE_ID + " INTEGER DEFAULT 0");
        ensureColumn(db, Constants.TABLE_SHELVES, Constants.COL_ROUTE_LABEL, Constants.COL_ROUTE_LABEL + " TEXT DEFAULT 'Store route'");
        ensureColumn(db, Constants.TABLE_SHELVES, Constants.COL_ROUTE_ORDER, Constants.COL_ROUTE_ORDER + " INTEGER DEFAULT 0");
        ensureColumn(db, Constants.TABLE_SHELVES, Constants.COL_NEAREST_CHECKPOINT_ID, Constants.COL_NEAREST_CHECKPOINT_ID + " INTEGER DEFAULT 0");
        ensureColumn(db, Constants.TABLE_SHELVES, Constants.COL_IMAGE_PATH, Constants.COL_IMAGE_PATH + " TEXT");
        ensureColumn(db, Constants.TABLE_SHELVES, Constants.COL_CAMERA_X, Constants.COL_CAMERA_X + " REAL DEFAULT 0");
        ensureColumn(db, Constants.TABLE_SHELVES, Constants.COL_CAMERA_Y, Constants.COL_CAMERA_Y + " REAL DEFAULT 0");
        ensureColumn(db, Constants.TABLE_SHELVES, Constants.COL_CAMERA_Z, Constants.COL_CAMERA_Z + " REAL DEFAULT 0");
        ensureColumn(db, Constants.TABLE_SHELVES, Constants.COL_CAMERA_ROT_X, Constants.COL_CAMERA_ROT_X + " REAL DEFAULT 0");
        ensureColumn(db, Constants.TABLE_SHELVES, Constants.COL_CAMERA_ROT_Y, Constants.COL_CAMERA_ROT_Y + " REAL DEFAULT 0");
        ensureColumn(db, Constants.TABLE_SHELVES, Constants.COL_CAMERA_ROT_Z, Constants.COL_CAMERA_ROT_Z + " REAL DEFAULT 0");
        ensureColumn(db, Constants.TABLE_SHELVES, Constants.COL_CAMERA_ROT_W, Constants.COL_CAMERA_ROT_W + " REAL DEFAULT 1");
        ensureColumn(db, Constants.TABLE_SHELVES, Constants.COL_CLOUD_ANCHOR_ID, Constants.COL_CLOUD_ANCHOR_ID + " TEXT");
        ensureColumn(db, Constants.TABLE_SHELVES, Constants.COL_CLOUD_ANCHOR_STATUS, Constants.COL_CLOUD_ANCHOR_STATUS + " TEXT DEFAULT 'NONE'");
        ensureColumn(db, Constants.TABLE_SHELVES, Constants.COL_CLOUD_ANCHOR_ERROR, Constants.COL_CLOUD_ANCHOR_ERROR + " TEXT");
        ensureColumn(db, Constants.TABLE_SHELVES, Constants.COL_CLOUD_ANCHOR_HOSTED_AT, Constants.COL_CLOUD_ANCHOR_HOSTED_AT + " INTEGER DEFAULT 0");
        ensureColumn(db, Constants.TABLE_SHELVES, Constants.COL_CLOUD_ANCHOR_TTL_DAYS, Constants.COL_CLOUD_ANCHOR_TTL_DAYS + " INTEGER DEFAULT " + Constants.CLOUD_ANCHOR_TTL_DAYS);
        ensureColumn(db, Constants.TABLE_SHELVES, Constants.COL_GUIDE_ANCHOR_BUNDLE, Constants.COL_GUIDE_ANCHOR_BUNDLE + " TEXT");
        ensureColumn(db, Constants.TABLE_SHELVES, Constants.COL_CREATED_AT, Constants.COL_CREATED_AT + " TEXT");
    }

    private void ensureAuditSessionColumns(SQLiteDatabase db) {
        ensureColumn(db, Constants.TABLE_AUDIT_SESSIONS, Constants.COL_OUTLET_ID, Constants.COL_OUTLET_ID + " INTEGER DEFAULT 1");
        ensureColumn(db, Constants.TABLE_AUDIT_SESSIONS, Constants.COL_STORE_REFERENCE_ID, Constants.COL_STORE_REFERENCE_ID + " INTEGER DEFAULT 0");
        ensureColumn(db, Constants.TABLE_AUDIT_SESSIONS, Constants.COL_STATUS, Constants.COL_STATUS + " TEXT DEFAULT 'IN_PROGRESS'");
        ensureColumn(db, Constants.TABLE_AUDIT_SESSIONS, Constants.COL_CREATED_AT, Constants.COL_CREATED_AT + " TEXT");
        ensureColumn(db, Constants.TABLE_AUDIT_SESSIONS, Constants.COL_AUDITED_AT, Constants.COL_AUDITED_AT + " TEXT");
    }

    private void ensureShelfAuditColumns(SQLiteDatabase db) {
        ensureColumn(db, Constants.TABLE_SHELF_AUDITS, Constants.COL_SESSION_ID, Constants.COL_SESSION_ID + " INTEGER DEFAULT 0");
        ensureColumn(db, Constants.TABLE_SHELF_AUDITS, Constants.COL_SHELF_ID, Constants.COL_SHELF_ID + " INTEGER DEFAULT 0");
        ensureColumn(db, Constants.TABLE_SHELF_AUDITS, Constants.COL_STATUS, Constants.COL_STATUS + " TEXT DEFAULT 'PENDING'");
        ensureColumn(db, Constants.TABLE_SHELF_AUDITS, Constants.COL_CREATED_AT, Constants.COL_CREATED_AT + " TEXT");
        ensureColumn(db, Constants.TABLE_SHELF_AUDITS, Constants.COL_AUDITED_AT, Constants.COL_AUDITED_AT + " TEXT");
        ensureColumn(db, Constants.TABLE_SHELF_AUDITS, Constants.COL_REMARKS, Constants.COL_REMARKS + " TEXT");
    }

    private void ensureAuditImageColumns(SQLiteDatabase db) {
        ensureColumn(db, Constants.TABLE_AUDIT_IMAGES, Constants.COL_SHELF_AUDIT_ID, Constants.COL_SHELF_AUDIT_ID + " INTEGER DEFAULT 0");
        ensureColumn(db, Constants.TABLE_AUDIT_IMAGES, Constants.COL_IMAGE_PATH, Constants.COL_IMAGE_PATH + " TEXT");
        ensureColumn(db, Constants.TABLE_AUDIT_IMAGES, Constants.COL_CAPTURE_ORDER, Constants.COL_CAPTURE_ORDER + " INTEGER DEFAULT 1");
        ensureColumn(db, Constants.TABLE_AUDIT_IMAGES, Constants.COL_CREATED_AT, Constants.COL_CREATED_AT + " TEXT");
    }

    private void ensureRouteCheckpointColumns(SQLiteDatabase db) {
        ensureColumn(db, Constants.TABLE_ROUTE_CHECKPOINTS, Constants.COL_OUTLET_ID, Constants.COL_OUTLET_ID + " INTEGER DEFAULT 1");
        ensureColumn(db, Constants.TABLE_ROUTE_CHECKPOINTS, Constants.COL_STORE_REFERENCE_ID, Constants.COL_STORE_REFERENCE_ID + " INTEGER DEFAULT 0");
        ensureColumn(db, Constants.TABLE_ROUTE_CHECKPOINTS, Constants.COL_SEQUENCE, Constants.COL_SEQUENCE + " INTEGER DEFAULT 0");
        ensureColumn(db, Constants.TABLE_ROUTE_CHECKPOINTS, Constants.COL_ROUTE_LABEL, Constants.COL_ROUTE_LABEL + " TEXT DEFAULT 'Store route'");
        ensureColumn(db, Constants.TABLE_ROUTE_CHECKPOINTS, Constants.COL_CHECKPOINT_KIND, Constants.COL_CHECKPOINT_KIND + " TEXT DEFAULT 'PATH'");
        ensureColumn(db, Constants.TABLE_ROUTE_CHECKPOINTS, Constants.COL_YAW_DEGREES, Constants.COL_YAW_DEGREES + " REAL DEFAULT 0");
        ensureColumn(db, Constants.TABLE_ROUTE_CHECKPOINTS, Constants.COL_CAPTURE_CONFIDENCE, Constants.COL_CAPTURE_CONFIDENCE + " REAL DEFAULT 0");
        ensureColumn(db, Constants.TABLE_ROUTE_CHECKPOINTS, Constants.COL_SCENE_QUALITY_SCORE, Constants.COL_SCENE_QUALITY_SCORE + " INTEGER DEFAULT 0");
        ensureColumn(db, Constants.TABLE_ROUTE_CHECKPOINTS, Constants.COL_CREATED_AT, Constants.COL_CREATED_AT + " TEXT");
    }

    private void ensureRouteEdgeColumns(SQLiteDatabase db) {
        ensureColumn(db, Constants.TABLE_ROUTE_EDGES, Constants.COL_STORE_REFERENCE_ID, Constants.COL_STORE_REFERENCE_ID + " INTEGER DEFAULT 0");
        ensureColumn(db, Constants.TABLE_ROUTE_EDGES, Constants.COL_FROM_CHECKPOINT_ID, Constants.COL_FROM_CHECKPOINT_ID + " INTEGER DEFAULT 0");
        ensureColumn(db, Constants.TABLE_ROUTE_EDGES, Constants.COL_TO_CHECKPOINT_ID, Constants.COL_TO_CHECKPOINT_ID + " INTEGER DEFAULT 0");
        ensureColumn(db, Constants.TABLE_ROUTE_EDGES, Constants.COL_DISTANCE_METERS, Constants.COL_DISTANCE_METERS + " REAL DEFAULT 0");
        ensureColumn(db, Constants.TABLE_ROUTE_EDGES, Constants.COL_EDGE_KIND, Constants.COL_EDGE_KIND + " TEXT DEFAULT 'PATH'");
        ensureColumn(db, Constants.TABLE_ROUTE_EDGES, Constants.COL_CREATED_AT, Constants.COL_CREATED_AT + " TEXT");
    }

    private void ensureTelemetryColumns(SQLiteDatabase db) {
        ensureColumn(db, Constants.TABLE_AUDIT_TELEMETRY, Constants.COL_STORE_REFERENCE_ID, Constants.COL_STORE_REFERENCE_ID + " INTEGER DEFAULT 0");
        ensureColumn(db, Constants.TABLE_AUDIT_TELEMETRY, Constants.COL_SHELF_ID, Constants.COL_SHELF_ID + " INTEGER DEFAULT 0");
        ensureColumn(db, Constants.TABLE_AUDIT_TELEMETRY, Constants.COL_SESSION_ID, Constants.COL_SESSION_ID + " INTEGER DEFAULT 0");
        ensureColumn(db, Constants.TABLE_AUDIT_TELEMETRY, Constants.COL_EVENT_NAME, Constants.COL_EVENT_NAME + " TEXT DEFAULT ''");
        ensureColumn(db, Constants.TABLE_AUDIT_TELEMETRY, Constants.COL_EVENT_VALUE, Constants.COL_EVENT_VALUE + " REAL DEFAULT 0");
        ensureColumn(db, Constants.TABLE_AUDIT_TELEMETRY, Constants.COL_EVENT_DETAIL, Constants.COL_EVENT_DETAIL + " TEXT");
        ensureColumn(db, Constants.TABLE_AUDIT_TELEMETRY, Constants.COL_CREATED_AT, Constants.COL_CREATED_AT + " TEXT");
    }

    private void normalizeCloudAnchorTtl(SQLiteDatabase db) {
        try {
            db.execSQL("UPDATE " + Constants.TABLE_STORE_REFERENCES + " SET " + Constants.COL_CLOUD_ANCHOR_TTL_DAYS + "=" + Constants.CLOUD_ANCHOR_TTL_DAYS + " WHERE " + Constants.COL_CLOUD_ANCHOR_TTL_DAYS + " IS NULL OR " + Constants.COL_CLOUD_ANCHOR_TTL_DAYS + "!=" + Constants.CLOUD_ANCHOR_TTL_DAYS);
        } catch (Exception ignore) {
        }
        try {
            db.execSQL("UPDATE " + Constants.TABLE_SHELVES + " SET " + Constants.COL_CLOUD_ANCHOR_TTL_DAYS + "=" + Constants.CLOUD_ANCHOR_TTL_DAYS + " WHERE " + Constants.COL_CLOUD_ANCHOR_TTL_DAYS + " IS NULL OR " + Constants.COL_CLOUD_ANCHOR_TTL_DAYS + "!=" + Constants.CLOUD_ANCHOR_TTL_DAYS);
        } catch (Exception ignore) {
        }
    }

    private void ensureColumn(SQLiteDatabase db, String table, String column, String definition) {
        if (!hasColumn(db, table, column)) {
            safeAlter(db, table, definition);
        }
    }

    private boolean hasColumn(SQLiteDatabase db, String table, String column) {
        Cursor cursor = null;
        try {
            cursor = db.rawQuery("PRAGMA table_info(" + table + ")", null);
            if (cursor != null) {
                int nameIndex = cursor.getColumnIndex("name");
                while (cursor.moveToNext()) {
                    if (nameIndex >= 0 && column.equalsIgnoreCase(cursor.getString(nameIndex))) {
                        return true;
                    }
                }
            }
        } catch (Exception ignore) {
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return false;
    }

    private void safeAlter(SQLiteDatabase db, String table, String definition) {
        try {
            db.execSQL("ALTER TABLE " + table + " ADD COLUMN " + definition);
        } catch (Exception ignore) {
        }
    }
}
