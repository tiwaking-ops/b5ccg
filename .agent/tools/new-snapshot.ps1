<#
.SYNOPSIS
  B0 non-agent snapshot job (B5-0009): commit the working tree to a snapshot
  branch on a timer. The owner enables it via Task Scheduler; agents never run
  it in live mode and never commit by hand.

.DESCRIPTION
  What it does, in order:
    1. Refuses when the tree is mid-merge/mid-rebase, when another snapshot
       holds the lock, or when the secret scan trips. Refusal exits nonzero
       and commits nothing.
    2. Runs a secret scan over tracked files plus untracked non-ignored files
       (private-key headers, AKIA / ghp_ / gho_ token shapes, credential
       assignments with real-looking values). .gitignore already keeps build
       output, logs, compiled classes and *.pem/*.key/.env out of both the
       scan and the commit; the scan is defence in depth for secrets pasted
       into otherwise-committed source.
    3. Stages the working tree into a THROWAWAY index (GIT_INDEX_FILE under
       .git), so the real index, the checked-out branch and every working-tree
       file are untouched. The snapshot commit is then built with plumbing
       (write-tree / commit-tree / update-ref) against refs/heads/<Branch>.
       First run parents the snapshot on current HEAD; later runs parent on
       the previous snapshot tip, so the branch is a linear backup history
       that is never merged back.
    4. Pushes only with -Push (default off: no remote/auth is assumed in a
       scheduler context).

  Safety contract (why a timer job is safe to run while work is in flight):
    * never checks out or switches branches;
    * never writes a working-tree file (the only write is the run log, which
      is gitignored and never committed);
    * never touches the real index;
    * the only ref it moves is the snapshot branch, via compare-and-swap
      update-ref; agents keep "no commit, no push" (D3: this job is the
      approved non-agent exception, pending owner enable).

  Exit codes: 0 committed, or nothing-to-do (tree identical to snapshot tip),
  or dry run clean. 1 blocked/refused (lock held, secrets hit, merge state,
  bad branch name, a git step failed). 2 environment (no git, not a repo,
  no HEAD). Read the exit code, not stdout.

.PARAMETER Branch   Snapshot branch to advance. Default auto-snapshot.
.PARAMETER DryRun   Report what would be committed; write no commit, move no ref.
.PARAMETER Push     Also push the snapshot branch to origin. Default off.
.PARAMETER LogPath  Run log. Default <repo>/snapshot.log (gitignored: *.log).

.EXAMPLE
  powershell -NoProfile -ExecutionPolicy Bypass -File .agent/tools/new-snapshot.ps1 -DryRun
#>
[CmdletBinding()]
param(
    [string]$Branch = 'auto-snapshot',
    [switch]$DryRun,
    [switch]$Push,
    [string]$LogPath
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
if (-not $LogPath) { $LogPath = Join-Path $repoRoot 'snapshot.log' }

function Write-Log {
    param([string]$Message)
    $line = ('[{0}] {1}' -f [DateTimeOffset]::UtcNow.ToString('yyyy-MM-ddTHH:mm:ssZ'), $Message)
    Write-Output $line
    try { Add-Content -LiteralPath $LogPath -Value $line -Encoding UTF8 } catch { }
}

function Invoke-Git {
    param([string[]]$GitArgs)
    # Git writes diagnostics (e.g. autocrlf warnings) to stderr, which 2>&1
    # folds into ErrorRecords. Under the script-wide Stop preference those
    # would throw and masquerade as failures, so judge git ONLY by its exit
    # code here; stderr text is captured into .Out for logging.
    $prev = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $out = (& git -C $repoRoot @GitArgs 2>&1)
        $code = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $prev
    }
    return @{ Exit = $code; Out = ($out | Out-String).Trim() }
}

# ---- 0. environment ---------------------------------------------------------
if (-not (Get-Command git -ErrorAction SilentlyContinue)) {
    Write-Log 'EXIT 2: git not found on PATH; nothing committed.'
    exit 2
}
$gr = Invoke-Git @('rev-parse', '--absolute-git-dir')
if ($gr.Exit -ne 0) {
    Write-Log 'EXIT 2: not a git repository; nothing committed.'
    exit 2
}
$gitDir = $gr.Out
$hr = Invoke-Git @('rev-parse', '--verify', '--quiet', 'HEAD')
if ($hr.Exit -ne 0 -or -not $hr.Out) {
    Write-Log 'EXIT 2: no HEAD commit; nothing committed.'
    exit 2
}
$headSha = $hr.Out
$fmt = Invoke-Git @('check-ref-format', ('refs/heads/' + $Branch))
if ($fmt.Exit -ne 0) {
    Write-Log ('EXIT 1: refused: branch name [{0}] fails check-ref-format; nothing committed.' -f $Branch)
    exit 1
}

# ---- 1. refuse mid-merge / mid-rebase ---------------------------------------
foreach ($flag in @('MERGE_HEAD', 'rebase-merge', 'rebase-apply')) {
    if (Test-Path -LiteralPath (Join-Path $gitDir $flag)) {
        Write-Log ('EXIT 1: refused: [{0}] present, tree is mid-merge/rebase; nothing committed.' -f $flag)
        exit 1
    }
}

# ---- 2. lock -----------------------------------------------------------------
$lockPath = Join-Path $gitDir 'snapshot.lock'
$lockOurs = $false
if (Test-Path -LiteralPath $lockPath) {
    $lockTaken = $true
    try {
        $raw = [IO.File]::ReadAllText($lockPath)
        $m = [regex]::Match($raw, 'pid=(\d+);utc=([0-9T:\-Z]+)')
        if ($m.Success) {
            $pid = [int]$m.Groups[1].Value
            $stamp = [DateTime]::MinValue
            if ([DateTime]::TryParse($m.Groups[2].Value, [ref]$stamp)) {
                $ageMin = ([DateTime]::UtcNow - $stamp).TotalMinutes
                $alive = $false
                try { $null = Get-Process -Id $pid -ErrorAction Stop; $alive = $true } catch { }
                if ($alive -and $ageMin -lt 30) { $lockTaken = $true } else { $lockTaken = $false }
            }
        }
    } catch { $lockTaken = $false }
    if ($lockTaken) {
        Write-Log 'EXIT 1: refused: another snapshot holds the lock; nothing committed.'
        exit 1
    }
    Write-Log 'stale lock found; taking over.'
}
try {
    [IO.File]::WriteAllText($lockPath, ('pid={0};utc={1}' -f $PID, [DateTimeOffset]::UtcNow.ToString('yyyy-MM-ddTHH:mm:ssZ')))
    $lockOurs = $true
} catch {
    Write-Log 'EXIT 1: refused: cannot write lock; nothing committed.'
    exit 1
}

$tmpIndex = Join-Path $gitDir 'snapshot-index.tmp'
if (Test-Path -LiteralPath $tmpIndex) { Remove-Item -LiteralPath $tmpIndex -Force }

try {
    # ---- 3. source branch (recorded in the message; never switched) -----------
    $br = Invoke-Git @('rev-parse', '--abbrev-ref', 'HEAD')
    $srcBranch = if ($br.Exit -eq 0 -and $br.Out) { $br.Out } else { 'unknown' }

    # ---- 4. secret scan (tracked + untracked non-ignored; .gitignore enforced
    #        by ls-files --exclude-standard, so ignored scratch never scans) ----
    $scanPats = @(
        '-----BEGIN (RSA |EC |DSA |OPENSSH |PGP )?PRIVATE KEY( BLOCK)?-----',
        'AKIA[0-9A-Z]{16}',
        '(ghp|gho)_[A-Za-z0-9]{36,}',
        '(?i)passw(or)?d\s*[:=]\s*[''"][A-Za-z0-9][^''"]{3,}[''"]'
    )
    $tr = Invoke-Git @('-c', 'core.quotepath=false', 'ls-files', '-z')
    $ur = Invoke-Git @('-c', 'core.quotepath=false', 'ls-files', '--others', '--exclude-standard', '-z')
    if ($tr.Exit -ne 0 -or $ur.Exit -ne 0) {
        Write-Log 'EXIT 1: refused: could not enumerate files for the scan; nothing committed.'
        exit 1
    }
    $rels = @()
    foreach ($chunk in @($tr.Out, $ur.Out)) {
        foreach ($p in ($chunk -split "`0")) {
            if ($p -ne '') { $rels += $p }
        }
    }
    $full = @()
    foreach ($r in $rels) { $full += (Join-Path $repoRoot $r) }
    $hits = @()
    $unreadable = @()
    if ($full.Count -gt 0) {
        try {
            $found = Select-String -Path $full -Pattern $scanPats -ErrorAction Stop |
                Select-Object -ExpandProperty Path -Unique
            foreach ($f in $found) { $hits += $f }
        } catch {
            foreach ($f in $full) {
                try {
                    $one = Select-String -LiteralPath $f -Pattern $scanPats -ErrorAction Stop |
                        Select-Object -First 1
                    if ($one) { $hits += $f }
                } catch { $unreadable += $f }
            }
        }
    }
    if ($hits.Count -gt 0) {
        Write-Log ('EXIT 1: refused: secret scan hit {0} file(s); names only, values never logged:' -f $hits.Count)
        foreach ($h in $hits) { Write-Log ('  HIT: ' + $h) }
        Write-Log 'nothing committed. Remove the secret or extend .gitignore, then re-run.'
        exit 1
    }
    if ($unreadable.Count -gt 0) {
        Write-Log ('WARN: {0} file(s) unreadable to the scanner; committed anyway, review them:' -f $unreadable.Count)
        foreach ($u in $unreadable) { Write-Log ('  UNREADABLE: ' + $u) }
    }
    Write-Log ('secret scan clean over {0} file(s).' -f $full.Count)

    # ---- 5. stage into the throwaway index; the real index is untouched -------
    $env:GIT_INDEX_FILE = $tmpIndex
    $rt = Invoke-Git @('read-tree', 'HEAD')
    if ($rt.Exit -ne 0) { Write-Log 'EXIT 1: read-tree failed; nothing committed.'; exit 1 }
    $ad = Invoke-Git @('add', '-A')
    if ($ad.Exit -ne 0) { Write-Log 'EXIT 1: git add failed (locked file?); nothing committed, retry next run.'; exit 1 }
    $wt = Invoke-Git @('write-tree')
    if ($wt.Exit -ne 0 -or -not $wt.Out) { Write-Log 'EXIT 1: write-tree failed; nothing committed.'; exit 1 }
    $tree = $wt.Out
    Write-Log ('staged tree {0} into throwaway index; real index untouched.' -f $tree)

    # ---- 6. parent: previous snapshot tip, else HEAD (first run) -------------
    $prev = Invoke-Git @('rev-parse', '--verify', '--quiet', ('refs/heads/' + $Branch))
    $parent = $null
    $oldSha = $null
    if ($prev.Exit -eq 0 -and $prev.Out) {
        $oldSha = $prev.Out
        $pt = Invoke-Git @('rev-parse', ($oldSha + '^{tree}'))
        if ($pt.Exit -eq 0 -and $pt.Out -eq $tree) {
            Write-Log ('EXIT 0: nothing-to-do: tree identical to {0} tip {1}.' -f $Branch, $oldSha.Substring(0, 12))
            exit 0
        }
        $parent = $oldSha
    } else {
        $parent = $headSha
    }

    $df = Invoke-Git @('diff', '--name-only', $parent, $tree)
    $files = @()
    if ($df.Out -ne '') { $files = @($df.Out -split "`r?`n") }
    Write-Log ('change vs parent: {0} file(s).' -f $files.Count)

    $stamp = [DateTimeOffset]::UtcNow.ToString('yyyy-MM-ddTHH:mm:ssZ')
    $msg = ('snapshot: {0} from {1} [{2}]' -f $stamp, $srcBranch, $headSha.Substring(0, 12)) +
        "`n`nscheduled non-agent snapshot (B0). Source branch $srcBranch at $headSha." +
        ("`nFiles changed vs parent: {0}." -f $files.Count)
    $shown = 0
    foreach ($f in $files) {
        if ($shown -ge 40) { break }
        $msg += ("`n  " + $f)
        $shown++
    }
    if ($files.Count -gt 40) { $msg += ("`n  ... +{0} more" -f ($files.Count - 40)) }

    if ($DryRun) {
        Write-Log 'DRY RUN: would commit the following; writing no commit, moving no ref.'
        Write-Log ('  branch : {0} (exists: {1})' -f $Branch, (($null -ne $oldSha)))
        Write-Log ('  parent : {0}' -f $parent)
        Write-Log ('  tree   : {0}' -f $tree)
        Write-Log ('  files  : {0}' -f $files.Count)
        Write-Log 'EXIT 0: dry run clean.'
        exit 0
    }

    $ct = Invoke-Git @('commit-tree', $tree, '-p', $parent, '-m', $msg)
    if ($ct.Exit -ne 0 -or -not $ct.Out) { Write-Log 'EXIT 1: commit-tree failed; moving no ref.'; exit 1 }
    $newSha = ($ct.Out -split "`r?`n")[0].Trim()
    if ($oldSha) { $up = Invoke-Git @('update-ref', ('refs/heads/' + $Branch), $newSha, $oldSha) }
    else { $up = Invoke-Git @('update-ref', ('refs/heads/' + $Branch), $newSha) }
    if ($up.Exit -ne 0) { Write-Log ('EXIT 1: update-ref failed; commit {0} unreferenced, retry next run.' -f $newSha); exit 1 }
    Write-Log ('EXIT 0: snapshot {0} on {1} ({2} files).' -f $newSha.Substring(0, 12), $Branch, $files.Count)

    if ($Push) {
        $ph = Invoke-Git @('push', 'origin', $Branch)
        if ($ph.Exit -ne 0) {
            Write-Log ('WARN: push failed; snapshot is local-only on {0}. Output: {1}' -f $Branch, $ph.Out)
        } else {
            Write-Log ('pushed {0} to origin.' -f $Branch)
        }
    }
    exit 0
} finally {
    $env:GIT_INDEX_FILE = $null
    if (Test-Path -LiteralPath $tmpIndex) { Remove-Item -LiteralPath $tmpIndex -Force -ErrorAction SilentlyContinue }
    if ($lockOurs -and (Test-Path -LiteralPath $lockPath)) {
        Remove-Item -LiteralPath $lockPath -Force -ErrorAction SilentlyContinue
    }
}
