---
document:
  title: "B5-0002 close-out — rulebook pin, rule-set profile stub (WP-01/A9)"
  status: "Report"
provenance:
  author_llm: {name: "Muse Spark", version: "muse-spark-1.3-contributor-free"}
  created_date: "2026-10-16"
  task: "B5-0002"
---

# B5-0002 close-out

## Acceptance

1. **Hash recorded.** `docs/RULEBOOK_PIN.md`: SHA-256
   `1c99c44b7b64c8927f1e952a22a49d5886788da2d08e211092b5558e154716d0`,
   134,586 working-tree bytes (BOM included), enforced by new gate
   `b5ccg.engine.HeadlessRulebookPinProbe` — 21/21 PASS.
2. **689-vs-1236 reconciled in writing.** Same bytes, different
   denominators: 1,236 = total LF lines, 689 = non-blank lines (547
   blank). The pin note also corrects the fork-time "CRLF count" label
   (the file holds 15 CRLF lines, not 689) and records the git-blob
   divergence (134,571 pure-LF bytes under `core.autocrlf`; pin is on
   working-tree bytes). The probe re-derives both counts independently
   in Java — reconciliation is asserted, not just written.
3. **Profile recorded in replays.** New `b5ccg.engine.RuleSetProfile`
   (immutable stub, standard-game defaults, `printingGoverns=DELUXE`
   per D5). `ReplayRecorder` header schema 1 → 2 carries `profile`;
   v1 loads as defaults; unknown printing fails loudly (all three paths
   exercised, including throwaway negative tests). `B51975ReplayProbe`
   +5 profile checks — 25/25 PASS.

## Changes (engine scope only)

* `b5ccg/src/b5ccg/engine/RuleSetProfile.java` — new (~150 lines).
* `b5ccg/src/b5ccg/engine/HeadlessRulebookPinProbe.java` — new gate.
* `b5ccg/src/b5ccg/engine/ReplayRecorder.java` — profile field,
  3-arg constructor, header `profile`, schema 2 with v1 compat (+49/-6).
* `b5ccg/src/b5ccg/engine/B51975ReplayProbe.java` — 5 profile checks.
* `b5ccg/compile.sh` — pin probe wired into `RUN_TESTS=1`
  (compile.bat has no test branch by decision).
* `docs/RULEBOOK_PIN.md` — new provenance record.
* `docs/DECISIONS.md` — B5-0002 entry; D5 PROPOSED → RECORDED
  (principle; per-title application stays WP-03/B5-0004).

## Interpretations recorded (rulebook untouched)

D5 Deluxe-governs (RB L1068); League 2nd ambassador in hand (RB
L878/L890/L912-913; binds future work — `NON_ALIGNED` setup currently
returns null); duplicate-setup sweep = engine decision (B5-2412/B5-4242;
no rulebook paragraph requires it, L1188-1190 covers replacement).

## Verification

* `compile.bat` green (only the pre-existing boot-classpath warning).
* Pin probe 21/21; replay probe 25/25; v1-compat load → defaults;
  bogus-printing load → `IOException` (throwaway runners, removed).
* Full `RUN_TESTS=1`: 4 conformance failures (AES/AIJ/MINES) reproduce
  identically on the pristine tree (changes stashed, rebuilt,
  re-measured) — pre-existing, unrelated, left for their owners.

## Notes for next session

* Measured mojibake is 3 lines (1228/1230/1236), not Claude's estimated
  5 — probe encodes 3 with the discrepancy noted.
* Card JSON deliberately size-unpinned (grows under A1b).
* CRLF deliberately unasserted (autocrlf platform variance); LF totals
  are the stable quantities.
* Agents do not commit: all files above are working-tree changes for the
  owner's snapshot job (D3 open). No pattern filed (no lesson family —
  pin arithmetic and profile-value plumbing are task-local).
