param(
    [string]$Apk = "app\build\outputs\apk\debug\app-debug.apk",
    [string]$OutDir = "dist"
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path $Apk)) {
    throw "No existe el APK: $Apk. Compila primero con Gradle."
}

New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
$targetApk = Join-Path $OutDir "WatchGuard-VP39-diagnostic.apk"
Copy-Item -LiteralPath $Apk -Destination $targetApk -Force

$html = @"
<!doctype html>
<html lang="es">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>WatchGuard VP39</title>
  <style>
    body { margin: 0; font-family: sans-serif; background: #050505; color: white; }
    main { padding: 18px; max-width: 420px; margin: auto; }
    a { display: block; background: #0b8e8e; color: white; text-align: center; padding: 14px; border-radius: 6px; text-decoration: none; font-weight: 700; }
    p { color: #d7e4e6; line-height: 1.35; }
    code { color: #80ffff; }
  </style>
</head>
<body>
  <main>
    <h1>WatchGuard VP39</h1>
    <p>Descarga la APK diagnostica sin USB. Si Android pregunta, permite instalar apps desconocidas para el navegador o gestor de archivos.</p>
    <a href="WatchGuard-VP39-diagnostic.apk">Descargar APK</a>
    <p>Despues de instalar: abre <code>WatchGuard VP39</code>, activa Overlay, <code>Uso de apps</code> y <code>Permiso camara</code>, luego toca <code>Activar modo boton</code>. Elige WatchGuard como Home temporal si Android lo pide. En apps, el boton congela/descongela. En camara nativa, abre la camara propia de WatchGuard y toma foto. En menu o WatchGuard se delega al launcher real.</p>
  </main>
</body>
</html>
"@

$html | Set-Content -Path (Join-Path $OutDir "index.html") -Encoding UTF8
Write-Host "Paquete listo: $((Resolve-Path $OutDir).Path)"
Write-Host "APK: $((Resolve-Path $targetApk).Path)"
