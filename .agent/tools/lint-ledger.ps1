<#
.SYNOPSIS
  Lint for TASK_LEDGER.md row defects: double lead pipe, wrong pipe count,
  malformed id, ids out of order, and B5- rows sitting outside the table.
.DESCRIPTION
  Exits 1 on any defect, 0 on clean.

  Two defects in an earlier revision are fixed here:
   1. The default path was built as (parent-of-.agent/tools) + '.agent/TASK_LEDGER.md',
      which resolved to '.agent/.agent/TASK_LEDGER.md' and threw DirectoryNotFound.
      The parent of $PSScriptRoot IS .agent already.
   2. $ErrorActionPreference was 'Continue', so the tool exited 0 after throwing.
      The exit code is the contract (AGENTS.md section 5); a crash must be red.
#>
param([string]$LedgerPath = (Join-Path (Split-Path -Parent $PSScriptRoot) 'TASK_LEDGER.md'))

$ErrorActionPreference = 'Stop'

if (-not (Test-Path -LiteralPath $LedgerPath)) {
    Write-Error ("ledger not found: {0}" -f $LedgerPath)
    exit 1
}

$content = [System.IO.File]::ReadAllText($LedgerPath)
$lines = $content -split "`r?`n"
$exit = 0
# Findings are collected with Write-Output, not Write-Error: with
# ErrorActionPreference='Stop' a Write-Error terminates the run and reports
# only the FIRST defect. A lint must list every defect it found in one pass.
$findings = New-Object System.Collections.Generic.List[string]
function Add-Finding([string]$m) { $findings.Add($m) | Out-Null; $script:exit = 1 }

$inTable = $false
$tableClosed = $false
$ids = @()

for ($i = 0; $i -lt $lines.Count; $i++) {
    $line = $lines[$i]

    # Table header opens the region we police.
    if ($line -match '^\|\s*ID\s*\|') { $inTable = $true; $tableClosed = $false; continue }

    # The 'Future' paragraph closes it. Everything after is prose.
    if ($inTable -and $line -match '^Future') { $inTable = $false; $tableClosed = $true; continue }

    if (-not $inTable) {
        # A B5- row after the table closed is the defect the ledger repair
        # B5-0011 was written to prevent: the row parses but a human cannot
        # read it in place, so the row is invisible to anyone scanning the file.
        if ($tableClosed -and $line -match '^\|+\s*B5-\d{4}') {
            Add-Finding ("row outside table at line {0}: {1}" -f ($i + 1), $line.Trim())
        }
        continue
    }

    if ($line -notmatch '^\|') { continue }

    # The markdown separator row under the header is not a data row.
    if ($line -match '^\|[\s\-\|:]+\|\s*$') { continue }

    if ($line.StartsWith('||')) {
        Add-Finding ("double lead pipe at line {0}" -f ($i + 1))
    }

    # 6 columns -> 7 pipes.
    $pipes = ([regex]::Matches($line, '\|')).Count
    if ($pipes -ne 7) {
        Add-Finding ("pipe count {0} (expected 7) at line {1}" -f $pipes, ($i + 1))
    }

    $m = [regex]::Match($line, '^\|\s*(B5-\d{4})\s*\|')
    if (-not $m.Success) {
        Add-Finding ("bad or missing B5-nnnn id at line {0}: {1}" -f ($i + 1), $line.Trim())
        continue
    }
    $ids += $m.Groups[1].Value
}

# Ids must ascend. B5-0021 and B5-0031 are known gaps, not disorder: a gap is
# a missing number, and only a DECREASING number is malformed.
for ($i = 1; $i -lt $ids.Count; $i++) {
    $nA = [int]($ids[$i - 1] -replace '^B5-', '')
    $nB = [int]($ids[$i] -replace '^B5-', '')
    if ($nB -le $nA) {
        Add-Finding ("ids out of order: {0} then {1} (table index {2})" -f $ids[$i - 1], $ids[$i], $i)
    }
}

if ($exit -ne 0) {
    Write-Output ("=== LEDGER LINT FAILED: {0} defect(s) ===" -f $findings.Count)
    $findings | ForEach-Object { Write-Output ("  - {0}" -f $_) }
    exit 1
}
Write-Output ("=== LEDGER LINT PASS ({0} rows in table) ===" -f $ids.Count)
exit 0