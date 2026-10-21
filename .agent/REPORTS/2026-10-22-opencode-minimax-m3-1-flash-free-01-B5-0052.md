---
document:
  title: "B5-0052 - the conflict-type scope, the conjunction the vocabulary could not say, and a kind constraint that failed open"
  status: "Report (observation, no authority)"
provenance:
  author_llm: {name: "opencode (MiniMax M3.1 Flash Free)", version: "minimax-m3.1-flash-free-2026-10-22-03"}
  created_date: "2026-10-22"
  last_modified_date: "2026-10-22"
  last_modified_by_llm: {name: "opencode (MiniMax M3.1 Flash Free)", version: "minimax-m3.1-flash-free-2026-10-22-03"}
  assessor_llm: none
  unassessed_revision:
    - {author_llm: {name: "opencode (MiniMax M3.1 Flash Free)", version: "minimax-m3.1-flash-free-2026-10-22-03"}, date: "2026-10-22", change: "initial report for B5-0052", note: "authored by this agent and NOT independently assessed. The findings this row fixes came from a same-family advisory review (B5-0051), which under B5-0045 is advisory and is NOT an assessor_llm entry. A different-family assessment of B5-0034 and of this row is outstanding: a DeepSeek-family reviewer was attempted on 2026-10-22 and produced no output at all (HTTP 429, free_rate_limited)."}
---

# B5-0052 - the conflict-type scope, and a kind constraint that failed open

**Row.** B5-0052, filed by the B5-0051 verification of the Groups pilot
(B5-0034) and OPEN at the start of this session. Scope
`engine,resources,docs`.

**Acceptance, verbatim from the ledger row:** "the four entries carry their
conflict class, the conjunction is expressible in the vocabulary, the loader
refuses an operation whose primitive does not allow the entry's kind, every
shipped document still loads, and the gate is green".

**Verdict.** All five clauses are met and measured. This row is the **second
pass over B5-0034's own data**, and it found that the pilot's data stated a
broader rule than three of its cards print, that the effect vocabulary had
nowhere to put the missing qualification, and that one of the format's own
declared constraints was not enforced anywhere.

**This report is not independently assessed.** It is recorded as an
`unassessed_revision` per AGENTS.md §1. The review that found the defects
(B5-0051) shares this session's model family, so under B5-0045 it is
**advisory**, recorded on the author's report as an `advisory_review` block
and not as an `assessor_llm` entry. A different-family assessment is still
outstanding - and the attempt at one is recorded in §7.

---

## 1. The three defects, and what each one was

### 1.1 Four entries stated a broader rule than their card prints

| Entry | Card text | What the data said | What it should say |
|---|---|---|---|
| `group_spin_doctors` | "Change the perceived winner of **a Diplomacy conflict** for Influence gain purposes" | `trigger: CONFLICT_RESOLVED` - any conflict, any type | a Diplomacy conflict |
| `group_diplomatic_corps` (2nd) | "Rotate this Group during any **Diplomacy** conflict ... if you win, draw 1 card" | `trigger: CONFLICT_WON` - won a conflict of any type | a Diplomacy conflict you won |
| `group_secret_police` (2nd) | "Rotate this Group during any **Intrigue** conflict ... If you win, the loser must also rotate one character" | `trigger: CONFLICT_WON` | an Intrigue conflict you won |
| `group_imperial_telepaths` (2nd) | "Rotate this Group during any **Psi** conflict ... If you win, the loser must discard 1 card" | `trigger: CONFLICT_WON` | a Psi conflict you won |

**The cause was a fifth vocabulary gap.** The v0 trigger set carries the
outcome (`CONFLICT_WON`) and the conflict class (`CONFLICT_TYPE_*`) as two
separate names, with no way to say both at once. A3's own shape puts the
conjunction in the trigger ("conflict resolved **and initiator won**",
`RB:VI/triggered-effects`), and the pilot's convention puts the conflict
class in the trigger and the step in the window - so "a conflict of type T
that you won" had nowhere to go, and the three riders were written the broad
way.

**The fix follows the v0's own design.** The v0's trigger javadoc says the
filter belongs on the condition: *"the filter ('and the initiator won')
belongs on the entry's EffectConditions, not inside a free-text trigger"*. So
the trigger keeps the conflict class and the outcome filter becomes a
condition: `CONTROLLER_WON_CONFLICT` - "the conflict was won by the source's
controller", which is the card's own "if you win". The rulebook's "Won" is
the initiator's outcome (`RB:IV/aftermath-cards`), and a participant may also
be considered to have won it (`RB:VI/sample-conflict`).

```
group_diplomatic_corps   trigger=CONFLICT_TYPE_DIPLOMACY  conditions=[CONTROLLER_WON_CONFLICT]
group_secret_police      trigger=CONFLICT_TYPE_INTRIGUE    conditions=[CONTROLLER_WON_CONFLICT]
group_imperial_telepaths trigger=CONFLICT_TYPE_PSI        conditions=[CONTROLLER_WON_CONFLICT]
group_spin_doctors       trigger=CONFLICT_TYPE_DIPLOMACY  conditions=[]
```

### 1.2 The primitive registry's declared kind constraint failed open

`EffectPrimitive.kinds()` declares "the effect kinds this primitive may
appear under", and the v0 registry's javadoc treats the kinds as part of the
contract. **No code path read the declaration**: the loader never looked at
the entry's kind, and `EffectRegistry.describe` resolved the primitive,
checked its parameters and checked bespoke registration without consulting
`kinds()`.

Measured over both shipped documents - the kind sets parsed out of the 24
primitive classes' own Java sources, every operation checked: **59
operations, 1 violation**, `effects-groups-v1.json` entry 13 ("Motivated
Leaders"), a `ONE_SHOT` entry using `STAT_MODIFY`, whose declaration was
{ONGOING, REPLACEMENT, TRIGGERED}. A3 principle 4 is "**Fail closed.** An
unknown effect kind is a load error"; this is a second kind of kind - a
*known* kind used with a primitive that does not allow it - and it failed
open too.

**The loader now refuses it**, and the refusal names both halves:

```
entries[0].operations[0]: operation 'DRAW' does not allow effect kind TRAIT; it allows
[ONE_SHOT, ONGOING, TRIGGERED, REPLACEMENT]
```

**And the declaration was the thing that was wrong.** "Motivated Leaders" is
"All your characters in Inner Circle gain +1 Leadership permanently. Discard
after this effect triggers" - a one-shot by any reading, and the effect
census classes it ONE_SHOT as well. A one-shot stat bonus is an ordinary
card effect, so `STAT_MODIFY`'s declared kinds were widened to include
`ONE_SHOT`. The declaration is now enforced, so it cannot drift again.

### 1.3 What was NOT a defect

The B5-0051 verification also recorded what it checked and did **not** find
wrong, and this row re-measured it: the "may" mapping is correct and is the
only optional operation in the file; all six play restrictions ("Centauri
only.", "Minbari only.", "Narn only.", "Human only.") are left as card data
rather than turned into effect-target race filters; the two inside-sentence
race restrictions are correct; all fourteen titles whose Deluxe text differs
from Premiere reflect the **Deluxe** reading; the bespoke card is unique and
agrees with the census; every `textSha256` matches the governing printed
text; and every cited rule ID resolves.

---

## 2. The changes

| Artefact | Change | Scope |
|---|---|---|
| `b5ccg/src/b5ccg/engine/effect/EffectConditionType.java` | `CONTROLLER_WON_CONFLICT` added | engine |
| `b5ccg/src/b5ccg/engine/effect/EffectLoader.java` | the kind constraint is enforced - a load error | engine |
| `b5ccg/src/b5ccg/engine/effect/primitives/StatPrimitives.java` | `STAT_MODIFY` allows `ONE_SHOT` | engine |
| `b5ccg/resources/effects/effects-groups-v1.json` | four entries carry their conflict class | resources |
| `.agent/tools/schemas/effect-document.schema.json` | the new condition type | tooling |
| `b5ccg/src/b5ccg/engine/HeadlessGroupEffectProbe.java` | 6 new checks (45 -> 51) | engine |

**The loader's check** is in the operation loop, immediately after the
primitive is resolved:

```java
EffectPrimitive resolved = registry.primitive(primitive, ol);
registry.describe(op, ol);
if (!resolved.kinds().contains(kind)) {
    throw new EffectLoadException(ol,
            "operation '" + primitive + "' does not allow effect kind " + kind.name()
            + "; it allows " + resolved.kinds());
}
```

**The new probe checks** (six, all of them falsifiable):

1. no pilot entry uses the bare `CONFLICT_WON` trigger;
2. the "if you win" rider of Diplomatic Corps names `CONFLICT_TYPE_DIPLOMACY`
   and carries the outcome filter;
3. the same for Secret Police (`CONFLICT_TYPE_INTRIGUE`);
4. the same for Imperial Telepaths (`CONFLICT_TYPE_PSI`);
5. "Spin Doctors" is scoped to a Diplomacy conflict;
6. the loader refuses an operation whose primitive does not allow the entry's
   kind - an actual load attempt, not a description of one.

---

## 3. Red-then-green, measured

Each revert was applied separately in an isolated copy of the tree (the
shared tree was running the gate for this row at the same time, and a red
tree there would have put a false failure into another row's evidence file),
`out/` was wiped before every build, and `javac`'s exit was recorded
separately from the probe's exit.

| Tree | javac | Probe | Failing checks |
|---|---|---|---|
| **Red A** - the loader stops enforcing the kind constraint | 0 | **exit 1** | 1 - "the loader refuses an operation whose primitive does not allow the entry's kind" |
| **Red B** - a rider's conflict class is removed (Secret Police back to `CONFLICT_WON`) | 0 | **exit 1** | 1 - "the \"if you win\" rider of group_secret_police names its conflict class (CONFLICT_TYPE_INTRIGUE) and the outcome filter" |
| the fixed tree | 0 | **exit 0** | 0 - 51/51 |

---

## 4. The gate

```
RUN_TESTS=1 sh compile.sh      (from b5ccg/)
  javac 25.0.4.1, --release 8
  Summary: 38 passed, 0 failed (of 38 gate classes run)
  Tooling probes: 4 passed, 0 failed
  GATE EXIT = 0
```

Evidence: `.agent/REPORTS/evidence/GATE-B5-0052.txt`. The failing-name set
is empty, so the baseline is unchanged (AGENTS.md §4).

---

## 5. What did NOT change

- **The engine's behaviour is unchanged.** The pilot data is inert; the
  engine still does not execute effect entries, and no card resolves
  differently.
- **The v0 skeleton is untouched.** Its 22 entries over 12 cards still load,
  and the v0 gate class is unchanged.
- **The pilot's measured numbers are unchanged.** 26 of 26 group titles
  covered; 25 of 26 (96.2%) expressible without the escape hatch; the
  bespoke tail is still one card, and it still agrees with the census.
- **The tripwire's baseline is unchanged** (328 unimplemented, 81 of them
  starter-deck), because nothing in the engine's behaviour moved.

---

## 6. The remaining gap, and what WP-12 inherits

The "a conflict of type T that you won" conjunction is now expressible, but
only in the direction this row needed: the conflict class in the trigger and
the outcome in the condition. The reverse - the outcome in the trigger and
the class in the condition - is not expressible, and the v0's
`CONFLICT_WON` trigger is now unused by the shipped data. WP-12 (B5-0035, the
Enhancements pilot) will need the conjunction in some form, and this row's
condition is the mechanism it should use.

Still open, and unchanged by this row: the engine does not execute effect
entries (the strangler), the `stacking` semantics for "N instead of M" are
not pinned down, the target vocabulary cannot say "the side you support in
this conflict", and the inert schema stubs (`GroupEffectSchema`,
`EnhancementEffectSchema`) still have zero implementers (**B5-0050**).

---

## 7. The reviewer that did not happen

A DeepSeek-family reviewer was launched on 2026-10-22 to assess B5-0034
independently. **It produced no output at all**: the provider returned HTTP
429 (`free_rate_limited`, `retry_after_seconds: 7644`) and the harness gave
up after five attempts. The assistant message exists and is empty.

A failed review is not an assessment, and the row says so: `assessor_llm:
none`, and an `unassessed_revision` entry that names the attempt. A GLM-family
reviewer was launched afterwards; whether it landed is outside this report.

**What is on record instead** is the same-family advisory review (B5-0051) and
a third MiniMax session's data review, both of which had their readings
re-derived from the shipped artefacts before being acted on - the kind
violation reproduced here with the same harness and the same result (59
operations, 1 violation). That is the honest state of the row: the findings
were re-measured, and no independent assessment exists.

---

## 8. Provenance of this document

Authored by `opencode (MiniMax M3.1 Flash Free)`, version
`minimax-m3.1-flash-free-2026-10-22-03`, on 2026-10-22, from the
`opencode-minimax-m3-1-flash-free-01` session, on branch
`task/opencode-space-bunny-free-01-post-b5-0034`.

**This document is not independently assessed.** It is recorded as an
`unassessed_revision` in the front matter, per AGENTS.md §1: it is
author-written and no different-family reviewer has reviewed it. The review
that produced the findings it acts on is same-family and therefore advisory
(B5-0045).

Every measurement in §1 and §3 was taken in this session, from the shipped
data, the primitive classes' own Java sources and the gate logs - the kind
violation was reproduced here with the same harness the verification used,
and both red states were produced and measured separately.
