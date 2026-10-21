param(
  [Parameter(Mandatory=$true)][string[]]$ScorerFiles,
  [Parameter(Mandatory=$true)][string]$GroundTruthFile,
  [Parameter(Mandatory=$true)][string]$OutFile,
  [string[]]$ScorerNames
)

$ErrorActionPreference = 'Stop'
# Score the calibration corpus: read the scorer sheets, compare them against
# the held-out answer key, and report agreement. B5-0046.
#
# The answer key is the corpus's own family table, so the key and the corpus
# cannot drift apart. The scorers saw a package built by
# build-scorer-package.ps1, from which every key field has been removed.

function Read-Scoring([string]$path) {
    $map = @{}
    $cur = $null
    foreach ($line in [System.IO.File]::ReadAllLines($path)) {
        if ($line -match '^\s*case:\s*([A-D]-\d+)\s*$') { $cur = $matches[1]; $map[$cur] = @{ Verdict = ''; Defect = '' } ; continue }
        if ($cur -and $line -match '^\s*verdict:\s*(.+?)\s*$') { $map[$cur].Verdict = $matches[1].ToUpper() ; continue }
        if ($cur -and $line -match '^\s*defect:\s*(.+?)\s*$') { $map[$cur].Defect = $matches[1] ; continue }
    }
    return $map
}

function Read-GroundTruth([string]$path) {
    $map = @{}
    $fams = @{}
    foreach ($l in [System.IO.File]::ReadAllLines($path)) {
        if ($l -match '^\|\s*([A-D])\.\s+[^|]*\|\s*\d+\s*\|\s*(DEFECTIVE|SOUND)\s*\|') { $fams[$matches[1]] = $matches[2]; continue }
        if ($l -match '^###\s+([A-D]-\d+)\.') {
            $id = $matches[1]
            $map[$id] = @{ Verdict = $fams[$id.Substring(0,1)]; Defect = '' }
            continue
        }
    }
    return $map
}

$gt = Read-GroundTruth $GroundTruthFile
$sheets = @()
for ($i = 0; $i -lt $ScorerFiles.Count; $i++) {
    $name = if ($ScorerNames -and $i -lt $ScorerNames.Count) { $ScorerNames[$i] } else { [System.IO.Path]::GetFileNameWithoutExtension($ScorerFiles[$i]) }
    $sheets += @{ Name = $name; Data = (Read-Scoring $ScorerFiles[$i]) }
}
$scorerCount = $sheets.Count

$ids = $gt.Keys | Sort-Object
$rows = @()
foreach ($id in $ids) {
    $g = $gt[$id].Verdict
    $verdicts = @()
    foreach ($sh in $sheets) {
        $v = if ($sh.Data.ContainsKey($id)) { $sh.Data[$id].Verdict } else { 'MISSING' }
        $verdicts += $v
    }
    $distinct = @($verdicts | Sort-Object -Unique)
    $unanimous = ($distinct.Count -eq 1)
    $majority = ($verdicts | Group-Object | Sort-Object Count -Descending | Select-Object -First 1).Name
    $matches = @()
    foreach ($sh in $sheets) {
        if ($sh.Data.ContainsKey($id)) { $m = ($sh.Data[$id].Verdict -eq $g) } else { $m = $false }
        $matches += $m
    }
    $rows += [pscustomobject]@{
        Case = $id
        Truth = $g
        Verdicts = $verdicts
        Unanimous = $unanimous
        Majority = $majority
        MajorityMatches = ($majority -eq $g)
        MatchCount = (@($matches | Where-Object { $_ }).Count)
    }
}

$n = $rows.Count
$unanimous = @($rows | Where-Object { $_.Unanimous }).Count
$majorityOk = @($rows | Where-Object { $_.MajorityMatches }).Count
$allOk = @($rows | Where-Object { $_.MatchCount -eq $scorerCount }).Count
$noneOk = @($rows | Where-Object { $_.MatchCount -eq 0 }).Count
$disputed = @($rows | Where-Object { $_.Unanimous -eq $false })

$sb = New-Object System.Text.StringBuilder
[void]$sb.AppendLine("=== Calibration corpus scoring (B5-0046) ===")
[void]$sb.AppendLine("cases                    : $n")
[void]$sb.AppendLine("scorers                  : $scorerCount ($((($sheets | ForEach-Object { $_.Name }) -join ', ')))")
[void]$sb.AppendLine("unanimous agreement      : $unanimous / $n")
[void]$sb.AppendLine("majority vote == truth   : $majorityOk / $n")
[void]$sb.AppendLine("ALL scorers == truth     : $allOk / $n")
[void]$sb.AppendLine("NO scorer == truth       : $noneOk / $n")
[void]$sb.AppendLine("disputed                 : $($disputed.Count)  -> $($disputed.Case -join ', ')")
[void]$sb.AppendLine("")
foreach ($sh in $sheets) {
    $ok = 0
    foreach ($r in $rows) { if ($sh.Data.ContainsKey($r.Case) -and $sh.Data[$r.Case].Verdict -eq $r.Truth) { $ok++ } }
    [void]$sb.AppendLine(("  {0,-22} matches truth: {1} / {2}" -f $sh.Name, $ok, $n))
}
[void]$sb.AppendLine("")
[void]$sb.AppendLine("CASE  TRUTH      " + (($sheets | ForEach-Object { $_.Name.Substring(0,[Math]::Min(9,$_.Name.Length)).PadRight(10) }) -join ''))
[void]$sb.AppendLine("-" * (20 + 10 * $scorerCount))
foreach ($r in $rows) {
    [void]$sb.AppendLine(("{0,-5} {1,-10} {2}" -f $r.Case, $r.Truth, (($r.Verdicts | ForEach-Object { $_.PadRight(10) }) -join '')))
}
[void]$sb.AppendLine("")
[void]$sb.AppendLine("Disputed cases (not unanimous), with each scorer's phrase:")
foreach ($r in $disputed) {
    [void]$sb.AppendLine("")
    [void]$sb.AppendLine("  $($r.Case)  truth=$($r.Truth)")
    for ($i = 0; $i -lt $sheets.Count; $i++) {
        $d = if ($sheets[$i].Data.ContainsKey($r.Case)) { $sheets[$i].Data[$r.Case].Defect } else { '(missing)' }
        [void]$sb.AppendLine("    $($sheets[$i].Name): $d")
    }
}

[System.IO.File]::WriteAllText($OutFile, $sb.ToString(), (New-Object System.Text.UTF8Encoding($false)))
Write-Output $sb.ToString()
