---
document:
  title: "B5-0009 close-out — B0 snapshot job build only (script plus scheduler definition, not enabled)"
  status: "Report"
provenance:
  author_llm: {name: "Muse Spark", version: "muse-spark-1.3-contributor-free"}
  created_date: "2026-10-16"
  task: "B5-0009"
---

# B5-0009 close-out

Owner order (2026-10-16 session): "may a non-agent process commit the tree on
a timer? yes. Implement the B0 snapshot job: script plus Task Scheduler
definition that commits to a snapshot branch; do not enable it. You build it;
I own the scheduler." D3 snapshot half thereby APPROVED; task branches still
deferred. This task builds only. Nothing registered, nothing enabled.

## Acceptance (owner review checklist)

1. **Script exists, uncommitted:** `.agent/tools/new-snapshot.ps1`
   (default branch `auto-snapshot`). DryRun exit 0, measured.
2. **Scheduler definition exists, never registered:**
   `.agent/tools/snapshot-task.xml` (hourly TimeTrigger, InteractiveToken,
   least privilege). No `schtasks` / `Register-ScheduledTask` was run;
   `git branch` shows `main` only, no `auto-snapshot` ref created.
3. **Agent made no commit and no push:** verified via `git status`
   (new files untracked) and empty `git diff --cached`.
4. **`compile.bat` green** after the change (tooling-only diff, zero Java
   files touched): "Build successful".
5. **D3 recorded** in `docs/DECISIONS.md` (pending list + dated entry).

## Design (what the owner is being asked to enable)

* **Plumbing, not porcelain:** the job stages the working tree into a
  throwaway index (`GIT_INDEX_FILE` under `.git`, deleted afterwards) and
  commits with `write-tree` / `commit-tree` / `update-ref`. It never checks
  out branches, never writes a working-tree file (only the gitignored run
  log), never touches the real index, and moves only
  `refs/heads/auto-snapshot` via compare-and-swap. Safe to run mid-edit.
* **Order per run:** refuse mid-merge/rebase and locked runs; secret scan;
  stage; skip when the tree equals the snapshot tip; commit; push only with
  `-Push` (default off — no remote/auth assumed in scheduler context).
* **First run** parents the snapshot on current HEAD; later runs parent on
  the previous snapshot tip: a linear backup history, never merged back.
* **Secret scan** covers tracked plus untracked non-ignored files
  (`.gitignore` enforced via `ls-files --exclude-standard`, so ignored
  scratch/output/secrets-by-extension stay out of scan and commit):
  private-key headers, `AKIA` + 16, `ghp_`/`gho_` + 36, credential
  assignments with real-looking values. The one prose mention of the
  placeholder shape (B5-0001 report) is excluded by requiring the value to
  start alphanumeric and run 4+ chars. Hits refuse the commit and log file
  names only, never values.
* **Exit codes:** 0 committed / nothing-to-do / dry-run clean; 1
  blocked-refused; 2 environment. The scheduler alert reads the code, and
  freshness is watched with
  `snapshot-age-check.ps1 -Ref auto-snapshot` (exit 1 past 24 h).

## Verification (measured, not asserted)

* Parser: `new-snapshot.ps1` 0 errors, `snapshot-age-check.ps1` 0 errors;
  `snapshot-task.xml` well-formed (`Task`, action `powershell.exe`,
  interval `PT1H`, working directory the repo root). One fix landed here:
  the XML comment contained `--` (forbidden in XML comments); reworded.
* Live-tree DryRun exit 0: scan clean over 220 files (no self-match on the
  new files), staged tree built, "23 files would snapshot" — reconciled
  exactly against `git status --untracked-files=all` (23 lines), including
  the uncommitted B5-0002 work and B5-0003's in-flight engine edits. One fix
  landed here: git's autocrlf stderr warning threw under the script-wide
  `Stop` preference; `Invoke-Git` now judges git by exit code only.
* Post-DryRun forensics: no `auto-snapshot` branch, empty staged diff, lock
  and throwaway index removed, no stash.
* `snapshot-age-check.ps1`: default exit 0 FRESH (contract unchanged),
  `-MaxAgeHours 0` exit 1 STALE, `-Ref auto-snapshot` exit 2 UNREADABLE
  (branch absent — expected until the first live snapshot; then it reads 0).
* `compile.bat`: green.

## Changes (all uncommitted, for owner review)

* `.agent/tools/new-snapshot.ps1` — new (the job).
* `.agent/tools/snapshot-task.xml` — new (the definition, disabled by
  non-registration; enable command in its header comment).
* `.agent/tools/snapshot-age-check.ps1` — ` -Ref` parameter added
  (default `HEAD`); output names the ref. Otherwise byte-identical behaviour.
* `docs/DECISIONS.md` — D3 bullet updated + dated B5-0009 entry.
* `.agent/TASK_LEDGER.md` — this row to DONE.

## Notes for the owner (enable path)

1. Review the two new files, then run the script once by hand with `-DryRun`
   (log to a temp path to keep the tree clean), then once live; confirm with
   `git log` on `auto-snapshot`; then import the XML.
2. After the first live snapshot, point the freshness alert at it:
   `snapshot-age-check.ps1 -Ref auto-snapshot`.
3. Off-machine copy (proposal §7.2) is still open: either `-Push` on a
   schedule once remote/auth exists, or a separate copy step. Not built here.
4. Observed, out of scope, not changed: `docs/DECISIONS.md` records D1 as
   decided (2026-10-16 owner rulings section) while the pending list still
   marks D1 OPEN; AGENTS.md's pin wording was corrected by another writer
   mid-task (no conflict with this scope).

## Reusable lesson

* No pattern filed (tooling build, no lesson family; novelty check negative).
