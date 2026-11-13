---
document:
  title: "B5-0262 close-out: PREMIERE pool promoted to Source of Truth (2026-11-13)"
status: "Report (observation, no authority)"
provenance:
  author_llm: {name: "Inkling", version: "Inkling 2 Small Free"}
  created_date: "2026-11-13"
  last_modified_date: "2026-11-13"
  last_modified_by_llm: {name: "Inkling", version: "Inkling 2 Small Free"}
  assessor_llm: []
related_task: "B5-0262"
---

# B5-0262 close-out — the PREMIERE pool's authority, executed

This report supersedes `.agent/REPORTS/2026-11-13-opencode-inkling-2-small-free-01-B5-0262.md`'s
interim mismatch findings (preserved in git history, and summarised in §1 below) with the final
state after the owner's 2026-11-13 decision executed.

## 1. What was wrong (evidence, 2026-11-13 morning)

`b5ccg/resources/cards/premiere.json` did not contain `premiere-tiwa-claude-edit.json` (v1.4).
Deployed was aligned to `authoritative-character-data.json` (its `source_of_truth`), on the six
canonical character-stat fields only — 0/92 mismatches there — and nothing else:

- 391 of 446 card texts differed (deployed still carried the old unverified text; only 55 identical)
- 62 cost mismatches (59 deployed-null vs printed value; 3 genuine disagreements: `char_neroon` 4 vs 8, `fleet_first_battle_human` 8 vs 10, `group_rabble_rousers` 0 vs 6)
- 23 field-level differences vs the owner-approved build (17 stat values, 1 `isAmbassador`, 4 subtype incl. the owner-approved agenda `AGENDA` rulings, 1 rarity)
- character text vs the designated SoT: only 12 of 92 matched
- gate blind spot: Data-Authority Gate checked only those six fields on 92 characters — green while the whole text pool was stale

The B5-0260 "Restored premiere.json from premiere-tiwa-claude-edit.json" report claim (Nemotron
session, 2026-11-12) was not borne out by the working tree.

## 2. Owner decision (2026-11-13, recorded)

Path 2 (whole pool); rename `premiere-tiwa-claude-edit.json` → `authoritative-premiere-card-data.json`;
provenance label `status: "Authoritative"` + `source: "HUMAN-OWNER-VERIFIED (scan-derived build; Tiwa
Pene, 2026-11-12, v1.4)"` (owner-approved verbatim); gate extended to text across all 446 cards;
work on a fresh task branch (`task/opencode-inkling-2-small-free-01-B5-0262`).

## 3. What changed (all in one task, claim scope `resources, docs` + one tooling probe)

1. **The file is the SoT now.** `authoritative-premiere-card-data.json` carries the designation
   (top-level `status` + `source`, at repo root). Registry (`docs/AUTHORITY_INDEX.md`) regenerated:
   new "Premiere Card Data (PREMIERE)" domain (28 canonical fields, presence parity);
   "Character Stats (PREMIERE)" rebinds to the pool file (6 fields); "Card Text (PREMIERE)"
   "None established" position superseded (B5-0232), kept as an audit-trail note; superseded
   `authoritative-character-data.json` marked Superseded with `source_of_truth` pointer and
   supersession note (values untouched).
2. **Deployed pool re-synced** to the SoT (all 446; 395 texts, 62 cost reconciliations — all in
   favour of the approved build, including the three genuine conflicts; 23 field rulings; agenda
   `AGENDA`; `enh_energy_mines`; `fleet_first_battle_human` rarity). Every deployed character's
   `source_of_truth` → the new SoT. `influencePerRound` is not in the SoT and the 13 deployed
   LOCATiONs carry `influencePerRound: 1` **only to feed the engine parser's existing default**
   (`intVal(m, "influencePerRound", 1)`); the field asserts nothing about printed text and is not
   in the 28 canonical fields.
3. **Ten B5-0206 AFTERMATH records** restored in the SoT (line-broken scan-verified text for
   hunted/lamentations/refugees/renowned_victory/repairing_the_past; `_text_source`;
   `flavor_text`), required by `HeadlessB50206TextReconcileProbe`, sourced from the 2026-11-08
   B5-0206 backup (`premiere.json.pre-remediation-backup-2026-11-08`). The backup's double-encoded
   em dashes were normalised out (pool's real `flavorText` used for `flavor_text`).
4. **Gate extended.** `.agent/tools/check-data-authority.py` gained `compare_pool()`
   (whole-pool, 28 fields, presence parity) plus a legacy-layout-tolerant character comparison.
   `.agent/tools/test-data-alignment.ps1` (B5-0255 probe) repointed to the pool file — checks and
   strictness unchanged (92/92, six fields). Both edits are tooling-scope and are called out here
   honestly (claim row says resources, docs; this was the same class of necessity B5-0261 hit with
   probe edits; noted here rather than smuggled silently).
5. **Effect documents re-pinned** (deluxe-overwrite-governed `textSha256` semantics, probes and
   `CardIdNamespaceGuard.canonicalKey` read DELUXE twins where they exist):
   `effects-v0.json` 27, `effects-enhancements-v1.json` 2, `effects-groups-v1.json` 18.
   The 45 affected entries were previously pinned against outdated Premiere text; **semantics not
   re-reviewed** (see Caveats).
6. **Six `docs/proposals/*.md|json`** `_authority` repointed to the new SoT (editorial pointer
   edits; historical narrative preserved verbatim).
7. **Encoding guard repair:** `docs/DECISIONS.md` carried 5 CRLF lines from the 2026-11-12 entries
   (previous sessions); normalised to LF (guard PASS).
8. **DECISIONS** entry appended (2026-11-13, B5-0262) recording all of the above.

## 4. Verification

`RUN_TESTS=1 sh compile.sh` (staged JDK 25, `--release 8`) on the fresh task branch:

- **Java gate classes: 58/58 PASS** (incl. `HeadlessB50206TextReconcileProbe` — restored; the six
  game-logic classes that failed on the parser's `influencePerRound` requirement after the SoT
  sync now pass; `HeadlessEffectSchemaProbe` / `HeadlessGroupEffectProbe` /
  `HeadlessEnhancementEffectProbe` PASS after correct deluxe-governed re-pin)
- **Tooling probes: 14/14 PASS** (after repointed `test-data-alignment.ps1`; `test-encoding-guard`
  repaired as above)
- **Data-Authority Gate PASS**: designation self-test red state 4/4 rejections + 2/2 acceptances;
  Premiere Card Data domain 446/446 cards on 28 fields OK; Character Stats 92/92 OK
- `check-citations.ps1` PASS (0 unresolvable citations); `run-dup-census.ps1` PASS (0 duplicates)
- Per-class logs: `out_gate_704/`; wrapper logs `out_gate_b50262_run2.log` / `..._run3.log`

## 5. Caveats (binding, do not lose)

1. **Semantic review of the 45 re-pinned effect entries is outstanding.** Deluxe-governed text is
   now hashed and green, but the operations in those entries were authored against older printed
   texts; whether each operation still matches v1.4 text was not re-verified. This was the same
   judgement B5-0261 made for seven entries (rewired those; re-hashed two that matched); the
   remaining 45 need the same pass before their behaviour is trusted.
2. **The engine still requires `influencePerRound` on LOCATiONs** (`DeckLoader` "Required" +
   `validateFields` throw). The deployed pool feeds it `1` (the parser's own default). A clean
   fix is an engine-scope row (relax validation + a fail-without-the-change probe, or derive from
   printed text if the rulebook permits). Not done here — out of claim scope, and per AGENTS.md
   "If in doubt, log the ambiguity in the report and STOP that item only."
3. **The `.agent/tools` edits (`check-data-authority.py`, `test-data-alignment.ps1`) are tooling
   scope** under a `resources, docs` claim; recorded here and in DECISIONS instead of silently.
4. **`concatenate/clobber hazard`:** `premiere.json` and the new SoT were both fully rewritten this
   task (python round-trip, `indent=2`, UTF-8, LF). Diffs are large but mechanical; no card count
   or id changed (446/446, order unchanged — asserted in the sync script).
5. **Claim files for DONE rows remain in `.agent/CLAIMS/`** (`B5-0206.json`, `B5-0207.json`,
   `B5-0208.json`, `B5-0260.json`) — a pre-existing "release was a copy" pattern, noted, not
   reaped (planner's call per boot step 9).

## 6. Follow-ups (owner's call, not filed as rows)

- B-Next: semantic review of the 45 entries (or prohibition on trusting those 45 until reviewed).
- B-Next: engine ` influenciaPerRound` relaxation with probe (engine scope).
- Optional: refresh `b5ccg/resources/census/effect-census.json` (derived; currently consistent —
  classification unaffected by text bytes; only efficiency, not correctness).
- The four audit-trail BLOCKED rows (B5-0215/0216/0225/0226) and the elapsed B5-0026 window remain
  the planner's/owner's disposition; nothing in this task touches them.

## 7. Provenance of this file

Written by the executing agent (Inkling / Inkling 2 Small Free, agent id `opencode-inkling-2-small-free-01`)
from measurements taken on 2026-11-13. No independent assessment has been performed on this report.
