# b5ccg

Babylon 5 CCG — Deluxe-edition engine, continuing from Claude's
`docs/proposals/claude-b5ccggamedesign-system-improvements-v1.md`.

Forked 2026-10-16 from `Babylon5CCGGameDesign` (now in hiatus). Full game
snapshot: `b5ccg/src/`, `b5ccg/resources/` (Premiere + Deluxe cards, starter
decks), `BABYLON5_CCG_RULEBOOK.md` (canonical, pinned — see
`docs/DECISIONS.md`).

## Quick start

1. Read `AGENTS.md`, then `.agent/00_BOOT.md`.
2. Build: `b5ccg/compile.bat` (or `compile.sh`). Expect JDK 8 today; the
   JDK 25 move is staged work (WP-05/WP-06), not today's compiler.
3. Work the planner backlog in `.agent/TASK_LEDGER.md` (Tier-0 first:
   WP-00 snapshot, WP-01 rulebook pin, WP-02/WP-03 canonical-id).

## Milestone

The Playable Slice (Claude §8.3): seeded AI-vs-AI stability across starter
decks, legitimate termination ≥95%, zero UNIMPLEMENTED hits in slice decks,
deterministic human-seat game, rule-ID coverage of core sections.
