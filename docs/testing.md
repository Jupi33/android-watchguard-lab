# Testing and Verification

## Local Build

```powershell
.\scripts\setup_android_env.ps1
.\gradlew.bat assembleDebug
.\gradlew.bat lintDebug
```

## APK Verification

```powershell
$buildTools = "$env:ANDROID_HOME\build-tools\35.0.0"
& "$buildTools\apksigner.bat" verify --verbose "app\build\outputs\apk\debug\app-debug.apk"
& "$buildTools\aapt.exe" dump badging "app\build\outputs\apk\debug\app-debug.apk"
Get-FileHash -Algorithm SHA256 "app\build\outputs\apk\debug\app-debug.apk"
```

Expected package metadata:

```text
package: com.codex.watchguard
versionCode: 73
versionName: 0.72.0-camera-downmenu-rescue
minSdkVersion: 23
targetSdkVersion: 28
```

## Hardware Test Matrix

These scenarios require compatible Android watch hardware:

| Area | Scenario | Expected Evidence |
| --- | --- | --- |
| Launcher/Home | Select WatchGuard as temporary launcher and press the physical HOME-style input | `home_trap_*` counters and cycle state update |
| Camera capture | Open WatchGuard camera and trigger capture through the physical input path | `photo_count` increments and Camera2 output path is recorded |
| Camera touch | Tap inside WatchGuard camera | Touch remains inert and no tap proxy is recorded |
| Top gesture | Swipe from top edge while camera is visible | `watch_camera_top_swipe_blocked_count` increments; OEM escape is recorded if `com.dw.downmenu` appears |
| Bottom gesture | Swipe from bottom edge while camera is visible | `watch_camera_bottom_swipe_blocked_count` increments; Recents rebound is recorded if `com.dw.recents` appears |
| Native camera bounce | Trigger an OEM panel during camera rescue | WatchGuard camera restores without launching `com.android.camera2` |
| Safe IME | Focus text fields in strict apps | IME opens only through the guarded path |
| Report generation | Generate diagnostic report after tests | JSON includes permissions, foreground state, input devices, camera state, and diagnostic log |

## CI

GitHub Actions runs:

- `./gradlew assembleDebug`
- `./gradlew lintDebug`

CI verifies the source builds from a clean checkout. Hardware-only behavior is documented through device reports and must be validated on physical hardware.
