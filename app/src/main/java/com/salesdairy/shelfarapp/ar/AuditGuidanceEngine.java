package com.salesdairy.shelfarapp.ar;

import com.google.ar.core.Pose;

public class AuditGuidanceEngine {

    private static final float AXIS_DEAD_ZONE_METERS = 0.06f;
    private static final float DIAGONAL_RATIO_LIMIT = 2.6f;

    private static final float ROOM_READY_DISTANCE_METERS = 0.72f;
    private static final float ROOM_MICRO_STEP_METERS = 0.78f;
    private static final float ROOM_TURN_ONLY_DISTANCE_METERS = 0.35f;
    private static final float ROOM_READY_HEADING_DEGREES = 16f;
    private static final float ROOM_TURN_HINT_DEGREES = 14f;
    private static final float ROOM_BIG_TURN_DEGREES = 45f;
    private static final float ROOM_TURN_FIRST_DEGREES = 85f;

    private static final float TIGHT_DISTANCE_METERS = 0.35f;
    private static final float READY_DISTANCE_METERS = 0.82f;
    private static final float MICRO_STEP_DISTANCE_METERS = 0.80f;
    private static final float TURN_ONLY_DISTANCE_METERS = 0.45f;

    private static final float TIGHT_HEADING_DEGREES = 55f;
    private static final float READY_HEADING_DEGREES = 85f;
    private static final float TIGHT_PHONE_HEIGHT_METERS = 0.20f;
    private static final float READY_PHONE_HEIGHT_METERS = 0.40f;
    private static final float TURN_HINT_DEGREES = 10f;
    private static final float BIG_TURN_HINT_DEGREES = 22f;

    public enum ArrowDirection {
        FORWARD("↑", "Go straight"),
        FORWARD_RIGHT("↗", "Go front-right"),
        RIGHT("→", "Go right"),
        BACK_RIGHT("↘", "Go back-right"),
        BACK("↓", "Go back"),
        BACK_LEFT("↙", "Go back-left"),
        LEFT("←", "Go left"),
        FORWARD_LEFT("↖", "Go front-left"),
        TURN_LEFT("↺", "Turn left"),
        TURN_RIGHT("↻", "Turn right"),
        HOLD("•", "Hold position");

        public final String arrow;
        public final String label;

        ArrowDirection(String arrow, String label) {
            this.arrow = arrow;
            this.label = label;
        }
    }

    public static class GuidanceResult {
        public float forwardMeters;
        public float sideMeters;
        public float distanceMeters;
        public float headingDiffDegrees;
        public float bearingDegrees;
        public float phoneHeightDiffMeters;

        public boolean inTightWindow;
        public boolean closeEnoughToAudit;
        public boolean turnOnly;
        public boolean microAdjust;
        public boolean nearShelfZone;
        public boolean shelfShouldBeVisible;

        public ArrowDirection arrowDirection;
        public String arrowText;
        public String title;
        public String detail;
        public String metricsLine;
        public String secondaryHint;
    }

    public GuidanceResult getRoomReturnGuidance(Pose cameraPose, Pose targetPose) {
        GuidanceResult result = buildBase(cameraPose, targetPose);
        // Walk guidance should point the user toward the saved phone spot,
        // not toward the saved final phone rotation. During Step 2, use the
        // bearing to the destination as the heading signal.
        result.headingDiffDegrees = result.bearingDegrees;
        float absHeading = Math.abs(result.headingDiffDegrees);

        result.closeEnoughToAudit = result.distanceMeters <= ROOM_READY_DISTANCE_METERS;
        result.microAdjust = result.distanceMeters <= ROOM_MICRO_STEP_METERS;
        result.inTightWindow = result.closeEnoughToAudit;
        result.nearShelfZone = result.distanceMeters <= 1.2f;
        result.shelfShouldBeVisible = result.distanceMeters <= 0.55f;

        fillRoomDirectionalTexts(result);
        result.metricsLine = buildRoomMetricsLine(result);
        return result;
    }

    public GuidanceResult getExactCameraGuidance(Pose cameraPose, Pose targetPose) {
        GuidanceResult result = buildBase(cameraPose, targetPose);
        float absHeading = Math.abs(result.headingDiffDegrees);

        result.inTightWindow = result.distanceMeters <= TIGHT_DISTANCE_METERS
                && absHeading <= TIGHT_HEADING_DEGREES;
        result.closeEnoughToAudit = result.distanceMeters <= READY_DISTANCE_METERS
                && absHeading <= READY_HEADING_DEGREES;
        result.microAdjust = result.distanceMeters <= MICRO_STEP_DISTANCE_METERS;
        result.nearShelfZone = result.distanceMeters <= 0.45f;
        result.shelfShouldBeVisible = absHeading <= 10f;

        fillExactDirectionalTexts(result);
        result.metricsLine = buildExactMetricsLine(result);
        return result;
    }

    public GuidanceResult getStandAlignmentGuidance(Pose cameraPose,
                                                    Pose targetStandPose,
                                                    Pose targetCameraPose) {
        GuidanceResult result = buildBase(cameraPose, targetStandPose);
        result.headingDiffDegrees = targetCameraPose != null
                ? getHeadingDifferenceDegrees(cameraPose, targetCameraPose)
                : result.bearingDegrees;
        result.phoneHeightDiffMeters = getPhoneHeightDifferenceMeters(cameraPose, targetCameraPose);
        float absHeading = Math.abs(result.headingDiffDegrees);
        float absHeight = Math.abs(result.phoneHeightDiffMeters);

        result.inTightWindow = result.distanceMeters <= TIGHT_DISTANCE_METERS
                && absHeading <= TIGHT_HEADING_DEGREES
                && absHeight <= TIGHT_PHONE_HEIGHT_METERS;
        result.closeEnoughToAudit = result.distanceMeters <= READY_DISTANCE_METERS
                && absHeading <= READY_HEADING_DEGREES
                && absHeight <= READY_PHONE_HEIGHT_METERS;
        result.microAdjust = result.distanceMeters <= MICRO_STEP_DISTANCE_METERS;
        result.nearShelfZone = result.distanceMeters <= 0.80f;
        result.shelfShouldBeVisible = result.distanceMeters <= 0.75f;

        fillExactDirectionalTexts(result);
        result.metricsLine = buildExactMetricsLine(result);
        return result;
    }

    private GuidanceResult buildBase(Pose cameraPose, Pose targetPose) {
        GuidanceResult result = new GuidanceResult();

        if (cameraPose == null || targetPose == null) {
            result.forwardMeters = 0f;
            result.sideMeters = 0f;
            result.distanceMeters = 0f;
            result.headingDiffDegrees = 0f;
            result.bearingDegrees = 0f;
            result.phoneHeightDiffMeters = 0f;
            result.arrowDirection = ArrowDirection.HOLD;
            result.arrowText = ArrowDirection.HOLD.arrow;
            result.title = "Hold position";
            result.detail = "Waiting for saved store reference alignment.";
            result.metricsLine = "Waiting for guidance";
            result.secondaryHint = "";
            return result;
        }

        Pose targetInCameraSpace = cameraPose.inverse().compose(targetPose);
        float[] local = targetInCameraSpace.getTranslation();

        result.sideMeters = local[0];
        result.forwardMeters = -local[2];
        result.distanceMeters = (float) Math.sqrt((result.forwardMeters * result.forwardMeters)
                + (result.sideMeters * result.sideMeters));
        result.headingDiffDegrees = getHeadingDifferenceDegrees(cameraPose, targetPose);
        result.bearingDegrees = (float) Math.toDegrees(Math.atan2(result.sideMeters, result.forwardMeters));
        return result;
    }

    private void fillRoomDirectionalTexts(GuidanceResult result) {
        float absHeading = Math.abs(result.headingDiffDegrees);

        if (result.closeEnoughToAudit) {
            result.arrowDirection = ArrowDirection.HOLD;
            result.arrowText = result.arrowDirection.arrow;
            result.title = "Shelf should be close now";
            result.detail = "Compare the real shelf with the saved shelf photo now.";
            result.secondaryHint = "If the shelf photo matches, walk onto the green floor ring";
            return;
        }

        result.turnOnly = (absHeading >= ROOM_TURN_FIRST_DEGREES && result.distanceMeters > ROOM_READY_DISTANCE_METERS)
                || (result.distanceMeters <= ROOM_TURN_ONLY_DISTANCE_METERS && absHeading >= ROOM_TURN_HINT_DEGREES);
        if (result.turnOnly) {
            result.arrowDirection = result.headingDiffDegrees > 0f ? ArrowDirection.TURN_LEFT : ArrowDirection.TURN_RIGHT;
            result.arrowText = result.arrowDirection.arrow;
            boolean bigTurn = absHeading >= ROOM_BIG_TURN_DEGREES;
            result.title = bigTurn ? "Turn and face the shelf" : result.arrowDirection.label;
            result.detail = bigTurn
                    ? (result.headingDiffDegrees > 0f ? "Turn left until the shelf pin comes into view, then keep walking." : "Turn right until the shelf pin comes into view, then keep walking.")
                    : (result.headingDiffDegrees > 0f ? "Turn a little left, then keep walking." : "Turn a little right, then keep walking.");
            result.secondaryHint = bigTurn ? "Look for the shelf pin after you turn" : "Face the saved phone spot, then keep walking";
            return;
        }

        result.arrowDirection = bucketDirection(result.forwardMeters, result.sideMeters);
        result.arrowText = result.arrowDirection.arrow;
        result.title = readableDirection(result.arrowDirection);
        result.detail = buildRoomMoveDetail(result.arrowDirection, result.distanceMeters, result.microAdjust);
        result.secondaryHint = buildTurnHint(absHeading, result.headingDiffDegrees, true);
    }

    private void fillExactDirectionalTexts(GuidanceResult result) {
        float absHeading = Math.abs(result.headingDiffDegrees);

        if (result.inTightWindow) {
            result.arrowDirection = ArrowDirection.HOLD;
            result.arrowText = result.arrowDirection.arrow;
            result.title = "Hold this phone pose";
            result.detail = "Saved phone position matched.";
            result.secondaryHint = "Keep the phone straight and start photo capture";
            return;
        }

        float absHeight = Math.abs(result.phoneHeightDiffMeters);
        if (result.distanceMeters <= READY_DISTANCE_METERS && absHeight > READY_PHONE_HEIGHT_METERS) {
            result.arrowDirection = ArrowDirection.HOLD;
            result.arrowText = result.arrowDirection.arrow;
            result.title = result.phoneHeightDiffMeters > 0f ? "Raise phone" : "Lower phone";
            result.detail = result.phoneHeightDiffMeters > 0f
                    ? "Raise the phone to match the saved onboarding height."
                    : "Lower the phone to match the saved onboarding height.";
            result.secondaryHint = "Match the onboarding person’s phone height before capture";
            return;
        }

        result.turnOnly = result.distanceMeters <= TURN_ONLY_DISTANCE_METERS && absHeading >= TURN_HINT_DEGREES;
        if (result.turnOnly) {
            result.arrowDirection = result.headingDiffDegrees > 0f ? ArrowDirection.TURN_LEFT : ArrowDirection.TURN_RIGHT;
            result.arrowText = result.arrowDirection.arrow;
            result.title = result.arrowDirection.label;
            result.detail = absHeading >= BIG_TURN_HINT_DEGREES
                    ? (result.headingDiffDegrees > 0f ? "Turn left in place to match the saved view." : "Turn right in place to match the saved view.")
                    : (result.headingDiffDegrees > 0f ? "Turn a little left in place." : "Turn a little right in place.");
            result.secondaryHint = "Keep the shelf photo matched while you turn";
            return;
        }

        result.arrowDirection = bucketDirection(result.forwardMeters, result.sideMeters);
        result.arrowText = result.arrowDirection.arrow;
        result.title = readableDirection(result.arrowDirection);
        result.detail = buildStandMoveDetail(result.arrowDirection, result.distanceMeters, result.microAdjust);
        result.secondaryHint = buildTurnHint(absHeading, result.headingDiffDegrees, false);
    }

    private ArrowDirection bucketDirection(float forwardMeters, float sideMeters) {
        float absForward = Math.abs(forwardMeters);
        float absSide = Math.abs(sideMeters);

        if (absForward <= AXIS_DEAD_ZONE_METERS && absSide <= AXIS_DEAD_ZONE_METERS) {
            return ArrowDirection.HOLD;
        }

        boolean diagonal = absForward > AXIS_DEAD_ZONE_METERS
                && absSide > AXIS_DEAD_ZONE_METERS
                && Math.max(absForward, absSide) / Math.max(0.001f, Math.min(absForward, absSide)) <= DIAGONAL_RATIO_LIMIT;

        if (diagonal) {
            if (forwardMeters >= 0f && sideMeters >= 0f) return ArrowDirection.FORWARD_RIGHT;
            if (forwardMeters >= 0f && sideMeters < 0f) return ArrowDirection.FORWARD_LEFT;
            if (forwardMeters < 0f && sideMeters >= 0f) return ArrowDirection.BACK_RIGHT;
            return ArrowDirection.BACK_LEFT;
        }

        if (absForward >= absSide) {
            return forwardMeters >= 0f ? ArrowDirection.FORWARD : ArrowDirection.BACK;
        }
        return sideMeters >= 0f ? ArrowDirection.RIGHT : ArrowDirection.LEFT;
    }

    private String readableDirection(ArrowDirection direction) {
        switch (direction) {
            case FORWARD:
                return "Walk straight";
            case FORWARD_LEFT:
                return "Walk front-left";
            case FORWARD_RIGHT:
                return "Walk front-right";
            case LEFT:
                return "Move left";
            case RIGHT:
                return "Move right";
            case BACK:
                return "Step back";
            case BACK_LEFT:
                return "Step back-left";
            case BACK_RIGHT:
                return "Step back-right";
            case TURN_LEFT:
                return "Turn left";
            case TURN_RIGHT:
                return "Turn right";
            case HOLD:
            default:
                return "Hold position";
        }
    }

    private String buildRoomMoveDetail(ArrowDirection direction, float distanceMeters, boolean microAdjust) {
        String distance = distancePhrase(distanceMeters);
        switch (direction) {
            case FORWARD:
                return microAdjust ? "Take a small straight step." : ("Walk straight for " + distance + " toward the saved phone spot.");
            case LEFT:
                return microAdjust ? "Take a small step left." : ("Move left for " + distance + " toward the saved phone spot.");
            case RIGHT:
                return microAdjust ? "Take a small step right." : ("Move right for " + distance + " toward the saved phone spot.");
            case FORWARD_LEFT:
                return microAdjust ? "Take a small front-left step." : ("Walk front-left for " + distance + ".");
            case FORWARD_RIGHT:
                return microAdjust ? "Take a small front-right step." : ("Walk front-right for " + distance + ".");
            case BACK:
                return microAdjust ? "Take a small step back." : ("Step back for " + distance + " toward the saved phone spot.");
            case BACK_LEFT:
                return microAdjust ? "Take a small back-left step." : ("Step back-left for " + distance + ".");
            case BACK_RIGHT:
                return microAdjust ? "Take a small back-right step." : ("Step back-right for " + distance + ".");
            case HOLD:
            default:
                return "Hold position and compare with the shelf photo.";
        }
    }

    private String buildStandMoveDetail(ArrowDirection direction, float distanceMeters, boolean microAdjust) {
        String distance = distancePhrase(distanceMeters);
        switch (direction) {
            case FORWARD:
                return microAdjust ? "Tiny step forward." : ("Walk straight for " + distance + " to reach the saved phone spot.");
            case LEFT:
                return microAdjust ? "Tiny step left to match the saved view." : ("Move left for " + distance + " to reach the saved phone spot.");
            case RIGHT:
                return microAdjust ? "Tiny step right to match the saved view." : ("Move right for " + distance + " to reach the saved phone spot.");
            case FORWARD_LEFT:
                return microAdjust ? "Tiny front-left step." : ("Walk front-left for " + distance + ".");
            case FORWARD_RIGHT:
                return microAdjust ? "Tiny front-right step." : ("Walk front-right for " + distance + ".");
            case BACK:
                return microAdjust ? "Tiny step back." : ("Step back for " + distance + " toward the saved phone spot.");
            case BACK_LEFT:
                return microAdjust ? "Tiny back-left step." : ("Step back-left for " + distance + ".");
            case BACK_RIGHT:
                return microAdjust ? "Tiny back-right step." : ("Step back-right for " + distance + ".");
            case HOLD:
            default:
                return "Hold position and keep the shelf photo matched.";
        }
    }

    private String buildRoomMetricsLine(GuidanceResult result) {
        if (result.closeEnoughToAudit) {
            return "Check the shelf photo now";
        }
        if (result.turnOnly) {
            return "Turn first, then keep walking";
        }
        if (result.distanceMeters >= 4f) {
            return "Keep following the arrows";
        }
        if (result.distanceMeters >= 1.4f) {
            return "Keep walking toward the saved phone spot";
        }
        return "The saved phone spot should be close now";
    }

    private String buildExactMetricsLine(GuidanceResult result) {
        if (result.inTightWindow) {
            return "Phone position matched";
        }
        if (Math.abs(result.phoneHeightDiffMeters) > READY_PHONE_HEIGHT_METERS
                && result.distanceMeters <= READY_DISTANCE_METERS) {
            return result.phoneHeightDiffMeters > 0f
                    ? "Raise the phone to the saved height"
                    : "Lower the phone to the saved height";
        }
        if (result.turnOnly) {
            return "Turn in place to match the saved view";
        }
        if (result.closeEnoughToAudit) {
            return "Use tiny steps to match the saved view";
        }
        return "Move to the saved phone spot";
    }

    private String buildTurnHint(float absHeading, float headingDiffDegrees, boolean walkingStage) {
        if (absHeading >= BIG_TURN_HINT_DEGREES) {
            return headingDiffDegrees > 0f
                    ? (walkingStage ? "Keep turning left while walking" : "Turn left a bit more")
                    : (walkingStage ? "Keep turning right while walking" : "Turn right a bit more");
        }
        if (absHeading >= TURN_HINT_DEGREES) {
            return headingDiffDegrees > 0f
                    ? "Add a small left turn"
                    : "Add a small right turn";
        }
        return walkingStage ? "Keep the shelf photo in mind while you walk" : "Keep the phone angle similar to the saved photo";
    }


    private float getPhoneHeightDifferenceMeters(Pose currentPose, Pose targetPose) {
        if (currentPose == null || targetPose == null) {
            return 0f;
        }
        try {
            Pose delta = currentPose.inverse().compose(targetPose);
            float[] local = delta.getTranslation();
            return local[1];
        } catch (Exception ignore) {
            return 0f;
        }
    }

    private String distancePhrase(float distanceMeters) {
        if (distanceMeters < 0.5f) {
            return "a tiny bit";
        }
        float rounded = Math.max(0.5f, Math.round(distanceMeters * 2f) / 2f);
        if (rounded == Math.rint(rounded)) {
            return ((int) rounded) + " meter" + (rounded >= 2f ? "s" : "");
        }
        return rounded + " meters";
    }

    private float getHeadingDifferenceDegrees(Pose currentPose, Pose targetPose) {
        float[] currentZ = currentPose.getZAxis();
        float[] targetZ = targetPose.getZAxis();

        float currentX = currentZ[0];
        float currentForward = currentZ[2];
        float targetX = targetZ[0];
        float targetForward = targetZ[2];

        float currentLength = (float) Math.sqrt((currentX * currentX) + (currentForward * currentForward));
        float targetLength = (float) Math.sqrt((targetX * targetX) + (targetForward * targetForward));

        if (currentLength < 0.0001f || targetLength < 0.0001f) {
            return 0f;
        }

        currentX /= currentLength;
        currentForward /= currentLength;
        targetX /= targetLength;
        targetForward /= targetLength;

        float currentYaw = (float) Math.toDegrees(Math.atan2(currentX, currentForward));
        float targetYaw = (float) Math.toDegrees(Math.atan2(targetX, targetForward));

        float diff = targetYaw - currentYaw;
        while (diff > 180f) diff -= 360f;
        while (diff < -180f) diff += 360f;
        return diff;
    }
}
