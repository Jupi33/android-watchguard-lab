# Security and Limitations

WatchGuard uses sensitive Android capabilities because the project studies firmware behavior near system boundaries. The repository documents those capabilities explicitly so reviewers can distinguish engineering intent from hidden privilege assumptions.

## Permissions and Services

`SYSTEM_ALERT_WINDOW` is used for overlay experiments and lock surfaces. The app uses overlays to measure what a normal APK can place above other windows and where OEM system panels still take precedence.

`BIND_ACCESSIBILITY_SERVICE` is used for window-state diagnostics, key filtering when Android exposes the key, camera touch shielding, and guarded UI automation experiments. Accessibility is not used to extract personal content.

`PACKAGE_USAGE_STATS` helps classify foreground state when accessibility or task signals are incomplete.

`CAMERA` and `WRITE_EXTERNAL_STORAGE` support the controlled Camera2 capture experiment on Android 8.1-era storage behavior.

`WRITE_SETTINGS`, `WRITE_SECURE_SETTINGS`, and battery-optimization controls are present because the device frequently changes screen timeout, input method, and background-service behavior. Some of these capabilities require explicit user or ADB provisioning and are reported as unavailable when missing.

## No Secrets

The app does not require API keys, cloud credentials, tokens, or `.env` files. Build-time local files such as `local.properties` are ignored.

## Ethical Boundaries

This is a diagnostics and control lab for owned test hardware. It should not be used to bypass another user's device controls, automate accounts without consent, or conceal accessibility behavior.

## Technical Boundaries

WatchGuard is a non-privileged APK. It cannot guarantee control over behavior implemented below or before the app-accessible Android layers.

Known boundaries include:

- OEM panels that classify gestures before Activity touch dispatch
- hardware inputs that never reach Android key APIs
- system surfaces that outrank app overlays
- lifecycle behavior that differs between Recents, top panels, launcher, and native camera
- battery or OEM background policies that can stop services unexpectedly

When those boundaries are reached, the project records evidence instead of pretending the app has stronger control than it does.
