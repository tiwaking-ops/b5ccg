param(
  [Parameter(Mandatory=$true)][string]$Source,
  [Parameter(Mandatory=$true)][string]$Dest
)
# Build a scorer package from the calibration corpus: the case bodies, with
# every ground-truth field removed. Used by B5-0046 so that a scorer cannot
# see the expected answers.
$ErrorActionPreference = 'Stop'

$src = [System.IO.File]::ReadAllText($Source)
$lines = $src -split "`r?`n"
$out = New-Object System.Collections.Generic.List[string]

$FIELD_KEYS = 'expect|ground_truth_source|note'
$i = 0
while ($i -lt $lines.Count) {
  $l = $lines[$i]
  # A ground-truth field opens with a backticked key at the start of the line.
  if ($l -match "^\s*``($FIELD_KEYS):") {
    $i++
    # Consume continuation lines: indented, non-empty, and not a new construct.
    while ($i -lt $lines.Count) {
      $c = $lines[$i]
      $isNew = ($c -match '^\s*``') -or ($c -match '^### ') -or ($c -match '^\*\*Question') -or ($c -match '^\|') -or ($c -match '^\s*$') -or ($c -match '^---')
      if ($isNew) { break }
      $i++
    }
    # Drop a blank line left behind by the removed field.
    if ($out.Count -gt 0 -and $out[$out.Count-1] -match '^\s*$') { $out.RemoveAt($out.Count-1) }
    continue
  }
  $out.Add($l)
  $i++
}

$clean = ($out -join "`n")

# The family table states how many cases in each family are DEFECTIVE and how
# many are SOUND. That is the answer key in table form, so the table is
# removed from the scorer package, line by line, rather than by pattern.
$tableLines = New-Object System.Collections.Generic.List[string]
$inTable = $false
foreach ($l in ($clean -split "`n")) {
  if ($l -match '^\| Family \| Cases \|') { $inTable = $true; continue }
  if ($inTable) {
    if ($l -match '^\|---') { continue }
    if ($l -match '^\|') { continue }
    $inTable = $false
  }
  $tableLines.Add($l)
}
$clean = ($tableLines -join "`n")
$clean = $clean -replace '(?s)Family D exists because a corpus in which every answer is DEFECTIVE measures\r?\nnothing:.*?judge from a guesser\.', 'A calibration corpus needs both outcomes, or it cannot tell a judge from a guesser.'

# The header paragraph names the field keys; rewrite it so the package does
# not advertise a structure the scorer cannot see.
$clean = $clean -replace '(?s)\*\*The expected answer is not shown to the scorer\.\*\*.*?reading comprehension\.', '**The expected answers are not shown to the scorer.** They are held separately, for scoring against, not for the scorer. A scorer that can see an answer measures reading comprehension, not judgement.'

# The family headers name the provenance of each family, which identifies the
# answers by where they came from.
$clean = $clean -replace '(?s)## Family A - guards that are dead or mis-aimed \(12 cases, all DEFECTIVE\)', '## Family A'
$clean = $clean -replace '(?s)## Family B - stubs that assert nothing \(8 cases, all DEFECTIVE\)', '## Family B'
$clean = $clean -replace '(?s)## Family C - tools that absorb what they should reject \(5 cases, all DEFECTIVE\)', '## Family C'
$clean = $clean -replace '(?s)## Family D - the fixes for those same defects \(8 cases, all SOUND\)', '## Family D'
$clean = $clean -replace '(?s)These are the repairs this repository shipped for the defects in families A\s*\r?\nand C\.\s*Each snippet achieves the required behaviour, so the answer is yes\.', 'Each snippet in this family achieves the required behaviour, so the answer is yes.'
$clean = $clean -replace '(?s)\*\*Facts that hold for every case in this family\*\*.*?make the answer determinate\.', '**Facts that hold for every case in this family**, stated here so a scorer does not need any other repository:

* The file is the **only** file that mentions the type or class it declares. No other file in the tree implements it, extends it, instantiates it, calls it, or refers to it by name. There is one type or class per case, and it has **no implementer and no caller**.
* Nothing in the tree tests it. It is not a gate class: the build''s test list does not name it.
* Therefore nothing in the running program can be affected by it.'

# The two `note:` cases explain which of two readings the ground truth takes.
# Their note blocks were removed above, but A-11 and C-3 also carry prose in
# the question paragraph that hints at the answer; neutralise it.
$clean = $clean -replace '(?s)Two cases \(A-11, C-3\) are marked `note:`.*?rather than pattern-match\.', 'Two of the cases turn on a judgement call rather than a single mechanical reading. A scorer is expected to reason about that judgement explicitly in the `defect` field, and to say which reading it chose.'

[System.IO.File]::WriteAllText($Dest, $clean, (New-Object System.Text.UTF8Encoding($false)))
Write-Output ("wrote {0} ({1} bytes)" -f $Dest, (Get-Item $Dest).Length)
