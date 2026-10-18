---
document:
  title: "Open-task census, gate ground truth, and why nothing can be promoted"
  status: "Observation. No authority (AGENTS.md section 3). Nothing here is promoted."
provenance:
  author_llm: {name: "UNATTRIBUTED - disputed, see provenance_dispute below", version: "unknown"}
  created_date: "2026-10-19"
  last_modified_date: "2026-10-19"
  last_modified_by_llm: {name: "opencode (Space Bunny Free)", version: "space-bunny-free"}
  assessor_llm: none
  no_self_assessment: true
  provenance_dispute:
    - "Originally named `2026-10-19-opencode-space-bunny-free-01-open-census-and-gate-truth.md`, with front matter claiming `author_llm: opencode (Space Bunny Free)`."
    - "It was NOT written by that session. While that session was running the gate, a second session created `.agent/CLAIMS/B5-0026.json` carrying the same agent id, rewrote `b5ccg/compile.sh` three seconds later, and overwrote that session's heartbeat."
    - "The true author is unknown. The identity collision cannot be resolved from the artifacts, so it is recorded as unattributed rather than guessed at."
    - "Renamed to `2026-10-19-UNATTRIBUTED-...` on the owner's instruction, so this file is not mistaken for part of the triage report beside it, which is a single-author account."
    - "Its technical findings are sound and were independently reproduced: same four-name failing set, same 27 pass / 4 fail verdict as the triage report's final run. The two sessions disagree on exactly one root-cause claim; see the triage report section 4.6."
  basis: "As claimed in the original front matter: every gate number was measured by its author in its own session rather than quoted. That claim was checked against the preserved logs in `.agent/REPORTS/evidence/` and it holds."
---

# Open-task census and gate ground truth

## 1. Census

Authoritative detector only (`ledger-query.ps1 -Status OPEN`), never a grep.

**10 OPEN, 10 UNCLAIMED, 0 suppressed-live-claim.** No scope is withheld by a
live `engine/` or `model/` claim. Heartbeat validation found 19 live files but
every one is far past the 30-minute TTL (oldest ~1185 min; the newest,
`opencode-space-bunny-free-01`, is this session). `run-queue.ps1 -DryRun` offers
all 10. `B5-2522` / `B5-2480` are absent from this fork's queue, so the
recurring live examples the census protocol warns about do not apply here.

| ID | Task (short) | Scope | Liveness |
|---|---|---|---|
| B5-0025 | Freeze window for WP-09 | docs, tooling | UNCLAIMED |
| B5-0026 | WP-09 process-tax cuts (B2) | docs, tooling | UNCLAIMED |
| B5-0027 | B5-0021 deterministic probe output | engine, tooling | UNCLAIMED |
| B5-0028 | WP-10 effect schema v0 | engine, tooling | UNCLAIMED |
| B5-0029 | WP-13 unimplemented-text tripwire | engine, tooling | UNCLAIMED |
| B5-0030 | WP-14 invariant checker + fuzz | engine, tooling | UNCLAIMED |
| B5-0032 | WP-15 VictoryRules extraction | engine, tooling | UNCLAIMED |
| B5-0033 | WP-16/17 MarkRules + CostRules | engine, tooling | UNCLAIMED |
| B5-0034 | WP-11 Groups pilot | engine, tooling | UNCLAIMED |
| B5-0035 | WP-12 Enhancements pilot | engine, tooling | UNCLAIMED |

**Ordering constraint.** B5-0025 declares a freeze window: *no other claim may
be created until this row is DONE*. It protects WP-09, which is B5-0026. So
B5-0026 is worked first and B5-0025 is released last. This serialises the queue
regardless of how many agents are free.

**Toolchain.** `javac` on `PATH` is **1.8.0_292**, a JDK that cannot accept
`--release` at all (BOOT step 2 calls this a stop condition). The staged JDK 25
named once in `AGENTS.md` section 2 resolves and reports `javac 25.0.4.1`; the
whole gate below was run with it first on `PATH`. `JAVA_HOME` is unset and
irrelevant, since the scripts invoke bare `javac`. DECISIONS records a claim that
`--release 8` was "rejected by staged JDK 25 javac 25.0.4.1" - that is inverted.
The staged JDK accepts `--release 8` fine; JDK 8 is simply first on `PATH`.

## 2. The gate is red, and it was hiding the reason

Isolated full run, staged JDK 25.0.4.1, all 31 classes executed:

```
27 passed, 4 failed (of 31 gate classes run)
Total gate time: 242608ms

b5ccg.engine.HeadlessJoinTimingProbe
b5ccg.engine.CardSetManifestChecker
b5ccg.engine.HeadlessCanonicalParityProbe
b5ccg.model.EffectCensusChecker
```

`HeadlessConformanceTest` **passes**, so the four historical baseline checks
(AES, AIJ, MINES-fleet, MINES-roundtrip) really were repaired by B5-0017. The
gate is red for four entirely different reasons.

The DECISIONS entry of 2026-10-18 asserts `failing-name-set = empty` and
promotes nine tasks FULL on that basis. **It was false when written, and it
could only ever have come out empty.** The gate loop fed its class list through a
pipe and let each `java` inherit that pipe as stdin. `HeadlessHumanSeatProbe`
reads stdin, consumed the remaining 27 class names, and the loop stopped after
4. The script then exited through its own "loop did not complete" guard. A gate
that runs 4 of 31 classes has no failing-name set to report, so "empty" was the
only verdict it was capable of producing.

Three runs on one tree: unfixed, 4 rows recorded; script-level `< /dev/null`,
31 rows; per-`java` `< /dev/null`, 31 rows. Fix landed on the `java` line
(tooling scope) so no class can consume the loop's input regardless of how the
script is invoked.

This matters beyond bookkeeping: `AGENTS.md` section 4 makes promotion depend on
the gate reporting its own verdict, so this defect silently voided the gate for
every promotion that cited it.

**Caveat on method.** A second, unclaimed agent was running the gate
concurrently in this shared working tree (`run_gate4.sh`, sharing `out/` and
`out_gate/`). One of my three runs was corrupted by it - 4 rows recorded while 31
logs were written, which is the fingerprint of a clobbered table. The set above is
the isolated run, taken with no competing build. The build lock behaved correctly
(it refused my run rather than clobbering). Anyone reading gate output here
should confirm no other build is running first.

## 3. Why no row was promoted

`AGENTS.md` section 4: promotion requires the verification gate to exit 0. It
exits 1. There is no recorded baseline clause to fall back on - the B5-0017
close-out deleted it, correctly, on the grounds that the baseline was empty. So
with four failures and no sanctioned exception, **no row in the queue can be
promoted**, and the nine that were promoted on the false claim have already been
returned to OPEN.

Closing the gate needs work that no OPEN row covers. In priority order:

### 3.1 Two of four share one root cause (cheapest)

`HeadlessCanonicalParityProbe` (15/17) and `EffectCensusChecker` (24/25) both
fail on the `DAMAGE_ON_ATTACK` registry at `CardEffects.java:1573-1585`. It holds
seven entries; four are dead:

```java
DAMAGE_ON_ATTACK.add("en_h_mines");          // 1575 - dead, not a card
DAMAGE_ON_ATTACK.add("en_h_energy_mines");   // 1576 - dead, not a card
DAMAGE_ON_ATTACK.add("enh_mines");           // 1579 - real
DAMAGE_ON_ATTACK.add("de_enh_mines");        // 1580 - redundant
DAMAGE_ON_ATTACK.add("enh_energy_mines");    // 1581 - real
DAMAGE_ON_ATTACK.add("de_enh_energy_mines"); // 1582 - redundant
DAMAGE_ON_ATTACK.add("enh_mines_rt");        // 1584 - B5-0556 fixture, exempt
```

`en_h_mines` and `en_h_energy_mines` occur nowhere else in `src` and nowhere in
`resources`. The shipped ids are the `enh_`/`de_enh_` forms
(`premiere.json:3742`, `3997`; `deluxe.json:2991`, `3212`). The two `de_`
entries are redundant because `lookupSet` canonicalises the `de_` prefix away, so
`damageOnAttack("de_enh_mines")` still resolves via `enh_mines`.

The javadoc at lines 1568-1569 states the `en_h_*` forms "are the shipped
data". That is the exact opposite of the shipped data, and it is what let two dead
literals pass review twice.

Removing all four is safe on the evidence: the only production reader is
`RulesEngine:1839`, and the only test callers pass `enh_*`/`de_enh_*` forms that
`lookupSet` still resolves. **This is the one change that turns two red classes
green at once, and it is a small, well-bounded engine edit.**

### 3.2 `CardSetManifestChecker` - 383 violations, stale invariant

Not 24; the log says **383** "Title appears in multiple sets" findings. Its
invariant is "every title appears in exactly one set"
(`CardSetManifestChecker.java:13, 61-68, 104-106`). The shipped data is a
two-printing model: all 383 DELUXE cards are reprints of PREMIERE titles with
distinct `de_`-prefixed ids and the same `imageKey`. Ids are unique, so this is
not duplicate data.

`CardSetManifestCrossCheck` encodes the current semantics and passes; its comment
at lines 212-217 says cross-set `imageKey` reuse "is the expected card-pool
pattern ... and must NOT be flagged". `DeckLoader:183-188` cites a human ruling
of 2026-09-21 establishing the reprint-collapse pool rule. The B5-0003 report
already recorded this exact contradiction and deferred it to A1b.

So the checker is stale, the data is right, and the fix is to retire or re-scope
the rule. **Not a data edit, and not something to guess at** - it changes what the
checker asserts about the card pool.

### 3.3 `HeadlessJoinTimingProbe` - two failures, only one needing a ruling

**This corrects section 3.3 as first written.** I initially recorded this class
as "a failed assertion, not a crash". Reading the log end to end shows **two**
independent failures.

**(a) S3 - a failed assertion, needs an owner ruling.**

```
[FAIL] S3: after resolution, joiner still NOT a participant (AI auto-join removed)
```

The probe (B5-4160) asserts the joiner must **not** auto-join.
`GameController.openConflictParticipation` (B5-0343) has the AI choose its own
side (+1 support / -1 oppose / 0 abstain) and commits when `side != 0`, which is
auto-join. Both cannot hold. An in-flight guard for this was already tried and
reverted: it did not fix the assertion, left the real hazard on a non-empty list
returning another seat's `AIPlayer`, and added two NPE risks at
`GameController:516` and `:616`, neither of which null-checks.

This is a contradiction between two of the repo's own recorded decisions. **It
needs a human ruling, and I have not made one.**

**(b) S4 - a crash, and it is purely mechanical.** All three S4 checks pass, then
the probe dies:

```
Exception in thread "main" java.lang.RuntimeException
Caused by: java.lang.IndexOutOfBoundsException: Index 0 out of bounds for length 0
	at b5ccg.engine.GameController.getAI(GameController.java:1912)
	at b5ccg.engine.GameController.openConflictParticipation(GameController.java:1261)
	at b5ccg.engine.GameController.resolveQueuedConflict(GameController.java:1368)
```

`getAI` ends in an unguarded `return aiPlayers.get(0);`. S4 registers no
`AIPlayer`, so a non-human player with no AI match indexes an empty list. **A
null guard fixes this. It is engine work, not a rules question.**

Two consequences worth stating plainly:

* Every historical report that attributes this `IndexOutOfBoundsException` to
  **S3** is **misattributed**. On this tree S3 asserts and **S4** crashes.
* The `if (ai == null) continue;` guard that was reverted before this pass was
  judged **only against S3**, which it does not fix - and so was discarded. It
  would have fixed (b). The revert reasoning was aimed at the wrong target.

A correct fix must handle both cases: the empty list, and the non-empty list with
no match, which today silently returns a **different seat's** `AIPlayer`. That
second case is a live hazard in its own right.

## 4. What was changed, and what was not

Claimed `B5-0026` (docs, tooling) for the duration; released. One writer, so no
independent assessor's pass exists for anything here.

| Path | Change |
|---|---|
| `b5ccg/compile.sh` | `< /dev/null` on the gate `java` line, with the measurement that justifies it |
| `.agent/TASK_LEDGER.md` | B5-0004 trailing delimiter restored; B5-0014 `||` double lead pipe removed; session note appended below the table |
| `docs/DECISIONS.md` | New correction entry recording the true failing set and withdrawing the false verdict |

Verified after the edits: `lint-ledger.ps1` PASS (33 rows), `run-dup-census.ps1`
PASS, `check-citations.ps1` PASS, `lint-encoding.ps1` still exactly 20 hits (no
new damage introduced), gate exits 1 with the 4-name set above.

**Not changed, deliberately:**

* **B5-0026 stays OPEN.** Its acceptance requires the B2 targets met *and
  measured over a two-week window*. Measured today, the targets are already met -
  boot file 4295 B (target under 5 KB), live ledger 15662 B, `investigations/`
  0 files, 2 pattern namespaces, no root `.md` debris. The window has not
  elapsed, so marking it DONE would be a false claim. Stopped this item only, per
  `AGENTS.md` section 4.
* **B5-0025 stays OPEN**, so the freeze is not released and no other row may be
  claimed until the four red classes are dealt with.
* **The lossy `U+FFFD` at DECISIONS.md 508-518.** The original characters are
  gone; the intended text cannot be inferred. Repairing them would be guessing at
  a governance record.
* **The 20 mojibake hits** from `lint-encoding.ps1` across HANDOFF.md, proposals
  and RULEBOOK_PIN.md - same reason, and a prior session made this class of damage
  worse by "fixing" it.
* **STUB/PARTIAL/FULL vocabulary** used to grade rows throughout DECISIONS.md and
  undefined in AGENTS.md. Five of the "FULL" deliverables are bare interfaces
  with no implementer and two are `return true;`, so the vocabulary is load
  bearing and undefined at the same time.

## 5. Recommended next steps, in order

1. **Rule on S3** (`HeadlessJoinTimingProbe`). Everything else is mechanical;
   this is not, and it is the one item no agent should decide.
2. **Open one engine row** to clear `DAMAGE_ON_ATTACK` (section 3.1). Two red
   classes for one four-line deletion plus a javadoc correction.
3. **Open one engine row** for the `getAI` crash (section 3.3b) - guard the
   empty-list case *and* the non-empty no-match case, so a non-human player with
   no registered AI is skipped rather than handed a different seat's `AIPlayer`.
4. **Open one engine row** to retire the stale title-uniqueness rule in
   `CardSetManifestChecker` (section 3.2), consistent with the CrossCheck and the
   2026-09-21 pool ruling.
5. Once the gate exits 0, re-derive B5-0026's two-week measurement and release
   B5-0025; only then does the remaining queue unblock.

Rows 2 and 3 are mechanical, evidence-backed, and need no ruling. Row 4 is
well-evidenced but changes what the gate asserts about the card pool, so it
wants your explicit agreement even though the evidence points one way. Row 1 is
the genuine blocker.

Until those land, no promotion in `docs/DECISIONS.md` should be treated as
supported - including the nine FULL entries.