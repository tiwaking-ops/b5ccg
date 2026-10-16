#Requires -Version 3.0
<#
.SYNOPSIS
    WP-18c (task B5-0024): rule coverage report against the Playable Slice.

.DESCRIPTION
    How many of the base-rules sections does anything actually exercise? The
    question cannot be answered from the rulebook, which never changes, nor from
    the Java, which carries no rule references. It needs a mapping between the
    two, and this tool reads that mapping from a sidecar file rather than from
    code:

        docs/RULE_COVERAGE_MAP.md    check name -> rule ID, with a status

    A rule counts as covered when the map says a check exercises it. Zero
    .java changes are required by that convention and this tool makes none: it
    reads the index, the map and a gate log, and writes nothing.

    Why the map is separate from the code. Putting these claims in the probes
    would mean editing 181 source files to ask a measurement question, touching
    every compile and every other agent's scope. Doing it here keeps the
    question answerable today and lets the inline comments be added later, one
    scope at a time, without changing what the measurement means.

    Status is the honesty mechanism. A row is either verified (the rulebook text
    was read and states the rule the check enforces) or unverified (plausible but
    unproved). Only verified rows count. Unverified rows are printed but never
    counted, so a guess can raise curiosity or a follow-up row and never a
    percentage.

    Two hard failures, because a map that silently rots is worse than none:
      * a mapped check name absent from the gate log - the check was renamed or
        deleted and its coverage evaporated;
      * an ID absent from docs/RULE_INDEX.md - the index moved on.

    Denominator is derived, never hard-coded: every index entry tagged S1, minus
    the chapter-heading containers of two lines that carry no rules of their own
    (see the proposal's section 4). Chapter V stays in; it has real content.

    This script is pure ASCII, like every other tool in .agent/tools/, because
    Windows PowerShell 5.1 mis-reads non-ASCII literals in a BOM-less file. The
    en dash written into the rule index is matched here as an escape.

.OUTPUTS
    Exit 0  report produced (a low number is a measurement, never a failure).
    Exit 1  a mapped check name is missing from the gate log, or an ID is not in
            the rule index.
    Exit 2  index, map or gate log missing or unreadable. Never 0: an
            unreachable source must not read as "all covered".
#>
[CmdletBinding()]
param(
    [string] $Index,
    [string] $Map,
    [string] $GateLog
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
if (-not $Index)   { $Index   = Join-Path $repoRoot 'docs/RULE_INDEX.md' }
if (-not $Map)     { $Map     = Join-Path $repoRoot 'docs/RULE_COVERAGE_MAP.md' }
if (-not $GateLog) {
    $newest = Get-ChildItem -LiteralPath $repoRoot -Filter '*_gate.log' -File -ErrorAction SilentlyContinue |
              Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if ($newest) { $GateLog = $newest.FullName }
}

foreach ($pair in @(@('rule index', $Index), @('coverage map', $Map), @('gate log', $GateLog))) {
    if (-not $pair[1] -or -not (Test-Path -LiteralPath $pair[1])) {
        Write-Output ("[WP-18c] EXIT 2: {0} not found: {1}" -f $pair[0], $(if ($pair[1]) { $pair[1] } else { '<unset>' }))
        if ($pair[0] -eq 'gate log') {
            Write-Output "[WP-18c] capture one with: RUN_TESTS=1 sh b5ccg/compile.sh > <name>_gate.log 2>&1"
        }
        exit 2
    }
}

# ------------------------------------------------------------- the index ----
$indexRows = New-Object System.Collections.Generic.List[object]
foreach ($row in @([System.IO.File]::ReadAllLines($Index) | Where-Object { $_ -match '^\| RB:' })) {
    $cells = ($row -split '\|')
    for ($k = 0; $k -lt $cells.Count; $k++) { $cells[$k] = $cells[$k].Trim() }
    if ($cells.Count -lt 6) { continue }
    $name = ($row -split '\|')[1].Trim()
    if ($name.Contains(' ')) { continue }   # skip any row whose first cell is prose
    $rangeRaw = $cells[3] -replace "[\u2012\u2013\u2014\u2015]", '-'
    if ($rangeRaw -match '^(\d+)-(\d+)$')   { $start = [int]$Matches[1]; $end = [int]$Matches[2] }
    elseif ($rangeRaw -match '^(\d+)$')     { $start = [int]$Matches[1]; $end = [int]$Matches[1] }
    else { continue }
    $blocks = 0
    if ($cells[4] -match '^\d+$') { $blocks = [int]$cells[4] }
    $null = $indexRows.Add([pscustomobject]@{
        Id = $cells[1]; Heading = $cells[2]; Start = $start; End = $end; Blocks = $blocks; Scope = $cells[5]
    })
}
if ($indexRows.Count -eq 0) {
    Write-Output ("[WP-18c] EXIT 2: {0} carries no rows starting with '| RB:'" -f $Index)
    exit 2
}

# The denominator: S1 entries that are not 2-line chapter containers. Derived so
# that re-tagging a section changes the denominator in the next report.
$s1 = @($indexRows | Where-Object { $_.Scope -eq 'S1' })
$denominator = @($s1 | Where-Object { -not ($_.End -eq $_.Start + 1 -and $_.Id -match '^RB:(I|II|III|IV|V|VI)$') })

# --------------------------------------------------------------- the map ----
$mapRows = New-Object System.Collections.Generic.List[object]
$lineNo = 0
foreach ($row in [System.IO.File]::ReadAllLines($Map)) {
    $lineNo++
    if ($row -notmatch '^\| `') { continue }
    $cells = ($row -split '\|')
    for ($k = 0; $k -lt $cells.Count; $k++) { $cells[$k] = $cells[$k].Trim() }
    if ($cells.Count -lt 5) { continue }
    $checkName = $cells[1].Trim('`')
    $id        = $cells[2].Trim('`')
    $status    = $cells[3]
    $how       = $cells[4]
    if (-not $checkName -or -not $id) { continue }
    $null = $mapRows.Add([pscustomobject]@{
        CheckName = $checkName; Id = $id; Status = $status; How = $how; Line = $lineNo
    })
}
if ($mapRows.Count -eq 0) {
    Write-Output ("[WP-18c] EXIT 2: {0} carries no mapping rows" -f $Map)
    exit 2
}

# ----------------------------------------------------------- the gate log ----
$logChecks = @{}
foreach ($line in [System.IO.File]::ReadAllLines($GateLog)) {
    if ($line -match '^\s*(\[[^\]]+\]\s+.*?):\s*(PASS|FAIL)\s*$') {
        $logChecks[$Matches[1].Trim()] = $Matches[2]
    }
}
if ($logChecks.Count -eq 0) {
    Write-Output ("[WP-18c] EXIT 2: {0} carries no check lines of the form '  [TAG] name: PASS|FAIL'" -f $GateLog)
    exit 2
}

# ------------------------------------------------------------ validation ----
$failures = New-Object System.Collections.Generic.List[object]

foreach ($r in $mapRows) {
    if (-not ($indexRows | Where-Object { $_.Id -eq $r.Id })) {
        $failures.Add([pscustomobject]@{
            Kind = 'id'
            Text = ('map line {0}: ID {1} is not in {2}' -f $r.Line, $r.Id, (Split-Path $Index -Leaf))
        })
    }
    if (-not $logChecks.ContainsKey($r.CheckName)) {
        $failures.Add([pscustomobject]@{
            Kind = 'stale'
            Text = ('map line {0}: check not in the gate log: {1}' -f $r.Line, $r.CheckName)
        })
    }
    if ($r.Status -ne 'verified' -and $r.Status -ne 'unverified') {
        $failures.Add([pscustomobject]@{
            Kind = 'status'
            Text = ('map line {0}: status must be verified or unverified, found "{1}"' -f $r.Line, $r.Status)
        })
    }
}

if ($failures.Count -gt 0) {
    Write-Output ("[WP-18c] FAIL: {0} map problem(s). The coverage figures below would be untrustworthy, so none are printed." -f $failures.Count)
    Write-Output ""
    foreach ($f in $failures) { Write-Output ("  " + $f.Text) }
    exit 1
}

# --------------------------------------------------------------- coverage ----
$covered   = New-Object System.Collections.Generic.List[object]
$uncovered = New-Object System.Collections.Generic.List[object]
$pending   = New-Object System.Collections.Generic.List[object]

foreach ($entry in $denominator) {
    $rows = @($mapRows | Where-Object { $_.Id -eq $entry.Id })
    if ($rows.Count -eq 0) { $uncovered.Add($entry); continue }

    $good = @($rows | Where-Object { $_.Status -eq 'verified' })
    if ($good.Count -eq 0) {
        $pending.Add([pscustomobject]@{ Entry = $entry; Count = $rows.Count })
        $uncovered.Add($entry)
        continue
    }

    $failing = @($good | Where-Object { $logChecks[[string]$_.CheckName] -eq 'FAIL' })
    $covered.Add([pscustomobject]@{
        Entry = $entry; Verified = $good.Count; Failing = @($failing).Count
        Checks = (($good | ForEach-Object { $_.CheckName }) -join '; ')
    })
}

$target90 = [int][Math]::Ceiling($denominator.Count * 0.9)
$pct = 0.0
if ($denominator.Count -gt 0) { $pct = [Math]::Round(100.0 * $covered.Count / $denominator.Count, 1) }

Write-Output ("[WP-18c] index      : {0} ({1} rows)" -f (Split-Path $Index -Leaf), $indexRows.Count)
Write-Output ("[WP-18c] map        : {0} ({1} rows, {2} verified, {3} unverified)" -f (Split-Path $Map -Leaf), $mapRows.Count, @($mapRows | Where-Object { $_.Status -eq 'verified' }).Count, @($mapRows | Where-Object { $_.Status -eq 'unverified' }).Count)
Write-Output ("[WP-18c] gate log   : {0} ({1} check lines, {2} FAIL)" -f (Split-Path $GateLog -Leaf), $logChecks.Count, @($logChecks.Values | Where-Object { $_ -eq 'FAIL' }).Count)
Write-Output ""
Write-Output ("[WP-18c] denominator: {0} S1 entries carrying rules ({1} S1 rows minus {2} two-line chapter containers)" -f $denominator.Count, $s1.Count, ($s1.Count - $denominator.Count))
Write-Output ("[WP-18c] covered    : {0}" -f $covered.Count)
Write-Output ("[WP-18c] uncovered  : {0}" -f $uncovered.Count)
Write-Output ("[WP-18c] coverage   : {0}%  (Playable Slice target 90% >= {1} of {2})" -f $pct, $target90, $denominator.Count)
Write-Output ""

if ($covered.Count -gt 0) {
    Write-Output "[WP-18c] covered entries:"
    foreach ($c in ($covered | Sort-Object { $_.Entry.Start })) {
        $flag = ''
        if ($c.Failing -gt 0) { $flag = ('  [' + $c.Failing + ' of ' + $c.Verified + ' mapped check(s) currently FAIL - the rule is exercised, not honoured]') }
        Write-Output ("    {0}  {1}{2}" -f $c.Entry.Id.PadRight(42), $c.Entry.Heading, $flag)
    }
    Write-Output ""
}

if ($pending.Count -gt 0) {
    Write-Output "[WP-18c] mapped but unverified - counted as uncovered, awaiting a human read:"
    foreach ($p in $pending) {
        Write-Output ("    {0}  {1}  ({2} row(s) in the map)" -f $p.Entry.Id.PadRight(42), $p.Entry.Heading, $p.Count)
    }
    Write-Output ""
}

if ($uncovered.Count -gt 0) {
    Write-Output "[WP-18c] uncovered entries (no verified check mapped):"
    foreach ($u in ($uncovered | Sort-Object { $_.Start })) {
        Write-Output ("    {0}  {1}  (lines {2}-{3})" -f $u.Id.PadRight(42), $u.Heading, $u.Start, $u.End)
    }
    Write-Output ""
}

Write-Output "-- ENCODING: UTF-8 (explicit) --"
exit 0
