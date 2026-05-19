package com.salesdairy.shelfarapp.activities;

import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Log;
import android.view.PixelCopy;
import android.view.View;
import android.widget.EditText;
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
import com.salesdairy.shelfarapp.databinding.ActivityStoreReferenceBinding;
import com.salesdairy.shelfarapp.models.StoreReference;
import com.salesdairy.shelfarapp.onboarding.StoreReferenceUi;
import com.salesdairy.shelfarapp.sensors.OrientationHelper;
import com.salesdairy.shelfarapp.utils.Constants;
import com.salesdairy.shelfarapp.utils.CrashLogRepository;
import com.salesdairy.shelfarapp.utils.ImageUtils;
import com.salesdairy.shelfarapp.utils.PermissionUtils;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.Deque;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public class StoreReferenceActivity extends AppCompatActivity implements FragmentOnAttachListener,
        BaseArFragment.OnSessionConfigurationListener,
        ArFragment.OnViewCreatedListener {

    private static final String TAG = "ShelfARFlow";
    private static final String STORE_AR_TAG = "store_ar_fragment";

    private static final float[] RETICLE_SEARCH_STEPS_DP = new float[]{0f, 18f, 36f, 56f, 80f};
    private static final float POSTURE_ICON_MAX_SHIFT_DP = 18f;

    private static final int COLOR_READY = Color.parseColor("#22C55E");
    private static final int COLOR_WARN = Color.parseColor("#38BDF8");
    private static final int COLOR_MUTED = Color.parseColor("#94A3B8");
    private static final int COLOR_ERROR = Color.parseColor("#F97316");

    private static final long ALIGN_HOLD_REQUIRED_MS = 90L;
    private static final long FEATURE_HISTORY_WINDOW_MS = 3500L;
    private static final long QUALITY_MUST_BE_RECENT_WITHIN_MS = 2200L;
    private static final long QUALITY_MUST_BE_STABLE_FOR_MS = 350L;
    private static final int MIN_RECENT_SAMPLES_FOR_CONFIDENCE = 2;
    private static final int MIN_SUFFICIENT_OR_GOOD_SAMPLES_FOR_CONFIDENCE = 1;
    private static final long QUALITY_LOG_INTERVAL_MS = 1000L;
    private static final float STORE_REFERENCE_PREVIEW_CROP_SCALE = 0.62f;

    private static final String STATE_REFERENCE_NAME = "state_reference_name";
    private static final String STATE_REFERENCE_HINT = "state_reference_hint";
    private static final String STATE_SAVING = "state_saving";

    private ActivityStoreReferenceBinding binding;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final CloudAnchorHelper cloudAnchorHelper = new CloudAnchorHelper();
    private final AtomicBoolean arBootstrapInProgress = new AtomicBoolean(false);
    private final Deque<FeatureQualitySample> featureQualityHistory = new ArrayDeque<>();

    private StoreReferenceRepository repository;
    private ARCoreManager arCoreManager;
    private OrientationHelper orientationHelper;
    private ArFragment arFragment;
    private StoreReferenceUi storeUi;

    private String referenceName = "Store entry point";
    private String referenceHint = "Save one entrance detail every auditor can find again";
    private Pose candidatePose;
    private boolean saving;
    private boolean userRequestedInstall = true;
    private boolean preferredCameraConfigApplied = false;
    private boolean sceneUpdateListenerAttached = false;
    private boolean wasTrackingReady = false;
    private boolean wasCandidateReady = false;

    private boolean targetCenteredNow = false;
    private long alignedSinceElapsedMs = 0L;
    private long lastSufficientOrBetterElapsedMs = 0L;
    private long stableEnoughSinceElapsedMs = 0L;
    private long lastQualityLogElapsedMs = 0L;

    private Session.FeatureMapQuality liveFeatureMapQuality = Session.FeatureMapQuality.INSUFFICIENT;
    private Session.FeatureMapQuality bestRecentFeatureMapQuality = Session.FeatureMapQuality.INSUFFICIENT;

    private static class FeatureQualitySample {
        final long timestampMs;
        final Session.FeatureMapQuality quality;

        FeatureQualitySample(long timestampMs, Session.FeatureMapQuality quality) {
            this.timestampMs = timestampMs;
            this.quality = quality != null ? quality : Session.FeatureMapQuality.INSUFFICIENT;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "Store reference onboarding onCreate start");

        binding = ActivityStoreReferenceBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        storeUi = new StoreReferenceUi(binding);
        CrashLogRepository.noteBreadcrumb(this, "StoreReferenceActivity onCreate");

        if (savedInstanceState != null) {
            referenceName = savedInstanceState.getString(STATE_REFERENCE_NAME, referenceName);
            referenceHint = savedInstanceState.getString(STATE_REFERENCE_HINT, referenceHint);
            boolean wasSaving = savedInstanceState.getBoolean(STATE_SAVING, false);
            saving = false;
            if (wasSaving) {
                Toast.makeText(this, "Store reference save was interrupted. Re-check the reference and save again.", Toast.LENGTH_SHORT).show();
            }
        }

        repository = new StoreReferenceRepository(this);
        arCoreManager = new ARCoreManager();
        orientationHelper = new OrientationHelper(this);
        getSupportFragmentManager().addFragmentOnAttachListener(this);

        Fragment existing = getSupportFragmentManager().findFragmentByTag(STORE_AR_TAG);
        if (existing instanceof ArFragment) {
            arFragment = (ArFragment) existing;
            bindArListeners();
            Log.d(TAG, "Store reference found existing AR fragment by tag");
        } else {
            Log.d(TAG, "Store reference onboarding will attach AR fragment later after ARCore checks");
        }

        binding.btnBack.setOnClickListener(v -> finish());
        binding.btnSaveReferencePoint.setOnClickListener(v -> hostStoreReference());
        binding.btnEditLabels.setOnClickListener(v -> showLabelDialog());

        binding.tvReferenceTitle.setText(referenceName);
        binding.tvReferenceHint.setText(referenceHint);
        binding.tvTrackingHint.setText("Save a clear, named entrance point so the next auditor knows exactly where to stand.");
        binding.tvCenterHint.setText("Keep one clear entrance detail inside the frame");
        updatePostureBadge(false);
        updateCenterGuide(false);
        storeUi.setInitialButtonState();
        updateProgressSteps(false, false, false, false, false);
    }

    @Override
    public void onAttachFragment(@NonNull FragmentManager fragmentManager, @NonNull Fragment fragment) {
        Log.d(TAG, "Store reference onboarding onAttachFragment fragment=" + fragment.getClass().getSimpleName() + " id=" + fragment.getId());
        if (binding != null && fragment.getId() == binding.arFragmentContainer.getId() && fragment instanceof ArFragment) {
            arFragment = (ArFragment) fragment;
            bindArListeners();
        }
    }

    private void bindArListeners() {
        if (arFragment == null) {
            Log.w(TAG, "bindArListeners ignored because arFragment is null");
            return;
        }
        arFragment.setOnSessionConfigurationListener(this);
        arFragment.setOnViewCreatedListener(this);
        Log.d(TAG, "Binding store-reference AR listeners");
    }

    private void attachArFragmentIfNeeded() {
        if (isFinishing() || isDestroyed()) {
            Log.w(TAG, "attachArFragmentIfNeeded aborted because activity is finishing/destroyed");
            return;
        }

        Fragment existingById = getSupportFragmentManager().findFragmentById(binding.arFragmentContainer.getId());
        if (existingById instanceof ArFragment) {
            arFragment = (ArFragment) existingById;
            bindArListeners();
            Log.d(TAG, "Store reference AR fragment already attached by container id");
            return;
        }

        Fragment existingByTag = getSupportFragmentManager().findFragmentByTag(STORE_AR_TAG);
        if (existingByTag instanceof ArFragment) {
            arFragment = (ArFragment) existingByTag;
            bindArListeners();
            Log.d(TAG, "Store reference AR fragment already attached by tag");
            return;
        }

        Log.d(TAG, "Attaching store-reference AR fragment now");
        ArFragment fragment = new ArFragment();
        getSupportFragmentManager()
                .beginTransaction()
                .replace(binding.arFragmentContainer.getId(), fragment, STORE_AR_TAG)
                .commitNowAllowingStateLoss();
        arFragment = fragment;
        bindArListeners();
        Log.d(TAG, "Store reference AR fragment attach complete");
    }

    @Override
    public void onViewCreated(ArSceneView arSceneView) {
        Log.d(TAG, "Store reference ArSceneView created width=" + arSceneView.getWidth() + " height=" + arSceneView.getHeight());
        if (!sceneUpdateListenerAttached) {
            arSceneView.getScene().addOnUpdateListener(frameTime -> refreshStoreReferenceState());
            sceneUpdateListenerAttached = true;
            Log.d(TAG, "Store reference scene update listener attached");
        }
    }

    @Override
    public void onSessionConfiguration(Session session, Config config) {
        Log.d(TAG, "Store reference onboarding onSessionConfiguration");
        maybeApplyPreferredCameraConfig(session);
        cloudAnchorHelper.enableCloudAnchors(config);
        config.setLightEstimationMode(Config.LightEstimationMode.DISABLED);
        config.setPlaneFindingMode(Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL);
        config.setUpdateMode(Config.UpdateMode.LATEST_CAMERA_IMAGE);
        config.setFocusMode(Config.FocusMode.AUTO);
        if (session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
            config.setDepthMode(Config.DepthMode.AUTOMATIC);
            Log.d(TAG, "Store reference depth mode AUTOMATIC enabled");
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
                Log.d(TAG, "Store reference onboarding applied preferred camera config TARGET_FPS_30");
            }
        } catch (Exception e) {
            Log.w(TAG, "Store reference failed to apply preferred camera config", e);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "Store reference onboarding onResume");
        CrashLogRepository.noteBreadcrumb(this, "StoreReferenceActivity onResume");
        orientationHelper.register();
        bootstrapArIfPossible();
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "Store reference onboarding onPause");
        CrashLogRepository.noteBreadcrumb(this, "StoreReferenceActivity onPause");
        orientationHelper.unregister();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Store reference onboarding onDestroy");
        CrashLogRepository.noteBreadcrumb(this, "StoreReferenceActivity onDestroy");
        cloudAnchorHelper.clear();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_REFERENCE_NAME, referenceName);
        outState.putString(STATE_REFERENCE_HINT, referenceHint);
        outState.putBoolean(STATE_SAVING, saving);
    }

    private void bootstrapArIfPossible() {
        if (arFragment != null) {
            return;
        }
        if (arBootstrapInProgress.getAndSet(true)) {
            Log.d(TAG, "bootstrapArIfPossible ignored because bootstrap already in progress");
            return;
        }

        try {
            if (!PermissionUtils.hasCameraPermission(this)) {
                Log.d(TAG, "Camera permission missing; requesting now");
                binding.tvTrackingHint.setText("Allow camera permission to start store reference setup");
                PermissionUtils.requestCameraPermission(this);
                arBootstrapInProgress.set(false);
                return;
            }

            binding.tvTrackingHint.setText("Checking ARCore support...");
            arCoreManager.checkAvailability(this, supported -> runOnUiThread(() -> {
                Log.d(TAG, "ARCore availability callback supported=" + supported);
                if (!supported) {
                    Toast.makeText(this, "This device does not support ARCore", Toast.LENGTH_LONG).show();
                    binding.tvTrackingHint.setText("This device does not support ARCore");
                    arBootstrapInProgress.set(false);
                    finish();
                    return;
                }
                try {
                    boolean installed = arCoreManager.requestInstall(this, userRequestedInstall);
                    Log.d(TAG, "ARCore requestInstall result installed=" + installed + " userRequestedInstall=" + userRequestedInstall);
                    if (!installed) {
                        userRequestedInstall = false;
                        binding.tvTrackingHint.setText("Finish ARCore setup, then return to store reference setup");
                        arBootstrapInProgress.set(false);
                        return;
                    }
                    attachArFragmentIfNeeded();
                    arBootstrapInProgress.set(false);
                } catch (Exception e) {
                    Log.e(TAG, "Store reference ARCore init failed", e);
                    ARCoreManager.showArError(this, e);
                    binding.tvTrackingHint.setText("ARCore start failed. Check logs.");
                    arBootstrapInProgress.set(false);
                }
            }));
        } catch (Exception e) {
            Log.e(TAG, "Store reference bootstrap failed", e);
            binding.tvTrackingHint.setText("AR startup failed. Check logs.");
            arBootstrapInProgress.set(false);
        }
    }


    @Override
    public void onLowMemory() {
        super.onLowMemory();
        CrashLogRepository.noteBreadcrumb(this, "StoreReferenceActivity onLowMemory");
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (level >= TRIM_MEMORY_RUNNING_LOW) {
            CrashLogRepository.noteBreadcrumb(this, "StoreReferenceActivity onTrimMemory level=" + level);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == Constants.CAMERA_PERMISSION_REQUEST_CODE || requestCode == 0) {
            boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            Log.d(TAG, "Store reference camera permission callback granted=" + granted);
            if (granted) {
                bootstrapArIfPossible();
            } else {
                binding.tvTrackingHint.setText("Camera permission denied");
                Toast.makeText(this, "Camera permission is required for store reference setup", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void refreshStoreReferenceState() {
        try {
            if (arFragment == null) {
                return;
            }
            ArSceneView sceneView = arFragment.getArSceneView();
            if (sceneView == null) {
                return;
            }
            Frame frame = sceneView.getArFrame();
            Session session = sceneView.getSession();
            long now = SystemClock.elapsedRealtime();
            boolean postureReady = orientationHelper.isCaptureReadyPosture();
            updatePostureBadge(postureReady);

            if (frame == null || frame.getCamera() == null || frame.getCamera().getTrackingState() != TrackingState.TRACKING) {
                resetTrackingDependentState();
                if (wasTrackingReady) {
                    Log.d(TAG, "Store reference onboarding tracking lost or not ready yet");
                }
                wasTrackingReady = false;
                binding.tvTrackingHint.setText("Move slowly until AR tracking is ready");
                binding.tvCenterHint.setText("Center a stable point like a tile corner or pillar edge");
                storeUi.setActionState(false, "Keep moving a little more");
                updateCenterGuide(false);
                updateProgressSteps(false, postureReady, false, false, false);
                return;
            }

            if (!wasTrackingReady) {
                Log.d(TAG, "Store reference tracking became ready");
            }
            wasTrackingReady = true;

            updateCandidatePose(frame, now);
            updateFeatureMapQuality(session, frame, now);
            maybeLogQualityState(now, postureReady);

            boolean candidateReady = candidatePose != null;
            boolean centeredReady = targetCenteredNow;
            boolean qualityReady = isQualityReadyForHost(now);
            boolean sweepReady = countSufficientOrGoodSamples() > 0 || isQualityUsableRightNow(now);
            boolean canHost = postureReady && candidateReady && centeredReady && qualityReady && !saving;

            storeUi.setActionState(canHost, canHost ? "Save store reference" : getStoreButtonText(now));
            updateCenterGuide(centeredReady);
            updateProgressSteps(true, postureReady, centeredReady, sweepReady, qualityReady);

            if (!postureReady) {
                binding.tvTrackingHint.setText(orientationHelper.getPostureGuidance());
                binding.tvCenterHint.setText("Hold the phone straight before locking the shared point");
            } else if (!candidateReady) {
                binding.tvTrackingHint.setText("Aim at one shared stable point the whole team can find again");
                binding.tvCenterHint.setText("Tile corner, pillar edge, fixture corner, or entry mark");
            } else if (!qualityReady) {
                binding.tvTrackingHint.setText(getStoreQualityGuidance(now));
                binding.tvCenterHint.setText("Keep the point centered and move slowly left-right for a stronger lock");
            } else {
                binding.tvTrackingHint.setText("Shared store reference ready. Keep it centered and save it.");
                binding.tvCenterHint.setText("Good lock. Save this shared point now.");
            }
        } catch (Exception e) {
            Log.e(TAG, "Store reference refresh failed", e);
            binding.tvTrackingHint.setText("Tracking update failed. Check logs.");
            storeUi.setActionState(false, "Keep moving a little more");
        }
    }

    private void resetTrackingDependentState() {
        targetCenteredNow = false;
        candidatePose = null;
        alignedSinceElapsedMs = 0L;
        liveFeatureMapQuality = Session.FeatureMapQuality.INSUFFICIENT;
        bestRecentFeatureMapQuality = Session.FeatureMapQuality.INSUFFICIENT;
        featureQualityHistory.clear();
        lastSufficientOrBetterElapsedMs = 0L;
        stableEnoughSinceElapsedMs = 0L;
        updateProgressSteps(false, orientationHelper.isCaptureReadyPosture(), false, false, false);
        wasCandidateReady = false;
    }

    private void updateCandidatePose(Frame frame, long now) {
        candidatePose = getBestReferencePoseNearCenter(frame);
        targetCenteredNow = candidatePose != null;
        if (targetCenteredNow) {
            if (alignedSinceElapsedMs == 0L) {
                alignedSinceElapsedMs = now;
            }
        } else {
            alignedSinceElapsedMs = 0L;
        }

        if (targetCenteredNow != wasCandidateReady) {
            Log.d(TAG, "Store reference candidate ready=" + targetCenteredNow);
            wasCandidateReady = targetCenteredNow;
        }
    }

    private void updateFeatureMapQuality(Session session, Frame frame, long now) {
        liveFeatureMapQuality = Session.FeatureMapQuality.INSUFFICIENT;
        if (session != null && frame != null && frame.getCamera() != null) {
            liveFeatureMapQuality = cloudAnchorHelper.getFeatureMapQuality(session, frame.getCamera().getPose());
        }

        featureQualityHistory.addLast(new FeatureQualitySample(now, liveFeatureMapQuality));
        while (!featureQualityHistory.isEmpty()
                && now - featureQualityHistory.peekFirst().timestampMs > FEATURE_HISTORY_WINDOW_MS) {
            featureQualityHistory.removeFirst();
        }

        bestRecentFeatureMapQuality = Session.FeatureMapQuality.INSUFFICIENT;
        long latestSufficientOrBetterTs = 0L;
        int sufficientOrGoodCount = 0;

        for (FeatureQualitySample sample : featureQualityHistory) {
            if (sample.quality == Session.FeatureMapQuality.GOOD) {
                bestRecentFeatureMapQuality = Session.FeatureMapQuality.GOOD;
                sufficientOrGoodCount++;
                latestSufficientOrBetterTs = sample.timestampMs;
            } else if (sample.quality == Session.FeatureMapQuality.SUFFICIENT) {
                if (bestRecentFeatureMapQuality != Session.FeatureMapQuality.GOOD) {
                    bestRecentFeatureMapQuality = Session.FeatureMapQuality.SUFFICIENT;
                }
                sufficientOrGoodCount++;
                latestSufficientOrBetterTs = sample.timestampMs;
            }
        }

        lastSufficientOrBetterElapsedMs = latestSufficientOrBetterTs;
        if (isRecentWindowStrongEnough(now, sufficientOrGoodCount)) {
            if (stableEnoughSinceElapsedMs == 0L) {
                stableEnoughSinceElapsedMs = now;
            }
        } else {
            stableEnoughSinceElapsedMs = 0L;
        }
    }

    private boolean isRecentWindowStrongEnough(long now, int sufficientOrGoodCount) {
        boolean recentEnough = lastSufficientOrBetterElapsedMs > 0L
                && (now - lastSufficientOrBetterElapsedMs) <= QUALITY_MUST_BE_RECENT_WITHIN_MS;
        boolean enoughSamples = featureQualityHistory.size() >= MIN_RECENT_SAMPLES_FOR_CONFIDENCE
                && sufficientOrGoodCount >= MIN_SUFFICIENT_OR_GOOD_SAMPLES_FOR_CONFIDENCE;
        return recentEnough
                && bestRecentFeatureMapQuality != Session.FeatureMapQuality.INSUFFICIENT
                && enoughSamples;
    }

    private boolean isQualityReadyForHost(long now) {
        return isRecentWindowStrongEnough(now, countSufficientOrGoodSamples())
                && stableEnoughSinceElapsedMs > 0L
                && (now - stableEnoughSinceElapsedMs) >= QUALITY_MUST_BE_STABLE_FOR_MS
                && isAlignmentHeldLongEnough(now);
    }

    private boolean isAlignmentHeldLongEnough(long now) {
        return alignedSinceElapsedMs > 0L && (now - alignedSinceElapsedMs) >= ALIGN_HOLD_REQUIRED_MS;
    }

    private int countSufficientOrGoodSamples() {
        int count = 0;
        for (FeatureQualitySample sample : featureQualityHistory) {
            if (sample.quality == Session.FeatureMapQuality.SUFFICIENT
                    || sample.quality == Session.FeatureMapQuality.GOOD) {
                count++;
            }
        }
        return count;
    }

    private boolean isQualityUsableRightNow(long now) {
        boolean liveUsable = liveFeatureMapQuality == Session.FeatureMapQuality.SUFFICIENT
                || liveFeatureMapQuality == Session.FeatureMapQuality.GOOD;
        boolean recentlyUsable = lastSufficientOrBetterElapsedMs > 0L
                && (now - lastSufficientOrBetterElapsedMs) <= QUALITY_MUST_BE_RECENT_WITHIN_MS;
        return liveUsable || recentlyUsable;
    }

    private String getStoreButtonText(long now) {
        if (!wasTrackingReady) {
            return "Wait for tracking";
        }
        if (!orientationHelper.isCaptureReadyPosture()) {
            return "Hold phone straight";
        }
        if (!targetCenteredNow || candidatePose == null) {
            return "Center point";
        }
        if (isQualityReadyForHost(now)) {
            return "Save store reference";
        }
        if (bestRecentFeatureMapQuality == Session.FeatureMapQuality.GOOD) {
            return "Hold steady to save";
        }
        if (bestRecentFeatureMapQuality == Session.FeatureMapQuality.SUFFICIENT || isQualityUsableRightNow(now)) {
            return "Hold steady";
        }
        return "Move a little more";
    }

    private String getStoreQualityGuidance(long now) {
        if (!isAlignmentHeldLongEnough(now)) {
            return "Keep the point centered and steady for a moment";
        }
        if (bestRecentFeatureMapQuality == Session.FeatureMapQuality.GOOD) {
            return "Lock looks strong. Hold steady and save now.";
        }
        if (bestRecentFeatureMapQuality == Session.FeatureMapQuality.SUFFICIENT || isQualityUsableRightNow(now)) {
            return "Lock is usable. Hold steady or move a little for an even stronger store reference.";
        }
        return "Move slowly around the shared point so ARCore can build a better map.";
    }

    private void maybeLogQualityState(long now, boolean postureReady) {
        if (now - lastQualityLogElapsedMs < QUALITY_LOG_INTERVAL_MS) {
            return;
        }
        lastQualityLogElapsedMs = now;
        Log.d(TAG, "Store FMQ"
                + " live=" + liveFeatureMapQuality
                + ", bestRecent=" + bestRecentFeatureMapQuality
                + ", histSz=" + featureQualityHistory.size()
                + ", sufficientCnt=" + countSufficientOrGoodSamples()
                + ", recentAgeMs=" + (lastSufficientOrBetterElapsedMs == 0L ? -1L : (now - lastSufficientOrBetterElapsedMs))
                + ", stableForMs=" + (stableEnoughSinceElapsedMs == 0L ? 0L : (now - stableEnoughSinceElapsedMs))
                + ", qualReady=" + isQualityReadyForHost(now)
                + ", centered=" + targetCenteredNow
                + ", posture=" + postureReady);
    }

    private Pose getBestReferencePoseNearCenter(Frame frame) {
        if (frame == null) {
            return null;
        }
        ArSceneView sceneView = arFragment != null ? arFragment.getArSceneView() : null;
        if (sceneView == null || sceneView.getWidth() <= 0 || sceneView.getHeight() <= 0) {
            return null;
        }

        float centerX = sceneView.getWidth() / 2f;
        float centerY = sceneView.getHeight() / 2f;
        float density = getResources().getDisplayMetrics().density;

        for (float dp : RETICLE_SEARCH_STEPS_DP) {
            float px = dp * density;
            Pose pose = findReferencePoseAt(frame, centerX, centerY, px);
            if (pose != null) {
                return pose;
            }
        }
        return null;
    }

    private Pose findReferencePoseAt(Frame frame, float centerX, float centerY, float offsetPx) {
        float[][] candidates = new float[][]{
                {centerX, centerY},
                {centerX - offsetPx, centerY}, {centerX + offsetPx, centerY},
                {centerX, centerY - offsetPx}, {centerX, centerY + offsetPx},
                {centerX - offsetPx, centerY - offsetPx},
                {centerX + offsetPx, centerY - offsetPx},
                {centerX - offsetPx, centerY + offsetPx},
                {centerX + offsetPx, centerY + offsetPx}
        };
        for (float[] candidate : candidates) {
            Pose pose = getPoseFromScreenPoint(frame, candidate[0], candidate[1]);
            if (pose != null) {
                return pose;
            }
        }
        return null;
    }

    private Pose getPoseFromScreenPoint(Frame frame, float x, float y) {
        try {
            List<HitResult> hits = frame.hitTest(x, y);
            for (HitResult hit : hits) {
                if (isValidReferenceHit(hit)) {
                    return hit.getHitPose();
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Store reference onboarding hitTest failed", e);
        }
        return null;
    }

    private boolean isValidReferenceHit(HitResult hit) {
        if (hit == null || hit.getTrackable() == null) {
            return false;
        }
        if (hit.getTrackable() instanceof Plane) {
            return ((Plane) hit.getTrackable()).getTrackingState() == TrackingState.TRACKING
                    && ((Plane) hit.getTrackable()).isPoseInPolygon(hit.getHitPose());
        }
        return hit.getTrackable() instanceof Point;
    }

    private void hostStoreReference() {
        Log.d(TAG, "hostStoreReference invoked");
        CrashLogRepository.noteBreadcrumb(this, "Store reference host start");
        if (saving) {
            Toast.makeText(this, "Store reference is already being saved.", Toast.LENGTH_SHORT).show();
            return;
        }
        final Session session;
        try {
            session = arFragment != null ? arFragment.getArSceneView().getSession() : null;
        } catch (Exception e) {
            Log.e(TAG, "Failed to fetch AR session for store reference hosting", e);
            Toast.makeText(this, "Could not start saving this store reference.", Toast.LENGTH_SHORT).show();
            return;
        }

        final Pose poseToHost = candidatePose;
        final long now = SystemClock.elapsedRealtime();
        if (session == null || poseToHost == null) {
            Log.w(TAG, "Store reference not ready session=" + (session != null) + " candidatePose=" + (poseToHost != null));
            Toast.makeText(this, "Store reference is not ready yet.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!orientationHelper.isCaptureReadyPosture()) {
            Toast.makeText(this, orientationHelper.getPostureGuidance(), Toast.LENGTH_SHORT).show();
            return;
        }
        if (!isQualityReadyForHost(now)) {
            Toast.makeText(this, getStoreQualityGuidance(now), Toast.LENGTH_SHORT).show();
            return;
        }

        saving = true;
        binding.loadingOverlay.setVisibility(View.VISIBLE);
        binding.tvLoadingMessage.setText("Saving store reference...");
        storeUi.setSavingState();

        try {
            final Anchor localAnchor = session.createAnchor(poseToHost);
            Log.d(TAG, "Local store reference anchor created; starting capture review");
            captureScreenshot(bitmap -> {
                Bitmap croppedBitmap = ImageUtils.cropCenterKeepingAspect(bitmap, STORE_REFERENCE_PREVIEW_CROP_SCALE);
                if (croppedBitmap != null && croppedBitmap != bitmap && bitmap != null && !bitmap.isRecycled()) {
                    bitmap.recycle();
                }
                runOnUiThread(() -> showReferenceCaptureReview(session, localAnchor, croppedBitmap));
            });
        } catch (Exception e) {
            Log.e(TAG, "Could not create or host store reference anchor", e);
            saving = false;
            binding.loadingOverlay.setVisibility(View.GONE);
            Toast.makeText(this, "Could not create store reference anchor.", Toast.LENGTH_LONG).show();
        }
    }

    private void showReferenceCaptureReview(Session session, Anchor localAnchor, Bitmap bitmap) {
        if (bitmap == null) {
            saving = false;
            binding.loadingOverlay.setVisibility(View.GONE);
            if (localAnchor != null) {
                try { localAnchor.detach(); } catch (Exception ignore) { }
            }
            Toast.makeText(this, "Could not capture entry point image.", Toast.LENGTH_LONG).show();
            return;
        }

        android.widget.ImageView preview = new android.widget.ImageView(this);
        preview.setImageBitmap(bitmap);
        preview.setAdjustViewBounds(true);
        preview.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
        preview.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                (int) (300 * getResources().getDisplayMetrics().density)));
        int pad = (int) (16 * getResources().getDisplayMetrics().density);

        android.widget.LinearLayout content = new android.widget.LinearLayout(this);
        content.setOrientation(android.widget.LinearLayout.VERTICAL);
        content.setPadding(pad, pad, pad, 0);

        android.widget.TextView hint = new android.widget.TextView(this);
        hint.setText("Check the focused entry photo before you save it.");
        hint.setTextColor(android.graphics.Color.parseColor("#CBD5E1"));
        hint.setTextSize(14f);
        content.addView(hint);
        content.addView(preview);

        new AlertDialog.Builder(this)
                .setTitle("Use this entry photo?")
                .setView(content)
                .setNegativeButton("Retake", (dialog, which) -> {
                    if (localAnchor != null) {
                        try { localAnchor.detach(); } catch (Exception ignore) { }
                    }
                    if (!bitmap.isRecycled()) {
                        bitmap.recycle();
                    }
                    saving = false;
                    binding.loadingOverlay.setVisibility(View.GONE);
                    storeUi.setActionState(true, "Capture entry point");
                })
                .setPositiveButton("Save", (dialog, which) -> confirmStoreReferenceSave(session, localAnchor, bitmap))
                .setOnCancelListener(dialog -> {
                    if (localAnchor != null) {
                        try { localAnchor.detach(); } catch (Exception ignore) { }
                    }
                    if (!bitmap.isRecycled()) {
                        bitmap.recycle();
                    }
                    saving = false;
                    binding.loadingOverlay.setVisibility(View.GONE);
                    storeUi.setActionState(true, "Capture entry point");
                })
                .show();
    }

    private void confirmStoreReferenceSave(Session session, Anchor localAnchor, Bitmap bitmap) {
        try {
            final String imagePath = ImageUtils.saveBitmapToFile(StoreReferenceActivity.this, bitmap);
            if (!bitmap.isRecycled()) {
                bitmap.recycle();
            }
            Log.d(TAG, "Store reference screenshot saved pathEmpty=" + TextUtils.isEmpty(imagePath));
            cloudAnchorHelper.hostAnchor(session, localAnchor, Constants.CLOUD_ANCHOR_TTL_DAYS, new CloudAnchorHelper.HostListener() {
                @Override
                public void onHostSuccess(Anchor anchor, String cloudAnchorId) {
                    Log.d(TAG, "Store reference cloud anchor hosted successfully id=" + cloudAnchorId);
                    CrashLogRepository.noteBreadcrumb(StoreReferenceActivity.this, "Store reference host success");
                    runOnUiThread(() -> {
                        StoreReference reference = new StoreReference();
                        reference.setOutletId(Constants.DEFAULT_OUTLET_ID);
                        reference.setReferenceScopeName("Store");
                        reference.setReferenceName(referenceName);
                        reference.setHint(referenceHint);
                        reference.setImagePath(imagePath);
                        reference.setCloudAnchorId(cloudAnchorId);
                        reference.setCloudAnchorStatus(Anchor.CloudAnchorState.SUCCESS.name());
                        reference.setCloudAnchorTtlDays(Constants.CLOUD_ANCHOR_TTL_DAYS);
                        reference.setCloudAnchorHostedAt(System.currentTimeMillis());
                        reference.setCreatedAt(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date()));
                        reference.setActive(true);
                        repository.insertStoreReference(reference);
                        saving = false;
                        binding.loadingOverlay.setVisibility(View.GONE);
                        Toast.makeText(StoreReferenceActivity.this, "Entry point saved. Continue with shelf onboarding.", Toast.LENGTH_LONG).show();
                        android.content.Intent next = new android.content.Intent(StoreReferenceActivity.this, OnboardShelfActivity.class);
                        next.putExtra("open_name_prompt", true);
                        startActivity(next);
                        finish();
                    });
                }

                @Override
                public void onHostFailure(Anchor.CloudAnchorState state, String message) {
                    Log.e(TAG, "Store reference cloud anchor host failed state=" + state + " message=" + message);
                    runOnUiThread(() -> {
                        saving = false;
                        binding.loadingOverlay.setVisibility(View.GONE);
                        String toastMessage = message == null ? String.valueOf(state) : message;
                        if (state == Anchor.CloudAnchorState.ERROR_NOT_AUTHORIZED) {
                            toastMessage = "Cloud Anchor auth failed. Keep TTL at 1 day and verify API key setup.";
                        }
                        Toast.makeText(StoreReferenceActivity.this,
                                "Entry point save failed: " + toastMessage,
                                Toast.LENGTH_LONG).show();
                    });
                }
            });
        } catch (IOException e) {
            Log.e(TAG, "Failed to save store reference image", e);
            saving = false;
            binding.loadingOverlay.setVisibility(View.GONE);
            Toast.makeText(StoreReferenceActivity.this, "Failed to save entry point image.", Toast.LENGTH_LONG).show();
        }
    }

    private void captureScreenshot(BitmapReadyListener listener) {
        final ArSceneView sceneView;
        try {
            sceneView = arFragment != null ? arFragment.getArSceneView() : null;
        } catch (Exception e) {
            Log.e(TAG, "Failed to access ArSceneView for screenshot", e);
            listener.onReady(null);
            return;
        }

        if (sceneView == null) {
            Log.w(TAG, "captureScreenshot returning null because ArSceneView is null");
            listener.onReady(null);
            return;
        }

        int width = sceneView.getWidth();
        int height = sceneView.getHeight();
        if (width <= 0 || height <= 0) {
            Log.w(TAG, "captureScreenshot returning null because view size is invalid width=" + width + " height=" + height);
            listener.onReady(null);
            return;
        }

        final Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        PixelCopy.request(sceneView, bitmap, copyResult -> {
            CrashLogRepository.noteBreadcrumb(this, "Store reference PixelCopy result=" + copyResult);
            Log.d(TAG, "PixelCopy result=" + copyResult);
            if (copyResult == PixelCopy.SUCCESS) {
                listener.onReady(bitmap);
            } else {
                if (!bitmap.isRecycled()) {
                    bitmap.recycle();
                }
                listener.onReady(null);
            }
        }, handler);
    }

    private void showLabelDialog() {
        EditText referenceInput = new EditText(this);
        referenceInput.setHint("Entry point name");
        referenceInput.setText(referenceName);
        referenceInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        referenceInput.setSingleLine(true);

        android.widget.LinearLayout container = new android.widget.LinearLayout(this);
        container.setOrientation(android.widget.LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        container.setPadding(pad, pad, pad, pad);

        android.widget.TextView help = new android.widget.TextView(this);
        help.setText("Use a clear name like Entrance point, Front desk, or Gallery area.");
        help.setTextColor(android.graphics.Color.parseColor("#64748B"));
        help.setTextSize(13f);
        help.setPadding(0, 0, 0, (int) (10 * getResources().getDisplayMetrics().density));
        container.addView(help);
        container.addView(referenceInput);

        new AlertDialog.Builder(this)
                .setTitle("Edit entry point name")
                .setView(container)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (dialog, which) -> {
                    String value = referenceInput.getText() != null ? referenceInput.getText().toString().trim() : "";
                    if (!TextUtils.isEmpty(value)) {
                        referenceName = value;
                    }
                    binding.tvReferenceTitle.setText(referenceName);
                    binding.tvReferenceHint.setText(referenceHint);
                    Log.d(TAG, "Store reference name updated name=" + referenceName);
                })
                .show();
    }

    private void updatePostureBadge(boolean postureReady) {
        float density = getResources().getDisplayMetrics().density;
        float shiftX = orientationHelper.getOverlayOffsetX() * POSTURE_ICON_MAX_SHIFT_DP * density;
        float shiftY = orientationHelper.getOverlayOffsetY() * POSTURE_ICON_MAX_SHIFT_DP * density;
        binding.tvPostureActiveCross.setTranslationX(shiftX);
        binding.tvPostureActiveCross.setTranslationY(shiftY);
        if (!orientationHelper.hasSensorReading()) {
            binding.tvPostureLabel.setText("ALIGN");
            binding.tvPostureLabel.setTextColor(COLOR_MUTED);
        } else if (orientationHelper.isStrictlyUpright()) {
            binding.tvPostureLabel.setText("GREAT");
            binding.tvPostureLabel.setTextColor(COLOR_READY);
        } else if (postureReady) {
            binding.tvPostureLabel.setText("READY");
            binding.tvPostureLabel.setTextColor(COLOR_WARN);
        } else {
            binding.tvPostureLabel.setText("ALIGN");
            binding.tvPostureLabel.setTextColor(COLOR_MUTED);
        }
    }

    private void updateCenterGuide(boolean candidateReady) {
        int color = candidateReady ? COLOR_READY : COLOR_MUTED;
        if (candidateReady && !isQualityUsableRightNow(SystemClock.elapsedRealtime())) {
            color = COLOR_WARN;
        } else if (!candidateReady && wasTrackingReady) {
            color = COLOR_ERROR;
        }
        binding.tvReticle.setTextColor(color);
        binding.viewAlignmentDot.setBackgroundColor(color);
        binding.tvFrameCornerTopLeft.setTextColor(color);
        binding.tvFrameCornerTopRight.setTextColor(color);
        binding.tvFrameCornerBottomLeft.setTextColor(color);
        binding.tvFrameCornerBottomRight.setTextColor(color);
    }

    private void updateProgressSteps(boolean trackingReady,
                                     boolean postureReady,
                                     boolean centeredReady,
                                     boolean sweepReady,
                                     boolean qualityReady) {
        setStepColor(binding.stepTrack, trackingReady, trackingReady);
        setStepColor(binding.stepPosture, postureReady, trackingReady);
        setStepColor(binding.stepCenter, centeredReady, trackingReady && postureReady);
        setStepColorAdvisory(binding.stepMotionBonus, sweepReady, trackingReady && postureReady && centeredReady);
        setStepColor(binding.stepQuality, qualityReady, trackingReady && postureReady && centeredReady);
    }

    private void setStepColor(View dot, boolean done, boolean active) {
        if (dot == null) return;
        if (done) {
            dot.setBackgroundColor(COLOR_READY);
        } else if (active) {
            dot.setBackgroundColor(COLOR_WARN);
        } else {
            dot.setBackgroundColor(COLOR_MUTED);
        }
    }

    private void setStepColorAdvisory(View dot, boolean achieved, boolean possible) {
        if (dot == null) return;
        if (achieved) {
            dot.setBackgroundColor(COLOR_READY);
        } else if (possible) {
            dot.setBackgroundColor(COLOR_ERROR);
        } else {
            dot.setBackgroundColor(COLOR_MUTED);
        }
    }

    interface BitmapReadyListener {
        void onReady(Bitmap bitmap);
    }
}
