package com.salesdairy.shelfarapp.audit;

import android.content.Context;
import android.util.Log;

import com.google.ar.core.Frame;
import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
import com.google.ar.core.Pose;
import com.google.ar.core.TrackingState;
import com.google.ar.sceneform.ArSceneView;
import com.google.ar.sceneform.Node;
import com.google.ar.sceneform.Scene;
import com.google.ar.sceneform.math.Quaternion;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.MaterialFactory;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.rendering.ShapeFactory;
import com.salesdairy.shelfarapp.ar.PoseUtils;

import java.util.ArrayList;
import java.util.List;

public final class AuditArStandMarkerHelper {

    private static final String TAG = "ShelfARFlow";

    private static final int MAX_PATH_CHEVRONS = 28;
    private static final float PATH_SPACING_METERS = 0.34f;
    private static final float ROUTE_Y_OFFSET = 0.055f;

    private static final float PIN_POSITION_SNAP_METERS = 0.004f;
    private static final float PIN_POSITION_LERP = 0.12f;
    private static final float PHONE_POSITION_SNAP_METERS = 0.004f;
    private static final float PHONE_POSITION_LERP = 0.12f;
    private static final float PHONE_YAW_SNAP_DEGREES = 1.5f;
    private static final float PHONE_YAW_LERP = 0.12f;

    private static final float SHELF_SIDE_DAMPING = 1.00f;
    private static final float PHONE_SIDE_DAMPING = 1.00f;
    private static final float CENTER_LOCK_ZONE_METERS = 0.015f;
    private static final float CENTER_SOFT_ZONE_METERS = 0.040f;

    private static final float FLOOR_SEARCH_Y_TOLERANCE_METERS = 0.08f;
    private static final float FLOOR_MAX_HORIZONTAL_SHIFT_METERS = 0.38f;
    private static final float FLOOR_RING_LIFT_METERS = -0.018f;
    private static final float[] FLOOR_SEARCH_RADII_METERS = new float[]{0f, 0.10f, 0.18f, 0.28f, 0.38f};
    private static final float[] FLOOR_SEARCH_ANGLES_DEGREES = new float[]{0f, 30f, -30f, 60f, -60f, 90f, -90f, 120f, -120f, 150f, -150f, 180f};
    private static final int[] FLOOR_SEARCH_X_OFFSETS_PX = new int[]{0, -28, 28, -56, 56};
    private static final int[] FLOOR_SEARCH_Y_OFFSETS_PX = new int[]{0, 36, 72, 108, 156, 220, 300, -36};

    private final Context appContext;

    private boolean renderablesRequested;

    private ModelRenderable pathStemRenderable;
    private ModelRenderable pathHeadWingRenderable;
    private ModelRenderable pathHeadTipRenderable;

    private ModelRenderable phoneBackRenderable;
    private ModelRenderable phoneScreenRenderable;
    private ModelRenderable phoneSideRenderable;
    private ModelRenderable phoneTopRenderable;
    private ModelRenderable phoneCenterDotRenderable;
    private ModelRenderable phoneMatchedDotRenderable;
    private ModelRenderable phoneCameraDotRenderable;
    private ModelRenderable phoneGuideLineRenderable;

    private ModelRenderable shelfPinHeadRenderable;
    private ModelRenderable shelfPinTailRenderable;
    private ModelRenderable shelfPinCenterRenderable;
    private ModelRenderable shelfPinShadowRenderable;

    private ModelRenderable standOuterRingSegmentRenderable;
    private ModelRenderable standInnerRingSegmentRenderable;
    private ModelRenderable standPointerStemRenderable;
    private ModelRenderable standPointerHeadWingRenderable;
    private ModelRenderable standPointerTipRenderable;
    private ModelRenderable standCenterRenderable;

    private Node shelfRootNode;
    private Node phoneRootNode;
    private Node phoneDotNode;
    private Node phoneGuideLineNode;
    private Node standRootNode;
    private final List<Node> pathRootNodes = new ArrayList<>();

    private boolean phoneMatchedVisual;
    private Vector3 lastStandPosition;
    private Vector3 lastShelfPosition;
    private Vector3 lastPhonePosition;
    private float lastPhoneYawDegrees = Float.NaN;

    public AuditArStandMarkerHelper(Context context) {
        this.appContext = context.getApplicationContext();
    }

    public void renderStand(ArSceneView sceneView,
                            Frame frame,
                            Pose standFeetPose,
                            boolean visible,
                            boolean compact) {
        if (sceneView == null || standFeetPose == null || !visible) {
            setStandVisible(false);
            return;
        }
        ensureRenderables();
        if (standOuterRingSegmentRenderable == null || standInnerRingSegmentRenderable == null || standCenterRenderable == null) {
            return;
        }
        ensureStandNode(sceneView, compact);
        if (standRootNode == null) {
            return;
        }

        Vector3 desiredStandPosition = toVector(standFeetPose, FLOOR_RING_LIFT_METERS);
        Vector3 stableStandPosition = blendWorldPosition(lastStandPosition, desiredStandPosition, 0.002f, 0.10f);
        lastStandPosition = stableStandPosition;

        standRootNode.setEnabled(true);
        standRootNode.setWorldPosition(stableStandPosition);
        float standYaw = !Float.isNaN(lastPhoneYawDegrees) ? lastPhoneYawDegrees : PoseUtils.yawDegrees(standFeetPose);
        standRootNode.setWorldRotation(buildYawRotation(standYaw));
    }

    public void renderPhoneCue(ArSceneView sceneView,
                               Pose targetCameraPose,
                               Pose viewerPose,
                               boolean visible,
                               boolean matched) {
        if (sceneView == null || targetCameraPose == null || !visible) {
            setPhoneVisible(false);
            return;
        }
        ensureRenderables();
        if (phoneBackRenderable == null || phoneSideRenderable == null || phoneTopRenderable == null || phoneCenterDotRenderable == null) {
            return;
        }
        ensurePhoneNode(sceneView);
        if (phoneRootNode == null) {
            return;
        }

        Vector3 desiredPhonePosition = toVector(targetCameraPose, 0f);
        Vector3 phonePosition = blendWorldPosition(lastPhonePosition, desiredPhonePosition, PHONE_POSITION_SNAP_METERS, PHONE_POSITION_LERP);
        phoneRootNode.setWorldPosition(phonePosition);
        phoneRootNode.setEnabled(true);
        lastPhonePosition = phonePosition;

        float desiredYaw = lastShelfPosition != null
                ? yawDegreesToward(phonePosition, lastShelfPosition)
                : PoseUtils.yawDegrees(targetCameraPose);
        float phoneYaw = blendYaw(lastPhoneYawDegrees, desiredYaw, PHONE_YAW_SNAP_DEGREES, PHONE_YAW_LERP);
        phoneRootNode.setWorldRotation(buildYawRotation(phoneYaw));
        lastPhoneYawDegrees = phoneYaw;
        updatePhoneMatchedVisual(matched);
        updatePhoneGuideLine();
    }

    public void renderShelfPin(ArSceneView sceneView,
                               Pose shelfPose,
                               Pose viewerPose,
                               boolean visible) {
        if (sceneView == null || shelfPose == null || !visible) {
            setShelfVisible(false);
            return;
        }
        ensureRenderables();
        if (shelfPinHeadRenderable == null || shelfPinTailRenderable == null || shelfPinCenterRenderable == null) {
            return;
        }
        ensureShelfNode(sceneView);
        if (shelfRootNode == null) {
            return;
        }

        Vector3 shelfPos = toVector(shelfPose, 0.002f);
        Vector3 stableShelfPosition = blendWorldPosition(lastShelfPosition, shelfPos, PIN_POSITION_SNAP_METERS, PIN_POSITION_LERP);
        shelfRootNode.setWorldPosition(stableShelfPosition);
        shelfRootNode.setEnabled(true);
        lastShelfPosition = stableShelfPosition;

        if (lastPhonePosition != null) {
            shelfRootNode.setWorldRotation(buildYawRotation(yawDegreesToward(stableShelfPosition, lastPhonePosition)));
        } else if (viewerPose != null) {
            shelfRootNode.setWorldRotation(buildYawRotation(PoseUtils.yawDegrees(viewerPose)));
        }
    }

    public void renderRoutePath(ArSceneView sceneView,
                                List<Pose> routeWorldPoses,
                                boolean visible) {
        if (sceneView == null || routeWorldPoses == null || routeWorldPoses.size() < 2 || !visible) {
            setPathVisible(false);
            return;
        }
        ensureRenderables();
        if (pathStemRenderable == null || pathHeadWingRenderable == null || pathHeadTipRenderable == null) {
            return;
        }
        ensurePathNodes(sceneView);
        List<Vector3> samples = sampleRoute(routeWorldPoses, MAX_PATH_CHEVRONS, PATH_SPACING_METERS);
        if (samples.isEmpty()) {
            setPathVisible(false);
            return;
        }

        Scene scene = sceneView.getScene();
        for (int i = 0; i < pathRootNodes.size(); i++) {
            Node root = pathRootNodes.get(i);
            if (i >= samples.size()) {
                root.setEnabled(false);
                continue;
            }
            Vector3 current = samples.get(i);
            Vector3 next = i + 1 < samples.size() ? samples.get(i + 1) : inferForward(current, routeWorldPoses);
            root.setParent(scene);
            root.setEnabled(true);
            root.setWorldPosition(current);
            root.setWorldRotation(buildYawTowards(current, next));
        }
    }

    public void clear() {
        clearNode(shelfRootNode);
        clearNode(phoneRootNode);
        clearNode(standRootNode);
        shelfRootNode = null;
        phoneRootNode = null;
        phoneDotNode = null;
        phoneGuideLineNode = null;
        standRootNode = null;
        phoneMatchedVisual = false;
        lastStandPosition = null;
        lastShelfPosition = null;
        lastPhonePosition = null;
        lastPhoneYawDegrees = Float.NaN;

        for (Node node : pathRootNodes) {
            clearNode(node);
        }
        pathRootNodes.clear();
    }

    private void ensureRenderables() {
        if (renderablesRequested) {
            return;
        }
        renderablesRequested = true;

        MaterialFactory.makeOpaqueWithColor(appContext,
                        new com.google.ar.sceneform.rendering.Color(android.graphics.Color.parseColor("#D9E8FF")))
                .thenAccept(material -> {
                    pathStemRenderable = ShapeFactory.makeCylinder(
                            0.042f,
                            0.005f,
                            new Vector3(0f, 0f, 0f),
                            material
                    );
                    pathHeadWingRenderable = ShapeFactory.makeCylinder(
                            0.034f,
                            0.005f,
                            new Vector3(0f, 0f, 0f),
                            material
                    );
                    pathHeadTipRenderable = ShapeFactory.makeCylinder(
                            0.026f,
                            0.005f,
                            new Vector3(0f, 0f, 0f),
                            material
                    );
                    disableShadows(pathStemRenderable, pathHeadWingRenderable, pathHeadTipRenderable);
                })
                .exceptionally(throwable -> {
                    Log.e(TAG, "Failed to build route arrows", throwable);
                    return null;
                });

        MaterialFactory.makeOpaqueWithColor(appContext,
                        new com.google.ar.sceneform.rendering.Color(android.graphics.Color.parseColor("#102133")))
                .thenAccept(darkMaterial -> {
                    phoneBackRenderable = ShapeFactory.makeCube(
                            new Vector3(0.110f, 0.190f, 0.010f),
                            new Vector3(0f, 0f, 0f),
                            darkMaterial
                    );
                    disableShadows(phoneBackRenderable);
                })
                .exceptionally(throwable -> {
                    Log.e(TAG, "Failed to build phone back", throwable);
                    return null;
                });

        MaterialFactory.makeOpaqueWithColor(appContext,
                        new com.google.ar.sceneform.rendering.Color(android.graphics.Color.parseColor("#D1FAE5")))
                .thenAccept(lightMaterial -> {
                    phoneScreenRenderable = ShapeFactory.makeCube(
                            new Vector3(0.092f, 0.168f, 0.003f),
                            new Vector3(0f, 0f, 0f),
                            lightMaterial
                    );
                    phoneSideRenderable = ShapeFactory.makeCube(
                            new Vector3(0.008f, 0.188f, 0.014f),
                            new Vector3(0f, 0f, 0f),
                            lightMaterial
                    );
                    phoneTopRenderable = ShapeFactory.makeCube(
                            new Vector3(0.094f, 0.008f, 0.014f),
                            new Vector3(0f, 0f, 0f),
                            lightMaterial
                    );
                    phoneCenterDotRenderable = ShapeFactory.makeSphere(
                            0.0125f,
                            new Vector3(0f, 0f, 0f),
                            lightMaterial
                    );
                    phoneCameraDotRenderable = ShapeFactory.makeSphere(
                            0.006f,
                            new Vector3(0f, 0f, 0f),
                            lightMaterial
                    );
                    phoneGuideLineRenderable = ShapeFactory.makeCube(
                            new Vector3(0.008f, 1.0f, 0.008f),
                            new Vector3(0f, 0f, 0f),
                            lightMaterial
                    );
                    disableShadows(phoneScreenRenderable, phoneSideRenderable, phoneTopRenderable,
                            phoneCenterDotRenderable, phoneCameraDotRenderable, phoneGuideLineRenderable);

                    MaterialFactory.makeOpaqueWithColor(appContext,
                                    new com.google.ar.sceneform.rendering.Color(android.graphics.Color.parseColor("#22C55E")))
                            .thenAccept(matchedMaterial -> {
                                phoneMatchedDotRenderable = ShapeFactory.makeSphere(
                                        0.0145f,
                                        new Vector3(0f, 0f, 0f),
                                        matchedMaterial
                                );
                                disableShadows(phoneMatchedDotRenderable);
                            });
                })
                .exceptionally(throwable -> {
                    Log.e(TAG, "Failed to build phone frame", throwable);
                    return null;
                });

        MaterialFactory.makeOpaqueWithColor(appContext,
                        new com.google.ar.sceneform.rendering.Color(android.graphics.Color.parseColor("#22C55E")))
                .thenAccept(pinMaterial -> {
                    shelfPinHeadRenderable = ShapeFactory.makeCylinder(
                            0.020f,
                            0.090f,
                            new Vector3(0f, 0f, 0f),
                            pinMaterial
                    );
                    shelfPinTailRenderable = ShapeFactory.makeCylinder(
                            0.006f,
                            0.120f,
                            new Vector3(0f, 0f, 0f),
                            pinMaterial
                    );
                    disableShadows(shelfPinHeadRenderable, shelfPinTailRenderable);
                })
                .exceptionally(throwable -> {
                    Log.e(TAG, "Failed to build shelf pin body", throwable);
                    return null;
                });

        MaterialFactory.makeOpaqueWithColor(appContext,
                        new com.google.ar.sceneform.rendering.Color(android.graphics.Color.parseColor("#ECFDF5")))
                .thenAccept(centerMaterial -> {
                    shelfPinCenterRenderable = ShapeFactory.makeSphere(
                            0.013f,
                            new Vector3(0f, 0f, 0f),
                            centerMaterial
                    );
                    standCenterRenderable = ShapeFactory.makeSphere(
                            0.012f,
                            new Vector3(0f, 0f, 0f),
                            centerMaterial
                    );
                    disableShadows(shelfPinCenterRenderable, standCenterRenderable);
                })
                .exceptionally(throwable -> {
                    Log.e(TAG, "Failed to build cue center dots", throwable);
                    return null;
                });

        MaterialFactory.makeOpaqueWithColor(appContext,
                        new com.google.ar.sceneform.rendering.Color(android.graphics.Color.parseColor("#0F172A")))
                .thenAccept(shadowMaterial -> {
                    shelfPinShadowRenderable = ShapeFactory.makeCylinder(
                            0.028f,
                            0.0020f,
                            new Vector3(0f, 0f, 0f),
                            shadowMaterial
                    );
                    disableShadows(shelfPinShadowRenderable);
                })
                .exceptionally(throwable -> {
                    Log.e(TAG, "Failed to build shelf pin shadow", throwable);
                    return null;
                });

        MaterialFactory.makeOpaqueWithColor(appContext,
                        new com.google.ar.sceneform.rendering.Color(android.graphics.Color.parseColor("#F59E0B")))
                .thenAccept(lightMaterial -> {
                    standOuterRingSegmentRenderable = ShapeFactory.makeCylinder(
                            0.118f,
                            0.0030f,
                            new Vector3(0f, 0f, 0f),
                            lightMaterial
                    );
                    standPointerStemRenderable = null;
                    standPointerHeadWingRenderable = null;
                    standPointerTipRenderable = null;
                    disableShadows(standOuterRingSegmentRenderable);
                })
                .exceptionally(throwable -> {
                    Log.e(TAG, "Failed to build floor circle", throwable);
                    return null;
                });

        MaterialFactory.makeOpaqueWithColor(appContext,
                        new com.google.ar.sceneform.rendering.Color(android.graphics.Color.parseColor("#FED7AA")))
                .thenAccept(accentMaterial -> {
                    standInnerRingSegmentRenderable = ShapeFactory.makeCylinder(
                            0.082f,
                            0.0024f,
                            new Vector3(0f, 0f, 0f),
                            accentMaterial
                    );
                    disableShadows(standInnerRingSegmentRenderable);
                })
                .exceptionally(throwable -> {
                    Log.e(TAG, "Failed to build floor circle accent", throwable);
                    return null;
                });
    }

    private void ensureShelfNode(ArSceneView sceneView) {
        if (shelfRootNode != null) {
            return;
        }

        shelfRootNode = new Node();
        shelfRootNode.setParent(sceneView.getScene());

        Node shadow = new Node();
        shadow.setParent(shelfRootNode);
        shadow.setRenderable(shelfPinShadowRenderable);
        shadow.setLocalPosition(new Vector3(0f, 0.002f, 0f));

        Node tail = new Node();
        tail.setParent(shelfRootNode);
        tail.setRenderable(shelfPinTailRenderable);
        tail.setLocalPosition(new Vector3(0f, 0.060f, 0f));

        Node head = new Node();
        head.setParent(shelfRootNode);
        head.setRenderable(shelfPinHeadRenderable);
        head.setLocalPosition(new Vector3(0f, 0.130f, 0f));

        Node center = new Node();
        center.setParent(shelfRootNode);
        center.setRenderable(shelfPinCenterRenderable);
        center.setLocalPosition(new Vector3(0f, 0.130f, 0.020f));
    }

    private void ensureStandNode(ArSceneView sceneView, boolean compact) {
        if (standRootNode != null) {
            return;
        }

        standRootNode = new Node();
        standRootNode.setParent(sceneView.getScene());

        Node outerRing = new Node();
        outerRing.setParent(standRootNode);
        outerRing.setRenderable(standOuterRingSegmentRenderable);
        outerRing.setLocalPosition(new Vector3(0f, 0f, 0f));

        Node innerRing = new Node();
        innerRing.setParent(standRootNode);
        innerRing.setRenderable(standInnerRingSegmentRenderable);
        innerRing.setLocalPosition(new Vector3(0f, 0.001f, 0f));

        Node centerDot = new Node();
        centerDot.setParent(standRootNode);
        centerDot.setRenderable(standCenterRenderable);
        centerDot.setLocalPosition(new Vector3(0f, 0.002f, 0f));
    }

    private void addRingSegments(Node root, ModelRenderable renderable, int count, float radius, float y) {
        if (root == null || renderable == null || count <= 0) {
            return;
        }
        for (int i = 0; i < count; i++) {
            float degrees = i * (360f / count);
            float radians = (float) Math.toRadians(degrees);
            Node segment = new Node();
            segment.setParent(root);
            segment.setRenderable(renderable);
            segment.setLocalPosition(new Vector3(
                    (float) Math.sin(radians) * radius,
                    y,
                    (float) -Math.cos(radians) * radius
            ));
            segment.setLocalRotation(Quaternion.axisAngle(new Vector3(0f, 1f, 0f), degrees));
        }
    }

    private void ensurePhoneNode(ArSceneView sceneView) {
        if (phoneRootNode != null) {
            return;
        }

        phoneRootNode = new Node();
        phoneRootNode.setParent(sceneView.getScene());

        Node back = new Node();
        back.setParent(phoneRootNode);
        back.setRenderable(phoneBackRenderable);
        back.setLocalPosition(new Vector3(0f, 0f, -0.005f));

        Node screen = new Node();
        screen.setParent(phoneRootNode);
        screen.setRenderable(phoneScreenRenderable);
        screen.setLocalPosition(new Vector3(0f, 0f, 0.003f));

        Node left = new Node();
        left.setParent(phoneRootNode);
        left.setRenderable(phoneSideRenderable);
        left.setLocalPosition(new Vector3(-0.051f, 0f, 0f));

        Node right = new Node();
        right.setParent(phoneRootNode);
        right.setRenderable(phoneSideRenderable);
        right.setLocalPosition(new Vector3(0.051f, 0f, 0f));

        Node top = new Node();
        top.setParent(phoneRootNode);
        top.setRenderable(phoneTopRenderable);
        top.setLocalPosition(new Vector3(0f, 0.091f, 0f));

        Node bottom = new Node();
        bottom.setParent(phoneRootNode);
        bottom.setRenderable(phoneTopRenderable);
        bottom.setLocalPosition(new Vector3(0f, -0.091f, 0f));

        Node cameraDot = new Node();
        cameraDot.setParent(phoneRootNode);
        cameraDot.setRenderable(phoneCameraDotRenderable);
        cameraDot.setLocalPosition(new Vector3(0f, 0.070f, 0.006f));

        phoneDotNode = new Node();
        phoneDotNode.setParent(phoneRootNode);
        phoneDotNode.setRenderable(phoneCenterDotRenderable);
        phoneDotNode.setLocalPosition(new Vector3(0f, 0f, 0.008f));

        phoneGuideLineNode = new Node();
        phoneGuideLineNode.setParent(phoneRootNode);
        phoneGuideLineNode.setRenderable(phoneGuideLineRenderable);
        phoneGuideLineNode.setEnabled(false);
    }

    private void ensurePathNodes(ArSceneView sceneView) {
        while (pathRootNodes.size() < MAX_PATH_CHEVRONS) {
            Node root = new Node();
            root.setParent(sceneView.getScene());
            root.setEnabled(false);

            Node dot = new Node();
            dot.setParent(root);
            dot.setRenderable(pathStemRenderable);

            pathRootNodes.add(root);
        }
    }

    private void updatePhoneGuideLine() {
        if (phoneGuideLineNode == null || lastPhonePosition == null || lastStandPosition == null) {
            if (phoneGuideLineNode != null) {
                phoneGuideLineNode.setEnabled(false);
            }
            return;
        }
        float lineHeight = Math.max(0f, lastPhonePosition.y - lastStandPosition.y);
        if (lineHeight < 0.12f) {
            phoneGuideLineNode.setEnabled(false);
            return;
        }
        phoneGuideLineNode.setEnabled(true);
        phoneGuideLineNode.setLocalScale(new Vector3(1f, lineHeight, 1f));
        phoneGuideLineNode.setLocalPosition(new Vector3(0f, -(lineHeight * 0.50f), 0f));
    }

    private void updatePhoneMatchedVisual(boolean matched) {
        if (phoneDotNode == null || phoneMatchedVisual == matched) {
            phoneMatchedVisual = matched;
            return;
        }
        phoneDotNode.setRenderable(matched && phoneMatchedDotRenderable != null ? phoneMatchedDotRenderable : phoneCenterDotRenderable);
        phoneMatchedVisual = matched;
    }

    private List<Vector3> sampleRoute(List<Pose> routeWorldPoses, int maxSamples, float spacingMeters) {
        List<Vector3> output = new ArrayList<>();
        if (routeWorldPoses == null || routeWorldPoses.size() < 2) {
            return output;
        }

        List<Vector3> points = new ArrayList<>();
        for (Pose pose : routeWorldPoses) {
            points.add(toVector(pose, ROUTE_Y_OFFSET));
        }

        float carry = Math.min(0.10f, spacingMeters * 0.30f);
        Vector3 previous = points.get(0);
        for (int i = 1; i < points.size() && output.size() < maxSamples; i++) {
            Vector3 current = points.get(i);
            Vector3 segment = Vector3.subtract(current, previous);
            float segmentLength = segment.length();
            if (segmentLength < 0.001f) {
                previous = current;
                continue;
            }
            Vector3 direction = segment.normalized();
            while (carry <= segmentLength && output.size() < maxSamples) {
                output.add(Vector3.add(previous, direction.scaled(carry)));
                carry += spacingMeters;
            }
            carry -= segmentLength;
            if (carry < 0.06f) {
                carry = spacingMeters;
            }
            previous = current;
        }
        return output;
    }

    private float yawDegreesToward(Vector3 from, Vector3 to) {
        if (from == null || to == null) {
            return 0f;
        }
        Vector3 delta = Vector3.subtract(to, from);
        return (float) Math.toDegrees(Math.atan2(delta.x, -delta.z));
    }

    private Quaternion buildYawRotation(float yawDegrees) {
        return Quaternion.axisAngle(new Vector3(0f, 1f, 0f), yawDegrees);
    }

    private Vector3 inferForward(Vector3 current, List<Pose> routeWorldPoses) {
        if (routeWorldPoses != null && !routeWorldPoses.isEmpty()) {
            Pose last = routeWorldPoses.get(routeWorldPoses.size() - 1);
            Vector3 tail = toVector(last, ROUTE_Y_OFFSET);
            if (Vector3.subtract(tail, current).length() > 0.05f) {
                return tail;
            }
        }
        return Vector3.add(current, new Vector3(0f, 0f, -0.4f));
    }

    private Quaternion buildYawTowards(Vector3 from, Vector3 to) {
        Vector3 delta = Vector3.subtract(to, from);
        float yaw = (float) Math.toDegrees(Math.atan2(delta.x, -delta.z));
        return Quaternion.axisAngle(new Vector3(0f, 1f, 0f), yaw);
    }

    private Pose adjustStandPoseToFloor(ArSceneView sceneView, Frame frame, Pose standFeetPose) {
        if (sceneView == null || frame == null || standFeetPose == null) {
            return standFeetPose;
        }

        float anchorYaw = !Float.isNaN(lastPhoneYawDegrees) ? lastPhoneYawDegrees : PoseUtils.yawDegrees(standFeetPose);
        Vector3 baseWorld = lastPhonePosition != null
                ? new Vector3(lastPhonePosition.x, standFeetPose.ty(), lastPhonePosition.z)
                : toVector(standFeetPose, 0f);

        Pose floorPose = findNearbyFloorPose(sceneView, frame, baseWorld, standFeetPose, anchorYaw);
        return floorPose != null ? floorPose : standFeetPose;
    }

    private Pose findNearbyFloorPose(ArSceneView sceneView,
                                     Frame frame,
                                     Vector3 baseWorld,
                                     Pose fallbackPose,
                                     float anchorYawDegrees) {
        if (sceneView == null || frame == null || baseWorld == null || fallbackPose == null) {
            return null;
        }
        try {
            Pose planePose = findFloorPoseFromTrackedPlanes(sceneView, baseWorld, fallbackPose.ty(), anchorYawDegrees);
            if (planePose != null) {
                return new Pose(
                        new float[]{planePose.tx(), planePose.ty(), planePose.tz()},
                        fallbackPose.getRotationQuaternion()
                );
            }

            Vector3 screenPoint = sceneView.getScene().getCamera().worldToScreenPoint(baseWorld);
            if (screenPoint == null || Float.isNaN(screenPoint.x) || Float.isNaN(screenPoint.y)) {
                return null;
            }

            int width = sceneView.getWidth();
            int height = sceneView.getHeight();
            if (width <= 0 || height <= 0) {
                return null;
            }

            Pose bestPose = null;
            float bestScore = Float.MAX_VALUE;
            for (int yOffset : FLOOR_SEARCH_Y_OFFSETS_PX) {
                for (int xOffset : FLOOR_SEARCH_X_OFFSETS_PX) {
                    float sampleX = clamp(screenPoint.x + xOffset, 0f, width - 1f);
                    float sampleY = clamp(screenPoint.y + yOffset, 0f, height - 1f);
                    Pose hitPose = getLowestFloorHit(frame, sampleX, sampleY, fallbackPose.ty());
                    if (hitPose == null) {
                        continue;
                    }
                    float dx = hitPose.tx() - baseWorld.x;
                    float dz = hitPose.tz() - baseWorld.z;
                    float horizontalDistance = (float) Math.sqrt((dx * dx) + (dz * dz));
                    if (horizontalDistance > FLOOR_MAX_HORIZONTAL_SHIFT_METERS) {
                        continue;
                    }
                    float score = horizontalDistance
                            + (Math.abs(xOffset) * 0.0012f)
                            + (Math.max(0, yOffset) * 0.0007f)
                            + (Math.max(0f, hitPose.ty() - fallbackPose.ty()) * 3.0f);
                    if (score < bestScore) {
                        bestScore = score;
                        bestPose = hitPose;
                    }
                }
            }

            if (bestPose == null) {
                return null;
            }
            return new Pose(
                    new float[]{bestPose.tx(), bestPose.ty(), bestPose.tz()},
                    fallbackPose.getRotationQuaternion()
            );
        } catch (Exception e) {
            Log.w(TAG, "Failed to place stand cue on nearby floor", e);
            return null;
        }
    }

    private Pose getLowestFloorHit(Frame frame, float x, float y, float fallbackFloorY) {
        try {
            List<HitResult> hits = frame.hitTest(x, y);
            Pose bestPreferred = null;
            float bestPreferredY = Float.MAX_VALUE;
            Pose bestAny = null;
            float bestAnyY = Float.MAX_VALUE;
            for (HitResult hit : hits) {
                if (hit == null || !(hit.getTrackable() instanceof Plane)) {
                    continue;
                }
                Plane plane = (Plane) hit.getTrackable();
                if (plane.getTrackingState() != TrackingState.TRACKING) {
                    continue;
                }
                if (plane.getType() != Plane.Type.HORIZONTAL_UPWARD_FACING) {
                    continue;
                }
                Pose hitPose = hit.getHitPose();
                if (hitPose == null || !plane.isPoseInPolygon(hitPose)) {
                    continue;
                }
                float hitY = hitPose.ty();
                if (hitY < bestAnyY) {
                    bestAnyY = hitY;
                    bestAny = hitPose;
                }
                if (hitY <= fallbackFloorY + FLOOR_SEARCH_Y_TOLERANCE_METERS && hitY < bestPreferredY) {
                    bestPreferredY = hitY;
                    bestPreferred = hitPose;
                }
            }
            return bestPreferred != null ? bestPreferred : bestAny;
        } catch (Exception e) {
            Log.w(TAG, "Floor hit test failed", e);
            return null;
        }
    }

    private Pose findFloorPoseFromTrackedPlanes(ArSceneView sceneView,
                                                Vector3 baseWorld,
                                                float fallbackFloorY,
                                                float anchorYawDegrees) {
        if (sceneView == null || sceneView.getSession() == null || baseWorld == null) {
            return null;
        }
        try {
            Pose bestPose = null;
            float bestScore = Float.MAX_VALUE;
            float yawRadians = (float) Math.toRadians(anchorYawDegrees);
            float forwardX = (float) Math.sin(yawRadians);
            float forwardZ = (float) -Math.cos(yawRadians);
            float rightX = (float) Math.cos(yawRadians);
            float rightZ = (float) Math.sin(yawRadians);

            for (Plane plane : sceneView.getSession().getAllTrackables(Plane.class)) {
                if (plane == null || plane.getTrackingState() != TrackingState.TRACKING) {
                    continue;
                }
                if (plane.getType() != Plane.Type.HORIZONTAL_UPWARD_FACING) {
                    continue;
                }
                float planeY = plane.getCenterPose().ty();
                if (planeY > fallbackFloorY + FLOOR_SEARCH_Y_TOLERANCE_METERS) {
                    continue;
                }

                for (float radius : FLOOR_SEARCH_RADII_METERS) {
                    for (float angleDegrees : FLOOR_SEARCH_ANGLES_DEGREES) {
                        float angleRadians = (float) Math.toRadians(angleDegrees);
                        float localForward = (float) Math.cos(angleRadians) * radius;
                        float localRight = (float) Math.sin(angleRadians) * radius;
                        float candidateX = baseWorld.x + (forwardX * localForward) + (rightX * localRight);
                        float candidateZ = baseWorld.z + (forwardZ * localForward) + (rightZ * localRight);
                        Pose candidatePose = new Pose(
                                new float[]{candidateX, planeY, candidateZ},
                                fallbackPoseRotationQuaternion()
                        );
                        if (!plane.isPoseInPolygon(candidatePose)) {
                            continue;
                        }

                        float dx = candidateX - baseWorld.x;
                        float dz = candidateZ - baseWorld.z;
                        float horizontalDistance = (float) Math.sqrt((dx * dx) + (dz * dz));
                        if (horizontalDistance > FLOOR_MAX_HORIZONTAL_SHIFT_METERS) {
                            continue;
                        }

                        float forwardBias = Math.abs(localForward) * 0.12f;
                        float sideBias = Math.abs(localRight) * 0.22f;
                        float score = horizontalDistance
                                + forwardBias
                                + sideBias
                                + (Math.max(0f, planeY - fallbackFloorY) * 4.0f);
                        if (score < bestScore) {
                            bestScore = score;
                            bestPose = candidatePose;
                        }
                    }
                }
            }
            return bestPose;
        } catch (Exception e) {
            Log.w(TAG, "Tracked plane floor search failed", e);
            return null;
        }
    }

    private float[] fallbackPoseRotationQuaternion() {
        return new float[]{0f, 0f, 0f, 1f};
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private Vector3 blendWorldPosition(Vector3 previous, Vector3 desired, float snapMeters, float alpha) {
        if (desired == null) {
            return previous;
        }
        if (previous == null) {
            return desired;
        }
        Vector3 delta = Vector3.subtract(desired, previous);
        if (delta.length() <= snapMeters) {
            return previous;
        }
        return new Vector3(
                previous.x + ((desired.x - previous.x) * alpha),
                previous.y + ((desired.y - previous.y) * alpha),
                previous.z + ((desired.z - previous.z) * alpha)
        );
    }

    private float blendYaw(float previousYaw, float desiredYaw, float snapDegrees, float alpha) {
        if (Float.isNaN(previousYaw)) {
            return desiredYaw;
        }
        float delta = normalizeDegrees(desiredYaw - previousYaw);
        if (Math.abs(delta) <= snapDegrees) {
            return previousYaw;
        }
        return normalizeDegrees(previousYaw + (delta * alpha));
    }

    private float normalizeDegrees(float value) {
        while (value > 180f) value -= 360f;
        while (value < -180f) value += 360f;
        return value;
    }

    private Vector3 stabilizeVisualPosition(Vector3 desiredWorld, Pose viewerPose, float lateralDamping, float depthOffsetMeters) {
        if (desiredWorld == null || viewerPose == null) {
            return desiredWorld;
        }
        try {
            Pose inverse = viewerPose.inverse();
            float[] local = inverse.transformPoint(new float[]{desiredWorld.x, desiredWorld.y, desiredWorld.z});
            float absSide = Math.abs(local[0]);
            if (absSide < CENTER_LOCK_ZONE_METERS) {
                local[0] = 0f;
            } else if (absSide < CENTER_SOFT_ZONE_METERS) {
                local[0] = local[0] * 0.98f;
            }
            if (lateralDamping < 0.999f) {
                local[0] = local[0] * lateralDamping;
            }
            if (Math.abs(depthOffsetMeters) > 0.001f) {
                local[2] = local[2] + depthOffsetMeters;
            }
            float[] world = viewerPose.transformPoint(local);
            return new Vector3(world[0], world[1], world[2]);
        } catch (Exception e) {
            Log.w(TAG, "Failed to stabilize AR cue position", e);
            return desiredWorld;
        }
    }

    private Vector3 toVector(Pose pose, float yOffset) {
        return new Vector3(pose.tx(), pose.ty() + yOffset, pose.tz());
    }

    private void setStandVisible(boolean visible) {
        if (standRootNode != null) {
            standRootNode.setEnabled(visible);
        }
    }

    private void setPhoneVisible(boolean visible) {
        if (phoneRootNode != null) {
            phoneRootNode.setEnabled(visible);
        }
        if (!visible && phoneGuideLineNode != null) {
            phoneGuideLineNode.setEnabled(false);
        }
    }

    private void setShelfVisible(boolean visible) {
        if (shelfRootNode != null) {
            shelfRootNode.setEnabled(visible);
        }
    }

    private void setPathVisible(boolean visible) {
        for (Node node : pathRootNodes) {
            node.setEnabled(visible);
        }
    }

    private void clearNode(Node node) {
        if (node != null) {
            node.setParent(null);
            node.setRenderable(null);
        }
    }

    private void disableShadows(ModelRenderable... renderables) {
        if (renderables == null) {
            return;
        }
        for (ModelRenderable renderable : renderables) {
            if (renderable != null) {
                renderable.setShadowCaster(false);
                renderable.setShadowReceiver(false);
            }
        }
    }
}
