#Requires -Version 3.0
<#
.SYNOPSIS
    Duplicate-task-ID census over .agent/TASK_LEDGER.md (boot step 9, gate (a)).

.DESCRIPTION
    Prints one line per duplicated task ID. THE PASS CONDITION IS EXIT 0, NOT
    EMPTY OUTPUT (B5-1732, 2026-10-01): "Empty output is the pass condition" was
    withdrawn as wrong, and it was written about THIS file while describing the
    wrapper's behaviour instead of its own. This file does keep stdout empty on
    pass by contract; the run-dup-census.ps1 wrapper then adds its own PASS or
    FAIL banner, so the caller sees 3 stdout lines at exit 0 and 4 at exit 1.
    Judge the exit code. The queue keys task status by ID, so a second row
    silently overwrites the first and one task becomes invisible to the gate and
    lane logic.

    Exit codes are the machine contract, not decoration:
      0  no duplicate IDs   (pass)
      1  one or more duplicate IDs found (fail)
      2  the ledger is missing or unreadable (fail, distinct from a clean pass)

.NOTES
    Pre-repair defect (B5-0777). This file previously held a bash one-liner:

        (Select-String -Path '...' -Pattern '^\|'+\s*(B5-...)...').Matches | ...

    The regex was written for bash/grep, where '...' ends at the first quote and
    the following + is a repetition operator. PowerShell has no such rule, so the
    single-quoted string terminated at the pipe, the + became array-index syntax,
    and the file failed to PARSE -- a ParserError before any census ran. The gate
    this file backs (boot step 9a) has therefore never executed on any tree.

    The fix is to put the whole regex inside one single-quoted string, where PowerShell
    treats | as an ordinary character and passes it to the regex engine, which reads
    it as alternation-or-literal per context and as an escaped literal for \|.
#>
[CmdletBinding()]
param(
    [string] $LedgerPath
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

# Resolved in the body, not as a param default: under -File, $PSScriptRoot is not yet
# bound while param defaults are evaluated, so a default built from it throws before
# the script body runs and the failure is reported as a census result.
if (-not $PSBoundParameters.ContainsKey('LedgerPath') -or [string]::IsNullOrEmpty($LedgerPath)) {
    $LedgerPath = Join-Path (Split-Path -Parent $PSScriptRoot) 'TASK_LEDGER.md'
}

# Write to stderr directly rather than via Write-Error: with $ErrorActionPreference
# 'Stop', Write-Error THROWS, which skips the exit 2 below and lets the shell report
# the throw's own exit 1 -- collapsing "could not read the ledger" into the same
# code as "the ledger is clean", which is the one distinction a gate must preserve.
function Write-CensusError([string] $message) {
    [Console]::Error.WriteLine($message)
}

if (-not (Test-Path -LiteralPath $LedgerPath)) {
    Write-CensusError ("duplicate-ID census: ledger not found: {0}" -f $LedgerPath)
    exit 2
}

# A row is a task row when it starts with one or more pipes, then the ID, then a pipe.
# The tolerant ^\|+ lead is required: a double-lead row is still a task row, and an
# anchor of ^\| would silently skip it (B5-0613).
$pattern = '^\|+\s*(B5-[0-9]{4}[a-z]?)\s*\|'

try {
    # B5-1002: encoding pinned to explicit UTF-8. Select-String without
    # -Encoding inherits the host default, which mis-decodes UTF-8 multi-byte
    # sequences into phantom marks (docs/DECISIONS.md reads 1600 C1 naive vs
    # 0 UTF-8 on the same bytes) -- a phantom-defect generator.
    $ids = (Select-String -Path $LedgerPath -Encoding UTF8 -Pattern $pattern -AllMatches).Matches |
        ForEach-Object { $_.Groups[1].Value }
}
catch {
    Write-CensusError ("duplicate-ID census: ledger unreadable: {0} -- {1}" -f $LedgerPath, $_.Exception.Message)
    exit 2
}

$duplicates = @($ids | Group-Object | Where-Object { $_.Count -gt 1 })

# B5-1002 receipt, reworded by B5-1732 (2026-10-01): stdout stays empty on pass
# BY CONTRACT at THIS level, because the run-dup-census wrapper re-prints every
# stdout line and would otherwise show the encoding receipt as if it were a
# duplicated ID. The 00_BOOT step 9 clause that "reads empty output as the pass
# condition" is WITHDRAWN -- it described this file while documenting the
# wrapper, whose banner is deliberate. So the encoding receipt goes to the
# verbose stream here: visible under -Verbose, invisible to the gate. The
# wrapper's own PASS/FAIL banner carries the caller-visible receipt, and the
# caller judges the EXIT CODE, not stdout emptiness.
Write-Verbose "dup-census encoding: UTF-8 (explicit; Select-String -Encoding UTF8)"

if ($duplicates.Count -gt 0) {
    foreach ($dup in $duplicates) {
        Write-Output ("{0} x{1}" -f $dup.Name, $dup.Count)
    }
    exit 1
}

exit 0
