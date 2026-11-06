<#
.SYNOPSIS
  Tooling probe for B5-0028 / WP-10 (A3) and B5-0034 / WP-11 (A3): the effect
  data must validate against the JSON Schema, the validator must be able to
  FAIL, and every rule ID the data cites must exist in the rule index.
.DESCRIPTION
  The Java gate class `b5ccg.engine.HeadlessEffectSchemaProbe` checks the
  shipped game's half of the contract: the engine's own loader fails closed
  and the renderer is canonical. `b5ccg.engine.HeadlessGroupEffectProbe` checks
  the Groups pilot: coverage of every group title, the bespoke tail, and that
  the schema's closed sets and the engine's closed sets agree. This probe
  checks the other half, in the dev tooling, in a different language and a
  different process:

    1. EVERY shipped effect document validates against
       `.agent/tools/schemas/effect-document.schema.json`. There are two:
       the v0 skeleton (`effects-v0.json`) and the Groups pilot
       (`effects-groups-v1.json`, added by WP-11 / B5-0034). A schema that
       only one of them satisfies is a schema that does not describe the
       shipped data.
    2. The validator REFUSES a document that breaks the schema. This is the
       red-then-green half: a validator that only ever says PASS is not a
       validator, and this assertion is what proves the check can fail. The
       broken fixture is written to a TEMP file, never to the repository.
    3. Every rule ID the effect data cites exists in `docs/RULE_INDEX.md`.
       AGENTS.md section 5 requires every DECISIONS interpretation to cite a
       rule ID, and `check-citations.ps1` enforces that for the prose; this
       applies the same rule to the DATA, where a stale rule ID would
       otherwise go unnoticed.
    4. Every card id the effect data names exists in the card pool.

  Exits 0 when every check passes, 1 otherwise.

  If no Python interpreter is on PATH the probe reports SKIPPED and exits 0
  with a loud banner rather than silently passing: the gate's Java half still
  runs, and a skipped check that is not reported as skipped is the failure
  mode this project keeps paying for.
#>
[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$validator = Join-Path $PSScriptRoot 'validate-effect-schema.py'
$schema   = Join-Path $PSScriptRoot 'schemas/effect-document.schema.json'
$ruleIndex = Join-Path $repoRoot 'docs/RULE_INDEX.md'

# The shipped effect documents. The v0 skeleton first, then the Groups pilot
# document (WP-11 / B5-0034).
$documents = @(
    (Join-Path $repoRoot 'b5ccg/resources/effects/effects-v0.json'),
    (Join-Path $repoRoot 'b5ccg/resources/effects/effects-groups-v1.json')
)

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

# ── Find a Python interpreter ───────────────────────────────────────────────
$python = $null
foreach ($candidate in @('py', 'python', 'python3')) {
    $cmd = Get-Command $candidate -ErrorAction SilentlyContinue
    if ($null -eq $cmd) { continue }
    try {
        $null = & $candidate --version 2>&1
        $python = $candidate
        break
    } catch {
        continue
    }
}

if ($null -eq $python) {
    Write-Host "=== TOOLING PROBE (B5-0028) ==="
    Write-Host "SKIPPED: no Python interpreter on PATH (tried py, python, python3)."
    Write-Host "         The JSON-Schema half of the acceptance was NOT checked in this run."
    Write-Host "         The engine's own fail-closed loader IS checked by the Java gate class."
    exit 0
}

Write-Host "=== TOOLING PROBE (B5-0028 / WP-10 effect schema; B5-0034 / WP-11 pilot) ==="
Write-Host ("  python: " + $python)

# ── 1. Every shipped effect document validates ─────────────────────────────
foreach ($document in $documents) {
    $name = Split-Path -Leaf $document
    if (-not (Test-Path -LiteralPath $document)) {
        Check ("the effect document exists (" + $name + ")") $false ("not found: " + $document)
        continue
    }
    $out = & $python $validator $schema $document 2>&1
    $code = $LASTEXITCODE
    Check ("the shipped effect document validates against the JSON Schema (" + $name + ")") `
        ($code -eq 0) ("exit ${code}: " + ($out -join ' '))
}

# ── 2. The validator REFUSES a broken document ──────────────────────────────
# The fixture breaks the schema in a way the schema itself forbids: an effect
# kind outside the closed set. The validator must exit non-zero.
$document = $documents[0]
$broken = $null
$tmp = $null
try {
    $raw = Get-Content -LiteralPath $document -Raw -Encoding UTF8
    $broken = $raw.Replace('"kind": "ONE_SHOT"', '"kind": "NOT_A_KIND"')
    if ($broken -eq $raw) { $broken = $raw.Replace('"kind":"ONE_SHOT"', '"kind":"NOT_A_KIND"') }
    $tmp = Join-Path ([System.IO.Path]::GetTempPath()) ("b5-0028-broken-effect-" + [System.Guid]::NewGuid().ToString('N') + ".json")
    [System.IO.File]::WriteAllText($tmp, $broken, (New-Object System.Text.UTF8Encoding($false)))
    $out2 = & $python $validator $schema $tmp 2>&1
    $code2 = $LASTEXITCODE
    Check "the validator REFUSES a document with an unknown effect kind" ($code2 -ne 0) `
        ("exit $code2 - the validator accepted a broken document")
    Check "the refusal names the offending kind" `
        (($out2 -join ' ') -match 'NOT_A_KIND') "the message did not name the offending kind"
} finally {
    if ($tmp -and (Test-Path -LiteralPath $tmp)) { Remove-Item -LiteralPath $tmp -Force }
}

# ── 3. Every rule ID the data cites exists in the rule index ────────────────
if (Test-Path -LiteralPath $ruleIndex) {
    $index = Get-Content -LiteralPath $ruleIndex -Raw -Encoding UTF8
    $known = @{}
    foreach ($m in [regex]::Matches($index, 'RB:[A-Za-z0-9]+/[A-Za-z0-9\-]+')) {
        $known[$m.Value] = $true
    }
    $cited = @{}
    $unknown = @()
    foreach ($doc in $documents) {
        if (-not (Test-Path -LiteralPath $doc)) { continue }
        $data = Get-Content -LiteralPath $doc -Raw -Encoding UTF8 | ConvertFrom-Json
        foreach ($entry in $data.entries) {
            foreach ($id in $entry.provenance.ruleIds) {
                $cited[$id] = $true
                if (-not $known.ContainsKey($id)) { $unknown += ((Split-Path -Leaf $doc) + ": " + $entry.cardId + " -> " + $id) }
            }
        }
    }
    Check ("every rule ID the data cites is in the rule index ({0} distinct ID(s))" -f $cited.Count) `
        ($unknown.Count -eq 0) ($unknown -join '; ')
} else {
    Check "the rule index is readable" $false ("not found: " + $ruleIndex)
}

# ── 4. Every card id the data names exists in the card pool ─────────────────
# B5-0182: the two pools have DIFFERENT shapes. deluxe.json is a bare array;
# premiere.json is the owner-approved wrapper object
# {"source_of_truth": ..., "cards": [...]} (docs/DECISIONS.md, 2026-10-31
# wrapper entry). Iterating the wrapper with foreach yields ONE object whose
# .id is null, which is how this section used to die with "You cannot call a
# method on a null-valued expression" and take the whole gate down with it.
# The unwrap is conditional, never unconditional: the wrapper is canonical and
# must survive this probe.
$cardFiles = @(
    (Join-Path $repoRoot 'b5ccg/resources/cards/premiere.json'),
    (Join-Path $repoRoot 'b5ccg/resources/cards/deluxe.json')
)
$ids = @{}
$noId = @()
foreach ($f in $cardFiles) {
    if (-not (Test-Path -LiteralPath $f)) { continue }
    $name = Split-Path -Leaf $f
    $parsed = Get-Content -LiteralPath $f -Raw -Encoding UTF8 | ConvertFrom-Json
    if ($parsed -is [System.Management.Automation.PSCustomObject] -and
        ($parsed.PSObject.Properties.Name -contains 'cards')) {
        $cards = @($parsed.cards)
    } else {
        $cards = @($parsed)
    }
    # A pool that parsed to nothing is a read failure, not an empty pool. Without
    # this check an unwrap that found no cards at all would satisfy every
    # remaining assertion by keeping them trivially true.
    Check ("the card pool file yielded cards (" + $name + ": " + $cards.Count + ")") `
        ($cards.Count -gt 0) ("0 card(s) parsed from " + $name)
    foreach ($c in $cards) {
        $canonical = $c.id
        if ([string]::IsNullOrEmpty($canonical)) { $noId += $name; continue }
        if ($canonical.StartsWith('de_')) { $canonical = $canonical.Substring(3) }
        $ids[$canonical] = $true
    }
}
Check ("every card in the pool files carries an id (" + $noId.Count + " without)") `
    ($noId.Count -eq 0) (($noId | Select-Object -Unique) -join ', ')
# Premiere holds 446 cards and Deluxe 383, 504 distinct canonical ids together.
# A floor of 400 sits far below the real union and far above zero, so a silently
# empty or half-unwrapped read fails here instead of passing quietly.
Check ("the pool holds a plausible number of card ids (" + $ids.Count + ")") `
    ($ids.Count -gt 400) ("only " + $ids.Count + " ids")
$missing = @()
foreach ($doc in $documents) {
    if (-not (Test-Path -LiteralPath $doc)) { continue }
    $data = Get-Content -LiteralPath $doc -Raw -Encoding UTF8 | ConvertFrom-Json
    foreach ($entry in $data.entries) {
        if (-not $ids.ContainsKey($entry.cardId)) { $missing += $entry.cardId }
    }
}
Check ("every card id the data names exists in the card pool ({0} card(s) in the pool)" -f $ids.Count) `
    ($missing.Count -eq 0) (($missing | Select-Object -Unique) -join ', ')

Write-Host ("RESULT: " + $(if ($failures.Count -eq 0) { "PASS" } else { "FAIL" }) +
            " (" + $checks + " checks, " + $failures.Count + " failures)")
if ($failures.Count -gt 0) { exit 1 }
exit 0
