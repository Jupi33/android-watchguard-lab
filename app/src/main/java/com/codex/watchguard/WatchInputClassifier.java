package com.codex.watchguard;

import android.view.InputDevice;
import android.view.KeyEvent;

import java.util.Locale;

final class WatchInputClassifier {
    private static final int KEYCODE_STEM_PRIMARY_COMPAT = 264;
    private static final int SCAN_WATCH_BUTTON_PRIMARY = 62;
    private static final int SCAN_ROTARY_COMPAT = 61;
    private static final int SCAN_ROTARY_UP_COMPAT = 65;
    private static final int SCAN_ROTARY_CENTER_COMPAT = 66;
    private static final int SCAN_ROTARY = 87;

    private WatchInputClassifier() {
    }

    static boolean isRotaryKey(KeyEvent event) {
        if (event == null) {
            return false;
        }
        return event.getKeyCode() == KeyEvent.KEYCODE_F11
                || event.getKeyCode() == KeyEvent.KEYCODE_F3
                || event.getKeyCode() == KeyEvent.KEYCODE_F7
                || event.getKeyCode() == KeyEvent.KEYCODE_F8
                || event.getScanCode() == SCAN_ROTARY_COMPAT
                || event.getScanCode() == SCAN_ROTARY_UP_COMPAT
                || event.getScanCode() == SCAN_ROTARY_CENTER_COMPAT
                || event.getScanCode() == SCAN_ROTARY
                || hasRotarySource(event)
                || isRotaryDevice(event);
    }

    static boolean isButtonCandidate(KeyEvent event) {
        if (event == null) {
            return false;
        }
        int keyCode = event.getKeyCode();
        if (isWatchButtonKey(event)) {
            return true;
        }
        if (keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_Z) {
            return false;
        }
        return event.getScanCode() > 0 || event.getDeviceId() > 0;
    }

    static boolean isSystemPowerKey(KeyEvent event) {
        if (event == null) {
            return false;
        }
        int keyCode = event.getKeyCode();
        return keyCode == KeyEvent.KEYCODE_POWER
                || keyCode == KeyEvent.KEYCODE_SLEEP
                || keyCode == KeyEvent.KEYCODE_WAKEUP;
    }

    static boolean isWatchButtonKey(KeyEvent event) {
        if (event == null || isRotaryKey(event)) {
            return false;
        }
        int keyCode = event.getKeyCode();
        return keyCode == KeyEvent.KEYCODE_HOME
                || keyCode == KeyEvent.KEYCODE_F4
                || keyCode == KEYCODE_STEM_PRIMARY_COMPAT
                || isKnownWatchButtonScan(event);
    }

    static String keyEventLabel(KeyEvent event) {
        if (event == null) {
            return "unknown_key";
        }
        return KeyEvent.keyCodeToString(event.getKeyCode())
                + "_scan_" + event.getScanCode()
                + "_device_" + event.getDeviceId();
    }

    private static boolean isKnownWatchButtonScan(KeyEvent event) {
        int scanCode = event.getScanCode();
        return scanCode == SCAN_WATCH_BUTTON_PRIMARY;
    }

    private static boolean hasRotarySource(KeyEvent event) {
        return (event.getSource() & InputDevice.SOURCE_ROTARY_ENCODER)
                == InputDevice.SOURCE_ROTARY_ENCODER;
    }

    private static boolean isRotaryDevice(KeyEvent event) {
        InputDevice device = event.getDevice();
        if (device == null || device.getName() == null) {
            return false;
        }
        String name = device.getName().toLowerCase(Locale.US);
        return name.contains("rotary") || name.contains("encoder");
    }
}
