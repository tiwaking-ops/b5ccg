<#
.SYNOPSIS
  B5-0053: seed the card-pool diff rows - one per batch of card titles.
.DESCRIPTION
  The owner ruled on 2026-10-22 that the printed cards are the text of record
  and that the diff runs over all 446 titles. This tool lays the work down as
  ledger rows, so the fleet has a queue instead of one open row.

  It is IDEMPOTENT: a row id that is already in the ledger is skipped, so
  re-running it after a partial failure appends only what is missing.

  Batch shape
    - the first batch is deliberately small (6 cards). It is the reference
      batch: the format lands there first, and the rest of the queue scales
      up from a standard that has been looked at.
    - the rest are 10 cards each.
    - the batches are grouped by card type, in the order the work is worth
      most: the two pilot types first (they unblock two OPEN rows), then the
      types the rulebook leans on, then the bulk, with the long tail last.

  The pool is NOT written by this tool and not by the rows it seeds. The
  rewrite is a separate gated row.

  Usage
    powershell -NoProfile -ExecutionPolicy Bypass -File .agent/tools/seed-card-diff-rows.ps1
    powershell -NoProfile -ExecutionPolicy Bypass -File .agent/tools/seed-card-diff-rows.ps1 -DryRun
#>
[CmdletBinding()]
param(
    # Report what would be appended and write nothing.
    [switch]$DryRun,
    # The first row id to allocate. B5-0053 by default; the tool skips any
    # id already present in the ledger.
    [string]$FirstId = 'B5-0053'
)

$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$ledger = Join-Path $repoRoot '.agent\TASK_LEDGER.md'
$append = Join-Path $PSScriptRoot 'append-ledger.ps1'

$pre = Get-Content (Join-Path $repoRoot 'b5ccg\resources\cards\premiere.json') -Raw -Encoding UTF8 | ConvertFrom-Json

# The priority order of card types. The two pilot types come first because
# Groups and Enhancements are what B5-0034 and B5-0035 are waiting for.
$typeOrder = @('GROUP', 'ENHANCEMENT', 'AGENDA', 'EVENT', 'CONFLICT', 'AFTERMATH', 'CHARACTER', 'FLEET', 'LOCATION')

$batches = @()
foreach ($type in $typeOrder) {
    $cards = @($pre | Where-Object { $_.type -eq $type } | Sort-Object id)
    if ($cards.Count -eq 0) { continue }
    $size = 10
    for ($i = 0; $i -lt $cards.Count; $i += $size) {
        $chunk = @($cards[$i..([Math]::Min($i + $size - 1, $cards.Count - 1))])
        $batches += [pscustomobject]@{ type = $type; cards = $chunk }
    }
}
# The first batch is the reference batch: half size, so it lands fast and can
# be reviewed before the queue scales up.
$batches[0].cards = @($batches[0].cards | Select-Object -First 6)

$existing = @{}
foreach ($line in (Get-Content -LiteralPath $ledger -Encoding UTF8)) {
    if ($line -match '^\|\s*(B5-[0-9]{4}[a-z]?)\s*\|') { $existing[$Matches[1]] = $true }
}

$next = [int]($FirstId -replace '^B5-', '')
$written = 0
$skipped = 0
foreach ($b in $batches) {
    $id = 'B5-{0:D4}' -f $next
    $next++
    if ($existing.ContainsKey($id)) { $skipped++; continue }
    $n = $batches.IndexOf($b) + 1
    $ids = ($b.cards | ForEach-Object { $_.id }) -join ', '
    $row = "| $id | OPEN | Card-pool diff batch $n/$($batches.Count) ($($b.type), $($b.cards.Count) card(s)): transcribe the PRINTED text of the listed cards from their scans and file one record per card under .agent/CARD-TEXT/<premiere-id>.md, comparing each against the pool record. Cards: $ids. Method, output shape, the fields to compare and the hard rules are in .agent/CARD-TEXT/README.md - read it first. Do NOT edit b5ccg/resources/cards/ - the pool rewrite is a separate gated row. Acceptance: one .agent/CARD-TEXT/<premiere-id>.md per listed card, each carrying the printed rules text transcribed verbatim with the flavour separated from it, every printed number, every printed trait, the pool record quoted, and a MATCH or CHANGED or INCONCLUSIVE verdict naming every field that differs; illegible regions recorded rather than guessed; no pool, census or effect-data file edited | card-text/b$('{0:D2}' -f $n) | - | - |"
    if ($DryRun) {
        Write-Host "DRY RUN would append: $id  ($($b.type), $($b.cards.Count) cards)"
        continue
    }
    & powershell -NoProfile -ExecutionPolicy Bypass -File $append -Row $row | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "append-ledger.ps1 refused $id" }
    $written++
    Write-Host "appended $id  ($($b.type), $($b.cards.Count) cards)"
}
Write-Host ("batches: {0}  appended: {1}  skipped (already present): {2}" -f $batches.Count, $written, $skipped)

# ── register the prerequisite gates ───────────────────────────────────────────
# The queue's gate table is a hard-coded hashtable in .agent/run-queue.ps1, and
# a row is only held back when it appears in it. A "gated: claim ONLY after"
# phrase in a row's Task cell is prose for humans and is NOT machine
# enforced - which means a seeded row that claims to be gated would be
# offered to the fleet immediately, and the pool rewrite could start before
# the diff it depends on had landed.
#
# So the seeder registers the gates it implies, in a marked block, and the
# block is rewritten from scratch on every run: the rows and their gates are
# then created by one tool and cannot drift apart.
if (-not $DryRun) {
    $queuePath = Join-Path $repoRoot '.agent\run-queue.ps1'
    $queue = [System.IO.File]::ReadAllText($queuePath)
    $begin = '(?m)^([ \t]*)# --- BEGIN seed-card-diff-rows.ps1 \(B5-0053\) ---'
    $end = '(?m)^([ \t]*)# --- END seed-card-diff-rows.ps1 \(B5-0053\) ---'
    # Drop any previous block, markers included.
    $rx = $begin + '.*?' + $end + '\r?\n'
    $queue = [regex]::Replace($queue, $rx, '', 'Singleline')

    $batchIds = @()
    for ($i = 0; $i -lt $batches.Count; $i++) { $batchIds += ('B5-{0:D4}' -f (53 + $i)) }
    $rollup = 'B5-{0:D4}' -f (53 + $batches.Count)          # the roll-up
    $rewrite = 'B5-{0:D4}' -f (54 + $batches.Count)        # the pool rewrite
    $stem = 'B5-{0:D4}' -f (55 + $batches.Count)            # the id-stem defect
    $mark = 'B5-{0:D4}' -f (56 + $batches.Count)            # the mark grant
    $vocab = 'B5-{0:D4}' -f (58 + $batches.Count)           # the vocabulary gap

    $rxAnchor = '(?m)^([ \t]*)\$Prereqs = @\{(\r?\n)'
    $m = [regex]::Match($queue, $rxAnchor)
    if (-not $m.Success) {
        throw "could not find the `$Prereqs table in run-queue.ps1; the gates were NOT registered"
    }
    $indent = $m.Groups[1].Value
    $eol = $m.Groups[2].Value
    $block = New-Object System.Collections.Generic.List[string]
    $block.Add($indent + '# --- BEGIN seed-card-diff-rows.ps1 (B5-0053) ---')
    $block.Add($indent + "'$rollup' = @(" + (($batchIds | ForEach-Object { "'" + $_ + "'" }) -join ', ') + ");")
    $block.Add($indent + "'$rewrite' = @('$rollup');")
    $block.Add($indent + "'$stem' = @('$rewrite');")
    $block.Add($indent + "'$mark' = @('$rewrite');")
    $block.Add($indent + "'$vocab' = @('$rewrite');")
    $block.Add($indent + '# --- END seed-card-diff-rows.ps1 (B5-0053) ---')
    $insert = ($block -join $eol) + $eol
    $queue = $queue.Substring(0, $m.Index + $m.Length) + $insert + $queue.Substring($m.Index + $m.Length)
    [System.IO.File]::WriteAllText($queuePath, $queue, (New-Object System.Text.UTF8Encoding($false)))
    Write-Host "registered gates: $rollup (on $($batchIds.Count) batch rows), $rewrite, $stem, $mark, $vocab"
}
