package com.salesdairy.shelfarapp.onboarding;

import android.graphics.Color;
import android.view.View;

import com.salesdairy.shelfarapp.databinding.ActivityStoreReferenceBinding;

public final class StoreReferenceUi {

    private static final int COLOR_READY = Color.parseColor("#22C55E");
    private static final int COLOR_WARN = Color.parseColor("#38BDF8");
    private static final int COLOR_MUTED = Color.parseColor("#94A3B8");

    private final ActivityStoreReferenceBinding binding;

    public StoreReferenceUi(ActivityStoreReferenceBinding binding) {
        this.binding = binding;
        binding.btnEditLabels.setText("Edit name");
    }

    public void setInitialButtonState() {
        binding.btnSaveReferencePoint.setEnabled(false);
        binding.btnSaveReferencePoint.setText("Save store reference");
    }

    public void setSavingState() {
        binding.btnSaveReferencePoint.setEnabled(false);
        binding.btnSaveReferencePoint.setText("Saving reference…");
    }

    public void setActionState(boolean canSave, String text) {
        binding.btnSaveReferencePoint.setEnabled(canSave);
        binding.btnSaveReferencePoint.setText(text);
    }

    public void updateProgressSteps(boolean trackingReady,
                                    boolean postureReady,
                                    boolean centeredReady,
                                    boolean qualityReady) {
        setStepColor(binding.stepTrack, trackingReady, true);
        setStepColor(binding.stepCenter, centeredReady, trackingReady);
        setStepColor(binding.stepQuality, qualityReady, trackingReady && centeredReady);

        if (binding.stepPosture != null) {
            binding.stepPosture.setVisibility(View.GONE);
        }
        if (binding.stepMotionBonus != null) {
            binding.stepMotionBonus.setVisibility(View.GONE);
        }
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
