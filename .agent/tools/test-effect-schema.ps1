<#
.SYNOPSIS
  Tooling probe for B5-0028 / WP-10 (A3): the effect data must validate against
  the JSON Schema, the validator must be able to FAIL, and every rule ID the
  data cites must exist in the rule index.
.DESCRIPTION
  The Java gate class `b5ccg.engine.HeadlessEffectSchemaProbe` checks the
  shipped game's half of the contract: the engine's own loader fails closed
  and the renderer is canonical. This probe checks the other half, in the dev
  tooling, in a different language and a different process:

    1. The shipped effect document validates against
       `.agent/tools/schemas/effect-document.schema.json`.
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
$document = Join-Path $repoRoot 'b5ccg/resources/effects/effects-v0.json'
$ruleIndex = Join-Path $repoRoot 'docs/RULE_INDEX.md'

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

Write-Host "=== TOOLING PROBE (B5-0028 / WP-10 effect schema) ==="
Write-Host ("  python: " + $python)

# ── 1. The shipped document validates ───────────────────────────────────────
$out = & $python $validator $schema $document 2>&1
$code = $LASTEXITCODE
Check "the shipped effect document validates against the JSON Schema" ($code -eq 0) `
    ("exit ${code}: " + ($out -join ' '))

# ── 2. The validator REFUSES a broken document ──────────────────────────────
# The fixture breaks the schema in a way the schema itself forbids: an effect
# kind outside the closed set. The validator must exit non-zero.
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
    $data = Get-Content -LiteralPath $document -Raw -Encoding UTF8 | ConvertFrom-Json
    $cited = @{}
    $unknown = @()
    foreach ($entry in $data.entries) {
        foreach ($id in $entry.provenance.ruleIds) {
            $cited[$id] = $true
            if (-not $known.ContainsKey($id)) { $unknown += ("{0}: {1}" -f $entry.cardId, $id) }
        }
    }
    Check ("every rule ID the data cites is in the rule index ({0} distinct ID(s))" -f $cited.Count) `
        ($unknown.Count -eq 0) ($unknown -join '; ')
} else {
    Check "the rule index is readable" $false ("not found: " + $ruleIndex)
}

# ── 4. Every card id the data names exists in the card pool ─────────────────
$cardFiles = @(
    (Join-Path $repoRoot 'b5ccg/resources/cards/premiere.json'),
    (Join-Path $repoRoot 'b5ccg/resources/cards/deluxe.json')
)
$ids = @{}
foreach ($f in $cardFiles) {
    if (-not (Test-Path -LiteralPath $f)) { continue }
    foreach ($c in (Get-Content -LiteralPath $f -Raw -Encoding UTF8 | ConvertFrom-Json)) {
        $canonical = $c.id
        if ($canonical.StartsWith('de_')) { $canonical = $canonical.Substring(3) }
        $ids[$canonical] = $true
    }
}
$data = Get-Content -LiteralPath $document -Raw -Encoding UTF8 | ConvertFrom-Json
$missing = @()
foreach ($entry in $data.entries) {
    if (-not $ids.ContainsKey($entry.cardId)) { $missing += $entry.cardId }
}
Check ("every card id the data names exists in the card pool ({0} card(s) in the pool)" -f $ids.Count) `
    ($missing.Count -eq 0) (($missing | Select-Object -Unique) -join ', ')

Write-Host ("RESULT: " + $(if ($failures.Count -eq 0) { "PASS" } else { "FAIL" }) +
            " (" + $checks + " checks, " + $failures.Count + " failures)")
if ($failures.Count -gt 0) { exit 1 }
exit 0
