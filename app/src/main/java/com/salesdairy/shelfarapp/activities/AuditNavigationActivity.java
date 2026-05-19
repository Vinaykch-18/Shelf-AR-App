package com.salesdairy.shelfarapp.activities;


import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.PixelCopy;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentOnAttachListener;

import com.google.ar.core.Anchor;
import com.google.ar.core.CameraConfig;
import com.google.ar.core.CameraConfigFilter;
import com.google.ar.core.Config;
import com.google.ar.core.Frame;
import com.google.ar.core.Pose;
import com.google.ar.core.Session;
import com.google.ar.core.TrackingFailureReason;
import com.google.ar.core.TrackingState;
import com.google.ar.sceneform.ArSceneView;
import com.google.ar.sceneform.ux.ArFragment;
import com.google.ar.sceneform.ux.BaseArFragment;
import com.salesdairy.shelfarapp.ar.ARCoreManager;
import com.salesdairy.shelfarapp.ar.AuditGuidanceEngine;
import com.salesdairy.shelfarapp.ar.CloudAnchorHelper;
import com.salesdairy.shelfarapp.audit.AuditPoseHelper;
import com.salesdairy.shelfarapp.audit.AuditRecoveryText;
import com.salesdairy.shelfarapp.audit.AuditGuideImageHelper;
import com.salesdairy.shelfarapp.audit.AuditNavigationUi;
import com.salesdairy.shelfarapp.audit.AuditArStandMarkerHelper;
import com.salesdairy.shelfarapp.audit.AuditSessionBundle;
import com.salesdairy.shelfarapp.audit.AuditSessionProgress;
import com.salesdairy.shelfarapp.data.AuditRepository;
import com.salesdairy.shelfarapp.data.StoreReferenceRepository;
import com.salesdairy.shelfarapp.data.RouteRepository;
import com.salesdairy.shelfarapp.data.ShelfRepository;
import com.salesdairy.shelfarapp.data.TelemetryRepository;
import com.salesdairy.shelfarapp.databinding.ActivityAuditNavigationBinding;
import com.salesdairy.shelfarapp.models.StoreReference;
import com.salesdairy.shelfarapp.models.RouteCheckpoint;
import com.salesdairy.shelfarapp.models.Shelf;
import com.salesdairy.shelfarapp.models.ShelfAuditStatus;
import com.salesdairy.shelfarapp.sensors.OrientationHelper;
import com.salesdairy.shelfarapp.utils.Constants;
import com.salesdairy.shelfarapp.utils.CrashLogRepository;
import com.salesdairy.shelfarapp.utils.ImageUtils;
import com.salesdairy.shelfarapp.utils.PermissionUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public class AuditNavigationActivity extends AppCompatActivity implements FragmentOnAttachListener,
        BaseArFragment.OnSessionConfigurationListener,
        ArFragment.OnViewCreatedListener {

    private static final String TAG = "ShelfARFlow";
    private static final String FRAGMENT_TAG_AUDIT_AR = "audit_ar_fragment";
    private static final float MAX_REASONABLE_SHELF_DISTANCE_METERS = 5.5f;
    private static final String STATE_SESSION_ID = "audit_state_session_id";
    private static final String STATE_SHELF_ID = "audit_state_shelf_id";
    private static final String STATE_STAGE = "audit_state_stage";
    private static final String STATE_RESOLVED_POSE = "audit_state_resolved_pose";
    private static final long ROUTE_RECOVERY_DWELL_MS = 520L;
    private static final long ROUTE_STABLE_DWELL_MS = 680L;
    private static final long NEAR_SHELF_DWELL_MS = 620L;
    private static final long MATCH_PHONE_DWELL_MS = 320L;
    private static final long READY_DWELL_MS = 220L;
    private static final long READY_STICKY_MS = 2200L;
    private static final float OUTER_APPROACH_DISTANCE_METERS = 1.95f;
    private static final float MID_APPROACH_DISTANCE_METERS = 1.45f;
    private static final float FINAL_APPROACH_DISTANCE_METERS = 1.20f;
    private static final float OUTER_APPROACH_SIDE_METERS = 0.55f;
    private static final float MID_APPROACH_SIDE_METERS = 0.62f;
    private static final float FINAL_APPROACH_SIDE_METERS = 0.55f;
    private static final float OUTER_APPROACH_BEARING_DEGREES = 42f;
    private static final float MID_APPROACH_BEARING_DEGREES = 48f;
    private static final float FINAL_APPROACH_BEARING_DEGREES = 42f;
    private static final float MID_APPROACH_HEADING_DEGREES = 62f;
    private static final float FINAL_APPROACH_HEADING_DEGREES = 60f;
    private static final float FINAL_APPROACH_PHONE_HEIGHT_METERS = 0.58f;
    private static final float CAPTURE_ZONE_DISTANCE_METERS = 1.45f;
    private static final float CAPTURE_ZONE_SIDE_METERS = 0.95f;
    private static final float CAPTURE_ZONE_HEADING_DEGREES = 105f;
    private static final float CAPTURE_ZONE_HEIGHT_METERS = 0.80f;
    private static final float DIRECT_NEAR_SHELF_DISTANCE_METERS = 0.95f;
    private static final float DIRECT_NEAR_SHELF_SIDE_METERS = 0.38f;
    private static final float VERY_CLOSE_CAPTURE_DISTANCE_METERS = 0.34f;
    private static final float PATH_SPACING_METERS = 0.34f;

    private enum Stage { FIND_REFERENCE, FOLLOW_PATH, NEAR_SHELF, MATCH_PHONE, READY, RECOVER_ROUTE }

    private ActivityAuditNavigationBinding binding;
    private ShelfRepository shelfRepository;
    private StoreReferenceRepository storeReferenceRepository;
    private RouteRepository routeRepository;
    private AuditRepository auditRepository;
    private TelemetryRepository telemetryRepository;
    private final AuditGuidanceEngine guidanceEngine = new AuditGuidanceEngine();
    private final CloudAnchorHelper referenceResolveHelper = new CloudAnchorHelper();
    private ARCoreManager arCoreManager;
    private ArFragment arFragment;
    private final AtomicBoolean arBootstrapInProgress = new AtomicBoolean(false);

    private Shelf currentShelf;
    private Shelf poseSourceShelf;
    private StoreReference storeReference;
    private List<Shelf> areaShelves = new ArrayList<>();
    private List<RouteCheckpoint> routeCheckpoints = new ArrayList<>();
    private List<RouteCheckpoint> activeRoutePath = new ArrayList<>();
    private RouteRepository.PathResult activeRouteResult = null;
    private AuditSessionProgress sessionProgress = new AuditSessionProgress();
    private AuditNavigationUi navigationUi;
    private AuditGuideImageHelper guideImageHelper;
    private AuditArStandMarkerHelper arStandMarkerHelper;
    private OrientationHelper orientationHelper;

    private Pose resolvedReferencePose;
    private Anchor resolvedReferenceAnchor;
    private Pose targetShelfPose;
    private Pose targetCameraPose;
    private Pose targetPhoneFloorPose;
    private Pose targetDisplayShelfPose;
    private Pose targetWalkPose;
    private Pose relativeShelfFromCameraPose;
    private Stage stage = Stage.FIND_REFERENCE;
    private long readyHoldSinceMs;
    private long readyStickyUntilMs;
    private long shelfReadyHoldSinceMs;
    private long mismatchHoldSinceMs;
    private long routeRecoveryHoldSinceMs;
    private long routeStableHoldSinceMs;
    private long stageEnteredAtMs;
    private long sessionId = -1L;
    private long resolveStartedAtMs;
    private long trackingLostSinceMs;
    private long lastWeakLockToastAtMs;
    private int resolveFailureCount;
    private String lastResolveMessage;
    private boolean userRequestedInstall = true;
    private boolean sceneListenerAdded;
    private boolean resolveStarted;
    private boolean resolveFinished;
    private boolean poseComputationFailed;
    private boolean preferredCameraConfigApplied;
    private long lastStep2DebugLogAtMs;
    private long lastStep3DebugLogAtMs;
    private long lastCaptureGateLogAtMs;
    private long shelfStartedAtMs;
    private long flapWindowStartedAtMs;
    private int flapCountInWindow;
    private long recoveryBurstWindowStartedAtMs;
    private int recoveryBurstCount;
    private long lastStageChangeAtMs;
    private long lastRelockSuggestionAtMs;
    private boolean trackingLossLoggedThisEpisode;
    private boolean readyTelemetryRecordedForShelf;
    private boolean inlineCaptureInProgress;
    private boolean launchedFromSalesDiary;
    private boolean externalCallbackDispatched;
    private String externalAuditSessionToken;
    private String externalCallbackScheme;
    private String externalCallbackHost;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAuditNavigationBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        navigationUi = new AuditNavigationUi(binding);
        guideImageHelper = new AuditGuideImageHelper(binding);
        arStandMarkerHelper = new AuditArStandMarkerHelper(this);
        orientationHelper = new OrientationHelper(this);
        shelfRepository = new ShelfRepository(this);
        storeReferenceRepository = new StoreReferenceRepository(this);
        routeRepository = new RouteRepository(this);
        auditRepository = new AuditRepository(this);
        telemetryRepository = new TelemetryRepository(this);
        CrashLogRepository.noteBreadcrumb(this, "AuditNavigationActivity onCreate");
        arCoreManager = new ARCoreManager();
        sessionId = AuditSessionBundle.getSessionId(getIntent());
        readExternalLaunch(getIntent());

        binding.btnBack.setOnClickListener(v -> handleAuditCancelled());
        binding.btnViewSavedView.setOnClickListener(v -> showSavedPhoto());
        binding.btnCloseSavedView.setOnClickListener(v -> binding.savedViewOverlay.setVisibility(View.GONE));
        binding.savedViewOverlay.setOnClickListener(v -> binding.savedViewOverlay.setVisibility(View.GONE));
        binding.savedViewCard.setOnClickListener(v -> { });
        binding.btnStartAudit.setOnClickListener(v -> handlePrimaryAction());

        getSupportFragmentManager().addFragmentOnAttachListener(this);
        int initialShelfId = savedInstanceState != null
                ? savedInstanceState.getInt(STATE_SHELF_ID, getIntent().getIntExtra(Constants.EXTRA_SHELF_ID, -1))
                : getIntent().getIntExtra(Constants.EXTRA_SHELF_ID, -1);
        if (!loadShelf(initialShelfId, false)) {
            return;
        }
        if (savedInstanceState != null) {
            sessionId = savedInstanceState.getLong(STATE_SESSION_ID, sessionId);
            clearResolvedReference();
            setStageSilently(Stage.FIND_REFERENCE);
        } else {
            restoreResolvedPoseFromIntent(getIntent());
        }
        updateStaticUi();
        if (openReviewIfAlreadyAudited()) {
            return;
        }
    }

    @Override
    public void onBackPressed() {
        handleAuditCancelled();
    }

    @Override
    protected void onResume() {
        super.onResume();
        CrashLogRepository.noteBreadcrumb(this, "AuditNavigationActivity onResume shelf=" + (currentShelf != null ? currentShelf.getId() : -1) + " stage=" + (stage != null ? stage.name() : "NONE"));
        if (orientationHelper != null) orientationHelper.register();
        if (telemetryRepository != null && sessionId > 0L && currentShelf != null) {
            telemetryRepository.record("audit_resumed",
                    currentShelf.getStoreReferenceId(),
                    currentShelf.getId(),
                    sessionId,
                    stage != null ? stage.name() : "UNKNOWN");
        }
        bootstrapArIfPossible();
    }

    @Override
    protected void onPause() {
        super.onPause();
        CrashLogRepository.noteBreadcrumb(this, "AuditNavigationActivity onPause shelf=" + (currentShelf != null ? currentShelf.getId() : -1) + " stage=" + (stage != null ? stage.name() : "NONE"));
        if (telemetryRepository != null && sessionId > 0L && currentShelf != null) {
            telemetryRepository.record("audit_interrupted",
                    currentShelf.getStoreReferenceId(),
                    currentShelf.getId(),
                    sessionId,
                    stage != null ? stage.name() : "UNKNOWN");
        }
        if (orientationHelper != null) orientationHelper.unregister();
        if (arStandMarkerHelper != null) {
            arStandMarkerHelper.clear();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        CrashLogRepository.noteBreadcrumb(this, "AuditNavigationActivity onDestroy");
        referenceResolveHelper.clear();
        if (arStandMarkerHelper != null) {
            arStandMarkerHelper.clear();
        }
        if (resolvedReferenceAnchor != null) {
            try {
                resolvedReferenceAnchor.detach();
            } catch (Exception ignore) {
            }
        }
    }

    private void writePoseToBundle(Bundle outState, String keyPrefix, Pose pose) {
        if (outState == null || pose == null) {
            return;
        }
        outState.putFloatArray(keyPrefix + "_t", pose.getTranslation());
        outState.putFloatArray(keyPrefix + "_q", pose.getRotationQuaternion());
    }

    private Pose readPoseFromBundle(Bundle bundle, String keyPrefix) {
        if (bundle == null) {
            return null;
        }
        float[] t = bundle.getFloatArray(keyPrefix + "_t");
        float[] q = bundle.getFloatArray(keyPrefix + "_q");
        if (t == null || q == null || t.length != 3 || q.length != 4) {
            return null;
        }
        try {
            return new Pose(t, q);
        } catch (Exception ignore) {
            return null;
        }
    }


    private void changeStage(Stage newStage, String reason) {
        if (newStage == null || stage == newStage) {
            return;
        }
        long now = SystemClock.elapsedRealtime();
        Stage previousStage = stage;
        stage = newStage;
        stageEnteredAtMs = now;
        readyHoldSinceMs = 0L;
        shelfReadyHoldSinceMs = 0L;
        mismatchHoldSinceMs = 0L;
        if (newStage != Stage.RECOVER_ROUTE) {
            routeRecoveryHoldSinceMs = 0L;
        }
        if (newStage != Stage.FOLLOW_PATH && newStage != Stage.NEAR_SHELF) {
            routeStableHoldSinceMs = 0L;
        }
        if (lastStageChangeAtMs > 0L && (now - lastStageChangeAtMs) <= 3500L) {
            if (flapWindowStartedAtMs == 0L || (now - flapWindowStartedAtMs) > 12000L) {
                flapWindowStartedAtMs = now;
                flapCountInWindow = 1;
            } else {
                flapCountInWindow++;
            }
            if (flapCountInWindow == 4 && telemetryRepository != null && currentShelf != null) {
                telemetryRepository.record("audit_stage_flap",
                        currentShelf.getStoreReferenceId(),
                        currentShelf.getId(),
                        sessionId,
                        flapCountInWindow,
                        "from=" + (previousStage != null ? previousStage.name() : "NONE") + " to=" + newStage.name());
            }
        } else if (flapWindowStartedAtMs > 0L && (now - flapWindowStartedAtMs) > 12000L) {
            flapWindowStartedAtMs = 0L;
            flapCountInWindow = 0;
        }
        lastStageChangeAtMs = now;

        if (newStage == Stage.RECOVER_ROUTE) {
            if (recoveryBurstWindowStartedAtMs == 0L || (now - recoveryBurstWindowStartedAtMs) > 25000L) {
                recoveryBurstWindowStartedAtMs = now;
                recoveryBurstCount = 1;
            } else {
                recoveryBurstCount++;
            }
            if (recoveryBurstCount >= 3 && (now - lastRelockSuggestionAtMs) > 15000L && telemetryRepository != null && currentShelf != null) {
                lastRelockSuggestionAtMs = now;
                telemetryRepository.record("audit_relock_suggested",
                        currentShelf.getStoreReferenceId(),
                        currentShelf.getId(),
                        sessionId,
                        recoveryBurstCount,
                        "Repeated recoveries near shelf");
            }
        }

        if (newStage == Stage.READY && !readyTelemetryRecordedForShelf && shelfStartedAtMs > 0L && telemetryRepository != null && currentShelf != null) {
            readyTelemetryRecordedForShelf = true;
            telemetryRepository.record("audit_ready_time_ms",
                    currentShelf.getStoreReferenceId(),
                    currentShelf.getId(),
                    sessionId,
                    (float) Math.max(0L, now - shelfStartedAtMs),
                    "Shelf became ready for capture");
        }

        if (telemetryRepository != null) {
            telemetryRepository.recordStage(newStage.name().toLowerCase(Locale.ROOT),
                    currentShelf != null ? currentShelf.getStoreReferenceId() : 0,
                    currentShelf != null ? currentShelf.getId() : 0L,
                    sessionId,
                    "from=" + (previousStage != null ? previousStage.name() : "NONE")
                            + (TextUtils.isEmpty(reason) ? "" : " reason=" + reason));
        }
        Log.d(TAG, "Audit stage -> " + newStage.name() + (TextUtils.isEmpty(reason) ? "" : (" " + reason)));
        CrashLogRepository.noteBreadcrumb(this, "Audit stage=" + newStage.name() + (TextUtils.isEmpty(reason) ? "" : (" " + reason)));
    }

    private void setStageSilently(Stage newStage) {
        stage = newStage == null ? Stage.FIND_REFERENCE : newStage;
        stageEnteredAtMs = SystemClock.elapsedRealtime();
        lastStageChangeAtMs = stageEnteredAtMs;
    }

    private long armHold(long sinceMs, boolean condition) {
        if (!condition) {
            return 0L;
        }
        return sinceMs == 0L ? SystemClock.elapsedRealtime() : sinceMs;
    }

    private boolean hasHeld(long sinceMs, long requiredMs) {
        return sinceMs > 0L && (SystemClock.elapsedRealtime() - sinceMs) >= requiredMs;
    }

    private boolean isPhoneAlignStage() {
        return stage == Stage.MATCH_PHONE || stage == Stage.READY;
    }

    private boolean isOuterApproachCorridor(AuditGuidanceEngine.GuidanceResult phoneApproach,
                                            AuditGuidanceEngine.GuidanceResult phonePreview) {
        return phoneApproach != null
                && phoneApproach.distanceMeters <= OUTER_APPROACH_DISTANCE_METERS
                && Math.abs(phoneApproach.sideMeters) <= OUTER_APPROACH_SIDE_METERS
                && Math.abs(phoneApproach.bearingDegrees) <= OUTER_APPROACH_BEARING_DEGREES
                && (phonePreview == null || Math.abs(phonePreview.headingDiffDegrees) <= 40f);
    }

    private boolean isMidApproachCorridor(AuditGuidanceEngine.GuidanceResult phoneApproach,
                                          AuditGuidanceEngine.GuidanceResult phonePreview) {
        return phoneApproach != null
                && phoneApproach.distanceMeters <= MID_APPROACH_DISTANCE_METERS
                && Math.abs(phoneApproach.sideMeters) <= MID_APPROACH_SIDE_METERS
                && Math.abs(phoneApproach.bearingDegrees) <= MID_APPROACH_BEARING_DEGREES
                && phonePreview != null
                && Math.abs(phonePreview.headingDiffDegrees) <= MID_APPROACH_HEADING_DEGREES
                && Math.abs(phonePreview.phoneHeightDiffMeters) <= 0.32f;
    }

    private boolean isFinalApproachCorridor(AuditGuidanceEngine.GuidanceResult phoneApproach,
                                            AuditGuidanceEngine.GuidanceResult phonePreview) {
        return phoneApproach != null
                && phoneApproach.distanceMeters <= FINAL_APPROACH_DISTANCE_METERS
                && Math.abs(phoneApproach.sideMeters) <= FINAL_APPROACH_SIDE_METERS
                && Math.abs(phoneApproach.bearingDegrees) <= FINAL_APPROACH_BEARING_DEGREES
                && phonePreview != null
                && Math.abs(phonePreview.headingDiffDegrees) <= FINAL_APPROACH_HEADING_DEGREES
                && Math.abs(phonePreview.phoneHeightDiffMeters) <= FINAL_APPROACH_PHONE_HEIGHT_METERS;
    }

    private boolean shouldForceRouteRecovery() {
        return activeRouteResult != null && activeRouteResult.shouldRecover();
    }

    private boolean isRouteStableEnoughForNearShelf() {
        return activeRouteResult != null
                && !activeRouteResult.shouldRecover()
                && activeRouteResult.confidence >= 0.52f
                && activeRouteResult.corridorErrorMeters <= 0.72f;
    }

    private boolean isSeverePhoneMismatch(AuditGuidanceEngine.GuidanceResult result) {
        return result != null && (Math.abs(result.headingDiffDegrees) > 60f
                || Math.abs(result.phoneHeightDiffMeters) > 0.55f
                || Math.abs(result.sideMeters) > 0.50f
                || result.distanceMeters > 1.10f);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putLong(STATE_SESSION_ID, sessionId);
        outState.putInt(STATE_SHELF_ID, currentShelf != null ? currentShelf.getId() : getIntent().getIntExtra(Constants.EXTRA_SHELF_ID, -1));
        outState.putInt(STATE_STAGE, stage != null ? stage.ordinal() : -1);
    }

    private boolean loadShelf(int shelfId, boolean keepResolvedReference) {
        int previousStoreReferenceId = storeReference != null ? storeReference.getId() : -1;
        currentShelf = shelfRepository.getShelfById(shelfId);
        poseSourceShelf = currentShelf != null ? copyShelfForPoseSource(currentShelf) : null;
        storeReference = currentShelf != null && currentShelf.getStoreReferenceId() > 0
                ? storeReferenceRepository.getStoreReferenceById(currentShelf.getStoreReferenceId())
                : storeReferenceRepository.getPreferredStoreReference();

        if (currentShelf == null) {
            Toast.makeText(this, "Shelf not found.", Toast.LENGTH_LONG).show();
            finish();
            return false;
        }
        if (storeReference == null || TextUtils.isEmpty(storeReference.getCloudAnchorId())) {
            Toast.makeText(this, "This shelf does not have a usable store reference yet.", Toast.LENGTH_LONG).show();
            finish();
            return false;
        }
        if (sessionId <= 0L) {
            sessionId = auditRepository.getOrCreateActiveSession(Constants.DEFAULT_OUTLET_ID, currentShelf.getStoreReferenceId());
        }

        if (!keepResolvedReference || resolvedReferencePose == null || storeReference.getId() != previousStoreReferenceId) {
            clearResolvedReference();
            setStageSilently(Stage.FIND_REFERENCE);
        } else {
            setStageSilently(Stage.FOLLOW_PATH);
        }
        targetShelfPose = null;
        targetCameraPose = null;
        targetPhoneFloorPose = null;
        targetDisplayShelfPose = null;
        targetWalkPose = null;
        relativeShelfFromCameraPose = null;
        poseComputationFailed = false;
        activeRouteResult = null;
        readyHoldSinceMs = 0L;
        readyStickyUntilMs = 0L;
        shelfReadyHoldSinceMs = 0L;
        mismatchHoldSinceMs = 0L;
        routeRecoveryHoldSinceMs = 0L;
        routeStableHoldSinceMs = 0L;
        trackingLostSinceMs = 0L;
        lastStep2DebugLogAtMs = 0L;
        lastStep3DebugLogAtMs = 0L;
        lastCaptureGateLogAtMs = 0L;
        lastRelockSuggestionAtMs = 0L;
        inlineCaptureInProgress = false;
        shelfStartedAtMs = SystemClock.elapsedRealtime();
        flapWindowStartedAtMs = 0L;
        flapCountInWindow = 0;
        recoveryBurstWindowStartedAtMs = 0L;
        recoveryBurstCount = 0;
        trackingLossLoggedThisEpisode = false;
        readyTelemetryRecordedForShelf = false;
        binding.savedViewOverlay.setVisibility(View.GONE);
        if (arStandMarkerHelper != null) arStandMarkerHelper.clear();
        binding.tvShelfName.setText(currentShelf.getShelfName());
        CrashLogRepository.noteBreadcrumb(this, "Audit loadShelf selectedShelfId=" + currentShelf.getId() + " poseShelfId=" + (poseSourceShelf != null ? poseSourceShelf.getId() : -1));
        Log.d(TAG, "Audit loadShelf selectedShelfId=" + currentShelf.getId()
                + ", poseShelfId=" + (poseSourceShelf != null ? poseSourceShelf.getId() : -1)
                + ", storeReferenceId=" + currentShelf.getStoreReferenceId()
                + ", keepResolvedReference=" + keepResolvedReference);
        refreshAreaShelves();
        return true;
    }

    private Shelf copyShelfForPoseSource(Shelf source) {
        if (source == null) {
            return null;
        }
        Shelf copy = new Shelf();
        copy.setId(source.getId());
        copy.setOutletId(source.getOutletId());
        copy.setStoreReferenceId(source.getStoreReferenceId());
        copy.setShelfName(source.getShelfName());
        copy.setImagePath(source.getImagePath());
        copy.setAnchorX(source.getAnchorX());
        copy.setAnchorY(source.getAnchorY());
        copy.setAnchorZ(source.getAnchorZ());
        copy.setRotX(source.getRotX());
        copy.setRotY(source.getRotY());
        copy.setRotZ(source.getRotZ());
        copy.setRotW(source.getRotW());
        copy.setCameraX(source.getCameraX());
        copy.setCameraY(source.getCameraY());
        copy.setCameraZ(source.getCameraZ());
        copy.setCameraRotX(source.getCameraRotX());
        copy.setCameraRotY(source.getCameraRotY());
        copy.setCameraRotZ(source.getCameraRotZ());
        copy.setCameraRotW(source.getCameraRotW());
        copy.setCreatedAt(source.getCreatedAt());
        copy.setCloudAnchorId(source.getCloudAnchorId());
        copy.setCloudAnchorStatus(source.getCloudAnchorStatus());
        copy.setCloudAnchorError(source.getCloudAnchorError());
        copy.setCloudAnchorHostedAt(source.getCloudAnchorHostedAt());
        copy.setCloudAnchorTtlDays(source.getCloudAnchorTtlDays());
        copy.setGuideAnchorBundle(source.getGuideAnchorBundle());
        copy.setAuditStatus(source.getAuditStatus());
        copy.setAuditDoneAt(source.getAuditDoneAt());
        return copy;
    }

    private void clearResolvedReference() {
        resolvedReferencePose = null;
        resolveStarted = false;
        resolveFinished = false;
        resolveStartedAtMs = 0L;
        resolveFailureCount = 0;
        lastResolveMessage = null;
        lastWeakLockToastAtMs = 0L;
        trackingLossLoggedThisEpisode = false;
        referenceResolveHelper.cancelResolveOnly();
        if (resolvedReferenceAnchor != null) {
            try {
                resolvedReferenceAnchor.detach();
            } catch (Exception ignore) {
            }
            resolvedReferenceAnchor = null;
        }
    }

    private void refreshAreaShelves() {
        areaShelves = new ArrayList<>(shelfRepository.getShelvesForStoreReference(currentShelf.getStoreReferenceId()));
        Map<Integer, ShelfAuditStatus> statusMap = sessionId > 0L
                ? auditRepository.getShelfAuditStatusMapForSession(sessionId)
                : auditRepository.getLatestShelfAuditStatusMap();
        for (Shelf shelf : areaShelves) {
            shelf.setAuditStatus("PENDING");
            shelf.setAuditDoneAt(null);
            ShelfAuditStatus status = statusMap.get(shelf.getId());
            if (status != null) {
                shelf.setAuditStatus(status.getStatus());
                shelf.setAuditDoneAt(status.getAuditedAt());
            }
        }
        Collections.sort(areaShelves, new Comparator<Shelf>() {
            @Override
            public int compare(Shelf left, Shelf right) {
                int leftRoute = left.getRouteOrder() > 0 ? left.getRouteOrder() : Integer.MAX_VALUE;
                int rightRoute = right.getRouteOrder() > 0 ? right.getRouteOrder() : Integer.MAX_VALUE;
                if (leftRoute != rightRoute) {
                    return Integer.compare(leftRoute, rightRoute);
                }
                String leftName = left.getShelfName() == null ? "" : left.getShelfName();
                String rightName = right.getShelfName() == null ? "" : right.getShelfName();
                return leftName.compareToIgnoreCase(rightName);
            }
        });
        routeCheckpoints = routeRepository.getCheckpointsForStoreReference(currentShelf.getStoreReferenceId());
        activeRoutePath = new ArrayList<>();
        sessionProgress = AuditSessionProgress.from(sessionId, areaShelves, currentShelf.getId());
    }

    private void restoreResolvedPoseFromIntent(Intent intent) {
        // Disabled intentionally. World poses from an older AR session should not be reused.
    }


    private void handlePrimaryAction() {
        if (stage == Stage.READY) {
            captureAuditInline();
        }
    }

    private void showSavedPhoto() {
        guideImageHelper.showOverlay(storeReference, currentShelf, getGuideMode());
    }

    private boolean isReferenceStage() {
        return resolvedReferencePose == null || stage == Stage.FIND_REFERENCE;
    }

    private int getGuideMode() {
        if (isReferenceStage()) {
            return AuditGuideImageHelper.MODE_REFERENCE;
        }
        if (isPhoneAlignStage()) {
            return AuditGuideImageHelper.MODE_STAND;
        }
        return AuditGuideImageHelper.MODE_SHELF;
    }

    private void bootstrapArIfPossible() {
        if (arFragment != null) {
            return;
        }
        if (arBootstrapInProgress.getAndSet(true)) {
            return;
        }
        try {
            if (!PermissionUtils.hasCameraPermission(this)) {
                binding.tvTrackingStatus.setText("Allow camera permission to start guidance");
                PermissionUtils.requestCameraPermission(this);
                arBootstrapInProgress.set(false);
                return;
            }
            binding.tvTrackingStatus.setText("Checking AR support…");
            arCoreManager.checkAvailability(this, supported -> runOnUiThread(() -> {
                if (!supported) {
                    Toast.makeText(this, "This device does not support ARCore", Toast.LENGTH_LONG).show();
                    finish();
                    arBootstrapInProgress.set(false);
                    return;
                }
                try {
                    boolean installed = arCoreManager.requestInstall(this, userRequestedInstall);
                    if (!installed) {
                        userRequestedInstall = false;
                        binding.tvTrackingStatus.setText("Finish ARCore setup, then reopen guided audit");
                        arBootstrapInProgress.set(false);
                        return;
                    }
                    attachArFragmentIfNeeded();
                    arBootstrapInProgress.set(false);
                } catch (Exception e) {
                    Log.e(TAG, "Audit navigation ARCore init failed", e);
                    ARCoreManager.showArError(this, e);
                    binding.tvTrackingStatus.setText("AR start failed. Check logs.");
                    arBootstrapInProgress.set(false);
                }
            }));
        } catch (Exception e) {
            Log.e(TAG, "Audit navigation bootstrap failed", e);
            binding.tvTrackingStatus.setText("Guided audit startup failed. Check logs.");
            arBootstrapInProgress.set(false);
        }
    }

    private void attachArFragmentIfNeeded() {
        Fragment existing = getSupportFragmentManager().findFragmentByTag(FRAGMENT_TAG_AUDIT_AR);
        if (existing instanceof ArFragment) {
            arFragment = (ArFragment) existing;
            bindFragmentCallbacks(arFragment);
            return;
        }
        getSupportFragmentManager().beginTransaction()
                .replace(binding.arFragmentContainer.getId(), new ArFragment(), FRAGMENT_TAG_AUDIT_AR)
                .commitNowAllowingStateLoss();
        Fragment fragment = getSupportFragmentManager().findFragmentByTag(FRAGMENT_TAG_AUDIT_AR);
        if (fragment instanceof ArFragment) {
            arFragment = (ArFragment) fragment;
            bindFragmentCallbacks(arFragment);
        }
    }

    private void bindFragmentCallbacks(ArFragment fragment) {
        if (fragment == null) {
            return;
        }
        fragment.setOnSessionConfigurationListener(this);
        fragment.setOnViewCreatedListener(this);
    }

    @Override
    public void onAttachFragment(@NonNull FragmentManager fragmentManager, @NonNull Fragment fragment) {
        if (fragment instanceof ArFragment) {
            arFragment = (ArFragment) fragment;
            bindFragmentCallbacks(arFragment);
        }
    }

    @Override
    public void onViewCreated(ArSceneView arSceneView) {
        if (!sceneListenerAdded) {
            sceneListenerAdded = true;
            arSceneView.getScene().addOnUpdateListener(frameTime -> updateGuidance());
        }
    }

    @Override
    public void onSessionConfiguration(Session session, Config config) {
        maybeApplyPreferredCameraConfig(session);
        referenceResolveHelper.enableCloudAnchors(config);
        config.setLightEstimationMode(Config.LightEstimationMode.DISABLED);
        config.setPlaneFindingMode(Config.PlaneFindingMode.DISABLED);
        config.setUpdateMode(Config.UpdateMode.LATEST_CAMERA_IMAGE);
        config.setFocusMode(Config.FocusMode.AUTO);
        if (session != null) {
            config.setDepthMode(Config.DepthMode.DISABLED);
        }
    }

    private void maybeApplyPreferredCameraConfig(Session session) {
        if (session == null || preferredCameraConfigApplied) {
            return;
        }
        try {
            CameraConfigFilter filter = new CameraConfigFilter(session);
            filter.setTargetFps(EnumSet.of(CameraConfig.TargetFps.TARGET_FPS_30));
            List<CameraConfig> configs = session.getSupportedCameraConfigs(filter);
            if (configs != null && !configs.isEmpty()) {
                session.setCameraConfig(configs.get(0));
                preferredCameraConfigApplied = true;
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to apply preferred camera config", e);
        }
    }

    private void updateStaticUi() {
        boolean unresolved = resolvedReferencePose == null;
        navigationUi.renderStaticState(storeReference, unresolved, sessionProgress, currentShelf);
        guideImageHelper.renderInlinePreview(storeReference, currentShelf, unresolved ? AuditGuideImageHelper.MODE_REFERENCE : AuditGuideImageHelper.MODE_SHELF);
    }



    private void updateGuidance() {
        try {
            if (arFragment == null || storeReference == null || poseComputationFailed) {
                if (arStandMarkerHelper != null) arStandMarkerHelper.clear();
                return;
            }
            Frame frame = arFragment.getArSceneView().getArFrame();
            Session session = arFragment.getArSceneView().getSession();
            if (frame == null || session == null) {
                if (arStandMarkerHelper != null) arStandMarkerHelper.clear();
                return;
            }
            if (frame.getCamera().getTrackingState() != TrackingState.TRACKING) {
                renderTrackingRecovery(frame.getCamera().getTrackingFailureReason());
                return;
            }

            if (inlineCaptureInProgress) {
                if (arStandMarkerHelper != null) {
                    arStandMarkerHelper.clear();
                }
                return;
            }

            trackingLostSinceMs = 0L;
            trackingLossLoggedThisEpisode = false;

            if (resolvedReferencePose == null) {
                maybeResolveReferencePoint(session);
                renderReferenceLockUi();
                return;
            }

            if (targetCameraPose == null || targetPhoneFloorPose == null || targetDisplayShelfPose == null || targetWalkPose == null) {
                if (!buildTargetPoses()) {
                    poseComputationFailed = true;
                    navigationUi.renderPoseError();
                    if (arStandMarkerHelper != null) arStandMarkerHelper.clear();
                    return;
                }
            }

            Pose cameraPose = frame.getCamera().getPose();
            if (stage == Stage.FIND_REFERENCE) {
                changeStage(Stage.FOLLOW_PATH, "reference locked");
            }

            Pose walkTargetPose = resolveRouteWalkTarget(cameraPose);
            AuditGuidanceEngine.GuidanceResult walkResult = guidanceEngine.getRoomReturnGuidance(cameraPose, walkTargetPose);
            AuditGuidanceEngine.GuidanceResult phoneApproach = guidanceEngine.getRoomReturnGuidance(cameraPose, targetPhoneFloorPose);
            AuditGuidanceEngine.GuidanceResult phonePreview = guidanceEngine.getStandAlignmentGuidance(cameraPose, targetPhoneFloorPose, targetCameraPose);
            boolean routeRecovering = shouldForceRouteRecovery();
            boolean routeUncertain = isRouteUncertain();
            boolean routeStable = isRouteStableEnoughForNearShelf();
            boolean routeNearTarget = isRouteNearTarget();
            boolean outerCorridor = isOuterApproachCorridor(phoneApproach, phonePreview);
            boolean midCorridor = isMidApproachCorridor(phoneApproach, phonePreview);
            boolean finalCorridor = isFinalApproachCorridor(phoneApproach, phonePreview);
            boolean postureOk = orientationHelper == null || !isPhoneAlignStage() || orientationHelper.isAuditLocalizationPostureReady();
            long now = SystemClock.elapsedRealtime();

            routeRecoveryHoldSinceMs = armHold(routeRecoveryHoldSinceMs, routeRecovering);
            routeStableHoldSinceMs = armHold(routeStableHoldSinceMs, routeStable && !routeRecovering);
            if (!routeRecovering) {
                routeRecoveryHoldSinceMs = 0L;
            }
            if (!(routeStable && !routeRecovering)) {
                routeStableHoldSinceMs = 0L;
            }

            if (walkResult != null && handleWeakResolvedLock(walkResult, isPhoneAlignStage())) {
                return;
            }

            if (phoneApproach != null && now - lastStep2DebugLogAtMs > 900L) {
                lastStep2DebugLogAtMs = now;
                Log.d(TAG, String.format("Audit route live shelfId=%d poseShelfId=%d dist=%.3f bearing=%.1f forward=%.3f side=%.3f previewHeading=%.1f previewPhoneHeight=%.3f routeState=%s routeConfidence=%.2f corridor=%.2f stage=%s",
                        currentShelf != null ? currentShelf.getId() : -1,
                        poseSourceShelf != null ? poseSourceShelf.getId() : -1,
                        phoneApproach.distanceMeters,
                        phoneApproach.bearingDegrees,
                        phoneApproach.forwardMeters,
                        phoneApproach.sideMeters,
                        phonePreview != null ? phonePreview.headingDiffDegrees : 0f,
                        phonePreview != null ? phonePreview.phoneHeightDiffMeters : 0f,
                        activeRouteResult != null ? activeRouteResult.routeState.name() : "NONE",
                        activeRouteResult != null ? activeRouteResult.confidence : 0f,
                        activeRouteResult != null ? activeRouteResult.corridorErrorMeters : 0f,
                        stage.name()));
            }

            if ((stage == Stage.FOLLOW_PATH || stage == Stage.NEAR_SHELF) && routeRecovering && hasHeld(routeRecoveryHoldSinceMs, ROUTE_RECOVERY_DWELL_MS)) {
                changeStage(Stage.RECOVER_ROUTE, String.format("routeRecover conf=%.2f corridor=%.2f",
                        activeRouteResult != null ? activeRouteResult.confidence : 0f,
                        activeRouteResult != null ? activeRouteResult.corridorErrorMeters : 0f));
            }

            if (stage == Stage.RECOVER_ROUTE) {
                navigationUi.renderRouteRecovery("Find the path again", "Go back to the next dot, then keep following the path toward the shelf.", true);
                if (arStandMarkerHelper != null) {
                    float routeDistance = phoneApproach != null ? phoneApproach.distanceMeters : (walkResult != null ? walkResult.distanceMeters : 0f);
                    arStandMarkerHelper.renderRoutePath(arFragment.getArSceneView(), buildActiveRouteWorldPoses(cameraPose), true);
                    arStandMarkerHelper.renderShelfPin(arFragment.getArSceneView(), null, cameraPose, false);
                    arStandMarkerHelper.renderPhoneCue(arFragment.getArSceneView(), targetCameraPose, cameraPose, shouldShowPhoneCue(routeDistance), false);
                }
                binding.btnStartAudit.setVisibility(View.GONE);
                if (!routeRecovering && hasHeld(routeStableHoldSinceMs, ROUTE_STABLE_DWELL_MS)) {
                    changeStage((routeNearTarget && outerCorridor) ? Stage.NEAR_SHELF : Stage.FOLLOW_PATH,
                            String.format("rejoin conf=%.2f corridor=%.2f",
                                    activeRouteResult != null ? activeRouteResult.confidence : 0f,
                                    activeRouteResult != null ? activeRouteResult.corridorErrorMeters : 0f));
                }
                return;
            }

            if (stage == Stage.FOLLOW_PATH) {
                AuditGuidanceEngine.GuidanceResult followResult = walkResult;
                if (phoneApproach != null && phoneApproach.distanceMeters <= 1.55f) {
                    followResult = phoneApproach;
                }
                if (routeUncertain && !routeRecovering) {
                    navigationUi.renderRouteRecovery("Stay with the path", "Keep following the next dot until the shelf marker settles again.", false);
                } else {
                    applyGuidance(followResult, false);
                }
                if (arStandMarkerHelper != null) {
                    float routeDistance = phoneApproach != null ? phoneApproach.distanceMeters : (walkResult != null ? walkResult.distanceMeters : 0f);
                    arStandMarkerHelper.renderRoutePath(arFragment.getArSceneView(), buildActiveRouteWorldPoses(cameraPose), true);
                    arStandMarkerHelper.renderShelfPin(arFragment.getArSceneView(), getLiveShelfCuePose(cameraPose, routeDistance), cameraPose, shouldShowShelfCueInRoute(routeDistance));
                    arStandMarkerHelper.renderStand(arFragment.getArSceneView(), frame, resolveVisualStandPose(), shouldShowStandCircle(routeDistance), false);
                    arStandMarkerHelper.renderPhoneCue(arFragment.getArSceneView(), targetCameraPose, cameraPose, shouldShowPhoneCue(routeDistance), false);
                }
                binding.btnStartAudit.setVisibility(View.GONE);
                boolean directNearShelf = phoneApproach != null
                        && phoneApproach.distanceMeters <= DIRECT_NEAR_SHELF_DISTANCE_METERS
                        && Math.abs(phoneApproach.sideMeters) <= DIRECT_NEAR_SHELF_SIDE_METERS;
                boolean allowNearShelf = !routeRecovering
                        && ((routeNearTarget && (outerCorridor || (phoneApproach != null && phoneApproach.distanceMeters <= 1.45f)))
                        || directNearShelf
                        || (phoneApproach != null && phoneApproach.distanceMeters <= 0.62f));
                shelfReadyHoldSinceMs = armHold(shelfReadyHoldSinceMs, allowNearShelf);
                if (hasHeld(shelfReadyHoldSinceMs, NEAR_SHELF_DWELL_MS)) {
                    changeStage(Stage.NEAR_SHELF, String.format("outer corridor dist=%.3f bearing=%.1f",
                            phoneApproach != null ? phoneApproach.distanceMeters : 0f,
                            phoneApproach != null ? phoneApproach.bearingDegrees : 0f));
                }
                return;
            }

            if (stage == Stage.NEAR_SHELF) {
                applyGuidance(walkResult, false);
                if (arStandMarkerHelper != null) {
                    float routeDistance = phoneApproach != null ? phoneApproach.distanceMeters : 0f;
                    arStandMarkerHelper.renderRoutePath(arFragment.getArSceneView(), buildActiveRouteWorldPoses(cameraPose), shouldShowRoutePathInAlignStage(routeDistance));
                    arStandMarkerHelper.renderShelfPin(arFragment.getArSceneView(), getLiveShelfCuePose(cameraPose, routeDistance), cameraPose, true);
                    arStandMarkerHelper.renderStand(arFragment.getArSceneView(), frame, resolveVisualStandPose(), true, false);
                    boolean showPhoneCue = (routeDistance <= 1.55f || midCorridor || shouldShowPhoneCue(routeDistance)) && !routeRecovering;
                    arStandMarkerHelper.renderPhoneCue(arFragment.getArSceneView(), targetCameraPose, cameraPose, showPhoneCue, false);
                }
                binding.btnStartAudit.setVisibility(View.GONE);

                mismatchHoldSinceMs = armHold(mismatchHoldSinceMs, routeRecovering || !outerCorridor || (phoneApproach != null && phoneApproach.distanceMeters > 1.35f));
                if (hasHeld(mismatchHoldSinceMs, ROUTE_RECOVERY_DWELL_MS)) {
                    changeStage(routeRecovering ? Stage.RECOVER_ROUTE : Stage.FOLLOW_PATH,
                            routeRecovering ? "near shelf lost route" : "left shelf corridor");
                    return;
                }
                mismatchHoldSinceMs = 0L;

                boolean strictReady = shouldEnterReady(phoneApproach, phonePreview);
                readyHoldSinceMs = armHold(readyHoldSinceMs, strictReady && postureOk && !routeRecovering);
                if (hasHeld(readyHoldSinceMs, READY_DWELL_MS + 80L)) {
                    changeStage(Stage.READY, String.format("stand zone reached dist=%.3f heading=%.1f",
                            phonePreview != null ? phonePreview.distanceMeters : 0f,
                            phonePreview != null ? phonePreview.headingDiffDegrees : 0f));
                    return;
                }
                if (!strictReady) {
                    readyHoldSinceMs = 0L;
                }
                boolean enterMatchPhone = shouldEnterMatchPhone(phoneApproach, phonePreview, routeStable);
                shelfReadyHoldSinceMs = armHold(shelfReadyHoldSinceMs, enterMatchPhone && postureOk && !routeRecovering);
                if (hasHeld(shelfReadyHoldSinceMs, MATCH_PHONE_DWELL_MS + 120L)) {
                    changeStage(Stage.MATCH_PHONE, String.format("mid corridor dist=%.3f heading=%.1f",
                            phonePreview != null ? phonePreview.distanceMeters : 0f,
                            phonePreview != null ? phonePreview.headingDiffDegrees : 0f));
                    return;
                }
                return;
            }

            AuditGuidanceEngine.GuidanceResult exactResult = phonePreview;
            if (exactResult == null) {
                exactResult = guidanceEngine.getStandAlignmentGuidance(cameraPose, targetPhoneFloorPose, targetCameraPose);
            }
            applyGuidance(exactResult, true);
            if (arStandMarkerHelper != null) {
                float nearDistance = exactResult != null ? exactResult.distanceMeters : 0f;
                arStandMarkerHelper.renderRoutePath(arFragment.getArSceneView(), buildActiveRouteWorldPoses(cameraPose), false);
                arStandMarkerHelper.renderShelfPin(arFragment.getArSceneView(), targetDisplayShelfPose, cameraPose, nearDistance < 1.10f);
                arStandMarkerHelper.renderStand(arFragment.getArSceneView(), frame, resolveVisualStandPose(), true, false);
                boolean showPhoneCue = shouldShowPhoneCue(nearDistance) || nearDistance <= 1.10f || stage == Stage.READY;
                arStandMarkerHelper.renderPhoneCue(arFragment.getArSceneView(), targetCameraPose, cameraPose, showPhoneCue, exactResult != null && exactResult.inTightWindow);
            }

            if (!postureOk) {
                readyHoldSinceMs = 0L;
                navigationUi.renderTrackingRecovery("Hold the phone straight", orientationHelper.getAuditPostureGuidance(), true);
                return;
            }

            boolean captureAllowed = isCaptureZoneReached(phoneApproach, exactResult);
            if (captureAllowed) {
                readyStickyUntilMs = Math.max(readyStickyUntilMs, now + READY_STICKY_MS);
            }

            if (exactResult != null && now - lastStep3DebugLogAtMs > 900L) {
                lastStep3DebugLogAtMs = now;
                Log.d(TAG, String.format("Audit phone live shelfId=%d poseShelfId=%d dist=%.3f heading=%.1f phoneHeight=%.3f forward=%.3f side=%.3f close=%s tight=%s stage=%s",
                        currentShelf != null ? currentShelf.getId() : -1,
                        poseSourceShelf != null ? poseSourceShelf.getId() : -1,
                        exactResult.distanceMeters,
                        exactResult.headingDiffDegrees,
                        exactResult.phoneHeightDiffMeters,
                        exactResult.forwardMeters,
                        exactResult.sideMeters,
                        String.valueOf(exactResult.closeEnoughToAudit),
                        String.valueOf(exactResult.inTightWindow),
                        stage.name()));
            }

            if (now - lastCaptureGateLogAtMs > 850L) {
                lastCaptureGateLogAtMs = now;
                Log.d(TAG, String.format(Locale.US,
                        "Audit capture gate stage=%s routeDist=%.3f exactDist=%.3f side=%.3f bearing=%.1f heading=%.1f h=%.3f outer=%s mid=%s final=%s capture=%s",
                        stage.name(),
                        phoneApproach != null ? phoneApproach.distanceMeters : -1f,
                        exactResult != null ? exactResult.distanceMeters : -1f,
                        exactResult != null ? exactResult.sideMeters : 0f,
                        phoneApproach != null ? phoneApproach.bearingDegrees : 0f,
                        exactResult != null ? exactResult.headingDiffDegrees : 0f,
                        exactResult != null ? exactResult.phoneHeightDiffMeters : 0f,
                        String.valueOf(outerCorridor),
                        String.valueOf(midCorridor),
                        String.valueOf(finalCorridor),
                        String.valueOf(captureAllowed)));
            }

            if (stage == Stage.MATCH_PHONE) {
                mismatchHoldSinceMs = armHold(mismatchHoldSinceMs,
                        routeRecovering || isSeverePhoneMismatch(exactResult));
                if (hasHeld(mismatchHoldSinceMs, ROUTE_RECOVERY_DWELL_MS)) {
                    changeStage(routeRecovering ? Stage.RECOVER_ROUTE : Stage.NEAR_SHELF,
                            routeRecovering ? "match phone lost route" : "phone pose drifted");
                    return;
                }
                mismatchHoldSinceMs = 0L;
                readyHoldSinceMs = armHold(readyHoldSinceMs, shouldHoldReadyState(phoneApproach, exactResult) || now <= readyStickyUntilMs || isStableReadyFallback(phoneApproach, exactResult));
                if (hasHeld(readyHoldSinceMs, READY_DWELL_MS)) {
                    changeStage(Stage.READY, String.format("tight pose dist=%.3f heading=%.1f phoneHeight=%.3f",
                            exactResult != null ? exactResult.distanceMeters : 0f,
                            exactResult != null ? exactResult.headingDiffDegrees : 0f,
                            exactResult != null ? exactResult.phoneHeightDiffMeters : 0f));
                }
                return;
            }

            if (stage == Stage.READY) {
                boolean keepReady = shouldHoldReadyState(phoneApproach, exactResult) || now <= readyStickyUntilMs || isStableReadyFallback(phoneApproach, exactResult);
                if (!keepReady) {
                    mismatchHoldSinceMs = armHold(mismatchHoldSinceMs, true);
                    if (hasHeld(mismatchHoldSinceMs, MATCH_PHONE_DWELL_MS + 520L)) {
                        changeStage(Stage.MATCH_PHONE, "ready pose relaxed");
                    }
                } else {
                    mismatchHoldSinceMs = 0L;
                }
                renderReadyState();
                if (arStandMarkerHelper != null) {
                    float finalDistance = exactResult != null ? exactResult.distanceMeters : 0f;
                    arStandMarkerHelper.renderRoutePath(arFragment.getArSceneView(), buildActiveRouteWorldPoses(cameraPose), false);
                    arStandMarkerHelper.renderShelfPin(arFragment.getArSceneView(), targetDisplayShelfPose, cameraPose, finalDistance < 1.20f);
                    arStandMarkerHelper.renderStand(arFragment.getArSceneView(), frame, resolveVisualStandPose(), true, false);
                    arStandMarkerHelper.renderPhoneCue(arFragment.getArSceneView(), targetCameraPose, cameraPose, true, exactResult != null && exactResult.inTightWindow);
                }
                return;
            }

            binding.btnStartAudit.setVisibility(View.GONE);
        } catch (Exception e) {
            CrashLogRepository.recordHandledException(this, "Audit updateGuidance", e);
            Log.e(TAG, "Audit navigation updateGuidance failed", e);
            binding.tvTrackingStatus.setText("Guidance failed. Check logs.");
        }
    }

    private void renderTrackingRecovery(TrackingFailureReason reason) {
        if (trackingLostSinceMs == 0L) {
            trackingLostSinceMs = SystemClock.elapsedRealtime();
            trackingLossLoggedThisEpisode = false;
        }
        long lostDurationMs = SystemClock.elapsedRealtime() - trackingLostSinceMs;
        if (!trackingLossLoggedThisEpisode && lostDurationMs >= 2500L && telemetryRepository != null && currentShelf != null) {
            trackingLossLoggedThisEpisode = true;
            telemetryRepository.record("audit_tracking_lost_prolonged",
                    currentShelf.getStoreReferenceId(),
                    currentShelf.getId(),
                    sessionId,
                    (float) lostDurationMs,
                    reason != null ? reason.name() : "UNKNOWN");
        }
        if (arStandMarkerHelper != null) arStandMarkerHelper.clear();
        AuditRecoveryText.RecoveryCopy copy = AuditRecoveryText.forTracking(reason, isPhoneAlignStage());
        navigationUi.renderTrackingRecovery(copy.title, copy.detail, isPhoneAlignStage());
        guideImageHelper.renderInlinePreview(storeReference, currentShelf, getGuideMode());
    }

    private void renderReferenceLockUi() {
        AuditRecoveryText.RecoveryCopy copy = AuditRecoveryText.forReferenceLock(
                storeReference,
                resolveStarted,
                resolveStartedAtMs,
                resolveFailureCount,
                lastResolveMessage,
                SystemClock.elapsedRealtime()
        );
        navigationUi.renderReferenceLock(storeReference, resolveStarted, copy.title, copy.detail);
        guideImageHelper.renderInlinePreview(storeReference, currentShelf, AuditGuideImageHelper.MODE_REFERENCE);
        if (arStandMarkerHelper != null) arStandMarkerHelper.clear();
    }

    private void renderReadyState() {
        navigationUi.renderReadyState(sessionProgress, currentShelf);
        guideImageHelper.renderInlinePreview(storeReference, currentShelf, AuditGuideImageHelper.MODE_STAND);
    }

    private boolean buildTargetPoses() {
        Shelf targetShelfSource = poseSourceShelf != null ? poseSourceShelf : currentShelf;
        targetShelfPose = AuditPoseHelper.buildAbsoluteShelfPose(resolvedReferencePose, targetShelfSource);
        targetCameraPose = AuditPoseHelper.buildAbsoluteCameraPose(resolvedReferencePose, targetShelfSource);
        targetPhoneFloorPose = AuditPoseHelper.buildAbsolutePhoneFloorPose(resolvedReferencePose, targetShelfSource);
        targetDisplayShelfPose = AuditPoseHelper.buildDisplayShelfPose(resolvedReferencePose, targetShelfSource);
        relativeShelfFromCameraPose = AuditPoseHelper.buildRelativeCameraToShelfPose(targetShelfSource);
        if (targetDisplayShelfPose == null && targetCameraPose != null) {
            targetDisplayShelfPose = targetCameraPose.compose(Pose.makeTranslation(0f, 0f, -1.0f));
        }
        targetWalkPose = AuditPoseHelper.buildWalkApproachPose(targetPhoneFloorPose, targetDisplayShelfPose);
        if (targetPhoneFloorPose != null && targetWalkPose != null) {
            Log.d(TAG, String.format("Audit buildTargetPoses selectedShelfId=%d poseShelfId=%d phoneFloor=(%.3f, %.3f, %.3f) camera=(%.3f, %.3f, %.3f) walk=(%.3f, %.3f, %.3f)",
                    currentShelf != null ? currentShelf.getId() : -1,
                    targetShelfSource != null ? targetShelfSource.getId() : -1,
                    targetPhoneFloorPose.tx(), targetPhoneFloorPose.ty(), targetPhoneFloorPose.tz(),
                    targetCameraPose != null ? targetCameraPose.tx() : 0f,
                    targetCameraPose != null ? targetCameraPose.ty() : 0f,
                    targetCameraPose != null ? targetCameraPose.tz() : 0f,
                    targetWalkPose.tx(), targetWalkPose.ty(), targetWalkPose.tz()));
        }
        return targetCameraPose != null && targetPhoneFloorPose != null && targetWalkPose != null;
    }

    private void applyGuidance(AuditGuidanceEngine.GuidanceResult result, boolean exact) {
        String recoveryTitle = exact ? "Match the saved phone pose" : "Follow the shelf pin to the saved phone frame";
        String recoveryDetail = TextUtils.isEmpty(result.secondaryHint) ? result.detail : result.secondaryHint;
        boolean locked = resolvedReferencePose != null;
        boolean shelfReached = stage == Stage.MATCH_PHONE || stage == Stage.READY;
        boolean phoneMatched = stage == Stage.READY;
        navigationUi.renderGuidance(result, exact, storeReference, currentShelf, recoveryTitle, recoveryDetail, locked, shelfReached, phoneMatched);
        guideImageHelper.renderInlinePreview(storeReference, currentShelf, exact ? AuditGuideImageHelper.MODE_STAND : AuditGuideImageHelper.MODE_SHELF);
    }

    private boolean handleWeakResolvedLock(AuditGuidanceEngine.GuidanceResult result, boolean exact) {
        if (result == null || resolvedReferencePose == null) {
            return false;
        }
        if (Float.isNaN(result.distanceMeters) || Float.isInfinite(result.distanceMeters)) {
            clearResolvedReference();
            targetShelfPose = null;
            targetCameraPose = null;
            targetPhoneFloorPose = null;
            targetDisplayShelfPose = null;
            targetWalkPose = null;
            relativeShelfFromCameraPose = null;
            setStageSilently(Stage.FIND_REFERENCE);
            renderReferenceLockUi();
            if (arStandMarkerHelper != null) arStandMarkerHelper.clear();
            return true;
        }

        float softLimit = exact ? 8.0f : 12.0f;
        if (result.distanceMeters <= softLimit) {
            return false;
        }

        long now = SystemClock.elapsedRealtime();
        if (now - lastWeakLockToastAtMs > 4000L) {
            lastWeakLockToastAtMs = now;
            Toast.makeText(this,
                    "Store reference lock may be off. Re-check the saved reference photo only if guidance becomes clearly wrong.",
                    Toast.LENGTH_LONG).show();
        }
        return false;
    }

    private void maybeResolveReferencePoint(Session session) {
        if (resolveStarted || resolveFinished || storeReference == null || TextUtils.isEmpty(storeReference.getCloudAnchorId())) {
            return;
        }
        resolveStarted = true;
        resolveStartedAtMs = SystemClock.elapsedRealtime();
        referenceResolveHelper.resolveAnchor(session, storeReference.getCloudAnchorId(), new CloudAnchorHelper.ResolveListener() {
            @Override
            public void onResolveSuccess(Anchor anchor) {
                runOnUiThread(() -> {
                    if (resolvedReferenceAnchor != null && resolvedReferenceAnchor != anchor) {
                        try {
                            resolvedReferenceAnchor.detach();
                        } catch (Exception ignore) {
                        }
                    }
                    resolvedReferenceAnchor = anchor;
                    resolvedReferencePose = anchor.getPose();
                    lastWeakLockToastAtMs = 0L;
                    resolveFinished = true;
                    resolveStarted = true;
                    if (telemetryRepository != null) {
                        telemetryRepository.record("audit_reference_lock_success",
                                currentShelf != null ? currentShelf.getStoreReferenceId() : 0,
                                currentShelf != null ? currentShelf.getId() : 0L,
                                sessionId,
                                (float) Math.max(0L, SystemClock.elapsedRealtime() - resolveStartedAtMs),
                                "resolved for audit");
                    }
                    setStageSilently(Stage.FOLLOW_PATH);
                    updateStaticUi();
                });
            }

            @Override
            public void onResolveFailure(Anchor.CloudAnchorState state, String message) {
                runOnUiThread(() -> {
                    resolveFinished = false;
                    resolveStarted = false;
                    resolveFailureCount++;
                    lastResolveMessage = message == null ? String.valueOf(state) : message;
                    if (telemetryRepository != null) {
                        telemetryRepository.record("audit_reference_lock_failure",
                                currentShelf != null ? currentShelf.getStoreReferenceId() : 0,
                                currentShelf != null ? currentShelf.getId() : 0L,
                                sessionId,
                                state != null ? state.name() : lastResolveMessage);
                    }
                    if (resolveFailureCount >= 2 && telemetryRepository != null && currentShelf != null) {
                        telemetryRepository.record("audit_reference_lock_retry_needed",
                                currentShelf.getStoreReferenceId(),
                                currentShelf.getId(),
                                sessionId,
                                resolveFailureCount,
                                state != null ? state.name() : lastResolveMessage);
                    }
                    Toast.makeText(AuditNavigationActivity.this,
                            resolveFailureCount >= 2
                                    ? "Store reference lock failed again. Step closer to the saved entrance view and try matching the photo more tightly."
                                    : "Store reference lock failed. Match the saved store reference again.",
                            Toast.LENGTH_LONG).show();
                    renderReferenceLockUi();
                });
            }
        });
    }


    private boolean openReviewIfAlreadyAudited() {
        if (currentShelf == null) {
            return false;
        }
        ShelfAuditStatus latestStatus = sessionId > 0L
                ? auditRepository.getLatestShelfAuditStatusForSession(sessionId, currentShelf.getId())
                : auditRepository.getLatestShelfAuditStatus(currentShelf.getId());
        if (latestStatus == null || !latestStatus.isDone()) {
            return false;
        }
        openAuditReviewAndFinish();
        return true;
    }

    private void openAuditReviewAndFinish() {
        Intent intent = new Intent(this, AuditReviewActivity.class);
        intent.putExtra(Constants.EXTRA_SHELF_ID, currentShelf.getId());
        startActivity(intent);
        finish();
    }


    private void captureAuditInline() {
        if (inlineCaptureInProgress || currentShelf == null || arFragment == null) {
            return;
        }
        ShelfAuditStatus latestStatus = sessionId > 0L
                ? auditRepository.getLatestShelfAuditStatusForSession(sessionId, currentShelf.getId())
                : auditRepository.getLatestShelfAuditStatus(currentShelf.getId());
        if (latestStatus != null && latestStatus.isDone()) {
            openAuditReviewAndFinish();
            return;
        }
        if (sessionId <= 0L) {
            sessionId = auditRepository.getOrCreateActiveSession(Constants.DEFAULT_OUTLET_ID, currentShelf.getStoreReferenceId());
        }
        ArSceneView sceneView = arFragment.getArSceneView();
        if (sceneView == null || sceneView.getWidth() <= 0 || sceneView.getHeight() <= 0) {
            Toast.makeText(this, "Camera view not ready yet.", Toast.LENGTH_SHORT).show();
            return;
        }

        inlineCaptureInProgress = true;
        binding.btnStartAudit.setEnabled(false);
        hideLiveGuidesForCapture();

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            Bitmap bitmap = Bitmap.createBitmap(sceneView.getWidth(), sceneView.getHeight(), Bitmap.Config.ARGB_8888);
            PixelCopy.request(sceneView, bitmap, result -> {
                inlineCaptureInProgress = false;
                binding.btnStartAudit.setEnabled(true);
                if (result != PixelCopy.SUCCESS) {
                    if (!bitmap.isRecycled()) {
                        bitmap.recycle();
                    }
                    Toast.makeText(this, "Could not capture audit image.", Toast.LENGTH_LONG).show();
                    return;
                }
                showAuditCaptureReview(bitmap);
            }, new Handler(Looper.getMainLooper()));
        }, 180L);
    }

    /** Hide AR guides briefly so the saved audit image stays clean. */
    private void hideLiveGuidesForCapture() {
        if (arStandMarkerHelper != null) {
            arStandMarkerHelper.clear();
        }
    }

    private void showAuditCaptureReview(Bitmap bitmap) {
        if (bitmap == null) {
            return;
        }
        android.widget.ImageView preview = new android.widget.ImageView(this);
        preview.setImageBitmap(bitmap);
        preview.setAdjustViewBounds(true);
        preview.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);

        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        android.widget.LinearLayout content = new android.widget.LinearLayout(this);
        content.setOrientation(android.widget.LinearLayout.VERTICAL);
        content.setPadding(pad, pad, pad, 0);

        android.widget.TextView hint = new android.widget.TextView(this);
        hint.setText("Check the photo before you save it.");
        hint.setTextColor(android.graphics.Color.parseColor("#CBD5E1"));
        hint.setTextSize(14f);
        content.addView(hint);
        content.addView(preview);

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Use this audit photo?")
                .setView(content)
                .setNegativeButton("Retake", (dialog, which) -> {
                    if (!bitmap.isRecycled()) {
                        bitmap.recycle();
                    }
                })
                .setPositiveButton("Use photo", (dialog, which) -> saveApprovedAuditCapture(bitmap))
                .setOnCancelListener(dialog -> {
                    if (!bitmap.isRecycled()) {
                        bitmap.recycle();
                    }
                })
                .show();
    }

    private void saveApprovedAuditCapture(Bitmap bitmap) {
        try {
            String path = ImageUtils.saveBitmapToFile(this, bitmap);
            long shelfAuditId = auditRepository.createOrReuseShelfAudit(sessionId, currentShelf.getId());
            List<String> paths = new ArrayList<>();
            paths.add(path);
            auditRepository.replaceAuditImages(shelfAuditId, paths);
            auditRepository.completeShelfAudit(shelfAuditId, "Captured from guided audit");
            CrashLogRepository.noteBreadcrumb(this, "Audit inline capture success shelf=" + currentShelf.getId() + " session=" + sessionId);
            if (telemetryRepository != null) {
                telemetryRepository.record("audit_capture_inline",
                        currentShelf.getStoreReferenceId(),
                        currentShelf.getId(),
                        sessionId,
                        "inline");
            }
            openNextPendingShelfOrFinish();
        } catch (Exception e) {
            CrashLogRepository.recordHandledException(this, "Audit inline capture", e);
            Log.e(TAG, "Inline audit capture failed", e);
            Toast.makeText(this, "Audit capture failed. Check logs.", Toast.LENGTH_LONG).show();
        } finally {
            if (bitmap != null && !bitmap.isRecycled()) {
                bitmap.recycle();
            }
        }
    }

    private Pose resolveRouteWalkTarget(Pose cameraPose) {
        if (cameraPose == null) {
            return targetWalkPose != null ? targetWalkPose : targetPhoneFloorPose;
        }
        List<RouteCheckpoint> path = computeActiveRoutePath(cameraPose);
        if (path.isEmpty()) {
            return targetWalkPose != null ? targetWalkPose : targetPhoneFloorPose;
        }
        if (path.size() == 1) {
            return targetWalkPose != null ? targetWalkPose : toAbsoluteCheckpointPose(path.get(0));
        }
        int nextIndex = 1;
        while (nextIndex < path.size() - 1) {
            Pose nextPose = toAbsoluteCheckpointPose(path.get(nextIndex));
            AuditGuidanceEngine.GuidanceResult nextGuide = guidanceEngine.getRoomReturnGuidance(cameraPose, nextPose);
            if (nextGuide != null && nextGuide.distanceMeters < 0.90f) {
                nextIndex++;
            } else {
                break;
            }
        }
        if (nextIndex >= path.size()) {
            return targetWalkPose != null ? targetWalkPose : targetPhoneFloorPose;
        }
        RouteCheckpoint nextCheckpoint = path.get(nextIndex);
        if (nextIndex == path.size() - 1 && targetWalkPose != null) {
            return targetWalkPose;
        }
        return toAbsoluteCheckpointPose(nextCheckpoint);
    }

    private Pose resolveVisualStandPose() {
        if (targetCameraPose == null) {
            return targetPhoneFloorPose;
        }
        if (targetPhoneFloorPose == null) {
            return targetCameraPose;
        }
        return new Pose(
                new float[]{targetCameraPose.tx(), targetPhoneFloorPose.ty(), targetCameraPose.tz()},
                targetCameraPose.getRotationQuaternion()
        );
    }

    private List<RouteCheckpoint> computeActiveRoutePath(Pose absoluteCameraPose) {
        activeRouteResult = null;
        if (routeRepository == null || resolvedReferencePose == null || currentShelf == null || absoluteCameraPose == null
                || currentShelf.getNearestCheckpointId() <= 0L || routeCheckpoints == null || routeCheckpoints.isEmpty()) {
            activeRoutePath = new ArrayList<>();
            return activeRoutePath;
        }
        RouteCheckpoint targetCheckpoint = getCheckpointById(currentShelf.getNearestCheckpointId());
        if (targetCheckpoint == null && currentShelf.getRouteOrder() > 0) {
            targetCheckpoint = getCheckpointBySequence(currentShelf.getRouteOrder());
        }
        if (targetCheckpoint == null && routeCheckpoints != null && !routeCheckpoints.isEmpty()) {
            targetCheckpoint = routeCheckpoints.get(routeCheckpoints.size() - 1);
        }
        if (targetCheckpoint == null) {
            activeRoutePath = new ArrayList<>();
            return activeRoutePath;
        }
        Pose relativeCameraPose = resolvedReferencePose.inverse().compose(absoluteCameraPose);
        activeRouteResult = routeRepository.resolveBestPathForStoreReference(
                currentShelf.getStoreReferenceId(),
                routeCheckpoints,
                relativeCameraPose.tx(),
                relativeCameraPose.ty(),
                relativeCameraPose.tz(),
                targetCheckpoint.getId());
        activeRoutePath = activeRouteResult == null ? new ArrayList<RouteCheckpoint>() : new ArrayList<>(activeRouteResult.path);
        if (activeRoutePath.isEmpty()) {
            activeRoutePath.add(targetCheckpoint);
        }
        return activeRoutePath;
    }

    private List<Pose> buildActiveRouteWorldPoses(Pose absoluteCameraPose) {
        List<Pose> poses = new ArrayList<>();
        if (absoluteCameraPose == null) {
            return poses;
        }
        List<RouteCheckpoint> path = computeActiveRoutePath(absoluteCameraPose);
        float floorY = targetPhoneFloorPose != null ? targetPhoneFloorPose.ty() : (absoluteCameraPose.ty() - 1.20f);

        Pose cameraFloorPose = Pose.makeTranslation(absoluteCameraPose.tx(), floorY, absoluteCameraPose.tz());
        poses.add(cameraFloorPose);

        Pose firstTarget = null;
        if (path.size() > 1) {
            firstTarget = toAbsoluteCheckpointPose(path.get(1));
        } else if (path.size() == 1) {
            firstTarget = toAbsoluteCheckpointPose(path.get(0));
        }
        if (firstTarget == null) {
            firstTarget = targetWalkPose != null ? targetWalkPose : targetPhoneFloorPose;
        }

        appendInterpolatedFloorPoses(poses, projectPoseToFloor(firstTarget, floorY), PATH_SPACING_METERS);

        int limit = Math.min(path.size(), 12);
        int startIndex = (path.size() > 1) ? 1 : 0;
        for (int i = startIndex; i < limit; i++) {
            Pose checkpointPose = toAbsoluteCheckpointPose(path.get(i));
            appendInterpolatedFloorPoses(poses, projectPoseToFloor(checkpointPose, floorY), PATH_SPACING_METERS);
        }
        appendInterpolatedFloorPoses(poses, projectPoseToFloor(targetWalkPose, floorY), PATH_SPACING_METERS);
        appendInterpolatedFloorPoses(poses, projectPoseToFloor(targetPhoneFloorPose, floorY), PATH_SPACING_METERS * 0.90f);
        return poses;
    }


    private void appendInterpolatedFloorPoses(List<Pose> poses, Pose targetPose, float spacingMeters) {
        if (poses == null || poses.isEmpty() || targetPose == null) {
            return;
        }
        Pose start = poses.get(poses.size() - 1);
        float dx = targetPose.tx() - start.tx();
        float dz = targetPose.tz() - start.tz();
        float distance = (float) Math.sqrt((dx * dx) + (dz * dz));
        if (distance < 0.001f) {
            appendPoseIfSeparated(poses, targetPose, 0.10f);
            return;
        }
        int steps = Math.max(1, Math.min(16, (int) Math.ceil(distance / Math.max(0.18f, spacingMeters))));
        for (int i = 1; i <= steps; i++) {
            float t = i / (float) steps;
            Pose sample = Pose.makeTranslation(
                    start.tx() + (dx * t),
                    targetPose.ty(),
                    start.tz() + (dz * t)
            );
            appendPoseIfSeparated(poses, sample, 0.12f);
        }
    }

    private Pose projectPoseToFloor(Pose pose, float floorY) {
        if (pose == null) {
            return null;
        }
        return Pose.makeTranslation(pose.tx(), floorY, pose.tz());
    }

    private Pose buildProjectedRouteStartPose(Pose cameraPose, Pose nextPose, float floorY) {
        if (cameraPose == null || nextPose == null) {
            return null;
        }
        float dx = nextPose.tx() - cameraPose.tx();
        float dz = nextPose.tz() - cameraPose.tz();
        float len = (float) Math.sqrt((dx * dx) + (dz * dz));
        if (len < 0.001f) {
            return Pose.makeTranslation(cameraPose.tx(), floorY, cameraPose.tz());
        }
        float step = Math.min(0.42f, Math.max(0.18f, len * 0.30f));
        float sx = cameraPose.tx() + ((dx / len) * step);
        float sz = cameraPose.tz() + ((dz / len) * step);
        return Pose.makeTranslation(sx, floorY, sz);
    }

    private void appendPoseIfSeparated(List<Pose> poses, Pose pose, float minDistance) {
        if (poses == null || pose == null) {
            return;
        }
        if (poses.isEmpty()) {
            poses.add(pose);
            return;
        }
        Pose last = poses.get(poses.size() - 1);
        if (distanceBetween(last, pose) >= minDistance) {
            poses.add(pose);
        }
    }

    private boolean shouldEnterMatchPhone(AuditGuidanceEngine.GuidanceResult phoneApproach,
                                          AuditGuidanceEngine.GuidanceResult exactResult,
                                          boolean routeStable) {
        if (phoneApproach == null || exactResult == null) {
            return false;
        }
        if (routeStable && exactResult.distanceMeters <= 1.05f
                && Math.abs(exactResult.sideMeters) <= 0.34f
                && Math.abs(exactResult.headingDiffDegrees) <= 55f
                && Math.abs(exactResult.phoneHeightDiffMeters) <= 0.38f) {
            return true;
        }
        return phoneApproach.distanceMeters <= 0.82f
                && Math.abs(phoneApproach.sideMeters) <= 0.34f
                && Math.abs(phoneApproach.bearingDegrees) <= 24f
                && Math.abs(exactResult.headingDiffDegrees) <= 36f
                && Math.abs(exactResult.phoneHeightDiffMeters) <= 0.24f;
    }

    private boolean shouldEnterReady(AuditGuidanceEngine.GuidanceResult phoneApproach,
                                     AuditGuidanceEngine.GuidanceResult exactResult) {
        return isStrictReadyPose(phoneApproach, exactResult)
                || isStableReadyFallback(phoneApproach, exactResult)
                || isVeryCloseToStand(exactResult);
    }

    private boolean isStrictReadyPose(AuditGuidanceEngine.GuidanceResult phoneApproach,
                                      AuditGuidanceEngine.GuidanceResult exactResult) {
        if (phoneApproach == null || exactResult == null) {
            return false;
        }
        return exactResult.distanceMeters <= 0.30f
                && Math.abs(exactResult.sideMeters) <= 0.12f
                && Math.abs(phoneApproach.bearingDegrees) <= 14f
                && Math.abs(exactResult.headingDiffDegrees) <= 14f
                && Math.abs(exactResult.phoneHeightDiffMeters) <= 0.14f;
    }

    private boolean isStableReadyFallback(AuditGuidanceEngine.GuidanceResult phoneApproach,
                                          AuditGuidanceEngine.GuidanceResult exactResult) {
        if (phoneApproach == null || exactResult == null) {
            return false;
        }
        return exactResult.distanceMeters <= 0.20f
                && Math.abs(exactResult.sideMeters) <= 0.10f
                && Math.abs(exactResult.headingDiffDegrees) <= 8f
                && Math.abs(exactResult.phoneHeightDiffMeters) <= 0.10f;
    }

    private RouteCheckpoint getCheckpointById(long checkpointId) {
        if (routeCheckpoints == null) {
            return null;
        }
        for (RouteCheckpoint checkpoint : routeCheckpoints) {
            if (checkpoint.getId() == checkpointId) {
                return checkpoint;
            }
        }
        return null;
    }

    private RouteCheckpoint getCheckpointBySequence(int sequence) {
        if (routeCheckpoints == null) {
            return null;
        }
        for (RouteCheckpoint checkpoint : routeCheckpoints) {
            if (checkpoint.getSequence() == sequence) {
                return checkpoint;
            }
        }
        return null;
    }

    private Pose toAbsoluteCheckpointPose(RouteCheckpoint checkpoint) {
        if (checkpoint == null || resolvedReferencePose == null) {
            return targetWalkPose != null ? targetWalkPose : targetPhoneFloorPose;
        }
        return resolvedReferencePose.compose(Pose.makeTranslation(
                checkpoint.getAnchorX(), checkpoint.getAnchorY(), checkpoint.getAnchorZ()));
    }

    private float distanceBetween(Pose a, Pose b) {
        if (a == null || b == null) {
            return Float.MAX_VALUE;
        }
        float dx = a.tx() - b.tx();
        float dy = a.ty() - b.ty();
        float dz = a.tz() - b.tz();
        return (float) Math.sqrt((dx * dx) + (dy * dy) + (dz * dz));
    }

    private Pose getLiveShelfCuePose(Pose cameraPose, float approachDistanceMeters) {
        if (!isRouteHighConfidence() || isRouteRecovering() || approachDistanceMeters > 1.25f) {
            return targetDisplayShelfPose;
        }
        Pose live = AuditPoseHelper.buildLiveDisplayShelfPose(cameraPose, targetDisplayShelfPose, relativeShelfFromCameraPose, approachDistanceMeters);
        return live != null ? live : targetDisplayShelfPose;
    }

    private boolean isRouteHighConfidence() {
        return activeRouteResult != null && activeRouteResult.isHighConfidence();
    }

    private boolean isRouteRecovering() {
        return activeRouteResult != null && activeRouteResult.shouldRecover();
    }

    private boolean isRouteUncertain() {
        return activeRouteResult != null && activeRouteResult.isUncertain();
    }

    private boolean isRouteNearTarget() {
        return activeRouteResult != null && activeRouteResult.isNearTarget();
    }

    private boolean shouldShowShelfCueInRoute(float distanceMeters) {
        if (distanceMeters < 0.95f) {
            return isRouteNearTarget() && !isRouteRecovering();
        }
        return isRouteHighConfidence() && !isRouteRecovering() && !isRouteUncertain();
    }

    private boolean shouldShowPhoneCue(float distanceMeters) {
        return distanceMeters < 2.05f && !isRouteRecovering();
    }

    private boolean shouldShowStandCircle(float distanceMeters) {
        return distanceMeters <= 4.20f && !isRouteRecovering();
    }

    private boolean shouldShowRoutePathInAlignStage(float distanceMeters) {
        return !isRouteRecovering() && distanceMeters >= 0.18f;
    }

    private boolean isVeryCloseToStand(AuditGuidanceEngine.GuidanceResult exactResult) {
        return exactResult != null
                && exactResult.distanceMeters <= 0.22f
                && Math.abs(exactResult.sideMeters) <= 0.12f
                && Math.abs(exactResult.headingDiffDegrees) <= 14f
                && Math.abs(exactResult.phoneHeightDiffMeters) <= 0.12f;
    }

    private boolean isCaptureZoneReached(AuditGuidanceEngine.GuidanceResult phoneApproach,
                                         AuditGuidanceEngine.GuidanceResult exactResult) {
        if (exactResult != null && exactResult.inTightWindow) {
            return true;
        }
        if (isVeryCloseToStand(exactResult)) {
            return true;
        }
        if (phoneApproach == null || exactResult == null) {
            return false;
        }
        if (phoneApproach.distanceMeters <= 0.24f
                && Math.abs(exactResult.sideMeters) <= 0.12f
                && Math.abs(exactResult.headingDiffDegrees) <= 14f
                && Math.abs(exactResult.phoneHeightDiffMeters) <= 0.14f) {
            return true;
        }
        return phoneApproach.distanceMeters <= 0.42f
                && Math.abs(phoneApproach.sideMeters) <= 0.14f
                && Math.abs(phoneApproach.bearingDegrees) <= 14f
                && Math.abs(exactResult.headingDiffDegrees) <= 16f
                && Math.abs(exactResult.phoneHeightDiffMeters) <= 0.14f;
    }

    private boolean shouldHoldReadyState(AuditGuidanceEngine.GuidanceResult phoneApproach,
                                         AuditGuidanceEngine.GuidanceResult exactResult) {
        if (isCaptureZoneReached(phoneApproach, exactResult)) {
            return true;
        }
        if (exactResult == null) {
            return false;
        }
        if (exactResult.distanceMeters <= 0.78f
                && Math.abs(exactResult.sideMeters) <= 0.18f
                && Math.abs(exactResult.headingDiffDegrees) <= 42f
                && Math.abs(exactResult.phoneHeightDiffMeters) <= 0.22f) {
            return true;
        }
        if (phoneApproach == null) {
            return false;
        }
        return phoneApproach.distanceMeters <= 1.05f
                && Math.abs(phoneApproach.sideMeters) <= 0.18f
                && Math.abs(phoneApproach.bearingDegrees) <= 16f
                && Math.abs(exactResult.headingDiffDegrees) <= 42f
                && Math.abs(exactResult.phoneHeightDiffMeters) <= 0.22f;
    }

    private void openNextPendingShelfOrFinish() {
        if (currentShelf == null) {
            return;
        }
        refreshAreaShelves();
        int nextShelfId = auditRepository.findNextPendingShelfIdForSession(areaShelves, currentShelf.getId(), sessionId);
        if (nextShelfId > 0) {
            Toast.makeText(this, "Shelf done. Continuing to the next shelf.", Toast.LENGTH_SHORT).show();
            loadShelf(nextShelfId, true);
            updateStaticUi();
            return;
        }
        if (sessionId > 0L) {
            auditRepository.markSessionCompleted(sessionId);
            if (telemetryRepository != null && currentShelf != null) {
                telemetryRepository.record("audit_session_completed",
                        currentShelf.getStoreReferenceId(),
                        currentShelf.getId(),
                        sessionId,
                        "All shelves completed");
            }
        }
        Toast.makeText(this, "Store audit completed.", Toast.LENGTH_LONG).show();
        if (launchedFromSalesDiary) {
            sendResultBackToSalesDiary("success", "audit_completed");
            return;
        }
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
        finish();
    }

    private void readExternalLaunch(Intent intent) {
        launchedFromSalesDiary = intent != null
                && Constants.SOURCE_APP_SALESDIARY.equalsIgnoreCase(intent.getStringExtra(Constants.EXTRA_SOURCE_APP));
        externalAuditSessionToken = intent != null ? intent.getStringExtra(Constants.EXTRA_EXTERNAL_AUDIT_SESSION_TOKEN) : null;
        externalCallbackScheme = intent != null ? intent.getStringExtra(Constants.EXTRA_EXTERNAL_CALLBACK_SCHEME) : null;
        externalCallbackHost = intent != null ? intent.getStringExtra(Constants.EXTRA_EXTERNAL_CALLBACK_HOST) : null;
    }

    private void handleAuditCancelled() {
        if (launchedFromSalesDiary) {
            sendResultBackToSalesDiary("failure", "audit_cancelled");
            return;
        }
        finish();
    }

    private void sendResultBackToSalesDiary(String status, String message) {
        if (externalCallbackDispatched || !launchedFromSalesDiary
                || TextUtils.isEmpty(externalCallbackScheme)
                || TextUtils.isEmpty(externalCallbackHost)) {
            if (!launchedFromSalesDiary) {
                finish();
            }
            return;
        }
        externalCallbackDispatched = true;
        try {
            Uri.Builder builder = new Uri.Builder()
                    .scheme(externalCallbackScheme)
                    .authority(externalCallbackHost)
                    .appendQueryParameter(Constants.EXTRA_EXTERNAL_STATUS, status)
                    .appendQueryParameter("audit_type", "visual_audit");
            if (!TextUtils.isEmpty(externalAuditSessionToken)) {
                builder.appendQueryParameter(Constants.EXTRA_EXTERNAL_AUDIT_SESSION_TOKEN, externalAuditSessionToken);
            }
            if (!TextUtils.isEmpty(message)) {
                builder.appendQueryParameter(Constants.EXTRA_EXTERNAL_RESULT_MESSAGE, message);
            }
            Intent callbackIntent = new Intent(Intent.ACTION_VIEW, builder.build());
            callbackIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(callbackIntent);
        } catch (Exception e) {
            Log.e(TAG, "Failed to callback SalesDiary", e);
        }
        finish();
    }


    @Override
    public void onLowMemory() {
        super.onLowMemory();
        CrashLogRepository.noteBreadcrumb(this, "AuditNavigationActivity onLowMemory");
        if (arStandMarkerHelper != null) arStandMarkerHelper.clear();
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (level >= TRIM_MEMORY_RUNNING_LOW) {
            CrashLogRepository.noteBreadcrumb(this, "AuditNavigationActivity onTrimMemory level=" + level);
            if (arStandMarkerHelper != null) arStandMarkerHelper.clear();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == Constants.CAMERA_PERMISSION_REQUEST_CODE || requestCode == 0) {
            boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            if (granted) {
                bootstrapArIfPossible();
            } else {
                binding.tvTrackingStatus.setText("Camera permission denied");
                Toast.makeText(this, "Camera permission is required for guided audit", Toast.LENGTH_LONG).show();
            }
        }
    }
}
