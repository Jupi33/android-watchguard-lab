package com.codex.watchguard;

import android.annotation.TargetApi;
import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.accessibilityservice.GestureDescription;
import android.content.Context;
import android.graphics.Path;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.widget.FrameLayout;

import java.util.List;
import java.util.Locale;

public class GptAutomationAccessibilityService extends AccessibilityService {
    private static final long RETRY_MS = 500L;
    private static final long MIN_ATTACHMENT_WAIT_MS = 6_000L;
    private static final long AGGREGATE_ATTACHMENT_BASE_WAIT_MS = 8_000L;
    private static final long AGGREGATE_ATTACHMENT_PER_PHOTO_MS = 650L;
    private static final long MANUAL_TIMEOUT_MS = 35_000L;
    private static final long HEARTBEAT_MS = 5_000L;
    private static final long SHARE_ATTACH_RETRY_MS = 9_500L;
    private static final long SEND_CONFIRM_CHECK_MS = 850L;
    private static final long SEND_CONFIRM_TIMEOUT_MS = 8_000L;
    private static final long SEND_SETTLE_AFTER_READY_MS = 500L;
    private static final long CAMERA_SHIELD_FAILSAFE_MS = 700L;
    private static final long CAMERA_SYSTEM_PANEL_REBOUND_THROTTLE_MS = 220L;
    private static final long[] CAMERA_PANEL_RESTORE_DELAYS_MS = new long[] {
            0L,
            250L,
            750L,
            1_500L
    };
    private static final int MAX_ATTEMPTS = 90;
    private static final int ATTACHMENT_READY_STREAK_REQUIRED = 2;
    private static final String CAMERA_SHIELD_MODE = "accessibility_overlay";

    private static GptAutomationAccessibilityService activeInstance;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private int attemptCount;
    private int activeChunk = -1;
    private int lastEvidenceCount = -1;
    private int attachmentReadyStreak;
    private long lastAttemptAtUptime;
    private long lastLockedBackReboundUptime;
    private long lastWatchCameraPanelReboundUptime;
    private WindowManager cameraShieldWindowManager;
    private View cameraTouchShieldView;
    private boolean sendGestureInFlight;
    private boolean observedPendingShare;
    private boolean cameraTouchShieldShown;

    private final Runnable attemptRunnable = new Runnable() {
        @Override
        public void run() {
            try {
                attemptAutomation();
            } catch (RuntimeException error) {
                handleAutomationException("attemptAutomation", error);
            }
        }
    };

    private final Runnable heartbeatRunnable = new Runnable() {
        @Override
        public void run() {
            AppState.recordGptAccessibilityHeartbeat(GptAutomationAccessibilityService.this);
            if (AppState.isGptPhotoSharePending(GptAutomationAccessibilityService.this)) {
                observedPendingShare = true;
                scheduleAttempt("heartbeat_pending", 120L);
            }
            syncWatchCameraTouchShield("heartbeat");
            handler.postDelayed(this, HEARTBEAT_MS);
        }
    };

    private final Runnable cameraShieldFailsafeRunnable = new Runnable() {
        @Override
        public void run() {
            if (!cameraTouchShieldShown) {
                return;
            }
            if (!isWatchCameraShieldDesired()) {
                removeWatchCameraTouchShield("failsafe");
                return;
            }
            handler.postDelayed(this, CAMERA_SHIELD_FAILSAFE_MS);
        }
    };

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        activeInstance = this;
        cameraShieldWindowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                | AccessibilityEvent.TYPE_WINDOWS_CHANGED
                | AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                | AccessibilityEvent.TYPE_VIEW_FOCUSED
                | AccessibilityEvent.TYPE_VIEW_CLICKED
                | AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
                | AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
                | AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
        info.notificationTimeout = 80L;
        setServiceInfo(info);
        AppState.recordGptAccessibilityServiceConnected(this);
        handler.removeCallbacks(heartbeatRunnable);
        handler.post(heartbeatRunnable);
        AppState.repairModeIfDesired(this, "gpt_accessibility_connected");
        if (AppState.isHomeTrapEnabled(this)) {
            GuardKeeperService.start(this, "gpt_accessibility_connected");
        }
        AppState.recordGptAutomationAction(this, "accessibility_connected");
        syncWatchCameraTouchShield("connected");
        scheduleAttempt("connected", 300L);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        try {
            AppState.recordGptAccessibilityHeartbeat(this);
            syncWatchCameraTouchShield("event");
            if (event == null || event.getPackageName() == null) {
                return;
            }
            maybeReboundLockedNavigation(event);
            maybeRecordWatchCameraSystemPanelEscape(event);
            if (!GptPhotoShareCoordinator.GPT_PACKAGE.contentEquals(event.getPackageName())) {
                return;
            }
            if (AppState.isGptPhotoSharePending(this)) {
                observedPendingShare = true;
                GptPhotoShareCoordinator.startGptAutoLockIfRequested(this,
                        "accessibility_event");
                scheduleAttempt("event_" + event.getEventType(), 220L);
            }
        } catch (RuntimeException error) {
            handleAutomationException("onAccessibilityEvent", error);
        }
    }

    @Override
    public boolean onKeyEvent(KeyEvent event) {
        try {
            if (event == null) {
                return false;
            }
            if (WatchInputClassifier.isSystemPowerKey(event)
                    && shouldConsumeCameraPowerKey()) {
                if (event.getAction() == KeyEvent.ACTION_DOWN
                        && event.getRepeatCount() == 0) {
                    AppState.recordWatchCameraPowerKeyConsumed(
                            this,
                            WatchInputClassifier.keyEventLabel(event),
                            "gpt_accessibility_camera"
                    );
                }
                return true;
            }
            if (WatchInputClassifier.isRotaryKey(event)) {
                boolean block = shouldBlockRotaryKey(event);
                if (block && event.getAction() == KeyEvent.ACTION_DOWN
                        && event.getRepeatCount() == 0) {
                    AppState.recordWatchCameraRotaryIgnored(
                            this,
                            KeyEvent.keyCodeToString(event.getKeyCode()),
                            event.getScanCode(),
                            event.getDeviceId(),
                            AppState.isLocked(this)
                                    ? "gpt_accessibility_locked"
                                    : "gpt_accessibility_camera"
                    );
                    AppState.recordWatchCameraRotaryCancelledPending(
                            this,
                            "gpt_accessibility_rotary",
                            AppState.getWatchCameraPendingCaptureCount(this) > 0
                                    || AppState.isWatchCameraSingleClickArmed(this)
                    );
                }
                return block;
            }
            if (event.getKeyCode() == KeyEvent.KEYCODE_BACK && AppState.isLocked(this)) {
                if (event.getAction() == KeyEvent.ACTION_DOWN
                        && event.getRepeatCount() == 0) {
                    AppState.recordLockedBackKeyConsumed(this, "gpt_accessibility");
                }
                return true;
            }
        } catch (RuntimeException error) {
            handleAutomationException("onKeyEvent", error);
        }
        return false;
    }

    @Override
    public void onInterrupt() {
        try {
            AppState.recordGptAutomationAction(this, "accessibility_interrupt");
            removeWatchCameraTouchShield("interrupt");
        } catch (RuntimeException error) {
            handleAutomationException("onInterrupt", error);
        }
    }

    static boolean startWatchCameraTouchShield(Context context, String reason) {
        GptAutomationAccessibilityService service = activeInstance;
        if (service == null || !AppState.isGptAutomationServiceAlive(context)) {
            return false;
        }
        service.handler.post(new Runnable() {
            @Override
            public void run() {
                service.showWatchCameraTouchShield(reason);
            }
        });
        return true;
    }

    static void stopWatchCameraTouchShield(Context context, String reason) {
        GptAutomationAccessibilityService service = activeInstance;
        if (service == null) {
            AppState.recordWatchCameraTouchShieldStopped(
                    context,
                    CAMERA_SHIELD_MODE,
                    reason + "_no_service"
            );
            return;
        }
        service.handler.post(new Runnable() {
            @Override
            public void run() {
                service.removeWatchCameraTouchShield(reason);
            }
        });
    }

    private void syncWatchCameraTouchShield(String reason) {
        if (isWatchCameraShieldDesired()) {
            showWatchCameraTouchShield(reason);
        } else if (cameraTouchShieldShown) {
            removeWatchCameraTouchShield(reason);
        }
    }

    private boolean isWatchCameraShieldDesired() {
        return AppState.isWatchCameraActive(this)
                || AppState.isWatchCameraWarmBridgeActive(this)
                || AppState.isWatchCameraHomeBridgeActive(this)
                || AppState.isWatchCameraPanelRescueActive(this);
    }

    private void showWatchCameraTouchShield(String reason) {
        if (cameraTouchShieldShown && cameraTouchShieldView != null) {
            AppState.recordWatchCameraTouchShieldStarted(this, CAMERA_SHIELD_MODE, reason);
            scheduleCameraShieldFailsafe();
            WatchCameraGestureGuardService.stop(this, "accessibility_primary");
            return;
        }
        if (cameraShieldWindowManager == null) {
            cameraShieldWindowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        }
        if (cameraShieldWindowManager == null) {
            AppState.recordWatchCameraTouchShieldStopped(this, CAMERA_SHIELD_MODE,
                    "window_manager_null");
            return;
        }
        CameraTouchShieldView shield = new CameraTouchShieldView(this, CAMERA_SHIELD_MODE);
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_FULLSCREEN,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;
        params.setTitle("WatchGuard camera accessibility shield");
        try {
            cameraShieldWindowManager.addView(shield, params);
            cameraTouchShieldView = shield;
            cameraTouchShieldShown = true;
            AppState.recordWatchCameraTouchShieldStarted(this, CAMERA_SHIELD_MODE, reason);
            AppState.appendLog(this, "watch_camera_touch_inert mode="
                    + CAMERA_SHIELD_MODE + " reason=" + reason);
            WatchCameraGestureGuardService.stop(this, "accessibility_primary");
            scheduleCameraShieldFailsafe();
        } catch (RuntimeException error) {
            cameraTouchShieldView = null;
            cameraTouchShieldShown = false;
            AppState.recordWatchCameraTouchShieldStopped(this, CAMERA_SHIELD_MODE,
                    "start_failed_" + error.getClass().getSimpleName());
            AppState.appendLog(this, "watch_camera_touch_shield_start_error "
                    + error.getClass().getSimpleName() + ": " + error.getMessage());
        }
    }

    private void removeWatchCameraTouchShield(String reason) {
        handler.removeCallbacks(cameraShieldFailsafeRunnable);
        boolean wasShown = cameraTouchShieldShown || cameraTouchShieldView != null;
        if (cameraTouchShieldView != null && cameraShieldWindowManager != null) {
            try {
                cameraShieldWindowManager.removeView(cameraTouchShieldView);
            } catch (RuntimeException ignored) {
            }
        }
        cameraTouchShieldView = null;
        cameraTouchShieldShown = false;
        if (wasShown) {
            AppState.recordWatchCameraTouchShieldStopped(this, CAMERA_SHIELD_MODE, reason);
        }
    }

    private void scheduleCameraShieldFailsafe() {
        handler.removeCallbacks(cameraShieldFailsafeRunnable);
        handler.postDelayed(cameraShieldFailsafeRunnable, CAMERA_SHIELD_FAILSAFE_MS);
    }

    private boolean shouldBlockRotaryKey(KeyEvent event) {
        if (AppState.isLocked(this)
                || AppState.isWatchCameraActive(this)
                || AppState.isWatchCameraHomeBridgeActive(this)
                || AppState.isWatchCameraWarmBridgeActive(this)
                || AppState.isWatchCameraPanelRescueActive(this)) {
            return true;
        }
        return CameraShutterController.isCameraScreen(ForegroundResolver.resolveCameraScreen(this));
    }

    private boolean shouldConsumeCameraPowerKey() {
        if (AppState.isWatchCameraActive(this)
                || AppState.isWatchCameraHomeBridgeActive(this)
                || AppState.isWatchCameraWarmBridgeActive(this)
                || AppState.isWatchCameraPanelRescueActive(this)) {
            return true;
        }
        return CameraShutterController.isCameraScreen(ForegroundResolver.resolveCameraScreen(this));
    }

    private void maybeRecordWatchCameraSystemPanelEscape(AccessibilityEvent event) {
        int type = event.getEventType();
        if (!AppState.isWatchCameraPanelDetectionArmed(this)) {
            return;
        }
        String packageName = event.getPackageName() == null
                ? ""
                : String.valueOf(event.getPackageName());
        if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            if (isWatchCameraSystemPanelPackage(packageName)) {
                AppState.recordWatchCameraFalsePanelEventIgnored(
                        this,
                        packageName,
                        type,
                        "event_type"
                );
            }
            return;
        }
        if (!isWatchCameraSystemPanelPackage(packageName)) {
            if (isWatchCameraPanelOrSystemPackage(packageName)) {
                AppState.recordWatchCameraFalsePanelEventIgnored(
                        this,
                        packageName,
                        type,
                        "package"
                );
            }
            return;
        }
        AppState.recordWatchCameraSystemPanelEscape(this, packageName);
        AppState.markWatchCameraPanelRescue(
                this,
                packageName,
                "accessibility_window_state_changed"
        );
        reboundWatchCameraFromSystemPanel(packageName);
    }

    private void reboundWatchCameraFromSystemPanel(final String packageName) {
        long now = SystemClock.uptimeMillis();
        boolean throttled = now - lastWatchCameraPanelReboundUptime >= 0L
                && now - lastWatchCameraPanelReboundUptime
                < CAMERA_SYSTEM_PANEL_REBOUND_THROTTLE_MS;
        if (throttled) {
            return;
        }
        lastWatchCameraPanelReboundUptime = now;
        AppState.recordWatchCameraSystemPanelRebound(
                this,
                packageName,
                "accessibility_window_state_changed"
        );
        AppState.recordWatchCameraSystemPanelFastRebound(
                this,
                packageName,
                "restore_only"
        );
        scheduleWatchCameraPanelRestoreAttempts(packageName);
    }

    private void scheduleWatchCameraPanelRestoreAttempts(final String packageName) {
        for (int index = 0; index < CAMERA_PANEL_RESTORE_DELAYS_MS.length; index += 1) {
            final long delayMs = CAMERA_PANEL_RESTORE_DELAYS_MS[index];
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    restoreWatchCameraFromSystemPanel(packageName, delayMs);
                }
            }, delayMs);
        }
    }

    private void restoreWatchCameraFromSystemPanel(String packageName, long delayMs) {
        if (!isWatchCameraShieldDesired()
                && !AppState.isWatchCameraPanelDetectionArmed(this)) {
            return;
        }
        AppState.recordWatchCameraPanelRestoreAttempt(this, packageName, delayMs);
        showWatchCameraTouchShield("system_panel_restore_delay_" + delayMs);
        WatchGuardCameraActivity.restoreAfterSystemPanel(this, packageName);
    }

    private boolean isWatchCameraSystemPanelPackage(String packageName) {
        if (packageName == null || packageName.length() == 0) {
            return false;
        }
        String normalized = packageName.toLowerCase(Locale.US);
        return "com.dw.downmenu".equals(normalized)
                || "com.dw.recents".equals(normalized);
    }

    private boolean isWatchCameraPanelOrSystemPackage(String packageName) {
        if (packageName == null || packageName.length() == 0) {
            return false;
        }
        String normalized = packageName.toLowerCase(Locale.US);
        return isWatchCameraSystemPanelPackage(normalized)
                || "com.android.systemui".equals(normalized)
                || "android".equals(normalized)
                || normalized.contains("recents")
                || normalized.contains("downmenu");
    }

    private void maybeReboundLockedNavigation(AccessibilityEvent event) {
        if (!AppState.isLocked(this)
                || AppState.isUnlockSettling(this)
                || event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            return;
        }
        String target = AppState.getLockedCyclePackage(this);
        if (target == null || target.length() == 0) {
            return;
        }
        String observed = event.getPackageName() == null
                ? ""
                : String.valueOf(event.getPackageName());
        if (observed.length() == 0
                || target.equals(observed)
                || getPackageName().equals(observed)
                || "com.google.android.inputmethod.latin".equals(observed)
                || "com.google.android.apps.inputmethod.hindi".equals(observed)) {
            return;
        }
        long now = SystemClock.uptimeMillis();
        if (now - lastLockedBackReboundUptime >= 0L
                && now - lastLockedBackReboundUptime < 700L) {
            return;
        }
        lastLockedBackReboundUptime = now;
        AppState.recordLockedBackRebound(this, target, observed, "window_changed_while_locked");
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!AppState.isLocked(GptAutomationAccessibilityService.this)
                        || AppState.isUnlockSettling(GptAutomationAccessibilityService.this)) {
                    return;
                }
                ForegroundResolver.restorePackage(GptAutomationAccessibilityService.this, target);
                if (AppState.LOCK_MODE_GPT_AUTOMATION.equals(
                        AppState.getGptAutomationLockedOverlayMode(
                                GptAutomationAccessibilityService.this))) {
                    TouchBlockerService.lockGptAutomation(
                            GptAutomationAccessibilityService.this,
                            TouchBlockerService.NO_TIMEOUT
                    );
                } else {
                    TouchBlockerService.lock(
                            GptAutomationAccessibilityService.this,
                            TouchBlockerService.NO_TIMEOUT
                    );
                }
            }
        }, 80L);
    }

    @Override
    public void onDestroy() {
        if (activeInstance == this) {
            activeInstance = null;
        }
        removeWatchCameraTouchShield("accessibility_destroy");
        handler.removeCallbacksAndMessages(null);
        AppState.recordGptAccessibilityServiceDestroyed(this);
        AppState.recordGptAutomationAction(this, "accessibility_destroy");
        super.onDestroy();
    }

    private void scheduleAttempt(String reason, long delayMs) {
        try {
            if (!AppState.isGptPhotoSharePending(this)) {
                markStateLostIfNeeded("schedule_" + reason);
                return;
            }
            observedPendingShare = true;
            int chunk = AppState.getGptShareChunkIndex(this);
            if (chunk != activeChunk) {
                activeChunk = chunk;
                attemptCount = 0;
                lastEvidenceCount = -1;
                attachmentReadyStreak = 0;
            }
            AppState.recordGptAutomationAction(this, "schedule " + reason + " chunk=" + chunk);
            handler.removeCallbacks(attemptRunnable);
            handler.postDelayed(attemptRunnable, Math.max(0L, delayMs));
        } catch (RuntimeException error) {
            handleAutomationException("scheduleAttempt", error);
        }
    }

    private void attemptAutomation() {
        if (!AppState.isGptPhotoSharePending(this)) {
            markStateLostIfNeeded("attempt_pending_missing");
            resetLocalState();
            return;
        }
        observedPendingShare = true;
        if (sendGestureInFlight) {
            scheduleAttempt("send_gesture_in_flight", RETRY_MS);
            return;
        }
        long now = SystemClock.uptimeMillis();
        if (now - lastAttemptAtUptime >= 0L && now - lastAttemptAtUptime < 120L) {
            scheduleAttempt("throttle", RETRY_MS);
            return;
        }
        lastAttemptAtUptime = now;
        attemptCount += 1;
        RootResult rootResult = findGptRoot();
        AccessibilityNodeInfo root = rootResult.node;
        if (root == null) {
            AppState.recordGptAutomationRoot(this, "missing", null);
            retryOrManual("root_missing", 0, 0, null);
            return;
        }
        AppState.recordGptAutomationRoot(this, rootResult.source, rootResult.packageName);
        SendCandidate sendCandidate = null;
        try {
            int chunk = AppState.getGptShareChunkIndex(this);
            if (chunk != activeChunk) {
                activeChunk = chunk;
                lastEvidenceCount = -1;
                attachmentReadyStreak = 0;
            }

            if (AppState.doesGptShareNeedNewChat(this)) {
                if (clickTextLike(root, "new chat")
                        || clickTextLike(root, "nuevo chat")
                        || clickTextLike(root, "chat nuevo")) {
                    AppState.clearGptShareNeedsNewChat(this, "clicked_new_chat");
                    scheduleAttempt("new_chat_clicked", 900L);
                    return;
                }
                if (attemptCount >= 5) {
                    AppState.clearGptShareNeedsNewChat(this, "new_chat_not_found");
                }
            }

            int expected = AppState.getGptShareChunkSize(this);
            long chunkLaunchedAt = AppState.getGptAutomationChunkLaunchedAt(this);
            if (chunkLaunchedAt <= 0L || expected <= 0) {
                retryOrManual("share_intent_missing", Math.max(0, expected), 0, null);
                return;
            }

            long elapsedSinceLaunch = Math.max(0L, System.currentTimeMillis() - chunkLaunchedAt);
            ScanState scan = new ScanState();
            scanTree(root, scan);
            int evidence = Math.min(scan.attachmentEvidenceCount, expected);
            String prompt = AppState.getGptSharePrompt(this);
            if (AppState.isGptAutomationSendClickPending(this)) {
                if (handlePendingSendConfirmation(root, prompt, expected, evidence,
                        elapsedSinceLaunch)) {
                    return;
                }
            }
            updateAttachmentStreak(evidence, scan.uploadingVisible);
            String candidateDescription = scan.audioCandidateDescription;

            if (elapsedSinceLaunch < MIN_ATTACHMENT_WAIT_MS) {
                if (manualIfTimedOut(root, "attachments_min_wait", elapsedSinceLaunch,
                        expected, evidence, candidateDescription)) {
                    return;
                }
                gateAndRetry("waiting_attachments", "attachments_min_wait",
                        expected, evidence, candidateDescription);
                return;
            }

            if (scan.uploadingVisible) {
                if (manualIfTimedOut(root, "uploading_visible", elapsedSinceLaunch,
                        expected, evidence, candidateDescription)) {
                    return;
                }
                gateAndRetry("waiting_attachments", "uploading_visible",
                        expected, evidence, candidateDescription);
                return;
            }

            int requiredEvidence = Math.max(1, expected);
            boolean exactAttachmentsReady = evidence >= requiredEvidence
                    && attachmentReadyStreak >= ATTACHMENT_READY_STREAK_REQUIRED;
            boolean aggregateAttachmentsReady = evidence > 0
                    && attachmentReadyStreak >= ATTACHMENT_READY_STREAK_REQUIRED
                    && elapsedSinceLaunch >= aggregateAttachmentWaitMs(expected);
            if (!exactAttachmentsReady && !aggregateAttachmentsReady) {
                String reason;
                if (evidence <= 0) {
                    reason = "attachment_not_confirmed";
                    if (elapsedSinceLaunch >= SHARE_ATTACH_RETRY_MS
                            && AppState.tryRecordGptShareReattach(this, chunk,
                            "share_not_attached")) {
                        GptPhotoShareCoordinator.launchPendingChunk(this, chunk,
                                "share_chunk_reattach");
                        scheduleAttempt("share_chunk_reattach", 1_200L);
                        return;
                    }
                } else if (elapsedSinceLaunch < aggregateAttachmentWaitMs(expected)) {
                    reason = "attachments_not_ready";
                } else {
                    reason = "attachments_not_stable";
                }
                if (manualIfTimedOut(root, reason, elapsedSinceLaunch,
                        expected, evidence, candidateDescription)) {
                    return;
                }
                gateAndRetry("waiting_attachments", reason,
                        expected, evidence, candidateDescription, requiredEvidence);
                return;
            }
            String attachmentsReadyReason = exactAttachmentsReady
                    ? "attachments_ready_exact"
                    : "attachments_ready_aggregate";
            AppState.markGptAutomationAttachmentsReady(this, attachmentsReadyReason);
            AppState.recordGptAutomationGate(this, "attachments_ready", attachmentsReadyReason,
                    expected, evidence, attachmentReadyStreak,
                    candidateDescription, requiredEvidence);

            if (!ensurePrompt(root, prompt)) {
                if (manualIfTimedOut(root, "composer_missing", elapsedSinceLaunch,
                        expected, evidence, candidateDescription)) {
                    return;
                }
                gateAndRetry("waiting_composer", "composer_missing",
                        expected, evidence, candidateDescription);
                return;
            }
            if (!composerContainsPrompt(root, prompt)) {
                gateAndRetry("waiting_composer", "prompt_not_reflected",
                        expected, evidence, candidateDescription);
                return;
            }
            AppState.markGptAutomationPromptReady(this);

            long settleRemaining = sendSettleRemainingMs();
            if (settleRemaining > 0L) {
                AppState.recordGptAutomationGate(this, "ready_to_send",
                        "send_settle_after_attachments",
                        expected, evidence, attachmentReadyStreak,
                        candidateDescription, requiredEvidence);
                scheduleAttempt("send_settle_after_attachments",
                        Math.min(RETRY_MS, settleRemaining));
                return;
            }

            Rect composerBounds = findBestEditableBounds(root);
            recordUnlabeledSendZoneCandidates(root, composerBounds);
            sendCandidate = findSafeSendCandidate(root, composerBounds);
            ScanState sendScan = new ScanState();
            scanTree(root, sendScan);
            candidateDescription = sendScan.audioCandidateDescription;
            if (sendScan.audioVisible) {
                String reason = "send_blocked_audio_visible";
                if (manualIfTimedOut(root, reason, elapsedSinceLaunch,
                        expected, evidence, candidateDescription)) {
                    return;
                }
                gateAndRetry("waiting_send", reason,
                        expected, evidence, candidateDescription, requiredEvidence);
                return;
            }
            if (sendCandidate == null) {
                if (dispatchArrowSendGesture(composerBounds, expected, evidence,
                        attachmentReadyStreak, requiredEvidence)) {
                    return;
                }
                if (AppState.hasGptAutomationSendGestureAttempted(this)) {
                    AppState.recordGptAutomationSendUnconfirmed(this,
                            "send_unconfirmed_no_retry");
                    AppState.recordGptAutomationGate(this, "manual_required",
                            "send_unconfirmed_no_retry",
                            expected, evidence, attachmentReadyStreak,
                            candidateDescription, requiredEvidence);
                    AppState.markGptAutomationManualRequired(this,
                            "send_unconfirmed_no_retry");
                    attemptCount = 0;
                    return;
                }
                String reason = "send_missing";
                if (manualIfTimedOut(root, reason, elapsedSinceLaunch,
                        expected, evidence, candidateDescription)) {
                    return;
                }
                gateAndRetry("waiting_send", reason,
                        expected, evidence, candidateDescription, requiredEvidence);
                return;
            }

            String sendReadyReason = sendCandidate.isArrowFallback()
                    ? "send_arrow_candidate"
                    : "safe_send_candidate";
            AppState.recordGptAutomationGate(this, "ready_to_send", sendReadyReason,
                    expected, evidence, attachmentReadyStreak,
                    sendCandidate.description, requiredEvidence);
            AppState.recordGptAutomationSendCandidate(this,
                    sendCandidate.source, sendCandidate.description);
            if (click(sendCandidate.node)) {
                if (sendCandidate.isArrowFallback()) {
                    AppState.recordGptAutomationArrowFallback(this, sendCandidate.source);
                }
                String clickBounds = boundsString(sendCandidate.node);
                boolean wasComposer = isComposerLikeClick(boundsOf(sendCandidate.node),
                        composerBounds);
                AppState.recordGptAutomationSendClick(this, sendCandidate.source,
                        clickBounds, wasComposer);
                AppState.markGptAutomationSendClickPending(this, sendCandidate.source,
                        clickBounds, wasComposer);
                AppState.recordGptAutomationGate(this, "send_click_pending",
                        "awaiting_send_confirmation",
                        expected, evidence, attachmentReadyStreak,
                        sendCandidate.description, requiredEvidence);
                scheduleAttempt("send_click_pending", SEND_CONFIRM_CHECK_MS);
                return;
            }
            retryOrManual("safe_send_click_failed", expected, evidence, sendCandidate.description);
        } finally {
            if (sendCandidate != null) {
                sendCandidate.recycle();
            }
            root.recycle();
        }
    }

    private RootResult findGptRoot() {
        AccessibilityNodeInfo activeRoot = null;
        try {
            activeRoot = getRootInActiveWindow();
            if (isGptTree(activeRoot)) {
                return new RootResult(activeRoot, "active_window",
                        packageNameOf(activeRoot));
            }
        } catch (RuntimeException error) {
            AppState.recordGptAutomationException(this, "find_active_root", error);
        }

        try {
            List<AccessibilityWindowInfo> windows = getWindows();
            if (windows != null) {
                for (AccessibilityWindowInfo window : windows) {
                    AccessibilityNodeInfo windowRoot = null;
                    try {
                        windowRoot = window == null ? null : window.getRoot();
                        if (isGptTree(windowRoot)) {
                            if (activeRoot != null) {
                                activeRoot.recycle();
                            }
                            return new RootResult(windowRoot, "interactive_window",
                                    packageNameOf(windowRoot));
                        }
                    } catch (RuntimeException error) {
                        AppState.recordGptAutomationException(this,
                                "find_window_root", error);
                    } finally {
                        if (windowRoot != null && !isGptTree(windowRoot)) {
                            windowRoot.recycle();
                        }
                    }
                }
                AppState.recordGptAutomationAction(this,
                        "root_missing windows=" + windowPackagesSummary(windows));
            }
        } catch (RuntimeException error) {
            AppState.recordGptAutomationException(this, "get_windows", error);
        }

        if (activeRoot != null) {
            activeRoot.recycle();
        }
        return new RootResult(null, "missing", null);
    }

    private boolean isGptNode(AccessibilityNodeInfo node) {
        return GptPhotoShareCoordinator.GPT_PACKAGE.equals(packageNameOf(node));
    }

    private boolean isGptTree(AccessibilityNodeInfo node) {
        if (node == null) {
            return false;
        }
        if (isGptNode(node)) {
            return true;
        }
        int childCount = Math.min(node.getChildCount(), 12);
        for (int i = 0; i < childCount; i++) {
            AccessibilityNodeInfo child = null;
            try {
                child = node.getChild(i);
                if (isGptNode(child)) {
                    return true;
                }
            } catch (RuntimeException ignored) {
                // Best effort only; failing child reads should not stop automation.
            } finally {
                if (child != null) {
                    child.recycle();
                }
            }
        }
        return false;
    }

    private String windowPackagesSummary(List<AccessibilityWindowInfo> windows) {
        if (windows == null || windows.isEmpty()) {
            return "none";
        }
        StringBuilder builder = new StringBuilder();
        int count = Math.min(windows.size(), 8);
        for (int i = 0; i < count; i++) {
            AccessibilityNodeInfo root = null;
            try {
                root = windows.get(i) == null ? null : windows.get(i).getRoot();
                if (builder.length() > 0) {
                    builder.append(',');
                }
                builder.append(packageNameOf(root));
            } catch (RuntimeException error) {
                if (builder.length() > 0) {
                    builder.append(',');
                }
                builder.append("error");
            } finally {
                if (root != null) {
                    root.recycle();
                }
            }
        }
        return builder.length() == 0 ? "empty" : builder.toString();
    }

    private String packageNameOf(AccessibilityNodeInfo node) {
        if (node == null || node.getPackageName() == null) {
            return null;
        }
        return node.getPackageName().toString();
    }

    private static final class RootResult {
        final AccessibilityNodeInfo node;
        final String source;
        final String packageName;

        RootResult(AccessibilityNodeInfo node, String source, String packageName) {
            this.node = node;
            this.source = source;
            this.packageName = packageName;
        }
    }

    private void handleAutomationException(String source, RuntimeException error) {
        sendGestureInFlight = false;
        AppState.recordGptAutomationException(this, source, error);
        if (AppState.isGptPhotoSharePending(this)) {
            AppState.recordGptAutomationGate(this, "retry_exception",
                    source == null ? "exception" : source,
                    AppState.getGptShareChunkSize(this),
                    AppState.getGptAutomationAttachmentEvidenceCount(this),
                    attachmentReadyStreak,
                    AppState.getGptAutomationSafeSendCandidate(this),
                    AppState.getGptAutomationAttachmentRequiredCount(this));
            handler.removeCallbacks(attemptRunnable);
            handler.postDelayed(attemptRunnable, RETRY_MS);
        }
    }

    private boolean handlePendingSendConfirmation(
            AccessibilityNodeInfo root,
            String prompt,
            int expected,
            int evidence,
            long elapsedSinceLaunch
    ) {
        long pendingAt = AppState.getGptAutomationSendPendingAt(this);
        long pendingAge = pendingAt <= 0L ? 0L : Math.max(0L,
                System.currentTimeMillis() - pendingAt);
        ScanState scan = new ScanState();
        scanTree(root, scan);
        if (scan.audioVisible) {
            AppState.recordGptAutomationSendUnconfirmed(this, "send_triggered_audio_risk");
            AppState.recordGptAutomationGate(this, "manual_required",
                    "send_triggered_audio_risk",
                    expected, evidence, attachmentReadyStreak,
                    scan.audioCandidateDescription,
                    Math.max(1, expected));
            AppState.markGptAutomationManualRequired(this, "send_triggered_audio_risk");
            attemptCount = 0;
            return true;
        }
        if (isSendConfirmed(root, prompt, evidence, pendingAge)) {
            String source = AppState.getGptAutomationSendPendingSource(this);
            AppState.recordGptAutomationSendConfirmed(this,
                    "send_confirmed_composer_cleared");
            AppState.recordGptAutomationGate(this, "sent",
                    "send_confirmed_composer_cleared",
                    expected, evidence, attachmentReadyStreak,
                    AppState.getGptAutomationSafeSendCandidate(this),
                    Math.max(1, expected));
            GptPhotoShareCoordinator.afterAutomationSend(this,
                    "accessibility_confirmed_"
                            + (source == null ? "send" : source));
            attemptCount = 0;
            return true;
        }
        if (pendingAge >= SEND_CONFIRM_TIMEOUT_MS || elapsedSinceLaunch >= MANUAL_TIMEOUT_MS) {
            AppState.recordGptAutomationSendUnconfirmed(this, "send_unconfirmed_no_retry");
            AppState.recordGptAutomationGate(this, "manual_required",
                    "send_unconfirmed_no_retry",
                    expected, evidence, attachmentReadyStreak,
                    AppState.getGptAutomationSafeSendCandidate(this),
                    Math.max(1, expected));
            AppState.markGptAutomationManualRequired(this, "send_unconfirmed_no_retry");
            attemptCount = 0;
            return true;
        }
        AppState.recordGptAutomationGate(this, "send_click_pending",
                "awaiting_send_confirmation",
                expected, evidence, attachmentReadyStreak,
                AppState.getGptAutomationSafeSendCandidate(this),
                Math.max(1, expected));
        scheduleAttempt("send_click_pending", SEND_CONFIRM_CHECK_MS);
        return true;
    }

    private boolean isSendConfirmed(
            AccessibilityNodeInfo root,
            String prompt,
            int evidence,
            long pendingAge
    ) {
        if (!composerContainsPrompt(root, prompt)) {
            AppState.recordGptAutomationAction(this,
                    "send_confirmed_composer_cleared");
            return true;
        }
        return false;
    }

    private void resetLocalState() {
        attemptCount = 0;
        activeChunk = -1;
        lastEvidenceCount = -1;
        attachmentReadyStreak = 0;
        sendGestureInFlight = false;
        observedPendingShare = false;
    }

    private void markStateLostIfNeeded(String reason) {
        if (!observedPendingShare) {
            return;
        }
        String phase = AppState.getGptAutomationPhase(this);
        if ("complete".equals(phase) || "manual_required".equals(phase)) {
            observedPendingShare = false;
            return;
        }
        AppState.markGptShareStateLost(this, reason);
        observedPendingShare = false;
    }

    private void updateAttachmentStreak(int evidence, boolean uploadingVisible) {
        if (uploadingVisible || evidence <= 0) {
            lastEvidenceCount = evidence;
            attachmentReadyStreak = 0;
            return;
        }
        if (evidence == lastEvidenceCount) {
            attachmentReadyStreak += 1;
        } else {
            attachmentReadyStreak = 1;
            lastEvidenceCount = evidence;
        }
    }

    private long aggregateAttachmentWaitMs(int expected) {
        int photoCount = Math.max(1, Math.min(10, expected));
        return AGGREGATE_ATTACHMENT_BASE_WAIT_MS
                + (photoCount * AGGREGATE_ATTACHMENT_PER_PHOTO_MS);
    }

    private long sendSettleRemainingMs() {
        long attachmentsReadyAt = AppState.getGptAutomationAttachmentsReadyAt(this);
        long promptReadyAt = AppState.getGptAutomationPromptReadyAt(this);
        if (attachmentsReadyAt <= 0L || promptReadyAt <= 0L) {
            return SEND_SETTLE_AFTER_READY_MS;
        }
        long readyAt = Math.max(attachmentsReadyAt, promptReadyAt);
        long sendAllowedAt = readyAt + SEND_SETTLE_AFTER_READY_MS;
        return Math.max(0L, sendAllowedAt - System.currentTimeMillis());
    }

    private boolean manualIfTimedOut(
            AccessibilityNodeInfo root,
            String reason,
            long elapsedSinceLaunch,
            int expected,
            int evidence,
            String candidateDescription
    ) {
        if (elapsedSinceLaunch < MANUAL_TIMEOUT_MS) {
            return false;
        }
        ensurePrompt(root, AppState.getGptSharePrompt(this));
        AppState.recordGptAutomationGate(this, "manual_required", reason,
                expected, evidence, attachmentReadyStreak, candidateDescription);
        AppState.markGptAutomationManualRequired(this, reason);
        attemptCount = 0;
        return true;
    }

    private void gateAndRetry(
            String phase,
            String reason,
            int expected,
            int evidence,
            String candidateDescription
    ) {
        gateAndRetry(phase, reason, expected, evidence, candidateDescription, expected);
    }

    private void gateAndRetry(
            String phase,
            String reason,
            int expected,
            int evidence,
            String candidateDescription,
            int requiredEvidence
    ) {
        AppState.recordGptAutomationGate(this, phase, reason,
                expected, evidence, attachmentReadyStreak, candidateDescription, requiredEvidence);
        scheduleAttempt(reason, RETRY_MS);
    }

    private void retryOrManual(
            String reason,
            int expected,
            int evidence,
            String candidateDescription
    ) {
        AppState.recordGptAutomationGate(this, "waiting", reason,
                expected, evidence, attachmentReadyStreak, candidateDescription);
        if (attemptCount >= MAX_ATTEMPTS) {
            AppState.markGptAutomationManualRequired(this, reason);
            attemptCount = 0;
            return;
        }
        scheduleAttempt(reason, RETRY_MS);
    }

    private boolean ensurePrompt(AccessibilityNodeInfo root, String prompt) {
        if (prompt == null || prompt.length() == 0) {
            return true;
        }
        AccessibilityNodeInfo editable = findBestEditable(root);
        if (editable == null) {
            return false;
        }
        try {
            if (textContains(editable, prompt)) {
                return true;
            }
            return setNodeText(editable, prompt);
        } finally {
            editable.recycle();
        }
    }

    private boolean composerContainsPrompt(AccessibilityNodeInfo root, String prompt) {
        if (prompt == null || prompt.length() == 0) {
            return false;
        }
        AccessibilityNodeInfo editable = findBestEditable(root);
        if (editable == null) {
            return false;
        }
        try {
            return textContains(editable, prompt);
        } finally {
            editable.recycle();
        }
    }

    private boolean setNodeText(AccessibilityNodeInfo node, String text) {
        if (node == null || text == null) {
            return false;
        }
        Bundle args = new Bundle();
        args.putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                text
        );
        try {
            boolean ok = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
            AppState.recordGptAutomationAction(this, "set_text ok=" + ok);
            return ok;
        } catch (RuntimeException error) {
            AppState.recordGptAutomationFailure(this,
                    "set_text:" + error.getClass().getSimpleName());
            return false;
        }
    }

    private AccessibilityNodeInfo findBestEditable(AccessibilityNodeInfo root) {
        return findBestEditable(root, null);
    }

    private Rect findBestEditableBounds(AccessibilityNodeInfo root) {
        AccessibilityNodeInfo editable = findBestEditable(root);
        if (editable == null) {
            return null;
        }
        try {
            Rect rect = new Rect();
            editable.getBoundsInScreen(rect);
            return rect;
        } finally {
            editable.recycle();
        }
    }

    private AccessibilityNodeInfo findBestEditable(AccessibilityNodeInfo node, AccessibilityNodeInfo best) {
        if (node == null) {
            return best;
        }
        AccessibilityNodeInfo nextBest = best;
        if (node.isVisibleToUser() && node.isEnabled() && looksEditable(node)) {
            if (nextBest == null || bottom(node) > bottom(nextBest)) {
                if (nextBest != null) {
                    nextBest.recycle();
                }
                nextBest = AccessibilityNodeInfo.obtain(node);
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child == null) {
                continue;
            }
            nextBest = findBestEditable(child, nextBest);
            child.recycle();
        }
        return nextBest;
    }

    private boolean looksEditable(AccessibilityNodeInfo node) {
        String className = nodeClassName(node);
        return node.isEditable()
                || className.contains("edittext")
                || className.contains("textinput")
                || className.contains("webedittext");
    }

    private SendCandidate findSafeSendCandidate(AccessibilityNodeInfo root, Rect composerBounds) {
        return findSafeSendCandidate(root, null, composerBounds);
    }

    private SendCandidate findSafeSendCandidate(
            AccessibilityNodeInfo node,
            SendCandidate best,
            Rect composerBounds
    ) {
        if (node == null) {
            return best;
        }
        SendCandidate nextBest = best;
        String label = nodeLabel(node);
        if (node.isVisibleToUser() && node.isEnabled() && isSendLabel(label)) {
            AccessibilityNodeInfo clickable = clickableNode(node);
            if (clickable != null) {
                Rect rect = boundsOf(clickable);
                if (!looksExplicitSendCandidate(label, rect, composerBounds)) {
                    AppState.recordGptAutomationAction(this,
                            "send_candidate_rejected_composer_bounds "
                                    + describeBounds(rect));
                    clickable.recycle();
                } else {
                    SendCandidate candidate = new SendCandidate(
                            clickable,
                            describeNode(clickable, label),
                            "explicit_label"
                    );
                    if (nextBest == null || bottom(candidate.node) > bottom(nextBest.node)) {
                        if (nextBest != null) {
                            nextBest.recycle();
                        }
                        nextBest = candidate;
                    } else {
                        candidate.recycle();
                    }
                }
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child == null) {
                continue;
            }
            nextBest = findSafeSendCandidate(child, nextBest, composerBounds);
            child.recycle();
        }
        return nextBest;
    }

    private SendCandidate findArrowSendCandidate(AccessibilityNodeInfo root, Rect composerBounds) {
        return findArrowSendCandidate(root, null, composerBounds);
    }

    private void recordUnlabeledSendZoneCandidates(AccessibilityNodeInfo root, Rect composerBounds) {
        recordUnlabeledSendZoneCandidates(root, composerBounds, 0);
    }

    private int recordUnlabeledSendZoneCandidates(
            AccessibilityNodeInfo node,
            Rect composerBounds,
            int logged
    ) {
        if (node == null || logged >= 3) {
            return logged;
        }
        int nextLogged = logged;
        if (node.isVisibleToUser() && node.isEnabled() && node.isClickable()) {
            Rect rect = new Rect();
            node.getBoundsInScreen(rect);
            String label = nodeLabel(node);
            if (label.length() == 0 && looksArrowSendCandidate(label, rect, composerBounds)) {
                AppState.recordGptAutomationAction(this,
                        "send_candidate_rejected_unlabeled " + describeBounds(rect));
                nextLogged += 1;
            }
        }
        for (int i = 0; i < node.getChildCount() && nextLogged < 3; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child == null) {
                continue;
            }
            nextLogged = recordUnlabeledSendZoneCandidates(child, composerBounds, nextLogged);
            child.recycle();
        }
        return nextLogged;
    }

    private SendCandidate findArrowSendCandidate(
            AccessibilityNodeInfo node,
            SendCandidate best,
            Rect composerBounds
    ) {
        if (node == null) {
            return best;
        }
        SendCandidate nextBest = best;
        if (node.isVisibleToUser() && node.isEnabled() && node.isClickable()) {
            Rect rect = new Rect();
            node.getBoundsInScreen(rect);
            String label = nodeLabel(node);
            if (looksArrowSendCandidate(label, rect, composerBounds)) {
                if (label.length() == 0) {
                    AppState.recordGptAutomationAction(this,
                            "send_candidate_rejected_unlabeled " + describeBounds(rect));
                    return nextBest;
                }
                SendCandidate candidate = new SendCandidate(
                        AccessibilityNodeInfo.obtain(node),
                        describeNode(node, label),
                        "arrow_node"
                );
                if (nextBest == null || arrowScore(rect) > arrowScore(boundsOf(nextBest.node))) {
                    if (nextBest != null) {
                        nextBest.recycle();
                    }
                    nextBest = candidate;
                } else {
                    candidate.recycle();
                }
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child == null) {
                continue;
            }
            nextBest = findArrowSendCandidate(child, nextBest, composerBounds);
            child.recycle();
        }
        return nextBest;
    }

    private boolean looksArrowSendCandidate(String label, Rect rect, Rect composerBounds) {
        if (isUnsafeArrowControl(label)) {
            return false;
        }
        int width = getResources().getDisplayMetrics().widthPixels;
        int height = getResources().getDisplayMetrics().heightPixels;
        int nodeWidth = rect.width();
        int nodeHeight = rect.height();
        if (nodeWidth < 24 || nodeHeight < 24 || nodeWidth > 76 || nodeHeight > 76) {
            return false;
        }
        boolean inComposerY;
        boolean rightOfComposer;
        if (composerBounds != null && !composerBounds.isEmpty()) {
            inComposerY = rect.centerY() >= composerBounds.top - 28
                    && rect.centerY() <= composerBounds.bottom + 28;
            rightOfComposer = rect.centerX() >= composerBounds.centerX()
                    && rect.centerX() > width * 0.70f;
        } else {
            inComposerY = rect.centerY() > height * 0.66f;
            rightOfComposer = rect.centerX() > width * 0.72f;
        }
        return inComposerY && rightOfComposer && rect.centerY() < height - 4;
    }

    private boolean looksExplicitSendCandidate(String label, Rect rect, Rect composerBounds) {
        if (!looksArrowSendCandidate(label, rect, composerBounds)) {
            return false;
        }
        return !isComposerLikeClick(rect, composerBounds);
    }

    private boolean isComposerLikeClick(Rect rect, Rect composerBounds) {
        if (rect == null || rect.isEmpty()) {
            return false;
        }
        int width = getResources().getDisplayMetrics().widthPixels;
        if (rect.width() > 96 || rect.width() > width * 0.34f) {
            return true;
        }
        if (composerBounds == null || composerBounds.isEmpty()) {
            return false;
        }
        boolean overlapsComposer = rect.left <= composerBounds.right
                && rect.right >= composerBounds.left
                && rect.top <= composerBounds.bottom
                && rect.bottom >= composerBounds.top;
        return overlapsComposer
                && rect.width() >= composerBounds.width() * 0.60f
                && rect.height() >= composerBounds.height() * 0.60f;
    }

    @TargetApi(Build.VERSION_CODES.N)
    private boolean dispatchArrowSendGesture(
            Rect composerBounds,
            final int expected,
            final int evidence,
            final int readyStreak,
            final int requiredEvidence
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N || sendGestureInFlight) {
            return false;
        }
        if (AppState.hasGptAutomationSendGestureAttempted(this)) {
            AppState.recordGptAutomationAction(this, "send_gesture_once_already_used");
            return false;
        }
        if (sendSettleRemainingMs() > 0L) {
            AppState.recordGptAutomationAction(this, "send_gesture_blocked_settle");
            return false;
        }
        final int width = getResources().getDisplayMetrics().widthPixels;
        final int height = getResources().getDisplayMetrics().heightPixels;
        final float x = width - 34f;
        final float y;
        if (composerBounds != null && !composerBounds.isEmpty()) {
            y = Math.max(height * 0.68f, Math.min(height - 22f, composerBounds.centerY()));
        } else {
            y = height - 35f;
        }
        final String description = "arrow_gesture x=" + (int) x + " y=" + (int) y;
        Path path = new Path();
        path.moveTo(x, y);
        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path, 0L, 90L))
                .build();
        sendGestureInFlight = true;
        AppState.markGptAutomationSendGestureAttempted(this);
        AppState.recordGptAutomationSendCandidate(this, "arrow_gesture", description);
        AppState.recordGptAutomationArrowFallback(this, "arrow_gesture");
        AppState.recordGptAutomationGate(this, "ready_to_send", "send_arrow_gesture",
                expected, evidence, readyStreak, description, requiredEvidence);
        boolean started = dispatchGesture(gesture, new GestureResultCallback() {
            @Override
            public void onCompleted(GestureDescription gestureDescription) {
                try {
                    sendGestureInFlight = false;
                    AppState.recordGptAutomationSendClick(
                            GptAutomationAccessibilityService.this,
                            "arrow_gesture",
                            description,
                            false
                    );
                    AppState.markGptAutomationSendClickPending(
                            GptAutomationAccessibilityService.this,
                            "arrow_gesture",
                            description,
                            false
                    );
                    AppState.recordGptAutomationGate(
                            GptAutomationAccessibilityService.this,
                            "send_click_pending",
                            "awaiting_send_confirmation",
                            expected,
                            evidence,
                            readyStreak,
                            description,
                            requiredEvidence
                    );
                    scheduleAttempt("send_click_pending", SEND_CONFIRM_CHECK_MS);
                } catch (RuntimeException error) {
                    handleAutomationException("gesture_completed", error);
                }
            }

            @Override
            public void onCancelled(GestureDescription gestureDescription) {
                try {
                    sendGestureInFlight = false;
                    AppState.recordGptAutomationFailure(
                            GptAutomationAccessibilityService.this,
                            "send_arrow_gesture_cancelled"
                    );
                    if (AppState.isGptPhotoSharePending(GptAutomationAccessibilityService.this)) {
                        scheduleAttempt("send_arrow_gesture_cancelled", RETRY_MS);
                    }
                } catch (RuntimeException error) {
                    handleAutomationException("gesture_cancelled", error);
                }
            }
        }, null);
        if (!started) {
            sendGestureInFlight = false;
            AppState.recordGptAutomationFailure(this, "send_arrow_gesture_not_started");
        }
        return started;
    }

    private boolean isUnsafeArrowControl(String label) {
        return looksAudio(label)
                || label.contains("attach")
                || label.contains("adjuntar")
                || label.contains("camera")
                || label.contains("camara")
                || label.contains("photo")
                || label.contains("image")
                || label.contains("foto")
                || label.contains("imagen")
                || label.contains("menu")
                || label.contains("more")
                || label.contains("option")
                || label.contains("plus")
                || label.contains("close")
                || label.contains("cerrar")
                || label.contains("remove")
                || label.contains("delete")
                || label.contains("cancel")
                || label.contains("back")
                || label.contains("new chat")
                || label.contains("nuevo chat");
    }

    private int arrowScore(Rect rect) {
        return rect.centerY() * 1000 + rect.centerX();
    }

    private Rect boundsOf(AccessibilityNodeInfo node) {
        Rect rect = new Rect();
        node.getBoundsInScreen(rect);
        return rect;
    }

    private String boundsString(AccessibilityNodeInfo node) {
        return describeBounds(boundsOf(node));
    }

    private String describeBounds(Rect rect) {
        if (rect == null) {
            return "";
        }
        return rect.left + "," + rect.top + "," + rect.right + "," + rect.bottom;
    }

    private void scanTree(AccessibilityNodeInfo node, ScanState state) {
        if (node == null) {
            return;
        }
        if (node.isVisibleToUser()) {
            String label = nodeLabel(node);
            String className = nodeClassName(node);
            Rect rect = new Rect();
            node.getBoundsInScreen(rect);
            if (looksUploading(label)) {
                state.uploadingVisible = true;
            }
            if (looksAttachmentEvidence(node, label, className, rect)) {
                state.attachmentEvidenceCount += 1;
            }
            if (node.isClickable() && looksAudio(label)) {
                state.audioVisible = true;
                if (state.audioCandidateDescription == null) {
                    state.audioCandidateDescription = describeNode(node, label);
                }
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child == null) {
                continue;
            }
            scanTree(child, state);
            child.recycle();
        }
    }

    private boolean looksAttachmentEvidence(
            AccessibilityNodeInfo node,
            String label,
            String className,
            Rect rect
    ) {
        boolean mediaLabel = label.contains("image")
                || label.contains("photo")
                || label.contains("foto")
                || label.contains("imagen")
                || label.contains("jpg")
                || label.contains("jpeg")
                || label.contains("png")
                || label.contains("heic")
                || label.contains("thumbnail")
                || label.contains("preview")
                || label.contains("vista previa")
                || label.contains("attached")
                || label.contains("adjunto");
        boolean unsafeControl = label.contains("send")
                || label.contains("enviar")
                || label.contains("voice")
                || label.contains("voz")
                || label.contains("micro")
                || label.contains("audio")
                || label.contains("record")
                || label.contains("dict")
                || label.contains("camera")
                || label.contains("camara")
                || label.contains("menu")
                || label.contains("more")
                || label.contains("close")
                || label.contains("cerrar")
                || label.contains("remove")
                || label.contains("delete")
                || label.contains("cancel")
                || label.contains("new chat")
                || label.contains("nuevo chat");
        if (mediaLabel && !unsafeControl) {
            return true;
        }
        boolean imageClass = className.contains("imageview")
                || className.contains("imagebutton")
                || className.contains("drawee")
                || className.contains("image");
        if (!imageClass || unsafeControl) {
            return false;
        }
        if (node.isClickable() && label.length() == 0) {
            return false;
        }
        int width = getResources().getDisplayMetrics().widthPixels;
        int height = getResources().getDisplayMetrics().heightPixels;
        int nodeWidth = rect.width();
        int nodeHeight = rect.height();
        return nodeWidth >= 24
                && nodeHeight >= 24
                && nodeWidth <= width * 0.72f
                && nodeHeight <= height * 0.55f
                && rect.centerY() > height * 0.34f
                && rect.centerX() > 0
                && rect.centerX() < width;
    }

    private boolean looksUploading(String label) {
        return label.contains("uploading")
                || label.contains("loading")
                || label.contains("preparing")
                || label.contains("processing")
                || label.contains("progress")
                || label.contains("cargando")
                || label.contains("subiendo")
                || label.contains("preparando")
                || label.contains("procesando")
                || label.contains("adjuntando");
    }

    private boolean looksAudio(String label) {
        return label.contains("voice")
                || label.contains("voz")
                || label.contains("micro")
                || label.contains("mic")
                || label.contains("audio")
                || label.contains("record")
                || label.contains("recording")
                || label.contains("grab")
                || label.contains("grabando")
                || label.contains("dict")
                || label.contains("listen")
                || label.contains("speak")
                || label.contains("stop recording")
                || label.contains("detener grabacion")
                || label.contains("pausar grabacion");
    }

    private boolean clickTextLike(AccessibilityNodeInfo root, String needle) {
        AccessibilityNodeInfo found = findTextLike(root, needle);
        if (found == null) {
            return false;
        }
        try {
            boolean ok = click(found);
            AppState.recordGptAutomationAction(this, "click_text " + needle + " ok=" + ok);
            return ok;
        } finally {
            found.recycle();
        }
    }

    private AccessibilityNodeInfo findTextLike(AccessibilityNodeInfo node, String needle) {
        if (node == null || needle == null) {
            return null;
        }
        if (node.isVisibleToUser() && nodeLabel(node).contains(needle.toLowerCase(Locale.US))) {
            AccessibilityNodeInfo clickable = clickableNode(node);
            if (clickable != null) {
                return clickable;
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child == null) {
                continue;
            }
            AccessibilityNodeInfo found = findTextLike(child, needle);
            child.recycle();
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private AccessibilityNodeInfo clickableNode(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo cursor = AccessibilityNodeInfo.obtain(node);
        while (cursor != null) {
            if (cursor.isClickable() && cursor.isEnabled()) {
                return cursor;
            }
            AccessibilityNodeInfo parent = cursor.getParent();
            cursor.recycle();
            cursor = parent;
        }
        return null;
    }

    private boolean click(AccessibilityNodeInfo node) {
        if (node == null) {
            return false;
        }
        try {
            return node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        } catch (RuntimeException error) {
            AppState.recordGptAutomationFailure(this,
                    "click:" + error.getClass().getSimpleName());
            return false;
        }
    }

    private boolean textContains(AccessibilityNodeInfo node, String text) {
        if (node == null || text == null || text.length() == 0) {
            return true;
        }
        CharSequence current = node.getText();
        return current != null && current.toString().contains(text);
    }

    private boolean isSendLabel(String label) {
        if (label == null || label.length() == 0) {
            return false;
        }
        boolean send = label.contains("send")
                || label.contains("enviar")
                || label.contains("submit");
        boolean avoid = looksAudio(label)
                || label.contains("attach")
                || label.contains("adjuntar")
                || label.contains("camera")
                || label.contains("camara")
                || label.contains("photo")
                || label.contains("image")
                || label.contains("foto")
                || label.contains("imagen")
                || label.contains("menu")
                || label.contains("more")
                || label.contains("option")
                || label.contains("plus")
                || label.contains("new chat")
                || label.contains("nuevo chat");
        return send && !avoid;
    }

    private String describeNode(AccessibilityNodeInfo node, String preferredLabel) {
        Rect rect = new Rect();
        node.getBoundsInScreen(rect);
        String label = preferredLabel == null || preferredLabel.length() == 0
                ? nodeLabel(node)
                : preferredLabel;
        return clip(label) + " bounds="
                + rect.left + "," + rect.top + "," + rect.right + "," + rect.bottom;
    }

    private String nodeLabel(AccessibilityNodeInfo node) {
        StringBuilder builder = new StringBuilder();
        append(builder, node.getText());
        append(builder, node.getContentDescription());
        append(builder, node.getViewIdResourceName());
        return builder.toString().toLowerCase(Locale.US);
    }

    private String nodeClassName(AccessibilityNodeInfo node) {
        CharSequence value = node.getClassName();
        return value == null ? "" : value.toString().toLowerCase(Locale.US);
    }

    private void append(StringBuilder builder, CharSequence value) {
        if (value == null) {
            return;
        }
        if (builder.length() > 0) {
            builder.append(' ');
        }
        builder.append(value);
    }

    private String clip(String value) {
        if (value == null) {
            return "";
        }
        String collapsed = value.replace('\n', ' ').replace('\r', ' ').trim();
        return collapsed.length() <= 90 ? collapsed : collapsed.substring(0, 90);
    }

    private int bottom(AccessibilityNodeInfo node) {
        Rect rect = new Rect();
        node.getBoundsInScreen(rect);
        return rect.bottom;
    }

    private static final class CameraTouchShieldView extends FrameLayout {
        private static final int EDGE_HEIGHT_DP = 72;
        private static final int SWIPE_THRESHOLD_DP = 14;

        private final String mode;
        private final int edgeHeightPx;
        private final int swipeThresholdPx;
        private float downX;
        private float downY;
        private String edge;
        private boolean swipeRecorded;

        CameraTouchShieldView(Context context, String mode) {
            super(context);
            this.mode = mode;
            float density = context.getResources().getDisplayMetrics().density;
            edgeHeightPx = Math.max(32, Math.round(EDGE_HEIGHT_DP * density));
            swipeThresholdPx = Math.max(10, Math.round(SWIPE_THRESHOLD_DP * density));
            setClickable(true);
            setFocusable(false);
            setBackgroundColor(Color.TRANSPARENT);
            setSystemUiVisibility(immersiveFlags());
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (event == null) {
                return true;
            }
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                downX = event.getX();
                downY = event.getY();
                edge = edgeForDown(event);
                swipeRecorded = false;
                return true;
            }
            if (action == MotionEvent.ACTION_MOVE) {
                maybeRecordEdgeSwipe(event);
                return true;
            }
            if (action == MotionEvent.ACTION_UP) {
                maybeRecordEdgeSwipe(event);
                reset();
                return true;
            }
            if (action == MotionEvent.ACTION_CANCEL) {
                reset();
                return true;
            }
            return true;
        }

        @Override
        public boolean performClick() {
            super.performClick();
            return true;
        }

        @Override
        public boolean dispatchKeyEvent(KeyEvent event) {
            return false;
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            setSystemUiVisibility(immersiveFlags());
        }

        private void maybeRecordEdgeSwipe(MotionEvent event) {
            if (swipeRecorded || edge == null) {
                return;
            }
            float dy = event.getY() - downY;
            if ("top".equals(edge) && dy >= swipeThresholdPx) {
                swipeRecorded = true;
                AppState.recordWatchCameraGestureGuardSwipeBlocked(getContext(), "top");
            } else if ("bottom".equals(edge) && -dy >= swipeThresholdPx) {
                swipeRecorded = true;
                AppState.recordWatchCameraGestureGuardSwipeBlocked(getContext(), "bottom");
            }
        }

        private String edgeForDown(MotionEvent event) {
            if (event.getY() <= edgeHeightPx) {
                return "top";
            }
            int height = getHeight();
            if (height > 0 && event.getY() >= height - edgeHeightPx) {
                return "bottom";
            }
            return null;
        }

        private void reset() {
            edge = null;
            swipeRecorded = false;
        }

        private static int immersiveFlags() {
            return View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
        }
    }

    private static final class ScanState {
        int attachmentEvidenceCount;
        boolean uploadingVisible;
        boolean audioVisible;
        String audioCandidateDescription;
    }

    private static final class SendCandidate {
        final AccessibilityNodeInfo node;
        final String description;
        final String source;

        SendCandidate(AccessibilityNodeInfo node, String description, String source) {
            this.node = node;
            this.description = description;
            this.source = source == null ? "" : source;
        }

        boolean isArrowFallback() {
            return source.startsWith("arrow");
        }

        void recycle() {
            if (node != null) {
                node.recycle();
            }
        }
    }
}
