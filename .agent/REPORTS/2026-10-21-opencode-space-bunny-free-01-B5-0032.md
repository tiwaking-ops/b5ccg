---
document:
  title: "B5-0032 - VictoryRules extraction, and the golden master that guards it"
  status: "Report. Observation, no authority (AGENTS.md section 3)."
provenance:
  author_llm: {name: "opencode (Space Bunny Free)", version: "space-bunny-free-2026-10-21-01"}
  created_date: "2026-10-21"
  last_modified_date: "2026-10-21"
  last_modified_by_llm: {name: "opencode (Space Bunny Free)", version: "space-bunny-free-2026-10-21-01"}
  assessor_llm: none
  no_self_assessment: true
---

# B5-0032 - VictoryRules extraction

Claim: `engine,tooling`. Agent id `opencode-space-bunny-free-01`.
Ledger row: `B5-0032` (OPEN -> DONE).

## The row, and what the spec asked for

> WP-15 VictoryRules extraction (A6.1)

The spec's work-package table gives the acceptance:

> WP-15 | VictoryRules extraction (A6.1) | WP-07 | new module; `RulesEngine`
> delegate | golden-master hashes identical; a simultaneous-victory probe
> passes | M

And A6 says what the module is: "Model each victory path as a rule with a
per-player eligibility predicate and an achieved predicate, and return a **set**
[of winners]".

The previous `VictoryRulesSchema` was a six-line interface with two accessor
declarations, no implementer and no caller - one of the eight withdrawn stubs.

## What is now in the tree

| File | Role |
|---|---|
| `b5ccg/model/VictoryOutcome.java` | the result: a **set** of winners, a path, a power |
| `b5ccg/engine/VictoryRules.java` | the five rulebook paths, each as eligibility + achieved predicates |
| `b5ccg/engine/HeadlessVictoryRulesGoldenMaster.java` | the gate class: the golden master and the simultaneous-victory probe |
| `b5ccg/engine/RulesEngine.java` | `checkVictory` now **delegates** to the module |

## Why the result is a set

The rulebook says it at :1048:

> If a card reduces the amount of power you need to win ... you can win the
> game even if you do not have more power than any other player. You can
> thereby circumvent part of the Victory requirements outlined in the standard
> rules. **Note that this makes it possible for more than one player to score
> a victory simultaneously.**

So a victory check returns a set, and the engine's single-winner
`checkVictory` is the degenerate case of a one-element set. `VictoryOutcome`
carries the set, plus `soleWinner()` (null when two won), `isSimultaneous()`,
and `firstWinner()` for the single-winner call sites.

The reduced-power mechanic is implemented, not just described:
`VictoryRules.reducePowerNeededToWin(player, threshold)` records it, and when
it is in force the strict-lead requirement does not apply - which is exactly
what lets two players qualify at once. No card in the current pool does this,
so the map is empty in production; the rule is expressed rather than assumed,
because a result type that cannot express "two players won" would be wrong
the moment such a card is printed.

## The golden master

`HeadlessVictoryRulesGoldenMaster` is a gate class (176 ms). It asks the
engine's own `checkVictory` and the extracted `VictoryRules.check` the same
question over **11 states** - each victory path, each bar, and each tie case:

    two players, nobody winning          four players, nobody winning
    one player left standing              standard victory: 20 and strictly leading
    standard victory blocked by a tie     standard victory blocked by 19 power
    major victory: 20 and 10 ahead       major victory blocked by a 9-point lead
    station condition 2                   shadow war bars Standard Victory
    a forfeited player does not block a win

and requires the same answer, and the same **path**, from both.

**Measured: 11/11 states, same winner; 11/11 states, same path.**

The simultaneous-victory probe then constructs the situation :1048 describes -
two players at 20 each, with the reduced-power requirement in force for both -
and requires the module to report **two** winners, to say it is simultaneous,
and to return null from `soleWinner()`. The control is the same state without
the reduced-power card: a tie at 20 must crown nobody.

## The delegation, and the one thing it changed

`RulesEngine.checkVictory` now delegates:

```java
public Player checkVictory(GameState state) {
    b5ccg.model.VictoryOutcome outcome = VICTORY_RULES.check(state);
    return outcome.firstWinner();
}
```

`RulesEngine.victoryRules()` exposes the module for callers that want the
multi-winner form.

**The golden master caught a real discrepancy in my own work.** The engine's
original `checkVictory` had *two* last-standing checks: the first returned the
last standing player immediately, and the second - reachable only when the
first did not fire - routed a last-standing player through `majorVictory()` so
the win would be recorded under the Major path (B5-0661, rulebook :821). I
implemented the second branch faithfully, and the golden master failed with
"engine=LAST_STANDING module=MAJOR" on the forfeited-opponent state.

The cause is that both checks counted with `isPlayerActive`, which already
excludes surrendered players, so the two counts are identical and the second
branch was **unreachable**: whenever it would have fired, the first check had
already returned. The engine's observable behaviour is the first check's
answer, so that is what the module reproduces, and the reason is recorded in
the module's comment so the next reader does not re-derive it.

This is the golden master doing exactly its job. It was not asked to find a
bug in the engine; it found one in the extraction, because the extraction is
the thing being checked.

## Red-then-green, measured

| Mutation | Result |
|---|---|
| none | **exit 0**, 19 checks, 0 failures |
| the module's power threshold changed to 99 | **exit 1** - the golden master disagrees with the engine on 3 of 11 states, and names different paths on 3 |
| the module always reports a winner | **exit 1** - the golden master disagrees on 4 of 11 states, including "two players, nobody winning" |

Both mutants were produced by editing the real module and re-running.

## The rule IDs

Every rule in the module carries a rule ID that `check-citations.ps1` resolves
against `docs/RULE_INDEX.md`: the five paths cite `RB:I/victory`, and the
reduced-power path cites `RB:VI/reducing-power-needed-to-win`.

## What was NOT changed

* `RulesEngine.checkVictoryPath` and `VictoryPathResult` (B5-0663) are
  untouched. They are additive, the conformance suite asserts on them, and
  they remain the single-winner path query.
* The engine's gameplay behaviour is unchanged: 35/35 gate classes pass, and
  the conformance suite's 904 checks are unchanged.
* No card data was touched.

## Verification

    HeadlessVictoryRulesGoldenMaster -> exit 0, 19 checks, 0 failures
    HeadlessConformanceTest          -> exit 0, "CONFORMANCE SUITE PASSED (904 checks)"
    RUN_TESTS=1 sh compile.sh         -> 35/35 Java classes + 2 tooling probes, exit 0, javac 25.0.4.1

## Reports

This report, plus the gate evidence in
`.agent/REPORTS/evidence/GATE-B5-0032.txt`.
