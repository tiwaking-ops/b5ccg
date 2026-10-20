<#
.SYNOPSIS
  Lint for encoding damage: U+FFFD replacement char, and the mojibake families
  that appear when UTF-8 bytes are read as CP1252/CP437 (an em dash becomes
  a-circumflex plus a Windows-1252 punctuation byte).
.DESCRIPTION
  Exits 1 on hit, 0 on clean. Scans *.md, *.ps1, *.json under the given paths.

  IMPORTANT: this file is deliberately pure ASCII. An earlier revision embedded
  the literal mojibake characters it was meant to detect ('a-circumflex
  euro-sign' and 'A-circumflex section-sign'). Because the file carried no BOM,
  Windows PowerShell read those multi-byte sequences as ANSI, mangled them
  further, and the tool failed to PARSE — so the encoding guard could not run at
  all. Every pattern below is therefore built from character codes, exactly as
  the U+FFFD check already was. Do not paste literal mojibake into this file.
#>
[CmdletBinding()]
param([string[]]$Paths = @('.agent/', 'docs/', 'b5ccg/src/'))

$ErrorActionPreference = 'Stop'

# U+FFFD REPLACEMENT CHARACTER
$cReplacement = [char]0xFFFD

# The UTF-8-read-as-CP1252 signature: a lead byte rendered as a Latin-1
# letter (A-circumflex / A-tilde / a-circumflex / a-tilde / i-diaeresis)
# immediately followed by a byte that CP1252 maps into the 0x80-0x9F range.
# Every mojibake family observed in this repo starts with one of these:
#   em dash   U+2014 -> 'a-circumflex' + U+20AC (euro)   ... actually U+0097 in CP1252
#   right arr U+2192 -> 'a-circumflex' + U+2020 + U+2019
#   multiply  U+00D7 -> 'A-tilde'       + U+2014
#   not-equal U+2260 -> 'a-circumflex'  + U+02C6 + U+0020
$leadChars = @(
    [char]0x00C2,   # A-circumflex
    [char]0x00C3,   # A-tilde
    [char]0x00E2,   # a-circumflex
    [char]0x00E5,   # a-tilde
    [char]0x00EF    # i-diaeresis
)

function Test-MojibakeLine {
    param([string]$Line)
    for ($i = 0; $i -lt $Line.Length - 1; $i++) {
        if ($leadChars -contains $Line[$i]) {
            $next = [int][char]$Line[$i + 1]
            # CP1252 0x80-0x9F maps to Latin-1 Supplement / General Punctuation,
            # never to a second ASCII letter. A plain accented word such as
            # 'cree' never produces a lead char followed by one of these.
            if (($next -ge 0x80 -and $next -le 0xBF) -or
                ($next -ge 0x2010 -and $next -le 0x2027) -or
                ($next -ge 0x20A0 -and $next -le 0x20BF)) {
                return $true
            }
        }
    }
    return $false
}

$bad = 0
$files = @()
foreach ($p in $Paths) {
    if (-not (Test-Path -LiteralPath $p)) { continue }
    $files += Get-ChildItem -LiteralPath $p -Recurse -File -ErrorAction SilentlyContinue |
        Where-Object { $_.Extension -in @('.md', '.ps1', '.json') }
}
$files = $files | Sort-Object FullName -Unique

foreach ($f in $files) {
    $t = [System.IO.File]::ReadAllText($f.FullName, (New-Object System.Text.UTF8Encoding($false)))
    $lines = $t -split "`r?`n"
    for ($l = 0; $l -lt $lines.Length; $l++) {
        $lineText = $lines[$l]
        if ($lineText.IndexOf($cReplacement) -ge 0) {
            Write-Output ("BAD {0}:{1} REPLACEMENT_CHAR (U+FFFD)" -f $f.FullName, ($l + 1))
            $bad++
        }
        if (Test-MojibakeLine -Line $lineText) {
            Write-Output ("BAD {0}:{1} MOJIBAKE_UTF8_READ_AS_CP1252" -f $f.FullName, ($l + 1))
            $bad++
        }
    }
}

if ($bad -gt 0) {
    Write-Output ("=== ENCODING LINT FAILED: {0} hit(s) ===" -f $bad)
    exit 1
}
Write-Output ("=== ENCODING LINT PASS ({0} files scanned) ===" -f @($files).Count)
exit 0