param(
    [string]$Adb = ".\.tools\android-sdk\platform-tools\adb.exe",
    [string]$Apk = "app\build\outputs\apk\debug\app-debug.apk"
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path $Apk)) {
    throw "No existe el APK: $Apk. Compila primero con Gradle."
}

& $Adb install -r $Apk
& $Adb shell monkey -p com.codex.watchguard 1
