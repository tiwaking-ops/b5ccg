# TASK_LEDGER — b5ccg (fresh at fork, 2026-10-16)

Planner-authored backlog seeded from Claude v1 §13. Keep ~5–10 OPEN rows.
Acceptance lines are binding. IDs WP-00.. continue the proposal's numbering
(deliberate fresh sequence — see fork DECISIONS entry).

| ID | Status | Task | Scope | Claim | Report |
|---|---|---|---|---|---|
| WP-00 | OPEN | Snapshot, secret scan, restore drill (B0). Acceptance: untracked .java = 0, restore drill builds + gates green, stale-snapshot alert exists | tooling | - | - |
| WP-01 | OPEN | Pin rulebook, rule-set profile stub (A9). Acceptance: hash recorded, 689-vs-1236 reconciled in writing, profile recorded in replays | engine | - | - |
| WP-02 | OPEN | Canonical-key function + parity probe (A1a). Acceptance: probe exits 0, every CardEffects literal canonical, existing probes unchanged | engine | - | - |
| WP-03 | OPEN | Printing-diff triage of 82 special-cased titles (A1a). Acceptance: all 58 single-literal titles resolved + scenario test passing under both ids | engine | - | - |
| WP-04 | OPEN | Effect census (A2). Acceptance: checker exits 0, 446 titles each classified exactly once, owner reviews class table | model | - | - |
| WP-05 | OPEN | Toolchain measurement T1.0. Acceptance: jdeps + throwaway JDK25 --release 8 warning/internals report delivered | tooling | - | - |
| WP-06 | OPEN | Toolchain hops + policy T1.1-T1.3 (needs D2 + DECISIONS entry). Acceptance: all gates green under JDK 25, old pin retired, census sentinel cycle run | tooling | - | - |
| WP-07 | OPEN | Seeded RNG, state hash, double-run probe (A5). Acceptance: identical hashes across runs, human-seat probe deterministic in gate | engine | - | - |

Future (not yet OPEN — promote after Tier-0): WP-08 BLOCKED triage n/a (no
legacy BLOCKED rows carried over), WP-09 process-tax review, WP-10..WP-12
effect schema + pilots, WP-13..WP-14 fail-loud instruments, WP-15..WP-17
engine decomposition, WP-18 traceability, WP-19 Faction→Race rename (needs
D1), WP-20 hygiene batch.
