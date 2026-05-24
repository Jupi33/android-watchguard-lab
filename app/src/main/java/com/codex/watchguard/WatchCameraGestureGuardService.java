package com.codex.watchguard;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

public class WatchCameraGestureGuardService extends Service {
    static final String ACTION_START =
            "com.codex.watchguard.action.WATCH_CAMERA_GESTURE_GUARD_START";
    static final String ACTION_STOP =
            "com.codex.watchguard.action.WATCH_CAMERA_GESTURE_GUARD_STOP";

    private static final String EXTRA_REASON = "reason";
    private static final String SHIELD_MODE = "app_overlay";
    private static final int EDGE_HEIGHT_DP = 72;
    private static final int SWIPE_TRIGGER_DP = 14;
    private static final long FAILSAFE_INTERVAL_MS = 700L;
    private static final long HEARTBEAT_STALE_MS = 2_500L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private WindowManager windowManager;
    private View shieldView;
    private boolean guardRunning;
    private boolean screenReceiverRegistered;

    private final Runnable failsafeRunnable = new Runnable() {
        @Override
        public void run() {
            long heartbeat = AppState.getWatchCameraGestureGuardHeartbeatAt(
                    WatchCameraGestureGuardService.this);
            long age = heartbeat <= 0L
                    ? Long.MAX_VALUE
                    : Math.max(0L, System.currentTimeMillis() - heartbeat);
            boolean desired = AppState.isWatchCameraActive(WatchCameraGestureGuardService.this)
                    || AppState.isWatchCameraWarmBridgeActive(WatchCameraGestureGuardService.this)
                    || AppState.isWatchCameraHomeBridgeActive(WatchCameraGestureGuardService.this)
                    || AppState.isWatchCameraPanelRescueActive(WatchCameraGestureGuardService.this);
            boolean panelRescue = AppState.isWatchCameraPanelRescueActive(
                    WatchCameraGestureGuardService.this);
            if (!desired || (!panelRescue && age > HEARTBEAT_STALE_MS)) {
                stopGuard("failsafe active="
                        + AppState.isWatchCameraActive(WatchCameraGestureGuardService.this)
                        + " warm="
                        + AppState.isWatchCameraWarmBridgeActive(WatchCameraGestureGuardService.this)
                        + " home_bridge="
                        + AppState.isWatchCameraHomeBridgeActive(WatchCameraGestureGuardService.this)
                        + " panel_rescue=" + panelRescue
                        + " heartbeat_age=" + age);
                stopSelf();
                return;
            }
            handler.postDelayed(this, FAILSAFE_INTERVAL_MS);
        }
    };

    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || !Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                return;
            }
            if (AppState.isWatchCameraActive(context)
                    || AppState.isWatchCameraGestureGuardActive(context)) {
                AppState.recordWatchCameraScreenOffDuringCamera(context, "screen_receiver");
            }
        }
    };

    static void start(Context context) {
        if (context == null) {
            return;
        }
        Intent intent = new Intent(context, WatchCameraGestureGuardService.class);
        intent.setAction(ACTION_START);
        try {
            context.getApplicationContext().startService(intent);
        } catch (RuntimeException error) {
            AppState.appendLog(context, "watch_camera_gesture_guard_start_failed "
                    + error.getClass().getSimpleName() + ": " + error.getMessage());
        }
    }

    static void stop(Context context, String reason) {
        if (context == null) {
            return;
        }
        Intent intent = new Intent(context, WatchCameraGestureGuardService.class);
        intent.setAction(ACTION_STOP);
        intent.putExtra(EXTRA_REASON, reason);
        try {
            context.getApplicationContext().startService(intent);
        } catch (RuntimeException error) {
            AppState.appendLog(context, "watch_camera_gesture_guard_stop_failed "
                    + error.getClass().getSimpleName() + ": " + error.getMessage());
            try {
                context.getApplicationContext().stopService(intent);
            } catch (RuntimeException ignored) {
            }
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_START : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            String reason = intent == null ? "stop" : intent.getStringExtra(EXTRA_REASON);
            stopGuard(reason == null ? "stop" : reason);
            stopSelf(startId);
            return START_NOT_STICKY;
        }
        startGuard(intent == null ? "start" : "start_command");
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        stopGuard("destroy");
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void startGuard(String reason) {
        if (AppState.isGptAutomationServiceAlive(this)) {
            stopGuard("accessibility_alive");
            return;
        }
        if (guardRunning && shieldView != null) {
            AppState.markWatchCameraGestureGuardHeartbeat(this, "service_start_existing");
            scheduleFailsafe();
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            AppState.setWatchCameraGestureGuardActive(this, false, "no_overlay_permission");
            return;
        }
        if (windowManager == null) {
            AppState.setWatchCameraGestureGuardActive(this, false, "window_manager_null");
            return;
        }
        try {
            shieldView = new GuardShieldView(this);
            windowManager.addView(shieldView, layoutParams());
            guardRunning = true;
            registerScreenReceiver();
            AppState.recordWatchCameraGestureGuardStarted(this, reason);
            AppState.recordWatchCameraTouchShieldStarted(this, SHIELD_MODE, reason);
            AppState.appendLog(this, "watch_camera_touch_inert mode="
                    + SHIELD_MODE + " reason=" + reason);
            scheduleFailsafe();
        } catch (RuntimeException error) {
            removeGuardViews();
            guardRunning = false;
            AppState.setWatchCameraGestureGuardActive(
                    this,
                    false,
                    "start_failed_" + error.getClass().getSimpleName()
            );
            AppState.appendLog(this, "watch_camera_gesture_guard_start_error "
                    + error.getClass().getSimpleName() + ": " + error.getMessage());
        }
    }

    private WindowManager.LayoutParams layoutParams() {
        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_FULLSCREEN,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 0;
        params.y = 0;
        params.setTitle("WatchGuard camera app shield fallback");
        return params;
    }

    private void stopGuard(String reason) {
        handler.removeCallbacks(failsafeRunnable);
        boolean wasRunning = guardRunning
                || shieldView != null
                || AppState.isWatchCameraGestureGuardActive(this);
        removeGuardViews();
        unregisterScreenReceiver();
        guardRunning = false;
        if (wasRunning) {
            AppState.recordWatchCameraGestureGuardStopped(this, reason);
            AppState.recordWatchCameraTouchShieldStopped(this, SHIELD_MODE, reason);
        }
    }

    private void removeGuardViews() {
        removeView(shieldView);
        shieldView = null;
    }

    private void removeView(View view) {
        if (view == null || windowManager == null) {
            return;
        }
        try {
            windowManager.removeView(view);
        } catch (RuntimeException ignored) {
        }
    }

    private void registerScreenReceiver() {
        if (screenReceiverRegistered) {
            return;
        }
        try {
            registerReceiver(screenReceiver, new IntentFilter(Intent.ACTION_SCREEN_OFF));
            screenReceiverRegistered = true;
        } catch (RuntimeException error) {
            AppState.appendLog(this, "watch_camera_screen_receiver_failed "
                    + error.getClass().getSimpleName() + ": " + error.getMessage());
        }
    }

    private void unregisterScreenReceiver() {
        if (!screenReceiverRegistered) {
            return;
        }
        try {
            unregisterReceiver(screenReceiver);
        } catch (RuntimeException ignored) {
        }
        screenReceiverRegistered = false;
    }

    private void scheduleFailsafe() {
        handler.removeCallbacks(failsafeRunnable);
        handler.postDelayed(failsafeRunnable, FAILSAFE_INTERVAL_MS);
    }

    private int dp(int value) {
        return Math.max(1, (int) (value * getResources().getDisplayMetrics().density + 0.5f));
    }

    private final class GuardShieldView extends View {
        private final int edgeHeightPx;
        private final int triggerPx;
        private float downX;
        private float downY;
        private String edge;
        private boolean blocked;

        GuardShieldView(Context context) {
            super(context);
            this.edgeHeightPx = dp(EDGE_HEIGHT_DP);
            this.triggerPx = dp(SWIPE_TRIGGER_DP);
            setBackgroundColor(Color.TRANSPARENT);
            setClickable(true);
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
                blocked = false;
                return true;
            }
            if (action == MotionEvent.ACTION_MOVE) {
                maybeRecordSwipe(event);
                return true;
            }
            if (action == MotionEvent.ACTION_UP) {
                maybeRecordSwipe(event);
                blocked = false;
                edge = null;
                return true;
            }
            if (action == MotionEvent.ACTION_CANCEL) {
                blocked = false;
                edge = null;
                return true;
            }
            return true;
        }

        private void maybeRecordSwipe(MotionEvent event) {
            if (blocked || edge == null) {
                return;
            }
            float delta = event.getY() - downY;
            if ("top".equals(edge) && delta >= triggerPx) {
                blocked = true;
                AppState.recordWatchCameraGestureGuardSwipeBlocked(
                        WatchCameraGestureGuardService.this,
                        "top"
                );
            } else if ("bottom".equals(edge) && -delta >= triggerPx) {
                blocked = true;
                AppState.recordWatchCameraGestureGuardSwipeBlocked(
                        WatchCameraGestureGuardService.this,
                        "bottom"
                );
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
    }
}
