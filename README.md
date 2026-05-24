# WatchGuard VP39

Android systems diagnostics and control lab for constrained VP39-class wearable firmware.

No public screenshot or GIF is committed yet; this project targets physical Android 8.1 watch hardware where the most important behavior is verified on-device.

## What it does

WatchGuard VP39 is an Android diagnostic application built for a low-cost Android 8.1 smartwatch whose firmware intercepts hardware buttons, system gestures, overlays, camera flows, and input-method behavior in non-standard ways. The app turns those constraints into measurable experiments: it records device state, tests what a normal APK can and cannot control, and produces structured reports that make firmware behavior debuggable.

The project includes a launcher/Home interception flow, foreground overlay locking, an accessibility-driven automation service, a minimal Camera2 capture surface, and a custom input method. Together these components explore how far a non-privileged Android app can go before the device requires root, device-owner provisioning, a system app, or framework-level changes.

The current build is intentionally conservative: it favors explicit diagnostics, reversible controls, and preserving working behavior over broad assumptions about the firmware.

## Tech stack

- Java
- Android SDK 35, target SDK 28, min SDK 23
- Android Gradle Plugin 8.6.1
- Gradle 8.7 wrapper
- Android AccessibilityService APIs
- Android overlay WindowManager APIs
- Camera2 API
- InputMethodService
- PowerShell utility scripts for setup, packaging, install, and device report collection

## Key features

- Structured device report covering build metadata, display metrics, runtime settings, permissions, input devices, accessibility state, launcher state, camera state, lock state, and diagnostic logs.
- Home/launcher trap that can treat the watch button as a controllable state transition when WatchGuard is selected as the temporary launcher.
- Foreground overlay lock designed for small Android watch screens and firmware that aggressively routes HOME, Back, Recents, and panel gestures.
- Camera2-based WatchGuard camera used as a controlled alternative to OEM camera behavior, with physical-button capture and guarded interaction paths.
- Accessibility-based camera touch shield and OEM panel rebound diagnostics for firmware overlays such as `com.dw.downmenu` and `com.dw.recents`.
- Safe IME and gate overlay experiments to prevent unsolicited keyboard openings in strict apps while still allowing deliberate text entry.
- GPT photo-share automation path with attachment readiness checks, send-button risk detection, and manual fallback when the UI cannot be verified safely.
- Local scripts for provisioning Android tooling, packaging the debug APK, serving it over Wi-Fi, installing with ADB, and collecting device profiles.

## Architecture

The app is a single Android application with several narrowly scoped runtime components:

```text
MainActivity
  -> permission checks, mode controls, report generation

HomeTrapActivity
  -> temporary launcher/Home entrypoint for hardware-button cycles

TouchBlockerService / GuardKeeperService / StayAwakeService
  -> overlay lock, lifecycle recovery, foreground keep-alive, screen timeout support

GptAutomationAccessibilityService
  -> key filtering, camera touch shield, GPT share automation, system-panel diagnostics

WatchGuardCameraActivity / WatchCameraGestureGuardService
  -> Camera2 preview/capture and fallback touch shield for camera-only behavior

TinyInputMethodService / SafeImeGateService
  -> controlled keyboard behavior on small-screen apps

DeviceReport / AppState / ForegroundResolver
  -> shared state, diagnostics, foreground classification, and report serialization
```

State is intentionally centralized in `AppState` so reports can explain what the app believed was active, why it made a transition, and which firmware signal caused that transition.

## Getting started

Clone the repository and bootstrap the local Android toolchain:

```powershell
git clone <repo-url>
cd WatchGuardVP39
.\scripts\setup_android_env.ps1
```

Build the debug APK:

```powershell
.\gradlew.bat assembleDebug
```

Run Android lint:

```powershell
.\gradlew.bat lintDebug
```

Install and open on a connected Android device:

```powershell
.\scripts\install_and_open.ps1
```

Package a local APK download folder:

```powershell
.\scripts\package_dist.ps1
```

Serve the APK over the local network for watch installation:

```powershell
.\scripts\share_apk_wifi.ps1
```

## Environment variables

No application secrets are required.

The setup script creates a local `local.properties` file pointing Gradle at the Android SDK. That file is intentionally ignored. Optional shell variables used during local builds are:

- `JAVA_HOME`: JDK 17 path
- `ANDROID_HOME` or `ANDROID_SDK_ROOT`: Android SDK path

## Live demo

There is no hosted live demo because the project depends on physical Android watch firmware, accessibility settings, overlay permissions, and launcher selection. The debug APK can be built locally and tested on compatible hardware.
