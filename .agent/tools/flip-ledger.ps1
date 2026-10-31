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
#
# TWO limits of this guard, both measured and both recorded rather than papered
# over:
#   1. The claim files carry no signature, so -AgentId is a CLAIM about
#      identity, not a proof of it. An agent that names another agent's id
#      passes the guard. The claim-file format has no field that could make
#      this stronger, so the limit is inherent to the format and is stated
#      here instead of being left for a reader to discover.
#   2. A claim file with no readable agent_id is treated as FOREIGN, not as
#      the caller's. "Unreadable" and "malformed" both mean the owner is
#      unknown, and an unknown owner must block, because guessing "mine" is
#      the unsafe direction.
#
# The claim FILENAME is matched exactly, not as a prefix. A prefix match would
# let a live claim on the sibling id B5-0202c block a flip of B5-0202, and let
# a flip of B5-0202 pass while B5-0202c was claimed.
$claims = @()
if (Test-Path -LiteralPath $claimsDir) {
  $claims = @(Get-ChildItem -LiteralPath $claimsDir -Filter "$Id*.json" -ErrorAction SilentlyContinue |
              Where-Object { $_.BaseName -eq $Id -or $_.BaseName -eq ($Id + '.RELEASED') })
}
$live = @($claims | Where-Object { $_.Name -notmatch '\.RELEASED\.json$' })
$foreign = @()
foreach ($c in $live) {
  $owner = $null
  try {
    $j = [System.IO.File]::ReadAllText($c.FullName) | ConvertFrom-Json
    if ($j.PSObject.Properties.Name -contains 'agent_id' -and $j.agent_id) {
      $owner = [string]$j.agent_id
    }
  } catch {
    $owner = $null
  }
  # No readable owner -> foreign. Unknown owner blocks.
  if ($null -eq $owner -or $owner -ne $AgentId) { $foreign += $c }
}
if ($foreign.Count -gt 0) {
  Write-Error "refuse: live claim by another agent for $Id ($($foreign.Name -join ', '))"; exit 1
}

# B5-0048: scope-overlap guard - refuse flips when ANOTHER LIVE claim holds any of
# THIS ROW's scopes.
# A claim held by the CALLER is not a blocker: the agent that holds a claim is the agent expected to close the row out.
#
# B5-0048 CORRECTION 2026-10-25. This guard refused EVERY flip in the repository,
# so no row could be closed DONE or BLOCKED by any sanctioned path. Three defects,
# each measured before the fix:
#   1. It counted `.RELEASED.json` archives as live claims. A released claim is a
#      record of a release, not a holder; ~20 archives carry
#      engine/tooling/docs/resources, so the guard fired on every row.
#   2. It never consulted the row's own scopes. flip-ledger.ps1 takes no -Scope
#      parameter, so the guard compared a HARDCODED scope list against every other
#      claim and fired whenever ANY other claim held ANY shared-files scope -
#      including a claim on an unrelated row. The row's scopes are read from the
#      ledger row's own Scope cell, which this tool already parses.
#   3. It counted ORPHAN claims (a claim whose task row is not OPEN/CLAIMED). An
#      orphan is not a live claim: no runner will ever offer it, and AGENTS.md
#      section 5's rule is about a LIVE claim holding a scope.
# The guard now reads this row's Scope cell and compares it against LIVE claims only.
$scopeConflicts = @()
if (Test-Path -LiteralPath $claimsDir) {
    # This row's own scopes, from the ledger row's Scope cell (cell index 4 of the
    # pipe split: '', id, status, task, scope, claim, report). The cell is free text
    # in places ("tooling (a: engine, freeze)"), so the known scope vocabulary is
    # matched rather than the cell split naively - a naive split would read
    # "engine" out of a parenthetical and invent a conflict.
    $scopeCell = ''
    $scopeCells = @($oldLine -split '\|')
    if ($scopeCells.Count -ge 5) { $scopeCell = [string]$scopeCells[4] }
    $rowScopes = @([regex]::Matches($scopeCell, '(engine|tooling|resources|docs|ai|ui|model|card-text/b[0-9]+)') |
                   ForEach-Object { $_.Value } | Select-Object -Unique)

    # Task id -> status, so a claim can be classified live or orphan.
    $ledgerStatus = @{}
    foreach ($ln in $lines) {
        if ($ln -match '^\|+\s*(B5-[0-9]{4}[a-z]?)\s*\|([^|]*)') {
            $ledgerStatus[$matches[1]] = $matches[2].Trim()
        }
    }

    $existingClaims = @(Get-ChildItem -LiteralPath $claimsDir -Filter "*.json" -ErrorAction SilentlyContinue |
                      Where-Object { $_.Name -ne ($Id + '.json') -and $_.Name -notmatch '\.RELEASED\.json$' })
    foreach ($claimFile in $existingClaims) {
        try {
            $claim = [System.IO.File]::ReadAllText($claimFile.FullName) | ConvertFrom-Json
            # Only check conflicts with claims by OTHER agents, not the current agent
            if (-not $claim.scope -or $claim.agent_id -eq $AgentId) { continue }
            # Only a LIVE claim holds a scope: its task row must be OPEN or CLAIMED.
            $taskStatus = ''
            if ($ledgerStatus.ContainsKey([string]$claim.task)) { $taskStatus = $ledgerStatus[[string]$claim.task] }
            if ($taskStatus -ne 'OPEN' -and $taskStatus -ne 'CLAIMED') { continue }
            foreach ($existingScope in $claim.scope) {
                # Overlap is against THIS ROW's scopes, not a hardcoded list.
                if ($rowScopes -contains $existingScope) {
                    $scopeConflicts += @{
                        ClaimFile = $claimFile.Name
                        ConflictingAgent = $claim.agent_id
                        ConflictingScope = $existingScope
                        ConflictingTask = $claim.task
                    }
                }
            }
        } catch {
            # If we can't read the claim file, treat it as a potential conflict to be safe
            $scopeConflicts += @{
                ClaimFile = $claimFile.Name
                ConflictingAgent = "<unreadable>"
                ConflictingScope = "<unreadable>"
                ConflictingTask = "<unreadable>"
            }
        }
    }
}

if ($scopeConflicts.Count -gt 0) {
    # B5-0048 acceptance: "the refusal names the conflicting claim". The fields were
    # referenced as bare variables inside the ForEach-Object script block, where $_
    # is the hashtable and the bare names are $null - so every refusal printed
    # "     by  (scope: )". Reference them through $_ and join on a real newline:
    # "\n" is a LITERAL backslash-n in PowerShell (the escape is `n), which is why
    # the old message printed one long line of "\n" separators.
    $conflictList = @($scopeConflicts | ForEach-Object {
        "    $($_.ConflictingTask) by $($_.ConflictingAgent) (scope: $($_.ConflictingScope))"
    }) -join [Environment]::Newline
    Write-Error ("refuse: scope overlap detected for task $Id. The following live claims hold conflicting scopes:" + [Environment]::Newline + $conflictList)
    exit 1
}

# B5-0048: freeze guard for flips. A flip to OPEN or CLAIMED creates a
# claimable/live row; if this row's scopes are frozen by ANOTHER OPEN
# freeze-window row, the flip would manufacture the very state the freeze
# bars (the claim would then be refused at new-claim time, leaving a staged,
# unclaimable row), so it is refused and names the freezing row. Closing
# flips (DONE, BLOCKED, SUPERSEDED, VOID) are never blocked - closing the
# freeze row itself is what lifts the freeze, and a guard that cannot be
# cleared is not a guard (CLAIMS/README.md). Measured 2026-10-26, before
# this guard: the freeze was honoured only at claim time, so the flip path
# could stage a claimable frozen row.
if ($NewStatus -eq 'OPEN' -or $NewStatus -eq 'CLAIMED') {
    $rowScopeCellF = ''
    $rowCellsF = @($oldLine -split '\|')
    if ($rowCellsF.Count -ge 5) { $rowScopeCellF = [string]$rowCellsF[4] }
    $rowScopesF = @([regex]::Matches($rowScopeCellF, '(engine|tooling|resources|docs|ai|ui|model|card-text/b[0-9]+)') |
                    ForEach-Object { $_.Value } | Select-Object -Unique)
    $frozenConflicts = @()
    foreach ($ln in $lines) {
        if ($ln -match '^\|\s*(B5-[0-9]{4}[a-z]?)\s*\|([^|]*)\|([^|]*)\|([^|]*)\|') {
            # Snapshot the groups FIRST: any later -notmatch repopulates $Matches
            # (a matching -notmatch sets it, and that pattern has no groups), which
            # would turn $matches[4] into $null - measured in the red-then-green
            # run of case 14 before this snapshot was added.
            $freezeId = $matches[1]
            $freezeStatus = $matches[2].Trim()
            $freezeTask = $matches[3]
            $freezeScopeCell = $matches[4]
            if ($freezeId -eq $Id) { continue }
            if ($freezeStatus -ne 'OPEN') { continue }
            if ($freezeTask -notmatch 'freeze window') { continue }
            $frozenScopes = @([regex]::Matches($freezeScopeCell, '(engine|tooling|resources|docs|ai|ui|model|card-text/b[0-9]+)') |
                              ForEach-Object { $_.Value } | Select-Object -Unique)
            foreach ($rs in $rowScopesF) {
                if ($frozenScopes -contains $rs) {
                    $frozenConflicts += "    $freezeId (freeze window) freezes scope '$rs'"
                }
            }
        }
    }
    if ($frozenConflicts.Count -gt 0) {
        Write-Error ("refuse: scope is frozen for task $Id. The following OPEN freeze-window rows freeze a scope of this row; close them first:" + [Environment]::Newline + ($frozenConflicts -join [Environment]::Newline))
        exit 1
    }
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
