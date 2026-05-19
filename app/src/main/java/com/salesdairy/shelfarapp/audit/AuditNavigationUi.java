package com.salesdairy.shelfarapp.audit;

import android.graphics.Color;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;

import com.salesdairy.shelfarapp.R;
import com.salesdairy.shelfarapp.ar.AuditGuidanceEngine;
import com.salesdairy.shelfarapp.databinding.ActivityAuditNavigationBinding;
import com.salesdairy.shelfarapp.models.Shelf;
import com.salesdairy.shelfarapp.models.StoreReference;

public final class AuditNavigationUi {

    private enum Accent { ENTRY, ROUTE, PHONE, READY, WARN, ERROR }

    private final ActivityAuditNavigationBinding binding;

    public AuditNavigationUi(ActivityAuditNavigationBinding binding) {
        this.binding = binding;
        binding.tvStepLock.setText("1 Entry");
        binding.tvStepShelf.setText("2 Path");
        binding.tvStepStand.setText("3 Phone");
        binding.layoutGuidePreview.setVisibility(View.GONE);
        hidePhoneTargetFrame();
    }

    public void renderStaticState(StoreReference storeReference, boolean unresolved, AuditSessionProgress progress, Shelf currentShelf) {
        binding.btnViewSavedView.setText(unresolved ? "View entry photo" : "View saved photo");
        binding.tvTrackingStatus.setText(unresolved ? "Step 1 of 3 • Find entry" : "Step 2 of 3 • Follow path");
        setCloudChip(unresolved ? "ENTRY" : "PATH", unresolved ? Accent.ENTRY : Accent.ROUTE);
        binding.tvDirection.setText(unresolved ? "Find the entry point" : "Follow the path");
        binding.tvDistance.setText(unresolved ? "Match the saved entry photo" : "Follow the dots to the shelf");
        setStatusText(null);
        binding.tvCenterHint.setVisibility(View.GONE);
        hidePhoneTargetFrame();
        showPathRibbon(unresolved ? null : "Path ready");
        setTargetBadge(unresolved ? "ENTRY" : "FOLLOW PATH", unresolved ? Accent.ENTRY : Accent.ROUTE);
        binding.btnStartAudit.setVisibility(View.GONE);
        binding.tvShelfProgress.setText(buildProgressLine(progress));
        updateStepChips(!unresolved, false, false);
        hideRecoveryCard();
    }

    public void renderTrackingRecovery(String title, String detail, boolean nearStand) {
        binding.tvArrow.setText("•");
        binding.btnStartAudit.setVisibility(View.GONE);
        binding.tvTrackingStatus.setText(nearStand ? "Tracking paused • Hold phone" : "Tracking paused • Re-lock entry");
        setCloudChip(nearStand ? "PHONE" : "ENTRY", nearStand ? Accent.PHONE : Accent.ENTRY);
        binding.tvDirection.setText(nearStand ? "Hold still" : "Point at fixed details");
        binding.tvDistance.setText("Wait a moment");
        setStatusText(detail);
        binding.tvCenterHint.setVisibility(View.GONE);
        hidePhoneTargetFrame();
        binding.tvPathRibbon.setVisibility(View.GONE);
        setTargetBadge(nearStand ? "PHONE" : "ENTRY", nearStand ? Accent.PHONE : Accent.ENTRY);
        hideRecoveryCard();
    }

    public void renderReferenceLock(StoreReference storeReference, boolean resolveStarted, String title, String detail) {
        binding.btnStartAudit.setVisibility(View.GONE);
        binding.btnViewSavedView.setText("View entry photo");
        binding.tvTrackingStatus.setText("Step 1 of 3 • Find entry");
        setCloudChip("ENTRY", Accent.ENTRY);
        binding.tvArrow.setText("⌖");
        binding.tvDirection.setText("Find the entry point");
        binding.tvDistance.setText(resolveStarted ? "Keep the saved entry photo in view" : "Match the saved entry photo");
        setStatusText(null);
        binding.tvCenterHint.setVisibility(View.GONE);
        hidePhoneTargetFrame();
        showPathRibbon(null);
        setTargetBadge("ENTRY", Accent.ENTRY);
        hideRecoveryCard();
        updateStepChips(false, false, false);
    }

    public void renderGuidance(AuditGuidanceEngine.GuidanceResult result,
                               boolean exact,
                               StoreReference storeReference,
                               Shelf currentShelf,
                               String recoveryTitle,
                               String recoveryDetail,
                               boolean locked,
                               boolean shelfReached,
                               boolean standMatched) {
        binding.btnViewSavedView.setText("View saved photo");
        binding.tvArrow.setText(result.arrowText);
        binding.tvTrackingStatus.setText(exact ? "Step 3 of 3 • Match phone" : "Step 2 of 3 • Follow path");
        setCloudChip(exact ? "PHONE" : "PATH", exact ? Accent.PHONE : Accent.ROUTE);
        binding.tvDirection.setText(exact ? buildStandHeadline(result) : buildWalkHeadline(result));
        binding.tvDistance.setText(exact ? buildStandDistance(result) : buildWalkDistance(result));
        setStatusText(exact ? null : buildWalkStatus(result));
        if (exact) {
            binding.tvCenterHint.setVisibility(View.GONE);
            binding.tvPathRibbon.setVisibility(View.GONE);
            hidePhoneTargetFrame();
            setTargetBadge(result.inTightWindow ? "HOLD" : "MATCH PHONE", Accent.PHONE);
        } else {
            hidePhoneTargetFrame();
            binding.tvCenterHint.setVisibility(View.GONE);
            showPathRibbon(result.closeEnoughToAudit ? "Shelf near" : null);
            setTargetBadge(result.closeEnoughToAudit ? "NEAR SHELF" : "FOLLOW PATH", Accent.ROUTE);
        }
        hideRecoveryCard();
        updateStepChips(locked, shelfReached, standMatched);
        binding.btnStartAudit.setVisibility(View.GONE);
    }

    public void renderReadyState(AuditSessionProgress progress, Shelf currentShelf) {
        binding.tvArrow.setText("✓");
        binding.tvDirection.setText("Capture photo");
        binding.tvDistance.setText("Phone straight");
        setStatusText(null);
        binding.tvTrackingStatus.setText("Step 3 of 3 • Capture photo");
        setCloudChip("READY", Accent.READY);
        binding.tvCenterHint.setVisibility(View.GONE);
        binding.tvPathRibbon.setVisibility(View.GONE);
        hidePhoneTargetFrame();
        binding.btnStartAudit.setVisibility(View.VISIBLE);
        binding.btnStartAudit.setText(progress.getPendingShelves() > 1 ? "Capture this shelf" : "Capture final shelf");
        binding.btnViewSavedView.setText("View saved photo");
        setTargetBadge("CAPTURE", Accent.READY);
        hideRecoveryCard();
        updateStepChips(true, true, true);
    }

    public void renderRouteRecovery(String title, String detail, boolean recovering) {
        binding.btnStartAudit.setVisibility(View.GONE);
        binding.tvTrackingStatus.setText("Step 2 of 3 • Follow path");
        setCloudChip(recovering ? "RECOVER" : "PATH", recovering ? Accent.WARN : Accent.ROUTE);
        binding.tvArrow.setText(recovering ? "↺" : "→");
        binding.tvDirection.setText(recovering ? "Find the path again" : title);
        binding.tvDistance.setText(recovering ? "Turn back until the dots appear" : "Stay with the next dot");
        setStatusText(recovering ? null : detail);
        binding.tvCenterHint.setVisibility(View.GONE);
        hidePhoneTargetFrame();
        showPathRibbon(recovering ? "Recovering" : null);
        setTargetBadge(recovering ? "RECOVER" : "FOLLOW PATH", recovering ? Accent.WARN : Accent.ROUTE);
        hideRecoveryCard();
    }

    public void renderPoseError() {
        binding.tvTrackingStatus.setText("Saved shelf data missing");
        setCloudChip("CHECK", Accent.ERROR);
        binding.tvDirection.setText("Save this shelf again");
        binding.tvDistance.setText("Required shelf pose was not found");
        setStatusText("Open shelf onboarding again under the same entry lock.");
        binding.tvCenterHint.setVisibility(View.GONE);
        hidePhoneTargetFrame();
        binding.tvPathRibbon.setVisibility(View.GONE);
        setTargetBadge("CHECK SHELF", Accent.ERROR);
        showRecoveryCard("Saved shelf data missing", "Re-save this shelf from the correct reference.");
    }

    public void updateStepChips(boolean locked, boolean shelfReached, boolean standMatched) {
        styleStepChip(binding.tvStepLock, locked, !locked);
        styleStepChip(binding.tvStepShelf, shelfReached, locked && !shelfReached);
        styleStepChip(binding.tvStepStand, standMatched, locked && shelfReached && !standMatched);
    }

    private boolean shouldTurnAround(AuditGuidanceEngine.GuidanceResult result) {
        return result != null && (result.turnOnly || Math.abs(result.headingDiffDegrees) >= 70f);
    }

    private String buildWalkHeadline(AuditGuidanceEngine.GuidanceResult result) {
        if (shouldTurnAround(result)) {
            return "Turn around";
        }
        return result.closeEnoughToAudit ? "Shelf is close" : "Follow the path";
    }

    private String buildWalkDistance(AuditGuidanceEngine.GuidanceResult result) {
        if (shouldTurnAround(result)) {
            return "Turn until the dots are in front of you";
        }
        if (result.closeEnoughToAudit) {
            return "Stand on the circle";
        }
        return result.distanceMeters >= 1.2f ? "Keep following the dots" : "Circle appears soon";
    }

    private String buildWalkStatus(AuditGuidanceEngine.GuidanceResult result) {
        if (shouldTurnAround(result)) {
            return "Turn until the dots line up in front of you.";
        }
        if (result.closeEnoughToAudit) {
            return "Step onto the circle, then match the phone box.";
        }
        return null;
    }

    private String buildStandHeadline(AuditGuidanceEngine.GuidanceResult result) {
        return result.inTightWindow ? "Hold still" : "Stand on the circle";
    }

    private String buildStandDistance(AuditGuidanceEngine.GuidanceResult result) {
        if (result.inTightWindow) {
            return "Hold this view";
        }
        return "Keep the shelf in front of you";
    }

    private String buildStandStatus(AuditGuidanceEngine.GuidanceResult result) {
        if (result.inTightWindow) {
            return null;
        }
        return null;
    }

    private void showPhoneTargetFrame(boolean matched, boolean activeMatch) {
        binding.layoutPhoneTargetFrame.setVisibility(View.GONE);
    }

    private void hidePhoneTargetFrame() {
        binding.layoutPhoneTargetFrame.setVisibility(View.GONE);
    }

    private void setStatusText(String value) {
        if (TextUtils.isEmpty(value)) {
            binding.tvStatus.setVisibility(View.GONE);
            return;
        }
        binding.tvStatus.setVisibility(View.VISIBLE);
        binding.tvStatus.setText(value);
    }

    private void showPathRibbon(String label) {
        if (TextUtils.isEmpty(label)) {
            binding.tvPathRibbon.setVisibility(View.GONE);
            return;
        }
        binding.tvPathRibbon.setVisibility(View.VISIBLE);
        binding.tvPathRibbon.setText(label);
    }

    private void setTargetBadge(String label, Accent accent) {
        if (TextUtils.isEmpty(label)) {
            binding.tvTargetBadge.setVisibility(View.GONE);
            return;
        }
        binding.tvTargetBadge.setVisibility(View.VISIBLE);
        binding.tvTargetBadge.setText(label);
        applyChipStyle(binding.tvTargetBadge, accent);
    }

    private void setCloudChip(String label, Accent accent) {
        binding.tvCloudStatus.setText(label);
        applyChipStyle(binding.tvCloudStatus, accent);
    }

    private void hideRecoveryCard() {
        binding.layoutRecoveryCard.setVisibility(View.GONE);
    }

    private String buildProgressLine(AuditSessionProgress progress) {
        String shelfLine = progress.getShelfLine();
        String headerLine = progress.getHeaderLine();
        return shelfLine == null || shelfLine.isEmpty() ? headerLine : (shelfLine + " • " + headerLine);
    }

    private void styleStepChip(TextView view, boolean done, boolean active) {
        if (done) {
            view.setBackgroundResource(R.drawable.bg_step_chip_done);
            view.setTextColor(Color.WHITE);
        } else if (active) {
            view.setBackgroundResource(R.drawable.bg_step_chip_active);
            view.setTextColor(Color.WHITE);
        } else {
            view.setBackgroundResource(R.drawable.bg_chip_dark);
            view.setTextColor(Color.parseColor("#CBD5E1"));
        }
    }

    private void showRecoveryCard(String title, String detail) {
        binding.layoutRecoveryCard.setVisibility(View.VISIBLE);
        binding.tvRecoveryTitle.setText(title);
        binding.tvRecoveryHint.setText(detail);
    }

    private void applyChipStyle(TextView view, Accent accent) {
        if (view == null) {
            return;
        }
        switch (accent) {
            case ENTRY:
                view.setBackgroundResource(R.drawable.bg_chip_dark);
                view.setTextColor(Color.parseColor("#93C5FD"));
                break;
            case ROUTE:
                view.setBackgroundResource(R.drawable.bg_step_chip_active);
                view.setTextColor(Color.WHITE);
                break;
            case PHONE:
                view.setBackgroundResource(R.drawable.bg_chip_dark);
                view.setTextColor(Color.parseColor("#A7F3D0"));
                break;
            case READY:
                view.setBackgroundResource(R.drawable.bg_step_chip_done);
                view.setTextColor(Color.WHITE);
                break;
            case WARN:
                view.setBackgroundResource(R.drawable.bg_step_chip_warn);
                view.setTextColor(Color.WHITE);
                break;
            case ERROR:
                view.setBackgroundResource(R.drawable.bg_step_chip_warn);
                view.setTextColor(Color.parseColor("#FED7AA"));
                break;
        }
    }
}
