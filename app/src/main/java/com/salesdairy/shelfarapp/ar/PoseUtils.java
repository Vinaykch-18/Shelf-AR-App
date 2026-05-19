package com.salesdairy.shelfarapp.ar;

import com.google.ar.core.Pose;

public class PoseUtils {

    public static float distance(float x1, float y1, float z1,
                                 float x2, float y2, float z2) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float dz = z2 - z1;
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    public static float yawDegrees(Pose pose) {
        if (pose == null) {
            return 0f;
        }
        float[] q = pose.getRotationQuaternion();
        float x = q[0];
        float y = q[1];
        float z = q[2];
        float w = q[3];
        float sinyCosp = 2f * (w * y + x * z);
        float cosyCosp = 1f - 2f * (y * y + z * z);
        return (float) Math.toDegrees(Math.atan2(sinyCosp, cosyCosp));
    }

    public static float normalizeDegrees(float degrees) {
        float value = degrees;
        while (value > 180f) value -= 360f;
        while (value < -180f) value += 360f;
        return value;
    }
}
