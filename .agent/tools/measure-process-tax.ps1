<#
.SYNOPSIS
  B5-0026 / WP-09 (B2): measure the three process-tax KPIs, so the two-week
  window the acceptance names can be read on a stated date with a stated
  instrument.
.DESCRIPTION
  WHY THIS TOOL EXISTS
  The B5-0026 acceptance reads "B2 targets in 7.4 met and measured over a
  two-week window", and the proposal's §7.4 "Verified by" clause names three
  numbers:

    (a) process documents per ledger row falls from about 5.1 to about 1 or less
    (b) boot tokens per task fall
    (c) gate-green merged changes per day stay flat or rise

  and one method: "Measure before and after on the same two-week window."

  A before/after measured with two different instruments is not a
  measurement. This tool is the instrument: it states its own definitions,
  prints its denominators, and writes a JSON snapshot, so that the reading
  taken on the closing date is comparable with the reading taken on the
  starting date BY CONSTRUCTION rather than by good intentions.

  The owner ruled on 2026-10-22 that the row closes when the window elapses,
  with the reading taken on 2026-10-30. The baseline snapshot is
  `.agent/REPORTS/measurements/process-tax-2026-10-22.json`.

  THE DEFINITIONS, which are the instrument's real content. A number without
  a definition is a rumour, so each one is printed with the reading.

    A. A "process document" is a Markdown file under .agent/REPORTS/,
       .agent/PATTERNS/, docs/ or investigations/. The denominator is the
       number of ledger rows with status DONE. The primary figure is the
       total; a narrower figure (reports + patterns only) is also printed,
       because docs/ mixes project-wide artefacts with per-task ones and the
       proposal's 5.1/row figure counted both. Both are reported rather
       than one being chosen silently.

    B. "Boot tokens per task" is the cost of one boot read: the size of
       .agent/00_BOOT.md, which every session reads once. Bytes and lines are
       the measured quantities; a token count is an ESTIMATE (bytes / 4) and
       is labelled as one, because the tokeniser's vocabulary is unknown.

    C. "Gate-green merged changes per day" is the number of commits per day
       on the working branch - in this repo a commit IS the unit of merged
       work, because AGENTS.md §5 forbids agents committing to main/master
       and D3 permits private task branches - counted only over days for
       which a gate capture exists, because "gate-green" is only evidenced
       where a capture exists. The verdict is parsed from the capture, and
       a capture is RED if it reports a failed class, a failed tooling
       probe, or an ERROR line. The coverage (days with a capture) is
       printed with the number, because a rate with unknown coverage is
       not a rate.

  The §7.4 target table is measured too, since the row's acceptance names
  the targets and the closing reading has to report them.

  USAGE
    measure-process-tax.ps1                      # print the reading
    measure-process-tax.ps1 -OutFile x.json      # also write a snapshot
    measure-process-tax.ps1 -Ref 94f0fa7         # the fork's static state

  EXIT CODES
    0 the reading was taken; 1 the tool could not measure (a broken
    instrument must be loud, and exit 2 rather than 0, so that it cannot be
    mistaken for a clean run).
#>
[CmdletBinding()]
param(
    # Where to write the JSON snapshot. Omit to print only.
    [string]$OutFile,
    # A git ref to measure the STATIC tree quantities against (the fork
    # commit, say). Omit to measure the working tree.
    [string]$Ref
)

$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)

# ── helpers ──────────────────────────────────────────────────────────────────

function Get-RowValue {
    param([string[]]$Lines, [string]$Id, [int]$Column)
    foreach ($line in $Lines) {
        if ($line -match ('^\|\s*' + [regex]::Escape($Id) + '\s*\|')) {
            $parts = $line -split '\|'
            if ($parts.Count -gt $Column) { return $parts[$Column].Trim() }
        }
    }
    return $null
}

function Get-LedgerLines {
    param([string]$Path)
    return [System.IO.File]::ReadAllLines($Path, [System.Text.Encoding]::UTF8)
}

# The number of ledger rows whose status cell reads DONE.
function Get-DoneRowCount {
    param([string[]]$Lines)
    $n = 0
    foreach ($line in $Lines) {
        if ($line -match '^\|\s*B5-[0-9]+\s*\|\s*DONE\s*\|') { $n++ }
    }
    return $n
}

# The DONE rows whose report file is older than the rotation threshold.
# A row's age comes from its report file when it names one, because the
# ledger carries no date column.
function Get-RotationEligibleCount {
    param([string[]]$Lines, [string]$LedgerPath, [int]$MaxAgeDays)
    $ledgerDir = Split-Path -Parent $LedgerPath
    $repo = Split-Path -Parent $ledgerDir
    $cutoff = (Get-Date).AddDays(-$MaxAgeDays)
    $n = 0
    foreach ($line in $Lines) {
        if ($line -notmatch '^\|\s*B5-[0-9]+\s*\|\s*DONE\s*\|') { continue }
        $parts = $line -split '\|'
        if ($parts.Count -lt 7) { continue }
        $report = $parts[6].Trim()
        if ($report -eq '-' -or $report.Length -eq 0) { continue }
        $path = Join-Path $repo ($report -replace '/', '\')
        if (Test-Path -LiteralPath $path) {
            if ((Get-Item -LiteralPath $path).LastWriteTime -lt $cutoff) { $n++ }
        }
    }
    return $n
}

# Read a file with SHARING allowed, so a capture that is being written RIGHT
# NOW - which is exactly the situation when this instrument runs inside the
# gate, because the gate is capturing its own output into the very directory
# being scanned - does not abort the whole measurement.
#
# This was found by the gate: the first wired run died with "The process
# cannot access the file ...GATE-B5-0026.txt", because the capture the gate
# was writing was locked. A measurement that dies because a log file is open
# is not a measurement.
function Read-TextShared {
    param([string]$Path)
    $stream = $null
    $reader = $null
    try {
        $stream = New-Object System.IO.FileStream(
            $Path,
            [System.IO.FileMode]::Open,
            [System.IO.FileAccess]::Read,
            [System.IO.FileShare]::ReadWrite)
        $reader = New-Object System.IO.StreamReader($stream, [System.Text.Encoding]::UTF8)
        return $reader.ReadToEnd()
    } catch {
        return $null
    } finally {
        if ($reader) { try { $reader.Close() } catch { } }
        if ($stream) { try { $stream.Close() } catch { } }
    }
}

# ── the tree's static quantities ─────────────────────────────────────────────

function Get-TreeFacts {
    param([string]$Root, [string]$GitRef)
    $facts = @{}
    if ($GitRef) {
        # A historical tree: file names and contents come from git, so the
        # measurement is of the committed bytes and not of whatever the
        # working tree happens to hold.
        $names = @(git -C $Root ls-tree -r --name-only $GitRef)
        if ($LASTEXITCODE -ne 0) { throw "git ls-tree failed for ref '$GitRef'" }
        $facts.ref = $GitRef
        $facts.reports = @($names | Where-Object { $_ -like '.agent/REPORTS/*' -and $_ -like '*.md' }).Count
        $facts.patterns = @($names | Where-Object { $_ -like '.agent/PATTERNS/*' -and $_ -like '*.md' }).Count
        $facts.docs = @($names | Where-Object { $_ -like 'docs/*' -and $_ -like '*.md' }).Count
        $facts.investigations = @($names | Where-Object { $_ -like 'investigations/*' }).Count
        $facts.pattern_namespaces = @($names | Where-Object { $_ -like '.agent/PATTERNS/*/*' } |
            ForEach-Object { ($_ -split '/')[2] } | Select-Object -Unique).Count
        $facts.root_md = @($names | Where-Object { $_ -notlike '*/*' -and $_ -like '*.md' }).Count
        # `git cat-file blob <object>` - "blob" is the TYPE argument, not an
        # option. `cat-file -blob` would be parsed as the switch -b and
        # fails with "unknown switch `b'".
        $boot = git -C $Root cat-file blob ($GitRef + ':.agent/00_BOOT.md') 2>$null
        $facts.boot_bytes = ($boot | Out-String).Length
        $facts.boot_lines = @($boot).Count
        $ledger = git -C $Root cat-file blob ($GitRef + ':.agent/TASK_LEDGER.md') 2>$null
        $facts.ledger_bytes = ($ledger | Out-String).Length
        $facts.done_rows = Get-DoneRowCount $ledger
        $facts.rotation_eligible = $null
    } else {
        $facts.ref = 'WORKING TREE'
        $facts.reports = @(Get-ChildItem (Join-Path $Root '.agent/REPORTS') -Recurse -File -Filter '*.md' -ErrorAction SilentlyContinue).Count
        $facts.patterns = @(Get-ChildItem (Join-Path $Root '.agent/PATTERNS') -Recurse -File -Filter '*.md' -ErrorAction SilentlyContinue).Count
        $facts.docs = @(Get-ChildItem (Join-Path $Root 'docs') -Recurse -File -Filter '*.md' -ErrorAction SilentlyContinue).Count
        $inv = Join-Path $Root 'investigations'
        $facts.investigations = if (Test-Path $inv) { @(Get-ChildItem $inv -Recurse -File -ErrorAction SilentlyContinue).Count } else { 0 }
        $patDir = Join-Path $Root '.agent/PATTERNS'
        $facts.pattern_namespaces = if (Test-Path $patDir) { @(Get-ChildItem $patDir -Directory -ErrorAction SilentlyContinue).Count } else { 0 }
        $facts.root_md = @(Get-ChildItem $Root -File -Filter '*.md' -ErrorAction SilentlyContinue).Count
        $bootPath = Join-Path $Root '.agent/00_BOOT.md'
        if (Test-Path $bootPath) {
            $facts.boot_bytes = (Get-Item $bootPath).Length
            $facts.boot_lines = @(Get-Content $bootPath).Count
        } else { $facts.boot_bytes = 0; $facts.boot_lines = 0 }
        $ledgerPath = Join-Path $Root '.agent/TASK_LEDGER.md'
        $facts.ledger_bytes = (Get-Item $ledgerPath).Length
        $lines = Get-LedgerLines $ledgerPath
        $facts.done_rows = Get-DoneRowCount $lines
        $facts.rotation_eligible = Get-RotationEligibleCount $lines $ledgerPath 14
    }
    $facts.process_docs = $facts.reports + $facts.patterns + $facts.docs + $facts.investigations
    $facts.narrow_docs = $facts.reports + $facts.patterns
    if ($facts.done_rows -gt 0) {
        $facts.kpi_a = [Math]::Round($facts.process_docs / [double]$facts.done_rows, 3)
        $facts.kpi_a_narrow = [Math]::Round($facts.narrow_docs / [double]$facts.done_rows, 3)
    } else {
        $facts.kpi_a = $null
        $facts.kpi_a_narrow = $null
    }
    $facts.kpi_b_bytes = $facts.boot_bytes
    $facts.kpi_b_lines = $facts.boot_lines
    $facts.kpi_b_tokens_est = [Math]::Round($facts.boot_bytes / 4.0, 1)
    return $facts
}

# ── the commit rate ──────────────────────────────────────────────────────────

function Get-CommitSeries {
    param([string]$Root, [string]$GitRef)
    # With a ref, the series is the history REACHABLE FROM that ref, so the
    # fork's reading is the fork's own history and not the branch's later
    # commits.
    if ($GitRef) {
        $lines = @(git -C $Root log --date=short --format='%ad' $GitRef)
    } else {
        $lines = @(git -C $Root log --date=short --format='%ad')
    }
    $byDay = @{}
    foreach ($d in $lines) {
        if ($d -match '^\d{4}-\d{2}-\d{2}$') {
            if ($byDay.ContainsKey($d)) { $byDay[$d] = $byDay[$d] + 1 } else { $byDay[$d] = 1 }
        }
    }
    $days = @($byDay.Keys | Sort-Object)
    $series = @()
    foreach ($d in $days) {
        $series += [pscustomobject]@{ date = $d; commits = $byDay[$d] }
    }
    return $series
}

# The gate captures, and the verdict each one records. A capture is RED if
# it reports a failed class, a failed tooling probe, or an ERROR line. The
# tooling-probe case matters: a capture can read "31 passed, 0 failed" for
# the Java classes and still be RED, because a tooling probe failed.
function Get-GateCaptures {
    param([string]$Root, [string]$GitRef)
    $out = @()
    if ($GitRef) {
        # A historical tree: the captures are the GATE-*.txt files that
        # existed at that ref. Their VERDICTS are not read (the instrument
        # does not dig through historical blobs for a coverage line), so they
        # are reported as NOT_READ rather than guessed at. The fork, for
        # one, had no captures at all, and the honest reading is zero
        # coverage - not an invented number.
        $names = @(git -C $Root ls-tree -r --name-only $GitRef -- '.agent/REPORTS/evidence')
        foreach ($n in $names) {
            if ((Split-Path -Leaf $n) -like 'GATE-*.txt') {
                $out += [pscustomobject]@{
                    file = (Split-Path -Leaf $n)
                    date = '(at the ref)'
                    verdict = 'NOT_READ'
                    deliberate_red = ((Split-Path -Leaf $n) -match '-RED')
                    classes = $null
                    tooling_probes = $null
                }
            }
        }
        return $out
    }
    $dir = Join-Path $Root '.agent/REPORTS/evidence'
    $out = @()
    if (-not (Test-Path $dir)) { return $out }
    foreach ($f in (Get-ChildItem $dir -File -Filter 'GATE-*.txt' -ErrorAction SilentlyContinue)) {
        $text = Read-TextShared $f.FullName
        if ($null -eq $text) {
            # Locked even with sharing, or unreadable for some other reason.
            # It is counted and named, and the measurement continues: one
            # unreadable capture is a gap in the coverage, not a reason to
            # report nothing.
            $out += [pscustomobject]@{
                file = $f.Name
                date = $f.LastWriteTime.ToString('yyyy-MM-dd')
                verdict = 'UNREADABLE'
                deliberate_red = ($f.Name -match '-RED')
                classes = $null
                tooling_probes = $null
            }
            continue
        }
        $verdict = 'UNKNOWN'
        $classes = $null
        $probes = $null
        foreach ($line in ($text -split "`n")) {
            if ($line -match 'Summary:\s*(\d+) passed,\s*(\d+) failed') {
                $classes = @{ passed = [int]$Matches[1]; failed = [int]$Matches[2] }
            }
            if ($line -match 'Tooling probes:\s*(\d+) passed,\s*(\d+) failed') {
                $probes = @{ passed = [int]$Matches[1]; failed = [int]$Matches[2] }
            }
        }
        $red = $false
        if ($text -match '(?m)^\s*ERROR:') { $red = $true }
        if ($null -ne $classes -and $classes.failed -gt 0) { $red = $true }
        if ($null -ne $probes -and $probes.failed -gt 0) { $red = $true }
        if ($text -match 'Verification passed') { $greenSignal = $true } else { $greenSignal = $false }
        if ($red) { $verdict = 'RED' }
        elseif ($greenSignal) { $verdict = 'GREEN' }
        # A capture whose FILE NAME carries -RED is deliberate red-then-green
        # evidence - the tree was deliberately broken to prove a probe could
        # fail - and is not a failure of the day's work. The distinction is
        # in the name because that is where the evidence record puts it, and
        # because a RED capture with no -RED in its name IS a failure.
        $deliberate = ($f.Name -match '-RED')
        $out += [pscustomobject]@{
            file = $f.Name
            date = $f.LastWriteTime.ToString('yyyy-MM-dd')
            verdict = $verdict
            deliberate_red = $deliberate
            classes = $classes
            tooling_probes = $probes
        }
    }
    return $out
}

# ── the reading ──────────────────────────────────────────────────────────────

try {
    $facts = Get-TreeFacts $repoRoot $Ref
    $series = @(Get-CommitSeries $repoRoot $Ref)
    $captures = @(Get-GateCaptures $repoRoot $Ref)

    Write-Host "=== PROCESS-TAX MEASUREMENT (B5-0026 / WP-09 B2) ==="
    Write-Host ("  measured:      " + (Get-Date -Format 'yyyy-MM-dd HH:mm:ss') + " local")
    Write-Host ("  tree:          " + $facts.ref)
    Write-Host ("  branch:        " + (git -C $repoRoot branch --show-current))
    Write-Host ""

    Write-Host "--- KPI (a): process documents per DONE ledger row ---"
    Write-Host ("  process documents: {0}  (REPORTS {1} + PATTERNS {2} + docs {3} + investigations {4})" -f `
        $facts.process_docs, $facts.reports, $facts.patterns, $facts.docs, $facts.investigations)
    Write-Host ("  DONE ledger rows:  {0}" -f $facts.done_rows)
    if ($null -eq $facts.kpi_a) {
        Write-Host ("  KPI (a): UNDEFINED - the denominator is 0 (no DONE row to divide by)" -f $facts.done_rows)
    } else {
        Write-Host ("  KPI (a) = {0} docs/row  (target: about 1 or less)" -f $facts.kpi_a)
        Write-Host ("  narrow reading (REPORTS + PATTERNS only) = {0} docs/row" -f $facts.kpi_a_narrow)
    }
    Write-Host ""

    Write-Host "--- KPI (b): boot read cost per task ---"
    Write-Host ("  .agent/00_BOOT.md: {0} B / {1} lines" -f $facts.boot_bytes, $facts.boot_lines)
    Write-Host ("  estimated tokens = bytes/4 = {0}  (ESTIMATE: the tokeniser is unknown)" -f $facts.kpi_b_tokens_est)
    Write-Host ""

    Write-Host "--- KPI (c): merged changes per day, and the gate verdicts ---"
    Write-Host "  date        commits  green  red(delib)  red(fail)  unknown"
    $greenDays = 0
    $daysWithCapture = 0
    $greenCommits = 0
    $daysWithGreen = 0
    foreach ($d in $series) {
        $caps = @($captures | Where-Object { $_.date -eq $d.date })
        $g = @($caps | Where-Object { $_.verdict -eq 'GREEN' }).Count
        $rd = @($caps | Where-Object { $_.verdict -eq 'RED' -and $_.deliberate_red }).Count
        $rf = @($caps | Where-Object { $_.verdict -eq 'RED' -and -not $_.deliberate_red }).Count
        $u = @($caps | Where-Object { $_.verdict -eq 'UNKNOWN' -or $_.verdict -eq 'UNREADABLE' }).Count
        if ($caps.Count -gt 0) { $daysWithCapture++ }
        if ($g -gt 0) { $daysWithGreen++; $greenCommits += $d.commits }
        Write-Host ("  {0}  {1,7}  {2,5}  {3,9}  {4,9}  {5,7}" -f $d.date, $d.commits, $g, $rd, $rf, $u)
    }
    Write-Host ("  commits/day: min {0}, max {1}, mean {2}  (n = {3} active day(s))" -f `
        (($series | Measure-Object -Property commits -Minimum).Minimum),
        (($series | Measure-Object -Property commits -Maximum).Maximum),
        [Math]::Round((($series | Measure-Object -Property commits -Average).Average), 2),
        $series.Count)
    Write-Host ("  KPI (c): gate-green merged changes per day = {0} commits/day over the {1} day(s) that have a GREEN capture" -f `
        $(if ($daysWithGreen -gt 0) { [Math]::Round($greenCommits / [double]$daysWithGreen, 2) } else { 'UNDEFINED' }),
        $daysWithGreen)
    Write-Host ("  coverage: {0} of {1} active day(s) have a gate capture; {2} of {1} have a GREEN one" -f `
        $daysWithCapture, $series.Count, $daysWithGreen)
    Write-Host ("  'red(delib)' is a capture whose file name carries -RED: deliberate red-then-green evidence, not a failure.")
    Write-Host ("  'red(fail)' is a capture with no -RED in its name: a real red gate run.")
    Write-Host ("  'unknown' counts captures whose verdict could not be read, including one that is locked for writing")
    Write-Host ("             at the moment of measurement - a gap in the coverage, not a reason to report nothing.")
    Write-Host ("  'gate-green' is evidenced only where a capture exists. The coverage line above")
    Write-Host ("        is part of the measurement, not a footnote to it.")
    Write-Host ""

    Write-Host "--- section 7.4 target table ---"
    Write-Host ("  boot file:                  {0} B          (target: under about 5 KB)" -f $facts.boot_bytes)
    Write-Host ("  live ledger:                {0} B    (target: under about 200 KB)" -f $facts.ledger_bytes)
    Write-Host ("  investigations/:            {0}            (target: not read at boot)" -f $facts.investigations)
    Write-Host ("  pattern namespaces:         {0}            (target: a few dozen, from 513)" -f $facts.pattern_namespaces)
    Write-Host ("  root .md files:             {0}            (AGENTS.md, the rulebook and README are required)" -f $facts.root_md)
    Write-Host ("  DONE rows eligible to rotate: {0}           (threshold: older than 14 days)" -f $facts.rotation_eligible)
    Write-Host ""

    if ($OutFile) {
        $snapshot = [ordered]@{
            measured_utc = (Get-Date).ToUniversalTime().ToString('yyyy-MM-ddTHH:mm:ssZ')
            tool = '.agent/tools/measure-process-tax.ps1'
            tree = $facts.ref
            branch = (git -C $repoRoot branch --show-current)
            definitions = [ordered]@{
                'kpi_a' = 'process documents (REPORTS + PATTERNS + docs + investigations .md files) per DONE ledger row'
                'kpi_a_narrow' = 'as kpi_a, but counting only REPORTS + PATTERNS'
                'kpi_b_bytes' = 'bytes of .agent/00_BOOT.md (the cost of one boot read)'
                'kpi_b_lines' = 'lines of .agent/00_BOOT.md'
                'kpi_b_tokens_est' = 'boot bytes / 4 - an ESTIMATE; the tokeniser is unknown'
                'kpi_c' = 'commits per day on the working branch, counted over the days that have at least one GREEN gate capture; the coverage is reported with it'
                'gate_green_rule' = 'a capture is RED if it reports a failed class, a failed tooling probe, or an ERROR line; GREEN if it reports Verification passed and no red signal; UNKNOWN otherwise'
                'deliberate_red' = 'a capture whose file name carries -RED is deliberate red-then-green evidence, not a failure; it is counted separately from a red capture with no -RED in its name'
                'process_document' = 'a Markdown file under .agent/REPORTS/, .agent/PATTERNS/, docs/ or investigations/'
            }
            counts = [ordered]@{
                reports = $facts.reports
                patterns = $facts.patterns
                docs = $facts.docs
                investigations = $facts.investigations
                process_documents = $facts.process_docs
                narrow_documents = $facts.narrow_docs
                done_rows = $facts.done_rows
                root_md = $facts.root_md
                pattern_namespaces = $facts.pattern_namespaces
                ledger_bytes = $facts.ledger_bytes
            }
            kpi_a = $facts.kpi_a
            kpi_a_narrow = $facts.kpi_a_narrow
            kpi_b_bytes = $facts.kpi_b_bytes
            kpi_b_lines = $facts.kpi_b_lines
            kpi_b_tokens_est = $facts.kpi_b_tokens_est
            commits_per_day = @($series | ForEach-Object { [ordered]@{ date = $_.date; commits = $_.commits } })
            days_with_gate_capture = $daysWithCapture
            days_with_green_capture = $daysWithGreen
            kpi_c_gate_green_commits_per_day = $(if ($daysWithGreen -gt 0) { [Math]::Round($greenCommits / [double]$daysWithGreen, 2) } else { $null })
            gate_captures = @($captures | ForEach-Object {
                [ordered]@{ file = $_.file; date = $_.date; verdict = $_.verdict; deliberate_red = $_.deliberate_red }
            })
        }
        $dir = Split-Path -Parent $OutFile
        if ($dir -and -not (Test-Path $dir)) { New-Item -ItemType Directory -Path $dir | Out-Null }
        $json = $snapshot | ConvertTo-Json -Depth 8
        [System.IO.File]::WriteAllText($OutFile, $json, (New-Object System.Text.UTF8Encoding($false)))
        Write-Host ("snapshot written: " + $OutFile)
    }
    exit 0
} catch {
    # Write-Error under $ErrorActionPreference='Stop' would throw again and
    # exit 1, which is indistinguishable from a measurement that was taken and
    # found nothing. The instrument's own failure code is 2.
    [Console]::Error.WriteLine("the measurement could not be taken: " + $_.Exception.Message)
    [Console]::Error.WriteLine("Nothing was measured; exit 2 is a false pass, never a clean run.")
    exit 2
}
