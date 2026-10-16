#Requires -Version 3.0
<#
.SYNOPSIS
    Runner wrapper for the duplicate-task-ID census (boot step 9, gate (a)).

.DESCRIPTION
    Thin, fail-transparent wrapper around dup-census.ps1. It exists so the census has
    ONE entry point named for the RUN step, and so the wrapper can add the pass/fail
    banner without duplicating the census logic. All parsing lives in dup-census.ps1;
    a duplicated rule in two files is exactly how the two drifted into disagreeing
    in the first place (B5-0777).

    Exit codes are passed through unchanged from the census, so the wrapper never
    converts a fail into a pass:
      0  no duplicate IDs   (pass)
      1  one or more duplicate IDs found (fail)
      2  the ledger is missing or unreadable (fail, distinct from a clean pass)

.NOTES
    Pre-repair defect (B5-0777). This file previously held a byte-identical copy of
    the unparseable bash one-liner. It failed with a ParserError rather than a census
    result, so boot step 9a had no working implementation at all.
#>
[CmdletBinding()]
param(
    [string] $LedgerPath
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$census = Join-Path $PSScriptRoot 'dup-census.ps1'
if (-not (Test-Path -LiteralPath $census)) {
    Write-Error ("census not found: {0}" -f $census)
    exit 2
}

$argList = @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', $census)
if ($PSBoundParameters.ContainsKey('LedgerPath')) {
    $argList += @('-LedgerPath', $LedgerPath)
}

# -LiteralPath is not accepted by the call operator, so shell-quote the one value
# that may carry spaces (a TEMP fixture path under a Windows user profile).
foreach ($i in 0..($argList.Count - 1)) {
    if ($argList[$i] -eq '-LedgerPath') {
        $argList[$i + 1] = '"{0}"' -f $argList[$i + 1].Replace('"', '""')
    }
}

$output = & powershell @argList
$code = $LASTEXITCODE

foreach ($line in @($output)) {
    if ($null -ne $line -and $line.ToString().Trim().Length -gt 0) {
        Write-Output $line
    }
}

switch ($code) {
    # B5-1002: encoding receipt. The wrapped census reads the ledger as
    # explicit UTF-8 (Select-String -Encoding UTF8); the receipt lives here
    # because the inner census must keep stdout empty on pass by contract.
    0 { Write-Output 'PASS'; Write-Output 'duplicate-ID census: 0 duplicate task IDs'; Write-Output '-- ENCODING: UTF-8 (explicit) --' }
    1 { Write-Output 'FAIL'; Write-Output 'duplicate-ID census: see duplicated IDs above'; Write-Output '-- ENCODING: UTF-8 (explicit) --' }
    default { Write-Output ("duplicate-ID census: ERROR (exit {0}) [encoding: UTF-8 (explicit)]" -f $code) }
}

exit $code
