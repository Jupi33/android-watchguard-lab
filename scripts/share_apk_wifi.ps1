param(
    [int]$Port = 8080,
    [string]$OutDir = "dist"
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path (Join-Path $OutDir "WatchGuard-VP39-diagnostic.apk"))) {
    & "$PSScriptRoot\package_dist.ps1" -OutDir $OutDir
}

$python = Get-Command python -ErrorAction SilentlyContinue
if ($null -eq $python) {
    $python = Get-Command py -ErrorAction SilentlyContinue
}
if ($null -eq $python) {
    throw "No encontre Python para servir archivos. Usa el APK en dist o instala Python."
}

$ips = Get-NetIPAddress -AddressFamily IPv4 |
    Where-Object {
        $_.IPAddress -notlike "127.*" -and
        $_.IPAddress -notlike "169.254.*" -and
        $_.PrefixOrigin -ne "WellKnown"
    } |
    Select-Object -ExpandProperty IPAddress

Write-Host ""
Write-Host "Abre uno de estos links desde el reloj:"
foreach ($ip in $ips) {
    Write-Host "  http://$ip`:$Port/"
}
Write-Host ""
Write-Host "Deja esta ventana abierta mientras descargas la APK."
Write-Host "Presiona Ctrl+C para detener."

Push-Location $OutDir
try {
    if ($python.Source -like "*py.exe") {
        & $python.Source -3 -m http.server $Port
    } else {
        & $python.Source -m http.server $Port
    }
} finally {
    Pop-Location
}
