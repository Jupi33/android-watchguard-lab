# Sanitized Sample Report

This is a shortened, synthetic report shaped like WatchGuard's real diagnostic output. It demonstrates the kind of evidence the app is designed to collect without exposing a real device serial, user data, installed-app history, photos, or private logs.

```json
{
  "generated_at_utc": "2026-05-19T16:40:00Z",
  "app": {
    "package": "com.codex.watchguard",
    "version_name": "0.72.0-camera-downmenu-rescue",
    "version_code": 73
  },
  "android_build": {
    "sdk_int": 27,
    "release": "8.1.0",
    "manufacturer": "VP39-class OEM",
    "model": "VP39-compatible wearable",
    "fingerprint": "redacted"
  },
  "permissions_state": {
    "can_draw_overlays": true,
    "usage_access_enabled": true,
    "camera_permission_granted": true,
    "gpt_automation_accessibility_enabled": true,
    "gpt_accessibility_service_alive": true,
    "default_home_package": "com.dw.launcher",
    "watchguard_launcher_selected": false
  },
  "watchguard_camera_state": {
    "active": false,
    "camera_generation": 10,
    "watch_camera_touch_proxy_enabled": false,
    "watch_camera_touch_inert_enabled": true,
    "watch_camera_top_swipe_blocked_count": 3,
    "watch_camera_bottom_swipe_blocked_count": 14,
    "watch_camera_system_panel_escape_count": 5,
    "watch_camera_system_panel_rebound_count": 5,
    "watch_camera_system_panel_last_package": "com.dw.downmenu",
    "watch_camera_panel_rescue_active": false,
    "watch_camera_panel_restore_attempt_count": 12,
    "watch_camera_downmenu_restore_attempt_count": 8,
    "watch_camera_native_camera_bounce_blocked_count": 0,
    "photo_count": 1,
    "last_error": null
  },
  "lock_mode_results": [
    {
      "mode": "watchguard_camera",
      "outcome": "watch_camera_top_swipe_blocked",
      "detail": "edge=top"
    },
    {
      "mode": "watchguard_camera",
      "outcome": "system_panel_escape",
      "detail": "package=com.dw.downmenu"
    },
    {
      "mode": "watchguard_camera",
      "outcome": "system_panel_fast_rebound",
      "detail": "package=com.dw.downmenu reason=restore_only"
    }
  ]
}
```

## How To Read It

The report does not merely say "the camera froze" or "a panel opened." It records the active mitigation, the observed OEM package, the rebound attempt, and whether the app accidentally bounced to the native camera path.

That is the core engineering value of the project: failures become attributable to a layer in the Android/OEM event pipeline.
