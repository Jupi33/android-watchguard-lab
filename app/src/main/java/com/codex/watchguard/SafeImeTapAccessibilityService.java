package com.codex.watchguard;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.graphics.Rect;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.lang.ref.WeakReference;

public class SafeImeTapAccessibilityService extends AccessibilityService {
    private static final int MAX_NODE_SCAN_DEPTH = 8;
    private static final long CLICK_TO_FOCUS_MS = 500L;
    private static final long NOT_SELECTED_LOG_MS = 2_500L;

    private static WeakReference<SafeImeTapAccessibilityService> activeService =
            new WeakReference<>(null);

    private long lastClickUptime;
    private String lastClickPackage = "";
    private long lastNotSelectedLogUptime;

    static boolean isReady() {
        return activeService.get() != null;
    }

    @Override
    protected void onServiceConnected() {
        AccessibilityServiceInfo info = getServiceInfo();
        if (info == null) {
            info = new AccessibilityServiceInfo();
        }
        info.eventTypes = AccessibilityEvent.TYPE_VIEW_CLICKED
                | AccessibilityEvent.TYPE_VIEW_FOCUSED;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        info.flags |= AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
        info.notificationTimeout = 60;
        setServiceInfo(info);
        activeService = new WeakReference<>(this);
        AppState.appendLog(this, "safe_ime_tap_accessibility connected");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) {
            return;
        }
        int type = event.getEventType();
        if (type != AccessibilityEvent.TYPE_VIEW_CLICKED
                && type != AccessibilityEvent.TYPE_VIEW_FOCUSED) {
            return;
        }

        String packageName = String.valueOf(event.getPackageName());
        if (packageName.length() == 0 || getPackageName().equals(packageName)) {
            return;
        }
        if (!AppState.isWatchKeyboardSelected(this)) {
            recordNotSelected(packageName);
            return;
        }

        AccessibilityNodeInfo source = null;
        AccessibilityNodeInfo root = null;
        try {
            source = event.getSource();
            EditableMatch sourceMatch = editableMatch(source, "source");
            if (type == AccessibilityEvent.TYPE_VIEW_CLICKED) {
                lastClickUptime = SystemClock.uptimeMillis();
                lastClickPackage = packageName;
                if (sourceMatch != null) {
                    acceptTap(packageName, sourceMatch, "clicked_source");
                    return;
                }
                root = getRootInActiveWindow();
                EditableMatch focused = focusedEditable(root, 0);
                if (focused != null) {
                    acceptTap(packageName, focused, "clicked_focused");
                    return;
                }
                return;
            }

            if (sourceMatch != null) {
                AppState.recordSafeImeA11yFocus(
                        this,
                        packageName,
                        sourceMatch.field,
                        "source"
                );
                if (recentClick(packageName)) {
                    acceptTap(packageName, sourceMatch, "focus_after_click");
                }
            }
        } catch (RuntimeException error) {
            AppState.recordSafeImeCrashGuard(this, "a11y_event", error);
        } finally {
            if (source != null) {
                source.recycle();
            }
            if (root != null) {
                root.recycle();
            }
        }
    }

    @Override
    public void onInterrupt() {
        AppState.appendLog(this, "safe_ime_tap_accessibility interrupted");
    }

    @Override
    public void onDestroy() {
        SafeImeTapAccessibilityService current = activeService.get();
        if (current == this) {
            activeService = new WeakReference<>(null);
        }
        AppState.appendLog(this, "safe_ime_tap_accessibility destroyed");
        super.onDestroy();
    }

    private void acceptTap(String packageName, EditableMatch match, String source) {
        AppState.recordSafeImeA11yTap(
                this,
                packageName,
                match.field,
                source,
                match.detail
        );
        TinyInputMethodService.notifyRealTapFromAccessibility(packageName, match.field);
    }

    private boolean recentClick(String packageName) {
        long age = SystemClock.uptimeMillis() - lastClickUptime;
        return age >= 0L && age <= CLICK_TO_FOCUS_MS
                && packageName != null
                && packageName.equals(lastClickPackage);
    }

    private void recordNotSelected(String packageName) {
        long now = SystemClock.uptimeMillis();
        if (now - lastNotSelectedLogUptime < NOT_SELECTED_LOG_MS) {
            return;
        }
        lastNotSelectedLogUptime = now;
        AppState.recordSafeImeA11yNotSelected(this, packageName);
    }

    private EditableMatch focusedEditable(AccessibilityNodeInfo node, int depth) {
        if (node == null || depth > MAX_NODE_SCAN_DEPTH) {
            return null;
        }
        EditableMatch match = editableMatch(node, "focused");
        if (match != null && node.isFocused()) {
            return match;
        }
        int childCount = node.getChildCount();
        for (int index = 0; index < childCount; index++) {
            AccessibilityNodeInfo child = null;
            try {
                child = node.getChild(index);
                EditableMatch childMatch = focusedEditable(child, depth + 1);
                if (childMatch != null) {
                    return childMatch;
                }
            } finally {
                if (child != null) {
                    child.recycle();
                }
            }
        }
        return null;
    }

    private EditableMatch editableMatch(AccessibilityNodeInfo node, String source) {
        if (node == null || !looksEditable(node)) {
            return null;
        }
        Rect bounds = new Rect();
        node.getBoundsInScreen(bounds);
        String viewId = safeString(node.getViewIdResourceName());
        String className = safeString(node.getClassName());
        String field = source
                + ":"
                + className
                + ":"
                + viewId
                + ":"
                + bounds.left
                + ","
                + bounds.top
                + "-"
                + bounds.right
                + ","
                + bounds.bottom;
        String detail = "class=" + className
                + " view_id=" + viewId
                + " bounds=" + bounds.toShortString()
                + " focused=" + node.isFocused()
                + " editable=" + node.isEditable();
        return new EditableMatch(field, detail);
    }

    private boolean looksEditable(AccessibilityNodeInfo node) {
        if (node.isEditable()) {
            return true;
        }
        CharSequence rawClass = node.getClassName();
        if (rawClass == null) {
            return false;
        }
        String className = rawClass.toString().toLowerCase();
        return className.contains("edittext")
                || className.contains("textinput")
                || className.contains("webedittext");
    }

    private String safeString(CharSequence value) {
        return value == null ? "none" : value.toString();
    }

    private static final class EditableMatch {
        final String field;
        final String detail;

        EditableMatch(String field, String detail) {
            this.field = field;
            this.detail = detail;
        }
    }
}
