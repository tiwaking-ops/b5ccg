<#
.SYNOPSIS
  Negative-case probe for the B5-0044 guard class: the five shipped tools must
  REJECT an unknown parameter instead of silently absorbing it.
.DESCRIPTION
  Red-then-green evidence for a process rule. Before B5-0044, none of these five
  tools declared [CmdletBinding()], so a typo in a parameter name was silently
  ignored and the tool ran to a normal exit. The worst case measured was
  ledger-query.ps1 -Statuss OPEN: exit 0, a normal table printed, and the
  intended filter never applied. A tool that exits 0 on a misspelled parameter
  is worse than a tool that crashes, because the reader believes it.

  This probe asserts the REJECTION path, not the happy path. For each tool it
  invokes the tool with a parameter that does not exist and asserts:
    1. the exit code is non-zero, and
    2. the error names the offending parameter.
  It then invokes each tool with its documented parameters and asserts the tool
  still runs, so the fix cannot be "reject everything".

  Exits 0 when every tool both rejects and still works, 1 otherwise.
#>
[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'

# The tools under test, and one valid argument list each. The valid list is
# deliberately minimal: the point is that the tool still functions.
#
# flip-ledger is invoked with -DryRun on a deliberately NON-EXISTENT id. A
# -DryRun writes nothing at all (no ledger change, no backup), and the id is
# absent so the tool exits at the "id not in ledger" check before it can reach
# any write. The probe therefore cannot mutate the real ledger even if the
# guard under test regresses.
$tools = @(
    @{ Name = 'flip-ledger';       Valid = @('-Id', 'B5-0000', '-NewStatus', 'OPEN', '-DryRun') },
    @{ Name = 'ledger-query';      Valid = @('-Status', 'OPEN') },
    @{ Name = 'lint-encoding';     Valid = @() },
    @{ Name = 'lint-ledger';       Valid = @() },
    @{ Name = 'snapshot-age-check';Valid = @('-MaxAgeHours', '100000') }
)

$toolDir = Join-Path (Split-Path -Parent $PSScriptRoot) 'tools'
$failures = @()

function Invoke-Tool {
    param([string]$Path, [string[]]$Arguments)
    # A rejected parameter makes the child write to stderr, and with
    # $ErrorActionPreference='Stop' in THIS script that native stderr becomes a
    # terminating error before $LASTEXITCODE can be read. So the child is run
    # with stderr redirected into stdout, and this scope relaxes the preference
    # for the call only.
    $old = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $out = & powershell -NoProfile -ExecutionPolicy Bypass -File $Path @Arguments 2>&1
        $code = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $old
    }
    # Whitespace-normalised: Out-String wraps to the console width and can
    # split a phrase mid-word, which made a literal substring match fail even
    # though the message was present.
    $flat = (($out | ForEach-Object { [string]$_ }) -join ' ') -replace '\s+', ' '
    return @{ Code = $code; Text = $flat }
}

foreach ($t in $tools) {
    $path = Join-Path $toolDir ("{0}.ps1" -f $t.Name)
    if (-not (Test-Path -LiteralPath $path)) {
        $failures += ("{0}: tool file missing at {1}" -f $t.Name, $path)
        continue
    }

    # --- NEGATIVE CASE: an unknown parameter must be rejected -------------
    $bogus = 'ThisParameterDoesNotExist'
    $r = Invoke-Tool -Path $path -Arguments @("-$bogus", 'x')
    $rejected = ($r.Text -match 'A parameter cannot be found')
    if ($r.Code -eq 0) {
        $failures += ("{0}: unknown parameter '{1}' was ABSORBED (exit 0). The tool must fail loudly." -f $t.Name, $bogus)
    }
    elseif (-not $rejected) {
        $failures += ("{0}: unknown parameter '{1}' gave exit {2} but the error does not name the parameter. Text: {3}" -f $t.Name, $bogus, $r.Code, $r.Text.Trim())
    }

    # --- POSITIVE CASE: the documented parameters must still work ---------
    $v = Invoke-Tool -Path $path -Arguments $t.Valid
    $crashed = ($v.Text -match 'A parameter cannot be found')
    if ($crashed) {
        $failures += ("{0}: valid invocation {1} was REJECTED. The guard is over-tight." -f $t.Name, ($t.Valid -join ' '))
    }
    if ($v.Text -match 'Cannot bind argument to parameter .Path. because it is an empty string') {
        $failures += ("{0}: valid invocation hit the empty-`$PSScriptRoot trap. A [CmdletBinding()] script has an EMPTY `$PSScriptRoot while param defaults are evaluated, so any default of the form (Join-Path (Split-Path -Parent `$PSScriptRoot) ...) must be resolved in the body instead." -f $t.Name)
    }
}

if ($failures.Count -gt 0) {
    Write-Output ("FAIL: {0} problem(s)" -f $failures.Count)
    foreach ($f in $failures) { Write-Output ("  - {0}" -f $f) }
    exit 1
}

Write-Output ("PASS: {0} tool(s) each reject an unknown parameter and still accept their documented ones" -f $tools.Count)
exit 0
