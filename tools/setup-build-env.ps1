#requires -Version 5.1
<#
.SYNOPSIS
    Downloads and wires up everything needed to build ProcWatch from the command line,
    without installing Android Studio.

.DESCRIPTION
    Fetches JDK 17, the Android SDK command-line tools and Gradle 8.9 into one folder under
    %LOCALAPPDATA%, installs the SDK packages this project needs, accepts the licences,
    generates the Gradle wrapper and — unless -SkipBuild is passed — builds a debug APK.

    Nothing is installed system-wide and nothing needs administrator rights. To undo the whole
    thing, delete the install root and remove the two user environment variables.

    Safe to re-run: anything already downloaded is skipped.

.PARAMETER InstallRoot
    Where the toolchain goes. Defaults to %LOCALAPPDATA%\procwatch-build.

.PARAMETER SkipBuild
    Set up the toolchain but do not run a build afterwards.

.EXAMPLE
    .\tools\setup-build-env.ps1
#>
[CmdletBinding()]
param(
    [string]$InstallRoot = (Join-Path $env:LOCALAPPDATA 'procwatch-build'),
    [switch]$SkipBuild
)

$ErrorActionPreference = 'Stop'

# Windows PowerShell 5.1 can still default to TLS 1.0, which every one of these hosts refuses.
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

# Invoke-WebRequest spends most of a large download repainting its progress bar.
$ProgressPreference = 'SilentlyContinue'

$ProjectRoot = Split-Path -Parent $PSScriptRoot

function Write-Step { param([string]$Text) Write-Host "`n=== $Text" -ForegroundColor Cyan }
function Write-Note { param([string]$Text) Write-Host "    $Text" -ForegroundColor DarkGray }
function Write-Good { param([string]$Text) Write-Host "    $Text" -ForegroundColor Green }

function Get-RemoteFile {
    param([string]$Url, [string]$Destination)

    if (Test-Path $Destination) {
        Write-Note "already downloaded: $(Split-Path -Leaf $Destination)"
        return
    }
    Write-Note "downloading $(Split-Path -Leaf $Destination) ..."
    $temp = "$Destination.part"
    try {
        # WebClient streams to disk and is markedly faster than Invoke-WebRequest here.
        (New-Object System.Net.WebClient).DownloadFile($Url, $temp)
    } catch {
        if (Test-Path $temp) { Remove-Item $temp -Force }
        throw "Download failed: $Url`n$($_.Exception.Message)"
    }
    Move-Item $temp $Destination -Force
}

function Expand-Once {
    param([string]$Zip, [string]$Destination)

    if (Test-Path $Destination) {
        Write-Note "already extracted: $(Split-Path -Leaf $Destination)"
        return
    }
    Write-Note "extracting $(Split-Path -Leaf $Zip) ..."
    $staging = "$Destination.staging"
    if (Test-Path $staging) { Remove-Item $staging -Recurse -Force }
    Expand-Archive -Path $Zip -DestinationPath $staging -Force
    Move-Item $staging $Destination -Force
}

# ---------------------------------------------------------------------------------------------
Write-Step "Install root"
$Downloads = Join-Path $InstallRoot 'downloads'
New-Item -ItemType Directory -Force -Path $Downloads | Out-Null
Write-Note $InstallRoot

# ---------------------------------------------------------------------------------------------
Write-Step "JDK 17"
# Gradle 8.9 runs on Java 8-22. A newer JDK on PATH fails with a message about class file
# versions that never mentions the real cause, so this pins its own copy regardless of what
# else is installed.
$JdkDir = Join-Path $InstallRoot 'jdk17'
$JdkZip = Join-Path $Downloads 'jdk17.zip'
Get-RemoteFile 'https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jdk/hotspot/normal/eclipse' $JdkZip
Expand-Once $JdkZip $JdkDir

# The archive nests one versioned folder; find the one that actually holds bin\java.exe.
$JavaHome = Get-ChildItem $JdkDir -Directory |
    Where-Object { Test-Path (Join-Path $_.FullName 'bin\java.exe') } |
    Select-Object -First 1 -ExpandProperty FullName
if (-not $JavaHome) {
    if (Test-Path (Join-Path $JdkDir 'bin\java.exe')) { $JavaHome = $JdkDir }
}
if (-not $JavaHome) { throw "Could not find bin\java.exe under $JdkDir" }

$env:JAVA_HOME = $JavaHome
$env:Path = "$JavaHome\bin;$env:Path"

# The version comes from the JDK's own release file rather than `java -version`.
#
# `java -version` writes to stderr, and Windows PowerShell 5.1 wraps a native command's
# redirected stderr in ErrorRecords — so `2>&1` here turns a healthy JDK into a terminating
# NativeCommandError under $ErrorActionPreference = 'Stop'. Never redirect a native exe's
# stderr in this script; check $LASTEXITCODE instead.
$JavaVersion = 'unknown'
$ReleaseFile = Join-Path $JavaHome 'release'
if (Test-Path $ReleaseFile) {
    $VersionLine = @(Get-Content $ReleaseFile) -match '^JAVA_VERSION='
    if ($VersionLine) {
        $JavaVersion = ($VersionLine | Select-Object -First 1) -replace '^JAVA_VERSION=|"', ''
    }
}
Write-Good "JDK $JavaVersion"
Write-Note $JavaHome

# ---------------------------------------------------------------------------------------------
Write-Step "Android SDK command-line tools"
$SdkRoot = Join-Path $InstallRoot 'android-sdk'
$CmdlineParent = Join-Path $SdkRoot 'cmdline-tools'
$CmdlineLatest = Join-Path $CmdlineParent 'latest'

if (-not (Test-Path (Join-Path $CmdlineLatest 'bin\sdkmanager.bat'))) {
    $ToolsZip = Join-Path $Downloads 'cmdline-tools.zip'
    # Google keeps old build numbers reachable, so a known-good one is safer than guessing the
    # newest. sdkmanager updates itself to current a few lines below.
    Get-RemoteFile 'https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip' $ToolsZip

    $Staging = Join-Path $Downloads 'cmdline-staging'
    if (Test-Path $Staging) { Remove-Item $Staging -Recurse -Force }
    Expand-Archive -Path $ToolsZip -DestinationPath $Staging -Force

    # The zip unpacks to a folder called cmdline-tools. sdkmanager refuses to run unless it
    # sits at cmdline-tools\latest\, which is the single most common setup mistake.
    New-Item -ItemType Directory -Force -Path $CmdlineParent | Out-Null
    Move-Item (Join-Path $Staging 'cmdline-tools') $CmdlineLatest -Force
    Remove-Item $Staging -Recurse -Force
}

$SdkManager = Join-Path $CmdlineLatest 'bin\sdkmanager.bat'
if (-not (Test-Path $SdkManager)) { throw "sdkmanager not found at $SdkManager" }

$env:ANDROID_HOME = $SdkRoot
$env:ANDROID_SDK_ROOT = $SdkRoot
Write-Good "sdkmanager ready"

# ---------------------------------------------------------------------------------------------
Write-Step "SDK packages and licences"
# --licenses prompts for each agreement; feeding it a stream of y answers is the standard way
# to run it unattended.
Write-Note "accepting licences ..."
$yes = ("y`r`n" * 40)
$yes | & $SdkManager --sdk_root="$SdkRoot" --licenses | Out-Null

Write-Note "installing platform-tools, platforms;android-35, build-tools;35.0.0 ..."
& $SdkManager --sdk_root="$SdkRoot" 'platform-tools' 'platforms;android-35' 'build-tools;35.0.0'
if ($LASTEXITCODE -ne 0) { throw "sdkmanager failed with exit code $LASTEXITCODE" }
Write-Good "SDK packages installed"

# ---------------------------------------------------------------------------------------------
Write-Step "Gradle 8.9"
# Needed once, only to generate the wrapper. After that gradlew fetches its own copy and this
# folder can be deleted.
$GradleDir = Join-Path $InstallRoot 'gradle'
$GradleZip = Join-Path $Downloads 'gradle-8.9-bin.zip'
Get-RemoteFile 'https://services.gradle.org/distributions/gradle-8.9-bin.zip' $GradleZip
Expand-Once $GradleZip $GradleDir
$GradleBat = Join-Path $GradleDir 'gradle-8.9\bin\gradle.bat'
if (-not (Test-Path $GradleBat)) { throw "gradle.bat not found at $GradleBat" }
Write-Good "gradle 8.9 ready"

# ---------------------------------------------------------------------------------------------
Write-Step "Project wiring"
# AGP reads sdk.dir from here before falling back to the environment. Writing it means the
# build works from any shell, not just one where the variables happen to be set.
$LocalProps = Join-Path $ProjectRoot 'local.properties'
$sdkEscaped = $SdkRoot -replace '\\', '\\'
Set-Content -Path $LocalProps -Value "sdk.dir=$sdkEscaped" -Encoding ascii
Write-Note "wrote local.properties (gitignored)"

Push-Location $ProjectRoot
try {
    if (-not (Test-Path (Join-Path $ProjectRoot 'gradlew.bat'))) {
        Write-Note "generating the Gradle wrapper ..."
        & $GradleBat wrapper --gradle-version 8.9
        if ($LASTEXITCODE -ne 0) { throw "gradle wrapper failed with exit code $LASTEXITCODE" }
    }
    Write-Good "wrapper present"
} finally {
    Pop-Location
}

# ---------------------------------------------------------------------------------------------
Write-Step "Remembering the paths"
# User scope, so no administrator rights and no effect on anything else on the machine.
[Environment]::SetEnvironmentVariable('JAVA_HOME', $JavaHome, 'User')
[Environment]::SetEnvironmentVariable('ANDROID_HOME', $SdkRoot, 'User')
Write-Note "JAVA_HOME    = $JavaHome"
Write-Note "ANDROID_HOME = $SdkRoot"
Write-Note "New terminals pick these up; this one already has them."

# ---------------------------------------------------------------------------------------------
if ($SkipBuild) {
    Write-Step "Done - build skipped"
    Write-Host "`nRun the build yourself with:" -ForegroundColor White
    Write-Host "    cd `"$ProjectRoot`"" -ForegroundColor White
    Write-Host "    .\gradlew.bat assembleDebug" -ForegroundColor White
    return
}

Write-Step "Building a debug APK"
Write-Note "first run downloads the dependencies and takes a while"
Push-Location $ProjectRoot
try {
    & .\gradlew.bat assembleDebug
    $buildCode = $LASTEXITCODE
} finally {
    Pop-Location
}

if ($buildCode -ne 0) {
    Write-Host "`n=== Build failed" -ForegroundColor Yellow
    Write-Host @"
    The toolchain is installed correctly - this is the compile step failing, not the setup.
    None of the recent source changes had ever been compiled, so errors here are expected.
    Send the output above to Claude and it can be fixed.
"@ -ForegroundColor Yellow
    exit $buildCode
}

$Apk = Join-Path $ProjectRoot 'app\build\outputs\apk\debug\app-debug.apk'
Write-Step "Done"
Write-Good $Apk
Write-Host @"

    Copy that file to the phone and install it. It arrives as com.procwatch.debug.

    For a release build, which installs as com.procwatch and is not debuggable:
      1. keytool -genkeypair -v -keystore procwatch-release.jks -alias procwatch ``
             -keyalg RSA -keysize 4096 -validity 10000
      2. copy keystore.properties.example to keystore.properties and fill it in
      3. .\gradlew.bat assembleRelease
"@ -ForegroundColor White
