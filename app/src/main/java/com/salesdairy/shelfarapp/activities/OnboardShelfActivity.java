package com.salesdairy.shelfarapp.activities;

import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.PixelCopy;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentOnAttachListener;

import com.google.ar.core.Anchor;
import com.google.ar.core.CameraConfig;
import com.google.ar.core.CameraConfigFilter;
import com.google.ar.core.Config;
import com.google.ar.core.Frame;
import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
import com.google.ar.core.Point;
import com.google.ar.core.Pose;
import com.google.ar.core.Session;
import com.google.ar.core.TrackingState;
import com.google.ar.sceneform.ArSceneView;
import com.google.ar.sceneform.ux.ArFragment;
import com.google.ar.sceneform.ux.BaseArFragment;
import com.salesdairy.shelfarapp.ar.ARCoreManager;
import com.salesdairy.shelfarapp.ar.CloudAnchorHelper;
import com.salesdairy.shelfarapp.data.StoreReferenceRepository;
import com.salesdairy.shelfarapp.data.RouteRepository;
import com.salesdairy.shelfarapp.data.ShelfRepository;
import com.salesdairy.shelfarapp.data.TelemetryRepository;
import com.salesdairy.shelfarapp.databinding.ActivityOnboardShelfBinding;
import com.salesdairy.shelfarapp.models.StoreReference;
import com.salesdairy.shelfarapp.models.Shelf;
import com.salesdairy.shelfarapp.onboarding.OnboardShelfUi;
import com.salesdairy.shelfarapp.onboarding.OnboardingRouteRecorder;
import com.salesdairy.shelfarapp.sensors.OrientationHelper;
import com.salesdairy.shelfarapp.utils.Constants;
import com.salesdairy.shelfarapp.utils.CrashLogRepository;
import com.salesdairy.shelfarapp.utils.ImageUtils;
import com.salesdairy.shelfarapp.utils.PermissionUtils;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.Deque;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public class OnboardShelfActivity extends AppCompatActivity implements
        FragmentOnAttachListener,
        BaseArFragment.OnSessionConfigurationListener,
        ArFragment.OnViewCreatedListener {

    private static final String TAG = "ShelfARFlow";
    private static final String SHELF_AR_TAG = "shelf_ar_fragment";

    private static final int COLOR_READY = Color.parseColor("#22C55E");
    private static final int COLOR_WARN  = Color.parseColor("#38BDF8");
    private static final int COLOR_ERROR = Color.parseColor("#F97316");
    private static final int COLOR_MUTED = Color.parseColor("#94A3B8");

    private static final long STATUS_REFRESH_MS          = 250L;
    private static final float POSTURE_ICON_MAX_SHIFT_DP = 18f;
    private static final float[] RETICLE_SEARCH_STEPS_DP = new float[]{0f, 18f, 36f, 56f, 80f};

    // Hold required once shelf is centered
    private static final long ALIGN_HOLD_REQUIRED_MS = 90L;

    // ── Feature-map quality windows ────────────────────────────────────────
    // Reduced from original values to make onboarding achievable on retail
    // shelves (shiny, low-texture, repeating surfaces).
    private static final long  FEATURE_HISTORY_WINDOW_MS             = 3500L;
    private static final long  QUALITY_MUST_BE_RECENT_WITHIN_MS      = 2200L;
    private static final long  QUALITY_MUST_BE_STABLE_FOR_MS         = 350L;
    private static final int   MIN_RECENT_SAMPLES_FOR_CONFIDENCE     = 2;
    private static final int   MIN_SUFFICIENT_OR_GOOD_SAMPLES_FOR_CONFIDENCE = 1;

    // ── Motion tracking (advisory only — not a capture gate) ───────────────
    // Kept so we can display the sweep step indicator, but it never blocks
    // capture. Users on shiny shelves cannot sweep and hold center at the
    // same time reliably.
    private static final long  MOTION_WINDOW_MS           = 4000L;
    private static final float MIN_HORIZONTAL_SPAN_METERS = 0.10f;
    private static final float MIN_TOTAL_PATH_METERS      = 0.18f;

    private static final long  STABLE_POSE_HISTORY_WINDOW_MS = 1400L;
    private static final int   MIN_STABLE_POSE_SAMPLES       = 3;
    private static final float MAX_STABLE_CAMERA_RADIUS_METERS = 0.16f;
    private static final float MAX_STABLE_SHELF_RADIUS_METERS  = 0.18f;

    private static final float GUIDE_ANCHOR_BACK_OFFSET_METERS = 1.85f;
    private static final float GUIDE_ANCHOR_SIDE_OFFSET_METERS = 1.05f;
    private static final float GUIDE_ANCHOR_BACK_DIAGONAL_SIDE_OFFSET_METERS = 1.20f;
    private static final float GUIDE_ANCHOR_FRONT_OFFSET_METERS = 1.05f;
    private static final float GUIDE_ANCHOR_FRONT_DIAGONAL_FORWARD_METERS = 0.82f;
    private static final float GUIDE_ANCHOR_FRONT_DIAGONAL_SIDE_OFFSET_METERS = 0.92f;
    private static final float MIN_GUIDE_POSE_SEPARATION_METERS = 0.28f;

    private static final long QUALITY_LOG_INTERVAL_MS = 1000L;


    private ActivityOnboardShelfBinding binding;
    private OrientationHelper           orientationHelper;
    private ShelfRepository             repository;
    private StoreReferenceRepository    storeReferenceRepository;
    private RouteRepository             routeRepository;
    private TelemetryRepository         telemetryRepository;
    private ARCoreManager               arCoreManager;
    private CloudAnchorHelper           cloudAnchorHelper;
    private CloudAnchorHelper           referenceResolveHelper;
    private ArFragment                  arFragment;
    private final AtomicBoolean         arBootstrapInProgress = new AtomicBoolean(false);
    private OnboardShelfUi             shelfUi;

    private StoreReference              activeStoreReference;
    private Anchor                      resolvedStoreReferenceAnchor;
    private Pose                        resolvedStoreReferencePose;
    private boolean                     referenceResolveStarted = false;
    private boolean                     referenceResolveFinished = false;

    private boolean userRequestedInstall        = true;
    private boolean isSaving                    = false;
    private boolean preferredCameraConfigApplied = false;
    private static final String DEFAULT_ROUTE_LABEL = "Store route";
    private static final String STATE_SHELF_NAME = "state_shelf_name";
    private static final String STATE_IMAGE_PATH = "state_image_path";
    private static final String STATE_CAPTURED_SHELF = "state_captured_shelf";
    private static final String STATE_CAPTURED_CAMERA = "state_captured_camera";
    private static final String STATE_RESOLVED_REFERENCE = "state_resolved_reference";
    private static final String STATE_HAS_CAPTURED = "state_has_captured";
    private static final float SHELF_PREVIEW_CROP_SCALE = 0.68f;
    private OnboardingRouteRecorder routeRecorder;

    private String imagePath = "";
    private Bitmap capturedBitmap;
    private Pose   capturedShelfPose;
    private Pose   capturedCameraPose;
    private boolean hasCapturedAnchor = false;

    private float anchorX, anchorY, anchorZ;
    private float rotX, rotY, rotZ, rotW = 1f;
    private float cameraX, cameraY, cameraZ;
    private float cameraRotX, cameraRotY, cameraRotZ, cameraRotW = 1f;

    private boolean targetCenteredNow      = false;
    private Pose    targetPoseNearCenter;
    private long    alignedSinceElapsedMs  = 0L;
    private long    allReadySinceElapsedMs = 0L;

    private Session.FeatureMapQuality liveFeatureMapQuality      = Session.FeatureMapQuality.INSUFFICIENT;
    private Session.FeatureMapQuality bestRecentFeatureMapQuality = Session.FeatureMapQuality.INSUFFICIENT;
    private Session.FeatureMapQuality capturedBestRecentQuality  = Session.FeatureMapQuality.INSUFFICIENT;

    private Pose lastKnownShelfPose;

    private long lastSufficientOrBetterElapsedMs = 0L;
    private long stableEnoughSinceElapsedMs      = 0L;
    private long lastQualityLogElapsedMs         = 0L;

    private int capturedRecentSampleCount    = 0;
    private int capturedRecentSufficientCount = 0;

    private final Handler                          uiHandler              = new Handler(Looper.getMainLooper());
    private final Deque<FeatureQualitySample>      featureQualityHistory   = new ArrayDeque<>();
    private final Deque<MotionSample>              motionHistory           = new ArrayDeque<>();
    private final Deque<PoseSample>                stableShelfPoseHistory  = new ArrayDeque<>();
    private final Deque<PoseSample>                stableCameraPoseHistory = new ArrayDeque<>();

    // ── Inner types ────────────────────────────────────────────────────────

    private static class FeatureQualitySample {
        final long                    timestampMs;
        final Session.FeatureMapQuality quality;

        FeatureQualitySample(long ts, Session.FeatureMapQuality q) {
            this.timestampMs = ts;
            this.quality     = q != null ? q : Session.FeatureMapQuality.INSUFFICIENT;
        }
    }

    private static class MotionSample {
        final long  timestampMs;
        final float x, y, z;

        MotionSample(long ts, Pose pose) {
            this.timestampMs = ts;
            this.x = pose.tx();
            this.y = pose.ty();
            this.z = pose.tz();
        }
    }

    private static class PoseSample {
        final long timestampMs;
        final float tx, ty, tz;
        final float qx, qy, qz, qw;

        PoseSample(long ts, Pose pose) {
            this.timestampMs = ts;
            this.tx = pose.tx();
            this.ty = pose.ty();
            this.tz = pose.tz();
            float[] q = pose.getRotationQuaternion();
            this.qx = q[0];
            this.qy = q[1];
            this.qz = q[2];
            this.qw = q[3];
        }
    }

    interface BitmapReadyListener {
        void onBitmapReady(Bitmap bitmap);
    }

    // ── Periodic status refresh ────────────────────────────────────────────

    private final Runnable statusRunnable = new Runnable() {
        @Override public void run() {
            refreshCaptureState();
            uiHandler.postDelayed(this, STATUS_REFRESH_MS);
        }
    };

    // ── Activity lifecycle ─────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityOnboardShelfBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        shelfUi = new OnboardShelfUi(binding);

        orientationHelper = new OrientationHelper(this);
        repository        = new ShelfRepository(this);
        storeReferenceRepository = new StoreReferenceRepository(this);
        routeRepository   = new RouteRepository(this);
        telemetryRepository = new TelemetryRepository(this);
        arCoreManager     = new ARCoreManager();
        cloudAnchorHelper = new CloudAnchorHelper();
        referenceResolveHelper = new CloudAnchorHelper();
        CrashLogRepository.noteBreadcrumb(this, "OnboardShelfActivity onCreate");
        activeStoreReference = storeReferenceRepository.getPreferredStoreReference();
        initRouteRecorderForActiveReference();

        getSupportFragmentManager().addFragmentOnAttachListener(this);

        Fragment existingById = getSupportFragmentManager().findFragmentById(binding.arFragment.getId());
        if (existingById instanceof ArFragment) {
            arFragment = (ArFragment) existingById;
            bindArListeners();
        } else {
            Fragment existingByTag = getSupportFragmentManager().findFragmentByTag(SHELF_AR_TAG);
            if (existingByTag instanceof ArFragment) {
                arFragment = (ArFragment) existingByTag;
                bindArListeners();
            } else {
                Log.d(TAG, "Shelf onboarding will attach AR fragment later after ARCore checks");
            }
        }

        binding.etShelfName.setFocusable(false);
        binding.etShelfName.setFocusableInTouchMode(false);
        binding.etShelfName.setClickable(true);
        binding.etShelfName.setOnClickListener(v -> {
            boolean canReuseCurrentMarker = activeStoreReference != null && resolvedStoreReferencePose != null;
            if (canReuseCurrentMarker) {
                showShelfNameDialog("Name shelf", false, false);
            } else {
                showShelfNameDialogIfNeeded();
            }
        });
        binding.tvAnchorStatus.setOnClickListener(v -> showCurrentStoreReferenceHint());
        binding.etShelfName.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) { refreshCaptureState(); }
        });

        binding.btnCaptureShelf.setOnClickListener(v -> captureShelfSnapshotAndAnchors(false));
        binding.btnRetakeShelf.setOnClickListener(v -> {
            resetCapturedState(false);
            refreshCaptureState();
        });
        binding.btnSaveShelf.setOnClickListener(v -> saveShelf());

        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                showExitWarningDialog();
            }
        });

        resetCapturedState(true);
        if (savedInstanceState != null) {
            restoreOnboardingState(savedInstanceState);
        } else {
            ensureStoreReferenceReadyAndPromptShelfName();
        }
        refreshCaptureState();
    }

    @Override
    public void onAttachFragment(@NonNull FragmentManager fragmentManager,
                                 @NonNull Fragment fragment) {
        if (fragment.getId() == binding.arFragment.getId()
                && fragment instanceof ArFragment) {
            arFragment = (ArFragment) fragment;
            bindArListeners();
        }
    }

    private void bindArListeners() {
        if (arFragment == null) return;
        arFragment.setOnSessionConfigurationListener(this);
        arFragment.setOnViewCreatedListener(this);
    }

    @Override public void onViewCreated(ArSceneView arSceneView) {
        Log.d(TAG, "ArSceneView created");
    }

    @Override
    public void onSessionConfiguration(Session session, Config config) {
        maybeApplyPreferredCameraConfig(session);
        cloudAnchorHelper.enableCloudAnchors(config);
        config.setLightEstimationMode(Config.LightEstimationMode.DISABLED);
        config.setPlaneFindingMode(Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL);
        config.setUpdateMode(Config.UpdateMode.LATEST_CAMERA_IMAGE);
        config.setFocusMode(Config.FocusMode.AUTO);

        if (session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
            config.setDepthMode(Config.DepthMode.AUTOMATIC);
            Log.d(TAG, "Depth mode AUTOMATIC enabled");
        }
    }

    private void maybeApplyPreferredCameraConfig(Session session) {
        if (session == null || preferredCameraConfigApplied) return;
        try {
            CameraConfigFilter filter = new CameraConfigFilter(session);
            filter.setTargetFps(EnumSet.of(CameraConfig.TargetFps.TARGET_FPS_30));
            List<CameraConfig> configs = session.getSupportedCameraConfigs(filter);
            if (configs != null && !configs.isEmpty()) {
                session.setCameraConfig(configs.get(0));
                preferredCameraConfigApplied = true;
                Log.d(TAG, "Applied preferred camera config: TARGET_FPS_30");
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to apply preferred camera config", e);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        CrashLogRepository.noteBreadcrumb(this, "OnboardShelfActivity onResume");
        orientationHelper.register();
        uiHandler.post(statusRunnable);
        if (activeStoreReference == null) {
            activeStoreReference = storeReferenceRepository.getPreferredStoreReference();
        initRouteRecorderForActiveReference();
        }
        bootstrapArIfPossible();
    }

    private void bootstrapArIfPossible() {
        if (arFragment != null) {
            return;
        }
        if (arBootstrapInProgress.getAndSet(true)) {
            Log.d(TAG, "Shelf onboarding bootstrap ignored because already in progress");
            return;
        }
        try {
            if (!PermissionUtils.hasCameraPermission(this)) {
                PermissionUtils.requestCameraPermission(this);
                arBootstrapInProgress.set(false);
                return;
            }

            arCoreManager.checkAvailability(this, supported -> runOnUiThread(() -> {
                if (!supported) {
                    Toast.makeText(this, "This device does not support ARCore", Toast.LENGTH_LONG).show();
                    arBootstrapInProgress.set(false);
                    finish();
                    return;
                }
                try {
                    boolean installed = arCoreManager.requestInstall(this, userRequestedInstall);
                    if (!installed) {
                        userRequestedInstall = false;
                        arBootstrapInProgress.set(false);
                        return;
                    }
                    attachArFragmentIfNeeded();
                    arBootstrapInProgress.set(false);
                } catch (Exception e) {
                    Log.e(TAG, "ARCore init failed", e);
                    ARCoreManager.showArError(this, e);
                    arBootstrapInProgress.set(false);
                    finish();
                }
            }));
        } catch (Exception e) {
            Log.e(TAG, "Shelf onboarding bootstrap failed", e);
            arBootstrapInProgress.set(false);
        }
    }

    private void attachArFragmentIfNeeded() {
        Fragment existingById = getSupportFragmentManager().findFragmentById(binding.arFragment.getId());
        if (existingById instanceof ArFragment) {
            arFragment = (ArFragment) existingById;
            bindArListeners();
            return;
        }
        Fragment existingByTag = getSupportFragmentManager().findFragmentByTag(SHELF_AR_TAG);
        if (existingByTag instanceof ArFragment) {
            arFragment = (ArFragment) existingByTag;
            bindArListeners();
            return;
        }
        getSupportFragmentManager()
                .beginTransaction()
                .replace(binding.arFragment.getId(), new ArFragment(), SHELF_AR_TAG)
                .commitNowAllowingStateLoss();
        Fragment fragment = getSupportFragmentManager().findFragmentByTag(SHELF_AR_TAG);
        if (fragment instanceof ArFragment) {
            arFragment = (ArFragment) fragment;
            bindArListeners();
            Log.d(TAG, "Shelf onboarding AR fragment attached safely");
        }
    }


    @Override
    public void onLowMemory() {
        super.onLowMemory();
        CrashLogRepository.noteBreadcrumb(this, "OnboardShelfActivity onLowMemory");
        if (capturedBitmap != null && !capturedBitmap.isRecycled()) {
            capturedBitmap.recycle();
            capturedBitmap = null;
        }
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (level >= TRIM_MEMORY_RUNNING_LOW) {
            CrashLogRepository.noteBreadcrumb(this, "OnboardShelfActivity onTrimMemory level=" + level);
            if (capturedBitmap != null && !capturedBitmap.isRecycled()) {
                capturedBitmap.recycle();
                capturedBitmap = null;
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == Constants.CAMERA_PERMISSION_REQUEST_CODE || requestCode == 0) {
            boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            Log.d(TAG, "Shelf onboarding camera permission callback granted=" + granted);
            if (granted) {
                bootstrapArIfPossible();
            } else {
                Toast.makeText(this, "Camera permission is required for shelf onboarding", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        CrashLogRepository.noteBreadcrumb(this, "OnboardShelfActivity onPause");
        orientationHelper.unregister();
        uiHandler.removeCallbacks(statusRunnable);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        CrashLogRepository.noteBreadcrumb(this, "OnboardShelfActivity onDestroy");
        recycleCapturedBitmap();
        cloudAnchorHelper.clear();
        if (referenceResolveHelper != null) {
            referenceResolveHelper.clear();
        }
    }

    // ── Core state refresh ─────────────────────────────────────────────────

    private void refreshCaptureState() {
        long now = SystemClock.elapsedRealtime();
        Frame frame = getCurrentFrame();
        Session session = getCurrentSession();

        maybeStartStoreReferenceResolve(session, frame);
        updateTargetPose(frame);
        updateFeatureMapQuality(session, frame, now);
        updateMotionHistory(frame, now);
        updateStablePoseHistory(frame, now);
        updatePostureBadgeMotion();
        maybeLogQualityState(now);

        boolean hasName = hasShelfName();
        boolean trackingReady = isTrackingReady(frame);
        boolean postureOk = orientationHelper.isCaptureReadyPosture();
        boolean markerReady = activeStoreReference != null && resolvedStoreReferencePose != null;
        boolean centeredOk = targetCenteredNow;
        boolean steadyReady = isAlignmentHeldLongEnough(now);
        boolean stablePoseReady = hasStablePoseConfidence();
        boolean movementBonus = isMovementGoodEnough();
        boolean qualityReady = isManualCaptureReady(now);

        boolean canCapture = hasName
                && trackingReady
                && postureOk
                && markerReady
                && centeredOk
                && steadyReady
                && qualityReady
                && !isSaving
                && !hasCapturedAnchor;

        boolean canSave = hasName
                && hasCapturedAnchor
                && capturedShelfPose != null
                && capturedCameraPose != null
                && capturedBitmap != null
                && !TextUtils.isEmpty(imagePath)
                && !isSaving;

        binding.btnCaptureShelf.setEnabled(canCapture);
        binding.btnCaptureShelf.setText(getCaptureButtonText(now, canCapture));
        binding.btnSaveShelf.setEnabled(canSave);
        binding.btnRetakeShelf.setEnabled(hasCapturedAnchor && !isSaving);
        binding.layoutResultPanel.setVisibility(hasCapturedAnchor ? View.VISIBLE : View.GONE);

        if (!hasCapturedAnchor) {
            binding.btnSaveShelf.setText("Save shelf");
        } else if (!hasName) {
            binding.btnSaveShelf.setText("Enter name to save");
        } else {
            binding.btnSaveShelf.setText("Save shelf");
        }

        setCenterGuideColor(
                canCapture ? COLOR_READY
                        : (centeredOk && trackingReady && postureOk) ? COLOR_WARN
                        : COLOR_MUTED
        );

        if (markerReady && trackingReady && frame != null && frame.getCamera() != null) {
            maybeRecordRouteCheckpoint(frame.getCamera().getPose(), false, "PATH");
        }

        shelfUi.updatePostureText(orientationHelper.hasSensorReading(), orientationHelper.isStrictlyUpright(), postureOk);
        if (!markerReady) {
            updateStoreReferenceUi(trackingReady);
        } else {
            updateSimpleGuideText(trackingReady, postureOk, centeredOk, steadyReady, stablePoseReady, movementBonus, qualityReady, now);
            updateAnchorStatus(hasName, trackingReady, postureOk, centeredOk, steadyReady, stablePoseReady, movementBonus, qualityReady, now);
        }
        updateCaptureSummary(now);
        shelfUi.updateProgressSteps(trackingReady, markerReady, centeredOk, steadyReady);
    }

    // ── Per-frame updates ──────────────────────────────────────────────────

    private void updateTargetPose(Frame frame) {
        Pose bestPose = null;
        if (frame != null) bestPose = getBestPoseNearCenter(frame);

        targetPoseNearCenter = bestPose;
        if (bestPose != null) lastKnownShelfPose = bestPose;
        targetCenteredNow = (bestPose != null);

        if (targetCenteredNow) {
            if (alignedSinceElapsedMs == 0L) alignedSinceElapsedMs = SystemClock.elapsedRealtime();
        } else {
            alignedSinceElapsedMs = 0L;
        }
    }

    private void updateFeatureMapQuality(Session session, Frame frame, long now) {
        liveFeatureMapQuality = Session.FeatureMapQuality.INSUFFICIENT;

        if (session != null && frame != null && isTrackingReady(frame)) {
            Pose qualityPose = frame.getCamera().getPose();
            liveFeatureMapQuality = cloudAnchorHelper.getFeatureMapQuality(session, qualityPose);
        }

        featureQualityHistory.addLast(new FeatureQualitySample(now, liveFeatureMapQuality));
        while (!featureQualityHistory.isEmpty()
                && now - featureQualityHistory.peekFirst().timestampMs > FEATURE_HISTORY_WINDOW_MS) {
            featureQualityHistory.removeFirst();
        }

        bestRecentFeatureMapQuality = Session.FeatureMapQuality.INSUFFICIENT;
        int  sufficientOrGoodCount     = 0;
        long latestSufficientOrBetterTs = 0L;

        for (FeatureQualitySample sample : featureQualityHistory) {
            if (sample.quality == Session.FeatureMapQuality.GOOD) {
                bestRecentFeatureMapQuality = Session.FeatureMapQuality.GOOD;
                sufficientOrGoodCount++;
                latestSufficientOrBetterTs = sample.timestampMs;
            } else if (sample.quality == Session.FeatureMapQuality.SUFFICIENT) {
                if (bestRecentFeatureMapQuality != Session.FeatureMapQuality.GOOD)
                    bestRecentFeatureMapQuality = Session.FeatureMapQuality.SUFFICIENT;
                sufficientOrGoodCount++;
                latestSufficientOrBetterTs = sample.timestampMs;
            }
        }

        lastSufficientOrBetterElapsedMs = latestSufficientOrBetterTs;

        if (isRecentWindowStrongEnough(now)) {
            if (stableEnoughSinceElapsedMs == 0L) stableEnoughSinceElapsedMs = now;
        } else {
            stableEnoughSinceElapsedMs = 0L;
        }
    }

    private void updateMotionHistory(Frame frame, long now) {
        if (frame != null && isTrackingReady(frame)) {
            Pose cameraPose = getStableAveragePose(stableCameraPoseHistory, frame.getCamera().getPose());
            if (cameraPose != null) motionHistory.addLast(new MotionSample(now, cameraPose));
        }
        while (!motionHistory.isEmpty()
                && now - motionHistory.peekFirst().timestampMs > MOTION_WINDOW_MS) {
            motionHistory.removeFirst();
        }
    }

    private void updateStablePoseHistory(Frame frame, long now) {
        boolean stableCaptureWindow = frame != null
                && isTrackingReady(frame)
                && orientationHelper.isCaptureReadyPosture()
                && targetCenteredNow
                && targetPoseNearCenter != null;

        if (!stableCaptureWindow) {
            if (!targetCenteredNow || !orientationHelper.isCaptureReadyPosture()) {
                stableShelfPoseHistory.clear();
                stableCameraPoseHistory.clear();
            }
            trimPoseHistory(stableShelfPoseHistory, now);
            trimPoseHistory(stableCameraPoseHistory, now);
            return;
        }

        Pose cameraPose = frame.getCamera() != null ? frame.getCamera().getPose() : null;
        if (cameraPose != null) {
            stableCameraPoseHistory.addLast(new PoseSample(now, cameraPose));
        }
        stableShelfPoseHistory.addLast(new PoseSample(now, targetPoseNearCenter));

        trimPoseHistory(stableShelfPoseHistory, now);
        trimPoseHistory(stableCameraPoseHistory, now);
    }

    private void trimPoseHistory(Deque<PoseSample> history, long now) {
        while (!history.isEmpty()
                && now - history.peekFirst().timestampMs > STABLE_POSE_HISTORY_WINDOW_MS) {
            history.removeFirst();
        }
    }

    private Pose getStableAveragePose(Deque<PoseSample> history, Pose fallback) {
        if (history == null || history.size() < MIN_STABLE_POSE_SAMPLES) {
            return fallback;
        }

        float tx = 0f, ty = 0f, tz = 0f;
        float qx = 0f, qy = 0f, qz = 0f, qw = 0f;
        int count = 0;
        PoseSample first = history.peekFirst();

        for (PoseSample sample : history) {
            tx += sample.tx;
            ty += sample.ty;
            tz += sample.tz;

            float sqx = sample.qx;
            float sqy = sample.qy;
            float sqz = sample.qz;
            float sqw = sample.qw;

            if (first != null) {
                float dot = (first.qx * sqx) + (first.qy * sqy) + (first.qz * sqz) + (first.qw * sqw);
                if (dot < 0f) {
                    sqx = -sqx;
                    sqy = -sqy;
                    sqz = -sqz;
                    sqw = -sqw;
                }
            }

            qx += sqx;
            qy += sqy;
            qz += sqz;
            qw += sqw;
            count++;
        }

        if (count == 0) {
            return fallback;
        }

        tx /= count;
        ty /= count;
        tz /= count;

        float qLen = (float) Math.sqrt((qx * qx) + (qy * qy) + (qz * qz) + (qw * qw));
        if (qLen < 0.0001f) {
            return fallback;
        }

        qx /= qLen;
        qy /= qLen;
        qz /= qLen;
        qw /= qLen;
        return new Pose(new float[]{tx, ty, tz}, new float[]{qx, qy, qz, qw});
    }

    // ── Quality checks ─────────────────────────────────────────────────────

    private boolean isRecentWindowStrongEnough(long now) {
        int sufficientOrGoodCount = 0;
        for (FeatureQualitySample sample : featureQualityHistory) {
            if (sample.quality == Session.FeatureMapQuality.SUFFICIENT
                    || sample.quality == Session.FeatureMapQuality.GOOD) {
                sufficientOrGoodCount++;
            }
        }

        boolean recentEnough = lastSufficientOrBetterElapsedMs > 0L
                && (now - lastSufficientOrBetterElapsedMs) <= QUALITY_MUST_BE_RECENT_WITHIN_MS;

        boolean enoughSamples = featureQualityHistory.size() >= MIN_RECENT_SAMPLES_FOR_CONFIDENCE
                && sufficientOrGoodCount >= MIN_SUFFICIENT_OR_GOOD_SAMPLES_FOR_CONFIDENCE;

        return recentEnough
                && bestRecentFeatureMapQuality != Session.FeatureMapQuality.INSUFFICIENT
                && enoughSamples;
    }

    private boolean isQualityReadyForCapture(long now) {
        return isRecentWindowStrongEnough(now)
                && stableEnoughSinceElapsedMs > 0L
                && (now - stableEnoughSinceElapsedMs) >= QUALITY_MUST_BE_STABLE_FOR_MS;
    }

    private boolean isManualCaptureReady(long now) {
        return isQualityReadyForCapture(now)
                || isQualityUsableRightNow(now);
    }

    private boolean isQualityUsableRightNow(long now) {
        boolean liveUsable = liveFeatureMapQuality == Session.FeatureMapQuality.SUFFICIENT
                || liveFeatureMapQuality == Session.FeatureMapQuality.GOOD;
        boolean recentlyUsable = lastSufficientOrBetterElapsedMs > 0L
                && (now - lastSufficientOrBetterElapsedMs) <= QUALITY_MUST_BE_RECENT_WITHIN_MS;
        return liveUsable || recentlyUsable;
    }

    private boolean isAlignmentHeldLongEnough(long now) {
        return alignedSinceElapsedMs > 0L
                && (now - alignedSinceElapsedMs) >= ALIGN_HOLD_REQUIRED_MS;
    }

    private boolean hasStablePoseConfidence() {
        return stableShelfPoseHistory.size() >= MIN_STABLE_POSE_SAMPLES
                && stableCameraPoseHistory.size() >= MIN_STABLE_POSE_SAMPLES
                && isPoseClusterStable(stableShelfPoseHistory, MAX_STABLE_SHELF_RADIUS_METERS)
                && isPoseClusterStable(stableCameraPoseHistory, MAX_STABLE_CAMERA_RADIUS_METERS);
    }

    private boolean isPoseClusterStable(Deque<PoseSample> history, float maxRadiusMeters) {
        if (history == null || history.size() < MIN_STABLE_POSE_SAMPLES) {
            return false;
        }

        float cx = 0f, cy = 0f, cz = 0f;
        int count = 0;
        for (PoseSample sample : history) {
            cx += sample.tx;
            cy += sample.ty;
            cz += sample.tz;
            count++;
        }
        if (count == 0) {
            return false;
        }
        cx /= count;
        cy /= count;
        cz /= count;

        float maxDistance = 0f;
        for (PoseSample sample : history) {
            float dx = sample.tx - cx;
            float dy = sample.ty - cy;
            float dz = sample.tz - cz;
            float distance = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (distance > maxDistance) {
                maxDistance = distance;
            }
        }
        return maxDistance <= maxRadiusMeters;
    }

    /** Advisory only — does not block capture. */
    private boolean isMovementGoodEnough() {
        if (motionHistory.size() < 3) return false;

        float    minX      = Float.MAX_VALUE;
        float    maxX      = -Float.MAX_VALUE;
        float    totalPath = 0f;
        MotionSample previous = null;

        for (MotionSample sample : motionHistory) {
            minX = Math.min(minX, sample.x);
            maxX = Math.max(maxX, sample.x);
            if (previous != null) {
                float dx = sample.x - previous.x;
                float dy = sample.y - previous.y;
                float dz = sample.z - previous.z;
                totalPath += (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            }
            previous = sample;
        }

        float horizontalSpan = maxX - minX;
        return horizontalSpan >= MIN_HORIZONTAL_SPAN_METERS
                || totalPath >= MIN_TOTAL_PATH_METERS;
    }

    // Manual capture only — user decides when to capture after scan quality is ready.

    // ── Logging ────────────────────────────────────────────────────────────

    private void maybeLogQualityState(long now) {
        if (now - lastQualityLogElapsedMs < QUALITY_LOG_INTERVAL_MS) return;
        lastQualityLogElapsedMs = now;

        int sufficientOrGoodCount = 0, goodCount = 0;
        for (FeatureQualitySample s : featureQualityHistory) {
            if (s.quality == Session.FeatureMapQuality.SUFFICIENT) sufficientOrGoodCount++;
            else if (s.quality == Session.FeatureMapQuality.GOOD) { sufficientOrGoodCount++; goodCount++; }
        }

        float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, totalPath = 0f;
        MotionSample prev = null;
        for (MotionSample s : motionHistory) {
            minX = Math.min(minX, s.x);
            maxX = Math.max(maxX, s.x);
            if (prev != null) {
                float dx = s.x - prev.x, dy = s.y - prev.y, dz = s.z - prev.z;
                totalPath += (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            }
            prev = s;
        }

        float horizontalSpan   = motionHistory.isEmpty() ? 0f : (maxX - minX);
        long  recentAgeMs      = lastSufficientOrBetterElapsedMs > 0L
                ? (now - lastSufficientOrBetterElapsedMs) : -1L;
        long  stableForMs      = stableEnoughSinceElapsedMs > 0L
                ? (now - stableEnoughSinceElapsedMs) : 0L;

        Log.d(TAG, "FMQ"
                + " live=" + liveFeatureMapQuality
                + ", bestRecent=" + bestRecentFeatureMapQuality
                + ", histSz=" + featureQualityHistory.size()
                + ", sufGoodCnt=" + sufficientOrGoodCount
                + ", goodCnt=" + goodCount
                + ", recentAgeMs=" + recentAgeMs
                + ", stableForMs=" + stableForMs
                + ", qualReady=" + isQualityReadyForCapture(now)
                + ", centered=" + targetCenteredNow
                + ", posture=" + orientationHelper.isCaptureReadyPosture()
                + ", phY=" + orientationHelper.getY()
                + ", phZ=" + orientationHelper.getZ()
                + ", motSamples=" + motionHistory.size()
                + ", hSpan=" + horizontalSpan
                + ", path=" + totalPath
                + ", movBonus=" + isMovementGoodEnough());
    }

    // ── UI update helpers ──────────────────────────────────────────────────

    /**
     * Guide text shown at the top of the screen.
     * Sweep is now advisory — we encourage it but don't block on it.
     */
    private void updateSimpleGuideText(boolean trackingReady,
                                       boolean postureOk,
                                       boolean centeredOk,
                                       boolean steadyReady,
                                       boolean stablePoseReady,
                                       boolean movementBonus,
                                       boolean qualityReady,
                                       long now) {
        String title;
        String hint;

        if (!trackingReady) {
            title = "Start scan";
            hint  = "Move slowly until tracking becomes stable";
        } else if (!postureOk) {
            title = "Hold phone straight";
            hint  = orientationHelper.getPostureGuidance();
        } else if (!centeredOk) {
            title = "Frame the shelf";
            hint  = "Fill the box with the whole shelf, labels, and both side edges";
        } else if (!steadyReady) {
            title = "Hold steady";
            hint  = "Keep the shelf centered for a short moment";
        } else if (!hasCapturedAnchor) {
            title = "Ready to capture";
            hint  = "Keep the full shelf in frame, then tap Capture shelf";
        } else {
            title = "Shelf captured";
            hint  = "Review the image and tap Save shelf";
        }

        binding.tvScanStepTitle.setText(title);
        binding.tvCaptureHint.setText(hint);
    }

    private void updateAnchorStatus(boolean hasName,
                                    boolean trackingReady,
                                    boolean postureOk,
                                    boolean centeredOk,
                                    boolean steadyReady,
                                    boolean stablePoseReady,
                                    boolean movementBonus,
                                    boolean qualityReady,
                                    long now) {
        String qualityLabel = getQualityLabel(now);
        String message;

        if (!trackingReady) {
            message = "Move slowly until AR tracking becomes ready.";
        } else if (!postureOk) {
            message = orientationHelper.getPostureGuidance();
        } else if (!centeredOk) {
            message = "Center the shelf in the guide box.";
        } else if (!steadyReady) {
            message = "Hold the phone steady for a moment.";
        } else if (!hasCapturedAnchor) {
            message = "Reference locked. Tap Capture shelf when the shelf looks clean and centered.";
        } else if (!hasName) {
            message = "Shelf name is required before capture.";
        } else {
            message = "Shelf captured. Ready to save.";
        }

        binding.tvAnchorStatus.setText(message);
    }

    private void updateCaptureSummary(long now) {
        if (!hasCapturedAnchor) {
            binding.tvCaptureSummary.setText("Best result: keep the whole shelf inside the box and keep labels and edges visible.");
            return;
        }
        binding.tvCaptureSummary.setText("Captured. Review the shelf image and save it.");
    }

    private void updatePostureText(boolean postureOk) {
        if (!orientationHelper.hasSensorReading()) {
            binding.tvPostureLabel.setText("ALIGN");
            binding.tvPostureLabel.setTextColor(COLOR_MUTED);
            return;
        }
        if (orientationHelper.isStrictlyUpright()) {
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

    private void updatePostureBadgeMotion() {
        float density = getResources().getDisplayMetrics().density;
        float shiftX  = orientationHelper.getOverlayOffsetX() * POSTURE_ICON_MAX_SHIFT_DP * density;
        float shiftY  = orientationHelper.getOverlayOffsetY() * POSTURE_ICON_MAX_SHIFT_DP * density;
        binding.tvPostureActiveCross.setTranslationX(shiftX);
        binding.tvPostureActiveCross.setTranslationY(shiftY);
    }

    /**
     * Progress step dots:
     *   1. Track    → trackingReady
     *   2. Posture  → postureOk
     *   3. Center   → centeredOk
     *   4. Sweep    → movementBonus (advisory, turns green as a bonus)
     *   5. Quality  → qualityReady
     *
     * stepMotionBonus is shown as a bonus indicator — it is NOT required for capture.
     */
    private void updateProgressSteps(boolean trackingReady,
                                     boolean postureOk,
                                     boolean centeredOk,
                                     boolean movementBonus,
                                     boolean qualityReady) {
        setStepColor(binding.stepTrack,   trackingReady, trackingReady);
        setStepColor(binding.stepPosture, postureOk,     trackingReady);
        setStepColor(binding.stepCenter,  centeredOk,    trackingReady && postureOk);
        // Sweep is advisory: active color shows it is an opportunity, not a blocker
        setStepColorAdvisory(binding.stepMotionBonus, movementBonus, trackingReady && postureOk && centeredOk);
        setStepColor(binding.stepQuality, qualityReady,  trackingReady && postureOk && centeredOk);
    }

    private void setStepColor(View dot, boolean done, boolean active) {
        if (dot == null) return;
        if      (done)   dot.setBackgroundColor(COLOR_READY);
        else if (active) dot.setBackgroundColor(COLOR_WARN);
        else             dot.setBackgroundColor(COLOR_MUTED);
    }

    /**
     * Advisory step: green when achieved, yellow-orange when possible, muted otherwise.
     * Never shown as a blocker.
     */
    private void setStepColorAdvisory(View dot, boolean achieved, boolean possible) {
        if (dot == null) return;
        if      (achieved) dot.setBackgroundColor(COLOR_READY);
        else if (possible) dot.setBackgroundColor(COLOR_ERROR); // orange = "optional, try it"
        else               dot.setBackgroundColor(COLOR_MUTED);
    }

    // ── Capture ────────────────────────────────────────────────────────────

    private void captureShelfSnapshotAndAnchors(boolean fromAutoCapture) {
        long    now     = SystemClock.elapsedRealtime();
        Frame   frame   = getCurrentFrame();
        Session session = getCurrentSession();

        if (hasCapturedAnchor || isSaving) return;

        Log.d(TAG, "captureShelfSnapshotAndAnchors()"
                + " manual=" + (!fromAutoCapture)
                + ", live=" + liveFeatureMapQuality
                + ", bestRecent=" + bestRecentFeatureMapQuality
                + ", qualReady=" + isQualityReadyForCapture(now));

        if (frame == null || session == null || !isTrackingReady(frame)) {
            Toast.makeText(this, "AR tracking not ready yet", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!hasShelfName()) {
            showShelfNameDialog("Name shelf", false, false);
            Toast.makeText(this, "Name the shelf before capture.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!orientationHelper.isCaptureReadyPosture()) {
            Toast.makeText(this, orientationHelper.getPostureGuidance(), Toast.LENGTH_SHORT).show();
            return;
        }
        if (!targetCenteredNow) {
            Toast.makeText(this, "Keep the shelf inside the guide and try again.",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        if (!isAlignmentHeldLongEnough(now)) {
            Toast.makeText(this, "Hold the shelf steady for a moment.",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        if (!isManualCaptureReady(now)) {
            Toast.makeText(this, "Keep scanning a little more until feature quality becomes ready.",
                    Toast.LENGTH_LONG).show();
            return;
        }

        Pose liveShelfPose = targetPoseNearCenter != null
                ? targetPoseNearCenter
                : getBestPoseNearCenter(frame);
        Pose shelfPose = getStableAveragePose(stableShelfPoseHistory, liveShelfPose);

        if (shelfPose == null) {
            Toast.makeText(this, "Could not detect the shelf surface. Keep it inside the guide.",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        Pose cameraPose = getStableAveragePose(stableCameraPoseHistory, frame.getCamera().getPose());
        if (cameraPose == null) {
            cameraPose = frame.getCamera().getPose();
        }
        final Pose finalShelfPose = shelfPose;
        final Pose finalCameraPose = cameraPose;

        copyPixelsFromScene(bitmap -> {
            try {
                CrashLogRepository.noteBreadcrumb(this, "Shelf capture save start");
                Bitmap focusedBitmap = ImageUtils.cropCenterKeepingAspect(bitmap, SHELF_PREVIEW_CROP_SCALE);
                imagePath = ImageUtils.saveBitmapToFile(this, focusedBitmap != null ? focusedBitmap : bitmap);
                recycleCapturedBitmap();
                Bitmap previewBitmap = ImageUtils.decodeSampledBitmap(imagePath, 1280, 1280);
                capturedBitmap = previewBitmap != null
                        ? previewBitmap
                        : (focusedBitmap != null ? focusedBitmap : bitmap);
                if (previewBitmap != null) {
                    if (focusedBitmap != null && focusedBitmap != bitmap && !focusedBitmap.isRecycled()) {
                        focusedBitmap.recycle();
                    }
                }
                if (bitmap != null && bitmap != capturedBitmap && bitmap != focusedBitmap && !bitmap.isRecycled()) {
                    bitmap.recycle();
                }
                capturedShelfPose  = finalShelfPose;
                capturedCameraPose = finalCameraPose;
                hasCapturedAnchor  = true;
                allReadySinceElapsedMs = 0L;

                persistPoseValues(finalShelfPose, finalCameraPose);
                captureRecentQualitySnapshot(now);
                binding.ivCapturedImage.setImageBitmap(capturedBitmap);
                binding.layoutResultPanel.setVisibility(View.VISIBLE);

                Log.d(TAG, "Shelf captured manual=" + (!fromAutoCapture));

                if (!fromAutoCapture) {
                    Toast.makeText(this, "Shelf captured", Toast.LENGTH_SHORT).show();
                }

                refreshCaptureState();
            } catch (IOException e) {
                CrashLogRepository.recordHandledException(this, "Onboard shelf save image", e);
                Log.e(TAG, "Failed to save captured shelf image", e);
                Toast.makeText(this, "Failed to save the captured image.", Toast.LENGTH_LONG).show();
            }
        });
    }

    private void recycleCapturedBitmap() {
        if (capturedBitmap != null && !capturedBitmap.isRecycled()) {
            capturedBitmap.recycle();
        }
        capturedBitmap = null;
    }

    private void maybeStartStoreReferenceResolve(Session session, Frame frame) {
        ensureActiveStoreReference();
        if (activeStoreReference == null || session == null || frame == null || !isTrackingReady(frame)) {
            return;
        }
        if (resolvedStoreReferencePose != null || referenceResolveStarted || TextUtils.isEmpty(activeStoreReference.getCloudAnchorId())) {
            return;
        }
        referenceResolveStarted = true;
        referenceResolveHelper.resolveAnchor(session, activeStoreReference.getCloudAnchorId(), new CloudAnchorHelper.ResolveListener() {
            @Override
            public void onResolveSuccess(Anchor anchor) {
                runOnUiThread(() -> {
                    resolvedStoreReferenceAnchor = anchor;
                    resolvedStoreReferencePose = anchor.getPose();
                    referenceResolveFinished = true;
                    if (telemetryRepository != null) {
                        telemetryRepository.record("onboarding_reference_lock_success",
                                activeStoreReference != null ? activeStoreReference.getId() : 0,
                                0L,
                                0L,
                                "resolved in onboarding");
                    }
                    refreshCaptureState();
                });
            }

            @Override
            public void onResolveFailure(Anchor.CloudAnchorState state, String message) {
                runOnUiThread(() -> {
                    referenceResolveStarted = false;
                    referenceResolveFinished = false;
                    if (telemetryRepository != null) {
                        telemetryRepository.record("onboarding_reference_lock_failure",
                                activeStoreReference != null ? activeStoreReference.getId() : 0,
                                0L,
                                0L,
                                state != null ? state.name() : "unknown");
                    }
                    Toast.makeText(OnboardShelfActivity.this,
                            "Store reference lock failed. Move to the saved store reference and try again.",
                            Toast.LENGTH_LONG).show();
                    refreshCaptureState();
                });
            }
        });
    }

    private void updateStoreReferenceUi(boolean trackingReady) {
        if (activeStoreReference == null) {
            binding.tvScanStepTitle.setText("Create store reference first");
            binding.tvCaptureHint.setText("Save one store entry reference before adding shelves.");
            binding.tvAnchorStatus.setText("Open 'Save store reference' from the home screen, then come back here.");
            return;
        }
        String areaLabel = activeStoreReference.getReferenceName();
        shelfUi.renderReferenceWaiting(trackingReady, areaLabel);
    }

    // ── Save ───────────────────────────────────────────────────────────────

    private void saveShelf() {
        Session session = getCurrentSession();
        if (activeStoreReference == null || resolvedStoreReferencePose == null) {
            Toast.makeText(this, "Resolve the saved store reference before saving this shelf.", Toast.LENGTH_LONG).show();
            return;
        }
        if (session == null || capturedShelfPose == null || capturedCameraPose == null) {
            Toast.makeText(this, "Capture the shelf first.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!hasShelfName()) {
            binding.etShelfName.requestFocus();
            showShelfNameDialog("Name shelf", false, false);
            Toast.makeText(this, "Enter a shelf name first.", Toast.LENGTH_SHORT).show();
            return;
        }

        isSaving = true;
        binding.loadingOverlay.setVisibility(View.VISIBLE);
        binding.tvLoadingMessage.setText("Saving shelf…");
        refreshCaptureState();

        Pose relativeShelfPose = resolvedStoreReferencePose.inverse().compose(capturedShelfPose);
        Pose relativeCameraPose = resolvedStoreReferencePose.inverse().compose(capturedCameraPose);
        maybeRecordRouteCheckpoint(capturedCameraPose, true, "SHELF");
        long linkedCheckpointId = routeRecorder != null
                ? routeRecorder.getSuggestedShelfLinkCheckpointId(relativeShelfPose, DEFAULT_ROUTE_LABEL)
                : 0L;
        int routeOrder = routeRecorder != null
                ? Math.max(routeRecorder.getLastSequence(), repository.getNextRouteOrderForReference(activeStoreReference.getId()))
                : repository.getNextRouteOrderForReference(activeStoreReference.getId());
        persistPoseValues(relativeShelfPose, relativeCameraPose);
        Shelf shelf = buildShelf(linkedCheckpointId, routeOrder);
        long rowIdLong = repository.insertShelf(shelf);
        if (rowIdLong <= 0) {
            Log.e(TAG, "Failed to insert shelf into DB");
            isSaving = false;
            binding.loadingOverlay.setVisibility(View.GONE);
            refreshCaptureState();
            Toast.makeText(this, "Failed to save shelf.", Toast.LENGTH_LONG).show();
            return;
        }

        int rowId = (int) rowIdLong;
        repository.updateCloudAnchorFields(rowId, null, "NOT_REQUIRED", null, 0L, 0);
        repository.updateGuideAnchorBundle(rowId, "");
        if (telemetryRepository != null) {
            telemetryRepository.record("shelf_saved",
                    activeStoreReference != null ? activeStoreReference.getId() : 0,
                    rowId,
                    0L,
                    "checkpoint=" + linkedCheckpointId + " order=" + routeOrder);
        }
        finishSaveWithoutHosting(rowId, "Shelf saved under store reference");
    }

    private void finishSaveWithoutHosting(int rowId, String successMessage) {
        isSaving = false;
        binding.loadingOverlay.setVisibility(View.GONE);
        refreshCaptureState();

        Log.d(TAG, "Shelf saved without hosting"
                + " rowId=" + rowId
                + ", storeReferenceId=" + (activeStoreReference != null ? activeStoreReference.getId() : 0));

        Toast.makeText(this,
                TextUtils.isEmpty(successMessage) ? "Shelf saved" : successMessage,
                Toast.LENGTH_SHORT).show();
        setResult(RESULT_OK);
        showPostSaveOptions();
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private Shelf buildShelf(long linkedCheckpointId, int routeOrder) {
        Shelf shelf = new Shelf();
        shelf.setOutletId(Constants.DEFAULT_OUTLET_ID);
        shelf.setStoreReferenceId(activeStoreReference != null ? activeStoreReference.getId() : 0);
        shelf.setRouteLabel(DEFAULT_ROUTE_LABEL);
        shelf.setRouteOrder(routeOrder > 0 ? routeOrder : repository.getNextRouteOrderForReference(shelf.getStoreReferenceId()));
        shelf.setNearestCheckpointId(linkedCheckpointId > 0L ? linkedCheckpointId : (routeRecorder != null ? routeRecorder.getLastStableCheckpointId() : 0L));
        shelf.setShelfName(binding.etShelfName.getText().toString().trim());
        shelf.setImagePath(imagePath);

        shelf.setAnchorX(anchorX);  shelf.setAnchorY(anchorY);  shelf.setAnchorZ(anchorZ);
        shelf.setRotX(rotX);        shelf.setRotY(rotY);         shelf.setRotZ(rotZ);    shelf.setRotW(rotW);

        shelf.setCameraX(cameraX);  shelf.setCameraY(cameraY);  shelf.setCameraZ(cameraZ);
        shelf.setCameraRotX(cameraRotX); shelf.setCameraRotY(cameraRotY);
        shelf.setCameraRotZ(cameraRotZ); shelf.setCameraRotW(cameraRotW);

        shelf.setCreatedAt(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                .format(new Date()));
        shelf.setCloudAnchorStatus("NOT_REQUIRED");
        shelf.setCloudAnchorTtlDays(0);
        shelf.setGuideAnchorBundle("");
        return shelf;
    }

    private void persistPoseValues(Pose shelfPose, Pose cameraPose) {
        float[] st = shelfPose.getTranslation(),   sr = shelfPose.getRotationQuaternion();
        float[] ct = cameraPose.getTranslation(),  cr = cameraPose.getRotationQuaternion();

        anchorX = st[0]; anchorY = st[1]; anchorZ = st[2];
        rotX = sr[0];    rotY = sr[1];    rotZ = sr[2];    rotW = sr[3];

        cameraX = ct[0]; cameraY = ct[1]; cameraZ = ct[2];
        cameraRotX = cr[0]; cameraRotY = cr[1]; cameraRotZ = cr[2]; cameraRotW = cr[3];
    }

    private void captureRecentQualitySnapshot(long now) {
        capturedBestRecentQuality    = bestRecentFeatureMapQuality;
        capturedRecentSampleCount    = featureQualityHistory.size();
        capturedRecentSufficientCount = 0;
        for (FeatureQualitySample s : featureQualityHistory) {
            if (s.quality == Session.FeatureMapQuality.SUFFICIENT
                    || s.quality == Session.FeatureMapQuality.GOOD) {
                capturedRecentSufficientCount++;
            }
        }
    }

    private boolean hadGoodEnoughConfidenceAtCapture() {
        boolean usableQuality = capturedBestRecentQuality == Session.FeatureMapQuality.SUFFICIENT
                || capturedBestRecentQuality == Session.FeatureMapQuality.GOOD;
        boolean enoughSamples = capturedRecentSampleCount    >= MIN_RECENT_SAMPLES_FOR_CONFIDENCE
                && capturedRecentSufficientCount >= MIN_SUFFICIENT_OR_GOOD_SAMPLES_FOR_CONFIDENCE;
        return usableQuality && enoughSamples;
    }

    private Pose getBestPoseNearCenter(Frame frame) {
        if (frame == null) return null;
        ArSceneView sceneView = arFragment != null ? arFragment.getArSceneView() : null;
        if (sceneView == null || sceneView.getWidth() <= 0 || sceneView.getHeight() <= 0) return null;

        float centerX = sceneView.getWidth()  / 2f;
        float centerY = sceneView.getHeight() / 2f;
        float density = getResources().getDisplayMetrics().density;

        for (float dp : RETICLE_SEARCH_STEPS_DP) {
            float px   = dp * density;
            Pose  pose = findPoseAt(frame, centerX, centerY, px);
            if (pose != null) return pose;
        }
        return null;
    }

    private Pose findPoseAt(Frame frame, float centerX, float centerY, float offsetPx) {
        float[][] candidates = {
                {centerX, centerY},
                {centerX - offsetPx, centerY}, {centerX + offsetPx, centerY},
                {centerX, centerY - offsetPx}, {centerX, centerY + offsetPx},
                {centerX - offsetPx, centerY - offsetPx},
                {centerX + offsetPx, centerY - offsetPx},
                {centerX - offsetPx, centerY + offsetPx},
                {centerX + offsetPx, centerY + offsetPx}
        };
        for (float[] c : candidates) {
            Pose pose = getPoseFromScreenPoint(frame, c[0], c[1]);
            if (pose != null) return pose;
        }
        return null;
    }

    private Pose getPoseFromScreenPoint(Frame frame, float x, float y) {
        try {
            List<HitResult> hits = frame.hitTest(x, y);
            for (HitResult hit : hits) {
                if (isValidShelfHit(hit)) return hit.getHitPose();
            }
        } catch (Exception e) {
            Log.w(TAG, "hitTest failed", e);
        }
        return null;
    }

    private boolean isValidShelfHit(HitResult hit) {
        if (hit == null || hit.getTrackable() == null) return false;
        if (hit.getTrackable() instanceof Plane) {
            return ((Plane) hit.getTrackable()).getTrackingState() == TrackingState.TRACKING;
        }
        return hit.getTrackable() instanceof Point;
    }

    private void copyPixelsFromScene(BitmapReadyListener listener) {
        ArSceneView sceneView = arFragment != null ? arFragment.getArSceneView() : null;
        if (sceneView == null || sceneView.getWidth() <= 0 || sceneView.getHeight() <= 0) {
            Toast.makeText(this, "Scene is not ready yet.", Toast.LENGTH_SHORT).show();
            return;
        }
        Bitmap bitmap = Bitmap.createBitmap(sceneView.getWidth(), sceneView.getHeight(),
                Bitmap.Config.ARGB_8888);
        PixelCopy.request(sceneView, bitmap, copyResult -> {
            CrashLogRepository.noteBreadcrumb(this, "Shelf PixelCopy result=" + copyResult);
            if (copyResult == PixelCopy.SUCCESS) {
                listener.onBitmapReady(bitmap);
            } else {
                if (!bitmap.isRecycled()) {
                    bitmap.recycle();
                }
                Toast.makeText(this, "Failed to capture the shelf image.", Toast.LENGTH_SHORT).show();
            }
        }, new Handler(Looper.getMainLooper()));
    }

    private void resetCapturedState(boolean clearNameField) {
        String oldName = binding.etShelfName.getText() != null
                ? binding.etShelfName.getText().toString() : "";

        hasCapturedAnchor = false;
        imagePath = ""; recycleCapturedBitmap();
        capturedShelfPose = null; capturedCameraPose = null;
        lastKnownShelfPose = null;
        motionHistory.clear();
        stableShelfPoseHistory.clear();
        stableCameraPoseHistory.clear();
        allReadySinceElapsedMs = 0L;

        binding.ivCapturedImage.setImageDrawable(null);
        binding.layoutResultPanel.setVisibility(View.GONE);
        binding.tvCaptureSummary.setText("Name the shelf first, keep the phone straight, then capture the full shelf from a steady phone view.");

        anchorX = anchorY = anchorZ = 0f;
        rotX = rotY = rotZ = 0f; rotW = 1f;
        cameraX = cameraY = cameraZ = 0f;
        cameraRotX = cameraRotY = cameraRotZ = 0f; cameraRotW = 1f;

        capturedBestRecentQuality = Session.FeatureMapQuality.INSUFFICIENT;
        capturedRecentSampleCount = 0; capturedRecentSufficientCount = 0;

        if (clearNameField) {
            binding.etShelfName.setText("");
        } else {
            binding.etShelfName.setText(oldName);
            if (binding.etShelfName.getText() != null)
                binding.etShelfName.setSelection(binding.etShelfName.getText().length());
        }
    }

    private void restoreCapturedPreviewIfNeeded() {
        if (capturedBitmap == null && !TextUtils.isEmpty(imagePath)) {
            recycleCapturedBitmap();
            capturedBitmap = ImageUtils.decodeSampledBitmap(imagePath, 1080, 1080);
            if (capturedBitmap != null) {
                binding.ivCapturedImage.setImageBitmap(capturedBitmap);
                binding.layoutResultPanel.setVisibility(View.VISIBLE);
            }
        }
    }

    private void restoreOnboardingState(Bundle state) {
        if (state == null) {
            return;
        }
        String restoredName = state.getString(STATE_SHELF_NAME, "");
        if (!TextUtils.isEmpty(restoredName)) {
            binding.etShelfName.setText(restoredName);
        }
        imagePath = state.getString(STATE_IMAGE_PATH, "");
        capturedShelfPose = readPoseFromBundle(state, STATE_CAPTURED_SHELF);
        capturedCameraPose = readPoseFromBundle(state, STATE_CAPTURED_CAMERA);
        resolvedStoreReferencePose = readPoseFromBundle(state, STATE_RESOLVED_REFERENCE);
        hasCapturedAnchor = state.getBoolean(STATE_HAS_CAPTURED, false);
        if (capturedShelfPose != null && capturedCameraPose != null && !TextUtils.isEmpty(imagePath)) {
            recycleCapturedBitmap();
            capturedBitmap = ImageUtils.decodeSampledBitmap(imagePath, 1080, 1080);
            if (capturedBitmap != null) {
                binding.ivCapturedImage.setImageBitmap(capturedBitmap);
            }
            binding.layoutResultPanel.setVisibility(View.VISIBLE);
        }
    }

    private void writePoseToBundle(Bundle outState, String keyPrefix, Pose pose) {
        if (outState == null || pose == null) {
            return;
        }
        float[] t = pose.getTranslation();
        float[] q = pose.getRotationQuaternion();
        outState.putFloatArray(keyPrefix + "_t", t);
        outState.putFloatArray(keyPrefix + "_q", q);
    }

    private Pose readPoseFromBundle(Bundle state, String keyPrefix) {
        if (state == null) {
            return null;
        }
        float[] t = state.getFloatArray(keyPrefix + "_t");
        float[] q = state.getFloatArray(keyPrefix + "_q");
        if (t == null || q == null || t.length != 3 || q.length != 4) {
            return null;
        }
        try {
            return new Pose(t, q);
        } catch (Exception ignore) {
            return null;
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        String shelfName = binding.etShelfName.getText() != null ? binding.etShelfName.getText().toString() : "";
        outState.putString(STATE_SHELF_NAME, shelfName);
        outState.putString(STATE_IMAGE_PATH, imagePath);
        outState.putBoolean(STATE_HAS_CAPTURED, hasCapturedAnchor);
        writePoseToBundle(outState, STATE_CAPTURED_SHELF, capturedShelfPose);
        writePoseToBundle(outState, STATE_CAPTURED_CAMERA, capturedCameraPose);
        writePoseToBundle(outState, STATE_RESOLVED_REFERENCE, resolvedStoreReferencePose);
    }

    private void ensureStoreReferenceReadyAndPromptShelfName() {
        if (!ensureActiveStoreReference()) {
            new AlertDialog.Builder(this)
                    .setTitle("Create store reference first")
                    .setMessage("Save the store entrance reference first. After it is saved, come back and keep the camera open while onboarding shelves one by one.")
                    .setCancelable(false)
                    .setNegativeButton("Back", (dialog, which) -> finish())
                    .setPositiveButton("Open store reference", (dialog, which) -> startActivity(new android.content.Intent(this, StoreReferenceActivity.class)))
                    .show();
            return;
        }

        showShelfNameDialog("Name shelf", false, false);
    }

    private void showCurrentStoreReferenceHint() {
        if (!ensureActiveStoreReference()) {
            Toast.makeText(this, "Store reference not ready yet", Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(this, "Using store reference: " + activeStoreReference.getReferenceName(), Toast.LENGTH_SHORT).show();
    }

    private boolean ensureActiveStoreReference() {
        StoreReference preferredReference = storeReferenceRepository.getPreferredStoreReference();
        if (preferredReference == null || TextUtils.isEmpty(preferredReference.getCloudAnchorId())) {
            return false;
        }
        boolean changed = activeStoreReference == null || activeStoreReference.getId() != preferredReference.getId();
        activeStoreReference = preferredReference;
        storeReferenceRepository.setActiveStoreReference(preferredReference.getId());
        if (changed) {
            referenceResolveStarted = false;
            referenceResolveFinished = false;
            resolvedStoreReferencePose = null;
            if (resolvedStoreReferenceAnchor != null) {
                try {
                    resolvedStoreReferenceAnchor.detach();
                } catch (Exception ignore) {
                }
                resolvedStoreReferenceAnchor = null;
            }
            resetCapturedState(false);
        }
        initRouteRecorderForActiveReference();
        return true;
    }

    private void initRouteRecorderForActiveReference() {
        if (routeRepository == null || activeStoreReference == null) {
            routeRecorder = null;
            return;
        }
        if (routeRecorder == null || routeRecorder.getReferenceId() != activeStoreReference.getId()) {
            routeRecorder = new OnboardingRouteRecorder(routeRepository, telemetryRepository, activeStoreReference.getId());
        }
    }

    private void showPostSaveOptions() {
        String areaLabel = activeStoreReference != null
                ? activeStoreReference.getReferenceName()
                : "the active store reference";
        new AlertDialog.Builder(this)
                .setTitle("Shelf saved")
                .setMessage("This shelf was saved under " + areaLabel + ". Keep the same AR session open and add the next shelf, or finish for now.")
                .setCancelable(false)
                .setNegativeButton("Done", (dialog, which) -> finish())
                .setPositiveButton("Add next shelf", (dialog, which) -> {
                    resetCapturedState(true);
                    refreshCaptureState();
                    showShelfNameDialog("Name next shelf", false, true);
                    Toast.makeText(this,
                            "The same store reference lock is still active. Move to the next shelf and capture when ready.",
                            Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void showShelfNameDialogIfNeeded() {
        if (hasShelfName()) {
            return;
        }
        showShelfNameDialog("Name this shelf", true, false);
    }

    private void showShelfNameDialog(String title, boolean finishOnBack, boolean promptNextShelf) {
        EditText input = new EditText(this);
        input.setHint("Shelf name");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        input.setPadding(padding, padding, padding, padding);
        String existingName = binding.etShelfName.getText() != null
                ? binding.etShelfName.getText().toString().trim() : "";
        input.setText(existingName);
        if (!TextUtils.isEmpty(existingName)) {
            input.setSelection(existingName.length());
        }

        String areaLabel = activeStoreReference != null
                ? activeStoreReference.getReferenceName()
                : "the active store reference";
        String message = promptNextShelf
                ? "Store reference is still locked: " + areaLabel + ". Enter the next shelf name and keep walking in the same AR session."
                : "Store reference: " + areaLabel + ". Enter the shelf name to keep this shelf linked to the live route.";

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int containerPad = (int) (16 * getResources().getDisplayMetrics().density);
        container.setPadding(containerPad, containerPad, containerPad, 0);
        TextView nameLabel = new TextView(this);
        nameLabel.setText("Shelf name");
        nameLabel.setTextSize(14f);
        nameLabel.setPadding(0, 0, 0, containerPad / 2);
        container.addView(nameLabel);
        container.addView(input);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setView(container)
                .setCancelable(!finishOnBack)
                .setNegativeButton(finishOnBack ? "Back" : "Cancel", (d, which) -> {
                    if (finishOnBack) {
                        finish();
                    }
                })
                .setPositiveButton(promptNextShelf ? "Continue" : "Use this name", null)
                .show();

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String shelfName = input.getText() != null ? input.getText().toString().trim() : "";
            if (TextUtils.isEmpty(shelfName)) {
                input.setError("Enter shelf name");
                return;
            }
            binding.etShelfName.setText(shelfName);
            binding.etShelfName.clearFocus();
            refreshCaptureState();
            dialog.dismiss();
        });
    }




    private void initRouteRecorderIfNeeded() {
        initRouteRecorderForActiveReference();
    }

    private long maybeRecordRouteCheckpoint(Pose absoluteCameraPose, boolean force, String kind) {
        if (absoluteCameraPose == null || resolvedStoreReferencePose == null) {
            return 0L;
        }
        initRouteRecorderIfNeeded();
        if (routeRecorder == null) {
            return 0L;
        }
        Pose relativeCameraPose = resolvedStoreReferencePose.inverse().compose(absoluteCameraPose);
        return routeRecorder.maybeRecord(
                relativeCameraPose,
                DEFAULT_ROUTE_LABEL,
                force,
                kind,
                computeRouteSceneQualityScore(force, kind),
                computeRouteStabilityScore(force, kind)
        );
    }

    private int computeRouteSceneQualityScore(boolean force, String kind) {
        Session.FeatureMapQuality quality = (force && "SHELF".equalsIgnoreCase(kind))
                ? capturedBestRecentQuality
                : bestRecentFeatureMapQuality;
        if (quality == Session.FeatureMapQuality.GOOD) {
            return 3;
        }
        if (quality == Session.FeatureMapQuality.SUFFICIENT) {
            return 2;
        }
        if (liveFeatureMapQuality == Session.FeatureMapQuality.SUFFICIENT
                || liveFeatureMapQuality == Session.FeatureMapQuality.GOOD) {
            return 1;
        }
        return 0;
    }

    private float computeRouteStabilityScore(boolean force, String kind) {
        int cameraSamples = stableCameraPoseHistory != null ? stableCameraPoseHistory.size() : 0;
        int shelfSamples = stableShelfPoseHistory != null ? stableShelfPoseHistory.size() : 0;
        int effectiveSamples = (force && "SHELF".equalsIgnoreCase(kind))
                ? Math.min(cameraSamples, Math.max(1, shelfSamples))
                : cameraSamples;
        float sampleScore = Math.min(1f, effectiveSamples / (float) Math.max(1, MIN_STABLE_POSE_SAMPLES + 2));
        float postureScore = orientationHelper != null && orientationHelper.hasSensorReading()
                ? (orientationHelper.isCaptureReadyPosture() ? 1f : 0.52f)
                : 0.45f;
        float alignmentScore = targetCenteredNow ? 1f : 0.55f;
        return Math.max(0f, Math.min(1f, (sampleScore * 0.50f) + (postureScore * 0.30f) + (alignmentScore * 0.20f)));
    }

    private void showExitWarningDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Leave onboarding?")
                .setMessage("If you leave now, you must rescan the store reference before you can continue the remaining shelves later.")
                .setNegativeButton("Keep mapping", null)
                .setPositiveButton("Leave now", (dialog, which) -> finish())
                .show();
    }

    private boolean hasShelfName() {
        return !TextUtils.isEmpty(binding.etShelfName.getText() != null
                ? binding.etShelfName.getText().toString().trim() : "");
    }

    private Frame getCurrentFrame() {
        return arFragment != null && arFragment.getArSceneView() != null
                ? arFragment.getArSceneView().getArFrame() : null;
    }

    private Session getCurrentSession() {
        return arFragment != null && arFragment.getArSceneView() != null
                ? arFragment.getArSceneView().getSession() : null;
    }

    private boolean isTrackingReady(Frame frame) {
        return frame != null && frame.getCamera() != null
                && frame.getCamera().getTrackingState() == TrackingState.TRACKING;
    }

    private String getQualityLabel(long now) {
        if (isQualityReadyForCapture(now)) {
            return bestRecentFeatureMapQuality == Session.FeatureMapQuality.GOOD ? "Strong" : "Usable";
        }
        if (liveFeatureMapQuality == Session.FeatureMapQuality.GOOD) {
            return "Strong";
        }
        if (liveFeatureMapQuality == Session.FeatureMapQuality.SUFFICIENT || isQualityUsableRightNow(now)) {
            return "Usable";
        }
        if (hadGoodEnoughConfidenceAtCapture()) {
            return "Captured usable";
        }
        return "Building";
    }

    private String getCaptureButtonText(long now, boolean enabled) {
        if (hasCapturedAnchor) {
            return "Captured";
        }
        if (!enabled) {
            if (!hasShelfName()) return "Name shelf first";
            if (!isTrackingReady(getCurrentFrame())) return "Move phone slowly";
            if (activeStoreReference == null || resolvedStoreReferencePose == null) return "Find store reference";
            if (!orientationHelper.isCaptureReadyPosture()) return "Hold phone straight";
            if (!targetCenteredNow) return "Frame full shelf";
            if (!isAlignmentHeldLongEnough(now)) return "Hold still";
            return "Almost ready";
        }
        return "Capture shelf";
    }

    private String safeMessage(String message) {
        return TextUtils.isEmpty(message) ? "Save failed" : message;
    }

    private void setCenterGuideColor(int color) {
        binding.viewAlignmentDot.setBackgroundColor(color);
        binding.tvReticle.setTextColor(color);
        binding.tvFrameCornerTopLeft.setTextColor(color);
        binding.tvFrameCornerTopRight.setTextColor(color);
        binding.tvFrameCornerBottomLeft.setTextColor(color);
        binding.tvFrameCornerBottomRight.setTextColor(color);
    }
}