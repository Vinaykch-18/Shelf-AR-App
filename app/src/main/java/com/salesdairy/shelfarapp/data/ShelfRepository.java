package com.salesdairy.shelfarapp.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.text.TextUtils;
import android.util.Log;

import com.salesdairy.shelfarapp.models.Shelf;
import com.salesdairy.shelfarapp.utils.Constants;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ShelfRepository {

    private static final String TAG = "ShelfARFlow";
    private final DBHelper dbHelper;

    public ShelfRepository(Context context) {
        dbHelper = new DBHelper(context);
    }

    public long insertShelf(Shelf shelf) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        return db.insert(Constants.TABLE_SHELVES, null, buildShelfValues(shelf));
    }

    public List<Shelf> getAllShelves() {
        return queryShelves(null, null);
    }

    public List<Shelf> getShelvesForStoreReference(int storeReferenceId) {
        return queryShelves(Constants.COL_STORE_REFERENCE_ID + "=?", new String[]{String.valueOf(storeReferenceId)});
    }

    public Shelf getShelfById(int shelfId) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = db.query(Constants.TABLE_SHELVES, null, Constants.COL_ID + "=?",
                new String[]{String.valueOf(shelfId)}, null, null, null);
        Shelf shelf = null;
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                shelf = mapCursorToShelf(cursor);
            }
            cursor.close();
        }
        return shelf;
    }

    public int getNextRouteOrderForReference(int storeReferenceId) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = db.rawQuery(
                "SELECT COALESCE(MAX(" + Constants.COL_ROUTE_ORDER + "), 0) FROM " + Constants.TABLE_SHELVES +
                        " WHERE " + Constants.COL_STORE_REFERENCE_ID + "=?",
                new String[]{String.valueOf(storeReferenceId)}
        );
        int nextOrder = 1;
        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    nextOrder = cursor.getInt(0) + 1;
                }
            } finally {
                cursor.close();
            }
        }
        return nextOrder;
    }

    public int updateCloudAnchorFields(int shelfId, String cloudAnchorId, String cloudAnchorStatus,
                                       String cloudAnchorError, long hostedAt, int ttlDays) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(Constants.COL_CLOUD_ANCHOR_ID, cloudAnchorId);
        values.put(Constants.COL_CLOUD_ANCHOR_STATUS, cloudAnchorStatus);
        values.put(Constants.COL_CLOUD_ANCHOR_ERROR, cloudAnchorError);
        values.put(Constants.COL_CLOUD_ANCHOR_HOSTED_AT, hostedAt);
        values.put(Constants.COL_CLOUD_ANCHOR_TTL_DAYS, ttlDays);
        return db.update(Constants.TABLE_SHELVES, values, Constants.COL_ID + "=?", new String[]{String.valueOf(shelfId)});
    }

    public int updateGuideAnchorBundle(int shelfId, String guideAnchorBundle) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(Constants.COL_GUIDE_ANCHOR_BUNDLE, guideAnchorBundle);
        return db.update(Constants.TABLE_SHELVES, values, Constants.COL_ID + "=?", new String[]{String.valueOf(shelfId)});
    }

    public boolean deleteShelvesForStoreReference(int storeReferenceId) {
        List<Shelf> shelves = getShelvesForStoreReference(storeReferenceId);
        boolean deletedAny = false;
        for (Shelf shelf : shelves) {
            deletedAny = deleteShelfById(shelf.getId()) || deletedAny;
        }
        return deletedAny || shelves.isEmpty();
    }

    public boolean deleteShelfById(int shelfId) {
        Shelf shelf = getShelfById(shelfId);
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.delete(Constants.TABLE_AUDIT_IMAGES,
                Constants.COL_SHELF_AUDIT_ID + " IN (SELECT " + Constants.COL_ID + " FROM " + Constants.TABLE_SHELF_AUDITS + " WHERE " + Constants.COL_SHELF_ID + "=?)",
                new String[]{String.valueOf(shelfId)});
        db.delete(Constants.TABLE_SHELF_AUDITS, Constants.COL_SHELF_ID + "=?", new String[]{String.valueOf(shelfId)});
        int deletedRows = db.delete(Constants.TABLE_SHELVES, Constants.COL_ID + "=?", new String[]{String.valueOf(shelfId)});
        if (deletedRows > 0 && shelf != null && !TextUtils.isEmpty(shelf.getImagePath())) {
            File file = new File(shelf.getImagePath());
            if (file.exists() && !file.delete()) {
                Log.w(TAG, "Image delete failed for shelfId=" + shelfId + ", path=" + shelf.getImagePath());
            }
        }
        return deletedRows > 0;
    }


    public List<Shelf> getShelvesForReference(int storeReferenceId) {
        return getShelvesForStoreReference(storeReferenceId);
    }

    public boolean deleteShelvesForReference(int storeReferenceId) {
        return deleteShelvesForStoreReference(storeReferenceId);
    }

    private List<Shelf> queryShelves(String selection, String[] args) {
        List<Shelf> shelves = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        String orderBy = Constants.COL_ROUTE_ORDER + " ASC, " + Constants.COL_ID + " DESC";
        Cursor cursor = db.query(Constants.TABLE_SHELVES, null, selection, args, null, null, orderBy);
        if (cursor != null) {
            while (cursor.moveToNext()) {
                shelves.add(mapCursorToShelf(cursor));
            }
            cursor.close();
        }
        return shelves;
    }

    private ContentValues buildShelfValues(Shelf shelf) {
        ContentValues values = new ContentValues();
        values.put(Constants.COL_OUTLET_ID, shelf.getOutletId());
        values.put(Constants.COL_STORE_REFERENCE_ID, shelf.getStoreReferenceId());
        values.put(Constants.COL_ROUTE_LABEL, shelf.getRouteLabel());
        values.put(Constants.COL_ROUTE_ORDER, shelf.getRouteOrder());
        values.put(Constants.COL_NEAREST_CHECKPOINT_ID, shelf.getNearestCheckpointId());
        values.put(Constants.COL_SHELF_NAME, shelf.getShelfName());
        values.put(Constants.COL_IMAGE_PATH, shelf.getImagePath());
        values.put(Constants.COL_ANCHOR_X, shelf.getAnchorX());
        values.put(Constants.COL_ANCHOR_Y, shelf.getAnchorY());
        values.put(Constants.COL_ANCHOR_Z, shelf.getAnchorZ());
        values.put(Constants.COL_ROT_X, shelf.getRotX());
        values.put(Constants.COL_ROT_Y, shelf.getRotY());
        values.put(Constants.COL_ROT_Z, shelf.getRotZ());
        values.put(Constants.COL_ROT_W, shelf.getRotW());
        values.put(Constants.COL_CAMERA_X, shelf.getCameraX());
        values.put(Constants.COL_CAMERA_Y, shelf.getCameraY());
        values.put(Constants.COL_CAMERA_Z, shelf.getCameraZ());
        values.put(Constants.COL_CAMERA_ROT_X, shelf.getCameraRotX());
        values.put(Constants.COL_CAMERA_ROT_Y, shelf.getCameraRotY());
        values.put(Constants.COL_CAMERA_ROT_Z, shelf.getCameraRotZ());
        values.put(Constants.COL_CAMERA_ROT_W, shelf.getCameraRotW());
        values.put(Constants.COL_CREATED_AT, shelf.getCreatedAt());
        values.put(Constants.COL_CLOUD_ANCHOR_ID, shelf.getCloudAnchorId());
        values.put(Constants.COL_CLOUD_ANCHOR_STATUS, shelf.getCloudAnchorStatus());
        values.put(Constants.COL_CLOUD_ANCHOR_ERROR, shelf.getCloudAnchorError());
        values.put(Constants.COL_CLOUD_ANCHOR_HOSTED_AT, shelf.getCloudAnchorHostedAt());
        values.put(Constants.COL_CLOUD_ANCHOR_TTL_DAYS, shelf.getCloudAnchorTtlDays());
        values.put(Constants.COL_GUIDE_ANCHOR_BUNDLE, shelf.getGuideAnchorBundle());
        return values;
    }

    private Shelf mapCursorToShelf(Cursor cursor) {
        Shelf shelf = new Shelf();
        shelf.setId(cursor.getInt(cursor.getColumnIndexOrThrow(Constants.COL_ID)));
        shelf.setOutletId(getOptionalInt(cursor, Constants.COL_OUTLET_ID, Constants.DEFAULT_OUTLET_ID));
        shelf.setStoreReferenceId(getOptionalInt(cursor, Constants.COL_STORE_REFERENCE_ID, 0));
        shelf.setRouteLabel(getOptionalString(cursor, Constants.COL_ROUTE_LABEL));
        shelf.setRouteOrder(getOptionalInt(cursor, Constants.COL_ROUTE_ORDER, 0));
        shelf.setNearestCheckpointId(getOptionalLong(cursor, Constants.COL_NEAREST_CHECKPOINT_ID));
        shelf.setShelfName(getOptionalString(cursor, Constants.COL_SHELF_NAME));
        shelf.setImagePath(getOptionalString(cursor, Constants.COL_IMAGE_PATH));
        shelf.setAnchorX(getOptionalFloat(cursor, Constants.COL_ANCHOR_X, 0f));
        shelf.setAnchorY(getOptionalFloat(cursor, Constants.COL_ANCHOR_Y, 0f));
        shelf.setAnchorZ(getOptionalFloat(cursor, Constants.COL_ANCHOR_Z, 0f));
        shelf.setRotX(getOptionalFloat(cursor, Constants.COL_ROT_X, 0f));
        shelf.setRotY(getOptionalFloat(cursor, Constants.COL_ROT_Y, 0f));
        shelf.setRotZ(getOptionalFloat(cursor, Constants.COL_ROT_Z, 0f));
        shelf.setRotW(getOptionalFloat(cursor, Constants.COL_ROT_W, 1f));
        shelf.setCameraX(getOptionalFloat(cursor, Constants.COL_CAMERA_X, 0f));
        shelf.setCameraY(getOptionalFloat(cursor, Constants.COL_CAMERA_Y, 0f));
        shelf.setCameraZ(getOptionalFloat(cursor, Constants.COL_CAMERA_Z, 0f));
        shelf.setCameraRotX(getOptionalFloat(cursor, Constants.COL_CAMERA_ROT_X, 0f));
        shelf.setCameraRotY(getOptionalFloat(cursor, Constants.COL_CAMERA_ROT_Y, 0f));
        shelf.setCameraRotZ(getOptionalFloat(cursor, Constants.COL_CAMERA_ROT_Z, 0f));
        shelf.setCameraRotW(getOptionalFloat(cursor, Constants.COL_CAMERA_ROT_W, 1f));
        shelf.setCreatedAt(getOptionalString(cursor, Constants.COL_CREATED_AT));
        shelf.setCloudAnchorId(getOptionalString(cursor, Constants.COL_CLOUD_ANCHOR_ID));
        shelf.setCloudAnchorStatus(getOptionalString(cursor, Constants.COL_CLOUD_ANCHOR_STATUS));
        shelf.setCloudAnchorError(getOptionalString(cursor, Constants.COL_CLOUD_ANCHOR_ERROR));
        shelf.setCloudAnchorHostedAt(getOptionalLong(cursor, Constants.COL_CLOUD_ANCHOR_HOSTED_AT));
        shelf.setCloudAnchorTtlDays(getOptionalInt(cursor, Constants.COL_CLOUD_ANCHOR_TTL_DAYS, Constants.CLOUD_ANCHOR_TTL_DAYS));
        shelf.setGuideAnchorBundle(getOptionalString(cursor, Constants.COL_GUIDE_ANCHOR_BUNDLE));
        return shelf;
    }

    private String getOptionalString(Cursor cursor, String columnName) { int index = cursor.getColumnIndex(columnName); return index >= 0 ? cursor.getString(index) : null; }
    private long getOptionalLong(Cursor cursor, String columnName) { int index = cursor.getColumnIndex(columnName); return index >= 0 ? cursor.getLong(index) : 0L; }
    private int getOptionalInt(Cursor cursor, String columnName, int defaultValue) { int index = cursor.getColumnIndex(columnName); return index >= 0 ? cursor.getInt(index) : defaultValue; }
    private float getOptionalFloat(Cursor cursor, String columnName, float defaultValue) { int index = cursor.getColumnIndex(columnName); return index >= 0 ? cursor.getFloat(index) : defaultValue; }


}
