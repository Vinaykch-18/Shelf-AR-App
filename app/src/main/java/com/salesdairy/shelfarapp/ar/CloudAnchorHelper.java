package com.salesdairy.shelfarapp.ar;

import android.util.Log;

import com.google.ar.core.Anchor;
import com.google.ar.core.Config;
import com.google.ar.core.FutureState;
import com.google.ar.core.HostCloudAnchorFuture;
import com.google.ar.core.Pose;
import com.google.ar.core.ResolveCloudAnchorFuture;
import com.google.ar.core.Session;

import java.util.Locale;
import java.util.function.BiConsumer;

public class CloudAnchorHelper {

    private static final String TAG = "ShelfARFlow";

    private final Object lock = new Object();

    private Anchor hostedAnchor;
    private Anchor resolvedAnchor;
    private HostCloudAnchorFuture hostFuture;
    private ResolveCloudAnchorFuture resolveFuture;

    private boolean cleared = false;
    private boolean resolveInFlight = false;
    private int resolveToken = 0;

    public interface HostListener {
        void onHostSuccess(Anchor anchor, String cloudAnchorId);
        void onHostFailure(Anchor.CloudAnchorState state, String message);
    }

    public interface ResolveListener {
        void onResolveSuccess(Anchor anchor);
        void onResolveFailure(Anchor.CloudAnchorState state, String message);
    }

    public static class ResolveFutureSnapshot {
        public final FutureState futureState;
        public final Anchor.CloudAnchorState cloudState;
        public final Anchor anchor;

        public ResolveFutureSnapshot(FutureState futureState,
                                     Anchor.CloudAnchorState cloudState,
                                     Anchor anchor) {
            this.futureState = futureState;
            this.cloudState = cloudState;
            this.anchor = anchor;
        }
    }

    public void enableCloudAnchors(Config config) {
        if (config != null) {
            config.setCloudAnchorMode(Config.CloudAnchorMode.ENABLED);
            Log.d(TAG, "CloudAnchorHelper.enableCloudAnchors: cloud mode enabled");
        } else {
            Log.w(TAG, "CloudAnchorHelper.enableCloudAnchors: config was null");
        }
    }

    public Session.FeatureMapQuality getFeatureMapQuality(Session session, Pose pose) {
        if (session == null || pose == null) {
            Log.w(TAG, "getFeatureMapQuality: session or pose null");
            return Session.FeatureMapQuality.INSUFFICIENT;
        }

        try {
            return session.estimateFeatureMapQualityForHosting(pose);
        } catch (Exception exception) {
            Log.w(TAG, "estimateFeatureMapQualityForHosting failed", exception);
            return Session.FeatureMapQuality.INSUFFICIENT;
        }
    }

    public boolean isFeatureMapGoodEnough(Session.FeatureMapQuality quality) {
        return quality == Session.FeatureMapQuality.SUFFICIENT
                || quality == Session.FeatureMapQuality.GOOD;
    }

    public String getFeatureMapGuidance(Session.FeatureMapQuality quality) {
        if (quality == Session.FeatureMapQuality.GOOD) {
            return "Exact lock looks strong.";
        }

        if (quality == Session.FeatureMapQuality.SUFFICIENT) {
            return "Exact lock is usable. Save now or move a little more for better accuracy.";
        }

        return "Aim at shelf edges, labels, and corners. Move left-right slowly. Avoid mostly shiny floor.";
    }

    public void hostAnchor(Session session, Anchor localAnchor, int ttlDays, HostListener listener) {
        if (session == null || localAnchor == null) {
            Log.e(TAG, "hostAnchor: session or localAnchor null");
            if (listener != null) {
                listener.onHostFailure(Anchor.CloudAnchorState.ERROR_INTERNAL, "Session or anchor is null");
            }
            return;
        }

        try {
            hostedAnchor = localAnchor;
            Log.d(TAG, "hostAnchor: starting async host, ttlDays=" + ttlDays
                    + ", pose=" + poseToShortString(localAnchor.getPose()));

            hostFuture = session.hostCloudAnchorAsync(
                    localAnchor,
                    ttlDays,
                    new BiConsumer<String, Anchor.CloudAnchorState>() {
                        @Override
                        public void accept(String cloudAnchorId, Anchor.CloudAnchorState state) {
                            Log.d(TAG, "hostAnchor callback: state=" + state
                                    + ", cloudAnchorId=" + safeAnchorId(cloudAnchorId));

                            if (listener == null) {
                                return;
                            }

                            if (state == Anchor.CloudAnchorState.SUCCESS
                                    && cloudAnchorId != null
                                    && !cloudAnchorId.trim().isEmpty()) {
                                listener.onHostSuccess(localAnchor, cloudAnchorId.trim());
                            } else if (state != Anchor.CloudAnchorState.TASK_IN_PROGRESS) {
                                listener.onHostFailure(state, toReadableMessage(state));
                            }
                        }
                    }
            );

            Log.d(TAG, "hostAnchor: async host request submitted");
        } catch (Exception exception) {
            Log.e(TAG, "hostAnchor failed", exception);
            if (listener != null) {
                listener.onHostFailure(Anchor.CloudAnchorState.ERROR_INTERNAL, exception.getMessage());
            }
        }
    }

    public void resolveAnchor(Session session, String cloudAnchorId, ResolveListener listener) {
        if (session == null || cloudAnchorId == null || cloudAnchorId.trim().isEmpty()) {
            Log.e(TAG, "resolveAnchor: missing session or cloud anchor id");
            if (listener != null) {
                listener.onResolveFailure(
                        Anchor.CloudAnchorState.ERROR_CLOUD_ID_NOT_FOUND,
                        "Cloud Anchor ID missing"
                );
            }
            return;
        }

        final String trimmedId = cloudAnchorId.trim();
        final int currentToken;

        synchronized (lock) {
            if (resolveInFlight) {
                Log.w(TAG, "resolveAnchor: ignored because another resolve is already in flight");
                return;
            }

            cleared = false;
            resolveInFlight = true;
            resolveToken++;
            currentToken = resolveToken;
        }

        try {
            Log.d(TAG, "resolveAnchor: starting async resolve for id=" + safeAnchorId(trimmedId)
                    + ", rawLength=" + cloudAnchorId.length()
                    + ", trimmedLength=" + trimmedId.length()
                    + ", changedByTrim=" + !cloudAnchorId.equals(trimmedId)
                    + ", token=" + currentToken);

            resolveFuture = session.resolveCloudAnchorAsync(
                    trimmedId,
                    new BiConsumer<Anchor, Anchor.CloudAnchorState>() {
                        @Override
                        public void accept(Anchor anchor, Anchor.CloudAnchorState state) {
                            boolean staleCallback;

                            synchronized (lock) {
                                staleCallback = cleared || currentToken != resolveToken;
                                if (!staleCallback && state != Anchor.CloudAnchorState.TASK_IN_PROGRESS) {
                                    resolveInFlight = false;
                                }
                            }

                            Log.d(TAG, "resolveAnchor callback: token=" + currentToken
                                    + ", state=" + state
                                    + ", stale=" + staleCallback
                                    + ", anchorNull=" + (anchor == null)
                                    + (anchor != null ? ", pose=" + poseToShortString(anchor.getPose()) : ""));

                            if (staleCallback) {
                                if (anchor != null) {
                                    try {
                                        anchor.detach();
                                    } catch (Exception ignore) {
                                    }
                                }
                                return;
                            }

                            if (listener == null) {
                                return;
                            }

                            if (state == Anchor.CloudAnchorState.SUCCESS && anchor != null) {
                                resolvedAnchor = anchor;
                                listener.onResolveSuccess(anchor);
                            } else if (state != Anchor.CloudAnchorState.TASK_IN_PROGRESS) {
                                listener.onResolveFailure(state, toReadableMessage(state));
                            }
                        }
                    }
            );

            Log.d(TAG, "resolveAnchor: async resolve request submitted, token=" + currentToken);
        } catch (Exception exception) {
            synchronized (lock) {
                if (currentToken == resolveToken) {
                    resolveInFlight = false;
                }
            }

            Log.e(TAG, "resolveAnchor failed", exception);
            if (listener != null) {
                listener.onResolveFailure(Anchor.CloudAnchorState.ERROR_INTERNAL, exception.getMessage());
            }
        }
    }

    public boolean isResolvePending() {
        try {
            return resolveFuture != null && resolveFuture.getState() == FutureState.PENDING;
        } catch (Exception exception) {
            Log.w(TAG, "isResolvePending failed", exception);
            return false;
        }
    }

    public boolean isResolveDone() {
        try {
            return resolveFuture != null && resolveFuture.getState() == FutureState.DONE;
        } catch (Exception exception) {
            Log.w(TAG, "isResolveDone failed", exception);
            return false;
        }
    }

    public boolean isResolveInFlight() {
        synchronized (lock) {
            return resolveInFlight;
        }
    }

    public void cancelResolveOnly() {
        synchronized (lock) {
            resolveToken++;
            resolveInFlight = false;
        }

        if (resolveFuture != null) {
            try {
                Log.d(TAG, "cancelResolveOnly: cancelling active resolve future");
                resolveFuture.cancel();
            } catch (Exception exception) {
                Log.w(TAG, "cancelResolveOnly: failed to cancel resolve future", exception);
            }
            resolveFuture = null;
        }
    }

    public ResolveFutureSnapshot getResolveFutureSnapshot() {
        if (resolveFuture == null) {
            return null;
        }

        try {
            FutureState futureState = resolveFuture.getState();
            Anchor.CloudAnchorState cloudState = null;
            Anchor anchor = null;

            if (futureState == FutureState.DONE) {
                cloudState = resolveFuture.getResultCloudAnchorState();
                anchor = resolveFuture.getResultAnchor();

                synchronized (lock) {
                    resolveInFlight = false;
                }

                if (cloudState == Anchor.CloudAnchorState.SUCCESS && anchor != null) {
                    resolvedAnchor = anchor;
                }
            }

            return new ResolveFutureSnapshot(futureState, cloudState, anchor);
        } catch (Exception exception) {
            Log.w(TAG, "getResolveFutureSnapshot failed", exception);
            return new ResolveFutureSnapshot(null, null, null);
        }
    }

    public void clear() {
        Log.d(TAG, "CloudAnchorHelper.clear: releasing futures and anchors");

        synchronized (lock) {
            cleared = true;
            resolveToken++;
            resolveInFlight = false;
        }

        if (hostFuture != null) {
            try {
                hostFuture.cancel();
            } catch (Exception exception) {
                Log.w(TAG, "clear: failed to cancel host future", exception);
            }
            hostFuture = null;
        }

        if (resolveFuture != null) {
            try {
                resolveFuture.cancel();
            } catch (Exception exception) {
                Log.w(TAG, "clear: failed to cancel resolve future", exception);
            }
            resolveFuture = null;
        }

        if (hostedAnchor != null) {
            try {
                hostedAnchor.detach();
            } catch (Exception ignore) {
            }
            hostedAnchor = null;
        }

        if (resolvedAnchor != null) {
            try {
                resolvedAnchor.detach();
            } catch (Exception ignore) {
            }
            resolvedAnchor = null;
        }
    }

    public String toReadableMessage(Anchor.CloudAnchorState state) {
        if (state == null) {
            return "Cloud Anchor state unknown";
        }

        switch (state) {
            case SUCCESS:
                return "Cloud Anchor ready";
            case ERROR_CLOUD_ID_NOT_FOUND:
                return "Cloud Anchor ID not found";
            case ERROR_HOSTING_DATASET_PROCESSING_FAILED:
                return "Need better scan for cloud hosting";
            case ERROR_HOSTING_SERVICE_UNAVAILABLE:
                return "Cloud Anchor service unavailable";
            case ERROR_INTERNAL:
                return "Internal Cloud Anchor error";
            case ERROR_NOT_AUTHORIZED:
                return "ARCore API authorization is not configured correctly";
            case ERROR_RESOLVING_SDK_VERSION_TOO_NEW:
                return "Resolve SDK is newer than the host SDK";
            case ERROR_RESOLVING_SDK_VERSION_TOO_OLD:
                return "Resolve SDK is older than the host SDK";
            case ERROR_RESOURCE_EXHAUSTED:
                return "ARCore API quota exhausted";
            case TASK_IN_PROGRESS:
                return "Cloud Anchor task in progress";
            case NONE:
            default:
                return state.name();
        }
    }

    private String safeAnchorId(String cloudAnchorId) {
        if (cloudAnchorId == null) {
            return "null";
        }

        String trimmed = cloudAnchorId.trim();
        if (trimmed.length() <= 8) {
            return trimmed;
        }

        return trimmed.substring(0, 4) + "..." + trimmed.substring(trimmed.length() - 4);
    }

    private String poseToShortString(Pose pose) {
        if (pose == null) {
            return "null";
        }
        return String.format(Locale.US, "(%.3f, %.3f, %.3f)", pose.tx(), pose.ty(), pose.tz());
    }
}