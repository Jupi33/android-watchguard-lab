package com.codex.watchguard;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

public class SafeImeGateService extends Service {
    private static final String CHANNEL_ID = "watch_guard_ime_gate";
    private static final int NOTIFICATION_ID = 3905;
    private static final String ACTION_START = "com.codex.watchguard.action.IME_GATE_START";
    private static final String ACTION_STOP = "com.codex.watchguard.action.IME_GATE_STOP";
    private static final long REFRESH_MS = 650L;
    private static final long LOOKBACK_MS = 15_000L;
    private static final long CACHED_STRICT_PACKAGE_MS = 10L * 60L * 1000L;
    private static final long TAP_MAX_MS = 700L;
    private static final long PASS_THROUGH_MS = 1_200L;
    private static final long RESCUE_VISIBLE_MS = 6_000L;
    private static final long RESCUE_MISS_WINDOW_MS = 8_000L;
    private static final long RECENT_TOUCH_QUIET_MS = 1_500L;
    private static final int RESCUE_MISS_THRESHOLD = 3;
    private static final int SWIPE_SLOP_DP = 20;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private WindowManager windowManager;
    private View gateView;
    private String activePackage;
    private String activeZone;
    private boolean gateShown;
    private boolean rescueShown;
    private boolean running;
    private long passThroughUntilUptime;
    private long shownAtUptime;
    private long lastTouchAtUptime;
    private long rescueUntilUptime;
    private String rescuePackage;
    private float downX;
    private float downY;
    private long downAtUptime;
    private boolean dragging;

    private final Runnable refreshRunnable = new Runnable() {
        @Override
        public void run() {
            refreshGate();
            if (running) {
                handler.postDelayed(this, REFRESH_MS);
            }
        }
    };

    static void start(Context context) {
        Intent intent = new Intent(context, SafeImeGateService.class);
        intent.setAction(ACTION_START);
        startServiceCompat(context, intent);
    }

    static void stop(Context context) {
        Intent intent = new Intent(context, SafeImeGateService.class);
        intent.setAction(ACTION_STOP);
        startServiceCompat(context, intent);
    }

    static void restartAfterUnlock(Context context, String reason) {
        final Context appContext = context.getApplicationContext();
        AppState.setSafeImeKeyboardVisible(appContext, false, "unlock_gate_restart");
        AppState.appendLog(appContext, "safe_ime_gate_restart_after_unlock reason=" + reason);
        start(appContext);
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                SafeImeGateService.start(appContext);
            }
        }, 450L);
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                SafeImeGateService.start(appContext);
            }
        }, 1_200L);
    }

    private static void startServiceCompat(Context context, Intent intent) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }
        } catch (RuntimeException error) {
            AppState.appendLog(context, "safe_ime_gate_service_start_failed "
                    + error.getClass().getSimpleName() + " " + error.getMessage());
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_STOP : intent.getAction();
        ensureForeground();
        if (ACTION_STOP.equals(action)) {
            running = false;
            hideGate("stop");
            handler.removeCallbacksAndMessages(null);
            stopSelf();
            return START_NOT_STICKY;
        }
        running = true;
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        handler.removeCallbacks(refreshRunnable);
        refreshGate();
        handler.postDelayed(refreshRunnable, REFRESH_MS);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        running = false;
        handler.removeCallbacksAndMessages(null);
        hideGate("destroy");
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void refreshGate() {
        try {
            if (!running) {
                return;
            }
            String blocked = blockedReason();
            if (blocked != null) {
                AppState.recordSafeImeGateBlocked(this, blocked);
                hideGate(blocked);
                return;
            }
            String packageName = currentStrictPackage();
            if (packageName == null) {
                AppState.recordSafeImeGateBlocked(this, "no_strict_package");
                hideGate("no_strict_package");
                return;
            }
            boolean rescue = shouldShowRescue(packageName);
            showGate(packageName, layoutForPackage(packageName, rescue));
        } catch (RuntimeException error) {
            AppState.recordSafeImeGateException(this, "refreshGate", error);
            try {
                hideGate("refresh_exception");
            } catch (RuntimeException ignored) {
                AppState.recordSafeImeGateException(this, "refreshGate_hide", ignored);
            }
        }
    }

    private String blockedReason() {
        if (!AppState.isHomeTrapEnabled(this)) {
            return "mode_off";
        }
        if (!Settings.canDrawOverlays(this)) {
            return "overlay_missing";
        }
        if (!AppState.isWatchKeyboardSelected(this)) {
            return "ime_not_selected";
        }
        if (AppState.isLocked(this)) {
            return "locked";
        }
        if (AppState.isWatchCameraActive(this)) {
            return "camera_active";
        }
        if (AppState.isSafeImeKeyboardVisible(this)) {
            return "keyboard_visible";
        }
        if (SystemClock.uptimeMillis() < passThroughUntilUptime) {
            return "pass_through";
        }
        return null;
    }

    private void showGate(String packageName, GateLayout layout) {
        try {
            if (windowManager == null) {
                windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
            }
            if (windowManager == null) {
                hideGate("no_window_manager");
                return;
            }
            if (gateShown && !isGateAttached()) {
                AppState.recordSafeImeGateDetachedRecover(this, activePackage, activeZone, "refresh");
                gateView = null;
                gateShown = false;
                rescueShown = false;
                activePackage = null;
                activeZone = null;
            }
            boolean logRescueShown = layout.rescue
                    && !(gateShown && packageName.equals(activePackage) && rescueShown);
            if (gateShown && packageName.equals(activePackage) && layout.zone.equals(activeZone)) {
                AppState.recordSafeImeGateLayout(this, layout.description, true);
                return;
            }
            hideGate("replace");
            activePackage = packageName;
            activeZone = layout.zone;
            rescueShown = layout.rescue;
            gateView = buildGateButtonView(layout.rescue);
            gateView.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View view, MotionEvent event) {
                    try {
                        return handleGateTouch(event);
                    } catch (RuntimeException error) {
                        AppState.recordSafeImeGateException(
                                SafeImeGateService.this,
                                "handleGateTouch",
                                error
                        );
                        return true;
                    }
                }
            });
            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    layout.width,
                    layout.height,
                    overlayType(),
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT
            );
            params.gravity = Gravity.BOTTOM | Gravity.LEFT;
            params.x = layout.left;
            params.y = layout.bottom;
            try {
                windowManager.addView(gateView, params);
                gateShown = true;
                shownAtUptime = SystemClock.uptimeMillis();
                lastTouchAtUptime = 0L;
                AppState.setSafeImeGateActive(this, true, activePackage, activeZone, "show");
                AppState.recordSafeImeGateLayout(this, layout.description, true);
                if (logRescueShown) {
                    AppState.recordSafeImeGateRescueShown(
                            this,
                            activePackage,
                            activeZone,
                            AppState.getSafeImeGateMissCount(this)
                    );
                }
            } catch (RuntimeException error) {
                gateShown = false;
                rescueShown = false;
                gateView = null;
                AppState.setSafeImeGateActive(this, false, packageName, layout.zone,
                        "add_view_failed");
                AppState.recordSafeImeGateNoConnection(this, packageName,
                        "add_view_failed:" + error.getClass().getSimpleName());
                AppState.recordSafeImeGateException(this, "addView", error);
            }
        } catch (RuntimeException error) {
            AppState.recordSafeImeGateException(this, "showGate", error);
        }
    }

    private boolean shouldShowRescue(String packageName) {
        long now = SystemClock.uptimeMillis();
        if (rescueUntilUptime > now && packageName != null && packageName.equals(rescuePackage)) {
            return true;
        }
        if (rescueUntilUptime <= now) {
            rescuePackage = null;
        }
        String missPackage = AppState.getSafeImeGateMissPackage(this);
        long missAge = AppState.getSafeImeGateMissAgeMs(this);
        long lastTouchAge = AppState.getSafeImeGateLastTouchAgeMs(this);
        int missCount = AppState.getSafeImeGateMissCount(this);
        boolean quietTouch = lastTouchAge < 0L || lastTouchAge > RECENT_TOUCH_QUIET_MS;
        if (packageName != null
                && packageName.equals(missPackage)
                && missCount >= RESCUE_MISS_THRESHOLD
                && missAge >= 0L
                && missAge <= RESCUE_MISS_WINDOW_MS
                && quietTouch) {
            rescuePackage = packageName;
            rescueUntilUptime = now + RESCUE_VISIBLE_MS;
            return true;
        }
        return false;
    }

    private GateLayout layoutForPackage(String packageName, boolean rescue) {
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        boolean claude = "com.anthropic.claude".equals(packageName);
        boolean whatsapp = "com.whatsapp".equals(packageName) || "com.whatsapp.w4b".equals(packageName);
        int width;
        int height;
        int left;
        int bottom;
        String kind;
        if (claude) {
            width = clamp(Math.round(screenWidth * 0.56f), dp(172), dp(190), screenWidth - dp(54));
            height = dp(rescue ? 48 : 44);
            left = clampLeft(Math.round(screenWidth * 0.10f), dp(28), dp(92), width, dp(24));
            bottom = dp(60);
            kind = rescue ? "claude_text_box_rescue" : "claude_text_box";
        } else if (whatsapp) {
            width = clamp(Math.round(screenWidth * 0.42f), dp(124), dp(142), screenWidth - dp(180));
            height = dp(rescue ? 46 : 42);
            left = clampLeft(Math.round(screenWidth * 0.16f), dp(48), dp(64), width, dp(132));
            bottom = dp(6);
            kind = rescue ? "whatsapp_text_bar_rescue" : "whatsapp_text_bar";
        } else {
            width = clamp(Math.round(screenWidth * 0.48f), dp(148), dp(166), screenWidth - dp(150));
            height = dp(rescue ? 48 : 44);
            left = clampLeft(Math.round(screenWidth * 0.24f), dp(68), dp(86), width, dp(84));
            bottom = dp(16);
            kind = rescue ? "gpt_text_bar_rescue" : "gpt_text_bar";
        }
        String zone = "v5 " + kind
                + " left=" + left
                + " w=" + width
                + " h=" + height
                + " bottom=" + bottom
                + " rescue=" + rescue;
        return new GateLayout(width, height, left, bottom, zone, zone, rescue);
    }

    private boolean isGateAttached() {
        return gateView != null && gateView.isAttachedToWindow() && gateView.getWindowToken() != null;
    }

    private View buildGateButtonView(boolean rescue) {
        TextView view = new TextView(this);
        view.setText("teclado");
        view.setTextColor(Color.WHITE);
        view.setTextSize(rescue ? 14 : 13);
        view.setGravity(Gravity.CENTER);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        GradientDrawable background = new GradientDrawable();
        background.setColor(0x402A686A);
        background.setStroke(dp(1), rescue ? 0xCCFFFFFF : 0x8066D3D5);
        background.setCornerRadius(dp(18));
        view.setBackground(background);
        return view;
    }

    private int clamp(int value, int min, int preferredMax, int hardMax) {
        int max = Math.max(min, Math.min(preferredMax, hardMax));
        return Math.min(max, Math.max(min, value));
    }

    private int clampLeft(int value, int min, int preferredMax, int width, int rightMargin) {
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int hardMax = Math.max(min, screenWidth - width - rightMargin);
        int max = Math.max(min, Math.min(preferredMax, hardMax));
        return Math.min(max, Math.max(min, value));
    }

    private boolean handleGateTouch(MotionEvent event) {
        if (event == null || activePackage == null) {
            return true;
        }
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            downX = event.getRawX();
            downY = event.getRawY();
            downAtUptime = SystemClock.uptimeMillis();
            lastTouchAtUptime = downAtUptime;
            dragging = false;
            AppState.recordSafeImeGateDown(this, activePackage, activeZone);
            return true;
        }
        if (action == MotionEvent.ACTION_MOVE) {
            float dx = event.getRawX() - downX;
            float dy = event.getRawY() - downY;
            if (Math.abs(dx) > dp(SWIPE_SLOP_DP) || Math.abs(dy) > dp(SWIPE_SLOP_DP)) {
                dragging = true;
            }
            return true;
        }
        if (action == MotionEvent.ACTION_CANCEL) {
            dragging = true;
            return true;
        }
        if (action != MotionEvent.ACTION_UP) {
            return true;
        }
        float dx = event.getRawX() - downX;
        float dy = event.getRawY() - downY;
        long duration = SystemClock.uptimeMillis() - downAtUptime;
        boolean swipe = dragging
                || duration < 0L
                || duration > TAP_MAX_MS
                || Math.abs(dx) > dp(SWIPE_SLOP_DP)
                || Math.abs(dy) > dp(SWIPE_SLOP_DP);
        if (swipe) {
            AppState.recordSafeImeGateSwipeIgnored(this, activePackage, dx, dy);
            return true;
        }
        if (TinyInputMethodService.canShowForGate(activePackage)) {
            if (rescueShown) {
                AppState.recordSafeImeGateRescueTap(this, activePackage, activeZone);
            }
            AppState.recordSafeImeGateTapToken(this, activePackage, activeZone);
            TinyInputMethodService.notifyGateTap(activePackage);
        } else {
            if (rescueShown) {
                AppState.recordSafeImeGateRescueTap(this, activePackage, activeZone);
            }
            AppState.recordSafeImeGateNoConnection(this, activePackage, "no_input_connection");
            AppState.armSafeImeGateFocusPass(this, activePackage, "no_input_connection");
            passThroughUntilUptime = SystemClock.uptimeMillis() + PASS_THROUGH_MS;
            hideGate("pass_through_no_connection");
        }
        return true;
    }

    private void hideGate(String reason) {
        try {
            if (gateView != null && windowManager != null) {
                try {
                    windowManager.removeView(gateView);
                } catch (RuntimeException error) {
                    AppState.recordSafeImeGateException(this, "hideGate_remove", error);
                    // The system may already have removed the overlay during app transitions.
                }
            }
            if (gateShown) {
                AppState.setSafeImeGateActive(this, false, activePackage, activeZone, reason);
            }
            gateView = null;
            gateShown = false;
            rescueShown = false;
            shownAtUptime = 0L;
            lastTouchAtUptime = 0L;
            activePackage = null;
            activeZone = null;
        } catch (RuntimeException error) {
            AppState.recordSafeImeGateException(this, "hideGate", error);
        }
    }

    private String currentStrictPackage() {
        if (!ForegroundResolver.hasUsageAccess(this)) {
            return null;
        }
        UsageStatsManager usage = (UsageStatsManager) getSystemService(USAGE_STATS_SERVICE);
        if (usage == null) {
            return null;
        }
        long now = System.currentTimeMillis();
        UsageEvents events = usage.queryEvents(now - LOOKBACK_MS, now + 1_000L);
        UsageEvents.Event event = new UsageEvents.Event();
        String rawPackage = null;
        while (events != null && events.hasNextEvent()) {
            events.getNextEvent(event);
            if (event.getEventType() == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                rawPackage = event.getPackageName();
            }
        }
        if (AppState.isStrictSafeImePackage(rawPackage)) {
            AppState.recordSafeImeGateCandidateSource(this, "foreground_raw:" + rawPackage);
            return rawPackage;
        }
        if (rawPackage != null && !isNeutralPackage(rawPackage)) {
            AppState.recordSafeImeGateCandidateSource(this, "blocked_raw:" + rawPackage);
            return null;
        }
        String lastStrictInput = lastStrictInputPackage();
        if (lastStrictInput != null) {
            AppState.recordSafeImeGateCandidateSource(this, "last_strict_input:" + lastStrictInput);
            return lastStrictInput;
        }
        String cached = AppState.getLastCachedApp(this);
        long cachedAt = AppState.getLastCachedAppAt(this);
        boolean fresh = cachedAt > 0L
                && System.currentTimeMillis() - cachedAt >= 0L
                && System.currentTimeMillis() - cachedAt <= CACHED_STRICT_PACKAGE_MS;
        if (fresh
                && AppState.isStrictSafeImePackage(cached)
                && !AppState.wasMenuSeenAfterCachedApp(this)
                && !AppState.wasNavigationSeenAfterCachedApp(this)) {
            AppState.recordSafeImeGateCandidateSource(this, "cached_app:" + cached);
            return cached;
        }
        AppState.recordSafeImeGateCandidateSource(this, "none");
        return null;
    }

    private String lastStrictInputPackage() {
        String packageName = AppState.getSafeImeLastStrictInputPackage(this);
        if (!AppState.isStrictSafeImePackage(packageName)) {
            return null;
        }
        long at = AppState.getSafeImeLastStrictInputAt(this);
        long now = System.currentTimeMillis();
        if (at <= 0L || now - at < 0L || now - at > CACHED_STRICT_PACKAGE_MS) {
            return null;
        }
        long menuAt = AppState.getLastMenuSeenAfterAppAt(this);
        long navigationAt = AppState.getLastNavigationSeenAfterAppAt(this);
        if ((menuAt > 0L && menuAt > at) || (navigationAt > 0L && navigationAt > at)) {
            return null;
        }
        return packageName;
    }

    private boolean isNeutralPackage(String packageName) {
        return packageName == null
                || packageName.length() == 0
                || packageName.equals(getPackageName());
    }

    private int overlayType() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        }
        return WindowManager.LayoutParams.TYPE_PHONE;
    }

    private void ensureForeground() {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && manager != null) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "WatchGuard teclado",
                    NotificationManager.IMPORTANCE_MIN
            );
            manager.createNotificationChannel(channel);
        }

        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        Notification notification = builder
                .setSmallIcon(android.R.drawable.ic_menu_edit)
                .setContentTitle("WatchGuard teclado seguro")
                .setContentText("Puerta segura activa para abrir el teclado solo por tap real")
                .setOngoing(true)
                .build();
        startForeground(NOTIFICATION_ID, notification);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static final class GateLayout {
        final int width;
        final int height;
        final int left;
        final int bottom;
        final String zone;
        final String description;
        final boolean rescue;

        GateLayout(int width, int height, int left, int bottom, String zone, String description, boolean rescue) {
            this.width = width;
            this.height = height;
            this.left = left;
            this.bottom = bottom;
            this.zone = zone;
            this.description = description;
            this.rescue = rescue;
        }
    }
}
