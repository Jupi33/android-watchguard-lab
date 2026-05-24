package com.codex.watchguard;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;

final class CameraShutterController {
    static final String CAMERA2_PACKAGE = "com.android.camera2";
    static final String CAMERA_PACKAGE = "com.android.camera";

    private static final int KEYCODE_CAMERA = 27;
    private static final long SHUTTER_DELAY_MS = 90L;

    private CameraShutterController() {
    }

    static boolean isCameraScreen(ForegroundResolver.VisibleScreen screen) {
        return screen != null
                && screen.type == ForegroundResolver.SCREEN_APP
                && isCameraPackage(screen.packageName);
    }

    static boolean isCameraPackage(String packageName) {
        return CAMERA2_PACKAGE.equals(packageName) || CAMERA_PACKAGE.equals(packageName);
    }

    static String[] supportedPackages() {
        return new String[]{CAMERA2_PACKAGE, CAMERA_PACKAGE};
    }

    static boolean handleHomePulse(
            Context context,
            ForegroundResolver.VisibleScreen screen,
            String source,
            long homeGapMs
    ) {
        if (!isCameraScreen(screen)) {
            return false;
        }
        if (AppState.isWatchCameraRotaryGuardActive(context)) {
            AppState.recordWatchCameraRotaryBlockedShutter(context, homeGapMs, source);
            ForegroundResolver.restorePackage(context.getApplicationContext(), screen.packageName);
            return true;
        }

        Context appContext = context.getApplicationContext();
        String packageName = screen.packageName;
        AppState.appendLog(appContext, "camera_shutter_detected package=" + packageName
                + " source=" + screen.source
                + " event_age=" + eventAge(screen)
                + " home_gap=" + homeGapMs
                + " home_source=" + source);
        AppState.appendLockModeResult(appContext, "camera_shutter", "detected",
                source + " " + screen.describe());

        if (!AppState.tryAcceptCameraShutter(appContext, packageName)) {
            ForegroundResolver.restorePackage(appContext, packageName);
            return true;
        }

        AppState.appendLog(appContext, "camera_shutter_backend watchguard_camera package="
                + packageName);
        AppState.appendCameraBackendResult(
                appContext,
                "watchguard_camera",
                true,
                0,
                "open own camera only package=" + packageName
        );
        WatchGuardCameraActivity.openForNativeShutter(appContext, packageName, source);
        return true;
    }

    private static void scheduleShutter(Context context, String packageName) {
        final Context appContext = context.getApplicationContext();
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        executeShutter(appContext, packageName);
                    }
                }, "WatchGuardCameraShutter").start();
            }
        }, SHUTTER_DELAY_MS);
    }

    private static void executeShutter(Context context, String packageName) {
        boolean enabled = AppState.isCameraTapAccessibilityEnabled(context);
        boolean ready = CameraTapAccessibilityService.isReady();
        if (enabled && ready && CameraTapAccessibilityService.requestShutter(packageName)) {
            AppState.appendLog(context, "camera_tap_requested package=" + packageName);
            return;
        }

        AppState.appendLog(context, "camera_tap_accessibility_missing"
                + " enabled=" + enabled
                + " ready=" + ready
                + " package=" + packageName);
        AppState.recordCameraTapBackend(
                context,
                "accessibility_missing",
                false,
                -1f,
                -1f,
                "enabled=" + enabled + " ready=" + ready
        );
        sendCameraButtonBroadcast(context, packageName);
    }

    private static void sendCameraButtonBroadcast(Context context, String packageName) {
        Intent intent = new Intent(Intent.ACTION_CAMERA_BUTTON);
        intent.setPackage(packageName);
        intent.putExtra(Intent.EXTRA_KEY_EVENT,
                new KeyEvent(KeyEvent.ACTION_UP, KEYCODE_CAMERA));
        try {
            context.sendBroadcast(intent);
            AppState.appendLog(context, "camera_button_broadcast_sent package=" + packageName);
            AppState.appendCameraBackendResult(
                    context,
                    "camera_button_broadcast",
                    true,
                    0,
                    "sent package=" + packageName
            );
        } catch (RuntimeException error) {
            AppState.appendLog(context, "camera_button_broadcast_failed "
                    + error.getClass().getSimpleName() + ": " + error.getMessage());
            AppState.appendCameraBackendResult(
                    context,
                    "camera_button_broadcast",
                    false,
                    -1,
                    error.getClass().getSimpleName() + ": " + error.getMessage()
            );
        }
    }

    private static long eventAge(ForegroundResolver.VisibleScreen screen) {
        if (screen == null || screen.eventTimeMs <= 0L) {
            return -1L;
        }
        return Math.max(0L, System.currentTimeMillis() - screen.eventTimeMs);
    }
}
