param(
  [string]$SqlitePath = "",
  [string]$RunDir = "",
  [string]$BackendConfigPath = "",
  [Parameter(Mandatory=$true)][string]$BackupDir,
  [int]$KeepLatest = 10
)

$ErrorActionPreference = "Stop"

function Get-SqlitePathFromConfig {
  param([string]$ConfigPath)

  if (-not $ConfigPath -or -not (Test-Path -LiteralPath $ConfigPath)) {
    return ""
  }

  $content = Get-Content -LiteralPath $ConfigPath -Raw -Encoding UTF8
  $match = [regex]::Match($content, "(?m)^\s*url\s*:\s*jdbc:sqlite:(.+?)\s*$")
  if (-not $match.Success) {
    return ""
  }

  $pathValue = $match.Groups[1].Value.Trim().Trim('"').Trim("'")
  $queryIndex = $pathValue.IndexOf("?")
  if ($queryIndex -ge 0) {
    $pathValue = $pathValue.Substring(0, $queryIndex)
  }

  return $pathValue -replace "/", "\"
}

function Copy-IfExists {
  param(
    [string]$SourcePath,
    [string]$TargetDir
  )

  if (Test-Path -LiteralPath $SourcePath) {
    Copy-Item -LiteralPath $SourcePath -Destination $TargetDir -Force
  }
}

function Resolve-SqlitePath {
  param(
    [string]$PathValue,
    [string]$BaseDir
  )

  $normalizedPath = $PathValue -replace "/", "\"
  if ([System.IO.Path]::IsPathRooted($normalizedPath)) {
    return $normalizedPath
  }

  if (-not $BaseDir) {
    $BaseDir = (Get-Location).Path
  }

  return [System.IO.Path]::GetFullPath((Join-Path $BaseDir $normalizedPath))
}

$resolvedSqlitePath = $SqlitePath
if (-not $resolvedSqlitePath) {
  $resolvedSqlitePath = Get-SqlitePathFromConfig -ConfigPath $BackendConfigPath
}

if (-not $resolvedSqlitePath) {
  throw "SQLite database path is empty. Set sqlitePath or backendConfigPath in electron/config/app-config.json."
}

$resolvedSqlitePath = Resolve-SqlitePath -PathValue $resolvedSqlitePath -BaseDir $RunDir

if (-not (Test-Path -LiteralPath $resolvedSqlitePath)) {
  throw "SQLite database file does not exist: $resolvedSqlitePath"
}

$dbFile = Get-Item -LiteralPath $resolvedSqlitePath
$timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
$backupRoot = New-Item -ItemType Directory -Path $BackupDir -Force
$backupName = "{0}_{1}" -f $dbFile.BaseName, $timestamp
$backupPath = Join-Path $backupRoot.FullName $backupName
New-Item -ItemType Directory -Path $backupPath -Force | Out-Null

Copy-IfExists -SourcePath $dbFile.FullName -TargetDir $backupPath
Copy-IfExists -SourcePath ($dbFile.FullName + "-wal") -TargetDir $backupPath
Copy-IfExists -SourcePath ($dbFile.FullName + "-shm") -TargetDir $backupPath

if ($KeepLatest -gt 0) {
  Get-ChildItem -LiteralPath $backupRoot.FullName -Directory |
    Where-Object { $_.Name -like "$($dbFile.BaseName)_*" } |
    Sort-Object LastWriteTime -Descending |
    Select-Object -Skip $KeepLatest |
    Remove-Item -Recurse -Force
}

Write-Output $backupPath
