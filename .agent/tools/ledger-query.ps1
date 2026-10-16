<#
.SYNOPSIS
  Read-only ledger census + claim-liveness query tool (B5-0609).
.DESCRIPTION
  Prints one line per row of .agent/TASK_LEDGER.md matching an optional status
  filter (default OPEN) as: ID | status | pipeCount | doubleLead | claimOwner |
  claimAgeMin | hbAgeMin | reportAgeMin | verdict [reason] | defectReport

  Hard rules (B5-0609 spec):
    * Parses ONLY structured row-start fields (^|B5-xxxx|status|). Task IDs that
      appear inside narrative cells (e.g. heartbeat last_completed prose) are
      never treated as rows or claims.
    * The agent_id -> heartbeat filename join normalises punctuation on BOTH
      sides (colons, U+2028/U+2029, spaces, dashes, underscores stripped) so
      "solar-pro4:free" matches the heartbeat file "solar-pro4<U+2028>free.json".
    * THREE-SIGNAL LIVENESS (B5-0659, human-approved 2026-09-27), implementing the
      rule already binding in .agent/HEARTBEATS/README.md section "Liveness: three
      signals, never one":
        live(T) <=> newest of ( claim started_utc, or claim file mtime when
                             unparseable
                           , owner heartbeat file mtime
                           , report file mtime ) is within the TTL
      Verdicts: LIVE = at least one signal found and within TTL; STALE = at least
      one found and none within TTL; UNKNOWN = a required signal absent or
      unparseable. A reaper may remove a claim only when NO signal is fresh.
      The owner heartbeat is REQUIRED, so its absence is UNKNOWN with a reason and
      is never read as freshness. The report signal is CONTRIBUTING-ONLY: a report
      is written at close-out, so its ordinary absence before then must not
      manufacture an UNKNOWN on every in-flight task, and a report that does exist
      legitimately refreshes the task. An UNCLAIMED row is neither LIVE nor STALE
      and carries no signal at all.
    * CLAIMS-FIRST (B5-0657, human-approved 2026-09-27): reads .agent/CLAIMS/
      before reporting and marks any row whose claim is not provably stale as
      `suppressed-live-claim` in the defectReport column, with a footer naming
      the suppressed rows. A row under repair reads as a transient state that
      belongs to a DIFFERENT defect class than its committed form, so reporting
      it manufactures false defects. The suppressed rows must be re-censused
      after the claim releases.
    * Strictly read-only: no ledger, claim, heartbeat, report or pattern writes.
.NOTES
  Manual fallback one-liner (capture-only rg, tolerates leading double pipe):
    rg -o --no-filename '^\|+\s*B5-[0-9]{4}[a-z]?\s*\|' .agent/TASK_LEDGER.md
.PARAMETER Status
  Filter on the status cell (default "OPEN"). Use "*", "" or "ALL" for all rows.
  (B5-1010: ALL is a wildcard alias, not a literal status. Before this fix, ALL
  fell through to a literal cell match, matched nothing, and exited 0 — an empty
  result indistinguishable from a clean ledger, the worst failure mode for a
  census instrument. An unrecognised value now fails loudly with exit 3 instead
  of printing an empty table, keeping the B5-0777 principle: a census that could
  not do its job must never report as a clean one. Exit 3 is deliberately
  distinct from 0/1/2: 3 = could-not-interpret, never matched-nothing.)

  B5-1017: the verdict cell is now LABELLED so no consumer can mistake a
  reported age for an adjudicated one. The two liveness tools hold different,
  each-defensible designs — this query REPORTS what the files say (a
  forged-future started_utc prints its age, negative), while run-queue APPLIES
  policy (refuses to offer on it). A claim more than 60 minutes ahead of the
  sampled UTC clock is now UNKNOWN rather than LIVE; smaller skew remains
  tolerated. What made that difference a defect was that
  nothing on the page said so: a plain "LIVE" could mean "adjudicated live" or
  "the arithmetic of an unverified timestamp". LIVE/STALE verdicts now carry a
  [reported-age] label exactly when the claim's started_utc was accepted
  unverified; UNSTEALABLE-policy verdicts (absent heartbeat, future-dated) are
  printed with their policy reason as before. The crosscheck's Get-Word still
  reads the leading token, so its comparisons are unchanged..PARAMETER TtlMinutes
    Liveness window (default 30, per 00_BOOT step 2).

B5-1020 (2026-09-30): content-exempt pipe classification. A non-7-pipe row whose
excess pipes fall strictly inside a double-quoted span, a backtick span, or a row
carrying an explicit in-row prior adjudication marker (the literal phrase
"left intact", the convention the adjudicated rows themselves use) is reported as
`exempt (<classes>)` in the defectReport column instead of `reportable`.

  * The exempt class exists ONLY for pipes inside quoted or adjudicated spans.
    A new free pipe in plain unquoted prose is STILL A DEFECT — there is no
    operator, parenthetical, or bare-pipe exemption, because a detector that
    exempts by row instead of by position would pass every other test.
  * Span classes apply only when the span delimiters are PAIRED (even count on
    the line); single-quote spans are deliberately NOT a class (apostrophes in
    prose make them unsafe), and the classifier requires
    pipeCount - exemptPipes == 7 EXACTLY — any leftover or over-strip stays
    `reportable`. DoubleLead rows are never exempt: the lead-pipe repair duty is
    independent of the count.
  * Conservative toward reporting MORE: every ambiguity lands on `reportable`,
    never on `exempt`. No exit code, threshold, TTL, verdict, or the 7-pipe
    contract is changed by this classification.
#>
param(
    [string]$Status = "OPEN",
    [int]$TtlMinutes = 30
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$ledger = Join-Path $repoRoot ".agent/TASK_LEDGER.md"
$claimsDir = Join-Path $repoRoot ".agent/CLAIMS"
$hbDir = Join-Path $repoRoot ".agent/HEARTBEATS"
$reportsDir = Join-Path $repoRoot ".agent/REPORTS"
$now = [DateTime]::UtcNow
$FutureTimestampToleranceMinutes = 60

# B5-3490: bounded-decay threshold (B5-2475, adopted B5-3489). A heartbeat-absent
# claim becomes decay-reapable once BOTH its started_utc and its file mtime are
# older than this many minutes AND the other conjunctive conditions hold
# (plausible timestamp, every heartbeat-lookup tier empty, no report in the
# window, non-terminal row). Fixed at 60 minutes (2xTTL) by the in-session
# threshold ruling -- deliberately NOT derived from -TtlMinutes, so a custom TTL
# never silently retunes the decay window.
$DecayMinutes = 60

if (-not (Test-Path -LiteralPath $ledger)) { Write-Error "ledger not found: $ledger"; exit 2 }

# --- row parsing: structured row-start cells only ---
$rowRegex = [regex]'^(\|+)\s*(B5-[0-9]{4}[a-z]?)\s*\|([^|]*)'
# B5-1010: wildcard handling. ALL (case-insensitive) joins "*" and "" as the
# all-rows wildcard, ending the ALL-vs-* divergence two sessions observed and
# deferred (B5-0998, 2026-09-29 verification pass). Any other value is matched
# LITERALLY against the status cell — but only after being checked against the
# vocabulary the ledger header and the live data actually use (OPEN, CLAIMED,
# DONE, BLOCKED, VOID, SUPERSEDED). An unrecognised value exits 3 BEFORE any
# table is printed, because an empty result must never be the tool's way of
# saying "could not interpret": a census that silently returns nothing is
# indistinguishable from a clean ledger. The vocabulary is stated, not inferred
# at runtime, so a future status value added to the ledger without updating
# this list fails LOUD here — which is the correct direction for a gate.
$knownStatuses = @("OPEN", "CLAIMED", "DONE", "BLOCKED", "VOID", "SUPERSEDED")
# B5-1720: -Status now accepts a comma-separated LIST of vocabulary statuses
# in addition to a single status or a wildcard (star, empty, ALL), which is
# all it accepted before. Every comma element is trimmed and matched
# case-insensitively; ALL elements must be recognised or the tool exits 3
# BEFORE any table is printed, because an empty result must never be the
# tool's way of saying "could not interpret" (the B5-1010 rule, unchanged).
# QUEUE is deliberately still refused: QUEUE notes are narrative ledger
# paragraphs, not row statuses, so a QUEUE filter is a category error the
# vocabulary gate exists to catch. A list whose every element is recognised
# yields $statusFilter as the recognised subset, and the row filter below
# switches from single-value equality to list membership.
$statusFilter = $null
$trimmedStatusInput = ([string]$Status).Trim()
if ($trimmedStatusInput -eq "*" -or $trimmedStatusInput -eq "") {
    $statusFilter = $null
} elseif ($trimmedStatusInput -ieq "ALL") {
    $statusFilter = $null
} else {
    $requested = @($trimmedStatusInput -split "," | ForEach-Object { $_.Trim() } | Where-Object { $_ -ne "" })
    $unrecognised = @($requested | Where-Object { $knownStatuses -inotcontains $_ })
    if ($requested.Count -eq 0 -or $unrecognised.Count -gt 0) {
        Write-Output ("UNRECOGNISED STATUS FILTER '{0}': not a wildcard (star, empty, ALL) and every comma-separated element must be in the ledger status vocabulary [{1}]. Exiting 3 rather than printing an empty table, because an empty result must never mean could-not-interpret (B5-1010)." -f $Status, ($knownStatuses -join ", "))
        exit 3
    }
    $statusFilter = @($knownStatuses | Where-Object { $requested -icontains $_ })
}

$rows = @()
# B5-1002: encoding pinned to explicit UTF-8. The parameterless ReadAllLines
# overload defaults to UTF-8 but names nothing, and a count whose instrument is
# unnamed cannot be reproduced -- a naive Get-Content read of docs/DECISIONS.md
# reports 1600 C1 marks where this read reports 0 on the same bytes.
foreach ($line in [System.IO.File]::ReadAllLines($ledger, [System.Text.Encoding]::UTF8)) {
    $m = $rowRegex.Match($line)
    if (-not $m.Success) { continue }
    $leading = $m.Groups[1].Value
    $id = $m.Groups[2].Value.Trim()
    $st = $m.Groups[3].Value.Trim()
    if ($statusFilter -and -not ($statusFilter -icontains $st)) { continue }
    $pipeCount = ([regex]::Matches($line, '\|')).Count
    $isDouble = ($leading.Length -gt 1)

    # B5-1020: content-exempt classification. See the header notes: quoted spans,
    # backtick spans, and the explicit in-row prior-adjudication marker are the
    # only classes; the exact-7 arithmetic backstop keeps every ambiguous row on
    # `reportable`, and doubleLead rows are never exempt.
    $exempt = $null
    if ($pipeCount -gt 7 -and -not $isDouble) {
        if ($line -match 'left intact') {
            $exempt = "exempt (in-row prior adjudication marker)"
        } else {
            $n = $line.Length
            $exemptSet = New-Object 'System.Collections.Generic.HashSet[int]'
            $btCount = 0; $dqCount = 0
            $btTotal = ([regex]::Matches($line, [regex]::Escape('`'))).Count
            if ($btTotal -ge 2 -and ($btTotal % 2 -eq 0)) {
                $inB = $false
                for ($i = 0; $i -lt $n; $i++) {
                    if ($line[$i] -eq '`') { $inB = -not $inB; continue }
                    if ($inB -and $line[$i] -eq '|') { [void]$exemptSet.Add($i); $btCount++ }
                }
            }
            $dqTotal = ([regex]::Matches($line, '"')).Count
            if ($dqTotal -ge 2 -and ($dqTotal % 2 -eq 0)) {
                $inQ = $false
                for ($i = 0; $i -lt $n; $i++) {
                    if ($line[$i] -eq '"') { $inQ = -not $inQ; continue }
                    if ($inQ -and $line[$i] -eq '|') { [void]$exemptSet.Add($i); $dqCount++ }
                }
            }
            if ($exemptSet.Count -gt 0 -and (($pipeCount - $exemptSet.Count) -eq 7)) {
                $classes = @()
                if ($btCount -gt 0) { $classes += 'backtick span' }
                if ($dqCount -gt 0) { $classes += 'double-quoted span' }
                if ($classes.Count -gt 0) { $exempt = "exempt (" + ($classes -join '; ') + ")" }
            }
        }
    }

    $rows += [pscustomobject]@{
        Id        = $id
        Status    = $st
        Pipes     = $pipeCount
        Double    = $isDouble
        Exempt    = $exempt
    }
}

# --- claim + heartbeat indexing (once) ---
function Get-NormName([string]$name) {
    # strip colons, U+2028/U+2029, spaces, dashes, underscores, dots; lowercase
    $sb = New-Object System.Text.StringBuilder
    foreach ($ch in $name.ToCharArray()) {
        $c = [int]$ch
        if ($c -eq 0x2028 -or $c -eq 0x2029) { continue }
        if ($ch -match '[\p{L}\p{N}]') { [void]$sb.Append($ch.ToString().ToLowerInvariant()) }
    }
    return $sb.ToString()
}

$claims = @{}
if (Test-Path -LiteralPath $claimsDir) {
    Get-ChildItem -LiteralPath $claimsDir -Filter "B5-*.json" | ForEach-Object {
        # Bind the file item ONCE. Inside a catch block `$_` is rebound to the
        # ErrorRecord, so `$_ .BaseName` there is null and the hashtable index
        # throws "the array index evaluated to null". The pre-B5-0659 code got away
        # with it only because its catch body was empty.
        $cf = $_
        try {
            $c = Get-Content -Raw -Encoding UTF8 -LiteralPath $cf.FullName | ConvertFrom-Json
            $started = $null
            $parsedOk = $false
            if ($c.started_utc) {
                try { $started = [DateTime]::Parse($c.started_utc).ToUniversalTime(); $parsedOk = $true } catch { $started = $null; $parsedOk = $false }
            }
            if (-not $started) { $started = $cf.LastWriteTimeUtc }
            $futureSkew = (($started - $now).TotalMinutes -gt $FutureTimestampToleranceMinutes)
            # B5-3490: condition-2 plausibility for the bounded-decay rule
            # (HEARTBEATS/README, adopted B5-3489): the timestamp must be a real
            # reading -- parsed (not the mtime fallback), non-future,
            # non-midnight, post-epoch -- mirroring the B5-0653/B5-0952 guards in
            # run-queue.ps1 so both tools share one plausibility definition.
            $unixEpoch = [DateTime]::new(1970, 1, 1, 0, 0, 0, [DateTimeKind]::Utc)
            $isMidnight = ($parsedOk -and $started.Hour -eq 0 -and $started.Minute -eq 0 -and $started.Second -eq 0)
            $plausible = ($parsedOk -and -not $futureSkew -and -not $isMidnight -and ($started -gt $unixEpoch))
            $claims[$cf.BaseName] = [pscustomobject]@{
                Owner   = $c.agent_id
                Started = $started
                FileT   = $cf.LastWriteTimeUtc
                Bad     = $false
                FutureSkew = $futureSkew
                Plausible = $plausible
            }
        } catch {
            # An UNPARSEABLE claim is UNKNOWN, not UNCLAIMED (B5-0659). Dropping it
            # from the index the way this used to made a corrupt claim file read as
            # "no claim at all", which is the exact opposite of the truth: a claim
            # EXISTS, this tool simply cannot read it. The file's own mtime is still
            # a usable signal, because the filesystem maintains it without the
            # writer's cooperation, so record it and let the verdict say UNKNOWN.
            $claims[$cf.BaseName] = [pscustomobject]@{
                Owner   = $null
                Started = $null
                FileT   = $cf.LastWriteTimeUtc
                Bad     = $true
            }
        }
    }
}

$heartbeats = @{}
if (Test-Path -LiteralPath $hbDir) {
    Get-ChildItem -LiteralPath $hbDir -Filter "*.json" | ForEach-Object {
        $key = Get-NormName $_.BaseName
        if (-not $heartbeats.ContainsKey($key) -or $_.LastWriteTimeUtc -gt $heartbeats[$key]) {
            $heartbeats[$key] = $_.LastWriteTimeUtc
        }
    }
}

# Report-mtime index: task id -> newest report mtime (B5-0659, third signal).
# Reports are named <date>-<agent-id>-<task-id>.md and may carry suffixes such as
# -WITHDRAWN, so the match is a SUBSTRING test on the stem rather than a suffix
# test. A task with no report yet contributes nothing, which is correct: the report
# is written at close-out, so its absence mid-task is the normal case.
$reports = @{}
if (Test-Path -LiteralPath $reportsDir) {
    Get-ChildItem -LiteralPath $reportsDir -Filter "*.md" -ErrorAction SilentlyContinue | ForEach-Object {
        $stem = $_.BaseName
        foreach ($m in [regex]::Matches($stem, 'B5-[0-9]{4}[a-z]?')) {
            $rid = $m.Value
            if (-not $reports.ContainsKey($rid) -or $_.LastWriteTimeUtc -gt $reports[$rid]) {
                $reports[$rid] = $_.LastWriteTimeUtc
            }
        }
    }
}

# --- claims-first census suppression (B5-0657) ---
function Get-CensusSuppression {
    # Live-repair-aware ledger census protocol, human-approved 2026-09-27
    # (docs/proposals/live-repair-aware-ledger-census-protocol.md). Returns
    # taskId -> owner for every claim that is NOT provably stale, so the census
    # never reports a row that another agent is mid-repair on as a defect.
    #
    # Grounding: a census taken while a live claim covers the row reads a
    # TRANSIENT state, and the transient state belongs to a different defect
    # class than the committed form. B5-0564/B5-0565 both read 6 pipes mid-repair
    # under claim B5-0592, which looks like a missing-trailing-delimiter defect;
    # git show d8216afa confirms the committed form was 8 pipes with a leading
    # double pipe. A naive pipe census reports two false defects.
    #
    # Keyed off the FILENAME, before JSON parsing, so a claim file this tool cannot
    # read is ALSO suppressed. An unreadable file is not evidence of a defect, and
    # the fail-safe direction here is toward silence: the protocol's own remedy for
    # a suppressed row is re-census after release, not a report.
    #
    # "Provably stale" means every DETERMINABLE signal is older than the TTL. An
    # absent owner heartbeat is absent information and is never read as staleness,
    # which is the B5-0609 defect class (a lookup matching nothing returned -1, and
    # -1 compares as younger than any TTL, manufacturing LIVE from an absent signal).
    #
    # B5-0659: the three-signal rule from .agent/HEARTBEATS/README.md, so the report
    # mtime participates too. The comment above already promised that an absent
    # heartbeat is not staleness, but the code did the opposite -- the heartbeat was
    # folded in only `if ($heartbeats.ContainsKey($k))`, so a claim whose owner has
    # no heartbeat file fell through to claim age alone and could be reported
    # reportable on the strength of the one signal that was never in dispute. Now a
    # claim with no matching heartbeat is NOT provably stale and is suppressed, which
    # is the fail-safe direction the protocol already states: an unreadable signal is
    # not evidence of a defect, and the remedy for a suppressed row is re-census
    # after release, not a report.
    #
    # The verdict predicate is deliberately IDENTICAL to Test-LiveClaim in
    # .agent/run-queue.ps1. Two tools disagreeing about which rows are reportable
    # would recreate exactly the defect class this protocol exists to stop, and
    # .agent/tools/census-crosscheck.ps1 is the shipped instrument for proving they
    # agree. B5-0953: run-queue.ps1 now answers UNKNOWN and suppresses on a
    # missing or unjoinable owner heartbeat exactly as this copy always has,
    # so the B5-0649 claim-age fallback is retired and the B5-0660 seed that
    # once tracked the divergence is closed by convergence.
    $sup = @{}
    if (-not (Test-Path -LiteralPath $claimsDir)) { return $sup }
    foreach ($f in (Get-ChildItem -LiteralPath $claimsDir -Filter "B5-*.json")) {
        $id = $f.BaseName
        $owner = $null
        $started = $null
        try {
            $c = Get-Content -Raw -Encoding UTF8 -LiteralPath $f.FullName | ConvertFrom-Json
            $owner = $c.agent_id
            $ttl = $TtlMinutes
            if ($c.ttl_min) { $ttl = [int]$c.ttl_min }   # per-claim TTL wins, as in run-queue
            if ($c.started_utc) {
                try { $started = [DateTime]::Parse($c.started_utc).ToUniversalTime() } catch { $started = $null }
            }
        } catch {
            $sup[$id] = "<unreadable claim file>"
            continue
        }
        if (-not $started) { $started = $f.LastWriteTimeUtc }
        if ($started -gt $now) { $sup[$id] = $owner; continue }   # future-dated clock skew: not a defect
        if (-not $owner) { $sup[$id] = "<unreadable agent_id>"; continue }  # cannot join to a heartbeat
        $k = Get-NormName $owner
        if (-not $heartbeats.ContainsKey($k)) { $sup[$id] = $owner; continue }  # required signal absent: not provably stale
        $newest = $started
        if ($heartbeats[$k] -gt $newest) { $newest = $heartbeats[$k] }
        if ($reports.ContainsKey($id) -and $reports[$id] -gt $newest) { $newest = $reports[$id] }
        if (($now - $newest).TotalMinutes -lt $ttl) { $sup[$id] = $owner }
    }
    return $sup
}

function Test-HeartbeatAbsentEveryTier {
    # B5-3490: condition-3 lookup across all three heartbeat-resolution tiers
    # named by the adopted rule (HEARTBEATS/README): exact filename stem,
    # _registry.json mapping, canonical transliteration. $true means absent
    # everywhere. An unreadable owner or registry proves nothing, so both fall
    # through toward "not provably absent" rather than toward absence.
    param([string]$Owner)
    if (-not $Owner) { return $false }
    if (Test-Path -LiteralPath (Join-Path $hbDir ($Owner + ".json"))) { return $false }
    $regPath = Join-Path $hbDir "_registry.json"
    if (Test-Path -LiteralPath $regPath) {
        try {
            $reg = Get-Content -Raw -Encoding UTF8 -LiteralPath $regPath | ConvertFrom-Json
            if ($reg.map -and $reg.map.PSObject.Properties[$Owner]) {
                $mapped = [string]$reg.map.PSObject.Properties[$Owner].Value
                if ($mapped -and (Test-Path -LiteralPath (Join-Path $hbDir $mapped))) { return $false }
            }
        } catch { }
    }
    if ($heartbeats.ContainsKey((Get-NormName $Owner))) { return $false }
    return $true
}

function Get-DecayEvidence {
    # B5-3490: bounded-decay eligibility (B5-2475 conditions, adopted B5-3489).
    # Returns an evidence string when ALL FIVE conjunctive conditions hold for a
    # heartbeat-absent claim, else $null. The verdict stays UNKNOWN either way --
    # this only distinguishes "decay-reapable, reap with the forensic note" from
    # "still protected", so a reaper (00_BOOT step 10) can act without
    # re-deriving the rule, while run-queue.ps1 Test-LiveClaim keeps answering
    # unstealable for both (offer behavior unchanged).
    param($Claim, [string]$TaskId, [string]$RowStatus)
    # Condition 5 first (cheapest): the row must be OPEN or CLAIMED. A DONE/VOID
    # row with a lingering claim is a B5-0622 orphan release, never a decay reap.
    if (@('OPEN','CLAIMED') -notcontains $RowStatus) { return $null }
    # Condition 2: a plausible timestamp (computed once at index time so every
    # consumer shares one plausibility definition).
    if (-not $Claim.Plausible) { return $null }
    # Condition 1: BOTH the stamped time and the file mtime older than 60 min.
    $claimAgeMin = ($now - $Claim.Started).TotalMinutes
    $fileAgeMin = ($now - $Claim.FileT).TotalMinutes
    if (-not ($claimAgeMin -gt $DecayMinutes -and $fileAgeMin -gt $DecayMinutes)) { return $null }
    # Condition 4: no task report with mtime inside the last 60 minutes.
    if ($reports.ContainsKey($TaskId) -and (($now - $reports[$TaskId]).TotalMinutes -lt $DecayMinutes)) { return $null }
    # Condition 3: the owner heartbeat resolves by NONE of the three tiers.
    if (-not (Test-HeartbeatAbsentEveryTier -Owner ([string]$Claim.Owner))) { return $null }
    return ("claim age " + [math]::Round($claimAgeMin,1) + " min and file age " + [math]::Round($fileAgeMin,1) + " min both over " + $DecayMinutes + ", owner heartbeat ABSENT at all 3 tiers (exact stem, _registry.json, transliteration), no report in " + $DecayMinutes + " min, row " + $RowStatus + ", timestamp plausible")
}

$suppressed = Get-CensusSuppression

# --- per-row verdict ---
$out = @()
foreach ($r in $rows) {
    $claimOrNull = $null
    if ($claims.ContainsKey($r.Id)) { $claimOrNull = $claims[$r.Id] }

    $owner = "-"; $claimAge = "-"; $hbAge = "-"; $reportAge = "-"; $verdict = "-"; $reason = ""; $claimAgeNote = $null
    if ($claimOrNull) {
        # B5-4207: the report is the THIRD of the three liveness signals, and
        # until this pass it was read only inside the heartbeat-present branch
        # below. So on precisely the rows whose verdict is UNKNOWN -- owner
        # heartbeat unresolvable -- it was never consulted at all: the column
        # printed "-" while a report for that very task sat on disk 13.9 days
        # stale (measured while reaping orphan claims B5-1405 and B5-1475). The
        # third signal was UNCONSULTED exactly where the reader most needs it,
        # because that is the case a report on disk would resolve.
        #
        # This hoists the lookup so it is unconditional for every claim row. It
        # is a COLUMN, not a verdict: nothing below folds it into the
        # heartbeat-absent UNKNOWN, whose verdict and reason text stay driven by
        # the heartbeat signal exactly as before -- the two signals answer
        # different questions, and a fresh report must never make a claim LIVE
        # on its own. Contributing-only semantics are unchanged: an absent report
        # leaves the column at "-", which is absence and not staleness. The one
        # place a report legitimately refreshes a task, the heartbeat-present
        # newest-signal minimum (B5-0659), still folds it below, unchanged.
        if ($reports.ContainsKey($r.Id)) {
            $reportAge = [math]::Round(($now - $reports[$r.Id]).TotalMinutes, 1)
        }
        if ($claimOrNull.Bad) {
            $claimAge = [math]::Round(($now - $claimOrNull.FileT).TotalMinutes, 1)
            $verdict = "UNKNOWN"
            $reason = "claim file present but unparseable; its mtime is still a usable signal, an unreadable signal is never LIVE"
        } else {
            # B5-1017: record HOW the started_utc was accepted. A parseable,
            # plausible timestamp is still an UNVERIFIED claim by the file's own
            # author; a future-dated one is the known forgery/skew class. The
            # three-signal NEWEST arithmetic below is identical either way — what
            # changes is the label on the printed verdict, so a reader can tell
            # "adjudicated from live signals" from "arithmetic over an unverified
            # timestamp". A future skew beyond the shared tolerance is UNKNOWN,
            # never LIVE; smaller skew is labelled and compared.
            if ($claimOrNull.Started -gt $now) { $claimAgeNote = "reported-age:started_utc-is-future-dated" }
            else { $claimAgeNote = "reported-age:started_utc-unverified" }
            $owner = $claimOrNull.Owner
        if ($claimOrNull.FutureSkew) {
            $claimAge = [math]::Round(($now - $claimOrNull.Started).TotalMinutes, 1)
            $verdict = "UNKNOWN"
            $reason = "started_utc is " + [math]::Round(($claimOrNull.Started - $now).TotalMinutes, 1) + " minutes ahead of sampled UTC; exceeds " + $FutureTimestampToleranceMinutes + "-minute tolerance and is never LIVE"
        } elseif ($owner) {
            $claimAge = [math]::Round(($now - $claimOrNull.Started).TotalMinutes, 1)
            $normOwner = Get-NormName $owner
            if (-not $heartbeats.ContainsKey($normOwner)) {
                $verdict = "UNKNOWN"
                $decayEvidence = Get-DecayEvidence -Claim $claimOrNull -TaskId $r.Id -RowStatus $r.Status
                if ($decayEvidence) {
                    $reason = "decay-reapable per bounded-decay rule (B5-3489): " + $decayEvidence + "; reap only with the mandatory forensic note, row stays OPEN"
                } else {
                    $reason = "no heartbeat file matching owner '$owner' (normalised '$normOwner'); absent signal never treated as live"
                }
            } else {
                $hf = $heartbeats[$normOwner]
                $hbAge = [math]::Round(($now - $hf).TotalMinutes, 1)
                # Newest of the signals that were FOUND. The heartbeat is required,
                # so at this point it is present; the report is contributing-only, so
                # its absence simply does not join the max (B5-0659).
                $newestMin = [math]::Min([double]$claimAge, [double]$hbAge)
                if ($reports.ContainsKey($r.Id)) { $newestMin = [math]::Min($newestMin, [double]$reportAge) }
                $verdict = if ($newestMin -lt $TtlMinutes) { "LIVE" } else { "STALE" }
                if ($claimAgeNote) { $reason = $claimAgeNote }   # B5-1017 label, never changes the arithmetic
            }
        } else {
            $verdict = "UNKNOWN"
            $reason = "claim file present but agent_id unreadable"
        }
        }
    } else {
        $verdict = "UNCLAIMED"
    }

    # Claims-first census verdict. Deliberately ADDITIVE and independent of the
    # liveness verdict printed above: that verdict is a per-claim liveness report and
    # this column is a per-row DEFECT-REPORTABILITY report. They answer different
    # questions, and keeping them separate means the suppression rule cannot perturb
    # the liveness verdicts the shipped crosscheck already diffs.
    # B5-1020: claims-first suppression keeps precedence over the exempt class —
    # a row mid-repair reads as a transient state and is re-censused after the
    # claim releases, whatever its committed classification will be.
    $defect = if ($suppressed.ContainsKey($r.Id)) { "suppressed-live-claim" }
              elseif ($r.Exempt) { $r.Exempt }
              else { "reportable" }

    # Compose the verdict cell once so an absent reason leaves no double space. The
    # rendered text is unchanged from the B5-0609 form ("LIVE [reason]"); only the
    # padding differs, and a census whose columns drift apart is a census two
    # readers stop agreeing on.
    $verdictCell = $verdict
    if ($reason) { $verdictCell = $verdict + " [" + $reason + "]" }

    $out += ("{0} | {1} | {2} | {3} | {4} | {5} | {6} | {7} | {8} | {9}" -f `
        $r.Id, $r.Status, $r.Pipes, $(if ($r.Double) { "yes" } else { "no" }),
        $owner, $claimAge, $hbAge, $reportAge, $verdictCell, $defect)
}

if ($out.Count -eq 0) { Write-Output "No rows match status filter '$Status'."; exit 0 }
$out | Sort-Object | ForEach-Object { $_.TrimEnd() }
Write-Output ("-- {0} row(s) matching status '{1}' --" -f $out.Count, $Status)

# Disclosure footer (B5-0657). The protocol requires that a census either apply
# claims-first suppression or SAY that it did not. This tool applies it, so the
# footer names what was suppressed and states the duty that discharges it: re-census
# a suppressed row after its claim is released, before calling any defect on it.
$supInView = @($out | Where-Object { $_ -match '\|\s*suppressed-live-claim\s*$' })
if ($supInView.Count -gt 0) {
    $ids = @()
    foreach ($line in $supInView) { $ids += ($line -split '\|')[0].Trim() }
    Write-Output ("-- CLAIMS-FIRST: " + $supInView.Count + " row(s) under a non-stale claim, NOT a defect report: " + ($ids -join ', ') + ". Re-census these rows after the claim is released before reporting any defect on them (live-repair-aware-ledger-census-protocol.md). --")
} else {
    Write-Output "-- CLAIMS-FIRST: 0 rows under a non-stale claim; every row above is reportable. --"
}
# B5-1020 exemption receipt: the exempt class exists only for pipes inside
# quoted or adjudicated spans; a new free pipe in plain unquoted prose is still
# a defect. Named here so the class cannot silently widen. The reportable count
# excludes the exempt rows, so the two numbers cannot be misread as disjoint
# halves of one set.
$exemptInView = @($out | Where-Object { $_ -match '\| exempt \(' })
$nonSevenReportable = @()
foreach ($line in $out) {
    if ($line -match '\| exempt \(') { continue }
    $cells = $line -split ' \| '
    if ([int]$cells[2] -ne 7) { $nonSevenReportable += $line }
}
Write-Output ("-- B5-1020: " + $exemptInView.Count + " row(s) content-exempt; " + $nonSevenReportable.Count + " further row(s) at a non-7 pipeCount remain reportable. A new free pipe in plain unquoted prose is still a defect. --")

# B5-1002: encoding receipt. A census is a verdict with a receipt, and an
# unnamed instrument cannot be reproduced, so this tool names its own: every
# text read above is explicit UTF-8 (ledger via
# [IO.File]::ReadAllLines(path, UTF8), claims via Get-Content -Encoding UTF8).
Write-Output "-- ENCODING: UTF-8 (explicit) --"