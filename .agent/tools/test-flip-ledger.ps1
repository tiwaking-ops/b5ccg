<#
.SYNOPSIS
  Acceptance probe for flip-ledger.ps1, built as a self-contained sandbox.
.DESCRIPTION
  B5-0044. This is the red-then-green evidence for the ledger-flip guard. It
  creates a throwaway copy of the repository layout under a temp directory and
  exercises flip-ledger.ps1 against it, so the real ledger is never touched.

  Every case asserts a REFUSAL PATH or a NO-WRITE PATH, not a happy path. That
  is deliberate. A guard is only evidence if you have seen it refuse. The
  original 13-case list was 6/9 PASS, and 6 of those passed for the wrong
  reason: they passed because the tool crashed, not because it refused
  correctly, and one of them passed while the tool had silently written to
  disk.

  The cases:
    1  a live claim held by ANOTHER agent blocks the flip
    2  a .RELEASED marker does not count as a live claim
    3  a sibling id is not blocked by a different id's claim
    4  a malformed id is rejected
    5  DONE without a report is refused
    6  a refused flip leaves NO backup and NO change
    7  -DryRun prints the diff and writes NOTHING (no ledger, no backup)
    8  exactly one line differs and CRLF is preserved
    9  an unknown parameter is rejected rather than absorbed
    10 a live claim held by the CALLER does not block the flip
    11 another agent's live claim still blocks, even when the caller names
       a different agent id

  Exits 0 when every case holds, 1 otherwise, listing the failures.
#>
[CmdletBinding()]
param(
  [string]$SandboxRoot
)

$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$tool = Join-Path $PSScriptRoot 'flip-ledger.ps1'
if (-not (Test-Path -LiteralPath $tool)) { Write-Error "flip-ledger.ps1 not found at $tool"; exit 1 }
if (-not $SandboxRoot) {
  $SandboxRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("b5-flip-ledger-" + [Guid]::NewGuid().ToString('N').Substring(0,8))
}
if (Test-Path -LiteralPath $SandboxRoot) { Remove-Item -LiteralPath $SandboxRoot -Recurse -Force }
$sb = $SandboxRoot
$null = New-Item -ItemType Directory -Force -Path (Join-Path $sb '.agent/CLAIMS')
$null = New-Item -ItemType Directory -Force -Path (Join-Path $sb '.agent/REPORTS')
$null = New-Item -ItemType Directory -Force -Path (Join-Path $sb '.agent/RESCUE')
$null = New-Item -ItemType Directory -Force -Path (Join-Path $sb '.agent/tools')
Copy-Item -LiteralPath $tool -Destination (Join-Path $sb '.agent/tools/flip-ledger.ps1') -Force
$sbTool = Join-Path $sb '.agent/tools/flip-ledger.ps1'
$sbLedger = Join-Path $sb '.agent/TASK_LEDGER.md'
$sbBak = Join-Path $sb '.agent/RESCUE'

function Reset-Sandbox {
  param([bool]$WithReport)
  $rows = @(
    '| ID | Status | Task | Scope | Claim | Report |',
    '|---|---|---|---|---|---|',
    '| B5-0202 | OPEN | parent row | tooling | - | - |',
    '| B5-0202c | OPEN | sibling row | tooling | - | - |',
    '| B5-0300 | OPEN | subject row | tooling | - | - |'
  )
  [System.IO.File]::WriteAllText($sbLedger, ($rows -join "`r`n") + "`r`n", (New-Object System.Text.UTF8Encoding($false)))
  Remove-Item (Join-Path $sb '.agent/REPORTS/*') -Force -EA SilentlyContinue
  Remove-Item (Join-Path $sb '.agent/RESCUE/*') -Force -EA SilentlyContinue
  Remove-Item (Join-Path $sb '.agent/CLAIMS/*') -Force -EA SilentlyContinue
  if ($WithReport) {
    [System.IO.File]::WriteAllText((Join-Path $sb '.agent/REPORTS/2026-10-19-me-agent-01-B5-0300.md'), '# r', (New-Object System.Text.UTF8Encoding($false)))
  }
}
function Get-Status {
  param([string]$Id)
  $m = Select-String -LiteralPath $sbLedger -Pattern ("^\|\s*" + [regex]::Escape($Id) + "\s*\|")
  if ($m) { return (($m.Line -split '\|')[2]).Trim() }
  return '(none)'
}
function Invoke-Flip {
  param([string[]]$Arguments)
  $old = $ErrorActionPreference
  $ErrorActionPreference = 'Continue'
  try {
    $out = & powershell -NoProfile -ExecutionPolicy Bypass -File $sbTool @Arguments 2>&1
    $code = $LASTEXITCODE
  } finally { $ErrorActionPreference = $old }
  # The text is whitespace-normalised. PowerShell's Out-String WRAPS the
  # console output to the host width, and it wrapped in the middle of the
  # phrase "A parameter cannot be found" - so a literal substring match
  # against the wrapped text failed even though the message was present.
  # Collapsing runs of whitespace to a single space makes the assertion
  # independent of console width, which is what a test of this kind needs.
  $flat = (($out | ForEach-Object { [string]$_ }) -join ' ') -replace '\s+', ' '
  return @{ Code = $code; Text = $flat }
}

$failures = @()
$results = @()

# --- case 1: a live claim by ANOTHER agent blocks the flip ---------------
Reset-Sandbox $true
[System.IO.File]::WriteAllText((Join-Path $sb '.agent/CLAIMS/B5-0300.json'), '{"task":"B5-0300","agent_id":"other-agent-99","started_utc":"2026-10-19T18:00:00Z","ttl_min":30}', (New-Object System.Text.UTF8Encoding($false)))
$before = [System.IO.File]::ReadAllText($sbLedger)
$r = Invoke-Flip @('-Id','B5-0300','-NewStatus','DONE')
$after = [System.IO.File]::ReadAllText($sbLedger)
$bak = @(Get-ChildItem $sbBak -EA SilentlyContinue).Count
$ok = ($r.Code -ne 0) -and ($before -eq $after) -and ($bak -eq 0) -and ((Get-Status 'B5-0300') -eq 'OPEN')
if (-not $ok) { $failures += "case 1: live claim by another agent did NOT block the flip (exit=$($r.Code), changed=$($before -ne $after), backups=$bak)" }
$results += [pscustomobject]@{ N=1; Verdict=$(if($ok){'PASS'}else{'FAIL'}) }

# --- case 2: a .RELEASED marker is not a live claim ----------------------
Reset-Sandbox $true
[System.IO.File]::WriteAllText((Join-Path $sb '.agent/CLAIMS/B5-0300.RELEASED.json'), '{"task":"B5-0300","agent_id":"other-agent-99","started_utc":"2026-10-19T18:00:00Z","ttl_min":30}', (New-Object System.Text.UTF8Encoding($false)))
$r = Invoke-Flip @('-Id','B5-0300','-NewStatus','DONE')
$ok = ($r.Code -eq 0) -and ((Get-Status 'B5-0300') -eq 'DONE')
if (-not $ok) { $failures += "case 2: a .RELEASED marker blocked the flip (exit=$($r.Code), status=$(Get-Status 'B5-0300'))" }
$results += [pscustomobject]@{ N=2; Verdict=$(if($ok){'PASS'}else{'FAIL'}) }

# --- case 3: a sibling id is not blocked by another id's claim -----------
Reset-Sandbox $true
[System.IO.File]::WriteAllText((Join-Path $sb '.agent/CLAIMS/B5-0202.json'), '{"task":"B5-0202","agent_id":"other-agent-99","started_utc":"2026-10-19T18:00:00Z","ttl_min":30}', (New-Object System.Text.UTF8Encoding($false)))
$r = Invoke-Flip @('-Id','B5-0202c','-NewStatus','BLOCKED')
$ok = ($r.Code -eq 0) -and ((Get-Status 'B5-0202c') -eq 'BLOCKED')
if (-not $ok) { $failures += "case 3: sibling id was blocked by a different id's claim (exit=$($r.Code), status=$(Get-Status 'B5-0202c'))" }
$results += [pscustomobject]@{ N=3; Verdict=$(if($ok){'PASS'}else{'FAIL'}) }

# --- case 4: a malformed id is rejected ----------------------------------
Reset-Sandbox $false
$r = Invoke-Flip @('-Id','NOPE','-NewStatus','BLOCKED')
$ok = ($r.Code -ne 0) -and ((Get-Status 'B5-0202') -eq 'OPEN')
if (-not $ok) { $failures += "case 4: malformed id was not rejected (exit=$($r.Code))" }
$results += [pscustomobject]@{ N=4; Verdict=$(if($ok){'PASS'}else{'FAIL'}) }

# --- case 5: DONE without a report is refused ----------------------------
Reset-Sandbox $false
$r = Invoke-Flip @('-Id','B5-0202','-NewStatus','DONE')
$ok = ($r.Code -ne 0) -and ((Get-Status 'B5-0202') -eq 'OPEN')
if (-not $ok) { $failures += "case 5: DONE without a report was accepted (exit=$($r.Code), status=$(Get-Status 'B5-0202'))" }
$results += [pscustomobject]@{ N=5; Verdict=$(if($ok){'PASS'}else{'FAIL'}) }

# --- case 6: a refused flip leaves NO backup and NO change ---------------
Reset-Sandbox $false
$before = [System.IO.File]::ReadAllText($sbLedger)
$r = Invoke-Flip @('-Id','B5-0202','-NewStatus','DONE')
$after = [System.IO.File]::ReadAllText($sbLedger)
$bak = @(Get-ChildItem $sbBak -EA SilentlyContinue).Count
$ok = ($r.Code -ne 0) -and ($before -eq $after) -and ($bak -eq 0)
if (-not $ok) { $failures += "case 6: refused flip left a change or a backup (changed=$($before -ne $after), backups=$bak)" }
$results += [pscustomobject]@{ N=6; Verdict=$(if($ok){'PASS'}else{'FAIL'}) }

# --- case 7: -DryRun prints the diff and writes NOTHING -------------------
Reset-Sandbox $true
$before = [System.IO.File]::ReadAllText($sbLedger)
$r = Invoke-Flip @('-Id','B5-0300','-NewStatus','DONE','-DryRun')
$after = [System.IO.File]::ReadAllText($sbLedger)
$bak = @(Get-ChildItem $sbBak -EA SilentlyContinue).Count
$ok = ($r.Code -eq 0) -and ($before -eq $after) -and ($bak -eq 0) -and ((Get-Status 'B5-0300') -eq 'OPEN') -and ($r.Text -match 'DRY RUN')
if (-not $ok) { $failures += "case 7: -DryRun wrote something (exit=$($r.Code), changed=$($before -ne $after), backups=$bak, status=$(Get-Status 'B5-0300'))" }
$results += [pscustomobject]@{ N=7; Verdict=$(if($ok){'PASS'}else{'FAIL'}) }

# --- case 8: exactly one line differs; CRLF preserved --------------------
Reset-Sandbox $false
$beforeBytes = [System.IO.File]::ReadAllBytes($sbLedger)
$beforeLines = [System.IO.File]::ReadAllText($sbLedger) -split "`r`n"
$r = Invoke-Flip @('-Id','B5-0202c','-NewStatus','BLOCKED')
$afterBytes = [System.IO.File]::ReadAllBytes($sbLedger)
$afterLines = [System.IO.File]::ReadAllText($sbLedger) -split "`r`n"
$diff = 0
for ($i=0; $i -lt [Math]::Min($beforeLines.Count,$afterLines.Count); $i++) { if ($beforeLines[$i] -ne $afterLines[$i]) { $diff++ } }
$crlfBefore = @($beforeBytes | Where-Object { $_ -eq 13 }).Count
$crlfAfter = @($afterBytes | Where-Object { $_ -eq 13 }).Count
$ok = ($r.Code -eq 0) -and ($diff -eq 1) -and ($crlfBefore -eq $crlfAfter) -and ($crlfAfter -gt 0)
if (-not $ok) { $failures += "case 8: expected 1 differing line with CRLF preserved (diff=$diff, crlf $crlfBefore->$crlfAfter)" }
$results += [pscustomobject]@{ N=8; Verdict=$(if($ok){'PASS'}else{'FAIL'}) }

# --- case 9: an unknown parameter is rejected ----------------------------
Reset-Sandbox $false
$r = Invoke-Flip @('-Id','B5-0202','-NewStatus','BLOCKED','-NotAParameter','x')
$ok = ($r.Code -ne 0) -and ($r.Text -match 'A parameter cannot be found')
if (-not $ok) { $failures += ("case 9: unknown parameter was not rejected (exit={0}); output was: {1}" -f $r.Code, ($r.Text.Trim() -replace '\s+', ' ')) }
$results += [pscustomobject]@{ N=9; Verdict=$(if($ok){'PASS'}else{'FAIL'}) }

# --- case 10: a live claim held by the CALLER is not a blocker ------------
# The agent that holds a claim is the agent expected to close the row out,
# and this tool is how it does that. Only another agent's claim blocks.
Reset-Sandbox $true
[System.IO.File]::WriteAllText((Join-Path $sb '.agent/CLAIMS/B5-0300.json'), '{"task":"B5-0300","agent_id":"me-agent-01","started_utc":"2026-10-19T18:00:00Z","ttl_min":30}', (New-Object System.Text.UTF8Encoding($false)))
$r = Invoke-Flip @('-Id','B5-0300','-NewStatus','DONE','-AgentId','me-agent-01')
$ok = ($r.Code -eq 0) -and ((Get-Status 'B5-0300') -eq 'DONE')
if (-not $ok) { $failures += ("case 10: the caller's own live claim blocked the flip (exit={0}, status={1})" -f $r.Code, (Get-Status 'B5-0300')) }
$results += [pscustomobject]@{ N=10; Verdict=$(if($ok){'PASS'}else{'FAIL'}) }

# --- case 11: a live claim by ANOTHER agent still blocks, even when the ----
#     caller names a different agent id -----------------------------------
Reset-Sandbox $true
[System.IO.File]::WriteAllText((Join-Path $sb '.agent/CLAIMS/B5-0300.json'), '{"task":"B5-0300","agent_id":"other-agent-99","started_utc":"2026-10-19T18:00:00Z","ttl_min":30}', (New-Object System.Text.UTF8Encoding($false)))
$before = [System.IO.File]::ReadAllText($sbLedger)
$r = Invoke-Flip @('-Id','B5-0300','-NewStatus','DONE','-AgentId','me-agent-01')
$after = [System.IO.File]::ReadAllText($sbLedger)
$ok = ($r.Code -ne 0) -and ($before -eq $after) -and ((Get-Status 'B5-0300') -eq 'OPEN')
if (-not $ok) { $failures += ("case 11: another agent's live claim did not block the flip (exit={0}, status={1})" -f $r.Code, (Get-Status 'B5-0300')) }
$results += [pscustomobject]@{ N=11; Verdict=$(if($ok){'PASS'}else{'FAIL'}) }

Write-Output '=== flip-ledger acceptance (sandboxed) ==='
foreach ($x in $results) { Write-Output ("  [{0}] case {1}" -f $x.Verdict, $x.N) }
Write-Output ''
if ($failures.Count -gt 0) {
  Write-Output ("FAIL: {0} of {1} case(s) failed" -f $failures.Count, $results.Count)
  foreach ($f in $failures) { Write-Output ("  - {0}" -f $f) }
  Remove-Item -LiteralPath $sb -Recurse -Force -EA SilentlyContinue
  exit 1
}
Write-Output ("PASS: {0}/{1} cases" -f $results.Count, $results.Count)
Remove-Item -LiteralPath $sb -Recurse -Force -EA SilentlyContinue
exit 0
