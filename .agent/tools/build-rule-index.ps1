#Requires -Version 3.0
<#
.SYNOPSIS
    WP-18a (task B5-0022): build docs/RULE_INDEX.md from the pinned rulebook.

.DESCRIPTION
    Every rule citation in this repo used to be a bare line number (RB:<digits>).
    Such a citation is only meaningful next to one exact revision of the file, and
    the file it pointed at can never be edited, so nothing could record what a
    citation meant after any re-flow. This tool builds the bridge: it derives a
    stable RB:<chapter>/<slug> ID per heading from the pinned bytes and writes it,
    with its line range, into docs/RULE_INDEX.md. Legacy RB:<digits> stays valid by
    range lookup; new text cites IDs.

    Derivation. A heading becomes an entry. Its chapter is one of FM (front matter,
    lines 1-121), the numbered chapters I..VI (122-1135), GL (glossary, 1136-1215)
    and BM (back matter, 1216-1236). The slug comes from the heading text: lower
    case, "&" becomes "and", apostrophes and punctuation vanish, hyphens and spaces
    become separators, and a leading "a", "an" or "the" is dropped. A slug longer
    than 40 characters is shortened by hand through $slugOverrides below, never by
    an automatic truncation that could silently collide.

    Guards, each a hard failure (exit 2) rather than a silently different table:
      * rulebook SHA-256 differs from the pin in AGENTS.md 2 and docs/RULEBOOK_PIN.md;
      * total line count differs from 1236;
      * the heading set differs from the 92 headings below in line, depth or title;
      * a heading other than line 1 carries non-ASCII text;
      * derived ranges do not tile lines 1..1236 exactly (no gap, no overlap);
      * two entries derive the same ID;
      * a long slug has no override, or an override is no longer used;
      * an ID has no entry in $scopeTags (a repin added a heading nobody scoped).

    Hand-kept data. The Scope (provisional) and Notes columns are not derived. They
    live in $scopeTags and $notes below, keyed by ID, and survive every
    regeneration untouched. Nothing else in this file is hand-maintained.

    Idempotent. The document is rendered to a string and compared with what is on
    disk; a byte-identical result is left alone, so a second run changes nothing.

    This script is deliberately pure ASCII. Every other script in .agent/tools/ is
    ASCII without a BOM, and Windows PowerShell 5.1 mis-reads non-ASCII literals
    from a BOM-less file, so the en dash written into the table is emitted as a
    character code and the one non-ASCII heading title (line 1) is folded before it
    reaches the golden comparison. Both deviations are called out where they occur.

.OUTPUTS
    Exit 0  docs/RULE_INDEX.md is current (written now, or already identical).
    Exit 1  -Check and the file differs from what would be generated.
    Exit 2  a guard failed. Never 0: an index derived from unexpected bytes must
            not report itself clean (the rule check-citations.ps1 and
            run-dup-census.ps1 already apply to an unreadable source).
#>
[CmdletBinding()]
param(
    [string] $Rulebook,
    [string] $OutFile,
    [switch] $Check
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Fail-Guard {
    param([string] $Message)
    Write-Error ('build-rule-index: GUARD FAILURE - ' + $Message) -ErrorAction Continue
    exit 2
}

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
if (-not $Rulebook) { $Rulebook = Join-Path $repoRoot 'BABYLON5_CCG_RULEBOOK.md' }
if (-not $OutFile)  { $OutFile  = Join-Path $repoRoot 'docs/RULE_INDEX.md' }

# ---------------------------------------------------------------- pins ----
$pinSha        = '1c99c44b7b64c8927f1e952a22a49d5886788da2d08e211092b5558e154716d0'
$pinLines      = 1236
$indexVersion  = '1.0'
$en            = [string][char]0x2013   # en dash in the Lines column; see the ASCII note above
$createdDate   = '2026-10-17'
$task          = 'B5-0022'

# Division boundaries inside the pinned line space.
$fmEnd = 121; $rulesStart = 122; $rulesEnd = 1135; $glStart = 1136; $glEnd = 1215; $bmStart = 1216

# ------------------------------------------------------- golden headings ----
# The 92 heading lines this tool was written against, as line|depth|title. Line 1
# holds an em dash in the file; it is folded to a hyphen here (see the ASCII note
# above) and the same fold is applied to the parsed titles before comparison, so
# no fidelity is lost and the script stays ASCII.
$golden = @'
1|1|Babylon 5 Customizable Card Game - Rulebook (canonical reference copy)
25|1|Babylon 5 Collectible Card Game
27|1|Babylon 5 Collectible Card Game Rulebook
29|2|Table of Contents
122|2|I. Introduction
124|3|Game Overview
130|3|Cards and Tokens
140|3|Anatomy of a Card
165|3|Influence
173|3|Victory
189|2|II. Preparing to Play
191|3|Customizing Your Game Deck
210|3|Choosing Your Race
226|3|Your Starting Hand
258|3|Shuffle & Cut
262|3|Preparing the Playing Field
274|3|Set Tension Tokens
286|3|Set Influence Tokens
292|3|Influence Changes
300|3|Beginning Play
304|3|Playing "Fast and Loose" and Resolving Timing Disputes
308|2|III. Playing the Game
310|3|Overview
332|3|Narrating the Story
340|3|The Ready Round
356|3|Sustained Actions
360|3|Ready and Rotated Cards
366|3|The Conflict Round
376|3|A Sample Conflict Round
380|3|The Action Round
404|3|The Resolution Round
432|3|Participants
444|3|The Draw Round
470|2|IV. Card Details
472|3|Status Tracker Card
476|3|Character Cards
482|3|Bringing Characters into Play
486|3|Your Starting Ambassador
490|3|Your Ambassador's Assistant
494|3|Group Cards
498|3|Fleet Cards
502|3|Location Cards
506|3|Enhancement Cards
520|3|Agenda Cards
528|3|Event Cards
536|3|Contingency Cards
548|3|Conflict Cards
586|3|Aftermath Cards
597|3|Type of Conflict
601|3|Participant
611|3|Aftermath Example
651|2|V. Action Details
745|2|VI. Additional Rules
747|3|A Sample Conflict
775|3|Triggered Effects
781|3|Personalities
789|3|Votes
799|3|States
805|3|War
819|3|Unconditional Surrender
827|3|Legal Targets
834|3|Marks
848|3|Discards
854|3|Bonuses and Blanking
860|3|Cumulative Effects
864|3|Rules of Precedence
874|3|The League of Non-Aligned Worlds
918|3|Alternate Factions
960|3|Dual Races
972|3|Tension and Unrest
980|3|Voting
984|3|Joint Effects
990|3|States (Dual Races)
994|3|Civil War
1014|3|Captured Locations
1018|3|Power & Victory (Optional Rule)
1022|3|More Factions
1026|3|Psi Corps
1038|3|Negative Power
1042|2|Severed Dreams New Rules and Clarifications
1046|3|Reducing Power Needed to Win
1052|3|Kha'Ri Characters
1056|3|Cards With No Printed Cost
1066|3|Errata
1090|2|Wheel of Fire New Rules and Clarifications
1094|3|The Drakh
1112|3|The Interstellar Alliance
1126|3|Additional Rules and Clarifications
1136|2|Glossary
1216|2|Contacting Us
1226|3|Rules Questions and Support
1232|3|Prec-Reps Demo-Tourney Program
'@

# ---------------------------------------------------- hand-kept columns ----
# Scope tags, keyed by ID. These are INFERRED FROM HEADING TITLES and have not
# been confirmed by the owner; the generated document says so in one header line.
# Ladder: S1 base rules, S2 the League, S3 additional rules, S4 Psi Corps and
# Severed Dreams, S5 Wheel of Fire.
$scopeTags = @{
    'RB:I' = 'S1'; 'RB:I/game-overview' = 'S1'; 'RB:I/cards-and-tokens' = 'S1'
    'RB:I/anatomy-of-a-card' = 'S1'; 'RB:I/influence' = 'S1'; 'RB:I/victory' = 'S1'
    'RB:II' = 'S1'; 'RB:II/customizing-your-game-deck' = 'S1'; 'RB:II/choosing-your-race' = 'S1'
    'RB:II/your-starting-hand' = 'S1'; 'RB:II/shuffle-and-cut' = 'S1'
    'RB:II/preparing-the-playing-field' = 'S1'; 'RB:II/set-tension-tokens' = 'S1'
    'RB:II/set-influence-tokens' = 'S1'; 'RB:II/influence-changes' = 'S1'
    'RB:II/beginning-play' = 'S1'; 'RB:II/fast-and-loose' = 'S1'
    'RB:III' = 'S1'; 'RB:III/overview' = 'S1'; 'RB:III/narrating-the-story' = 'S1'
    'RB:III/ready-round' = 'S1'; 'RB:III/sustained-actions' = 'S1'
    'RB:III/ready-and-rotated-cards' = 'S1'; 'RB:III/conflict-round' = 'S1'
    'RB:III/sample-conflict-round' = 'S1'; 'RB:III/action-round' = 'S1'
    'RB:III/resolution-round' = 'S1'; 'RB:III/participants' = 'S1'; 'RB:III/draw-round' = 'S1'
    'RB:IV' = 'S1'; 'RB:IV/status-tracker-card' = 'S1'; 'RB:IV/character-cards' = 'S1'
    'RB:IV/bringing-characters-into-play' = 'S1'; 'RB:IV/your-starting-ambassador' = 'S1'
    'RB:IV/your-ambassadors-assistant' = 'S1'; 'RB:IV/group-cards' = 'S1'
    'RB:IV/fleet-cards' = 'S1'; 'RB:IV/location-cards' = 'S1'; 'RB:IV/enhancement-cards' = 'S1'
    'RB:IV/agenda-cards' = 'S1'; 'RB:IV/event-cards' = 'S1'; 'RB:IV/contingency-cards' = 'S1'
    'RB:IV/conflict-cards' = 'S1'; 'RB:IV/aftermath-cards' = 'S1'
    'RB:IV/type-of-conflict' = 'S1'; 'RB:IV/participant' = 'S1'; 'RB:IV/aftermath-example' = 'S1'
    'RB:V' = 'S1'
    'RB:VI' = 'S1'; 'RB:VI/sample-conflict' = 'S1'; 'RB:VI/triggered-effects' = 'S1'
    'RB:VI/personalities' = 'S1'; 'RB:VI/votes' = 'S1'; 'RB:VI/states' = 'S1'; 'RB:VI/war' = 'S1'
    'RB:VI/unconditional-surrender' = 'S1'; 'RB:VI/legal-targets' = 'S1'; 'RB:VI/marks' = 'S1'
    'RB:VI/discards' = 'S1'; 'RB:VI/bonuses-and-blanking' = 'S1'
    'RB:VI/cumulative-effects' = 'S1'; 'RB:VI/rules-of-precedence' = 'S1'
    'RB:VI/league-of-non-aligned-worlds' = 'S2'
    'RB:VI/alternate-factions' = 'S3'; 'RB:VI/dual-races' = 'S3'; 'RB:VI/tension-and-unrest' = 'S3'
    'RB:VI/voting' = 'S3'; 'RB:VI/joint-effects' = 'S3'; 'RB:VI/states-dual-races' = 'S3'
    'RB:VI/civil-war' = 'S3'; 'RB:VI/captured-locations' = 'S3'
    'RB:VI/power-and-victory-optional-rule' = 'S3'
    'RB:VI/more-factions' = 'S4'; 'RB:VI/psi-corps' = 'S4'; 'RB:VI/negative-power' = 'S4'
    'RB:VI/severed-dreams' = 'S4'; 'RB:VI/reducing-power-needed-to-win' = 'S4'
    'RB:VI/khari-characters' = 'S4'; 'RB:VI/cards-with-no-printed-cost' = 'S4'
    'RB:VI/errata' = 'S4'
    'RB:VI/wheel-of-fire' = 'S5'; 'RB:VI/drakh' = 'S5'
    'RB:VI/interstellar-alliance' = 'S5'; 'RB:VI/additional-rules-and-clarifications' = 'S5'
}

# Derived slugs longer than 40 characters, shortened by hand. Keyed by the
# derived slug, never by line number, so the entry survives a re-flow inside a
# chapter. An override that is no longer used is a guard failure: that means a
# heading changed and the shortening needs re-thinking, not re-applying.
$slugOverrides = @{
    'playing-fast-and-loose-and-resolving-timing-disputes' = 'fast-and-loose'
    'severed-dreams-new-rules-and-clarifications'          = 'severed-dreams'
    'wheel-of-fire-new-rules-and-clarifications'           = 'wheel-of-fire'
}

# Notes, keyed by ID. {blocks} is replaced with the row's block count at render
# time so that an ordinal range can never go stale.
$notes = @{
    'RB:IV/contingency-cards' = 'Base-rules heading, but no contingency card data is in the pool; the owner may prefer S4.'
    'RB:V' = 'One {blocks}-block entry with no sub-headings; cite a paragraph as RB:V#n.'
    'RB:VI/votes' = 'The census declares a VOTE class with 0 rows, so S1 here covers a rule no card uses.'
    'RB:VI/states' = 'The census declares a STATE class with 0 rows; see RB:VI/votes.'
    'RB:VI/marks' = 'The census declares a MARK class with 0 rows; mark behaviour may be filed under ONE_SHOT or TRIGGERED.'
    'RB:VI/power-and-victory-optional-rule' = 'Labelled optional; the rule-set profile should list it by ID and default it off unless the owner says otherwise.'
    'RB:VI/cards-with-no-printed-cost' = 'May settle open question Q5 (the four cost-less characters); whether it is expansion-specific is unconfirmed.'
}

# Non-normative divisions. Each folds several headings into one row and carries
# no scope tag, so counts taken over the scope column can never mistake front or
# back matter for a rule.
$divisions = @(
    [pscustomobject]@{ Id = 'RB:FM'; Start = 1;    End = 121;  Heading = 'Front matter (title lines and Table of Contents)';                                   Note = 'Non-normative; four headings folded into one division.' }
    [pscustomobject]@{ Id = 'RB:GL'; Start = 1136; End = 1215; Heading = 'Glossary';                                                                             Note = 'Per-term IDs of the form RB:GL/<term-slug> come in a later pass.' }
    [pscustomobject]@{ Id = 'RB:BM'; Start = 1216; End = 1236; Heading = 'Back matter (Contacting Us, Rules Questions and Support, Prec-Reps Demo-Tourney Program)'; Note = 'Non-normative; three headings folded into one division.' }
)

# ----------------------------------------------------------- functions ----
function ConvertTo-Slug {
    param([string] $Title)
    $s = $Title.ToLowerInvariant()
    $s = $s -replace '&', ' and '
    # Apostrophes vanish rather than becoming separators, so "Kha'Ri" is khari
    # and "Your Ambassador's" is your-ambassadors, not kha-ri / your-ambassador-s.
    $s = $s -replace "['\u2019]", ''
    $s = $s -replace '[^a-z0-9]+', ' '
    $s = (($s -replace '\s+', ' ').Trim())
    $s = $s -replace '^(a|an|the)\s+', ''
    $s = (($s -replace '\s+', ' ').Trim())
    if ($s.Length -eq 0) { return '' }
    return ($s -replace ' ', '-')
}

function Format-Title {
    # Fold typographic dashes to ASCII so the golden table can stay ASCII too.
    param([string] $Title)
    return ($Title -replace "[\u2012\u2013\u2014\u2015]", '-')
}

function Get-BlockCount {
    # A block is a maximal run of non-blank lines. This is the unit the optional
    # paragraph ordinal addresses, so the count has to match RB:V#n exactly.
    param([string[]] $Text, [int] $From, [int] $To)
    if ($To -lt $From) { return 0 }
    $count = 0
    $inBlock = $false
    for ($i = $From; $i -le $To; $i++) {
        if ([string]::IsNullOrWhiteSpace($Text[$i - 1])) { $inBlock = $false }
        elseif (-not $inBlock) { $count++; $inBlock = $true }
    }
    return $count
}

# ------------------------------------------------------- read and guard ----
if (-not (Test-Path -LiteralPath $Rulebook)) {
    Fail-Guard ('rulebook not found: ' + $Rulebook)
}
$rulebookAbs = (Resolve-Path -LiteralPath $Rulebook).Path

$hash = (Get-FileHash -LiteralPath $rulebookAbs -Algorithm SHA256).Hash.ToLowerInvariant()
if ($hash -ne $pinSha) {
    Fail-Guard ('rulebook SHA-256 ' + $hash + ' differs from the pin ' + $pinSha + '; the pinned bytes changed, so every line range below is void until consciously re-pinned')
}

# File.ReadAllLines both strips the pinned BOM and folds the 15 CRLF lines, so
# line numbers here are the 1236 LF lines the pin is stated in.
$lines = [System.IO.File]::ReadAllLines($rulebookAbs)
if ($lines.Count -ne $pinLines) {
    Fail-Guard ('rulebook has ' + $lines.Count + ' lines, the pin says ' + $pinLines)
}

$headingPattern = '^(#{1,6})\s+(?:\[\]\(#[^)]*\))?(.*)$'
$headings = New-Object System.Collections.ArrayList
for ($i = 0; $i -lt $lines.Count; $i++) {
    $m = [regex]::Match($lines[$i], $headingPattern)
    if (-not $m.Success) { continue }
    $null = $headings.Add([pscustomobject]@{
        Line  = $i + 1
        Depth = $m.Groups[1].Value.Length
        Title = $m.Groups[2].Value.Trim()
    })
}

# Exactly one heading title carries non-ASCII text: line 1, an em dash. Further
# damage of the kind RULEBOOK_PIN.md records must surface here rather than
# quietly changing a slug or vanishing from a comparison.
$nonAscii = @($headings | Where-Object { @($_.Title.ToCharArray() | Where-Object { [int]$_ -gt 127 }).Count -gt 0 })
if ($nonAscii.Count -ne 1 -or $nonAscii[0].Line -ne 1) {
    Fail-Guard ('expected exactly one non-ASCII heading title (line 1); found ' + $nonAscii.Count + ' at lines ' + (($nonAscii | ForEach-Object { $_.Line }) -join ','))
}

$goldenRows = @($golden -split "`r?`n" | Where-Object { $_.Trim().Length -gt 0 })
$parsedRows = @($headings | ForEach-Object { '{0}|{1}|{2}' -f $_.Line, $_.Depth, (Format-Title $_.Title) })
$differences = New-Object System.Collections.ArrayList
for ($i = 0; $i -lt [Math]::Max($parsedRows.Count, $goldenRows.Count); $i++) {
    $p = if ($i -lt $parsedRows.Count) { $parsedRows[$i] } else { '<no heading>' }
    $g = if ($i -lt $goldenRows.Count) { $goldenRows[$i] } else { '<no heading>' }
    if ($p -ne $g) { $null = $differences.Add(('  position {0}: rulebook={1} golden={2}' -f ($i + 1), $p, $g)) }
}
if ($differences.Count -gt 0) {
    Fail-Guard ("heading set drifted from the 92 golden headings:`n" + ($differences -join "`n"))
}

# ------------------------------------------------------ derive the IDs ----
$ruleEntries = New-Object System.Collections.ArrayList
$seenIds = @{}
$usedOverrides = @{}
$currentChapter = $null
$chapterCount = 0

foreach ($h in $headings) {
    if ($h.Line -lt $rulesStart -or $h.Line -gt $rulesEnd) { continue }

    $chapterMatch = [regex]::Match($h.Title, '^(I|II|III|IV|V|VI)\.\s+(.*)$')
    if ($chapterMatch.Success) {
        $currentChapter = $chapterMatch.Groups[1].Value
        $chapterCount++
        $id = 'RB:' + $currentChapter
        $null = $ruleEntries.Add([pscustomobject]@{ Line = $h.Line; Id = $id; Heading = $h.Title })
        $seenIds[$id] = $h.Line
        continue
    }

    if (-not $currentChapter) {
        Fail-Guard ('heading at line ' + $h.Line + ' has no enclosing numbered chapter')
    }

    $slug = ConvertTo-Slug $h.Title
    if ($slug.Length -gt 40) {
        if (-not $slugOverrides.ContainsKey($slug)) {
            Fail-Guard ('derived slug "' + $slug + '" (line ' + $h.Line + ') is ' + $slug.Length + ' characters long with no override')
        }
        $usedOverrides[$slug] = $true
        $slug = $slugOverrides[$slug]
    }
    if ($slug.Length -eq 0) {
        Fail-Guard ('heading at line ' + $h.Line + ' slugs to nothing')
    }

    $id = 'RB:' + $currentChapter + '/' + $slug
    if ($seenIds.ContainsKey($id)) {
        Fail-Guard ('ID ' + $id + ' derived twice, from lines ' + $seenIds[$id] + ' and ' + $h.Line)
    }
    $seenIds[$id] = $h.Line
    $null = $ruleEntries.Add([pscustomobject]@{ Line = $h.Line; Id = $id; Heading = $h.Title })
}

foreach ($key in $slugOverrides.Keys) {
    if (-not $usedOverrides.ContainsKey($key)) {
        Fail-Guard ('slug override "' + $key + '" is no longer used')
    }
}
if ($chapterCount -ne 6)      { Fail-Guard ('derived ' + $chapterCount + ' chapters, expected 6') }
if ($ruleEntries.Count -ne 84) { Fail-Guard ('derived ' + $ruleEntries.Count + ' rule entries, expected 84') }

for ($i = 0; $i -lt $ruleEntries.Count; $i++) {
    $start = $ruleEntries[$i].Line
    if ($i + 1 -lt $ruleEntries.Count) { $end = $ruleEntries[$i + 1].Line - 1 } else { $end = $rulesEnd }
    $ruleEntries[$i] | Add-Member -MemberType NoteProperty -Name 'Start' -Value $start -Force
    $ruleEntries[$i] | Add-Member -MemberType NoteProperty -Name 'End'   -Value $end   -Force
}

# ---------------------------------------------------------- build rows ----
function New-Row {
    param(
        [string]  $Id,
        [int]     $Start,
        [int]     $End,
        [string]  $Heading,
        [string]  $Note,
        [switch]  $BodyExcludesHeading
    )
    if ($BodyExcludesHeading) { $bodyStart = $Start + 1 } else { $bodyStart = $Start }
    $blockCount = Get-BlockCount -Text $lines -From $bodyStart -To $End

    # Divisions carry no scope, so a count taken over the scope column can never
    # mistake front or back matter for a rule.
    if ($Id -eq 'RB:FM' -or $Id -eq 'RB:GL' -or $Id -eq 'RB:BM') {
        $scope = '-'
    } else {
        if (-not $scopeTags.ContainsKey($Id)) {
            Fail-Guard ('ID ' + $Id + ' has no scope tag; add one to $scopeTags before regenerating')
        }
        $scope = $scopeTags[$Id]
    }

    $resolvedNote = $Note
    if ($notes.ContainsKey($Id)) { $resolvedNote = $notes[$Id] }
    if ($resolvedNote) { $resolvedNote = $resolvedNote -replace '\{blocks\}', [string]$blockCount }

    if ($Heading.Contains('|')) { Fail-Guard ('heading "' + $Heading + '" contains a pipe and cannot sit in a table cell') }
    if ($resolvedNote -and $resolvedNote.Contains('|')) { Fail-Guard ('note for ' + $Id + ' contains a pipe and cannot sit in a table cell') }

    $range = [string]$Start
    if ($End -ne $Start) { $range = $Start.ToString() + $en + $End.ToString() }

    return [pscustomobject]@{
        Id      = $Id
        Heading = $Heading
        Lines   = $range
        Blocks  = $blockCount
        Scope   = $scope
        Note    = $resolvedNote
        Start   = $Start
        End     = $End
    }
}

$rows = New-Object System.Collections.ArrayList
$null = $rows.Add((New-Row -Id $divisions[0].Id -Start $divisions[0].Start -End $divisions[0].End -Heading $divisions[0].Heading -Note $divisions[0].Note))
foreach ($e in $ruleEntries) {
    $row = New-Row -Id $e.Id -Start $e.Start -End $e.End -Heading $e.Heading -BodyExcludesHeading
    $null = $rows.Add($row)
}
$null = $rows.Add((New-Row -Id $divisions[1].Id -Start $divisions[1].Start -End $divisions[1].End -Heading $divisions[1].Heading -Note $divisions[1].Note))
$null = $rows.Add((New-Row -Id $divisions[2].Id -Start $divisions[2].Start -End $divisions[2].End -Heading $divisions[2].Heading -Note $divisions[2].Note))

# Tiling: every line of the pinned file belongs to exactly one row.
$expected = 1
foreach ($r in $rows) {
    if ($r.Start -ne $expected) {
        Fail-Guard ('row ' + $r.Id + ' starts at line ' + $r.Start + ', expected ' + $expected + ' (gap or overlap in the tiling)')
    }
    $expected = $r.End + 1
}
if ($expected -ne ($pinLines + 1)) {
    Fail-Guard ('rows cover lines 1 to ' + ($expected - 1) + ', the pin says the file has ' + $pinLines)
}

# ------------------------------------------------------------- render ----
$staticHeader = @'
# Rule-ID index

Every part of the pinned rulebook has one citation ID of the form
`RB:<chapter>/<slug>`, with an optional paragraph ordinal for long entries:
`RB:V#7` is the seventh block after heading RB:V, where a block is a maximal run
of non-blank lines. IDs are never renumbered and never reused. If a future re-pin
removes a heading, its ID is retired in place and a migration table is added,
because every citation already written has to keep resolving.

The rulebook body is never edited (AGENTS.md section 2), so IDs cannot be written
into it. This table is the mapping between an ID and the lines it means. It is
machine-derived from the pinned bytes: the generator refuses to run against any
other revision of that file, so an ID always names the same lines of the same
bytes.

Cite by ID in new text. A legacy `RB:<line>` citation stays valid and resolves by
range lookup against the Lines column below.

**Scope tags are inferred from heading titles, unconfirmed by the owner.**

## Columns

| Column | Kept by | Meaning |
|---|---|---|
| ID | generator | `RB:<chapter>/<slug>`. A chapter heading carries no slug: `RB:V`. |
| Heading | generator | The heading text in the pinned copy. |
| Lines | generator | Inclusive line range. The rows tile lines 1-1236 with no gap and no overlap. |
| Blocks | generator | Maximal runs of non-blank lines in the body, so an ordinal `RB:<id>#n` is checkable. The heading line itself is not a block. |
| Scope (provisional) | hand | Which rule-slice the section belongs to. `-` means the row is a non-normative division and has no scope. |
| Notes | hand | Open questions for the owner. |

## Divisions

Four divisions tile the file, and one row of the Index below belongs to each:

* `RB:FM`, lines 1-121: front matter - the wrapper header, two title lines and
  the Table of Contents. Non-normative.
* `RB:I` to `RB:VI`, lines 122-1135: the six numbered chapters, 84 entries.
* `RB:GL`, lines 1136-1215: the glossary. Per-term IDs (`RB:GL/<term>`) come in
  a later pass.
* `RB:BM`, lines 1216-1236: Contacting Us, Rules Questions and Support, and the
  Prec-Reps Demo-Tourney Program. Non-normative.

The Index below is the only table in this file whose rows begin with a citation
ID, so taking every row that starts with `RB:` yields exactly one complete,
non-overlapping tiling of the rulebook and nothing else.

## Regenerating

    powershell -NoProfile -File .agent/tools/build-rule-index.ps1

`-Check` asserts the file is already current, which is how "a second run produces
no diff" is demonstrated. Scope (provisional) and Notes are hand-kept inside the
generator, keyed by ID, so they survive every regeneration.

## Index
'@

$out = New-Object System.Collections.ArrayList
$null = $out.Add('---')
$null = $out.Add('document:')
$null = $out.Add('  title: "Rule-ID index for BABYLON5_CCG_RULEBOOK.md"')
$null = $out.Add('  status: "Generated truth table. Regenerate with .agent/tools/build-rule-index.ps1 (WP-18a); do not hand-edit the generated columns."')
$null = $out.Add('  index_version: "' + $indexVersion + '"')
$null = $out.Add('  rulebook_sha256: "' + $pinSha + '"')
$null = $out.Add('  rulebook_lines: ' + $pinLines)
$null = $out.Add('  rows: ' + [string]$rows.Count)
$null = $out.Add('provenance:')
$null = $out.Add('  author_llm: {name: "opencode (tencent/hy4-preview)", version: "opencode-hy4-preview-02"}')
$null = $out.Add('  created_date: "' + $createdDate + '"')
$null = $out.Add('  task: "' + $task + '"')
$null = $out.Add('  generator: ".agent/tools/build-rule-index.ps1 (WP-18a)"')
$null = $out.Add('  hand_kept_columns: "Scope (provisional) and Notes; keyed by ID inside the generator"')
$null = $out.Add('---')
$null = $out.Add('')
foreach ($line in ($staticHeader -split "`r?`n")) { $null = $out.Add($line) }
$null = $out.Add('')
$null = $out.Add('| ID | Heading | Lines | Blocks | Scope (provisional) | Notes |')
$null = $out.Add('|---|---|---|---|---|---|')
foreach ($r in $rows) {
    $note = ''
    if ($r.Note) { $note = $r.Note }
    $null = $out.Add(('| {0} | {1} | {2} | {3} | {4} | {5} |' -f $r.Id, $r.Heading, $r.Lines, $r.Blocks, $r.Scope, $note))
}

# LF, UTF-8, no BOM: same shape as docs/RULEBOOK_PIN.md. LF-only also keeps a
# generated file out of core.autocrlf churn in git status.
$content = ($out -join "`n") + "`n"

$outDir = Split-Path -Parent $OutFile
if (-not (Test-Path -LiteralPath $outDir)) {
    New-Item -ItemType Directory -Path $outDir -Force | Out-Null
}

$existing = $null
if (Test-Path -LiteralPath $OutFile) {
    $existing = ([System.IO.File]::ReadAllText($OutFile) -replace "`r`n", "`n")
}

if ($existing -eq $content) {
    Write-Output 'PASS'
    Write-Output ('rule index: ' + $OutFile + ' already current (' + $rows.Count + ' rows)')
    Write-Output '-- ENCODING: UTF-8 (no BOM), LF --'
    exit 0
}

if ($Check) {
    Write-Error ('build-rule-index: ' + $OutFile + ' differs from what the pinned rulebook generates; run the tool without -Check') -ErrorAction Continue
    exit 1
}

$enc = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllText($OutFile, $content, $enc)

Write-Output 'PASS'
Write-Output ('rule index: wrote ' + $rows.Count + ' rows to ' + $OutFile)
Write-Output ('  divisions: ' + $divisions.Count + ' (FM, GL, BM); rule entries: ' + $ruleEntries.Count + '; chapters: ' + $chapterCount)
Write-Output '-- ENCODING: UTF-8 (no BOM), LF --'
exit 0



