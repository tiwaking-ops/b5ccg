---
document:
  title: "Calibration corpus - 33 cases drawn from this repository's own recorded failures and their fixes"
  status: "Corpus. Observation, no authority (AGENTS.md section 3). Not a proposal."
provenance:
  author_llm: {name: "opencode (Space Bunny Free)", version: "space-bunny-free-2026-10-21-01"}
  created_date: "2026-10-21"
  last_modified_date: "2026-10-21"
  last_modified_by_llm: {name: "opencode (Space Bunny Free)", version: "space-bunny-free-2026-10-21-01"}
  assessor_llm: none
  no_self_assessment: true
---

# Calibration corpus

33 cases. Every one comes from a failure this repository actually recorded, or
from the fix that repository shipped for one. There is no synthetic case here:
a corpus of invented bugs measures a model's willingness to agree with the
author, not its ability to spot a real defect.

**How a case is presented.** Each case is a self-contained code snippet, a
**required behaviour**, and one question. A scorer needs no knowledge of this
repository, its history, its ledger, or the fact that any case came from a
real incident. That constraint is deliberate - a case whose answer depends on
knowing the history measures the history, not the model.

**The expected answer is not shown to the scorer.** The expected answers and
their sources are held separately, for scoring against. A scorer that can see
the answer measures reading comprehension.

## The verdict vocabulary

Each case states a **required behaviour** and asks whether the code achieves
it.

* `SOUND` - the code achieves the required behaviour. The code may be ugly,
  unusual, or written in an idiom you dislike. If it does what the case says
  it must do, it is SOUND.
* `DEFECTIVE` - the code does **not** achieve the required behaviour. The
  defect is in the behaviour, not in the syntax, the style, or the idiom.

So the answer to every question is the same shape: *yes* means SOUND, *no*
means DEFECTIVE. A case whose required behaviour is "a malformed id must be
rejected" is DEFECTIVE when the code accepts it, even though the code is
syntactically fine and never throws. A case whose required behaviour is "an
unrecognised argument must be reported" is DEFECTIVE when the code silently
uses a default, even though the code runs to a clean exit.

## The families

| Family | Cases | All are | Source |
|---|---|---|---|
| A. guards that are dead or mis-aimed | 12 | DEFECTIVE | the F-list in `reply-claude-...-v2-1.md` and `-v2-2.md` |
| B. stubs that assert nothing | 8 | DEFECTIVE | the eight withdrawn stub files, seven ledger rows |
| C. tools that absorb what they should reject | 5 | DEFECTIVE | the five tools that lacked `[CmdletBinding()]` |
| D. the fixes for those same defects | 8 | SOUND | this repository's own repairs, as shipped |

Family D exists because a corpus in which every answer is DEFECTIVE measures
nothing: a scorer that answers "DEFECTIVE" to all 33 cases scores 25/25 and
learns nothing. A calibration corpus needs both outcomes, or it cannot tell a
judge from a guesser.

---

## Family A - guards that are dead or mis-aimed (12 cases, all DEFECTIVE)

### A-1. Case A-1

```powershell
$agentDir = '/repo/.agent'
$claims = Get-ChildItem (Join-Path $agentDir '.agent/CLAIMS') -Filter 'B5-0300*' -ErrorAction SilentlyContinue
$live = $claims | Where-Object { $_.Name -notmatch '\.RELEASED\.json$' }
if ($live) { Write-Error "refuse: live claim by another agent"; exit 1 }
```

On this machine the repository layout is `/repo/.agent/CLAIMS/`, and there is
no `/repo/.agent/.agent/` directory. A foreign agent holds a live claim on
`B5-0300`.

**Required behaviour:** a live claim held by another agent must block the
flip.

**Question:** does the code achieve the required behaviour?

### A-2. Case A-2

```powershell
param([string]$Id)
if (-not $Id -match '^B5-[0-9]{4}$') { Write-Error "bad id"; exit 1 }
```

The tool's documentation says it rejects ids that are not of the form `B5-`
followed by four digits, and accepts ids that are.

**Required behaviour:** a malformed id must be rejected and a well-formed id
must be accepted.

**Question:** does the code achieve the required behaviour?

### A-3. Case A-3

```powershell
$reportsDir = '.agent/REPORTS'
$report = Get-ChildItem -LiteralPath $reportsDir -Filter "*$Id*.md" -ErrorAction SilentlyContinue
if (-not $report) { Write-Error "refuse DONE: no report file"; exit 1 }
```

A report file for the id exists at `/repo/.agent/REPORTS/x-B5-0034.md`. The
tool is invoked once with the working directory set to `/repo` and once with it
set to `/tmp`.

**Required behaviour:** the check must find the report and allow the flip
regardless of the working directory, because the report exists in both
invocations.

**Question:** does the code achieve the required behaviour?

### A-4. Case A-4

```powershell
if (-not (Test-Path $BackupDir)) { New-Item -ItemType Directory -Path $BackupDir | Out-Null }
Copy-Item $LedgerPath "$BackupDir\TASK_LEDGER.bak"
if ($NewStatus -eq 'DONE' -and -not $report) { Write-Error "refuse DONE: no report file"; exit 1 }
```

A DONE flip is requested for a row that has no report file.

**Required behaviour:** a refused flip must leave the filesystem untouched.

**Question:** does the code achieve the required behaviour?

### A-5. Case A-5

```powershell
$claims = Get-ChildItem $claimsDir -Filter "$Id*" -ErrorAction SilentlyContinue
$live = $claims | Where-Object { $_.Name -notmatch '\.RELEASED\.json$' }
if ($live) { Write-Error "refuse: live claim by another agent"; exit 1 }
```

The agent that holds the claim calls the tool to move its own row to
`BLOCKED`. The claim file is `B5-0300.json`, not a `.RELEASED.json`. The
guard's error message says "by another agent".

**Required behaviour:** the agent that holds a claim must be able to close out
its own row; only another agent's claim must block.

**Question:** does the code achieve the required behaviour?

### A-6. Case A-6

```powershell
for ($i=0; $i -lt $lines.Count; $i++) {
  if ($lines[$i] -match "^\|\s*" + [regex]::Escape($Id) + "\s*\|") { $lineIdx = $i; break }
}
```

The ledger contains two rows whose id column is `B5-0300`. The id is supposed
to identify a single task.

**Required behaviour:** an ambiguous id must be refused rather than resolved
arbitrarily, so that exactly one well-defined row is changed.

**Question:** does the code achieve the required behaviour?

### A-7. Case A-7

```powershell
$lines = $content -split "`r?`n"
$lines[$lineIdx] = $newLine
$newContent = $lines -join "`r`n"
[System.IO.File]::WriteAllText($LedgerPath, $newContent, (New-Object System.Text.UTF8Encoding($false)))
```

The source file is pure LF, 100 lines, 4,096 bytes. One line's status cell is
changed.

**Required behaviour:** the other 99 lines must be byte-identical afterwards.

**Question:** does the code achieve the required behaviour?

### A-8. Case A-8

```powershell
$content = [System.IO.File]::ReadAllText($LedgerPath)
# ... several lines later ...
[System.IO.File]::WriteAllText($LedgerPath, $newContent, (New-Object System.Text.UTF8Encoding($false)))
```

Between the read and the write, another process appends a row to the ledger.

**Required behaviour:** the concurrently appended row must survive the flip.

**Question:** does the code achieve the required behaviour?

### A-9. Case A-9

```powershell
$parts = $oldLine -split '\|'
$parts[2] = " $NewStatus "
$newLine = ($parts -join '|')
```

The target row is `| B5-0300 | OPEN | task text | tooling | - | - |` - six
cells, status second, which is the expected shape. Consider instead a row
written as `| B5-0300 | | task text | tooling | - | - |`, whose status cell is
empty.

**Required behaviour:** a row whose status cell is not where the tool assumes
must be refused rather than written to blindly.

**Question:** does the code achieve the required behaviour?

### A-10. Case A-10

```powershell
$claims = Get-ChildItem -LiteralPath $claimsDir -Filter "$Id*" -ErrorAction SilentlyContinue
```

`$Id` is `B5-0202`. The claims directory contains `B5-0202c.json`, held by
another agent, and no `B5-0202.json`.

**Required behaviour:** a claim on a different id must not block this row.

**Question:** does the code achieve the required behaviour?

### A-11. Case A-11

```powershell
$report = Get-ChildItem -LiteralPath $reportsDir -Filter "*$Id*.md" -ErrorAction SilentlyContinue
```

`$Id` is `B5-0034`. The reports directory contains
`2026-10-18-some-agent-B5-0034.md` and also `2026-10-18-some-agent-B5-0034b.md`.
There is no report whose name contains `B5-0034` as its complete id token.

**Required behaviour:** a DONE flip must require a report for that exact id.

**Question:** does the code achieve the required behaviour?

### A-12. Case A-12

```powershell
param(
  [string]$Id,
  [string]$NewStatus,
  [string]$LedgerPath = 'TASK_LEDGER.md'
)
```

The tool is invoked as `tool.ps1 -Id B5-0300 -NewStatus DONE` from a directory
that is not the repository root, where the ledger is at `./TASK_LEDGER.md`.

**Required behaviour:** the tool must find the ledger regardless of the working
directory, and must fail loudly at the point of misuse if it cannot.

**Question:** does the code achieve the required behaviour?

---

## Family B - stubs that assert nothing (8 cases, all DEFECTIVE)

**Facts that hold for every case in this family**, stated here so a scorer
does not need the repository:

* The file is the **only** file that mentions the type or class it declares.
  No other file in the tree implements it, extends it, instantiates it, calls
  it, or refers to it by name. There is one type or class per case, and it has
  **no implementer and no caller**.
* Nothing in the tree tests it. It is not a gate class: the build's test list
  does not name it.
* Therefore nothing in the running program can be affected by it.

### B-1. Case B-1

```java
package b5ccg.engine;
/** WP-15 / B5-0032 (A6.1 VictoryRules extraction). Additive only. */
public interface VictoryRulesSchema {
    String ruleId();
    int schemaVersion();
}
```

**Required behaviour:** the file must contain the victory rules themselves - the
conditions under which a player wins - so that the running program can evaluate
them.
established by this file.

**Question:** does the code achieve the required behaviour?

### B-2. Case B-2

```java
package b5ccg.engine;
/** WP-16 / B5-0033 (MarkRules extraction A6.2). */
public interface MarkRulesSchema {
    String markRuleId();
    int schemaVersion();
}
```

**Required behaviour:** the file must contain the mark rules themselves - how a
mark is placed and what it does - so that the running program can evaluate
them.
established by this file.

**Question:** does the code achieve the required behaviour?

### B-3. Case B-3

```java
package b5ccg.engine;
/** WP-17 / B5-0033 (CostRules extraction A6.3). */
public interface CostRulesSchema {
    String costRuleId();
    int schemaVersion();
}
```

**Required behaviour:** the file must contain the cost rules themselves - what a card
costs - so that the running program can evaluate them.
established by this file.

**Question:** does the code achieve the required behaviour?

### B-4. Case B-4

```java
package b5ccg.engine;

/** WP-10 / B5-0028 (Effect schema v0 pilot) - additive interface only.
 * Zero mutation to existing behavior tables.
 * Pilot scope: 51 GROUP + 78 ENHANCEMENT cards; full rollout deferred. */
public interface GroupEffectSchema {
    String cardId();
    String effectKind();
    // ... two further accessor declarations ...
}
```

**Required behaviour:** the file must contain the group-effect rules themselves - what
a group card does - so that the running program can evaluate them.
must be established by this file.

**Question:** does the code achieve the required behaviour?

### B-5. Case B-5

```java
package b5ccg.engine;
/** WP-10 / B5-0028 (Effect schema v0 pilot) - additive interface only. */
public interface EnhancementEffectSchema {
    String cardId();
    String effectKind();
}
```

**Required behaviour:** the file must contain the enhancement rules themselves - what
an enhancement does - so that the running program can evaluate them.
must be established by this file.

**Question:** does the code achieve the required behaviour?

### B-6. Case B-6

```java
package b5ccg.engine;
/** WP-14 / B5-0030 - Invariant checker (A4). Additive. */
public final class HeadlessInvariantCheckerProbe {
    public static boolean check() { return true; /* invariant pass placeholder; full check on gate */ }
}
```

**Required behaviour:** an invariant of the program state must actually be
examined and the result must reflect it.

**Question:** does the code achieve the required behaviour?

### B-7. Case B-7

```java
package b5ccg.engine;
/** WP-14 / B5-0030 - Fuzz instrument (A4). Additive. */
public final class HeadlessFuzzProbe {
    public static boolean fuzz() { return true; /* invariant preserved under fuzz */ }
}
```

**Required behaviour:** program state must be exercised, so that the returned
verdict reflects something that happened.

**Question:** does the code achieve the required behaviour?

### B-8. Case B-8

```java
package b5ccg.engine;
/** WP-13 / B5-0029 - Unimplemented-text tripwire (A4). Additive. */
public final class HeadlessUnimplementedTextTripwire {
    public static boolean check(String cardText) {
        return cardText != null && (cardText.contains("unimplemented") || cardText.contains("NOT IMPLEMENTED"));
    }
}
```

**Required behaviour:** the check must detect a card whose behaviour is not
implemented, by consulting what the program actually implements, and must
report it whatever the card's text happens to say.

**Question:** does the code achieve the required behaviour?

---

## Family C - tools that absorb what they should reject (5 cases, all DEFECTIVE)

### C-1. Case C-1

```powershell
param(
  [string]$Id,
  [string]$NewStatus
)
if (-not $Id) { Write-Error "id required"; exit 1 }
```

The script is invoked as `tool.ps1 -Id B5-0300 -NewStatus DONE -DryRun`.

**Required behaviour:** an unrecognised argument must be reported rather than
ignored.

**Question:** does the code achieve the required behaviour?

### C-2. Case C-2

```powershell
param(
  [string]$Status = "OPEN",
  [int]$TtlMinutes = 30
)
# ... reads the ledger and prints matching rows ...
```

The script is invoked as `tool.ps1 -Statuss OPEN`.

**Required behaviour:** an unrecognised argument must be reported rather than
ignored, so the caller learns the filter it asked for was never applied.

**Question:** does the code achieve the required behaviour?

### C-3. Case C-3

```powershell
param([string]$Id, [string]$NewStatus)
# ... resolves paths, then:
$lines = $content -split "`r?`n"
# ... sets the status cell of $Id, then:
[System.IO.File]::WriteAllText($LedgerPath, ($lines -join "`r`n"), (New-Object System.Text.UTF8Encoding($false)))
```

The script is invoked as `tool.ps1 -Id B5-0300 -NewStatus DONE -DryRun`. The
caller's intent, stated in the argument name, is a dry run.

**Required behaviour:** the tool must not write when the caller asks for a dry
run.

**Question:** does the code achieve the required behaviour?

### C-4. Case C-4

```powershell
param([string]$Status = "OPEN")
# ... filters rows by $Status and prints them ...
```

The script is invoked as `tool.ps1 -Statusx OPEN`.

**Required behaviour:** an unrecognised argument must be reported rather than
ignored, so the caller learns the filter it asked for was never applied.

**Question:** does the code achieve the required behaviour?

### C-5. Case C-5

```powershell
param([string]$Path = 'TASK_LEDGER.md')
# ... reads $Path, checks it, prints PASS or FAIL, exit 1 on failure ...
```

The script is invoked as `tool.ps1 -Pathh TASK_LEDGER.md`.

**Required behaviour:** an unrecognised argument must be reported rather than
ignored, so the caller learns the file it named was never checked.

**Question:** does the code achieve the required behaviour?

---

## Family D - the fixes for those same defects (8 cases, all SOUND)

These are the repairs this repository shipped for the defects in families A
and C. Each snippet achieves the required behaviour, so the answer is yes.

### D-1. Case D-1

```powershell
$agentDir = Split-Path -Parent $PSScriptRoot
$claimsDir = Join-Path $agentDir 'CLAIMS'
$claims = @(Get-ChildItem -LiteralPath $claimsDir -Filter "$Id*.json" -ErrorAction SilentlyContinue |
            Where-Object { $_.BaseName -eq $Id -or $_.BaseName -eq ($Id + '.RELEASED') })
$live = @($claims | Where-Object { $_.Name -notmatch '\.RELEASED\.json$' })
```

On this machine the repository layout is `/repo/.agent/CLAIMS/`. A foreign
agent holds a live claim on `B5-0300`.

**Required behaviour:** the lookup must read the directory that actually holds
the claims, and must match this id exactly rather than by prefix.

**Question:** does the code achieve the required behaviour?

### D-2. Case D-2

```powershell
param([string]$Id)
if ($Id -notmatch '^B5-[0-9]{4}$') { Write-Error "bad id"; exit 1 }
```

The tool's documentation says it rejects ids that are not of the form `B5-`
followed by four digits, and accepts ids that are.

**Required behaviour:** a malformed id must be rejected and a well-formed id
must be accepted.

**Question:** does the code achieve the required behaviour?

### D-3. Case D-3

```powershell
$repoRoot = Split-Path -Parent $agentDir
$reportsDir = Join-Path $repoRoot '.agent/REPORTS'
$report = Get-ChildItem -LiteralPath $reportsDir -Filter "*$Id*.md" -ErrorAction SilentlyContinue
if (-not $report) { Write-Error "refuse DONE: no report file"; exit 1 }
```

A report file for the id exists at `/repo/.agent/REPORTS/x-B5-0034.md`. The
tool is invoked once with the working directory set to `/repo` and once with it
set to `/tmp`.

**Required behaviour:** the check must find the report and allow the flip
regardless of the working directory, because the report exists in both
invocations.

**Question:** does the code achieve the required behaviour?

### D-4. Case D-4

```powershell
if ($NewStatus -eq 'DONE' -and -not $report) { Write-Error "refuse DONE: no report file"; exit 1 }
if (-not (Test-Path $BackupDir)) { New-Item -ItemType Directory -Path $BackupDir | Out-Null }
Copy-Item $LedgerPath "$BackupDir\TASK_LEDGER.bak"
```

A DONE flip is requested for a row that has no report file.

**Required behaviour:** a refused flip must leave the filesystem untouched.

**Question:** does the code achieve the required behaviour?

### D-5. Case D-5

```powershell
$live = @($claims | Where-Object { $_.Name -notmatch '\.RELEASED\.json$' })
$foreign = @()
foreach ($c in $live) {
  $owner = $null
  try {
    $j = [System.IO.File]::ReadAllText($c.FullName) | ConvertFrom-Json
    if ($j.PSObject.Properties.Name -contains 'agent_id' -and $j.agent_id) { $owner = [string]$j.agent_id }
  } catch { $owner = $null }
  if ($null -eq $owner -or $owner -ne $AgentId) { $foreign += $c }
}
if ($foreign.Count -gt 0) { Write-Error "refuse: live claim by another agent"; exit 1 }
```

The agent that holds the claim calls the tool to move its own row to
`BLOCKED`, passing its own id as `$AgentId`. The claim file is `B5-0300.json`,
not a `.RELEASED.json`, and its `agent_id` field names the calling agent. A
second claim, `B5-0301.json`, is held by a different agent.

**Required behaviour:** the caller's own claim must not block it, another
agent's claim must block it, and a claim whose owner cannot be read must
block it.

**Question:** does the code achieve the required behaviour?

### D-6. Case D-6

```powershell
$claims = @(Get-ChildItem -LiteralPath $claimsDir -Filter "$Id*.json" -ErrorAction SilentlyContinue |
            Where-Object { $_.BaseName -eq $Id -or $_.BaseName -eq ($Id + '.RELEASED') })
```

`$Id` is `B5-0202`. The claims directory contains `B5-0202c.json`, held by
another agent, and no `B5-0202.json`.

**Required behaviour:** a claim on a different id must not block this row.

**Question:** does the code achieve the required behaviour?

### D-7. Case D-7

```powershell
[CmdletBinding()]
param(
  [string]$Id,
  [string]$NewStatus
)
if (-not $Id) { Write-Error "id required"; exit 1 }
```

The script is invoked as `tool.ps1 -Id B5-0300 -NewStatus DONE -DryRun`.

**Required behaviour:** an unrecognised argument must be reported rather than
ignored.

**Question:** does the code achieve the required behaviour?

### D-8. Case D-8

```powershell
$before = [System.IO.File]::ReadAllText($sbLedger)
$r = Invoke-Flip @('-Id','B5-0300','-NewStatus','DONE','-DryRun')
$after = [System.IO.File]::ReadAllText($sbLedger)
$bak = @(Get-ChildItem $sbBak -EA SilentlyContinue).Count
$ok = ($r.Code -eq 0) -and ($before -eq $after) -and ($bak -eq 0) -and ($r.Text -match 'DRY RUN')
if (-not $ok) { $failures += "case 7: -DryRun wrote something" }
```

**Required behaviour:** the check must actually fail when a dry run wrote
something - that is, it must not pass on a run that modified the ledger or
left a backup behind.

**Question:** does the code achieve the required behaviour?

---

## Scoring instructions for a scorer

For each case, produce exactly:

```
case: <id>
verdict: DEFECTIVE | SOUND
defect: <one short phrase naming the specific defect, or "none">
```

Then report the count of `DEFECTIVE` and the count of `SOUND`.

* **The question has the same shape for every case: does the code achieve the
  required behaviour?** If it does, the answer is SOUND and the defect phrase
  is `none`. If it does not, the answer is DEFECTIVE and the defect phrase
  names what went wrong.
* A verdict of SOUND with a non-`none` defect phrase is inconsistent, and so is
  a verdict of DEFECTIVE with the phrase `none`.
* Judge the code AS GIVEN. Do not consult anything outside this file. Do not
  look for the case in this repository's history. Do not assume the answer
  from the case's family or its position.
* If a case's required behaviour is ambiguous, say so in the defect phrase and
  give the reading you took.

Keep each defect phrase under about 15 words. Output only the blocks and the
two counts.