---
document:
  title: "SHELL.knowledge.md — which shell runs here, and how to write a command that works in it"
  status: "Advisory knowledge record (auto-loaded by clients that discover *.knowledge.md; AGENTS.md and .agent/00_BOOT.md win on conflict)"
provenance:
  author_llm: {name: "opencode (big-pickle-free) bp5", version: "big-pickle-free"}
  assessor_llm:
    - {name: "opencode (mimo-v2.6-flash-free) 1", version: "mimo-v2.6-flash-free", passes: 1, last_pass: "2026-10-02", note: "edit: added the Python-invocation section (py launcher only; python and python3 absent from PATH in both shells), human-approved 2026-10-02 (B5-1991)"}
    - {name: "Hermes (stealth-space-bunny-alpha) 1733", version: "stealth-space-bunny-alpha", passes: 1, last_pass: "2026-10-02", note: "edit: recorded the B5-1733 detector that enforces the rule in this file, and that this file's own line 66 is the exempted counter-example it depends on (B5-1733)"}
  last_modified_by_llm: {name: "Hermes (stealth-space-bunny-alpha) 1733", version: "stealth-space-bunny-alpha"}
  created_date: "2026-10-01"
  last_modified_date: "2026-10-02"
---

# SHELL.knowledge.md

**Read this if a command you copied out of this repo failed with something like
`Select-String: command not found`, `wc: command not found`, or
`/usr/bin/bash: line 1:`.** That is not your mistake and not a repo defect. It is
a shell mismatch, and it has a fixed shape. This file is the generalisation of
`QWEN.md`, which pins the same thing for qwen-code alone.

## The one fact

**The repo's toolset is PowerShell. Your shell may not be.**

Every instrument under `.agent/tools/` is PowerShell (`.ps1`) or Python (`.py`).
There is no `.sh` anywhere under `.agent/`.

The count is deliberately omitted, because it is not the invariant. This line
originally stated nine `.ps1` plus one `.py`, and was already wrong when it
shipped: the directory held **12 `.ps1` and 2 `.py`** at the first measurement
(2026-10-01, B5-1731). A knowledge record that pins an inventory total is stale
the moment a sibling agent ships a tool, so the thing to rely on is the
**extension set**, not a number, and the authority for the total is
`Get-ChildItem .agent/tools` - never a sentence.

The coordination docs used to advertise `bash .agent/run-queue.sh -DryRun` as a
"bash equivalent"; that file has never existed in any commit and is not planned.
Do not look for it.

## Why your shell surprises you

It is set by **your client**, not by this repo and not by `AGENTS.md`. Measured
on this host 2026-10-01:

| Client | Shell you get |
|---|---|
| `freebuff` (the queue runner's default, `run-queue.ps1:94`) | **Git Bash**, always on Windows |
| `qwen-code` | `cmd.exe /c` — see `QWEN.md` |
| `opencode` on this host | PowerShell 5.1 |

`freebuff` resolves its shell by looking for `CODEBUFF_GIT_BASH_PATH`, then
`%ProgramFiles%\Git\bin\bash.exe`, then `bash.exe` on `PATH`. It has
`cmd.exe /c` and `powershell.exe` paths in its code and uses neither on Windows.
So if you are a FreeBuff session: **assume every command you run is bash**, and
never assume a `.ps1` cmdlet exists.

## The rule that prevents the failure

**Never paste a PowerShell *cmdlet* into your shell. Paste the command that
starts `powershell`.**

Compare:

```
Select-String -Path .agent/TASK_LEDGER.md -Pattern '^\|\s*B5-1445\s*\|'   # FAILS in bash
powershell -NoProfile -ExecutionPolicy Bypass -File .agent/tools/ledger-query.ps1 -Status OPEN   # works everywhere
```

The first form is PowerShell syntax with no `powershell` in front of it, so it
is delivered to bash and bash answers `Select-String: command not found`. The
second form names the interpreter explicitly and hands it a **file**, so it runs
unchanged from PowerShell, Git Bash, or `cmd.exe`.

That first line is a deliberate counter-example, and it is the one the standing
detector exempts, because it carries the `# FAILS in bash` annotation that says
out loud what the detector would otherwise have to say for it. If you ever
change that line, keep the annotation:

```
powershell -NoProfile -ExecutionPolicy Bypass -File .agent/tools/detect-bare-powershell-cmdlet.ps1
```

Exit `0` clean, `1` a bare cmdlet in instruction position, `2` unreadable root.
The rule in this section stopped being advice and became a gate on 2026-10-02
(B5-1733). Run `-SelfTest` to confirm it can still fail.

This is the B5-0777 class. There, a bash regex embedded in a `.ps1` failed to
*parse*, so a governance gate had **no working implementation** and every run
read as a pass. Here the string is not even syntactically wrong for its shell —
it is simply the wrong language, which is quieter.

## The commands to use

Each is shell-agnostic as written. Run them from whatever shell you have.

```
powershell -NoProfile -ExecutionPolicy Bypass -File .agent/run-queue.ps1 -DryRun
powershell -NoProfile -ExecutionPolicy Bypass -File .agent/tools/run-dup-census.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File .agent/tools/ledger-query.ps1 -Status OPEN
powershell -NoProfile -ExecutionPolicy Bypass -File .agent/tools/validate-heartbeats.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File .agent/tools/census-crosscheck.ps1
```

Two things that are **not** shell-agnostic, because they are bare cmdlets and
must be typed into PowerShell:

* `.agent/00_BOOT.md` step 3 requires the exact text `powershell -NoProfile
  -ExecutionPolicy Bypass -File .agent/tools/validate-heartbeats.ps1` in your
  heartbeat `notes` field. If you cannot run PowerShell, say so in `notes` —
  do not paraphrase it.
* The B5-1462 claim tool `.agent/tools/new-claim.ps1` is the only sanctioned
  claim path because **the tool** stamps `started_utc` from `UtcNow`, never your
  prose. Call it with an explicit `-Scope` array; a bare comma-joined
  `-Scope a,b` will bind wrongly.

## If you are in bash and need something PowerShell-only

Two escape hatches, both verified on this tree:

1. **Wrap it.** `powershell -NoProfile -Command '<cmdlet expression>'`. Mind the
   quoting: an outer bash layer eats `$_`, so pass `$` doubled or use a `.ps1`
   file instead.
2. **Git Bash by full path.** A bare `bash` on this host is a broken WSL relay
   and exits 1 with `execvpe(/bin/bash) failed` — that is WSL-not-installed, not
   a syntax verdict. Real Git Bash is `"C:/Program Files/Git/bin/bash.exe"`.

## What is bash-native here, in case you prefer it

`rg` (the `freebuff` CLI ships one at `~/.config/manicode/rg.exe`), `git`, and
`node`. `b5ccg/compile.sh` exists and is the genuine bash build gate.

**Do not** use `ls`, `cat`, `head`, `tail`, `wc`, `sed`, `awk`, or `find -exec`
to inspect coordination state — several do not exist under Git Bash, and the
tools above print richer verdicts than any of them. For file reading, use your
client's read tool.

## Python: run it with `py` — `python` and `python3` do not exist here

Measured 2026-10-02 (B5-1991) in **both** shells this repo's agents actually
get — PowerShell 5.1 and the Git Bash `freebuff` uses:

| Invocation | Result |
|---|---|
| `python --version` | not recognised / not on `PATH` |
| `python3 --version` | not recognised / not on `PATH` |
| `py --version` | `Python 3.14.7` (resolves to `/c/Windows/py` in Git Bash) |

The runnable form for any `.py` under `.agent/` is therefore:

```
py .agent/tools/census-b50960.py
PYTHONIOENCODING=utf-8 py .agent/tools/census-b50960.py
```

`PYTHONIOENCODING=utf-8` is **load-bearing** wherever the script prints source
lines: tracked comments carry box-drawing glyphs and the Windows console
defaults to cp1252, which raises `UnicodeEncodeError` (hit and recorded during
the B5-1012 census re-run). `AGENTS.md` section 2a already documents the `py`
form for the census script — this section is the same fact stated for every
other `.py` tool in the repo. If an instruction tells you to run
`python3 script.py`, that command cannot execute on this host: run
`py script.py` instead and note the substitution in your report.

## If you maintain this file

An inventory stated here — a tool count, a tool list, the client/shell table —
is a **measurement with a date**, not a memory. Measure it in the same pass that
writes it: `Get-ChildItem .agent/tools` for the toolset, a fresh client run if
the shell table changes. The count this file first carried was written from a
prior turn's tally and was already stale on arrival (B5-1731); the fix is the
discipline above, not a corrected number that will decay the same way. When a
stated inventory is superseded, correct it in place with the new date — do not
append a second number, so the record keeps one current truth plus its history.

## Reading order if you are new

`.agent/00_BOOT.md` (governance-adjacent boot procedure) →
`.agent/AGENT_LOOP.md` (the loop) → `AGENTS.md` (what wins on conflict).
This file only tells you which shell you are holding.