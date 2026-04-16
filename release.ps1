#
# release.ps1 - Bump version, build signed AAB, upload to Play Store internal testing
#
# Usage:
#   .\release.ps1                    # Auto-bump versionCode, keep versionName
#   .\release.ps1 -VersionName 1.1.0 # Auto-bump versionCode, set versionName
#   .\release.ps1 -DryRun            # Bump + build only, skip upload
#
param(
    [string]$VersionName = "",
    [switch]$DryRun
)

$ErrorActionPreference = "Stop"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$BuildGradle = Join-Path $ScriptDir "app\build.gradle.kts"

# ── Find JDK 17+ ─────────────────────────────────────────────────────

if (-not $env:JAVA_HOME -or -not (Test-Path "$env:JAVA_HOME\bin\java.exe")) {
    $candidates = @(
        "$env:USERPROFILE\.gradle\jdks\jetbrains_s_r_o_-21-amd64-windows.2"
        "C:\Program Files\Java\jdk-21"
        "C:\Program Files\Java\jdk-17"
        "C:\Program Files\Microsoft\jdk-21*"
        "C:\Program Files\Microsoft\jdk-17*"
    )

    $found = $false
    foreach ($candidate in $candidates) {
        # Resolve wildcards
        $resolved = Resolve-Path $candidate -ErrorAction SilentlyContinue
        if ($resolved) {
            foreach ($r in $resolved) {
                if (Test-Path "$($r.Path)\bin\java.exe") {
                    $env:JAVA_HOME = $r.Path
                    $found = $true
                    break
                }
            }
        }
        if ($found) { break }
    }

    if (-not $found) {
        Write-Host "ERROR: No JDK 17+ found. Install a JDK or set JAVA_HOME." -ForegroundColor Red
        exit 1
    }
}

Write-Host "Using JDK: $env:JAVA_HOME" -ForegroundColor Green

# ── Header ────────────────────────────────────────────────────────────

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Carlink Native - Release Pipeline" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# ── Step 1: Read current version ──────────────────────────────────────

$content = Get-Content $BuildGradle -Raw

if ($content -match 'versionCode = (\d+)') {
    $currentCode = [int]$Matches[1]
} else {
    Write-Host "ERROR: Could not parse versionCode from build.gradle.kts" -ForegroundColor Red
    exit 1
}

if ($content -match 'versionName = "([^"]+)"') {
    $currentName = $Matches[1]
} else {
    Write-Host "ERROR: Could not parse versionName from build.gradle.kts" -ForegroundColor Red
    exit 1
}

Write-Host "Current version: $currentName (code $currentCode)" -ForegroundColor Yellow

# ── Step 2: Bump version ─────────────────────────────────────────────

$newCode = $currentCode + 1
$finalName = if ($VersionName) { $VersionName } else { $currentName }

Write-Host "New version:     $finalName (code $newCode)" -ForegroundColor Green
Write-Host ""

# Replace versionCode
$content = $content -replace "versionCode = $currentCode", "versionCode = $newCode"

# Replace versionName if requested
if ($VersionName) {
    $content = $content -replace "versionName = `"$currentName`"", "versionName = `"$VersionName`""
}

Set-Content -Path $BuildGradle -Value $content -NoNewline

# Verify
$verify = Get-Content $BuildGradle -Raw
if ($verify -match 'versionCode = (\d+)' -and [int]$Matches[1] -eq $newCode) {
    Write-Host "OK Version bumped in build.gradle.kts" -ForegroundColor Green
} else {
    Write-Host "ERROR: versionCode bump failed" -ForegroundColor Red
    exit 1
}

# ── Step 3: Build signed release AAB ──────────────────────────────────

Write-Host ""
Write-Host "Building signed release AAB..." -ForegroundColor Cyan
Write-Host ""

Push-Location $ScriptDir
try {
    $ErrorActionPreference = "Continue"
    & .\gradlew.bat clean bundleRelease
    $ErrorActionPreference = "Stop"
    if ($LASTEXITCODE -ne 0) {
        Write-Host "ERROR: Gradle build failed (exit code $LASTEXITCODE)" -ForegroundColor Red
        exit 1
    }
} finally {
    Pop-Location
}

$aabPath = Join-Path $ScriptDir "app\build\outputs\bundle\release\app-release.aab"

if (-not (Test-Path $aabPath)) {
    Write-Host "ERROR: AAB not found at $aabPath" -ForegroundColor Red
    exit 1
}

$aabSize = (Get-Item $aabPath).Length / 1MB
Write-Host ""
Write-Host ("OK AAB built: {0} ({1:N1} MB)" -f $aabPath, $aabSize) -ForegroundColor Green

# ── Step 4: Upload to Play Store ──────────────────────────────────────

if ($DryRun) {
    Write-Host ""
    Write-Host "DRY RUN - skipping Play Store upload" -ForegroundColor Yellow
    Write-Host "To upload manually: .\gradlew.bat publishReleaseBundle" -ForegroundColor Cyan
} else {
    Write-Host ""
    Write-Host "Uploading to Play Store (internal testing track)..." -ForegroundColor Cyan
    Write-Host ""

    Push-Location $ScriptDir
    try {
        $ErrorActionPreference = "Continue"
        & .\gradlew.bat publishReleaseBundle
        $ErrorActionPreference = "Stop"
        if ($LASTEXITCODE -ne 0) {
            Write-Host "ERROR: Play Store upload failed (exit code $LASTEXITCODE)" -ForegroundColor Red
            exit 1
        }
    } finally {
        Pop-Location
    }

    Write-Host ""
    Write-Host "OK Uploaded to Play Store internal testing" -ForegroundColor Green
}

# ── Summary ───────────────────────────────────────────────────────────

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Release complete!" -ForegroundColor Green
Write-Host "  Version: $finalName ($newCode)"
if ($DryRun) {
    Write-Host "  Upload skipped (dry run)" -ForegroundColor Yellow
} else {
    Write-Host "  Track: internal testing"
}
Write-Host "========================================" -ForegroundColor Cyan
