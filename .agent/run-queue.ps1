<#
.SYNOPSIS
  Queue runner for the Babylon 5 CCG autonomous-agent pipeline.

.DESCRIPTION
  Repeatedly invokes an agent CLI, ONE ledger task per invocation, until the
  queue is drained or progress stalls. Fresh process per task: a CLI crash
  only ever loses one task's in-memory work, and long-context bloat never
  accumulates. All coordination stays file-based (TASK_LEDGER / CLAIMS /
  HEARTBEATS), so resumed iterations lose nothing.

  Stop conditions (checked after every iteration):
    a) No OPEN task without a live claim remains -> "queue drained".
    b) $BlockedStreakLimit consecutive iterations flip nothing to DONE
       (e.g. every code task BLOCKED on the same red gate) -> stop.
    c) $MaxIterations reached -> stop (overseer re-invokes).

  Opt-in seed-on-drain (B5-0900). Condition (a) is the only state in which
  .agent/AGENT_LOOP.md LOOP step 1 ("if none, seed ONE genuinely useful task")
  can act, and until this branch existed that clause was unreachable: the runner
  only ever invoked an agent WITH a task id, so the procedure assigned a duty to
  a state the runner never produced. Measured, not assumed -- git log -G seed
  over this file returns prose and never a function, and the B5-0626 template
  ends at "Single task, then exit". Two properties are deliberate. The branch is
  OFF by default (-MaxSeedInvocations 0), so unattended behaviour is unchanged
  until a human opts in: whether the fleet may seed itself is a governance
  decision and one parameter flip is the whole of it, reversible in one edit. And
  it fires only on a queue drained for the RIGHT reason -- see Test-SeedableQueue,
  because an empty claimable list also means claimed, gated or BLOCKED, and
  seeding on top of those is the B5-0618 duplicate-wave defect. A seed invocation
  consumes one $MaxIterations iteration and deliberately does NOT touch the (b)
  stagnation counter: it flips no DONE row by construction, so counting it would
  trip condition (b) on the iteration right after a successful seed.

  Claim TTL rule (mirrors .agent/00_BOOT.md step 10 and the B5-0597
  three-signal lesson): a claim is LIVE when the NEWEST of its own
  started_utc and its owner's heartbeat mtime falls inside its ttl_min, and a
  live claim is never stolen. Both signals matter: reading started_utc alone
  declared a working agent free the moment it wrote a placeholder timestamp,
  and disagreed with .agent/tools/ledger-query.ps1 about the same claim.
  B5-0953: the owner heartbeat is a REQUIRED signal, so a claim whose owner
  has no readable heartbeat is UNKNOWN -- never offered and never stealable,
  converging this runner with ledger-query.ps1 and retiring the B5-0649
  claim-age fallback. Small future-dating (clock skew, within
  $FutureTimestampToleranceMinutes) is still treated as live. Stale claims
  are left for the agent to reap per protocol, not reaped here.

  Future-dated claim refusal (B5-0952, the forward complement of the B5-0653
  guard below): a started_utc that parses and sits more than
  $FutureTimestampToleranceMinutes AHEAD of the wall clock makes
  Test-LiveClaim and Get-ImplausibleClaimTaskIds DECLINE TO OFFER the task,
  with a loud warning naming the id, owner, value and measured overshoot. It is
  a data-quality refusal, not a liveness verdict, for the same reason B5-0653
  is one: (now - started) is NEGATIVE for a future-dated claim, a negative age
  compares as younger than any TTL (the B5-0597 failure-3 inversion), and the
  claim therefore reads LIVE until the wall clock catches up rather than aging
  out. Same direction rule as B5-0653 -- a wrongly-refused claim costs one
  blocked task the owner re-claims in seconds, a wrongly-accepted one costs a
  collision and destroyed work -- and the same non-repair rule: a claim file
  belongs to its owner, so it is warned about, never edited. One-sided by
  construction: a claim in the past is not this guard's business.

  Implausible started_utc refusal (B5-0653): a started_utc that parses but
  cannot be a just-now claim - exactly midnight UTC or at/before the epoch -
  makes Test-LiveClaim DECLINE TO OFFER the task with a loud warning naming
  the task id, owner and value. Never auto-repaired (editing another agent's
  claim is forbidden); never silently passed (a silent pass is how the
  original defect hid). Ambiguity resolves toward NOT OFFERING: a wrongly-
  stale live claim costs a collision and destroyed work, a wrongly-implausible
  claim costs one blocked task. Accepted cost: a genuine claim written in the
  exact second of midnight, ~1 in 86400, is declined until re-claimed.

  Claims-first census rule (B5-0657, human-approved 2026-09-27): the three
  structural warnings Get-LedgerRows emits are DEFECT REPORTS, so they are
  suppressed on any row whose claim is not provably stale and re-censused after
  the claim releases. A row mid-repair reads as a transient state belonging to a
  different defect class than its committed form - B5-0564/B5-0565 both read 6
  pipes under live claim B5-0592 while the committed form was 8 pipes with a
  leading double pipe. See Get-CensusSuppression and
  docs/proposals/live-repair-aware-ledger-census-protocol.md.

.EXAMPLE
  # Dry run: show what would be claimed, invoke nothing.
  powershell -NoProfile -ExecutionPolicy Bypass -File .agent/run-queue.ps1 -DryRun

.EXAMPLE
  # Real run, default 10 iterations, custom CLI:
  powershell -NoProfile -ExecutionPolicy Bypass -File .agent/run-queue.ps1 `
    -AgentCli 'freebuff' -AgentArgs '--yes'
#>
param(
  [int]$MaxIterations = 10,
  [int]$BlockedStreakLimit = 3,
  [string]$AgentCli = 'freebuff',
  [string]$AgentArgs = '',
  [int]$CooldownSeconds = 5,
  [int]$MaxSeedInvocations = 0,
  [switch]$DryRun
)

$ErrorActionPreference = 'Stop'
$AgentDir  = $PSScriptRoot
$RepoRoot  = Split-Path -Parent $AgentDir
$Ledger    = Join-Path $AgentDir 'TASK_LEDGER.md'
$ClaimsDir = Join-Path $AgentDir 'CLAIMS'
$hbDir     = Join-Path $AgentDir 'HEARTBEATS'

$LedgerStatuses = @('OPEN','CLAIMED','DONE','BLOCKED','SUPERSEDED','VOID')

# B5-0981: statuses on which a row can never be offered, so an offer-path
# warning about such a row is noise rather than a report. 'OPEN' and 'CLAIMED'
# are absent on purpose: they are the only two statuses the offer path can
# actually withhold work on, and those are exactly the rows whose warning is
# load-bearing. A row whose status is NOT FOUND is not terminal either --
# unknown is never read as 'finished' (the B5-0597 failure-3 direction, applied
# to status instead of age).
$LedgerTerminalStatuses = @('DONE','BLOCKED','SUPERSEDED','VOID')

# B5-0952: tolerance for the future-timestamp guard, stated here rather than
# chosen silently at each of its two call sites. A claim whose started_utc sits
# more than this many minutes AHEAD of the wall clock is refused as un-offerable.
#
# Why 60 minutes, and why not the 14 hours a full timezone sweep would need:
# the value is written by an agent and read by this runner on the SAME host, so
# the only skew it can legitimately absorb is drift between the write and the
# read, plus a NTP resync landing between them. Windows w32time resyncs on a
# ~1024s (17 min) interval by default, and w32time's own tolerance band is
# ±15 min, so 60 min is a full resync cycle plus a wide margin over any
# defensible drift. A larger offset (India +5:30, Pacific/Auckland +13, a
# hardcoded "tomorrow") is not drift, it is a data-entry defect, and it is
# refused loudly rather than absorbed -- see the direction rule in
# Get-FutureDatedClaimSecondsAhead.
$FutureTimestampToleranceMinutes = 60

function Get-CensusSuppression {
  # Claims-first census suppression (B5-0657, human-approved 2026-09-27,
  # docs/proposals/live-repair-aware-ledger-census-protocol.md).
  # taskId -> owner for every claim that is NOT provably stale.
  #
  # Why this runner needs it: Get-LedgerRows below is a structural census of the
  # ledger, and its three warnings ARE defect reports. Run while another agent holds
  # a live claim on the very row being censused, they manufacture false defects,
  # because a row mid-repair reads as a transient state belonging to a DIFFERENT
  # defect class than its committed form. Recorded instance: B5-0564 and B5-0565 both
  # read 6 pipes mid-repair under live claim B5-0592, which looks exactly like the
  # missing-trailing-delimiter class, while git show d8216afa confirms the committed
  # form was 8 pipes with a leading double pipe. This is the mirror image of the
  # pre-write-grep lesson: a pre-write census is not a defect report either.
  #
  # Copied verbatim in semantics from Get-CensusSuppression in
  # .agent/tools/ledger-query.ps1, for the same reason Get-NormName is copied there:
  # the two census tools must agree about which rows are reportable, and a second
  # hand-rolled variant is how they came to disagree in the first place. The
  # shared-library leg of docs/proposals/tool-rule-convergence-proposal.md section 6
  # is what retires this duplication; until it lands, the copy is the fix and
  # .agent/tools/census-crosscheck.ps1 is what proves the copies still agree.
  #
  # "Provably stale" means every DETERMINABLE signal is older than the claim's own
  # ttl_min. An absent owner heartbeat is absent information and is never read as
  # staleness: that is the B5-0609 defect class, where a lookup matching nothing
  # returned -1 and -1 compares as younger than any TTL, manufacturing LIVE out of
  # an absent signal. Fail-safe direction is silence, because the protocol's remedy
  # for a suppressed row is re-census after release, not a report.
  # B5-0660: the signal set is now the full THREE-signal triad (claim,
  # owner heartbeat, and the newest report matching the task id), matching
  # Test-LiveClaim so the offer decision and the census suppression read the
  # same evidence. A fresh close-out report on a task whose claim and
  # heartbeat both lag is liveness evidence.
  $sup = @{}
  if (-not (Test-Path -LiteralPath $ClaimsDir)) { return $sup }
  $now = (Get-Date).ToUniversalTime()
  $hb = Get-HeartbeatIndex
  foreach ($f in (Get-ChildItem -LiteralPath $ClaimsDir -Filter 'B5-*.json' -ErrorAction SilentlyContinue)) {
    $id = $f.BaseName
    $owner = $null
    $started = $null
    $ttl = 30
    try {
      # B5-1002: encoding pinned to explicit UTF-8 (claims are strict JSON,
      # UTF-8; a host-default read mis-decodes multi-byte sequences into
      # phantom C1 marks -- docs/DECISIONS.md reads 1600 naive vs 0 UTF-8).
      $j = Get-Content -LiteralPath $f.FullName -Raw -Encoding UTF8 | ConvertFrom-Json
      $owner = [string]$j.agent_id
      if ($j.ttl_min) { $ttl = [int]$j.ttl_min }
      if ($j.started_utc) {
        try {
          $started = [System.DateTime]::Parse(
            [string]$j.started_utc,
            [System.Globalization.CultureInfo]::InvariantCulture,
            [System.Globalization.DateTimeStyles]::RoundtripKind).ToUniversalTime()
        } catch { $started = $null }
      }
    } catch {
      $sup[$id] = '<unreadable claim file>'
      continue
    }
    if (-not $started) { $started = $f.LastWriteTimeUtc }
    # B5-0952 (deliberately NOT changed here): a future-dated started_utc is still
    # read as suppressing, and that is correct for THIS function. Suppression is
    # the fail-safe direction -- it makes the row NOT reportable, i.e. it silences
    # a possible defect report rather than manufacturing one. The B5-0952 guard
    # lives in the offer path (Get-ImplausibleClaimTaskIds / Test-LiveClaim), where
    # the same input used to make a row read LIVE and stay un-offered. Suppressing
    # here and refusing there agree on the outcome -- not offered -- by different
    # routes, so this line needs no bound of its own.
    if ($started -gt $now) { $sup[$id] = $owner; continue }   # future-dated clock skew: not a defect
    # B5-0953: converged with Get-CensusSuppression in ledger-query.ps1, the
    # copy that was already correct. The owner heartbeat is REQUIRED; an
    # absent or unjoinable owner signal is absent information and is never
    # read as staleness (the B5-0609 defect class), so the claim SUPPRESSES
    # -- the fail-safe direction: silence a possible defect report, never
    # manufacture one -- instead of falling through to claim/report age and
    # reading reportable, which is how the B5-0481 row read reportable here
    # and suppressed there. Marker strings and guard order match
    # ledger-query.ps1 so census-crosscheck.ps1 proves the copies agree.
    if (-not $owner) { $sup[$id] = '<unreadable agent_id>'; continue }
    $k = Get-NormName $owner
    if (-not $hb.ContainsKey($k)) { $sup[$id] = $owner; continue }   # required signal absent: not provably stale
    $newest = $started
    if ($hb[$k] -gt $newest) { $newest = $hb[$k] }
    # B5-0771: this was Join-Path (Split-Path -Parent $AgentDir) 'REPORTS', which
    # steps OUT of .agent to the repo root, where no REPORTS directory exists. The
    # Test-Path guard below then failed silently and the third signal was never
    # read. $AgentDir IS .agent, so the parent hop was the whole bug.
    $reportsDir = Join-Path $AgentDir 'REPORTS'
    if (Test-Path -LiteralPath $reportsDir) {
      $pattern = '*' + $id + '*.md'
      Get-ChildItem -LiteralPath $reportsDir -Filter $pattern -File -ErrorAction SilentlyContinue | ForEach-Object {
        if ($_.LastWriteTimeUtc -gt $newest) { $newest = $_.LastWriteTimeUtc }
      }
    }
    if (($now - $newest).TotalMinutes -lt $ttl) { $sup[$id] = $owner }
  }
  return $sup
}

function Get-LedgerRows {
  $rows = @()
  # B5-1002: encoding pinned to explicit UTF-8; see the note at the claim read above.
  foreach ($line in (Get-Content -LiteralPath $Ledger -Encoding UTF8)) {
    # Anchor tolerantly: 1 or more leading pipes (double-pipe defect class) and an
    # optional letter suffix on the id (B5-0202c, B5-0329a, ...). The suffix class
    # was still invisible after the leading-pipe fix, because \d+ cannot match "c".
    if ($line -match '^\|+\s*(B5-[0-9]{4}[a-z]?)\s*\|') {
      $parts = $line -split '\|'
      if ($parts.Count -ge 4) {
        # Derive Id AND Status the same tolerant way. Reading a fixed cell index is
        # wrong on every double-pipe row: for "|| B5-0604 | OPEN | ..." the split
        # yields ['','',' B5-0604 ',' OPEN '], so $parts[2] is the ID, not the status.
        # That made Status unmatchable against 'OPEN', so the queue-drained stop
        # condition fired while claimable OPEN rows were on disk.
        $idPart = $null; $statusPart = $null
        foreach ($p in $parts) {
          $tp = $p.Trim()
          if ($null -eq $idPart -and $tp -match '^B5-[0-9]{4}[a-z]?$') { $idPart = $tp }
          if ($null -eq $statusPart -and $LedgerStatuses -contains $tp) { $statusPart = $tp }
          if ($idPart -and $statusPart) { break }
        }
        if ($idPart) {
          $rows += [pscustomobject]@{
            Id     = $idPart
            Status = $statusPart
          }
        }
      }
    }
  }
  # Claims-first suppression set (B5-0657), computed once per call rather than per
  # row. Read BEFORE any warning below is emitted, because those warnings are defect
  # reports and a defect report on a row someone is mid-repair on is a false positive.
  $sup = Get-CensusSuppression

  # Self-check. Compare against a DELIBERATELY PERMISSIVE pattern, not the pattern
  # under test: the previous check counted matches with the same regex that built
  # $rows, so it compared a set against itself and could never detect a row the
  # regex could not see. It reported 312==312 while 9 rows carried a corrupt status.
  $permissive = 0
  foreach ($ln in (Get-Content -LiteralPath $Ledger -Encoding UTF8)) {
    if ($ln -match '^\|+\s*B5-[0-9]{4}[a-z]?\s*\|') { $permissive++ }
  }
  if ($permissive -ne $rows.Count) {
    # This check is an aggregate COUNT comparison, so the offending row cannot be
    # named from it. When a live claim exists the honest reading is "this mismatch
    # may be a row mid-repair", not "the ledger is broken" -- so it is reported as
    # not-a-defect-report and the suppressed rows are named for re-census.
    if ($sup.Count -gt 0) {
      Write-Warning ("Get-LedgerRows: returned " + $rows.Count + " rows but a permissive scan found " + $permissive + " candidate row lines. NOT A DEFECT REPORT: " + $sup.Count + " row(s) hold a non-stale claim (" + (($sup.Keys | Sort-Object) -join ', ') + ") and a row mid-repair can move this count. Re-census those rows after the claim releases before treating this as a real defect.")
    } else {
      Write-Warning ("Get-LedgerRows: returned " + $rows.Count + " rows but a permissive scan found " + $permissive + " candidate row lines -- a row filter is silently hiding rows.")
    }
  }
  $noStatus = @($rows | Where-Object { -not $_.Status })
  $noStatusReported = @($noStatus | Where-Object { -not $sup.ContainsKey($_.Id) })
  $noStatusSuppressed = @($noStatus | Where-Object { $sup.ContainsKey($_.Id) })
  if ($noStatusReported.Count -gt 0) {
    Write-Warning ("Get-LedgerRows: " + $noStatusReported.Count + " row(s) parsed with no recognisable status: " + (($noStatusReported | ForEach-Object { $_.Id }) -join ', '))
  }
  if ($noStatusSuppressed.Count -gt 0) {
    Write-Warning ("Get-LedgerRows: " + $noStatusSuppressed.Count + " row(s) with no recognisable status are UNDER A LIVE CLAIM and are NOT a defect report: " + (($noStatusSuppressed | ForEach-Object { $_.Id }) -join ', ') + ". Re-census after the claim releases.")
  }
  # Distinct-ID assertion (B5-0622). The permissive check above compares two counts
  # of ROWS, so two well-formed rows sharing one task ID sail straight through it.
  # That class is not cosmetic: downstream, $statusOf[$r.Id] = $r.Status means the
  # second row silently overwrites the first, and one task stops existing as far as
  # the gate and lane tables are concerned - it can never be offered, and its
  # status is never read. Name the offenders so the collision is visible at census
  # time. This MUST NOT change exit behaviour for a healthy ledger.
  $dupIds = @($rows | Group-Object Id | Where-Object { $_.Count -gt 1 })
  if ($dupIds.Count -gt 0) {
    # A duplicate ID is only actionable if no live claim covers the row: under a live
    # claim the second row is most likely a half-written row the owner is mid-repair
    # on, and reporting it would manufacture a defect (B5-0657). Genuine collisions
    # still print in full, with the same instruction as before.
    $dupLive = @($dupIds | Where-Object { $sup.ContainsKey($_.Name) })
    $dupReal = @($dupIds | Where-Object { -not $sup.ContainsKey($_.Name) })
    if ($dupReal.Count -gt 0) {
      $dupText = (($dupReal | ForEach-Object { $_.Name + ' x' + $_.Count }) -join ', ')
      Write-Warning ("DUPLICATE TASK ID: " + $dupText + " -- a duplicate ID silently drops a task from the queue, because status is keyed by ID and the last row wins. Exactly one writer should renumber, and MUST diverge to a NON-ADJACENT id: renumbering into the slot the other writer just vacated deadlocks, since they will usually move there too. Leave the other row byte-identical. See 00_BOOT.md step 9 and B5-0622.")
    }
    if ($dupLive.Count -gt 0) {
      Write-Warning ("DUPLICATE TASK ID, NOT A DEFECT REPORT: " + ((($dupLive | ForEach-Object { $_.Name + ' x' + $_.Count }) -join ', ')) + " -- these row(s) hold a non-stale claim, so the duplicate is most likely a half-written row being repaired. Re-census after the claim releases before renumbering anything.")
    }
  }
  return $rows
}

function Get-TaskNumber {
  # Numeric sort/lane key for a task ID. MUST tolerate the letter-suffixed id
  # class (B5-0202c, B5-0329a, B5-0330a, B5-0331a): Get-LedgerRows accepts that
  # shape, so every numeric use of the id has to accept it too. A bare
  # [int]($Id -replace 'B5-','') throws on '0202c' and took the whole runner
  # down on any OPEN suffixed row (B5-0624). Extract the leading digit run
  # instead, so 'B5-0202c' -> 202 and sorts with its numeric siblings.
  param([string]$Id)
  # Anchor on the number AFTER the B5- prefix. An unanchored '(\d+)' matches the
  # '5' inside 'B5' first, so EVERY id would score 5 and the sort would silently
  # degrade to file order -- a worse failure than the crash it replaced, because
  # nothing goes red.
  $m = [regex]::Match([string]$Id, '^B5-(\d+)')
  if ($m.Success) { return [int]$m.Groups[1].Value }
  return 0
}

function Get-NormName([string]$name) {
  # Punctuation-normalised key for joining an agent_id to a heartbeat FILENAME.
  # Copied verbatim from .agent/tools/ledger-query.ps1 (Get-NormName) on purpose:
  # the two tools must agree, and a second hand-rolled variant is how they came to
  # disagree in the first place. Strips colons, U+2028/U+2029, spaces, dashes,
  # underscores and dots; lowercases; keeps letters and digits only. This is what
  # lets 'solar-pro4:free' match the file 'solar-pro4<U+2028>free.json'.
  $sb = New-Object System.Text.StringBuilder
  foreach ($ch in $name.ToCharArray()) {
    $c = [int]$ch
    if ($c -eq 0x2028 -or $c -eq 0x2029) { continue }
    if ($ch -match '[\p{L}\p{N}]') { [void]$sb.Append($ch.ToString().ToLowerInvariant()) }
  }
  return $sb.ToString()
}

function Get-HeartbeatIndex {
  # normalised agent key -> NEWEST heartbeat mtime for that key. Rebuilt on every
  # call rather than cached: a single run may iterate many tasks over many minutes,
  # and a cache captured at start-up would report a growing agent as silent. ~30
  # files per call is not worth the staleness.
  $idx = @{}
  if (Test-Path -LiteralPath $hbDir) {
    Get-ChildItem -LiteralPath $hbDir -Filter "*.json" -ErrorAction SilentlyContinue | ForEach-Object {
      $key = Get-NormName $_.BaseName
      if (-not $idx.ContainsKey($key) -or $_.LastWriteTimeUtc -gt $idx[$key]) { $idx[$key] = $_.LastWriteTimeUtc }
    }
  }
  return $idx
}

function Get-FutureDatedClaimMinutesAhead {
  # B5-0952: the forward bound the B5-0653 guard never had. That guard refuses a
  # started_utc which is exactly midnight UTC or at/before the epoch, so it covers
  # the PAST only. A started_utc ahead of the wall clock passes it, and then
  # (now - started) is NEGATIVE. A negative age compares as younger than any TTL
  # -- the same inversion as the B5-0597 failure-3 defect, where a join matching
  # no heartbeat returned -1 and rendered 4 claims LIVE -- so the claim reads LIVE
  # until the clock catches up instead of aging out. Measured instance: claim
  # B5-0939 carried started_utc 2026-09-28T22:05:00Z against a wall clock of
  # 2026-09-28T10:14Z, and ledger-query.ps1 printed its age as MINUS 711.7
  # minutes while still rendering the verdict LIVE.
  #
  # Returns the number of minutes started_utc sits ahead of now, or $null when it
  # is not ahead beyond $FutureTimestampToleranceMinutes (which includes the
  # unparseable and the comfortably-past cases -- those are other tools' business).
  #
  # DIRECTION RULE, preserved from B5-0653 rather than re-derived: ambiguity
  # resolves toward NOT OFFERING. A wrongly-refused live claim costs one blocked
  # task, which the owner clears in seconds by re-claiming with a real timestamp;
  # a wrongly-accepted future-dated claim costs a collision and destroyed work,
  # because the claim will not age out for as many hours as it is dated ahead.
  # The two errors are not symmetric, so the tolerance is set to absorb
  # everything absorbable and no more (see $FutureTimestampToleranceMinutes),
  # and the check is ONE-SIDED: a claim in the past is left entirely alone here.
  # The claim file is never edited or repaired -- that is another agent's artifact.
  param(
    [System.DateTime]$Parsed,  # the parsed value, already ToUniversalTime()
    [System.DateTime]$NowUtc
  )
  if ($null -eq $Parsed) { return $null }
  $aheadMinutes = ($Parsed - $NowUtc).TotalMinutes
  if ($aheadMinutes -le $FutureTimestampToleranceMinutes) { return $null }
  return $aheadMinutes
}

function Test-TerminalLedgerRow {
  # B5-0981: is this task's ledger row already in a status the offer path can
  # never act on?
  #
  # The B5-0653 and B5-0952 warnings exist to stop a CLAIMABLE task being
  # withheld by a bad timestamp. That is the whole point of them, and the point
  # evaporates on a DONE row: a DONE row is withheld by definition, so the
  # warning cannot be preventing anything -- it can only be noise. Measured
  # before this guard: the orphan claim .agent/CLAIMS/B5-0481.json (midnight
  # placeholder, B5-0653) sits on a row DONE since 2026-09-26 and emitted
  # 10 WARNING lines per -DryRun invocation, because the scan runs once per
  # iteration. Log noise, not a stalled task.
  #
  # WHAT THIS DOES NOT DO, deliberately and in the B5-0653 spirit: it does not
  # change the refusal. The id is still excluded from the candidate list and
  # Test-LiveClaim still answers UNSTEALABLE ($true) for an implausible or
  # future-dated claim. Only the LINE is demoted from Write-Warning to
  # Write-Verbose. The direction rule is untouched and states the same thing:
  # a wrongly-silenced warning costs visibility, a wrongly-dropped refusal
  # costs a collision and destroyed work. The expensive error is unchanged.
  #
  # It also does not repair, edit, delete or reap the claim file, and it does
  # not touch the ledger row. The orphan on a terminal row is still the OWNER's
  # to release (B5-0622); this only stops the runner narrating it every pass.
  #
  # UNKNOWN is not terminal. A task id with no readable ledger row returns
  # $false, so the warning stays loud -- the same rule the B5-0597 failure-3
  # lesson demands for an absent signal, here applied to a row lookup.
  param([string]$TaskId)
  if (-not $TaskId) { return $false }
  try {
    foreach ($r in (Get-LedgerRows)) {
      if ($r.Id -eq $TaskId) {
        return ($LedgerTerminalStatuses -contains [string]$r.Status)
      }
    }
  } catch {
    # An unreadable ledger is not evidence that the row is finished.
    return $false
  }
  return $false
}

function Write-ImplausibleClaimNotice {
  # B5-0981: the single seam both implausible-timestamp warnings route through.
  # Routing them here rather than sprinkling a status test at each of the four
  # call sites is what keeps the two guards (B5-0653 past, B5-0952 future) and
  # their two implementations (the Test-LiveClaim backstop and the
  # Get-ImplausibleClaimTaskIds primary scan) from drifting apart -- a second
  # hand-rolled variant is how the two census tools came to disagree in the
  # first place, and the same reasoning is recorded at Get-CensusSuppression.
  #
  # Terminal row -> Write-Verbose (still emitted, visible under -Verbose, silent
  # in the default run). Non-terminal OR UNKNOWN row -> Write-Warning, byte for
  # byte the line that was there before.
  param(
    [string]$TaskId,
    [string]$Message
  )
  if (Test-TerminalLedgerRow -TaskId $TaskId) {
    Write-Verbose ("[B5-0981 demoted: task " + $TaskId + " is TERMINAL in the ledger, " +
      "so this notice cannot be withholding offerable work] " + $Message)
    return
  }
  Write-Warning $Message
}

function Test-LiveClaim {
  # Liveness = the NEWEST of the THREE signals (B5-0660, completing the
  # B5-0597 three-signal rule): (a) the claim's own started_utc (falling back
  # to the claim file's mtime when that will not parse), (b) the OWNER's
  # heartbeat mtime -- matched through Get-NormName so 'solar-pro4:free'
  # finds 'solar-pro4<U+2028>free.json' -- and (c) the NEWEST report mtime
  # matching the task id in .agent/REPORTS/. Whichever is youngest decides.
  # History: the original read started_utc ALONE (B5-0649 closed that); then
  # claim+heartbeat (two signals); a report-only task's close-out lands in
  # REPORTS and keeps the claim honest even when both other signals lag, so
  # the third signal completes the triad the HEARTBEATS README defines.
  # B5-0660 UNKNOWN semantics, AMENDED by B5-0953 for the binary offer
  # decision (the B5-0649 claim-age fallback is retired):
  #   - owner heartbeat ABSENT -> UNKNOWN, never LIVE and never STALE per
  #     the binding three-signal rule (.agent/HEARTBEATS/README.md, "Liveness:
  #     three signals, never one"); the claim is left UNSTEALABLE ($true)
  #     and the task NOT offered.
  #   - claim agent_id UNREADABLE -> same disposition: the owner signal
  #     cannot be joined, which is absent information, never staleness
  #     evidence.
  #   - claim file UNPARSEABLE -> NOT offerable (UNKNOWN is never LIVE).
  #   - implausible started_utc -> refused per B5-0653 (below).
  param([string]$TaskId)
  $claimPath = Join-Path $ClaimsDir ($TaskId + '.json')
  if (-not (Test-Path -LiteralPath $claimPath)) { return $false }
  try {
    $j = Get-Content -LiteralPath $claimPath -Raw -Encoding UTF8 | ConvertFrom-Json
    $ttl = 30
    if ($j.ttl_min) { $ttl = [int]$j.ttl_min }
    $now = (Get-Date).ToUniversalTime()

    $started = $null
    $startedParsed = $null
    if ($j.started_utc) {
      try {
        $started = [System.DateTime]::Parse(
          [string]$j.started_utc,
          [System.Globalization.CultureInfo]::InvariantCulture,
          [System.Globalization.DateTimeStyles]::RoundtripKind).ToUniversalTime()
        $startedParsed = $started
      } catch { $started = $null }
    }
    if (-not $started) { $started = (Get-Item -LiteralPath $claimPath).LastWriteTimeUtc }

    # B5-0653: an IMPLAUSIBLE started_utc is a data-quality refusal, not a
    # liveness verdict. A value that parses but cannot be a just-now claim --
    # exactly midnight UTC (the placeholder default) or at/before the Unix
    # epoch -- must not be consumed as the claim's age, because a two-signal
    # liveness check is only as good as its weaker signal. Direction rule:
    # every ambiguity resolves toward NOT OFFERING, since wrongly calling a
    # live claim stale costs a collision and destroyed work, while wrongly
    # calling an implausible claim stale costs one blocked task a human
    # clears in seconds. We decline to offer and warn loudly, naming the id,
    # the owner and the value; we never repair or rewrite the claim file
    # (editing another agent's claim is forbidden). Accepted cost: a genuine
    # claim written in the exact second of midnight UTC, roughly 1 in 86400,
    # is declined until the owner re-claims with a real timestamp.
    if ($null -ne $startedParsed) {
      $unixEpoch = [System.DateTime]::new(1970, 1, 1, 0, 0, 0, [System.DateTimeKind]::Utc)
      $isMidnight = ($startedParsed.Hour -eq 0 -and $startedParsed.Minute -eq 0 -and $startedParsed.Second -eq 0)
      if ($isMidnight -or ($startedParsed -le $unixEpoch)) {
        $why = "[B5-0653] task " + $TaskId + " NOT OFFERED: claim started_utc " +
          "[" + [string]$j.started_utc + "] owner [" + [string]$j.agent_id + "] " +
          "parses but is IMPLAUSIBLE as a just-now claim (midnight/epoch placeholder). " +
          "Re-claim with the actual current UTC time."
        Write-ImplausibleClaimNotice -TaskId $TaskId -Message $why
        # Treated as LIVE (unstealable), not as free: returning $false here
        # would hand the task out. The candidate-list filter in
        # Get-ClaimableOpenTasks (Get-ImplausibleClaimTaskIds) is the primary
        # refusal; this path is the backstop for any other caller.
        return $true
      }
    }

    # B5-0952: the FORWARD bound, complementing the midnight/epoch guard above.
    # That guard only knows how to reject the past, so a started_utc dated ahead
    # of the wall clock reaches here untouched and (now - started) goes negative.
    # A negative age is younger than any TTL, so the claim reads LIVE and will
    # keep reading LIVE until the clock catches up -- hours or days of a claim
    # that is not going stale because its timestamp says it cannot. Refuse it as
    # un-offerable and say why, rather than letting the arithmetic speak.
    if ($null -ne $startedParsed) {
      $ahead = Get-FutureDatedClaimMinutesAhead -Parsed $startedParsed -NowUtc $now
      if ($null -ne $ahead) {
        Write-ImplausibleClaimNotice -TaskId $TaskId -Message ("[B5-0952] task " + $TaskId + " NOT OFFERED: claim started_utc [" +
          [string]$j.started_utc + "] owner [" + [string]$j.agent_id + "] parses and is " +
          [math]::Round($ahead, 1) + " minutes AHEAD of the wall clock, beyond the " +
          $FutureTimestampToleranceMinutes + "-minute tolerance. A claim dated in the " +
          "future never ages out (its age is negative, and negative compares as " +
          "younger than any TTL), so it would read LIVE until the clock caught up. " +
          "Re-claim with the actual current UTC time. Not repaired here: a claim file " +
          "belongs to its owner.")
        # Same disposition as the B5-0653 refusal above and for the same reason:
        # LIVE (unstealable), not free. Returning $false would hand the task out.
        return $true
      }
    }
    $newest = $started

    # Signal (c): the NEWEST report matching this task id. A close-out report
    # is liveness evidence exactly like a heartbeat: report-only tasks (no
    # compile, quick execution) finish in a burst and their claim+heartbeat
    # can both lag while the work is genuinely done or in flight.
    # B5-0771: same dead path as the census-suppression site above -- it resolved
    # to the repo-root REPORTS, so this signal was skipped rather than read, and
    # the OFFER decision fell back to claim+heartbeat alone.
    $reportsDir = Join-Path $AgentDir 'REPORTS'
    Write-Verbose ("[B5-0660] scanning reports at " + $reportsDir + " for " + $TaskId)
    $reportNewest = $null
    if (Test-Path -LiteralPath $reportsDir) {
      $pattern = '*' + $TaskId + '*.md'
      Get-ChildItem -LiteralPath $reportsDir -Filter $pattern -File -ErrorAction SilentlyContinue |
        ForEach-Object { if ($null -eq $reportNewest -or $_.LastWriteTimeUtc -gt $reportNewest) { $reportNewest = $_.LastWriteTimeUtc } }
    }
    if ($null -ne $reportNewest -and $reportNewest -gt $newest) { $newest = $reportNewest }

    # B5-0953: the owner heartbeat is a REQUIRED signal of the three-signal
    # rule (.agent/HEARTBEATS/README.md), so its absence is UNKNOWN -- never
    # LIVE and never STALE. Until this fix the offer decision fell back to
    # claim/report age here, which is the B5-0609 defect class in offer form:
    # a liveness verdict manufactured out of the one signal that was never in
    # dispute, so a stale-age heartbeat-less claim was OFFERED while
    # ledger-query.ps1 printed UNKNOWN for the same claim (the B5-0481
    # divergence B5-0953 exists to reconcile). Corrode-shape check: the
    # UNPARSEABLE-claim branch below already answers not-offered for "a
    # required signal is unreadable"; a heartbeat that is ABSENT is the same
    # epistemic state and gets the same disposition. The claim is left
    # UNSTEALABLE ($true, matching the unstealable disposition of the
    # B5-0653/B5-0952 refusals above) so every caller that asks "is this
    # claim live?" -- the candidate filter, the lane-occupation check and
    # Test-SeedableQueue -- keeps holding, and the runner does not hand out a
    # task whose liveness cannot be established. Direction rule, unchanged
    # from B5-0653: wrongly withholding costs one blocked task, which the
    # owner clears in seconds by writing a heartbeat or re-claiming; wrongly
    # offering costs a collision and destroyed work. The stale-age ghost this
    # can leave on a dead owner is a REAP question under 00_BOOT step 10 (all
    # three real signals STALE, evidence recorded first) -- never an offer
    # question, and reaping stays forbidden to this function.
    $owner = [string]$j.agent_id
    $ownerSignal = $null
    if ($owner) {
      $hb = Get-HeartbeatIndex
      $key = Get-NormName $owner
      if ($hb.ContainsKey($key)) { $ownerSignal = $hb[$key] }
    }
    if ($null -eq $ownerSignal) {
      if ($owner) {
        Write-Warning ("[B5-0953] task " + $TaskId + ": owner heartbeat UNKNOWN (no heartbeat file for [" + $owner + "]). UNKNOWN is never LIVE and never STALE: the task is NOT offered and the claim is not stealable, until the owner writes a heartbeat or the claim is released.")
      } else {
        Write-Warning ("[B5-0953] task " + $TaskId + ": claim agent_id unreadable, so the owner signal cannot be joined. UNKNOWN is never LIVE and never STALE: the task is NOT offered and the claim is not stealable, until the claim is fixed or released by its owner.")
      }
      return $true
    }
    if ($ownerSignal -gt $newest) { $newest = $ownerSignal }
    if ($started -gt $now) { return $true }  # future-dated (clock skew): treat as live
    return (($now - $newest).TotalMinutes -lt $ttl)
  } catch {
    # B5-0660: an UNPARSEABLE claim is UNKNOWN, and UNKNOWN is never LIVE.
    # Returning $true here kept a corrupted claim unstealable, which was safe
    # against collisions but silently ZEROED the liveness signal; a corrupted
    # claim now blocks the task loudly instead, per 00_BOOT step 10 as amended.
    Write-Warning ("[B5-0660] task " + $TaskId + ": claim file UNPARSEABLE -- UNKNOWN is never LIVE; task NOT offered until the claim is fixed or reaped by its owner.")
    return $false
  }
}

# B5-0653: scan claim files for a started_utc that parses but is IMPLAUSIBLE
# as a just-now claim -- exactly midnight UTC (the placeholder default) or
# at/before the Unix epoch. Returns the task ids whose claims carry one.
# We never repair or rewrite the claim file (editing another agent's claim is
# forbidden); the owner re-claims with a real timestamp and the task returns
# to the queue on the next pass.
function Get-ImplausibleClaimTaskIds {
  $ids = @()
  if (-not (Test-Path -LiteralPath $ClaimsDir)) { return $ids }
  $unixEpoch = [System.DateTime]::new(1970, 1, 1, 0, 0, 0, [System.DateTimeKind]::Utc)
  $nowUtc = (Get-Date).ToUniversalTime()
  Get-ChildItem -LiteralPath $ClaimsDir -Filter 'B5-*.json' -ErrorAction SilentlyContinue | ForEach-Object {
    try {
      $c = Get-Content -LiteralPath $_.FullName -Raw -Encoding UTF8 | ConvertFrom-Json
      $s = $null
      if ($c.started_utc) {
        try {
          $s = [System.DateTime]::Parse(
            [string]$c.started_utc,
            [System.Globalization.CultureInfo]::InvariantCulture,
            [System.Globalization.DateTimeStyles]::RoundtripKind).ToUniversalTime()
        } catch { $s = $null }
      }
      if ($null -ne $s) {
        $isMidnight = ($s.Hour -eq 0 -and $s.Minute -eq 0 -and $s.Second -eq 0)
        if ($isMidnight -or ($s -le $unixEpoch)) {
          $ids += $_.BaseName
          Write-ImplausibleClaimNotice -TaskId $_.BaseName -Message ("[B5-0653] task " + $_.BaseName + " NOT OFFERED: claim started_utc [" +
            [string]$c.started_utc + "] owner [" + [string]$c.agent_id +
            "] parses but is IMPLAUSIBLE as a just-now claim (midnight/epoch placeholder). " +
            "Re-claim with the actual current UTC time.")
          return   # one task id, one warning: never report both defects for one claim
        }
        # B5-0952: the same scan, the other direction. B5-0653 rejects the PAST
        # (midnight / at-or-before the epoch) and says nothing about the future,
        # so a claim dated ahead of the wall clock is caught by neither branch
        # and its age goes NEGATIVE -- which compares as younger than any TTL,
        # the B5-0597 failure-3 inversion, so the row would read LIVE until the
        # clock caught up. This is the PRIMARY refusal site (it excludes the row
        # from the candidate list); the identical check in Test-LiveClaim is the
        # backstop for any other caller. Same disposition, same tolerance, same
        # one-sided rule: past-dated claims are left to B5-0653 alone.
        $ahead = Get-FutureDatedClaimMinutesAhead -Parsed $s -NowUtc $nowUtc
        if ($null -ne $ahead) {
          $ids += $_.BaseName
          Write-ImplausibleClaimNotice -TaskId $_.BaseName -Message ("[B5-0952] task " + $_.BaseName + " NOT OFFERED: claim started_utc [" +
            [string]$c.started_utc + "] owner [" + [string]$c.agent_id + "] parses and is " +
            [math]::Round($ahead, 1) + " minutes AHEAD of the wall clock, beyond the " +
            $FutureTimestampToleranceMinutes + "-minute tolerance. A claim dated in the " +
            "future never ages out (its age is negative, and negative compares as " +
            "younger than any TTL), so it would read LIVE until the clock caught up. " +
            "Re-claim with the actual current UTC time. Not repaired here: a claim " +
            "file belongs to its owner.")
        }
      }
    } catch { }   # unreadable claim: never steal it, never warn here
  }
  return $ids
}

function Get-ClaimableOpenTasks {
  # Prerequisite gates (from the QUEUE overseer notes in TASK_LEDGER.md):
  # a task is offered only when every listed predecessor reads DONE.
  $Prereqs = @{
    'B5-0368' = @('B5-0367');
    'B5-0370' = @('B5-0368');
    'B5-0371' = @('B5-0368', 'B5-0369');
    'B5-0372' = @('B5-0362', 'B5-0364', 'B5-0365', 'B5-0366');
    'B5-0373' = @('B5-0369');
    'B5-0374' = @('B5-0369');
    'B5-0377' = @('B5-0362', 'B5-0364', 'B5-0365', 'B5-0366');
    'B5-0378' = @('B5-0370', 'B5-0371');
    'B5-0384' = @('B5-0362');
    'B5-0389' = @('B5-0362', 'B5-0364', 'B5-0365', 'B5-0366')
  }
  $statusOf = @{}
  foreach ($r in (Get-LedgerRows)) { $statusOf[$r.Id] = $r.Status }
  $rows = Get-LedgerRows | Where-Object { $_.Status -eq 'OPEN' }
  # B5-0657 claims-first suppression: a row whose claim is not provably stale
  # is mid-repair by a live agent -- never offered, never a defect report.
  $sup = Get-CensusSuppression
  # B5-0653: a claim whose started_utc is IMPLAUSIBLE (midnight/epoch
  # placeholder) makes its task un-offerable. Excluded from the candidate
  # list here so it cannot head-slot every iteration; Test-LiveClaim also
  # refuses it as live so no other path hands it out. Both paths warn.
  $implausible = Get-ImplausibleClaimTaskIds
  $free = @()
  foreach ($r in $rows) {
    if ($implausible -contains $r.Id) { continue }
    if ($sup.ContainsKey($r.Id)) { continue }
    if (Test-LiveClaim -TaskId $r.Id) { continue }
    $gated = $false
    if ($Prereqs.ContainsKey($r.Id)) {
      foreach ($p in $Prereqs[$r.Id]) {
        if ($statusOf[$p] -ne 'DONE') { $gated = $true; break }
      }
    }
    if ($gated) {
      Write-Host ("Skipping gated " + $r.Id + " (predecessor not DONE).")
    } else {
      $free += $r
    }
  }
  $sorted = @($free | Sort-Object { Get-TaskNumber $_.Id })
  # Serialize the shared-code lanes (one writer per lane, lowest number first):
  # engine/model 0367-0376, ai 0377-0378, ui 0379-0381. Only the lane head is
  # offered; docs/harness/report/proposal rows are lane-free. A lane with a
  # LIVE claim on any of its OPEN rows is occupied: offer nothing from it.
  $laneRanges = @{
    'eng' = 367..376;
    'ai'  = 377..378;
    'ui'  = 379..381
  }
  $occupied = @()
  foreach ($lane in $laneRanges.Keys) {
    foreach ($n in $laneRanges[$lane]) {
      $lid = 'B5-' + $n.ToString('0000')
      if ($statusOf.ContainsKey($lid) -and
          ($statusOf[$lid] -eq 'OPEN' -or $statusOf[$lid] -eq 'CLAIMED')) {
        if (Test-LiveClaim -TaskId $lid) { $occupied += $lane; break }
      }
    }
  }
  foreach ($lane in $occupied) {
    Write-Host ("Lane " + $lane + " occupied by a live claim; holding its tasks.")
  }
  $usedLanes = @()
  $picked = @()
  foreach ($r in $sorted) {
    $n = Get-TaskNumber $r.Id
    $lane = ''
    if ($n -ge 367 -and $n -le 376) { $lane = 'eng' }
    elseif ($n -ge 377 -and $n -le 378) { $lane = 'ai' }
    elseif ($n -ge 379 -and $n -le 381) { $lane = 'ui' }
    if ($lane -ne '' -and (($usedLanes -contains $lane) -or ($occupied -contains $lane))) { continue }
    if ($lane -ne '') { $usedLanes += $lane }
    $picked += $r
  }
  return $picked
}

function Test-SeedableQueue {
  # B5-0900: is the queue drained for the RIGHT reason? Get-ClaimableOpenTasks
  # returns nothing both when there is no work and when every remaining row is
  # claimed, gated or BLOCKED - three states with opposite correct actions. The
  # first wants a seed wave; the other two want a stop. Treating the empty list as
  # permission to seed is the B5-0618 duplicate-wave defect with a delay: two
  # seeders each measure the same window free and both write. So every
  # non-terminal row and every live claim HOLDS the branch, and the hold is named
  # rather than silent, because a silent hold is indistinguishable from the bug
  # this branch was written to fix. Direction rule as in B5-0653 above: every
  # ambiguity resolves toward NOT acting, since a wrongly-held seed costs one
  # iteration and a wrongly-fired one costs a duplicate wave.
  $rows = @(Get-LedgerRows)
  $pending = @($rows | Where-Object {
    $_.Status -eq 'OPEN' -or $_.Status -eq 'CLAIMED' -or $_.Status -eq 'BLOCKED'
  })
  if ($pending.Count -gt 0) {
    $names = @($pending | ForEach-Object { $_.Id })
    $show = if ($names.Count -le 5) { $names -join ', ' }
            else { (($names[0..4]) -join ', ') + ' and ' + ($names.Count - 5) + ' more' }
    Write-Host ("Holding seed: " + $pending.Count + " row(s) still read OPEN, CLAIMED or BLOCKED (" + $show + "). A queue that is only drained because its rows are taken is not drained.")
    return $false
  }
  foreach ($r in $rows) {
    if (Test-LiveClaim -TaskId $r.Id) {
      Write-Host ("Holding seed: a live claim exists (" + $r.Id + "). Residue on a closed row is a reap decision for a human, not a seed trigger - reaping it is never this runner's job.")
      return $false
    }
  }
  return $true
}

$TaskPromptTemplate = @'
Boot per .agent/00_BOOT.md, then read .agent/AGENT_LOOP.md and execute its
IDENTITY AND FILENAMES rules and its close-out checklist for this one task:
__TASK_ID__ only. (The claim file was already checked by the runner - re-verify
.agent/CLAIMS/__TASK_ID__.json is absent before creating your own claim, and
re-read the row to confirm it still reads OPEN.) Work ONLY inside the claimed
scope, verify per the row's gate, then close out fully (.agent/TASK_LEDGER.md row,
docs/DECISIONS.md entry, .agent/REPORTS/<date>-<sanitised-agent-id>-__TASK_ID__.md
with one "Reusable lesson" line filed as a NEW file under
.agent/PATTERNS/<agent-id>/, delete your claim file, refresh your heartbeat on the
binding schema in .agent/HEARTBEATS/README.md). Single task, then exit. If the
gate is red from out-of-scope in-flight edits, mark BLOCKED per .agent/00_BOOT.md step 8 and
release - do not fix outside your scope. Use your name and version as agent_id.
Seeding is NOT part of this prompt: do not create a new ledger row while working
this task. The seed cycle is .agent/AGENT_LOOP.md LOOP step 1, and the runner
invokes it through its own separate prompt when the queue drains.
Do NOT read .agent/HANDOFF.md: it is superseded and its state section is stale.
'@

$SeedPromptTemplate = @'
Boot per .agent/00_BOOT.md, then read .agent/AGENT_LOOP.md and carry out its LOOP
step 1 seeding duty and nothing else. You were invoked because the queue drained,
NOT because a task was assigned to you: no claim exists for you and you must not
create one. Ground ONE genuinely useful task in-tree before writing it - read
.agent/TASK_LEDGER.md, the close-out reports under .agent/REPORTS/ and the newest
records across every .agent/PATTERNS/ namespace, and prefer a defect, regression
or gap that a report or a close-out already names over a speculative one. Do not
re-seed anything the ledger already carries, in any status. Write it as exactly
ONE new row in .agent/TASK_LEDGER.md: status OPEN, a single leading pipe, exactly
7 pipes, and no pipe character inside any cell. Choose a task id that is
non-adjacent to the highest id already in the ledger, so a concurrent seeder
cannot land on the same number; renumbering into a slot another writer just
vacated deadlocks, so if you collide, leave their row byte-identical and diverge
further. Then, in this order: run the duplicate-id census with
powershell -NoProfile -ExecutionPolicy Bypass -File .agent/tools/run-dup-census.ps1
(0 duplicates is the pass condition), prove your own row with
powershell -NoProfile -ExecutionPolicy Bypass -File .agent/tools/ledger-query.ps1
-Status OPEN, which must read pipeCount 7 and doubleLead no, and record an
assessor_llm entry plus last_modified_by_llm and last_modified_date in the ledger
frontmatter. Seed ONE row and exit. Do not claim it, do not work it, do not
commit, and do not touch .agent/CLAIMS/ or .agent/HEARTBEATS/. If you cannot
ground a genuinely useful task, seed nothing and say so in one line: an ungrounded
row is worse than an empty queue. Do NOT read .agent/HANDOFF.md: it is superseded.
'@

# B5-1002: encoding receipt. Every text read in this runner is explicit
# UTF-8 (Get-Content -Encoding UTF8 throughout). A census is a verdict with a
# receipt, and an unnamed instrument cannot be reproduced: a naive read reports
# 1600 C1 marks on docs/DECISIONS.md where this read reports 0 on the same
# bytes, so the instrument is named here once per run. Exit codes, TTL, and
# liveness verdicts are unchanged by the pinning.
Write-Output "-- ENCODING: UTF-8 (explicit; Get-Content -Encoding UTF8) --"
$stagnant = 0
$seedInvocations = 0
# B5-1004: cross-process offer memory so concurrently launched lanes divide the
# candidate set instead of converging on its head. The claim file stays the
# single authority (atomic create, duplicate-ID assertion untouched); this only
# shapes WHICH row each lane is offered. Mechanism: for each candidate row a
# lane tries an OS-atomic exclusive CREATE of a per-row marker file under
# %TEMP% (keyed per ledger so a fixture never contends with the live file),
# holding the owning lane's PID. The first lane to touch a row wins the create;
# every other lane's create fails and it walks to the next candidate. There is
# no read-modify-write step, so there is no window: two lanes racing on the same
# row are separated by the filesystem itself, not by a lock protocol. A marker
# blocks only while its owning process is still alive, so markers from finished
# runs are reclaimed by the next reader and a finished lane never suppresses a
# later run - the negative-control bound (never offer NOTHING while claimable
# rows exist) holds across runs, not just within one. $PID anchors each lane's
# start offset for variety. The in-run hashtable still prevents a lane from
# re-offering its own rows.
$offeredThisRun = @{}
function Test-OfferMarkerAvailable {
  param([string]$TaskId)
  try {
    $key = ($Ledger -replace '[^A-Za-z0-9]', '_')
    $dir = Join-Path ([System.IO.Path]::GetTempPath()) ('run-queue-offers-' + $key)
    if (-not (Test-Path -LiteralPath $dir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }
    $marker = Join-Path $dir ($TaskId + '.offer')
    if (Test-Path -LiteralPath $marker) {
      # A marker blocks only while its owning lane is still alive. A dead
      # owner's marker is reclaimed here; an unreadable one falls back to a
      # 2-minute age bound so a corrupt file cannot suppress forever.
      $ownerPid = 0
      try { $ownerPid = [int](Get-Content -LiteralPath $marker -TotalCount 1 -ErrorAction Stop) } catch { $ownerPid = 0 }
      $ownerAlive = $false
      if ($ownerPid -gt 0) {
        if ($ownerPid -eq $PID) { $ownerAlive = $true }
        else { $ownerAlive = $null -ne (Get-Process -Id $ownerPid -ErrorAction SilentlyContinue) }
      } else {
        $ageMin = (([DateTime]::Now) - (Get-Item -LiteralPath $marker).LastWriteTime).TotalMinutes
        $ownerAlive = ($ageMin -lt 2)
      }
      if ($ownerAlive) { return $false }
      Remove-Item -LiteralPath $marker -Force -ErrorAction SilentlyContinue
    }
    try {
      $fs = [System.IO.File]::Create($marker, 1, [System.IO.FileShare]::None)
      try {
        $b = [System.Text.Encoding]::ASCII.GetBytes("$PID")
        $fs.Write($b, 0, $b.Length)
      } finally { $fs.Close() }
      return $true
    } catch [System.IO.IOException] {
      return $false   # lost an atomic-create race: the row is taken by a live lane
    }
  } catch { return $true }   # fail-open: infrastructure trouble never suppresses an offer
}
function Select-OfferRow {
  param([array]$Free, [hashtable]$InRunOffered)
  if (-not $Free -or $Free.Count -eq 0) { return $null }
  $laneOffset = $PID % $Free.Count
  for ($k = 0; $k -lt $Free.Count; $k++) {
    $c = $Free[($laneOffset + $k) % $Free.Count]
    if ($InRunOffered.ContainsKey($c.Id)) { continue }
    if (Test-OfferMarkerAvailable -TaskId $c.Id) { return $c }
  }
  return $null
}
for ($i = 1; $i -le $MaxIterations; $i++) {
  $free = Get-ClaimableOpenTasks
  if (-not $free -or $free.Count -eq 0) {
    # B5-0900: the seed branch. It sits INSIDE the drain arm on purpose - this is
    # the only state .agent/AGENT_LOOP.md LOOP step 1 can act in, so putting it
    # anywhere else would make it unreachable again. Opt-in via
    # -MaxSeedInvocations, default 0, so today's behaviour is unchanged until a
    # human decides the fleet may seed itself. `continue` (with the same cooldown
    # the task path uses) so the next iteration re-censuses and picks up whatever
    # the seeder wrote; the stagnation counter is deliberately NOT touched,
    # because a seed flips no DONE row and counting it would trip condition (b)
    # immediately after a successful seed.
    if ($MaxSeedInvocations -gt 0 -and
        $seedInvocations -lt $MaxSeedInvocations -and
        (Test-SeedableQueue)) {
      $seedOrdinal = $seedInvocations + 1
      if ($DryRun) {
        Write-Output ("[$i] Queue drained and seedable: DRY RUN would invoke '" + $AgentCli + " " + $AgentArgs + "' once with the seed prompt (AGENT_LOOP.md LOOP step 1, seed invocation " + $seedOrdinal + "/" + $MaxSeedInvocations + ").")
        Write-Output ("[$i] DRY RUN ends the run here. Nothing is invoked, so the queue stays drained and the next iteration would decide identically - printing that decision once is the honest answer, not once per iteration.")
        break
      }
      Write-Output ("[$i] Queue drained and seedable: one agent invocation to seed ONE new OPEN row (seed invocation " + $seedOrdinal + "/" + $MaxSeedInvocations + ").")
      $openBefore = @(Get-LedgerRows | Where-Object { $_.Status -eq 'OPEN' }).Count
      $seedInvocations++
      # Same native-argument safety as the task path (B5-0628): Windows
      # PowerShell 5.1 does not escape embedded double quotes for a native
      # executable, so the prompt is neutralised identically here rather than
      # trusted to be quote-free.
      $seedArgList = @()
      if ($AgentArgs -ne '') { $seedArgList += $AgentArgs }
      $seedArgList += ($SeedPromptTemplate -replace '"', "'")
      & $AgentCli @seedArgList
      Write-Output ("[$i] Seed agent exit code: " + $LASTEXITCODE + ".")
      $openAfter = @(Get-LedgerRows | Where-Object { $_.Status -eq 'OPEN' }).Count
      if ($openAfter -le $openBefore) {
        Write-Output ("[$i] NOTE: the seed invocation added no new OPEN row (" + $openBefore + " before, " + $openAfter + " after). Seeding nothing is a legitimate outcome under AGENT_LOOP step 1 when nothing genuinely useful can be grounded; if that agent reported success, the report is false and the next iteration will hold the branch and stop.")
      }
      if ($i -ge $MaxIterations) {
        Write-Output ("[$i] The run ended on its seed iteration, so the row it just wrote is unworked. Raise -MaxIterations to have this run pick it up.")
      } else {
        Start-Sleep -Seconds $CooldownSeconds
      }
      continue
    }
    Write-Output "[$i] Queue drained: no OPEN task without a live claim. Done."
    break
  }
  # B5-1004: distribute the offer instead of always taking the head. The old
  # code picked $free[0] on every iteration, so N concurrent lanes (which all
  # start at iteration 1 on the same census) were all offered the same row and
  # only the atomic claim file stopped them colliding - a correctness backstop
  # doing a scheduler's job. See Select-OfferRow above.
  $pick = Select-OfferRow -Free $free -InRunOffered $offeredThisRun
  if (-not $pick) {
    Write-Output "[$i] Every claimable row has already been offered by this run; further iterations would duplicate offers. Done."
    break
  }
  $offeredThisRun[$pick.Id] = $true
  $doneBefore = @(Get-LedgerRows | Where-Object { $_.Status -eq 'DONE' }).Count
  Write-Output "[$i] Claiming lane for $($pick.Id) ($($free.Count) claimable OPEN)."

  $prompt = $TaskPromptTemplate.Replace('__TASK_ID__', $pick.Id)
  if ($DryRun) {
    Write-Output "[$i] DRY RUN: would invoke '$AgentCli $AgentArgs' for $($pick.Id)."
    continue
  }

  $argList = @()
  if ($AgentArgs -ne '') { $argList += $AgentArgs }
  # Native-argument safety (B5-0628). Windows PowerShell 5.1 does NOT escape
  # embedded double quotes when handing an argument to a native executable: the
  # quotes act as argument delimiters, so a prompt containing them is split into
  # several argv entries and a subcommand-style CLI reads a trailing fragment as
  # a command name. Reproduced with `hermes -z` on this very prompt: a task
  # template containing the phrase "Reusable lesson" exited 2 with
  # "is not a `hermes` command", while the identical prompt with its double
  # quotes removed ran to completion and exited 0. Newlines alone were tested
  # separately and are SAFE, so line breaks are preserved and only the quotes
  # are neutralised - this is the minimal fix the evidence supports, not a
  # blanket rewrite of the prompt. Single quotes read the same to the model.
  $safePrompt = $prompt -replace '"', "'"
  $argList += $safePrompt
  & $AgentCli @argList
  $exitCode = $LASTEXITCODE
  Write-Output "[$i] Agent exit code: $exitCode."

  $doneAfter = @(Get-LedgerRows | Where-Object { $_.Status -eq 'DONE' }).Count
  if ($doneAfter -gt $doneBefore) { $stagnant = 0 }
  else {
    $stagnant++
    Write-Output "[$i] No DONE flip this iteration (stagnant streak: $stagnant/$BlockedStreakLimit)."
  }
  if ($stagnant -ge $BlockedStreakLimit) {
    Write-Output "[$i] Stopping: $BlockedStreakLimit consecutive iterations with no DONE flip. Likely a shared red gate - inspect compile output before re-running."
    break
  }
  if ($i -lt $MaxIterations) { Start-Sleep -Seconds $CooldownSeconds }
}
Write-Output "run-queue finished."
