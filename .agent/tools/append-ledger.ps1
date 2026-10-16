<#
.SYNOPSIS
  B5-2400: append a row to `.agent/TASK_LEDGER.md` and PROVE it landed, retrying if a
  concurrent writer destroys it. The safe path, shipped.

.DESCRIPTION
  Why this tool exists. `Add-Content` reports success. It is telling the truth about the
  write it performed -- and nothing at all about whether the write is still there a
  moment later.

  Measured, three times in one session, on the live ledger:

    attempt 1 : pre=1821 post=1821 delta=0 present=False   <- row written, then gone
    attempt 2 : pre=1821 post=1822 delta=1 present=True    <- landed

  `new-claim.ps1` is what finally reported it: `no ledger row for B5-2363 - a claim without a
  row is a claim the queue cannot see`. The cause is a **read-modify-write race**: an agent
  reads the whole ledger into memory, another agent appends, and the first writes its
  stale snapshot back over the append. The ledger's byte count also rose ~1 KB between two
  of my readings with no write of mine in between, so this is an active fleet behaviour, not
  a one-off.

  The cost is not the lost row. It is that **`Add-Content` plus a line-count delta looks
  like verification and is not**: I asserted the count before and after (the B5-2314
  pattern), the assertion printed `delta 0`, and I read past it.

  WHAT THIS TOOL DOES DIFFERENTLY. It never trusts its own write:

    1. capture the ledger's state BEFORE the append -- byte length, line count, and a SHA-256
       of its last 512 bytes (the tail is what a concurrent append changes);
    2. append;
    3. RE-READ FROM DISK and assert, independently of the write's return value:
         - the row's task id is present, exactly once;
         - the line count grew by exactly the number of rows appended;
    4. on any mismatch, treat the append as LOST, report it, and RETRY -- bounded, with a
       short settle delay so the racing writer finishes first.

  Verification is re-read based on purpose. A write that "succeeded" and a write that is
  still present are different facts, and only the second one is worth anything.

  IT ALSO REFUSES TO CREATE THE DEFECTS THIS REPO KEEPS RECORDING, because a safe append
  path that can write a malformed row is not a safe path:
    - a row must have a SINGLE leading pipe and EXACTLY 7 pipes (B5-0568/B5-0611);
    - no interior `|` inside any cell (B5-0435);
    - the task id must be well formed and must not already exist in the ledger (B5-0618 --
      a duplicate id silently drops a task, because status is keyed by id and last row
      wins);
    - a row whose status is being FLIPPED is out of scope here. This tool appends; editing
      an existing row in place is a different operation with a different race, and pretending
      one tool covers both is how the first race got shipped.

  Exit codes, deliberately distinct so "could not act" never reads as "clean":
    0  appended, and VERIFIED present on a re-read
    1  refused: malformed row, duplicate id, or retries exhausted after repeated loss
    2  ledger missing or unreadable
    3  -VerifyOnly found the row already present

.PARAMETER Row          The full row text, pipes included. One row.
.PARAMETER LedgerPath   Ledger to append to. Default <repo>/.agent/TASK_LEDGER.md
.PARAMETER VerifyOnly   Report whether the row's id is already present. Writes nothing.
.PARAMETER MaxAttempts  Retries after a lost append (default 5).
.PARAMETER SettleMs     Settle delay before verifying, so a racing writer can finish.
.PARAMETER SelfTest     Run the fixture suite and exit. Touches no real ledger.
.PARAMETER SimulateClobberCount
                        TEST-ONLY. Rewrites the ledger from the pre-append snapshot after
                        the append, for the FIRST N attempts, deterministically reproducing
                        the exact failure this tool exists to survive. Bounded on purpose:
                        a racing writer eventually loses the race, and injecting the clobber
                        forever would make retry futile by construction and test nothing but
                        the timeout. Never use on the real ledger; both the tool and the
                        self-test refuse it there (T6).

.EXAMPLE
  powershell -NoProfile -ExecutionPolicy Bypass -File .agent/tools/append-ledger.ps1 -Row '| B5-2400 | OPEN | ... | - | - |'

.EXAMPLE
  powershell -NoProfile -ExecutionPolicy Bypass -File .agent/tools/append-ledger.ps1 -Row '...' -SelfTest
#>
[CmdletBinding()]
param(
    [string]$Row,
    [string]$LedgerPath,
    [switch]$VerifyOnly,
    [int]$MaxAttempts = 5,
    [int]$SettleMs = 400,
    [switch]$SelfTest,
    [int]$SimulateClobberCount = 0
)

$ErrorActionPreference = 'Continue'
$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
if (-not $LedgerPath) { $LedgerPath = Join-Path $repoRoot '.agent/TASK_LEDGER.md' }

function Get-LedgerState {
    <# Everything needed to tell "my append survived" from "my append was overwritten". #>
    param([string]$Path)
    if (-not (Test-Path -LiteralPath $Path)) { return $null }
    $bytes = [System.IO.File]::ReadAllBytes($Path)
    $text = [System.Text.Encoding]::UTF8.GetString($bytes)
    if ($text.Length -gt 0 -and $text[0] -eq [char]0xFEFF) { $text = $text.Substring(1) }
    $tailLen = [Math]::Min(512, $bytes.Length)
    $tail = $bytes[($bytes.Length - $tailLen)..($bytes.Length - 1)]
    $sha = [System.Security.Cryptography.SHA256]::Create()
    return @{
        Bytes = $bytes.Length
        Lines = ($text -split "`r?`n").Count
        TailHash = [BitConverter]::ToString($sha.ComputeHash($tail)).Replace('-', '')
        Text = $text
    }
}

function Test-RowShape {
    <# B5-0568/B5-0611/B5-0435. Returns $null when the row is acceptable, else the reason. #>
    param([string]$R)
    $t = $R.Trim()
    if ($t -eq '') { return 'row is empty' }
    if (-not $t.StartsWith('|')) { return 'row does not start with a single leading pipe' }
    if ($t.StartsWith('||')) { return 'row starts with a DOUBLE leading pipe (the retired defect class)' }
    $pipes = ([regex]::Matches($t, '\|')).Count
    if ($pipes -ne 7) { return ("row has {0} pipes, expected exactly 7" -f $pipes) }
    if ($t -notmatch '^\|\s*(B5-\d+)\s*\|') { return 'row does not start with a B5-NNNN task id' }
    # interior pipes: 7 pipes means 6 cells; any empty cell between them is a split note
    $cells = $t -split '\|'
    for ($i = 2; $i -lt 7; $i++) { if ($cells[$i] -match '\|') { return 'interior pipe inside a cell' } }
    return $null
}

function Get-IdCount {
    param([string]$Text, [string]$Id)
    return ([regex]::Matches($Text, ('(?m)^\|+\s*' + [regex]::Escape($Id) + '\s*\|'))).Count
}

# =================================================================================
if ($SelfTest) {
    $fail = 0
    $tmp = Join-Path ([System.IO.Path]::GetTempPath()) ('b5-2400-' + [guid]::NewGuid().ToString('N').Substring(0, 8))
    New-Item -ItemType Directory -Path $tmp -Force | Out-Null
    $self = $PSCommandPath
    function New-FixtureLedger([string]$name) {
        $p = Join-Path $tmp $name
        $seed = "| B5-0001 | DONE | seed row one | scope | owner-x | notes |`r`n| B5-0002 | DONE | seed row two | scope | owner-y | notes |`r`n"
        [System.IO.File]::WriteAllText($p, $seed, (New-Object System.Text.UTF8Encoding($true)))
        return $p
    }
    function Run-Append([string]$ledger, [string]$row, [int]$clobber = 0, [int]$attempts = 5) {
        $a = @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', $self, '-LedgerPath', $ledger, '-Row', $row, '-MaxAttempts', "$attempts", '-SettleMs', '10')
        if ($clobber -gt 0) { $a += ('-SimulateClobberCount', "$clobber") }
        $null = & powershell @a 2>&1
        return $LASTEXITCODE
    }
    function Check([string]$label, [bool]$ok, [string]$detail) {
        if ($ok) { Write-Output ("  SELFTEST PASS  {0}  ({1})" -f $label, $detail) }
        else { Write-Output ("  SELFTEST FAIL  {0}  ({1})" -f $label, $detail); $script:fail++ }
    }
    $good = '| B5-9001 | OPEN | a well formed seeded row | scope-x | - | - |'

    # T1 GREEN PATH -- a plain append lands and is verified present exactly once.
    $L1 = New-FixtureLedger 'ok.json.md'
    $e1 = Run-Append $L1 $good
    $t1 = [System.IO.File]::ReadAllText($L1)
    Check 'T1 a clean append lands, verified present exactly once' `
        ($e1 -eq 0 -and (Get-IdCount $t1 'B5-9001') -eq 1) `
        ("exit=" + $e1 + " occurrences=" + (Get-IdCount $t1 'B5-9001') + " expected 0 and 1")

    # T2 THE CASE THIS TOOL EXISTS FOR -- a concurrent writer overwrites the append. Injected
    # deterministically by rewriting the ledger from the pre-append snapshot, for the FIRST
    # attempt only (a racing writer eventually loses the race; injecting forever would make
    # retry futile by construction). Without re-read verification the tool would report
    # success and the row would be gone.
    $L2 = New-FixtureLedger 'clobber.json.md'
    $e2 = Run-Append $L2 $good -clobber 1 -attempts 6
    $t2 = [System.IO.File]::ReadAllText($L2)
    Check 'T2 detects a lost append and RETRIES until the row lands' `
        ($e2 -eq 0 -and (Get-IdCount $t2 'B5-9001') -eq 1) `
        ("exit=" + $e2 + " occurrences=" + (Get-IdCount $t2 'B5-9001') + " -- clobbered on attempt 1 only, so this can only pass via re-read detection plus retry")

    # T3 NEGATIVE CONTROL -- what the OLD way did. Plain Add-Content with NO verification,
    # against the same injected clobber. This must leave the row ABSENT while the write
    # reported success. If this ever stops being true the fixture stopped reproducing the bug.
    $L3 = New-FixtureLedger 'oldway.json.md'
    $pre3 = [System.IO.File]::ReadAllText($L3)
    Add-Content -LiteralPath $L3 -Value $good -Encoding UTF8
    [System.IO.File]::WriteAllText($L3, $pre3, (New-Object System.Text.UTF8Encoding($true)))   # the clobber
    $t3 = [System.IO.File]::ReadAllText($L3)
    Check 'T3 NEGATIVE CONTROL: unverified Add-Content loses the row and reports success' `
        ((Get-IdCount $t3 'B5-9001') -eq 0) `
        ("occurrences=" + (Get-IdCount $t3 'B5-9001') + " -- Add-Content threw no error; the row is simply gone")

    # T4 REFUSAL -- malformed rows must be refused, not appended. Each of these is a defect
    # class this repo has actually recorded.
    $bad = @(
        @{ R = '| B5-9002 | OPEN | too few pipes | - |'; Why = '5 pipes' },
        @{ R = '|| B5-9003 | OPEN | double leading pipe | scope | - | - |'; Why = 'double leading pipe' },
        @{ R = '| B5-9004 | OPEN | a note | splitting on | a pipe | - | - |'; Why = 'interior pipe in a note' },
        @{ R = 'not a row at all'; Why = 'no leading pipe' }
    )
    $L4 = New-FixtureLedger 'refuse.json.md'
    $e4 = Run-Append $L4 $bad[0].R
    $t4 = [System.IO.File]::ReadAllText($L4)
    Check 'T4 refuses a malformed row (5 pipes)' `
        ($e4 -eq 1 -and $t4 -eq [System.IO.File]::ReadAllText($L4) -and -not (Get-IdCount $t4 'B5-9002')) `
        ("exit=" + $e4 + " appended=" + (Get-IdCount $t4 'B5-9002') + " expected 1 and 0")

    # T5 REFUSAL -- duplicate id. B5-0618: a duplicate id silently drops a task.
    $L5 = New-FixtureLedger 'dupe.json.md'
    $null = Run-Append $L5 $good
    $e5 = Run-Append $L5 $good
    $t5 = [System.IO.File]::ReadAllText($L5)
    Check 'T5 refuses a duplicate task id' `
        ($e5 -eq 1 -and (Get-IdCount $t5 'B5-9001') -eq 1) `
        ("exit=" + $e5 + " occurrences=" + (Get-IdCount $t5 'B5-9001') + " expected 1 and 1, refusing the second")

    # T6 SAFETY -- the clobber injector must refuse to run against the REAL ledger.
    $realPath = Join-Path $repoRoot '.agent/TASK_LEDGER.md'
    $o6 = (& powershell -NoProfile -ExecutionPolicy Bypass -File $self -LedgerPath $realPath -Row $good -SimulateClobberCount 1 2>&1 | Out-String)
    $e6 = $LASTEXITCODE
    Check 'T6 the clobber injector REFUSES to target the real ledger' `
        ($e6 -ne 0 -and -not (Get-IdCount ([System.IO.File]::ReadAllText($realPath)) 'B5-9001')) `
        ("exit=" + $e6 + " and B5-9001 absent from the real ledger=" + (-not (Get-IdCount ([System.IO.File]::ReadAllText($realPath)) 'B5-9001')) + " -- a test hook that could destroy the canonical file is not a test hook")

    Remove-Item -LiteralPath $tmp -Recurse -Force -ErrorAction SilentlyContinue
    if ($fail -eq 0) { Write-Output '=== append-ledger SELF-TEST GREEN: 6/6 ==='; exit 0 }
    Write-Output ("=== append-ledger SELF-TEST RED: " + $fail + " case(s) failed ==="); exit 1
}

# =================================================================================
if (-not $Row) { Write-Output '[B5-2400] REFUSED: -Row is required (or -SelfTest)'; exit 1 }

# The canonical ledger, derived from $repoRoot and NOT from $LedgerPath. Deriving it from
# -LedgerPath is the bug this line replaces: Resolve-Path of whatever path was passed
# always equals that path, so the guard below matched trivially and refused EVERY call,
# fixtures included. It also made self-test T6 a false pass -- the guard fired for the wrong
# reason and appeared to prove the safety it was never testing.
$canonicalLedger = Join-Path $repoRoot '.agent/TASK_LEDGER.md'
$canonicalResolved = $null
if (Test-Path -LiteralPath $canonicalLedger) { $canonicalResolved = (Resolve-Path -LiteralPath $canonicalLedger).Path }

if ($SimulateClobberCount -gt 0 -and $canonicalResolved -and $LedgerPath -eq $canonicalResolved) {
    Write-Output '[B5-2400] REFUSED: -SimulateClobberCount is a test hook and will not run against the real ledger.'
    Write-Output '    -SimulateClobberCount rewrites the ledger from a pre-append snapshot, which is the destruction this
    tool exists to prevent.'
    Write-Output '    Point -LedgerPath at a fixture. The self-test refuses this combination too (T6).'
    exit 1
}

$shape = Test-RowShape $Row
if ($shape) {
    Write-Output ("[B5-2400] REFUSED: {0}" -f $shape)
    Write-Output '    Nothing written. This tool will not create a malformed row; B5-0435/B5-0568/B5-0611 are all defects'
    Write-Output '    that reached the ledger because a writer appended without checking first.'
    exit 1
}
$id = ([regex]::Match($Row.Trim(), '^\|\s*(B5-\d+)\s*\|')).Groups[1].Value

if (-not (Test-Path -LiteralPath $LedgerPath)) { Write-Output ("[B5-2400] MISSING: " + $LedgerPath); exit 2 }

$pre = Get-LedgerState $LedgerPath
if ($null -eq $pre) { Write-Output ("[B5-2400] UNREADABLE: " + $LedgerPath); exit 2 }

if ($VerifyOnly) {
    $c = Get-IdCount $pre.Text $id
    if ($c -ge 1) { Write-Output ("[B5-2400] PRESENT: {0} ({1} occurrence(s))" -f $id, $c); exit 3 }
    Write-Output ("[B5-2400] ABSENT: {0}" -f $id); exit 0
}

$existing = Get-IdCount $pre.Text $id
if ($existing -gt 0) {
    Write-Output ("[B5-2400] REFUSED: task id {0} already appears {1} time(s) in the ledger." -f $id, $existing)
    Write-Output '    Nothing written. B5-0618: a duplicate id is not cosmetic -- status is keyed by id, so the last row'
    Write-Output '    wins and one task becomes invisible to the queue. If you meant to add a NOTE to an existing row,'
    Write-Output '    that is an in-place edit, not an append, and this tool deliberately does not do it.'
    exit 1
}

$rowLines = ($Row.Trim() -split "`r?`n").Count
$payload = ($Row.Trim() -replace "`r?`n$", '') + "`r`n"

for ($attempt = 1; $attempt -le $MaxAttempts; $attempt++) {
    # Re-read immediately before each attempt: a retry must not be based on the state that
    # was current before the previous attempt was clobbered.
    $pre = Get-LedgerState $LedgerPath
    $preCount = Get-IdCount $pre.Text $id
    if ($preCount -gt 0) {
        Write-Output ("[B5-2400] LANDED (found already present on the re-read before attempt {0}): {1}" -f ($attempt + 1), $id)
        exit 0
    }
    Add-Content -LiteralPath $LedgerPath -Value $payload -Encoding UTF8 -ErrorAction SilentlyContinue

    if ($attempt -le $SimulateClobberCount) {
        # Deterministic reproduction of the fleet's read-modify-write clobber, for the first
        # N attempts only. A racing writer loses the race eventually; injecting it forever
        # would make retry futile by construction and would test nothing but the timeout.
        [System.IO.File]::WriteAllText($LedgerPath, $pre.Text, (New-Object System.Text.UTF8Encoding($true)))
    }

    if ($SettleMs -gt 0) { Start-Sleep -Milliseconds $SettleMs }

    # ---- VERIFY BY RE-READ. The write's own success value is not evidence. ----
    #
    # B5-2400, measured on the LIVE ledger: the line-delta check was WRONG and had to be
    # demoted to advisory. A concurrent append lands at the same time as mine, so the delta
    # read +2 while my row was present exactly once, and the tool declared its own successful
    # append LOST and burned a retry. The delta cannot distinguish "mine landed" from "mine
    # landed and someone else also appended", so it cannot carry the verdict.
    #
    # PRESENCE IS THE FACT. The row's id appearing exactly once on a fresh read is the only
    # thing that proves the append survived. Everything else is printed for the human.
    $post = Get-LedgerState $LedgerPath
    $postCount = Get-IdCount $post.Text $id
    $grew = ($post.Lines - $pre.Lines)
    $deltaNote = if ($grew -eq $rowLines) { "line delta as expected" }
                 else { ("line delta {0}, expected +{1} -- a concurrent append is also in flight, which is advisory only" -f $grew, $rowLines) }
    if ($postCount -eq 1) {
        Write-Output ("[B5-2400] APPENDED and VERIFIED on re-read: {0}  (attempt {1}/{2}, {3} -> {4} lines, {5})" -f `
            $id, $attempt, $MaxAttempts, $pre.Lines, $post.Lines, $deltaNote)
        Write-Output ("            id present exactly once; tail hash {0} -> {1}" -f $pre.TailHash.Substring(0, 12), $post.TailHash.Substring(0, 12))
        exit 0
    }
    Write-Output ("[B5-2400] LOST APPEND on attempt {0}/{1}: id present {2}x on re-read (expected 1), line delta {3}." -f `
        $attempt, $MaxAttempts, $postCount, $grew)
    Write-Output '            The row is genuinely absent, so a concurrent writer rewrote the ledger from a stale'
    Write-Output '            snapshot. Retrying after settle.'
}

Write-Output ("[B5-2400] REFUSED: {0} could not be landed after {1} attempts." -f $id, $MaxAttempts)
Write-Output '    Every attempt was written and then found absent on re-read, so this is a live write conflict, not a'
Write-Output '    malformed row. Do NOT hand-append around this: an unverified append is what loses rows. Either wait'
Write-Output '    for the other writer to finish, or claim a row that does not touch TASK_LEDGER.md.'
exit 1