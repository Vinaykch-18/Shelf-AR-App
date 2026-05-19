package com.salesdairy.shelfarapp.audit;

import androidx.annotation.NonNull;

import com.google.ar.core.TrackingFailureReason;
import com.salesdairy.shelfarapp.models.StoreReference;

public final class AuditRecoveryText {

    public static final class RecoveryCopy {
        public final String title;
        public final String detail;

        public RecoveryCopy(String title, String detail) {
            this.title = title;
            this.detail = detail;
        }
    }

    private AuditRecoveryText() {
    }

    @NonNull
    public static RecoveryCopy forReferenceLock(StoreReference reference,
                                                boolean resolveStarted,
                                                long startedAtMs,
                                                int failureCount,
                                                String lastError,
                                                long nowMs) {
        String shortName = ReferencePointText.shortName(reference);
        if (!resolveStarted) {
            return new RecoveryCopy(
                    "Start at the saved reference",
                    "Stand near " + shortName + " and match the saved reference photo."
            );
        }
        long elapsedMs = startedAtMs > 0L ? Math.max(0L, nowMs - startedAtMs) : 0L;
        if (failureCount >= 2) {
            return new RecoveryCopy(
                    "Need a better reference view",
                    "Go back to the saved reference and keep more fixed entrance details in frame."
            );
        }
        if (elapsedMs > 5500L) {
            return new RecoveryCopy(
                    "Still locking the room",
                    "Stay near the saved reference. Step back a little if needed, then match the photo again."
            );
        }
        return new RecoveryCopy(
                "Locking the saved reference",
                "Keep the same fixed details around " + shortName + " in view."
        );
    }

    @NonNull
    public static RecoveryCopy forTracking(TrackingFailureReason reason, boolean nearStandSpot) {
        if (reason == null) {
            return new RecoveryCopy(
                    "Tracking is weak",
                    nearStandSpot
                            ? "Hold steady and keep the shelf in view."
                            : "Move slowly and keep store details in frame."
            );
        }
        String value = reason.name();
        if ("BAD_STATE".equals(value)) {
            return new RecoveryCopy("Camera changed", "Pause for a moment, then move slowly.");
        }
        if ("EXCESSIVE_MOTION".equals(value)) {
            return new RecoveryCopy("Phone moved too fast", "Slow down, then hold steady for a moment.");
        }
        if ("INSUFFICIENT_FEATURES".equals(value)) {
            return new RecoveryCopy("Need more details", "Aim at shelf edges, labels, corners, and other fixed details in view.");
        }
        if ("INSUFFICIENT_LIGHT".equals(value)) {
            return new RecoveryCopy("Scene is too dark", "Point at brighter shelf details or improve the lighting.");
        }
        if ("CAMERA_UNAVAILABLE".equals(value)) {
            return new RecoveryCopy("Camera is busy", "Close other camera apps and try again.");
        }
        return new RecoveryCopy("Tracking is weak", "Move slowly and keep the saved shelf area in view.");
    }
}
