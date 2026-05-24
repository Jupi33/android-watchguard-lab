package com.codex.watchguard;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.WindowManager;

public class HomeTrapActivity extends Activity {
    private static final long HOME_LOCK_TIMEOUT_MS = TouchBlockerService.NO_TIMEOUT;
    private static final long ARM_GRACE_MS = 3_000L;
    private static final long CONTROL_UI_STALE_IGNORE_MS = 8_000L;
    private static final long LOCK_STABILIZE_RESTORE_DELAY_MS = 100L;
    private static final long UNLOCK_FORCED_RESTORE_DELAY_MS = 20L;
    private static final long UNLOCK_OVERLAY_DELAY_MS = 180L;
    private static final long UNLOCK_RESTORE_CHECK_DELAY_MS = 420L;
    private static final long UNLOCK_CLEAR_DELAY_MS = 900L;
    private static final long UNLOCK_SETTLE_MS = 1_200L;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
                | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
        handleHomeIntent("create");
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleHomeIntent("newIntent");
    }

    private void handleHomeIntent(String source) {
        AppState.repairModeIfDesired(this, "home_trap_" + source);
        long homeGapMs = AppState.recordHomeTrapInvocation(this);
        long keeperTick = AppState.getKeeperLastTick(this);
        if (AppState.isHomeTrapEnabled(this)
                && (keeperTick <= 0L || System.currentTimeMillis() - keeperTick > 30_000L)) {
            GuardKeeperService.start(this, "home_trap_pulse");
        }
        boolean lockedAtEntry = AppState.isLocked(this);
        boolean cyclePendingAtEntry = AppState.getLockedCyclePackage(this) != null
                || AppState.isLockedCycleMenu(this);
        AppState.setHomeTrapSessionActive(this, false);

        if (lockedAtEntry) {
            if (AppState.isUnlockSettling(this)) {
                AppState.appendLog(this, "ignored_unlock_settle remaining="
                        + AppState.getUnlockSettleInMs(this)
                        + " locked=true"
                        + " source=" + source);
                AppState.appendLockModeResult(this, "home_trap", "ignored_unlock_settle",
                        source + " locked=true until=" + AppState.getUnlockSettleUntil(this));
                finishPulse();
                return;
            }
            if (AppState.isLockSettling(this)) {
                AppState.recordLockedHomeBurstIgnored(this, homeGapMs, "lock_settle");
                AppState.appendLog(this, "lock_settle_home_ignored remaining="
                        + AppState.getLockSettleInMs(this)
                        + " gap=" + homeGapMs
                        + " locked_age=" + AppState.getLockedAgeMs(this)
                        + " source=" + source);
                AppState.appendLockModeResult(this, "home_trap", "lock_settle_home_ignored",
                        source + " remaining=" + AppState.getLockSettleInMs(this)
                                + " gap=" + homeGapMs);
                String target = AppState.getLockedCyclePackage(this);
                long activeCycleId = AppState.getHomeTrapCycleId(this);
                if (target != null && AppState.tryRecordLockStabilizeRestore(
                        this,
                        AppState.getLockStabilizeRestoreMax(),
                        "lock_settle_home")) {
                    scheduleForcedRestore(target, activeCycleId,
                            "lock_restore_forced_to_settle", 0L);
                }
                finishPulse();
                return;
            }
            if (!AppState.hasAcceptedHomeGap(homeGapMs) || !AppState.isUnlockAllowed(this)) {
                String reason = AppState.hasAcceptedHomeGap(homeGapMs)
                        ? "min_lock_age"
                        : "short_gap";
                AppState.recordLockedHomeBurstIgnored(this, homeGapMs, reason);
                AppState.appendLog(this, "unlock_burst_ignored detail=" + reason
                        + " gap=" + homeGapMs
                        + " required=" + AppState.getHomeGapAcceptMs()
                        + " allowed_in=" + AppState.getUnlockAllowedInMs(this)
                        + " locked_age=" + AppState.getLockedAgeMs(this)
                        + " source=" + source);
                AppState.appendLockModeResult(this, "home_trap", "unlock_burst_ignored",
                        source + " reason=" + reason
                                + " gap=" + homeGapMs
                                + " required=" + AppState.getHomeGapAcceptMs()
                                + " allowed_at=" + AppState.getUnlockAllowedAt(this));
                finishPulse();
                return;
            }
            long cycleId = AppState.nextHomeTrapCycle(this, source);
            AppState.appendLog(this, "unlock_gap_accepted gap=" + homeGapMs
                    + " required=" + AppState.getHomeGapAcceptMs()
                    + " locked_age=" + AppState.getLockedAgeMs(this)
                    + " cycle=" + cycleId
                    + " source=" + source);
            AppState.appendLockModeResult(this, "home_trap", "unlock_gap_accepted",
                    source + " gap=" + homeGapMs + " cycle=" + cycleId);
            unlockCycle(source, cycleId);
            finishPulse();
            return;
        }

        if (AppState.isUnlockSettling(this)) {
            AppState.appendLog(this, "ignored_unlock_settle remaining="
                    + AppState.getUnlockSettleInMs(this)
                    + " source=" + source);
            AppState.appendLockModeResult(this, "home_trap", "ignored_unlock_settle",
                    source + " until=" + AppState.getUnlockSettleUntil(this));
            finishPulse();
            return;
        }

        if (AppState.isWatchCameraHomeBridgeActive(this) || AppState.isWatchCameraActive(this)) {
            if (WatchGuardCameraActivity.handleHomeTrapPulse(this, homeGapMs, source)) {
                finishPulse();
                return;
            }
        }

        if (!lockedAtEntry && !cyclePendingAtEntry) {
            AppState.recordHomeStormPulse(this);
            if (AppState.isHomeStormThresholdReached(this)) {
                homeStormRescue(source);
                return;
            }
        }

        if (!AppState.markHomeTrapProcessed(this, source)) {
            AppState.appendLockModeResult(this, "home_trap", "ignored_debounce", source);
            if (!lockedAtEntry && !cyclePendingAtEntry) {
                delegateHomeAndFinish("debounce");
                return;
            }
            finishPulse();
            return;
        }

        if (cyclePendingAtEntry) {
            long currentCycleId = AppState.getHomeTrapCycleId(this);
            AppState.clearHomeTrapCycle(this, currentCycleId, "stale_cycle_rescue");
            AppState.appendLockModeResult(this, "home_trap", "stale_cycle_rescue", source);
            delegateHomeAndFinish("stale_cycle_rescue");
            return;
        }

        long cycleId = AppState.nextHomeTrapCycle(this, source);
        if (!AppState.isHomeTrapEnabled(this)) {
            AppState.appendLog(this, "home trap received but disabled: " + source);
            AppState.clearHomeTrapCycle(this, cycleId, "disabled_home");
            delegateHomeAndFinish("disabled");
            return;
        }

        if (handleCameraShutterIfCurrent(source, homeGapMs, cycleId)) {
            return;
        }

        if (!Settings.canDrawOverlays(this)) {
            AppState.appendLog(this, "home trap cannot lock: overlay OFF");
            AppState.clearHomeTrapCycle(this, cycleId, "overlay_missing");
            delegateHomeAndFinish("overlay_missing");
            return;
        }

        lockVisibleCycle(source, cycleId);
    }

    private boolean handleCameraShutterIfCurrent(String source, long homeGapMs, long cycleId) {
        ForegroundResolver.VisibleScreen cameraScreen = ForegroundResolver.resolveCameraScreen(this);
        if (!CameraShutterController.isCameraScreen(cameraScreen)) {
            return false;
        }
        if (AppState.isWatchCameraActive(this)
                || AppState.isWatchCameraWarmBridgeActive(this)
                || AppState.isWatchCameraHomeBridgeActive(this)
                || AppState.isWatchCameraPanelRescueActive(this)
                || AppState.isWatchCameraSystemPanelReboundRecent(this)) {
            AppState.clearHomeStorm(this, "watch_camera_native_bounce_blocked");
            AppState.clearHomeTrapCycle(this, cycleId, "watch_camera_native_bounce_blocked");
            AppState.recordWatchCameraNativeCameraBounceBlocked(
                    this,
                    cameraScreen.packageName,
                    source
            );
            finishPulse();
            return true;
        }
        if (AppState.isWatchCameraRotaryGuardActive(this)) {
            AppState.clearHomeStorm(this, "camera_rotary_guard");
            AppState.clearHomeTrapCycle(this, cycleId, "camera_rotary_guard");
            AppState.recordWatchCameraRotaryBlockedShutter(this, homeGapMs, source);
            finishPulse();
            return true;
        }
        AppState.clearHomeStorm(this, "camera_shutter");
        AppState.clearHomeTrapCycle(this, cycleId, "camera_shutter");
        CameraShutterController.handleHomePulse(this, cameraScreen, source, homeGapMs);
        finishPulse();
        return true;
    }

    private void lockVisibleCycle(String source, long cycleId) {
        ForegroundResolver.VisibleScreen screen = ForegroundResolver.resolveVisibleScreen(this);
        long armedAt = AppState.getHomeTrapArmedAt(this);
        boolean appAfterArm = screen.type == ForegroundResolver.SCREEN_APP
                && screen.eventTimeMs > 0L
                && (armedAt <= 0L || screen.eventTimeMs >= armedAt);
        boolean staleControlUiPulse = AppState.wasControlUiPausedRecently(this, CONTROL_UI_STALE_IGNORE_MS)
                && !appAfterArm;

        if (AppState.isHomeTrapArmGraceActive(this, ARM_GRACE_MS) && !appAfterArm) {
            AppState.clearHomeTrapCycle(this, cycleId, "ignored_control_ui_home");
            AppState.appendLockModeResult(this, "home_trap", "ignored_control_ui_home",
                    source + " " + screen.describe());
            AppState.appendLog(this, "home trap ignored control ui/home during arm grace "
                    + screen.describe());
            delegateHomeAndFinish("ignored_control_ui_home");
            return;
        }

        if (staleControlUiPulse) {
            AppState.clearHomeTrapCycle(this, cycleId, "ignored_control_ui_home");
            AppState.appendLockModeResult(this, "home_trap", "ignored_control_ui_home",
                    source + " " + screen.describe());
            AppState.appendLog(this, "home trap ignored control ui/home "
                    + screen.describe());
            delegateHomeAndFinish("ignored_control_ui_home");
            return;
        }

        if (screen.type == ForegroundResolver.SCREEN_UNKNOWN) {
            String cachedPackage = AppState.getStrictCachedLockPackage(this);
            if (cachedPackage != null) {
                AppState.appendLog(this, "screen_fallback_cached_app package=" + cachedPackage
                        + " from=" + screen.describe());
                AppState.appendLockModeResult(this, "home_trap", "lock_cycle_cached_app",
                        source + " package=" + cachedPackage);
                lockCachedApp(source, cycleId, cachedPackage);
                return;
            }
            AppState.clearHomeTrapCycle(this, cycleId, "ignored_unresolved");
            AppState.appendLockModeResult(this, "home_trap", "ignored_unresolved", source);
            AppState.appendLog(this, "home trap ignored: " + screen.describe());
            delegateHomeAndFinish("ignored_unresolved");
            return;
        }

        if (screen.type == ForegroundResolver.SCREEN_MENU) {
            AppState.clearHomeTrapCycle(this, cycleId, "ignored_menu_home");
            AppState.appendLockModeResult(this, "home_trap", "ignored_menu_home",
                    source + " " + screen.describe());
            AppState.appendLog(this, "home trap ignored menu/home " + screen.describe());
            AppState.appendLog(this, "ignored_menu_home delegated");
            delegateHomeAndFinish("ignored_menu_home");
            return;
        }

        String visiblePackage = screen.packageName;
        if (visiblePackage == null || visiblePackage.length() == 0) {
            AppState.clearHomeTrapCycle(this, cycleId, "ignored_unresolved");
            AppState.appendLockModeResult(this, "home_trap", "ignored_unresolved",
                    source + " app package missing");
            AppState.appendLog(this, "home trap ignored: app package missing");
            delegateHomeAndFinish("ignored_unresolved");
            return;
        }

        AppState.clearHomeStorm(this, "lock_cycle");
        AppState.recordSafeImeSnapshot(this, "lock_start", cycleId, visiblePackage);
        AppState.trySilentRestoreSafeImeDefault(this, "lock_start", cycleId);
        AppState.setLockedCycle(this, visiblePackage, false);
        AppState.appendLockModeResult(this, "home_trap", "lock_cycle",
                source + " " + screen.describe());
        AppState.markLockSettle(this, AppState.getLockSettleMs(), "lock_cycle");
        TouchBlockerService.lock(this, HOME_LOCK_TIMEOUT_MS);
        if (AppState.tryRecordLockStabilizeRestore(
                this,
                AppState.getLockStabilizeRestoreMax(),
                "lock_cycle")) {
            scheduleForcedRestore(visiblePackage, cycleId,
                    "lock_restore_forced_to_settle", LOCK_STABILIZE_RESTORE_DELAY_MS);
        }
        finishPulse();
    }

    private void lockCachedApp(String source, long cycleId, String packageName) {
        AppState.clearHomeStorm(this, "lock_cycle_cached_app");
        AppState.recordSafeImeSnapshot(this, "lock_start", cycleId, packageName);
        AppState.trySilentRestoreSafeImeDefault(this, "lock_start", cycleId);
        AppState.setLockedCycle(this, packageName, false);
        AppState.markLockSettle(this, AppState.getLockSettleMs(), "lock_cycle_cached_app");
        TouchBlockerService.lock(this, HOME_LOCK_TIMEOUT_MS);
        if (AppState.tryRecordLockStabilizeRestore(
                this,
                AppState.getLockStabilizeRestoreMax(),
                "lock_cycle_cached_app")) {
            scheduleForcedRestore(packageName, cycleId,
                    "lock_restore_forced_to_settle", LOCK_STABILIZE_RESTORE_DELAY_MS);
        }
        AppState.appendLog(this, "lock_cycle_cached_app package=" + packageName
                + " source=" + source);
        finishPulse();
    }

    private void unlockCycle(String source, long cycleId) {
        String target = AppState.getLockedCyclePackage(this);
        boolean menuCycle = AppState.isLockedCycleMenu(this);
        if (!menuCycle && (target == null || target.length() == 0)) {
            String fallbackTarget = AppState.getUnlockFallbackPackage(this);
            if (fallbackTarget != null && fallbackTarget.length() > 0) {
                target = fallbackTarget;
                AppState.setLockedCycle(this, target, false);
                AppState.recordUnlockMissingTargetRecovered(this, target, "unlock_fallback");
                if (GptPhotoShareCoordinator.GPT_PACKAGE.equals(target)) {
                    AppState.recordGptUnlockRestoreTarget(this, target);
                }
            }
        }
        AppState.appendLockModeResult(this, "home_trap", "unlock_cycle",
                source + " cycle=" + (menuCycle ? "menu" : target == null ? "none" : target));

        long restoreStartMs = System.currentTimeMillis();
        if (!menuCycle && target != null) {
            AppState.recordSafeImeSnapshot(this, "unlock_start", cycleId, target);
            AppState.trySilentRestoreSafeImeDefault(this, "unlock_start", cycleId);
            AppState.clearHomeStorm(this, "unlock_cycle");
            AppState.clearLockSettle(this, "unlock_cycle");
            AppState.markUnlockSettle(this, UNLOCK_SETTLE_MS, "unlock_cycle");
            scheduleForcedRestore(target, cycleId, "unlock_restore_forced",
                    UNLOCK_FORCED_RESTORE_DELAY_MS);
            scheduleUnlockOverlay(cycleId, UNLOCK_OVERLAY_DELAY_MS);
            scheduleConditionalRestore(target, cycleId, restoreStartMs,
                    "unlock", UNLOCK_RESTORE_CHECK_DELAY_MS);
            scheduleCycleClear(cycleId, UNLOCK_CLEAR_DELAY_MS, "unlock_complete");
            return;
        }

        AppState.appendLog(this, "unlock without app target -> rescue");
        AppState.recordUnlockMissingTargetLauncherRescue(this,
                menuCycle ? "unlock_menu_rescue" : "unlock_no_target_rescue");
        TouchBlockerService.unlock(this);
        AppState.clearHomeTrapCycle(this, cycleId, menuCycle ? "unlock_menu_rescue" : "unlock_no_target_rescue");
        delegateHomeAndFinish(menuCycle ? "unlock_menu_rescue" : "unlock_no_target_rescue");
    }

    private void scheduleForcedRestore(
            String packageName,
            long cycleId,
            String reason,
            long delayMs
    ) {
        final Context appContext = getApplicationContext();
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                long currentCycleId = AppState.getHomeTrapCycleId(appContext);
                if (currentCycleId != cycleId) {
                    AppState.appendLog(appContext, "restore_abort reason=cycle_changed"
                            + " target=" + packageName
                            + " cycle=" + cycleId
                            + " current_cycle=" + currentCycleId);
                    return;
                }
                AppState.appendLog(appContext, reason + " target=" + packageName
                        + " cycle=" + cycleId);
                if ("unlock_restore_forced".equals(reason)) {
                    AppState.recordSafeImeSnapshot(appContext, "before_restore", cycleId, packageName);
                    AppState.trySilentRestoreSafeImeDefault(appContext, "before_restore", cycleId);
                }
                ForegroundResolver.restorePackage(appContext, packageName);
            }
        }, delayMs);
    }

    private void scheduleConditionalRestore(
            String packageName,
            long cycleId,
            long restoreStartMs,
            String reason,
            long delayMs
    ) {
        final Context appContext = getApplicationContext();
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                AppState.recordSafeImeSnapshot(appContext, "post_restore_check", cycleId, packageName);
                AppState.trySilentRestoreSafeImeDefault(appContext, "post_restore_check", cycleId);
                if (shouldAbortRestore(appContext, packageName, cycleId, restoreStartMs, reason)) {
                    return;
                }

                ForegroundResolver.RestoreSignal signal =
                        ForegroundResolver.readRestoreSignalSince(appContext, restoreStartMs);
                if (packageName.equals(signal.taskPackage)
                        || packageName.equals(signal.usablePackage)) {
                    AppState.appendLog(appContext, "restore_skip target=" + packageName
                            + " reason=already_visible"
                            + " phase=" + reason
                            + " cycle=" + cycleId);
                    return;
                }
                if (signal.taskPackage != null && !packageName.equals(signal.taskPackage)) {
                    AppState.appendLog(appContext, "restore_abort reason=" + reason
                            + " target=" + packageName
                            + " current=" + signal.taskPackage
                            + " source=tasks"
                            + " cycle=" + cycleId);
                    return;
                }
                if (signal.usablePackage != null && !packageName.equals(signal.usablePackage)) {
                    AppState.appendLog(appContext, "restore_abort reason=" + reason
                            + " target=" + packageName
                            + " current=" + signal.usablePackage
                            + " source=usage"
                            + " cycle=" + cycleId);
                    return;
                }
                if (signal.rawIsHome()) {
                    AppState.appendLog(appContext, "restore_attempt reason=" + reason
                            + " target=" + packageName
                            + " raw=" + signal.rawPackage
                            + " cycle=" + cycleId);
                    ForegroundResolver.restorePackage(appContext, packageName);
                    return;
                }
                AppState.appendLog(appContext, "restore_skip target=" + packageName
                        + " reason=no_home_signal"
                        + " phase=" + reason
                        + " raw=" + signal.rawPackageLabel()
                        + " raw_age=" + signal.rawAgeMs()
                        + " cycle=" + cycleId);
            }
        }, delayMs);
    }

    private void homeStormRescue(String source) {
        AppState.resetHomeTrapRuntime(this, "home_storm_rescue");
        AppState.recordHomeStormRescue(this, source);
        AppState.appendLockModeResult(this, "home_trap", "home_storm_rescue", source);
        TouchBlockerService.unlock(this);
        delegateHomeAndFinish("home_storm_rescue");
    }

    private void delegateHomeAndFinish(String reason) {
        AppState.appendLog(this, "home trap delegate reason=" + reason);
        if (AppState.isLocked(this)) {
            TouchBlockerService.unlock(this);
        } else {
            AppState.appendLog(this, "home trap delegate unlock skipped locked=false");
        }
        ForegroundResolver.launchExternalHome(this);
        finishPulse();
    }

    private void scheduleUnlockOverlay(long cycleId, long delayMs) {
        final Context appContext = getApplicationContext();
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                long currentCycleId = AppState.getHomeTrapCycleId(appContext);
                if (currentCycleId != cycleId) {
                    AppState.appendLog(appContext, "unlock_overlay_skip cycle=" + cycleId
                            + " current=" + currentCycleId);
                    return;
                }
                AppState.recordSafeImeSnapshot(appContext, "before_overlay_off", cycleId,
                        AppState.getLockedCyclePackage(appContext));
                AppState.trySilentRestoreSafeImeDefault(appContext, "before_overlay_off", cycleId);
                TouchBlockerService.unlock(appContext, cycleId);
            }
        }, delayMs);
    }

    private void scheduleCycleClear(long cycleId, long delayMs, String reason) {
        final Context appContext = getApplicationContext();
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                if ("unlock_complete".equals(reason)) {
                    AppState.recordSafeImeSnapshot(appContext, "unlock_complete", cycleId,
                            AppState.getLockedCyclePackage(appContext));
                    AppState.trySilentRestoreSafeImeDefault(appContext, "unlock_complete", cycleId);
                    SafeImeGateService.restartAfterUnlock(appContext, "unlock_complete");
                }
                AppState.clearHomeTrapCycle(appContext, cycleId, reason);
            }
        }, delayMs);
    }

    private boolean shouldAbortRestore(
            Context context,
            String target,
            long cycleId,
            long restoreStartMs,
            String reason
    ) {
        long currentCycleId = AppState.getHomeTrapCycleId(context);
        if (currentCycleId != cycleId) {
            AppState.appendLog(context, "restore_abort reason=cycle_changed"
                    + " target=" + target
                    + " cycle=" + cycleId
                    + " current_cycle=" + currentCycleId);
            return true;
        }

        String current = ForegroundResolver.findRecentUsablePackageSince(context, restoreStartMs);
        if (current != null && !target.equals(current)) {
            AppState.appendLog(context, "restore_abort reason=" + reason
                    + " target=" + target
                    + " current=" + current
                    + " cycle=" + cycleId);
            return true;
        }
        return false;
    }

    private void finishPulse() {
        finish();
        overridePendingTransition(0, 0);
    }

    @Override
    protected void onDestroy() {
        AppState.appendLog(this, "home trap pulse destroyed locked=" + AppState.isLocked(this));
        super.onDestroy();
    }
}
