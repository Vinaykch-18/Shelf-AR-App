package com.salesdairy.shelfarapp.onboarding;

import android.graphics.Color;
import android.view.View;

import com.salesdairy.shelfarapp.databinding.ActivityOnboardShelfBinding;

public final class OnboardShelfUi {

    private static final int COLOR_READY = Color.parseColor("#22C55E");
    private static final int COLOR_WARN = Color.parseColor("#38BDF8");
    private static final int COLOR_MUTED = Color.parseColor("#94A3B8");

    private final ActivityOnboardShelfBinding binding;

    public OnboardShelfUi(ActivityOnboardShelfBinding binding) {
        this.binding = binding;
        binding.btnCaptureShelf.setText("Capture photo");
        binding.btnSaveShelf.setText("Use this photo");
    }

    public void renderReferenceWaiting(boolean trackingReady, String areaLabel) {
        if (!trackingReady) {
            binding.tvScanStepTitle.setText("Start camera scan");
            binding.tvCaptureHint.setText("Move slowly until the camera view becomes stable.");
            binding.tvAnchorStatus.setText("Active reference • " + areaLabel);
        } else {
            binding.tvScanStepTitle.setText("Lock the store reference");
            binding.tvCaptureHint.setText("Lock the saved entrance point once, then keep the camera open and add shelves one by one.");
            binding.tvAnchorStatus.setText(areaLabel + " • Reference lock in progress");
        }
    }

    public void renderCaptureFlow(boolean trackingReady,
                                  boolean markerReady,
                                  boolean postureOk,
                                  boolean centeredOk,
                                  boolean steadyReady,
                                  boolean hasCaptured,
                                  boolean hasName) {
        String title;
        String hint;
        String status;

        if (!trackingReady) {
            title = "Scan the shelf";
            hint = "Move slowly until the camera view becomes stable.";
            status = "Hold the phone straight and keep the full shelf in view.";
        } else if (!markerReady) {
            title = "Scan the entry point";
            hint = "Stand at the saved entry point until it locks.";
            status = "This happens only once for the current walkthrough.";
        } else if (!postureOk) {
            title = "Hold the phone straight";
            hint = "Keep the phone upright at normal walking height.";
            status = "Avoid tilting too far up or down.";
        } else if (!centeredOk) {
            title = "Frame the full shelf";
            hint = "Keep the whole shelf inside the guide box.";
            status = "Show the full shelf clearly before you capture.";
        } else if (!steadyReady) {
            title = "Hold steady";
            hint = "Keep the shelf centered for a brief moment.";
            status = "A steady frame helps the audit user return here later.";
        } else if (!hasCaptured) {
            title = "Ready to capture";
            hint = "Tap capture when the shelf looks clean and centered.";
            status = "You can retake the photo before saving it.";
        } else if (!hasName) {
            title = "Name and save";
            hint = "Check the photo, enter the shelf name, then save it.";
            status = "After saving, keep AR open and walk to the next shelf.";
        } else {
            title = "Save shelf";
            hint = "Review the photo and save this shelf.";
            status = "Stay in the same session and keep mapping the store path.";
        }

        binding.tvScanStepTitle.setText(title);
        binding.tvCaptureHint.setText(hint);
        binding.tvAnchorStatus.setText(status);
    }

    public void renderCaptureSummary(boolean hasCaptured) {
        binding.tvCaptureSummary.setText(hasCaptured
                ? "Photo captured. Retake it or save this shelf and continue."
                : "Keep the full shelf inside the frame, then capture it.");
    }

    public void updateProgressSteps(boolean trackingReady,
                                    boolean markerReady,
                                    boolean centeredOk,
                                    boolean steadyReady) {
        setStepColor(binding.stepTrack, trackingReady, true);
        setStepColor(binding.stepPosture, markerReady, trackingReady);
        setStepColor(binding.stepCenter, centeredOk, trackingReady && markerReady);
        setStepColor(binding.stepQuality, steadyReady, trackingReady && markerReady && centeredOk);
        if (binding.stepMotionBonus != null) {
            binding.stepMotionBonus.setVisibility(View.GONE);
        }
    }

    public void updatePostureText(boolean hasSensorReading, boolean strictlyUpright, boolean postureOk) {
        if (!hasSensorReading) {
            binding.tvPostureLabel.setText("ALIGN");
            binding.tvPostureLabel.setTextColor(COLOR_MUTED);
            return;
        }
        if (strictlyUpright) {
            binding.tvPostureLabel.setText("GREAT");
            binding.tvPostureLabel.setTextColor(COLOR_READY);
            return;
        }
        if (postureOk) {
            binding.tvPostureLabel.setText("READY");
            binding.tvPostureLabel.setTextColor(COLOR_WARN);
            return;
        }
        binding.tvPostureLabel.setText("ALIGN");
        binding.tvPostureLabel.setTextColor(COLOR_MUTED);
    }

    private void setStepColor(View dot, boolean done, boolean active) {
        if (dot == null) {
            return;
        }
        if (done) {
            dot.setBackgroundColor(COLOR_READY);
        } else if (active) {
            dot.setBackgroundColor(COLOR_WARN);
        } else {
            dot.setBackgroundColor(COLOR_MUTED);
        }
    }
}
