param(
    [string]$ToolsDir = ".tools",
    [string]$AndroidSdkDir = ".tools\android-sdk"
)

$ErrorActionPreference = "Stop"

function Resolve-FullPath {
    param([string]$Path)
    $executionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($Path)
}

function Download-IfMissing {
    param(
        [string]$Url,
        [string]$OutFile
    )
    if (Test-Path $OutFile) {
        return
    }
    Write-Host "Descargando $Url"
    Invoke-WebRequest -Uri $Url -OutFile $OutFile -UseBasicParsing
}

function Expand-IfMissing {
    param(
        [string]$Zip,
        [string]$Destination,
        [string]$Marker
    )
    if (Test-Path $Marker) {
        return
    }
    if ((Test-Path $Destination) -and (Get-ChildItem -LiteralPath $Destination -Force | Select-Object -First 1)) {
        throw "Existe $Destination pero falta $Marker. Borra esa carpeta manualmente si quieres reconstruirla."
    }
    New-Item -ItemType Directory -Force -Path $Destination | Out-Null
    Expand-Archive -LiteralPath $Zip -DestinationPath $Destination -Force
}

$tools = Resolve-FullPath $ToolsDir
$sdk = Resolve-FullPath $AndroidSdkDir
$downloads = Join-Path $tools "downloads"
New-Item -ItemType Directory -Force -Path $downloads | Out-Null
New-Item -ItemType Directory -Force -Path $sdk | Out-Null

$jdkZip = Join-Path $downloads "temurin-jdk17.zip"
$gradleZip = Join-Path $downloads "gradle-8.7-bin.zip"
$cmdlineZip = Join-Path $downloads "android-commandlinetools.zip"

Download-IfMissing "https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jdk/hotspot/normal/eclipse?project=jdk" $jdkZip
Download-IfMissing "https://services.gradle.org/distributions/gradle-8.7-bin.zip" $gradleZip
Download-IfMissing "https://dl.google.com/android/repository/commandlinetools-win-14742923_latest.zip" $cmdlineZip

$jdkRoot = Join-Path $tools "jdk17"
$gradleRoot = Join-Path $tools "gradle"
$cmdlineRoot = Join-Path $sdk "cmdline-tools"
$latestRoot = Join-Path $cmdlineRoot "latest"

Expand-IfMissing $jdkZip $jdkRoot (Join-Path $jdkRoot "jdk-17*")
Expand-IfMissing $gradleZip $gradleRoot (Join-Path $gradleRoot "gradle-8.7")

if (-not (Test-Path (Join-Path $latestRoot "bin\sdkmanager.bat"))) {
    $tempCmdline = Join-Path $tools ("cmdline-temp-" + (Get-Date -Format "yyyyMMddHHmmss"))
    Expand-IfMissing $cmdlineZip $tempCmdline (Join-Path $tempCmdline "cmdline-tools\bin\sdkmanager.bat")
    if (Test-Path $latestRoot) {
        throw "Existe $latestRoot pero no contiene sdkmanager.bat. Borra esa carpeta manualmente si quieres reconstruirla."
    }
    New-Item -ItemType Directory -Force -Path $cmdlineRoot | Out-Null
    Move-Item -LiteralPath (Join-Path $tempCmdline "cmdline-tools") -Destination $latestRoot
}

$jdkHome = Get-ChildItem -Path $jdkRoot -Directory | Select-Object -First 1
$gradleHome = Join-Path $gradleRoot "gradle-8.7"
if ($null -eq $jdkHome) {
    throw "No se encontro JDK dentro de $jdkRoot"
}

$env:JAVA_HOME = $jdkHome.FullName
$env:ANDROID_HOME = $sdk
$env:ANDROID_SDK_ROOT = $sdk
$env:Path = "$($env:JAVA_HOME)\bin;$gradleHome\bin;$latestRoot\bin;$sdk\platform-tools;$env:Path"

Write-Host "Aceptando licencias Android SDK"
"y`ny`ny`ny`ny`ny`ny`ny`ny`ny`n" | & (Join-Path $latestRoot "bin\sdkmanager.bat") --sdk_root=$sdk --licenses

Write-Host "Instalando paquetes Android SDK"
& (Join-Path $latestRoot "bin\sdkmanager.bat") --sdk_root=$sdk "platform-tools" "platforms;android-35" "build-tools;35.0.0"

$localProperties = Join-Path (Get-Location) "local.properties"
"sdk.dir=$($sdk -replace '\\','/')" | Set-Content -Path $localProperties -Encoding ASCII

Write-Host ""
Write-Host "Entorno listo para esta terminal:"
Write-Host "`$env:JAVA_HOME='$($env:JAVA_HOME)'"
Write-Host "`$env:ANDROID_HOME='$sdk'"
Write-Host "`$env:Path='$($env:JAVA_HOME)\bin;$gradleHome\bin;$latestRoot\bin;$sdk\platform-tools;...'"
