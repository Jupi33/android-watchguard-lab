# WatchGuard VP39

[![Android CI](https://github.com/Jupi33/android-watchguard-lab/actions/workflows/android.yml/badge.svg)](https://github.com/Jupi33/android-watchguard-lab/actions/workflows/android.yml)

Constrained Android firmware diagnostics lab for measuring OEM gesture, accessibility, camera, launcher, and input behavior on VP39-class wearables.

WatchGuard asks a practical systems question:

> What can a normal Android APK control when the firmware intercepts input before the application layer sees it?

The project is built around a low-cost Android 8.1 watch where HOME, Recents, notification panels, camera launch behavior, overlays, keyboard focus, and accessibility events do not behave like standard Android reference devices. Instead of assuming those controls are reliable, WatchGuard instruments them, tries multiple non-privileged strategies, records the observed firmware response, and makes the limits visible in structured diagnostic reports.

No public screenshot or GIF is committed yet. The relevant behavior depends on physical watch firmware, accessibility settings, overlay permissions, launcher selection, and hardware input timing, so the most meaningful verification happens on-device.

## Engineering Problem

Most Android apps receive input after the framework has already classified gestures, key events, window focus, accessibility events, and task transitions. On this device, parts of that pipeline are owned by OEM components such as `com.dw.downmenu`, `com.dw.recents`, and a custom launcher. That means familiar app-level techniques can fail silently or appear to work while the firmware still wins the race.

WatchGuard treats that as an engineering problem rather than a UI problem. It measures which layer receives an event first, which mitigation is actually effective, when the app must stop trying, and when a stronger privilege model would be required.

The current build focuses on reversible controls and explicit evidence. If an app overlay, accessibility overlay, launcher trap, or Camera2 flow cannot fully control a behavior, the report should show why.

## What It Does

WatchGuard VP39 is an Android diagnostics app for constrained wearable firmware. It combines foreground detection, overlay experiments, accessibility event handling, Camera2 capture, a temporary launcher/Home flow, a safe input method, and report generation into one testbed.

The app is intentionally not positioned as a polished consumer kiosk product. It is a lab for answering questions such as:

- Can a non-system APK intercept or rebound OEM gesture panels?
- Can a temporary launcher turn a hardware HOME-style input into an observable state transition?
- When do app overlays lose to firmware-level panels?
- Can Accessibility overlays shield a Camera2 Activity from touch without breaking camera lifecycle?
- How much can an app infer from usage stats, accessibility windows, task state, and its own event log?
- Which behaviors require root, device-owner provisioning, system-app privileges, or framework changes?

GPT automation is included as a UI-automation stress test: it exercises accessibility tree inspection, attachment readiness, send-target validation, and fallback behavior in a real third-party app. It is not the central thesis of the repository.

## System Model

```text
Physical input / OEM panel / app window event
        |
        v
ForegroundResolver + Accessibility services + launcher/Home entrypoint
        |
        v
Input and window-state classifiers
        |
        v
Control surfaces
  - overlay lock
  - accessibility camera shield
  - Camera2 capture surface
  - safe IME gate
  - GPT automation guard
        |
        v
AppState event log + structured diagnostic report
```

The architecture is deliberately evidence-oriented. The app records what it believed was active, which package or event caused a transition, what mitigation was attempted, and whether the firmware escaped that mitigation.

## Key Experiments

- **Launcher/Home trap:** uses a temporary launcher entrypoint to study hardware-button behavior without privileged key interception.
- **Overlay lock:** measures how far `WindowManager` overlays can constrain touches, Back, HOME-adjacent flows, and system panels.
- **Camera shield:** uses Camera2 plus accessibility/app overlays to test whether the app can keep its own camera surface stable when OEM top/bottom gestures try to open panels.
- **OEM panel rebound:** detects real `com.dw.downmenu` and `com.dw.recents` state changes and restores the WatchGuard camera without falling back to the native camera app.
- **Safe IME gate:** prevents unsolicited keyboard openings while allowing deliberate input in constrained apps.
- **Automation safety checks:** validates UI state before attempting attachment/send actions in a third-party app.
- **Device reporting:** exports build, display, permission, launcher, accessibility, input-device, camera, lock, and diagnostic state for review.

## Tech Stack

- Java
- Android SDK 35, target SDK 28, min SDK 23
- Android Gradle Plugin 8.6.1
- Gradle 8.7 wrapper
- Android `AccessibilityService`
- Android `WindowManager` overlay APIs
- Camera2 API
- `InputMethodService`
- PowerShell utility scripts for Android setup, packaging, install, Wi-Fi sharing, and device profile collection

## Repository Structure

```text
app/src/main/java/com/codex/watchguard/
  AppState.java                         central diagnostic state and event log
  ForegroundResolver.java               foreground and navigation classification
  HomeTrapActivity.java                 temporary launcher/Home control surface
  TouchBlockerService.java              overlay lock surface
  GptAutomationAccessibilityService.java accessibility diagnostics, shield, automation guard
  WatchGuardCameraActivity.java         Camera2 preview/capture experiment
  WatchCameraGestureGuardService.java   app-overlay fallback for camera-only touch shielding
  TinyInputMethodService.java           controlled IME behavior

docs/
  architecture.md
  engineering-case-study.md
  sample-report.md
  security-and-limitations.md
  testing.md
```

## Evidence

The app produces JSON reports that make firmware behavior inspectable instead of anecdotal. A sanitized example is available in [docs/sample-report.md](docs/sample-report.md).

The most important evidence fields include:

- foreground package classification and raw signal source
- accessibility service heartbeat and observed window package
- overlay/shield active state and start/stop counters
- Camera2 session state, capture state, and file output
- OEM panel escape and rebound counters
- HOME/launcher trap cycle state
- input-device inventory and observed key classifications

## Limitations

WatchGuard is a non-privileged APK. It cannot guarantee control over firmware code that executes before Android delivers events to app, accessibility, or overlay layers. The project documents those boundaries explicitly.

Some behaviors may require stronger deployment models:

- device-owner provisioning for true kiosk policies
- system-app privileges for lower-level key or panel control
- root or firmware modification for OEM gesture components
- hardware-specific input-driver changes for buttons that never reach Android APIs

See [docs/security-and-limitations.md](docs/security-and-limitations.md) for permission rationale and safety boundaries.

## Getting Started

Clone the repository:

```powershell
git clone https://github.com/Jupi33/android-watchguard-lab.git
cd android-watchguard-lab
```

Bootstrap the local Android toolchain:

```powershell
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

## Verification

The repository includes GitHub Actions for `assembleDebug` and `lintDebug`. Local release-style checks are documented in [docs/testing.md](docs/testing.md), including `aapt`, `apksigner`, and SHA-256 verification.

The current public build keeps:

- package: `com.codex.watchguard`
- version name: `0.72.0-camera-downmenu-rescue`
- version code: `73`

## Environment Variables

No application secrets are required.

The setup script creates a local `local.properties` file pointing Gradle at the Android SDK. That file is intentionally ignored. Optional shell variables used during local builds are:

- `JAVA_HOME`: JDK 17 path
- `ANDROID_HOME` or `ANDROID_SDK_ROOT`: Android SDK path

## Live Demo

There is no hosted live demo because the project depends on physical Android watch firmware, accessibility settings, overlay permissions, and launcher selection. The debug APK can be built locally and tested on compatible hardware.
