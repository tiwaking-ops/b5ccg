---
document:
  title: "B5-0003 close-out — canonical-key function + parity probe (WP-02/A1a)"
  status: "Report"
provenance:
  author_llm: {name: "Muse Spark", version: "muse-spark-1.3-contributor-free"}
  created_date: "2026-10-16"
  task: "B5-0003"
---

# B5-0003 close-out

## Acceptance

1. **Probe exits 0.** New gate `b5ccg.engine.HeadlessCanonicalParityProbe`
   — 17/17 PASS, exit 0, wired into the `RUN_TESTS=1` gate in `compile.sh`
   (single decision file per B5-0956; `compile.bat` keeps no test branch
   by decision).
2. **Every CardEffects literal canonical.** 83 behaviour references (80 pool
   + 3 documented fixtures): each resolves to exactly one pool entry, no
   unexplained non-canonical literal remains — asserted by the probe, not
   just written here. 24 collapsed `de_` duplicates removed; deluxe-only
   overrides and aftermath slug pairs stay exact deliberately (see below).
3. **Existing probes unchanged.** Namespace 18/18, pin 21/21, replay 25/25,
   smoke PASS, manifest cross-check PASS (829 cards) — identical counts to
   the pre-change baseline. Conformance: 900/904 with the same 4
   pre-existing failures (AES/AIJ/MINES) recorded at baseline before any
   edit; no new failure.

## Changes (engine scope only)

* `b5ccg/src/b5ccg/engine/CardIdNamespaceGuard.java` — new
  `canonicalKey(String)`: strips the `de_` DELUXE prefix registered in
  `cards/index.json` (premiere prefix is empty = identity). Premise pinned
  twice: the existing namespace probe asserts the DELUXE prefix is still
  `de_`, and the new parity probe fails if the registry ever requires a
  prefix this function does not strip. Measured: no premiere id starts
  with `de_`, all deluxe ids carry it (C-NAMESPACE-2 makes the strip
  unambiguous).
* `b5ccg/src/b5ccg/engine/CardEffects.java` — every behaviour-table lookup
  routed through exact-first/canonical-fallback `get()`/`has()` helpers;
  24 `de_` duplicate entries collapsed to one canonical entry per title;
  `behaviourReferences()` + `NON_POOL_FIXTURE_IDS` expose the literal set
  to the probe (an unlisted future table is invisible to the probe — the
  method javadoc says so).
* `b5ccg/src/b5ccg/engine/HeadlessCanonicalParityProbe.java` — new gate:
  registry premise, denominators (2 files, 829 ids, 83 refs, 325 canonical
  pairs), per-literal pool resolution, same-title printing-pair rule for
  canonical collisions, fixture-exemption liveness, and three mutation
  negative-controls proving the detector fires (M1 replays the removed
  dangling literal through the resolver).
* `b5ccg/compile.sh` — probe wired into `RUN_TESTS=1`.

## Behaviour deltas (both intended A1a fixes, no interpretation needed)

* **Deluxe "Intrigues Mature" now draws 2** (was generic draw-1): its
  `de_` twin exists in the pool with byte-identical text
  ("Play any time. Draw 2 cards") but had no literal — the exact
  one-printing-silently-does-nothing defect A1a exists to kill.
  Unchanged-text unification; WP-03 triages changed-text titles.
* **`de_am_assigning_blame` literal removed**: "Assigning Blame" is
  premiere-only (deluxe.json carries no Deluxe printing), so the literal
  named no pool card and could never trigger. The probe fails on exactly
  this shape, so the dead literal went rather than the probe learning an
  exemption. Reachable behaviour unchanged (the card cannot enter play).

## Deliberately NOT unified (printing-specific by design, WP-03 input)

* `de_conf_bio_weapon_discovery` loser influence, `de_agenda_servants_of_order`
  round income, `de_agenda_total_war` aftermath ban — exact-first lookup
  preserves them; premiere twins unaffected (verified by reasoning + probes).
* Aftermath `aftermath_X` / `de_am_X` slug divergence: prefix-strip yields
  `am_X`, which matches nothing, so both printings keep exact pool-id
  literals. A1b's `cardKey` (WP-10..12) is the structural answer.
* Premiere-only pool cards (`event_affirm_alliance`,
  `conf_affirmation_of_peace`, `conf_dishonor`, `aftermath_assigning_blame`)
  keep bare-only literals — complete, probe-verified.

## Verification

* `compile.bat` green (exit 0, marker present; one self-caught red mid-way:
  `has()` applied to a Map — fixed to a `get()!=null` test before rebuild).
* New probe 17/17, exit 0. Existing probes: see Acceptance §3.
* Measured census (for WP-03/B5-0004): 102 pool literals before (53 bare +
  49 `de_`, 24 both-ways titles — matches proposal §8.3 modulo drift:
  single-literal non-aftermath titles are 4 bare-only + 0 `de_`-only, plus
  25 aftermath slug pairs); 325 same-title canonical pairs, 0 title
  mismatches; 58 deluxe aftermath vs 59 premiere (Assigning Blame has no
  Deluxe printing).
* Pre-existing, unrelated, left alone: `CardSetManifestChecker` exits 1
  with 383 "Title appears in multiple sets" findings at baseline — its
  title-uniqueness rule contradicts the two-printing data model; A1b
  resolves it structurally. Not in this row's scope.

## Notes for next session

* No pattern filed: canonical-key routing + exact-first-override is
  task-local mechanics, and the ledger-race/report lessons belong to
  earlier rows. One tooling footnote (not a family): `py -c` with regex
  quoting breaks under this host's PowerShell — scratch scripts went in
  `.agent/scratch-*.py` files instead (removed at close).
* Coexistence: `.agent/HEARTBEATS/... 03.json` (active, `live_claims:
  ["B5-0009"]`, "building snapshot job") appeared mid-task; B5-0009 has
  no ledger row. Untouched per protocol (30-min TTL, not mine to reap);
  its scope reads tooling, mine was engine — no writer collision.
* Agents do not commit: all files above are working-tree changes for the
  owner's snapshot job (D3 open).
