# snapshot-age-check.ps1 — B0 stale-snapshot alert (B5-0001, -Ref added B5-0009).
# Exit 0: <Ref> is newer than -MaxAgeHours (default 24). Exit 1: STALE.
# Exit 2: git unreadable (includes naming a ref that does not exist yet).
# Read the exit code, not stdout. Default Ref HEAD preserves the B5-0001
# contract; pass -Ref auto-snapshot to watch the timer job once it lands.
param([int]$MaxAgeHours = 24, [string]$Ref = 'HEAD')
$ErrorActionPreference = 'Stop'
try {
  $headTs = git log -1 --format=%ct $Ref 2>$null
} catch { Write-Output "UNREADABLE: git log failed"; exit 2 }
if (-not $headTs) { Write-Output "UNREADABLE: no commits"; exit 2 }
$headUtc = [DateTimeOffset]::FromUnixTimeSeconds([long]$headTs)
$ageH = ([DateTimeOffset]::UtcNow - $headUtc).TotalHours
Write-Output ("[{0}] age: {1:N1} h (limit {2} h)" -f $Ref, $ageH, $MaxAgeHours)
if ($ageH -gt $MaxAgeHours) { Write-Output "ALERT: snapshot is stale"; exit 1 }
Write-Output "FRESH: snapshot within limit"
exit 0
