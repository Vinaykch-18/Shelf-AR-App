package com.salesdairy.shelfarapp.audit;

import android.util.Log;

import com.google.ar.core.Pose;
import com.salesdairy.shelfarapp.models.Shelf;

public final class AuditPoseHelper {

    private static final String TAG = "ShelfARFlow";

    private AuditPoseHelper() {
    }

    public static Pose buildAbsoluteShelfPose(Pose resolvedReferencePose, Shelf shelf) {
        Pose relative = buildRelativeShelfPose(shelf);
        return resolvedReferencePose == null || relative == null ? null : resolvedReferencePose.compose(relative);
    }

    public static Pose buildAbsoluteCameraPose(Pose resolvedReferencePose, Shelf shelf) {
        Pose relative = buildRelativeCameraPose(shelf);
        return resolvedReferencePose == null || relative == null ? null : resolvedReferencePose.compose(relative);
    }

    public static Pose buildAbsolutePhoneFloorPose(Pose resolvedReferencePose, Shelf shelf) {
        if (resolvedReferencePose == null || shelf == null) {
            return null;
        }

        Pose absoluteCameraPose = buildAbsoluteCameraPose(resolvedReferencePose, shelf);
        if (absoluteCameraPose != null) {
            try {
                return new Pose(
                        new float[]{absoluteCameraPose.tx(), resolvedReferencePose.ty(), absoluteCameraPose.tz()},
                        absoluteCameraPose.getRotationQuaternion()
                );
            } catch (Exception e) {
                Log.e(TAG, "Invalid projected phone-floor pose for shelfId=" + shelf.getId(), e);
            }
        }

        Pose relative = buildRelativePhoneFloorPose(shelf);
        return relative == null ? null : resolvedReferencePose.compose(relative);
    }

    // Compatibility wrapper for older audit code paths.
    public static Pose buildAbsoluteStandFeetPose(Pose resolvedReferencePose, Shelf shelf) {
        return buildAbsolutePhoneFloorPose(resolvedReferencePose, shelf);
    }

    public static Pose buildWalkApproachPose(Pose targetPhoneFloorPose, Pose targetDisplayShelfPose) {
        // Keep path guidance visible slightly before the exact stand point so the
        // rep approaches the ring from a stable direction instead of the route
        // collapsing too early right on top of the saved pose.
        if (targetPhoneFloorPose == null) {
            return targetDisplayShelfPose;
        }
        if (targetDisplayShelfPose == null) {
            return targetPhoneFloorPose;
        }
        try {
            float dx = targetPhoneFloorPose.tx() - targetDisplayShelfPose.tx();
            float dz = targetPhoneFloorPose.tz() - targetDisplayShelfPose.tz();
            float len = (float) Math.sqrt((dx * dx) + (dz * dz));
            if (len < 0.08f || Float.isNaN(len)) {
                return targetPhoneFloorPose;
            }
            float offset = Math.min(0.65f, Math.max(0.34f, len * 0.30f));
            float ox = targetPhoneFloorPose.tx() + ((dx / len) * offset);
            float oz = targetPhoneFloorPose.tz() + ((dz / len) * offset);
            return new Pose(
                    new float[]{ox, targetPhoneFloorPose.ty(), oz},
                    targetPhoneFloorPose.getRotationQuaternion()
            );
        } catch (Exception e) {
            Log.e(TAG, "Invalid walk approach pose", e);
            return targetPhoneFloorPose;
        }
    }

    public static Pose buildDisplayShelfPose(Pose resolvedReferencePose, Shelf shelf) {
        Pose actualShelf = buildAbsoluteShelfPose(resolvedReferencePose, shelf);
        Pose cameraPose = buildAbsoluteCameraPose(resolvedReferencePose, shelf);
        if (actualShelf != null) {
            if (cameraPose == null || isShelfPosePlausibleFromCamera(cameraPose, actualShelf)) {
                return actualShelf;
            }
        }
        return buildCameraDerivedShelfCue(cameraPose);
    }

    public static Pose buildRelativeCameraToShelfPose(Shelf shelf) {
        Pose relativeCamera = buildRelativeCameraPose(shelf);
        Pose relativeShelf = buildRelativeShelfPose(shelf);
        if (relativeCamera == null || relativeShelf == null) {
            return null;
        }
        try {
            return relativeCamera.inverse().compose(relativeShelf);
        } catch (Exception e) {
            Log.e(TAG, "Invalid saved camera-to-shelf offset for shelfId=" + shelf.getId(), e);
            return null;
        }
    }

    public static Pose buildLiveDisplayShelfPose(Pose currentCameraPose,
                                                 Pose fallbackShelfPose,
                                                 Pose relativeCameraToShelfPose,
                                                 float approachDistanceMeters) {
        if (currentCameraPose != null && relativeCameraToShelfPose != null && approachDistanceMeters <= 0.38f) {
            try {
                Pose live = currentCameraPose.compose(relativeCameraToShelfPose);
                if (!isShelfPosePlausibleFromCamera(currentCameraPose, live)) {
                    return fallbackShelfPose;
                }
                if (fallbackShelfPose != null) {
                    float dx = live.tx() - fallbackShelfPose.tx();
                    float dy = live.ty() - fallbackShelfPose.ty();
                    float dz = live.tz() - fallbackShelfPose.tz();
                    float delta = (float) Math.sqrt((dx * dx) + (dy * dy) + (dz * dz));
                    if (delta > 0.18f) {
                        return fallbackShelfPose;
                    }
                    float closeness = Math.max(0f, Math.min(1f, (0.45f - approachDistanceMeters) / 0.22f));
                    float liveWeight = 0.08f + (closeness * 0.10f);
                    float blendX = fallbackShelfPose.tx() + ((live.tx() - fallbackShelfPose.tx()) * liveWeight);
                    float blendY = fallbackShelfPose.ty() + ((live.ty() - fallbackShelfPose.ty()) * liveWeight);
                    float blendZ = fallbackShelfPose.tz() + ((live.tz() - fallbackShelfPose.tz()) * liveWeight);
                    return new Pose(new float[]{blendX, blendY, blendZ}, fallbackShelfPose.getRotationQuaternion());
                }
                return live;
            } catch (Exception e) {
                Log.e(TAG, "Invalid live shelf cue pose", e);
            }
        }
        return fallbackShelfPose;
    }

    public static Pose buildRelativeShelfPose(Shelf shelf) {
        if (shelf == null) {
            return null;
        }
        return createPose(
                shelf.getAnchorX(), shelf.getAnchorY(), shelf.getAnchorZ(),
                shelf.getRotX(), shelf.getRotY(), shelf.getRotZ(), shelf.getRotW(),
                shelf.getId(), true
        );
    }

    public static Pose buildRelativeCameraPose(Shelf shelf) {
        if (shelf == null) {
            return null;
        }
        return createPose(
                shelf.getCameraX(), shelf.getCameraY(), shelf.getCameraZ(),
                shelf.getCameraRotX(), shelf.getCameraRotY(), shelf.getCameraRotZ(), shelf.getCameraRotW(),
                shelf.getId(), false
        );
    }

    public static Pose buildRelativePhoneFloorPose(Shelf shelf) {
        if (shelf == null) {
            return null;
        }
        try {
            return Pose.makeTranslation(shelf.getCameraX(), 0f, shelf.getCameraZ());
        } catch (Exception e) {
            Log.e(TAG, "Invalid saved phone-floor pose for shelfId=" + shelf.getId(), e);
            return null;
        }
    }

    // Compatibility wrapper for older audit code paths.
    public static Pose buildRelativeStandFeetPose(Shelf shelf) {
        return buildRelativePhoneFloorPose(shelf);
    }

    private static Pose createPose(float tx, float ty, float tz,
                                   float qx, float qy, float qz, float qw,
                                   int shelfId, boolean shelfPose) {
        float[] normalized = normalizeQuaternion(qx, qy, qz, qw);
        try {
            return new Pose(new float[]{tx, ty, tz}, normalized);
        } catch (Exception e) {
            Log.e(TAG, "Invalid saved " + (shelfPose ? "shelf" : "camera") + " pose for shelfId=" + shelfId, e);
            return null;
        }
    }

    private static Pose buildCameraDerivedShelfCue(Pose cameraPose) {
        if (cameraPose == null) {
            return null;
        }
        try {
            return cameraPose.compose(Pose.makeTranslation(0f, -0.05f, -0.95f));
        } catch (Exception e) {
            Log.e(TAG, "Invalid camera-derived shelf cue", e);
            return null;
        }
    }

    private static boolean isShelfPosePlausibleFromCamera(Pose cameraPose, Pose shelfPose) {
        try {
            Pose shelfInCamera = cameraPose.inverse().compose(shelfPose);
            float[] local = shelfInCamera.getTranslation();
            float side = local[0];
            float up = local[1];
            float forward = -local[2];
            if (Float.isNaN(side) || Float.isNaN(up) || Float.isNaN(forward)) {
                return false;
            }
            return forward >= 0.35f && forward <= 2.20f
                    && Math.abs(side) <= 1.15f
                    && up >= -0.90f && up <= 1.20f;
        } catch (Exception e) {
            Log.e(TAG, "Failed to validate saved shelf pose", e);
            return false;
        }
    }

    private static float[] normalizeQuaternion(float qx, float qy, float qz, float qw) {
        float mag = (float) Math.sqrt((qx * qx) + (qy * qy) + (qz * qz) + (qw * qw));
        if (mag < 0.0001f || Float.isNaN(mag)) {
            return new float[]{0f, 0f, 0f, 1f};
        }
        return new float[]{qx / mag, qy / mag, qz / mag, qw / mag};
    }
}
