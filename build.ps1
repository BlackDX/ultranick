# ==============================================================================
# UltraNick Automated Multi-Module Build Script
# Author: Chatbxn
# ==============================================================================

$ErrorActionPreference = "Stop"
$root = $PSScriptRoot
if (-not $root) { $root = (Get-Location).Path }

Write-Host "========================================================" -ForegroundColor Cyan
Write-Host "  Building UltraNick Multi-Module Project (Chatbxn)" -ForegroundColor Cyan
Write-Host "========================================================" -ForegroundColor Cyan

# Directories
$apiDir = Join-Path $root "ultranick-api"
$commonDir = Join-Path $root "ultranick-common"
$velocityDir = Join-Path $root "ultranick-velocity"
$paperDir = Join-Path $root "ultranick-paper"
$distDir = Join-Path $root "dist"

if (Test-Path $distDir) { Remove-Item -Recurse -Force $distDir }
New-Item -ItemType Directory -Path $distDir -Force | Out-Null

$m2 = "C:\Users\gamer\.m2\repository"
$utf8NoBom = [System.Text.UTF8Encoding]::new($false)

function Compile-Module {
    param (
        [string]$ModuleName,
        [string]$ModuleDir,
        [string[]]$ClasspathJars,
        [string[]]$ExtraSourceDirs
    )

    Write-Host "`n[Compiling $ModuleName]..." -ForegroundColor Yellow
    $srcDir = Join-Path $ModuleDir "src\main\java"
    $classesDir = Join-Path $ModuleDir "target\classes"

    if (Test-Path $classesDir) { Remove-Item -Recurse -Force $classesDir }
    New-Item -ItemType Directory -Path $classesDir -Force | Out-Null

    $sources = @()
    if (Test-Path $srcDir) {
        $sources += (Get-ChildItem -Path $srcDir -Recurse -Filter "*.java" | Select-Object -ExpandProperty FullName)
    }
    if ($ExtraSourceDirs) {
        foreach ($ed in $ExtraSourceDirs) {
            if (Test-Path $ed) {
                $sources += (Get-ChildItem -Path $ed -Recurse -Filter "*.java" | Select-Object -ExpandProperty FullName)
            }
        }
    }

    if ($sources.Count -eq 0) {
        Write-Host "No sources found for $ModuleName" -ForegroundColor Red
        return
    }

    $argFile = [System.IO.Path]::GetTempFileName()
    $cpString = ($ClasspathJars -join ";")

    $argsList = @(
        "--release", "21",
        "-encoding", "UTF-8",
        "-d", "`"$($classesDir.Replace('\', '/'))`""
    )

    if ($cpString) {
        $argsList += "-cp"
        $argsList += "`"$cpString`""
    }

    foreach ($src in $sources) {
        $argsList += "`"$($src.Replace('\', '/'))`""
    }

    [System.IO.File]::WriteAllLines($argFile, $argsList, $utf8NoBom)

    & javac "@$argFile"
    $res = $LASTEXITCODE
    Remove-Item -Force $argFile -ErrorAction SilentlyContinue

    if ($res -ne 0) {
        throw "Compilation failed for $ModuleName with exit code $res"
    }

    Write-Host "Compiled $($sources.Count) Java classes for $ModuleName successfully." -ForegroundColor Green
}

# 1. Locate Core Dependencies from .m2
$allM2Jars = (Get-ChildItem -Path $m2 -Recurse -Filter "*.jar" | Select-Object -ExpandProperty FullName) | ForEach-Object { $_.Replace("\", "/") }
$velApi = ($allM2Jars | Where-Object { $_ -like "*velocity-api-3.4.0-SNAPSHOT.jar*" } | Select-Object -First 1)
$paperApi = ($allM2Jars | Where-Object { $_ -like "*paper-api-1.21.4-R0.1-SNAPSHOT.jar*" } | Select-Object -First 1)

Write-Host "Found Velocity API: $velApi" -ForegroundColor Gray
Write-Host "Found Paper API: $paperApi" -ForegroundColor Gray

# 2. Compile API
Compile-Module -ModuleName "ultranick-api" -ModuleDir $apiDir -ClasspathJars $allM2Jars

# 3. Compile Common
$commonCp = @((Join-Path $apiDir "target\classes").Replace("\", "/")) + $allM2Jars
Compile-Module -ModuleName "ultranick-common" -ModuleDir $commonDir -ClasspathJars $commonCp

# 4. Compile Velocity
$velCp = @(
    (Join-Path $apiDir "target\classes").Replace("\", "/"),
    (Join-Path $commonDir "target\classes").Replace("\", "/")
) + $allM2Jars
Compile-Module -ModuleName "ultranick-velocity" -ModuleDir $velocityDir -ClasspathJars $velCp

# 5. Compile Paper
$paperCp = @(
    (Join-Path $apiDir "target\classes").Replace("\", "/"),
    (Join-Path $commonDir "target\classes").Replace("\", "/")
) + $allM2Jars
Compile-Module -ModuleName "ultranick-paper" -ModuleDir $paperDir -ClasspathJars $paperCp

# ==============================================================================
# Packaging JARs
# ==============================================================================
Write-Host "`n[Packaging Plugins into JARs]..." -ForegroundColor Yellow

# A) Package Velocity Plugin
$velStaging = Join-Path $velocityDir "target\staging"
if (Test-Path $velStaging) { Remove-Item -Recurse -Force $velStaging }
New-Item -ItemType Directory -Path $velStaging -Force | Out-Null

# Copy classes
Copy-Item -Recurse -Force (Join-Path $apiDir "target\classes\*") $velStaging
Copy-Item -Recurse -Force (Join-Path $commonDir "target\classes\*") $velStaging
Copy-Item -Recurse -Force (Join-Path $velocityDir "target\classes\*") $velStaging

# Copy velocity-plugin.json to root
$velPluginJson = Join-Path $velocityDir "src\main\resources\velocity-plugin.json"
Copy-Item -Force $velPluginJson (Join-Path $velStaging "velocity-plugin.json")

$velOutJar = Join-Path $distDir "ultranick-velocity-1.0.0-SNAPSHOT.jar"
& jar --create --file $velOutJar -C $velStaging .
Write-Host "Created Velocity Artifact: $velOutJar" -ForegroundColor Green

# B) Package Paper Plugin
$paperStaging = Join-Path $paperDir "target\staging"
if (Test-Path $paperStaging) { Remove-Item -Recurse -Force $paperStaging }
New-Item -ItemType Directory -Path $paperStaging -Force | Out-Null

# Copy classes
Copy-Item -Recurse -Force (Join-Path $apiDir "target\classes\*") $paperStaging
Copy-Item -Recurse -Force (Join-Path $commonDir "target\classes\*") $paperStaging
Copy-Item -Recurse -Force (Join-Path $paperDir "target\classes\*") $paperStaging

# Copy plugin.yml and config.yml
Copy-Item -Force (Join-Path $paperDir "src\main\resources\plugin.yml") (Join-Path $paperStaging "plugin.yml")
Copy-Item -Force (Join-Path $paperDir "src\main\resources\config.yml") (Join-Path $paperStaging "config.yml")

$paperOutJar = Join-Path $distDir "ultranick-paper-1.0.0-SNAPSHOT.jar"
& jar --create --file $paperOutJar -C $paperStaging .
Write-Host "Created Paper Artifact: $paperOutJar" -ForegroundColor Green

Write-Host "`n========================================================" -ForegroundColor Cyan
Write-Host "  UltraNick Multi-Module Build Completed Successfully!" -ForegroundColor Green
Write-Host "========================================================" -ForegroundColor Cyan
Get-ChildItem -Path $distDir | Format-Table Name, Length, LastWriteTime
