# Engineering Case Study: Camera Gestures vs OEM Panels

## Problem

The VP39-class watch firmware owns top and bottom edge gestures. Swiping from the top can open `com.dw.downmenu`; swiping from the bottom can open `com.dw.recents`. On a reference Android device, an Activity may be able to rely on immersive mode, touch dispatch, or app overlays to reduce accidental navigation. On this firmware, those layers can receive the gesture and still lose to an OEM panel.

The camera experiment exists because it creates a tight, measurable problem:

- keep a custom Camera2 preview active
- make touch inert inside that camera surface
- allow physical-button capture
- prevent accidental return to the native camera app
- record when the firmware opens a panel anyway

## Attempts and Findings

`dispatchTouchEvent()` and Activity-level gesture handling were not sufficient. The firmware could classify edge gestures before the Activity had a meaningful chance to stop the system panel.

Top and bottom app-overlay bands improved visibility into the gesture path but did not stop the OEM panel reliably. Reports showed both facts at the same time: WatchGuard counted the swipe as blocked, and the system still opened `com.dw.downmenu` or `com.dw.recents`.

A full-screen app overlay reduced the app's own touch handling but introduced UX problems when normal taps were treated as actionable signals. That made the camera feel laggy and created false restore paths.

An accessibility overlay became the primary shield because it sits in a different layer from normal application overlays. It is paired with strict event filtering: normal taps are absorbed without proxy behavior, while real `TYPE_WINDOW_STATE_CHANGED` events from `com.dw.downmenu` and `com.dw.recents` are recorded as OEM panel escapes.

The app does not use global Back as the default rescue path because Back can be delivered to the restored Activity and trigger the camera's own close path. Instead, the current approach restores/reorders the WatchGuard camera and suppresses camera-native bounce during a short rescue window.

## Result

The final behavior is not framed as perfect kiosk control. It is framed as measured control with explicit limits:

- normal camera touch is inert
- real OEM panel escapes are counted
- Recents can be rebounded to the WatchGuard camera
- Downmenu requires a longer rescue window because it does not always produce the same lifecycle signal as Recents
- native camera bounce is guarded during panel rescue

That outcome is useful because it distinguishes three classes of behavior:

- controllable at Activity level
- controllable with Accessibility or overlay layers
- not fully controllable by a normal APK on this firmware

## Engineering Lesson

The important lesson is not that one Android API is "the fix." The useful result is the layered diagnostic method: try the least-privileged control surface, measure the firmware response, move only when the evidence shows the layer is too late, and keep a report that can prove which boundary was reached.
