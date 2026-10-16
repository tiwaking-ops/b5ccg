<#
.SYNOPSIS
  Read-only validator for the agent heartbeat store (heartbeat-schema-proposal Sec.7).

.DESCRIPTION
  Audits every entry in .agent/HEARTBEATS/ against heartbeat schema v1 and reports
  per-file findings. STRICTLY READ-ONLY: creates, edits, moves and deletes nothing.

  Design rules, each traceable to a failure this repo actually hit:

   * One unparseable file must not abort the audit. A survey using ConvertFrom-Json
     over *.json THREW on the first YAML-frontmatter file and silently returned zero
     rows. Every file is read inside its own try/catch; findings are COLLECTED and the
     exit code is decided at the END. A parse failure is a finding, never a crash.

   * The checks are INDEPENDENT of the writer, so they can go red on real data. A
     self-check whose predicate matches the code it checks is a tautology: the B5-0613
     version reported 312==312 green while 9 rows carried a corrupt status.

   * The FILENAME is not authoritative. Windows forbids both ':' and '/' in filenames
     and four agent_ids contain one or both, so "stem == agent_id" is unsatisfiable
     for them. Resolution order: exact stem match, then an explicit _registry.json
     entry, then a ':'/'/' -> '-' sanitised match (the repo's own report-filename
     convention).

   * live_claims is validated against the RAW TEXT, not the parsed object, because
     PowerShell 5.1 ConvertFrom-Json does not round-trip array-ness reliably.

   * B5-1469: a heartbeat whose utc payload reads more than 60 minutes ahead of
     sampled UtcNow OR more than 60 minutes ahead of its own file mtime is a
     FUTURE SKEW finding, reported with the value and both deltas. Tolerance is
     matched to the runner's 60-minute bound (B5-0952/B5-1466) so the two
     instruments agree. The finding is a data-quality defect, NOT a liveness
     input: mtime stays the sole liveness source per the run-queue heartbeat
     index, and this check never changes TTL or reap semantics.

   * mtime is reported next to the parsed value: during the 2026-09-27 survey mtime
     was the most reliable liveness signal because payloads were malformed in 19 of 32
     files while the filesystem maintained mtime regardless.

   * Unknown/legacy keys are reported as NOTES, not violations. The schema does not
     forbid extra properties, and failing on them would make the tool noise.

.PARAMETER Directory  Heartbeat directory. Default <repo>/.agent/HEARTBEATS
.PARAMETER Json       Emit machine-readable JSON instead of the table.
.PARAMETER TtlMinutes Freshness window for the mtime column (default 30).
.PARAMETER FutureSkewMinutes Future-skew tolerance for the utc payload vs sampled UtcNow and vs the file's own mtime (default 60, matched to the runner bound).

.OUTPUTS
  Exit 0 = every LIVE heartbeat conforms, there is no live identity collision, and no
           archived file carries a lookalike filename.
  Exit 1 = a live-store defect: a non-conforming live file, or one live agent_id claimed
           by more than one live file, or a FILENAME LOOKALIKE in any directory.
  Exit 2 = the directory is missing or empty.

  B5-2340 -- LIVE vs ARCHIVED. The store root is the live store; _retired/ and
  _quarantine/ are the archive, out of the live store by decision under the retirement
  policy in HEARTBEATS/README.md (2026-09-29), where a reader resolving an archived
  identity treats its signal as UNKNOWN. The two populations are counted and printed
  separately and an identity collision requires two LIVE files. Measured on the store as
  it stood: 111 of 336 enumerated files were archived, and 5 of 6 reported collisions
  were a live file beside its own archived copy -- the state every correct retirement
  produces, so the old rule reported a defect the moment the approved policy was obeyed.
  Archived pairs are still PRINTED, under ARCHIVED COPIES, because the archived sibling
  is the only cross-file evidence that a lookalike name exists.
#>
[CmdletBinding()]
param(
    [string]$Directory,
    [switch]$Json,
    [switch]$SelfTest,
    [int]$TtlMinutes = 30,
    [int]$FutureSkewMinutes = 60
)

$ErrorActionPreference = "Continue"      # deliberate: a bad file is a finding, not a crash
$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
if (-not $Directory) { $Directory = Join-Path $repoRoot ".agent/HEARTBEATS" }

# =================================================================================
# B5-2340 -SelfTest. A check is only worth its cost if it can fail, and this pass
# changed what "a collision" and "the store" MEAN, so both new verdicts are proved
# able to go red on a fixture rather than asserted. Five cases, run against the real
# script via -Directory so the code under test is this code, not a restatement:
#
#   1 RED   live-vs-live collision is a collision and exits 1.
#   2 RED   an ARCHIVED lookalike filename still exits 1, with 0 live non-conforming
#          and 0 live collisions -- this is B5-1062's guarantee, and the change that
#          stopped the archive voting on live-store identity could have dropped it.
#   3 GREEN live file + its own archived copy is NOT a collision and exits 0. This is
#          the case the old rule called a defect on every correct retirement.
#   4 GREEN archived payload drift alone exits 0, yet the archived file is still
#          REPORTED non-conforming -- the finding is demoted, never swallowed.
#   5 LEGIB one collision finding prints two DISTINCT paths, never the same one twice.
# =================================================================================
if ($SelfTest) {
    $toolSelf = $PSCommandPath
    if (-not $toolSelf) { $toolSelf = Join-Path $repoRoot '.agent/tools/validate-heartbeats.ps1' }
    $fxRoot = Join-Path ([System.IO.Path]::GetTempPath()) ('b5-2340-vh-' + [guid]::NewGuid().ToString('N').Substring(0, 8))
    $fails = 0

    function New-Fixture([string]$name) {
        $d = Join-Path $fxRoot $name
        New-Item -ItemType Directory -Path $d -Force | Out-Null
        New-Item -ItemType Directory -Path (Join-Path $d '_retired') -Force | Out-Null
        return $d
    }
    function Put-Hb([string]$dir, [string]$rel, [string]$agentId, [bool]$preSchema) {
        $p = Join-Path $dir $rel
        $parent = Split-Path -Parent $p
        if (-not (Test-Path -LiteralPath $parent)) { New-Item -ItemType Directory -Path $parent -Force | Out-Null }
        $body = if ($preSchema) {
            '{ "agent_id": "' + $agentId + '", "updated_utc": "2026-01-01T00:00:00Z", "state": "idle", "notes": "fixture, pre-schema" }'
        } else {
            '{ "schema_version": 1, "agent_id": "' + $agentId + '", "utc": "2026-01-01T00:00:00Z", "state": "idle", "current_task": null, "live_claims": [], "javac": "1.8.0_292", "notes": "fixture" }'
        }
        [System.IO.File]::WriteAllText($p, $body, [System.Text.UTF8Encoding]::new($false))
    }
    # Runs THIS script on a fixture and returns exit code, parsed rows and stdout.
    function Invoke-Fixture([string]$dir) {
        $raw = (& powershell -NoProfile -ExecutionPolicy Bypass -File $toolSelf -Directory $dir -Json 2>$null | Out-String)
        $code = $LASTEXITCODE
        $lines = $raw -split "`r?`n"
        $banner = ($lines | Select-String -Pattern '^===' | Select-Object -First 1).LineNumber
        $prefix = if ($banner) { ($lines[0..($banner - 2)] -join "`n") } else { ($lines -join "`n") }
        $rows = @()
        try { $d = $prefix | ConvertFrom-Json -ErrorAction Stop; if ($null -ne $d) { $rows = @($d) } } catch { $rows = @() }
        return @{ Exit = $code; Rows = $rows; Raw = $raw }
    }
    function Assert-Case([string]$label, [bool]$ok, [string]$detail) {
        if ($ok) { Write-Output ("  SELFTEST PASS  {0}  ({1})" -f $label, $detail) }
        else     { Write-Output ("  SELFTEST FAIL  {0}  ({1})" -f $label, $detail); $script:fails++ }
    }
    function Live-CollisionIds($r) {
        return @($r.Rows | Where-Object { $_.Live -and -not [string]::IsNullOrEmpty($_.AgentId) } |
                    Group-Object AgentId | Where-Object { $_.Count -gt 1 } | ForEach-Object { $_.Name })
    }

    # 1 RED -- two LIVE files, one agent_id.
    $d1 = New-Fixture 'c1-live-collision'
    Put-Hb $d1 'dup-a.json' 'dup-agent' $false
    Put-Hb $d1 'dup-b.json' 'dup-agent' $false
    $r1 = Invoke-Fixture $d1
    Assert-Case '1 live-vs-live collision exits 1' `
        ($r1.Exit -eq 1 -and (Live-CollisionIds $r1) -contains 'dup-agent') `
        ("exit=" + $r1.Exit + " liveCollisions=[" + ((Live-CollisionIds $r1) -join ',') + "] expected exit 1 and dup-agent")

    # 2 RED -- ARCHIVED lookalike name must still hold the gate (B5-1062).
    $d2 = New-Fixture 'c2-archived-lookalike'
    Put-Hb $d2 'alpha.json' 'alpha' $false
    Put-Hb $d2 (Join-Path '_retired' ('solar-pro4' + [char]0xF03A + 'free.json')) 'solar-pro4:free' $false
    $r2 = Invoke-Fixture $d2
    $lk2 = @($r2.Rows | Where-Object { @($_.Issues | Where-Object { $_ -like 'FILENAME LOOKALIKE*' }).Count -gt 0 })
    $liveBad2 = @($r2.Rows | Where-Object { $_.Live -and $_.Verdict -ne 'CONFORMS' })
    Assert-Case '2 archived lookalike filename still exits 1' `
        ($r2.Exit -eq 1 -and $lk2.Count -eq 1 -and $liveBad2.Count -eq 0 -and (Live-CollisionIds $r2).Count -eq 0) `
        ("exit=" + $r2.Exit + " lookalikeRows=" + $lk2.Count + " liveNonConforming=" + $liveBad2.Count + " liveCollisions=" + (Live-CollisionIds $r2).Count + " -- exit must be 1 from the ARCHIVED name alone")

    # 3 GREEN -- a live file beside its own archived copy is a retirement, not a collision.
    $d3 = New-Fixture 'c3-archival-pair'
    Put-Hb $d3 'gamma.json' 'gamma' $false
    Put-Hb $d3 (Join-Path '_retired' 'gamma.json') 'gamma' $false
    $r3 = Invoke-Fixture $d3
    Assert-Case '3 live + archived copy is NOT a collision, exits 0' `
        ($r3.Exit -eq 0 -and (Live-CollisionIds $r3).Count -eq 0) `
        ("exit=" + $r3.Exit + " liveCollisions=" + (Live-CollisionIds $r3).Count + " -- under the old rule this store reported 1 collision")

    # 4 GREEN -- archived payload drift is demoted to a report, not a gate, and not swallowed.
    $d4 = New-Fixture 'c4-archived-drift'
    Put-Hb $d4 'delta.json' 'delta' $false
    Put-Hb $d4 (Join-Path '_retired' 'delta.json') 'delta' $true
    $r4 = Invoke-Fixture $d4
    $archBad4 = @($r4.Rows | Where-Object { -not $_.Live -and $_.Verdict -ne 'CONFORMS' })
    Assert-Case '4 archived drift exits 0 yet is still REPORTED' `
        ($r4.Exit -eq 0 -and $archBad4.Count -eq 1) `
        ("exit=" + $r4.Exit + " archivedNonConformingReported=" + $archBad4.Count + " expected 0 and 1")

    # 5 LEGIBILITY -- one collision finding prints two distinct paths.
    $raw5 = (& powershell -NoProfile -ExecutionPolicy Bypass -File $toolSelf -Directory $d1 2>$null | Out-String)
    $arrow = @([regex]::Matches($raw5, '(?m)^\s+->\s+(.+?)\s*$') | ForEach-Object { $_.Groups[1].Value.Trim() })
    $dupPaths = @($arrow | Group-Object | Where-Object { $_.Count -gt 1 })
    Assert-Case '5 one collision finding prints two distinct paths' `
        ($arrow.Count -ge 2 -and $dupPaths.Count -eq 0) `
        ("paths=[" + ($arrow -join ' | ') + "] repeatedPathCount=" + $dupPaths.Count + " -- the pre-fix defect printed one basename twice")

    Remove-Item -LiteralPath $fxRoot -Recurse -Force -ErrorAction SilentlyContinue
    if ($fails -eq 0) { Write-Output '=== validate-heartbeats SELF-TEST GREEN: 5/5, the new verdicts can both fail ==='; exit 0 }
    Write-Output ("=== validate-heartbeats SELF-TEST RED: " + $fails + " case(s) failed ==="); exit 1
}

# ---- canonical schema (heartbeat-schema-proposal Sec.2, as amended 2026-09-27) ----
# 'busy' was added to the enum by human approval after a live agent (opencode
# (me-so-poor), working B5-0620) adopted the schema and chose it. A schema that a
# working agent cannot satisfy is a schema that gets abandoned, not one that gets obeyed.
$ValidState   = @('active', 'idle', 'busy')
$TimestampKey = @('utc', 'updated_utc', 'heartbeat_utc', 'last_heartbeat_utc')  # utc canonical; rest tolerated one cycle
$KnownKey     = @('schema_version','agent_id','utc','state','current_task','live_claims','javac','notes',
                  'updated_utc','heartbeat_utc','last_heartbeat_utc','status','thread_id','model','last_seen_utc',
                  'session_start_utc','tasks_done_this_session','tasks_seeded_this_session','tasks_completed',
                  'scope','task','started_utc','ttl_min','note','completed_tasks','tasks_in_progress',
                  'blocked_count','last_completed','last_compile','conformance','smoke','reusable_lesson',
                  'scope_covered','build_gate_ok','smoke_test_ok','conformance_ok','current_task_id',
                  'state_history','merged_from','session_notes_history')

if (-not (Test-Path -LiteralPath $Directory)) { Write-Error "Heartbeat directory not found: $Directory"; exit 2 }

# ---- agent_id -> filename registry (authoritative for ids a filename cannot hold) --
$registry = $null
$regPath  = Join-Path $Directory '_registry.json'
if (Test-Path -LiteralPath $regPath) {
    try {
        $registry = @{}
        # B5-1002: encoding pinned to explicit UTF-8 (heartbeats are strict JSON, UTF-8 per HEARTBEATS/README.md).
        $rm = ([IO.File]::ReadAllText($regPath, [System.Text.Encoding]::UTF8) | ConvertFrom-Json -ErrorAction Stop).map
        if ($null -ne $rm) { $rm.PSObject.Properties | ForEach-Object { $registry[$_.Name] = $_.Value } }
    } catch { $registry = $null }
}

$now   = [DateTime]::UtcNow
# B5-1062: recurse into _quarantine/. A quarantined file is out of the live store by
# decision, but a lookalike-codepoint name is a DEFECT OF THE NAME, not of the
# location, and the only cross-file evidence of the collision is the sibling's presence.
# Without -Recurse this tool reported 0 collisions on a store that held a live one.
# -File keeps subdirectories from being returned; '*.json' filtering is done below
# rather than via -Filter so that non-.json entries stay visible as EXTENSION findings.
$files = @(Get-ChildItem -LiteralPath $Directory -File -Recurse | Where-Object { $_.Name -ne 'README.md' -and $_.Name -ne '_registry.json' })
if ($files.Count -eq 0) { Write-Error "Heartbeat directory is empty: $Directory"; exit 2 }

# B5-2340: a file's LOCATION is a first-class fact, so resolve the store root once and
# tag every result live vs archived. Before this, Get-ChildItem -Recurse (added by
# B5-1062 so a lookalike NAME in _quarantine/ could not hide) enumerated the archive as
# if it were the live store: on the measured store, 111 of 336 reported files sat in
# _retired/ or _quarantine/, and 5 of the 6 reported IDENTITY COLLISIONS were a live
# file beside its own archived copy. Every correct execution of the approved retirement
# policy (HEARTBEATS/README.md, 2026-09-29) MOVES a file byte-identically into
# _retired/, so a live file and its archived twin are the NORMAL terminal state of a
# retirement, not an identity defect. The policy is explicit that a reader resolving an
# archived identity treats its signal as UNKNOWN, never LIVE or STALE.
#
# The -Recurse STAYS. B5-1062's finding was that a lookalike codepoint in a FILENAME is
# a defect of the name regardless of location, and the only cross-file evidence is the
# sibling's presence; dropping recursion reinstates the false pass that check was written
# to close. What changes is that an archived file no longer votes on live-store identity
# and no longer inflates the live conformance census.
$storeRoot = (Resolve-Path -LiteralPath $Directory).Path.TrimEnd('\','/')

$results = @()

foreach ($f in $files) {
    $issues = New-Object System.Collections.Generic.List[string]
    $notes  = New-Object System.Collections.Generic.List[string]
    $data   = $null
    $raw    = ''

    try { $raw = [IO.File]::ReadAllText($f.FullName, [System.Text.Encoding]::UTF8) } catch { $issues.Add("UNREADABLE: $($_.Exception.Message)"); $raw = '' }

    $stem   = [IO.Path]::GetFileNameWithoutExtension($f.Name)
    $ageMin = [int](($now - $f.LastWriteTimeUtc).TotalMinutes)
    $format = 'unknown'; $tsValue = $null; $tsKey = $null
    # B5-2340: Rel is the store-root-relative path. File (the bare basename) is kept as
    # a separate field because run-verification-battery.ps1's declared non-conforming
    # inventory is keyed on it, and Rel is what every human-facing listing must print.
    # Printing only the basename made a live file and its archived copy render as the
    # SAME line twice inside one collision finding -- evidence that could not be read.
    $rel    = $f.FullName.Substring($storeRoot.Length).TrimStart('\','/')
    $isLive = ($f.DirectoryName.TrimEnd('\','/') -eq $storeRoot)

    if ($raw.Trim() -eq '') {
        $format = 'empty'; $issues.Add('EMPTY: file has no content')
    } elseif ($raw.TrimStart().StartsWith('---')) {
        $format = 'YAML frontmatter'
        $issues.Add('FORMAT: YAML frontmatter, not JSON -- unreadable by any JSON reader')
        if ($raw -match '(?m)^\s*heartbeat_utc\s*:\s*"?([^"\r\n]+)') { $tsKey = 'heartbeat_utc'; $tsValue = $Matches[1].Trim() }
    } else {
        try { $data = $raw | ConvertFrom-Json -ErrorAction Stop; $format = 'JSON' }
        catch { $format = 'unparseable'; $issues.Add("PARSE FAILED: " + $_.Exception.Message.Split([char]10)[0]) }
    }

    if ($null -ne $data) {
        $props = @($data.PSObject.Properties.Name)

        if ('schema_version' -notin $props) { $issues.Add('schema_version missing (pre-schema file)') }

        foreach ($k in $TimestampKey) { if ($k -in $props -and $null -ne $data.$k) { $tsKey = $k; $tsValue = [string]$data.$k; break } }
        if ($null -eq $tsValue) {
            $issues.Add('TIMESTAMP MISSING: no utc / updated_utc / heartbeat_utc / last_heartbeat_utc')
        } else {
            $parsed = [DateTime]::MinValue
            if (-not [DateTime]::TryParse($tsValue, [ref]$parsed)) { $issues.Add("TIMESTAMP UNPARSEABLE: '$tsValue'") }
            else {
                # B5-1469: heartbeat-side future-skew gate, the complement of the
                # claims-side B5-1466 rule in the census tools. A payload clocked more
                # than $FutureSkewMinutes ahead of sampled UtcNow or of its own file
                # mtime is the B5-1008/B5-0952 clock-defect class wearing a heartbeat:
                # report it with the value and both deltas. It is a FINDING only -- it
                # never feeds liveness; mtime stays the sole liveness source.
                $parsedUtc = if ($parsed.Kind -eq [DateTimeKind]::Local) { $parsed.ToUniversalTime() } else { [DateTime]::SpecifyKind($parsed, [DateTimeKind]::Utc) }
                $aheadOfNowMin   = ($parsedUtc - $now).TotalMinutes
                $aheadOfMtimeMin = ($parsedUtc - $f.LastWriteTimeUtc).TotalMinutes
                if ($aheadOfNowMin -gt $FutureSkewMinutes -or $aheadOfMtimeMin -gt $FutureSkewMinutes) {
                    $issues.Add(("FUTURE SKEW: utc '{0}' is {1:N1} min ahead of sampled UtcNow and {2:N1} min ahead of its own file mtime (tolerance {3} min). Finding only -- never feeds liveness; mtime stays the sole liveness source per the run-queue heartbeat index." -f $tsValue, $aheadOfNowMin, $aheadOfMtimeMin, $FutureSkewMinutes))
                }
            }
            if ('utc' -notin $props) { $notes.Add("legacy timestamp field '$tsKey' (tolerated one cycle); canonical field is 'utc'") }
        }

        if ('agent_id' -notin $props) {
            $issues.Add('agent_id missing')
        } else {
            $id = [string]$data.agent_id
            $sanitised = ($id -replace '[:/]', '-')
            $viaRegistry = ($null -ne $registry -and $registry.ContainsKey($id) -and $registry[$id] -eq $f.Name)
            if ($id -ne $stem -and -not $viaRegistry -and $sanitised -ne $stem) {
                $issues.Add("agent_id '$id' matches no known file (stem='$stem', sanitised='$sanitised', registered=$viaRegistry)")
            }
            elseif ($viaRegistry) { $notes.Add("agent_id resolved via _registry.json (filename cannot express this id)") }
        }

        $lm = [regex]::Match($raw, '"live_claims"\s*:\s*(\[[^\]]*\]|null|")')
        if (-not $lm.Success) {
            $issues.Add('live_claims missing -- MUST be present; [] asserts holding nothing, omission is UNKNOWN')
        } elseif ($lm.Groups[1].Value -notmatch '^\[') {
            $issues.Add("live_claims is not a JSON array (found: $($lm.Groups[1].Value))")
        }

        if ('state' -notin $props) { $issues.Add('state missing (expected active|idle|busy)') }
        elseif ($ValidState -notcontains [string]$data.state) { $issues.Add("state '$($data.state)' not in enum ($($ValidState -join '|'))") }

        $unknown = @($props | Where-Object { $_ -notin $KnownKey })
        if ($unknown.Count -gt 0) { $notes.Add("legacy/unknown keys: " + ($unknown -join ', ')) }
    }

    if ($f.Extension -ne '.json') { $issues.Add("EXTENSION: '$($f.Name)' is not a .json file") }

    # B5-1062: a lookalike codepoint in the FILENAME is a finding in its own right.
    # The cross-file agent_id check below cannot see it: a U+F03A colon satisfies every
    # Windows filename constraint, so 'solar-pro4<U+F03A>free.json' parses, and if the
    # correctly-spelled sibling is absent from the scanned set the store reports
    # 0 collisions while two sessions share one identity. Measured on the live store
    # before this check existed: 94 files / 0 collisions, with the sibling sitting
    # in _quarantine/ that the non-recursive listing never reached.
    # NOTE: no \u{...} astral escape here. .NET's regex engine rejects the brace form
    # outright ("Insufficient hexadecimal digits"), and a regex that throws inside
    # Matches() returns 0 findings -- a validator that silently reports "clean" on the
    # exact input it was written to catch. Astral PUA is matched by explicit surrogate
    # pairs below instead.
    $lookalikes = @(
        [regex]::Matches($f.Name, '[\uE000-\uF8FF]')                       # BMP private use
        [regex]::Matches($f.Name, '\uD800[\uDC00-\uDFFF]')                 # astral PUA, low surrogate
        [regex]::Matches($f.Name, '\u2028|\u2029|\u00A0')                 # line/para sep, NBSP
    ) | ForEach-Object { $_ } | Where-Object { $_ -ne $null }
    if (@($lookalikes).Count -gt 0) {
        $cps = (@($lookalikes) | ForEach-Object { 'U+{0:X4}' -f [int][char]$_.Value } | Sort-Object -Unique) -join ', '
        $issues.Add("FILENAME LOOKALIKE: '$($f.Name)' contains non-ASCII codepoint(s) $cps -- a PUA or separator character that Windows accepts in a filename but that reads as ':' or '/' and produces a DIFFERENT stem for the SAME agent_id (R7). Sanitise to the documented ':'/'/' -> '-' form; do not mint a second file.")
    }

    $results += [pscustomobject]@{
        File         = $f.Name
        Rel          = $rel
        Live         = $isLive
        Format       = $format
        AgentId      = $(if ($null -ne $data -and $data.PSObject.Properties.Name -contains 'agent_id') { [string]$data.agent_id } else { $null })
        State        = $(if ($null -ne $data -and $data.PSObject.Properties.Name -contains 'state') { [string]$data.state } else { $null })
        TimestampKey = $tsKey
        TimestampUtc = $tsValue
        AgeMinutes   = $ageMin
        Verdict      = $(if ($issues.Count -eq 0) { 'CONFORMS' } else { 'NON-CONFORMING' })
        IssueCount   = $issues.Count
        Issues       = $issues
        NoteCount    = $notes.Count
        Notes        = $notes
    }
}

# ---- store-level invariant: every agent_id must resolve to EXACTLY ONE file ---------
# This is the enforceable form of the amended rule. The per-file check above confirms an
# agent_id resolves to SOME file; this confirms it resolves to only ONE. Two files
# asserting the same identity make live_claims ambiguous no matter how well-formed
# either file is -- the ambiguity is in the store, not in the row.
# B5-2340: two maps, because "two files carry this agent_id" and "two LIVE files carry
# this agent_id" are different questions and only the second is an identity defect.
# $byAgentLive drives the collision verdict and the exit code. $archCopies records an
# id whose archived copies exist, so the archival pair stays VISIBLE as evidence instead
# of being silently dropped -- dropping it would lose the cross-file evidence B5-1062
# added -Recurse to obtain. Paths are Rel, so one finding can never print one path twice.
$byAgentLive = @{}
$archCopies  = @{}
foreach ($r in $results) {
    if ([string]::IsNullOrEmpty($r.AgentId)) { continue }
    $k = $r.AgentId
    if ($r.Live) {
        if (-not $byAgentLive.ContainsKey($k)) { $byAgentLive[$k] = @() }
        $byAgentLive[$k] += $r.Rel
    } else {
        if (-not $archCopies.ContainsKey($k)) { $archCopies[$k] = @() }
        $archCopies[$k] += $r.Rel
    }
}
$collisions = @($byAgentLive.GetEnumerator() | Where-Object { $_.Value.Count -gt 1 } | Sort-Object Name)
# An id is reported under ARCHIVED COPIES only when the archive adds information: a live
# file that was retired, or an archive that is ambiguous among itself. A lone archived
# file with no live sibling is the ordinary terminal state of a retirement.
$archReport = @($archCopies.GetEnumerator() | Where-Object {
    ($_.Value.Count -gt 1) -or ($byAgentLive.ContainsKey($_.Key))
} | Sort-Object Name)

# ---- report -----------------------------------------------------------------------
# B5-2340: the conformance census is reported as TWO populations. Before, the single
# "files: N" line counted the archive, so 336 named the live store while 225 of those
# rows were archived copies no reader may treat as live (retirement policy, 2026-09-29).
$bad      = @($results | Where-Object { $_.Verdict -ne 'CONFORMS' })
$ok       = @($results | Where-Object { $_.Verdict -eq 'CONFORMS' })
$liveAll  = @($results | Where-Object { $_.Live })
$archAll  = @($results | Where-Object { -not $_.Live })
$badLive  = @($bad | Where-Object { $_.Live })
$badArch  = @($bad | Where-Object { -not $_.Live })
$archDirs = @(Get-ChildItem -LiteralPath $Directory -Directory -Force -ErrorAction SilentlyContinue |
                ForEach-Object { $_.Name } | Sort-Object)

if ($Json) {
    $results | ConvertTo-Json -Depth 5
} else {
    Write-Output "=== Heartbeat schema validation ==="
    Write-Output ("directory : {0}" -f $Directory)
    Write-Output ("registry  : {0}" -f $(if ($null -ne $registry) { "$($registry.Count) agent_id -> filename entries" } else { 'absent' }))
    Write-Output ("files     : {0} LIVE (conforming: {1}, non-conforming: {2})" -f $liveAll.Count, (@($liveAll | Where-Object { $_.Verdict -eq 'CONFORMS' }).Count), $badLive.Count)
    # The archive line is deliberately NOT folded into "files". It is out of the live
    # store by decision, and a reader resolving an archived identity must treat its
    # signal as UNKNOWN (HEARTBEATS/README.md retirement policy, 2026-09-29).
    Write-Output ("archive   : {0} file(s) under {1} -- OUT of the live store, reported not counted; a reader resolving an archived identity gets UNKNOWN, never LIVE or STALE" -f $archAll.Count, $(if ($archDirs.Count -gt 0) { ($archDirs -join ', ') } else { '(none)' }))
    Write-Output ("identity  : {0} distinct LIVE agent_id   collisions: {1}   (archived copies listed separately below and are NOT collisions)" -f $byAgentLive.Count, $collisions.Count)
    Write-Output ("TTL       : {0} min" -f $TtlMinutes)
    # B5-1002: encoding receipt -- every file above was read as explicit UTF-8.
    Write-Output "encoding  : UTF-8 (explicit; [IO.File]::ReadAllText(path, UTF8))"
    Write-Output ""
    Write-Output ("{0,-44} {1,-40} {2,-9} {3,-8} {4}" -f 'FILE', 'AGENT_ID', 'MTIME_MIN', 'STATE', 'VERDICT')
    Write-Output ("-" * 118)
    foreach ($r in ($results | Sort-Object Verdict, Rel)) {
        Write-Output ("{0,-44} {1,-40} {2,-9} {3,-8} {4}" -f `
            $r.Rel, $(if ($r.AgentId) { $r.AgentId } else { '-' }), $r.AgeMinutes, $(if ($r.State) { $r.State } else { '-' }), $r.Verdict)
    }
    if ($bad.Count -gt 0) {
        Write-Output ""
        Write-Output "=== Findings ==="
        if ($badArch.Count -gt 0) {
            Write-Output ("    ({0} of these are ARCHIVED files. An archived payload's schema state is not a live-store defect; it is recorded for the retirement record and it does not decide the exit code.)" -f $badArch.Count)
        }
        foreach ($r in $bad) {
            Write-Output ""
            Write-Output ("[{0}]{1}  ({2} issue{3})" -f $r.Rel, $(if ($r.Live) { '' } else { ' [ARCHIVED]' }), $r.IssueCount, $(if ($r.IssueCount -eq 1) { '' } else { 's' }))
            foreach ($i in $r.Issues) { Write-Output ("    - {0}" -f $i) }
        }
    }
    if (@($results | Where-Object { $_.NoteCount -gt 0 }).Count -gt 0) {
        Write-Output ""
        Write-Output "=== Notes (informational, not violations) ==="
        foreach ($r in ($results | Where-Object { $_.NoteCount -gt 0 } | Sort-Object Rel)) {
            foreach ($n in $r.Notes) { Write-Output ("  {0,-44} {1}" -f $r.Rel, $n) }
        }
    }
}

if ($collisions.Count -gt 0) {
    Write-Output ""
    Write-Output "=== IDENTITY COLLISIONS (one LIVE agent_id claimed by more than one live file) ==="
    Write-Output "    live_claims cannot be trusted for these agents: the store cannot say which file is authoritative."
    foreach ($c in $collisions) {
        Write-Output ("  {0}" -f $c.Key)
        foreach ($fn in $c.Value) { Write-Output ("      -> {0}" -f $fn) }
    }
}

# B5-2340: the archival pair, shown and explicitly NOT called a collision. This is the
# evidence B5-1062's -Recurse was added to obtain: a lookalike NAME is a defect of the
# name whatever directory it sits in, and the sibling's presence is the only cross-file
# proof. Printing it here is what keeps the recursion honest without letting the archive
# vote on live-store identity.
if ($archReport.Count -gt 0) {
    Write-Output ""
    Write-Output "=== ARCHIVED COPIES (NOT identity collisions -- the retirement policy moves files here) ==="
    Write-Output "    A live file beside its own archived copy is the NORMAL terminal state of a retirement, not two sessions sharing one identity."
    foreach ($c in $archReport) {
        $liveHere = if ($byAgentLive.ContainsKey($c.Key)) { $byAgentLive[$c.Key] -join ', ' } else { '(no live file)' }
        Write-Output ("  {0}" -f $c.Key)
        Write-Output ("      live     : {0}" -f $liveHere)
        foreach ($fn in $c.Value) { Write-Output ("      archived : {0}" -f $fn) }
    }
}

# B5-2340: the exit is decided by the LIVE store plus the one archived-file defect class
# that is a defect of the NAME rather than of the archived payload. -Recurse stays (see
# the enumeration comment), so an archived lookalike filename still holds the gate: that
# is B5-1062's exact argument, and dropping it would reinstate the false pass it closed.
# Every OTHER archived-file finding is recorded for the retirement record and does not
# hold a live-store gate red indefinitely -- which is precisely how four archived files
# came to sit in the battery's standing non-conforming inventory.
$lookalikeBad = @($results | Where-Object { $_.Live -eq $false -and @($_.Issues | Where-Object { $_ -like 'FILENAME LOOKALIKE*' }).Count -gt 0 })
if ($badLive.Count -gt 0) { exit 1 }
if ($collisions.Count -gt 0) { exit 1 }
if ($lookalikeBad.Count -gt 0) { exit 1 }
exit 0
