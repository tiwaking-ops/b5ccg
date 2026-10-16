# TASK_LEDGER — b5ccg (fresh at fork, 2026-10-16)

Planner-authored backlog seeded from Claude v1 §13 (WP numbers in Task text).
IDs are B5-nnnn (fresh sequence from B5-0001) because the queue, claim, and
census tools key on the B5-[0-9]{4} pattern. Keep ~5–10 OPEN rows.
Acceptance lines are binding.

| ID | Status | Task | Scope | Claim | Report |
|---|---|---|---|---|---|
| B5-0001 | OPEN | WP-00 Snapshot, secret scan, restore drill (B0). Acceptance: untracked .java = 0, restore drill builds + gates green, stale-snapshot alert exists | tooling | - | - |
| B5-0002 | OPEN | WP-01 Pin rulebook, rule-set profile stub (A9). Acceptance: hash recorded, 689-vs-1236 reconciled in writing, profile recorded in replays | engine | - | - |
| B5-0003 | OPEN | WP-02 Canonical-key function + parity probe (A1a). Acceptance: probe exits 0, every CardEffects literal canonical, existing probes unchanged | engine | - | - |
| B5-0004 | OPEN | WP-03 Printing-diff triage of 82 special-cased titles (A1a). Acceptance: all 58 single-literal titles resolved + scenario test passing under both ids | engine | - | - |
| B5-0005 | OPEN | WP-04 Effect census (A2). Acceptance: checker exits 0, 446 titles each classified exactly once, owner reviews class table | model | - | - |
| B5-0006 | OPEN | WP-05 Toolchain measurement T1.0. Acceptance: jdeps + throwaway JDK25 --release 8 warning/internals report delivered | tooling | - | - |
| B5-0007 | OPEN | WP-06 Toolchain hops + policy T1.1-T1.3 (needs D2 + DECISIONS entry). Acceptance: all gates green under JDK 25, old pin retired, census sentinel cycle run | tooling | - | - |
| B5-0008 | OPEN | WP-07 Seeded RNG, state hash, double-run probe (A5). Acceptance: identical hashes across runs, human-seat probe deterministic in gate | engine | - | - |

Future (not yet OPEN — promote after Tier-0): WP-08 BLOCKED triage n/a (no
legacy BLOCKED rows carried over), WP-09 process-tax review, WP-10..WP-12
effect schema + pilots, WP-13..WP-14 fail-loud instruments, WP-15..WP-17
engine decomposition, WP-18 traceability, WP-19 Faction-to-Race rename (needs
D1), WP-20 hygiene batch.
