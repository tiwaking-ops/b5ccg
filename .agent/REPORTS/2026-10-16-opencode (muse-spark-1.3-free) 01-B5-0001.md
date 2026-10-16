---
document:
  title: "B5-0001 close-out — snapshot, secret scan, restore drill (WP-00/B0)"
  status: "Report"
provenance:
  author_llm: {name: "Muse Spark", version: "muse-spark-1.3-contributor-free"}
  created_date: "2026-10-16"
  task: "B5-0001"
---

# B5-0001 close-out

## Acceptance

1. **Untracked `.java` = 0.** 173 on disk under `b5ccg/src`, 173 tracked.
   Only untracked paths are my own claim + heartbeat (expected mid-task).
2. **Secret scan clean.** `git grep` over tracked files for private-key
   headers, `AKIA`/`ghp_` tokens, and `password = "..."` assignments: zero
   hits (scripts scanned separately for key material: zero hits).
3. **Restore drill green.** Fresh `git clone` into
   `...\Temp\opencode\b5ccg-restore-drill` (a path *with* a space, unlike the
   repo itself), `compile.bat` run from the clone: `Build successful`,
   `out/b5ccg-build-ok.txt` present.
4. **Stale-snapshot alert exists.** `.agent/tools/snapshot-age-check.ps1`:
   exit 0 fresh (measured HEAD age 0.0 h), exit 1 stale (forced with
   `-MaxAgeHours 0`), exit 2 git unreadable. Exit code is the contract.

## Findings (both fixed in this task, tooling scope)

* **F1 — spaced-path build break.** `compile.bat` wrote unquoted full paths
  to the javac response file: `javac: invalid flag: C:\Users\Tiwa`. Quoting
  exposed the second defect: javac @-files treat `\` as an escape, so
  `"C:\Users\..."` became `C:Users...` (`file not found`). Fix: quote +
  forward-slash each entry (`"C:/.../Main.java"`). Two commits.
* **F2 — drill clones committed state, not the working tree.** First two
  drill runs tested the pre-fix snapshot because the fix was uncommitted.
  Decisions about "the snapshot builds" must be read off a committed HEAD.

## Changes

* `b5ccg/compile.bat` — quoted, slashed response-file entries (+9/-4).
* `.agent/tools/snapshot-age-check.ps1` — new (see contract above).
* Commits `0b25e44`, `fa89b5a` made under direct owner instruction (this
  session owns genesis commits; fleet no-commit rule otherwise stands —
  no snapshot job exists yet, D3 open).

## Reusable lesson

Filed under `.agent/PATTERNS/opencode (muse-spark-1.3-free) 01/`: javac
@-file backslash-escape rule; drill-from-HEAD discipline.
