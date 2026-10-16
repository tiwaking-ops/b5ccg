<#
.SYNOPSIS
  B5-1915: write your own heartbeat file with `utc` stamped by THIS TOOL from
  DateTimeOffset UtcNow - never agent prose - and accept the write only when the
  payload agrees with the file's own mtime and is not ahead of the real clock.

.DESCRIPTION
  Why this tool exists: `.agent/update_heartbeat.ps1` is not a tool. It is one
  session's scratch script holding that session's literal heartbeat payload and
  a hardcoded absolute path, untracked. So at the time B5-1915 ran, the claim
  side of the agent-timestamp problem had a remedy (new-claim.ps1, B5-1462) and
  the heartbeat side had none - every heartbeat in the store was hand-authored,
  which is the weakest link in a liveness design, in the one file whose entire
  purpose is to be a trustworthy signal.

  Measured before this tool existed: three heartbeat files carried `utc` values
  186 to 852 minutes AHEAD of real UTC. Two were local time stamped as `Z` on a
  host running UTC+13; two were invented round values (12:00:00Z, 23:00:00Z)
  that no clock ever read. Each agent had written the SAME wrong value into both
  its claim and its heartbeat, so the natural cross-check - do the two signals
  corroborate each other? - could not see the class at all. See
  docs/reports/clock-integrity-in-agent-coordination-files-2026-09-30.md.

  The three rules this tool enforces:

   * THE TOOL AUTHORS THE TIME. -Utc is stamped from UtcNow at the moment of
     writing. The agent never types a timestamp, which is the only change that
     reduces this failure to zero rather than detecting it.

   * NEVER ANOTHER AGENT'S HEARTBEAT. If the target file already exists and its
     agent_id differs from -AgentId, the write is REFUSED and nothing is
     modified. 00_BOOT step 3 says never edit another agent's heartbeat, not even
     a colliding one; this is that rule as code rather than as prose. Same-agent
     refresh IS allowed and expected, because a heartbeat is a periodic signal.

   * A NEGATIVE AGE NEVER REACHES A COMPARISON. A payload ahead of the sampled
     real clock is refused BEFORE the payload-vs-mtime comparison is attempted,
     because after a self-agreeing payload clears mtime there is nothing left to
     catch it (B5-1913). Both checks share one tolerance so they cannot disagree
     about acceptable imprecision.

  Schema written (HEARTBEATS/README.md field contract): schema_version, agent_id,
  utc, state, live_claims always present, plus current_task, javac, notes.
  `live_claims` is a positive assertion that you hold nothing when empty, so it is
  written even when empty. `notes` is prose and is never parsed by any reader.

  -VerifyOnly runs the same two acceptance checks against an EXISTING file,
  read-only, and is the supported way to inspect someone else's heartbeat without
  writing to it.

.OUTPUTS
  Exit 0 = heartbeat written and accepted, or existing heartbeat verified AGREE.
  Exit 1 = refused (foreign agent_id, invalid id, future-dated payload, unreadable,
  unparseable, missing required field on verify) or payload-vs-mtime disagreement.
  Exit 2 = heartbeat file absent on -VerifyOnly (UNKNOWN, never AGREE), or the
  heartbeats directory is absent.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$AgentId,
    [ValidateSet('active', 'idle', 'busy')][string]$State = 'idle',
    [string]$CurrentTask,
    [string[]]$LiveClaims = @(),
    [string]$Javac = '1.8.0_292',
    [string]$Notes = '',
    [string]$HeartbeatsDir,
    [switch]$VerifyOnly,
    [int]$ToleranceMinutes = 60
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
if (-not $HeartbeatsDir) { $HeartbeatsDir = Join-Path $repoRoot '.agent/HEARTBEATS' }

# Diagnostics go through Write-Host, NOT Write-Output. A function whose output
# pipeline carries strings cannot be used as `exit (fn)`: PowerShell captures the
# strings into the expression value and the int return is lost inside an Object[],
# so every verdict coerces to 0 and a refusal reads as success. This is not
# hypothetical - new-claim.ps1 shipped exactly that bug in one branch until
# B5-1913 found it (an unparseable timestamp exited 0). Keep every branch on
# Write-Host.

# ---------------------------------------------------------------------------
# Shared acceptance probe: payload vs sampled real clock, then payload vs mtime.
# Returns 0 AGREE / 1 DISAGREE. Ordering is load-bearing; see the header.
# ---------------------------------------------------------------------------
function Test-HeartbeatPayload {
    param([string]$Path, [int]$ToleranceMinutes)
    try {
        $raw = [IO.File]::ReadAllText($Path, [System.Text.Encoding]::UTF8)
    } catch {
        Write-Host ("[B5-1915] DISAGREE(UNREADABLE): {0} -- {1}" -f $Path, $_.Exception.Message)
        return 1
    }
    try {
        $j = $raw | ConvertFrom-Json -ErrorAction Stop
    } catch {
        Write-Host ("[B5-1915] DISAGREE(UNPARSEABLE): {0} -- {1}" -f $Path, $_.Exception.Message.Split([char]10)[0])
        return 1
    }
    if (-not $j.utc) {
        Write-Host ("[B5-1915] DISAGREE(NO-PAYLOAD): {0} carries no utc field." -f $Path)
        return 1
    }
    # Strongly typed init: PS 5.1 cannot resolve TryParse(String, ref) against a
    # null-typed variable (the same convention new-claim.ps1 and
    # validate-heartbeats.ps1 use).
    $parsed = [DateTime]::MinValue
    if (-not [DateTime]::TryParse([string]$j.utc, [ref]$parsed)) {
        Write-Host ("[B5-1915] DISAGREE(UNPARSEABLE-TIMESTAMP): '{0}'" -f [string]$j.utc)
        return 1
    }
    $payloadUtc = $(if ($parsed.Kind -eq [DateTimeKind]::Local) { $parsed.ToUniversalTime() } else { [DateTime]::SpecifyKind($parsed, [DateTimeKind]::Utc) })

    # Refuse a payload ahead of the real clock FIRST. A negative age is not a
    # measurement: it compares as maximally fresh, so the heartbeat would read
    # live forever. Name both readings so the diagnosis stands on its own.
    $sampledUtc = [DateTimeOffset]::UtcNow.UtcDateTime
    $aheadMin = ($payloadUtc - $sampledUtc).TotalMinutes
    if ($aheadMin -gt $ToleranceMinutes) {
        Write-Host ("[B5-1915] utc [{0}] is {1:N1} min AHEAD of sampled UtcNow [{2}] (tolerance {3} min)." -f `
            [string]$j.utc, $aheadMin, $sampledUtc.ToString('yyyy-MM-ddTHH:mm:ssZ'), $ToleranceMinutes)
        Write-Host "[B5-1915] DISAGREE: a future-dated heartbeat is not a measurement. Two causes seen on this tree: (a) local time stamped as UTC - this host runs UTC+13, so local 18:17 is UTC 05:17, not 18:17Z; (b) a rounded or invented value that no clock ever read. Rewrite it with this tool so the tool authors the stamp."
        return 1
    }

    # Retained from the claim-side contract (B5-1462): catches the OPPOSITE class,
    # a payload that is stale or hand-set relative to the file that carries it,
    # which the real-clock comparison above cannot see.
    $mtimeUtc = (Get-Item -LiteralPath $Path).LastWriteTimeUtc
    $deltaMin = ($payloadUtc - $mtimeUtc).TotalMinutes
    Write-Host ("[B5-1915] payload utc [{0}] vs file mtime [{1}] delta {2:N3} min (tolerance {3} min)" -f `
        [string]$j.utc, $mtimeUtc.ToString('yyyy-MM-ddTHH:mm:ssZ'), $deltaMin, $ToleranceMinutes)
    if ([math]::Abs($deltaMin) -gt $ToleranceMinutes) {
        Write-Host "[B5-1915] DISAGREE: payload-vs-mtime delta beyond tolerance. Rewrite the heartbeat with this tool."
        return 1
    }
    Write-Host "[B5-1915] AGREE: payload is not ahead of the sampled clock and agrees with its file mtime within tolerance."
    return 0
}

if (-not (Test-Path -LiteralPath $HeartbeatsDir)) {
    Write-Error ("[B5-1915] heartbeats directory not found: {0}" -f $HeartbeatsDir)
    exit 2
}

# Filenames carry the agent_id verbatim, matching the store's existing convention
# (parentheses and spaces are legal on NTFS). ':' and '/' are not legal in a
# Windows filename and are replaced per HEARTBEATS/README.md sanitisation, so an
# id like `solar-pro4:free` resolves to `solar-pro4-free.json` rather than
# failing the write.
$stem = $AgentId -replace '[:/]', '-'
$path = Join-Path $HeartbeatsDir ($stem + '.json')

if ($VerifyOnly) {
    if (-not (Test-Path -LiteralPath $path)) {
        Write-Output ("[B5-1915] MISSING: {0} -- absence is UNKNOWN, never AGREE and never DISAGREE." -f $path)
        exit 2
    }
    $rc = Test-HeartbeatPayload -Path $path -ToleranceMinutes $ToleranceMinutes
    exit $rc
}

# ---- write mode --------------------------------------------------------------
# Refuse another agent's heartbeat. Read-only against the existing file, and no
# write happens unless this check passes, so a refusal leaves the file's bytes
# untouched. Identity is compared verbatim, not normalised: Get-NormName collapses
# `... free 2` and `... free2` onto one key, so a normalised comparison would call
# two distinct sessions the same agent and let either overwrite the other.
if (Test-Path -LiteralPath $path) {
    $existing = $null
    try {
        $existing = ([IO.File]::ReadAllText($path, [System.Text.Encoding]::UTF8) | ConvertFrom-Json -ErrorAction Stop)
    } catch {
        Write-Output ("[B5-1915] REFUSED: {0} exists but does not parse, so its owner cannot be established. Refusing to write; a human or the owner must resolve it. No file was modified." -f $path)
        exit 1
    }
    if ([string]$existing.agent_id -ne $AgentId) {
        Write-Output ("[B5-1915] REFUSED: {0} is held by agent_id '{1}', not '{2}'. 00_BOOT step 3 forbids editing another agent's heartbeat, not even a colliding one. No file was modified." -f $path, [string]$existing.agent_id, $AgentId)
        exit 1
    }
}

# THE TOOL AUTHORS THE TIME (B5-1915). The agent never types a timestamp.
$now = [DateTimeOffset]::UtcNow
$stamp = $now.ToString('yyyy-MM-ddTHH:mm:ssZ')
$heartbeat = [ordered]@{
    schema_version = 1
    agent_id       = $AgentId
    utc            = $stamp
    state          = $State
    current_task   = $(if ($CurrentTask) { $CurrentTask } else { $null })
    live_claims    = @($LiveClaims)
    javac          = $Javac
    notes          = $Notes
}
$json = ($heartbeat | ConvertTo-Json -Depth 4)
[IO.File]::WriteAllText($path, $json, (New-Object System.Text.UTF8Encoding($false)))
Write-Output ("[B5-1915] WROTE {0} (utc tool-stamped {1}, state {2}, live_claims {3})" -f $path, $stamp, $State, @($LiveClaims).Count)

$rc = Test-HeartbeatPayload -Path $path -ToleranceMinutes $ToleranceMinutes
exit $rc