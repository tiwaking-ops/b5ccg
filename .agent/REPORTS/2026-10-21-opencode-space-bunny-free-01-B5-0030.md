---
document:
  title: "B5-0030 - the invariant checker, the fuzz harness, and a real engine bug it found"
  status: "Report. Observation, no authority (AGENTS.md section 3)."
provenance:
  author_llm: {name: "opencode (Space Bunny Free)", version: "space-bunny-free-2026-10-21-01"}
  created_date: "2026-10-21"
  last_modified_date: "2026-10-21"
  last_modified_by_llm: {name: "opencode (Space Bunny Free)", version: "space-bunny-free-2026-10-21-01"}
  assessor_llm: none
  no_self_assessment: true
---

# B5-0030 - the invariant checker and the fuzz harness

Claim: `engine,tooling`. Agent id `opencode-space-bunny-free-01`.
Ledger row: `B5-0030` (OPEN -> DONE).

## The row, and what the spec asked for

> WP-14 Invariant checker + fuzz (A4) - freeze sustained; independent of
> --release 8; depends on B5-0029 tripwire (FULL)

The spec (A4.2, A4.3) asks for two instruments:

* **A4.2** - "After every action in test mode, assert state invariants, each
  tagged with its rule-ID."
* **A4.3** - "Run a few hundred seeded games over every starter-deck pairing,
  count action types, rule-IDs and effect entries actually fired, and flag
  dead engine paths, stalls and invariant violations."

Both stubs were seven lines and five lines respectively, and both had the
same two defects: they asserted nothing (one was
`return true; /* invariant pass placeholder */`, the other
`return true; /* invariant preserved under fuzz */`), and nothing called
them.

## What is now in the tree

| File | Role | Tier |
|---|---|---|
| `HeadlessInvariantCheckerProbe.java` | the checker: reads real state, asserts 7 invariants, each tagged with its rule ID | called by the fuzz harness |
| `HeadlessFuzzProbe.java` | the harness: seeded AI-vs-AI games, invariant check after every state change, reachability histogram | report-only rig, **not** a gate class |
| `HeadlessInvariantCheckerSelfTest.java` | fast self-test: proves the checker can fail | **gate class** (169 ms) |

## The invariants, and their sources

Each is a direct reading of the rulebook, and each cites a rule ID that
`check-citations.ps1` can resolve:

| Rule ID | Invariant | Rulebook text |
|---|---|---|
| `RB:II/set-tension-tokens` | tension is within 1..5 | "Tension and unrest range from a 1 to 5." |
| `RB:II/set-tension-tokens` | unrest is within 1..5 | same |
| `RB:VI/marks` | a faction does not hold both a Shadow and a Vorlon mark | the opposing-mark rule |
| `RB:IV/agenda-cards` | a player holds at most one agenda | "one agenda in play" |
| `RB:I/influence` | every card in play is in exactly one zone | zone membership |
| `RB:VI/tension-and-unrest` | unrest is not negative | the unrest range |
| `RB:VI/marks` | a player's mark total is not negative | the mark rules |

**One invariant deliberately NOT asserted, and why.** The spec's example list
includes "influence rating never below 3 (RB L288)". The engine does not
enforce that floor at the model level, and this repository has already made
that exact call: `HeadlessBonusFloorProbe` (B5-4145) removed its assertion
against `Player.loseInfluence` for exactly this reason, calling it "a
wrong-layer test" - the floor is a constraint on *transfers and actions*, not
on the model, and the conformance suite itself uses `loseInfluence` to set
influence to zero as a fixture. Asserting the floor in the invariant checker
would re-introduce that wrong-layer test, and it would fire on the suite's
own fixtures. The rule is enforced where it belongs, by the transfer paths.

**One invariant that is structural rather than runtime.** "At most one agenda"
is enforced by the compiler: `Player` holds a single `AgendaCard` field. What
is checked at runtime is the part that is *not* structural - that the agenda
a player holds is a real card with an id.

## The real bug the harness found

**This is the payoff, and it is a genuine engine defect.**

The fuzz harness plays real games and checks the invariants after every state
change. On **seed 20348130** it found:

    game seed 20348130 errored: java.lang.UnsupportedOperationException
      at b5ccg.engine.CardEffects.applyAftermathEffect(CardEffects.java:948)
      at b5ccg.engine.GameController.applyAftermathPlay(GameController.java:1591)
      at b5ccg.engine.GameController.resolveQueuedConflict(GameController.java:1456)
      at b5ccg.engine.GameController.resolveQueuedConflicts(GameController.java:1371)
      at b5ccg.engine.GameController.runGame(GameController.java:225)

The cause, at `CardEffects.java:948`:

```java
Card retrieved = deck.getDiscardPile().remove(deck.getDiscardPile().size() - 1);
```

`Deck.getDiscardPile()` returns `Collections.unmodifiableList(discardPile)`.
So `.remove(...)` throws `UnsupportedOperationException` **every time**. The
"Focus Your Efforts" aftermath - "Play after winning any conflict. Move one
card from your discard pile to your hand" - could never have worked. Not
"worked unreliably": it always threw.

It was the only call site in the tree that removed from the discard pile
through the read-only view, and the engine had been running with a latent
exception in a card effect that no other gate class reached.

**The fix.** `Deck` gained `takeTopDiscarded()`, the mutator that the
read-only view was hiding the need for, and `CardEffects` calls it. The seed
that threw now completes with `GAME_OVER`.

**This is exactly what the spec meant by "green is not evidence".** The
conformance suite, the canonical-parity probe and the 34 gate classes were
all green with that exception in the tree.

## The measurements

    games                 : 12   (24 also measured)
    games completed       : 12   (24/24)
    games timed out       : 0
    games errored         : 0    (1 before the fix)
    state changes         : 860  (1,628 over 24 games)
    invariant checks      : 6,020 (13,024 over 24 games)
    invariant violations  : 0

The reachability histogram reports the rule IDs exercised and the action types
observed, both as sorted maps so two runs produce the same report. The
end-condition histogram showed `GAME_OVER` for every game after the fix.

## Why the harness is not a gate class, and what covers the gap instead

The harness takes **~20 s per game** - 475 s for 24 games. That is the
**game's** pace, not the harness's: measured at a 2 ms poll interval the
4-game run took 90.2 s against 90.6 s at 20 ms. So it is quarantined in
`compile.sh` beside the other soak runners, with its measured time recorded
and the reason stated.

That leaves a gap, and it is a gap of exactly the kind this project has been
burned by: **if the invariant checker were broken, nothing in the gate tier
would notice**, and the fuzz rig would report "no violations" because it was
checking nothing. That is the withdrawn-stub failure mode, rebuilt one layer
up.

`HeadlessInvariantCheckerSelfTest` closes it in 169 ms. It builds a state
that violates an invariant on purpose and asserts the checker reports it, with
the right rule ID; it builds a clean state and asserts the checker reports
nothing; and it asserts the checker is deterministic and that every invariant
carries a rule ID.

**Red-then-green, measured:**

| Mutation | Result |
|---|---|
| none | **exit 0**, 11 checks, 0 failures |
| the checker returns no violations always | **exit 1** - "a tension above 5 is a violation" fails, "a tension below 1 is a violation" fails |
| the checker always reports a violation | **exit 1** - "a freshly built state has no invariant violations" fails, and the two rule-ID checks fail |

Both mutants were produced by editing the real checker and re-running. A
self-test that has never been seen failing is not evidence that it can fail.

## What was changed

* `b5ccg/src/b5ccg/model/Deck.java` - added `takeTopDiscarded()`.
* `b5ccg/src/b5ccg/engine/CardEffects.java` - the "Focus Your Efforts"
  aftermath uses it. **This is a behaviour change**: the effect now works
  where it previously threw.
* `b5ccg/src/b5ccg/engine/HeadlessInvariantCheckerProbe.java` - new.
* `b5ccg/src/b5ccg/engine/HeadlessFuzzProbe.java` - new.
* `b5ccg/src/b5ccg/engine/HeadlessInvariantCheckerSelfTest.java` - new.
* `b5ccg/compile.sh` - the self-test added to `GATE_CLASSES`; the fuzz rig
  added to the quarantine list with its measured time and reason.

## Verification

    HeadlessInvariantCheckerSelfTest -> exit 0, 11 checks, 0 failures
    HeadlessUnimplementedTextTripwire -> exit 0, 12 checks, 0 failures
    HeadlessFuzzProbe 12 games      -> exit 0, 6,020 invariant checks, 0 violations
    RUN_TESTS=1 sh compile.sh       -> 34/34 Java classes + 2 tooling probes, exit 0, javac 25.0.4.1

## Reports

This report, plus the gate evidence in
`.agent/REPORTS/evidence/GATE-B5-0030.txt`.
