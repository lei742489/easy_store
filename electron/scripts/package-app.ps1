param(
  [string]$MavenHome = "E:\java\apache-maven-3.3.9-bin",
  [string]$OutputDir = "",
  [switch]$SkipBuild,
  [switch]$Network
)

$ErrorActionPreference = "Stop"

function Resolve-FullPath {
  param([string]$PathValue)
  return [System.IO.Path]::GetFullPath($PathValue)
}

function Assert-InsideRoot {
  param(
    [string]$RootPath,
    [string]$TargetPath
  )

  $root = Resolve-FullPath $RootPath
  $target = Resolve-FullPath $TargetPath
  if (-not $target.StartsWith($root, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "Refuse to operate outside repository root: $target"
  }
}

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ElectronDir = Resolve-FullPath (Join-Path $ScriptDir "..")
$RepoRoot = Resolve-FullPath (Join-Path $ElectronDir "..")
$BackendDir = Join-Path $RepoRoot "easy_store_boot"
$FrontendDir = Join-Path $RepoRoot "easy_store_vue"
$FrontendPackageSource = Join-Path $ElectronDir "web"
$ReleaseRoot = Join-Path $RepoRoot "release"
if (-not $OutputDir) {
  $OutputDir = Join-Path $ReleaseRoot "easy-store"
}
$OutputDir = Resolve-FullPath $OutputDir

Assert-InsideRoot -RootPath $RepoRoot -TargetPath $OutputDir

$AsarCmd = Join-Path $ElectronDir "node_modules\.bin\asar.cmd"
if (-not (Test-Path -LiteralPath $AsarCmd)) {
  throw "asar command not found: $AsarCmd"
}

if (-not $SkipBuild) {
  if (-not $Network) {
    $MavenCmd = Join-Path $MavenHome "bin\mvn.cmd"
    if (-not (Test-Path -LiteralPath $MavenCmd)) {
      throw "Maven not found: $MavenCmd"
    }

    Push-Location $BackendDir
    try {
      & $MavenCmd "-DskipTests" "-Dmaven.compiler.useIncrementalCompilation=false" "clean" "package"
      if ($LASTEXITCODE -ne 0) {
        throw "Backend package failed."
      }
    } finally {
      Pop-Location
    }
  }

  Push-Location $FrontendDir
    try {
      & "npx.cmd" "vite" "build" "--config" ".\config\vite.config.prod.ts"
      if ($LASTEXITCODE -ne 0) {
        throw "Frontend build failed."
      }
      Copy-Item -Path (Join-Path $FrontendDir "dist\*") -Destination $FrontendPackageSource -Recurse -Force
    } finally {
      Pop-Location
    }
}

$ElectronDist = Join-Path $ElectronDir "node_modules\electron\dist"
$BackendJarSource = Join-Path $BackendDir "target\easy_store_boot-0.0.1-SNAPSHOT.jar"
$BackendConfigSource = Join-Path $BackendDir "src\main\resources\application-online.yml"

 $requiredSources = @($ElectronDist, $FrontendPackageSource)
if (-not $Network) {
  $requiredSources += @($BackendJarSource, $BackendConfigSource)
}
foreach ($pathToCheck in $requiredSources) {
  if (-not (Test-Path -LiteralPath $pathToCheck)) {
    throw "Required package source not found: $pathToCheck"
  }
}

if (Test-Path -LiteralPath $OutputDir) {
  Assert-InsideRoot -RootPath $RepoRoot -TargetPath $OutputDir
  Remove-Item -LiteralPath $OutputDir -Recurse -Force
}

New-Item -ItemType Directory -Path $OutputDir -Force | Out-Null
Copy-Item -Path (Join-Path $ElectronDist "*") -Destination $OutputDir -Recurse -Force
Rename-Item -LiteralPath (Join-Path $OutputDir "electron.exe") -NewName "Easy Store.exe"
$DefaultAppAsar = Join-Path $OutputDir "resources\default_app.asar"
if (Test-Path -LiteralPath $DefaultAppAsar) {
  Remove-Item -LiteralPath $DefaultAppAsar -Force
}

$AppDir = Join-Path $OutputDir "resources\app"
$WebDir = Join-Path $OutputDir "web"
$ConfigDir = Join-Path $OutputDir "config"
$BackupDir = Join-Path $OutputDir "backup\sqlite"

New-Item -ItemType Directory -Path $AppDir, $WebDir, $ConfigDir -Force | Out-Null
if (-not $Network) {
  $BootDir = Join-Path $OutputDir "boot"
  New-Item -ItemType Directory -Path $BootDir, $BackupDir -Force | Out-Null
}

Copy-Item -LiteralPath (Join-Path $ElectronDir "main.js") -Destination $AppDir -Force
Copy-Item -LiteralPath (Join-Path $ElectronDir "preload.js") -Destination $AppDir -Force
Copy-Item -LiteralPath (Join-Path $ElectronDir "package.json") -Destination $AppDir -Force
Copy-Item -LiteralPath (Join-Path $ElectronDir "assets") -Destination $AppDir -Recurse -Force
Copy-Item -LiteralPath (Join-Path $ElectronDir "ipcHandlers") -Destination $AppDir -Recurse -Force
Copy-Item -LiteralPath (Join-Path $ElectronDir "scripts") -Destination $AppDir -Recurse -Force

$AppNodeModules = Join-Path $AppDir "node_modules"
New-Item -ItemType Directory -Path $AppNodeModules -Force | Out-Null
Get-ChildItem -LiteralPath (Join-Path $ElectronDir "node_modules") -Directory |
  Where-Object { $_.Name -notin @(".bin", "electron") } |
  ForEach-Object {
    Copy-Item -LiteralPath $_.FullName -Destination $AppNodeModules -Recurse -Force
  }

& $AsarCmd "pack" $AppDir (Join-Path $OutputDir "resources\app.asar")
if ($LASTEXITCODE -ne 0) {
  throw "Electron app asar package failed."
}

if (-not $Network) {
  Copy-Item -LiteralPath $BackendJarSource -Destination (Join-Path $BootDir "easy_store_boot.jar") -Force
  Copy-Item -LiteralPath $BackendConfigSource -Destination (Join-Path $BootDir "application-online.yml") -Force
}

Copy-Item -Path (Join-Path $FrontendPackageSource "*") -Destination $WebDir -Recurse -Force

if ($Network) {
@'
{
  "appMode": "network",
  "backupOnExit": false,
  "webUrl": "http://es.njhy6920.cn/",
  "webVersionUrl": "http://es.njhy6920.cn/version.json",
  "webDir": "web"
}
'@ | Set-Content -LiteralPath (Join-Path $ConfigDir "app-config.json") -Encoding UTF8
} else {
@'
{
  "appMode": "single",
  "backupOnExit": true,
  "sqlitePath": "",
  "backendRunDir": "boot",
  "backendConfigPath": "boot/application-online.yml",
  "backendJarName": "easy_store_boot.jar",
  "backendStartupTimeout": 30000,
  "webDir": "web",
  "backupDir": "backup/sqlite",
  "backupKeepLatest": 10
}
'@ | Set-Content -LiteralPath (Join-Path $ConfigDir "app-config.json") -Encoding UTF8
}

Write-Output "Package created: $OutputDir"
