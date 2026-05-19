package com.salesdairy.shelfarapp.audit;

import android.graphics.Bitmap;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;

import com.salesdairy.shelfarapp.databinding.ActivityAuditNavigationBinding;
import com.salesdairy.shelfarapp.models.StoreReference;
import com.salesdairy.shelfarapp.models.Shelf;
import com.salesdairy.shelfarapp.utils.ImageUtils;

import java.io.File;

public final class AuditGuideImageHelper {

    public static final int MODE_REFERENCE = 1;
    public static final int MODE_SHELF = 2;
    public static final int MODE_STAND = 3;

    private static final String TAG = "ShelfARFlow";
    private final ActivityAuditNavigationBinding binding;
    private String lastOverlayKey = "";

    public AuditGuideImageHelper(ActivityAuditNavigationBinding binding) {
        this.binding = binding;
    }

    public void renderInlinePreview(StoreReference storeReference, Shelf shelf, int mode) {
        binding.layoutGuidePreview.setVisibility(View.GONE);
    }

    public void showOverlay(StoreReference storeReference, Shelf shelf, int mode) {
        Copy copy = buildOverlayCopy(mode);
        String path = pickPath(storeReference, shelf, mode);
        String key = copy.title + "|" + copy.hint + "|" + safe(path);
        if (!key.equals(lastOverlayKey)) {
            lastOverlayKey = key;
            binding.tvSavedViewTitle.setText(copy.title);
            binding.tvSavedViewHint.setText(copy.hint);
            loadInto(binding.ivReferenceOverlay, path, 900, 900);
        }
        binding.savedViewOverlay.setVisibility(View.VISIBLE);
    }

    private Copy buildOverlayCopy(int mode) {
        if (mode == MODE_REFERENCE) {
            return new Copy("Saved reference photo", "Use this to match the saved store reference before guidance starts.");
        }
        if (mode == MODE_STAND) {
            return new Copy("Saved shelf photo", "Quick shelf check only while you match the phone frame.");
        }
        return new Copy("Saved shelf photo", "Open this only if you need a quick shelf check.");
    }

    private String pickPath(StoreReference storeReference, Shelf shelf, int mode) {
        String primary;
        String fallback;
        if (mode == MODE_REFERENCE) {
            primary = storeReference != null ? storeReference.getImagePath() : null;
            fallback = shelf != null ? shelf.getImagePath() : null;
        } else {
            primary = shelf != null ? shelf.getImagePath() : null;
            fallback = storeReference != null ? storeReference.getImagePath() : null;
        }
        return exists(primary) ? primary : fallback;
    }

    private boolean exists(String path) {
        return !TextUtils.isEmpty(path) && new File(path).exists();
    }

    private void loadInto(android.widget.ImageView imageView, String path, int reqWidth, int reqHeight) {
        try {
            if (exists(path)) {
                Bitmap bitmap = ImageUtils.decodeSampledBitmap(path, reqWidth, reqHeight);
                imageView.setImageBitmap(bitmap);
            } else {
                imageView.setImageDrawable(null);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to load saved guidance image", e);
            imageView.setImageDrawable(null);
        }
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private static final class Copy {
        final String title;
        final String hint;

        Copy(String title, String hint) {
            this.title = title;
            this.hint = hint;
        }
    }
}
