---
document:
  title: "B5-0044 - red-then-green evidence for the tooling guards"
  status: "Report. Observation, no authority (AGENTS.md section 3)."
provenance:
  author_llm: {name: "opencode (Space Bunny Free)", version: "space-bunny-free-2026-10-21-01"}
  created_date: "2026-10-21"
  last_modified_date: "2026-10-21"
  last_modified_by_llm: {name: "opencode (Space Bunny Free)", version: "space-bunny-free-2026-10-21-01"}
  assessor_llm: none
  no_self_assessment: true
---

# B5-0044 - red-then-green evidence for the tooling guards

Claim: `tooling,docs`. Agent id `opencode-space-bunny-free-01`.
Ledger row: `B5-0044` (OPEN -> DONE).

## The row's acceptance, and how each clause was met

> each of the five either declares CmdletBinding or explicitly enumerates and
> rejects unknown parameters; one negative-case probe per tool that fails
> without the change; a demonstration that -DryRun writes nothing; gate exits 0

| Clause | Met by | Evidence |
|---|---|---|
| five tools reject unknown parameters | `[CmdletBinding()]` added to all five | `test-param-guard.ps1` |
| one negative-case probe per tool | `test-param-guard.ps1` covers all five; `test-flip-ledger.ps1` covers flip-ledger's guards in depth | both probes |
| a negative-case probe that **fails without the change** | red run below: 5/5 fail against HEAD's tools, 11-case probe fails 6/11 | red evidence |
| `-DryRun` writes nothing | `flip-ledger.ps1` gained a real `-DryRun`; probe case 7 asserts no ledger change, no backup, no report | `test-flip-ledger.ps1` case 7 |
| gate exits 0 | `RUN_TESTS=1 sh compile.sh` -> **31/31 Java classes + 2 tooling probes, exit 0** | `.agent/REPORTS/evidence/GATE-B5-0044.txt` |

## What the five tools did before

Measured, not assumed. Each was invoked with `-ThisParameterDoesNotExist x`:

| Tool | Exit before | Rejected the unknown parameter? |
|---|---|---|
| `flip-ledger.ps1` | 1 | no |
| `ledger-query.ps1` | **0** | no |
| `lint-encoding.ps1` | 1 | no |
| `lint-ledger.ps1` | 0 | no |
| `snapshot-age-check.ps1` | 1 | no |

`ledger-query` was the worst: `powershell -File ledger-query.ps1 -Statuss OPEN`
exited **0** and printed a normal table. The typo was absorbed and the intended
filter never applied. A tool that exits 0 on a misspelled parameter is worse
than one that crashes, because the reader believes it.

Ten of the fifteen tools already declared `[CmdletBinding()]`. The five that
did not were the gap.

## The trap found while fixing it

Adding `[CmdletBinding()]` **broke** `flip-ledger` and `lint-ledger`.

`[CmdletBinding()]` turns a script into an advanced function, and in an
advanced function `$PSScriptRoot` is **empty while param defaults are
evaluated**. Both tools had a default of the form

    [string]$LedgerPath = (Join-Path (Split-Path -Parent $PSScriptRoot) 'TASK_LEDGER.md')

which then threw `Cannot bind argument to parameter 'Path' because it is an
empty string` before the body ran. Measured in isolation:

    without CmdletBinding -> A=[C:\...\X]
    with    CmdletBinding -> throws, Path is empty

The fix is to keep `[CmdletBinding()]` and resolve the path defaults in the
body, where `$PSScriptRoot` is populated. The ten already-correct tools do
**not** use `$PSScriptRoot` in a param block, so none of them carries this
latent bug. Verified: all fifteen tools parse clean after the change.

## Five defects found in `flip-ledger.ps1`

The row asked for a `-DryRun` demonstration. Building one required reading
what the tool actually did, and four further defects surfaced.

**1. The live-claim guard never fired.** The lookup was

    Get-ChildItem (Join-Path (Split-Path -Parent $PSScriptRoot) '.agent/CLAIMS') ...

`$PSScriptRoot` is `.agent/tools`, so its parent is `.agent`, and the path
became `.agent/.agent/CLAIMS` - a directory that does not exist.
`Get-ChildItem` on a missing path with `-ErrorAction SilentlyContinue` returns
nothing, so `$live` was always empty and the guard was dead code.

Proven in a sandbox with a live claim planted by `other-agent-99` on
`B5-0300`: the tool flipped that row to DONE, wrote a backup, and **exited 0**.
The tool's own synopsis claims it "Refuses DONE ... with live claim by another
agent". It did not.

**2. A refused flip still left a backup.** The backup was taken before the
refusal checks, so a refused flip still wrote into `RESCUE/`.

**3. The report check was CWD-relative.** `.agent/REPORTS/*$Id*.md` resolved
against the current directory rather than the repository, so a DONE flip was
refused spuriously whenever the caller was not sitting in the repo root.

**4. There was no `-DryRun` at all.** A caller who passed `-DryRun` got it
silently absorbed - the tool had no `[CmdletBinding()]` - and the flip happened
anyway. The acceptance case "`-DryRun` prints the diff and writes nothing"
could not pass, because the switch did not exist.

**5. The claim guard over-refused.** This one was found *by using the tool*.
Closing out this very row failed with

    refuse: live claim by another agent for B5-0044 (B5-0044.json)

even though `opencode-space-bunny-free-01` was the agent holding that claim.
The tool's contract - "refuses DONE ... with live claim by **another** agent"
- was not what the code did: it refused every live claim, including the
caller's own. The agent that holds a claim is the agent expected to close the
row out, and this tool is how it does that, so the old behaviour made the
close-out path unusable.

The fix adds `-AgentId` and exempts a claim whose `agent_id` matches it. This
is a deliberate widening of a guard that, per defect 1, could never fire at
all - so the widening is not a loosening of something that was working. An
unreadable claim file is treated as foreign, not as absent, because a claim
that cannot be read is a claim that cannot be reasoned about, and guessing
"mine" would be the unsafe direction.

`flip-ledger.ps1` now resolves its paths in the body, checks the claims
directory at `.agent/CLAIMS`, resolves the report directory against the
repository root, takes the backup only after every refusal check has passed,
exempts the caller's own claim, and supports a real `-DryRun` that prints the
diff and writes nothing at all.

## The probes

Two new files, both under `.agent/tools/`:

* `test-param-guard.ps1` - for each of the five tools: an unknown parameter
  must be rejected (non-zero exit, and the error must name the parameter),
  and the documented parameters must still work. The flip-ledger positive case
  uses `-DryRun` on a non-existent id (`B5-0000`), so the probe cannot mutate
  the real ledger even if the guard regresses.
* `test-flip-ledger.ps1` - eleven cases in a throwaway sandbox, covering the
  live-claim refusal, the `.RELEASED` marker, sibling ids, malformed ids, the
  missing-report refusal, "a refused flip leaves no backup and no change",
  `-DryRun` writing nothing, one-line-diff-with-CRLF-preserved, the
  unknown-parameter rejection, the caller's own claim being exempt, and
  another agent's claim still blocking. The real ledger is never touched.

## Red-then-green, measured

| Probe | Against the fixed tools | Against HEAD's (unfixed) tools |
|---|---|---|
| `test-param-guard.ps1` | **exit 0** - 5/5 | **exit 1** - 5/5 fail |
| `test-flip-ledger.ps1` | **exit 0** - 11/11 | **exit 1** - 6/11 fail (cases 1, 2, 6, 7, 9, 10) |

The red run is the point. A probe that has never been seen failing is not
evidence that it can fail.

## The probes are wired into the gate

A probe that nothing runs is a comment. `compile.sh`'s `RUN_TESTS=1` branch
now runs both tooling probes after the Java table, so the 31-class invariant
and the summary line still describe exactly the Java classes. The probes are
skipped (not failed) when no `powershell` is on PATH, because the gate must
still run on a machine that has only a JDK. When PowerShell is present and a
probe fails, **the gate exits non-zero**.

That was verified by reverting `flip-ledger.ps1` to HEAD and re-running the
whole gate:

    Summary: 31 passed, 0 failed (of 31 gate classes run)
    Tooling probes: 0 passed, 2 failed
    ERROR: 2 tooling probe(s) failed. Exit non-zero.
    GATE EXIT = 1

Evidence: `.agent/REPORTS/evidence/GATE-B5-0044.txt` (green) and
`.agent/REPORTS/evidence/GATE-B5-0044-RED.txt` (red).

## One more finding, in the probe rather than the tool

`Out-String` wraps console output to the host width, and it wrapped **in the
middle of** the phrase `A parameter cannot be found`. The message was present
and the substring match still failed, which cost real time to diagnose. Both
probes now collapse whitespace before matching, so the assertions do not
depend on console width. Recorded here because the next author of a
PowerShell probe will hit it.

## What was NOT changed

* The other four tools got `[CmdletBinding()]` and nothing else. Their
  `lint-encoding` exit 1 (20 known encoding hits) and `snapshot-age-check`
  exit 1 (no snapshot branch exists) are pre-existing and unchanged.
* `compile.bat` was not touched; its gate already delegates to `compile.sh`.
* The real `.agent/TASK_LEDGER.md` was not modified by any of this work
  (verified: 154 CRLF terminators before and after, `git diff` empty).
