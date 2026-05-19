package com.salesdairy.shelfarapp.audit;

import android.content.Intent;
import android.os.Bundle;

import com.google.ar.core.Pose;
import com.salesdairy.shelfarapp.utils.Constants;

public final class AuditSessionBundle {

    private static final String EXTRA_RESOLVED_TX = "extra_resolved_tx";
    private static final String EXTRA_RESOLVED_TY = "extra_resolved_ty";
    private static final String EXTRA_RESOLVED_TZ = "extra_resolved_tz";
    private static final String EXTRA_RESOLVED_QX = "extra_resolved_qx";
    private static final String EXTRA_RESOLVED_QY = "extra_resolved_qy";
    private static final String EXTRA_RESOLVED_QZ = "extra_resolved_qz";
    private static final String EXTRA_RESOLVED_QW = "extra_resolved_qw";

    private AuditSessionBundle() {
    }

    public static void putSessionId(Intent intent, long sessionId) {
        if (intent != null && sessionId > 0L) {
            intent.putExtra(Constants.EXTRA_AUDIT_SESSION_ID, sessionId);
        }
    }

    public static long getSessionId(Intent intent) {
        return intent == null ? -1L : intent.getLongExtra(Constants.EXTRA_AUDIT_SESSION_ID, -1L);
    }

    public static void putResolvedPose(Intent intent, Pose pose) {
        if (intent == null || pose == null) {
            return;
        }
        float[] t = pose.getTranslation();
        float[] q = pose.getRotationQuaternion();
        intent.putExtra(EXTRA_RESOLVED_TX, t[0]);
        intent.putExtra(EXTRA_RESOLVED_TY, t[1]);
        intent.putExtra(EXTRA_RESOLVED_TZ, t[2]);
        intent.putExtra(EXTRA_RESOLVED_QX, q[0]);
        intent.putExtra(EXTRA_RESOLVED_QY, q[1]);
        intent.putExtra(EXTRA_RESOLVED_QZ, q[2]);
        intent.putExtra(EXTRA_RESOLVED_QW, q[3]);
    }

    public static Pose readResolvedPose(Intent intent) {
        if (intent == null) {
            return null;
        }
        Bundle extras = intent.getExtras();
        if (extras == null || !extras.containsKey(EXTRA_RESOLVED_TX) || !extras.containsKey(EXTRA_RESOLVED_QW)) {
            return null;
        }
        float[] translation = new float[]{
                extras.getFloat(EXTRA_RESOLVED_TX, 0f),
                extras.getFloat(EXTRA_RESOLVED_TY, 0f),
                extras.getFloat(EXTRA_RESOLVED_TZ, 0f)
        };
        float[] rotation = new float[]{
                extras.getFloat(EXTRA_RESOLVED_QX, 0f),
                extras.getFloat(EXTRA_RESOLVED_QY, 0f),
                extras.getFloat(EXTRA_RESOLVED_QZ, 0f),
                extras.getFloat(EXTRA_RESOLVED_QW, 1f)
        };
        try {
            return new Pose(translation, rotation);
        } catch (Exception ignored) {
            return null;
        }
    }
}
