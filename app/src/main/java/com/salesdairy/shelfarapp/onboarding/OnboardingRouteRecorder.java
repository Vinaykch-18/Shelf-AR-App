package com.salesdairy.shelfarapp.onboarding;

import com.google.ar.core.Pose;
import com.salesdairy.shelfarapp.ar.PoseUtils;
import com.salesdairy.shelfarapp.data.RouteRepository;
import com.salesdairy.shelfarapp.data.TelemetryRepository;
import com.salesdairy.shelfarapp.models.RouteCheckpoint;
import com.salesdairy.shelfarapp.models.RouteEdge;
import com.salesdairy.shelfarapp.utils.Constants;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class OnboardingRouteRecorder {

    private static final float BASE_DISTANCE_METERS = 1.15f;
    private static final float MIN_DISTANCE_METERS = 0.90f;
    private static final float MAX_DISTANCE_METERS = 1.55f;
    private static final float BASE_TURN_THRESHOLD_DEGREES = 8.5f;
    private static final float MIN_TURN_THRESHOLD_DEGREES = 6.0f;
    private static final float MAX_TURN_THRESHOLD_DEGREES = 13.0f;
    private static final float LEVEL_CHANGE_VERTICAL_METERS = 1.1f;
    private static final float REDUNDANT_DISTANCE_METERS = 0.55f;
    private static final float REDUNDANT_YAW_DEGREES = 8.0f;

    private final RouteRepository routeRepository;
    private final TelemetryRepository telemetryRepository;
    private final int storeReferenceId;
    private final SimpleDateFormat timestampFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());

    private long lastCheckpointId = -1L;
    private long lastStableCheckpointId = -1L;
    private int lastSequence = 0;
    private float lastX;
    private float lastY;
    private float lastZ;
    private float lastYaw;
    private String lastRouteLabel;
    private boolean initialized;

    public OnboardingRouteRecorder(RouteRepository routeRepository,
                                   TelemetryRepository telemetryRepository,
                                   int storeReferenceId) {
        this.routeRepository = routeRepository;
        this.telemetryRepository = telemetryRepository;
        this.storeReferenceId = storeReferenceId;
    }

    public long maybeRecord(Pose relativeCameraPose,
                            String routeLabel,
                            boolean force,
                            String kind,
                            int sceneQualityScore,
                            float stabilityScore) {
        if (relativeCameraPose == null || storeReferenceId <= 0) {
            return lastCheckpointId;
        }
        float[] t = relativeCameraPose.getTranslation();
        float x = t[0];
        float y = t[1];
        float z = t[2];
        float yaw = PoseUtils.yawDegrees(relativeCameraPose);
        String normalizedKind = normalizeKind(kind);
        float confidence = clamp(((Math.max(0, sceneQualityScore) / 3f) * 0.55f) + (clamp(stabilityScore) * 0.45f));

        if (!initialized) {
            return createCheckpoint(x, y, z, yaw, routeLabel, normalizedKind, confidence, sceneQualityScore);
        }

        float distanceMeters = PoseUtils.distance(lastX, lastY, lastZ, x, y, z);
        float yawDelta = Math.abs(PoseUtils.normalizeDegrees(yaw - lastYaw));
        float verticalDelta = Math.abs(y - lastY);
        boolean levelChanged = lastRouteLabel != null && routeLabel != null && !lastRouteLabel.equalsIgnoreCase(routeLabel);
        boolean transition = levelChanged || verticalDelta >= LEVEL_CHANGE_VERTICAL_METERS;
        boolean lowSignal = confidence < 0.34f || sceneQualityScore <= 0;

        float distanceThreshold = interpolate(MAX_DISTANCE_METERS, MIN_DISTANCE_METERS, confidence);
        distanceThreshold = Math.max(MIN_DISTANCE_METERS, Math.min(MAX_DISTANCE_METERS, distanceThreshold));
        float turnThreshold = interpolate(MAX_TURN_THRESHOLD_DEGREES, MIN_TURN_THRESHOLD_DEGREES, confidence);
        turnThreshold = Math.max(MIN_TURN_THRESHOLD_DEGREES, Math.min(MAX_TURN_THRESHOLD_DEGREES, turnThreshold));

        boolean denseWalk = distanceMeters >= Math.max(BASE_DISTANCE_METERS, distanceThreshold);
        boolean denseTurn = yawDelta >= turnThreshold && distanceMeters >= Math.max(0.42f, distanceThreshold * 0.45f);
        boolean gentleProgress = confidence >= 0.72f && sceneQualityScore >= 2 && distanceMeters >= Math.max(0.72f, distanceThreshold * 0.72f);
        boolean redundant = !force && !transition
                && distanceMeters <= REDUNDANT_DISTANCE_METERS
                && yawDelta <= REDUNDANT_YAW_DEGREES;

        if (!force && !transition && lowSignal && !denseWalk && !denseTurn) {
            return lastCheckpointId;
        }
        if (redundant && !gentleProgress) {
            return lastCheckpointId;
        }
        if (force || transition || denseWalk || denseTurn || gentleProgress) {
            String resolvedKind;
            if (force) {
                resolvedKind = normalizedKind;
            } else if (transition) {
                resolvedKind = "TRANSITION";
            } else if (yawDelta >= turnThreshold) {
                resolvedKind = "TURN";
            } else if (gentleProgress && sceneQualityScore >= 2) {
                resolvedKind = "PATH_DENSE";
            } else {
                resolvedKind = normalizedKind;
            }
            return createCheckpoint(x, y, z, yaw, routeLabel, resolvedKind, confidence, sceneQualityScore);
        }
        return lastCheckpointId;
    }

    public long getLastCheckpointId() {
        return lastCheckpointId;
    }

    public long getLastStableCheckpointId() {
        return lastStableCheckpointId > 0L ? lastStableCheckpointId : lastCheckpointId;
    }

    public long getSuggestedShelfLinkCheckpointId(Pose relativeShelfPose, String routeLabel) {
        if (relativeShelfPose == null) {
            return getLastStableCheckpointId();
        }
        float[] t = relativeShelfPose.getTranslation();
        RouteCheckpoint checkpoint = routeRepository.getBestStableCheckpoint(storeReferenceId, t[0], t[1], t[2], routeLabel);
        if (checkpoint != null && checkpoint.getId() > 0L) {
            return checkpoint.getId();
        }
        return getLastStableCheckpointId();
    }

    public int getLastSequence() {
        return lastSequence;
    }

    public int getReferenceId() {
        return storeReferenceId;
    }

    private long createCheckpoint(float x,
                                  float y,
                                  float z,
                                  float yaw,
                                  String routeLabel,
                                  String kind,
                                  float confidence,
                                  int sceneQualityScore) {
        RouteCheckpoint checkpoint = new RouteCheckpoint();
        checkpoint.setOutletId(Constants.DEFAULT_OUTLET_ID);
        checkpoint.setStoreReferenceId(storeReferenceId);
        checkpoint.setSequence(routeRepository.getNextCheckpointSequence(storeReferenceId));
        checkpoint.setRouteLabel(routeLabel);
        checkpoint.setKind(kind);
        checkpoint.setAnchorX(x);
        checkpoint.setAnchorY(y);
        checkpoint.setAnchorZ(z);
        checkpoint.setYawDegrees(yaw);
        checkpoint.setCaptureConfidence(confidence);
        checkpoint.setSceneQualityScore(Math.max(0, sceneQualityScore));
        checkpoint.setCreatedAt(timestampFormat.format(new Date()));
        long previousCheckpointId = lastCheckpointId;
        long checkpointId = routeRepository.insertCheckpoint(checkpoint);

        if (checkpointId > 0L && previousCheckpointId > 0L) {
            RouteEdge edge = new RouteEdge();
            edge.setStoreReferenceId(storeReferenceId);
            edge.setFromCheckpointId(previousCheckpointId);
            edge.setToCheckpointId(checkpointId);
            edge.setDistanceMeters(PoseUtils.distance(lastX, lastY, lastZ, x, y, z));
            edge.setEdgeKind(kind);
            edge.setCreatedAt(timestampFormat.format(new Date()));
            routeRepository.insertEdge(edge);
        }
        if (checkpointId > 0L) {
            linkNearbyShortcuts(checkpointId, x, y, z, kind, confidence);
            if (!"SHELF".equals(kind)) {
                lastStableCheckpointId = checkpointId;
            }
            if (telemetryRepository != null) {
                telemetryRepository.record("route_checkpoint_recorded", storeReferenceId, 0L, 0L, confidence,
                        kind + " scene=" + sceneQualityScore + " seq=" + checkpoint.getSequence());
            }
        }

        lastCheckpointId = checkpointId;
        lastSequence = checkpoint.getSequence();
        lastX = x;
        lastY = y;
        lastZ = z;
        lastYaw = yaw;
        lastRouteLabel = routeLabel;
        initialized = checkpointId > 0L;
        return checkpointId;
    }

    private void linkNearbyShortcuts(long checkpointId, float x, float y, float z, String kind, float confidence) {
        if (confidence < 0.74f) {
            return;
        }
        List<RouteCheckpoint> checkpoints = routeRepository.getCheckpointsForStoreReference(storeReferenceId);
        int linked = 0;
        for (int i = checkpoints.size() - 1; i >= 0 && linked < 1; i--) {
            RouteCheckpoint checkpoint = checkpoints.get(i);
            if (checkpoint.getId() == checkpointId || checkpoint.getId() == lastCheckpointId) {
                continue;
            }
            if ("SHELF".equalsIgnoreCase(checkpoint.getKind())) {
                continue;
            }
            if (Math.abs(checkpoint.getSequence() - lastSequence) > 1) {
                continue;
            }
            if (checkpoint.getRouteLabel() != null && lastRouteLabel != null
                    && !checkpoint.getRouteLabel().equalsIgnoreCase(lastRouteLabel)) {
                continue;
            }
            float verticalDelta = Math.abs(checkpoint.getAnchorY() - y);
            if (verticalDelta > 0.55f) {
                continue;
            }
            float distance = PoseUtils.distance(checkpoint.getAnchorX(), checkpoint.getAnchorY(), checkpoint.getAnchorZ(), x, y, z);
            if (distance > 1.80f) {
                continue;
            }
            RouteEdge edge = new RouteEdge();
            edge.setStoreReferenceId(storeReferenceId);
            edge.setFromCheckpointId(checkpoint.getId());
            edge.setToCheckpointId(checkpointId);
            edge.setDistanceMeters(distance);
            edge.setEdgeKind(kind);
            edge.setCreatedAt(timestampFormat.format(new Date()));
            routeRepository.insertEdge(edge);
            linked++;
        }
    }

    private String normalizeKind(String kind) {
        if (kind == null || kind.trim().isEmpty()) {
            return "PATH";
        }
        return kind.trim().toUpperCase(Locale.ROOT);
    }

    private float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private float interpolate(float start, float end, float t) {
        float clamped = clamp(t);
        return start + ((end - start) * clamped);
    }
}
