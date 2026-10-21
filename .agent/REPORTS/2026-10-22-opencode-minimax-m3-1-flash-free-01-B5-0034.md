---
document:
  title: "B5-0034 - WP-11 Groups pilot: 26 titles, one bespoke card, four vocabulary gaps"
  status: "Report (observation, no authority)"
provenance:
  author_llm: {name: "opencode (MiniMax M3.1 Flash Free)", version: "minimax-m3.1-flash-free-2026-10-22-01"}
  created_date: "2026-10-22"
  last_modified_date: "2026-10-22"
  last_modified_by_llm: {name: "opencode (MiniMax M3.1 Flash Free)", version: "minimax-m3.1-flash-free-2026-10-22-02"}
  assessor_llm: none
  advisory_review:
    - reviewer_llm: {name: "opencode (MiniMax M3.1 Flash Free)", version: "minimax-m3.1-flash-free-2026-10-22-02"}
      family: "minimax"
      author_family: "minimax"
      independence: "NONE - same family as the author"
      date: "2026-10-22"
      note: "Under the B5-0045 rule (reviewer independence is by model family, not agent id) this review is NOT an independent assessment and is not recorded as an assessor_llm entry. It is advisory. It was measured by execution - the pilot document was loaded entry by entry against the v0 registry rebuilt from its own commit - and it found two things. (1) Section 3's '19 of the 26 cards (73%) could be expressed' is the count of cards with AT LEAST ONE expressible entry; the card-level answer is 16 of 26 (61.5%), because a card whose Psi-triggered entry cannot be written down is not expressible. That is the figure this row's own DECISIONS entry (item 2) and its commit message carry, and it inverts the conclusion: the v0 vocabulary would have FAILED the Groups pilot, not passed it. (2) Four entries state a broader rule than their card prints - Spin Doctors (entries[21]) carries no Diplomacy restriction, and Diplomatic Corps, Secret Police and Imperial Telepaths use CONFLICT_WON without the conflict class - and the loader does not enforce the primitive registry's declared kind constraint (1 violation in 59 operations). The report's prose body is NOT rewritten; the author's author_llm is untouched. See .agent/REPORTS/2026-10-22-opencode-minimax-m3-1-flash-free-02-B5-0051-verification.md, docs/DECISIONS.md 'CORRECTION to the B5-0034 report' and 'FINDINGS from the verification of B5-0034', and ledger row B5-0052. A different-family assessment of this row is still outstanding."
  unassessed_revision:
    - {author_llm: {name: "opencode (MiniMax M3.1 Flash Free)", version: "minimax-m3.1-flash-free-2026-10-22-01"}, date: "2026-10-22", change: "initial report for B5-0034", note: "authored by this agent and NOT independently assessed; no assessor_llm entry is claimed for this pass. A different-family assessment of this row is outstanding."}
---

# B5-0034 - WP-11, the Groups pilot

**Row.** B5-0034, OPEN at the start of this session. Scope `engine,tooling`
widened to `engine,tooling,resources,docs` before the claim was taken - see
§12 - because the pilot's data is card data and lives in
`b5ccg/resources/effects/`, and because the row interprets rules in
`docs/DECISIONS.md`.

**Acceptance, verbatim from the proposal's work-package table (WP-11):**
"pilot-gate criteria (A3) met for Groups". The pilot gate (A3, "Pilot and
migration") is: "both pilots done, at least 70% of pilot cards expressible
without the escape hatch, zero UNIMPLEMENTED hits for pilot cards in fuzz
runs, no regression in the existing probes".

**Verdict.** The Groups half of that gate is measured and met on every clause
that can be measured: **26 of 26 group titles are covered**, **25 of 26
(96.2%) need no escape hatch**, the engine's behaviour is unchanged and the
legacy suite is green. **One clause is NOT met in its literal form** - "zero
UNIMPLEMENTED hits for pilot cards **in fuzz runs**" - because two of its
preconditions do not exist yet: the engine does not execute effect entries,
and there are no fuzz runs in the gate. That is stated in §7 and in the
DECISIONS entry rather than papered over.

**This report is not independently assessed.** It is recorded as an
`unassessed_revision` per AGENTS.md §1: a different-family reviewer has not
reviewed this row. The interpretations are logged in `docs/DECISIONS.md` under
"2026-10-22 - The Groups pilot: 26 titles, one bespoke card, four vocabulary
gaps (B5-0034 / WP-11)".

---

## 1. What was delivered

| Artefact | Size | Scope |
|---|---|---|
| `b5ccg/resources/effects/effects-groups-v1.json` | 35 entries over 26 cards | resources |
| `b5ccg/src/b5ccg/engine/HeadlessGroupEffectProbe.java` | 45 checks, a gate class | engine |
| `b5ccg/src/b5ccg/engine/effect/primitives/InformationPrimitives.java` | 3 primitives, a new family | engine |
| `b5ccg/src/b5ccg/engine/effect/primitives/RotationPrimitives.java` | 2 primitives, a new family | engine |
| `b5ccg/src/b5ccg/engine/effect/bespoke/SpinDoctorsPerceivedWinner.java` | the first bespoke handler | engine |
| `b5ccg/src/b5ccg/engine/effect/` (10 existing files widened, 3 new files) | the vocabulary the pilot forced | engine |
| `.agent/tools/schemas/effect-document.schema.json` | 3 vocabulary additions, 1 field | tooling |
| `.agent/tools/test-effect-schema.ps1` | 5 checks -> 6 (two documents) | tooling |
| `b5ccg/resources/effects/effects-v0.json` | 29 entries over 17 cards -> 22 over 12 | resources |
| `b5ccg/compile.sh` | one gate class added | tooling |
| `docs/DECISIONS.md`, `.agent/TASK_LEDGER.md` | the interpretations, the row | docs |

The new gate class is wired into `compile.sh`'s `GATE_CLASSES` here-doc, so it
is a gate rather than a comment.

---

## 2. The first correction: the pilot is 26 titles, not 51

The proposal's work-package table sizes WP-11 as "51 group titles". That
number is the two printings added together. Measured from the card files in
this session:

| Printing | Group cards |
|---|---|
| `premiere.json` | 26 |
| `deluxe.json` | 25 |
| **distinct titles, merged by canonical id** | **26** |

One title - "Psi Corps Intelligence" - is Premiere-only. The effect census
(B5-0005) holds exactly 26 GROUP records, which is the independent
confirmation: the census counts titles, not printings.

**Why the number matters.** The gate's 70% is a share of the pilot cards.
70% of 51 is 36 cards; 70% of 26 is 19. A pilot that reported "24 of 51"
would have flattered itself by a mile. The handoff this session started from
inherited the proposal's number and said "the other 46 group titles" - the
real figure was 21.

---

## 3. The measurement: 25 of 26 without the escape hatch

```
pilot cards: 26
expressible without the escape hatch: 25 (96%)
bespoke cards: 1 [group_spin_doctors]
bespoke operations: 1
```

The gate asserts the threshold (`>= 70%`), and the probe prints the share on
every run.

**The honest reading has two numbers, and the first one describes the
schema.** With the v0 registry exactly as B5-0028 left it, **16 of the 26
cards (61.5%)** could be expressed; **ten could not**, and the gate's
threshold is 70% - so the v0 vocabulary would have **failed** the Groups
pilot:

| Card | What the v0 vocabulary could not say |
|---|---|
| Military Telepaths | a conflict of the Psi type - the trigger set named three of the rulebook's four types |
| Imperial Telepaths | the same |
| Psi Corps Intelligence | the same |
| Damage Control Team | unrotate a fleet |
| ISN | reveal a hand |
| Motivated Leaders | discard the source card |
| Observers | look at hands; and "may" against "must" |
| Rangers Surveillance | look at a draw pile and reorder it |
| Religious Caste | a count that is "at most", not "at least" |
| Spin Doctors | change who a conflict's winner *is* - and the v0 registry published no bespoke handler, so it could not be written at all |

Ten of the 26 cards is 38.5% of the pilot, and the gate asks for at least
70% expressible. **The pilot found a real hole in the schema, not a
cosmetic one**, and the four vocabulary additions below close it. The
remaining one card - "Spin Doctors" - is bespoke by nature and is measured
as such (§5).

---

## 4. The four vocabulary gaps the pilot forced

A3 calls the effect-entry shape "a hypothesis derived from the rulebook; the
census may revise it". These are the revisions, and each one is a rule a
printed card states and the v0 vocabulary could not say:

1. **The trigger vocabulary could not name a Psi conflict.** The rulebook
   names four conflict types - Military, Diplomatic, Intrigue, Psi
   (`RB:IV/type-of-conflict`) - and the v0 set named three. Three group cards
   rotate during a Psi conflict. `CONFLICT_TYPE_PSI` is added, and the gate
   now asserts that the trigger vocabulary names all four types **the
   rulebook** names, checked against the rulebook rather than against
   itself.
2. **The condition vocabulary could not say "at most".** Every v0 condition
   was an "at least" test. "Religious Caste" needs "if you have no damaged
   characters". `DAMAGED_CHARACTERS_AT_MOST` is added.
3. **The operation shape could not say "may".** The rulebook distinguishes
   "the loser must discard 2 cards" from "all players **may** discard as
   many cards as they wish" (`RB:III/draw-round`). The Deluxe change to
   "Observers" ("may also discard 1 card from any one hand") could only be
   written as if it printed "must" - a rule stated wrongly, which is worse
   than a rule left unstated. Operations now carry an `optional` flag, and
   the canonical renderer marks a permitted operation with a trailing "?" so
   that it can never render the same way as a compulsory one.
4. **Three primitive families were missing.** `INFORMATION`
   (`REVEAL_HAND`, `VIEW_HAND`, `REORDER_DRAW_PILE`), `ROTATION`
   (`ROTATE_TARGET`, which moved there from `CONFLICT`, and the new
   `UNROTATE_TARGET`), and `DISCARD_SOURCE` in the `DISCARD` family.

**They are primitives, not bespoke handlers, and that distinction is the
point.** Revealing a hand is a behaviour three different cards have; a keyed
handler per card would make the escape hatch measure the wrong thing. A3
principle 1 asks for a hatch that handles "the genuinely bespoke tail", and a
tail padded with one-off handlers is not a measurement.

**The registry after the pilot:** 24 primitives across 14 families, the
largest family holding 3. The god-class bound (A3 principle 5) is asserted by
the gate, not by a reader's opinion.

---

## 5. The bespoke tail: one card, and the census already said so

"Spin Doctors" reads "Rotate this Group after any conflict. Change the
perceived winner of a Diplomacy conflict for Influence gain purposes. (you
gain their Influence; they gain none)".

The rule is not an operation on a card or a player: it changes **who a
conflict's outcome belongs to**, and then lets the ordinary influence-gain
rule run against that answer. No primitive can say it, and a primitive that
took an amount, a target and a trigger could say "you gain 1 influence" but
not "the winner is you, and therefore the real winner gains nothing" - the
second half is the whole card.

**The census agreed independently.** B5-0005 classified the 26 group cards
(23 TRIGGERED, 1 ONE_SHOT, 1 STAT_MODIFIER, 1 BESPOKE), and the card it
classes BESPOKE is "Spin Doctors" - the one the pilot found. The gate now
asserts that the data's bespoke usage and the census's BESPOKE class are the
**same set in both directions**, so neither artefact can drift away from the
other.

**The escape hatch is no longer empty.** The v0 registry published zero
handlers, which said the tail was empty without having measured it. It now
publishes one - `SPIN_DOCTORS_PERCEIVED_WINNER` ->
`SpinDoctorsPerceivedWinner` - and the class behind it is a pure rule with
no `GameState` dependency, in the shape A6 asks of an extracted module. The
honest limit: the engine does not execute effect entries yet, so the handler
is a published key and a tested rule, **not** a call site. The strangler
will call it.

---

## 6. The rulebook, the card text, and four judgement calls

The B5-0028 finding - "the rulebook SUPPLEMENTS the card text" - holds for
the groups, and so does the printing-scope rule (D5: the Deluxe printing
governs a dual title). Every entry's `textSha256` is computed from the
governing printed text at probe time, and the probe fails if it drifts.

Four judgement calls are worth naming, because a later reader would otherwise
have to guess them:

1. **"Your total" is a faction total.** "Add 3 to your Intrigue total" is a
   modifier on the controller's side of the conflict total, not a modifier on
   one character. The v0 skeleton modelled the *event* "Shadow Strike" ("Add
   4 to your total") with a FACTION-scoped target but modelled the five
   *group* cards with a CHARACTER-scoped one - the same rule written two ways
   in the same file. The pilot document uses the faction-scoped form
   throughout. The residual limit - the target vocabulary cannot say "the
   side you support in this conflict" - is recorded as an open item.
2. **A card that names two conflict types gets two entries.** "Rotate this
   Group during any Military or Intrigue conflict" is two entries, one per
   conflict type. The rotation cost is paid once, because the two triggers
   are mutually exclusive: a conflict has one type, so only one entry's
   trigger can fire. One entry with a two-type filter would need a filter the
   trigger vocabulary does not have; two entries with a shared cost would
   read as "rotate twice".
3. **A conflict-type trigger names the conflict class; the window names the
   step.** "Rotate this Group after any Military conflict" is
   `trigger=CONFLICT_TYPE_MILITARY, window=RESOLUTION`; "after any conflict"
   is `trigger=CONFLICT_RESOLVED, window=RESOLUTION`; "during any Military
   conflict" is `trigger=CONFLICT_TYPE_MILITARY, window=CONFLICT`.
4. **"Centauri only." is a play restriction, and it is card data.** The
   rulebook: "If a race name is part of a group's Card Type, then the card
   is restricted to that race. Only the player controlling the race listed as
   part of the Card Type may bring a restricted card into play"
   (`RB:IV/group-cards`). That restricts **who may play the card**, not what
   the effect targets, and the card files already carry it
   (`faction: CENTAURI`). Putting the race into the effect's target filter
   would have stated a different rule. "Influential Lords" shows the
   difference: its leading "Centauri only." is card data, while the "3 or
   more Centauri characters in your Inner Circle" inside the sentence is a
   condition on a counted set, and the entry says so.

---

## 7. What the pilot gate's third clause means, and what it does not

The clause is "zero UNIMPLEMENTED hits for pilot cards **in fuzz runs**". Its
preconditions:

- **the engine does not execute effect entries.** The legacy strangler - the
  registry consulted first, falling back to the existing `CardEffects`
  predicate - is not built. Nothing in the engine reads the pilot document.
- **there are no fuzz runs in the gate.** The fuzz rig is quarantined for
  runtime (475 s for 24 games) and is not part of the gate.

So the clause is **not met in its literal form**, and this row does not
pretend otherwise. What is measured instead:

| Measure | Value | Where |
|---|---|---|
| Group titles with at least one effect entry | **26 / 26** | `HeadlessGroupEffectProbe` |
| Pilot cards expressible without the escape hatch | **25 / 26 (96%)** | `HeadlessGroupEffectProbe` |
| The tripwire's own baseline (engine behaviour registry) | **unchanged** - 328 total / 81 starter | `HeadlessUnimplementedTextTripwire` |
| The engine's behaviour | **unchanged** - no existing probe edited, no card resolves differently | the whole gate |

The tripwire is the honest counterweight: it counts what the *engine*
implements, and it still says that 26 group titles are unimplemented, because
they are. The data now describes them; the engine does not yet execute the
description. The clause becomes measurable when the strangler lands.

**The legacy golden masters are not part of this row's scope.** The strangler
requires a golden-master scenario for each of the 82 *legacy* titles before
porting. Groups have **0% legacy coverage** - the proposal's own phrase, and
the census agrees: all 26 group records carry `covered: false` - so there is
no legacy behaviour for a group card to reproduce. A3 calls the Groups pilot
"a pure test of the ongoing, duration and restriction model with no legacy to
conflict with", and that is what was measured.

---

## 8. Red-then-green, measured

Each revert was applied separately, `out/` was wiped before every build, and
`javac`'s exit was recorded separately from the probe's exit.

### Probe level

| Tree | javac | Probe | Failing checks |
|---|---|---|---|
| **Red A** - one group card loses its entry (the "ISN" block is removed from the document) | 0 | **exit 1** | 2 - "every group title has at least one effect entry (25/26)" and "the pilot document describes as many cards as the pool has group titles" |
| **Red B** - the renderer drops the "?" optional marker | 0 | **exit 1** | 1 - "an entry with an optional operation renders to its second golden line" |
| **Red C** - the JSON Schema loses `CONFLICT_TYPE_PSI` | 0 | **exit 1** | 1 - "the schema and the engine agree on effectTrigger" |
| the fixed tree | 0 | **exit 0** | 0 - 45/45 |

Red B is the one worth dwelling on: the golden that catches it is the second
golden, for the entry whose operations include a *permitted* choice. A
renderer that dropped the marker would render "Observers" as if it printed
"must", and the golden catches it. The v0 probe's B5-0044 lesson - a golden
that cannot distinguish a correct implementation from a broken one is not
evidence - applied to this row's own new mechanism.

### Gate level

With **Red A** applied, the full gate was run and the failing-name set was
the new probe:

```
Summary: 37 passed, 1 failed (of 38 gate classes run)
FAILING-NAME SET (compare by name, never by count):
  - b5ccg.engine.HeadlessGroupEffectProbe
GATE EXIT = 1
```

Evidence: `.agent/REPORTS/evidence/GATE-B5-0034-RED.txt`.

**One caveat, stated because it is in the capture.** The first gate-level red
run in the copy reported **two** failing names - the group probe *and*
`HeadlessRulebookPinProbe`. The second is a copy artefact, not a revert: the
copy was made from `b5ccg/src`, `b5ccg/resources` and the tools, and the
rulebook lives at the repository root, so the pin class had nothing to pin.
The rulebook was copied into the tree and the gate was re-run; the failing
set is then the group probe alone, as quoted above. The capture in the
evidence file is the second run.

### Where the red states were run, and why

**The red states were produced in an isolated copy of the tree, not in the
shared one.** Another agent's row (B5-0026) was running the gate in the same
working tree at the same time, and a red tree there would have put a false
failure into *their* evidence file - which is exactly what happened once,
at 09:52, from this row's in-flight changes (their capture records
`HeadlessEffectSchemaProbe` failing on the v0 entry count while this row was
mid-flight). The copy holds the same sources, the same data, the same
`compile.sh` and the same tooling probes, and the red states were produced
and measured there; the green state is the real tree, whose capture is in
§9. The reverts are ordinary source changes, and the copy is what kept one
row's evidence from being corrupted by another's.

---

## 9. Gate evidence

```
RUN_TESTS=1 sh compile.sh      (from b5ccg/)
  javac 25.0.4.1, --release 8, 242 sources, 667 classes
  Summary: 38 passed, 0 failed (of 38 gate classes run)
  Total gate time: 342730ms
  Tooling probes: 4 passed, 0 failed
  GATE EXIT = 0
```

The gate class count went from 37 to 38 and the tooling probes from 3 to 4
(the fourth is the other row's process-tax probe, which is in the tree while
this row was running). **Every pre-existing class still passes and the
failing-name set is empty**, so the baseline is unchanged (AGENTS.md §4).

Evidence: `.agent/REPORTS/evidence/GATE-B5-0034.txt`.

**The other gates, run per `00_BOOT.md` step 8:**

| Tool | Result |
|---|---|
| `.agent/tools/run-dup-census.ps1` | exit 0 - 0 duplicate task IDs |
| `.agent/tools/check-citations.ps1` | exit 0 - every cited rule ID is in the index |
| `.agent/tools/lint-encoding.ps1` | exit 1 - **20 hits, the documented repo baseline**, unchanged, and **none of them in a file this row created** |

---

## 10. What did NOT change

- **The engine's behaviour is unchanged.** No existing probe was edited except
  the v0 effect probe's two count constants, which track a documented data
  move (§11); every conformance assertion is untouched; every card still
  resolves through `CardEffects`. The pilot document is **inert**: nothing in
  the engine reads it, exactly as the v0 skeleton was inert.
- **The legacy strangler is not started.** The registry is consulted by the
  loader only. The engine does not consult it.
- **The census is not touched.** 446 records, unchanged; the probe reads it.
- **The card files are not touched.** The pilot's `textSha256` values are
  computed from them.
- **`MiniJson` is not widened again.** The probe reads the census with
  `MiniJson.parseValue`, which B5-0028 already widened.
- **The inert schema stubs are still inert.** `GroupEffectSchema` and
  `EnhancementEffectSchema` remain 7-to-10-line interfaces with zero
  implementers. This row adds a *live* schema package beside them; it does
  not delete them, because deleting a public interface is a wider change than
  this row's scope. **They remain a standing defect.**

---

## 11. One source of truth per card: the five group cards moved

The v0 skeleton carried five group cards as well as the twelve cards that are
not part of either pilot. They now live only in the pilot document, so that
every card has exactly one entry set and the two documents cannot disagree
about it.

| Document | Before | After |
|---|---|---|
| `effects-v0.json` | 29 entries over 17 cards | **22 entries over 12 cards** |
| `effects-groups-v1.json` | - | **35 entries over 26 cards** |

The v0 gate class's two constants (`EXPECTED_ENTRIES`, `EXPECTED_CARDS`)
change with the data; **its three acceptance clauses and every other check in
it are unchanged**, and its two golden render lines are on cards that did not
move (`enh_babylon5_unrest` and `event_contact_with_shadows`).

The v0 report's §9 table (which listed the 17 cards) is **superseded, not
rewritten**: it is a record of what that row measured, and this row says
plainly where the five cards went.

---

## 12. Scope, and one piece of governance to note

The row's scope cell read `engine,tooling` and was widened to
`engine,tooling,resources,docs` before the claim was taken, because the
pilot's data is card data in `b5ccg/resources/effects/` (as B5-0028's row
was widened for the same file), because the row interprets rules in
`docs/DECISIONS.md`, and because AGENTS.md §5 lists the ledger under `docs`.

**A governance note, not a claim of authority.** `new-claim.ps1` does not
check scope overlap: this row's claim was taken while another agent held a
live claim on `docs,tooling` (B5-0026, same tree, 09:33). The two rows'
*files* barely overlap - the other row's artefacts are the process-tax
instrument and its probe - but `compile.sh` and `docs/DECISIONS.md` are
shared, and both were edited by both rows. The edits were additive and
re-read immediately before each write, but **the scope guard is missing from
the tooling, and the three recorded breaches (B5-0039/0040/0041) are the
same kind of hole.** A new ledger row for it is filed as part of this row's
close-out - **B5-0048** (scope `tooling`), alongside **B5-0049** (the
`rule-coverage.ps1` clone gap) and **B5-0050** (the five inert schema
interfaces) - because a hole that has already
let two rows touch one file is worth naming.

---

## 13. Open items, recorded rather than resolved

1. **The `stacking` semantics for "N instead of M" are not pinned down.** The
   v0 skeleton and the pilot document both write a conditional draw as a base
   entry with `REPLACING` and a conditional entry over it, and whether the
   conditional replaces the base's amount or adds to it is a question for the
   strangler - the first code that will have to answer it
   (`RB:VI/cumulative-effects`). "Extended Contacts" and "Contact with
   Shadows" both have this shape.
2. **The target vocabulary cannot say "the side you support in this
   conflict"** (`RB:III/conflict-round`). A conflict-total bonus is modelled
   as a faction-scoped modifier, which is as close as the vocabulary gets.
3. **The engine does not execute effect entries**, so the pilot document is
   inert and the pilot gate's third clause stays unmet until the strangler
   lands (§7).
4. **`GroupEffectSchema` and `EnhancementEffectSchema` are still inert
   stubs** with zero implementers (§10).
5. **The two data documents are not yet in the strangler's order.** The
   strangler is supposed to consult the registry first and fall back to
   `CardEffects`; no such path exists yet.

---

## 14. What the next rows inherit

**WP-12 (B5-0035, Enhancements pilot).** The schema now has the vocabulary
the groups needed, and the Enhancements pilot will need more: the "no two
copies of the same enhancement on one target" rule (`RB:IV/enhancement-cards`)
is a *restriction* on an attachment, the `StatBonus` reuse is a stat
modifier with a race filter, and "global enhancements" need a GLOBAL-scoped
target. The 78 enhancement titles are also two printings merged: premiere
ENHANCEMENT = 41, deluxe = 37, and the distinct count is 78 only if the
twins are counted twice - **measure the merged count before quoting it**, the
way §2 does for the groups.

**The legacy strangler.** The registry is populated and the data is
authoritative, but the engine does not read it. The strangler needs a
golden-master scenario per legacy title before any port, and the pilot
documents are inert until then.

**A suggested next row (not taken here).** The freeze-guard gap: the claim,
flip and ledger tools contain no freeze references, and no scope-overlap
check either - which is how two rows ended up editing `compile.sh` and
`docs/DECISIONS.md` in the same hour. Scope: `tooling`.

---

## 15. Provenance of this document

Authored by `opencode (MiniMax M3.1 Flash Free)`, version
`minimax-m3.1-flash-free-2026-10-22-01`, on 2026-10-22, from the
`opencode-minimax-m3-1-flash-free-01` session, on branch
`task/opencode-space-bunny-free-01-post-b5-0034`.

**This document is not independently assessed.** It is recorded as an
`unassessed_revision` in the front matter, per AGENTS.md §1: it is
author-written and no different-family reviewer has reviewed it. A
different-family assessment of this revision is outstanding.

The gate result quoted in §9 was measured in this session, in the real tree.
The red-then-green numbers in §8 were measured in this session, in an isolated
copy of the tree, for the reason given there. Every B5-0034 measurement in
§2, §3, §5 and §6 was measured in this session from the card files, the
census, the data and the gate logs - not carried forward from the proposal,
from the census's own numbers, or from the handoff this session started from
(two of which were wrong: the group-title count, and the number of group
titles left to do).
