package com.codex.watchguard;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaScannerConnection;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Size;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;

public class WatchGuardCameraActivity extends Activity {
    static final String ACTION_OPEN_AND_SHOOT = "com.codex.watchguard.action.OPEN_WATCH_CAMERA_AND_SHOOT";
    static final String ACTION_OPEN_ONLY = "com.codex.watchguard.action.OPEN_WATCH_CAMERA";
    static final String ACTION_HOME_SHUTTER = "com.codex.watchguard.action.WATCH_CAMERA_HOME_SHUTTER";
    static final String ACTION_RESTORE_AFTER_SYSTEM_PANEL =
            "com.codex.watchguard.action.WATCH_CAMERA_RESTORE_AFTER_SYSTEM_PANEL";

    private static final int REQUEST_CAMERA_PERMISSION = 3201;
    private static final long WARM_CLOSE_DELAY_MS = 2_000L;
    private static final long QUEUED_RETRY_FUDGE_MS = 25L;
    private static final long SINGLE_CLICK_DEADLINE_MS = 1_350L;
    private static final long DOUBLE_CLICK_REBOUNCE_MS = 25L;
    private static final long DOUBLE_CLICK_MAX_MS = 1_200L;
    private static final long DOUBLE_CLICK_BUFFER_MS =
            SINGLE_CLICK_DEADLINE_MS - DOUBLE_CLICK_MAX_MS;
    private static final long DIRECT_KEY_PULSE_DELAY_MS = 90L;
    private static final int EDGE_SWIPE_GUARD_PX = 42;
    private static final int EDGE_SWIPE_TRIGGER_PX = 18;

    private TextureView previewView;
    private TextView statusView;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private HandlerThread cameraThread;
    private Handler cameraHandler;
    private CameraDevice cameraDevice;
    private CameraCaptureSession captureSession;
    private CaptureRequest.Builder previewRequestBuilder;
    private ImageReader imageReader;
    private Size previewSize;
    private String cameraId;
    private String nativeCameraPackage = CameraShutterController.CAMERA2_PACKAGE;
    private String lastPhotoStorageBackend = "app_private";
    private boolean pendingShot;
    private boolean queuedShot;
    private boolean captureInFlight;
    private boolean previewReady;
    private boolean closingFromUi;
    private boolean keepCameraWarm;
    private boolean resumed;
    private boolean singleShotArmed;
    private long singleShotArmedAtUptime;
    private long singleShotDeadlineUptime;
    private long lastHomePulseUptime;
    private int cameraGeneration;
    private boolean directKeyPulsePending;
    private String directKeyPulseSource;
    private boolean edgeSwipeCandidate;
    private boolean edgeSwipeBlocked;
    private String edgeSwipeEdge;
    private float edgeSwipeStartY;

    private final Runnable warmCloseRunnable = new Runnable() {
        @Override
        public void run() {
            handleWarmCloseTimeout();
        }
    };

    private final Runnable queuedShotRunnable = new Runnable() {
        @Override
        public void run() {
            if (!queuedShot) {
                return;
            }
            queuedShot = false;
            updateCameraRuntime();
            takePhoto("queued");
        }
    };

    private final Runnable singleShotRunnable = new Runnable() {
        @Override
        public void run() {
            flushSingleClickDeadline("deadline_elapsed");
        }
    };

    private final Runnable directKeyPulseRunnable = new Runnable() {
        @Override
        public void run() {
            if (!directKeyPulsePending) {
                return;
            }
            String source = directKeyPulseSource == null
                    ? "direct_key_fallback"
                    : directKeyPulseSource;
            directKeyPulsePending = false;
            directKeyPulseSource = null;
            AppState.recordWatchCameraDirectKeyPulseFired(
                    WatchGuardCameraActivity.this,
                    source
            );
            handleWatchButtonPulse(source, -1L);
        }
    };

    private final Runnable gestureGuardHeartbeatRunnable = new Runnable() {
        @Override
        public void run() {
            if (!resumed || closingFromUi) {
                return;
            }
            AppState.markWatchCameraGestureGuardHeartbeat(
                    WatchGuardCameraActivity.this,
                    "activity_heartbeat"
            );
            if (GptAutomationAccessibilityService.startWatchCameraTouchShield(
                    WatchGuardCameraActivity.this,
                    "activity_heartbeat")) {
                WatchCameraGestureGuardService.stop(
                        WatchGuardCameraActivity.this,
                        "accessibility_primary"
                );
            }
            mainHandler.postDelayed(this, 1_000L);
        }
    };

    static void openForNativeShutter(Context context, String nativePackage, String source) {
        Intent intent = new Intent(context, WatchGuardCameraActivity.class);
        intent.setAction(ACTION_OPEN_ONLY);
        intent.putExtra("native_package", nativePackage);
        intent.putExtra("source", source);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_SINGLE_TOP
                | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        try {
            context.startActivity(intent);
            AppState.appendLog(context, "watch_camera_open requested native="
                    + nativePackage + " source=" + source);
            AppState.appendLockModeResult(context, "watchguard_camera", "open_requested",
                    "native=" + nativePackage + " source=" + source);
        } catch (RuntimeException error) {
            AppState.recordWatchCameraError(context, "open failed "
                    + error.getClass().getSimpleName() + ": " + error.getMessage());
        }
    }

    static boolean handleHomeTrapPulse(Context context, long homeGapMs, String source) {
        if (AppState.isWatchCameraInputNoiseActive(context)) {
            AppState.recordWatchCameraHomeIgnoredNoise(context, homeGapMs, source);
            return true;
        }
        Intent intent = new Intent(context, WatchGuardCameraActivity.class);
        intent.setAction(ACTION_HOME_SHUTTER);
        intent.putExtra("source", source);
        intent.putExtra("home_gap", homeGapMs);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_SINGLE_TOP
                | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        try {
            context.startActivity(intent);
            AppState.clearHomeStorm(context, "watch_camera_home");
            AppState.appendLog(context, "camera_home_ambiguous accepted_as_button=true gap="
                    + homeGapMs + " source=" + source);
            AppState.appendLog(context, "watch_camera_home_shutter requested gap="
                    + homeGapMs + " source=" + source);
            AppState.appendLockModeResult(context, "watchguard_camera", "home_shutter",
                    "gap=" + homeGapMs + " source=" + source);
            return true;
        } catch (RuntimeException error) {
            AppState.recordWatchCameraError(context, "home shutter launch failed "
                    + error.getClass().getSimpleName() + ": " + error.getMessage());
            return false;
        }
    }

    static void restoreAfterSystemPanel(Context context, String packageName) {
        Intent intent = new Intent(context, WatchGuardCameraActivity.class);
        intent.setAction(ACTION_RESTORE_AFTER_SYSTEM_PANEL);
        intent.putExtra("system_panel_package", packageName);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_SINGLE_TOP
                | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        try {
            context.startActivity(intent);
            AppState.appendLog(context, "watch_camera_restore_after_system_panel package="
                    + packageName);
        } catch (RuntimeException error) {
            AppState.recordWatchCameraError(context, "restore after panel failed "
                    + error.getClass().getSimpleName() + ": " + error.getMessage());
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(buildContent());
        installWatchCameraSystemUiGuard();
        applyWatchCameraImmersive("create");
        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        closingFromUi = false;
        applyWatchCameraImmersive("resume");
        mainHandler.removeCallbacks(warmCloseRunnable);
        AppState.setWatchCameraActive(this, true, "resume");
        startWatchCameraGestureGuard("resume");
        if (keepCameraWarm && cameraDevice != null && captureSession != null && imageReader != null) {
            keepCameraWarm = false;
            AppState.clearWatchCameraWarmBridge(this, "resume_reuse");
            AppState.appendLog(this, "watch_camera_reuse_session generation=" + cameraGeneration);
            previewReady = true;
            refreshSingleClickDeadline("resume_reuse");
            maybeRunPendingShot();
            return;
        }
        keepCameraWarm = false;
        AppState.clearWatchCameraWarmBridge(this, "resume");
        startCameraThread();
        if (previewView.isAvailable()) {
            openCameraIfAllowed();
        } else {
            previewView.setSurfaceTextureListener(surfaceListener);
        }
        refreshSingleClickDeadline("resume");
    }

    @Override
    protected void onPause() {
        resumed = false;
        boolean preserveForPanel = !closingFromUi
                && AppState.shouldPreserveWatchCameraForPanel(this);
        if (preserveForPanel) {
            keepCameraWarm = true;
            AppState.recordWatchCameraPausePreservedForPanel(this, "pause");
        }
        if (!keepCameraWarm) {
            stopWatchCameraGestureGuard("pause");
        } else {
            AppState.appendLog(this, "watch_camera_touch_shield_preserved reason=pause_warm");
        }
        if (!closingFromUi && keepCameraWarm) {
            AppState.markWatchCameraWarmBridge(this, WARM_CLOSE_DELAY_MS, "pause");
            mainHandler.removeCallbacks(warmCloseRunnable);
            mainHandler.postDelayed(warmCloseRunnable, WARM_CLOSE_DELAY_MS);
            AppState.appendLog(this, "watch_camera_keep_warm generation=" + cameraGeneration + " reason=pause");
        } else {
            closeCamera("pause");
            stopCameraThread();
        }
        super.onPause();
    }

    @Override
    protected void onStop() {
        if (!closingFromUi
                && !keepCameraWarm
                && !AppState.shouldPreserveWatchCameraForPanel(this)) {
            AppState.setWatchCameraActive(this, false, "stop");
        }
        super.onStop();
    }

    @Override
    protected void onUserLeaveHint() {
        if (!closingFromUi) {
            keepCameraWarm = true;
            AppState.markWatchCameraHomeBridge(this, "user_leave_hint");
            AppState.markWatchCameraWarmBridge(this, WARM_CLOSE_DELAY_MS, "user_leave_hint");
        }
        super.onUserLeaveHint();
    }

    @Override
    protected void onDestroy() {
        stopWatchCameraGestureGuard("destroy");
        if (isFinishing()) {
            mainHandler.removeCallbacks(warmCloseRunnable);
            clearSingleClickState("destroy", true);
            closeCamera("destroy");
            stopCameraThread();
            AppState.setWatchCameraActive(this, false, "destroy");
            AppState.clearWatchCameraHomeBridge(this, "destroy");
            AppState.clearWatchCameraWarmBridge(this, "destroy");
            AppState.clearWatchCameraPanelRescue(this, "destroy");
        }
        mainHandler.removeCallbacks(directKeyPulseRunnable);
        super.onDestroy();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            applyWatchCameraImmersive("window_focus");
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            if (AppState.isWatchCameraSystemPanelReboundRecent(this)) {
                if (event.getAction() == KeyEvent.ACTION_UP) {
                    suppressPanelBack("back_key");
                }
                return true;
            }
            if (event.getAction() == KeyEvent.ACTION_UP) {
                closeBackToNative("back_key");
            }
            return true;
        }
        if (WatchInputClassifier.isSystemPowerKey(event)) {
            if (event.getAction() == KeyEvent.ACTION_DOWN
                    && event.getRepeatCount() == 0) {
                AppState.recordWatchCameraPowerKeyConsumed(
                        this,
                        WatchInputClassifier.keyEventLabel(event),
                        "watch_camera_key"
                );
            }
            return true;
        }
        if (WatchInputClassifier.isRotaryKey(event)) {
            if (event.getAction() == KeyEvent.ACTION_DOWN
                    && event.getRepeatCount() == 0) {
                AppState.recordWatchCameraRotaryIgnored(
                        this,
                        KeyEvent.keyCodeToString(event.getKeyCode()),
                        event.getScanCode(),
                        event.getDeviceId(),
                        "watch_camera_key"
                );
                cancelPendingForRotary("watch_camera_key");
            }
            return true;
        }
        if (WatchInputClassifier.isButtonCandidate(event)) {
            AppState.appendKeyEvent(
                    this,
                    "WATCH_CAMERA_IGNORED_" + (event.getAction() == KeyEvent.ACTION_DOWN ? "DOWN" : "UP"),
                    KeyEvent.keyCodeToString(event.getKeyCode()),
                    event.getKeyCode(),
                    event.getScanCode(),
                    event.getRepeatCount(),
                    event.getDeviceId()
            );
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (WatchInputClassifier.isWatchButtonKey(event)) {
                    AppState.recordWatchCameraRealButton(this,
                            WatchInputClassifier.keyEventLabel(event));
                    AppState.appendLog(this, "watch_camera_button_key_consumed_no_noise key="
                            + KeyEvent.keyCodeToString(event.getKeyCode()));
                    if (event.getRepeatCount() == 0) {
                        scheduleDirectKeyPulse(event);
                    }
                } else {
                    AppState.recordWatchCameraRejectedButton(this,
                            WatchInputClassifier.keyEventLabel(event));
                    AppState.recordWatchCameraInputNoise(this,
                            "key_" + KeyEvent.keyCodeToString(event.getKeyCode()));
                }
            }
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (event == null) {
            return true;
        }
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            beginEdgeSwipeCandidateIfNeeded(event);
            return true;
        }
        if (handleEdgeSwipeEvent(event, action)) {
            return true;
        }
        return true;
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent event) {
        if (event != null) {
            if ((event.getSource() & InputDevice.SOURCE_ROTARY_ENCODER)
                    == InputDevice.SOURCE_ROTARY_ENCODER) {
                AppState.recordWatchCameraRotaryIgnored(
                        this,
                        "GENERIC_ROTARY",
                        -1,
                        event.getDeviceId(),
                        "watch_camera_generic_motion"
                );
                cancelPendingForRotary("watch_camera_generic_motion");
                return true;
            }
            AppState.recordWatchCameraInputNoise(this,
                    "generic_motion_source_" + event.getSource());
        }
        return true;
    }

    @Override
    public boolean onTrackballEvent(MotionEvent event) {
        AppState.recordWatchCameraRotaryIgnored(
                this,
                "TRACKBALL_ROTARY",
                -1,
                event == null ? -1 : event.getDeviceId(),
                "watch_camera_trackball"
        );
        cancelPendingForRotary("watch_camera_trackball");
        return true;
    }

    @Override
    public void onBackPressed() {
        if (AppState.isWatchCameraSystemPanelReboundRecent(this)) {
            suppressPanelBack("back");
            return;
        }
        closeBackToNative("back");
    }

    private void suppressPanelBack(String source) {
        AppState.recordWatchCameraPanelBackSuppressed(this, source);
        AppState.markWatchCameraGestureGuardHeartbeat(this, source);
        String panelPackage = AppState.getWatchCameraPanelRescueLastPackage(this);
        if (panelPackage != null && panelPackage.length() > 0) {
            AppState.markWatchCameraPanelRescue(this, panelPackage, source);
            restoreAfterSystemPanel(this, panelPackage);
        }
        applyWatchCameraImmersive("panel_back_suppressed");
        startWatchCameraGestureGuard("panel_back_suppressed");
        setStatus("Lista: boton para foto");
    }

    private void installWatchCameraSystemUiGuard() {
        final View decor = getWindow().getDecorView();
        decor.setOnSystemUiVisibilityChangeListener(new View.OnSystemUiVisibilityChangeListener() {
            @Override
            public void onSystemUiVisibilityChange(int visibility) {
                int required = watchCameraImmersiveFlags();
                if ((visibility & required) != required) {
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            applyWatchCameraImmersive("system_ui_change");
                        }
                    });
                }
            }
        });
    }

    private void applyWatchCameraImmersive(String reason) {
        View decor = getWindow().getDecorView();
        decor.setSystemUiVisibility(watchCameraImmersiveFlags());
        AppState.recordWatchCameraSystemUiReapplied(this, reason);
    }

    private int watchCameraImmersiveFlags() {
        return View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
    }

    private void beginEdgeSwipeCandidateIfNeeded(MotionEvent event) {
        resetEdgeSwipeState();
        int height = getCameraTouchHeight();
        float y = event.getY();
        if (y <= EDGE_SWIPE_GUARD_PX) {
            edgeSwipeCandidate = true;
            edgeSwipeEdge = "top";
        } else if (height > 0 && y >= height - EDGE_SWIPE_GUARD_PX) {
            edgeSwipeCandidate = true;
            edgeSwipeEdge = "bottom";
        }
        if (!edgeSwipeCandidate) {
            return;
        }
        edgeSwipeStartY = y;
        AppState.appendLog(this, "watch_camera_edge_swipe_candidate edge="
                + edgeSwipeEdge + " y=" + Math.round(y) + " h=" + height);
    }

    private boolean handleEdgeSwipeEvent(MotionEvent event, int action) {
        if (!edgeSwipeCandidate) {
            return false;
        }
        if (action == MotionEvent.ACTION_MOVE) {
            maybeBlockEdgeSwipe(event.getY());
            return true;
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            if (edgeSwipeBlocked) {
                applyWatchCameraImmersive("edge_swipe_finish");
            }
            resetEdgeSwipeState();
            return true;
        }
        return true;
    }

    private void maybeBlockEdgeSwipe(float y) {
        if (edgeSwipeBlocked || edgeSwipeEdge == null) {
            return;
        }
        float deltaY = y - edgeSwipeStartY;
        boolean shouldBlock = ("top".equals(edgeSwipeEdge) && deltaY >= EDGE_SWIPE_TRIGGER_PX)
                || ("bottom".equals(edgeSwipeEdge) && -deltaY >= EDGE_SWIPE_TRIGGER_PX);
        if (!shouldBlock) {
            return;
        }
        edgeSwipeBlocked = true;
        AppState.recordWatchCameraEdgeSwipeBlocked(this, edgeSwipeEdge);
        applyWatchCameraImmersive("edge_swipe_" + edgeSwipeEdge);
    }

    private int getCameraTouchHeight() {
        View decor = getWindow().getDecorView();
        int height = decor == null ? 0 : decor.getHeight();
        if (height <= 0) {
            height = getResources().getDisplayMetrics().heightPixels;
        }
        return height;
    }

    private void resetEdgeSwipeState() {
        edgeSwipeCandidate = false;
        edgeSwipeBlocked = false;
        edgeSwipeEdge = null;
        edgeSwipeStartY = 0f;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_CAMERA_PERMISSION) {
            return;
        }
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            setStatus("Permiso OK. Abriendo camara...");
            AppState.appendLog(this, "watch_camera permission granted");
            openCameraIfAllowed();
        } else {
            setStatus("Falta permiso de camara");
            AppState.recordWatchCameraError(this, "camera_permission_denied");
        }
    }

    private View buildContent() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        previewView = new TextureView(this);
        root.addView(previewView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));

        statusView = new TextView(this);
        statusView.setTextColor(Color.WHITE);
        statusView.setTextSize(12);
        statusView.setGravity(Gravity.CENTER_HORIZONTAL);
        statusView.setBackgroundColor(0x99000000);
        statusView.setPadding(dp(8), dp(5), dp(8), dp(5));
        FrameLayout.LayoutParams statusParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP
        );
        root.addView(statusView, statusParams);

        return root;
    }

    private void handleIntent(Intent intent) {
        if (intent == null) {
            return;
        }
        String nativePackage = intent.getStringExtra("native_package");
        if (nativePackage != null && nativePackage.length() > 0) {
            nativeCameraPackage = nativePackage;
        }
        String action = intent.getAction();
        if (ACTION_OPEN_ONLY.equals(action) || ACTION_OPEN_AND_SHOOT.equals(action)) {
            clearSingleClickState("open_only", true);
            pendingShot = false;
            queuedShot = false;
            updateCameraRuntime();
            AppState.recordWatchCameraOpenOnly(this, action);
            setStatus("Lista: boton para foto");
            maybeRunPendingShot();
            return;
        }
        if (ACTION_HOME_SHUTTER.equals(action)) {
            String source = intent.getStringExtra("source");
            long homeGapMs = intent.getLongExtra("home_gap", -1L);
            cancelDirectKeyPulseForHome(source == null ? action : source);
            handleWatchButtonPulse(source == null ? action : source, homeGapMs);
            return;
        }
        if (ACTION_RESTORE_AFTER_SYSTEM_PANEL.equals(action)) {
            String packageName = intent.getStringExtra("system_panel_package");
            AppState.appendLog(this, "watch_camera_restored_after_panel package="
                    + packageName);
            AppState.markWatchCameraPanelRescue(this, packageName, "activity_restore_intent");
            AppState.markWatchCameraGestureGuardHeartbeat(this, "restore_after_panel");
            startWatchCameraGestureGuard("restore_after_panel");
            refreshSingleClickDeadline("restore_after_panel");
            setStatus("Lista: boton para foto");
            return;
        }
    }

    private void handleWatchButtonPulse(String source, long homeGapMs) {
        long now = SystemClock.uptimeMillis();
        hydratePersistentSingleClick(now, "home_pulse");
        if (AppState.isWatchCameraRotaryGuardActive(this)) {
            AppState.recordWatchCameraRotaryBlockedShutter(this, homeGapMs, source);
            cancelPendingForRotary("home_pulse_rotary_guard");
            return;
        }
        long previousPulse = lastHomePulseUptime;
        long pulseGapMs = previousPulse <= 0L ? -1L : Math.max(0L, now - previousPulse);
        lastHomePulseUptime = now;
        AppState.recordWatchCameraHomePulse(this, source, homeGapMs, pulseGapMs);

        if (singleShotArmed && singleShotArmedAtUptime > 0L) {
            long activityAgeMs = Math.max(0L, now - singleShotArmedAtUptime);
            AgeMeasurement age = effectiveDoubleClickAge(homeGapMs, pulseGapMs, activityAgeMs);
            if (age.valueMs < DOUBLE_CLICK_REBOUNCE_MS) {
                AppState.recordWatchCameraLastBlock(this,
                        "double_click_too_fast age=" + age.valueMs
                                + " source=" + age.source);
                AppState.appendLog(this, "watch_camera_double_click_too_fast age="
                        + age.valueMs
                        + " age_source=" + age.source
                        + " min=" + DOUBLE_CLICK_REBOUNCE_MS);
                return;
            }
            if (age.valueMs <= DOUBLE_CLICK_MAX_MS) {
                handleDoubleClick(source, age.valueMs, age.source);
                return;
            }
            fireSingleClickDeadline("late_pulse_before_new age=" + age.valueMs
                    + " source=" + age.source);
        }

        armSingleClick(now, source);
    }

    private void armSingleClick(long nowUptime, String source) {
        singleShotArmed = true;
        singleShotArmedAtUptime = nowUptime;
        singleShotDeadlineUptime = nowUptime + SINGLE_CLICK_DEADLINE_MS;
        AppState.setWatchCameraSingleClickArmed(this, true, SINGLE_CLICK_DEADLINE_MS,
                "arm source=" + source);
        AppState.setWatchCameraPendingCaptureCount(this, 1, "single_click_wait_double");
        AppState.appendLog(this, "watch_camera_single_click_armed deadline_ms="
                + SINGLE_CLICK_DEADLINE_MS + " double_min=" + DOUBLE_CLICK_REBOUNCE_MS
                + " double_max=" + DOUBLE_CLICK_MAX_MS
                + " source=" + source);
        setStatus("Foto en 1.35s; doble boton envia GPT");
        mainHandler.removeCallbacks(singleShotRunnable);
        mainHandler.postDelayed(singleShotRunnable, SINGLE_CLICK_DEADLINE_MS);
    }

    private void handleDoubleClick(String source, long ageMs, String ageSource) {
        if (AppState.getWatchCameraSessionPhotoCount(this) <= 0) {
            long sessionId = AppState.getWatchCameraSessionId(this);
            AppState.markGptPhotoShareNoPhotos(this, sessionId);
            AppState.recordWatchCameraLastBlock(this,
                    "double_click_no_photos age=" + ageMs
                            + " source=" + ageSource);
            AppState.appendLog(this, "watch_camera_double_click_no_photos age=" + ageMs
                    + " age_source=" + ageSource
                    + " session=" + sessionId + " single_click_kept=true");
            setStatus("Sin fotos; tomando foto...");
            refreshSingleClickDeadline("double_click_no_photos_keep_single");
            return;
        }

        clearSingleClickState("double_click_accepted", true);
        pendingShot = false;
        queuedShot = false;
        AppState.recordWatchCameraDoubleClick(this, source, ageMs, ageSource);
        AppState.appendLog(this, "watch_camera_double_click_accepted age=" + ageMs
                + " age_source=" + ageSource
                + " photos=" + AppState.getWatchCameraSessionPhotoCount(this)
                + " source=" + source);
        updateCameraRuntime();
        setStatus("Enviando a GPT...");
        AppState.recordWatchCameraGptShareLaunch(this,
                "double_click age=" + ageMs + " source=" + ageSource);
        AppState.deactivateWatchCameraForGpt(this, "double_click");
        closingFromUi = true;
        keepCameraWarm = false;
        mainHandler.removeCallbacks(warmCloseRunnable);
        stopWatchCameraGestureGuard("gpt_share");
        closeCamera("gpt_share");
        stopCameraThread();
        GptPhotoShareCoordinator.startSessionShare(this, "watch_camera_double_click");
        finish();
        overridePendingTransition(0, 0);
    }

    private AgeMeasurement effectiveDoubleClickAge(
            long homeGapMs,
            long pulseGapMs,
            long activityAgeMs
    ) {
        if (homeGapMs >= 0L) {
            return new AgeMeasurement(homeGapMs, "home_gap");
        }
        if (pulseGapMs >= 0L) {
            return new AgeMeasurement(pulseGapMs, "activity_pulse_gap");
        }
        return new AgeMeasurement(activityAgeMs, "activity_elapsed");
    }

    private void scheduleDirectKeyPulse(KeyEvent event) {
        directKeyPulsePending = true;
        directKeyPulseSource = "direct_key_" + WatchInputClassifier.keyEventLabel(event);
        mainHandler.removeCallbacks(directKeyPulseRunnable);
        mainHandler.postDelayed(directKeyPulseRunnable, DIRECT_KEY_PULSE_DELAY_MS);
        AppState.recordWatchCameraDirectKeyPulseScheduled(this, directKeyPulseSource);
    }

    private void cancelDirectKeyPulseForHome(String source) {
        if (!directKeyPulsePending) {
            return;
        }
        directKeyPulsePending = false;
        directKeyPulseSource = null;
        mainHandler.removeCallbacks(directKeyPulseRunnable);
        AppState.recordWatchCameraDirectKeyPulseCancelledHome(this, source);
    }

    private void cancelPendingForRotary(String source) {
        boolean hadPending = singleShotArmed
                || pendingShot
                || queuedShot
                || directKeyPulsePending
                || AppState.getWatchCameraPendingCaptureCount(this) > 0;
        mainHandler.removeCallbacks(singleShotRunnable);
        mainHandler.removeCallbacks(queuedShotRunnable);
        mainHandler.removeCallbacks(directKeyPulseRunnable);
        directKeyPulsePending = false;
        directKeyPulseSource = null;
        pendingShot = false;
        queuedShot = false;
        clearSingleClickState("rotary_cancelled_pending", true);
        AppState.recordWatchCameraRotaryCancelledPending(this, source, hadPending);
        updateCameraRuntime();
        setStatus("Perilla ignorada");
    }

    private void hydratePersistentSingleClick(long nowUptime, String reason) {
        if (singleShotArmed || !AppState.isWatchCameraSingleClickArmed(this)) {
            return;
        }
        long armedAtWall = AppState.getWatchCameraSingleClickArmedAt(this);
        if (armedAtWall <= 0L) {
            return;
        }
        long nowWall = System.currentTimeMillis();
        long ageMs = Math.max(0L, nowWall - armedAtWall);
        long deadlineWall = AppState.getWatchCameraSingleClickDeadline(this);
        singleShotArmed = true;
        singleShotArmedAtUptime = nowUptime - ageMs;
        long remainingMs = deadlineWall <= 0L ? 0L : deadlineWall - nowWall;
        singleShotDeadlineUptime = nowUptime + remainingMs;
        AppState.appendLog(this, "watch_camera_single_click_hydrated reason=" + reason
                + " age=" + ageMs
                + " remaining=" + remainingMs);
        if (remainingMs > 0L) {
            mainHandler.removeCallbacks(singleShotRunnable);
            mainHandler.postDelayed(singleShotRunnable, remainingMs);
        }
    }

    private void refreshSingleClickDeadline(String reason) {
        if (!singleShotArmed) {
            return;
        }
        long now = SystemClock.uptimeMillis();
        long remainingMs = singleShotDeadlineUptime <= 0L
                ? 0L
                : Math.max(0L, singleShotDeadlineUptime - now);
        if (remainingMs <= 0L) {
            flushSingleClickDeadline(reason);
            return;
        }
        AppState.setWatchCameraSingleClickArmed(this, true, remainingMs,
                "refresh " + reason);
        mainHandler.removeCallbacks(singleShotRunnable);
        mainHandler.postDelayed(singleShotRunnable, remainingMs);
    }

    private void flushSingleClickDeadline(String reason) {
        if (!singleShotArmed) {
            return;
        }
        long now = SystemClock.uptimeMillis();
        long remainingMs = singleShotDeadlineUptime <= 0L
                ? 0L
                : singleShotDeadlineUptime - now;
        if (remainingMs > 0L) {
            mainHandler.removeCallbacks(singleShotRunnable);
            mainHandler.postDelayed(singleShotRunnable, remainingMs);
            return;
        }
        fireSingleClickDeadline(reason);
    }

    private void fireSingleClickDeadline(String reason) {
        if (!singleShotArmed) {
            return;
        }
        if (AppState.isWatchCameraRotaryGuardActive(this)) {
            AppState.recordWatchCameraRotaryBlockedShutter(this, -1L,
                    "single_click_deadline");
            cancelPendingForRotary("single_click_deadline");
            return;
        }
        long now = SystemClock.uptimeMillis();
        AppState.appendLog(this, "watch_camera_single_deadline_elapsed reason="
                + reason + " armed_age=" + Math.max(0L, now - singleShotArmedAtUptime));
        clearSingleClickState("deadline_elapsed", false);
        requestWatchButtonCapture(reason);
    }

    private void requestWatchButtonCapture(String source) {
        if (AppState.isWatchCameraRotaryGuardActive(this)) {
            AppState.recordWatchCameraRotaryBlockedShutter(this, -1L, source);
            cancelPendingForRotary("capture_request_rotary_guard");
            return;
        }
        pendingShot = true;
        AppState.recordWatchCameraCaptureRequested(this, source, previewReady);
        updateCameraRuntime();
        if (!previewReady || cameraHandler == null) {
            AppState.recordWatchCameraWaitingPreview(this, source, previewReady, cameraHandler != null);
            setStatus("Preparando foto...");
            return;
        }
        AppState.appendLog(this, "watch_camera_capture_requested source=" + source
                + " preview_ready=true");
        maybeRunPendingShot();
    }

    private void clearSingleClickState(String reason, boolean clearPendingCount) {
        mainHandler.removeCallbacks(singleShotRunnable);
        singleShotArmed = false;
        singleShotArmedAtUptime = 0L;
        singleShotDeadlineUptime = 0L;
        AppState.setWatchCameraSingleClickArmed(this, false, 0L, reason);
        if (clearPendingCount) {
            AppState.setWatchCameraPendingCaptureCount(this, 0, reason);
        }
    }

    private void startCameraThread() {
        if (cameraThread != null) {
            return;
        }
        cameraThread = new HandlerThread("WatchGuardCamera");
        cameraThread.start();
        cameraHandler = new Handler(cameraThread.getLooper());
    }

    private void stopCameraThread() {
        if (cameraThread == null) {
            return;
        }
        cameraThread.quitSafely();
        try {
            cameraThread.join(700L);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        cameraThread = null;
        cameraHandler = null;
    }

    private final TextureView.SurfaceTextureListener surfaceListener =
            new TextureView.SurfaceTextureListener() {
                @Override
                public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                    openCameraIfAllowed();
                }

                @Override
                public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
                }

                @Override
                public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                    if (keepCameraWarm) {
                        AppState.appendLog(WatchGuardCameraActivity.this,
                                "watch_camera_keep_warm surface");
                        return false;
                    }
                    return true;
                }

                @Override
                public void onSurfaceTextureUpdated(SurfaceTexture surface) {
                }
            };

    private void openCameraIfAllowed() {
        if (!AppState.hasCameraPermission(this)) {
            setStatus("Permiso de camara requerido");
            AppState.recordWatchCameraError(this, "camera_permission_missing");
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
            return;
        }
        openCamera();
    }

    @SuppressLint("MissingPermission")
    private void openCamera() {
        if (cameraDevice != null) {
            AppState.appendLog(this, "watch_camera_reuse_session generation=" + cameraGeneration);
            maybeRunPendingShot();
            return;
        }
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        if (manager == null) {
            fail("camera_manager_missing");
            return;
        }
        try {
            cameraId = chooseCameraId(manager);
            if (cameraId == null) {
                fail("camera_id_missing");
                return;
            }
            int generation = nextCameraGeneration("open");
            configureSizes(manager, cameraId, generation);
            manager.openCamera(cameraId, stateCallbackFor(generation), cameraHandler);
            setStatus("Abriendo camara...");
        } catch (CameraAccessException error) {
            fail("open access " + error.getMessage());
        } catch (RuntimeException error) {
            fail("open " + error.getClass().getSimpleName() + ": " + error.getMessage());
        }
    }

    private CameraDevice.StateCallback stateCallbackFor(final int generation) {
        return new CameraDevice.StateCallback() {
            @Override
            public void onOpened(CameraDevice camera) {
                if (!isCurrentGeneration(generation)) {
                    AppState.appendLog(WatchGuardCameraActivity.this,
                            "watch_camera_callback_ignored type=opened generation=" + generation
                                    + " current=" + cameraGeneration);
                    camera.close();
                    return;
                }
                cameraDevice = camera;
                createPreviewSession(generation);
            }

            @Override
            public void onDisconnected(CameraDevice camera) {
                if (!isCurrentGeneration(generation)) {
                    AppState.appendLog(WatchGuardCameraActivity.this,
                            "watch_camera_callback_ignored type=disconnected generation=" + generation
                                    + " current=" + cameraGeneration);
                    camera.close();
                    return;
                }
                camera.close();
                cameraDevice = null;
                previewReady = false;
                fail("camera_disconnected");
            }

            @Override
            public void onError(CameraDevice camera, int error) {
                if (!isCurrentGeneration(generation)) {
                    AppState.appendLog(WatchGuardCameraActivity.this,
                            "watch_camera_callback_ignored type=error generation=" + generation
                                    + " current=" + cameraGeneration);
                    camera.close();
                    return;
                }
                camera.close();
                cameraDevice = null;
                previewReady = false;
                fail("camera_error=" + error);
            }
        };
    }

    private void createPreviewSession(final int generation) {
        if (cameraDevice == null || !previewView.isAvailable()) {
            return;
        }
        if (!isCurrentGeneration(generation)) {
            AppState.appendLog(this, "watch_camera_callback_ignored type=preview_start generation="
                    + generation + " current=" + cameraGeneration);
            return;
        }
        try {
            SurfaceTexture texture = previewView.getSurfaceTexture();
            if (texture == null) {
                fail("preview_texture_missing");
                return;
            }
            if (imageReader == null) {
                fail("preview_reader_missing");
                return;
            }
            if (previewSize != null) {
                texture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
            }
            Surface previewSurface = new Surface(texture);
            previewRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            previewRequestBuilder.addTarget(previewSurface);
            previewRequestBuilder.set(CaptureRequest.CONTROL_MODE,
                    CaptureRequest.CONTROL_MODE_AUTO);
            previewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);

            cameraDevice.createCaptureSession(
                    Arrays.asList(previewSurface, imageReader.getSurface()),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(CameraCaptureSession session) {
                            if (!isCurrentGeneration(generation) || cameraDevice == null) {
                                AppState.appendLog(WatchGuardCameraActivity.this,
                                        "watch_camera_callback_ignored type=configured generation="
                                                + generation + " current=" + cameraGeneration);
                                try {
                                    session.close();
                                } catch (RuntimeException ignored) {
                                }
                                return;
                            }
                            captureSession = session;
                            try {
                                captureSession.setRepeatingRequest(
                                        previewRequestBuilder.build(),
                                        null,
                                        cameraHandler
                                );
                                previewReady = true;
                                setStatus("Camara lista");
                                updateCameraRuntime();
                                maybeRunPendingShot();
                            } catch (CameraAccessException error) {
                                fail("preview request " + error.getMessage());
                            }
                        }

                        @Override
                        public void onConfigureFailed(CameraCaptureSession session) {
                            if (!isCurrentGeneration(generation)) {
                                AppState.appendLog(WatchGuardCameraActivity.this,
                                        "watch_camera_callback_ignored type=configure_failed generation="
                                                + generation + " current=" + cameraGeneration);
                                return;
                            }
                            fail("preview_config_failed");
                        }
                    },
                    cameraHandler
            );
        } catch (CameraAccessException error) {
            fail("preview access " + error.getMessage());
        } catch (RuntimeException error) {
            fail("preview " + error.getClass().getSimpleName() + ": " + error.getMessage());
        }
    }

    private void maybeRunPendingShot() {
        if (!pendingShot) {
            return;
        }
        if (AppState.isWatchCameraRotaryGuardActive(this)) {
            AppState.recordWatchCameraRotaryBlockedShutter(this, -1L,
                    "pending_shot");
            cancelPendingForRotary("pending_shot_rotary_guard");
            return;
        }
        if (!previewReady || cameraHandler == null) {
            AppState.recordWatchCameraWaitingPreview(this, "maybe_run_pending",
                    previewReady, cameraHandler != null);
            return;
        }
        pendingShot = false;
        updateCameraRuntime();
        takePhoto("pending");
    }

    private void takePhoto(String source) {
        if (AppState.isWatchCameraRotaryGuardActive(this)) {
            AppState.recordWatchCameraRotaryBlockedShutter(this, -1L, source);
            cancelPendingForRotary("take_photo_rotary_guard");
            return;
        }
        AppState.setWatchCameraPendingCaptureCount(this, 0, "take_photo_" + source);
        if (!previewReady || cameraDevice == null || captureSession == null || imageReader == null) {
            pendingShot = true;
            updateCameraRuntime();
            AppState.recordWatchCameraWaitingPreview(this, "take_photo_" + source,
                    previewReady, cameraHandler != null);
            setStatus("Preparando foto...");
            return;
        }
        if (captureInFlight) {
            AppState.recordWatchCameraLastBlock(this, "capture_in_flight");
            queueShot("in_flight", 0L);
            return;
        }
        long readyInMs = AppState.getWatchCameraShotReadyInMs(this);
        if (readyInMs > 0L) {
            AppState.recordWatchCameraLastBlock(this, "rate_limit ready_in=" + readyInMs);
            queueShot("rate_limit", readyInMs + QUEUED_RETRY_FUDGE_MS);
            return;
        }
        if (!AppState.tryAcceptWatchCameraShot(this, source)) {
            AppState.recordWatchCameraLastBlock(this, "throttled");
            queueShot("throttled", AppState.getWatchCameraShotReadyInMs(this) + QUEUED_RETRY_FUDGE_MS);
            return;
        }
        try {
            final int generation = cameraGeneration;
            CaptureRequest.Builder captureBuilder =
                    cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(imageReader.getSurface());
            captureBuilder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
            captureBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            captureInFlight = true;
            updateCameraRuntime();
            setStatus("Tomando foto...");
            captureSession.capture(
                    captureBuilder.build(),
                    new CameraCaptureSession.CaptureCallback() {
                        @Override
                        public void onCaptureFailed(
                                CameraCaptureSession session,
                                CaptureRequest request,
                                CaptureFailure failure
                        ) {
                            if (!isCurrentGeneration(generation)) {
                                AppState.appendLog(WatchGuardCameraActivity.this,
                                        "watch_camera_callback_ignored type=capture_failed generation="
                                                + generation + " current=" + cameraGeneration);
                                return;
                            }
                            captureInFlight = false;
                            updateCameraRuntime();
                            fail("capture_failed reason=" + failure.getReason());
                            runQueuedShotIfNeeded();
                        }
                    },
                    cameraHandler
            );
        } catch (CameraAccessException error) {
            captureInFlight = false;
            updateCameraRuntime();
            fail("capture access " + error.getMessage());
            runQueuedShotIfNeeded();
        } catch (RuntimeException error) {
            captureInFlight = false;
            updateCameraRuntime();
            fail("capture " + error.getClass().getSimpleName() + ": " + error.getMessage());
            runQueuedShotIfNeeded();
        }
    }

    private ImageReader.OnImageAvailableListener imageListenerFor(final int generation) {
        return new ImageReader.OnImageAvailableListener() {
            @Override
            public void onImageAvailable(ImageReader reader) {
                Image image = null;
                try {
                    image = reader.acquireNextImage();
                    if (!isCurrentGeneration(generation)) {
                        AppState.appendLog(WatchGuardCameraActivity.this,
                                "watch_camera_callback_ignored type=image generation="
                                        + generation + " current=" + cameraGeneration);
                        return;
                    }
                    if (image == null) {
                        fail("image_missing");
                        return;
                    }
                    ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                    byte[] bytes = new byte[buffer.remaining()];
                    buffer.get(bytes);
                    File file = writePhoto(bytes);
                    MediaScannerConnection.scanFile(
                            WatchGuardCameraActivity.this,
                            new String[]{file.getAbsolutePath()},
                            new String[]{"image/jpeg"},
                            null
                    );
                    AppState.recordWatchCameraPhoto(
                            WatchGuardCameraActivity.this,
                            file.getAbsolutePath(),
                            lastPhotoStorageBackend
                    );
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            setStatus("Foto guardada");
                        }
                    });
                } catch (IOException | RuntimeException error) {
                    fail("save " + error.getClass().getSimpleName() + ": " + error.getMessage());
                } finally {
                    if (image != null) {
                        image.close();
                    }
                    if (isCurrentGeneration(generation)) {
                        captureInFlight = false;
                        updateCameraRuntime();
                        runQueuedShotIfNeeded();
                    }
                }
            }
        };
    }

    private void queueShot(String reason, long delayMs) {
        queuedShot = true;
        updateCameraRuntime();
        long safeDelay = Math.max(0L, delayMs);
        AppState.appendLog(this, "watch_camera_capture_queued reason=" + reason
                + " delay=" + safeDelay);
        setStatus(safeDelay > 0L ? "Foto en cola" : "Guardando foto...");
        if (cameraHandler == null) {
            pendingShot = true;
            updateCameraRuntime();
            return;
        }
        cameraHandler.removeCallbacks(queuedShotRunnable);
        cameraHandler.postDelayed(queuedShotRunnable, safeDelay);
    }

    private void runQueuedShotIfNeeded() {
        if (!queuedShot || cameraHandler == null) {
            return;
        }
        long readyInMs = AppState.getWatchCameraShotReadyInMs(this);
        cameraHandler.removeCallbacks(queuedShotRunnable);
        cameraHandler.postDelayed(queuedShotRunnable, readyInMs + QUEUED_RETRY_FUDGE_MS);
    }

    private File writePhoto(byte[] bytes) throws IOException {
        if (AppState.hasPublicPhotoPermission(this)) {
            File publicFile = nextPhotoFile(true);
            try {
                writeBytes(publicFile, bytes);
                lastPhotoStorageBackend = "dcim_camera";
                return publicFile;
            } catch (IOException error) {
                AppState.recordWatchCameraStorageFallback(
                        this,
                        "public_write_failed_" + error.getClass().getSimpleName()
                );
            }
        } else {
            AppState.recordWatchCameraStorageFallback(this, "missing_write_external_storage");
        }

        File privateFile = nextPhotoFile(false);
        writeBytes(privateFile, bytes);
        lastPhotoStorageBackend = "app_private_fallback";
        return privateFile;
    }

    private void writeBytes(File file, byte[] bytes) throws IOException {
        FileOutputStream output = new FileOutputStream(file);
        try {
            output.write(bytes);
        } finally {
            output.close();
        }
    }

    private File nextPhotoFile(boolean preferPublic) throws IOException {
        File dir;
        if (preferPublic) {
            dir = new File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
                    "Camera"
            );
        } else {
            dir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
            if (dir == null) {
                dir = new File(getFilesDir(), "Pictures");
            }
        }
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("mkdir failed " + dir.getAbsolutePath());
        }
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(new Date());
        String prefix = preferPublic ? "IMG_" : "WG_";
        return new File(dir, prefix + stamp + ".jpg");
    }

    private void configureSizes(CameraManager manager, String id, int generation) throws CameraAccessException {
        CameraCharacteristics characteristics = manager.getCameraCharacteristics(id);
        StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        if (map == null) {
            throw new IllegalStateException("stream map missing");
        }
        Size[] jpegSizes = map.getOutputSizes(ImageFormat.JPEG);
        Size jpegSize = chooseSmallReasonableSize(jpegSizes, 640 * 480);
        if (jpegSize == null) {
            throw new IllegalStateException("jpeg size missing");
        }
        imageReader = ImageReader.newInstance(
                jpegSize.getWidth(),
                jpegSize.getHeight(),
                ImageFormat.JPEG,
                2
        );
        imageReader.setOnImageAvailableListener(imageListenerFor(generation), cameraHandler);

        Size[] previewSizes = map.getOutputSizes(SurfaceTexture.class);
        previewSize = chooseSmallReasonableSize(previewSizes, 320 * 385);
        if (previewSize == null) {
            previewSize = jpegSize;
        }
    }

    private String chooseCameraId(CameraManager manager) throws CameraAccessException {
        String fallback = null;
        for (String id : manager.getCameraIdList()) {
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(id);
            Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
            if (fallback == null) {
                fallback = id;
            }
            if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) {
                return id;
            }
        }
        return fallback;
    }

    private Size chooseSmallReasonableSize(Size[] sizes, int minArea) {
        if (sizes == null || sizes.length == 0) {
            return null;
        }
        Size best = null;
        for (Size size : sizes) {
            if (size == null) {
                continue;
            }
            int area = size.getWidth() * size.getHeight();
            if (area >= minArea && (best == null || area < best.getWidth() * best.getHeight())) {
                best = size;
            }
        }
        if (best != null) {
            return best;
        }
        best = sizes[0];
        for (Size size : sizes) {
            if (size == null) {
                continue;
            }
            int area = size.getWidth() * size.getHeight();
            int bestArea = best.getWidth() * best.getHeight();
            if (area > bestArea) {
                best = size;
            }
        }
        return best;
    }

    private void closeCamera(String reason) {
        boolean preserveButtonCapture = "pause".equals(reason) && (singleShotArmed || pendingShot);
        nextCameraGeneration("close_" + reason);
        previewReady = false;
        if (!preserveButtonCapture) {
            pendingShot = false;
        }
        queuedShot = false;
        if (!preserveButtonCapture) {
            clearSingleClickState("close_" + reason, true);
        } else {
            refreshSingleClickDeadline("close_pause_preserve");
            AppState.appendLog(this, "watch_camera_preserve_pending_capture reason=" + reason);
        }
        captureInFlight = false;
        updateCameraRuntime();
        if (cameraHandler != null) {
            cameraHandler.removeCallbacks(queuedShotRunnable);
        }
        if (captureSession != null) {
            try {
                captureSession.close();
            } catch (RuntimeException ignored) {
            }
            captureSession = null;
        }
        if (cameraDevice != null) {
            try {
                cameraDevice.close();
            } catch (RuntimeException ignored) {
            }
            cameraDevice = null;
        }
        if (imageReader != null) {
            try {
                imageReader.close();
            } catch (RuntimeException ignored) {
            }
            imageReader = null;
        }
    }

    private void closeBackToNative(String reason) {
        closingFromUi = true;
        keepCameraWarm = false;
        mainHandler.removeCallbacks(warmCloseRunnable);
        stopWatchCameraGestureGuard(reason);
        AppState.appendLog(this, "watch_camera_back_close reason=" + reason);
        AppState.setWatchCameraActive(this, false, reason);
        AppState.clearWatchCameraHomeBridge(this, reason);
        AppState.clearWatchCameraWarmBridge(this, reason);
        AppState.clearWatchCameraPanelRescue(this, reason);
        closeCamera(reason);
        stopCameraThread();
        if (nativeCameraPackage != null && nativeCameraPackage.length() > 0) {
            ForegroundResolver.restorePackage(this, nativeCameraPackage);
        }
        finish();
    }

    private void startWatchCameraGestureGuard(String reason) {
        AppState.markWatchCameraGestureGuardHeartbeat(this, reason);
        if (!GptAutomationAccessibilityService.startWatchCameraTouchShield(this, reason)) {
            WatchCameraGestureGuardService.start(this);
        }
        mainHandler.removeCallbacks(gestureGuardHeartbeatRunnable);
        mainHandler.postDelayed(gestureGuardHeartbeatRunnable, 1_000L);
    }

    private void stopWatchCameraGestureGuard(String reason) {
        mainHandler.removeCallbacks(gestureGuardHeartbeatRunnable);
        GptAutomationAccessibilityService.stopWatchCameraTouchShield(this, reason);
        WatchCameraGestureGuardService.stop(this, reason);
    }

    private void handleWarmCloseTimeout() {
        if (!keepCameraWarm || resumed || closingFromUi) {
            return;
        }
        if (AppState.shouldPreserveWatchCameraForPanel(this)) {
            AppState.appendLog(this, "watch_camera_warm_timeout_delayed reason=panel_rescue");
            AppState.markWatchCameraWarmBridge(this, WARM_CLOSE_DELAY_MS,
                    "panel_rescue_delay");
            mainHandler.removeCallbacks(warmCloseRunnable);
            mainHandler.postDelayed(warmCloseRunnable, WARM_CLOSE_DELAY_MS);
            return;
        }
        stopWatchCameraGestureGuard("warm_timeout");
        AppState.recordWatchCameraWarmClose(this, "timeout");
        keepCameraWarm = false;
        closeCamera("warm_timeout");
        stopCameraThread();
        AppState.setWatchCameraActive(this, false, "warm_timeout");
        AppState.clearWatchCameraWarmBridge(this, "warm_timeout");
    }

    private int nextCameraGeneration(String reason) {
        cameraGeneration += 1;
        AppState.setWatchCameraRuntime(this, captureInFlight, pendingShot || queuedShot, cameraGeneration);
        AppState.appendLog(this, "watch_camera_generation generation=" + cameraGeneration
                + " reason=" + reason);
        return cameraGeneration;
    }

    private boolean isCurrentGeneration(int generation) {
        return generation == cameraGeneration;
    }

    private void updateCameraRuntime() {
        AppState.setWatchCameraRuntime(
                this,
                captureInFlight,
                pendingShot || queuedShot,
                cameraGeneration
        );
    }

    private void fail(String message) {
        AppState.recordWatchCameraError(this, message);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                setStatus(compactError(message));
            }
        });
    }

    private String compactError(String message) {
        if (message == null) {
            return "Camara reiniciando";
        }
        if (message.contains("CameraDevice")
                || message.contains("ImageReader")
                || message.contains("Surface")
                || message.contains("already closed")
                || message.contains("NullPointerException")) {
            return "Camara reiniciando";
        }
        return "Error camara";
    }

    private void setStatus(String value) {
        if (statusView == null) {
            return;
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            statusView.setText(value);
            return;
        }
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                statusView.setText(value);
            }
        });
    }

    private static final class AgeMeasurement {
        final long valueMs;
        final String source;

        AgeMeasurement(long valueMs, String source) {
            this.valueMs = Math.max(0L, valueMs);
            this.source = source == null ? "unknown" : source;
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
