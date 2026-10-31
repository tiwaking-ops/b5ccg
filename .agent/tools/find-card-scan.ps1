<#
.SYNOPSIS
  B5-0053 (card-pool diff): resolve a card id to its scan and to the pool
  record it must be compared against.
.DESCRIPTION
  The card scans live in the pre-fork repository, which the fork deliberately
  left behind:

    C:\temp\projects\Babylon5CCGGameDesign\investigations\card-images\

  and the mapping from a card TITLE to its scan file is
  `sorted/MAPPING.json` - the owner's own artefact, built when the images
  arrived. **The title is the key, not the pool id**, for two measured
  reasons:

    * The AFTERMATH cards carry different id stems in the two printings -
      `aftermath_approval_of_the_grey` in Premiere and
      `de_am_approval_of_the_grey` in Deluxe - so "deluxe id = de_ + premiere
      id" is false for all 58 of them, and a scan cannot be found by
      constructing an id.
    * Two cards were supplied by the owner as real JPEGs
      (`event_changing_opinion.jpg`, `event_exploration.jpg`), so the
      extension is not always .gif.

  So this tool resolves by TITLE, through MAPPING.json, and prints everything
  an agent needs to do one card: the scan path, the pool records, and the
  fields to compare. It exists so that 41 seeded ledger rows do not each have
  to carry a hand-written path, and so that the resolution is auditable in
  one place rather than 446 times.

  Usage:
    find-card-scan.ps1 -CardId enh_vorlon_enhancement
    find-card-scan.ps1 -CardId enh_vorlon_enhancement -Json
    find-card-scan.ps1 -CardId enh_vorlon_enhancement -NoPool

  -NoPool (B5-0169) prints the resolved scan path, file name, byte size and
  pixel dimensions ONLY - no stat, no trait, no rules text, no pool field of
  any kind. It exists because the default output prints the pool answer key
  directly above the transcription a reading agent is about to make, and the
  pool is measured to diverge from the printed card (B5-0167 recorded the pool
  visible in both of its passes). Reading agents use -NoPool; the scan-resolution
  logic is identical either way, and the pool is still consulted internally to
  resolve the card id.

  -NoPool is refused in combination with -Json, whose whole purpose is to print
  the pool records, rather than the combination being silently ignored.

  Exit codes:
    0 the card was resolved (the scan may still be unreadable - that is the
      agent's finding, not this tool's)
    1 the card id is not in the pool
    2 no scan could be located for the card
    4 -NoPool was combined with a switch that prints card data (nothing printed)
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$CardId,
    # The pre-fork repository holding the scans. Overridable so the tool is
    # not welded to one machine's layout.
    [string]$ScanRoot = 'C:\temp\projects\Babylon5CCGGameDesign\investigations\card-images',
    [switch]$Json,
    # B5-0169: quarantine every pool-derived value. See the help block.
    [switch]$NoPool
)

$ErrorActionPreference = 'Stop'

# B5-0169: refuse the combination instead of silently printing the pool anyway.
# Checked before any file is read and before any line is emitted, so a refusal
# can never leave half a record on stdout. -ErrorAction Continue keeps the
# message flowing while the script's own 'Stop' preference is in force, so the
# exit code is deterministic (4) rather than whatever the thrower chooses.
if ($NoPool -and $Json) {
    Write-Error ('-NoPool and -Json cannot be combined: -Json exists to print the pool ' +
                 'records that -NoPool quarantines. Nothing was printed.') -ErrorAction Continue
    exit 4
}

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)

# B5-0169: the pixel dimensions printed by -NoPool. System.Drawing is part of the
# .NET Framework this tool already runs under (Windows PowerShell 5.1). If it
# cannot be loaded the line still appears and says so, rather than vanishing, so
# 'no dimension printed' can never be mistaken for 'this file has no dimension'.
function Get-ScanPixelSize {
    param([Parameter(Mandatory = $true)][string]$Path)
    try {
        if (-not ('System.Drawing.Image' -as [type])) {
            Add-Type -AssemblyName System.Drawing
        }
        $img = [System.Drawing.Image]::FromFile($Path)
        try { return ('{0}x{1}' -f $img.Width, $img.Height) }
        finally { $img.Dispose() }
    }
    catch {
        return ('unavailable ({0})' -f $_.Exception.GetType().Name)
    }
}

# ── the pool records for this id ─────────────────────────────────────────────
$records = @()
foreach ($set in @('premiere', 'deluxe')) {
    $path = Join-Path $repoRoot "b5ccg\resources\cards\$set.json"
    if (-not (Test-Path $path)) { continue }
    $all = Get-Content -LiteralPath $path -Raw -Encoding UTF8 | ConvertFrom-Json
    # Premiere may wrap its records in a cards property; Deluxe is an array.
    if ($all -isnot [array] -and $null -ne $all.PSObject.Properties['cards']) {
        $all = $all.cards
    }
    foreach ($c in $all) {
        if ($c.id -eq $CardId) {
            $records += [pscustomobject]@{
                id = $c.id; set = $set; title = $c.title; type = $c.type
                subtype = $c.subtype; rarity = $c.rarity; faction = $c.faction
                cost = $c.cost; text = $c.text
                extra = @($c.PSObject.Properties |
                    Where-Object { $_.Name -notin @('id','title','type','subtype','rarity','faction','set','imageKey','text','cost') } |
                    ForEach-Object { "$($_.Name)=$($_.Value)" })
            }
        }
    }
}
if ($records.Count -eq 0) {
    Write-Error "card id '$CardId' is not in the card pool"
    exit 1
}

# ── the scan, through the owner's title mapping ─────────────────────────────
$mappingPath = Join-Path $ScanRoot 'sorted\MAPPING.json'
$scans = @()
$mapped = $null
$m = Get-Content -LiteralPath $mappingPath -Raw -Encoding UTF8 | ConvertFrom-Json
$mapping = $m.mapping
# The owner supplied two cards by ID rather than by title, and MAPPING records
# them in `user_supplied` (file name -> pool id) rather than in `mapping`
# (title -> pool id). Both are checked, so a card supplied either way is
# found.
$userSupplied = @{}
if ($m.user_supplied) {
    foreach ($prop in $m.user_supplied.PSObject.Properties) {
        $userSupplied[$prop.Name] = [string]$prop.Value
    }
}

foreach ($title in $records.title | Select-Object -Unique) {
    $entry = $mapping.$title
    if ($null -ne $entry) {
        foreach ($file in $entry.files) {
            foreach ($dir in @((Join-Path $ScanRoot 'sorted'), $ScanRoot)) {
                $p = Join-Path $dir $file
                if (Test-Path -LiteralPath $p) {
                    $scans += $p
                    if ($null -eq $mapped) {
                        $mapped = [pscustomobject]@{ title = $title; id = $entry.id; set = $entry.set }
                    }
                }
            }
        }
    }
}
# `verified_alias_titles` maps a PHYSICAL card file name to the pool title,
# for the 20 cards whose printed title differs from the pool's ("Vital
# Interest" for "Vital Interests", "Empereror Turham" for "Emperor Turhan",
# and the race-suffixed fleets). The title-keyed map above cannot find
# those, so the alias map is consulted for every title as well.
if ($m.verified_alias_titles) {
    foreach ($prop in $m.verified_alias_titles.PSObject.Properties) {
        if ($prop.Value -notin $records.title) { continue }
        foreach ($dir in @((Join-Path $ScanRoot 'sorted'), $ScanRoot)) {
            $p = Join-Path $dir $prop.Name
            if (Test-Path -LiteralPath $p) {
                $scans += $p
                if ($null -eq $mapped) {
                    $mapped = [pscustomobject]@{ title = $prop.Value; id = $CardId; set = '(verified alias)' }
                }
            }
        }
    }
}
# Fallbacks: a file named after the pool id, in either printings' naming.
foreach ($id in @($CardId) + @($records.id | Where-Object { $_ -like 'de_*' } | ForEach-Object { $_.Substring(3) })) {
    foreach ($dir in @((Join-Path $ScanRoot 'sorted'), $ScanRoot)) {
        foreach ($ext in @('.gif', '.jpg', '.jpeg', '.png')) {
            $p = Join-Path $dir ($id + $ext)
            if (Test-Path -LiteralPath $p) {
                $scans += $p
                if ($null -eq $mapped) {
                    $mapped = [pscustomobject]@{ title = ($records.title | Select-Object -First 1); id = $CardId; set = '(by id)' }
                }
            }
        }
    }
}
# And the owner's id-named supplies, which MAPPING records by file name.
foreach ($file in $userSupplied.Keys) {
    if ([string]$userSupplied[$file] -like "*$CardId*") {
        foreach ($dir in @((Join-Path $ScanRoot 'sorted'), $ScanRoot)) {
            $p = Join-Path $dir $file
            if (Test-Path -LiteralPath $p) {
                $scans += $p
                if ($null -eq $mapped) {
                    $mapped = [pscustomobject]@{ title = ($records.title | Select-Object -First 1); id = $CardId; set = '(owner-supplied)' }
                }
            }
        }
    }
}
$scans = @($scans | Select-Object -Unique)

if ($scans.Count -eq 0) {
    Write-Error "no scan found for '$CardId' (title(s): $($records.title -join ', ')) under $ScanRoot"
    exit 2
}

# B5-0169: the quarantined mode. Every value below is a property of the FILE. The
# card id is echoed as the caller's own input (the caller typed it); the pool
# title, the mapped set, the stats, the traits and the rules text are all absent
# on purpose. Image dimensions come from System.Drawing; when that assembly is
# unavailable the line says so rather than being dropped, so a caller can tell
# 'no dimension printed' from 'this file has none'.
if ($NoPool) {
    Write-Host '-NoPool (pool values quarantined): scan path, file name, bytes, pixel dimensions only.'
    # No index on the header line: a number there is the one thing in the block
    # that is neither a file property nor the caller's input, and the quarantine
    # is easier to assert when the only digits on stdout are bytes and pixels.
    foreach ($s in $scans) {
        $item = Get-Item -LiteralPath $s
        Write-Host 'SCAN FILE'
        Write-Host ("  FILE NAME : {0}" -f $item.Name)
        Write-Host ("  PATH      : {0}" -f $item.FullName)
        Write-Host ("  BYTES     : {0}" -f $item.Length)
        Write-Host ("  PIXELS    : {0}" -f (Get-ScanPixelSize -Path $item.FullName))
    }
    exit 0
}

if ($Json) {
    $out = [ordered]@{
        card_id = $CardId
        titles = @($records.title | Select-Object -Unique)
        mapped_to = $mapped
        scans = $scans
        pool_records = $records
    }
    $out | ConvertTo-Json -Depth 6
    exit 0
}

Write-Host "CARD ID      : $CardId"
Write-Host "TITLE(S)     : $($records.title -join ' | ')"
if ($mapped) { Write-Host ("MAPPED TO    : {0} ({1})" -f $mapped.id, $mapped.set) }
Write-Host "SCAN FILE(S) :"
foreach ($s in $scans) {
    $len = (Get-Item -LiteralPath $s).Length
    Write-Host ("  {0}  ({1:N0} bytes)" -f $s, $len)
}
Write-Host ""
Write-Host "POOL RECORD(S) to compare against:"
foreach ($r in $records) {
    Write-Host "--- $($r.id)  [$($r.set)]  type=$($r.type)  cost=$($r.cost) ---"
    Write-Host ("  text: " + $r.text)
    if ($r.extra.Count -gt 0) { Write-Host ("  structured: " + ($r.extra -join '; ')) }
}
Write-Host ""
Write-Host "FIELDS TO COMPARE: title, type, cost, stats, traits, text, and every structured field."
exit 0
