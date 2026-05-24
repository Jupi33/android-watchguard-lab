param(
    [string]$Adb = ".\.tools\android-sdk\platform-tools\adb.exe",
    [string]$OutDir = "device-reports"
)

$ErrorActionPreference = "Stop"
$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$target = Join-Path $OutDir $timestamp
New-Item -ItemType Directory -Force -Path $target | Out-Null

function Save-Adb {
    param(
        [string]$Name,
        [string[]]$Args
    )
    $path = Join-Path $target $Name
    & $Adb @Args *> $path
}

Save-Adb "adb-devices.txt" @("devices", "-l")
Save-Adb "getprop.txt" @("shell", "getprop")
Save-Adb "wm-size.txt" @("shell", "wm", "size")
Save-Adb "wm-density.txt" @("shell", "wm", "density")
Save-Adb "dumpsys-input.txt" @("shell", "dumpsys", "input")
Save-Adb "dumpsys-window-policy.txt" @("shell", "dumpsys", "window", "policy")
Save-Adb "dumpsys-window-windows.txt" @("shell", "dumpsys", "window", "windows")
Save-Adb "dumpsys-power.txt" @("shell", "dumpsys", "power")
Save-Adb "dumpsys-package-watchguard.txt" @("shell", "dumpsys", "package", "com.codex.watchguard")
Save-Adb "settings-default-ime.txt" @("shell", "settings", "get", "secure", "default_input_method")
Save-Adb "settings-accessibility.txt" @("shell", "settings", "get", "secure", "enabled_accessibility_services")
Save-Adb "watchguard-logcat.txt" @("logcat", "-d", "-v", "time", "-s", "WatchGuard:I", "*:S")
Save-Adb "watchguard-shared-prefs.xml" @("exec-out", "run-as", "com.codex.watchguard", "cat", "shared_prefs/watch_guard_state.xml")

Write-Host "Reporte guardado en: $((Resolve-Path $target).Path)"
