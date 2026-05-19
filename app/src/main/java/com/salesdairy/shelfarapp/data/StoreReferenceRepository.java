package com.salesdairy.shelfarapp.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;
import android.text.TextUtils;

import com.salesdairy.shelfarapp.models.StoreReference;
import com.salesdairy.shelfarapp.utils.Constants;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class StoreReferenceRepository {

    private static final String TAG = "ShelfARFlow";
    private final Context appContext;
    private final DBHelper dbHelper;

    public StoreReferenceRepository(Context context) {
        appContext = context.getApplicationContext();
        dbHelper = new DBHelper(appContext);
    }

    public long insertStoreReference(StoreReference reference) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        if (reference.isActive()) {
            clearActiveFlag(db);
        }
        return db.insert(Constants.TABLE_STORE_REFERENCES, null, toValues(reference));
    }

    public int updateStoreReference(StoreReference reference) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        if (reference.isActive()) {
            clearActiveFlag(db);
        }
        return db.update(Constants.TABLE_STORE_REFERENCES, toValues(reference), Constants.COL_ID + "=?",
                new String[]{String.valueOf(reference.getId())});
    }

    public StoreReference getActiveStoreReference() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = db.query(Constants.TABLE_STORE_REFERENCES, null,
                Constants.COL_IS_ACTIVE + "=1", null, null, null,
                Constants.COL_ID + " DESC", "1");
        StoreReference reference = null;
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                reference = map(cursor);
            }
            cursor.close();
        }
        return reference;
    }

    public boolean setActiveStoreReference(int id) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.beginTransaction();
        try {
            clearActiveFlag(db);
            ContentValues values = new ContentValues();
            values.put(Constants.COL_IS_ACTIVE, 1);
            int rows = db.update(Constants.TABLE_STORE_REFERENCES, values, Constants.COL_ID + "=?",
                    new String[]{String.valueOf(id)});
            db.setTransactionSuccessful();
            return rows > 0;
        } finally {
            db.endTransaction();
        }
    }

    public List<StoreReference> getHostedStoreReferences() {
        List<StoreReference> list = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        String selection = Constants.COL_CLOUD_ANCHOR_ID + " IS NOT NULL AND TRIM(" + Constants.COL_CLOUD_ANCHOR_ID + ")!=''";
        Cursor cursor = db.query(Constants.TABLE_STORE_REFERENCES, null, selection, null, null, null,
                Constants.COL_IS_ACTIVE + " DESC, " + Constants.COL_REFERENCE_SCOPE_NAME + " COLLATE NOCASE ASC, " + Constants.COL_ID + " DESC");
        if (cursor != null) {
            while (cursor.moveToNext()) {
                list.add(map(cursor));
            }
            cursor.close();
        }
        return list;
    }

    public StoreReference getPreferredStoreReference() {
        StoreReference active = getActiveStoreReference();
        if (active != null && !TextUtils.isEmpty(active.getCloudAnchorId())) {
            return active;
        }
        List<StoreReference> hosted = getHostedStoreReferences();
        return hosted.isEmpty() ? null : hosted.get(0);
    }

    public StoreReference getStoreReferenceById(int id) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = db.query(Constants.TABLE_STORE_REFERENCES, null,
                Constants.COL_ID + "=?", new String[]{String.valueOf(id)}, null, null, null);
        StoreReference reference = null;
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                reference = map(cursor);
            }
            cursor.close();
        }
        return reference;
    }

    public List<StoreReference> getAllStoreReferences() {
        List<StoreReference> list = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = db.query(Constants.TABLE_STORE_REFERENCES, null, null, null, null, null,
                Constants.COL_IS_ACTIVE + " DESC, " + Constants.COL_ID + " DESC");
        if (cursor != null) {
            while (cursor.moveToNext()) {
                list.add(map(cursor));
            }
            cursor.close();
        }
        return list;
    }

    public int deactivateAll() {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(Constants.COL_IS_ACTIVE, 0);
        return db.update(Constants.TABLE_STORE_REFERENCES, values, null, null);
    }

    public boolean deleteById(int id) {
        StoreReference reference = getStoreReferenceById(id);
        if (reference == null) {
            return false;
        }

        SQLiteDatabase db = dbHelper.getWritableDatabase();
        List<String> shelfImages = new ArrayList<>();
        boolean deletedActive = reference.isActive();

        Cursor shelfCursor = db.query(Constants.TABLE_SHELVES,
                new String[]{Constants.COL_IMAGE_PATH},
                Constants.COL_STORE_REFERENCE_ID + "=?",
                new String[]{String.valueOf(id)}, null, null, null);
        if (shelfCursor != null) {
            while (shelfCursor.moveToNext()) {
                String path = shelfCursor.getString(0);
                if (!TextUtils.isEmpty(path)) {
                    shelfImages.add(path);
                }
            }
            shelfCursor.close();
        }

        db.beginTransaction();
        try {
            String[] args = new String[]{String.valueOf(id)};
            db.delete(Constants.TABLE_AUDIT_IMAGES,
                    Constants.COL_SHELF_AUDIT_ID + " IN (SELECT " + Constants.COL_ID + " FROM " + Constants.TABLE_SHELF_AUDITS
                            + " WHERE " + Constants.COL_SHELF_ID + " IN (SELECT " + Constants.COL_ID + " FROM " + Constants.TABLE_SHELVES
                            + " WHERE " + Constants.COL_STORE_REFERENCE_ID + "=?))",
                    args);
            db.delete(Constants.TABLE_SHELF_AUDITS,
                    Constants.COL_SHELF_ID + " IN (SELECT " + Constants.COL_ID + " FROM " + Constants.TABLE_SHELVES
                            + " WHERE " + Constants.COL_STORE_REFERENCE_ID + "=?)",
                    args);
            db.delete(Constants.TABLE_SHELVES, Constants.COL_STORE_REFERENCE_ID + "=?", args);
            int rows = db.delete(Constants.TABLE_STORE_REFERENCES, Constants.COL_ID + "=?", args);
            if (rows <= 0) {
                return false;
            }
            if (deletedActive) {
                Cursor nextCursor = db.query(Constants.TABLE_STORE_REFERENCES,
                        new String[]{Constants.COL_ID}, null, null, null, null,
                        Constants.COL_ID + " DESC", "1");
                if (nextCursor != null) {
                    if (nextCursor.moveToFirst()) {
                        ContentValues values = new ContentValues();
                        values.put(Constants.COL_IS_ACTIVE, 1);
                        db.update(Constants.TABLE_STORE_REFERENCES, values, Constants.COL_ID + "=?",
                                new String[]{String.valueOf(nextCursor.getInt(0))});
                    }
                    nextCursor.close();
                }
            }
            db.setTransactionSuccessful();
        } catch (Exception e) {
            Log.e(TAG, "Store reference delete failed", e);
            return false;
        } finally {
            db.endTransaction();
        }

        if (!TextUtils.isEmpty(reference.getImagePath())) {
            File file = new File(reference.getImagePath());
            if (file.exists() && !file.delete()) {
                Log.w(TAG, "Reference image delete failed: " + reference.getImagePath());
            }
        }
        for (String path : shelfImages) {
            File file = new File(path);
            if (file.exists() && !file.delete()) {
                Log.w(TAG, "Shelf image delete failed: " + path);
            }
        }
        return true;
    }


        private void clearActiveFlag(SQLiteDatabase db) {
        ContentValues values = new ContentValues();
        values.put(Constants.COL_IS_ACTIVE, 0);
        db.update(Constants.TABLE_STORE_REFERENCES, values, null, null);
    }

    private ContentValues toValues(StoreReference reference) {
        ContentValues values = new ContentValues();
        values.put(Constants.COL_OUTLET_ID, reference.getOutletId());
        values.put(Constants.COL_REFERENCE_SCOPE_NAME, reference.getReferenceScopeName());
        values.put(Constants.COL_REFERENCE_NAME, reference.getReferenceName());
        values.put(Constants.COL_REFERENCE_IMAGE_PATH, reference.getImagePath());
        values.put(Constants.COL_REFERENCE_HINT, reference.getHint());
        values.put(Constants.COL_CLOUD_ANCHOR_ID, reference.getCloudAnchorId());
        values.put(Constants.COL_CLOUD_ANCHOR_STATUS, reference.getCloudAnchorStatus());
        values.put(Constants.COL_CLOUD_ANCHOR_ERROR, reference.getCloudAnchorError());
        values.put(Constants.COL_CLOUD_ANCHOR_HOSTED_AT, reference.getCloudAnchorHostedAt());
        values.put(Constants.COL_CLOUD_ANCHOR_TTL_DAYS, reference.getCloudAnchorTtlDays());
        values.put(Constants.COL_CREATED_AT, reference.getCreatedAt());
        values.put(Constants.COL_IS_ACTIVE, reference.isActive() ? 1 : 0);
        return values;
    }

    private StoreReference map(Cursor cursor) {
        StoreReference reference = new StoreReference();
        reference.setId(cursor.getInt(cursor.getColumnIndexOrThrow(Constants.COL_ID)));
        reference.setOutletId(cursor.getInt(cursor.getColumnIndexOrThrow(Constants.COL_OUTLET_ID)));
        reference.setReferenceScopeName(cursor.getString(cursor.getColumnIndexOrThrow(Constants.COL_REFERENCE_SCOPE_NAME)));
        reference.setReferenceName(cursor.getString(cursor.getColumnIndexOrThrow(Constants.COL_REFERENCE_NAME)));
        reference.setImagePath(getString(cursor, Constants.COL_REFERENCE_IMAGE_PATH));
        reference.setHint(getString(cursor, Constants.COL_REFERENCE_HINT));
        reference.setCloudAnchorId(getString(cursor, Constants.COL_CLOUD_ANCHOR_ID));
        reference.setCloudAnchorStatus(getString(cursor, Constants.COL_CLOUD_ANCHOR_STATUS));
        reference.setCloudAnchorError(getString(cursor, Constants.COL_CLOUD_ANCHOR_ERROR));
        reference.setCloudAnchorHostedAt(getLong(cursor, Constants.COL_CLOUD_ANCHOR_HOSTED_AT));
        reference.setCloudAnchorTtlDays(getInt(cursor, Constants.COL_CLOUD_ANCHOR_TTL_DAYS, Constants.CLOUD_ANCHOR_TTL_DAYS));
        reference.setCreatedAt(getString(cursor, Constants.COL_CREATED_AT));
        reference.setActive(getInt(cursor, Constants.COL_IS_ACTIVE, 0) == 1);
        return reference;
    }

    private String getString(Cursor cursor, String column) { int index = cursor.getColumnIndex(column); return index >= 0 ? cursor.getString(index) : null; }
    private long getLong(Cursor cursor, String column) { int index = cursor.getColumnIndex(column); return index >= 0 ? cursor.getLong(index) : 0L; }
    private int getInt(Cursor cursor, String column, int def) { int index = cursor.getColumnIndex(column); return index >= 0 ? cursor.getInt(index) : def; }
}
