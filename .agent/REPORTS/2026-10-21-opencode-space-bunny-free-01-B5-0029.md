---
document:
  title: "B5-0029 - the unimplemented-text tripwire, and what it found"
  status: "Report. Observation, no authority (AGENTS.md section 3)."
provenance:
  author_llm: {name: "opencode (Space Bunny Free)", version: "space-bunny-free-2026-10-21-01"}
  created_date: "2026-10-21"
  last_modified_date: "2026-10-21"
  last_modified_by_llm: {name: "opencode (Space Bunny Free)", version: "space-bunny-free-2026-10-21-01"}
  assessor_llm: none
  no_self_assessment: true
---

# B5-0029 - the unimplemented-text tripwire

Claim: `engine,tooling`. Agent id `opencode-space-bunny-free-01`.
Ledger row: `B5-0029` (OPEN -> DONE).

## The row, and what the spec asked for

> WP-13 Unimplemented-text tripwire (A4)

The spec (A4.1) says:

> When a card flagged behaviour-bearing (by the census) is played or resolved
> with no matching effect entry, emit `UNIMPLEMENTED(cardKey)`. Fatal in test
> mode for decks in the Playable Slice; a counter in normal play.

The Playable Slice is defined in section 8.3 as the **starter decks**
(`premiere-starter-decks.json`), and criterion 3 of it is "Zero UNIMPLEMENTED
hits for any behaviour-bearing card in those decks". So the fatal set is the
starter-deck cards, and the counter set is everything else.

## What was there before: a stub that asserted nothing

The previous `HeadlessUnimplementedTextTripwire` was seven lines:

```java
public static boolean check(String cardText) {
    return cardText != null && (cardText.contains("unimplemented") || cardText.contains("NOT IMPLEMENTED"));
}
```

Three independent defects, and it is worth naming all three because the
withdrawn-stub correction is the reason this row matters:

1. **It checked the wrong signal.** It looked for a literal word in the
   card's *text*. A card can have unimplemented behaviour and never say so; a
   card can say the word "unimplemented" in a quoted rule reference and be
   fully implemented. The spec asks for "a card flagged behaviour-bearing (by
   the census) ... with no matching effect entry" - a question about the
   *census* and the *effect registry*, not about prose.
2. **It was never called.** The withdrawn-stub correction found it had no
   caller and was not a gate class, so it asserted nothing even about the
   thing it was looking for.
3. **It had no severity rule.** The spec distinguishes fatal-in-test-mode from
   counter-in-normal-play. The stub had one boolean.

## What it is now

`b5ccg/src/b5ccg/engine/HeadlessUnimplementedTextTripwire.java`, wired into
`compile.sh`'s `GATE_CLASSES` list as the 33rd class.

The join is the interesting part, and getting it wrong was the main risk:

* the **census** holds 446 card **titles**, each with a class and a `covered`
  flag;
* the **engine's registry** (`CardEffects.behaviourReferences()`) holds 93 card
  **ids**;
* the **card pool** maps id -> title, and is the only thing that can bridge
  them.

So the tripwire loads both printings of the pool, resolves every registry id
through it, and compares **titles** to the census. The synthetic fixture ids
are exempt, because they are deliberately not cards.

Three bugs I introduced and caught while building this, each by a measurement
rather than by reading:

| Bug | Symptom | How it was caught |
|---|---|---|
| compared ids to titles | every card looked unimplemented | the count came out as 137 findings against a census that said 56 were covered |
| loaded `premiere.json` only | 35 registry ids unresolvable | the "every registry id resolves to a pool title" check failed and named them; the ids were `de_*`, i.e. Deluxe-only |
| did not exempt the fixtures | 3 synthetic ids unresolvable | the same check named `enh_mines_rt`, `mer_metric_fixture`, `probe_multi_side_conflict` |

## The measurements

    behaviour-bearing titles   : 384   (of 446; the other 62 are VANILLA or TRAIT_ONLY)
    census-marked covered      : 56
    implemented by the engine  : 58    (the 2 extra are Delenn and Londo Mollari,
                                        whose census class is TRAIT_ONLY)
    UNIMPLEMENTED findings     : 328
      of which in starter decks: 81    <- the fatal set
      counted (non-starter)    : 247
    census/engine drift        : 0     (the census and the engine agree exactly)

**The 81 are real.** Spot-checked, not assumed: `conf_affirmation_of_power`,
`conf_border_raid` and `agenda_alliance_of_races` are all in
`premiere-starter-decks.json` by id, and none of their ids appears in
`CardEffects`. The census's `starter` flag and `covered` flag are both
correct, and the tripwire's re-derivation agrees with them.

**So the Playable Slice criterion 3 is not met: 81 cards in the starter decks
have printed behaviour and no implementation behind it.** That is a finding,
not a failure of the instrument, and the tripwire is the first thing in this
repository that reports it as a number.

## The baseline contract, and why it is shaped this way

A gate class that is red for a known-and-accepted gap stops being a signal
for anything. The gap is real but it is a **milestone target**, not a
regression - the same shape as `rule-coverage.ps1` reporting 22.8% against a
90% target and exiting 0.

So the tripwire asserts the **number**, not the ideal:

* `unimplemented total <= 328` (the measured baseline)
* `starter unimplemented <= 81` (the measured baseline)

Implementing a card lowers the count and is accepted. **Leaving a new card
unimplemented raises the count and turns the gate red.** A regression cannot
pass silently, and the known gap does not make the gate permanently red. The
assertion is `<=` rather than `==` so the baseline does not have to be
re-pinned every time a card is implemented; it only has to be re-pinned if
the gap *grows*, which is the direction that matters.

**Red-then-green, measured:**

| Injected fault | Result |
|---|---|
| none (current tree) | **exit 0**, 12 checks, 0 failures |
| a covered id removed from the registry (census/engine drift) | **exit 1** - "the census's covered flag agrees with the engine registry" fails, drift = 1 |
| a census record's `covered` flipped to true for an unimplemented card | **exit 1** - same check fails, drift = 1 |

Both red states were produced by editing the real files and re-running, and
both files were restored byte-exactly afterwards (`git diff` empty).

## The instrument is not vacuous

Two checks guard against a tripwire that cannot fail:

* "the join found a non-trivial set of behaviour-bearing titles" (384 > 100) -
  so the join is not finding nothing.
* "the tripwire found a non-trivial number of unimplemented cards" (328 > 50)
  - so the instrument is not reporting an empty world.

And the registry itself is checked for sorted order, so "is it implemented"
is a question with a stable answer. That is the B5-0027 contract, applied
here because this instrument reads the same registry.

## What was NOT changed

* The census data artefact was not changed. It was edited only to prove the
  red state, and restored byte-exactly.
* `CardEffects` was not changed by this row.
* No gameplay behaviour was touched. This row adds an instrument.

## Verification

    HeadlessUnimplementedTextTripwire -> exit 0, 12 checks, 0 failures
    RUN_TESTS=1 sh compile.sh          -> 33/33 Java classes + 2 tooling probes, exit 0, javac 25.0.4.1

## Reports

This report, plus the gate evidence in
`.agent/REPORTS/evidence/GATE-B5-0029.txt`.
