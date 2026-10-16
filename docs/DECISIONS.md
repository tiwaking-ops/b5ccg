# DECISIONS.md — b5ccg

## 2026-10-16 — Fork genesis (owner-directed)

* Old repo `Babylon5CCGGameDesign` placed in hiatus, left untouched. New repo
  `b5ccg` forked as full snapshot: `b5ccg/src` (173 .java), `b5ccg/resources`
  (cards + decks), `b5ccg/compile.bat|sh`, `run.bat|sh`, root rulebook, Claude
  proposal `docs/proposals/claude-b5ccggamedesign-system-improvements-v1.md`.
* Deliberately NOT carried over: TASK_LEDGER history (3.0 MB), DECISIONS
  history (1.55 MB), REPORTS/PATTERNS/investigations (~5,600 docs), root
  scratch, React/Vite stack, out_*/tmp_* scratch. Ledger restarts at WP-00
  (proposal §13 numbering), not B5-4250+.
* Workflow: Claude lean B-reset adopted (B1/B2). Boot + AGENTS.md rewritten
  short; pattern-per-family with novelty check; ledger rotation at ~14 days.
* Toolchain: D2 approved in principle — staged move to JDK 25 LTS (WP-05,
  WP-06). Build stays JDK 8 `-source 6` until hops land. Census kept for one
  sentinel cycle, then retired.
* Rulebook pin: SHA-256
  `1c99c44b7b64c8927f1e952a22a49d5886788da2d08e211092b5558e154716d0`,
  689 CRLF lines on disk (= Claude's 1,236 LF lines, same bytes). Canonical,
  unedited.

## 2026-10-16 — B5-0001 DONE (WP-00/B0)

* Untracked `.java` = 0 (173/173); secret scan clean; restore drill green
  from a spaced-path clone; `snapshot-age-check.ps1` alert shipped.
* Build fix landed: `compile.bat` response file now emits quoted
  forward-slash paths (javac @-files split on spaces unquoted and eat
  backslashes quoted — two measured failures, commits `0b25e44`,
  `fa89b5a`). In-place build re-verified green after the change.
* Branch note: genesis commit landed on `master`, later commits on `main`
  (host default-branch config); reconcile before first push.

## Pending owner decisions (Claude §1.5)

* D1 product intent (gates A7/F1–F5) — OPEN.
* D2 JDK 25 + no rewrite — APPROVED IN PRINCIPLE at fork.
* D3 non-agent snapshot job; task branches deferred — OPEN.
* D4 test/tooling dependencies in principle — APPROVED IN PRINCIPLE, not now.
* D5 Deluxe text governs on printing diffs — PROPOSED (record as
  interpretation when WP-03 lands it).
