<#
.SYNOPSIS
  Flip one ledger row status in place with backup. Refuses DONE without
  existing report file or with live claim by another agent.
.DESCRIPTION
  B5-0044. Five defects fixed here, each found by red-then-green testing
  rather than by reading:

  1. THE CLAIM GUARD NEVER FIRED. The live-claim lookup was
     `Join-Path (Split-Path -Parent $PSScriptRoot) '.agent/CLAIMS'`, which
     resolves to `.agent/.agent/CLAIMS` - a directory that does not exist.
     Get-ChildItem on a missing path with -ErrorAction SilentlyContinue
     returns nothing, so $live was always empty and the guard was dead code.
     Measured in a sandbox: with a live claim planted by another agent on
     B5-0300, this tool flipped that row to DONE and exited 0.

  2. A REFUSED FLIP STILL LEFT A BACKUP. The backup was taken before the
     refusal checks, so a refused flip still wrote to the RESCUE directory.

  3. THE REPORT CHECK WAS CWD-RELATIVE. `.agent/REPORTS/*$Id*.md` resolved
     against the current directory, not the repository, so a DONE flip was
     refused spuriously whenever the caller was not sitting in the repo root.

  4. THERE WAS NO -DryRun. A caller who passed -DryRun got it silently
     absorbed (the tool had no [CmdletBinding()]), and the flip happened
     anyway. The acceptance case "-DryRun prints the diff and writes nothing"
     could not pass because the switch did not exist. -DryRun now exists,
     prints the diff, and writes nothing at all - no ledger change, no
     backup, no report check side effects.

  Also note: [CmdletBinding()] makes this an advanced function, and
  $PSScriptRoot is EMPTY while param defaults are evaluated, so the path
  defaults are resolved in the body instead of in the param block.

  5. THE CLAIM GUARD OVER-REFUSED. A live claim held by the CALLER blocked
     the flip, so the agent that holds a claim could not use this tool to
     close the row it had claimed. The tool's contract - "refuses ... live
     claim by another agent" - is now enforced literally: a claim whose
     agent_id equals -AgentId is exempt, and any other agent's claim blocks.
     This is a deliberate widening of a guard that, before defect 1, could
     never fire at all.
#>
[CmdletBinding()]
param(
  [string]$Id,
  [string]$NewStatus,
  # The parent of $PSScriptRoot IS `.agent` already, so these join directly onto
  # it. They previously appended `.agent/...` a second time and resolved to
  # `.agent/.agent/TASK_LEDGER.md`, which threw DirectoryNotFound - the same
  # defect class as lint-ledger.ps1 had. See the B5-0038 report.
  [string]$LedgerPath,
  [string]$BackupDir,
  # The agent running this flip. A live claim held by this agent is not a
  # blocker; a live claim held by any other agent is.
  [string]$AgentId,
  # Report what would change; write nothing.
  [switch]$DryRun
)
$ErrorActionPreference = 'Stop'
$agentDir = Split-Path -Parent $PSScriptRoot
if (-not $LedgerPath) { $LedgerPath = Join-Path $agentDir 'TASK_LEDGER.md' }
if (-not $BackupDir)  { $BackupDir  = Join-Path $agentDir 'RESCUE' }
# The repository root is the parent of `.agent`.
$repoRoot = Split-Path -Parent $agentDir
$claimsDir = Join-Path $agentDir 'CLAIMS'
$reportsDir = Join-Path $repoRoot '.agent/REPORTS'

if (-not $Id -match '^B5-[0-9]{4}[a-z]?$') { Write-Error "bad id $Id"; exit 1 }
if ($NewStatus -notin @('OPEN','DONE','CLAIMED','BLOCKED','SUPERSEDED','VOID')) { Write-Error "bad status $NewStatus"; exit 1 }
if (-not (Test-Path -LiteralPath $LedgerPath)) { Write-Error "ledger not found: $LedgerPath"; exit 1 }
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

# --- refusal checks, BEFORE any write of any kind -------------------------
# refuse DONE without a report. Resolved against the REPOSITORY, not the CWD.
if ($NewStatus -eq 'DONE') {
  $report = Get-ChildItem -LiteralPath $reportsDir -Filter "*$Id*.md" -ErrorAction SilentlyContinue
  if (-not $report) {
    Write-Error "refuse DONE: no report file for $Id under $reportsDir"; exit 1
  }
}
# Refuse if a LIVE claim exists for this id. The claims directory is
# $agentDir/CLAIMS - NOT $agentDir/.agent/CLAIMS, which never existed.
#
# A claim held by the CALLER is not a blocker: the agent that holds a claim is
# the agent expected to close the row out, and this tool is how it does that.
# The tool's contract is to refuse a claim by ANOTHER agent, so a claim whose
# agent_id equals -AgentId is exempt. Before B5-0044 the guard matched nothing
# at all (it read a path that does not exist), so this exemption is a
# deliberate widening of a guard that could previously never fire.
$claims = @()
if (Test-Path -LiteralPath $claimsDir) {
  $claims = @(Get-ChildItem -LiteralPath $claimsDir -Filter "$Id*" -ErrorAction SilentlyContinue)
}
$live = @($claims | Where-Object { $_.Name -notmatch '\.RELEASED\.json$' })
$foreign = @()
foreach ($c in $live) {
  $owner = ''
  try {
    $j = [System.IO.File]::ReadAllText($c.FullName) | ConvertFrom-Json
    if ($j.PSObject.Properties.Name -contains 'agent_id') { $owner = [string]$j.agent_id }
  } catch {
    # An unreadable claim file is treated as foreign, not as absent. A claim
    # that cannot be read is a claim that cannot be reasoned about, and
    # guessing "mine" would be the unsafe direction.
    $owner = '<unreadable>'
  }
  if ($owner -ne $AgentId) { $foreign += $c }
}
if ($foreign.Count -gt 0) {
  Write-Error "refuse: live claim by another agent for $Id ($($foreign.Name -join ', '))"; exit 1
}

# --- compute the new line -------------------------------------------------
# replace status cell (assume 2nd cell is status after split by |)
$parts = $oldLine -split '\|'
# parts[0] = empty; [1]=id; [2]=status; ...
$parts[2] = " $NewStatus "
$newLine = ($parts -join '|')
# verify 7 pipes
$after = ([regex]::Matches($newLine,'\|')).Count
if ($before -ne 7 -or $after -ne 7) { Write-Error "pipe count changed: before=$before after=$after"; exit 1 }

if ($DryRun) {
  # Print the diff and write NOTHING: no ledger change, no backup, no report.
  Write-Output "DRY RUN: $Id -> $NewStatus (no files written)"
  Write-Output ("  - {0}" -f $oldLine)
  Write-Output ("  + {0}" -f $newLine)
  exit 0
}

# --- now write ------------------------------------------------------------
if (-not (Test-Path $BackupDir)) { New-Item -ItemType Directory -Path $BackupDir | Out-Null }
$ts = (Get-Date -Format "yyyyMMddHHmmss")
Copy-Item $LedgerPath "$BackupDir\TASK_LEDGER.$ts.bak"
$lines[$lineIdx] = $newLine
$newContent = $lines -join "`r`n"
[System.IO.File]::WriteAllText($LedgerPath, $newContent, (New-Object System.Text.UTF8Encoding($false)))
Write-Output "flipped $Id -> $NewStatus (backup $BackupDir)"
