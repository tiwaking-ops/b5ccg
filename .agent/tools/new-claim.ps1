<#
.SYNOPSIS
  B5-1462: create a task claim file whose started_utc is stamped by THIS TOOL
  from DateTimeOffset UtcNow — never agent prose — and accept the write only
  when the payload agrees with the file's own mtime.

.DESCRIPTION
  The clock-integrity report (docs/reports/clock-integrity-in-agent-
  coordination-files-2026-09-30.md, amending manual §7.5/§11) measured the
  self-reported timestamp as the weakest link in file-based liveness: an LLM
  can emit a plausible ISO string without reading a clock, and B5-1047's
  future-dated claim still blocks a whole gate chain. Rule: either the tool
  writes the timestamp, or the decision reads the filesystem instead.

  Until this tool existed there was NO claim-creation path in tooling —
  .agent/run-queue.ps1 only reads claims (offer/liveness/refusal), and every
  claim file on disk was hand-authored. This adds the creation half:

   * Create mode (default): refuses an existing claim file (creating the file
     IS the claim, per .agent/CLAIMS/README.md — the tool never overwrites),
     stamps started_utc from [DateTimeOffset]::UtcNow at the moment of
     writing, writes UTF-8 strict JSON (task, agent_id, started_utc, ttl_min,
     scope, javac), then runs the ACCEPTANCE CHECK: the written payload's
     started_utc must agree with the new file's LastWriteTimeUtc within
     tolerance, so one liveness signal (a tool-stamped payload) survives
     bulk-mtime events per clock-report rule §6.3.
   * -VerifyOnly: runs the same payload-vs-mtime agreement check against an
     EXISTING claim file, read-only. Exit 0 = AGREE, 1 = DISAGREE or
     unparseable, 2 = missing. Missing is exit 2 deliberately: an absent
     signal must be distinct from a disagreeing one (the B5-0597 class —
     an absent signal is UNKNOWN, never silently folded into another verdict).

   * Reap semantics are NEVER touched (B5-1462 fence; B5-1008 admin-release
     precedent stays the only non-owner removal path). The tool creates or
     reads; it never deletes, and it never edits the ledger.

  Exit codes: 0 created+accepted / verified AGREE; 1 refused (exists, invalid
  id, orphan-claim guard: non-OPEN row, no ledger row, or duplicate rows per
  B5-1722, acceptance disagreement, unparseable); 2 missing file or claims dir.

.PARAMETER TaskId      Task id (B5-NNNN or B5-NNNNa).
.PARAMETER AgentId     Claiming agent_id, verbatim.
.PARAMETER Scope       Scope strings recorded in the claim.
.PARAMETER TtlMinutes  Claim TTL (default 30).
.PARAMETER Javac       Toolchain version recorded in the claim.
.PARAMETER ClaimsDir   Defaults to <repo>/.agent/CLAIMS.
.PARAMETER VerifyOnly  Check an existing claim's payload-vs-mtime agreement instead of creating.
.PARAMETER ToleranceMinutes  Payload-vs-mtime acceptance tolerance (default 60, matched to the runner's future-timestamp bound).

.OUTPUTS
  Exit 0 = claim created and accepted, or existing claim verified AGREE.
  Exit 1 = refused or DISAGREE. Exit 2 = missing (VerifyOnly) / claims dir absent.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$TaskId,
    [string]$AgentId,
    [string[]]$Scope = @('unspecified'),
    [int]$TtlMinutes = 30,
    [string]$Javac = '1.8.0_292',
    [string]$ClaimsDir,
    [switch]$VerifyOnly,
    [int]$ToleranceMinutes = 60
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
if (-not $ClaimsDir) { $ClaimsDir = Join-Path $repoRoot '.agent/CLAIMS' }

if ($TaskId -notmatch '^B5-[0-9]{4}[a-z]?$') {
    Write-Error ("[B5-1462] REFUSED: task id '{0}' is not a B5-NNNN[a] id." -f $TaskId)
    exit 1
}

# Shared acceptance probe: newest-tool-written payload vs filesystem mtime.
# One sampled DateTimeOffset drives both the write and the check, so a healthy
# host measures a near-zero delta and any bulk-mtime event still leaves the
# payload as the surviving, trustworthy signal.
# Diagnostics go through Write-Host, NOT Write-Output: a function whose output
# pipeline carries strings cannot be used as `exit (fn)` -- PowerShell captures
# the strings into the expression value and the int return is lost inside an
# Object[] (self-caught in the B5-1462 fixture run: every verdict coerced to 0).
#
# B5-1913: a payload-vs-mtime check alone CANNOT refuse a future-dated claim.
# The two channels are not independent when one bad value is written to both:
# an agent that stamps local time and labels it Z, or that invents a round
# hour, produces a payload whose mtime comparison can read as agreement, and
# a negative age then compares as maximally fresh (clock-integrity report
# section 2). Measured before this change: VerifyOnly returned AGREE exit 0 at
# 10 min of matched-ahead skew, and at every skew inside the 60 min tolerance
# including 59 min; only 61 min flipped to DISAGREE. So the real clock is
# sampled HERE as a third channel, and a payload ahead of it is refused on its
# own, before the mtime agreement test which cannot see this class.
#
# Tolerance is deliberately the same $ToleranceMinutes the mtime check uses, so
# the two branches cannot disagree about how much imprecision is acceptable.
# Ordering matters and is load-bearing: future-refusal FIRST, because after a
# self-agreeing payload passes mtime there is nothing left to catch it.
function Test-PayloadMtimeAgreement {
    param([string]$Path, [int]$ToleranceMinutes)
    try {
        $raw = [IO.File]::ReadAllText($Path, [System.Text.Encoding]::UTF8)
    } catch {
        Write-Host ("[B5-1462] DISAGREE(UNREADABLE): {0} -- {1}" -f $Path, $_.Exception.Message)
        return 1
    }
    try {
        $j = $raw | ConvertFrom-Json -ErrorAction Stop
    } catch {
        Write-Host ("[B5-1462] DISAGREE(UNPARSEABLE): {0} -- {1}" -f $Path, $_.Exception.Message.Split([char]10)[0])
        return 1
    }
    if (-not $j.started_utc) {
        # B5-1915: name the near-miss instead of reporting only "no payload". A
        # claim written with task_id/claimed_utc instead of task/started_utc (the
        # B5-1613 shape) parses cleanly as JSON and carries a real timestamp, so
        # a reader looking for started_utc reports NO-PAYLOAD and the liveness
        # join silently cannot see this claim at all. Detectable, so say so.
        if ($j.claimed_utc -or $j.task_id) {
            # Build the field-name list as a plain string BEFORE interpolating it:
            # an array expression inside a -f format argument coerces to
            # "System.Object[]" (self-caught in the B5-1915 fixture run).
            $present = @()
            if ($j.claimed_utc) { $present += 'claimed_utc' }
            if ($j.task_id) { $present += 'task_id' }
            $presentStr = [string]::Join(', ', $present)
            Write-Host ("[B5-1915] DISAGREE(NO-PAYLOAD): {0} has no started_utc field but carries [{1}]. That is the task_id/claimed_utc variant: it breaks the three-signal liveness join because the reader looks for started_utc, so this claim is invisible to the queue rather than merely malformed. Field contract is .agent/CLAIMS/README.md: task, started_utc, ttl_min, scope, javac. Re-create the claim through this tool." -f $Path, $presentStr)
        } else {
            Write-Host ("[B5-1462] DISAGREE(NO-PAYLOAD): {0} carries no started_utc field." -f $Path)
        }
        return 1
    }
    # Strongly typed init: PS 5.1 cannot resolve TryParse(String, ref) against a
    # null-typed variable (the same convention validate-heartbeats.ps1 uses).
    $parsed = [DateTime]::MinValue
    if (-not [DateTime]::TryParse([string]$j.started_utc, [ref]$parsed)) {
        # Write-Host, NOT Write-Output (B5-1913): this line was Write-Output, which
        # pushed a string into the function's output pipeline and swallowed the
        # `return 1` inside an Object[] exactly as the header comment describes --
        # so an UNPARSEABLE-TIMESTAMP claim exited 0, reading as a healthy claim.
        # Proven by probe before the fix; see the B5-1913 report. An unparseable
        # timestamp is UNKNOWN, never AGREE (CLAIMS/README.md).
        Write-Host ("[B5-1462] DISAGREE(UNPARSEABLE-TIMESTAMP): '{0}'" -f [string]$j.started_utc)
        return 1
    }
    $payloadUtc = $(if ($parsed.Kind -eq [DateTimeKind]::Local) { $parsed.ToUniversalTime() } else { [DateTime]::SpecifyKind($parsed, [DateTimeKind]::Utc) })

    # --- B5-1913: refuse a payload ahead of the real clock, before mtime ------
    # A negative age is not a measurement. Per clock-integrity report 5.4 it must
    # never reach a comparison, so it is caught here at the parse boundary and
    # reported as DISAGREE, never as a fresh claim. Name BOTH readings: the
    # payload and the sampled clock, so the diagnosis is legible without re-running.
    $sampledUtc = [DateTimeOffset]::UtcNow.UtcDateTime
    $aheadMin = ($payloadUtc - $sampledUtc).TotalMinutes
    if ($aheadMin -gt $ToleranceMinutes) {
        Write-Host ("[B5-1913] payload started_utc [{0}] is {1:N1} min AHEAD of sampled UtcNow [{2}] (tolerance {3} min)." -f `
            [string]$j.started_utc, $aheadMin, $sampledUtc.ToString('yyyy-MM-ddTHH:mm:ssZ'), $ToleranceMinutes)
        Write-Host "[B5-1913] DISAGREE: a future-dated payload is not a measurement. Two causes, both seen on this tree: (a) local time stamped as UTC -- check the host TZ offset, this host runs UTC+13, so local 18:17 is UTC 05:17, not 18:17Z; (b) a rounded or invented value that no clock ever read. Either way the resulting age is NEGATIVE and compares as maximally fresh, so the claim would read live forever. Re-create the claim through this tool so the tool authors the stamp."
        return 1
    }

    $mtimeUtc = (Get-Item -LiteralPath $Path).LastWriteTimeUtc
    $deltaMin = ($payloadUtc - $mtimeUtc).TotalMinutes
    Write-Host ("[B5-1462] payload started_utc [{0}] vs file mtime [{1}] delta {2:N3} min (tolerance {3} min)" -f `
        [string]$j.started_utc, $mtimeUtc.ToString('yyyy-MM-ddTHH:mm:ssZ'), $deltaMin, $ToleranceMinutes)
    if ([math]::Abs($deltaMin) -gt $ToleranceMinutes) {
        Write-Host "[B5-1462] DISAGREE: payload-vs-mtime delta beyond tolerance. The payload is not a surviving liveness signal; re-create the claim with the tool."
        return 1
    }
    Write-Host "[B5-1462] AGREE: payload-vs-mtime within tolerance; the tool-stamped payload survives as a liveness signal."
    return 0
}

if (-not (Test-Path -LiteralPath $ClaimsDir)) {
    Write-Error ("[B5-1462] claims directory not found: {0}" -f $ClaimsDir)
    exit 2
}

$path = Join-Path $ClaimsDir ($TaskId + '.json')

if ($VerifyOnly) {
    if (-not (Test-Path -LiteralPath $path)) {
        Write-Output ("[B5-1462] MISSING: {0} -- absence is UNKNOWN, never AGREE and never DISAGREE." -f $path)
        exit 2
    }
    $rc = Test-PayloadMtimeAgreement -Path $path -ToleranceMinutes $ToleranceMinutes
    exit $rc
}

# ---- create mode ----------------------------------------------------------------
# B5-1722: orphan-claim guard, read-only over the ledger, CREATE MODE ONLY (the
# -VerifyOnly branch above keeps its own 0/1/2 contract untouched, because
# verifying an existing orphan claim's payload-vs-mtime agreement is exactly the
# B5-1645/B5-1677 reconciliation use case the verify path must still serve).
# Absence of the claim file is NECESSARY but NOT SUFFICIENT (B5-0622): a claim
# against a row that is not OPEN is an ORPHAN no runner will ever offer, and a
# claim against an id with no row at all is a claim the queue cannot see. Refuse
# loudly, create nothing, and let the agent re-read the ledger. Duplicate ledger
# rows for one id (the B5-0618 class, where status is keyed by id and the last
# row silently wins) are refused too, because the claim's meaning would be
# ambiguous. This is a PRE-WRITE check: necessary, never sufficient - the
# post-write duplicate-ID census (run-dup-census.ps1) remains the race-proof
# gate, exactly as 00_BOOT step 9 states.
$ledgerPath = Join-Path $repoRoot '.agent/TASK_LEDGER.md'
if (-not (Test-Path -LiteralPath $ledgerPath)) {
    Write-Error ("[B5-1722] REFUSED: ledger not found at {0} - cannot verify the row; no claim was created." -f $ledgerPath)
    exit 1
}
$rowLines = @()
foreach ($ln in (Get-Content -LiteralPath $ledgerPath -Encoding UTF8)) {
    if ($ln -match ('^\|+\s*' + [regex]::Escape($TaskId) + '\s*\|')) { $rowLines += $ln }
}
if ($rowLines.Count -gt 1) {
    Write-Output ("[B5-1722] REFUSED: {0} matches {1} ledger rows - a duplicate id silently drops a task (last row wins). Run the duplicate-ID census and let exactly one writer renumber to a NON-ADJACENT id; no claim was created." -f $TaskId, $rowLines.Count)
    exit 1
}
if ($rowLines.Count -eq 1) {
    $cells = ($rowLines[0] -split '\|') | ForEach-Object { $_.Trim() }
    $st = $null
    foreach ($c in $cells) { if (@('OPEN','CLAIMED','DONE','BLOCKED','SUPERSEDED','VOID') -contains $c) { $st = $c; break } }
    if ($st -ne 'OPEN') {
        Write-Output ("[B5-1722] REFUSED: ledger row {0} reads {1}, not OPEN - a claim on a non-OPEN row is an ORPHAN no runner will ever offer (B5-0622). Re-read the row and claim only OPEN work; no claim was created." -f $TaskId, $(if ($st) { $st } else { 'UNRECOGNISED-STATUS' }))
        exit 1
    }
} else {
    Write-Output ("[B5-1722] REFUSED: no ledger row for {0} - a claim without a row is a claim the queue cannot see (seed the OPEN row first, then claim it; the B5-1645/B5-1677 orphan-claim class). No claim was created." -f $TaskId)
    exit 1
}
if (Test-Path -LiteralPath $path) {
    # Atomicity per CLAIMS/README: the file's existence IS the claim. Never
    # overwrite, never merge -- refuse and let the agent pick another task.
    Write-Output ("[B5-1462] REFUSED: {0} already exists (claim taken). No file was modified." -f $path)
    exit 1
}

if (-not $AgentId) {
    Write-Error "[B5-1462] REFUSED: -AgentId is required in create mode."
    exit 1
}

$now = [DateTimeOffset]::UtcNow          # B5-1462: the TOOL authors the timestamp, not the agent
$stamp = $now.ToString('yyyy-MM-ddTHH:mm:ssZ')
$claim = [ordered]@{
    task        = $TaskId
    agent_id    = $AgentId
    started_utc = $stamp
    ttl_min     = $TtlMinutes
    scope       = $Scope
    javac       = $Javac
}
$json = ($claim | ConvertTo-Json -Depth 4)
[IO.File]::WriteAllText($path, $json, [System.Text.Encoding]::UTF8)   # B5-1002 encoding convention
Write-Output ("[B5-1462] CREATED {0} (started_utc tool-stamped {1})" -f $path, $stamp)

$rc = Test-PayloadMtimeAgreement -Path $path -ToleranceMinutes $ToleranceMinutes
exit $rc
