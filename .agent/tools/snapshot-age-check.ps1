# snapshot-age-check.ps1 — B0 stale-snapshot alert (B5-0001).
# Exit 0: HEAD is newer than -MaxAgeHours (default 24). Exit 1: STALE.
# Exit 2: git unreadable. Read the exit code, not stdout.
param([int]$MaxAgeHours = 24)
$ErrorActionPreference = 'Stop'
try {
  $headTs = git log -1 --format=%ct 2>$null
} catch { Write-Output "UNREADABLE: git log failed"; exit 2 }
if (-not $headTs) { Write-Output "UNREADABLE: no commits"; exit 2 }
$headUtc = [DateTimeOffset]::FromUnixTimeSeconds([long]$headTs)
$ageH = ([DateTimeOffset]::UtcNow - $headUtc).TotalHours
Write-Output ("HEAD age: {0:N1} h (limit {1} h)" -f $ageH, $MaxAgeHours)
if ($ageH -gt $MaxAgeHours) { Write-Output "ALERT: snapshot is stale"; exit 1 }
Write-Output "FRESH: snapshot within limit"
exit 0
