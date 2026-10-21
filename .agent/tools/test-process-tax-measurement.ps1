<#
.SYNOPSIS
  B5-0026 / WP-09 (B2): guard for the process-tax measurement instrument.
.DESCRIPTION
  The B5-0026 acceptance is a measurement, and the reading is due on
  2026-10-30. An instrument that has rotted by then is worse than no
  instrument, because the reading would be taken with it and the row would
  close on a number nobody can reproduce.

  This probe therefore asserts that the instrument still WORKS, not that it
  reports a particular value - the values change daily by design, and a
  probe that asserted them would be a probe that had to be edited every day
  (and would therefore not be run on the day it mattered).

  What it asserts:
    1. the instrument runs and exits 0 on the working tree;
    2. it prints all three KPI sections and the target table;
    3. it writes a JSON snapshot, and the snapshot parses, and it carries
       the definitions, the denominators and the three KPIs;
    4. a bad git ref is refused with exit 2, which is the instrument's
       false-pass code. A measurement that cannot be taken must never look
       like a measurement that found nothing.

  Exits 0 when every check passes, 1 otherwise.
#>
[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$tool = Join-Path $PSScriptRoot 'measure-process-tax.ps1'
$failures = @()
$checks = 0

function Check {
    param([string]$What, [bool]$Ok, [string]$Detail = '')
    $script:checks++
    if ($Ok) {
        Write-Host ("PASS  " + $What)
    } else {
        $script:failures += $What
        Write-Host ("FAIL  " + $What + $(if ($Detail) { " - $Detail" } else { '' }))
    }
}

if (-not (Test-Path -LiteralPath $tool)) {
    Write-Host ("FAIL  the instrument is missing: " + $tool)
    Write-Host "RESULT: FAIL (1 checks, 1 failures)"
    exit 1
}

# A child that writes to stderr (git's "fatal: Not a valid object name", for
# one) makes that stderr a TERMINATING error in THIS script under
# $ErrorActionPreference='Stop', before $LASTEXITCODE can be read. So every
# child call runs with stderr folded into stdout and the preference relaxed
# for the call only - the same pattern test-param-guard.ps1 uses.
function Invoke-Instrument {
    param([string[]]$Arguments)
    $old = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $result = & powershell -NoProfile -ExecutionPolicy Bypass -File $tool @Arguments 2>&1
        $code = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $old
    }
    return [pscustomobject]@{ Output = $result; Code = $code }
}

# ── 1 + 2: it runs, and it prints the sections ─────────────────────────────
$run1 = Invoke-Instrument @()
$out = $run1.Output
$code = $run1.Code
Check "the instrument runs and exits 0 on the working tree" ($code -eq 0) `
    ("exit " + $code + ": " + (($out | Out-String) -replace '\s+', ' ').Substring(0, [Math]::Min(200, (($out | Out-String) -replace '\s+', ' ').Length)))
$text = ($out | Out-String)
Check "it prints KPI (a)"            ($text -match 'KPI \(a\)') ""
Check "it prints KPI (b)"            ($text -match 'KPI \(b\)') ""
Check "it prints KPI (c)"            ($text -match 'KPI \(c\)') ""
Check "it prints the section 7.4 target table" ($text -match 'section 7\.4 target table') ""
Check "it prints the denominators"   ($text -match 'denominator') ""
Check "it prints the coverage line for KPI (c)" ($text -match 'coverage:') ""

# ── 3: it writes a snapshot that parses and carries its definitions ────────
$tmp = Join-Path ([System.IO.Path]::GetTempPath()) ("b5-0026-probe-" + [System.Guid]::NewGuid().ToString('N') + ".json")
try {
    $run2 = Invoke-Instrument @('-OutFile', $tmp)
    $code2 = $run2.Code
    Check "it writes a snapshot and exits 0" ($code2 -eq 0) ("exit " + $code2)
    Check "the snapshot file exists" (Test-Path -LiteralPath $tmp) ""
    if (Test-Path -LiteralPath $tmp) {
        $json = Get-Content -LiteralPath $tmp -Raw -Encoding UTF8 | ConvertFrom-Json
        Check "the snapshot parses as JSON" ($null -ne $json) ""
        Check "the snapshot carries its definitions" ($null -ne $json.definitions) ""
        $defs = $json.definitions
        foreach ($k in @('kpi_a', 'kpi_b_bytes', 'kpi_c', 'gate_green_rule', 'process_document')) {
            $present = ($defs.PSObject.Properties.Name -contains $k) -and ($defs.$k)
            Check ("the snapshot defines " + $k) $present ""
        }
        Check "the snapshot carries the denominators" `
            (($json.counts.PSObject.Properties.Name -contains 'done_rows') -and `
             ($json.counts.PSObject.Properties.Name -contains 'process_documents')) ""
        Check "the snapshot carries the three KPI values" `
            (($json.PSObject.Properties.Name -contains 'kpi_a') -and `
             ($json.PSObject.Properties.Name -contains 'kpi_b_bytes') -and `
             ($json.PSObject.Properties.Name -contains 'kpi_c_gate_green_commits_per_day')) ""
        # The owner ruling of 2026-10-22 must be IN the snapshot, so that a
        # reading taken on 2026-10-30 cannot silently use another baseline.
        Check "the snapshot records the baseline it is read against" `
            (($json.PSObject.Properties.Name -contains 'baseline') -and `
             ($json.baseline.PSObject.Properties.Name -contains 'docs_per_task') -and `
             ($json.baseline.PSObject.Properties.Name -contains 'boot_bytes')) ""
        Check "the snapshot records the targets it is judged against" `
            (($json.PSObject.Properties.Name -contains 'targets') -and `
             ($json.targets.PSObject.Properties.Name -contains 'docs_per_row') -and `
             ($json.targets.PSObject.Properties.Name -contains 'boot_bytes')) ""
        Check "the snapshot records a verdict for each judged KPI" `
            (($json.PSObject.Properties.Name -contains 'kpi_a_verdict') -and `
             ($json.PSObject.Properties.Name -contains 'kpi_b_verdict')) ""
        Check "the snapshot's denominator is ALL ledger rows" `
            (($json.counts.PSObject.Properties.Name -contains 'ledger_rows') -and `
             ($json.counts.PSObject.Properties.Name -contains 'row_scoped_reports')) ""
        Check "the ruling is stated in the definitions" `
            (($defs.kpi_a -match 'owner ruling') -and ($defs.kpi_a_denominator -match 'ALL ledger rows')) ""
        Check "the snapshot names the tree it measured" ([bool]$json.tree) ""
        Check "the snapshot records the commit series" ($json.commits_per_day.Count -gt 0) ""
    }
} finally {
    if (Test-Path -LiteralPath $tmp) { Remove-Item -LiteralPath $tmp -Force }
}

# ── 4: a measurement that cannot be taken must not look like a clean one ────
$run3 = Invoke-Instrument @('-Ref', 'no-such-ref')
$out3 = $run3.Output
$code3 = $run3.Code
Check "a bad git ref is refused with exit 2 (the false-pass code)" ($code3 -eq 2) `
    ("exit " + $code3 + " - an instrument that cannot measure must not report 0")
Check "the refusal says nothing was measured" `
    ((($out3 | Out-String) -match 'Nothing was measured')) ""

# ── 5: a capture that is LOCKED FOR WRITING must not kill the measurement ──
# The gate found this one. The instrument read the capture files with
# ReadAllText, and the capture the gate was WRITING at that moment was
# locked, so the whole measurement died with "The process cannot access the
# file". A locked capture is a gap in the coverage, not a reason to report
# nothing - and on the morning of the reading, the session's own gate
# capture may well be open.
$lockPath = Join-Path $repoRoot '.agent/REPORTS/evidence/GATE-LOCKTEST-probe.txt'
$lock = $null
try {
    $lock = [System.IO.File]::Open($lockPath, [System.IO.FileMode]::Create,
                                   [System.IO.FileAccess]::Write, [System.IO.FileShare]::None)
    $bytes = [System.Text.Encoding]::UTF8.GetBytes('Summary: 37 passed, 0 failed')
    $lock.Write($bytes, 0, $bytes.Length)
    $lock.Flush()
    $run4 = Invoke-Instrument @()
    Check "the instrument survives a capture that is locked for writing" ($run4.Code -eq 0) `
        ("exit " + $run4.Code + " - a locked capture killed the whole measurement")
    Check "the locked capture is reported as a coverage gap, not hidden" `
        ((($run4.Output | Out-String) -match 'unreadable|unknown')) ""
} finally {
    if ($lock) { try { $lock.Close() } catch { } }
    if (Test-Path -LiteralPath $lockPath) { Remove-Item -LiteralPath $lockPath -Force }
}

Write-Host ("RESULT: " + $(if ($failures.Count -eq 0) { "PASS" } else { "FAIL" }) +
            " (" + $checks + " checks, " + $failures.Count + " failures)")
if ($failures.Count -gt 0) { exit 1 }
exit 0
