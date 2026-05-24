package com.codex.watchguard;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.Locale;

public class WatchAccessibilityService extends AccessibilityService {
    private static final long POWER_LOCK_SAFETY_TIMEOUT_MS = 240_000L;
    private static final int KEYCODE_STEM_PRIMARY_COMPAT = 264;
    private static final long AUTO_FOCUS_WINDOW_MS = 4_000L;
    private static final long USER_TAP_ALLOW_MS = 2_200L;
    private static final long KEYBOARD_HIDE_COOLDOWN_MS = 1_600L;
    private static final long SYSTEM_PANEL_DISMISS_COOLDOWN_MS = 1_200L;
    private static final int MAX_NODE_SCAN_DEPTH = 7;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private String lastWindow = "";
    private long lastWindowChangeUptime;
    private String lastClickedPackage = "";
    private long lastUserClickUptime;
    private long lastKeyboardHideUptime;
    private long lastSystemPanelDismissUptime;

    @Override
    protected void onServiceConnected() {
        AccessibilityServiceInfo info = getServiceInfo();
        if (info == null) {
            info = new AccessibilityServiceInfo();
        }
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                | AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                | AccessibilityEvent.TYPE_VIEW_FOCUSED
                | AccessibilityEvent.TYPE_VIEW_CLICKED
                | AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
                | AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.flags |= AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
        info.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        info.flags |= AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
        info.notificationTimeout = 50;
        setServiceInfo(info);
        AppState.appendLog(this, "accessibility connected; key filter requested");
    }

    @Override
    public boolean onKeyEvent(KeyEvent event) {
        int keyCode = event.getKeyCode();
        String action = event.getAction() == KeyEvent.ACTION_DOWN ? "DOWN" : "UP";
        String keyName = KeyEvent.keyCodeToString(keyCode);
        boolean capture = shouldCaptureHardwareKey(event);
        AppState.appendKeyEvent(
                this,
                action,
                keyName,
                keyCode,
                event.getScanCode(),
                event.getRepeatCount(),
                event.getDeviceId()
        );
        AppState.appendLog(this, "key " + action
                + " " + keyName
                + " code=" + keyCode
                + " scan=" + event.getScanCode()
                + " repeat=" + event.getRepeatCount()
                + " device=" + event.getDeviceId()
                + " capture=" + capture);

        if (capture && AppState.isButtonControlEnabled(this)) {
            if (event.getAction() == KeyEvent.ACTION_UP && event.getRepeatCount() == 0) {
                AppState.appendLog(this, "hardware key captured -> toggle lock");
                TouchBlockerService.toggle(this, POWER_LOCK_SAFETY_TIMEOUT_MS);
            }
            return true;
        }
        if (capture) {
            AppState.appendLog(this, "hardware key observed but button control is OFF");
        }
        return false;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) {
            return;
        }
        String window = String.valueOf(event.getPackageName()) + "/"
                + String.valueOf(event.getClassName());
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && !window.equals(lastWindow)) {
            lastWindow = window;
            lastWindowChangeUptime = SystemClock.uptimeMillis();
            AppState.appendLog(this, "window " + window);
        }
        if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            lastClickedPackage = String.valueOf(event.getPackageName());
            lastUserClickUptime = SystemClock.uptimeMillis();
        }
        maybeDismissSystemPanel(event);
        maybeHideAutoKeyboard(event);
    }

    @Override
    public void onInterrupt() {
        AppState.appendLog(this, "accessibility interrupted");
    }

    @Override
    public void onDestroy() {
        AppState.appendLog(this, "accessibility destroyed");
        super.onDestroy();
    }

    private boolean isLockToggleKey(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_POWER
                || keyCode == KeyEvent.KEYCODE_SLEEP
                || keyCode == KeyEvent.KEYCODE_WAKEUP
                || keyCode == KEYCODE_STEM_PRIMARY_COMPAT
                || keyCode == KeyEvent.KEYCODE_HOME
                || keyCode == KeyEvent.KEYCODE_BACK
                || keyCode == KeyEvent.KEYCODE_APP_SWITCH;
    }

    private boolean shouldCaptureHardwareKey(KeyEvent event) {
        int keyCode = event.getKeyCode();
        if (event.getAction() != KeyEvent.ACTION_DOWN && event.getAction() != KeyEvent.ACTION_UP) {
            return false;
        }
        if (isLockToggleKey(keyCode)) {
            return true;
        }
        if (isTypingKey(keyCode)) {
            return false;
        }
        return event.getScanCode() > 0 || event.getDeviceId() > 0;
    }

    private boolean isTypingKey(int keyCode) {
        return keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_Z;
    }

    private void maybeDismissSystemPanel(AccessibilityEvent event) {
        if (!AppState.isLocked(this)) {
            return;
        }
        String packageName = String.valueOf(event.getPackageName()).toLowerCase(Locale.US);
        String className = String.valueOf(event.getClassName()).toLowerCase(Locale.US);
        boolean looksLikeSystemPanel = packageName.contains("systemui")
                || className.contains("recents")
                || className.contains("statusbar")
                || className.contains("notification")
                || className.contains("quick");
        if (!looksLikeSystemPanel) {
            return;
        }
        long sinceDismiss = SystemClock.uptimeMillis() - lastSystemPanelDismissUptime;
        if (sinceDismiss >= 0 && sinceDismiss < SYSTEM_PANEL_DISMISS_COOLDOWN_MS) {
            return;
        }
        lastSystemPanelDismissUptime = SystemClock.uptimeMillis();
        AppState.appendLog(this, "lock guard dismissing system panel " + packageName + "/" + className);
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                performGlobalAction(GLOBAL_ACTION_BACK);
            }
        }, 120L);
    }

    private void maybeHideAutoKeyboard(AccessibilityEvent event) {
        if (!AppState.isKeyboardGuardEnabled(this)) {
            return;
        }
        String packageName = String.valueOf(event.getPackageName());
        if (packageName.equals(getPackageName())
                || packageName.equals("com.google.android.inputmethod.latin")) {
            return;
        }
        long age = SystemClock.uptimeMillis() - lastWindowChangeUptime;
        if (age < 0 || age > AUTO_FOCUS_WINDOW_MS) {
            return;
        }
        long sinceTap = SystemClock.uptimeMillis() - lastUserClickUptime;
        if (packageName.equals(lastClickedPackage) && sinceTap >= 0 && sinceTap <= USER_TAP_ALLOW_MS) {
            AppState.appendLog(this, "keyboard guard allowed user tap in " + packageName);
            return;
        }
        long sinceHide = SystemClock.uptimeMillis() - lastKeyboardHideUptime;
        if (sinceHide >= 0 && sinceHide < KEYBOARD_HIDE_COOLDOWN_MS) {
            return;
        }
        if (!eventOrWindowHasFocusedTextField(event)) {
            return;
        }
        lastKeyboardHideUptime = SystemClock.uptimeMillis();
        AppState.recordKeyboardGuardHide(this);
        AppState.appendLog(this, "keyboard guard BACK for auto-focus in " + packageName);
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                performGlobalAction(GLOBAL_ACTION_BACK);
            }
        }, 300L);
    }

    private boolean eventOrWindowHasFocusedTextField(AccessibilityEvent event) {
        if (looksLikeTextField(event.getClassName())) {
            return true;
        }
        AccessibilityNodeInfo source = null;
        AccessibilityNodeInfo root = null;
        try {
            source = event.getSource();
            if (source != null && nodeIsFocusedTextField(source)) {
                return true;
            }
            root = getRootInActiveWindow();
            return root != null && scanForFocusedTextField(root, 0);
        } catch (RuntimeException error) {
            AppState.appendLog(this, "keyboard guard scan failed: "
                    + error.getClass().getSimpleName());
            return false;
        } finally {
            if (source != null) {
                source.recycle();
            }
            if (root != null) {
                root.recycle();
            }
        }
    }

    private boolean scanForFocusedTextField(AccessibilityNodeInfo node, int depth) {
        if (node == null || depth > MAX_NODE_SCAN_DEPTH) {
            return false;
        }
        if (nodeIsFocusedTextField(node)) {
            return true;
        }
        int childCount = node.getChildCount();
        for (int i = 0; i < childCount; i++) {
            AccessibilityNodeInfo child = null;
            try {
                child = node.getChild(i);
                if (child != null && scanForFocusedTextField(child, depth + 1)) {
                    return true;
                }
            } finally {
                if (child != null) {
                    child.recycle();
                }
            }
        }
        return false;
    }

    private boolean nodeIsFocusedTextField(AccessibilityNodeInfo node) {
        return node != null && node.isFocused()
                && (node.isEditable() || looksLikeTextField(node.getClassName()));
    }

    private boolean looksLikeTextField(CharSequence classNameValue) {
        String className = String.valueOf(classNameValue).toLowerCase(Locale.US);
        return className.contains("edittext")
                || className.contains("textfield")
                || className.contains("textinput")
                || className.contains("input");
    }
}
