# HANDOFF — b5ccg (2026-10-16, after B5-0001)

## Where things stand

* New repo `C:\temp\projects\b5ccg` forked from `Babylon5CCGGameDesign`
  (hiatus, untouched). 8 commits. Working tree clean. Build green.
* Workflow: lean B-reset. Ledger has 8 rows: **B5-0001 DONE**, B5-0002…B5-0008
  OPEN with acceptance lines. Queue census verified offering all OPEN rows.
* Agent identity used this session: `opencode (muse-spark-1.3-free) 01`
  (heartbeat idle, no live claims).

## Next task: B5-0002 (WP-01/A9)

Pin rulebook, rule-set profile stub. Acceptance: hash recorded, 689-vs-1236
reconciled in writing, profile recorded in replays. Pointers: rulebook
`BABYLON5_CCG_RULEBOOK.md` (SHA-256 `1c99c44b…5886`, 689 CRLF lines =
Claude's 1236 LF lines, same bytes); Claude v1 §A9 for the three first
interpretations to record (D5 Deluxe-governs, League 2nd ambassador in hand,
duplicate-setup sweep as engine decision).

## Open issues for the owner

1. **Branch name**: genesis commit on `master`, rest on `main` (host config).
   Reconcile before first push. Never pushed anywhere yet.
2. **D3**: no non-agent snapshot job exists; this session committed directly
   under owner instruction. Fleet no-commit rule stands for agent sessions.
3. **D1, D5** still open (see `docs/DECISIONS.md`).

## Tool quirks learned (don't re-learn)

* `cmd /c` does not inherit the tool's workdir reliably and mangles spaced
  paths — invoke batch files by full quoted path (`cmd /c """$p"""`).
* Ledger IDs MUST match `B5-[0-9]{4}` or queue/claim/census tools ignore the
  row (WP-numbers failed silently once; fixed, ledger uses B5-0001…).
* `run-dup-census.ps1` needs sibling `dup-census.ps1` (copied over).
* `compile.bat` response file: quoted forward-slash absolute paths (javac
  @-files split on spaces unquoted, eat backslashes quoted).
* Drill clones test committed HEAD only — commit before drilling.
