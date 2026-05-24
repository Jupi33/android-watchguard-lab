package com.codex.watchguard;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;

public class TouchBlockerService extends Service {
    private static final String CHANNEL_ID = "watch_guard_lock";
    private static final int NOTIFICATION_ID = 3901;
    static final long NO_TIMEOUT = -1L;
    private static final long DEFAULT_SAFETY_TIMEOUT_MS = NO_TIMEOUT;
    private static final long MAX_SAFETY_TIMEOUT_MS = 24L * 60L * 60L * 1000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable safetyUnlock = new Runnable() {
        @Override
        public void run() {
            AppState.appendLog(TouchBlockerService.this, "safety timeout -> unlock");
            removeOverlay();
            stopSelf();
        }
    };

    private WindowManager windowManager;
    private View overlayView;
    private View leftEdgeGuardView;
    private View rightEdgeGuardView;
    private String currentLockMode;
    private boolean currentEdgeGuardsPassive;
    private BroadcastReceiver screenReceiver;
    private boolean screenReceiverRegistered;
    private final Runnable recoveryStop = new Runnable() {
        @Override
        public void run() {
            AppState.clearScreenOffRecovery(TouchBlockerService.this, "recovery_timeout");
            unregisterScreenReceiver();
            stopSelf();
        }
    };

    public static void lock(Context context, long timeoutMs) {
        start(context, AppState.ACTION_LOCK, timeoutMs, 0L, null);
    }

    public static void lockGptAutomation(Context context, long timeoutMs) {
        start(context, AppState.ACTION_LOCK, timeoutMs, 0L, AppState.LOCK_MODE_GPT_AUTOMATION);
    }

    public static void unlock(Context context) {
        start(context, AppState.ACTION_UNLOCK, 0L, 0L, null);
    }

    public static void unlock(Context context, long cycleId) {
        start(context, AppState.ACTION_UNLOCK, 0L, cycleId, null);
    }

    public static void toggle(Context context, long timeoutMs) {
        start(context, AppState.ACTION_TOGGLE, timeoutMs, 0L, null);
    }

    public static void emergencyStop(Context context) {
        start(context, AppState.ACTION_EMERGENCY_STOP, 0L, 0L, null);
    }

    private static void start(
            Context context,
            String action,
            long timeoutMs,
            long unlockCycleId,
            String lockMode
    ) {
        Intent intent = new Intent(context, TouchBlockerService.class);
        intent.setAction(action);
        intent.putExtra(AppState.EXTRA_TIMEOUT_MS, timeoutMs);
        if (lockMode != null && lockMode.length() > 0) {
            intent.putExtra(AppState.EXTRA_LOCK_MODE, lockMode);
        }
        if (unlockCycleId > 0L) {
            intent.putExtra(AppState.EXTRA_UNLOCK_CYCLE_ID, unlockCycleId);
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }
        } catch (RuntimeException error) {
            AppState.setLocked(context, false);
            if (AppState.LOCK_MODE_GPT_AUTOMATION.equals(lockMode)) {
                AppState.recordGptAutoLockFailed(context, "service_start_failed");
            }
            AppState.appendLog(context, "touch service start failed: "
                    + error.getClass().getSimpleName() + " " + error.getMessage());
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            AppState.appendLog(this, "null start -> overlay recovery unlock");
            AppState.recordTouchServiceNullIntent(this);
            AppState.recordModeOverlayRecovery(this, "touch_service_null_intent");
            AppState.setLocked(this, false);
            AppState.clearScreenOffRecovery(this, "touch_service_null_intent");
            removeOverlay();
            stopSelf();
            return START_NOT_STICKY;
        }

        ensureForeground();

        String action = intent.getAction();
        long timeoutMs = intent.getLongExtra(AppState.EXTRA_TIMEOUT_MS, DEFAULT_SAFETY_TIMEOUT_MS);
        long unlockCycleId = intent.getLongExtra(
                AppState.EXTRA_UNLOCK_CYCLE_ID,
                AppState.getHomeTrapCycleId(this)
        );
        String lockMode = intent.getStringExtra(AppState.EXTRA_LOCK_MODE);

        if (AppState.ACTION_EMERGENCY_STOP.equals(action)) {
            AppState.appendLog(this, "overlay recovery stop request");
            AppState.recordModeOverlayRecovery(this, "touch_service_action");
            AppState.setLocked(this, false);
            AppState.clearScreenOffRecovery(this, "touch_service_action");
            handler.removeCallbacks(recoveryStop);
            removeOverlay();
            stopSelf();
            return START_NOT_STICKY;
        }

        if (AppState.ACTION_UNLOCK.equals(action)) {
            AppState.appendLog(this, "manual unlock request");
            AppState.clearScreenOffRecovery(this, "manual_unlock");
            handler.removeCallbacks(recoveryStop);
            removeOverlay();
            AppState.recordSafeImeSnapshot(this, "overlay_off", unlockCycleId,
                    AppState.getLockedCyclePackage(this));
            AppState.trySilentRestoreSafeImeDefault(this, "overlay_off", unlockCycleId);
            SafeImeGateService.restartAfterUnlock(this, "overlay_off");
            stopSelf();
            return START_NOT_STICKY;
        }

        if (AppState.ACTION_TOGGLE.equals(action) && AppState.isLocked(this)) {
            AppState.appendLog(this, "toggle -> unlock");
            removeOverlay();
            stopSelf();
            return START_NOT_STICKY;
        }

        showOverlay(clampTimeout(timeoutMs), lockMode);
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(recoveryStop);
        removeOverlay();
        unregisterScreenReceiver();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void showOverlay(long timeoutMs, String lockMode) {
        boolean gptAutomationLock = AppState.LOCK_MODE_GPT_AUTOMATION.equals(lockMode);
        if (overlayView != null) {
            if (gptAutomationLock) {
                currentLockMode = AppState.LOCK_MODE_GPT_AUTOMATION;
                if (!AppState.isGptAutoLockActive(this)) {
                    AppState.recordGptAutoLockStarted(this, "existing_overlay");
                }
            } else if (!gptAutomationLock
                    && AppState.LOCK_MODE_GPT_AUTOMATION.equals(currentLockMode)) {
                currentLockMode = "";
                AppState.appendLog(this, "gpt automation lock promoted to normal edges");
            }
            showEdgeGuards("existing_overlay", gptAutomationLock);
            registerScreenReceiver();
            scheduleSafetyUnlock(timeoutMs);
            return;
        }

        if (!Settings.canDrawOverlays(this)) {
            AppState.appendLog(this, "overlay permission missing; cannot lock touches");
            AppState.setLocked(this, false);
            if (gptAutomationLock) {
                AppState.recordGptAutoLockFailed(this, "overlay_permission_missing");
            }
            stopSelf();
            return;
        }

        FrameLayout root = new TouchSinkView(this);
        root.setBackgroundColor(Color.TRANSPARENT);
        root.setFocusable(!gptAutomationLock);
        root.setFocusableInTouchMode(!gptAutomationLock);
        root.setSystemUiVisibility(immersiveFlags());

        TextView label = new TextView(this);
        label.setText(R.string.lock_label);
        label.setTextColor(Color.WHITE);
        label.setTextSize(10);
        label.setGravity(Gravity.CENTER);
        label.setAlpha(0.55f);
        label.setPadding(dp(6), dp(2), dp(6), dp(2));
        GradientDrawable labelBg = new GradientDrawable();
        labelBg.setColor(0x99000000);
        labelBg.setCornerRadius(dp(4));
        label.setBackground(labelBg);

        FrameLayout.LayoutParams labelParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
        );
        labelParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        labelParams.topMargin = dp(4);
        root.addView(label, labelParams);

        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        int windowFlags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                | WindowManager.LayoutParams.FLAG_FULLSCREEN;
        if (gptAutomationLock) {
            windowFlags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        }
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                type,
                windowFlags,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;
        params.setTitle(gptAutomationLock
                ? "WatchGuard GPT automation touch blocker"
                : "WatchGuard touch blocker");

        try {
            windowManager.addView(root, params);
            overlayView = root;
            currentLockMode = gptAutomationLock ? AppState.LOCK_MODE_GPT_AUTOMATION : "";
            if (!gptAutomationLock) {
                root.requestFocus();
            }
            AppState.setLocked(this, true);
            if (gptAutomationLock) {
                AppState.recordGptAutoLockStarted(this, "overlay_started");
            }
            showEdgeGuards("overlay_started", gptAutomationLock);
            registerScreenReceiver();
            AppState.appendLockModeResult(this, "overlay_lock", "started",
                    gptAutomationLock
                            ? "gpt automation non-focus overlay"
                            : "overlay focus + immersive requested");
            AppState.appendLog(this, "touch lock ON strict; mode="
                    + (gptAutomationLock ? AppState.LOCK_MODE_GPT_AUTOMATION : "normal")
                    + " safety=" + timeoutLabel(timeoutMs));
            scheduleSafetyUnlock(timeoutMs);
        } catch (RuntimeException error) {
            AppState.setLocked(this, false);
            if (gptAutomationLock) {
                AppState.recordGptAutoLockFailed(this, error.getClass().getSimpleName());
            }
            AppState.appendLockModeResult(this, "overlay_lock", "failed",
                    error.getClass().getSimpleName() + " " + error.getMessage());
            AppState.appendLog(this, "overlay add failed: " + error.getClass().getSimpleName()
                    + " " + error.getMessage());
            stopSelf();
        }
    }

    private void removeOverlay() {
        handler.removeCallbacks(safetyUnlock);
        boolean wasGptAutomationLock = AppState.LOCK_MODE_GPT_AUTOMATION.equals(currentLockMode);
        removeEdgeGuards("overlay_removed");
        if (overlayView != null) {
            try {
                windowManager.removeView(overlayView);
            } catch (RuntimeException ignored) {
                // The service may be torn down after the window manager has already detached it.
            }
            overlayView = null;
        }
        currentLockMode = null;
        AppState.setLocked(this, false);
        if (wasGptAutomationLock || AppState.isGptAutoLockActive(this)) {
            AppState.recordGptAutoLockInactive(this, "overlay_removed");
        }
        AppState.appendLockModeResult(this, "overlay_lock", "stopped", "overlay removed");
        AppState.appendLog(this, "touch lock OFF");
    }

    private void showEdgeGuards(String reason, boolean passive) {
        if (leftEdgeGuardView != null || rightEdgeGuardView != null) {
            if (currentEdgeGuardsPassive == passive) {
                AppState.setLockedEdgeGuardActive(this, true,
                        reason + (passive ? "_passive_already_present" : "_already_present"));
                return;
            }
            removeEdgeGuards(reason + "_mode_change");
        }
        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        int width = dp(34);
        leftEdgeGuardView = new EdgeGuardView(this, "left", passive);
        rightEdgeGuardView = new EdgeGuardView(this, "right", passive);
        try {
            windowManager.addView(leftEdgeGuardView, edgeGuardParams(type, width,
                    Gravity.TOP | Gravity.START, "left", passive));
            windowManager.addView(rightEdgeGuardView, edgeGuardParams(type, width,
                    Gravity.TOP | Gravity.END, "right", passive));
            currentEdgeGuardsPassive = passive;
            AppState.setLockedEdgeGuardActive(this, true,
                    reason + (passive ? "_passive" : "_active"));
        } catch (RuntimeException error) {
            removeEdgeGuards("edge_guard_add_failed");
            AppState.appendLog(this, "locked_edge_guard_failed "
                    + error.getClass().getSimpleName() + " " + error.getMessage());
        }
    }

    private WindowManager.LayoutParams edgeGuardParams(
            int type,
            int width,
            int gravity,
            String edge,
            boolean passive
    ) {
        int windowFlags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                | WindowManager.LayoutParams.FLAG_FULLSCREEN
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
        if (passive) {
            windowFlags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        }
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                width,
                WindowManager.LayoutParams.MATCH_PARENT,
                type,
                windowFlags,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = gravity;
        params.setTitle("WatchGuard locked edge guard " + edge
                + (passive ? " passive" : " active"));
        return params;
    }

    private void removeEdgeGuards(String reason) {
        if (leftEdgeGuardView != null) {
            try {
                windowManager.removeView(leftEdgeGuardView);
            } catch (RuntimeException ignored) {
                // The edge guard may already be detached during service teardown.
            }
            leftEdgeGuardView = null;
        }
        if (rightEdgeGuardView != null) {
            try {
                windowManager.removeView(rightEdgeGuardView);
            } catch (RuntimeException ignored) {
                // The edge guard may already be detached during service teardown.
            }
            rightEdgeGuardView = null;
        }
        currentEdgeGuardsPassive = false;
        AppState.setLockedEdgeGuardActive(this, false, reason);
    }

    private void registerScreenReceiver() {
        if (screenReceiverRegistered) {
            return;
        }
        if (screenReceiver == null) {
            screenReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (intent == null || intent.getAction() == null) {
                        return;
                    }
                    if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                        handleScreenOffDuringLock();
                    } else if (Intent.ACTION_SCREEN_ON.equals(intent.getAction())) {
                        handleScreenOnRecovery();
                    }
                }
            };
        }
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        try {
            registerReceiver(screenReceiver, filter);
            screenReceiverRegistered = true;
            AppState.appendLog(this, "screen recovery receiver registered");
        } catch (RuntimeException error) {
            AppState.appendLog(this, "screen recovery receiver failed: "
                    + error.getClass().getSimpleName() + " " + error.getMessage());
        }
    }

    private void unregisterScreenReceiver() {
        if (!screenReceiverRegistered || screenReceiver == null) {
            return;
        }
        try {
            unregisterReceiver(screenReceiver);
        } catch (RuntimeException ignored) {
            // The receiver may already be gone if Android is tearing the service down.
        }
        screenReceiverRegistered = false;
    }

    private void handleScreenOffDuringLock() {
        if (!AppState.isLocked(this)) {
            return;
        }
        String target = AppState.getLockedCyclePackage(this);
        long cycleId = AppState.getHomeTrapCycleId(this);
        AppState.recordScreenOffRecovery(this, target);
        AppState.appendLockModeResult(this, "overlay_lock", "screen_off_recovery",
                target == null ? "target=none" : "target=" + target);
        AppState.clearHomeTrapCycle(this, cycleId, "screen_off_recovery");
        removeOverlay();
        handler.removeCallbacks(recoveryStop);
        handler.postDelayed(recoveryStop, AppState.getScreenOffRecoveryMs());
    }

    private void handleScreenOnRecovery() {
        String target = AppState.consumeScreenOffRecoveryPackage(this);
        if (target != null) {
            ForegroundResolver.restorePackage(this, target);
            AppState.appendLockModeResult(this, "overlay_lock", "screen_on_restore",
                    "target=" + target);
        }
        handler.removeCallbacks(recoveryStop);
        unregisterScreenReceiver();
        stopSelf();
    }

    private void scheduleSafetyUnlock(long timeoutMs) {
        handler.removeCallbacks(safetyUnlock);
        long clamped = clampTimeout(timeoutMs);
        if (clamped == NO_TIMEOUT) {
            AppState.appendLog(this, "safety timeout disabled for active session");
            return;
        }
        handler.postDelayed(safetyUnlock, clamped);
    }

    private void ensureForeground() {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && manager != null) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "WatchGuard bloqueo",
                    NotificationManager.IMPORTANCE_MIN
            );
            manager.createNotificationChannel(channel);
        }

        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        Intent rescueIntent = new Intent(this, TouchBlockerService.class);
        rescueIntent.setAction(AppState.ACTION_EMERGENCY_STOP);
        PendingIntent rescuePendingIntent = PendingIntent.getService(
                this,
                3903,
                rescueIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        Notification notification = builder
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .setContentTitle("WatchGuard activo")
                .setContentText("Bloqueo tactil activo")
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Desbloquear", rescuePendingIntent)
                .setOngoing(true)
                .build();
        startForeground(NOTIFICATION_ID, notification);
    }

    private long clampTimeout(long timeoutMs) {
        if (timeoutMs == NO_TIMEOUT) {
            return DEFAULT_SAFETY_TIMEOUT_MS;
        }
        if (timeoutMs <= 0L) {
            return DEFAULT_SAFETY_TIMEOUT_MS;
        }
        return Math.min(timeoutMs, MAX_SAFETY_TIMEOUT_MS);
    }

    private String timeoutLabel(long timeoutMs) {
        return timeoutMs == NO_TIMEOUT ? "off" : clampTimeout(timeoutMs) + "ms";
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private int immersiveFlags() {
        return View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
    }

    private static final class TouchSinkView extends FrameLayout {
        private final int edgeWidthPx;
        private final int swipeThresholdPx;
        private float downX;
        private float downY;
        private boolean edgeBackCandidate;
        private boolean edgeBackRecorded;
        private String edgeSide;

        TouchSinkView(Context context) {
            super(context);
            setClickable(true);
            float density = context.getResources().getDisplayMetrics().density;
            edgeWidthPx = Math.max(18, Math.round(24f * density));
            swipeThresholdPx = Math.max(24, Math.round(30f * density));
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
                edgeSide = edgeSideForDown(event);
                edgeBackCandidate = edgeSide != null && AppState.isLocked(getContext());
                edgeBackRecorded = false;
            } else if (action == MotionEvent.ACTION_MOVE && edgeBackCandidate
                    && !edgeBackRecorded) {
                float dx = event.getX() - downX;
                float dy = event.getY() - downY;
                if (Math.abs(dx) >= swipeThresholdPx && Math.abs(dx) > Math.abs(dy)) {
                    edgeBackRecorded = true;
                    AppState.recordLockedEdgeBackSwipeBlocked(getContext(), edgeSide, dx);
                }
            } else if (action == MotionEvent.ACTION_UP) {
                performClick();
                resetEdgeState();
            } else if (action == MotionEvent.ACTION_CANCEL) {
                resetEdgeState();
            }
            return true;
        }

        private String edgeSideForDown(MotionEvent event) {
            if (event.getX() <= edgeWidthPx) {
                return "left";
            }
            int width = getWidth();
            if (width > 0 && event.getX() >= width - edgeWidthPx) {
                return "right";
            }
            return null;
        }

        private void resetEdgeState() {
            edgeBackCandidate = false;
            edgeBackRecorded = false;
            edgeSide = null;
        }

        @Override
        public boolean performClick() {
            super.performClick();
            return true;
        }

        @Override
        public boolean dispatchKeyEvent(android.view.KeyEvent event) {
            return true;
        }

        @Override
        public void onWindowFocusChanged(boolean hasWindowFocus) {
            super.onWindowFocusChanged(hasWindowFocus);
            if (hasWindowFocus) {
                setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
            }
        }
    }

    private static final class EdgeGuardView extends FrameLayout {
        private final String edge;
        private final boolean passive;
        private final int swipeThresholdPx;
        private float downX;
        private float downY;
        private boolean blocked;

        EdgeGuardView(Context context, String edge, boolean passive) {
            super(context);
            this.edge = edge;
            this.passive = passive;
            float density = context.getResources().getDisplayMetrics().density;
            swipeThresholdPx = Math.max(18, Math.round(24f * density));
            setClickable(true);
            setFocusable(!passive);
            setFocusableInTouchMode(!passive);
            setBackgroundColor(Color.TRANSPARENT);
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            if (!passive) {
                requestFocus();
            }
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
                blocked = false;
                AppState.recordLockedEdgeGuardTouch(getContext(), edge);
            } else if (action == MotionEvent.ACTION_MOVE && !blocked) {
                float dx = event.getX() - downX;
                float dy = event.getY() - downY;
                if (Math.abs(dx) >= swipeThresholdPx && Math.abs(dx) > Math.abs(dy)) {
                    blocked = true;
                    AppState.recordLockedEdgeGuardBlocked(getContext(), edge, dx);
                    AppState.recordLockedEdgeBackSwipeBlocked(getContext(), edge, dx);
                }
            } else if (action == MotionEvent.ACTION_UP) {
                performClick();
                blocked = false;
            } else if (action == MotionEvent.ACTION_CANCEL) {
                blocked = false;
            }
            return true;
        }

        @Override
        public boolean performClick() {
            super.performClick();
            return true;
        }

        @Override
        public boolean dispatchKeyEvent(android.view.KeyEvent event) {
            return true;
        }
    }
}
