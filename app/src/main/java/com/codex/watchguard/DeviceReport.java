package com.codex.watchguard;

import android.app.ActivityManager;
import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Point;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.InputDevice;
import android.view.WindowManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

final class DeviceReport {
    private static final int MAX_JSON_STRING_CHARS = 12000;

    private DeviceReport() {
    }

    static String build(Context context) {
        JSONObject root = new JSONObject();
        ParseDiagnostics parseDiagnostics = new ParseDiagnostics();
        try {
            put(root, "generated_at_utc", utcNow());
            put(root, "app", app(context));
            put(root, "android_build", androidBuild());
            put(root, "display", display(context));
            put(root, "runtime", runtime(context));
            put(root, "features", features(context));
            put(root, "permissions_state", permissionState(context));
            put(root, "capability_tier", capabilityTier(context));
            put(root, "crash_state", crashState(context));
            put(root, "keyboard_state", keyboardState(context));
            put(root, "guardian_state", guardianState(context));
            put(root, "home_trap_state", homeTrapState(context));
            put(root, "camera_button_state", cameraButtonState(context));
            put(root, "watchguard_camera_state", watchguardCameraState(context));
            put(root, "lock_mode_results", lockModeResults(context, parseDiagnostics));
            put(root, "input_devices", inputDevices());
            put(root, "detected_key_events", keyEvents(context, parseDiagnostics));
            put(root, "diagnostic_parse_error_count", parseDiagnostics.errorCount);
            put(root, "diagnostic_parse_last_error", parseDiagnostics.lastError);
            put(root, "diagnostic_log", AppState.getLog(context));
            return root.toString(2);
        } catch (JSONException error) {
            return "{\"error\":\"" + error.getMessage() + "\"}";
        }
    }

    private static JSONObject app(Context context) throws JSONException {
        JSONObject app = new JSONObject();
        PackageManager pm = context.getPackageManager();
        try {
            PackageInfo info = pm.getPackageInfo(context.getPackageName(), 0);
            put(app, "package", context.getPackageName());
            put(app, "version_name", info.versionName);
            put(app, "version_code", info.versionCode);
        } catch (PackageManager.NameNotFoundException ignored) {
            put(app, "package", context.getPackageName());
        }
        return app;
    }

    private static JSONObject androidBuild() throws JSONException {
        JSONObject build = new JSONObject();
        put(build, "sdk_int", Build.VERSION.SDK_INT);
        put(build, "release", Build.VERSION.RELEASE);
        put(build, "security_patch", Build.VERSION.SECURITY_PATCH);
        put(build, "manufacturer", Build.MANUFACTURER);
        put(build, "brand", Build.BRAND);
        put(build, "model", Build.MODEL);
        put(build, "device", Build.DEVICE);
        put(build, "product", Build.PRODUCT);
        put(build, "board", Build.BOARD);
        put(build, "hardware", Build.HARDWARE);
        put(build, "display", Build.DISPLAY);
        put(build, "id", Build.ID);
        put(build, "fingerprint", Build.FINGERPRINT);
        put(build, "bootloader", Build.BOOTLOADER);
        put(build, "host", Build.HOST);
        put(build, "tags", Build.TAGS);
        put(build, "type", Build.TYPE);
        put(build, "user", Build.USER);
        JSONArray abis = new JSONArray();
        for (String abi : Build.SUPPORTED_ABIS) {
            abis.put(abi);
        }
        put(build, "supported_abis", abis);
        return build;
    }

    private static JSONObject display(Context context) throws JSONException {
        JSONObject displayJson = new JSONObject();
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();

        DisplayMetrics metrics = new DisplayMetrics();
        display.getMetrics(metrics);
        put(displayJson, "width_px", metrics.widthPixels);
        put(displayJson, "height_px", metrics.heightPixels);
        put(displayJson, "density", metrics.density);
        put(displayJson, "density_dpi", metrics.densityDpi);
        put(displayJson, "scaled_density", metrics.scaledDensity);
        put(displayJson, "xdpi", metrics.xdpi);
        put(displayJson, "ydpi", metrics.ydpi);

        Point realSize = new Point();
        display.getRealSize(realSize);
        put(displayJson, "real_width_px", realSize.x);
        put(displayJson, "real_height_px", realSize.y);
        put(displayJson, "rotation", display.getRotation());

        Configuration config = context.getResources().getConfiguration();
        put(displayJson, "orientation", config.orientation);
        put(displayJson, "font_scale", config.fontScale);
        put(displayJson, "screen_layout", config.screenLayout);
        put(displayJson, "smallest_screen_width_dp", config.smallestScreenWidthDp);
        put(displayJson, "screen_width_dp", config.screenWidthDp);
        put(displayJson, "screen_height_dp", config.screenHeightDp);
        return displayJson;
    }

    private static JSONObject runtime(Context context) throws JSONException {
        JSONObject runtime = new JSONObject();
        put(runtime, "timezone", TimeZone.getDefault().getID());
        put(runtime, "locale", Locale.getDefault().toString());
        put(runtime, "screen_off_timeout_ms", Settings.System.getString(
                context.getContentResolver(),
                Settings.System.SCREEN_OFF_TIMEOUT
        ));
        put(runtime, "accelerometer_rotation", Settings.System.getString(
                context.getContentResolver(),
                Settings.System.ACCELEROMETER_ROTATION
        ));
        put(runtime, "user_rotation", Settings.System.getString(
                context.getContentResolver(),
                Settings.System.USER_ROTATION
        ));
        put(runtime, "is_debuggable_build", "userdebug".equals(Build.TYPE) || "eng".equals(Build.TYPE));
        return runtime;
    }

    private static JSONObject features(Context context) throws JSONException {
        PackageManager pm = context.getPackageManager();
        JSONObject features = new JSONObject();
        put(features, "watch", pm.hasSystemFeature(PackageManager.FEATURE_WATCH));
        put(features, "touchscreen", pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN));
        put(features, "bluetooth", pm.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH));
        put(features, "wifi", pm.hasSystemFeature(PackageManager.FEATURE_WIFI));
        put(features, "telephony", pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY));
        put(features, "microphone", pm.hasSystemFeature(PackageManager.FEATURE_MICROPHONE));
        return features;
    }

    private static JSONObject permissionState(Context context) throws JSONException {
        JSONObject state = new JSONObject();
        put(state, "can_draw_overlays", Settings.canDrawOverlays(context));
        put(state, "watch_keyboard_selected", AppState.isWatchKeyboardSelected(context));
        put(state, "default_input_method", Settings.Secure.getString(
                context.getContentResolver(),
                Settings.Secure.DEFAULT_INPUT_METHOD
        ));
        PowerManager power = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        put(state, "ignoring_battery_optimizations",
                power != null && power.isIgnoringBatteryOptimizations(context.getPackageName()));
        DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
        put(state, "device_owner", dpm != null && dpm.isDeviceOwnerApp(context.getPackageName()));
        put(state, "lock_task_permitted",
                dpm != null && dpm.isLockTaskPermitted(context.getPackageName()));
        put(state, "usage_access_enabled", ForegroundResolver.hasUsageAccess(context));
        put(state, "camera_permission_granted", AppState.hasCameraPermission(context));
        put(state, "write_secure_settings_granted",
                AppState.hasWriteSecureSettingsPermission(context));
        put(state, "write_settings_granted", AppState.canWriteSystemSettings(context));
        put(state, "default_home_package", ForegroundResolver.defaultHomePackageName(context));
        put(state, "watchguard_launcher_selected",
                ForegroundResolver.isWatchGuardDefaultHome(context));
        put(state, "gpt_automation_accessibility_enabled",
                AppState.isGptAutomationAccessibilityEnabled(context));
        put(state, "gpt_accessibility_service_alive",
                AppState.isGptAutomationServiceAlive(context));
        put(state, "gpt_automation_process", "main");
        put(state, "state_storage_policy", "single_process_private_prefs");
        long heartbeatAt = AppState.getGptAccessibilityHeartbeatAt(context);
        put(state, "gpt_accessibility_heartbeat_at_ms", nullableTime(heartbeatAt));
        put(state, "gpt_accessibility_heartbeat_age_ms",
                heartbeatAt == 0L ? JSONObject.NULL
                        : Math.max(0L, System.currentTimeMillis() - heartbeatAt));
        put(state, "enabled_accessibility_services", Settings.Secure.getString(
                context.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ));
        return state;
    }

    private static JSONObject crashState(Context context) throws JSONException {
        JSONObject crash = new JSONObject();
        long lastCrashAt = AppState.getLastCrashAt(context);
        put(crash, "crash_count", AppState.getCrashCount(context));
        put(crash, "last_crash_at_ms", nullableTime(lastCrashAt));
        put(crash, "last_crash_age_ms",
                lastCrashAt == 0L ? JSONObject.NULL
                        : Math.max(0L, System.currentTimeMillis() - lastCrashAt));
        put(crash, "last_crash_thread", AppState.getLastCrashThread(context));
        put(crash, "last_crash_type", AppState.getLastCrashType(context));
        put(crash, "last_crash_message", AppState.getLastCrashMessage(context));
        put(crash, "last_crash_stack", AppState.getLastCrashStack(context));
        return crash;
    }

    private static JSONObject capabilityTier(Context context) throws JSONException {
        JSONObject tier = new JSONObject();
        DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
        boolean overlay = Settings.canDrawOverlays(context);
        boolean lockTaskPermitted = dpm != null && dpm.isLockTaskPermitted(context.getPackageName());
        boolean deviceOwner = dpm != null && dpm.isDeviceOwnerApp(context.getPackageName());

        String name;
        if (lockTaskPermitted || deviceOwner) {
            name = "true_kiosk_available";
        } else if (overlay) {
            name = "apk_overlay_only";
        } else {
            name = "apk_diagnostic_only";
        }

        put(tier, "name", name);
        put(tier, "can_read_physical_button", false);
        put(tier, "captures_unknown_hardware_keys", false);
        put(tier, "button_control_enabled", AppState.isButtonControlEnabled(context));
        put(tier, "button_control_paused", AppState.isButtonControlPaused(context));
        put(tier, "home_trap_enabled", AppState.isHomeTrapEnabled(context));
        put(tier, "mode_button_enabled", AppState.isHomeTrapEnabled(context));
        put(tier, "mode_user_desired_enabled",
                AppState.isModeUserDesiredEnabled(context));
        long modeEnabledAt = AppState.getModeEnabledAt(context);
        put(tier, "mode_enabled_at_ms", nullableTime(modeEnabledAt));
        put(tier, "mode_enabled_age_ms",
                modeEnabledAt == 0L ? JSONObject.NULL
                        : Math.max(0L, System.currentTimeMillis() - modeEnabledAt));
        put(tier, "mode_last_disabled_reason",
                AppState.getModeLastDisabledReason(context));
        put(tier, "mode_disable_source",
                AppState.getModeDisableSource(context));
        put(tier, "mode_repair_count", AppState.getModeRepairCount(context));
        put(tier, "mode_overlay_recovery_count",
                AppState.getModeOverlayRecoveryCount(context));
        put(tier, "touch_service_null_intent_count",
                AppState.getTouchServiceNullIntentCount(context));
        put(tier, "mode_rearm_reason", AppState.getModeRearmReason(context));
        put(tier, "boot_receiver_last_action",
                AppState.getBootReceiverLastAction(context));
        put(tier, "boot_receiver_last_result",
                AppState.getBootReceiverLastResult(context));
        put(tier, "home_trap_invocations", AppState.getHomeTrapInvocations(context));
        put(tier, "home_trap_ignored", AppState.getHomeTrapIgnored(context));
        put(tier, "home_trap_session_active", AppState.isHomeTrapSessionActive(context));
        put(tier, "home_trap_cycle_id", AppState.getHomeTrapCycleId(context));
        put(tier, "locked_cycle_package", AppState.getLockedCyclePackage(context));
        put(tier, "locked_cycle_is_menu", AppState.isLockedCycleMenu(context));
        put(tier, "unlock_allowed_at", nullableTime(AppState.getUnlockAllowedAt(context)));
        put(tier, "unlock_settle_until", nullableTime(AppState.getUnlockSettleUntil(context)));
        put(tier, "lock_settle_until", nullableTime(AppState.getLockSettleUntil(context)));
        put(tier, "locked_age_ms", nullableAge(AppState.getLockedAgeMs(context)));
        put(tier, "last_home_gap_ms", nullableAge(AppState.getHomeTrapLastGap(context)));
        put(tier, "locked_home_burst_ignored", AppState.getLockedHomeBurstIgnored(context));
        put(tier, "locked_back_key_consumed_count",
                AppState.getLockedBackKeyConsumedCount(context));
        put(tier, "locked_edge_back_swipe_blocked_count",
                AppState.getLockedEdgeBackSwipeBlockedCount(context));
        put(tier, "locked_back_rebound_count",
                AppState.getLockedBackReboundCount(context));
        put(tier, "locked_edge_guard_active",
                AppState.isLockedEdgeGuardActive(context));
        put(tier, "locked_edge_guard_touch_count",
                AppState.getLockedEdgeGuardTouchCount(context));
        put(tier, "locked_edge_guard_blocked_count",
                AppState.getLockedEdgeGuardBlockedCount(context));
        put(tier, "locked_back_escape_count",
                AppState.getLockedBackEscapeCount(context));
        put(tier, "locked_back_last_target", AppState.getLockedBackLastTarget(context));
        put(tier, "locked_back_last_package", AppState.getLockedBackLastPackage(context));
        put(tier, "home_gap_accept_ms", AppState.getHomeGapAcceptMs());
        put(tier, "lock_stabilize_restore_count", AppState.getLockStabilizeRestoreCount(context));
        put(tier, "screen_off_recovery_package", AppState.getScreenOffRecoveryPackage(context));
        put(tier, "screen_off_recovery_count", AppState.getScreenOffRecoveryCount(context));
        put(tier, "usage_access_enabled", ForegroundResolver.hasUsageAccess(context));
        put(tier, "stay_awake_enabled", AppState.isStayAwakeEnabled(context));
        put(tier, "screen_timeout_write_ready", AppState.canWriteSystemSettings(context));
        put(tier, "screen_timeout_changed", AppState.isScreenTimeoutChanged(context));
        put(tier, "screen_timeout_current_ms", AppState.getCurrentScreenTimeout(context));
        put(tier, "screen_timeout_original_ms", AppState.getScreenTimeoutOriginal(context));
        put(tier, "screen_timeout_last_result", AppState.getScreenTimeoutLastResult(context));
        put(tier, "keyboard_guard_enabled", AppState.isKeyboardGuardEnabled(context));
        put(tier, "can_block_basic_touches", overlay);
        put(tier, "pin_lock_requires_user_confirmation", !lockTaskPermitted);
        put(tier, "true_kiosk_available", lockTaskPermitted || deviceOwner);
        put(tier, "launcher_mode_enabled", false);
        put(tier, "notes", "Ruta principal de lock sin Accessibility: HomeTrapActivity alterna overlay lock al ser launcher predeterminado. v0.72 conserva perilla/GPT y agrega rescate OEM de downmenu sin camara nativa.");
        return tier;
    }

    private static JSONObject keyboardState(Context context) throws JSONException {
        AppState.observeSafeImeDefault(context);
        JSONObject keyboard = new JSONObject();
        String defaultIme = Settings.Secure.getString(
                context.getContentResolver(),
                Settings.Secure.DEFAULT_INPUT_METHOD
        );
        String enabledImes = Settings.Secure.getString(
                context.getContentResolver(),
                Settings.Secure.ENABLED_INPUT_METHODS
        );
        put(keyboard, "watchguard_ime_declared_in_manifest", true);
        put(keyboard, "default_input_method", defaultIme);
        put(keyboard, "enabled_input_methods", enabledImes);
        put(keyboard, "default_is_gboard",
                defaultIme != null && defaultIme.contains("com.google.android.inputmethod.latin"));
        put(keyboard, "watchguard_enabled", AppState.isWatchKeyboardEnabled(context));
        put(keyboard, "watchguard_selected",
                defaultIme != null && defaultIme.contains(context.getPackageName()));
        put(keyboard, "safe_ime_selected",
                defaultIme != null && defaultIme.contains(context.getPackageName()));
        put(keyboard, "safe_ime_default_lost_count", AppState.getSafeImeDefaultLostCount(context));
        put(keyboard, "safe_ime_default_lost_context",
                AppState.getSafeImeDefaultLostContext(context));
        put(keyboard, "safe_ime_snapshot_before_lock",
                AppState.getSafeImeSnapshotBeforeLock(context));
        put(keyboard, "safe_ime_last_snapshot",
                AppState.getSafeImeLastSnapshot(context));
        put(keyboard, "safe_ime_snapshot_log",
                AppState.getSafeImeSnapshotLog(context));
        put(keyboard, "safe_ime_lost_phase",
                AppState.getSafeImeLostPhase(context));
        put(keyboard, "safe_ime_lost_during_unlock_count",
                AppState.getSafeImeLostDuringUnlockCount(context));
        put(keyboard, "safe_ime_silent_restore_available",
                AppState.hasWriteSecureSettingsPermission(context));
        put(keyboard, "safe_ime_adb_grant_command",
                "adb shell pm grant " + context.getPackageName()
                        + " android.permission.WRITE_SECURE_SETTINGS");
        put(keyboard, "safe_ime_silent_restore_ready",
                AppState.hasWriteSecureSettingsPermission(context)
                        && AppState.isWatchKeyboardEnabled(context));
        put(keyboard, "safe_ime_restore_attempt_phase",
                AppState.getSafeImeRestoreAttemptPhase(context));
        put(keyboard, "safe_ime_restore_ok_count",
                AppState.getSafeImeSilentRestoreCount(context));
        put(keyboard, "safe_ime_silent_restore_count",
                AppState.getSafeImeSilentRestoreCount(context));
        put(keyboard, "safe_ime_silent_restore_last_result",
                AppState.getSafeImeSilentRestoreLastResult(context));
        put(keyboard, "keyboard_hide_unlock_count",
                AppState.getKeyboardHideUnlockCount(context));
        put(keyboard, "safe_ime_hidden_count", AppState.getSafeImeHiddenCount(context));
        put(keyboard, "safe_ime_shown_count", AppState.getSafeImeShownCount(context));
        put(keyboard, "safe_ime_hide_button_count", AppState.getSafeImeHideButtonCount(context));
        put(keyboard, "safe_ime_auto_repeat_hidden_count",
                AppState.getSafeImeAutoRepeatHiddenCount(context));
        put(keyboard, "safe_ime_view_click_count", AppState.getSafeImeViewClickCount(context));
        put(keyboard, "safe_ime_manual_click_show_count",
                AppState.getSafeImeManualClickShowCount(context));
        put(keyboard, "safe_ime_explicit_show_count",
                AppState.getSafeImeExplicitShowCount(context));
        put(keyboard, "safe_ime_explicit_suppressed_navigation_count",
                AppState.getSafeImeExplicitSuppressedNavigationCount(context));
        put(keyboard, "safe_ime_explicit_suppressed_churn_count",
                AppState.getSafeImeExplicitSuppressedChurnCount(context));
        put(keyboard, "safe_ime_explicit_without_gate_suppressed_count",
                AppState.getSafeImeExplicitWithoutGateSuppressedCount(context));
        put(keyboard, "safe_ime_view_clicked_ignored_count",
                AppState.getSafeImeViewClickedIgnoredCount(context));
        put(keyboard, "safe_ime_gate_active",
                AppState.isSafeImeGateActive(context));
        put(keyboard, "safe_ime_gate_package",
                AppState.getSafeImeGatePackage(context));
        put(keyboard, "safe_ime_gate_zone",
                AppState.getSafeImeGateZone(context));
        put(keyboard, "safe_ime_gate_attached",
                AppState.isSafeImeGateAttached(context));
        put(keyboard, "safe_ime_gate_block_reason",
                AppState.getSafeImeGateBlockReason(context));
        put(keyboard, "safe_ime_gate_off_reason",
                AppState.getSafeImeGateOffReason(context));
        put(keyboard, "safe_ime_gate_candidate_source",
                AppState.getSafeImeGateCandidateSource(context));
        long gateVisibleAt = AppState.getSafeImeGateLastVisibleAt(context);
        put(keyboard, "safe_ime_gate_last_visible_at",
                gateVisibleAt == 0L ? JSONObject.NULL : gateVisibleAt);
        put(keyboard, "safe_ime_gate_visible_age_ms",
                gateVisibleAt == 0L ? JSONObject.NULL
                        : Math.max(0L, System.currentTimeMillis() - gateVisibleAt));
        put(keyboard, "safe_ime_gate_down_count",
                AppState.getSafeImeGateDownCount(context));
        put(keyboard, "safe_ime_gate_zone_revision",
                AppState.getSafeImeGateZoneRevision(context));
        put(keyboard, "safe_ime_gate_readd_count",
                AppState.getSafeImeGateReaddCount(context));
        put(keyboard, "safe_ime_gate_detached_count",
                AppState.getSafeImeGateDetachedCount(context));
        put(keyboard, "safe_ime_gate_last_layout",
                AppState.getSafeImeGateLastLayout(context));
        long gateTouchAt = AppState.getSafeImeGateLastTouchAt(context);
        put(keyboard, "safe_ime_gate_last_touch_at_ms",
                gateTouchAt == 0L ? JSONObject.NULL : gateTouchAt);
        put(keyboard, "safe_ime_gate_last_touch_age_ms",
                gateTouchAt == 0L ? JSONObject.NULL
                        : AppState.getSafeImeGateLastTouchAgeMs(context));
        put(keyboard, "safe_ime_gate_tap_count",
                AppState.getSafeImeGateTapCount(context));
        put(keyboard, "safe_ime_gate_swipe_ignored_count",
                AppState.getSafeImeGateSwipeIgnoredCount(context));
        put(keyboard, "safe_ime_gate_no_connection_count",
                AppState.getSafeImeGateNoConnectionCount(context));
        put(keyboard, "safe_ime_gate_rescue_shown_count",
                AppState.getSafeImeGateRescueShownCount(context));
        put(keyboard, "safe_ime_gate_rescue_tap_count",
                AppState.getSafeImeGateRescueTapCount(context));
        put(keyboard, "safe_ime_gate_miss_package",
                AppState.getSafeImeGateMissPackage(context));
        put(keyboard, "safe_ime_gate_miss_field",
                AppState.getSafeImeGateMissField(context));
        put(keyboard, "safe_ime_gate_miss_count",
                AppState.getSafeImeGateMissCount(context));
        long gateMissAt = AppState.getSafeImeGateMissAt(context);
        put(keyboard, "safe_ime_gate_miss_at_ms",
                gateMissAt == 0L ? JSONObject.NULL : gateMissAt);
        put(keyboard, "safe_ime_gate_miss_age_ms",
                gateMissAt == 0L ? JSONObject.NULL
                        : AppState.getSafeImeGateMissAgeMs(context));
        put(keyboard, "safe_ime_gate_exception_count",
                AppState.getSafeImeGateExceptionCount(context));
        put(keyboard, "safe_ime_gate_last_exception",
                AppState.getSafeImeGateLastException(context));
        put(keyboard, "safe_ime_policy_mode",
                AppState.getSafeImePolicyMode(context));
        put(keyboard, "safe_ime_last_strict_input_package",
                AppState.getSafeImeLastStrictInputPackage(context));
        put(keyboard, "safe_ime_last_strict_input_field",
                AppState.getSafeImeLastStrictInputField(context));
        put(keyboard, "safe_ime_last_strict_input_type",
                AppState.getSafeImeLastStrictInputType(context));
        long strictInputAt = AppState.getSafeImeLastStrictInputAt(context);
        put(keyboard, "safe_ime_last_strict_input_at_ms",
                strictInputAt == 0L ? JSONObject.NULL : strictInputAt);
        put(keyboard, "safe_ime_last_strict_input_age_ms",
                strictInputAt == 0L ? JSONObject.NULL
                        : AppState.getSafeImeLastStrictInputAgeMs(context));
        put(keyboard, "safe_ime_hide_suppression_ms",
                AppState.getSafeImeHideSuppressionMs(context));
        long navSuppressUntil = AppState.getSafeImeNavigationSuppressUntil(context);
        put(keyboard, "safe_ime_navigation_suppress_until",
                navSuppressUntil == 0L ? JSONObject.NULL : navSuppressUntil);
        put(keyboard, "safe_ime_navigation_suppress_in_ms",
                navSuppressUntil == 0L ? JSONObject.NULL
                        : Math.max(0L, navSuppressUntil - System.currentTimeMillis()));
        put(keyboard, "safe_ime_navigation_guard_until",
                navSuppressUntil == 0L ? JSONObject.NULL : navSuppressUntil);
        put(keyboard, "safe_ime_navigation_guard_reason",
                AppState.getSafeImeLastNavigationReason(context));
        put(keyboard, "safe_ime_last_navigation_field",
                AppState.getSafeImeLastNavigationField(context));
        put(keyboard, "safe_ime_last_navigation_reason",
                AppState.getSafeImeLastNavigationReason(context));
        put(keyboard, "safe_ime_strict_hidden_count", AppState.getSafeImeStrictHiddenCount(context));
        put(keyboard, "safe_ime_extract_hidden_count",
                AppState.getSafeImeExtractHiddenCount(context));
        put(keyboard, "safe_ime_click_signal_missing_count",
                AppState.getSafeImeClickSignalMissingCount(context));
        put(keyboard, "safe_ime_start_input_count",
                AppState.getSafeImeStartInputCount(context));
        put(keyboard, "safe_ime_start_input_view_count",
                AppState.getSafeImeStartInputViewCount(context));
        put(keyboard, "safe_ime_show_request_count",
                AppState.getSafeImeShowRequestCount(context));
        put(keyboard, "safe_ime_last_show_flags",
                AppState.getSafeImeLastShowFlags(context));
        put(keyboard, "safe_ime_last_show_config_change",
                AppState.getSafeImeLastShowConfigChange(context));
        put(keyboard, "safe_ime_evaluate_fullscreen_count",
                AppState.getSafeImeEvaluateFullscreenCount(context));
        put(keyboard, "safe_ime_create_extract_count",
                AppState.getSafeImeCreateExtractCount(context));
        put(keyboard, "safe_ime_update_extract_visibility_count",
                AppState.getSafeImeUpdateExtractVisibilityCount(context));
        put(keyboard, "safe_ime_manual_signal_absent_count",
                AppState.getSafeImeManualSignalAbsentCount(context));
        put(keyboard, "safe_ime_last_callback",
                AppState.getSafeImeLastCallback(context));
        put(keyboard, "safe_ime_last_callback_detail",
                AppState.getSafeImeLastCallbackDetail(context));
        put(keyboard, "safe_ime_crash_guard_count",
                AppState.getSafeImeCrashGuardCount(context));
        put(keyboard, "safe_ime_last_crash_source",
                AppState.getSafeImeLastCrashSource(context));
        put(keyboard, "safe_ime_last_crash_error",
                AppState.getSafeImeLastCrashError(context));
        put(keyboard, "safe_ime_last_hide_reason", AppState.getSafeImeLastHideReason(context));
        put(keyboard, "safe_ime_last_field", AppState.getSafeImeLastField(context));
        long lastAutoHide = AppState.getSafeImeLastAutoHideAt(context);
        put(keyboard, "safe_ime_last_auto_hide_at_ms", lastAutoHide == 0L ? null : lastAutoHide);
        put(keyboard, "safe_ime_last_auto_hide_age_ms",
                lastAutoHide == 0L ? null : Math.max(0L, System.currentTimeMillis() - lastAutoHide));
        long lastViewClick = AppState.getSafeImeLastViewClickAt(context);
        put(keyboard, "safe_ime_last_view_click_at_ms",
                lastViewClick == 0L ? null : lastViewClick);
        put(keyboard, "safe_ime_click_signal_age_ms",
                lastViewClick == 0L ? null : Math.max(0L, System.currentTimeMillis() - lastViewClick));
        put(keyboard, "keyboard_guard_enabled", AppState.isKeyboardGuardEnabled(context));
        put(keyboard, "keyboard_guard_hide_count", AppState.getKeyboardGuardHideCount(context));
        put(keyboard, "mitigation", "v0.60: GPT/WhatsApp/Claude usan boton overlay visible calibrado por app; la puerta se mantiene attached sin re-add por stale_no_touch y SHOW_EXPLICIT sigue bloqueado sin tap real.");
        return keyboard;
    }

    private static JSONObject guardianState(Context context) throws JSONException {
        JSONObject guardian = new JSONObject();
        long lastTick = AppState.getKeeperLastTick(context);
        put(guardian, "keeper_last_tick_ms", lastTick == 0L ? JSONObject.NULL : lastTick);
        put(guardian, "keeper_tick_age_ms",
                lastTick == 0L ? JSONObject.NULL : Math.max(0L, System.currentTimeMillis() - lastTick));
        long taskRemovedAt = AppState.getKeeperTaskRemovedAt(context);
        long gptConnectedAt = AppState.getGptAccessibilityServiceConnectedAt(context);
        long gptDestroyedAt = AppState.getGptAccessibilityServiceDestroyedAt(context);
        long gptHeartbeatAt = AppState.getGptAccessibilityHeartbeatAt(context);
        long gptStaleAt = AppState.getGptAccessibilityStaleAt(context);
        put(guardian, "keeper_start_reason", AppState.getKeeperStartReason(context));
        put(guardian, "keeper_on_task_removed_at_ms", nullableTime(taskRemovedAt));
        put(guardian, "keeper_on_task_removed_age_ms",
                taskRemovedAt == 0L ? JSONObject.NULL : Math.max(0L, System.currentTimeMillis() - taskRemovedAt));
        put(guardian, "keeper_restart_count", AppState.getKeeperRestartCount(context));
        put(guardian, "button_control_enabled", AppState.isButtonControlEnabled(context));
        put(guardian, "button_control_paused", AppState.isButtonControlPaused(context));
        put(guardian, "home_trap_enabled", AppState.isHomeTrapEnabled(context));
        put(guardian, "mode_button_enabled", AppState.isHomeTrapEnabled(context));
        put(guardian, "mode_user_desired_enabled",
                AppState.isModeUserDesiredEnabled(context));
        long modeEnabledAt = AppState.getModeEnabledAt(context);
        put(guardian, "mode_enabled_at_ms", nullableTime(modeEnabledAt));
        put(guardian, "mode_enabled_age_ms",
                modeEnabledAt == 0L ? JSONObject.NULL
                        : Math.max(0L, System.currentTimeMillis() - modeEnabledAt));
        put(guardian, "mode_last_disabled_reason",
                AppState.getModeLastDisabledReason(context));
        put(guardian, "mode_disable_source",
                AppState.getModeDisableSource(context));
        put(guardian, "mode_repair_count", AppState.getModeRepairCount(context));
        put(guardian, "mode_overlay_recovery_count",
                AppState.getModeOverlayRecoveryCount(context));
        put(guardian, "touch_service_null_intent_count",
                AppState.getTouchServiceNullIntentCount(context));
        put(guardian, "mode_rearm_reason", AppState.getModeRearmReason(context));
        put(guardian, "boot_receiver_last_action",
                AppState.getBootReceiverLastAction(context));
        put(guardian, "boot_receiver_last_result",
                AppState.getBootReceiverLastResult(context));
        put(guardian, "gpt_automation_process", "main");
        put(guardian, "state_storage_policy", "single_process_private_prefs");
        put(guardian, "keyboard_guard_enabled", AppState.isKeyboardGuardEnabled(context));
        put(guardian, "gpt_automation_accessibility_enabled",
                AppState.isGptAutomationAccessibilityEnabled(context));
        put(guardian, "gpt_accessibility_service_alive",
                AppState.isGptAutomationServiceAlive(context));
        put(guardian, "gpt_accessibility_service_connected_at_ms", nullableTime(gptConnectedAt));
        put(guardian, "gpt_accessibility_service_connected_age_ms",
                gptConnectedAt == 0L ? JSONObject.NULL : Math.max(0L, System.currentTimeMillis() - gptConnectedAt));
        put(guardian, "gpt_accessibility_service_destroyed_at_ms", nullableTime(gptDestroyedAt));
        put(guardian, "gpt_accessibility_service_destroyed_age_ms",
                gptDestroyedAt == 0L ? JSONObject.NULL : Math.max(0L, System.currentTimeMillis() - gptDestroyedAt));
        put(guardian, "gpt_accessibility_heartbeat_at_ms", nullableTime(gptHeartbeatAt));
        put(guardian, "gpt_accessibility_heartbeat_age_ms",
                gptHeartbeatAt == 0L ? JSONObject.NULL
                        : Math.max(0L, System.currentTimeMillis() - gptHeartbeatAt));
        put(guardian, "gpt_accessibility_stale_at_ms", nullableTime(gptStaleAt));
        put(guardian, "gpt_accessibility_stale_age_ms",
                gptStaleAt == 0L ? JSONObject.NULL
                        : Math.max(0L, System.currentTimeMillis() - gptStaleAt));
        put(guardian, "gpt_accessibility_stale_count",
                AppState.getGptAccessibilityStaleCount(context));
        put(guardian, "gpt_accessibility_stale_reason",
                AppState.getGptAccessibilityStaleReason(context));
        put(guardian, "accessibility_last_seen_off_ms",
                AppState.getAccessibilityLastOff(context) == 0L
                        ? JSONObject.NULL
                        : AppState.getAccessibilityLastOff(context));
        put(guardian, "locked_back_key_consumed_count",
                AppState.getLockedBackKeyConsumedCount(context));
        put(guardian, "locked_edge_back_swipe_blocked_count",
                AppState.getLockedEdgeBackSwipeBlockedCount(context));
        put(guardian, "locked_back_rebound_count",
                AppState.getLockedBackReboundCount(context));
        put(guardian, "locked_edge_guard_active",
                AppState.isLockedEdgeGuardActive(context));
        put(guardian, "locked_edge_guard_touch_count",
                AppState.getLockedEdgeGuardTouchCount(context));
        put(guardian, "locked_edge_guard_blocked_count",
                AppState.getLockedEdgeGuardBlockedCount(context));
        put(guardian, "locked_back_escape_count",
                AppState.getLockedBackEscapeCount(context));
        put(guardian, "locked_back_last_target", AppState.getLockedBackLastTarget(context));
        put(guardian, "locked_back_last_package", AppState.getLockedBackLastPackage(context));
        put(guardian, "notes", "v0.72 mantiene keeper foreground/sticky, GPT Accessibility en proceso principal, auto-lock GPT y recuperacion de unlock; la camara propia preserva escudo/camara durante rescate OEM.");
        return guardian;
    }

    private static JSONObject homeTrapState(Context context) throws JSONException {
        JSONObject homeTrap = new JSONObject();
        long last = AppState.getHomeTrapLast(context);
        long expires = AppState.getHomeTrapExpires(context);
        long armedAt = AppState.getHomeTrapArmedAt(context);
        ForegroundResolver.VisibleScreen visibleScreen = ForegroundResolver.resolveVisibleScreen(context);
        put(homeTrap, "enabled", AppState.isHomeTrapEnabled(context));
        put(homeTrap, "session_active", AppState.isHomeTrapSessionActive(context));
        put(homeTrap, "cycle_id", AppState.getHomeTrapCycleId(context));
        put(homeTrap, "locked_cycle_package", AppState.getLockedCyclePackage(context));
        put(homeTrap, "locked_cycle_is_menu", AppState.isLockedCycleMenu(context));
        put(homeTrap, "external_home_package", ForegroundResolver.externalHomePackageName(context));
        put(homeTrap, "home_storm_count", AppState.getHomeStormCount(context));
        put(homeTrap, "home_storm_rescues", AppState.getHomeStormRescues(context));
        put(homeTrap, "last_home_delegate", AppState.getLastHomeDelegate(context));
        put(homeTrap, "unlock_allowed_at", nullableTime(AppState.getUnlockAllowedAt(context)));
        put(homeTrap, "unlock_allowed_in_ms", nullableAge(AppState.getUnlockAllowedInMs(context)));
        put(homeTrap, "unlock_settle_until", nullableTime(AppState.getUnlockSettleUntil(context)));
        put(homeTrap, "unlock_settle_in_ms", nullableAge(AppState.getUnlockSettleInMs(context)));
        put(homeTrap, "lock_settle_until", nullableTime(AppState.getLockSettleUntil(context)));
        put(homeTrap, "lock_settle_in_ms", nullableAge(AppState.getLockSettleInMs(context)));
        put(homeTrap, "locked_age_ms", nullableAge(AppState.getLockedAgeMs(context)));
        put(homeTrap, "last_home_gap_ms", nullableAge(AppState.getHomeTrapLastGap(context)));
        put(homeTrap, "locked_home_burst_ignored", AppState.getLockedHomeBurstIgnored(context));
        put(homeTrap, "home_gap_accept_ms", AppState.getHomeGapAcceptMs());
        put(homeTrap, "lock_stabilize_restore_count", AppState.getLockStabilizeRestoreCount(context));
        put(homeTrap, "lock_stabilize_restore_max", AppState.getLockStabilizeRestoreMax());
        put(homeTrap, "screen_off_recovery_package", AppState.getScreenOffRecoveryPackage(context));
        put(homeTrap, "screen_off_recovery_until", nullableTime(AppState.getScreenOffRecoveryUntil(context)));
        put(homeTrap, "screen_off_recovery_count", AppState.getScreenOffRecoveryCount(context));
        put(homeTrap, "usage_access_enabled", ForegroundResolver.hasUsageAccess(context));
        put(homeTrap, "stay_awake_enabled", AppState.isStayAwakeEnabled(context));
        put(homeTrap, "visible_screen_type", visibleScreen.typeName());
        put(homeTrap, "visible_screen_package", visibleScreen.packageName);
        put(homeTrap, "visible_screen_source", visibleScreen.source);
        put(homeTrap, "visible_screen_event_age_ms",
                visibleScreen.eventTimeMs == 0L ? JSONObject.NULL
                        : Math.max(0L, System.currentTimeMillis() - visibleScreen.eventTimeMs));
        long cachedAt = AppState.getLastCachedAppAt(context);
        put(homeTrap, "last_cached_app", AppState.getLastCachedApp(context));
        put(homeTrap, "last_cached_app_age_ms",
                cachedAt == 0L ? JSONObject.NULL : Math.max(0L, System.currentTimeMillis() - cachedAt));
        put(homeTrap, "last_menu_seen_after_app", AppState.wasMenuSeenAfterCachedApp(context));
        long menuAfterAppAt = AppState.getLastMenuSeenAfterAppAt(context);
        put(homeTrap, "last_menu_seen_after_app_at_ms",
                menuAfterAppAt == 0L ? JSONObject.NULL : menuAfterAppAt);
        put(homeTrap, "last_menu_seen_after_app_age_ms",
                menuAfterAppAt == 0L ? JSONObject.NULL
                        : Math.max(0L, System.currentTimeMillis() - menuAfterAppAt));
        put(homeTrap, "last_navigation_seen_after_app",
                AppState.wasNavigationSeenAfterCachedApp(context));
        long navigationAfterAppAt = AppState.getLastNavigationSeenAfterAppAt(context);
        put(homeTrap, "last_navigation_seen_after_app_at_ms",
                navigationAfterAppAt == 0L ? JSONObject.NULL : navigationAfterAppAt);
        put(homeTrap, "last_navigation_seen_after_app_age_ms",
                navigationAfterAppAt == 0L ? JSONObject.NULL
                        : Math.max(0L, System.currentTimeMillis() - navigationAfterAppAt));
        put(homeTrap, "last_navigation_source", AppState.getLastNavigationSource(context));
        put(homeTrap, "armed_at_ms", armedAt == 0L ? JSONObject.NULL : armedAt);
        put(homeTrap, "armed_age_ms",
                armedAt == 0L ? JSONObject.NULL : Math.max(0L, System.currentTimeMillis() - armedAt));
        put(homeTrap, "expires_ms", expires == 0L ? JSONObject.NULL : expires);
        put(homeTrap, "expires_in_ms",
                expires == 0L ? JSONObject.NULL : Math.max(0L, expires - System.currentTimeMillis()));
        put(homeTrap, "invocations", AppState.getHomeTrapInvocations(context));
        put(homeTrap, "ignored_debounce", AppState.getHomeTrapIgnored(context));
        put(homeTrap, "last_invocation_ms", last == 0L ? JSONObject.NULL : last);
        put(homeTrap, "last_invocation_age_ms",
                last == 0L ? JSONObject.NULL : Math.max(0L, System.currentTimeMillis() - last));
        put(homeTrap, "requires_accessibility", false);
        put(homeTrap, "requires_overlay", true);
        put(homeTrap, "requires_default_home_selection", true);
        put(homeTrap, "notes", "Debe elegirse WatchGuard como launcher/Home temporal. v0.43 invalida cache al ver menu/recents/sistema para no volver a la ultima app por error.");
        return homeTrap;
    }

    private static JSONObject cameraButtonState(Context context) throws JSONException {
        JSONObject camera = new JSONObject();
        JSONArray packages = new JSONArray();
        for (String packageName : CameraShutterController.supportedPackages()) {
            packages.put(packageName);
        }
        long lastAt = AppState.getCameraShutterLastAt(context);
        put(camera, "enabled", AppState.isHomeTrapEnabled(context));
        put(camera, "packages", packages);
        put(camera, "min_interval_ms", AppState.getCameraShutterMinIntervalMs());
        put(camera, "last_shutter_at_ms", lastAt == 0L ? JSONObject.NULL : lastAt);
        put(camera, "last_shutter_age_ms",
                lastAt == 0L ? JSONObject.NULL : Math.max(0L, System.currentTimeMillis() - lastAt));
        put(camera, "last_package", AppState.getCameraShutterLastPackage(context));
        put(camera, "accepted_count", AppState.getCameraShutterAcceptedCount(context));
        put(camera, "throttled_count", AppState.getCameraShutterThrottledCount(context));
        put(camera, "backend_results", AppState.getCameraShutterBackendResults(context));
        put(camera, "notes", "v0.43: cuando la pantalla actual es camara nativa, HOME no crea overlay ni ciclo; el primer pulso abre WatchGuardCameraActivity sin disparar.");
        return camera;
    }

    private static JSONObject watchguardCameraState(Context context) throws JSONException {
        JSONObject camera = new JSONObject();
        long lastActive = AppState.getWatchCameraLastActive(context);
        long lastShotAt = AppState.getWatchCameraLastShotAt(context);
        put(camera, "camera_permission_granted", AppState.hasCameraPermission(context));
        put(camera, "public_storage_permission", AppState.hasPublicPhotoPermission(context));
        put(camera, "active", AppState.isWatchCameraActive(context));
        put(camera, "home_bridge_active", AppState.isWatchCameraHomeBridgeActive(context));
        put(camera, "home_bridge_until", nullableTime(AppState.getWatchCameraHomeBridgeUntil(context)));
        put(camera, "camera_warm_bridge_active", AppState.isWatchCameraWarmBridgeActive(context));
        put(camera, "camera_warm_bridge_until", nullableTime(AppState.getWatchCameraWarmBridgeUntil(context)));
        put(camera, "capture_in_flight", AppState.isWatchCameraCaptureInFlight(context));
        put(camera, "pending_shot", AppState.hasWatchCameraPendingShot(context));
        put(camera, "camera_generation", AppState.getWatchCameraGeneration(context));
        put(camera, "warm_closes", AppState.getWatchCameraWarmCloses(context));
        put(camera, "camera_session_id", AppState.getWatchCameraSessionId(context));
        put(camera, "camera_session_started_at_ms",
                nullableTime(AppState.getWatchCameraSessionStarted(context)));
        put(camera, "camera_session_photo_count",
                AppState.getWatchCameraSessionPhotoCount(context));
        put(camera, "camera_session_photos",
                AppState.getWatchCameraSessionPhotosRaw(context));
        put(camera, "pending_capture_count",
                AppState.getWatchCameraPendingCaptureCount(context));
        put(camera, "double_click_gpt_count",
                AppState.getWatchCameraDoubleClickCount(context));
        put(camera, "watch_camera_single_click_deadline_config_ms", 1350);
        put(camera, "watch_camera_double_click_window_ms", "25-1200");
        put(camera, "watch_camera_double_click_min_ms", 25);
        put(camera, "watch_camera_double_click_max_ms", 1200);
        put(camera, "watch_camera_double_click_buffer_ms", 150);
        put(camera, "watch_camera_double_click_last_age_ms",
                nullableAge(AppState.getWatchCameraDoubleClickLastAge(context)));
        put(camera, "watch_camera_double_click_age_source",
                AppState.getWatchCameraDoubleClickAgeSource(context));
        put(camera, "watch_camera_double_click_effective_age_ms",
                nullableAge(AppState.getWatchCameraDoubleClickEffectiveAge(context)));
        put(camera, "watch_camera_direct_key_pulse_scheduled_count",
                AppState.getWatchCameraDirectKeyPulseScheduledCount(context));
        put(camera, "watch_camera_direct_key_pulse_fired_count",
                AppState.getWatchCameraDirectKeyPulseFiredCount(context));
        put(camera, "watch_camera_direct_key_pulse_cancelled_home_count",
                AppState.getWatchCameraDirectKeyPulseCancelledHomeCount(context));
        put(camera, "watch_camera_touch_double_candidate_count",
                AppState.getWatchCameraTouchDoubleCandidateCount(context));
        put(camera, "watch_camera_touch_double_accepted_count",
                AppState.getWatchCameraTouchDoubleAcceptedCount(context));
        put(camera, "watch_camera_touch_double_last_age_ms",
                nullableAge(AppState.getWatchCameraTouchDoubleLastAge(context)));
        put(camera, "watch_camera_edge_swipe_guard_enabled", true);
        put(camera, "watch_camera_edge_swipe_top_blocked_count",
                AppState.getWatchCameraEdgeSwipeTopBlockedCount(context));
        put(camera, "watch_camera_edge_swipe_bottom_blocked_count",
                AppState.getWatchCameraEdgeSwipeBottomBlockedCount(context));
        put(camera, "watch_camera_edge_swipe_last_edge",
                AppState.getWatchCameraEdgeSwipeLastEdge(context));
        put(camera, "watch_camera_system_ui_reapply_count",
                AppState.getWatchCameraSystemUiReapplyCount(context));
        put(camera, "watch_camera_gesture_guard_active",
                AppState.isWatchCameraGestureGuardActive(context));
        put(camera, "watch_camera_gesture_guard_started_count",
                AppState.getWatchCameraGestureGuardStartedCount(context));
        put(camera, "watch_camera_gesture_guard_stopped_count",
                AppState.getWatchCameraGestureGuardStoppedCount(context));
        put(camera, "watch_camera_top_swipe_blocked_count",
                AppState.getWatchCameraTopSwipeBlockedCount(context));
        put(camera, "watch_camera_bottom_swipe_blocked_count",
                AppState.getWatchCameraBottomSwipeBlockedCount(context));
        put(camera, "watch_camera_system_panel_escape_count",
                AppState.getWatchCameraSystemPanelEscapeCount(context));
        put(camera, "watch_camera_power_key_consumed_count",
                AppState.getWatchCameraPowerKeyConsumedCount(context));
        put(camera, "watch_camera_screen_off_during_camera_count",
                AppState.getWatchCameraScreenOffDuringCameraCount(context));
        put(camera, "watch_camera_touch_shield_mode",
                AppState.getWatchCameraTouchShieldMode(context));
        put(camera, "watch_camera_touch_shield_active",
                AppState.isWatchCameraTouchShieldActive(context));
        put(camera, "watch_camera_touch_shield_started_count",
                AppState.getWatchCameraTouchShieldStartedCount(context));
        put(camera, "watch_camera_touch_shield_stopped_count",
                AppState.getWatchCameraTouchShieldStoppedCount(context));
        put(camera, "watch_camera_touch_shield_tap_proxy_count",
                AppState.getWatchCameraTouchShieldTapProxyCount(context));
        put(camera, "watch_camera_touch_proxy_enabled",
                AppState.isWatchCameraTouchProxyEnabled());
        put(camera, "watch_camera_touch_inert_enabled",
                AppState.isWatchCameraTouchInertEnabled());
        put(camera, "watch_camera_touch_absorbed_count",
                AppState.getWatchCameraTouchAbsorbedCount(context));
        put(camera, "watch_camera_system_panel_rebound_count",
                AppState.getWatchCameraSystemPanelReboundCount(context));
        put(camera, "watch_camera_system_panel_last_package",
                AppState.getWatchCameraSystemPanelLastPackage(context));
        put(camera, "watch_camera_system_panel_back_action_count",
                AppState.getWatchCameraSystemPanelBackActionCount(context));
        put(camera, "watch_camera_system_panel_fast_rebound_count",
                AppState.getWatchCameraSystemPanelFastReboundCount(context));
        put(camera, "watch_camera_system_panel_burst_count",
                AppState.getWatchCameraSystemPanelBurstCount(context));
        put(camera, "watch_camera_false_panel_event_ignored_count",
                AppState.getWatchCameraFalsePanelEventIgnoredCount(context));
        put(camera, "watch_camera_native_camera_bounce_blocked_count",
                AppState.getWatchCameraNativeCameraBounceBlockedCount(context));
        put(camera, "watch_camera_panel_back_suppressed_count",
                AppState.getWatchCameraPanelBackSuppressedCount(context));
        put(camera, "watch_camera_panel_rescue_active",
                AppState.isWatchCameraPanelRescueActive(context));
        put(camera, "watch_camera_panel_rescue_until_ms",
                nullableTime(AppState.getWatchCameraPanelRescueUntil(context)));
        put(camera, "watch_camera_panel_rescue_last_package",
                AppState.getWatchCameraPanelRescueLastPackage(context));
        put(camera, "watch_camera_panel_restore_attempt_count",
                AppState.getWatchCameraPanelRestoreAttemptCount(context));
        put(camera, "watch_camera_downmenu_restore_attempt_count",
                AppState.getWatchCameraDownmenuRestoreAttemptCount(context));
        put(camera, "watch_camera_pause_preserved_for_panel_count",
                AppState.getWatchCameraPausePreservedForPanelCount(context));
        put(camera, "watch_camera_rotary_ignored_count",
                AppState.getWatchCameraRotaryIgnoredCount(context));
        put(camera, "watch_camera_rotary_last_key",
                AppState.getWatchCameraRotaryLastKey(context));
        put(camera, "watch_camera_rotary_last_scan",
                AppState.getWatchCameraRotaryLastScan(context));
        put(camera, "watch_camera_rotary_guard_active",
                AppState.isWatchCameraRotaryGuardActive(context));
        put(camera, "watch_camera_rotary_guard_until_ms",
                nullableTime(AppState.getWatchCameraRotaryGuardUntil(context)));
        put(camera, "watch_camera_rotary_suppress_until_ms",
                nullableTime(AppState.getWatchCameraRotarySuppressUntil(context)));
        put(camera, "watch_camera_rotary_guard_count",
                AppState.getWatchCameraRotaryGuardCount(context));
        put(camera, "watch_camera_rotary_blocked_shutter_count",
                AppState.getWatchCameraRotaryBlockedShutterCount(context));
        put(camera, "watch_camera_rotary_last_source",
                AppState.getWatchCameraRotaryLastSource(context));
        put(camera, "watch_camera_rotary_variant",
                AppState.getWatchCameraRotaryVariant(context));
        put(camera, "watch_camera_rotary_cancelled_pending_count",
                AppState.getWatchCameraRotaryCancelledPendingCount(context));
        put(camera, "watch_camera_rotary_blocked_key_counts",
                AppState.getWatchCameraRotaryBlockedKeyCounts(context));
        put(camera, "watch_camera_real_button_last_key",
                AppState.getWatchCameraRealButtonLastKey(context));
        put(camera, "watch_camera_rejected_button_key",
                AppState.getWatchCameraRejectedButtonKey(context));
        put(camera, "watch_camera_gpt_share_launch_count",
                AppState.getWatchCameraGptShareLaunchCount(context));
        put(camera, "watch_camera_deactivated_for_gpt_count",
                AppState.getWatchCameraDeactivatedForGptCount(context));
        put(camera, "gpt_share_pending",
                AppState.isGptPhotoSharePending(context));
        put(camera, "gpt_share_session_id",
                AppState.getGptShareSessionId(context));
        put(camera, "gpt_share_chunk_index",
                AppState.getGptShareChunkIndex(context));
        put(camera, "gpt_share_chunk_count",
                AppState.getGptShareChunkCount(context));
        put(camera, "gpt_share_chunk_size",
                AppState.getGptShareChunkSize(context));
        put(camera, "gpt_share_last_result",
                AppState.getGptShareLastResult(context));
        put(camera, "gpt_share_no_photos_count",
                AppState.getGptShareNoPhotosCount(context));
        put(camera, "gpt_share_launch_count",
                AppState.getGptShareLaunchCount(context));
        put(camera, "gpt_share_reattach_attempt_count",
                AppState.getGptShareReattachAttemptCount(context));
        put(camera, "gpt_share_state_lost_count",
                AppState.getGptShareStateLostCount(context));
        put(camera, "gpt_share_needs_new_chat",
                AppState.doesGptShareNeedNewChat(context));
        put(camera, "gpt_share_target_mode",
                AppState.getGptShareTargetMode(context));
        put(camera, "gpt_share_target_fallback_reason",
                AppState.getGptShareTargetFallbackReason(context));
        put(camera, "gpt_share_target_policy",
                "usar chat/tarea GPT visible o existente; solo pedir chat nuevo si GPT no esta abierto ni visible");
        put(camera, "gpt_automation_sent_count",
                AppState.getGptAutomationSentCount(context));
        put(camera, "gpt_automation_fail_count",
                AppState.getGptAutomationFailCount(context));
        put(camera, "gpt_automation_last_action",
                AppState.getGptAutomationLastAction(context));
        put(camera, "gpt_automation_phase",
                AppState.getGptAutomationPhase(context));
        put(camera, "gpt_automation_gate_reason",
                AppState.getGptAutomationGateReason(context));
        put(camera, "gpt_automation_attachment_expected_count",
                AppState.getGptAutomationAttachmentExpectedCount(context));
        put(camera, "gpt_automation_attachment_evidence_count",
                AppState.getGptAutomationAttachmentEvidenceCount(context));
        put(camera, "gpt_automation_attachment_ready_streak",
                AppState.getGptAutomationAttachmentReadyStreak(context));
        put(camera, "gpt_automation_attachment_required_count",
                AppState.getGptAutomationAttachmentRequiredCount(context));
        long attachmentsReadyAt = AppState.getGptAutomationAttachmentsReadyAt(context);
        long promptReadyAt = AppState.getGptAutomationPromptReadyAt(context);
        long sendReadyBase = attachmentsReadyAt > 0L && promptReadyAt > 0L
                ? Math.max(attachmentsReadyAt, promptReadyAt)
                : 0L;
        put(camera, "gpt_automation_attachments_ready_at_ms",
                nullableTime(attachmentsReadyAt));
        put(camera, "gpt_automation_attachments_ready_age_ms",
                attachmentsReadyAt == 0L ? JSONObject.NULL
                        : Math.max(0L, System.currentTimeMillis() - attachmentsReadyAt));
        put(camera, "gpt_automation_prompt_ready_at_ms",
                nullableTime(promptReadyAt));
        put(camera, "gpt_automation_prompt_ready_age_ms",
                promptReadyAt == 0L ? JSONObject.NULL
                        : Math.max(0L, System.currentTimeMillis() - promptReadyAt));
        put(camera, "gpt_automation_send_settle_after_ready_ms", 500);
        put(camera, "gpt_automation_send_settle_remaining_ms",
                sendReadyBase == 0L ? JSONObject.NULL
                        : Math.max(0L, (sendReadyBase + 500L) - System.currentTimeMillis()));
        long sendGestureAt = AppState.getGptAutomationSendGestureAttemptedAt(context);
        put(camera, "gpt_automation_send_gesture_attempted_at_ms",
                nullableTime(sendGestureAt));
        put(camera, "gpt_automation_send_gesture_attempted_age_ms",
                sendGestureAt == 0L ? JSONObject.NULL
                        : Math.max(0L, System.currentTimeMillis() - sendGestureAt));
        long gptChunkLaunchedAt = AppState.getGptAutomationChunkLaunchedAt(context);
        put(camera, "gpt_automation_chunk_launched_at_ms", nullableTime(gptChunkLaunchedAt));
        put(camera, "gpt_automation_chunk_launched_age_ms",
                gptChunkLaunchedAt == 0L ? JSONObject.NULL
                        : Math.max(0L, System.currentTimeMillis() - gptChunkLaunchedAt));
        put(camera, "gpt_automation_safe_send_candidate",
                AppState.getGptAutomationSafeSendCandidate(context));
        put(camera, "gpt_automation_send_candidate_source",
                AppState.getGptAutomationSendCandidateSource(context));
        put(camera, "gpt_automation_send_confirmation_phase",
                AppState.getGptAutomationSendConfirmationPhase(context));
        put(camera, "gpt_automation_send_confirmed_count",
                AppState.getGptAutomationSendConfirmedCount(context));
        put(camera, "gpt_automation_send_unconfirmed_count",
                AppState.getGptAutomationSendUnconfirmedCount(context));
        put(camera, "gpt_automation_last_click_bounds",
                AppState.getGptAutomationLastClickBounds(context));
        put(camera, "gpt_automation_last_click_was_composer",
                AppState.wasGptAutomationLastClickComposer(context));
        put(camera, "gpt_automation_arrow_fallback_count",
                AppState.getGptAutomationArrowFallbackCount(context));
        long gptSendClickAt = AppState.getGptAutomationSendClickAt(context);
        put(camera, "gpt_automation_send_click_at_ms", nullableTime(gptSendClickAt));
        put(camera, "gpt_automation_send_click_age_ms",
                gptSendClickAt == 0L ? JSONObject.NULL
                        : Math.max(0L, System.currentTimeMillis() - gptSendClickAt));
        put(camera, "gpt_automation_manual_required_count",
                AppState.getGptAutomationManualRequiredCount(context));
        put(camera, "gpt_automation_accessibility_off_count",
                AppState.getGptAutomationAccessibilityOffCount(context));
        put(camera, "gpt_automation_service_not_alive_count",
                AppState.getGptAutomationServiceNotAliveCount(context));
        put(camera, "gpt_automation_accessibility_enabled",
                AppState.isGptAutomationAccessibilityEnabled(context));
        put(camera, "gpt_automation_service_alive",
                AppState.isGptAutomationServiceAlive(context));
        put(camera, "gpt_automation_process", "main");
        put(camera, "state_storage_policy", "single_process_private_prefs");
        put(camera, "gpt_automation_root_source",
                AppState.getGptAutomationRootSource(context));
        put(camera, "gpt_automation_root_package",
                AppState.getGptAutomationRootPackage(context));
        put(camera, "gpt_automation_locked_overlay_mode",
                AppState.getGptAutomationLockedOverlayMode(context));
        put(camera, "gpt_auto_lock_requested",
                AppState.isGptAutoLockRequested(context));
        put(camera, "gpt_auto_lock_active",
                AppState.isGptAutoLockActive(context));
        put(camera, "gpt_auto_lock_state",
                AppState.getGptAutoLockState(context));
        long gptAutoLockStartedAt = AppState.getGptAutoLockStartedAt(context);
        put(camera, "gpt_auto_lock_started_at_ms",
                nullableTime(gptAutoLockStartedAt));
        put(camera, "gpt_auto_lock_started_age_ms",
                gptAutoLockStartedAt == 0L ? JSONObject.NULL
                        : Math.max(0L, System.currentTimeMillis() - gptAutoLockStartedAt));
        put(camera, "gpt_auto_lock_start_count",
                AppState.getGptAutoLockStartCount(context));
        put(camera, "gpt_auto_lock_fail_count",
                AppState.getGptAutoLockFailCount(context));
        put(camera, "gpt_automation_exception_count",
                AppState.getGptAutomationExceptionCount(context));
        put(camera, "gpt_automation_last_exception",
                AppState.getGptAutomationLastException(context));
        put(camera, "open_only_count", AppState.getWatchCameraOpenOnlyCount(context));
        put(camera, "touch_ignored_count", AppState.getWatchCameraTouchIgnoredCount(context));
        put(camera, "input_noise_active", AppState.isWatchCameraInputNoiseActive(context));
        put(camera, "input_noise_until", nullableTime(AppState.getWatchCameraInputNoiseUntil(context)));
        put(camera, "input_noise_count", AppState.getWatchCameraInputNoiseCount(context));
        put(camera, "last_noise_source", AppState.getWatchCameraLastNoiseSource(context));
        put(camera, "home_ignored_noise_count",
                AppState.getWatchCameraHomeIgnoredNoiseCount(context));
        long lastHomePulseAt = AppState.getWatchCameraLastHomePulseAt(context);
        long singleClickDeadline = AppState.getWatchCameraSingleClickDeadline(context);
        put(camera, "last_home_pulse_at_ms", nullableTime(lastHomePulseAt));
        put(camera, "last_home_pulse_age_ms",
                lastHomePulseAt == 0L ? JSONObject.NULL : Math.max(0L, System.currentTimeMillis() - lastHomePulseAt));
        put(camera, "last_home_pulse_source", AppState.getWatchCameraLastHomePulseSource(context));
        put(camera, "last_home_gap_ms", nullableAge(AppState.getWatchCameraLastHomeGap(context)));
        put(camera, "last_click_elapsed_ms", nullableAge(AppState.getWatchCameraLastClickElapsed(context)));
        long singleClickArmedAt = AppState.getWatchCameraSingleClickArmedAt(context);
        put(camera, "single_click_armed", AppState.isWatchCameraSingleClickArmed(context));
        put(camera, "single_click_armed_at_ms", nullableTime(singleClickArmedAt));
        put(camera, "single_click_armed_age_ms",
                singleClickArmedAt == 0L ? JSONObject.NULL : Math.max(0L, System.currentTimeMillis() - singleClickArmedAt));
        put(camera, "single_click_deadline_ms", nullableTime(singleClickDeadline));
        put(camera, "single_click_deadline_in_ms",
                singleClickDeadline == 0L ? JSONObject.NULL : Math.max(0L, singleClickDeadline - System.currentTimeMillis()));
        put(camera, "capture_requested_count", AppState.getWatchCameraCaptureRequestedCount(context));
        put(camera, "last_block_reason", AppState.getWatchCameraLastBlockReason(context));
        put(camera, "last_active_ms", nullableTime(lastActive));
        put(camera, "last_active_age_ms",
                lastActive == 0L ? JSONObject.NULL : Math.max(0L, System.currentTimeMillis() - lastActive));
        put(camera, "min_interval_ms", AppState.getCameraShutterMinIntervalMs());
        put(camera, "last_shot_at_ms", nullableTime(lastShotAt));
        put(camera, "last_shot_age_ms",
                lastShotAt == 0L ? JSONObject.NULL : Math.max(0L, System.currentTimeMillis() - lastShotAt));
        put(camera, "photo_count", AppState.getWatchCameraPhotoCount(context));
        put(camera, "throttled_count", AppState.getWatchCameraThrottledCount(context));
        put(camera, "last_file", AppState.getWatchCameraLastFile(context));
        put(camera, "last_error", AppState.getWatchCameraLastError(context));
        put(camera, "backend", AppState.getWatchCameraBackend(context));
        put(camera, "photo_storage_backend", AppState.getWatchCameraStorageBackend(context));
        put(camera, "public_storage_fallback_count", AppState.getWatchCameraPublicFallbackCount(context));
        put(camera, "public_camera_dir", "/storage/emulated/0/DCIM/Camera");
        put(camera, "storage", "DCIM/Camera");
        put(camera, "notes", "Camara propia Camera2. v0.72: touch inerte; rescate OEM mantiene escudo y reordena la camara propia para downmenu/recents sin GLOBAL_ACTION_BACK ni camara nativa.");
        return camera;
    }

    private static JSONArray inputDevices() throws JSONException {
        JSONArray devices = new JSONArray();
        int[] ids = InputDevice.getDeviceIds();
        for (int id : ids) {
            InputDevice device = InputDevice.getDevice(id);
            if (device == null) {
                continue;
            }
            JSONObject item = new JSONObject();
            put(item, "id", device.getId());
            put(item, "name", device.getName());
            put(item, "descriptor", device.getDescriptor());
            put(item, "keyboard_type", device.getKeyboardType());
            put(item, "sources", "0x" + Integer.toHexString(device.getSources()));
            put(item, "vendor_id", device.getVendorId());
            put(item, "product_id", device.getProductId());
            devices.put(item);
        }
        return devices;
    }

    private static JSONArray keyEvents(Context context, ParseDiagnostics parseDiagnostics) {
        JSONArray events = new JSONArray();
        String raw = AppState.getKeyEvents(context);
        if (raw.length() == 0) {
            return events;
        }
        String[] lines = raw.split("\\n");
        for (String line : lines) {
            if (line.trim().length() == 0) {
                continue;
            }
            addJsonLine(events, line, "detected_key_events", parseDiagnostics);
        }
        return events;
    }

    private static JSONArray lockModeResults(
            Context context,
            ParseDiagnostics parseDiagnostics
    ) throws JSONException {
        JSONArray results = new JSONArray();
        String raw = AppState.getLockModeResults(context);
        if (raw.length() > 0) {
            String[] lines = raw.split("\\n");
            for (String line : lines) {
                if (line.trim().length() == 0) {
                    continue;
                }
                addJsonLine(results, line, "lock_mode_results", parseDiagnostics);
            }
        }
        ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        JSONObject current = new JSONObject();
        put(current, "time", utcNow());
        put(current, "mode", "current_system_state");
        put(current, "outcome", "observed");
        put(current, "lock_task_mode_state",
                activityManager == null ? -1 : activityManager.getLockTaskModeState());
        results.put(current);
        return results;
    }

    private static void addJsonLine(
            JSONArray array,
            String line,
            String source,
            ParseDiagnostics parseDiagnostics
    ) {
        try {
            array.put(new JSONObject(line));
        } catch (JSONException error) {
            parseDiagnostics.record(source, line, error);
        }
    }

    private static String utcNow() {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(new Date());
    }

    private static void put(JSONObject object, String key, Object value) throws JSONException {
        if (value instanceof String) {
            object.put(key, safeJsonString((String) value));
            return;
        }
        object.put(key, value == null ? JSONObject.NULL : value);
    }

    private static String safeJsonString(String value) {
        if (value == null) {
            return null;
        }
        StringBuilder builder = new StringBuilder(Math.min(value.length(), MAX_JSON_STRING_CHARS));
        int limit = Math.min(value.length(), MAX_JSON_STRING_CHARS);
        for (int i = 0; i < limit; i++) {
            char ch = value.charAt(i);
            if (Character.isHighSurrogate(ch)) {
                if (i + 1 < limit && Character.isLowSurrogate(value.charAt(i + 1))) {
                    builder.append(ch).append(value.charAt(i + 1));
                    i += 1;
                } else {
                    builder.append('?');
                }
            } else if (Character.isLowSurrogate(ch)) {
                builder.append('?');
            } else if (ch == 0) {
                builder.append(' ');
            } else {
                builder.append(ch);
            }
        }
        if (value.length() > MAX_JSON_STRING_CHARS) {
            builder.append("\n...[truncated ").append(value.length() - MAX_JSON_STRING_CHARS)
                    .append(" chars]");
        }
        return builder.toString();
    }

    private static Object nullableTime(long timestampMs) {
        return timestampMs <= 0L ? JSONObject.NULL : timestampMs;
    }

    private static Object nullableAge(long ageMs) {
        return ageMs < 0L ? JSONObject.NULL : ageMs;
    }

    private static Object nullableFloat(float value) {
        return value < 0f ? JSONObject.NULL : value;
    }

    private static final class ParseDiagnostics {
        int errorCount;
        String lastError;

        void record(String source, String line, JSONException error) {
            errorCount += 1;
            String sample = line == null ? "" : line.trim();
            if (sample.length() > 120) {
                sample = sample.substring(0, 120);
            }
            lastError = source + ": " + error.getMessage() + " line=" + sample;
        }
    }
}
