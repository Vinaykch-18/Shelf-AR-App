package com.salesdairy.shelfarapp.sensors;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

public class OrientationHelper implements SensorEventListener {

    private static final float FILTER_ALPHA = 0.18f;

    private static final float MAX_SIDE_TILT_CAPTURE_READY = 5.5f;
    private static final float MIN_VERTICAL_AXIS_CAPTURE_READY = 7.5f;
    private static final float MAX_FORWARD_BACK_CAPTURE_READY = 5.0f;

    private static final float MAX_SIDE_TILT_AUDIT_READY = 6.8f;
    private static final float MIN_VERTICAL_AXIS_AUDIT_READY = 6.2f;
    private static final float MAX_FORWARD_BACK_AUDIT_READY = 6.6f;

    private static final float MAX_SIDE_TILT_AUDIT_CAPTURE = 5.0f;
    private static final float MIN_VERTICAL_AXIS_AUDIT_CAPTURE = 7.4f;
    private static final float MAX_FORWARD_BACK_AUDIT_CAPTURE = 2.6f;

    private static final float MAX_SIDE_TILT_STRICT = 3.0f;
    private static final float MIN_VERTICAL_AXIS_STRICT = 8.8f;
    private static final float MAX_FORWARD_BACK_STRICT = 3.0f;

    private static final float OVERLAY_ALIGNED_THRESHOLD = 0.40f;

    private final SensorManager sensorManager;
    private final Sensor postureSensor;

    private float x;
    private float y;
    private float z;
    private boolean hasReading = false;

    public OrientationHelper(Context context) {
        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);

        Sensor gravitySensor = sensorManager != null
                ? sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
                : null;

        postureSensor = gravitySensor != null
                ? gravitySensor
                : (sensorManager != null
                ? sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
                : null);
    }

    public void register() {
        if (sensorManager != null && postureSensor != null) {
            sensorManager.registerListener(this, postureSensor, SensorManager.SENSOR_DELAY_UI);
        }
    }

    public void unregister() {
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event == null || event.sensor == null || postureSensor == null) return;
        if (event.sensor.getType() != postureSensor.getType()) return;

        if (!hasReading) {
            x = event.values[0];
            y = event.values[1];
            z = event.values[2];
            hasReading = true;
            return;
        }

        x += FILTER_ALPHA * (event.values[0] - x);
        y += FILTER_ALPHA * (event.values[1] - y);
        z += FILTER_ALPHA * (event.values[2] - z);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    public boolean hasSensorReading() {
        return hasReading;
    }

    public boolean isCaptureReadyPosture() {
        if (!hasReading) return false;
        return Math.abs(x) <= MAX_SIDE_TILT_CAPTURE_READY
                && y >= MIN_VERTICAL_AXIS_CAPTURE_READY
                && Math.abs(z) <= MAX_FORWARD_BACK_CAPTURE_READY;
    }

    public boolean isAuditLocalizationPostureReady() {
        if (!hasReading) return false;
        return Math.abs(x) <= MAX_SIDE_TILT_AUDIT_READY
                && y >= MIN_VERTICAL_AXIS_AUDIT_READY
                && Math.abs(z) <= MAX_FORWARD_BACK_AUDIT_READY;
    }

    public boolean isStrictlyUpright() {
        if (!hasReading) return false;
        return Math.abs(x) <= MAX_SIDE_TILT_STRICT
                && y >= MIN_VERTICAL_AXIS_STRICT
                && Math.abs(z) <= MAX_FORWARD_BACK_STRICT;
    }

    public boolean isAuditPhotoCaptureReady() {
        if (!hasReading) return false;
        return Math.abs(x) <= MAX_SIDE_TILT_AUDIT_CAPTURE
                && y >= MIN_VERTICAL_AXIS_AUDIT_CAPTURE
                && Math.abs(z) <= MAX_FORWARD_BACK_AUDIT_CAPTURE;
    }

    public String getPostureGuidance() {
        if (!hasReading) {
            return "Hold phone steady";
        }

        if (Math.abs(z) > MAX_FORWARD_BACK_CAPTURE_READY) {
            if (z > 0) {
                return "Point phone straight ahead - not down at the shelf";
            }
            return "Point phone straight ahead - not up toward the ceiling";
        }

        if (Math.abs(x) > MAX_SIDE_TILT_CAPTURE_READY) {
            return x > 0 ? "Tilt phone slightly left" : "Tilt phone slightly right";
        }

        if (y < MIN_VERTICAL_AXIS_CAPTURE_READY) {
            return "Hold phone more upright";
        }

        if (isStrictlyUpright()) {
            return "Great hold";
        }

        return "Hold steady";
    }

    public String getAuditPostureGuidance() {
        if (!hasReading) {
            return "Hold phone steady";
        }

        if (Math.abs(z) > MAX_FORWARD_BACK_AUDIT_READY) {
            return z > 0
                    ? "Raise the phone a little - do not point it down"
                    : "Lower the phone a little - do not point it up";
        }

        if (Math.abs(x) > MAX_SIDE_TILT_AUDIT_READY) {
            return x > 0 ? "Tilt the phone slightly left" : "Tilt the phone slightly right";
        }

        if (y < MIN_VERTICAL_AXIS_AUDIT_READY) {
            return "Hold the phone a bit more upright";
        }

        if (isStrictlyUpright()) {
            return "Great hold";
        }

        return "Phone posture looks good";
    }

    public String getAuditCaptureGuidance() {
        if (!hasReading) {
            return "Hold phone straight before capture";
        }

        if (Math.abs(z) > MAX_FORWARD_BACK_AUDIT_CAPTURE) {
            return z > 0
                    ? "Do not point the phone down at the floor while capturing"
                    : "Do not point the phone up toward the ceiling while capturing";
        }

        if (Math.abs(x) > MAX_SIDE_TILT_AUDIT_CAPTURE) {
            return x > 0
                    ? "Tilt the phone a little left before capturing"
                    : "Tilt the phone a little right before capturing";
        }

        if (y < MIN_VERTICAL_AXIS_AUDIT_CAPTURE) {
            return "Hold the phone more straight before capturing";
        }

        return isStrictlyUpright() ? "Phone is straight - capture now" : "Phone is straight enough - capture now";
    }

    public float getOverlayOffsetX() {
        return clamp(x / MAX_SIDE_TILT_CAPTURE_READY, -1f, 1f);
    }

    public float getOverlayOffsetY() {
        return clamp(z / MAX_FORWARD_BACK_CAPTURE_READY, -1f, 1f);
    }

    public boolean isOverlayAligned() {
        return Math.abs(getOverlayOffsetX()) <= OVERLAY_ALIGNED_THRESHOLD
                && Math.abs(getOverlayOffsetY()) <= OVERLAY_ALIGNED_THRESHOLD;
    }

    public float getX() { return x; }
    public float getY() { return y; }
    public float getZ() { return z; }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
