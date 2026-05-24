package com.codex.watchguard;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

final class AppState {
    static final String ACTION_LOCK = "com.codex.watchguard.action.LOCK";
    static final String ACTION_UNLOCK = "com.codex.watchguard.action.UNLOCK";
    static final String ACTION_TOGGLE = "com.codex.watchguard.action.TOGGLE";
    static final String ACTION_EMERGENCY_STOP = "com.codex.watchguard.action.EMERGENCY_STOP";
    static final String EXTRA_TIMEOUT_MS = "timeout_ms";
    static final String EXTRA_UNLOCK_CYCLE_ID = "unlock_cycle_id";
    static final String EXTRA_LOCK_MODE = "lock_mode";
    static final String LOCK_MODE_GPT_AUTOMATION = "gpt_automation_lock";

    private static final String PREFS = "watch_guard_state";
    private static final long GPT_ACCESSIBILITY_HEARTBEAT_STALE_MS = 20_000L;
    private static final int CRASH_STACK_LINES = 12;
    private static final String KEY_LOCKED = "locked";
    private static final String KEY_LOCKED_AT = "locked_at";
    private static final String KEY_UNLOCK_ALLOWED_AT = "unlock_allowed_at";
    private static final String KEY_UNLOCK_SETTLE_UNTIL = "unlock_settle_until";
    private static final String KEY_LOCK_SETTLE_UNTIL = "lock_settle_until";
    private static final String KEY_LOCK_STABILIZE_RESTORE_COUNT = "lock_stabilize_restore_count";
    private static final String KEY_SCREEN_OFF_RECOVERY_PACKAGE = "screen_off_recovery_package";
    private static final String KEY_SCREEN_OFF_RECOVERY_UNTIL = "screen_off_recovery_until";
    private static final String KEY_SCREEN_OFF_RECOVERY_COUNT = "screen_off_recovery_count";
    private static final String KEY_CAMERA_SHUTTER_LAST_AT = "camera_shutter_last_at";
    private static final String KEY_CAMERA_SHUTTER_LAST_PACKAGE = "camera_shutter_last_package";
    private static final String KEY_CAMERA_SHUTTER_ACCEPTED = "camera_shutter_accepted";
    private static final String KEY_CAMERA_SHUTTER_THROTTLED = "camera_shutter_throttled";
    private static final String KEY_CAMERA_SHUTTER_BACKEND_RESULTS = "camera_shutter_backend_results";
    private static final String KEY_CAMERA_TAP_BACKEND = "camera_tap_backend";
    private static final String KEY_CAMERA_TAP_COUNT = "camera_tap_count";
    private static final String KEY_CAMERA_TAP_FAILED_COUNT = "camera_tap_failed_count";
    private static final String KEY_CAMERA_TAP_LAST_X = "camera_tap_last_x";
    private static final String KEY_CAMERA_TAP_LAST_Y = "camera_tap_last_y";
    private static final String KEY_WATCH_CAMERA_ACTIVE = "watch_camera_active";
    private static final String KEY_WATCH_CAMERA_LAST_ACTIVE = "watch_camera_last_active";
    private static final String KEY_WATCH_CAMERA_HOME_BRIDGE_UNTIL = "watch_camera_home_bridge_until";
    private static final String KEY_WATCH_CAMERA_LAST_SHOT_AT = "watch_camera_last_shot_at";
    private static final String KEY_WATCH_CAMERA_PHOTO_COUNT = "watch_camera_photo_count";
    private static final String KEY_WATCH_CAMERA_THROTTLED = "watch_camera_throttled";
    private static final String KEY_WATCH_CAMERA_LAST_FILE = "watch_camera_last_file";
    private static final String KEY_WATCH_CAMERA_LAST_ERROR = "watch_camera_last_error";
    private static final String KEY_WATCH_CAMERA_BACKEND = "watch_camera_backend";
    private static final String KEY_WATCH_CAMERA_STORAGE_BACKEND = "watch_camera_storage_backend";
    private static final String KEY_WATCH_CAMERA_PUBLIC_FALLBACKS = "watch_camera_public_fallbacks";
    private static final String KEY_WATCH_CAMERA_WARM_BRIDGE_UNTIL = "watch_camera_warm_bridge_until";
    private static final String KEY_WATCH_CAMERA_CAPTURE_IN_FLIGHT = "watch_camera_capture_in_flight";
    private static final String KEY_WATCH_CAMERA_PENDING_SHOT = "watch_camera_pending_shot";
    private static final String KEY_WATCH_CAMERA_GENERATION = "watch_camera_generation";
    private static final String KEY_WATCH_CAMERA_WARM_CLOSES = "watch_camera_warm_closes";
    private static final String KEY_WATCH_CAMERA_SESSION_ID = "watch_camera_session_id";
    private static final String KEY_WATCH_CAMERA_SESSION_STARTED = "watch_camera_session_started";
    private static final String KEY_WATCH_CAMERA_SESSION_PHOTOS = "watch_camera_session_photos";
    private static final String KEY_WATCH_CAMERA_SESSION_PHOTO_COUNT = "watch_camera_session_photo_count";
    private static final String KEY_WATCH_CAMERA_PENDING_CAPTURE_COUNT = "watch_camera_pending_capture_count";
    private static final String KEY_WATCH_CAMERA_DOUBLE_CLICK_COUNT = "watch_camera_double_click_count";
    private static final String KEY_WATCH_CAMERA_DOUBLE_CLICK_LAST_AGE = "watch_camera_double_click_last_age";
    private static final String KEY_WATCH_CAMERA_DOUBLE_CLICK_AGE_SOURCE = "watch_camera_double_click_age_source";
    private static final String KEY_WATCH_CAMERA_DOUBLE_CLICK_EFFECTIVE_AGE = "watch_camera_double_click_effective_age";
    private static final String KEY_WATCH_CAMERA_DIRECT_KEY_PULSE_SCHEDULED = "watch_camera_direct_key_pulse_scheduled";
    private static final String KEY_WATCH_CAMERA_DIRECT_KEY_PULSE_FIRED = "watch_camera_direct_key_pulse_fired";
    private static final String KEY_WATCH_CAMERA_DIRECT_KEY_PULSE_CANCELLED_HOME = "watch_camera_direct_key_pulse_cancelled_home";
    private static final String KEY_WATCH_CAMERA_OPEN_ONLY = "watch_camera_open_only";
    private static final String KEY_WATCH_CAMERA_TOUCH_IGNORED = "watch_camera_touch_ignored";
    private static final String KEY_WATCH_CAMERA_TOUCH_DOUBLE_CANDIDATE = "watch_camera_touch_double_candidate";
    private static final String KEY_WATCH_CAMERA_TOUCH_DOUBLE_ACCEPTED = "watch_camera_touch_double_accepted";
    private static final String KEY_WATCH_CAMERA_TOUCH_DOUBLE_LAST_AGE = "watch_camera_touch_double_last_age";
    private static final String KEY_WATCH_CAMERA_EDGE_SWIPE_TOP_BLOCKED = "watch_camera_edge_swipe_top_blocked";
    private static final String KEY_WATCH_CAMERA_EDGE_SWIPE_BOTTOM_BLOCKED = "watch_camera_edge_swipe_bottom_blocked";
    private static final String KEY_WATCH_CAMERA_EDGE_SWIPE_LAST_EDGE = "watch_camera_edge_swipe_last_edge";
    private static final String KEY_WATCH_CAMERA_SYSTEM_UI_REAPPLY = "watch_camera_system_ui_reapply";
    private static final String KEY_WATCH_CAMERA_GESTURE_GUARD_ACTIVE = "watch_camera_gesture_guard_active";
    private static final String KEY_WATCH_CAMERA_GESTURE_GUARD_HEARTBEAT = "watch_camera_gesture_guard_heartbeat";
    private static final String KEY_WATCH_CAMERA_GESTURE_GUARD_STARTED = "watch_camera_gesture_guard_started";
    private static final String KEY_WATCH_CAMERA_GESTURE_GUARD_STOPPED = "watch_camera_gesture_guard_stopped";
    private static final String KEY_WATCH_CAMERA_TOP_SWIPE_BLOCKED = "watch_camera_top_swipe_blocked";
    private static final String KEY_WATCH_CAMERA_BOTTOM_SWIPE_BLOCKED = "watch_camera_bottom_swipe_blocked";
    private static final String KEY_WATCH_CAMERA_SYSTEM_PANEL_ESCAPE = "watch_camera_system_panel_escape";
    private static final String KEY_WATCH_CAMERA_POWER_KEY_CONSUMED = "watch_camera_power_key_consumed";
    private static final String KEY_WATCH_CAMERA_SCREEN_OFF_DURING_CAMERA = "watch_camera_screen_off_during_camera";
    private static final String KEY_WATCH_CAMERA_TOUCH_SHIELD_MODE = "watch_camera_touch_shield_mode";
    private static final String KEY_WATCH_CAMERA_TOUCH_SHIELD_ACTIVE = "watch_camera_touch_shield_active";
    private static final String KEY_WATCH_CAMERA_TOUCH_SHIELD_STARTED = "watch_camera_touch_shield_started";
    private static final String KEY_WATCH_CAMERA_TOUCH_SHIELD_STOPPED = "watch_camera_touch_shield_stopped";
    private static final String KEY_WATCH_CAMERA_TOUCH_SHIELD_TAP_PROXY = "watch_camera_touch_shield_tap_proxy";
    private static final String KEY_WATCH_CAMERA_TOUCH_ABSORBED = "watch_camera_touch_absorbed";
    private static final String KEY_WATCH_CAMERA_SYSTEM_PANEL_REBOUND = "watch_camera_system_panel_rebound";
    private static final String KEY_WATCH_CAMERA_SYSTEM_PANEL_LAST_PACKAGE = "watch_camera_system_panel_last_package";
    private static final String KEY_WATCH_CAMERA_SYSTEM_PANEL_REBOUND_AT = "watch_camera_system_panel_rebound_at";
    private static final String KEY_WATCH_CAMERA_SYSTEM_PANEL_BACK_ACTION = "watch_camera_system_panel_back_action";
    private static final String KEY_WATCH_CAMERA_SYSTEM_PANEL_FAST_REBOUND = "watch_camera_system_panel_fast_rebound";
    private static final String KEY_WATCH_CAMERA_SYSTEM_PANEL_BURST = "watch_camera_system_panel_burst";
    private static final String KEY_WATCH_CAMERA_FALSE_PANEL_EVENT_IGNORED = "watch_camera_false_panel_event_ignored";
    private static final String KEY_WATCH_CAMERA_NATIVE_CAMERA_BOUNCE_BLOCKED = "watch_camera_native_camera_bounce_blocked";
    private static final String KEY_WATCH_CAMERA_PANEL_BACK_SUPPRESSED = "watch_camera_panel_back_suppressed";
    private static final String KEY_WATCH_CAMERA_PANEL_RESCUE_UNTIL = "watch_camera_panel_rescue_until";
    private static final String KEY_WATCH_CAMERA_PANEL_RESCUE_LAST_PACKAGE = "watch_camera_panel_rescue_last_package";
    private static final String KEY_WATCH_CAMERA_PANEL_RESTORE_ATTEMPT = "watch_camera_panel_restore_attempt";
    private static final String KEY_WATCH_CAMERA_DOWNMENU_RESTORE_ATTEMPT = "watch_camera_downmenu_restore_attempt";
    private static final String KEY_WATCH_CAMERA_PAUSE_PRESERVED_FOR_PANEL = "watch_camera_pause_preserved_for_panel";
    private static final String KEY_WATCH_CAMERA_ROTARY_IGNORED = "watch_camera_rotary_ignored";
    private static final String KEY_WATCH_CAMERA_ROTARY_LAST_KEY = "watch_camera_rotary_last_key";
    private static final String KEY_WATCH_CAMERA_ROTARY_LAST_SCAN = "watch_camera_rotary_last_scan";
    private static final String KEY_WATCH_CAMERA_ROTARY_GUARD_UNTIL = "watch_camera_rotary_guard_until";
    private static final String KEY_WATCH_CAMERA_ROTARY_GUARD_COUNT = "watch_camera_rotary_guard_count";
    private static final String KEY_WATCH_CAMERA_ROTARY_BLOCKED_SHUTTER = "watch_camera_rotary_blocked_shutter";
    private static final String KEY_WATCH_CAMERA_ROTARY_LAST_SOURCE = "watch_camera_rotary_last_source";
    private static final String KEY_WATCH_CAMERA_ROTARY_VARIANT = "watch_camera_rotary_variant";
    private static final String KEY_WATCH_CAMERA_ROTARY_CANCELLED_PENDING = "watch_camera_rotary_cancelled_pending";
    private static final String KEY_WATCH_CAMERA_ROTARY_BLOCKED_KEY_COUNTS = "watch_camera_rotary_blocked_key_counts";
    private static final String KEY_WATCH_CAMERA_REAL_BUTTON_LAST_KEY = "watch_camera_real_button_last_key";
    private static final String KEY_WATCH_CAMERA_REJECTED_BUTTON_KEY = "watch_camera_rejected_button_key";
    private static final String KEY_WATCH_CAMERA_INPUT_NOISE_UNTIL = "watch_camera_input_noise_until";
    private static final String KEY_WATCH_CAMERA_INPUT_NOISE_COUNT = "watch_camera_input_noise_count";
    private static final String KEY_WATCH_CAMERA_LAST_NOISE_SOURCE = "watch_camera_last_noise_source";
    private static final String KEY_WATCH_CAMERA_HOME_IGNORED_NOISE = "watch_camera_home_ignored_noise";
    private static final String KEY_WATCH_CAMERA_LAST_HOME_PULSE_AT = "watch_camera_last_home_pulse_at";
    private static final String KEY_WATCH_CAMERA_LAST_HOME_PULSE_SOURCE = "watch_camera_last_home_pulse_source";
    private static final String KEY_WATCH_CAMERA_LAST_HOME_GAP = "watch_camera_last_home_gap";
    private static final String KEY_WATCH_CAMERA_LAST_CLICK_ELAPSED = "watch_camera_last_click_elapsed";
    private static final String KEY_WATCH_CAMERA_SINGLE_CLICK_ARMED = "watch_camera_single_click_armed";
    private static final String KEY_WATCH_CAMERA_SINGLE_CLICK_ARMED_AT = "watch_camera_single_click_armed_at";
    private static final String KEY_WATCH_CAMERA_SINGLE_CLICK_DEADLINE = "watch_camera_single_click_deadline";
    private static final String KEY_WATCH_CAMERA_CAPTURE_REQUESTED = "watch_camera_capture_requested";
    private static final String KEY_WATCH_CAMERA_LAST_BLOCK_REASON = "watch_camera_last_block_reason";
    private static final String KEY_WATCH_CAMERA_GPT_SHARE_LAUNCH = "watch_camera_gpt_share_launch";
    private static final String KEY_WATCH_CAMERA_DEACTIVATED_FOR_GPT = "watch_camera_deactivated_for_gpt";
    private static final String KEY_SAFE_IME_HIDDEN = "safe_ime_hidden";
    private static final String KEY_SAFE_IME_SHOWN = "safe_ime_shown";
    private static final String KEY_SAFE_IME_HIDE_BUTTON = "safe_ime_hide_button";
    private static final String KEY_SAFE_IME_AUTO_REPEAT_HIDDEN = "safe_ime_auto_repeat_hidden";
    private static final String KEY_SAFE_IME_DEFAULT_LOST = "safe_ime_default_lost";
    private static final String KEY_SAFE_IME_LAST_HIDE_REASON = "safe_ime_last_hide_reason";
    private static final String KEY_SAFE_IME_LAST_FIELD = "safe_ime_last_field";
    private static final String KEY_SAFE_IME_LAST_AUTO_HIDE_AT = "safe_ime_last_auto_hide_at";
    private static final String KEY_SAFE_IME_WAS_SELECTED = "safe_ime_was_selected";
    private static final String KEY_SAFE_IME_VIEW_CLICK = "safe_ime_view_click";
    private static final String KEY_SAFE_IME_MANUAL_CLICK_SHOW = "safe_ime_manual_click_show";
    private static final String KEY_SAFE_IME_STRICT_HIDDEN = "safe_ime_strict_hidden";
    private static final String KEY_SAFE_IME_EXTRACT_HIDDEN = "safe_ime_extract_hidden";
    private static final String KEY_SAFE_IME_CLICK_SIGNAL_MISSING = "safe_ime_click_signal_missing";
    private static final String KEY_SAFE_IME_LAST_VIEW_CLICK_AT = "safe_ime_last_view_click_at";
    private static final String KEY_SAFE_IME_START_INPUT = "safe_ime_start_input";
    private static final String KEY_SAFE_IME_START_INPUT_VIEW = "safe_ime_start_input_view";
    private static final String KEY_SAFE_IME_SHOW_REQUEST = "safe_ime_show_request";
    private static final String KEY_SAFE_IME_LAST_SHOW_FLAGS = "safe_ime_last_show_flags";
    private static final String KEY_SAFE_IME_LAST_SHOW_CONFIG = "safe_ime_last_show_config";
    private static final String KEY_SAFE_IME_EVALUATE_FULLSCREEN = "safe_ime_evaluate_fullscreen";
    private static final String KEY_SAFE_IME_CREATE_EXTRACT = "safe_ime_create_extract";
    private static final String KEY_SAFE_IME_UPDATE_EXTRACT_VISIBILITY = "safe_ime_update_extract_visibility";
    private static final String KEY_SAFE_IME_MANUAL_SIGNAL_ABSENT = "safe_ime_manual_signal_absent";
    private static final String KEY_SAFE_IME_LAST_CALLBACK = "safe_ime_last_callback";
    private static final String KEY_SAFE_IME_LAST_CALLBACK_DETAIL = "safe_ime_last_callback_detail";
    private static final String KEY_SAFE_IME_A11Y_WAS_ENABLED = "safe_ime_a11y_was_enabled";
    private static final String KEY_SAFE_IME_A11Y_TAP_AT = "safe_ime_a11y_tap_at";
    private static final String KEY_SAFE_IME_A11Y_TAP_PACKAGE = "safe_ime_a11y_tap_package";
    private static final String KEY_SAFE_IME_A11Y_TAP_FIELD = "safe_ime_a11y_tap_field";
    private static final String KEY_SAFE_IME_A11Y_TAP_COUNT = "safe_ime_a11y_tap_count";
    private static final String KEY_SAFE_IME_A11Y_FOCUS_COUNT = "safe_ime_a11y_focus_count";
    private static final String KEY_SAFE_IME_A11Y_CONSUMED_COUNT = "safe_ime_a11y_consumed_count";
    private static final String KEY_SAFE_IME_A11Y_EXPIRED_COUNT = "safe_ime_a11y_expired_count";
    private static final String KEY_SAFE_IME_A11Y_NOT_SELECTED = "safe_ime_a11y_not_selected";
    private static final String KEY_SAFE_IME_TAP_SERVICE_OFF = "safe_ime_tap_service_off";
    private static final String KEY_SAFE_IME_CRASH_GUARD = "safe_ime_crash_guard";
    private static final String KEY_SAFE_IME_EXPLICIT_SHOW = "safe_ime_explicit_show";
    private static final String KEY_SAFE_IME_EXPLICIT_SUPPRESSED_NAV = "safe_ime_explicit_suppressed_nav";
    private static final String KEY_SAFE_IME_EXPLICIT_SUPPRESSED_CHURN = "safe_ime_explicit_suppressed_churn";
    private static final String KEY_SAFE_IME_EXPLICIT_WITHOUT_GATE_SUPPRESSED = "safe_ime_explicit_without_gate_suppressed";
    private static final String KEY_SAFE_IME_VIEW_CLICK_IGNORED = "safe_ime_view_click_ignored";
    private static final String KEY_SAFE_IME_GATE_ACTIVE = "safe_ime_gate_active";
    private static final String KEY_SAFE_IME_GATE_PACKAGE = "safe_ime_gate_package";
    private static final String KEY_SAFE_IME_GATE_ZONE = "safe_ime_gate_zone";
    private static final String KEY_SAFE_IME_GATE_TAP_COUNT = "safe_ime_gate_tap_count";
    private static final String KEY_SAFE_IME_GATE_SWIPE_IGNORED = "safe_ime_gate_swipe_ignored";
    private static final String KEY_SAFE_IME_GATE_NO_CONNECTION = "safe_ime_gate_no_connection";
    private static final String KEY_SAFE_IME_GATE_TOKEN_AT = "safe_ime_gate_token_at";
    private static final String KEY_SAFE_IME_GATE_TOKEN_PACKAGE = "safe_ime_gate_token_package";
    private static final String KEY_SAFE_IME_GATE_FOCUS_PASS_UNTIL = "safe_ime_gate_focus_pass_until";
    private static final String KEY_SAFE_IME_GATE_FOCUS_PASS_PACKAGE = "safe_ime_gate_focus_pass_package";
    private static final String KEY_SAFE_IME_GATE_KEYBOARD_VISIBLE = "safe_ime_gate_keyboard_visible";
    private static final String KEY_SAFE_IME_GATE_BLOCK_REASON = "safe_ime_gate_block_reason";
    private static final String KEY_SAFE_IME_GATE_OFF_REASON = "safe_ime_gate_off_reason";
    private static final String KEY_SAFE_IME_GATE_CANDIDATE_SOURCE = "safe_ime_gate_candidate_source";
    private static final String KEY_SAFE_IME_GATE_LAST_VISIBLE_AT = "safe_ime_gate_last_visible_at";
    private static final String KEY_SAFE_IME_GATE_DOWN_COUNT = "safe_ime_gate_down_count";
    private static final String KEY_SAFE_IME_GATE_ZONE_REVISION = "safe_ime_gate_zone_revision";
    private static final String KEY_SAFE_IME_GATE_ATTACHED = "safe_ime_gate_attached";
    private static final String KEY_SAFE_IME_GATE_READD_COUNT = "safe_ime_gate_readd_count";
    private static final String KEY_SAFE_IME_GATE_DETACHED_COUNT = "safe_ime_gate_detached_count";
    private static final String KEY_SAFE_IME_GATE_LAST_LAYOUT = "safe_ime_gate_last_layout";
    private static final String KEY_SAFE_IME_GATE_LAST_TOUCH_AT = "safe_ime_gate_last_touch_at";
    private static final String KEY_SAFE_IME_GATE_RESCUE_SHOWN = "safe_ime_gate_rescue_shown";
    private static final String KEY_SAFE_IME_GATE_RESCUE_TAP = "safe_ime_gate_rescue_tap";
    private static final String KEY_SAFE_IME_GATE_MISS_PACKAGE = "safe_ime_gate_miss_package";
    private static final String KEY_SAFE_IME_GATE_MISS_FIELD = "safe_ime_gate_miss_field";
    private static final String KEY_SAFE_IME_GATE_MISS_COUNT = "safe_ime_gate_miss_count";
    private static final String KEY_SAFE_IME_GATE_MISS_AT = "safe_ime_gate_miss_at";
    private static final String KEY_SAFE_IME_LAST_STRICT_INPUT_PACKAGE = "safe_ime_last_strict_input_package";
    private static final String KEY_SAFE_IME_LAST_STRICT_INPUT_FIELD = "safe_ime_last_strict_input_field";
    private static final String KEY_SAFE_IME_LAST_STRICT_INPUT_TYPE = "safe_ime_last_strict_input_type";
    private static final String KEY_SAFE_IME_LAST_STRICT_INPUT_AT = "safe_ime_last_strict_input_at";
    private static final String KEY_SAFE_IME_HIDE_SUPPRESSION_MS = "safe_ime_hide_suppression_ms";
    private static final String KEY_SAFE_IME_POLICY_MODE = "safe_ime_policy_mode";
    private static final String KEY_GPT_SHARE_PENDING = "gpt_share_pending";
    private static final String KEY_GPT_SHARE_SESSION_ID = "gpt_share_session_id";
    private static final String KEY_GPT_SHARE_PROMPT = "gpt_share_prompt";
    private static final String KEY_GPT_SHARE_ALL_PHOTOS = "gpt_share_all_photos";
    private static final String KEY_GPT_SHARE_CHUNK_INDEX = "gpt_share_chunk_index";
    private static final String KEY_GPT_SHARE_CHUNK_COUNT = "gpt_share_chunk_count";
    private static final String KEY_GPT_SHARE_CHUNK_SIZE = "gpt_share_chunk_size";
    private static final String KEY_GPT_SHARE_NEEDS_NEW_CHAT = "gpt_share_needs_new_chat";
    private static final String KEY_GPT_SHARE_TARGET_MODE = "gpt_share_target_mode";
    private static final String KEY_GPT_SHARE_TARGET_FALLBACK_REASON = "gpt_share_target_fallback_reason";
    private static final String KEY_GPT_SHARE_LAST_RESULT = "gpt_share_last_result";
    private static final String KEY_GPT_SHARE_NO_PHOTOS = "gpt_share_no_photos";
    private static final String KEY_GPT_SHARE_LAUNCH_COUNT = "gpt_share_launch_count";
    private static final String KEY_GPT_AUTOMATION_SENT_COUNT = "gpt_automation_sent_count";
    private static final String KEY_GPT_AUTOMATION_FAIL_COUNT = "gpt_automation_fail_count";
    private static final String KEY_GPT_AUTOMATION_LAST_ACTION = "gpt_automation_last_action";
    private static final String KEY_GPT_AUTOMATION_ACCESSIBILITY_OFF = "gpt_automation_accessibility_off";
    private static final String KEY_GPT_AUTOMATION_PHASE = "gpt_automation_phase";
    private static final String KEY_GPT_AUTOMATION_GATE_REASON = "gpt_automation_gate_reason";
    private static final String KEY_GPT_AUTOMATION_ATTACHMENT_EXPECTED = "gpt_automation_attachment_expected";
    private static final String KEY_GPT_AUTOMATION_ATTACHMENT_EVIDENCE = "gpt_automation_attachment_evidence";
    private static final String KEY_GPT_AUTOMATION_ATTACHMENT_STREAK = "gpt_automation_attachment_streak";
    private static final String KEY_GPT_AUTOMATION_CHUNK_LAUNCHED_AT = "gpt_automation_chunk_launched_at";
    private static final String KEY_GPT_AUTOMATION_SAFE_SEND_CANDIDATE = "gpt_automation_safe_send_candidate";
    private static final String KEY_GPT_AUTOMATION_MANUAL_REQUIRED = "gpt_automation_manual_required";
    private static final String KEY_GPT_AUTOMATION_SEND_CANDIDATE_SOURCE = "gpt_automation_send_candidate_source";
    private static final String KEY_GPT_AUTOMATION_ARROW_FALLBACK = "gpt_automation_arrow_fallback";
    private static final String KEY_GPT_AUTOMATION_SEND_CLICK_AT = "gpt_automation_send_click_at";
    private static final String KEY_GPT_AUTOMATION_ATTACHMENT_REQUIRED = "gpt_automation_attachment_required";
    private static final String KEY_GPT_AUTOMATION_LAST_EXCEPTION = "gpt_automation_last_exception";
    private static final String KEY_GPT_AUTOMATION_EXCEPTION_COUNT = "gpt_automation_exception_count";
    private static final String KEY_GPT_AUTOMATION_SERVICE_NOT_ALIVE = "gpt_automation_service_not_alive";
    private static final String KEY_GPT_AUTOMATION_SEND_CONFIRMATION_PHASE = "gpt_automation_send_confirmation_phase";
    private static final String KEY_GPT_AUTOMATION_SEND_CONFIRMED = "gpt_automation_send_confirmed";
    private static final String KEY_GPT_AUTOMATION_SEND_UNCONFIRMED = "gpt_automation_send_unconfirmed";
    private static final String KEY_GPT_AUTOMATION_SEND_PENDING_AT = "gpt_automation_send_pending_at";
    private static final String KEY_GPT_AUTOMATION_SEND_PENDING_SOURCE = "gpt_automation_send_pending_source";
    private static final String KEY_GPT_AUTOMATION_SEND_CONFIRM_RETRIES = "gpt_automation_send_confirm_retries";
    private static final String KEY_GPT_AUTOMATION_LAST_CLICK_BOUNDS = "gpt_automation_last_click_bounds";
    private static final String KEY_GPT_AUTOMATION_LAST_CLICK_WAS_COMPOSER = "gpt_automation_last_click_was_composer";
    private static final String KEY_GPT_AUTOMATION_ATTACHMENTS_READY_AT = "gpt_automation_attachments_ready_at";
    private static final String KEY_GPT_AUTOMATION_PROMPT_READY_AT = "gpt_automation_prompt_ready_at";
    private static final String KEY_GPT_AUTOMATION_SEND_GESTURE_ATTEMPTED_AT = "gpt_automation_send_gesture_attempted_at";
    private static final String KEY_GPT_AUTOMATION_ROOT_SOURCE = "gpt_automation_root_source";
    private static final String KEY_GPT_AUTOMATION_ROOT_PACKAGE = "gpt_automation_root_package";
    private static final String KEY_GPT_AUTOMATION_LOCKED_OVERLAY_MODE = "gpt_automation_locked_overlay_mode";
    private static final String KEY_GPT_AUTO_LOCK_REQUESTED = "gpt_auto_lock_requested";
    private static final String KEY_GPT_AUTO_LOCK_ACTIVE = "gpt_auto_lock_active";
    private static final String KEY_GPT_AUTO_LOCK_STATE = "gpt_auto_lock_state";
    private static final String KEY_GPT_AUTO_LOCK_STARTED_AT = "gpt_auto_lock_started_at";
    private static final String KEY_GPT_AUTO_LOCK_START_COUNT = "gpt_auto_lock_start_count";
    private static final String KEY_GPT_AUTO_LOCK_FAIL_COUNT = "gpt_auto_lock_fail_count";
    private static final String KEY_GPT_SHARE_REATTACH_ATTEMPT_COUNT = "gpt_share_reattach_attempt_count";
    private static final String KEY_GPT_SHARE_REATTACH_CHUNK_INDEX = "gpt_share_reattach_chunk_index";
    private static final String KEY_GPT_SHARE_STATE_LOST_COUNT = "gpt_share_state_lost_count";
    private static final String KEY_MODE_REARM_REASON = "mode_rearm_reason";
    private static final String KEY_MODE_USER_DESIRED = "mode_user_desired_enabled";
    private static final String KEY_MODE_ENABLED_AT = "mode_enabled_at";
    private static final String KEY_MODE_LAST_DISABLED_REASON = "mode_last_disabled_reason";
    private static final String KEY_MODE_DISABLE_SOURCE = "mode_disable_source";
    private static final String KEY_MODE_REPAIR_COUNT = "mode_repair_count";
    private static final String KEY_MODE_OVERLAY_RECOVERY_COUNT = "mode_overlay_recovery_count";
    private static final String KEY_TOUCH_SERVICE_NULL_INTENT_COUNT = "touch_service_null_intent_count";
    private static final String KEY_BOOT_RECEIVER_LAST_ACTION = "boot_receiver_last_action";
    private static final String KEY_BOOT_RECEIVER_LAST_RESULT = "boot_receiver_last_result";
    private static final String KEY_LOCKED_BACK_KEY_CONSUMED = "locked_back_key_consumed";
    private static final String KEY_LOCKED_EDGE_BACK_SWIPE_BLOCKED = "locked_edge_back_swipe_blocked";
    private static final String KEY_LOCKED_BACK_REBOUND = "locked_back_rebound";
    private static final String KEY_LOCKED_BACK_LAST_TARGET = "locked_back_last_target";
    private static final String KEY_LOCKED_BACK_LAST_PACKAGE = "locked_back_last_package";
    private static final String KEY_LOCKED_EDGE_GUARD_ACTIVE = "locked_edge_guard_active";
    private static final String KEY_LOCKED_EDGE_GUARD_TOUCH = "locked_edge_guard_touch";
    private static final String KEY_LOCKED_EDGE_GUARD_BLOCKED = "locked_edge_guard_blocked";
    private static final String KEY_LOCKED_BACK_ESCAPE = "locked_back_escape";
    private static final String KEY_SAFE_IME_GATE_LAST_EXCEPTION = "safe_ime_gate_last_exception";
    private static final String KEY_SAFE_IME_GATE_EXCEPTION_COUNT = "safe_ime_gate_exception_count";
    private static final String KEY_CRASH_COUNT = "crash_count";
    private static final String KEY_LAST_CRASH_AT = "last_crash_at";
    private static final String KEY_LAST_CRASH_THREAD = "last_crash_thread";
    private static final String KEY_LAST_CRASH_TYPE = "last_crash_type";
    private static final String KEY_LAST_CRASH_MESSAGE = "last_crash_message";
    private static final String KEY_LAST_CRASH_STACK = "last_crash_stack";
    private static final String KEY_SAFE_IME_NAV_SUPPRESS_UNTIL = "safe_ime_nav_suppress_until";
    private static final String KEY_SAFE_IME_LAST_NAV_FIELD = "safe_ime_last_nav_field";
    private static final String KEY_SAFE_IME_LAST_NAV_REASON = "safe_ime_last_nav_reason";
    private static final String KEY_SAFE_IME_LAST_CRASH_SOURCE = "safe_ime_last_crash_source";
    private static final String KEY_SAFE_IME_LAST_CRASH_ERROR = "safe_ime_last_crash_error";
    private static final String KEY_SAFE_IME_DEFAULT_LOST_CONTEXT = "safe_ime_default_lost_context";
    private static final String KEY_SAFE_IME_LOCK_DEFAULT = "safe_ime_lock_default";
    private static final String KEY_SAFE_IME_LOCK_SELECTED = "safe_ime_lock_selected";
    private static final String KEY_SAFE_IME_SNAPSHOT_BEFORE_LOCK = "safe_ime_snapshot_before_lock";
    private static final String KEY_SAFE_IME_LAST_SNAPSHOT = "safe_ime_last_snapshot";
    private static final String KEY_SAFE_IME_SNAPSHOT_LOG = "safe_ime_snapshot_log";
    private static final String KEY_SAFE_IME_LOST_PHASE = "safe_ime_lost_phase";
    private static final String KEY_SAFE_IME_LOST_DURING_UNLOCK = "safe_ime_lost_during_unlock";
    private static final String KEY_SAFE_IME_LOST_RECORDED_CYCLE = "safe_ime_lost_recorded_cycle";
    private static final String KEY_SAFE_IME_SILENT_RESTORE_COUNT = "safe_ime_silent_restore_count";
    private static final String KEY_SAFE_IME_SILENT_RESTORE_LAST_RESULT = "safe_ime_silent_restore_last_result";
    private static final String KEY_SAFE_IME_SILENT_RESTORE_SKIP_CYCLE = "safe_ime_silent_restore_skip_cycle";
    private static final String KEY_SAFE_IME_RESTORE_ATTEMPT_PHASE = "safe_ime_restore_attempt_phase";
    private static final String KEY_KEYBOARD_HIDE_UNLOCK_COUNT = "keyboard_hide_unlock_count";
    private static final String KEY_LAST_CACHED_APP = "last_cached_app";
    private static final String KEY_LAST_CACHED_APP_AT = "last_cached_app_at";
    private static final String KEY_LAST_CACHED_APP_SOURCE = "last_cached_app_source";
    private static final String KEY_LAST_MENU_SEEN_AFTER_APP = "last_menu_seen_after_app";
    private static final String KEY_LAST_MENU_SEEN_AFTER_APP_AT = "last_menu_seen_after_app_at";
    private static final String KEY_LAST_NAVIGATION_SEEN_AFTER_APP = "last_navigation_seen_after_app";
    private static final String KEY_LAST_NAVIGATION_SEEN_AFTER_APP_AT = "last_navigation_seen_after_app_at";
    private static final String KEY_LAST_NAVIGATION_SOURCE = "last_navigation_source";
    private static final String KEY_LOG = "event_log";
    private static final String KEY_EVENTS = "key_events";
    private static final String KEY_LOCK_MODE_RESULTS = "lock_mode_results";
    private static final String KEY_BUTTON_CONTROL = "button_control";
    private static final String KEY_BUTTON_CONTROL_PAUSED = "button_control_paused";
    private static final String KEY_KEYBOARD_GUARD = "keyboard_guard";
    private static final String KEY_KEYBOARD_GUARD_HIDES = "keyboard_guard_hides";
    private static final String KEY_KEEPER_LAST_TICK = "keeper_last_tick";
    private static final String KEY_KEEPER_START_REASON = "keeper_start_reason";
    private static final String KEY_KEEPER_RESTART_COUNT = "keeper_restart_count";
    private static final String KEY_KEEPER_TASK_REMOVED = "keeper_task_removed";
    private static final String KEY_ACCESSIBILITY_LAST_OFF = "accessibility_last_off";
    private static final String KEY_GPT_ACCESSIBILITY_CONNECTED_AT = "gpt_accessibility_connected_at";
    private static final String KEY_GPT_ACCESSIBILITY_DESTROYED_AT = "gpt_accessibility_destroyed_at";
    private static final String KEY_GPT_ACCESSIBILITY_HEARTBEAT_AT = "gpt_accessibility_heartbeat_at";
    private static final String KEY_GPT_ACCESSIBILITY_STALE_AT = "gpt_accessibility_stale_at";
    private static final String KEY_GPT_ACCESSIBILITY_STALE_REASON = "gpt_accessibility_stale_reason";
    private static final String KEY_GPT_ACCESSIBILITY_STALE_COUNT = "gpt_accessibility_stale_count";
    private static final String KEY_HOME_TRAP_ENABLED = "home_trap_enabled";
    private static final String KEY_HOME_TRAP_EXPIRES = "home_trap_expires";
    private static final String KEY_HOME_TRAP_INVOCATIONS = "home_trap_invocations";
    private static final String KEY_HOME_TRAP_LAST = "home_trap_last";
    private static final String KEY_HOME_TRAP_LAST_GAP = "home_trap_last_gap";
    private static final String KEY_HOME_TRAP_LAST_PROCESSED = "home_trap_last_processed";
    private static final String KEY_HOME_TRAP_IGNORED = "home_trap_ignored";
    private static final String KEY_LOCKED_HOME_BURST_IGNORED = "locked_home_burst_ignored";
    private static final String KEY_HOME_TRAP_SESSION_ACTIVE = "home_trap_session_active";
    private static final String KEY_HOME_TRAP_CYCLE_ID = "home_trap_cycle_id";
    private static final String KEY_HOME_STORM_COUNT = "home_storm_count";
    private static final String KEY_HOME_STORM_STARTED = "home_storm_started";
    private static final String KEY_HOME_STORM_RESCUES = "home_storm_rescues";
    private static final String KEY_LAST_HOME_DELEGATE = "last_home_delegate";
    private static final String KEY_HOME_TRAP_TARGET = "home_trap_target";
    private static final String KEY_HOME_TRAP_TARGET_SOURCE = "home_trap_target_source";
    private static final String KEY_HOME_TRAP_LOCK_TARGET = "home_trap_lock_target";
    private static final String KEY_LOCKED_CYCLE_PACKAGE = "locked_cycle_package";
    private static final String KEY_LOCKED_CYCLE_IS_MENU = "locked_cycle_is_menu";
    private static final String KEY_STAY_AWAKE = "stay_awake";
    private static final String KEY_SCREEN_TIMEOUT_ORIGINAL = "screen_timeout_original";
    private static final String KEY_SCREEN_TIMEOUT_CHANGED = "screen_timeout_changed";
    private static final String KEY_SCREEN_TIMEOUT_LAST_RESULT = "screen_timeout_last_result";
    private static final String KEY_CONTROL_UI_PAUSED_AT = "control_ui_paused_at";
    private static final String KEY_HOME_TRAP_ARMED_AT = "home_trap_armed_at";
    private static final String KEY_HOME_TRAP_MENU_GRACE_UNTIL = "home_trap_menu_grace_until";
    private static final long HOME_TRAP_DEBOUNCE_MS = 650L;
    private static final long UNLOCK_DUPLICATE_GUARD_MS = 650L;
    private static final long HOME_GAP_ACCEPT_MS = 300L;
    private static final long LOCK_SETTLE_MS = 1_200L;
    private static final int LOCK_STABILIZE_RESTORE_MAX = 2;
    private static final long SCREEN_OFF_RECOVERY_MS = 15_000L;
    private static final long CAMERA_SHUTTER_MIN_INTERVAL_MS = 700L;
    private static final long WATCH_CAMERA_HOME_BRIDGE_MS = 1_600L;
    private static final long WATCH_CAMERA_INPUT_NOISE_MS = 1_500L;
    private static final long WATCH_CAMERA_ROTARY_GUARD_MS = 1_800L;
    private static final long WATCH_CAMERA_PANEL_RESCUE_MS = 8_000L;
    private static final long WATCH_CAMERA_PANEL_DETECTION_GRACE_MS = 2_500L;
    private static final long SAFE_IME_TAP_TOKEN_MS = 800L;
    private static final long SAFE_IME_GATE_TOKEN_MS = 1_200L;
    private static final long SAFE_IME_GATE_FOCUS_PASS_MS = 2_500L;
    private static final long HOME_STORM_WINDOW_MS = 2_500L;
    private static final int HOME_STORM_THRESHOLD = 8;
    private static final int MAX_LOG_CHARS = 18000;
    private static final int MAX_KEY_EVENTS_CHARS = 12000;
    private static final int MAX_LOCK_MODE_CHARS = 12000;
    private static final int MAX_CAMERA_BACKEND_CHARS = 8000;
    private static final int MAX_SAFE_IME_SNAPSHOT_CHARS = 6000;
    private static final long STRICT_CACHED_LOCK_APP_MS = 10L * 60L * 1000L;

    private AppState() {
    }

    static boolean isLocked(Context context) {
        return prefs(context).getBoolean(KEY_LOCKED, false);
    }

    static void setLocked(Context context, boolean locked) {
        SharedPreferences.Editor editor = prefs(context).edit().putBoolean(KEY_LOCKED, locked);
        if (locked) {
            long now = System.currentTimeMillis();
            editor.putLong(KEY_LOCKED_AT, now)
                    .putLong(KEY_UNLOCK_ALLOWED_AT, now + UNLOCK_DUPLICATE_GUARD_MS)
                    .remove(KEY_UNLOCK_SETTLE_UNTIL);
        } else {
            editor.remove(KEY_LOCKED_AT)
                    .remove(KEY_UNLOCK_ALLOWED_AT)
                    .remove(KEY_LOCK_SETTLE_UNTIL)
                    .remove(KEY_LOCK_STABILIZE_RESTORE_COUNT);
        }
        editor.apply();
    }

    static long getLockedAt(Context context) {
        return prefs(context).getLong(KEY_LOCKED_AT, 0L);
    }

    static long getLockedAgeMs(Context context) {
        long lockedAt = getLockedAt(context);
        if (!isLocked(context) || lockedAt <= 0L) {
            return -1L;
        }
        return Math.max(0L, System.currentTimeMillis() - lockedAt);
    }

    static synchronized void recordLockedBackKeyConsumed(Context context, String source) {
        SharedPreferences preferences = prefs(context);
        int count = preferences.getInt(KEY_LOCKED_BACK_KEY_CONSUMED, 0) + 1;
        String target = getLockedCyclePackage(context);
        preferences.edit()
                .putInt(KEY_LOCKED_BACK_KEY_CONSUMED, count)
                .putString(KEY_LOCKED_BACK_LAST_TARGET, target == null ? "" : target)
                .putString(KEY_LOCKED_BACK_LAST_PACKAGE, "KEYCODE_BACK")
                .apply();
        appendLog(context, "locked_back_key_consumed count=" + count
                + " source=" + sanitizeForLog(source)
                + " target=" + sanitizeForLog(target));
        appendLockModeResult(context, "overlay_lock", "back_key_consumed",
                "source=" + sanitizeForLog(source)
                        + " target=" + sanitizeForLog(target));
    }

    static synchronized void recordLockedEdgeBackSwipeBlocked(
            Context context,
            String edge,
            float deltaX
    ) {
        SharedPreferences preferences = prefs(context);
        int count = preferences.getInt(KEY_LOCKED_EDGE_BACK_SWIPE_BLOCKED, 0) + 1;
        String target = getLockedCyclePackage(context);
        preferences.edit()
                .putInt(KEY_LOCKED_EDGE_BACK_SWIPE_BLOCKED, count)
                .putString(KEY_LOCKED_BACK_LAST_TARGET, target == null ? "" : target)
                .putString(KEY_LOCKED_BACK_LAST_PACKAGE, edge == null ? "edge" : edge)
                .apply();
        appendLog(context, "lock_edge_back_swipe_blocked count=" + count
                + " edge=" + sanitizeForLog(edge)
                + " dx=" + Math.round(deltaX)
                + " target=" + sanitizeForLog(target));
    }

    static synchronized void setLockedEdgeGuardActive(
            Context context,
            boolean active,
            String reason
    ) {
        prefs(context).edit()
                .putBoolean(KEY_LOCKED_EDGE_GUARD_ACTIVE, active)
                .apply();
        appendLog(context, "locked_edge_guard_"
                + (active ? "started" : "stopped")
                + " reason=" + sanitizeForLog(reason));
    }

    static synchronized void recordLockedEdgeGuardTouch(
            Context context,
            String edge
    ) {
        SharedPreferences preferences = prefs(context);
        int count = preferences.getInt(KEY_LOCKED_EDGE_GUARD_TOUCH, 0) + 1;
        preferences.edit()
                .putInt(KEY_LOCKED_EDGE_GUARD_TOUCH, count)
                .putString(KEY_LOCKED_BACK_LAST_PACKAGE, edge == null ? "edge" : edge)
                .apply();
        appendLog(context, "locked_edge_guard_touch count=" + count
                + " edge=" + sanitizeForLog(edge));
    }

    static synchronized void recordLockedEdgeGuardBlocked(
            Context context,
            String edge,
            float deltaX
    ) {
        SharedPreferences preferences = prefs(context);
        int count = preferences.getInt(KEY_LOCKED_EDGE_GUARD_BLOCKED, 0) + 1;
        String target = getLockedCyclePackage(context);
        preferences.edit()
                .putInt(KEY_LOCKED_EDGE_GUARD_BLOCKED, count)
                .putString(KEY_LOCKED_BACK_LAST_TARGET, target == null ? "" : target)
                .putString(KEY_LOCKED_BACK_LAST_PACKAGE, edge == null ? "edge_guard" : edge)
                .apply();
        appendLog(context, "locked_edge_guard_blocked count=" + count
                + " edge=" + sanitizeForLog(edge)
                + " dx=" + Math.round(deltaX)
                + " target=" + sanitizeForLog(target));
        appendLockModeResult(context, "overlay_lock", "edge_guard_blocked",
                "edge=" + sanitizeForLog(edge)
                        + " target=" + sanitizeForLog(target));
    }

    static synchronized void recordLockedBackRebound(
            Context context,
            String targetPackage,
            String observedPackage,
            String reason
    ) {
        SharedPreferences preferences = prefs(context);
        int count = preferences.getInt(KEY_LOCKED_BACK_REBOUND, 0) + 1;
        int escapeCount = preferences.getInt(KEY_LOCKED_BACK_ESCAPE, 0) + 1;
        preferences.edit()
                .putInt(KEY_LOCKED_BACK_REBOUND, count)
                .putInt(KEY_LOCKED_BACK_ESCAPE, escapeCount)
                .putString(KEY_LOCKED_BACK_LAST_TARGET, targetPackage == null ? "" : targetPackage)
                .putString(KEY_LOCKED_BACK_LAST_PACKAGE, observedPackage == null ? "" : observedPackage)
                .apply();
        appendLog(context, "locked_back_rebound count=" + count
                + " escape_count=" + escapeCount
                + " target=" + sanitizeForLog(targetPackage)
                + " observed=" + sanitizeForLog(observedPackage)
                + " reason=" + sanitizeForLog(reason));
        appendLog(context, "locked_back_escape_rebound count=" + escapeCount
                + " target=" + sanitizeForLog(targetPackage)
                + " observed=" + sanitizeForLog(observedPackage));
        appendLockModeResult(context, "overlay_lock", "back_rebound",
                "target=" + sanitizeForLog(targetPackage)
                        + " observed=" + sanitizeForLog(observedPackage)
                        + " reason=" + sanitizeForLog(reason));
    }

    static int getLockedBackKeyConsumedCount(Context context) {
        return prefs(context).getInt(KEY_LOCKED_BACK_KEY_CONSUMED, 0);
    }

    static int getLockedEdgeBackSwipeBlockedCount(Context context) {
        return prefs(context).getInt(KEY_LOCKED_EDGE_BACK_SWIPE_BLOCKED, 0);
    }

    static int getLockedBackReboundCount(Context context) {
        return prefs(context).getInt(KEY_LOCKED_BACK_REBOUND, 0);
    }

    static boolean isLockedEdgeGuardActive(Context context) {
        return prefs(context).getBoolean(KEY_LOCKED_EDGE_GUARD_ACTIVE, false);
    }

    static int getLockedEdgeGuardTouchCount(Context context) {
        return prefs(context).getInt(KEY_LOCKED_EDGE_GUARD_TOUCH, 0);
    }

    static int getLockedEdgeGuardBlockedCount(Context context) {
        return prefs(context).getInt(KEY_LOCKED_EDGE_GUARD_BLOCKED, 0);
    }

    static int getLockedBackEscapeCount(Context context) {
        return prefs(context).getInt(KEY_LOCKED_BACK_ESCAPE, 0);
    }

    static String getLockedBackLastTarget(Context context) {
        String value = prefs(context).getString(KEY_LOCKED_BACK_LAST_TARGET, null);
        return value == null || value.length() == 0 ? null : value;
    }

    static String getLockedBackLastPackage(Context context) {
        String value = prefs(context).getString(KEY_LOCKED_BACK_LAST_PACKAGE, null);
        return value == null || value.length() == 0 ? null : value;
    }

    static long getUnlockAllowedAt(Context context) {
        return prefs(context).getLong(KEY_UNLOCK_ALLOWED_AT, 0L);
    }

    static long getUnlockAllowedInMs(Context context) {
        long allowedAt = getUnlockAllowedAt(context);
        if (allowedAt <= 0L) {
            return -1L;
        }
        return Math.max(0L, allowedAt - System.currentTimeMillis());
    }

    static boolean isUnlockAllowed(Context context) {
        long allowedAt = getUnlockAllowedAt(context);
        return allowedAt <= 0L || System.currentTimeMillis() >= allowedAt;
    }

    static long getHomeGapAcceptMs() {
        return HOME_GAP_ACCEPT_MS;
    }

    static long getLockSettleMs() {
        return LOCK_SETTLE_MS;
    }

    static int getLockStabilizeRestoreMax() {
        return LOCK_STABILIZE_RESTORE_MAX;
    }

    static long getScreenOffRecoveryMs() {
        return SCREEN_OFF_RECOVERY_MS;
    }

    static boolean hasAcceptedHomeGap(long homeGapMs) {
        return homeGapMs < 0L || homeGapMs >= HOME_GAP_ACCEPT_MS;
    }

    static synchronized void recordLockedHomeBurstIgnored(Context context, long gapMs, String reason) {
        SharedPreferences preferences = prefs(context);
        int count = preferences.getInt(KEY_LOCKED_HOME_BURST_IGNORED, 0) + 1;
        preferences.edit().putInt(KEY_LOCKED_HOME_BURST_IGNORED, count).apply();
        appendLog(context, "unlock_burst_ignored gap=" + gapMs
                + " required=" + HOME_GAP_ACCEPT_MS
                + " reason=" + reason
                + " total=" + count);
    }

    static int getLockedHomeBurstIgnored(Context context) {
        return prefs(context).getInt(KEY_LOCKED_HOME_BURST_IGNORED, 0);
    }

    static void markUnlockSettle(Context context, long durationMs, String reason) {
        long until = durationMs <= 0L ? 0L : System.currentTimeMillis() + durationMs;
        SharedPreferences.Editor editor = prefs(context).edit();
        if (until <= 0L) {
            editor.remove(KEY_UNLOCK_SETTLE_UNTIL);
        } else {
            editor.putLong(KEY_UNLOCK_SETTLE_UNTIL, until);
        }
        editor.apply();
        appendLog(context, "unlock settle until=" + until + " reason=" + reason);
    }

    static boolean isUnlockSettling(Context context) {
        long until = getUnlockSettleUntil(context);
        return until > 0L && System.currentTimeMillis() < until;
    }

    static long getUnlockSettleUntil(Context context) {
        return prefs(context).getLong(KEY_UNLOCK_SETTLE_UNTIL, 0L);
    }

    static long getUnlockSettleInMs(Context context) {
        long until = getUnlockSettleUntil(context);
        if (until <= 0L) {
            return -1L;
        }
        return Math.max(0L, until - System.currentTimeMillis());
    }

    static void clearUnlockSettle(Context context, String reason) {
        prefs(context).edit().remove(KEY_UNLOCK_SETTLE_UNTIL).apply();
        appendLog(context, "unlock settle cleared reason=" + reason);
    }

    static void markLockSettle(Context context, long durationMs, String reason) {
        long until = durationMs <= 0L ? 0L : System.currentTimeMillis() + durationMs;
        SharedPreferences.Editor editor = prefs(context).edit();
        if (until <= 0L) {
            editor.remove(KEY_LOCK_SETTLE_UNTIL);
        } else {
            editor.putLong(KEY_LOCK_SETTLE_UNTIL, until);
        }
        editor.apply();
        appendLog(context, "lock settle until=" + until + " reason=" + reason);
    }

    static boolean isLockSettling(Context context) {
        long until = getLockSettleUntil(context);
        return until > 0L && System.currentTimeMillis() < until;
    }

    static long getLockSettleUntil(Context context) {
        return prefs(context).getLong(KEY_LOCK_SETTLE_UNTIL, 0L);
    }

    static long getLockSettleInMs(Context context) {
        long until = getLockSettleUntil(context);
        if (until <= 0L) {
            return -1L;
        }
        return Math.max(0L, until - System.currentTimeMillis());
    }

    static void clearLockSettle(Context context, String reason) {
        prefs(context).edit().remove(KEY_LOCK_SETTLE_UNTIL).apply();
        appendLog(context, "lock settle cleared reason=" + reason);
    }

    static synchronized boolean tryRecordLockStabilizeRestore(Context context, int max, String reason) {
        SharedPreferences preferences = prefs(context);
        int count = preferences.getInt(KEY_LOCK_STABILIZE_RESTORE_COUNT, 0);
        if (count >= max) {
            appendLog(context, "lock_restore_forced_to_settle_skip reason=" + reason
                    + " count=" + count
                    + " max=" + max);
            return false;
        }
        int next = count + 1;
        preferences.edit().putInt(KEY_LOCK_STABILIZE_RESTORE_COUNT, next).apply();
        appendLog(context, "lock_restore_forced_to_settle_count count=" + next
                + " max=" + max
                + " reason=" + reason);
        return true;
    }

    static int getLockStabilizeRestoreCount(Context context) {
        return prefs(context).getInt(KEY_LOCK_STABILIZE_RESTORE_COUNT, 0);
    }

    static synchronized void recordScreenOffRecovery(Context context, String packageName) {
        SharedPreferences preferences = prefs(context);
        int count = preferences.getInt(KEY_SCREEN_OFF_RECOVERY_COUNT, 0) + 1;
        SharedPreferences.Editor editor = preferences.edit()
                .putInt(KEY_SCREEN_OFF_RECOVERY_COUNT, count)
                .putLong(KEY_SCREEN_OFF_RECOVERY_UNTIL,
                        System.currentTimeMillis() + SCREEN_OFF_RECOVERY_MS);
        if (packageName == null || packageName.length() == 0) {
            editor.remove(KEY_SCREEN_OFF_RECOVERY_PACKAGE);
        } else {
            editor.putString(KEY_SCREEN_OFF_RECOVERY_PACKAGE, packageName);
        }
        editor.apply();
        appendLog(context, "screen_off_during_lock target="
                + (packageName == null ? "none" : packageName)
                + " total=" + count);
    }

    static synchronized String consumeScreenOffRecoveryPackage(Context context) {
        SharedPreferences preferences = prefs(context);
        String packageName = preferences.getString(KEY_SCREEN_OFF_RECOVERY_PACKAGE, null);
        long until = preferences.getLong(KEY_SCREEN_OFF_RECOVERY_UNTIL, 0L);
        preferences.edit()
                .remove(KEY_SCREEN_OFF_RECOVERY_PACKAGE)
                .remove(KEY_SCREEN_OFF_RECOVERY_UNTIL)
                .commit();
        if (packageName == null || packageName.length() == 0) {
            appendLog(context, "screen_on_restore skipped target=none");
            return null;
        }
        if (until > 0L && System.currentTimeMillis() > until) {
            appendLog(context, "screen_on_restore expired target=" + packageName);
            return null;
        }
        appendLog(context, "screen_on_restore target=" + packageName);
        return packageName;
    }

    static void clearScreenOffRecovery(Context context, String reason) {
        prefs(context).edit()
                .remove(KEY_SCREEN_OFF_RECOVERY_PACKAGE)
                .remove(KEY_SCREEN_OFF_RECOVERY_UNTIL)
                .commit();
        appendLog(context, "screen_off_recovery cleared reason=" + reason);
    }

    static String getScreenOffRecoveryPackage(Context context) {
        String packageName = prefs(context).getString(KEY_SCREEN_OFF_RECOVERY_PACKAGE, null);
        long until = getScreenOffRecoveryUntil(context);
        if (packageName == null || packageName.length() == 0) {
            return null;
        }
        if (until > 0L && System.currentTimeMillis() > until) {
            return null;
        }
        return packageName;
    }

    static long getScreenOffRecoveryUntil(Context context) {
        return prefs(context).getLong(KEY_SCREEN_OFF_RECOVERY_UNTIL, 0L);
    }

    static int getScreenOffRecoveryCount(Context context) {
        return prefs(context).getInt(KEY_SCREEN_OFF_RECOVERY_COUNT, 0);
    }

    static long getCameraShutterMinIntervalMs() {
        return CAMERA_SHUTTER_MIN_INTERVAL_MS;
    }

    static synchronized boolean tryAcceptCameraShutter(Context context, String packageName) {
        SharedPreferences preferences = prefs(context);
        long now = System.currentTimeMillis();
        long lastAt = preferences.getLong(KEY_CAMERA_SHUTTER_LAST_AT, 0L);
        long gap = lastAt <= 0L ? -1L : Math.max(0L, now - lastAt);
        if (gap >= 0L && gap < CAMERA_SHUTTER_MIN_INTERVAL_MS) {
            int throttled = preferences.getInt(KEY_CAMERA_SHUTTER_THROTTLED, 0) + 1;
            preferences.edit().putInt(KEY_CAMERA_SHUTTER_THROTTLED, throttled).apply();
            appendLog(context, "camera_shutter_throttled package=" + packageName
                    + " gap=" + gap
                    + " required=" + CAMERA_SHUTTER_MIN_INTERVAL_MS
                    + " total=" + throttled);
            appendLockModeResult(context, "camera_shutter", "throttled",
                    "package=" + packageName + " gap=" + gap);
            return false;
        }

        int accepted = preferences.getInt(KEY_CAMERA_SHUTTER_ACCEPTED, 0) + 1;
        preferences.edit()
                .putLong(KEY_CAMERA_SHUTTER_LAST_AT, now)
                .putString(KEY_CAMERA_SHUTTER_LAST_PACKAGE, packageName == null ? "" : packageName)
                .putInt(KEY_CAMERA_SHUTTER_ACCEPTED, accepted)
                .apply();
        appendLog(context, "camera_shutter_accepted package=" + packageName
                + " count=" + accepted
                + " gap=" + gap);
        appendLockModeResult(context, "camera_shutter", "accepted",
                "package=" + packageName + " gap=" + gap);
        return true;
    }

    static synchronized void appendCameraBackendResult(
            Context context,
            String backend,
            boolean success,
            int exitCode,
            String detail
    ) {
        String stamp = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US)
                .format(new Date());
        String safeDetail = detail == null ? "" : detail.replace('\n', ' ').replace('\r', ' ');
        if (safeDetail.length() > 220) {
            safeDetail = safeDetail.substring(0, 220);
        }
        String line = stamp
                + " backend=" + backend
                + " success=" + success
                + " exit=" + exitCode
                + " detail=" + safeDetail;
        SharedPreferences preferences = prefs(context);
        String oldResults = preferences.getString(KEY_CAMERA_SHUTTER_BACKEND_RESULTS, "");
        String nextResults = line + "\n" + oldResults;
        if (nextResults.length() > MAX_CAMERA_BACKEND_CHARS) {
            nextResults = nextResults.substring(0, MAX_CAMERA_BACKEND_CHARS);
        }
        preferences.edit().putString(KEY_CAMERA_SHUTTER_BACKEND_RESULTS, nextResults).apply();
    }

    static long getCameraShutterLastAt(Context context) {
        return prefs(context).getLong(KEY_CAMERA_SHUTTER_LAST_AT, 0L);
    }

    static String getCameraShutterLastPackage(Context context) {
        String value = prefs(context).getString(KEY_CAMERA_SHUTTER_LAST_PACKAGE, null);
        return value == null || value.length() == 0 ? null : value;
    }

    static int getCameraShutterAcceptedCount(Context context) {
        return prefs(context).getInt(KEY_CAMERA_SHUTTER_ACCEPTED, 0);
    }

    static int getCameraShutterThrottledCount(Context context) {
        return prefs(context).getInt(KEY_CAMERA_SHUTTER_THROTTLED, 0);
    }

    static String getCameraShutterBackendResults(Context context) {
        return prefs(context).getString(KEY_CAMERA_SHUTTER_BACKEND_RESULTS, "");
    }

    static synchronized void recordCameraTapBackend(
            Context context,
            String backend,
            boolean success,
            float x,
            float y,
            String detail
    ) {
        SharedPreferences preferences = prefs(context);
        SharedPreferences.Editor editor = preferences.edit()
                .putString(KEY_CAMERA_TAP_BACKEND, backend == null ? "" : backend)
                .putFloat(KEY_CAMERA_TAP_LAST_X, x)
                .putFloat(KEY_CAMERA_TAP_LAST_Y, y);
        if (success) {
            editor.putInt(KEY_CAMERA_TAP_COUNT,
                    preferences.getInt(KEY_CAMERA_TAP_COUNT, 0) + 1);
        } else {
            editor.putInt(KEY_CAMERA_TAP_FAILED_COUNT,
                    preferences.getInt(KEY_CAMERA_TAP_FAILED_COUNT, 0) + 1);
        }
        editor.apply();
        appendCameraBackendResult(context, backend, success, success ? 0 : -1, detail);
    }

    static String getCameraTapBackend(Context context) {
        String value = prefs(context).getString(KEY_CAMERA_TAP_BACKEND, null);
        return value == null || value.length() == 0 ? null : value;
    }

    static int getCameraTapCount(Context context) {
        return prefs(context).getInt(KEY_CAMERA_TAP_COUNT, 0);
    }

    static int getCameraTapFailedCount(Context context) {
        return prefs(context).getInt(KEY_CAMERA_TAP_FAILED_COUNT, 0);
    }

    static float getCameraTapLastX(Context context) {
        return prefs(context).getFloat(KEY_CAMERA_TAP_LAST_X, -1f);
    }

    static float getCameraTapLastY(Context context) {
        return prefs(context).getFloat(KEY_CAMERA_TAP_LAST_Y, -1f);
    }

    static boolean hasCameraPermission(Context context) {
        return context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    static boolean hasPublicPhotoPermission(Context context) {
        return context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED;
    }

    static boolean canWriteSystemSettings(Context context) {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.System.canWrite(context);
    }

    static synchronized void enableIndefiniteScreenTimeout(Context context, String reason) {
        SharedPreferences preferences = prefs(context);
        if (!canWriteSystemSettings(context)) {
            preferences.edit()
                    .putString(KEY_SCREEN_TIMEOUT_LAST_RESULT,
                            "skip_no_write_settings reason=" + sanitizeForLog(reason))
                    .apply();
            appendLog(context, "screen_timeout_indefinite_skip reason=no_write_settings source="
                    + sanitizeForLog(reason));
            return;
        }
        String current = Settings.System.getString(
                context.getContentResolver(),
                Settings.System.SCREEN_OFF_TIMEOUT
        );
        SharedPreferences.Editor editor = preferences.edit();
        if (!preferences.getBoolean(KEY_SCREEN_TIMEOUT_CHANGED, false)) {
            editor.putString(KEY_SCREEN_TIMEOUT_ORIGINAL, current == null ? "" : current);
        }
        boolean ok = Settings.System.putInt(
                context.getContentResolver(),
                Settings.System.SCREEN_OFF_TIMEOUT,
                Integer.MAX_VALUE
        );
        editor.putBoolean(KEY_SCREEN_TIMEOUT_CHANGED, ok)
                .putString(KEY_SCREEN_TIMEOUT_LAST_RESULT,
                        (ok ? "ok" : "failed") + " indefinite reason=" + sanitizeForLog(reason)
                                + " previous=" + sanitizeForLog(current))
                .apply();
        appendLog(context, "screen_timeout_indefinite value=" + Integer.MAX_VALUE
                + " ok=" + ok
                + " reason=" + sanitizeForLog(reason)
                + " previous=" + sanitizeForLog(current));
    }

    static synchronized void restoreScreenTimeout(Context context, String reason) {
        SharedPreferences preferences = prefs(context);
        if (!preferences.getBoolean(KEY_SCREEN_TIMEOUT_CHANGED, false)) {
            appendLog(context, "screen_timeout_restore_skip reason=no_change source="
                    + sanitizeForLog(reason));
            return;
        }
        if (!canWriteSystemSettings(context)) {
            preferences.edit()
                    .putString(KEY_SCREEN_TIMEOUT_LAST_RESULT,
                            "restore_skip_no_write_settings reason=" + sanitizeForLog(reason))
                    .apply();
            appendLog(context, "screen_timeout_restore_skip reason=no_write_settings source="
                    + sanitizeForLog(reason));
            return;
        }
        String original = preferences.getString(KEY_SCREEN_TIMEOUT_ORIGINAL, "");
        boolean ok;
        if (original != null && original.length() > 0) {
            ok = Settings.System.putString(
                    context.getContentResolver(),
                    Settings.System.SCREEN_OFF_TIMEOUT,
                    original
            );
        } else {
            ok = Settings.System.putInt(
                    context.getContentResolver(),
                    Settings.System.SCREEN_OFF_TIMEOUT,
                    30_000
            );
        }
        SharedPreferences.Editor editor = preferences.edit()
                .putString(KEY_SCREEN_TIMEOUT_LAST_RESULT,
                        (ok ? "ok" : "failed") + " restore reason=" + sanitizeForLog(reason)
                                + " value=" + sanitizeForLog(original));
        if (ok) {
            editor.remove(KEY_SCREEN_TIMEOUT_ORIGINAL)
                    .putBoolean(KEY_SCREEN_TIMEOUT_CHANGED, false);
        }
        editor.apply();
        appendLog(context, "screen_timeout_restored ok=" + ok
                + " value=" + sanitizeForLog(original)
                + " reason=" + sanitizeForLog(reason));
    }

    static boolean isScreenTimeoutChanged(Context context) {
        return prefs(context).getBoolean(KEY_SCREEN_TIMEOUT_CHANGED, false);
    }

    static String getScreenTimeoutOriginal(Context context) {
        String value = prefs(context).getString(KEY_SCREEN_TIMEOUT_ORIGINAL, null);
        return value == null || value.length() == 0 ? null : value;
    }

    static String getScreenTimeoutLastResult(Context context) {
        String value = prefs(context).getString(KEY_SCREEN_TIMEOUT_LAST_RESULT, null);
        return value == null || value.length() == 0 ? null : value;
    }

    static String getCurrentScreenTimeout(Context context) {
        String value = Settings.System.getString(
                context.getContentResolver(),
                Settings.System.SCREEN_OFF_TIMEOUT
        );
        return value == null || value.length() == 0 ? null : value;
    }

    static void setWatchCameraActive(Context context, boolean active, String reason) {
        SharedPreferences.Editor editor = prefs(context).edit()
                .putBoolean(KEY_WATCH_CAMERA_ACTIVE, active);
        if (active) {
            editor.putLong(KEY_WATCH_CAMERA_LAST_ACTIVE, System.currentTimeMillis());
        }
        editor.apply();
        appendLog(context, "watch_camera active=" + active + " reason=" + reason);
    }

    static boolean isWatchCameraActive(Context context) {
        return prefs(context).getBoolean(KEY_WATCH_CAMERA_ACTIVE, false);
    }

    static long getWatchCameraLastActive(Context context) {
        return prefs(context).getLong(KEY_WATCH_CAMERA_LAST_ACTIVE, 0L);
    }

    static boolean isWatchCameraRecentlyActive(Context context, long maxAgeMs) {
        long lastActive = getWatchCameraLastActive(context);
        return lastActive > 0L
                && Math.max(0L, System.currentTimeMillis() - lastActive)
                < Math.max(1L, maxAgeMs);
    }

    static void markWatchCameraHomeBridge(Context context, String reason) {
        long until = System.currentTimeMillis() + WATCH_CAMERA_HOME_BRIDGE_MS;
        prefs(context).edit()
                .putLong(KEY_WATCH_CAMERA_HOME_BRIDGE_UNTIL, until)
                .putLong(KEY_WATCH_CAMERA_LAST_ACTIVE, System.currentTimeMillis())
                .apply();
        appendLog(context, "watch_camera home_bridge until=" + until + " reason=" + reason);
    }

    static boolean isWatchCameraHomeBridgeActive(Context context) {
        long until = prefs(context).getLong(KEY_WATCH_CAMERA_HOME_BRIDGE_UNTIL, 0L);
        return until > 0L && System.currentTimeMillis() < until;
    }

    static long getWatchCameraHomeBridgeUntil(Context context) {
        return prefs(context).getLong(KEY_WATCH_CAMERA_HOME_BRIDGE_UNTIL, 0L);
    }

    static void clearWatchCameraHomeBridge(Context context, String reason) {
        prefs(context).edit().remove(KEY_WATCH_CAMERA_HOME_BRIDGE_UNTIL).apply();
        appendLog(context, "watch_camera home_bridge cleared reason=" + reason);
    }

    static void markWatchCameraWarmBridge(Context context, long durationMs, String reason) {
        long until = System.currentTimeMillis() + Math.max(1L, durationMs);
        prefs(context).edit()
                .putLong(KEY_WATCH_CAMERA_WARM_BRIDGE_UNTIL, until)
                .putLong(KEY_WATCH_CAMERA_LAST_ACTIVE, System.currentTimeMillis())
                .apply();
        appendLog(context, "watch_camera_keep_warm until=" + until + " reason=" + reason);
    }

    static boolean isWatchCameraWarmBridgeActive(Context context) {
        long until = prefs(context).getLong(KEY_WATCH_CAMERA_WARM_BRIDGE_UNTIL, 0L);
        return until > 0L && System.currentTimeMillis() < until;
    }

    static long getWatchCameraWarmBridgeUntil(Context context) {
        return prefs(context).getLong(KEY_WATCH_CAMERA_WARM_BRIDGE_UNTIL, 0L);
    }

    static void clearWatchCameraWarmBridge(Context context, String reason) {
        prefs(context).edit().remove(KEY_WATCH_CAMERA_WARM_BRIDGE_UNTIL).apply();
        appendLog(context, "watch_camera warm_bridge cleared reason=" + reason);
    }

    static void setWatchCameraRuntime(
            Context context,
            boolean captureInFlight,
            boolean pendingShot,
            int generation
    ) {
        prefs(context).edit()
                .putBoolean(KEY_WATCH_CAMERA_CAPTURE_IN_FLIGHT, captureInFlight)
                .putBoolean(KEY_WATCH_CAMERA_PENDING_SHOT, pendingShot)
                .putInt(KEY_WATCH_CAMERA_GENERATION, generation)
                .apply();
    }

    static boolean isWatchCameraCaptureInFlight(Context context) {
        return prefs(context).getBoolean(KEY_WATCH_CAMERA_CAPTURE_IN_FLIGHT, false);
    }

    static boolean hasWatchCameraPendingShot(Context context) {
        return prefs(context).getBoolean(KEY_WATCH_CAMERA_PENDING_SHOT, false);
    }

    static int getWatchCameraGeneration(Context context) {
        return prefs(context).getInt(KEY_WATCH_CAMERA_GENERATION, 0);
    }

    static void recordWatchCameraWarmClose(Context context, String reason) {
        SharedPreferences preferences = prefs(context);
        int count = preferences.getInt(KEY_WATCH_CAMERA_WARM_CLOSES, 0) + 1;
        preferences.edit().putInt(KEY_WATCH_CAMERA_WARM_CLOSES, count).apply();
        appendLog(context, "watch_camera_warm_close count=" + count + " reason=" + reason);
    }

    static int getWatchCameraWarmCloses(Context context) {
        return prefs(context).getInt(KEY_WATCH_CAMERA_WARM_CLOSES, 0);
    }

    static synchronized void recordWatchCameraOpenOnly(Context context, String source) {
        SharedPreferences preferences = prefs(context);
        int count = preferences.getInt(KEY_WATCH_CAMERA_OPEN_ONLY, 0) + 1;
        preferences.edit()
                .putInt(KEY_WATCH_CAMERA_OPEN_ONLY, count)
                .apply();
        beginWatchCameraSession(context, source);
        appendLog(context, "watch_camera_open_only count=" + count
                + " source=" + sanitizeForLog(source));
        appendLockModeResult(context, "watchguard_camera", "open_only",
                "source=" + sanitizeForLog(source));
    }

    static int getWatchCameraOpenOnlyCount(Context context) {
        return prefs(context).getInt(KEY_WATCH_CAMERA_OPEN_ONLY, 0);
    }

    static synchronized void recordWatchCameraTouchIgnored(Context context, String source) {
        SharedPreferences preferences = prefs(context);
        int count = preferences.getInt(KEY_WATCH_CAMERA_TOUCH_IGNORED, 0) + 1;
        preferences.edit()
                .putInt(KEY_WATCH_CAMERA_TOUCH_IGNORED, count)
                .apply();
        appendLog(context, "watch_camera_touch_ignored count=" + count
                + " source=" + sanitizeForLog(source));
    }

    static int getWatchCameraTouchIgnoredCount(Context context) {
        return prefs(context).getInt(KEY_WATCH_CAMERA_TOUCH_IGNORED, 0);
    }

    static synchronized void recordWatchCameraInputNoise(Context context, String source) {
        SharedPreferences preferences = prefs(context);
        long until = System.currentTimeMillis() + WATCH_CAMERA_INPUT_NOISE_MS;
        int count = preferences.getInt(KEY_WATCH_CAMERA_INPUT_NOISE_COUNT, 0) + 1;
        preferences.edit()
                .putLong(KEY_WATCH_CAMERA_INPUT_NOISE_UNTIL, until)
                .putInt(KEY_WATCH_CAMERA_INPUT_NOISE_COUNT, count)
                .putString(KEY_WATCH_CAMERA_LAST_NOISE_SOURCE, source == null ? "" : source)
                .apply();
        appendLog(context, "watch_camera_input_noise count=" + count
                + " source=" + sanitizeForLog(source)
                + " until=" + until);
    }

    static boolean isWatchCameraInputNoiseActive(Context context) {
        long until = prefs(context).getLong(KEY_WATCH_CAMERA_INPUT_NOISE_UNTIL, 0L);
        return until > 0L && System.currentTimeMillis() < until;
    }

    static long getWatchCameraInputNoiseUntil(Context context) {
        return prefs(context).getLong(KEY_WATCH_CAMERA_INPUT_NOISE_UNTIL, 0L);
    }

    static int getWatchCameraInputNoiseCount(Context context) {
        return prefs(context).getInt(KEY_WATCH_CAMERA_INPUT_NOISE_COUNT, 0);
    }

    static String getWatchCameraLastNoiseSource(Context context) {
        String value = prefs(context).getString(KEY_WATCH_CAMERA_LAST_NOISE_SOURCE, null);
        return value == null || value.length() == 0 ? null : value;
    }

    static synchronized void recordWatchCameraHomeIgnoredNoise(Context context, long homeGapMs, String source) {
        SharedPreferences preferences = prefs(context);
        int count = preferences.getInt(KEY_WATCH_CAMERA_HOME_IGNORED_NOISE, 0) + 1;
        long remaining = Math.max(0L, getWatchCameraInputNoiseUntil(context) - System.currentTimeMillis());
        preferences.edit()
                .putInt(KEY_WATCH_CAMERA_HOME_IGNORED_NOISE, count)
                .apply();
        appendLog(context, "watch_camera_home_ignored_noise count=" + count
                + " source=" + sanitizeForLog(source)
                + " gap=" + homeGapMs
                + " remaining=" + remaining
                + " noise=" + sanitizeForLog(getWatchCameraLastNoiseSource(context)));
        appendLockModeResult(context, "watchguard_camera", "home_ignored_noise",
                "source=" + sanitizeForLog(source)
                        + " gap=" + homeGapMs
                        + " remaining=" + remaining);
    }

    static int getWatchCameraHomeIgnoredNoiseCount(Context context) {
        return prefs(context).getInt(KEY_WATCH_CAMERA_HOME_IGNORED_NOISE, 0);
    }

    static synchronized void recordWatchCameraHomePulse(
            Context context,
            String source,
            long homeGapMs,
            long clickElapsedMs
    ) {
        long now = System.currentTimeMillis();
        prefs(context).edit()
                .putLong(KEY_WATCH_CAMERA_LAST_HOME_PULSE_AT, now)
                .putString(KEY_WATCH_CAMERA_LAST_HOME_PULSE_SOURCE, source == null ? "" : source)
                .putLong(KEY_WATCH_CAMERA_LAST_HOME_GAP, homeGapMs)
                .putLong(KEY_WATCH_CAMERA_LAST_CLICK_ELAPSED, clickElapsedMs)
                .apply();
        appendLog(context, "watch_camera_home_pulse source=" + sanitizeForLog(source)
                + " home_gap=" + homeGapMs
                + " click_elapsed=" + clickElapsedMs);
    }

    static long getWatchCameraLastHomePulseAt(Context context) {
        return prefs(context).getLong(KEY_WATCH_CAMERA_LAST_HOME_PULSE_AT, 0L);
    }

    static String getWatchCameraLastHomePulseSource(Context context) {
        String value = prefs(context).getString(KEY_WATCH_CAMERA_LAST_HOME_PULSE_SOURCE, null);
        return value == null || value.length() == 0 ? null : value;
    }

    static long getWatchCameraLastHomeGap(Context context) {
        return prefs(context).getLong(KEY_WATCH_CAMERA_LAST_HOME_GAP, -1L);
    }

    static long getWatchCameraLastClickElapsed(Context context) {
        return prefs(context).getLong(KEY_WATCH_CAMERA_LAST_CLICK_ELAPSED, -1L);
    }

    static synchronized void setWatchCameraSingleClickArmed(
            Context context,
            boolean armed,
            long deadlineInMs,
            String reason
    ) {
        SharedPreferences preferences = prefs(context);
        long deadline = armed ? System.currentTimeMillis() + Math.max(0L, deadlineInMs) : 0L;
        long armedAt = preferences.getLong(KEY_WATCH_CAMERA_SINGLE_CLICK_ARMED_AT, 0L);
        if (!armed) {
            armedAt = 0L;
        } else if (!preferences.getBoolean(KEY_WATCH_CAMERA_SINGLE_CLICK_ARMED, false)
                || armedAt <= 0L) {
            armedAt = System.currentTimeMillis();
        }
        preferences.edit()
                .putBoolean(KEY_WATCH_CAMERA_SINGLE_CLICK_ARMED, armed)
                .putLong(KEY_WATCH_CAMERA_SINGLE_CLICK_ARMED_AT, armedAt)
                .putLong(KEY_WATCH_CAMERA_SINGLE_CLICK_DEADLINE, deadline)
                .apply();
        appendLog(context, "watch_camera_single_click_state armed=" + armed
                + " armed_at=" + armedAt
                + " deadline=" + deadline
                + " reason=" + sanitizeForLog(reason));
    }

    static boolean isWatchCameraSingleClickArmed(Context context) {
        return prefs(context).getBoolean(KEY_WATCH_CAMERA_SINGLE_CLICK_ARMED, false);
    }

    static long getWatchCameraSingleClickDeadline(Context context) {
        return prefs(context).getLong(KEY_WATCH_CAMERA_SINGLE_CLICK_DEADLINE, 0L);
    }

    static long getWatchCameraSingleClickArmedAt(Context context) {
        return prefs(context).getLong(KEY_WATCH_CAMERA_SINGLE_CLICK_ARMED_AT, 0L);
    }

    static synchronized void recordWatchCameraCaptureRequested(
            Context context,
            String source,
            boolean previewReady
    ) {
        SharedPreferences preferences = prefs(context);
        int count = preferences.getInt(KEY_WATCH_CAMERA_CAPTURE_REQUESTED, 0) + 1;
        preferences.edit()
                .putInt(KEY_WATCH_CAMERA_CAPTURE_REQUESTED, count)
                .remove(KEY_WATCH_CAMERA_LAST_BLOCK_REASON)
                .apply();
        appendLog(context, "watch_camera_capture_requested count=" + count
                + " source=" + sanitizeForLog(source)
                + " preview_ready=" + previewReady);
    }

    static int getWatchCameraCaptureRequestedCount(Context context) {
        return prefs(context).getInt(KEY_WATCH_CAMERA_CAPTURE_REQUESTED, 0);
    }

    static synchronized void recordWatchCameraWaitingPreview(
            Context context,
            String source,
            boolean previewReady,
            boolean handlerReady
    ) {
        String reason = "waiting_preview source=" + sanitizeForLog(source)
                + " preview_ready=" + previewReady
                + " handler_ready=" + handlerReady;
        prefs(context).edit()
                .putString(KEY_WATCH_CAMERA_LAST_BLOCK_REASON, reason)
                .apply();
        appendLog(context, "watch_camera_waiting_preview " + reason);
    }

    static synchronized void recordWatchCameraLastBlock(Context context, String reason) {
        prefs(context).edit()
                .putString(KEY_WATCH_CAMERA_LAST_BLOCK_REASON, reason == null ? "" : reason)
                .apply();
        appendLog(context, "watch_camera_block reason=" + sanitizeForLog(reason));
    }

    static String getWatchCameraLastBlockReason(Context context) {
        String value = prefs(context).getString(KEY_WATCH_CAMERA_LAST_BLOCK_REASON, null);
        return value == null || value.length() == 0 ? null : value;
    }

    static synchronized void recordWatchCameraGptShareLaunch(Context context, String source) {
        SharedPreferences preferences = prefs(context);
        int count = preferences.getInt(KEY_WATCH_CAMERA_GPT_SHARE_LAUNCH, 0) + 1;
        preferences.edit()
                .putInt(KEY_WATCH_CAMERA_GPT_SHARE_LAUNCH, count)
                .apply();
        appendLog(context, "watch_camera_gpt_share_launch count=" + count
                + " source=" + sanitizeForLog(source));
        appendLockModeResult(context, "watchguard_camera", "gpt_share_launch",
                sanitizeForLog(source));
    }

    static int getWatchCameraGptShareLaunchCount(Context context) {
        return prefs(context).getInt(KEY_WATCH_CAMERA_GPT_SHARE_LAUNCH, 0);
    }

    static synchronized void deactivateWatchCameraForGpt(Context context, String reason) {
        SharedPreferences preferences = prefs(context);
        int count = preferences.getInt(KEY_WATCH_CAMERA_DEACTIVATED_FOR_GPT, 0) + 1;
        preferences.edit()
                .putInt(KEY_WATCH_CAMERA_DEACTIVATED_FOR_GPT, count)
                .putBoolean(KEY_WATCH_CAMERA_ACTIVE, false)
                .putBoolean(KEY_WATCH_CAMERA_CAPTURE_IN_FLIGHT, false)
                .putBoolean(KEY_WATCH_CAMERA_PENDING_SHOT, false)
                .remove(KEY_WATCH_CAMERA_HOME_BRIDGE_UNTIL)
                .remove(KEY_WATCH_CAMERA_WARM_BRIDGE_UNTIL)
                .apply();
        appendLog(context, "watch_camera_deactivated_for_gpt count=" + count
                + " reason=" + sanitizeForLog(reason));
    }

    static int getWatchCameraDeactivatedForGptCount(Context context) {
        return prefs(context).getInt(KEY_WATCH_CAMERA_DEACTIVATED_FOR_GPT, 0);
    }

    static long getWatchCameraShotReadyInMs(Context context) {
        long lastAt = prefs(context).getLong(KEY_WATCH_CAMERA_LAST_SHOT_AT, 0L);
        if (lastAt <= 0L) {
            return 0L;
        }
        long elapsed = Math.max(0L, System.currentTimeMillis() - lastAt);
        return Math.max(0L, CAMERA_SHUTTER_MIN_INTERVAL_MS - elapsed);
    }

    static synchronized boolean tryAcceptWatchCameraShot(Context context, String source) {
        SharedPreferences preferences = prefs(context);
        long now = System.currentTimeMillis();
        long lastAt = preferences.getLong(KEY_WATCH_CAMERA_LAST_SHOT_AT, 0L);
        long gap = lastAt <= 0L ? -1L : Math.max(0L, now - lastAt);
        if (gap >= 0L && gap < CAMERA_SHUTTER_MIN_INTERVAL_MS) {
            int throttled = preferences.getInt(KEY_WATCH_CAMERA_THROTTLED, 0) + 1;
            preferences.edit().putInt(KEY_WATCH_CAMERA_THROTTLED, throttled).apply();
            appendLog(context, "watch_camera_shot_throttled source=" + source
                    + " gap=" + gap
                    + " required=" + CAMERA_SHUTTER_MIN_INTERVAL_MS
                    + " total=" + throttled);
            appendLockModeResult(context, "watchguard_camera", "throttled",
                    "source=" + source + " gap=" + gap);
            return false;
        }
        preferences.edit()
                .putLong(KEY_WATCH_CAMERA_LAST_SHOT_AT, now)
                .putString(KEY_WATCH_CAMERA_BACKEND, "camera2")
                .apply();
        appendLog(context, "watch_camera_shot_accepted source=" + source + " gap=" + gap);
        return true;
    }

    static synchronized void recordWatchCameraPhoto(Context context, String path) {
        recordWatchCameraPhoto(context, path, "app_private");
    }

    static synchronized void recordWatchCameraPhoto(Context context, String path, String storageBackend) {
        SharedPreferences preferences = prefs(context);
        int count = preferences.getInt(KEY_WATCH_CAMERA_PHOTO_COUNT, 0) + 1;
        int sessionCount = preferences.getInt(KEY_WATCH_CAMERA_SESSION_PHOTO_COUNT, 0) + 1;
        String photos = appendLine(preferences.getString(KEY_WATCH_CAMERA_SESSION_PHOTOS, ""), path);
        preferences.edit()
                .putInt(KEY_WATCH_CAMERA_PHOTO_COUNT, count)
                .putString(KEY_WATCH_CAMERA_LAST_FILE, path == null ? "" : path)
                .putString(KEY_WATCH_CAMERA_SESSION_PHOTOS, photos)
                .putInt(KEY_WATCH_CAMERA_SESSION_PHOTO_COUNT, sessionCount)
                .remove(KEY_WATCH_CAMERA_LAST_ERROR)
                .putString(KEY_WATCH_CAMERA_BACKEND, "camera2")
                .putString(KEY_WATCH_CAMERA_STORAGE_BACKEND,
                        storageBackend == null ? "unknown" : storageBackend)
                .apply();
        appendLog(context, "watch_camera_photo_saved count=" + count
                + " session_count=" + sessionCount
                + " storage=" + (storageBackend == null ? "unknown" : storageBackend)
                + " path=" + (path == null ? "none" : path));
        appendLockModeResult(context, "watchguard_camera", "photo_saved",
                path == null ? "" : path);
    }

    static synchronized void beginWatchCameraSession(Context context, String source) {
        SharedPreferences preferences = prefs(context);
        long sessionId = System.currentTimeMillis();
        preferences.edit()
                .putLong(KEY_WATCH_CAMERA_SESSION_ID, sessionId)
                .putLong(KEY_WATCH_CAMERA_SESSION_STARTED, sessionId)
                .putString(KEY_WATCH_CAMERA_SESSION_PHOTOS, "")
                .putInt(KEY_WATCH_CAMERA_SESSION_PHOTO_COUNT, 0)
                .putInt(KEY_WATCH_CAMERA_PENDING_CAPTURE_COUNT, 0)
                .putBoolean(KEY_GPT_SHARE_PENDING, false)
                .apply();
        appendLog(context, "watch_camera_session_begin id=" + sessionId
                + " source=" + sanitizeForLog(source));
    }

    static long getWatchCameraSessionId(Context context) {
        return prefs(context).getLong(KEY_WATCH_CAMERA_SESSION_ID, 0L);
    }

    static long getWatchCameraSessionStarted(Context context) {
        return prefs(context).getLong(KEY_WATCH_CAMERA_SESSION_STARTED, 0L);
    }

    static String getWatchCameraSessionPhotosRaw(Context context) {
        return prefs(context).getString(KEY_WATCH_CAMERA_SESSION_PHOTOS, "");
    }

    static int getWatchCameraSessionPhotoCount(Context context) {
        return prefs(context).getInt(KEY_WATCH_CAMERA_SESSION_PHOTO_COUNT, 0);
    }

    static synchronized void setWatchCameraPendingCaptureCount(Context context, int count, String reason) {
        prefs(context).edit()
                .putInt(KEY_WATCH_CAMERA_PENDING_CAPTURE_COUNT, Math.max(0, count))
                .apply();
        appendLog(context, "watch_camera_pending_capture_count count=" + Math.max(0, count)
                + " reason=" + sanitizeForLog(reason));
    }

    static int getWatchCameraPendingCaptureCount(Context context) {
        return prefs(context).getInt(KEY_WATCH_CAMERA_PENDING_CAPTURE_COUNT, 0);
    }

    static synchronized void recordWatchCameraDoubleClick(
            Context context,
            String source,
            long ageMs,
            String ageSource
    ) {
        SharedPreferences preferences = prefs(context);
        int count = preferences.getInt(KEY_WATCH_CAMERA_DOUBLE_CLICK_COUNT, 0) + 1;
        long safeAge = Math.max(0L, ageMs);
        String safeAgeSource = ageSource == null || ageSource.length() == 0
                ? "unknown"
                : ageSource;
        preferences.edit()
                .putInt(KEY_WATCH_CAMERA_DOUBLE_CLICK_COUNT, count)
                .putLong(KEY_WATCH_CAMERA_DOUBLE_CLICK_LAST_AGE, safeAge)
                .putLong(KEY_WATCH_CAMERA_DOUBLE_CLICK_EFFECTIVE_AGE, safeAge)
                .putString(KEY_WATCH_CAMERA_DOUBLE_CLICK_AGE_SOURCE, safeAgeSource)
                .apply();
        appendLog(context, "watch_camera_double_click count=" + count
                + " age=" + safeAge
                + " age_source=" + sanitizeForLog(safeAgeSource)
                + " source=" + sanitizeForLog(source));
        appendLockModeResult(context, "watchguard_camera", "double_click_gpt",
                "age=" + safeAge
                        + " age_source=" + sanitizeForLog(safeAgeSource)
                        + " source=" + sanitizeForLog(source));
    }

    static int getWatchCameraDoubleClickCount(Context context) {
        return prefs(context).getInt(KEY_WATCH_CAMERA_DOUBLE_CLICK_COUNT, 0);
    }

    static long getWatchCameraDoubleClickLastAge(Context context) {
        return prefs(context).getLong(KEY_WATCH_CAMERA_DOUBLE_CLICK_LAST_AGE, -1L);
    }

    static String getWatchCameraDoubleClickAgeSource(Context context) {
        String value = prefs(context).getString(KEY_WATCH_CAMERA_DOUBLE_CLICK_AGE_SOURCE, null);
        return value == null || value.length() == 0 ? null : value;
    }

    static long getWatchCameraDoubleClickEffectiveAge(Context context) {
        return prefs(context).getLong(KEY_WATCH_CAMERA_DOUBLE_CLICK_EFFECTIVE_AGE, -1L);
    }

    static synchronized void recordWatchCameraDirectKeyPulseScheduled(Context context, String source) {
        SharedPreferences preferences = prefs(context);
        int count = preferences.getInt(KEY_WATCH_CAMERA_DIRECT_KEY_PULSE_SCHEDULED, 0) + 1;
        preferences.edit()
                .putInt(KEY_WATCH_CAMERA_DIRECT_KEY_PULSE_SCHEDULED, count)
                .apply();
        appendLog(context, "watch_camera_direct_key_pulse_scheduled count=" + count
                + " source=" + sanitizeForLog(source));
    }

    static synchronized void recordWatchCameraDirectKeyPulseFired(Context context, String source) {
        SharedPreferences preferences = prefs(context);
        int count = preferences.getInt(KEY_WATCH_CAMERA_DIRECT_KEY_PULSE_FIRED, 0) + 1;
        preferences.edit()
                .putInt(KEY_WATCH_CAMERA_DIRECT_KEY_PULSE_FIRED, count)
                .apply();
        appendLog(context, "watch_camera_direct_key_pulse_fired count=" + count
                + " source=" + sanitizeForLog(source));
    }

    static synchronized void recordWatchCameraDirectKeyPulseCancelledHome(Context context, String source) {
        SharedPreferences preferences = prefs(context);
        int count = preferences.getInt(KEY_WATCH_CAMERA_DIRECT_KEY_PULSE_CANCELLED_HOME, 0) + 1;
        preferences.edit()
                .putInt(KEY_WATCH_CAMERA_DIRECT_KEY_PULSE_CANCELLED_HOME, count)
                .apply();
        appendLog(context, "watch_camera_direct_key_pulse_cancelled_home count=" + count
                + " source=" + sanitizeForLog(source));
    }

    static int getWatchCameraDirectKeyPulseScheduledCount(Context context) {
        return prefs(context).getInt(KEY_WATCH_CAMERA_DIRECT_KEY_PULSE_SCHEDULED, 0);
    }

    static int getWatchCameraDirectKeyPulseFiredCount(Context context) {
        return prefs(context).getInt(KEY_WATCH_CAMERA_DIRECT_KEY_PULSE_FIRED, 0);
    }

    static int getWatchCameraDirectKeyPulseCancelledHomeCount(Context context) {
        return prefs(context).getInt(KEY_WATCH_CAMERA_DIRECT_KEY_PULSE_CANCELLED_HOME, 0);
    }

    static synchronized void recordWatchCameraTouchDoubleCandidate(Context context, long ageMs) {
        SharedPreferences preferences = prefs(context);
        int count = preferences.getInt(KEY_WATCH_CAMERA_TOUCH_DOUBLE_CANDIDATE, 0) + 1;
        long safeAge = Math.max(0L, ageMs);
        preferences.edit()
                .putInt(KEY_WATCH_CAMERA_TOUCH_DOUBLE_CANDIDATE, count)
                .putLong(KEY_WATCH_CAMERA_TOUCH_DOUBLE_LAST_AGE, safeAge)
                .apply();
        appendLog(context, "watch_camera_touch_double_candidate count=" + count
                + " age=" + safeAge);
    }

    static synchronized void recordWatchCameraTouchDoubleAccepted(
            Context context,
            long ageMs,
            int photoCount
    ) {
        SharedPreferences preferences = prefs(context);
        int count = preferences.getInt(KEY_WATCH_CAMERA_TOUCH_DOUBLE_ACCEPTED, 0) + 1;
        long safeAge = Math.max(0L, ageMs);
        preferences.edit()
                .putInt(KEY_WATCH_CAMERA_TOUCH_DOUBLE_ACCEPTED, count)
                .putLong(KEY_WATCH_CAMERA_TOUCH_DOUBLE_LAST_AGE, safeAge)
                .apply();
        appendLog(context, "watch_camera_touch_double_accepted count=" + count
                + " age=" + safeAge
                + " photos=" + Math.max(0, photoCount));
    }

    static int getWatchCameraTouchDoubleCandidateCount(Context context) {
        return prefs(context).getInt(KEY_WATCH_CAMERA_TOUCH_DOUBLE_CANDIDATE, 0);
    }

    static int getWatchCameraTouchDoubleAcceptedCount(Context context) {
        return prefs(context).getInt(KEY_WATCH_CAMERA_TOUCH_DOUBLE_ACCEPTED, 0);
    }

    static long getWatchCameraTouchDoubleLastAge(Context context) {
        return prefs(context).getLong(KEY_WATCH_CAMERA_TOUCH_DOUBLE_LAST_AGE, -1L);
    }

    static synchronized void recordWatchCameraEdgeSwipeBlocked(Context context, String edge) {
        String normalized = "top".equals(edge) ? "top" : "bottom";
        String countKey = "top".equals(normalized)
                ? KEY_WATCH_CAMERA_EDGE_SWIPE_TOP_BLOCKED
                : KEY_WATCH_CAMERA_EDGE_SWIPE_BOTTOM_BLOCKED;
        SharedPreferences preferences = prefs(context);
        int count = preferences.getInt(countKey, 0) + 1;
        preferences.edit()
                .putInt(countKey, count)
                .putString(KEY_WATCH_CAMERA_EDGE_SWIPE_LAST_EDGE, normalized)
                .apply();
        appendLog(context, "watch_camera_edge_swipe_blocked edge=" + normalized
                + " count=" + count);
    }

    static int getWatchCameraEdgeSwipeTopBlockedCount(Context context) {
        return prefs(context).getInt(KEY_WATCH_CAMERA_EDGE_SWIPE_TOP_BLOCKED, 0);
    }

    static int getWatchCameraEdgeSwipeBottomBlockedCount(Context context) {
        return prefs(context).getInt(KEY_WATCH_CAMERA_EDGE_SWIPE_BOTTOM_BLOCKED, 0);
    }

    static String getWatchCameraEdgeSwipeLastEdge(Context context) {
        String value = prefs(context).getString(KEY_WATCH_CAMERA_EDGE_SWIPE_LAST_EDGE, null);
        return value == null || value.length() == 0 ? null : value;
    }

    static synchronized void recordWatchCameraSystemUiReapplied(Context context, String reason) {
        SharedPreferences preferences = prefs(context);
        int count = preferences.getInt(KEY_WATCH_CAMERA_SYSTEM_UI_REAPPLY, 0) + 1;
        preferences.edit()
                .putInt(KEY_WATCH_CAMERA_SYSTEM_UI_REAPPLY, count)
                .apply();
        appendLog(context, "watch_camera_immersive_reapplied count=" + count
                + " reason=" + sanitizeForLog(reason));
    }

    static int getWatchCameraSystemUiReapplyCount(Context context) {
        return prefs(context).getInt(KEY_WATCH_CAMERA_SYSTEM_UI_REAPPLY, 0);
    }

    static synchronized void markWatchCameraGestureGuardHeartbeat(Context context, String source) {
        prefs(context).edit()
                .putLong(KEY_WATCH_CAMERA_GESTURE_GUARD_HEARTBEAT, System.currentTimeMillis())
                .apply();
    }

    static long getWatchCameraGestureGuardHeartbeatAt(Context context) {
        return prefs(context).getLong(KEY_WATCH_CAMERA_GESTURE_GUARD_HEARTBEAT, 0L);
    }

    static synchronized void recordWatchCameraGestureGuardStarted(Context context, String reason) {
        SharedPreferences preferences = prefs(context);
        int count = preferences.getInt(KEY_WATCH_CAMERA_GESTURE_GUARD_STARTED, 0) + 1;
        preferences.edit()
                .putBoolean(KEY_WATCH_CAMERA_GESTURE_GUARD_ACTIVE, true)
                .putLong(KEY_WATCH_CAMERA_GESTURE_GUARD_HEARTBEAT, System.currentTimeMillis())
                .putInt(KEY_WATCH_CAMERA_GESTURE_GUARD_STARTED, count)
                .apply();
        appendLog(context, "watch_camera_gesture_guard_started count=" + count
                + " reason=" + sanitizeForLog(reason));
    }

    static synchronized void recordWatchCameraGestureGuardStopped(Context context, String reason) {
        SharedPreferences preferences = prefs(context);
        int count = preferences.getInt(KEY_WATCH_CAMERA_GESTURE_GUARD_STOPPED, 0) + 1;
        preferences.edit()
                .putBoolean(KEY_WATCH_CAMERA_GESTURE_GUARD_ACTIVE, false)
                .putInt(KEY_WATCH_CAMERA_GESTURE_GUARD_STOPPED, count)
                .apply();
        appendLog(context, "watch_camera_gesture_guard_stopped count=" + count
                + " reason=" + sanitizeForLog(reason));
    }

    static synchronized void setWatchCameraGestureGuardActive(
            Context context,
            boolean active,
            String reason
    ) {
        prefs(context).edit()
                .putBoolean(KEY_WATCH_CAMERA_GESTURE_GUARD_ACTIVE, active)
                .apply();
        appendLog(context, "watch_camera_gesture_guard_active=" + active
                + " reason=" + sanitizeForLog(reason));
    }

    static boolean isWatchCameraGestureGuardActive(Context context) {
        return prefs(context).getBoolean(KEY_WATCH_CAMERA_GESTURE_GUARD_ACTIVE, false);
    }

    static int getWatchCameraGestureGuardStartedCount(Context context) {
        return prefs(context).getInt(KEY_WATCH_CAMERA_GESTURE_GUARD_STARTED, 0);
    }

    static int getWatchCameraGestureGuardStoppedCount(Context context) {
        return prefs(context).getInt(KEY_WATCH_CAMERA_GESTURE_GUARD_STOPPED, 0);
    }

    static synchronized void recordWatchCameraGestureGuardSwipeBlocked(Context context, String edge) {
        String normalized = "top".equals(edge) ? "top" : "bottom";
        String countKey = "top".equals(normalized)
                ? KEY_WATCH_CAMERA_TOP_SWIPE_BLOCKED
                : KEY_WATCH_CAMERA_BOTTOM_SWIPE_BLOCKED;
        SharedPreferences preferences = prefs(context);
        int count = preferences.getInt(countKey, 0) + 1;
        preferences.edit()
                .putInt(countKey, count)
                .apply();
        String logName = "top".equals(normalized)
                ? "watch_camera_top_swipe_blocked"
                : "watch_camera_bottom_swipe_blocked";
        appendLog(context, logName + " count=" + count);
        appendLockModeResult(context, "watchguard_camera", logName,
                "edge=" + normalized);
    }

    static int getWatchCameraTopSwipeBlockedCount(Context context) {
        return prefs(context).getInt(KEY_WATCH_CAMERA_TOP_SWIPE_BLOCKED, 0);
    }

    static int getWatchCameraBottomSwipeBlockedCount(Context context) {
        return prefs(context).getInt(KEY_WATCH_CAMERA_BOTTOM_SWIPE_BLOCKED, 0);
    }

    static synchronized void recordWatchCameraSystemPanelEscape(Context context, String packageName) {
        SharedPreferences preferences = prefs(context);
        int count = preferences.getInt(KEY_WATCH_CAMERA_SYSTEM_PANEL_ESCAPE, 0) + 1;
        preferences.edit()
                .putInt(KEY_WATCH_CAMERA_SYSTEM_PANEL_ESCAPE, count)
                .putString(KEY_WATCH_CAMERA_SYSTEM_PANEL_LAST_PACKAGE,
                        packageName == null ? "" : packageName)
                .apply();
        appendLog(context, "watch_camera_system_panel_escape count=" + count
                + " package=" + sanitizeForLog(packageName));
        appendLockModeResult(context, "watchguard_camera", "system_panel_escape",
                "package=" + sanitizeForLog(packageName));
    }

    static int getWatchCameraSystemPanelEscapeCount(Context context) {
        return prefs(context).getInt(KEY_WATCH_CAMERA_SYSTEM_PANEL_ESCAPE, 0);
    }

    static synchronized void markWatchCameraPanelRescue(
            Context context,
            String packageName,
            String reason
    ) {
        long now = System.currentTimeMillis();
        long until = now + WATCH_CAMERA_PANEL_RESCUE_MS;
        String safePackage = packageName == null ? "" : packageName;
        prefs(context).edit()
                .putLong(KEY_WATCH_CAMERA_PANEL_RESCUE_UNTIL, until)
                .putString(KEY_WATCH_CAMERA_PANEL_RESCUE_LAST_PACKAGE, safePackage)
                .putString(KEY_WATCH_CAMERA_SYSTEM_PANEL_LAST_PACKAGE, safePackage)
                .putLong(KEY_WATCH_CAMERA_SYSTEM_PANEL_REBOUND_AT, now)
                .putLong(KEY_WATCH_CAMERA_LAST_ACTIVE, now)
                .apply();
        appendLog(context, "watch_camera_panel_rescue_started until=" + until
                + " package=" + sanitizeForLog(packageName)
                + " reason=" + sanitizeForLog(reason));
    }

    static boolean isWatchCameraPanelRescueActive(Context context) {
        long until = prefs(context).getLong(KEY_WATCH_CAMERA_PANEL_RESCUE_UNTIL, 0L);
        return until > 0L && System.currentTimeMillis() < until;
    }

    static long getWatchCameraPanelRescueUntil(Context context) {
        return prefs(context).getLong(KEY_WATCH_CAMERA_PANEL_RESCUE_UNTIL, 0L);
    }

    static String getWatchCameraPanelRescueLastPackage(Context context) {
        String value = prefs(context).getString(KEY_WATCH_CAMERA_PANEL_RESCUE_LAST_PACKAGE, null);
        return value == null || value.length() == 0 ? null : value;
    }

    static boolean isWatchCameraPanelDetectionArmed(Context context) {
        return isWatchCameraActive(context)
                || isWatchCameraHomeBridgeActive(context)
                || isWatchCameraWarmBridgeActive(context)
                || isWatchCameraPanelRescueActive(context)
                || isWatchCameraTouchShieldActive(context)
                || isWatchCameraGestureGuardActive(context)
                || isWatchCameraRecentlyActive(context, WATCH_CAMERA_PANEL_DETECTION_GRACE_MS);
    }

    static boolean shouldPreserveWatchCameraForPanel(Context context) {
        return isWatchCameraPanelRescueActive(context)
                || isWatchCameraSystemPanelReboundRecent(context);
    }

    static void clearWatchCameraPanelRescue(Context context, String reason) {
        prefs(context).edit()
                .putLong(KEY_WATCH_CAMERA_PANEL_RESCUE_UNTIL, 0L)
                .apply();
        appendLog(context, "watch_camera_panel_rescue_cleared reason="
                + sanitizeForLog(reason));
    }

    static synchronized void recordWatchCameraPowerKeyConsumed(
            Context context,
            String keyLabel,
            String source
    ) {
        SharedPreferences preferences = prefs(context);
        int count = preferences.getInt(KEY_WATCH_CAMERA_POWER_KEY_CONSUMED, 0) + 1;
        preferences.edit()
                .putInt(KEY_WATCH_CAMERA_POWER_KEY_CONSUMED, count)
                .apply();
        appendLog(context, "watch_camera_power_key_consumed_no_shutter count=" + count
                + " key=" + sanitizeForLog(keyLabel)
                + " source=" + sanitizeForLog(source));
    }

    static int getWatchCameraPowerKeyConsumedCount(Context context) {
        return prefs(context).getInt(KEY_WATCH_CAMERA_POWER_KEY_CONSUMED, 0);
    }

    static synchronized void recordWatchCameraScreenOffDuringCamera(Context context, String source) {
        SharedPreferences preferences = prefs(context);
        int count = preferences.getInt(KEY_WATCH_CAMERA_SCREEN_OFF_DURING_CAMERA, 0) + 1;
        preferences.edit()
                .putInt(KEY_WATCH_CAMERA_SCREEN_OFF_DURING_CAMERA, count)
                .apply();
        appendLog(context, "watch_camera_screen_off_during_camera count=" + count
                + " source=" + sanitizeForLog(source));
    }

    static int getWatchCameraScreenOffDuringCameraCount(Context context) {
        return prefs(context).getInt(KEY_WATCH_CAMERA_SCREEN_OFF_DURING_CAMERA, 0);
    }

    static synchronized void recordWatchCameraTouchShieldStarted(
            Context context,
            String mode,
            String reason
    ) {
        SharedPreferences preferences = prefs(context);
        String safeMode = normalizeWatchCameraTouchShieldMode(mode);
        boolean alreadyActive = preferences.getBoolean(KEY_WATCH_CAMERA_TOUCH_SHIELD_ACTIVE, false)
                && safeMode.equals(preferences.getString(KEY_WATCH_CAMERA_TOUCH_SHIELD_MODE, ""));
        SharedPreferences.Editor editor = preferences.edit()
                .putBoolean(KEY_WATCH_CAMERA_TOUCH_SHIELD_ACTIVE, true)
                .putString(KEY_WATCH_CAMERA_TOUCH_SHIELD_MODE, safeMode);
        int count = preferences.getInt(KEY_WATCH_CAMERA_TOUCH_SHIELD_STARTED, 0);
        if (!alreadyActive) {
            count += 1;
            editor.putInt(KEY_WATCH_CAMERA_TOUCH_SHIELD_STARTED, count);
        }
        editor.apply();
        if (!alreadyActive) {
            appendLog(context, "watch_camera_touch_shield_started count=" + count
                    + " mode=" + sanitizeForLog(safeMode)
                    + " reason=" + sanitizeForLog(reason));
        }
    }

    static synchronized void recordWatchCameraTouchShieldStopped(
            Context context,
            String mode,
            String reason
    ) {
        SharedPreferences preferences = prefs(context);
        String safeMode = normalizeWatchCameraTouchShieldMode(mode);
        String currentMode = preferences.getString(KEY_WATCH_CAMERA_TOUCH_SHIELD_MODE, "inactive");
        boolean sameMode = safeMode.equals(currentMode);
        boolean wasActive = preferences.getBoolean(KEY_WATCH_CAMERA_TOUCH_SHIELD_ACTIVE, false)
                && sameMode;
        int count = preferences.getInt(KEY_WATCH_CAMERA_TOUCH_SHIELD_STOPPED, 0);
        SharedPreferences.Editor editor = preferences.edit();
        if (sameMode || !preferences.getBoolean(KEY_WATCH_CAMERA_TOUCH_SHIELD_ACTIVE, false)) {
            editor.putBoolean(KEY_WATCH_CAMERA_TOUCH_SHIELD_ACTIVE, false)
                    .putString(KEY_WATCH_CAMERA_TOUCH_SHIELD_MODE, "inactive");
        }
        if (wasActive) {
            count += 1;
            editor.putInt(KEY_WATCH_CAMERA_TOUCH_SHIELD_STOPPED, count);
        }
        editor.apply();
        if (wasActive) {
            appendLog(context, "watch_camera_touch_shield_stopped count=" + count
                    + " mode=" + sanitizeForLog(safeMode)
                    + " reason=" + sanitizeForLog(reason));
        }
    }

    static String getWatchCameraTouchShieldMode(Context context) {
        String value = prefs(context).getString(KEY_WATCH_CAMERA_TOUCH_SHIELD_MODE, "inactive");
        return value == null || value.length() == 0 ? "inactive" : value;
    }

    static boolean isWatchCameraTouchShieldActive(Context context) {
        return prefs(context).getBoolean(KEY_WATCH_CAMERA_TOUCH_SHIELD_ACTIVE, false);
    }

    static int getWatchCameraTouchShieldStartedCount(Context context) {
        return prefs(context).getInt(KEY_WATCH_CAMERA_TOUCH_SHIELD_STARTED, 0);
    }

    static int getWatchCameraTouchShieldStoppedCount(Context context) {
        return prefs(context).getInt(KEY_WATCH_CAMERA_TOUCH_SHIELD_STOPPED, 0);
    }

    static synchronized void recordWatchCameraTouchShieldTapProxy(
            Context context,
            String mode,
            long durationMs
    ) {
        SharedPreferences preferences = prefs(context);
        int count = preferences.getInt(KEY_WATCH_CAMERA_TOUCH_SHIELD_TAP_PROXY, 0) + 1;
        preferences.edit()
                .putInt(KEY_WATCH_CAMERA_TOUCH_SHIELD_TAP_PROXY, count)
                .apply();
        appendLog(context, "watch_camera_touch_shield_tap_proxy count=" + count
                + " mode=" + sanitizeForLog(normalizeWatchCameraTouchShieldMode(mode))
                + " duration=" + durationMs);
    }

    static int getWatchCameraTouchShieldTapProxyCount(Context context) {
        return prefs(context).getInt(KEY_WATCH_CAMERA_TOUCH_SHIELD_TAP_PROXY, 0);
    }

    static boolean isWatchCameraTouchProxyEnabled() {
        return false;
    }

    static boolean isWatchCameraTouchInertEnabled() {
        return true;
    }

    static synchronized void recordWatchCameraTouchAbsorbed(
            Context context,
            String mode,
            String source
    ) {
        SharedPreferences preferences = prefs(context);
        int count = preferences.getInt(KEY_WATCH_CAMERA_TOUCH_ABSORBED, 0) + 1;
        preferences.edit()
                .putInt(KEY_WATCH_CAMERA_TOUCH_ABSORBED, count)
                .apply();
        if (count <= 3 || count % 10 == 0) {
            appendLog(context, "watch_camera_touch_absorbed count=" + count
                    + " mode=" + sanitizeForLog(normalizeWatchCameraTouchShieldMode(mode))
                    + " source=" + sanitizeForLog(source));
        }
    }

    static int getWatchCameraTouchAbsorbedCount(Context context) {
        return prefs(context).getInt(KEY_WATCH_CAMERA_TOUCH_ABSORBED, 0);
    }

    static synchronized void recordWatchCameraSystemPanelRebound(
            Context context,
            String packageName,
            String reason
    ) {
        SharedPreferences preferences = prefs(context);
        int count = preferences.getInt(KEY_WATCH_CAMERA_SYSTEM_PANEL_REBOUND, 0) + 1;
        preferences.edit()
                .putInt(KEY_WATCH_CAMERA_SYSTEM_PANEL_REBOUND, count)
                .putString(KEY_WATCH_CAMERA_SYSTEM_PANEL_LAST_PACKAGE,
                        packageName == null ? "" : packageName)
                .putLong(KEY_WATCH_CAMERA_SYSTEM_PANEL_REBOUND_AT, System.currentTimeMillis())
                .apply();
        appendLog(context, "watch_camera_system_panel_rebound count=" + count
                + " package=" + sanitizeForLog(packageName)
                + " reason=" + sanitizeForLog(reason));
        appendLockModeResult(context, "watchguard_camera", "system_panel_rebound",
                "package=" + sanitizeForLog(packageName)
                        + " reason=" + sanitizeForLog(reason));
    }

    static int getWatchCameraSystemPanelReboundCount(Context context) {
        return prefs(context).getInt(KEY_WATCH_CAMERA_SYSTEM_PANEL_REBOUND, 0);
    }

    static String getWatchCameraSystemPanelLastPackage(Context context) {
        String value = prefs(context).getString(KEY_WATCH_CAMERA_SYSTEM_PANEL_LAST_PACKAGE, null);
        return value == null || value.length() == 0 ? null : value;
    }

    static synchronized void recordWatchCameraSystemPanelBackAction(
            Context context,
            String packageName,
            boolean success
    ) {
        SharedPreferences preferences = prefs(context);
        int count = preferences.getInt(KEY_WATCH_CAMERA_SYSTEM_PANEL_BACK_ACTION, 0) + 1;
        preferences.edit()
                .putInt(KEY_WATCH_CAMERA_SYSTEM_PANEL_BACK_ACTION, count)
                .putString(KEY_WATCH_CAMERA_SYSTEM_PANEL_LAST_PACKAGE,
                        packageName == null ? "" : packageName)
                .putLong(KEY_WATCH_CAMERA_SYSTEM_PANEL_REBOUND_AT, System.currentTimeMillis())
                .apply();
        appendLog(context, "watch_camera_system_panel_back_action count=" + count
                + " package=" + sanitizeForLog(packageName)
                + " success=" + success);
    }

    static int getWatchCameraSystemPanelBackActionCount(Context context) {
        return prefs(context).getInt(KEY_WATCH_CAMERA_SYSTEM_PANEL_BACK_ACTION, 0);
    }

    static synchronized void recordWatchCameraSystemPanelFastRebound(
            Context context,
            String packageName,
            String reason
    ) {
        SharedPreferences preferences = prefs(context);
        int count = preferences.getInt(KEY_WATCH_CAMERA_SYSTEM_PANEL_FAST_REBOUND, 0) + 1;
        preferences.edit()
                .putInt(KEY_WATCH_CAMERA_SYSTEM_PANEL_FAST_REBOUND, count)
                .putString(KEY_WATCH_CAMERA_SYSTEM_PANEL_LAST_PACKAGE,
                        packageName == null ? "" : packageName)
                .putLong(KEY_WATCH_CAMERA_SYSTEM_PANEL_REBOUND_AT, System.currentTimeMillis())
                .apply();
        appendLog(context, "watch_camera_system_panel_fast_rebound count=" + count
                + " package=" + sanitizeForLog(packageName)
                + " reason=" + sanitizeForLog(reason));
        appendLockModeResult(context, "watchguard_camera", "system_panel_fast_rebound",
                "package=" + sanitizeForLog(packageName)
                        + " reason=" + sanitizeForLog(reason));
    }

    static int getWatchCameraSystemPanelFastReboundCount(Context context) {
        return prefs(context).getInt(KEY_WATCH_CAMERA_SYSTEM_PANEL_FAST_REBOUND, 0);
    }

    static synchronized void recordWatchCameraPanelRestoreAttempt(
            Context context,
            String packageName,
            long delayMs
    ) {
        SharedPreferences preferences = prefs(context);
        int count = preferences.getInt(KEY_WATCH_CAMERA_PANEL_RESTORE_ATTEMPT, 0) + 1;
        SharedPreferences.Editor editor = preferences.edit()
                .putInt(KEY_WATCH_CAMERA_PANEL_RESTORE_ATTEMPT, count)
                .putString(KEY_WATCH_CAMERA_SYSTEM_PANEL_LAST_PACKAGE,
                        packageName == null ? "" : packageName)
                .putLong(KEY_WATCH_CAMERA_SYSTEM_PANEL_REBOUND_AT, System.currentTimeMillis());
        String normalized = packageName == null ? "" : packageName.toLowerCase(Locale.US);
        if ("com.dw.downmenu".equals(normalized)) {
            int downmenuCount = preferences.getInt(
                    KEY_WATCH_CAMERA_DOWNMENU_RESTORE_ATTEMPT,
                    0
            ) + 1;
            editor.putInt(KEY_WATCH_CAMERA_DOWNMENU_RESTORE_ATTEMPT, downmenuCount);
        }
        editor.apply();
        appendLog(context, "watch_camera_panel_restore_attempt count=" + count
                + " package=" + sanitizeForLog(packageName)
                + " delay_ms=" + delayMs);
    }

    static int getWatchCameraPanelRestoreAttemptCount(Context context) {
        return prefs(context).getInt(KEY_WATCH_CAMERA_PANEL_RESTORE_ATTEMPT, 0);
    }

    static int getWatchCameraDownmenuRestoreAttemptCount(Context context) {
        return prefs(context).getInt(KEY_WATCH_CAMERA_DOWNMENU_RESTORE_ATTEMPT, 0);
    }

    static synchronized void recordWatchCameraSystemPanelBurst(
            Context context,
            String packageName,
            int step
    ) {
        SharedPreferences preferences = prefs(context);
        int count = preferences.getInt(KEY_WATCH_CAMERA_SYSTEM_PANEL_BURST, 0) + 1;
        preferences.edit()
                .putInt(KEY_WATCH_CAMERA_SYSTEM_PANEL_BURST, count)
                .apply();
        appendLog(context, "watch_camera_system_panel_burst count=" + count
                + " step=" + step
                + " package=" + sanitizeForLog(packageName));
    }

    static int getWatchCameraSystemPanelBurstCount(Context context) {
        return prefs(context).getInt(KEY_WATCH_CAMERA_SYSTEM_PANEL_BURST, 0);
    }

    static synchronized void recordWatchCameraFalsePanelEventIgnored(
            Context context,
            String packageName,
            int eventType,
            String reason
    ) {
        SharedPreferences preferences = prefs(context);
        int count = preferences.getInt(KEY_WATCH_CAMERA_FALSE_PANEL_EVENT_IGNORED, 0) + 1;
        preferences.edit()
                .putInt(KEY_WATCH_CAMERA_FALSE_PANEL_EVENT_IGNORED, count)
                .apply();
        if (count <= 5 || count % 10 == 0) {
            appendLog(context, "watch_camera_false_panel_event_ignored count=" + count
                    + " package=" + sanitizeForLog(packageName)
                    + " type=" + eventType
                    + " reason=" + sanitizeForLog(reason));
        }
    }

    static int getWatchCameraFalsePanelEventIgnoredCount(Context context) {
        return prefs(context).getInt(KEY_WATCH_CAMERA_FALSE_PANEL_EVENT_IGNORED, 0);
    }

    static synchronized void recordWatchCameraNativeCameraBounceBlocked(
            Context context,
            String packageName,
            String source
    ) {
        SharedPreferences preferences = prefs(context);
        int count = preferences.getInt(KEY_WATCH_CAMERA_NATIVE_CAMERA_BOUNCE_BLOCKED, 0) + 1;
        preferences.edit()
                .putInt(KEY_WATCH_CAMERA_NATIVE_CAMERA_BOUNCE_BLOCKED, count)
                .apply();
        appendLog(context, "watch_camera_native_camera_bounce_blocked count=" + count
                + " package=" + sanitizeForLog(packageName)
                + " source=" + sanitizeForLog(source));
        appendLockModeResult(context, "watchguard_camera", "native_camera_bounce_blocked",
                "package=" + sanitizeForLog(packageName)
                        + " source=" + sanitizeForLog(source));
    }

    static int getWatchCameraNativeCameraBounceBlockedCount(Context context) {
        return prefs(context).getInt(KEY_WATCH_CAMERA_NATIVE_CAMERA_BOUNCE_BLOCKED, 0);
    }

    static synchronized void recordWatchCameraPanelBackSuppressed(
            Context context,
            String source
    ) {
        SharedPreferences preferences = prefs(context);
        int count = preferences.getInt(KEY_WATCH_CAMERA_PANEL_BACK_SUPPRESSED, 0) + 1;
        preferences.edit()
                .putInt(KEY_WATCH_CAMERA_PANEL_BACK_SUPPRESSED, count)
                .apply();
        appendLog(context, "watch_camera_panel_back_suppressed count=" + count
                + " source=" + sanitizeForLog(source));
    }

    static int getWatchCameraPanelBackSuppressedCount(Context context) {
        return prefs(context).getInt(KEY_WATCH_CAMERA_PANEL_BACK_SUPPRESSED, 0);
    }

    static boolean isWatchCameraSystemPanelReboundRecent(Context context) {
        long at = prefs(context).getLong(KEY_WATCH_CAMERA_SYSTEM_PANEL_REBOUND_AT, 0L);
        return isWatchCameraPanelRescueActive(context)
                || (at > 0L && Math.max(0L, System.currentTimeMillis() - at) < 3_000L);
    }

    static synchronized void recordWatchCameraPausePreservedForPanel(
            Context context,
            String reason
    ) {
        SharedPreferences preferences = prefs(context);
        int count = preferences.getInt(KEY_WATCH_CAMERA_PAUSE_PRESERVED_FOR_PANEL, 0) + 1;
        preferences.edit()
                .putInt(KEY_WATCH_CAMERA_PAUSE_PRESERVED_FOR_PANEL, count)
                .apply();
        appendLog(context, "watch_camera_pause_preserved_for_panel count=" + count
                + " reason=" + sanitizeForLog(reason));
    }

    static int getWatchCameraPausePreservedForPanelCount(Context context) {
        return prefs(context).getInt(KEY_WATCH_CAMERA_PAUSE_PRESERVED_FOR_PANEL, 0);
    }

    private static String normalizeWatchCameraTouchShieldMode(String mode) {
        if ("accessibility_overlay".equals(mode) || "app_overlay".equals(mode)) {
            return mode;
        }
        return "inactive";
    }

    static synchronized void recordWatchCameraRotaryIgnored(
            Context context,
            String keyName,
            int scanCode,
            int deviceId
    ) {
        recordWatchCameraRotaryIgnored(context, keyName, scanCode, deviceId, "camera_activity");
    }

    static synchronized void recordWatchCameraRotaryIgnored(
            Context context,
            String keyName,
            int scanCode,
            int deviceId,
            String source
    ) {
        SharedPreferences preferences = prefs(context);
        int count = preferences.getInt(KEY_WATCH_CAMERA_ROTARY_IGNORED, 0) + 1;
        int guardCount = preferences.getInt(KEY_WATCH_CAMERA_ROTARY_GUARD_COUNT, 0) + 1;
        long until = System.currentTimeMillis() + WATCH_CAMERA_ROTARY_GUARD_MS;
        String variant = rotaryVariantLabel(keyName, scanCode);
        String blockedCounts = incrementCompactCount(
                preferences.getString(KEY_WATCH_CAMERA_ROTARY_BLOCKED_KEY_COUNTS, ""),
                variant,
                8
        );
        preferences.edit()
                .putInt(KEY_WATCH_CAMERA_ROTARY_IGNORED, count)
                .putString(KEY_WATCH_CAMERA_ROTARY_LAST_KEY, keyName == null ? "" : keyName)
                .putInt(KEY_WATCH_CAMERA_ROTARY_LAST_SCAN, scanCode)
                .putLong(KEY_WATCH_CAMERA_ROTARY_GUARD_UNTIL, until)
                .putInt(KEY_WATCH_CAMERA_ROTARY_GUARD_COUNT, guardCount)
                .putString(KEY_WATCH_CAMERA_ROTARY_LAST_SOURCE, source == null ? "" : source)
                .putString(KEY_WATCH_CAMERA_ROTARY_VARIANT, variant)
                .putString(KEY_WATCH_CAMERA_ROTARY_BLOCKED_KEY_COUNTS, blockedCounts)
                .apply();
        appendLog(context, "watch_camera_rotary_ignored count=" + count
                + " key=" + sanitizeForLog(keyName)
                + " scan=" + scanCode
                + " variant=" + sanitizeForLog(variant)
                + " device=" + deviceId
                + " source=" + sanitizeForLog(source)
                + " guard_until=" + until);
    }

    static synchronized void recordWatchCameraRotaryCancelledPending(
            Context context,
            String source,
            boolean hadPending
    ) {
        SharedPreferences preferences = prefs(context);
        int count = preferences.getInt(KEY_WATCH_CAMERA_ROTARY_CANCELLED_PENDING, 0)
                + (hadPending ? 1 : 0);
        SharedPreferences.Editor editor = preferences.edit()
                .putInt(KEY_WATCH_CAMERA_PENDING_CAPTURE_COUNT, 0)
                .putBoolean(KEY_WATCH_CAMERA_SINGLE_CLICK_ARMED, false)
                .putLong(KEY_WATCH_CAMERA_SINGLE_CLICK_ARMED_AT, 0L)
                .putLong(KEY_WATCH_CAMERA_SINGLE_CLICK_DEADLINE, 0L)
                .putBoolean(KEY_WATCH_CAMERA_PENDING_SHOT, false);
        if (hadPending) {
            editor.putInt(KEY_WATCH_CAMERA_ROTARY_CANCELLED_PENDING, count);
        }
        editor.apply();
        appendLog(context, "rotary_cancelled_pending had_pending=" + hadPending
                + " count=" + count
                + " source=" + sanitizeForLog(source)
                + " suppress_until=" + getWatchCameraRotaryGuardUntil(context));
        if (hadPending) {
            appendLockModeResult(context, "watchguard_camera", "rotary_cancelled_pending",
                    "source=" + sanitizeForLog(source));
        }
    }

    static int getWatchCameraRotaryIgnoredCount(Context context) {
        return prefs(context).getInt(KEY_WATCH_CAMERA_ROTARY_IGNORED, 0);
    }

    static String getWatchCameraRotaryLastKey(Context context) {
        String value = prefs(context).getString(KEY_WATCH_CAMERA_ROTARY_LAST_KEY, null);
        return value == null || value.length() == 0 ? null : value;
    }

    static int getWatchCameraRotaryLastScan(Context context) {
        return prefs(context).getInt(KEY_WATCH_CAMERA_ROTARY_LAST_SCAN, -1);
    }

    static boolean isWatchCameraRotaryGuardActive(Context context) {
        long until = getWatchCameraRotaryGuardUntil(context);
        return until > 0L && System.currentTimeMillis() < until;
    }

    static long getWatchCameraRotaryGuardUntil(Context context) {
        return prefs(context).getLong(KEY_WATCH_CAMERA_ROTARY_GUARD_UNTIL, 0L);
    }

    static int getWatchCameraRotaryGuardCount(Context context) {
        return prefs(context).getInt(KEY_WATCH_CAMERA_ROTARY_GUARD_COUNT, 0);
    }

    static String getWatchCameraRotaryLastSource(Context context) {
        String value = prefs(context).getString(KEY_WATCH_CAMERA_ROTARY_LAST_SOURCE, null);
        return value == null || value.length() == 0 ? null : value;
    }

    static String getWatchCameraRotaryVariant(Context context) {
        String value = prefs(context).getString(KEY_WATCH_CAMERA_ROTARY_VARIANT, null);
        return value == null || value.length() == 0 ? null : value;
    }

    static int getWatchCameraRotaryCancelledPendingCount(Context context) {
        return prefs(context).getInt(KEY_WATCH_CAMERA_ROTARY_CANCELLED_PENDING, 0);
    }

    static String getWatchCameraRotaryBlockedKeyCounts(Context context) {
        String value = prefs(context).getString(KEY_WATCH_CAMERA_ROTARY_BLOCKED_KEY_COUNTS, null);
        return value == null || value.length() == 0 ? null : value;
    }

    static synchronized void recordWatchCameraRealButton(Context context, String keyLabel) {
        prefs(context).edit()
                .putString(KEY_WATCH_CAMERA_REAL_BUTTON_LAST_KEY,
                        keyLabel == null ? "" : keyLabel)
                .apply();
        appendLog(context, "watch_camera_real_button key=" + sanitizeForLog(keyLabel));
    }

    static String getWatchCameraRealButtonLastKey(Context context) {
        String value = prefs(context).getString(KEY_WATCH_CAMERA_REAL_BUTTON_LAST_KEY, null);
        return value == null || value.length() == 0 ? null : value;
    }

    static synchronized void recordWatchCameraRejectedButton(Context context, String keyLabel) {
        prefs(context).edit()
                .putString(KEY_WATCH_CAMERA_REJECTED_BUTTON_KEY,
                        keyLabel == null ? "" : keyLabel)
                .apply();
        appendLog(context, "watch_camera_rejected_button key=" + sanitizeForLog(keyLabel));
    }

    static String getWatchCameraRejectedButtonKey(Context context) {
        String value = prefs(context).getString(KEY_WATCH_CAMERA_REJECTED_BUTTON_KEY, null);
        return value == null || value.length() == 0 ? null : value;
    }

    static long getWatchCameraRotarySuppressUntil(Context context) {
        return getWatchCameraRotaryGuardUntil(context);
    }

    private static String rotaryVariantLabel(String keyName, int scanCode) {
        String key = keyName == null ? "" : keyName;
        if (scanCode == 87 || key.contains("F11")) {
            return "f11_scan87";
        }
        if (scanCode == 66 || key.contains("F8")) {
            return "f8_scan66";
        }
        if (scanCode == 65 || key.contains("F7")) {
            return "f7_scan65";
        }
        if (scanCode == 61 || key.contains("F3")) {
            return "f3_scan61";
        }
        if (key.length() > 0) {
            return sanitizeForLog(key).toLowerCase(Locale.US);
        }
        return "rotary_source";
    }

    static synchronized void recordWatchCameraRotaryBlockedShutter(
            Context context,
            long homeGapMs,
            String source
    ) {
        SharedPreferences preferences = prefs(context);
        int count = preferences.getInt(KEY_WATCH_CAMERA_ROTARY_BLOCKED_SHUTTER, 0) + 1;
        long remaining = Math.max(0L, getWatchCameraRotaryGuardUntil(context)
                - System.currentTimeMillis());
        preferences.edit()
                .putInt(KEY_WATCH_CAMERA_ROTARY_BLOCKED_SHUTTER, count)
                .apply();
        appendLog(context, "camera_shutter_blocked_rotary_guard count=" + count
                + " source=" + sanitizeForLog(source)
                + " home_gap=" + homeGapMs
                + " remaining=" + remaining
                + " rotary_source=" + sanitizeForLog(getWatchCameraRotaryLastSource(context)));
        appendLockModeResult(context, "camera_shutter", "blocked_rotary_guard",
                "source=" + sanitizeForLog(source)
                        + " gap=" + homeGapMs
                        + " remaining=" + remaining);
    }

    static int getWatchCameraRotaryBlockedShutterCount(Context context) {
        return prefs(context).getInt(KEY_WATCH_CAMERA_ROTARY_BLOCKED_SHUTTER, 0);
    }

    static synchronized void markGptPhotoShareNoPhotos(Context context, long sessionId) {
        SharedPreferences preferences = prefs(context);
        int count = preferences.getInt(KEY_GPT_SHARE_NO_PHOTOS, 0) + 1;
        preferences.edit()
                .putInt(KEY_GPT_SHARE_NO_PHOTOS, count)
                .putString(KEY_GPT_SHARE_LAST_RESULT, "no_photos session=" + sessionId)
                .putBoolean(KEY_GPT_SHARE_PENDING, false)
                .apply();
        appendLog(context, "gpt_photo_share_no_photos count=" + count
                + " session=" + sessionId);
    }

    static synchronized void startGptPhotoShare(
            Context context,
            long sessionId,
            String prompt,
            String allPhotos,
            int chunkCount,
            boolean needsNewChat,
            String targetMode,
            String fallbackReason
    ) {
        SharedPreferences preferences = prefs(context);
        int launchCount = preferences.getInt(KEY_GPT_SHARE_LAUNCH_COUNT, 0) + 1;
        preferences.edit()
                .putBoolean(KEY_GPT_SHARE_PENDING, true)
                .putLong(KEY_GPT_SHARE_SESSION_ID, sessionId)
                .putString(KEY_GPT_SHARE_PROMPT, prompt == null ? "" : prompt)
                .putString(KEY_GPT_SHARE_ALL_PHOTOS, allPhotos == null ? "" : allPhotos)
                .putInt(KEY_GPT_SHARE_CHUNK_INDEX, 0)
                .putInt(KEY_GPT_SHARE_CHUNK_COUNT, Math.max(0, chunkCount))
                .putInt(KEY_GPT_SHARE_CHUNK_SIZE, 0)
                .putBoolean(KEY_GPT_SHARE_NEEDS_NEW_CHAT, needsNewChat)
                .putString(KEY_GPT_SHARE_TARGET_MODE, targetMode == null ? "" : targetMode)
                .putString(KEY_GPT_SHARE_TARGET_FALLBACK_REASON,
                        fallbackReason == null ? "" : fallbackReason)
                .putInt(KEY_GPT_SHARE_LAUNCH_COUNT, launchCount)
                .putString(KEY_GPT_SHARE_LAST_RESULT, "pending session=" + sessionId)
                .putString(KEY_GPT_AUTOMATION_PHASE, "pending")
                .putString(KEY_GPT_AUTOMATION_GATE_REASON, "waiting_share_intent")
                .putInt(KEY_GPT_AUTOMATION_ATTACHMENT_EXPECTED, 0)
                .putInt(KEY_GPT_AUTOMATION_ATTACHMENT_EVIDENCE, 0)
                .putInt(KEY_GPT_AUTOMATION_ATTACHMENT_STREAK, 0)
                .putLong(KEY_GPT_AUTOMATION_CHUNK_LAUNCHED_AT, 0L)
                .putString(KEY_GPT_AUTOMATION_SAFE_SEND_CANDIDATE, "")
                .putString(KEY_GPT_AUTOMATION_SEND_CANDIDATE_SOURCE, "")
                .putLong(KEY_GPT_AUTOMATION_SEND_CLICK_AT, 0L)
                .putInt(KEY_GPT_AUTOMATION_ATTACHMENT_REQUIRED, 0)
                .putString(KEY_GPT_AUTOMATION_SEND_CONFIRMATION_PHASE, "idle")
                .putLong(KEY_GPT_AUTOMATION_SEND_PENDING_AT, 0L)
                .putString(KEY_GPT_AUTOMATION_SEND_PENDING_SOURCE, "")
                .putInt(KEY_GPT_AUTOMATION_SEND_CONFIRM_RETRIES, 0)
                .putString(KEY_GPT_AUTOMATION_LAST_CLICK_BOUNDS, "")
                .putBoolean(KEY_GPT_AUTOMATION_LAST_CLICK_WAS_COMPOSER, false)
                .putLong(KEY_GPT_AUTOMATION_ATTACHMENTS_READY_AT, 0L)
                .putLong(KEY_GPT_AUTOMATION_PROMPT_READY_AT, 0L)
                .putLong(KEY_GPT_AUTOMATION_SEND_GESTURE_ATTEMPTED_AT, 0L)
                .putString(KEY_GPT_AUTOMATION_ROOT_SOURCE, "")
                .putString(KEY_GPT_AUTOMATION_ROOT_PACKAGE, "")
                .putString(KEY_GPT_AUTOMATION_LOCKED_OVERLAY_MODE, "")
                .putBoolean(KEY_GPT_AUTO_LOCK_REQUESTED, false)
                .putBoolean(KEY_GPT_AUTO_LOCK_ACTIVE, false)
                .putString(KEY_GPT_AUTO_LOCK_STATE, "")
                .putLong(KEY_GPT_AUTO_LOCK_STARTED_AT, 0L)
                .putInt(KEY_GPT_SHARE_REATTACH_CHUNK_INDEX, -1)
                .commit();
        appendLog(context, "gpt_photo_share_start session=" + sessionId
                + " chunks=" + chunkCount
                + " new_chat=" + needsNewChat
                + " target_mode=" + sanitizeForLog(targetMode)
                + " launch_count=" + launchCount);
    }

    static synchronized void markGptShareChunkLaunched(Context context, int chunkIndex, int chunkSize) {
        long now = System.currentTimeMillis();
        prefs(context).edit()
                .putInt(KEY_GPT_SHARE_CHUNK_INDEX, chunkIndex)
                .putInt(KEY_GPT_SHARE_CHUNK_SIZE, Math.max(0, chunkSize))
                .putString(KEY_GPT_AUTOMATION_PHASE, "chunk_launched")
                .putString(KEY_GPT_AUTOMATION_GATE_REASON, "waiting_min_attachment_load")
                .putInt(KEY_GPT_AUTOMATION_ATTACHMENT_EXPECTED, Math.max(0, chunkSize))
                .putInt(KEY_GPT_AUTOMATION_ATTACHMENT_EVIDENCE, 0)
                .putInt(KEY_GPT_AUTOMATION_ATTACHMENT_STREAK, 0)
                .putLong(KEY_GPT_AUTOMATION_CHUNK_LAUNCHED_AT, now)
                .putString(KEY_GPT_AUTOMATION_SAFE_SEND_CANDIDATE, "")
                .putString(KEY_GPT_AUTOMATION_SEND_CANDIDATE_SOURCE, "")
                .putLong(KEY_GPT_AUTOMATION_SEND_CLICK_AT, 0L)
                .putInt(KEY_GPT_AUTOMATION_ATTACHMENT_REQUIRED, Math.max(0, chunkSize))
                .putString(KEY_GPT_AUTOMATION_SEND_CONFIRMATION_PHASE, "idle")
                .putLong(KEY_GPT_AUTOMATION_SEND_PENDING_AT, 0L)
                .putString(KEY_GPT_AUTOMATION_SEND_PENDING_SOURCE, "")
                .putInt(KEY_GPT_AUTOMATION_SEND_CONFIRM_RETRIES, 0)
                .putString(KEY_GPT_AUTOMATION_LAST_CLICK_BOUNDS, "")
                .putBoolean(KEY_GPT_AUTOMATION_LAST_CLICK_WAS_COMPOSER, false)
                .putLong(KEY_GPT_AUTOMATION_ATTACHMENTS_READY_AT, 0L)
                .putLong(KEY_GPT_AUTOMATION_PROMPT_READY_AT, 0L)
                .putLong(KEY_GPT_AUTOMATION_SEND_GESTURE_ATTEMPTED_AT, 0L)
                .putString(KEY_GPT_SHARE_LAST_RESULT,
                        "chunk_launched index=" + chunkIndex + " size=" + chunkSize)
                .commit();
        appendLog(context, "gpt_photo_share_chunk_launched index=" + chunkIndex
                + " size=" + chunkSize);
    }

    static synchronized void requestGptAutoLock(Context context, String source) {
        prefs(context).edit()
                .putBoolean(KEY_GPT_AUTO_LOCK_REQUESTED, true)
                .putBoolean(KEY_GPT_AUTO_LOCK_ACTIVE, false)
                .putString(KEY_GPT_AUTO_LOCK_STATE, "waiting_gpt_visible")
                .putLong(KEY_GPT_AUTO_LOCK_STARTED_AT, 0L)
                .putString(KEY_GPT_AUTOMATION_LOCKED_OVERLAY_MODE, "pending")
                .apply();
        appendLog(context, "gpt_auto_lock_requested source=" + sanitizeForLog(source));
    }

    static boolean shouldStartGptAutoLock(Context context) {
        return prefs(context).getBoolean(KEY_GPT_AUTO_LOCK_REQUESTED, false)
                && !prefs(context).getBoolean(KEY_GPT_AUTO_LOCK_ACTIVE, false);
    }

    static synchronized void recordGptAutoLockStarted(Context context, String source) {
        SharedPreferences preferences = prefs(context);
        int count = preferences.getInt(KEY_GPT_AUTO_LOCK_START_COUNT, 0) + 1;
        long now = System.currentTimeMillis();
        preferences.edit()
                .putBoolean(KEY_GPT_AUTO_LOCK_REQUESTED, true)
                .putBoolean(KEY_GPT_AUTO_LOCK_ACTIVE, true)
                .putString(KEY_GPT_AUTO_LOCK_STATE, "active")
                .putLong(KEY_GPT_AUTO_LOCK_STARTED_AT, now)
                .putInt(KEY_GPT_AUTO_LOCK_START_COUNT, count)
                .putString(KEY_GPT_AUTOMATION_LOCKED_OVERLAY_MODE,
                        LOCK_MODE_GPT_AUTOMATION + "_passive_edges")
                .apply();
        appendLog(context, "gpt_auto_lock_started count=" + count
                + " source=" + sanitizeForLog(source));
    }

    static synchronized void recordGptAutoLockFailed(Context context, String reason) {
        SharedPreferences preferences = prefs(context);
        int count = preferences.getInt(KEY_GPT_AUTO_LOCK_FAIL_COUNT, 0) + 1;
        preferences.edit()
                .putString(KEY_GPT_AUTO_LOCK_STATE,
                        reason == null ? "failed" : "failed_" + reason)
                .putInt(KEY_GPT_AUTO_LOCK_FAIL_COUNT, count)
                .apply();
        appendLog(context, "gpt_auto_lock_failed count=" + count
                + " reason=" + sanitizeForLog(reason));
    }

    static synchronized void recordGptAutoLockInactive(Context context, String reason) {
        prefs(context).edit()
                .putBoolean(KEY_GPT_AUTO_LOCK_ACTIVE, false)
                .putString(KEY_GPT_AUTO_LOCK_STATE,
                        reason == null ? "inactive" : reason)
                .putString(KEY_GPT_AUTOMATION_LOCKED_OVERLAY_MODE, "inactive")
                .apply();
        appendLog(context, "gpt_auto_lock_inactive reason=" + sanitizeForLog(reason));
    }

    static synchronized void recordGptAutoLockPromoted(Context context, String reason) {
        if (!isGptAutoLockActive(context)) {
            return;
        }
        prefs(context).edit()
                .putString(KEY_GPT_AUTO_LOCK_STATE, "locked_after_complete")
                .putString(KEY_GPT_AUTOMATION_LOCKED_OVERLAY_MODE,
                        "normal_lock_after_gpt_complete")
                .apply();
        appendLog(context, "gpt_auto_lock_promoted reason=" + sanitizeForLog(reason));
    }

    static boolean isGptAutoLockRequested(Context context) {
        return prefs(context).getBoolean(KEY_GPT_AUTO_LOCK_REQUESTED, false);
    }

    static boolean isGptAutoLockActive(Context context) {
        return prefs(context).getBoolean(KEY_GPT_AUTO_LOCK_ACTIVE, false);
    }

    static String getGptAutoLockState(Context context) {
        String value = prefs(context).getString(KEY_GPT_AUTO_LOCK_STATE, null);
        return value == null || value.length() == 0 ? null : value;
    }

    static long getGptAutoLockStartedAt(Context context) {
        return prefs(context).getLong(KEY_GPT_AUTO_LOCK_STARTED_AT, 0L);
    }

    static int getGptAutoLockStartCount(Context context) {
        return prefs(context).getInt(KEY_GPT_AUTO_LOCK_START_COUNT, 0);
    }

    static int getGptAutoLockFailCount(Context context) {
        return prefs(context).getInt(KEY_GPT_AUTO_LOCK_FAIL_COUNT, 0);
    }

    static synchronized void recordGptAutomationRoot(
            Context context,
            String source,
            String packageName
    ) {
        String overlayMode = isGptAutoLockActive(context)
                ? prefs(context).getString(KEY_GPT_AUTOMATION_LOCKED_OVERLAY_MODE,
                        LOCK_MODE_GPT_AUTOMATION + "_passive_edges")
                : "none";
        prefs(context).edit()
                .putString(KEY_GPT_AUTOMATION_ROOT_SOURCE, source == null ? "" : source)
                .putString(KEY_GPT_AUTOMATION_ROOT_PACKAGE,
                        packageName == null ? "" : packageName)
                .putString(KEY_GPT_AUTOMATION_LOCKED_OVERLAY_MODE, overlayMode)
                .apply();
    }

    static String getGptAutomationRootSource(Context context) {
        String value = prefs(context).getString(KEY_GPT_AUTOMATION_ROOT_SOURCE, null);
        return value == null || value.length() == 0 ? null : value;
    }

    static String getGptAutomationRootPackage(Context context) {
        String value = prefs(context).getString(KEY_GPT_AUTOMATION_ROOT_PACKAGE, null);
        return value == null || value.length() == 0 ? null : value;
    }

    static String getGptAutomationLockedOverlayMode(Context context) {
        String value = prefs(context).getString(KEY_GPT_AUTOMATION_LOCKED_OVERLAY_MODE, null);
        return value == null || value.length() == 0 ? null : value;
    }

    static synchronized void clearGptShareNeedsNewChat(Context context, String reason) {
        prefs(context).edit()
                .putBoolean(KEY_GPT_SHARE_NEEDS_NEW_CHAT, false)
                .putString(KEY_GPT_SHARE_TARGET_FALLBACK_REASON,
                        reason == null ? "" : reason)
                .apply();
        appendLog(context, "gpt_photo_share_new_chat_ready reason=" + sanitizeForLog(reason));
    }

    static synchronized void markGptAutomationAccessibilityOff(Context context) {
        SharedPreferences preferences = prefs(context);
        int count = preferences.getInt(KEY_GPT_AUTOMATION_ACCESSIBILITY_OFF, 0) + 1;
        preferences.edit()
                .putInt(KEY_GPT_AUTOMATION_ACCESSIBILITY_OFF, count)
                .putString(KEY_GPT_SHARE_LAST_RESULT, "accessibility_off_fallback")
                .apply();
        appendLog(context, "gpt_automation_accessibility_off count=" + count);
    }

    static synchronized void markGptAutomationServiceNotAlive(Context context, String reason) {
        SharedPreferences preferences = prefs(context);
        int count = preferences.getInt(KEY_GPT_AUTOMATION_SERVICE_NOT_ALIVE, 0) + 1;
        preferences.edit()
                .putInt(KEY_GPT_AUTOMATION_SERVICE_NOT_ALIVE, count)
                .putString(KEY_GPT_AUTOMATION_PHASE, "manual_prepared")
                .putString(KEY_GPT_AUTOMATION_GATE_REASON,
                        reason == null ? "service_not_alive" : reason)
                .putString(KEY_GPT_SHARE_LAST_RESULT,
                        "manual_prepared " + sanitizeForLog(reason))
                .putString(KEY_GPT_AUTOMATION_LAST_ACTION,
                        "service_not_alive " + sanitizeForLog(reason))
                .apply();
        appendLog(context, "gpt_automation_service_not_alive count=" + count
                + " reason=" + sanitizeForLog(reason)
                + " heartbeat_age=" + getGptAccessibilityHeartbeatAgeMs(context));
    }

    static int getGptAutomationServiceNotAliveCount(Context context) {
        return prefs(context).getInt(KEY_GPT_AUTOMATION_SERVICE_NOT_ALIVE, 0);
    }

    static synchronized void recordGptAutomationAction(Context context, String action) {
        prefs(context).edit()
                .putString(KEY_GPT_AUTOMATION_LAST_ACTION, action == null ? "" : action)
                .apply();
        appendLog(context, "gpt_automation_action " + sanitizeForLog(action));
    }

    static synchronized void recordGptAutomationGate(
            Context context,
            String phase,
            String reason,
            int expectedCount,
            int evidenceCount,
            int readyStreak,
            String safeSendCandidate
    ) {
        recordGptAutomationGate(
                context,
                phase,
                reason,
                expectedCount,
                evidenceCount,
                readyStreak,
                safeSendCandidate,
                expectedCount
        );
    }

    static synchronized void recordGptAutomationGate(
            Context context,
            String phase,
            String reason,
            int expectedCount,
            int evidenceCount,
            int readyStreak,
            String safeSendCandidate,
            int requiredCount
    ) {
        prefs(context).edit()
                .putString(KEY_GPT_AUTOMATION_PHASE, phase == null ? "" : phase)
                .putString(KEY_GPT_AUTOMATION_GATE_REASON, reason == null ? "" : reason)
                .putInt(KEY_GPT_AUTOMATION_ATTACHMENT_EXPECTED, Math.max(0, expectedCount))
                .putInt(KEY_GPT_AUTOMATION_ATTACHMENT_EVIDENCE, Math.max(0, evidenceCount))
                .putInt(KEY_GPT_AUTOMATION_ATTACHMENT_STREAK, Math.max(0, readyStreak))
                .putInt(KEY_GPT_AUTOMATION_ATTACHMENT_REQUIRED, Math.max(0, requiredCount))
                .putString(KEY_GPT_AUTOMATION_SAFE_SEND_CANDIDATE,
                        safeSendCandidate == null ? "" : safeSendCandidate)
                .putString(KEY_GPT_AUTOMATION_LAST_ACTION,
                        "gate " + sanitizeForLog(phase) + " " + sanitizeForLog(reason))
                .apply();
        appendLog(context, "gpt_automation_gate phase=" + sanitizeForLog(phase)
                + " reason=" + sanitizeForLog(reason)
                + " expected=" + Math.max(0, expectedCount)
                + " evidence=" + Math.max(0, evidenceCount)
                + " streak=" + Math.max(0, readyStreak)
                + " required=" + Math.max(0, requiredCount)
                + " send=" + sanitizeForLog(safeSendCandidate));
    }

    static synchronized void recordGptAutomationSendCandidate(
            Context context,
            String source,
            String description
    ) {
        prefs(context).edit()
                .putString(KEY_GPT_AUTOMATION_SEND_CANDIDATE_SOURCE,
                        source == null ? "" : source)
                .putString(KEY_GPT_AUTOMATION_SAFE_SEND_CANDIDATE,
                        description == null ? "" : description)
                .putString(KEY_GPT_AUTOMATION_LAST_ACTION,
                        "send_candidate " + sanitizeForLog(source))
                .apply();
        appendLog(context, "gpt_automation_send_candidate source="
                + sanitizeForLog(source)
                + " candidate=" + sanitizeForLog(description));
    }

    static synchronized void recordGptAutomationArrowFallback(Context context, String source) {
        SharedPreferences preferences = prefs(context);
        int count = preferences.getInt(KEY_GPT_AUTOMATION_ARROW_FALLBACK, 0) + 1;
        preferences.edit()
                .putInt(KEY_GPT_AUTOMATION_ARROW_FALLBACK, count)
                .putString(KEY_GPT_AUTOMATION_SEND_CANDIDATE_SOURCE,
                        source == null ? "" : source)
                .apply();
        appendLog(context, "gpt_automation_arrow_fallback count=" + count
                + " source=" + sanitizeForLog(source));
    }

    static synchronized void recordGptAutomationSendClick(Context context, String source) {
        recordGptAutomationSendClick(context, source, null, false);
    }

    static synchronized void recordGptAutomationSendClick(
            Context context,
            String source,
            String clickBounds,
            boolean wasComposer
    ) {
        long now = System.currentTimeMillis();
        prefs(context).edit()
                .putLong(KEY_GPT_AUTOMATION_SEND_CLICK_AT, now)
                .putString(KEY_GPT_AUTOMATION_SEND_CANDIDATE_SOURCE,
                        source == null ? "" : source)
                .putString(KEY_GPT_AUTOMATION_LAST_CLICK_BOUNDS,
                        clickBounds == null ? "" : clickBounds)
                .putBoolean(KEY_GPT_AUTOMATION_LAST_CLICK_WAS_COMPOSER, wasComposer)
                .putString(KEY_GPT_AUTOMATION_LAST_ACTION,
                        "send_click " + sanitizeForLog(source))
                .apply();
        appendLog(context, "gpt_automation_send_click source=" + sanitizeForLog(source)
                + " at=" + now
                + " bounds=" + sanitizeForLog(clickBounds)
                + " composer=" + wasComposer);
    }

    static synchronized void markGptAutomationSendClickPending(
            Context context,
            String source,
            String clickBounds,
            boolean wasComposer
    ) {
        long now = System.currentTimeMillis();
        prefs(context).edit()
                .putString(KEY_GPT_AUTOMATION_PHASE, "send_click_pending")
                .putString(KEY_GPT_AUTOMATION_GATE_REASON, "awaiting_send_confirmation")
                .putString(KEY_GPT_AUTOMATION_SEND_CONFIRMATION_PHASE, "send_click_pending")
                .putLong(KEY_GPT_AUTOMATION_SEND_PENDING_AT, now)
                .putString(KEY_GPT_AUTOMATION_SEND_PENDING_SOURCE,
                        source == null ? "" : source)
                .putString(KEY_GPT_AUTOMATION_LAST_CLICK_BOUNDS,
                        clickBounds == null ? "" : clickBounds)
                .putBoolean(KEY_GPT_AUTOMATION_LAST_CLICK_WAS_COMPOSER, wasComposer)
                .putString(KEY_GPT_AUTOMATION_LAST_ACTION,
                        "send_click_pending " + sanitizeForLog(source))
                .apply();
        appendLog(context, "gpt_automation_send_click_pending source="
                + sanitizeForLog(source)
                + " bounds=" + sanitizeForLog(clickBounds)
                + " composer=" + wasComposer);
    }

    static boolean isGptAutomationSendClickPending(Context context) {
        return prefs(context).getLong(KEY_GPT_AUTOMATION_SEND_PENDING_AT, 0L) > 0L;
    }

    static long getGptAutomationSendPendingAt(Context context) {
        return prefs(context).getLong(KEY_GPT_AUTOMATION_SEND_PENDING_AT, 0L);
    }

    static String getGptAutomationSendPendingSource(Context context) {
        String value = prefs(context).getString(KEY_GPT_AUTOMATION_SEND_PENDING_SOURCE, null);
        return value == null || value.length() == 0 ? null : value;
    }

    static int getGptAutomationSendConfirmRetries(Context context) {
        return prefs(context).getInt(KEY_GPT_AUTOMATION_SEND_CONFIRM_RETRIES, 0);
    }

    static synchronized int incrementGptAutomationSendConfirmRetries(
            Context context,
            String reason
    ) {
        SharedPreferences preferences = prefs(context);
        int count = preferences.getInt(KEY_GPT_AUTOMATION_SEND_CONFIRM_RETRIES, 0) + 1;
        preferences.edit()
                .putInt(KEY_GPT_AUTOMATION_SEND_CONFIRM_RETRIES, count)
                .putString(KEY_GPT_AUTOMATION_SEND_CONFIRMATION_PHASE,
                        reason == null ? "retry" : reason)
                .putString(KEY_GPT_AUTOMATION_GATE_REASON,
                        reason == null ? "retry" : reason)
                .apply();
        appendLog(context, "gpt_automation_send_confirm_retry count=" + count
                + " reason=" + sanitizeForLog(reason));
        return count;
    }

    static synchronized void recordGptAutomationSendConfirmed(Context context, String reason) {
        SharedPreferences preferences = prefs(context);
        int count = preferences.getInt(KEY_GPT_AUTOMATION_SEND_CONFIRMED, 0) + 1;
        preferences.edit()
                .putInt(KEY_GPT_AUTOMATION_SEND_CONFIRMED, count)
                .putString(KEY_GPT_AUTOMATION_SEND_CONFIRMATION_PHASE, "confirmed")
                .putLong(KEY_GPT_AUTOMATION_SEND_PENDING_AT, 0L)
                .putString(KEY_GPT_AUTOMATION_SEND_PENDING_SOURCE, "")
                .putInt(KEY_GPT_AUTOMATION_SEND_CONFIRM_RETRIES, 0)
                .putString(KEY_GPT_AUTOMATION_LAST_ACTION,
                        "send_confirmed " + sanitizeForLog(reason))
                .apply();
        appendLog(context, "gpt_automation_send_confirmed count=" + count
                + " reason=" + sanitizeForLog(reason));
    }

    static synchronized void recordGptAutomationSendUnconfirmed(Context context, String reason) {
        SharedPreferences preferences = prefs(context);
        int count = preferences.getInt(KEY_GPT_AUTOMATION_SEND_UNCONFIRMED, 0) + 1;
        preferences.edit()
                .putInt(KEY_GPT_AUTOMATION_SEND_UNCONFIRMED, count)
                .putString(KEY_GPT_AUTOMATION_SEND_CONFIRMATION_PHASE,
                        reason == null ? "send_unconfirmed" : reason)
                .putString(KEY_GPT_AUTOMATION_GATE_REASON,
                        reason == null ? "send_unconfirmed" : reason)
                .putString(KEY_GPT_AUTOMATION_LAST_ACTION,
                        "send_unconfirmed " + sanitizeForLog(reason))
                .apply();
        appendLog(context, "gpt_automation_send_unconfirmed count=" + count
                + " reason=" + sanitizeForLog(reason));
    }

    static synchronized void markGptAutomationManualRequired(Context context, String reason) {
        SharedPreferences preferences = prefs(context);
        int count = preferences.getInt(KEY_GPT_AUTOMATION_MANUAL_REQUIRED, 0) + 1;
        preferences.edit()
                .putInt(KEY_GPT_AUTOMATION_MANUAL_REQUIRED, count)
                .putBoolean(KEY_GPT_SHARE_PENDING, false)
                .putBoolean(KEY_GPT_SHARE_NEEDS_NEW_CHAT, false)
                .putString(KEY_GPT_AUTOMATION_PHASE, "manual_required")
                .putString(KEY_GPT_AUTOMATION_GATE_REASON, reason == null ? "" : reason)
                .putString(KEY_GPT_AUTOMATION_SEND_CONFIRMATION_PHASE,
                        reason == null ? "manual_required" : reason)
                .putLong(KEY_GPT_AUTOMATION_SEND_PENDING_AT, 0L)
                .putString(KEY_GPT_AUTOMATION_SEND_PENDING_SOURCE, "")
                .putInt(KEY_GPT_AUTOMATION_SEND_CONFIRM_RETRIES, 0)
                .putString(KEY_GPT_SHARE_LAST_RESULT,
                        "manual_required " + sanitizeForLog(reason))
                .putString(KEY_GPT_AUTOMATION_LAST_ACTION,
                        "manual_required " + sanitizeForLog(reason))
                .apply();
        appendLog(context, "gpt_automation_manual_required count=" + count
                + " reason=" + sanitizeForLog(reason));
    }

    static synchronized void recordGptAutomationSent(Context context, String source) {
        SharedPreferences preferences = prefs(context);
        int count = preferences.getInt(KEY_GPT_AUTOMATION_SENT_COUNT, 0) + 1;
        preferences.edit()
                .putInt(KEY_GPT_AUTOMATION_SENT_COUNT, count)
                .putString(KEY_GPT_AUTOMATION_PHASE, "sent")
                .putString(KEY_GPT_AUTOMATION_GATE_REASON, "safe_send_clicked")
                .putString(KEY_GPT_SHARE_LAST_RESULT, "sent " + sanitizeForLog(source))
                .apply();
        appendLog(context, "gpt_automation_sent count=" + count
                + " source=" + sanitizeForLog(source));
    }

    static synchronized void recordGptAutomationFailure(Context context, String reason) {
        SharedPreferences preferences = prefs(context);
        int count = preferences.getInt(KEY_GPT_AUTOMATION_FAIL_COUNT, 0) + 1;
        preferences.edit()
                .putInt(KEY_GPT_AUTOMATION_FAIL_COUNT, count)
                .putString(KEY_GPT_AUTOMATION_PHASE, "failed")
                .putString(KEY_GPT_AUTOMATION_GATE_REASON, reason == null ? "" : reason)
                .putString(KEY_GPT_SHARE_LAST_RESULT, "automation_failed " + sanitizeForLog(reason))
                .apply();
        appendLog(context, "gpt_automation_failed count=" + count
                + " reason=" + sanitizeForLog(reason));
    }

    static synchronized void recordGptAutomationException(Context context, String source, Throwable error) {
        SharedPreferences preferences = prefs(context);
        int count = preferences.getInt(KEY_GPT_AUTOMATION_EXCEPTION_COUNT, 0) + 1;
        String detail = compactException(source, error);
        preferences.edit()
                .putInt(KEY_GPT_AUTOMATION_EXCEPTION_COUNT, count)
                .putString(KEY_GPT_AUTOMATION_LAST_EXCEPTION, detail)
                .putString(KEY_GPT_AUTOMATION_LAST_ACTION,
                        "exception " + sanitizeForLog(source))
                .apply();
        appendLog(context, "gpt_automation_exception count=" + count
                + " " + sanitizeForLog(detail));
    }

    static int getGptAutomationExceptionCount(Context context) {
        return prefs(context).getInt(KEY_GPT_AUTOMATION_EXCEPTION_COUNT, 0);
    }

    static String getGptAutomationLastException(Context context) {
        String value = prefs(context).getString(KEY_GPT_AUTOMATION_LAST_EXCEPTION, null);
        return value == null || value.length() == 0 ? null : value;
    }

    static synchronized void completeGptPhotoShare(Context context, String reason) {
        prefs(context).edit()
                .putBoolean(KEY_GPT_SHARE_PENDING, false)
                .putBoolean(KEY_GPT_SHARE_NEEDS_NEW_CHAT, false)
                .putString(KEY_GPT_AUTOMATION_PHASE, "complete")
                .putString(KEY_GPT_AUTOMATION_GATE_REASON, reason == null ? "" : reason)
                .putString(KEY_GPT_AUTOMATION_SEND_CONFIRMATION_PHASE, "complete")
                .putLong(KEY_GPT_AUTOMATION_SEND_PENDING_AT, 0L)
                .putString(KEY_GPT_AUTOMATION_SEND_PENDING_SOURCE, "")
                .putInt(KEY_GPT_AUTOMATION_SEND_CONFIRM_RETRIES, 0)
                .putString(KEY_GPT_SHARE_LAST_RESULT, "complete " + sanitizeForLog(reason))
                .apply();
        appendLog(context, "gpt_photo_share_complete reason=" + sanitizeForLog(reason));
    }

    static boolean isGptPhotoSharePending(Context context) {
        return prefs(context).getBoolean(KEY_GPT_SHARE_PENDING, false);
    }

    static long getGptShareSessionId(Context context) {
        return prefs(context).getLong(KEY_GPT_SHARE_SESSION_ID, 0L);
    }

    static String getGptSharePrompt(Context context) {
        return prefs(context).getString(KEY_GPT_SHARE_PROMPT, "");
    }

    static String getGptShareAllPhotosRaw(Context context) {
        return prefs(context).getString(KEY_GPT_SHARE_ALL_PHOTOS, "");
    }

    static int getGptShareChunkIndex(Context context) {
        return prefs(context).getInt(KEY_GPT_SHARE_CHUNK_INDEX, 0);
    }

    static int getGptShareChunkCount(Context context) {
        return prefs(context).getInt(KEY_GPT_SHARE_CHUNK_COUNT, 0);
    }

    static int getGptShareChunkSize(Context context) {
        return prefs(context).getInt(KEY_GPT_SHARE_CHUNK_SIZE, 0);
    }

    static boolean doesGptShareNeedNewChat(Context context) {
        return prefs(context).getBoolean(KEY_GPT_SHARE_NEEDS_NEW_CHAT, false);
    }

    static String getGptShareTargetMode(Context context) {
        String value = prefs(context).getString(KEY_GPT_SHARE_TARGET_MODE, null);
        return value == null || value.length() == 0 ? null : value;
    }

    static String getGptShareTargetFallbackReason(Context context) {
        String value = prefs(context).getString(KEY_GPT_SHARE_TARGET_FALLBACK_REASON, null);
        return value == null || value.length() == 0 ? null : value;
    }

    static String getGptShareLastResult(Context context) {
        String value = prefs(context).getString(KEY_GPT_SHARE_LAST_RESULT, null);
        return value == null || value.length() == 0 ? null : value;
    }

    static int getGptShareNoPhotosCount(Context context) {
        return prefs(context).getInt(KEY_GPT_SHARE_NO_PHOTOS, 0);
    }

    static int getGptShareLaunchCount(Context context) {
        return prefs(context).getInt(KEY_GPT_SHARE_LAUNCH_COUNT, 0);
    }

    static int getGptAutomationSentCount(Context context) {
        return prefs(context).getInt(KEY_GPT_AUTOMATION_SENT_COUNT, 0);
    }

    static int getGptAutomationFailCount(Context context) {
        return prefs(context).getInt(KEY_GPT_AUTOMATION_FAIL_COUNT, 0);
    }

    static String getGptAutomationLastAction(Context context) {
        String value = prefs(context).getString(KEY_GPT_AUTOMATION_LAST_ACTION, null);
        return value == null || value.length() == 0 ? null : value;
    }

    static int getGptAutomationAccessibilityOffCount(Context context) {
        return prefs(context).getInt(KEY_GPT_AUTOMATION_ACCESSIBILITY_OFF, 0);
    }

    static String getGptAutomationPhase(Context context) {
        String value = prefs(context).getString(KEY_GPT_AUTOMATION_PHASE, null);
        return value == null || value.length() == 0 ? null : value;
    }

    static String getGptAutomationGateReason(Context context) {
        String value = prefs(context).getString(KEY_GPT_AUTOMATION_GATE_REASON, null);
        return value == null || value.length() == 0 ? null : value;
    }

    static int getGptAutomationAttachmentExpectedCount(Context context) {
        return prefs(context).getInt(KEY_GPT_AUTOMATION_ATTACHMENT_EXPECTED, 0);
    }

    static int getGptAutomationAttachmentEvidenceCount(Context context) {
        return prefs(context).getInt(KEY_GPT_AUTOMATION_ATTACHMENT_EVIDENCE, 0);
    }

    static int getGptAutomationAttachmentReadyStreak(Context context) {
        return prefs(context).getInt(KEY_GPT_AUTOMATION_ATTACHMENT_STREAK, 0);
    }

    static int getGptAutomationAttachmentRequiredCount(Context context) {
        return prefs(context).getInt(KEY_GPT_AUTOMATION_ATTACHMENT_REQUIRED, 0);
    }

    static long getGptAutomationChunkLaunchedAt(Context context) {
        return prefs(context).getLong(KEY_GPT_AUTOMATION_CHUNK_LAUNCHED_AT, 0L);
    }

    static String getGptAutomationSafeSendCandidate(Context context) {
        String value = prefs(context).getString(KEY_GPT_AUTOMATION_SAFE_SEND_CANDIDATE, null);
        return value == null || value.length() == 0 ? null : value;
    }

    static int getGptAutomationManualRequiredCount(Context context) {
        return prefs(context).getInt(KEY_GPT_AUTOMATION_MANUAL_REQUIRED, 0);
    }

    static String getGptAutomationSendCandidateSource(Context context) {
        String value = prefs(context).getString(KEY_GPT_AUTOMATION_SEND_CANDIDATE_SOURCE, null);
        return value == null || value.length() == 0 ? null : value;
    }

    static int getGptAutomationArrowFallbackCount(Context context) {
        return prefs(context).getInt(KEY_GPT_AUTOMATION_ARROW_FALLBACK, 0);
    }

    static long getGptAutomationSendClickAt(Context context) {
        return prefs(context).getLong(KEY_GPT_AUTOMATION_SEND_CLICK_AT, 0L);
    }

    static String getGptAutomationSendConfirmationPhase(Context context) {
        String value = prefs(context).getString(
                KEY_GPT_AUTOMATION_SEND_CONFIRMATION_PHASE,
                null
        );
        return value == null || value.length() == 0 ? null : value;
    }

    static int getGptAutomationSendConfirmedCount(Context context) {
        return prefs(context).getInt(KEY_GPT_AUTOMATION_SEND_CONFIRMED, 0);
    }

    static int getGptAutomationSendUnconfirmedCount(Context context) {
        return prefs(context).getInt(KEY_GPT_AUTOMATION_SEND_UNCONFIRMED, 0);
    }

    static String getGptAutomationLastClickBounds(Context context) {
        String value = prefs(context).getString(KEY_GPT_AUTOMATION_LAST_CLICK_BOUNDS, null);
        return value == null || value.length() == 0 ? null : value;
    }

    static boolean wasGptAutomationLastClickComposer(Context context) {
        return prefs(context).getBoolean(KEY_GPT_AUTOMATION_LAST_CLICK_WAS_COMPOSER, false);
    }

    static synchronized void markGptAutomationAttachmentsReady(Context context, String reason) {
        SharedPreferences preferences = prefs(context);
        long existing = preferences.getLong(KEY_GPT_AUTOMATION_ATTACHMENTS_READY_AT, 0L);
        if (existing > 0L) {
            return;
        }
        long now = System.currentTimeMillis();
        preferences.edit()
                .putLong(KEY_GPT_AUTOMATION_ATTACHMENTS_READY_AT, now)
                .putString(KEY_GPT_AUTOMATION_LAST_ACTION,
                        "attachments_ready " + sanitizeForLog(reason))
                .apply();
        appendLog(context, "gpt_automation_attachments_ready at=" + now
                + " reason=" + sanitizeForLog(reason));
    }

    static long getGptAutomationAttachmentsReadyAt(Context context) {
        return prefs(context).getLong(KEY_GPT_AUTOMATION_ATTACHMENTS_READY_AT, 0L);
    }

    static synchronized void markGptAutomationPromptReady(Context context) {
        SharedPreferences preferences = prefs(context);
        long existing = preferences.getLong(KEY_GPT_AUTOMATION_PROMPT_READY_AT, 0L);
        if (existing > 0L) {
            return;
        }
        long now = System.currentTimeMillis();
        preferences.edit()
                .putLong(KEY_GPT_AUTOMATION_PROMPT_READY_AT, now)
                .putString(KEY_GPT_AUTOMATION_LAST_ACTION, "prompt_ready")
                .apply();
        appendLog(context, "gpt_automation_prompt_ready at=" + now);
    }

    static long getGptAutomationPromptReadyAt(Context context) {
        return prefs(context).getLong(KEY_GPT_AUTOMATION_PROMPT_READY_AT, 0L);
    }

    static boolean hasGptAutomationSendGestureAttempted(Context context) {
        return prefs(context).getLong(KEY_GPT_AUTOMATION_SEND_GESTURE_ATTEMPTED_AT, 0L) > 0L;
    }

    static synchronized void markGptAutomationSendGestureAttempted(Context context) {
        prefs(context).edit()
                .putLong(KEY_GPT_AUTOMATION_SEND_GESTURE_ATTEMPTED_AT, System.currentTimeMillis())
                .apply();
        appendLog(context, "gpt_automation_send_gesture_once");
    }

    static long getGptAutomationSendGestureAttemptedAt(Context context) {
        return prefs(context).getLong(KEY_GPT_AUTOMATION_SEND_GESTURE_ATTEMPTED_AT, 0L);
    }

    static int getGptShareReattachAttemptCount(Context context) {
        return prefs(context).getInt(KEY_GPT_SHARE_REATTACH_ATTEMPT_COUNT, 0);
    }

    static synchronized void markGptShareStateLost(Context context, String reason) {
        SharedPreferences preferences = prefs(context);
        int count = preferences.getInt(KEY_GPT_SHARE_STATE_LOST_COUNT, 0) + 1;
        preferences.edit()
                .putInt(KEY_GPT_SHARE_STATE_LOST_COUNT, count)
                .putString(KEY_GPT_AUTOMATION_PHASE, "manual_required")
                .putString(KEY_GPT_AUTOMATION_GATE_REASON,
                        reason == null ? "gpt_share_state_lost" : reason)
                .putString(KEY_GPT_SHARE_LAST_RESULT,
                        "state_lost " + sanitizeForLog(reason))
                .putString(KEY_GPT_AUTOMATION_LAST_ACTION,
                        "state_lost " + sanitizeForLog(reason))
                .putBoolean(KEY_GPT_SHARE_PENDING, false)
                .commit();
        appendLog(context, "gpt_share_state_lost count=" + count
                + " reason=" + sanitizeForLog(reason));
    }

    static int getGptShareStateLostCount(Context context) {
        return prefs(context).getInt(KEY_GPT_SHARE_STATE_LOST_COUNT, 0);
    }

    static synchronized boolean tryRecordGptShareReattach(
            Context context,
            int chunkIndex,
            String reason
    ) {
        SharedPreferences preferences = prefs(context);
        if (preferences.getInt(KEY_GPT_SHARE_REATTACH_CHUNK_INDEX, -1) == chunkIndex) {
            return false;
        }
        int count = preferences.getInt(KEY_GPT_SHARE_REATTACH_ATTEMPT_COUNT, 0) + 1;
        preferences.edit()
                .putInt(KEY_GPT_SHARE_REATTACH_ATTEMPT_COUNT, count)
                .putInt(KEY_GPT_SHARE_REATTACH_CHUNK_INDEX, chunkIndex)
                .putString(KEY_GPT_AUTOMATION_PHASE, "waiting_share_attach")
                .putString(KEY_GPT_AUTOMATION_GATE_REASON,
                        reason == null ? "share_chunk_reattach" : reason)
                .putString(KEY_GPT_SHARE_LAST_RESULT,
                        "reattach_chunk " + chunkIndex + " " + sanitizeForLog(reason))
                .apply();
        appendLog(context, "share_chunk_reattach count=" + count
                + " index=" + chunkIndex
                + " reason=" + sanitizeForLog(reason));
        return true;
    }

    static String getModeRearmReason(Context context) {
        String value = prefs(context).getString(KEY_MODE_REARM_REASON, null);
        return value == null || value.length() == 0 ? null : value;
    }

    static boolean isModeUserDesiredEnabled(Context context) {
        SharedPreferences preferences = prefs(context);
        if (preferences.contains(KEY_MODE_USER_DESIRED)) {
            return preferences.getBoolean(KEY_MODE_USER_DESIRED, false);
        }
        return preferences.getBoolean(KEY_HOME_TRAP_ENABLED, false);
    }

    static long getModeEnabledAt(Context context) {
        return prefs(context).getLong(KEY_MODE_ENABLED_AT, 0L);
    }

    static String getModeLastDisabledReason(Context context) {
        String value = prefs(context).getString(KEY_MODE_LAST_DISABLED_REASON, null);
        return value == null || value.length() == 0 ? null : value;
    }

    static String getModeDisableSource(Context context) {
        String value = prefs(context).getString(KEY_MODE_DISABLE_SOURCE, null);
        return value == null || value.length() == 0 ? null : value;
    }

    static int getModeRepairCount(Context context) {
        return prefs(context).getInt(KEY_MODE_REPAIR_COUNT, 0);
    }

    static int getModeOverlayRecoveryCount(Context context) {
        return prefs(context).getInt(KEY_MODE_OVERLAY_RECOVERY_COUNT, 0);
    }

    static int getTouchServiceNullIntentCount(Context context) {
        return prefs(context).getInt(KEY_TOUCH_SERVICE_NULL_INTENT_COUNT, 0);
    }

    static synchronized void setModeUserDesiredEnabled(
            Context context,
            boolean enabled,
            String reason
    ) {
        SharedPreferences.Editor editor = prefs(context).edit()
                .putBoolean(KEY_MODE_USER_DESIRED, enabled);
        if (enabled) {
            long now = System.currentTimeMillis();
            editor.putLong(KEY_MODE_ENABLED_AT, now)
                    .remove(KEY_MODE_LAST_DISABLED_REASON)
                    .remove(KEY_MODE_REARM_REASON);
        } else {
            editor.remove(KEY_MODE_ENABLED_AT)
                    .putString(KEY_MODE_LAST_DISABLED_REASON,
                            reason == null ? "" : reason)
                    .putString(KEY_MODE_DISABLE_SOURCE,
                            reason == null ? "" : reason);
        }
        editor.commit();
        appendLog(context, "mode_user_desired " + (enabled ? "ON" : "OFF")
                + " reason=" + sanitizeForLog(reason));
    }

    static synchronized void recordModeOverlayRecovery(Context context, String reason) {
        SharedPreferences preferences = prefs(context);
        int count = preferences.getInt(KEY_MODE_OVERLAY_RECOVERY_COUNT, 0) + 1;
        preferences.edit()
                .putInt(KEY_MODE_OVERLAY_RECOVERY_COUNT, count)
                .apply();
        appendLog(context, "mode_overlay_recovery count=" + count
                + " reason=" + sanitizeForLog(reason));
    }

    static synchronized void recordTouchServiceNullIntent(Context context) {
        SharedPreferences preferences = prefs(context);
        int count = preferences.getInt(KEY_TOUCH_SERVICE_NULL_INTENT_COUNT, 0) + 1;
        preferences.edit()
                .putInt(KEY_TOUCH_SERVICE_NULL_INTENT_COUNT, count)
                .apply();
        appendLog(context, "touch_service_null_intent_count count=" + count);
    }

    static synchronized void recordModeRequiresManualRearm(Context context, String reason) {
        prefs(context).edit()
                .putString(KEY_MODE_REARM_REASON,
                        reason == null ? "mode_requires_manual_rearm" : reason)
                .commit();
        appendLog(context, "mode_requires_manual_rearm reason=" + sanitizeForLog(reason));
    }

    static synchronized void recordBootReceiver(Context context, String action, String result) {
        prefs(context).edit()
                .putString(KEY_BOOT_RECEIVER_LAST_ACTION, action == null ? "" : action)
                .putString(KEY_BOOT_RECEIVER_LAST_RESULT, result == null ? "" : result)
                .commit();
        appendLog(context, "boot_receiver action=" + sanitizeForLog(action)
                + " result=" + sanitizeForLog(result));
    }

    static String getBootReceiverLastAction(Context context) {
        String value = prefs(context).getString(KEY_BOOT_RECEIVER_LAST_ACTION, null);
        return value == null || value.length() == 0 ? null : value;
    }

    static String getBootReceiverLastResult(Context context) {
        String value = prefs(context).getString(KEY_BOOT_RECEIVER_LAST_RESULT, null);
        return value == null || value.length() == 0 ? null : value;
    }

    static synchronized boolean repairModeIfDesired(Context context, String reason) {
        if (!isModeUserDesiredEnabled(context)) {
            return false;
        }
        boolean homeTrap = isHomeTrapEnabled(context);
        boolean stayAwake = isStayAwakeEnabled(context);
        if (homeTrap && stayAwake) {
            return false;
        }
        SharedPreferences preferences = prefs(context);
        int count = preferences.getInt(KEY_MODE_REPAIR_COUNT, 0) + 1;
        preferences.edit()
                .putInt(KEY_MODE_REPAIR_COUNT, count)
                .putBoolean(KEY_MODE_USER_DESIRED, true)
                .remove(KEY_MODE_LAST_DISABLED_REASON)
                .remove(KEY_MODE_REARM_REASON)
                .commit();
        if (!homeTrap) {
            resetHomeTrapRuntime(context, "mode_repair " + reason);
            clearHomeTrapLegacyTargets(context);
            setHomeTrapEnabled(context, true);
            markHomeTrapArmed(context);
        }
        setStayAwakeEnabled(context, true);
        enableIndefiniteScreenTimeout(context, "mode_repair " + reason);
        StayAwakeService.start(context);
        GuardKeeperService.start(context, "mode_repair");
        SafeImeGateService.start(context);
        TouchBlockerService.unlock(context);
        appendLog(context, "mode_repaired_after_state_loss count=" + count
                + " reason=" + sanitizeForLog(reason)
                + " home_trap_was=" + homeTrap
                + " stay_awake_was=" + stayAwake);
        return true;
    }

    static synchronized void recordWatchCameraStorageFallback(Context context, String reason) {
        SharedPreferences preferences = prefs(context);
        int count = preferences.getInt(KEY_WATCH_CAMERA_PUBLIC_FALLBACKS, 0) + 1;
        preferences.edit()
                .putInt(KEY_WATCH_CAMERA_PUBLIC_FALLBACKS, count)
                .putString(KEY_WATCH_CAMERA_STORAGE_BACKEND, "app_private_fallback")
                .apply();
        appendLog(context, "watch_camera_storage_fallback count=" + count
                + " reason=" + reason);
    }

    static synchronized void recordWatchCameraError(Context context, String message) {
        String safeMessage = message == null ? "unknown" : message.replace('\n', ' ').replace('\r', ' ');
        if (safeMessage.length() > 220) {
            safeMessage = safeMessage.substring(0, 220);
        }
        prefs(context).edit()
                .putString(KEY_WATCH_CAMERA_LAST_ERROR, safeMessage)
                .putString(KEY_WATCH_CAMERA_BACKEND, "camera2")
                .apply();
        appendLog(context, "watch_camera_error " + safeMessage);
        appendLockModeResult(context, "watchguard_camera", "error", safeMessage);
    }

    static long getWatchCameraLastShotAt(Context context) {
        return prefs(context).getLong(KEY_WATCH_CAMERA_LAST_SHOT_AT, 0L);
    }

    static int getWatchCameraPhotoCount(Context context) {
        return prefs(context).getInt(KEY_WATCH_CAMERA_PHOTO_COUNT, 0);
    }

    static int getWatchCameraThrottledCount(Context context) {
        return prefs(context).getInt(KEY_WATCH_CAMERA_THROTTLED, 0);
    }

    static String getWatchCameraLastFile(Context context) {
        String value = prefs(context).getString(KEY_WATCH_CAMERA_LAST_FILE, null);
        return value == null || value.length() == 0 ? null : value;
    }

    static String getWatchCameraLastError(Context context) {
        String value = prefs(context).getString(KEY_WATCH_CAMERA_LAST_ERROR, null);
        return value == null || value.length() == 0 ? null : value;
    }

    static String getWatchCameraBackend(Context context) {
        String value = prefs(context).getString(KEY_WATCH_CAMERA_BACKEND, null);
        return value == null || value.length() == 0 ? null : value;
    }

    static String getWatchCameraStorageBackend(Context context) {
        String value = prefs(context).getString(KEY_WATCH_CAMERA_STORAGE_BACKEND, null);
        return value == null || value.length() == 0 ? null : value;
    }

    static int getWatchCameraPublicFallbackCount(Context context) {
        return prefs(context).getInt(KEY_WATCH_CAMERA_PUBLIC_FALLBACKS, 0);
    }

    static void recordSafeImeAutofocusHideSelf(Context context, String fieldKey, long delayMs) {
        SharedPreferences preferences = prefs(context);
        int count = preferences.getInt(KEY_SAFE_IME_HIDDEN, 0) + 1;
        preferences.edit()
                .putInt(KEY_SAFE_IME_HIDDEN, count)
                .putString(KEY_SAFE_IME_LAST_HIDE_REASON, "autofocus")
                .putString(KEY_SAFE_IME_LAST_FIELD, fieldKey == null ? "" : fieldKey)
                .putLong(KEY_SAFE_IME_LAST_AUTO_HIDE_AT, System.currentTimeMillis())
                .apply();
        appendLog(context, "safe_ime_autofocus_hide_self count=" + count
                + " delay=" + delayMs
                + " field=" + sanitizeForLog(fieldKey));
    }

    static void recordSafeImeAutofocusHiddenStrict(
            Context context,
            String fieldKey,
            String reason,
            long delayMs
    ) {
        SharedPreferences preferences = prefs(context);
        int hidden = preferences.getInt(KEY_SAFE_IME_HIDDEN, 0) + 1;
        int strict = preferences.getInt(KEY_SAFE_IME_STRICT_HIDDEN, 0) + 1;
        preferences.edit()
                .putInt(KEY_SAFE_IME_HIDDEN, hidden)
                .putInt(KEY_SAFE_IME_STRICT_HIDDEN, strict)
                .putString(KEY_SAFE_IME_LAST_HIDE_REASON, reason == null ? "strict" : reason)
                .putString(KEY_SAFE_IME_LAST_FIELD, fieldKey == null ? "" : fieldKey)
                .putLong(KEY_SAFE_IME_LAST_AUTO_HIDE_AT, System.currentTimeMillis())
                .apply();
        appendLog(context, "safe_ime_autofocus_hidden_strict count=" + strict
                + " delay=" + delayMs
                + " reason=" + sanitizeForLog(reason)
                + " field=" + sanitizeForLog(fieldKey));
    }

    static void recordSafeImeAutoRepeatHidden(
            Context context,
            String fieldKey,
            long hiddenAgeMs,
            long requiredQuietMs
    ) {
        SharedPreferences preferences = prefs(context);
        int count = preferences.getInt(KEY_SAFE_IME_AUTO_REPEAT_HIDDEN, 0) + 1;
        preferences.edit()
                .putInt(KEY_SAFE_IME_AUTO_REPEAT_HIDDEN, count)
                .putString(KEY_SAFE_IME_LAST_HIDE_REASON, "auto_repeat")
                .putString(KEY_SAFE_IME_LAST_FIELD, fieldKey == null ? "" : fieldKey)
                .putLong(KEY_SAFE_IME_LAST_AUTO_HIDE_AT, System.currentTimeMillis())
                .apply();
        appendLog(context, "safe_ime_auto_repeat_hidden count=" + count
                + " age=" + hiddenAgeMs
                + " required=" + requiredQuietMs
                + " field=" + sanitizeForLog(fieldKey));
    }

    static void recordSafeImeViewClicked(
            Context context,
            String fieldKey,
            boolean focusChanged,
            long windowMs
    ) {
        SharedPreferences preferences = prefs(context);
        int count = preferences.getInt(KEY_SAFE_IME_VIEW_CLICK, 0) + 1;
        preferences.edit()
                .putInt(KEY_SAFE_IME_VIEW_CLICK, count)
                .putString(KEY_SAFE_IME_LAST_FIELD, fieldKey == null ? "" : fieldKey)
                .putLong(KEY_SAFE_IME_LAST_VIEW_CLICK_AT, System.currentTimeMillis())
                .apply();
        appendLog(context, "safe_ime_view_clicked count=" + count
                + " focus_changed=" + focusChanged
                + " window=" + windowMs
                + " field=" + sanitizeForLog(fieldKey));
    }

    static void recordSafeImeViewClickedIgnored(
            Context context,
            String fieldKey,
            boolean focusChanged,
            String reason
    ) {
        SharedPreferences preferences = prefs(context);
        int count = preferences.getInt(KEY_SAFE_IME_VIEW_CLICK_IGNORED, 0) + 1;
        preferences.edit()
                .putInt(KEY_SAFE_IME_VIEW_CLICK_IGNORED, count)
                .putString(KEY_SAFE_IME_LAST_FIELD, fieldKey == null ? "" : fieldKey)
                .putString(KEY_SAFE_IME_LAST_CALLBACK, "view_clicked_ignored")
                .putString(KEY_SAFE_IME_LAST_CALLBACK_DETAIL,
                        "focus=" + focusChanged + " reason=" + sanitizeForLog(reason))
                .apply();
        appendLog(context, "safe_ime_view_clicked_ignored count=" + count
                + " focus_changed=" + focusChanged
                + " reason=" + sanitizeForLog(reason)
                + " field=" + sanitizeForLog(fieldKey));
    }

    static void recordSafeImeManualClickShow(Context context, String fieldKey, String source) {
        SharedPreferences preferences = prefs(context);
        int shown = preferences.getInt(KEY_SAFE_IME_SHOWN, 0) + 1;
        int clickShown = preferences.getInt(KEY_SAFE_IME_MANUAL_CLICK_SHOW, 0) + 1;
        preferences.edit()
                .putInt(KEY_SAFE_IME_SHOWN, shown)
                .putInt(KEY_SAFE_IME_MANUAL_CLICK_SHOW, clickShown)
                .putString(KEY_SAFE_IME_LAST_FIELD, fieldKey == null ? "" : fieldKey)
                .apply();
        appendLog(context, "safe_ime_manual_click_show count=" + clickShown
                + " source=" + sanitizeForLog(source)
                + " field=" + sanitizeForLog(fieldKey));
    }

    static void recordSafeImeExplicitShow(
            Context context,
            String fieldKey,
            int flags,
            String source
    ) {
        SharedPreferences preferences = prefs(context);
        int shown = preferences.getInt(KEY_SAFE_IME_SHOWN, 0) + 1;
        int explicit = preferences.getInt(KEY_SAFE_IME_EXPLICIT_SHOW, 0) + 1;
        preferences.edit()
                .putInt(KEY_SAFE_IME_SHOWN, shown)
                .putInt(KEY_SAFE_IME_EXPLICIT_SHOW, explicit)
                .putString(KEY_SAFE_IME_LAST_FIELD, fieldKey == null ? "" : fieldKey)
                .putString(KEY_SAFE_IME_LAST_CALLBACK, "explicit_show")
                .putString(KEY_SAFE_IME_LAST_CALLBACK_DETAIL,
                        "flags=" + flags + " source=" + sanitizeForLog(source))
                .apply();
        appendLog(context, "safe_ime_explicit_show count=" + explicit
                + " flags=" + flags
                + " source=" + sanitizeForLog(source)
                + " field=" + sanitizeForLog(fieldKey));
    }

    static void recordSafeImeExplicitSuppressedNavigation(
            Context context,
            String fieldKey,
            int flags,
            String source
    ) {
        SharedPreferences preferences = prefs(context);
        int count = preferences.getInt(KEY_SAFE_IME_EXPLICIT_SUPPRESSED_NAV, 0) + 1;
        preferences.edit()
                .putInt(KEY_SAFE_IME_EXPLICIT_SUPPRESSED_NAV, count)
                .putString(KEY_SAFE_IME_LAST_FIELD, fieldKey == null ? "" : fieldKey)
                .putString(KEY_SAFE_IME_LAST_CALLBACK, "explicit_suppressed_navigation")
                .putString(KEY_SAFE_IME_LAST_CALLBACK_DETAIL,
                        "flags=" + flags + " source=" + sanitizeForLog(source))
                .apply();
        appendLog(context, "safe_ime_navigation_suppressed count=" + count
                + " flags=" + flags
                + " source=" + sanitizeForLog(source)
                + " field=" + sanitizeForLog(fieldKey));
    }

    static void recordSafeImeExplicitSuppressedChurn(
            Context context,
            String fieldKey,
            int flags,
            String reason
    ) {
        SharedPreferences preferences = prefs(context);
        int count = preferences.getInt(KEY_SAFE_IME_EXPLICIT_SUPPRESSED_CHURN, 0) + 1;
        preferences.edit()
                .putInt(KEY_SAFE_IME_EXPLICIT_SUPPRESSED_CHURN, count)
                .putString(KEY_SAFE_IME_LAST_FIELD, fieldKey == null ? "" : fieldKey)
                .putString(KEY_SAFE_IME_LAST_CALLBACK, "explicit_suppressed_churn")
                .putString(KEY_SAFE_IME_LAST_CALLBACK_DETAIL,
                        "flags=" + flags + " reason=" + sanitizeForLog(reason))
                .apply();
        appendLog(context, "safe_ime_explicit_suppressed_churn count=" + count
                + " flags=" + flags
                + " reason=" + sanitizeForLog(reason)
                + " field=" + sanitizeForLog(fieldKey));
        if (reason != null && reason.contains("navigation_window")) {
            appendLog(context, "safe_ime_manual_blocked_navigation_window"
                    + " field=" + sanitizeForLog(fieldKey)
                    + " reason=" + sanitizeForLog(reason));
        }
    }

    static void setSafeImeKeyboardVisible(Context context, boolean visible, String reason) {
        SharedPreferences preferences = prefs(context);
        boolean previous = preferences.getBoolean(KEY_SAFE_IME_GATE_KEYBOARD_VISIBLE, false);
        preferences.edit()
                .putBoolean(KEY_SAFE_IME_GATE_KEYBOARD_VISIBLE, visible)
                .apply();
        if (previous != visible) {
            appendLog(context, "safe_ime_keyboard_visible value=" + visible
                    + " reason=" + sanitizeForLog(reason));
        }
    }

    static boolean isSafeImeKeyboardVisible(Context context) {
        return prefs(context).getBoolean(KEY_SAFE_IME_GATE_KEYBOARD_VISIBLE, false);
    }

    static boolean isStrictSafeImePackage(String packageName) {
        return "com.openai.chatgpt".equals(packageName)
                || "com.whatsapp".equals(packageName)
                || "com.whatsapp.w4b".equals(packageName)
                || "com.anthropic.claude".equals(packageName);
    }

    static synchronized void recordSafeImeLastStrictInput(
            Context context,
            String packageName,
            String fieldKey,
            int inputType
    ) {
        if (!isStrictSafeImePackage(packageName)) {
            return;
        }
        long now = System.currentTimeMillis();
        prefs(context).edit()
                .putString(KEY_SAFE_IME_LAST_STRICT_INPUT_PACKAGE,
                        packageName == null ? "" : packageName)
                .putString(KEY_SAFE_IME_LAST_STRICT_INPUT_FIELD,
                        fieldKey == null ? "" : fieldKey)
                .putInt(KEY_SAFE_IME_LAST_STRICT_INPUT_TYPE, inputType)
                .putLong(KEY_SAFE_IME_LAST_STRICT_INPUT_AT, now)
                .apply();
        appendLog(context, "safe_ime_last_strict_input"
                + " package=" + sanitizeForLog(packageName)
                + " field=" + sanitizeForLog(fieldKey)
                + " input_type=" + inputType
                + " at=" + now);
    }

    static String getSafeImeLastStrictInputPackage(Context context) {
        String value = prefs(context).getString(KEY_SAFE_IME_LAST_STRICT_INPUT_PACKAGE, null);
        return value == null || value.length() == 0 ? null : value;
    }

    static String getSafeImeLastStrictInputField(Context context) {
        String value = prefs(context).getString(KEY_SAFE_IME_LAST_STRICT_INPUT_FIELD, null);
        return value == null || value.length() == 0 ? null : value;
    }

    static int getSafeImeLastStrictInputType(Context context) {
        return prefs(context).getInt(KEY_SAFE_IME_LAST_STRICT_INPUT_TYPE, 0);
    }

    static long getSafeImeLastStrictInputAt(Context context) {
        return prefs(context).getLong(KEY_SAFE_IME_LAST_STRICT_INPUT_AT, 0L);
    }

    static long getSafeImeLastStrictInputAgeMs(Context context) {
        long at = getSafeImeLastStrictInputAt(context);
        if (at <= 0L) {
            return -1L;
        }
        return Math.max(0L, System.currentTimeMillis() - at);
    }

    static synchronized void setSafeImeGateActive(
            Context context,
            boolean active,
            String packageName,
            String zone,
            String reason
    ) {
        SharedPreferences.Editor editor = prefs(context).edit()
                .putBoolean(KEY_SAFE_IME_GATE_ACTIVE, active)
                .putString(KEY_SAFE_IME_POLICY_MODE, active ? "strict_gate" : "inactive");
        if (active) {
            editor.putString(KEY_SAFE_IME_GATE_PACKAGE, packageName == null ? "" : packageName)
                    .putString(KEY_SAFE_IME_GATE_ZONE, zone == null ? "" : zone)
                    .putString(KEY_SAFE_IME_GATE_BLOCK_REASON, "")
                    .putString(KEY_SAFE_IME_GATE_OFF_REASON, "")
                    .putString(KEY_SAFE_IME_GATE_ZONE_REVISION, "v5_visible_app_buttons")
                    .putLong(KEY_SAFE_IME_GATE_LAST_VISIBLE_AT, System.currentTimeMillis())
                    .putBoolean(KEY_SAFE_IME_GATE_ATTACHED, true);
        } else {
            editor.remove(KEY_SAFE_IME_GATE_PACKAGE)
                    .remove(KEY_SAFE_IME_GATE_ZONE)
                    .putBoolean(KEY_SAFE_IME_GATE_ATTACHED, false)
                    .putString(KEY_SAFE_IME_GATE_BLOCK_REASON, reason == null ? "" : reason)
                    .putString(KEY_SAFE_IME_GATE_OFF_REASON, reason == null ? "" : reason);
        }
        editor.apply();
        appendLog(context, (active ? "safe_ime_gate_started" : "safe_ime_gate_stopped")
                + " package=" + sanitizeForLog(packageName)
                + " zone=" + sanitizeForLog(zone)
                + " reason=" + sanitizeForLog(reason));
    }

    static boolean isSafeImeGateActive(Context context) {
        return prefs(context).getBoolean(KEY_SAFE_IME_GATE_ACTIVE, false);
    }

    static String getSafeImeGatePackage(Context context) {
        String value = prefs(context).getString(KEY_SAFE_IME_GATE_PACKAGE, null);
        return value == null || value.length() == 0 ? null : value;
    }

    static String getSafeImeGateZone(Context context) {
        String value = prefs(context).getString(KEY_SAFE_IME_GATE_ZONE, null);
        return value == null || value.length() == 0 ? null : value;
    }

    static synchronized void recordSafeImeGateBlocked(Context context, String reason) {
        SharedPreferences preferences = prefs(context);
        String normalized = reason == null ? "" : reason;
        String previous = preferences.getString(KEY_SAFE_IME_GATE_BLOCK_REASON, "");
        preferences.edit()
                .putString(KEY_SAFE_IME_GATE_BLOCK_REASON, normalized)
                .putString(KEY_SAFE_IME_GATE_OFF_REASON, normalized)
                .apply();
        if (!normalized.equals(previous)) {
            appendLog(context, "safe_ime_gate_blocked reason=" + sanitizeForLog(normalized));
        }
    }

    static String getSafeImeGateBlockReason(Context context) {
        String value = prefs(context).getString(KEY_SAFE_IME_GATE_BLOCK_REASON, null);
        return value == null || value.length() == 0 ? null : value;
    }

    static String getSafeImeGateOffReason(Context context) {
        String value = prefs(context).getString(KEY_SAFE_IME_GATE_OFF_REASON, null);
        return value == null || value.length() == 0 ? null : value;
    }

    static synchronized void recordSafeImeGateCandidateSource(Context context, String source) {
        String normalized = source == null ? "" : source;
        SharedPreferences preferences = prefs(context);
        String previous = preferences.getString(KEY_SAFE_IME_GATE_CANDIDATE_SOURCE, "");
        preferences.edit()
                .putString(KEY_SAFE_IME_GATE_CANDIDATE_SOURCE, normalized)
                .apply();
        if (!normalized.equals(previous)) {
            appendLog(context, "safe_ime_gate_candidate_source "
                    + sanitizeForLog(normalized));
        }
    }

    static String getSafeImeGateCandidateSource(Context context) {
        String value = prefs(context).getString(KEY_SAFE_IME_GATE_CANDIDATE_SOURCE, null);
        return value == null || value.length() == 0 ? null : value;
    }

    static long getSafeImeGateLastVisibleAt(Context context) {
        return prefs(context).getLong(KEY_SAFE_IME_GATE_LAST_VISIBLE_AT, 0L);
    }

    static String getSafeImeGateZoneRevision(Context context) {
        String value = prefs(context).getString(KEY_SAFE_IME_GATE_ZONE_REVISION, null);
        return value == null || value.length() == 0 ? null : value;
    }

    static synchronized void recordSafeImeGateLayout(
            Context context,
            String layout,
            boolean attached
    ) {
        prefs(context).edit()
                .putString(KEY_SAFE_IME_GATE_LAST_LAYOUT, layout == null ? "" : layout)
                .putBoolean(KEY_SAFE_IME_GATE_ATTACHED, attached)
                .putString(KEY_SAFE_IME_GATE_ZONE_REVISION, "v5_visible_app_buttons")
                .apply();
    }

    static synchronized void recordSafeImeGateDetachedRecover(
            Context context,
            String packageName,
            String zone,
            String reason
    ) {
        SharedPreferences preferences = prefs(context);
        int count = preferences.getInt(KEY_SAFE_IME_GATE_DETACHED_COUNT, 0) + 1;
        preferences.edit()
                .putInt(KEY_SAFE_IME_GATE_DETACHED_COUNT, count)
                .putBoolean(KEY_SAFE_IME_GATE_ATTACHED, false)
                .apply();
        appendLog(context, "safe_ime_gate_detached_recover count=" + count
                + " package=" + sanitizeForLog(packageName)
                + " zone=" + sanitizeForLog(zone)
                + " reason=" + sanitizeForLog(reason));
    }

    static synchronized void recordSafeImeGateReadd(
            Context context,
            String packageName,
            String zone,
            String reason
    ) {
        SharedPreferences preferences = prefs(context);
        int count = preferences.getInt(KEY_SAFE_IME_GATE_READD_COUNT, 0) + 1;
        preferences.edit()
                .putInt(KEY_SAFE_IME_GATE_READD_COUNT, count)
                .apply();
        appendLog(context, "safe_ime_gate_readd count=" + count
                + " package=" + sanitizeForLog(packageName)
                + " zone=" + sanitizeForLog(zone)
                + " reason=" + sanitizeForLog(reason));
    }

    static boolean isSafeImeGateAttached(Context context) {
        return prefs(context).getBoolean(KEY_SAFE_IME_GATE_ATTACHED, false);
    }

    static int getSafeImeGateReaddCount(Context context) {
        return prefs(context).getInt(KEY_SAFE_IME_GATE_READD_COUNT, 0);
    }

    static int getSafeImeGateDetachedCount(Context context) {
        return prefs(context).getInt(KEY_SAFE_IME_GATE_DETACHED_COUNT, 0);
    }

    static String getSafeImeGateLastLayout(Context context) {
        String value = prefs(context).getString(KEY_SAFE_IME_GATE_LAST_LAYOUT, null);
        return value == null || value.length() == 0 ? null : value;
    }

    static long getSafeImeGateLastTouchAt(Context context) {
        return prefs(context).getLong(KEY_SAFE_IME_GATE_LAST_TOUCH_AT, 0L);
    }

    static long getSafeImeGateLastTouchAgeMs(Context context) {
        long at = getSafeImeGateLastTouchAt(context);
        if (at <= 0L) {
            return -1L;
        }
        return Math.max(0L, System.currentTimeMillis() - at);
    }

    static String getSafeImePolicyMode(Context context) {
        String value = prefs(context).getString(KEY_SAFE_IME_POLICY_MODE, null);
        return value == null || value.length() == 0 ? "legacy_explicit" : value;
    }

    static synchronized void recordSafeImeGateTapToken(
            Context context,
            String packageName,
            String zone
    ) {
        SharedPreferences preferences = prefs(context);
        int count = preferences.getInt(KEY_SAFE_IME_GATE_TAP_COUNT, 0) + 1;
        long now = System.currentTimeMillis();
        preferences.edit()
                .putInt(KEY_SAFE_IME_GATE_TAP_COUNT, count)
                .putLong(KEY_SAFE_IME_GATE_TOKEN_AT, now)
                .putString(KEY_SAFE_IME_GATE_TOKEN_PACKAGE, packageName == null ? "" : packageName)
                .putLong(KEY_SAFE_IME_GATE_LAST_TOUCH_AT, now)
                .remove(KEY_SAFE_IME_GATE_MISS_PACKAGE)
                .remove(KEY_SAFE_IME_GATE_MISS_FIELD)
                .remove(KEY_SAFE_IME_GATE_MISS_COUNT)
                .remove(KEY_SAFE_IME_GATE_MISS_AT)
                .putString(KEY_SAFE_IME_POLICY_MODE, "strict_gate")
                .apply();
        appendLog(context, "safe_ime_gate_tap_token count=" + count
                + " package=" + sanitizeForLog(packageName)
                + " zone=" + sanitizeForLog(zone)
                + " at=" + now);
    }

    static synchronized void recordSafeImeGateDown(Context context, String packageName, String zone) {
        SharedPreferences preferences = prefs(context);
        int count = preferences.getInt(KEY_SAFE_IME_GATE_DOWN_COUNT, 0) + 1;
        preferences.edit()
                .putInt(KEY_SAFE_IME_GATE_DOWN_COUNT, count)
                .putLong(KEY_SAFE_IME_GATE_LAST_TOUCH_AT, System.currentTimeMillis())
                .apply();
        appendLog(context, "safe_ime_gate_down count=" + count
                + " package=" + sanitizeForLog(packageName)
                + " zone=" + sanitizeForLog(zone));
    }

    static synchronized void recordSafeImeGateRescueShown(
            Context context,
            String packageName,
            String zone,
            int missCount
    ) {
        SharedPreferences preferences = prefs(context);
        int count = preferences.getInt(KEY_SAFE_IME_GATE_RESCUE_SHOWN, 0) + 1;
        preferences.edit()
                .putInt(KEY_SAFE_IME_GATE_RESCUE_SHOWN, count)
                .apply();
        appendLog(context, "safe_ime_gate_rescue_shown count=" + count
                + " package=" + sanitizeForLog(packageName)
                + " zone=" + sanitizeForLog(zone)
                + " miss_count=" + missCount);
    }

    static synchronized void recordSafeImeGateRescueTap(
            Context context,
            String packageName,
            String zone
    ) {
        SharedPreferences preferences = prefs(context);
        int count = preferences.getInt(KEY_SAFE_IME_GATE_RESCUE_TAP, 0) + 1;
        preferences.edit()
                .putInt(KEY_SAFE_IME_GATE_RESCUE_TAP, count)
                .putLong(KEY_SAFE_IME_GATE_LAST_TOUCH_AT, System.currentTimeMillis())
                .apply();
        appendLog(context, "safe_ime_gate_rescue_tap count=" + count
                + " package=" + sanitizeForLog(packageName)
                + " zone=" + sanitizeForLog(zone));
    }

    static synchronized boolean consumeSafeImeGateToken(
            Context context,
            String fieldKey,
            String packageName,
            String source
    ) {
        SharedPreferences preferences = prefs(context);
        long at = preferences.getLong(KEY_SAFE_IME_GATE_TOKEN_AT, 0L);
        String tokenPackage = preferences.getString(KEY_SAFE_IME_GATE_TOKEN_PACKAGE, "");
        long age = at <= 0L ? Long.MAX_VALUE : System.currentTimeMillis() - at;
        boolean matchesPackage = packageName != null
                && packageName.length() > 0
                && packageName.equals(tokenPackage);
        boolean matchesField = fieldKey == null
                || fieldKey.length() == 0
                || "unknown".equals(fieldKey)
                || fieldKey.startsWith(packageName + ":");
        boolean accepted = at > 0L
                && age >= 0L
                && age <= SAFE_IME_GATE_TOKEN_MS
                && matchesPackage
                && matchesField;
        if (accepted) {
            preferences.edit()
                    .remove(KEY_SAFE_IME_GATE_TOKEN_AT)
                    .remove(KEY_SAFE_IME_GATE_TOKEN_PACKAGE)
                    .apply();
            appendLog(context, "safe_ime_gate_token_consumed"
                    + " package=" + sanitizeForLog(packageName)
                    + " age=" + age
                    + " source=" + sanitizeForLog(source)
                    + " field=" + sanitizeForLog(fieldKey));
        }
        return accepted;
    }

    static synchronized void armSafeImeGateFocusPass(Context context, String packageName, String reason) {
        long until = System.currentTimeMillis() + SAFE_IME_GATE_FOCUS_PASS_MS;
        prefs(context).edit()
                .putLong(KEY_SAFE_IME_GATE_FOCUS_PASS_UNTIL, until)
                .putString(KEY_SAFE_IME_GATE_FOCUS_PASS_PACKAGE, packageName == null ? "" : packageName)
                .apply();
        appendLog(context, "safe_ime_gate_focus_pass until=" + until
                + " package=" + sanitizeForLog(packageName)
                + " reason=" + sanitizeForLog(reason));
    }

    static synchronized boolean consumeSafeImeGateFocusPass(
            Context context,
            String packageName,
            String source
    ) {
        SharedPreferences preferences = prefs(context);
        long until = preferences.getLong(KEY_SAFE_IME_GATE_FOCUS_PASS_UNTIL, 0L);
        String passPackage = preferences.getString(KEY_SAFE_IME_GATE_FOCUS_PASS_PACKAGE, "");
        boolean accepted = until > 0L
                && System.currentTimeMillis() <= until
                && packageName != null
                && packageName.equals(passPackage);
        if (accepted) {
            preferences.edit()
                    .remove(KEY_SAFE_IME_GATE_FOCUS_PASS_UNTIL)
                    .remove(KEY_SAFE_IME_GATE_FOCUS_PASS_PACKAGE)
                    .apply();
            appendLog(context, "safe_ime_gate_focus_pass_consumed"
                    + " package=" + sanitizeForLog(packageName)
                    + " source=" + sanitizeForLog(source));
        }
        return accepted;
    }

    static synchronized void recordSafeImeGateSwipeIgnored(
            Context context,
            String packageName,
            float dx,
            float dy
    ) {
        SharedPreferences preferences = prefs(context);
        int count = preferences.getInt(KEY_SAFE_IME_GATE_SWIPE_IGNORED, 0) + 1;
        preferences.edit()
                .putInt(KEY_SAFE_IME_GATE_SWIPE_IGNORED, count)
                .apply();
        appendLog(context, "safe_ime_gate_swipe_ignored count=" + count
                + " package=" + sanitizeForLog(packageName)
                + " dx=" + dx
                + " dy=" + dy);
    }

    static synchronized void recordSafeImeGateNoConnection(
            Context context,
            String packageName,
            String reason
    ) {
        SharedPreferences preferences = prefs(context);
        int count = preferences.getInt(KEY_SAFE_IME_GATE_NO_CONNECTION, 0) + 1;
        preferences.edit()
                .putInt(KEY_SAFE_IME_GATE_NO_CONNECTION, count)
                .apply();
        appendLog(context, "safe_ime_gate_no_connection count=" + count
                + " package=" + sanitizeForLog(packageName)
                + " reason=" + sanitizeForLog(reason));
    }

    static synchronized void recordSafeImeGateShow(
            Context context,
            String packageName,
            String fieldKey,
            String source
    ) {
        appendLog(context, "safe_ime_gate_show"
                + " package=" + sanitizeForLog(packageName)
                + " source=" + sanitizeForLog(source)
                + " field=" + sanitizeForLog(fieldKey));
    }

    static synchronized void recordSafeImeExplicitWithoutGateSuppressed(
            Context context,
            String fieldKey,
            String packageName,
            int flags,
            String reason
    ) {
        SharedPreferences preferences = prefs(context);
        int count = preferences.getInt(KEY_SAFE_IME_EXPLICIT_WITHOUT_GATE_SUPPRESSED, 0) + 1;
        long now = System.currentTimeMillis();
        String oldPackage = preferences.getString(KEY_SAFE_IME_GATE_MISS_PACKAGE, "");
        String oldField = preferences.getString(KEY_SAFE_IME_GATE_MISS_FIELD, "");
        long oldAt = preferences.getLong(KEY_SAFE_IME_GATE_MISS_AT, 0L);
        int oldMissCount = preferences.getInt(KEY_SAFE_IME_GATE_MISS_COUNT, 0);
        boolean sameMiss = String.valueOf(packageName).equals(oldPackage)
                && String.valueOf(fieldKey).equals(oldField)
                && oldAt > 0L
                && now - oldAt >= 0L
                && now - oldAt <= 8_000L;
        int missCount = sameMiss ? oldMissCount + 1 : 1;
        preferences.edit()
                .putInt(KEY_SAFE_IME_EXPLICIT_WITHOUT_GATE_SUPPRESSED, count)
                .putString(KEY_SAFE_IME_LAST_FIELD, fieldKey == null ? "" : fieldKey)
                .putString(KEY_SAFE_IME_LAST_CALLBACK, "explicit_without_gate_suppressed")
                .putString(KEY_SAFE_IME_LAST_CALLBACK_DETAIL,
                        "flags=" + flags
                                + " package=" + sanitizeForLog(packageName)
                                + " reason=" + sanitizeForLog(reason))
                .putString(KEY_SAFE_IME_GATE_MISS_PACKAGE, packageName == null ? "" : packageName)
                .putString(KEY_SAFE_IME_GATE_MISS_FIELD, fieldKey == null ? "" : fieldKey)
                .putInt(KEY_SAFE_IME_GATE_MISS_COUNT, missCount)
                .putLong(KEY_SAFE_IME_GATE_MISS_AT, now)
                .putString(KEY_SAFE_IME_POLICY_MODE, "strict_gate")
                .apply();
        appendLog(context, "safe_ime_explicit_without_gate_suppressed count=" + count
                + " miss_count=" + missCount
                + " flags=" + flags
                + " package=" + sanitizeForLog(packageName)
                + " reason=" + sanitizeForLog(reason)
                + " field=" + sanitizeForLog(fieldKey));
    }

    static int getSafeImeGateTapCount(Context context) {
        return prefs(context).getInt(KEY_SAFE_IME_GATE_TAP_COUNT, 0);
    }

    static int getSafeImeGateDownCount(Context context) {
        return prefs(context).getInt(KEY_SAFE_IME_GATE_DOWN_COUNT, 0);
    }

    static int getSafeImeGateSwipeIgnoredCount(Context context) {
        return prefs(context).getInt(KEY_SAFE_IME_GATE_SWIPE_IGNORED, 0);
    }

    static int getSafeImeGateNoConnectionCount(Context context) {
        return prefs(context).getInt(KEY_SAFE_IME_GATE_NO_CONNECTION, 0);
    }

    static int getSafeImeExplicitWithoutGateSuppressedCount(Context context) {
        return prefs(context).getInt(KEY_SAFE_IME_EXPLICIT_WITHOUT_GATE_SUPPRESSED, 0);
    }

    static int getSafeImeGateRescueShownCount(Context context) {
        return prefs(context).getInt(KEY_SAFE_IME_GATE_RESCUE_SHOWN, 0);
    }

    static int getSafeImeGateRescueTapCount(Context context) {
        return prefs(context).getInt(KEY_SAFE_IME_GATE_RESCUE_TAP, 0);
    }

    static String getSafeImeGateMissPackage(Context context) {
        String value = prefs(context).getString(KEY_SAFE_IME_GATE_MISS_PACKAGE, null);
        return value == null || value.length() == 0 ? null : value;
    }

    static String getSafeImeGateMissField(Context context) {
        String value = prefs(context).getString(KEY_SAFE_IME_GATE_MISS_FIELD, null);
        return value == null || value.length() == 0 ? null : value;
    }

    static int getSafeImeGateMissCount(Context context) {
        return prefs(context).getInt(KEY_SAFE_IME_GATE_MISS_COUNT, 0);
    }

    static long getSafeImeGateMissAt(Context context) {
        return prefs(context).getLong(KEY_SAFE_IME_GATE_MISS_AT, 0L);
    }

    static long getSafeImeGateMissAgeMs(Context context) {
        long at = getSafeImeGateMissAt(context);
        if (at <= 0L) {
            return -1L;
        }
        return Math.max(0L, System.currentTimeMillis() - at);
    }

    static synchronized void recordSafeImeGateException(Context context, String source, Throwable error) {
        SharedPreferences preferences = prefs(context);
        int count = preferences.getInt(KEY_SAFE_IME_GATE_EXCEPTION_COUNT, 0) + 1;
        String detail = compactException(source, error);
        preferences.edit()
                .putInt(KEY_SAFE_IME_GATE_EXCEPTION_COUNT, count)
                .putString(KEY_SAFE_IME_GATE_LAST_EXCEPTION, detail)
                .apply();
        appendLog(context, "safe_ime_gate_exception count=" + count
                + " " + sanitizeForLog(detail));
    }

    static int getSafeImeGateExceptionCount(Context context) {
        return prefs(context).getInt(KEY_SAFE_IME_GATE_EXCEPTION_COUNT, 0);
    }

    static String getSafeImeGateLastException(Context context) {
        String value = prefs(context).getString(KEY_SAFE_IME_GATE_LAST_EXCEPTION, null);
        return value == null || value.length() == 0 ? null : value;
    }

    static void recordSafeImeNavigationInput(
            Context context,
            String fieldKey,
            String reason,
            long suppressMs
    ) {
        long until = System.currentTimeMillis() + Math.max(1L, suppressMs);
        prefs(context).edit()
                .putLong(KEY_SAFE_IME_NAV_SUPPRESS_UNTIL, until)
                .putString(KEY_SAFE_IME_LAST_NAV_FIELD, fieldKey == null ? "" : fieldKey)
                .putString(KEY_SAFE_IME_LAST_NAV_REASON, reason == null ? "" : reason)
                .apply();
        appendLog(context, "safe_ime_navigation_input field=" + sanitizeForLog(fieldKey)
                + " reason=" + sanitizeForLog(reason)
                + " until=" + until);
        appendLog(context, "safe_ime_navigation_guard field=" + sanitizeForLog(fieldKey)
                + " reason=" + sanitizeForLog(reason)
                + " until=" + until);
    }

    static long getSafeImeNavigationSuppressUntil(Context context) {
        return prefs(context).getLong(KEY_SAFE_IME_NAV_SUPPRESS_UNTIL, 0L);
    }

    static String getSafeImeLastNavigationField(Context context) {
        String value = prefs(context).getString(KEY_SAFE_IME_LAST_NAV_FIELD, null);
        return value == null || value.length() == 0 ? null : value;
    }

    static String getSafeImeLastNavigationReason(Context context) {
        String value = prefs(context).getString(KEY_SAFE_IME_LAST_NAV_REASON, null);
        return value == null || value.length() == 0 ? null : value;
    }

    static void recordSafeImeManualShowAccepted(Context context, String fieldKey, long hiddenAgeMs) {
        SharedPreferences preferences = prefs(context);
        int count = preferences.getInt(KEY_SAFE_IME_SHOWN, 0) + 1;
        preferences.edit().putInt(KEY_SAFE_IME_SHOWN, count).apply();
        appendLog(context, "safe_ime_manual_show_accepted count=" + count
                + " age=" + hiddenAgeMs
                + " field=" + sanitizeForLog(fieldKey));
    }

    static void recordSafeImeHideButton(Context context, String fieldKey, long suppressionMs) {
        SharedPreferences preferences = prefs(context);
        int count = preferences.getInt(KEY_SAFE_IME_HIDE_BUTTON, 0) + 1;
        preferences.edit()
                .putInt(KEY_SAFE_IME_HIDE_BUTTON, count)
                .putString(KEY_SAFE_IME_LAST_HIDE_REASON, "hide_button")
                .putString(KEY_SAFE_IME_LAST_FIELD, fieldKey == null ? "" : fieldKey)
                .putLong(KEY_SAFE_IME_HIDE_SUPPRESSION_MS, Math.max(0L, suppressionMs))
                .putLong(KEY_SAFE_IME_LAST_AUTO_HIDE_AT, System.currentTimeMillis())
                .apply();
        appendLog(context, "safe_ime_hide_button count=" + count
                + " suppress_ms=" + Math.max(0L, suppressionMs)
                + " field=" + sanitizeForLog(fieldKey));
    }

    static long getSafeImeHideSuppressionMs(Context context) {
        return prefs(context).getLong(KEY_SAFE_IME_HIDE_SUPPRESSION_MS, 0L);
    }

    static void recordSafeImeHideSelfRequested(Context context, String reason, String fieldKey) {
        appendLog(context, "safe_ime_hide_self_requested reason=" + sanitizeForLog(reason)
                + " field=" + sanitizeForLog(fieldKey));
    }

    static void recordSafeImeExtractHidden(Context context, String source) {
        SharedPreferences preferences = prefs(context);
        int count = preferences.getInt(KEY_SAFE_IME_EXTRACT_HIDDEN, 0) + 1;
        preferences.edit().putInt(KEY_SAFE_IME_EXTRACT_HIDDEN, count).apply();
        appendLog(context, "safe_ime_extract_hidden count=" + count
                + " source=" + sanitizeForLog(source));
    }

    static void recordSafeImeClickSignalMissing(Context context, String fieldKey, String reason) {
        SharedPreferences preferences = prefs(context);
        int count = preferences.getInt(KEY_SAFE_IME_CLICK_SIGNAL_MISSING, 0) + 1;
        preferences.edit().putInt(KEY_SAFE_IME_CLICK_SIGNAL_MISSING, count).apply();
        appendLog(context, "safe_ime_click_signal_missing count=" + count
                + " reason=" + sanitizeForLog(reason)
                + " field=" + sanitizeForLog(fieldKey));
    }

    static void recordSafeImeStartInput(Context context, String fieldKey, boolean restarting) {
        SharedPreferences preferences = prefs(context);
        int count = preferences.getInt(KEY_SAFE_IME_START_INPUT, 0) + 1;
        preferences.edit()
                .putInt(KEY_SAFE_IME_START_INPUT, count)
                .putString(KEY_SAFE_IME_LAST_FIELD, fieldKey == null ? "" : fieldKey)
                .putString(KEY_SAFE_IME_LAST_CALLBACK, "onStartInput")
                .putString(KEY_SAFE_IME_LAST_CALLBACK_DETAIL, "restarting=" + restarting)
                .apply();
        appendLog(context, "safe_ime_on_start_input count=" + count
                + " restarting=" + restarting
                + " field=" + sanitizeForLog(fieldKey));
    }

    static void recordSafeImeStartInputView(Context context, String fieldKey, boolean restarting) {
        SharedPreferences preferences = prefs(context);
        int count = preferences.getInt(KEY_SAFE_IME_START_INPUT_VIEW, 0) + 1;
        preferences.edit()
                .putInt(KEY_SAFE_IME_START_INPUT_VIEW, count)
                .putString(KEY_SAFE_IME_LAST_FIELD, fieldKey == null ? "" : fieldKey)
                .putString(KEY_SAFE_IME_LAST_CALLBACK, "onStartInputView")
                .putString(KEY_SAFE_IME_LAST_CALLBACK_DETAIL, "restarting=" + restarting)
                .apply();
        appendLog(context, "safe_ime_on_start_input_view count=" + count
                + " restarting=" + restarting
                + " field=" + sanitizeForLog(fieldKey));
    }

    static void recordSafeImeShowInputRequested(
            Context context,
            int flags,
            boolean configChange,
            boolean accepted,
            String reason
    ) {
        SharedPreferences preferences = prefs(context);
        int count = preferences.getInt(KEY_SAFE_IME_SHOW_REQUEST, 0) + 1;
        preferences.edit()
                .putInt(KEY_SAFE_IME_SHOW_REQUEST, count)
                .putInt(KEY_SAFE_IME_LAST_SHOW_FLAGS, flags)
                .putBoolean(KEY_SAFE_IME_LAST_SHOW_CONFIG, configChange)
                .putString(KEY_SAFE_IME_LAST_CALLBACK, "onShowInputRequested")
                .putString(KEY_SAFE_IME_LAST_CALLBACK_DETAIL,
                        "flags=" + flags + " config=" + configChange
                                + " accepted=" + accepted + " reason=" + sanitizeForLog(reason))
                .apply();
        appendLog(context, "safe_ime_on_show_input_requested count=" + count
                + " flags=" + flags
                + " config=" + configChange
                + " accepted=" + accepted
                + " reason=" + sanitizeForLog(reason));
    }

    static void recordSafeImeEvaluateFullscreen(Context context, boolean fullscreen) {
        SharedPreferences preferences = prefs(context);
        int count = preferences.getInt(KEY_SAFE_IME_EVALUATE_FULLSCREEN, 0) + 1;
        preferences.edit()
                .putInt(KEY_SAFE_IME_EVALUATE_FULLSCREEN, count)
                .putString(KEY_SAFE_IME_LAST_CALLBACK, "onEvaluateFullscreenMode")
                .putString(KEY_SAFE_IME_LAST_CALLBACK_DETAIL, "fullscreen=" + fullscreen)
                .apply();
        appendLog(context, "safe_ime_on_evaluate_fullscreen count=" + count
                + " fullscreen=" + fullscreen);
    }

    static void recordSafeImeCreateExtract(Context context) {
        SharedPreferences preferences = prefs(context);
        int count = preferences.getInt(KEY_SAFE_IME_CREATE_EXTRACT, 0) + 1;
        preferences.edit()
                .putInt(KEY_SAFE_IME_CREATE_EXTRACT, count)
                .putString(KEY_SAFE_IME_LAST_CALLBACK, "onCreateExtractTextView")
                .putString(KEY_SAFE_IME_LAST_CALLBACK_DETAIL, "safe_dark_view")
                .apply();
        appendLog(context, "safe_ime_on_create_extract_text_view count=" + count
                + " view=safe_dark");
    }

    static void recordSafeImeUpdateExtractingVisibility(
            Context context,
            String source,
            String fieldKey
    ) {
        SharedPreferences preferences = prefs(context);
        int count = preferences.getInt(KEY_SAFE_IME_UPDATE_EXTRACT_VISIBILITY, 0) + 1;
        preferences.edit()
                .putInt(KEY_SAFE_IME_UPDATE_EXTRACT_VISIBILITY, count)
                .putString(KEY_SAFE_IME_LAST_FIELD, fieldKey == null ? "" : fieldKey)
                .putString(KEY_SAFE_IME_LAST_CALLBACK, "onUpdateExtractingVisibility")
                .putString(KEY_SAFE_IME_LAST_CALLBACK_DETAIL,
                        "source=" + sanitizeForLog(source))
                .apply();
        appendLog(context, "safe_ime_on_update_extracting_visibility count=" + count
                + " source=" + sanitizeForLog(source)
                + " field=" + sanitizeForLog(fieldKey));
    }

    static void recordSafeImeManualSignalAbsent(Context context, String fieldKey, String source) {
        SharedPreferences preferences = prefs(context);
        int count = preferences.getInt(KEY_SAFE_IME_MANUAL_SIGNAL_ABSENT, 0) + 1;
        preferences.edit()
                .putInt(KEY_SAFE_IME_MANUAL_SIGNAL_ABSENT, count)
                .putString(KEY_SAFE_IME_LAST_FIELD, fieldKey == null ? "" : fieldKey)
                .putString(KEY_SAFE_IME_LAST_CALLBACK, "manual_signal_absent")
                .putString(KEY_SAFE_IME_LAST_CALLBACK_DETAIL,
                        "source=" + sanitizeForLog(source))
                .apply();
        appendLog(context, "safe_ime_manual_signal_absent count=" + count
                + " source=" + sanitizeForLog(source)
                + " field=" + sanitizeForLog(fieldKey));
    }

    static long getSafeImeTapTokenMs() {
        return SAFE_IME_TAP_TOKEN_MS;
    }

    static synchronized void recordSafeImeA11yTap(
            Context context,
            String packageName,
            String field,
            String source,
            String detail
    ) {
        SharedPreferences preferences = prefs(context);
        int count = preferences.getInt(KEY_SAFE_IME_A11Y_TAP_COUNT, 0) + 1;
        preferences.edit()
                .putLong(KEY_SAFE_IME_A11Y_TAP_AT, System.currentTimeMillis())
                .putString(KEY_SAFE_IME_A11Y_TAP_PACKAGE, packageName == null ? "" : packageName)
                .putString(KEY_SAFE_IME_A11Y_TAP_FIELD, field == null ? "" : field)
                .putInt(KEY_SAFE_IME_A11Y_TAP_COUNT, count)
                .putString(KEY_SAFE_IME_LAST_CALLBACK, "a11y_tap")
                .putString(KEY_SAFE_IME_LAST_CALLBACK_DETAIL,
                        "source=" + sanitizeForLog(source)
                                + " detail=" + sanitizeForLog(detail))
                .apply();
        appendLog(context, "safe_ime_a11y_tap_detected count=" + count
                + " package=" + sanitizeForLog(packageName)
                + " source=" + sanitizeForLog(source)
                + " field=" + sanitizeForLog(field)
                + " detail=" + sanitizeForLog(detail));
    }

    static synchronized void recordSafeImeA11yFocus(
            Context context,
            String packageName,
            String field,
            String detail
    ) {
        SharedPreferences preferences = prefs(context);
        int count = preferences.getInt(KEY_SAFE_IME_A11Y_FOCUS_COUNT, 0) + 1;
        preferences.edit()
                .putInt(KEY_SAFE_IME_A11Y_FOCUS_COUNT, count)
                .putString(KEY_SAFE_IME_LAST_CALLBACK, "a11y_focus")
                .putString(KEY_SAFE_IME_LAST_CALLBACK_DETAIL,
                        "package=" + sanitizeForLog(packageName)
                                + " detail=" + sanitizeForLog(detail))
                .apply();
        appendLog(context, "safe_ime_a11y_focus count=" + count
                + " package=" + sanitizeForLog(packageName)
                + " field=" + sanitizeForLog(field)
                + " detail=" + sanitizeForLog(detail));
    }

    static synchronized boolean consumeSafeImeA11yTap(
            Context context,
            String fieldKey,
            String source
    ) {
        SharedPreferences preferences = prefs(context);
        long tapAt = preferences.getLong(KEY_SAFE_IME_A11Y_TAP_AT, 0L);
        String packageName = preferences.getString(KEY_SAFE_IME_A11Y_TAP_PACKAGE, "");
        String field = preferences.getString(KEY_SAFE_IME_A11Y_TAP_FIELD, "");
        if (tapAt <= 0L) {
            return false;
        }
        long age = Math.max(0L, System.currentTimeMillis() - tapAt);
        if (age > SAFE_IME_TAP_TOKEN_MS) {
            int expired = preferences.getInt(KEY_SAFE_IME_A11Y_EXPIRED_COUNT, 0) + 1;
            preferences.edit()
                    .remove(KEY_SAFE_IME_A11Y_TAP_AT)
                    .remove(KEY_SAFE_IME_A11Y_TAP_PACKAGE)
                    .remove(KEY_SAFE_IME_A11Y_TAP_FIELD)
                    .putInt(KEY_SAFE_IME_A11Y_EXPIRED_COUNT, expired)
                    .apply();
            appendLog(context, "safe_ime_a11y_tap_expired count=" + expired
                    + " age=" + age
                    + " source=" + sanitizeForLog(source));
            return false;
        }
        if (!safeImeTapMatches(packageName, fieldKey)) {
            appendLog(context, "safe_ime_a11y_tap_mismatch package="
                    + sanitizeForLog(packageName)
                    + " field_key=" + sanitizeForLog(fieldKey)
                    + " source=" + sanitizeForLog(source));
            return false;
        }
        int consumed = preferences.getInt(KEY_SAFE_IME_A11Y_CONSUMED_COUNT, 0) + 1;
        preferences.edit()
                .remove(KEY_SAFE_IME_A11Y_TAP_AT)
                .remove(KEY_SAFE_IME_A11Y_TAP_PACKAGE)
                .remove(KEY_SAFE_IME_A11Y_TAP_FIELD)
                .putInt(KEY_SAFE_IME_A11Y_CONSUMED_COUNT, consumed)
                .putString(KEY_SAFE_IME_LAST_CALLBACK, "a11y_manual_show")
                .putString(KEY_SAFE_IME_LAST_CALLBACK_DETAIL,
                        "source=" + sanitizeForLog(source)
                                + " package=" + sanitizeForLog(packageName)
                                + " field=" + sanitizeForLog(field))
                .apply();
        appendLog(context, "safe_ime_a11y_manual_show count=" + consumed
                + " source=" + sanitizeForLog(source)
                + " package=" + sanitizeForLog(packageName)
                + " age=" + age
                + " field=" + sanitizeForLog(field));
        return true;
    }

    private static boolean safeImeTapMatches(String packageName, String fieldKey) {
        if (packageName == null || packageName.length() == 0) {
            return true;
        }
        if (fieldKey == null || fieldKey.length() == 0 || "unknown".equals(fieldKey)) {
            return true;
        }
        return fieldKey.startsWith(packageName + ":");
    }

    static synchronized void recordSafeImeA11yNotSelected(Context context, String packageName) {
        SharedPreferences preferences = prefs(context);
        int count = preferences.getInt(KEY_SAFE_IME_A11Y_NOT_SELECTED, 0) + 1;
        preferences.edit().putInt(KEY_SAFE_IME_A11Y_NOT_SELECTED, count).apply();
        appendLog(context, "safe_ime_a11y_not_selected count=" + count
                + " package=" + sanitizeForLog(packageName));
    }

    static void observeSafeImeTapAccessibility(Context context) {
        SharedPreferences preferences = prefs(context);
        boolean enabled = isSafeImeTapAccessibilityEnabled(context);
        boolean wasEnabled = preferences.getBoolean(KEY_SAFE_IME_A11Y_WAS_ENABLED, false);
        SharedPreferences.Editor editor = preferences.edit()
                .putBoolean(KEY_SAFE_IME_A11Y_WAS_ENABLED, enabled);
        if (wasEnabled && !enabled) {
            int count = preferences.getInt(KEY_SAFE_IME_TAP_SERVICE_OFF, 0) + 1;
            editor.putInt(KEY_SAFE_IME_TAP_SERVICE_OFF, count);
            appendLog(context, "safe_ime_tap_service_off count=" + count);
        }
        editor.apply();
    }

    static void recordSafeImeCrashGuard(Context context, String source, Throwable error) {
        SharedPreferences preferences = prefs(context);
        int count = preferences.getInt(KEY_SAFE_IME_CRASH_GUARD, 0) + 1;
        String type = error == null ? "unknown" : error.getClass().getSimpleName();
        String message = error == null ? "" : error.getMessage();
        preferences.edit()
                .putInt(KEY_SAFE_IME_CRASH_GUARD, count)
                .putString(KEY_SAFE_IME_LAST_CRASH_SOURCE, source == null ? "" : source)
                .putString(KEY_SAFE_IME_LAST_CRASH_ERROR, type + ":" + message)
                .putString(KEY_SAFE_IME_LAST_CALLBACK, "crash_guard")
                .putString(KEY_SAFE_IME_LAST_CALLBACK_DETAIL,
                        "source=" + sanitizeForLog(source)
                                + " error=" + sanitizeForLog(type + ":" + message))
                .apply();
        appendLog(context, "safe_ime_crash_guard count=" + count
                + " source=" + sanitizeForLog(source)
                + " error=" + sanitizeForLog(type + ":" + message));
    }

    static void observeSafeImeDefault(Context context) {
        SharedPreferences preferences = prefs(context);
        boolean enabled = isWatchKeyboardEnabled(context);
        boolean selected = isWatchKeyboardSelected(context);
        boolean wasSelected = preferences.getBoolean(KEY_SAFE_IME_WAS_SELECTED, false);
        SharedPreferences.Editor editor = preferences.edit()
                .putBoolean(KEY_SAFE_IME_WAS_SELECTED, selected);
        if (enabled && wasSelected && !selected) {
            int count = preferences.getInt(KEY_SAFE_IME_DEFAULT_LOST, 0) + 1;
            String defaultIme = Settings.Secure.getString(
                    context.getContentResolver(),
                    Settings.Secure.DEFAULT_INPUT_METHOD
            );
            editor.putInt(KEY_SAFE_IME_DEFAULT_LOST, count);
            String contextDetail = safeImeDefaultLostContext(context, defaultIme);
            editor.putString(KEY_SAFE_IME_DEFAULT_LOST_CONTEXT, contextDetail);
            appendLog(context, "safe_ime_default_lost count=" + count
                    + " " + sanitizeForLog(contextDetail));
        }
        editor.apply();
    }

    static void recordSafeImeSnapshot(Context context, String phase, long cycleId, String target) {
        SharedPreferences preferences = prefs(context);
        String defaultIme = currentDefaultIme(context);
        boolean enabled = isWatchKeyboardEnabled(context);
        boolean selected = isWatchKeyboardSelected(context);
        String snapshot = "phase=" + sanitizeForLog(phase)
                + " cycle=" + cycleId
                + " default=" + sanitizeForLog(defaultIme)
                + " selected=" + selected
                + " enabled=" + enabled
                + " locked=" + isLocked(context)
                + " target=" + sanitizeForLog(target)
                + " cached_app=" + sanitizeForLog(getLastCachedApp(context))
                + " menu_after_app=" + wasMenuSeenAfterCachedApp(context);
        SharedPreferences.Editor editor = preferences.edit()
                .putString(KEY_SAFE_IME_LAST_SNAPSHOT, snapshot);
        String old = preferences.getString(KEY_SAFE_IME_SNAPSHOT_LOG, "");
        String next = snapshot + "\n" + old;
        if (next.length() > MAX_SAFE_IME_SNAPSHOT_CHARS) {
            next = next.substring(0, MAX_SAFE_IME_SNAPSHOT_CHARS);
        }
        editor.putString(KEY_SAFE_IME_SNAPSHOT_LOG, next);
        if ("lock_start".equals(phase)) {
            editor.putString(KEY_SAFE_IME_LOCK_DEFAULT, defaultIme == null ? "" : defaultIme)
                    .putBoolean(KEY_SAFE_IME_LOCK_SELECTED, selected)
                    .putString(KEY_SAFE_IME_SNAPSHOT_BEFORE_LOCK, snapshot)
                    .remove(KEY_SAFE_IME_LOST_PHASE)
                    .remove(KEY_SAFE_IME_LOST_RECORDED_CYCLE)
                    .remove(KEY_SAFE_IME_SILENT_RESTORE_SKIP_CYCLE);
        }
        editor.apply();
        appendLog(context, "safe_ime_snapshot " + snapshot);
        recordSafeImeLostDuringUnlockIfNeeded(context, phase, cycleId, selected, enabled, defaultIme);
    }

    private static void recordSafeImeLostDuringUnlockIfNeeded(
            Context context,
            String phase,
            long cycleId,
            boolean selected,
            boolean enabled,
            String defaultIme
    ) {
        if (!isUnlockImeSnapshotPhase(phase) || selected || !enabled) {
            return;
        }
        SharedPreferences preferences = prefs(context);
        if (!preferences.getBoolean(KEY_SAFE_IME_LOCK_SELECTED, false)) {
            return;
        }
        long recordedCycle = preferences.getLong(KEY_SAFE_IME_LOST_RECORDED_CYCLE, -1L);
        if (recordedCycle == cycleId) {
            return;
        }
        int count = preferences.getInt(KEY_SAFE_IME_LOST_DURING_UNLOCK, 0) + 1;
        preferences.edit()
                .putInt(KEY_SAFE_IME_LOST_DURING_UNLOCK, count)
                .putString(KEY_SAFE_IME_LOST_PHASE, phase == null ? "" : phase)
                .putLong(KEY_SAFE_IME_LOST_RECORDED_CYCLE, cycleId)
                .apply();
        appendLog(context, "safe_ime_default_lost_during_unlock count=" + count
                + " phase=" + sanitizeForLog(phase)
                + " cycle=" + cycleId
                + " default=" + sanitizeForLog(defaultIme));
    }

    static boolean trySilentRestoreSafeImeDefault(Context context, String phase, long cycleId) {
        SharedPreferences preferences = prefs(context);
        preferences.edit()
                .putString(KEY_SAFE_IME_RESTORE_ATTEMPT_PHASE, phase == null ? "" : phase)
                .apply();
        if (isWatchKeyboardSelected(context)) {
            return true;
        }
        if (!isWatchKeyboardEnabled(context)) {
            preferences.edit()
                    .putString(KEY_SAFE_IME_SILENT_RESTORE_LAST_RESULT,
                            "skip_keyboard_not_enabled phase=" + phase)
                    .apply();
            appendLog(context, "safe_ime_silent_restore_skip reason=keyboard_not_enabled"
                    + " phase=" + sanitizeForLog(phase)
                    + " cycle=" + cycleId);
            return false;
        }
        String targetIme = new ComponentName(context, TinyInputMethodService.class)
                .flattenToShortString();
        if (!hasWriteSecureSettingsPermission(context)) {
            long skippedCycle = preferences.getLong(KEY_SAFE_IME_SILENT_RESTORE_SKIP_CYCLE, -1L);
            if (skippedCycle != cycleId) {
                preferences.edit()
                        .putLong(KEY_SAFE_IME_SILENT_RESTORE_SKIP_CYCLE, cycleId)
                        .putString(KEY_SAFE_IME_SILENT_RESTORE_LAST_RESULT,
                                "skip_no_write_secure_settings phase=" + phase)
                        .apply();
                appendLog(context, "safe_ime_silent_restore_skip reason=no_write_secure_settings"
                        + " phase=" + sanitizeForLog(phase)
                        + " cycle=" + cycleId);
            }
            return false;
        }
        try {
            boolean ok = Settings.Secure.putString(
                    context.getContentResolver(),
                    Settings.Secure.DEFAULT_INPUT_METHOD,
                    targetIme
            );
            String result = ok ? "ok" : "putString_false";
            SharedPreferences.Editor editor = preferences.edit()
                    .putString(KEY_SAFE_IME_SILENT_RESTORE_LAST_RESULT,
                            result + " phase=" + phase + " ime=" + targetIme);
            if (ok) {
                int count = preferences.getInt(KEY_SAFE_IME_SILENT_RESTORE_COUNT, 0) + 1;
                editor.putInt(KEY_SAFE_IME_SILENT_RESTORE_COUNT, count);
            }
            editor.apply();
            appendLog(context, "safe_ime_silent_restore_" + (ok ? "ok" : "failed")
                    + " phase=" + sanitizeForLog(phase)
                    + " cycle=" + cycleId
                    + " ime=" + sanitizeForLog(targetIme));
            return ok;
        } catch (RuntimeException error) {
            String detail = error.getClass().getSimpleName() + ":" + error.getMessage();
            preferences.edit()
                    .putString(KEY_SAFE_IME_SILENT_RESTORE_LAST_RESULT,
                            "error phase=" + phase + " " + detail)
                    .apply();
            appendLog(context, "safe_ime_silent_restore_failed phase=" + sanitizeForLog(phase)
                    + " cycle=" + cycleId
                    + " error=" + sanitizeForLog(detail));
            return false;
        }
    }

    static void recordKeyboardHideUnlock(
            Context context,
            String phase,
            boolean requested,
            boolean hasToken,
            String source
    ) {
        SharedPreferences preferences = prefs(context);
        int count = preferences.getInt(KEY_KEYBOARD_HIDE_UNLOCK_COUNT, 0) + 1;
        preferences.edit()
                .putInt(KEY_KEYBOARD_HIDE_UNLOCK_COUNT, count)
                .apply();
        appendLog(context, "keyboard_hide_unlock count=" + count
                + " phase=" + sanitizeForLog(phase)
                + " requested=" + requested
                + " token=" + hasToken
                + " source=" + sanitizeForLog(source));
    }

    static void recordVisibleAppCache(Context context, String packageName, String source) {
        if (packageName == null || packageName.length() == 0) {
            return;
        }
        prefs(context).edit()
                .putString(KEY_LAST_CACHED_APP, packageName)
                .putLong(KEY_LAST_CACHED_APP_AT, System.currentTimeMillis())
                .putString(KEY_LAST_CACHED_APP_SOURCE, source == null ? "" : source)
                .putBoolean(KEY_LAST_MENU_SEEN_AFTER_APP, false)
                .remove(KEY_LAST_MENU_SEEN_AFTER_APP_AT)
                .apply();
        appendLog(context, "cached_visible_app package=" + sanitizeForLog(packageName)
                + " source=" + sanitizeForLog(source));
    }

    static void recordMenuSeenAfterCachedApp(Context context, String source) {
        if (getLastCachedApp(context) == null) {
            return;
        }
        boolean navigation = source != null
                && (source.contains("navigation") || source.contains("usage_home"));
        SharedPreferences.Editor editor = prefs(context).edit()
                .putBoolean(KEY_LAST_MENU_SEEN_AFTER_APP, true)
                .putLong(KEY_LAST_MENU_SEEN_AFTER_APP_AT, System.currentTimeMillis());
        if (navigation) {
            editor.putBoolean(KEY_LAST_NAVIGATION_SEEN_AFTER_APP, true)
                    .putLong(KEY_LAST_NAVIGATION_SEEN_AFTER_APP_AT, System.currentTimeMillis())
                    .putString(KEY_LAST_NAVIGATION_SOURCE, source == null ? "" : source);
        }
        editor.apply();
        appendLog(context, "cached_menu_seen_after_app source=" + sanitizeForLog(source));
        if (navigation) {
            appendLog(context, "cached_app_invalidated_navigation source=" + sanitizeForLog(source));
        }
    }

    static String getStrictCachedLockPackage(Context context) {
        SharedPreferences preferences = prefs(context);
        String packageName = preferences.getString(KEY_LAST_CACHED_APP, null);
        long cachedAt = preferences.getLong(KEY_LAST_CACHED_APP_AT, 0L);
        long armedAt = getHomeTrapArmedAt(context);
        if (!isStrictCachedLockPackage(packageName)
                || cachedAt <= 0L
                || (armedAt > 0L && cachedAt < armedAt)
                || preferences.getBoolean(KEY_LAST_MENU_SEEN_AFTER_APP, false)) {
            return null;
        }
        long age = System.currentTimeMillis() - cachedAt;
        if (age < 0L || age > STRICT_CACHED_LOCK_APP_MS) {
            return null;
        }
        appendLog(context, "screen_fallback_cached_app candidate=" + sanitizeForLog(packageName)
                + " age=" + age
                + " source=" + sanitizeForLog(preferences.getString(KEY_LAST_CACHED_APP_SOURCE, "")));
        return packageName;
    }

    static String getUnlockFallbackPackage(Context context) {
        if (isGptAutoLockActive(context) || isGptAutoLockRequested(context)) {
            return GptPhotoShareCoordinator.GPT_PACKAGE;
        }
        SharedPreferences preferences = prefs(context);
        String packageName = preferences.getString(KEY_LAST_CACHED_APP, null);
        long cachedAt = preferences.getLong(KEY_LAST_CACHED_APP_AT, 0L);
        if (!isStrictCachedLockPackage(packageName) || cachedAt <= 0L) {
            return null;
        }
        long age = System.currentTimeMillis() - cachedAt;
        return age >= 0L && age <= STRICT_CACHED_LOCK_APP_MS ? packageName : null;
    }

    static synchronized void recordUnlockMissingTargetRecovered(
            Context context,
            String targetPackage,
            String reason
    ) {
        appendLog(context, "unlock_missing_target_recovered target="
                + sanitizeForLog(targetPackage)
                + " reason=" + sanitizeForLog(reason));
        appendLockModeResult(context, "home_trap", "unlock_missing_target_recovered",
                "target=" + sanitizeForLog(targetPackage)
                        + " reason=" + sanitizeForLog(reason));
    }

    static synchronized void recordUnlockMissingTargetLauncherRescue(
            Context context,
            String reason
    ) {
        appendLog(context, "unlock_missing_target_launcher_rescue reason="
                + sanitizeForLog(reason));
        appendLockModeResult(context, "home_trap", "unlock_missing_target_launcher_rescue",
                sanitizeForLog(reason));
    }

    static synchronized void recordGptUnlockRestoreTarget(Context context, String targetPackage) {
        appendLog(context, "gpt_unlock_restore_target target="
                + sanitizeForLog(targetPackage));
        appendLockModeResult(context, "home_trap", "gpt_unlock_restore_target",
                "target=" + sanitizeForLog(targetPackage));
    }

    static String getLastCachedApp(Context context) {
        String value = prefs(context).getString(KEY_LAST_CACHED_APP, null);
        return value == null || value.length() == 0 ? null : value;
    }

    static long getLastCachedAppAt(Context context) {
        return prefs(context).getLong(KEY_LAST_CACHED_APP_AT, 0L);
    }

    static boolean wasMenuSeenAfterCachedApp(Context context) {
        return prefs(context).getBoolean(KEY_LAST_MENU_SEEN_AFTER_APP, false);
    }

    static long getLastMenuSeenAfterAppAt(Context context) {
        return prefs(context).getLong(KEY_LAST_MENU_SEEN_AFTER_APP_AT, 0L);
    }

    static boolean wasNavigationSeenAfterCachedApp(Context context) {
        return prefs(context).getBoolean(KEY_LAST_NAVIGATION_SEEN_AFTER_APP, false);
    }

    static long getLastNavigationSeenAfterAppAt(Context context) {
        return prefs(context).getLong(KEY_LAST_NAVIGATION_SEEN_AFTER_APP_AT, 0L);
    }

    static String getLastNavigationSource(Context context) {
        String value = prefs(context).getString(KEY_LAST_NAVIGATION_SOURCE, null);
        return value == null || value.length() == 0 ? null : value;
    }

    private static boolean isStrictCachedLockPackage(String packageName) {
        return "com.openai.chatgpt".equals(packageName)
                || "com.whatsapp".equals(packageName);
    }

    private static String safeImeDefaultLostContext(Context context, String defaultIme) {
        String visible = "unknown";
        try {
            visible = ForegroundResolver.resolveVisibleScreen(context).describe();
        } catch (RuntimeException error) {
            visible = "error=" + error.getClass().getSimpleName();
        }
        String results = prefs(context).getString(KEY_LOCK_MODE_RESULTS, "");
        String latest = "";
        if (results != null && results.length() > 0) {
            int end = results.indexOf('\n');
            latest = end >= 0 ? results.substring(0, end) : results;
            if (latest.length() > 160) {
                latest = latest.substring(0, 160);
            }
        }
        String cycle = getLockedCyclePackage(context);
        return "default=" + (defaultIme == null ? "none" : defaultIme)
                + " visible=" + visible
                + " locked=" + isLocked(context)
                + " cycle=" + (cycle == null ? "none" : cycle)
                + " last_result=" + latest;
    }

    private static boolean isUnlockImeSnapshotPhase(String phase) {
        if (phase == null) {
            return false;
        }
        return phase.startsWith("unlock")
                || "before_restore".equals(phase)
                || "before_overlay_off".equals(phase)
                || "overlay_off".equals(phase)
                || "post_restore_check".equals(phase);
    }

    private static String currentDefaultIme(Context context) {
        return Settings.Secure.getString(
                context.getContentResolver(),
                Settings.Secure.DEFAULT_INPUT_METHOD
        );
    }

    static boolean hasWriteSecureSettingsPermission(Context context) {
        return context.checkCallingOrSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS)
                == PackageManager.PERMISSION_GRANTED;
    }

    static int getSafeImeHiddenCount(Context context) {
        return prefs(context).getInt(KEY_SAFE_IME_HIDDEN, 0);
    }

    static int getSafeImeShownCount(Context context) {
        return prefs(context).getInt(KEY_SAFE_IME_SHOWN, 0);
    }

    static int getSafeImeHideButtonCount(Context context) {
        return prefs(context).getInt(KEY_SAFE_IME_HIDE_BUTTON, 0);
    }

    static int getSafeImeAutoRepeatHiddenCount(Context context) {
        return prefs(context).getInt(KEY_SAFE_IME_AUTO_REPEAT_HIDDEN, 0);
    }

    static int getSafeImeDefaultLostCount(Context context) {
        return prefs(context).getInt(KEY_SAFE_IME_DEFAULT_LOST, 0);
    }

    static int getSafeImeViewClickCount(Context context) {
        return prefs(context).getInt(KEY_SAFE_IME_VIEW_CLICK, 0);
    }

    static int getSafeImeManualClickShowCount(Context context) {
        return prefs(context).getInt(KEY_SAFE_IME_MANUAL_CLICK_SHOW, 0);
    }

    static int getSafeImeExplicitShowCount(Context context) {
        return prefs(context).getInt(KEY_SAFE_IME_EXPLICIT_SHOW, 0);
    }

    static int getSafeImeExplicitSuppressedNavigationCount(Context context) {
        return prefs(context).getInt(KEY_SAFE_IME_EXPLICIT_SUPPRESSED_NAV, 0);
    }

    static int getSafeImeExplicitSuppressedChurnCount(Context context) {
        return prefs(context).getInt(KEY_SAFE_IME_EXPLICIT_SUPPRESSED_CHURN, 0);
    }

    static int getSafeImeViewClickedIgnoredCount(Context context) {
        return prefs(context).getInt(KEY_SAFE_IME_VIEW_CLICK_IGNORED, 0);
    }

    static int getSafeImeStrictHiddenCount(Context context) {
        return prefs(context).getInt(KEY_SAFE_IME_STRICT_HIDDEN, 0);
    }

    static int getSafeImeExtractHiddenCount(Context context) {
        return prefs(context).getInt(KEY_SAFE_IME_EXTRACT_HIDDEN, 0);
    }

    static int getSafeImeClickSignalMissingCount(Context context) {
        return prefs(context).getInt(KEY_SAFE_IME_CLICK_SIGNAL_MISSING, 0);
    }

    static long getSafeImeLastViewClickAt(Context context) {
        return prefs(context).getLong(KEY_SAFE_IME_LAST_VIEW_CLICK_AT, 0L);
    }

    static String getSafeImeLastHideReason(Context context) {
        String value = prefs(context).getString(KEY_SAFE_IME_LAST_HIDE_REASON, null);
        return value == null || value.length() == 0 ? null : value;
    }

    static String getSafeImeLastField(Context context) {
        String value = prefs(context).getString(KEY_SAFE_IME_LAST_FIELD, null);
        return value == null || value.length() == 0 ? null : value;
    }

    static long getSafeImeLastAutoHideAt(Context context) {
        return prefs(context).getLong(KEY_SAFE_IME_LAST_AUTO_HIDE_AT, 0L);
    }

    static int getSafeImeStartInputCount(Context context) {
        return prefs(context).getInt(KEY_SAFE_IME_START_INPUT, 0);
    }

    static int getSafeImeStartInputViewCount(Context context) {
        return prefs(context).getInt(KEY_SAFE_IME_START_INPUT_VIEW, 0);
    }

    static int getSafeImeShowRequestCount(Context context) {
        return prefs(context).getInt(KEY_SAFE_IME_SHOW_REQUEST, 0);
    }

    static int getSafeImeLastShowFlags(Context context) {
        return prefs(context).getInt(KEY_SAFE_IME_LAST_SHOW_FLAGS, 0);
    }

    static boolean getSafeImeLastShowConfigChange(Context context) {
        return prefs(context).getBoolean(KEY_SAFE_IME_LAST_SHOW_CONFIG, false);
    }

    static int getSafeImeEvaluateFullscreenCount(Context context) {
        return prefs(context).getInt(KEY_SAFE_IME_EVALUATE_FULLSCREEN, 0);
    }

    static int getSafeImeCreateExtractCount(Context context) {
        return prefs(context).getInt(KEY_SAFE_IME_CREATE_EXTRACT, 0);
    }

    static int getSafeImeUpdateExtractVisibilityCount(Context context) {
        return prefs(context).getInt(KEY_SAFE_IME_UPDATE_EXTRACT_VISIBILITY, 0);
    }

    static int getSafeImeManualSignalAbsentCount(Context context) {
        return prefs(context).getInt(KEY_SAFE_IME_MANUAL_SIGNAL_ABSENT, 0);
    }

    static String getSafeImeLastCallback(Context context) {
        String value = prefs(context).getString(KEY_SAFE_IME_LAST_CALLBACK, null);
        return value == null || value.length() == 0 ? null : value;
    }

    static String getSafeImeLastCallbackDetail(Context context) {
        String value = prefs(context).getString(KEY_SAFE_IME_LAST_CALLBACK_DETAIL, null);
        return value == null || value.length() == 0 ? null : value;
    }

    static int getSafeImeA11yTapCount(Context context) {
        return prefs(context).getInt(KEY_SAFE_IME_A11Y_TAP_COUNT, 0);
    }

    static int getSafeImeA11yFocusCount(Context context) {
        return prefs(context).getInt(KEY_SAFE_IME_A11Y_FOCUS_COUNT, 0);
    }

    static int getSafeImeA11yConsumedCount(Context context) {
        return prefs(context).getInt(KEY_SAFE_IME_A11Y_CONSUMED_COUNT, 0);
    }

    static int getSafeImeA11yExpiredCount(Context context) {
        return prefs(context).getInt(KEY_SAFE_IME_A11Y_EXPIRED_COUNT, 0);
    }

    static int getSafeImeA11yNotSelectedCount(Context context) {
        return prefs(context).getInt(KEY_SAFE_IME_A11Y_NOT_SELECTED, 0);
    }

    static int getSafeImeTapServiceOffCount(Context context) {
        return prefs(context).getInt(KEY_SAFE_IME_TAP_SERVICE_OFF, 0);
    }

    static int getSafeImeCrashGuardCount(Context context) {
        return prefs(context).getInt(KEY_SAFE_IME_CRASH_GUARD, 0);
    }

    static String getSafeImeLastCrashSource(Context context) {
        String value = prefs(context).getString(KEY_SAFE_IME_LAST_CRASH_SOURCE, null);
        return value == null || value.length() == 0 ? null : value;
    }

    static String getSafeImeLastCrashError(Context context) {
        String value = prefs(context).getString(KEY_SAFE_IME_LAST_CRASH_ERROR, null);
        return value == null || value.length() == 0 ? null : value;
    }

    static String getSafeImeDefaultLostContext(Context context) {
        String value = prefs(context).getString(KEY_SAFE_IME_DEFAULT_LOST_CONTEXT, null);
        return value == null || value.length() == 0 ? null : value;
    }

    static String getSafeImeSnapshotBeforeLock(Context context) {
        String value = prefs(context).getString(KEY_SAFE_IME_SNAPSHOT_BEFORE_LOCK, null);
        return value == null || value.length() == 0 ? null : value;
    }

    static String getSafeImeLastSnapshot(Context context) {
        String value = prefs(context).getString(KEY_SAFE_IME_LAST_SNAPSHOT, null);
        return value == null || value.length() == 0 ? null : value;
    }

    static String getSafeImeSnapshotLog(Context context) {
        String value = prefs(context).getString(KEY_SAFE_IME_SNAPSHOT_LOG, null);
        return value == null || value.length() == 0 ? null : value;
    }

    static String getSafeImeLostPhase(Context context) {
        String value = prefs(context).getString(KEY_SAFE_IME_LOST_PHASE, null);
        return value == null || value.length() == 0 ? null : value;
    }

    static int getSafeImeLostDuringUnlockCount(Context context) {
        return prefs(context).getInt(KEY_SAFE_IME_LOST_DURING_UNLOCK, 0);
    }

    static int getSafeImeSilentRestoreCount(Context context) {
        return prefs(context).getInt(KEY_SAFE_IME_SILENT_RESTORE_COUNT, 0);
    }

    static String getSafeImeSilentRestoreLastResult(Context context) {
        String value = prefs(context).getString(KEY_SAFE_IME_SILENT_RESTORE_LAST_RESULT, null);
        return value == null || value.length() == 0 ? null : value;
    }

    static String getSafeImeRestoreAttemptPhase(Context context) {
        String value = prefs(context).getString(KEY_SAFE_IME_RESTORE_ATTEMPT_PHASE, null);
        return value == null || value.length() == 0 ? null : value;
    }

    static int getKeyboardHideUnlockCount(Context context) {
        return prefs(context).getInt(KEY_KEYBOARD_HIDE_UNLOCK_COUNT, 0);
    }

    static long getSafeImeA11yTapAt(Context context) {
        return prefs(context).getLong(KEY_SAFE_IME_A11Y_TAP_AT, 0L);
    }

    static String getSafeImeA11yTapPackage(Context context) {
        String value = prefs(context).getString(KEY_SAFE_IME_A11Y_TAP_PACKAGE, null);
        return value == null || value.length() == 0 ? null : value;
    }

    static String getSafeImeA11yTapField(Context context) {
        String value = prefs(context).getString(KEY_SAFE_IME_A11Y_TAP_FIELD, null);
        return value == null || value.length() == 0 ? null : value;
    }

    private static String incrementCompactCount(String raw, String key, int maxEntries) {
        String safeKey = sanitizeForLog(key == null || key.length() == 0 ? "unknown" : key);
        String[] labels = new String[Math.max(1, maxEntries)];
        int[] counts = new int[labels.length];
        int size = 0;
        boolean incremented = false;
        if (raw != null && raw.length() > 0) {
            String[] entries = raw.split(",");
            for (String entry : entries) {
                if (entry == null || entry.length() == 0 || size >= labels.length) {
                    continue;
                }
                int sep = entry.lastIndexOf('=');
                String label = sep <= 0 ? entry.trim() : entry.substring(0, sep).trim();
                if (label.length() == 0) {
                    continue;
                }
                int count = 0;
                if (sep > 0 && sep + 1 < entry.length()) {
                    try {
                        count = Integer.parseInt(entry.substring(sep + 1).trim());
                    } catch (NumberFormatException ignored) {
                        count = 0;
                    }
                }
                if (label.equals(safeKey)) {
                    count += 1;
                    incremented = true;
                }
                labels[size] = label;
                counts[size] = Math.max(0, count);
                size += 1;
            }
        }
        if (!incremented) {
            if (size < labels.length) {
                labels[size] = safeKey;
                counts[size] = 1;
                size += 1;
            } else {
                labels[labels.length - 1] = safeKey;
                counts[labels.length - 1] = 1;
            }
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < size; i++) {
            if (labels[i] == null || labels[i].length() == 0) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(',');
            }
            builder.append(labels[i]).append('=').append(counts[i]);
        }
        return builder.toString();
    }

    private static String sanitizeForLog(String value) {
        String safe = value == null ? "none" : value.replace('\n', ' ').replace('\r', ' ');
        if (safe.length() > 120) {
            safe = safe.substring(0, 120);
        }
        return safe;
    }

    private static String appendLine(String existing, String value) {
        if (value == null || value.length() == 0) {
            return existing == null ? "" : existing;
        }
        if (existing == null || existing.length() == 0) {
            return value;
        }
        return existing + "\n" + value;
    }

    static synchronized void recordCrash(Context context, Thread thread, Throwable error) {
        if (context == null) {
            return;
        }
        SharedPreferences preferences = prefs(context);
        int count = preferences.getInt(KEY_CRASH_COUNT, 0) + 1;
        String type = error == null ? "unknown" : error.getClass().getName();
        String message = error == null || error.getMessage() == null ? "" : error.getMessage();
        String stack = compactStack(error);
        preferences.edit()
                .putInt(KEY_CRASH_COUNT, count)
                .putLong(KEY_LAST_CRASH_AT, System.currentTimeMillis())
                .putString(KEY_LAST_CRASH_THREAD, thread == null ? "unknown" : thread.getName())
                .putString(KEY_LAST_CRASH_TYPE, type)
                .putString(KEY_LAST_CRASH_MESSAGE, message)
                .putString(KEY_LAST_CRASH_STACK, stack)
                .commit();
        appendLog(context, "watchguard_uncaught_crash count=" + count
                + " thread=" + sanitizeForLog(thread == null ? "unknown" : thread.getName())
                + " type=" + sanitizeForLog(type)
                + " message=" + sanitizeForLog(message));
    }

    static int getCrashCount(Context context) {
        return prefs(context).getInt(KEY_CRASH_COUNT, 0);
    }

    static long getLastCrashAt(Context context) {
        return prefs(context).getLong(KEY_LAST_CRASH_AT, 0L);
    }

    static String getLastCrashThread(Context context) {
        String value = prefs(context).getString(KEY_LAST_CRASH_THREAD, null);
        return value == null || value.length() == 0 ? null : value;
    }

    static String getLastCrashType(Context context) {
        String value = prefs(context).getString(KEY_LAST_CRASH_TYPE, null);
        return value == null || value.length() == 0 ? null : value;
    }

    static String getLastCrashMessage(Context context) {
        String value = prefs(context).getString(KEY_LAST_CRASH_MESSAGE, null);
        return value == null || value.length() == 0 ? null : value;
    }

    static String getLastCrashStack(Context context) {
        String value = prefs(context).getString(KEY_LAST_CRASH_STACK, null);
        return value == null || value.length() == 0 ? null : value;
    }

    private static String compactStack(Throwable error) {
        if (error == null) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        Throwable current = error;
        int causeDepth = 0;
        while (current != null && causeDepth < 2) {
            if (builder.length() > 0) {
                builder.append("\nCaused by: ");
            }
            builder.append(current.getClass().getName());
            if (current.getMessage() != null) {
                builder.append(": ").append(current.getMessage());
            }
            StackTraceElement[] stack = current.getStackTrace();
            int limit = Math.min(CRASH_STACK_LINES, stack == null ? 0 : stack.length);
            for (int i = 0; i < limit; i++) {
                builder.append("\n  at ").append(stack[i].toString());
            }
            current = current.getCause();
            causeDepth += 1;
        }
        String value = builder.toString();
        return value.length() > 3000 ? value.substring(0, 3000) : value;
    }

    private static String compactException(String source, Throwable error) {
        String type = error == null ? "unknown" : error.getClass().getSimpleName();
        String message = error == null || error.getMessage() == null ? "" : error.getMessage();
        String firstFrame = "";
        if (error != null && error.getStackTrace() != null && error.getStackTrace().length > 0) {
            firstFrame = " at " + error.getStackTrace()[0].toString();
        }
        String value = "source=" + (source == null ? "unknown" : source)
                + " error=" + type + ":" + message + firstFrame;
        return value.length() > 500 ? value.substring(0, 500) : value;
    }

    static boolean isButtonControlEnabled(Context context) {
        if (isHomeTrapEnabled(context)) {
            return true;
        }
        return prefs(context).getBoolean(KEY_BUTTON_CONTROL, false);
    }

    static void setButtonControlEnabled(Context context, boolean enabled) {
        prefs(context).edit()
                .putBoolean(KEY_BUTTON_CONTROL, enabled)
                .putBoolean(KEY_BUTTON_CONTROL_PAUSED, !enabled)
                .apply();
        appendLog(context, "button control " + (enabled ? "ON" : "OFF"));
    }

    static boolean isButtonControlPaused(Context context) {
        if (isHomeTrapEnabled(context)) {
            return false;
        }
        return prefs(context).getBoolean(KEY_BUTTON_CONTROL_PAUSED, false);
    }

    static void autoArmButtonControl(Context context, String reason) {
        SharedPreferences preferences = prefs(context);
        boolean paused = preferences.getBoolean(KEY_BUTTON_CONTROL_PAUSED, false);
        if (paused || preferences.getBoolean(KEY_BUTTON_CONTROL, false)) {
            return;
        }
        preferences.edit()
                .putBoolean(KEY_BUTTON_CONTROL, true)
                .putBoolean(KEY_BUTTON_CONTROL_PAUSED, false)
                .apply();
        appendLog(context, "button control AUTO ON: " + reason);
    }

    static boolean isKeyboardGuardEnabled(Context context) {
        return prefs(context).getBoolean(KEY_KEYBOARD_GUARD, true);
    }

    static void setKeyboardGuardEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_KEYBOARD_GUARD, enabled).apply();
        appendLog(context, "keyboard guard " + (enabled ? "ON" : "OFF"));
    }

    static void recordKeyboardGuardHide(Context context) {
        SharedPreferences preferences = prefs(context);
        int count = preferences.getInt(KEY_KEYBOARD_GUARD_HIDES, 0);
        preferences.edit().putInt(KEY_KEYBOARD_GUARD_HIDES, count + 1).apply();
    }

    static int getKeyboardGuardHideCount(Context context) {
        return prefs(context).getInt(KEY_KEYBOARD_GUARD_HIDES, 0);
    }

    static void recordKeeperTick(Context context, boolean accessibilityEnabled) {
        recordKeeperTick(context, accessibilityEnabled, isGptAutomationServiceAlive(context));
    }

    static void recordKeeperTick(Context context, boolean accessibilityEnabled, boolean accessibilityAlive) {
        SharedPreferences.Editor editor = prefs(context).edit()
                .putLong(KEY_KEEPER_LAST_TICK, System.currentTimeMillis());
        if (!accessibilityEnabled) {
            editor.putLong(KEY_ACCESSIBILITY_LAST_OFF, System.currentTimeMillis());
        }
        editor.apply();
    }

    static synchronized void recordKeeperStart(Context context, String reason) {
        prefs(context).edit()
                .putString(KEY_KEEPER_START_REASON, reason == null ? "" : reason)
                .apply();
        appendLog(context, "keeper_start reason=" + sanitizeForLog(reason));
    }

    static synchronized void recordKeeperTaskRemoved(Context context, String reason) {
        prefs(context).edit()
                .putLong(KEY_KEEPER_TASK_REMOVED, System.currentTimeMillis())
                .apply();
        appendLog(context, "keeper_on_task_removed reason=" + sanitizeForLog(reason));
    }

    static synchronized void recordKeeperRestart(Context context, String reason) {
        SharedPreferences preferences = prefs(context);
        int count = preferences.getInt(KEY_KEEPER_RESTART_COUNT, 0) + 1;
        preferences.edit()
                .putInt(KEY_KEEPER_RESTART_COUNT, count)
                .putString(KEY_KEEPER_START_REASON, reason == null ? "" : reason)
                .apply();
        appendLog(context, "keeper_restart_count count=" + count
                + " reason=" + sanitizeForLog(reason));
    }

    static long getKeeperLastTick(Context context) {
        return prefs(context).getLong(KEY_KEEPER_LAST_TICK, 0L);
    }

    static String getKeeperStartReason(Context context) {
        String value = prefs(context).getString(KEY_KEEPER_START_REASON, null);
        return value == null || value.length() == 0 ? null : value;
    }

    static int getKeeperRestartCount(Context context) {
        return prefs(context).getInt(KEY_KEEPER_RESTART_COUNT, 0);
    }

    static long getKeeperTaskRemovedAt(Context context) {
        return prefs(context).getLong(KEY_KEEPER_TASK_REMOVED, 0L);
    }

    static long getAccessibilityLastOff(Context context) {
        return prefs(context).getLong(KEY_ACCESSIBILITY_LAST_OFF, 0L);
    }

    static synchronized void recordGptAccessibilityServiceConnected(Context context) {
        long now = System.currentTimeMillis();
        prefs(context).edit()
                .putLong(KEY_GPT_ACCESSIBILITY_CONNECTED_AT, now)
                .putLong(KEY_GPT_ACCESSIBILITY_HEARTBEAT_AT, now)
                .commit();
        appendLog(context, "gpt_accessibility_service_connected");
    }

    static synchronized void recordGptAccessibilityServiceDestroyed(Context context) {
        prefs(context).edit()
                .putLong(KEY_GPT_ACCESSIBILITY_DESTROYED_AT, System.currentTimeMillis())
                .commit();
        appendLog(context, "gpt_accessibility_service_destroyed");
    }

    static synchronized void recordGptAccessibilityHeartbeat(Context context) {
        prefs(context).edit()
                .putLong(KEY_GPT_ACCESSIBILITY_HEARTBEAT_AT, System.currentTimeMillis())
                .commit();
    }

    static long getGptAccessibilityServiceConnectedAt(Context context) {
        return prefs(context).getLong(KEY_GPT_ACCESSIBILITY_CONNECTED_AT, 0L);
    }

    static long getGptAccessibilityServiceDestroyedAt(Context context) {
        return prefs(context).getLong(KEY_GPT_ACCESSIBILITY_DESTROYED_AT, 0L);
    }

    static long getGptAccessibilityHeartbeatAt(Context context) {
        return prefs(context).getLong(KEY_GPT_ACCESSIBILITY_HEARTBEAT_AT, 0L);
    }

    static long getGptAccessibilityHeartbeatAgeMs(Context context) {
        long at = getGptAccessibilityHeartbeatAt(context);
        if (at <= 0L) {
            return -1L;
        }
        return Math.max(0L, System.currentTimeMillis() - at);
    }

    static boolean isGptAutomationServiceAlive(Context context) {
        if (!isGptAutomationAccessibilityEnabled(context)) {
            return false;
        }
        long heartbeatAt = getGptAccessibilityHeartbeatAt(context);
        if (heartbeatAt <= 0L) {
            return false;
        }
        long age = System.currentTimeMillis() - heartbeatAt;
        if (age < 0L || age > GPT_ACCESSIBILITY_HEARTBEAT_STALE_MS) {
            return false;
        }
        long destroyedAt = getGptAccessibilityServiceDestroyedAt(context);
        long connectedAt = getGptAccessibilityServiceConnectedAt(context);
        return destroyedAt <= 0L || destroyedAt < heartbeatAt || destroyedAt < connectedAt;
    }

    static boolean isGptAutomationServiceReady(Context context) {
        return isGptAutomationAccessibilityEnabled(context)
                && isGptAutomationServiceAlive(context);
    }

    static synchronized void recordGptAccessibilityStale(Context context, String reason) {
        SharedPreferences preferences = prefs(context);
        int count = preferences.getInt(KEY_GPT_ACCESSIBILITY_STALE_COUNT, 0) + 1;
        preferences.edit()
                .putInt(KEY_GPT_ACCESSIBILITY_STALE_COUNT, count)
                .putLong(KEY_GPT_ACCESSIBILITY_STALE_AT, System.currentTimeMillis())
                .putString(KEY_GPT_ACCESSIBILITY_STALE_REASON, reason == null ? "" : reason)
                .apply();
        appendLog(context, "gpt_accessibility_stale count=" + count
                + " reason=" + sanitizeForLog(reason)
                + " heartbeat_age=" + getGptAccessibilityHeartbeatAgeMs(context));
    }

    static long getGptAccessibilityStaleAt(Context context) {
        return prefs(context).getLong(KEY_GPT_ACCESSIBILITY_STALE_AT, 0L);
    }

    static String getGptAccessibilityStaleReason(Context context) {
        String value = prefs(context).getString(KEY_GPT_ACCESSIBILITY_STALE_REASON, null);
        return value == null || value.length() == 0 ? null : value;
    }

    static int getGptAccessibilityStaleCount(Context context) {
        return prefs(context).getInt(KEY_GPT_ACCESSIBILITY_STALE_COUNT, 0);
    }

    static boolean isHomeTrapEnabled(Context context) {
        SharedPreferences preferences = prefs(context);
        boolean enabled = preferences.getBoolean(KEY_HOME_TRAP_ENABLED, false);
        long expires = preferences.getLong(KEY_HOME_TRAP_EXPIRES, 0L);
        if (enabled && expires > 0L && System.currentTimeMillis() > expires) {
            if (isModeUserDesiredEnabled(context)) {
                preferences.edit().remove(KEY_HOME_TRAP_EXPIRES).commit();
                appendLog(context, "home trap expiry ignored because mode desired");
                return true;
            }
            preferences.edit()
                    .putBoolean(KEY_HOME_TRAP_ENABLED, false)
                    .putBoolean(KEY_BUTTON_CONTROL, false)
                    .putBoolean(KEY_BUTTON_CONTROL_PAUSED, true)
                    .putString(KEY_MODE_LAST_DISABLED_REASON, "expired")
                    .putString(KEY_MODE_DISABLE_SOURCE, "expired")
                    .commit();
            appendLog(context, "home trap expired");
            return false;
        }
        return enabled;
    }

    static void setHomeTrapEnabled(Context context, boolean enabled) {
        SharedPreferences.Editor editor = prefs(context).edit()
                .putBoolean(KEY_HOME_TRAP_ENABLED, enabled)
                .putBoolean(KEY_BUTTON_CONTROL, enabled)
                .putBoolean(KEY_BUTTON_CONTROL_PAUSED, !enabled);
        if (enabled) {
            editor.remove(KEY_HOME_TRAP_EXPIRES);
        } else {
            editor.remove(KEY_HOME_TRAP_EXPIRES);
        }
        if (!enabled && isModeUserDesiredEnabled(context)) {
            editor.putString(KEY_MODE_LAST_DISABLED_REASON, "home_trap_off");
        }
        editor.commit();
        appendLog(context, "home trap " + (enabled ? "ON" : "OFF"));
    }

    static long getHomeTrapExpires(Context context) {
        return prefs(context).getLong(KEY_HOME_TRAP_EXPIRES, 0L);
    }

    static synchronized boolean markHomeTrapProcessed(Context context, String source) {
        SharedPreferences preferences = prefs(context);
        long now = System.currentTimeMillis();
        long last = preferences.getLong(KEY_HOME_TRAP_LAST_PROCESSED, 0L);
        if (last > 0L && now - last >= 0L && now - last < HOME_TRAP_DEBOUNCE_MS) {
            int ignored = preferences.getInt(KEY_HOME_TRAP_IGNORED, 0) + 1;
            preferences.edit().putInt(KEY_HOME_TRAP_IGNORED, ignored).apply();
            appendLog(context, "home trap debounce ignored " + source + " delta=" + (now - last));
            return false;
        }
        preferences.edit().putLong(KEY_HOME_TRAP_LAST_PROCESSED, now).apply();
        return true;
    }

    static int getHomeTrapIgnored(Context context) {
        return prefs(context).getInt(KEY_HOME_TRAP_IGNORED, 0);
    }

    static boolean isHomeTrapSessionActive(Context context) {
        return prefs(context).getBoolean(KEY_HOME_TRAP_SESSION_ACTIVE, false);
    }

    static void setHomeTrapSessionActive(Context context, boolean active) {
        prefs(context).edit().putBoolean(KEY_HOME_TRAP_SESSION_ACTIVE, active).apply();
        appendLog(context, "home trap session " + (active ? "ACTIVE" : "OFF"));
    }

    static synchronized long nextHomeTrapCycle(Context context, String reason) {
        SharedPreferences preferences = prefs(context);
        long next = preferences.getLong(KEY_HOME_TRAP_CYCLE_ID, 0L) + 1L;
        preferences.edit().putLong(KEY_HOME_TRAP_CYCLE_ID, next).apply();
        appendLog(context, "home trap cycle id=" + next + " reason=" + reason);
        return next;
    }

    static synchronized long invalidateHomeTrapCycle(Context context, String reason) {
        SharedPreferences preferences = prefs(context);
        long next = preferences.getLong(KEY_HOME_TRAP_CYCLE_ID, 0L) + 1L;
        preferences.edit().putLong(KEY_HOME_TRAP_CYCLE_ID, next).apply();
        appendLog(context, "home trap cycle invalidated id=" + next + " reason=" + reason);
        return next;
    }

    static long getHomeTrapCycleId(Context context) {
        return prefs(context).getLong(KEY_HOME_TRAP_CYCLE_ID, 0L);
    }

    static void clearHomeTrapCycle(Context context, long expectedCycleId, String reason) {
        SharedPreferences preferences = prefs(context);
        long current = preferences.getLong(KEY_HOME_TRAP_CYCLE_ID, 0L);
        if (expectedCycleId > 0L && current != expectedCycleId) {
            appendLog(context, "home trap cycle clear skipped expected=" + expectedCycleId
                    + " current=" + current
                    + " reason=" + reason);
            return;
        }
        long next = current + 1L;
        preferences.edit()
                .putLong(KEY_HOME_TRAP_CYCLE_ID, next)
                .remove(KEY_LOCKED_CYCLE_PACKAGE)
                .remove(KEY_LOCKED_CYCLE_IS_MENU)
                .remove(KEY_UNLOCK_SETTLE_UNTIL)
                .remove(KEY_LOCK_SETTLE_UNTIL)
                .remove(KEY_LOCK_STABILIZE_RESTORE_COUNT)
                .apply();
        appendLog(context, "home trap cycle cleared reason=" + reason + " next_id=" + next);
    }

    static synchronized void resetHomeTrapRuntime(Context context, String reason) {
        SharedPreferences preferences = prefs(context);
        long next = preferences.getLong(KEY_HOME_TRAP_CYCLE_ID, 0L) + 1L;
        preferences.edit()
                .putBoolean(KEY_LOCKED, false)
                .putBoolean(KEY_HOME_TRAP_SESSION_ACTIVE, false)
                .putLong(KEY_HOME_TRAP_CYCLE_ID, next)
                .remove(KEY_LOCKED_CYCLE_PACKAGE)
                .remove(KEY_LOCKED_CYCLE_IS_MENU)
                .remove(KEY_LOCKED_AT)
                .remove(KEY_UNLOCK_ALLOWED_AT)
                .remove(KEY_UNLOCK_SETTLE_UNTIL)
                .remove(KEY_LOCK_SETTLE_UNTIL)
                .remove(KEY_LOCK_STABILIZE_RESTORE_COUNT)
                .remove(KEY_SAFE_IME_LOCK_DEFAULT)
                .remove(KEY_SAFE_IME_LOCK_SELECTED)
                .remove(KEY_SAFE_IME_LOST_PHASE)
                .remove(KEY_SAFE_IME_LOST_RECORDED_CYCLE)
                .remove(KEY_SAFE_IME_SILENT_RESTORE_SKIP_CYCLE)
                .remove(KEY_LAST_CACHED_APP)
                .remove(KEY_LAST_CACHED_APP_AT)
                .remove(KEY_LAST_CACHED_APP_SOURCE)
                .remove(KEY_LAST_MENU_SEEN_AFTER_APP)
                .remove(KEY_LAST_MENU_SEEN_AFTER_APP_AT)
                .remove(KEY_LAST_NAVIGATION_SEEN_AFTER_APP)
                .remove(KEY_LAST_NAVIGATION_SEEN_AFTER_APP_AT)
                .remove(KEY_LAST_NAVIGATION_SOURCE)
                .remove(KEY_HOME_TRAP_LAST_PROCESSED)
                .remove(KEY_HOME_TRAP_LAST_GAP)
                .remove(KEY_HOME_STORM_COUNT)
                .remove(KEY_HOME_STORM_STARTED)
                .remove(KEY_HOME_STORM_RESCUES)
                .remove(KEY_LOCKED_HOME_BURST_IGNORED)
                .remove(KEY_SCREEN_OFF_RECOVERY_PACKAGE)
                .remove(KEY_SCREEN_OFF_RECOVERY_UNTIL)
                .remove(KEY_LAST_HOME_DELEGATE)
                .putBoolean(KEY_WATCH_CAMERA_ACTIVE, false)
                .remove(KEY_WATCH_CAMERA_HOME_BRIDGE_UNTIL)
                .remove(KEY_WATCH_CAMERA_WARM_BRIDGE_UNTIL)
                .remove(KEY_WATCH_CAMERA_INPUT_NOISE_UNTIL)
                .putBoolean(KEY_WATCH_CAMERA_CAPTURE_IN_FLIGHT, false)
                .putBoolean(KEY_WATCH_CAMERA_PENDING_SHOT, false)
                .apply();
        appendLog(context, "home trap runtime reset reason=" + reason + " next_id=" + next);
    }

    static synchronized int recordHomeStormPulse(Context context) {
        SharedPreferences preferences = prefs(context);
        long now = System.currentTimeMillis();
        long started = preferences.getLong(KEY_HOME_STORM_STARTED, 0L);
        int count = preferences.getInt(KEY_HOME_STORM_COUNT, 0);
        if (started <= 0L || now - started < 0L || now - started > HOME_STORM_WINDOW_MS) {
            started = now;
            count = 1;
        } else {
            count++;
        }
        preferences.edit()
                .putLong(KEY_HOME_STORM_STARTED, started)
                .putInt(KEY_HOME_STORM_COUNT, count)
                .apply();
        return count;
    }

    static boolean isHomeStormThresholdReached(Context context) {
        return prefs(context).getInt(KEY_HOME_STORM_COUNT, 0) >= HOME_STORM_THRESHOLD;
    }

    static synchronized void clearHomeStorm(Context context, String reason) {
        prefs(context).edit()
                .remove(KEY_HOME_STORM_COUNT)
                .remove(KEY_HOME_STORM_STARTED)
                .apply();
        appendLog(context, "home storm cleared reason=" + reason);
    }

    static synchronized void recordHomeStormRescue(Context context, String reason) {
        SharedPreferences preferences = prefs(context);
        int rescues = preferences.getInt(KEY_HOME_STORM_RESCUES, 0) + 1;
        preferences.edit()
                .putInt(KEY_HOME_STORM_RESCUES, rescues)
                .remove(KEY_HOME_STORM_COUNT)
                .remove(KEY_HOME_STORM_STARTED)
                .apply();
        appendLog(context, "home_storm_rescue reason=" + reason + " total=" + rescues);
    }

    static int getHomeStormCount(Context context) {
        return prefs(context).getInt(KEY_HOME_STORM_COUNT, 0);
    }

    static int getHomeStormRescues(Context context) {
        return prefs(context).getInt(KEY_HOME_STORM_RESCUES, 0);
    }

    static void recordHomeDelegate(Context context, String packageName) {
        prefs(context).edit()
                .putString(KEY_LAST_HOME_DELEGATE, packageName == null ? "" : packageName)
                .apply();
        appendLog(context, "home_delegate package=" + (packageName == null ? "none" : packageName));
    }

    static String getLastHomeDelegate(Context context) {
        String value = prefs(context).getString(KEY_LAST_HOME_DELEGATE, null);
        return value == null || value.length() == 0 ? null : value;
    }

    static void setHomeTrapTarget(Context context, String packageName, String source) {
        if (packageName == null || packageName.length() == 0 || context.getPackageName().equals(packageName)) {
            return;
        }
        prefs(context).edit()
                .putString(KEY_HOME_TRAP_TARGET, packageName)
                .putString(KEY_HOME_TRAP_TARGET_SOURCE, source == null ? "" : source)
                .apply();
        appendLog(context, "home trap target=" + packageName + " source=" + source);
    }

    static String getHomeTrapTarget(Context context) {
        return prefs(context).getString(KEY_HOME_TRAP_TARGET, null);
    }

    static String getHomeTrapTargetSource(Context context) {
        return prefs(context).getString(KEY_HOME_TRAP_TARGET_SOURCE, "");
    }

    static void setHomeTrapLockTarget(Context context, String packageName) {
        SharedPreferences.Editor editor = prefs(context).edit();
        if (packageName == null || packageName.length() == 0) {
            editor.remove(KEY_HOME_TRAP_LOCK_TARGET);
        } else {
            editor.putString(KEY_HOME_TRAP_LOCK_TARGET, packageName);
        }
        editor.apply();
        appendLog(context, "home trap lock target=" + (packageName == null ? "none" : packageName));
    }

    static String getHomeTrapLockTarget(Context context) {
        return prefs(context).getString(KEY_HOME_TRAP_LOCK_TARGET, null);
    }

    static void setLockedCycle(Context context, String packageName, boolean isMenu) {
        SharedPreferences.Editor editor = prefs(context).edit();
        if (packageName == null || packageName.length() == 0) {
            editor.remove(KEY_LOCKED_CYCLE_PACKAGE);
        } else {
            editor.putString(KEY_LOCKED_CYCLE_PACKAGE, packageName);
        }
        editor.putBoolean(KEY_LOCKED_CYCLE_IS_MENU, isMenu);
        editor.apply();
        appendLog(context, "locked cycle=" + (isMenu ? "menu" : packageName == null ? "none" : packageName));
    }

    static String getLockedCyclePackage(Context context) {
        return prefs(context).getString(KEY_LOCKED_CYCLE_PACKAGE, null);
    }

    static boolean isLockedCycleMenu(Context context) {
        return prefs(context).getBoolean(KEY_LOCKED_CYCLE_IS_MENU, false);
    }

    static void clearLockedCycle(Context context) {
        prefs(context).edit()
                .remove(KEY_LOCKED_CYCLE_PACKAGE)
                .remove(KEY_LOCKED_CYCLE_IS_MENU)
                .apply();
        appendLog(context, "locked cycle cleared");
    }

    static void clearHomeTrapLegacyTargets(Context context) {
        prefs(context).edit()
                .remove(KEY_HOME_TRAP_TARGET)
                .remove(KEY_HOME_TRAP_TARGET_SOURCE)
                .remove(KEY_HOME_TRAP_LOCK_TARGET)
                .remove(KEY_LOCKED_CYCLE_PACKAGE)
                .remove(KEY_LOCKED_CYCLE_IS_MENU)
                .remove(KEY_LOCKED_AT)
                .remove(KEY_UNLOCK_ALLOWED_AT)
                .remove(KEY_UNLOCK_SETTLE_UNTIL)
                .remove(KEY_LOCK_SETTLE_UNTIL)
                .remove(KEY_LOCK_STABILIZE_RESTORE_COUNT)
                .remove(KEY_HOME_TRAP_ARMED_AT)
                .remove(KEY_HOME_TRAP_LAST_PROCESSED)
                .remove(KEY_HOME_TRAP_LAST_GAP)
                .remove(KEY_SCREEN_OFF_RECOVERY_PACKAGE)
                .remove(KEY_SCREEN_OFF_RECOVERY_UNTIL)
                .remove(KEY_WATCH_CAMERA_HOME_BRIDGE_UNTIL)
                .remove(KEY_WATCH_CAMERA_WARM_BRIDGE_UNTIL)
                .remove(KEY_WATCH_CAMERA_INPUT_NOISE_UNTIL)
                .putBoolean(KEY_WATCH_CAMERA_CAPTURE_IN_FLIGHT, false)
                .putBoolean(KEY_WATCH_CAMERA_PENDING_SHOT, false)
                .apply();
        invalidateHomeTrapCycle(context, "legacy_targets_cleared");
        appendLog(context, "home trap legacy targets cleared");
    }

    static void markHomeTrapArmed(Context context) {
        long now = System.currentTimeMillis();
        prefs(context).edit().putLong(KEY_HOME_TRAP_ARMED_AT, now).commit();
        appendLog(context, "home trap armed at=" + now);
    }

    static long getHomeTrapArmedAt(Context context) {
        return prefs(context).getLong(KEY_HOME_TRAP_ARMED_AT, 0L);
    }

    static boolean isHomeTrapArmGraceActive(Context context, long durationMs) {
        long armedAt = getHomeTrapArmedAt(context);
        return armedAt > 0L && System.currentTimeMillis() - armedAt >= 0L
                && System.currentTimeMillis() - armedAt < durationMs;
    }

    static void setStayAwakeEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_STAY_AWAKE, enabled).commit();
        appendLog(context, "stay awake " + (enabled ? "ON" : "OFF"));
    }

    static boolean isStayAwakeEnabled(Context context) {
        return prefs(context).getBoolean(KEY_STAY_AWAKE, false);
    }

    static void recordControlUiPaused(Context context) {
        prefs(context).edit().putLong(KEY_CONTROL_UI_PAUSED_AT, System.currentTimeMillis()).apply();
    }

    static boolean wasControlUiPausedRecently(Context context, long windowMs) {
        long pausedAt = prefs(context).getLong(KEY_CONTROL_UI_PAUSED_AT, 0L);
        return pausedAt > 0L && System.currentTimeMillis() - pausedAt >= 0L
                && System.currentTimeMillis() - pausedAt < windowMs;
    }

    static void setHomeTrapMenuGrace(Context context, long durationMs) {
        long until = durationMs <= 0L ? 0L : System.currentTimeMillis() + durationMs;
        SharedPreferences.Editor editor = prefs(context).edit();
        if (until <= 0L) {
            editor.remove(KEY_HOME_TRAP_MENU_GRACE_UNTIL);
        } else {
            editor.putLong(KEY_HOME_TRAP_MENU_GRACE_UNTIL, until);
        }
        editor.apply();
        appendLog(context, "home trap menu grace until=" + until);
    }

    static boolean isHomeTrapMenuGraceActive(Context context) {
        long until = prefs(context).getLong(KEY_HOME_TRAP_MENU_GRACE_UNTIL, 0L);
        return until > 0L && System.currentTimeMillis() < until;
    }

    static void emergencyReset(Context context, String reason) {
        prefs(context).edit()
                .putBoolean(KEY_LOCKED, false)
                .putBoolean(KEY_HOME_TRAP_ENABLED, false)
                .putBoolean(KEY_MODE_USER_DESIRED, false)
                .putBoolean(KEY_HOME_TRAP_SESSION_ACTIVE, false)
                .putBoolean(KEY_BUTTON_CONTROL, false)
                .putBoolean(KEY_BUTTON_CONTROL_PAUSED, true)
                .putBoolean(KEY_STAY_AWAKE, false)
                .remove(KEY_MODE_ENABLED_AT)
                .putString(KEY_MODE_LAST_DISABLED_REASON,
                        "emergency:" + sanitizeForLog(reason))
                .putString(KEY_MODE_DISABLE_SOURCE,
                        "emergency:" + sanitizeForLog(reason))
                .remove(KEY_HOME_TRAP_EXPIRES)
                .remove(KEY_HOME_TRAP_LAST_PROCESSED)
                .remove(KEY_HOME_TRAP_LAST_GAP)
                .remove(KEY_HOME_TRAP_TARGET)
                .remove(KEY_HOME_TRAP_TARGET_SOURCE)
                .remove(KEY_HOME_TRAP_LOCK_TARGET)
                .remove(KEY_LOCKED_CYCLE_PACKAGE)
                .remove(KEY_LOCKED_CYCLE_IS_MENU)
                .remove(KEY_LOCKED_AT)
                .remove(KEY_UNLOCK_ALLOWED_AT)
                .remove(KEY_UNLOCK_SETTLE_UNTIL)
                .remove(KEY_LOCK_SETTLE_UNTIL)
                .remove(KEY_LOCK_STABILIZE_RESTORE_COUNT)
                .remove(KEY_HOME_TRAP_ARMED_AT)
                .remove(KEY_HOME_TRAP_MENU_GRACE_UNTIL)
                .remove(KEY_HOME_STORM_COUNT)
                .remove(KEY_HOME_STORM_STARTED)
                .remove(KEY_HOME_STORM_RESCUES)
                .remove(KEY_LOCKED_HOME_BURST_IGNORED)
                .remove(KEY_SCREEN_OFF_RECOVERY_PACKAGE)
                .remove(KEY_SCREEN_OFF_RECOVERY_UNTIL)
                .remove(KEY_LAST_HOME_DELEGATE)
                .putBoolean(KEY_WATCH_CAMERA_ACTIVE, false)
                .remove(KEY_WATCH_CAMERA_HOME_BRIDGE_UNTIL)
                .remove(KEY_WATCH_CAMERA_WARM_BRIDGE_UNTIL)
                .remove(KEY_WATCH_CAMERA_INPUT_NOISE_UNTIL)
                .remove(KEY_WATCH_CAMERA_PENDING_CAPTURE_COUNT)
                .remove(KEY_GPT_SHARE_PENDING)
                .remove(KEY_GPT_SHARE_SESSION_ID)
                .remove(KEY_GPT_SHARE_PROMPT)
                .remove(KEY_GPT_SHARE_ALL_PHOTOS)
                .remove(KEY_GPT_SHARE_CHUNK_INDEX)
                .remove(KEY_GPT_SHARE_CHUNK_COUNT)
                .remove(KEY_GPT_SHARE_CHUNK_SIZE)
                .remove(KEY_GPT_SHARE_NEEDS_NEW_CHAT)
                .remove(KEY_GPT_SHARE_TARGET_MODE)
                .remove(KEY_GPT_SHARE_TARGET_FALLBACK_REASON)
                .remove(KEY_GPT_AUTOMATION_PHASE)
                .remove(KEY_GPT_AUTOMATION_GATE_REASON)
                .remove(KEY_GPT_AUTOMATION_ATTACHMENT_EXPECTED)
                .remove(KEY_GPT_AUTOMATION_ATTACHMENT_EVIDENCE)
                .remove(KEY_GPT_AUTOMATION_ATTACHMENT_STREAK)
                .remove(KEY_GPT_AUTOMATION_CHUNK_LAUNCHED_AT)
                .remove(KEY_GPT_AUTOMATION_SAFE_SEND_CANDIDATE)
                .remove(KEY_GPT_AUTOMATION_SEND_CANDIDATE_SOURCE)
                .remove(KEY_GPT_AUTOMATION_SEND_CLICK_AT)
                .remove(KEY_GPT_AUTOMATION_ATTACHMENT_REQUIRED)
                .remove(KEY_GPT_AUTOMATION_ROOT_SOURCE)
                .remove(KEY_GPT_AUTOMATION_ROOT_PACKAGE)
                .remove(KEY_GPT_AUTOMATION_LOCKED_OVERLAY_MODE)
                .remove(KEY_GPT_AUTO_LOCK_REQUESTED)
                .remove(KEY_GPT_AUTO_LOCK_ACTIVE)
                .remove(KEY_GPT_AUTO_LOCK_STATE)
                .remove(KEY_GPT_AUTO_LOCK_STARTED_AT)
                .putBoolean(KEY_WATCH_CAMERA_CAPTURE_IN_FLIGHT, false)
                .putBoolean(KEY_WATCH_CAMERA_PENDING_SHOT, false)
                .commit();
        invalidateHomeTrapCycle(context, "emergency_reset");
        appendLog(context, "EMERGENCY RESET: " + reason);
    }

    static void uiRescue(Context context, String reason) {
        prefs(context).edit()
                .putBoolean(KEY_LOCKED, false)
                .putBoolean(KEY_HOME_TRAP_SESSION_ACTIVE, false)
                .putBoolean(KEY_BUTTON_CONTROL, false)
                .putBoolean(KEY_BUTTON_CONTROL_PAUSED, true)
                .putBoolean(KEY_STAY_AWAKE, false)
                .remove(KEY_HOME_TRAP_LAST_PROCESSED)
                .remove(KEY_HOME_TRAP_LAST_GAP)
                .remove(KEY_HOME_TRAP_TARGET)
                .remove(KEY_HOME_TRAP_TARGET_SOURCE)
                .remove(KEY_HOME_TRAP_LOCK_TARGET)
                .remove(KEY_LOCKED_CYCLE_PACKAGE)
                .remove(KEY_LOCKED_CYCLE_IS_MENU)
                .remove(KEY_LOCKED_AT)
                .remove(KEY_UNLOCK_ALLOWED_AT)
                .remove(KEY_UNLOCK_SETTLE_UNTIL)
                .remove(KEY_LOCK_SETTLE_UNTIL)
                .remove(KEY_LOCK_STABILIZE_RESTORE_COUNT)
                .remove(KEY_HOME_TRAP_ARMED_AT)
                .remove(KEY_HOME_TRAP_MENU_GRACE_UNTIL)
                .remove(KEY_HOME_STORM_COUNT)
                .remove(KEY_HOME_STORM_STARTED)
                .remove(KEY_HOME_STORM_RESCUES)
                .remove(KEY_LOCKED_HOME_BURST_IGNORED)
                .remove(KEY_SCREEN_OFF_RECOVERY_PACKAGE)
                .remove(KEY_SCREEN_OFF_RECOVERY_UNTIL)
                .remove(KEY_LAST_HOME_DELEGATE)
                .putBoolean(KEY_WATCH_CAMERA_ACTIVE, false)
                .remove(KEY_WATCH_CAMERA_HOME_BRIDGE_UNTIL)
                .remove(KEY_WATCH_CAMERA_WARM_BRIDGE_UNTIL)
                .remove(KEY_WATCH_CAMERA_INPUT_NOISE_UNTIL)
                .remove(KEY_WATCH_CAMERA_PENDING_CAPTURE_COUNT)
                .remove(KEY_GPT_SHARE_PENDING)
                .remove(KEY_GPT_SHARE_SESSION_ID)
                .remove(KEY_GPT_SHARE_PROMPT)
                .remove(KEY_GPT_SHARE_ALL_PHOTOS)
                .remove(KEY_GPT_SHARE_CHUNK_INDEX)
                .remove(KEY_GPT_SHARE_CHUNK_COUNT)
                .remove(KEY_GPT_SHARE_CHUNK_SIZE)
                .remove(KEY_GPT_SHARE_NEEDS_NEW_CHAT)
                .remove(KEY_GPT_SHARE_TARGET_MODE)
                .remove(KEY_GPT_SHARE_TARGET_FALLBACK_REASON)
                .remove(KEY_GPT_AUTOMATION_PHASE)
                .remove(KEY_GPT_AUTOMATION_GATE_REASON)
                .remove(KEY_GPT_AUTOMATION_ATTACHMENT_EXPECTED)
                .remove(KEY_GPT_AUTOMATION_ATTACHMENT_EVIDENCE)
                .remove(KEY_GPT_AUTOMATION_ATTACHMENT_STREAK)
                .remove(KEY_GPT_AUTOMATION_CHUNK_LAUNCHED_AT)
                .remove(KEY_GPT_AUTOMATION_SAFE_SEND_CANDIDATE)
                .remove(KEY_GPT_AUTOMATION_SEND_CANDIDATE_SOURCE)
                .remove(KEY_GPT_AUTOMATION_SEND_CLICK_AT)
                .remove(KEY_GPT_AUTOMATION_ATTACHMENT_REQUIRED)
                .remove(KEY_GPT_AUTOMATION_ROOT_SOURCE)
                .remove(KEY_GPT_AUTOMATION_ROOT_PACKAGE)
                .remove(KEY_GPT_AUTOMATION_LOCKED_OVERLAY_MODE)
                .remove(KEY_GPT_AUTO_LOCK_REQUESTED)
                .remove(KEY_GPT_AUTO_LOCK_ACTIVE)
                .remove(KEY_GPT_AUTO_LOCK_STATE)
                .remove(KEY_GPT_AUTO_LOCK_STARTED_AT)
                .putBoolean(KEY_WATCH_CAMERA_CAPTURE_IN_FLIGHT, false)
                .putBoolean(KEY_WATCH_CAMERA_PENDING_SHOT, false)
                .apply();
        invalidateHomeTrapCycle(context, "ui_rescue");
        appendLog(context, "UI RESCUE: " + reason);
    }

    static synchronized long recordHomeTrapInvocation(Context context) {
        SharedPreferences preferences = prefs(context);
        int count = preferences.getInt(KEY_HOME_TRAP_INVOCATIONS, 0);
        long now = System.currentTimeMillis();
        long previous = preferences.getLong(KEY_HOME_TRAP_LAST, 0L);
        long gap = previous <= 0L ? -1L : Math.max(0L, now - previous);
        preferences.edit()
                .putInt(KEY_HOME_TRAP_INVOCATIONS, count + 1)
                .putLong(KEY_HOME_TRAP_LAST, now)
                .putLong(KEY_HOME_TRAP_LAST_GAP, gap)
                .apply();
        appendLog(context, "home trap invoked count=" + (count + 1)
                + " gap=" + gap);
        return gap;
    }

    static int getHomeTrapInvocations(Context context) {
        return prefs(context).getInt(KEY_HOME_TRAP_INVOCATIONS, 0);
    }

    static long getHomeTrapLast(Context context) {
        return prefs(context).getLong(KEY_HOME_TRAP_LAST, 0L);
    }

    static long getHomeTrapLastGap(Context context) {
        return prefs(context).getLong(KEY_HOME_TRAP_LAST_GAP, -1L);
    }

    static synchronized void appendLog(Context context, String message) {
        String stamp = new SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US).format(new Date());
        SharedPreferences preferences = prefs(context);
        String oldLog = preferences.getString(KEY_LOG, "");
        String nextLog = stamp + "  " + message + "\n" + oldLog;
        if (nextLog.length() > MAX_LOG_CHARS) {
            nextLog = nextLog.substring(0, MAX_LOG_CHARS);
        }
        Log.i("WatchGuard", message);
        preferences.edit().putString(KEY_LOG, nextLog).apply();
    }

    static synchronized void appendKeyEvent(
            Context context,
            String action,
            String keyName,
            int keyCode,
            int scanCode,
            int repeatCount,
            int deviceId
    ) {
        String stamp = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US)
                .format(new Date());
        String event = "{\"time\":\"" + stamp
                + "\",\"action\":\"" + action
                + "\",\"key_name\":\"" + keyName
                + "\",\"key_code\":" + keyCode
                + ",\"scan_code\":" + scanCode
                + ",\"repeat_count\":" + repeatCount
                + ",\"device_id\":" + deviceId
                + "}";
        SharedPreferences preferences = prefs(context);
        String oldEvents = preferences.getString(KEY_EVENTS, "");
        String nextEvents = event + "\n" + oldEvents;
        if (nextEvents.length() > MAX_KEY_EVENTS_CHARS) {
            nextEvents = trimAtLineBoundary(nextEvents, MAX_KEY_EVENTS_CHARS);
        }
        preferences.edit().putString(KEY_EVENTS, nextEvents).apply();
    }

    static String getLog(Context context) {
        return prefs(context).getString(KEY_LOG, "");
    }

    static String getKeyEvents(Context context) {
        return prefs(context).getString(KEY_EVENTS, "");
    }

    static synchronized void appendLockModeResult(
            Context context,
            String mode,
            String outcome,
            String detail
    ) {
        try {
            JSONObject object = new JSONObject();
            object.put("time", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US)
                    .format(new Date()));
            object.put("mode", mode);
            object.put("outcome", outcome);
            object.put("detail", detail == null ? "" : detail);
            SharedPreferences preferences = prefs(context);
            String oldResults = preferences.getString(KEY_LOCK_MODE_RESULTS, "");
            String nextResults = object.toString() + "\n" + oldResults;
            if (nextResults.length() > MAX_LOCK_MODE_CHARS) {
                nextResults = trimAtLineBoundary(nextResults, MAX_LOCK_MODE_CHARS);
            }
            preferences.edit().putString(KEY_LOCK_MODE_RESULTS, nextResults).apply();
        } catch (JSONException error) {
            appendLog(context, "lock result json failed: " + error.getMessage());
        }
    }

    static String getLockModeResults(Context context) {
        return prefs(context).getString(KEY_LOCK_MODE_RESULTS, "");
    }

    private static String trimAtLineBoundary(String value, int maxChars) {
        if (value == null || value.length() <= maxChars) {
            return value == null ? "" : value;
        }
        int cut = value.lastIndexOf('\n', Math.max(0, maxChars - 1));
        if (cut <= 0) {
            return value.substring(0, maxChars);
        }
        return value.substring(0, cut + 1);
    }

    static void clearLog(Context context) {
        prefs(context).edit()
                .remove(KEY_LOG)
                .remove(KEY_EVENTS)
                .remove(KEY_LOCK_MODE_RESULTS)
                .remove(KEY_KEYBOARD_GUARD_HIDES)
                .remove(KEY_HOME_TRAP_INVOCATIONS)
                .remove(KEY_HOME_TRAP_LAST)
                .remove(KEY_HOME_TRAP_IGNORED)
                .remove(KEY_HOME_TRAP_LAST_PROCESSED)
                .remove(KEY_HOME_TRAP_LAST_GAP)
                .remove(KEY_HOME_STORM_COUNT)
                .remove(KEY_HOME_STORM_STARTED)
                .remove(KEY_HOME_STORM_RESCUES)
                .remove(KEY_LOCKED_HOME_BURST_IGNORED)
                .remove(KEY_LOCK_STABILIZE_RESTORE_COUNT)
                .remove(KEY_SCREEN_OFF_RECOVERY_COUNT)
                .remove(KEY_SCREEN_OFF_RECOVERY_PACKAGE)
                .remove(KEY_SCREEN_OFF_RECOVERY_UNTIL)
                .remove(KEY_CAMERA_SHUTTER_LAST_AT)
                .remove(KEY_CAMERA_SHUTTER_LAST_PACKAGE)
                .remove(KEY_CAMERA_SHUTTER_ACCEPTED)
                .remove(KEY_CAMERA_SHUTTER_THROTTLED)
                .remove(KEY_CAMERA_SHUTTER_BACKEND_RESULTS)
                .remove(KEY_CAMERA_TAP_BACKEND)
                .remove(KEY_CAMERA_TAP_COUNT)
                .remove(KEY_CAMERA_TAP_FAILED_COUNT)
                .remove(KEY_CAMERA_TAP_LAST_X)
                .remove(KEY_CAMERA_TAP_LAST_Y)
                .remove(KEY_WATCH_CAMERA_LAST_SHOT_AT)
                .remove(KEY_WATCH_CAMERA_PHOTO_COUNT)
                .remove(KEY_WATCH_CAMERA_THROTTLED)
                .remove(KEY_WATCH_CAMERA_LAST_FILE)
                .remove(KEY_WATCH_CAMERA_LAST_ERROR)
                .remove(KEY_WATCH_CAMERA_BACKEND)
                .remove(KEY_WATCH_CAMERA_STORAGE_BACKEND)
                .remove(KEY_WATCH_CAMERA_PUBLIC_FALLBACKS)
                .remove(KEY_WATCH_CAMERA_WARM_BRIDGE_UNTIL)
                .remove(KEY_WATCH_CAMERA_CAPTURE_IN_FLIGHT)
                .remove(KEY_WATCH_CAMERA_PENDING_SHOT)
                .remove(KEY_WATCH_CAMERA_GENERATION)
                .remove(KEY_WATCH_CAMERA_WARM_CLOSES)
                .remove(KEY_WATCH_CAMERA_SESSION_ID)
                .remove(KEY_WATCH_CAMERA_SESSION_STARTED)
                .remove(KEY_WATCH_CAMERA_SESSION_PHOTOS)
                .remove(KEY_WATCH_CAMERA_SESSION_PHOTO_COUNT)
                .remove(KEY_WATCH_CAMERA_PENDING_CAPTURE_COUNT)
                .remove(KEY_WATCH_CAMERA_DOUBLE_CLICK_COUNT)
                .remove(KEY_WATCH_CAMERA_DOUBLE_CLICK_LAST_AGE)
                .remove(KEY_WATCH_CAMERA_DOUBLE_CLICK_AGE_SOURCE)
                .remove(KEY_WATCH_CAMERA_DOUBLE_CLICK_EFFECTIVE_AGE)
                .remove(KEY_WATCH_CAMERA_DIRECT_KEY_PULSE_SCHEDULED)
                .remove(KEY_WATCH_CAMERA_DIRECT_KEY_PULSE_FIRED)
                .remove(KEY_WATCH_CAMERA_DIRECT_KEY_PULSE_CANCELLED_HOME)
                .remove(KEY_WATCH_CAMERA_OPEN_ONLY)
                .remove(KEY_WATCH_CAMERA_TOUCH_IGNORED)
                .remove(KEY_WATCH_CAMERA_TOUCH_DOUBLE_CANDIDATE)
                .remove(KEY_WATCH_CAMERA_TOUCH_DOUBLE_ACCEPTED)
                .remove(KEY_WATCH_CAMERA_TOUCH_DOUBLE_LAST_AGE)
                .remove(KEY_WATCH_CAMERA_ROTARY_IGNORED)
                .remove(KEY_WATCH_CAMERA_ROTARY_LAST_KEY)
                .remove(KEY_WATCH_CAMERA_ROTARY_LAST_SCAN)
                .remove(KEY_WATCH_CAMERA_ROTARY_GUARD_UNTIL)
                .remove(KEY_WATCH_CAMERA_ROTARY_GUARD_COUNT)
                .remove(KEY_WATCH_CAMERA_ROTARY_BLOCKED_SHUTTER)
                .remove(KEY_WATCH_CAMERA_ROTARY_LAST_SOURCE)
                .remove(KEY_WATCH_CAMERA_ROTARY_VARIANT)
                .remove(KEY_WATCH_CAMERA_ROTARY_CANCELLED_PENDING)
                .remove(KEY_WATCH_CAMERA_INPUT_NOISE_UNTIL)
                .remove(KEY_WATCH_CAMERA_INPUT_NOISE_COUNT)
                .remove(KEY_WATCH_CAMERA_LAST_NOISE_SOURCE)
                .remove(KEY_WATCH_CAMERA_HOME_IGNORED_NOISE)
                .remove(KEY_SAFE_IME_HIDDEN)
                .remove(KEY_SAFE_IME_SHOWN)
                .remove(KEY_SAFE_IME_HIDE_BUTTON)
                .remove(KEY_SAFE_IME_AUTO_REPEAT_HIDDEN)
                .remove(KEY_SAFE_IME_DEFAULT_LOST)
                .remove(KEY_SAFE_IME_LAST_HIDE_REASON)
                .remove(KEY_SAFE_IME_LAST_FIELD)
                .remove(KEY_SAFE_IME_LAST_AUTO_HIDE_AT)
                .remove(KEY_SAFE_IME_WAS_SELECTED)
                .remove(KEY_SAFE_IME_VIEW_CLICK)
                .remove(KEY_SAFE_IME_MANUAL_CLICK_SHOW)
                .remove(KEY_SAFE_IME_STRICT_HIDDEN)
                .remove(KEY_SAFE_IME_EXTRACT_HIDDEN)
                .remove(KEY_SAFE_IME_CLICK_SIGNAL_MISSING)
                .remove(KEY_SAFE_IME_LAST_VIEW_CLICK_AT)
                .remove(KEY_SAFE_IME_START_INPUT)
                .remove(KEY_SAFE_IME_START_INPUT_VIEW)
                .remove(KEY_SAFE_IME_SHOW_REQUEST)
                .remove(KEY_SAFE_IME_LAST_SHOW_FLAGS)
                .remove(KEY_SAFE_IME_LAST_SHOW_CONFIG)
                .remove(KEY_SAFE_IME_EVALUATE_FULLSCREEN)
                .remove(KEY_SAFE_IME_CREATE_EXTRACT)
                .remove(KEY_SAFE_IME_UPDATE_EXTRACT_VISIBILITY)
                .remove(KEY_SAFE_IME_MANUAL_SIGNAL_ABSENT)
                .remove(KEY_SAFE_IME_LAST_CALLBACK)
                .remove(KEY_SAFE_IME_LAST_CALLBACK_DETAIL)
                .remove(KEY_SAFE_IME_A11Y_WAS_ENABLED)
                .remove(KEY_SAFE_IME_A11Y_TAP_AT)
                .remove(KEY_SAFE_IME_A11Y_TAP_PACKAGE)
                .remove(KEY_SAFE_IME_A11Y_TAP_FIELD)
                .remove(KEY_SAFE_IME_A11Y_TAP_COUNT)
                .remove(KEY_SAFE_IME_A11Y_FOCUS_COUNT)
                .remove(KEY_SAFE_IME_A11Y_CONSUMED_COUNT)
                .remove(KEY_SAFE_IME_A11Y_EXPIRED_COUNT)
                .remove(KEY_SAFE_IME_A11Y_NOT_SELECTED)
                .remove(KEY_SAFE_IME_TAP_SERVICE_OFF)
                .remove(KEY_SAFE_IME_CRASH_GUARD)
                .remove(KEY_SAFE_IME_EXPLICIT_SHOW)
                .remove(KEY_SAFE_IME_EXPLICIT_SUPPRESSED_NAV)
                .remove(KEY_SAFE_IME_EXPLICIT_SUPPRESSED_CHURN)
                .remove(KEY_SAFE_IME_EXPLICIT_WITHOUT_GATE_SUPPRESSED)
                .remove(KEY_SAFE_IME_VIEW_CLICK_IGNORED)
                .remove(KEY_SAFE_IME_GATE_ACTIVE)
                .remove(KEY_SAFE_IME_GATE_PACKAGE)
                .remove(KEY_SAFE_IME_GATE_ZONE)
                .remove(KEY_SAFE_IME_GATE_TAP_COUNT)
                .remove(KEY_SAFE_IME_GATE_SWIPE_IGNORED)
                .remove(KEY_SAFE_IME_GATE_NO_CONNECTION)
                .remove(KEY_SAFE_IME_GATE_TOKEN_AT)
                .remove(KEY_SAFE_IME_GATE_TOKEN_PACKAGE)
                .remove(KEY_SAFE_IME_GATE_FOCUS_PASS_UNTIL)
                .remove(KEY_SAFE_IME_GATE_FOCUS_PASS_PACKAGE)
                .remove(KEY_SAFE_IME_GATE_KEYBOARD_VISIBLE)
                .remove(KEY_SAFE_IME_GATE_BLOCK_REASON)
                .remove(KEY_SAFE_IME_GATE_OFF_REASON)
                .remove(KEY_SAFE_IME_GATE_CANDIDATE_SOURCE)
                .remove(KEY_SAFE_IME_GATE_LAST_VISIBLE_AT)
                .remove(KEY_SAFE_IME_GATE_DOWN_COUNT)
                .remove(KEY_SAFE_IME_GATE_ZONE_REVISION)
                .remove(KEY_SAFE_IME_GATE_ATTACHED)
                .remove(KEY_SAFE_IME_GATE_READD_COUNT)
                .remove(KEY_SAFE_IME_GATE_DETACHED_COUNT)
                .remove(KEY_SAFE_IME_GATE_LAST_LAYOUT)
                .remove(KEY_SAFE_IME_GATE_LAST_TOUCH_AT)
                .remove(KEY_SAFE_IME_GATE_RESCUE_SHOWN)
                .remove(KEY_SAFE_IME_GATE_RESCUE_TAP)
                .remove(KEY_SAFE_IME_GATE_MISS_PACKAGE)
                .remove(KEY_SAFE_IME_GATE_MISS_FIELD)
                .remove(KEY_SAFE_IME_GATE_MISS_COUNT)
                .remove(KEY_SAFE_IME_GATE_MISS_AT)
                .remove(KEY_SAFE_IME_LAST_STRICT_INPUT_PACKAGE)
                .remove(KEY_SAFE_IME_LAST_STRICT_INPUT_FIELD)
                .remove(KEY_SAFE_IME_LAST_STRICT_INPUT_TYPE)
                .remove(KEY_SAFE_IME_LAST_STRICT_INPUT_AT)
                .remove(KEY_SAFE_IME_HIDE_SUPPRESSION_MS)
                .remove(KEY_SAFE_IME_POLICY_MODE)
                .remove(KEY_GPT_SHARE_PENDING)
                .remove(KEY_GPT_SHARE_SESSION_ID)
                .remove(KEY_GPT_SHARE_PROMPT)
                .remove(KEY_GPT_SHARE_ALL_PHOTOS)
                .remove(KEY_GPT_SHARE_CHUNK_INDEX)
                .remove(KEY_GPT_SHARE_CHUNK_COUNT)
                .remove(KEY_GPT_SHARE_CHUNK_SIZE)
                .remove(KEY_GPT_SHARE_NEEDS_NEW_CHAT)
                .remove(KEY_GPT_SHARE_TARGET_MODE)
                .remove(KEY_GPT_SHARE_TARGET_FALLBACK_REASON)
                .remove(KEY_GPT_SHARE_LAST_RESULT)
                .remove(KEY_GPT_SHARE_NO_PHOTOS)
                .remove(KEY_GPT_SHARE_LAUNCH_COUNT)
                .remove(KEY_GPT_AUTOMATION_SENT_COUNT)
                .remove(KEY_GPT_AUTOMATION_FAIL_COUNT)
                .remove(KEY_GPT_AUTOMATION_LAST_ACTION)
                .remove(KEY_GPT_AUTOMATION_ACCESSIBILITY_OFF)
                .remove(KEY_GPT_AUTOMATION_PHASE)
                .remove(KEY_GPT_AUTOMATION_GATE_REASON)
                .remove(KEY_GPT_AUTOMATION_ATTACHMENT_EXPECTED)
                .remove(KEY_GPT_AUTOMATION_ATTACHMENT_EVIDENCE)
                .remove(KEY_GPT_AUTOMATION_ATTACHMENT_STREAK)
                .remove(KEY_GPT_AUTOMATION_CHUNK_LAUNCHED_AT)
                .remove(KEY_GPT_AUTOMATION_SAFE_SEND_CANDIDATE)
                .remove(KEY_GPT_AUTOMATION_MANUAL_REQUIRED)
                .remove(KEY_GPT_AUTOMATION_SEND_CANDIDATE_SOURCE)
                .remove(KEY_GPT_AUTOMATION_ARROW_FALLBACK)
                .remove(KEY_GPT_AUTOMATION_SEND_CLICK_AT)
                .remove(KEY_GPT_AUTOMATION_ATTACHMENT_REQUIRED)
                .remove(KEY_GPT_AUTOMATION_ROOT_SOURCE)
                .remove(KEY_GPT_AUTOMATION_ROOT_PACKAGE)
                .remove(KEY_GPT_AUTOMATION_LOCKED_OVERLAY_MODE)
                .remove(KEY_GPT_AUTO_LOCK_REQUESTED)
                .remove(KEY_GPT_AUTO_LOCK_ACTIVE)
                .remove(KEY_GPT_AUTO_LOCK_STATE)
                .remove(KEY_GPT_AUTO_LOCK_STARTED_AT)
                .remove(KEY_GPT_AUTO_LOCK_START_COUNT)
                .remove(KEY_GPT_AUTO_LOCK_FAIL_COUNT)
                .remove(KEY_LOCKED_BACK_KEY_CONSUMED)
                .remove(KEY_LOCKED_EDGE_BACK_SWIPE_BLOCKED)
                .remove(KEY_LOCKED_BACK_REBOUND)
                .remove(KEY_LOCKED_BACK_LAST_TARGET)
                .remove(KEY_LOCKED_BACK_LAST_PACKAGE)
                .remove(KEY_LOCKED_EDGE_GUARD_ACTIVE)
                .remove(KEY_LOCKED_EDGE_GUARD_TOUCH)
                .remove(KEY_LOCKED_EDGE_GUARD_BLOCKED)
                .remove(KEY_LOCKED_BACK_ESCAPE)
                .remove(KEY_SAFE_IME_NAV_SUPPRESS_UNTIL)
                .remove(KEY_SAFE_IME_LAST_NAV_FIELD)
                .remove(KEY_SAFE_IME_LAST_NAV_REASON)
                .remove(KEY_SAFE_IME_LAST_CRASH_SOURCE)
                .remove(KEY_SAFE_IME_LAST_CRASH_ERROR)
                .remove(KEY_SAFE_IME_DEFAULT_LOST_CONTEXT)
                .remove(KEY_SAFE_IME_LOCK_DEFAULT)
                .remove(KEY_SAFE_IME_LOCK_SELECTED)
                .remove(KEY_SAFE_IME_SNAPSHOT_BEFORE_LOCK)
                .remove(KEY_SAFE_IME_LAST_SNAPSHOT)
                .remove(KEY_SAFE_IME_SNAPSHOT_LOG)
                .remove(KEY_SAFE_IME_LOST_PHASE)
                .remove(KEY_SAFE_IME_LOST_DURING_UNLOCK)
                .remove(KEY_SAFE_IME_LOST_RECORDED_CYCLE)
                .remove(KEY_SAFE_IME_SILENT_RESTORE_COUNT)
                .remove(KEY_SAFE_IME_SILENT_RESTORE_LAST_RESULT)
                .remove(KEY_SAFE_IME_SILENT_RESTORE_SKIP_CYCLE)
                .remove(KEY_SAFE_IME_RESTORE_ATTEMPT_PHASE)
                .remove(KEY_KEYBOARD_HIDE_UNLOCK_COUNT)
                .remove(KEY_LAST_CACHED_APP)
                .remove(KEY_LAST_CACHED_APP_AT)
                .remove(KEY_LAST_CACHED_APP_SOURCE)
                .remove(KEY_LAST_MENU_SEEN_AFTER_APP)
                .remove(KEY_LAST_MENU_SEEN_AFTER_APP_AT)
                .remove(KEY_LAST_NAVIGATION_SEEN_AFTER_APP)
                .remove(KEY_LAST_NAVIGATION_SEEN_AFTER_APP_AT)
                .remove(KEY_LAST_NAVIGATION_SOURCE)
                .remove(KEY_LAST_HOME_DELEGATE)
                .apply();
    }

    static boolean isAccessibilityEnabled(Context context) {
        return isAccessibilityServiceEnabled(context, new ComponentName(context, WatchAccessibilityService.class));
    }

    static boolean isGptAutomationAccessibilityEnabled(Context context) {
        return isAccessibilityServiceEnabled(
                context,
                new ComponentName(context, GptAutomationAccessibilityService.class)
        );
    }

    static boolean isCameraTapAccessibilityEnabled(Context context) {
        return isAccessibilityServiceEnabled(context, new ComponentName(context, CameraTapAccessibilityService.class));
    }

    static boolean isSafeImeTapAccessibilityEnabled(Context context) {
        return isAccessibilityServiceEnabled(context, new ComponentName(context, SafeImeTapAccessibilityService.class));
    }

    static boolean isAccessibilityPackageEnabled(Context context, String packageName) {
        String enabled = Settings.Secure.getString(
                context.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        );
        return enabled != null && enabled.contains(packageName + "/");
    }

    private static boolean isAccessibilityServiceEnabled(Context context, ComponentName expected) {
        String enabled = Settings.Secure.getString(
                context.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        );
        if (enabled == null) {
            return false;
        }
        TextUtils.SimpleStringSplitter splitter = new TextUtils.SimpleStringSplitter(':');
        splitter.setString(enabled);
        while (splitter.hasNext()) {
            ComponentName actual = ComponentName.unflattenFromString(splitter.next());
            if (expected.equals(actual)) {
                return true;
            }
        }
        return false;
    }

    static boolean isWatchKeyboardSelected(Context context) {
        String currentIme = Settings.Secure.getString(
                context.getContentResolver(),
                Settings.Secure.DEFAULT_INPUT_METHOD
        );
        return currentIme != null && currentIme.contains(context.getPackageName());
    }

    static boolean isWatchKeyboardEnabled(Context context) {
        String enabled = Settings.Secure.getString(
                context.getContentResolver(),
                Settings.Secure.ENABLED_INPUT_METHODS
        );
        return enabled != null && enabled.contains(context.getPackageName());
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(
                PREFS,
                Context.MODE_PRIVATE
        );
    }
}
