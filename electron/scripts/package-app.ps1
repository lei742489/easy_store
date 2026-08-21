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

function Convert-PngToIco {
  param(
    [string]$PngPath,
    [string]$IcoPath
  )

  Add-Type -AssemblyName System.Drawing

  $source = [System.Drawing.Image]::FromFile($PngPath)
  try {
    $bitmap = New-Object System.Drawing.Bitmap 256, 256
    $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
    try {
      $graphics.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality
      $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
      $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
      $graphics.Clear([System.Drawing.Color]::Transparent)
      $graphics.DrawImage($source, 0, 0, 256, 256)

      $pngStream = New-Object System.IO.MemoryStream
      try {
        $bitmap.Save($pngStream, [System.Drawing.Imaging.ImageFormat]::Png)
        $pngBytes = $pngStream.ToArray()
      } finally {
        $pngStream.Dispose()
      }
    } finally {
      $graphics.Dispose()
      $bitmap.Dispose()
    }
  } finally {
    $source.Dispose()
  }

  $outputDir = Split-Path -Parent $IcoPath
  if ($outputDir) {
    New-Item -ItemType Directory -Path $outputDir -Force | Out-Null
  }

  $stream = [System.IO.File]::Create($IcoPath)
  $writer = New-Object System.IO.BinaryWriter $stream
  try {
    $writer.Write([UInt16]0)
    $writer.Write([UInt16]1)
    $writer.Write([UInt16]1)
    $writer.Write([Byte]0)
    $writer.Write([Byte]0)
    $writer.Write([Byte]0)
    $writer.Write([Byte]0)
    $writer.Write([UInt16]1)
    $writer.Write([UInt16]32)
    $writer.Write([UInt32]$pngBytes.Length)
    $writer.Write([UInt32]22)
    $writer.Write($pngBytes)
  } finally {
    $writer.Dispose()
    $stream.Dispose()
  }
}

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ElectronDir = Resolve-FullPath (Join-Path $ScriptDir "..")
$RepoRoot = Resolve-FullPath (Join-Path $ElectronDir "..")
$BackendDir = Join-Path $RepoRoot "easy_store_boot"
$FrontendDir = Join-Path $RepoRoot "easy_store_vue"
$FrontendPackageSource = Join-Path $ElectronDir "web"
$FrontendIconSource = Join-Path $FrontendDir "src\assets\icon.png"
$ElectronIconTarget = Join-Path $ElectronDir "assets\icon.png"
$ElectronIconIco = Join-Path $ElectronDir "assets\icon.ico"
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

if (Test-Path -LiteralPath $FrontendIconSource) {
  New-Item -ItemType Directory -Path (Split-Path -Parent $ElectronIconTarget) -Force | Out-Null
  Copy-Item -LiteralPath $FrontendIconSource -Destination $ElectronIconTarget -Force
  Convert-PngToIco -PngPath $FrontendIconSource -IcoPath $ElectronIconIco
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
$OutputExe = Join-Path $OutputDir "Easy Store.exe"
Rename-Item -LiteralPath (Join-Path $OutputDir "electron.exe") -NewName "Easy Store.exe"
$RceditCmd = Join-Path $ElectronDir "node_modules\rcedit\bin\rcedit-x64.exe"
if (Test-Path -LiteralPath $RceditCmd) {
  & $RceditCmd $OutputExe "--set-icon" $ElectronIconIco
  if ($LASTEXITCODE -ne 0) {
    throw "Set exe icon failed."
  }
}
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
