package com.salesdairy.shelfarapp.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ImageUtils {

    private static final int MAX_SAVED_EDGE_PX = 1600;
    private static final int JPEG_QUALITY = 88;

    public static File createImageFile(Context context) throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        File storageDir = context.getExternalFilesDir("Pictures");

        if (storageDir == null) {
            throw new IOException("Storage directory not available");
        }

        if (!storageDir.exists() && !storageDir.mkdirs()) {
            throw new IOException("Failed to create image directory");
        }

        return new File(storageDir, "SHELF_" + timeStamp + ".jpg");
    }

    public static String saveBitmapToFile(Context context, Bitmap bitmap) throws IOException {
        File file = createImageFile(context);
        Bitmap bitmapToSave = downscaleIfNeeded(bitmap, MAX_SAVED_EDGE_PX);

        FileOutputStream out = new FileOutputStream(file);
        try {
            bitmapToSave.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out);
            out.flush();
        } finally {
            out.close();
            if (bitmapToSave != bitmap) {
                bitmapToSave.recycle();
            }
        }

        return file.getAbsolutePath();
    }

    public static Bitmap decodeSampledBitmap(String path, int reqWidth, int reqHeight) {
        if (path == null || path.isEmpty()) return null;
        File file = new File(path);
        if (!file.exists()) return null;

        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;

        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = calculateInSampleSize(bounds, reqWidth, reqHeight);
        opts.inPreferredConfig = Bitmap.Config.RGB_565;
        return BitmapFactory.decodeFile(path, opts);
    }

    public static Bitmap downscaleIfNeeded(Bitmap bitmap, int maxEdgePx) {
        if (bitmap == null || maxEdgePx <= 0) return bitmap;
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int maxEdge = Math.max(width, height);
        if (maxEdge <= maxEdgePx) return bitmap;

        float scale = maxEdgePx / (float) maxEdge;
        int outW = Math.max(1, Math.round(width * scale));
        int outH = Math.max(1, Math.round(height * scale));
        return Bitmap.createScaledBitmap(bitmap, outW, outH, true);
    }



    public static Bitmap cropCenterKeepingAspect(Bitmap bitmap, float scaleFraction) {
        if (bitmap == null) return null;

        float safeScale = Math.max(0.1f, Math.min(1.0f, scaleFraction));
        if (safeScale >= 0.999f) return bitmap;

        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int cropWidth = Math.max(1, Math.round(width * safeScale));
        int cropHeight = Math.max(1, Math.round(height * safeScale));
        int left = Math.max(0, (width - cropWidth) / 2);
        int top = Math.max(0, (height - cropHeight) / 2);

        try {
            return Bitmap.createBitmap(bitmap, left, top, cropWidth, cropHeight);
        } catch (IllegalArgumentException e) {
            return bitmap;
        }
    }

    private static int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        int height = options.outHeight;
        int width = options.outWidth;
        int inSampleSize = 1;

        if (reqWidth <= 0) reqWidth = width;
        if (reqHeight <= 0) reqHeight = height;

        while ((height / inSampleSize) > reqHeight * 2 || (width / inSampleSize) > reqWidth * 2) {
            inSampleSize *= 2;
        }
        return Math.max(1, inSampleSize);
    }
}
