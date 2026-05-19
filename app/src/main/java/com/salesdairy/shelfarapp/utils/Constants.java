package com.salesdairy.shelfarapp.utils;

public class Constants {
    public static final String DB_NAME = "shelf_ar.db";
    public static final int DB_VERSION = 39;

    public static final String TABLE_SHELVES = "shelves";
    public static final String TABLE_STORE_REFERENCES = "store_references";
    public static final String TABLE_AUDIT_SESSIONS = "audit_sessions";
    public static final String TABLE_SHELF_AUDITS = "shelf_audits";
    public static final String TABLE_AUDIT_IMAGES = "audit_images";
    public static final String TABLE_ROUTE_CHECKPOINTS = "route_checkpoints";
    public static final String TABLE_ROUTE_EDGES = "route_edges";
    public static final String TABLE_AUDIT_TELEMETRY = "audit_telemetry";

    public static final String COL_ID = "id";
    public static final String COL_OUTLET_ID = "outlet_id";
    public static final String COL_SHELF_NAME = "shelf_name";
    public static final String COL_IMAGE_PATH = "image_path";
    public static final String COL_ANCHOR_X = "anchor_x";
    public static final String COL_ANCHOR_Y = "anchor_y";
    public static final String COL_ANCHOR_Z = "anchor_z";
    public static final String COL_ROT_X = "rot_x";
    public static final String COL_ROT_Y = "rot_y";
    public static final String COL_ROT_Z = "rot_z";
    public static final String COL_ROT_W = "rot_w";
    public static final String COL_CREATED_AT = "created_at";
    public static final String COL_CLOUD_ANCHOR_ID = "cloud_anchor_id";
    public static final String COL_CLOUD_ANCHOR_STATUS = "cloud_anchor_status";
    public static final String COL_CLOUD_ANCHOR_ERROR = "cloud_anchor_error";
    public static final String COL_CLOUD_ANCHOR_HOSTED_AT = "cloud_anchor_hosted_at";
    public static final String COL_CLOUD_ANCHOR_TTL_DAYS = "cloud_anchor_ttl_days";
    public static final String COL_CAMERA_X = "camera_x";
    public static final String COL_CAMERA_Y = "camera_y";
    public static final String COL_CAMERA_Z = "camera_z";
    public static final String COL_CAMERA_ROT_X = "camera_rot_x";
    public static final String COL_CAMERA_ROT_Y = "camera_rot_y";
    public static final String COL_CAMERA_ROT_Z = "camera_rot_z";
    public static final String COL_CAMERA_ROT_W = "camera_rot_w";
    public static final String COL_GUIDE_ANCHOR_BUNDLE = "guide_anchor_bundle";
    public static final String COL_STORE_REFERENCE_ID = "store_reference_id";
    public static final String COL_ROUTE_LABEL = "level_name";
    public static final String COL_ROUTE_ORDER = "route_order";
    public static final String COL_NEAREST_CHECKPOINT_ID = "nearest_checkpoint_id";
    public static final String COL_CAPTURE_CONFIDENCE = "capture_confidence";
    public static final String COL_SCENE_QUALITY_SCORE = "scene_quality_score";

    public static final String COL_REFERENCE_NAME = "reference_name";
    public static final String COL_REFERENCE_SCOPE_NAME = "store_section_name";
    public static final String COL_REFERENCE_IMAGE_PATH = "reference_image_path";
    public static final String COL_REFERENCE_HINT = "reference_hint";
    public static final String COL_IS_ACTIVE = "is_active";

    public static final String COL_SESSION_ID = "session_id";
    public static final String COL_SHELF_ID = "shelf_id";
    public static final String COL_STATUS = "status";
    public static final String COL_AUDITED_AT = "audited_at";
    public static final String COL_REMARKS = "remarks";

    public static final String COL_SHELF_AUDIT_ID = "shelf_audit_id";
    public static final String COL_CAPTURE_ORDER = "capture_order";

    public static final String COL_SEQUENCE = "sequence_no";
    public static final String COL_CHECKPOINT_KIND = "checkpoint_kind";
    public static final String COL_YAW_DEGREES = "yaw_degrees";
    public static final String COL_FROM_CHECKPOINT_ID = "from_checkpoint_id";
    public static final String COL_TO_CHECKPOINT_ID = "to_checkpoint_id";
    public static final String COL_DISTANCE_METERS = "distance_meters";
    public static final String COL_EDGE_KIND = "edge_kind";
    public static final String COL_EVENT_NAME = "event_name";
    public static final String COL_EVENT_VALUE = "event_value";
    public static final String COL_EVENT_DETAIL = "event_detail";

    public static final String EXTRA_SHELF_ID = "extra_shelf_id";
    public static final String EXTRA_STORE_REFERENCE_ID = "extra_store_reference_id";
    public static final String EXTRA_AUDIT_SESSION_ID = "extra_audit_session_id";

    public static final String EXTRA_SOURCE_APP = "source_app";
    public static final String EXTRA_EXTERNAL_AUDIT_SESSION_TOKEN = "audit_session_token";
    public static final String EXTRA_EXTERNAL_CALLBACK_SCHEME = "callback_scheme";
    public static final String EXTRA_EXTERNAL_CALLBACK_HOST = "callback_host";
    public static final String EXTRA_EXTERNAL_STATUS = "status";
    public static final String EXTRA_EXTERNAL_RESULT_MESSAGE = "message";
    public static final String EXTRA_EXTERNAL_AUDIT_TYPE = "audit_type";

    public static final String SOURCE_APP_SALESDIARY = "salesdiary";

    public static final int CAMERA_PERMISSION_REQUEST_CODE = 1001;
    public static final int DEFAULT_OUTLET_ID = 1;
    public static final int CLOUD_ANCHOR_TTL_DAYS = 1;

    public static final float CAPTURE_POSITION_THRESHOLD_METERS = 0.22f;
    public static final float CAPTURE_HEADING_THRESHOLD_DEGREES = 8f;
    public static final float NEAR_CAPTURE_POSITION_THRESHOLD_METERS = 0.55f;

    public static final float TURN_ONLY_THRESHOLD_DEGREES = 14f;
    public static final float STRONG_TURN_THRESHOLD_DEGREES = 28f;

    public static final float GUIDE_ARROW_START_DISTANCE = 0.55f;
    public static final float GUIDE_ARROW_STEP_DISTANCE = 0.24f;
    public static final float GUIDE_FLOOR_Y_OFFSET = 0.42f;
    public static final int GUIDE_MAX_CHEVRONS = 8;
    public static final long GUIDE_RENDER_INTERVAL_MS = 180L;

    public static final float GUIDE_CHEVRON_ARM_LENGTH = 0.16f;
    public static final float GUIDE_CHEVRON_ARM_WIDTH = 0.040f;
    public static final float GUIDE_CHEVRON_THICKNESS = 0.010f;
    public static final float GUIDE_CHEVRON_SPREAD = 0.050f;
    public static final float GUIDE_CHEVRON_ANGLE_DEGREES = 34f;

    public static final float GUIDE_TARGET_DISC_RADIUS = 0.12f;
    public static final float GUIDE_TARGET_DISC_HEIGHT = 0.012f;
    public static final float GUIDE_TARGET_ORB_RADIUS = 0.030f;
    public static final float GUIDE_TARGET_ORB_Y = 0.09f;

    public static final long CLOUD_RESOLVE_TIMEOUT_MS = 8000L;
    public static final long CLOUD_RESOLVE_RETRY_COOLDOWN_MS = 1200L;
    public static final long AUDIT_STABLE_TRACKING_BEFORE_RESOLVE_MS = 220L;
    public static final long CLOUD_RESOLVE_PROGRESS_LOG_INTERVAL_MS = 2000L;
    public static final long AR_TRACKING_LOG_INTERVAL_MS = 1500L;

    public static final long FEATURE_HISTORY_WINDOW_MS = 5000L;
    public static final int MIN_RECENT_SAMPLES_FOR_CONFIDENCE = 2;
    public static final int MIN_SUFFICIENT_OR_GOOD_SAMPLES_FOR_CONFIDENCE = 1;
    public static final long ALIGN_HOLD_REQUIRED_MS = 120L;
    public static final long QUALITY_LOG_INTERVAL_MS = 1500L;
    public static final long FEATURE_QUALITY_STICKY_WINDOW_MS = 3500L;

    public static final float CAPTURE_RETICLE_SEARCH_RADIUS_DP = 40f;
    public static final float CAPTURE_RETICLE_SEARCH_INNER_RADIUS_DP = 20f;

    private Constants() {
    }
}
