---
document:
  title: "B5-0039 - Owner ruling: S3 wins, B5-0343 AI auto-commit removed"
  status: "Report. Records an owner ruling and the change it authorised; confers no authority on its own (AGENTS.md section 4)."
provenance:
  author_llm: {name: "opencode (Space Bunny Free)", version: "space-bunny-free"}
  created_date: "2026-10-19"
  last_modified_date: "2026-10-19"
  last_modified_by_llm: {name: "opencode (Space Bunny Free)", version: "space-bunny-free"}
  assessor_llm: none
  no_self_assessment: true
  basis: "Owner ruling given 2026-10-19 after the rulebook reading was presented and its citations quoted. Rule citations were read from the pinned rulebook in this session; no behaviour figure in this report is quoted from another document."
---

# B5-0039 - AI auto-join removed (owner ruling)

Scope `engine,docs`. One file: `b5ccg/src/b5ccg/engine/GameController.java`.

## 1. The contradiction

Two pieces of the repo asserted opposite things about the same behaviour:

* **B5-4160** - `HeadlessJoinTimingProbe` S3 asserted the joiner must NOT become
  a participant after resolution, naming the reason: "AI auto-join removed".
* **B5-0343** - `GameController.openConflictParticipation` called
  `decideJoinSide` at conflict initiation and committed a character whenever the
  side was non-zero.

Both cannot hold, and neither was authoritative, so neither could simply be
trusted. This blocked the last red gate class.

## 2. The rulebook reading

Participation is a **player action**: rotating a card to support or oppose,
taken during the Action round.

* `RB:IV/conflict-cards` L574 - "During the Action round of each turn,
  characters may rotate to either 'support' or 'oppose' any conflict currently
  in play."
* `RB:V` L685 - "Characters and fleets (and, in some cases, locations) may
  rotate to either support or oppose a conflict that has been initiated."
* `RB:VI/sample-conflict` L755 - participation is a deliberate, cost-bearing
  choice: "He does not want to support the conflict with Urza Jaddo (he wants to
  keep him as a threat to others)".
* `RB:VI/sample-conflict` L757 - declining is legal: "Dave goes next, but
  passes, claiming no interest in the conflict."
* `RB:III/conflict-round` L366-375 covers declaration and reveal only.

The consequence argument is what carries this, not the incidental absence of the
word "join" from the rulebook. A rotated card can be attacked (`RB:V` L691) and
discarded, so participation has a real price. The controller rotating a card at
initiation, with no action taken by that seat, is not a reading of any of the
above.

## 3. The ruling

**S3 wins.** B5-4160 was right and B5-0343 was unauthorised. The owner ruled
this on 2026-10-19 after the above was presented with citations.

## 4. The change

The auto-commit loop was removed from `openConflictParticipation`. Nothing else
in that method changed: the human join window, `enforceMandatoryParticipation`,
and the human attack window are all untouched.

The AI still participates - through the ordinary action pipeline, which was
already there and is not new:

* `AIPlayer.java:631-635` offers `GameAction.joinSupport()` /
  `joinOppose()` from `decideJoinSide` when a conflict is live;
* `AIPlayer.java:1165-1169` executes them in `chooseAction`.

So the removed loop was a **duplicate mechanism, not the source** of AI
participation. That was the critical thing to establish before touching it: had
`AIPlayer` not been able to emit join actions, removal would have made AI
participation impossible rather than merely deliberate.

## 5. Verification

```
HeadlessJoinTimingProbe: 20 checks, 0 failures.  EXIT=0
  [PASS] S3: joiner NOT auto-collected during resolution setup (not yet resolved)
  [PASS] S3: after resolution, joiner still NOT a participant (AI auto-join removed)
  [PASS] S5: joiner joined as support during ACTION phase

HeadlessConformanceTest: CONFORMANCE SUITE PASSED (904 checks)  EXIT=0
```

**S5 is the load-bearing check here.** It asserts the joiner *does* join during
the ACTION phase, and it still passes. That is the evidence that the ruling was
implemented without breaking AI participation: the AI now joins by acting, not
by being collected.

Before: `20 checks, 1 failures` with `[FAIL] S3`.

## 6. Effect on the gate

**Measured, sole session, staged JDK 25.0.4.1, `--release 8`, lock released
clean:**

```
Verification passed (all 31 gate classes).
Summary: 31 passed, 0 failed (of 31 gate classes run)
Total gate time: 239969ms
GATE_EXIT=0
```

31 distinct `PASS` verdict rows appear in the results table, matching the
summary count. Preserved at `.agent/REPORTS/evidence/GATE-B5-0039.txt`.

Session progression, all sole-session runs on the same tree:

| After | Result |
|---|---|
| triage baseline | 27 pass / 4 fail |
| B5-0036 + B5-0037 | 29 pass / 2 fail |
| B5-0038 | 30 pass / 1 fail |
| B5-0039 | **31 pass / 0 fail** |

## 7. Scope note

B5-0025's freeze window nominally holds `docs,tooling`; this row is
`engine,docs`. The docs portion is a single DECISIONS entry citing the rule IDs
above, which AGENTS.md section 4 requires for any change that interprets rules.
No governance text was altered.