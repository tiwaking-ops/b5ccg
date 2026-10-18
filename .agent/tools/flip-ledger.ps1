<#
.SYNOPSIS
  Flip one ledger row status in place with backup. Refuses DONE without
  existing report file or with live claim by another agent.
#>
param(
  [string]$Id,
  [string]$NewStatus,
  # The parent of $PSScriptRoot IS `.agent` already, so these join directly onto
  # it. They previously appended `.agent/...` a second time and resolved to
  # `.agent/.agent/TASK_LEDGER.md`, which threw DirectoryNotFound - the same
  # defect class as lint-ledger.ps1 had. See the B5-0038 report.
  [string]$LedgerPath = (Join-Path (Split-Path -Parent $PSScriptRoot) 'TASK_LEDGER.md'),
  [string]$BackupDir  = (Join-Path (Split-Path -Parent $PSScriptRoot) 'RESCUE')
)
$ErrorActionPreference = 'Stop'
if (-not $Id -match '^B5-[0-9]{4}[a-z]?$') { Write-Error "bad id $Id"; exit 1 }
if ($NewStatus -notin @('OPEN','DONE','CLAIMED','BLOCKED','SUPERSEDED','VOID')) { Write-Error "bad status $NewStatus"; exit 1 }
$content = [System.IO.File]::ReadAllText($LedgerPath)
# find line containing id in table (leading pipe, id, then pipes)
$lines = $content -split "`r?`n"
$lineIdx = -1
for ($i=0; $i -lt $lines.Count; $i++) {
  if ($lines[$i] -match "^\|\s*" + [regex]::Escape($Id) + "\s*\|") {
    $lineIdx = $i; break
  }
}
if ($lineIdx -lt 0) { Write-Error "id $Id not in ledger"; exit 1 }
$oldLine = $lines[$lineIdx]
# exactly 7 pipes before and after
$before = ([regex]::Matches($oldLine,'\|')).Count
# backup
if (-not (Test-Path $BackupDir)) { New-Item -ItemType Directory -Path $BackupDir | Out-Null }
$ts = (Get-Date -Format "yyyyMMddHHmmss")
Copy-Item $LedgerPath "$BackupDir\TASK_LEDGER.$ts.bak"
# refuse DONE without report
if ($NewStatus -eq 'DONE') {
  $reportPattern = ".agent/REPORTS/*$Id*.md"
  if (-not (Get-ChildItem $reportPattern -ErrorAction SilentlyContinue)) {
    Write-Error "refuse DONE: no report file for $Id"; exit 1
  }
}
# refuse if live claim by another agent (claim file without .RELEASED for id)
$claims = Get-ChildItem (Join-Path (Split-Path -Parent $PSScriptRoot) '.agent/CLAIMS') -Filter "$Id*" -ErrorAction SilentlyContinue
$live = $claims | Where-Object { $_.Name -notmatch '\.RELEASED\.json$' }
if ($live) {
  Write-Error "refuse: live claim by another agent for $Id ($($live.Name))"; exit 1
}
# replace status cell (assume 2nd cell is status after split by |)
$parts = $oldLine -split '\|'
# rebuild with new status at index 2 (0=empty before first pipe)
# But simpler: replace the status token in line
$newLine = $oldLine -replace ("\|\s*\w+\s*\|"), ("| $NewStatus |")
# Actually safer: split and rebuild
$parts = $oldLine -split '\|'
# parts[0] = empty; [1]=id; [2]=status; ...
$parts[2] = " $NewStatus "
$newLine = ($parts -join '|')
# verify 7 pipes
$after = ([regex]::Matches($newLine,'\|')).Count
if ($before -ne 7 -or $after -ne 7) { Write-Error "pipe count changed: before=$before after=$after"; exit 1 }
$lines[$lineIdx] = $newLine
$newContent = $lines -join "`r`n"
[System.IO.File]::WriteAllText($LedgerPath, $newContent, (New-Object System.Text.UTF8Encoding($false)))
Write-Output "flipped $Id -> $NewStatus (backup $BackupDir)"
