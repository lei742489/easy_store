param(
  [string]$Message = "Update project",
  [string]$Remote = "origin",
  [string]$Branch = ""
)

$ErrorActionPreference = "Stop"

function Invoke-Git {
  param(
    [Parameter(Mandatory = $true)]
    [string[]]$Arguments
  )

  & git @Arguments
  if ($LASTEXITCODE -ne 0) {
    throw "Git command failed: git $($Arguments -join ' ')"
  }
}

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location -LiteralPath $root

$currentBranch = (& git branch --show-current).Trim()
if ($LASTEXITCODE -ne 0) {
  throw "Unable to determine the current Git branch."
}

if ([string]::IsNullOrWhiteSpace($currentBranch)) {
  throw "The repository is in detached HEAD state. Check out a branch first."
}

if ([string]::IsNullOrWhiteSpace($Branch)) {
  $Branch = $currentBranch
}

Write-Host "Repository: $root"
Write-Host "Branch:     $Branch"
Write-Host "Remote:     $Remote"
Write-Host "Message:    $Message"

$status = @(git status --porcelain)
if ($LASTEXITCODE -ne 0) {
  throw "Unable to read Git status."
}

if ($status.Count -eq 0) {
  Write-Host "Working tree is clean. Nothing to commit."
  exit 0
}

Write-Host ""
Write-Host "Changes to be committed:"
$status | ForEach-Object { Write-Host "  $_" }

Invoke-Git -Arguments @("add", "-A")

$stagedChanges = @(git diff --cached --name-only)
if ($LASTEXITCODE -ne 0) {
  throw "Unable to inspect staged changes."
}

if ($stagedChanges.Count -eq 0) {
  Write-Host "No staged changes found. Nothing to commit."
  exit 0
}

Invoke-Git -Arguments @("commit", "-m", $Message)
Invoke-Git -Arguments @("push", $Remote, $Branch)

Write-Host ""
Write-Host "Commit and push completed successfully."
