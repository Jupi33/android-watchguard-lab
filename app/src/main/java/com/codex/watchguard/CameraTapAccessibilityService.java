package com.codex.watchguard;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.accessibilityservice.GestureDescription;
import android.annotation.TargetApi;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.lang.ref.WeakReference;

public class CameraTapAccessibilityService extends AccessibilityService {
    private static final int MAX_NODE_SCAN_DEPTH = 8;
    private static final float SHUTTER_X_RATIO = 0.50f;
    private static final float SHUTTER_Y_RATIO = 0.825f;
    private static final float MIN_CLICKABLE_Y_RATIO = 0.52f;
    private static final float MAX_NODE_DISTANCE_PX = 92f;

    private static WeakReference<CameraTapAccessibilityService> activeService =
            new WeakReference<>(null);

    private final Handler handler = new Handler(Looper.getMainLooper());

    static boolean isReady() {
        CameraTapAccessibilityService service = activeService.get();
        return service != null;
    }

    static boolean requestShutter(String packageName) {
        CameraTapAccessibilityService service = activeService.get();
        if (service == null) {
            return false;
        }
        service.tapShutter(packageName);
        return true;
    }

    @Override
    protected void onServiceConnected() {
        AccessibilityServiceInfo info = getServiceInfo();
        if (info == null) {
            info = new AccessibilityServiceInfo();
        }
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                | AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        info.flags |= AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
        info.notificationTimeout = 50;
        setServiceInfo(info);
        activeService = new WeakReference<>(this);
        AppState.appendLog(this, "camera tap accessibility connected");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Passive service: shutter taps are requested explicitly by HomeTrapActivity.
    }

    @Override
    public void onInterrupt() {
        AppState.appendLog(this, "camera tap accessibility interrupted");
    }

    @Override
    public void onDestroy() {
        CameraTapAccessibilityService current = activeService.get();
        if (current == this) {
            activeService = new WeakReference<>(null);
        }
        AppState.appendLog(this, "camera tap accessibility destroyed");
        super.onDestroy();
    }

    private void tapShutter(String packageName) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                performTap(packageName, 0);
            }
        });
    }

    private void performTap(String packageName, int attempt) {
        DisplayMetrics metrics = realMetrics();
        float targetX = metrics.widthPixels * SHUTTER_X_RATIO;
        float targetY = metrics.heightPixels * SHUTTER_Y_RATIO;
        AccessibilityNodeInfo root = null;
        try {
            root = getRootInActiveWindow();
            if (root != null
                    && root.getPackageName() != null
                    && !CameraShutterController.isCameraPackage(String.valueOf(root.getPackageName()))) {
                String actualPackage = String.valueOf(root.getPackageName());
                AppState.appendLog(this, "camera_tap_wrong_window package=" + actualPackage
                        + " expected=" + packageName
                        + " attempt=" + attempt);
                if (attempt == 0) {
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            performTap(packageName, 1);
                        }
                    }, 180L);
                } else {
                    AppState.recordCameraTapBackend(
                            this,
                            "wrong_window",
                            false,
                            -1f,
                            -1f,
                            "actual=" + actualPackage + " expected=" + packageName
                    );
                }
                return;
            }
            NodeMatch match = findBestShutterNode(root, targetX, targetY, metrics.heightPixels);
            if (match != null && match.node != null) {
                boolean clicked = match.node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                AppState.recordCameraTapBackend(
                        this,
                        "node_click",
                        clicked,
                        match.centerX,
                        match.centerY,
                        "distance=" + Math.round(match.distance)
                );
                AppState.appendLog(this, "camera_tap_node_click success=" + clicked
                        + " x=" + Math.round(match.centerX)
                        + " y=" + Math.round(match.centerY)
                        + " distance=" + Math.round(match.distance));
                match.node.recycle();
                if (clicked) {
                    return;
                }
            }
        } catch (RuntimeException error) {
            AppState.appendLog(this, "camera_tap_node_scan_failed "
                    + error.getClass().getSimpleName() + ": " + error.getMessage());
        } finally {
            if (root != null) {
                root.recycle();
            }
        }
        dispatchTapGesture(targetX, targetY, packageName);
    }

    private void dispatchTapGesture(float x, float y, String packageName) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            AppState.recordCameraTapBackend(this, "gesture", false, x, y, "api_below_24");
            AppState.appendLog(this, "camera_tap_gesture unsupported api="
                    + Build.VERSION.SDK_INT);
            return;
        }
        dispatchTapGestureApi24(x, y, packageName);
    }

    @TargetApi(Build.VERSION_CODES.N)
    private void dispatchTapGestureApi24(float x, float y, String packageName) {
        Path path = new Path();
        path.moveTo(x, y);
        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path, 0L, 70L))
                .build();
        boolean started = dispatchGesture(gesture, new GestureResultCallback() {
            @Override
            public void onCompleted(GestureDescription gestureDescription) {
                AppState.recordCameraTapBackend(
                        CameraTapAccessibilityService.this,
                        "gesture",
                        true,
                        x,
                        y,
                        "completed package=" + packageName
                );
                AppState.appendLog(CameraTapAccessibilityService.this, "camera_tap_gesture success=true"
                        + " x=" + Math.round(x)
                        + " y=" + Math.round(y));
            }

            @Override
            public void onCancelled(GestureDescription gestureDescription) {
                AppState.recordCameraTapBackend(
                        CameraTapAccessibilityService.this,
                        "gesture",
                        false,
                        x,
                        y,
                        "cancelled package=" + packageName
                );
                AppState.appendLog(CameraTapAccessibilityService.this, "camera_tap_gesture cancelled"
                        + " x=" + Math.round(x)
                        + " y=" + Math.round(y));
            }
        }, null);
        if (!started) {
            AppState.recordCameraTapBackend(this, "gesture", false, x, y, "dispatch_false");
            AppState.appendLog(this, "camera_tap_gesture success=false"
                    + " x=" + Math.round(x)
                    + " y=" + Math.round(y));
        }
    }

    private NodeMatch findBestShutterNode(
            AccessibilityNodeInfo node,
            float targetX,
            float targetY,
            int screenHeight
    ) {
        return findBestShutterNode(node, targetX, targetY, screenHeight, 0, null);
    }

    private NodeMatch findBestShutterNode(
            AccessibilityNodeInfo node,
            float targetX,
            float targetY,
            int screenHeight,
            int depth,
            NodeMatch currentBest
    ) {
        if (node == null || depth > MAX_NODE_SCAN_DEPTH) {
            return currentBest;
        }

        NodeMatch best = currentBest;
        Rect bounds = new Rect();
        node.getBoundsInScreen(bounds);
        if (node.isClickable() && usefulBounds(bounds, screenHeight)) {
            float centerX = bounds.exactCenterX();
            float centerY = bounds.exactCenterY();
            float dx = centerX - targetX;
            float dy = centerY - targetY;
            float distance = (float) Math.sqrt(dx * dx + dy * dy);
            if (distance <= MAX_NODE_DISTANCE_PX
                    && (best == null || distance < best.distance)) {
                if (best != null && best.node != null) {
                    best.node.recycle();
                }
                best = new NodeMatch(AccessibilityNodeInfo.obtain(node), centerX, centerY, distance);
            }
        }

        int childCount = node.getChildCount();
        for (int index = 0; index < childCount; index++) {
            AccessibilityNodeInfo child = null;
            try {
                child = node.getChild(index);
                best = findBestShutterNode(child, targetX, targetY, screenHeight, depth + 1, best);
            } finally {
                if (child != null) {
                    child.recycle();
                }
            }
        }
        return best;
    }

    private boolean usefulBounds(Rect bounds, int screenHeight) {
        if (bounds == null || bounds.isEmpty()) {
            return false;
        }
        int width = bounds.width();
        int height = bounds.height();
        if (width < 26 || height < 26 || width > 170 || height > 170) {
            return false;
        }
        return bounds.centerY() >= screenHeight * MIN_CLICKABLE_Y_RATIO;
    }

    private DisplayMetrics realMetrics() {
        DisplayMetrics metrics = new DisplayMetrics();
        WindowManager windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (windowManager != null && windowManager.getDefaultDisplay() != null) {
            windowManager.getDefaultDisplay().getRealMetrics(metrics);
            return metrics;
        }
        return getResources().getDisplayMetrics();
    }

    private static final class NodeMatch {
        final AccessibilityNodeInfo node;
        final float centerX;
        final float centerY;
        final float distance;

        NodeMatch(AccessibilityNodeInfo node, float centerX, float centerY, float distance) {
            this.node = node;
            this.centerX = centerX;
            this.centerY = centerY;
            this.distance = distance;
        }
    }
}
