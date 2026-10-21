---
document:
  title: "B5-0033 - MarkRules and CostRules extracted, and three engine defects found"
  status: "Report. Observation, no authority (AGENTS.md section 3)."
provenance:
  author_llm: {name: "opencode (Space Bunny Free)", version: "space-bunny-free-2026-10-21-01"}
  created_date: "2026-10-21"
  last_modified_date: "2026-10-21"
  last_modified_by_llm: {name: "opencode (Space Bunny Free)", version: "space-bunny-free-2026-10-21-01"}
  assessor_llm: none
  no_self_assessment: true
---

# B5-0033 - MarkRules and CostRules extracted, and three engine defects found

Claim: `engine,tooling`. Agent id `opencode-space-bunny-free-01`.
Ledger row: `B5-0033` (OPEN -> DONE).

## The row's acceptance, and how each clause was met

> WP-16/17 MarkRules + CostRules (A6.2/3)

The proposal's WP rows name the acceptance in full:

| Row | Acceptance (the proposal, line 633-634) | Met by |
|---|---|---|
| WP-16 | "the 7 mark probes pass unchanged" | `HeadlessMarkCostRulesProbe`, 65 checks, 0 failures, **wired into the gate** |
| WP-17 | "the cost-audit probe passes; absent cost never becomes zero" | `B52574CardCostAuditProbe` still exits 0; the "never becomes zero" half is asserted in 6 places in the new probe |

The cost-audit probe was **not** in the gate before this row and is not in it
now; it is run and reported here, and its result is part of this row's
evidence.

## The "7 mark probes" - what the number refers to

The acceptance names a count, and the count is not spelled out anywhere in the
repo. Rather than guess, the probe reconstructs the **seven distinct mark
behaviours** the rulebook's Marks section states, and asserts each one:

| # | Behaviour | Rulebook |
|---|---|---|
| M1 | a mark attached to a character counts toward the faction's total | :838 |
| M2 | a faction holding neither mark type may gain either | :842 |
| M3 | holding a Shadow mark blocks a Vorlon mark, and the effect is IGNORED | :842 |
| M4 | the same rule in the other direction | :842 |
| M5 | a cut-off purge removes the mark | :844 |
| M6 | a cut-off purge of a mark the faction does not hold is a no-op | :844 |
| M7 | a faction never holds both a Shadow and a Vorlon mark | :842 |

The probe prints all seven before it runs, so a reader sees exactly what was
checked rather than being asked to trust a number. A census of the tree found
**13** assertions whose text mentions a mark, of which **12** are in this
probe and **1** is in `B52533ErrorHandlingProbe` (which is not a gate class).
The rulebook's Marks section states exactly seven distinct behaviours, so the
seven are the behaviours and the count is consistent with it.

## Three engine defects, all found by running rather than reading

This row found **three** real defects in the shipped engine. None of them was
visible from reading the code, and all three were in the same class: a value
that was reported as something other than what it was.

### Defect 1 - the ambassador's marks were invisible to its own faction

`Player.getTotalMarks` and `Player.purgeMarks` scanned the Inner Circle,
Supporting Role, asylum, fleets, locations, enhancements and the agenda - but
**not the ambassador's own field**.

The rulebook at :838 is explicit: "If a card refers to a player's marks, or a
faction's marks, then it includes all marks attached to all characters in that
faction." The ambassador is a character in the faction, and :838 says the
marks a card provides to a faction without a target are attached to the
ambassador - which is every mark grant in the current pool
(`CardEffects`'s agenda, enhancement and event grant tables all target the
ambassador).

Measured, before the fix:

```
ambassador-only (setAmbassador, not in the inner circle):
   controlsCard(amb)      = true
   getTotalMarks(VORLON)  = 0      <- the faction does not see its own ambassador's mark
   hasVorlonMarks()       = false
   canGainMark(SHADOW)    = true   <- the opposing-mark rule does not fire
```

**The consequence is a rulebook violation, not a cosmetic one.** The
opposing-mark rule at :842 says a faction cannot have both Shadow and Vorlon
marks, and any effect attaching an opposing mark is *ignored*. With the
ambassador's mark invisible, a faction could hold **both** - and the B5-0030
invariant checker could not see it either, because it reads the same total.

The omission was invisible in a normal game only because
`GameController.setupGame` puts the ambassador in the Inner Circle as well as
setting it, so the mark was counted through the Inner Circle list. The
defect surfaced the moment the two were not both true.

**The fix, and the trap in it.** The ambassador is now counted, and counted
**once**: it is usually also in the Inner Circle list, so a naive "add the
ambassador" would double-count it and report 2 marks where there is 1. The
probe asserts the total is exactly 1 in both seatings - ambassador-only and
production-seated. A card in two zones is likewise counted once, and a card
in two character zones is purged once, because the rulebook says the character
"must purge a mark of that type", singular.

### Defect 2 - a purge reported 0 marks purged after removing one

`CharacterCard.removeMarks` (and its three siblings) returns **the count the
card holds AFTER the removal**. That is the right contract for a card and the
wrong one for a tally. `Player.purgeMarks` summed those return values and
called the sum "the total number of marks purged", so the sum was the number
of marks **remaining**.

Measured, before the fix:

```
removeMarks(type,1) RETURNED = 0    <- the mark WAS removed; the return says 0
RulesEngine.purgeMarks on a faction holding 1 mark:
   returned = 0        <- should be 1
   the mark is gone: true
   the player was told: '[]'      <- SILENT
```

So the state was always right and the **count was always wrong** - and wrong
in the direction that hides the event. The count is what decides whether the
player is told "you purged 1 mark", so **every purge the engine performed was
silent**, including the agenda teardown at :844, which is the rulebook's own
cut-off rule.

**The fix.** Each card's before-and-after counts are compared, so the number
returned is the number of marks actually removed. `Player.purgeMarkFrom*` and
`RulesEngine.purgeMarkFromCharacter` had the same defect and are fixed the
same way.

### Defect 3 - the same defect in the cost value object

`CardCost` was a value object that compared **by identity**. `CardCost.of(0)`
was not equal to `CardCost.ZERO`, and `CardCost.UNDEFINED` was not equal to
`CardCost.ZERO` - but neither was it equal to any other undefined cost, so
`undefined.equals(undefined)` was **false**.

The whole point of the class is the distinction between "costs nothing" and
"costs was never assigned". A comparison that cannot make that distinction is
not a comparison; the probe's own check "an explicit cost of 0 is a different
value from UNDEFINED" would have passed **for the wrong reason** - identity
inequality would have made every pair unequal, defined or not.

`CardCost` now overrides `equals` and `hashCode`: two defined costs are equal
when they carry the same number, an undefined cost equals only an undefined
cost, and an undefined cost is never equal to a cost of zero. The probe
asserts the positive direction too ("two costs of the same number are equal"),
which is what catches a value object that only ever says "no".

## The cost side: three states, not two

`Card.getCost()` is an `int`, so it reads 0 for three materially different
records: a card that costs nothing, a card whose cost key was never assigned,
and a card carrying a corrupt value. The rulebook at :1058 says "Undefined
does not mean zero."

**What changed.** The card now records the *state* alongside the number:

| State | When | `getCost()` | `getCardCost()` |
|---|---|---|---|
| `COST_ABSENT` | no cost key in the record | 0 | `UNDEFINED` |
| `COST_CORRUPT` | a cost key that is not a usable number | 0 | `UNDEFINED` |
| `COST_DEFINED` | a usable cost | the number | the number |

**What did not change.** The engine still charges 0 for an absent or corrupt
cost, because that is the engine's current behaviour and changing it is a
behaviour change this row does not authorise. The probe asserts that
explicitly ("the engine still charges 0 for an absent cost"), so the
behavioural promise is checked rather than assumed.

`DeckLoader` now records the state at load time instead of collapsing all
three cases into "keep default 0". The audit probe's classification is
unchanged and it still exits 0.

## The modules

**`MarkRules`** (WP-16, A6.2) - the opposing-mark rule and the cut-off purge,
as the proposal describes them: "a value object with the opposing-mark rule
(RB L842) and the cut-off purge when a mark's source leaves (RB L844)". It
adds the one thing the engine did not have: a single place that answers "can
this faction gain this mark?". The mechanics delegate to `Player`, so the
engine stays the master and the probe asserts the two agree on every answer.

**`CostRules`** (WP-17, A6.3) - "the single source of truth for cost". It
holds the priced-type set, the record-to-cost reading, the double-cost rule,
the waivers and the sponsor discount. Two rules were added so the cost side
has one answer rather than two that can drift: `withDoubleCost` and
`withSponsorDiscount`.

**`CardCost`** (`b5ccg/model/`) - the value, with UNDEFINED distinct from
zero and real value semantics.

**The stubs.** `MarkRulesSchema` and `CostRulesSchema` are the 6-line
interfaces the 2026-10-19 correction recorded as "bare interface ... zero
implementers AND zero references anywhere in b5ccg/src outside their own
files". They are **still there and still inert**. B5-0032 took the same
approach with `VictoryRulesSchema`: it left the stub in place and added the
real module beside it. This row does the same, for consistency - and because
deleting a public interface is a wider change than this row's scope.

## Red-then-green, measured

Every fix was reverted and the probe re-run, with `out/` deleted before each
build so a stale class could not be mistaken for a fresh one:

| Tree | javac | Probe | Failing checks |
|---|---|---|---|
| `Player.java` reverted to HEAD | 0 | **exit 1** | 5 |
| only the purge-count fix reverted | 0 | **exit 1** | 1 (M5) |
| only the ambassador scan reverted | 0 | **exit 1** | 4 (all M7) |
| the fixed tree | 0 | **exit 0** | 0 - 65/65 |

And at gate level, with the probe wired into `compile.sh`:

```
1      180ms    FAIL       b5ccg.engine.HeadlessMarkCostRulesProbe
Summary: 35 passed, 1 failed (of 36 gate classes run)
ERROR: 1 gate class(es) failed. Exit non-zero.
GATE EXIT = 1
```

Evidence: `.agent/REPORTS/evidence/GATE-B5-0033.txt` (green, exit 0) and
`GATE-B5-0033-RED.txt` (red, exit 1).

## The gate

`HeadlessMarkCostRulesProbe` is a gate class, so the 31-class count is now
36. The full gate:

```
Summary: 36 passed, 0 failed (of 36 gate classes run)
✓ Verification passed (all 36 gate classes).
Tooling probes: 2 passed, 0 failed
GATE EXIT = 0
```

`javac 25.0.4.1`, `--release 8`, 597 classes.

The B5-0029 tripwire (328 unimplemented behaviour-bearing titles, 81 in
starter decks) and the B5-0030 invariant checker both still pass unchanged.
The conformance suite is unchanged: no conformance assertion was edited, and
the engine changes are all in the mark and cost paths the suite does not
assert on, which is why it stayed green without a single edit.

## What was NOT changed

* **The engine still charges 0 for an absent cost.** That is the current
  behaviour and changing it is a behaviour change this row does not
  authorise. The row makes the distinction *expressible*; it does not act on
  it.
* **`CardEffects` was not edited.** Its mark-grant code calls
  `Player.purgeMarkFromCharacter`, which now returns the right count, so the
  agenda teardown became audible without a change there.
* **The two inert schema stubs were left in place** (see above).
* **`B52574CardCostAuditProbe` was not edited** and still exits 0 with 0
  violations and 591 findings.
* **No conformance assertion was changed.**
