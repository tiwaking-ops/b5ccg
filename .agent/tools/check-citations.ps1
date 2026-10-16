<#
.SYNOPSIS
  B5-1925: find every `.agent/REPORTS/...` and `.agent/PATTERNS/...` citation in
  the ledger and the decision register whose target file does not exist on disk.

.DESCRIPTION
  Why this tool exists. A DONE task's ledger row and its `docs/DECISIONS.md` entry
  both name a report and a pattern file. Nothing checked that those files exist.
  Measured 2026-10-01: of 238 such citations, SIX dangled while the rows asserting
  them read DONE with verification recorded. Two were reports that were never filed
  at all (B5-0472, B5-0485); two named a real file under the wrong path; two named
  a real file under the wrong filename. The record looked complete and was not, and
  the only reason it surfaced was an incidental check of one task whose report I had
  already reconstructed by hand (B5-1471).

  Three categories of reference are counted but NOT scored as defects, because
  scoring them manufactures false alarms at scale (the clock report's 6.3 failure):

   * GLOB      - contains `*`. A pattern, not a citation, e.g. `.agent/REPORTS/*.md`.
   * LOSSY     - contains `?`. Prose that mangled `:` or `/` on its way through a
                 quoting or transcoding layer. Not resolvable by construction, so
                 flagging it is noise: there are 11+ such refs here, nearly all
                 `solar-pro4?free`, where `:` is ILLEGAL in an NTFS filename and the
                 real path is the sanitised `solar-pro4-free`.
   * UNRESOLVABLE - contains a character NTFS forbids (`:`, `<`, `>`, `|`, `"`, `?`).
                 Same reasoning; reported for visibility, never a defect.

  The `:` case is the sharpest trap in this repo and the reason this tool exists as
  code rather than as a grep a human runs. `solar-pro4:free` is a REAL agent_id,
  cited in 100+ ledger rows, and its pattern directory is legally named
  `solar-pro4:free` only on filesystems that permit colons. Hand such a path to
  PowerShell's `Join-Path` or `Test-Path` and it is read as DRIVE `solar-pro4:` plus
  a relative path, producing a wall of "Cannot find drive" errors that mask every
  other result. This tool resolves with a leading-slash-free relative path and
  `-LiteralPath`, so a colon in a name is just a character.

  Read-only. Writes nothing, modifies nothing, deletes nothing.

.OUTPUTS
  Exit 0 = no dangling citations (the pass condition; empty defect list).
  Exit 1 = at least one dangling citation, each printed with source file and line.
  Exit 2 = a source file is missing or unreadable. Deliberately NOT 0: a census that
           could not read the ledger must never report as a clean ledger (the same
           discipline run-dup-census.ps1 uses).
#>
[CmdletBinding()]
param(
    [string[]]$Source,
    [switch]$Quiet
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
if (-not $Source) { $Source = @('.agent/TASK_LEDGER.md', 'docs/DECISIONS.md') }

$abs = @()
foreach ($s in $Source) {
    # An absolute -Source must be used verbatim; only a repo-relative one is joined
    # onto the repo root. Without this test, Join-Path produced nonsense like
    # <repo>\C:\Users\... and the tool exited 2 on a perfectly good fixture
    # (self-caught in the B5-1925 fail-proof fixture run).
    if ([System.IO.Path]::IsPathRooted($s)) { $abs += $s } else { $abs += (Join-Path $repoRoot $s) }
}
foreach ($a in $abs) {
    if (-not (Test-Path -LiteralPath $a)) {
        Write-Output ("[B5-1925] EXIT 2: source not found or unreadable: {0}" -f $a)
        exit 2
    }
}

# Only .md targets: reports and patterns are Markdown documents. Matching .ps1/.json
# too would sweep in deliberate historical references to deleted scratch scripts,
# which are record, not defects.
$refPattern = '\.agent/(?:REPORTS|PATTERNS)/[^\s`\u0029\uFF09\u3001\uFF0C\uFF1B\*"]+\.md'

$dangling = New-Object System.Collections.Generic.List[object]
$total = 0
$globs = 0
$lossy = 0
$unresolvable = 0

foreach ($a in $abs) {
    # Label the source relative to the repo root when it lives inside it, else by
    # its own name. A plain substring on an out-of-tree path produced mangled labels
    # like "encode/cit/fixture.md" (self-caught in the B5-1925 fail-proof run).
    $rel = $a
    if ($a.StartsWith($repoRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
        $rel = $a.Substring($repoRoot.Length + 1) -replace '\\', '/'
    }
    $lineNo = 0
    foreach ($line in (Get-Content -LiteralPath $a -Encoding UTF8)) {
        $lineNo++
        foreach ($m in [regex]::Matches($line, $refPattern)) {
            $total++
            $ref = $m.Value

            if ($ref.Contains('*')) { $globs++; continue }
            if ($ref.Contains('?')) { $lossy++; continue }
            # A colon is legal in an agent_id but illegal in an NTFS filename. Such a
            # reference is a LOSSY prose rendering of a sanitised path, not a broken
            # link, and cannot be resolved by guessing. Counted, never a defect.
            if ($ref -match '[:<>|]') { $unresolvable++; continue }

            $native = $ref -replace '/', '\'
            if (Test-Path -LiteralPath (Join-Path $repoRoot $native)) { continue }

            $dangling.Add([pscustomobject]@{
                Source = $rel
                Line   = $lineNo
                Ref    = $ref
                Text   = $line.Trim()
            })
        }
    }
}

Write-Output ("[B5-1925] sources  : {0}" -f ($abs | ForEach-Object { $_; } | Measure-Object).Count)
foreach ($a in $abs) { Write-Output ("[B5-1925]   {0}" -f $a) }
Write-Output ("[B5-1925] refs scanned: {0}" -f $total)
Write-Output ("[B5-1925] skipped: {0} glob, {1} lossy (`?` or NTFS-illegal char, unresolvable by construction)" -f $globs, ($lossy + $unresolvable))
Write-Output ""

if ($dangling.Count -eq 0) {
    Write-Output "[B5-1925] PASS: every resolvable REPORTS/PATTERNS citation resolves to a file on disk."
    exit 0
}

Write-Output ("[B5-1925] FAIL: {0} dangling citation(s)." -f $dangling.Count)
Write-Output ""
foreach ($d in $dangling) {
    Write-Output ("  {0}:{1}" -f $d.Source, $d.Line)
    Write-Output ("    ref     : {0}" -f $d.Ref)
    if (-not $Quiet) {
        $t = $d.Text
        if ($t.Length -gt 150) { $t = $t.Substring(0, 150) + '...' }
        Write-Output ("    context : {0}" -f $t)
    }
    # Name the likely intended target so the fix does not require a manual search:
    # same basename, anywhere under .agent/.
    $leaf = Split-Path ($d.Ref -replace '/', '\') -Leaf
    $stem = $leaf -replace '\.md$', ''
    $hits = @()
    foreach ($base in @('.agent/REPORTS', '.agent/PATTERNS')) {
        $baseAbs = Join-Path $repoRoot ($base -replace '/', '\')
        if (Test-Path -LiteralPath $baseAbs) {
            $hits += @(Get-ChildItem -LiteralPath $baseAbs -Recurse -File -Filter ('*' + $stem + '*') -ErrorAction SilentlyContinue)
        }
    }
    if ($hits.Count -eq 1) {
        $rp = $hits[0].FullName.Substring($repoRoot.Length + 1) -replace '\\', '/'
        Write-Output ("    likely target (1 basename match): {0}" -f $rp)
    } elseif ($hits.Count -gt 1) {
        Write-Output ("    basename '{0}' matches {1} files -- disambiguate manually:" -f $stem, $hits.Count)
        foreach ($h in $hits) { Write-Output ("       {0}" -f ($h.FullName.Substring($repoRoot.Length + 1) -replace '\\', '/')) }
    } else {
        Write-Output ("    NO file with basename '{0}' anywhere under .agent/ -- the artifact was likely never filed." -f $stem)
    }
    Write-Output ""
}

exit 1