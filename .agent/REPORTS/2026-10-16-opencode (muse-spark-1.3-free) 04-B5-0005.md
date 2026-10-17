---
document:
  title: "B5-0005 close-out — effect census (WP-04/A2)"
  status: "Report"
provenance:
  author_llm: {name: "Muse Spark", version: "muse-spark-1.3-contributor-free"}
  created_date: "2026-10-16"
  task: "B5-0005"
---

# B5-0005 close-out — Effect census (WP-04/A2)

## Acceptance

1. **Checker exits 0.** New gate `b5ccg.model.EffectCensusChecker` —
   25/25 PASS, exit 0, wired into the `RUN_TESTS=1` gate in `compile.sh`
   (single decision file per B5-0956; `compile.bat` keeps no test branch
   by decision). Measured on a green tree before the B5-0010 rename
   broke the shared build (see §8).
2. **446 titles each classified exactly once.** Asserted by the checker
   against the live pool, not just written here: census title set ==
   pool title set (446 premiere titles; every deluxe title also a
   premiere title), zero duplicates, zero orphans, class counts sum to
   446, plus three mutation negative-controls.
3. **Owner reviews the class table.** The table is §4 below and is also
   printed by the checker on every gate run. Owner review is pending;
   the artefact and the gate are delivered.

## Changes (model scope + resources + one gate line)

* `b5ccg/resources/census/effect-census.json` (new) — the census: 446
  records `{title, type, printing, class, tags[], starter, covered}`,
  schema_version 1, closed 14-class taxonomy embedded. Effective text
  rule: Deluxe governs dual-printing titles (D5), premiere-only titles
  fall through. `covered` = named by >= 1 `CardEffects`
  behaviour literal; `starter` = member of
  `premiere-starter-decks.json` (146 titles).
* `b5ccg/src/b5ccg/model/EffectCensusChecker.java` (new) — the checker:
  pool denominators, exactly-once classification, closed-vocabulary
  classes/tags (tags != primary, unique), printing/type agreement with
  the pool, starter join vs the live deck list, coverage join vs the
  live `CardEffects.behaviourReferences()` (fixture liveness included),
  class-table printout, 3 mutation controls. Self-contained JSON
  reader; reads engine code only for the live behaviour set. Java 6,
  stdlib only. 0/1/2 exit contract.
* `b5ccg/compile.sh` — two lines: checker wired into `RUN_TESTS=1`
  after the parity probe.

No engine, ai, ui, card-data, or deck-list file touched. No
interpretation recorded (measurement only — no DECISIONS entry needed).

## Method (first pass, per proposal A2)

LLM first pass over all 446 effective texts (read in full, grouped by
type), deterministic checker proves exactly-once, stratified ~11%
self-audit (49 titles: 4 per large class, all of BESPOKE and
COST_MODIFIER) re-read against texts. Audit corrections applied:
Lyta Alexander TRAIT_ONLY → STAT_MODIFIER (contributes Psi, same shape
as Minbari Telepath); +STAT_MODIFIER tags on 6 combat-bonus locations;
+TRAIT_ONLY tags on 17 cross-faction-recruitment titles (feeds A3
`traits[]`/`loyalty` field work). Human spot-check of a stratified 10%
and owner review of the class table remain open to the owner.

## Class table (checker output, owner-review copy)

| Class | n | Starter | Covered | Reads as |
|---|---|---|---|---|
| ONE_SHOT | 93 | 28 | 24 | immediate resolve-at-play effects |
| TRIGGERED | 67 | 20 | 2 | activated/round-start/conditional abilities, location income |
| STAT_MODIFIER | 63 | 16 | 6 | stat auras, attach bonuses, permanent/temporary bumps |
| CONFLICT_VARIANT | 59 | 15 | 8 | all 59 conflict cards: type + winner/loser clauses |
| VANILLA | 43 | 36 | 0 | no rules text (32 fleets, 11 characters) |
| DECK_HAND | 33 | 8 | 8 | draw/discard/search/recycle/look/reorder as the whole effect |
| RESTRICTION | 30 | 4 | 0 | prohibitions, permissions, protections, candidacy locks |
| AGENDA_WIN | 26 | 9 | 5 | all 26 agendas: win condition + ongoing |
| TRAIT_ONLY | 19 | 9 | 0 | eligibility/role/participation text only (A3 `traits[]` feed) |
| COST_MODIFIER | 7 | 1 | 0 | influence-cost increases/reductions/taxes |
| BESPOKE | 6 | 0 | 0 | escape-hatch candidates (list below) |
| MARK | 0 | 0 | 0 | no mark mechanics in the pool |
| STATE | 0 | 0 | 0 | no tension/unrest mechanics in the pool |
| VOTE | 0 | 0 | 0 | no vote/council mechanics in the pool |

Sum 446. Behaviour-bearing (non-vanilla, non-trait-only) = 384.

## Decision rule (proposal §5 A2 heuristic)

* Bespoke tail: 6/384 = **1.6%** (far below the ~40% rethink bar).
* The 6 bespoke: Carpe Diem (extra action), Chrysalis (card
  transformation), Elric (once-per-game whole-pile recycle),
  Enrage (free out-of-sequence conflict), Seduction (temporary control
  change), Spin Doctors (winner redirection for payout).
* Operation inventory (keyword-measured per class, hand-verified):
  ~24 distinct primitives cover the 378 non-bespoke behaviour-bearing
  titles — gain/lose/transfer influence, draw, discard (targeted,
  random, all), look/reveal, search/tutor, shuffle, reorder, rotate/
  unrotate, damage, heal, cancel/negate, stat bonus (temp, EOR,
  permanent, floor), immunity/untargetable, force-participate,
  force-support/oppose, cost +/-, recruit-discount/free, row move,
  seize/control, winner/loser clauses, win-condition checks,
  round-triggers, conditional thresholds. Each bespoke title needs at
  most one escape-hatch primitive.
* Verdict: **proceed with A3 as designed** — >= 70% fits <= 25
  primitives holds with wide margin (98.4% in 11 schema classes).

## Joins (both asserted live by the checker every gate run)

* Starter decks: 146 titles flagged; flag set == deck-list title set
  exactly (every deck id maps to a pool title). Starter-heavy classes:
  VANILLA 36, ONE_SHOT 28, TRIGGERED 20 — the Playable Slice already
  touches every major class.
* CardEffects coverage: 53 titles covered (80 pool literals). By class:
  ONE_SHOT 24, DECK_HAND 8, CONFLICT_VARIANT 8, STAT_MODIFIER 6,
  AGENDA_WIN 5, TRIGGERED 2. RESTRICTION 0, COST_MODIFIER 0 —
  whole behaviour families with zero engine implementation; A3 pilots
  should expect no legacy to conflict with there.

## Scope-ladder evidence (proposal §4)

MARK/STATE/VOTE at zero is measured, not inferred: no pool text
mentions marks-as-mechanics, tension, unrest, votes, council,
contingencies, Psi Corps faction rules, Drakh, or ISA (only
flavor-name hits: Markab, N'Grath, Grey Council, Bester/Sarah). S4/S5
mechanics have zero pool representation — do not build them
speculatively. 47 agenda `winCondition` fields: 21× "Reach 20
Influence", 4× most-characters/most-military variants, all classified
AGENDA_WIN (proposal Q3 input delivered).

## Verification log

* `compile.bat` green (exit 0) with the census + checker in tree.
* `EffectCensusChecker` 25/25, exit 0 (run standalone and in-gate
  position; re-verified after the audit tag fixes and the Lyta fix).
* Full gate on the green tree: conformance 900/904 with the same 4
  pre-existing failures as the B5-0002/B5-0003 baseline (AES, AIJ,
  MINES ×2); namespace, parity, pin, smoke, cross-check, detach,
  UI suites all green; `CardSetManifestChecker` exit 1 with the known
  383-title baseline (A1b resolves it structurally, out of scope).
* Checker self-caught two of its own test bugs before green (schema
  number formatting; a mutation using an already-starter title) —
  fixed, no census change.
* Two observations NOT from this row, left for owners (see §8):
  HeadlessJoinTimingProbe EXIT=1 (engine-internal, causally excluded);
  shared tree red from live B5-0010 rename at close-out.

## Collision / handoff note (B5-0010)

Mid-task a second session (`opencode (muse-spark-1.3-free) 05`,
claim B5-0010 engine scope, live) began the Faction→Race rename and
broke the shared build (285–297 `Loyalty/Race` type errors) while this
row was verifying. B5-0005's diff is provably inert to it (no
references to the checker; no directory scans in DeckLoader; red
build reproduced with B5-0005 files removed — same error family).
This row's acceptance was measured green before the break; the final
confirming rebuild must run after B5-0010 lands green. Claim/heartbeat
discipline held throughout: no foreign claim or heartbeat touched.
B5-0010's ledger row already warns "never alongside live engine
work" — the window was not an engine freeze in practice; owner may
want to make that rule structural (one-tree-one-writer or branches).

## Addendum at close (~06:45 UTC): deferred green confirmation

B5-0005 closes DONE against green-tree verification with one deferred
item: at close-out the shared tree is still red under the live B5-0010
engine rename (688 compile errors and falling at last measure — the
rename is converging, not finished). The next session MUST run
`compile.bat` green plus `EffectCensusChecker` 25/25 before treating
the tree as green; if the checker ever exits non-zero after B5-0010
lands, the §8 drift analysis (zero drift at close) says to suspect a
changed behaviour table first, and the checker's diff output names the
titles. Byte-identical backups of both deliverables sit at
`C:\temp\b50005-hold\` until that confirmation lands; remove them
after.
