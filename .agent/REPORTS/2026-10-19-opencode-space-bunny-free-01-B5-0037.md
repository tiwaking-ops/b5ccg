---
document:
  title: "B5-0037 DONE - getAI returns null on no match; wrong-seat AIPlayer and IndexOutOfBoundsException eliminated"
  status: "Report. Proposes a change for owner verification; confers no authority on its own (AGENTS.md section 4)."
provenance:
  author_llm: {name: "opencode (Space Bunny Free)", version: "space-bunny-free"}
  created_date: "2026-10-19"
  last_modified_date: "2026-10-19"
  last_modified_by_llm: {name: "opencode (Space Bunny Free)", version: "space-bunny-free"}
  assessor_llm: none
  no_self_assessment: true
  basis: "Every number below was measured in this session on this tree. The 'before' figure is quoted from .agent/REPORTS/evidence/GATE-FINAL.txt; the 'after' figure is from the run in section 5."
---

# B5-0037 - getAI no-match guard

Scope `engine`. One file: `b5ccg/src/b5ccg/engine/GameController.java`.

## 1. What was wrong

`getAI(Player p)` searched `aiPlayers` for a wrapper whose player is `p`, and
if none matched, fell through to:

```java
return aiPlayers.get(0);
```

Wrong twice over:

* **EMPTY list** - throws `IndexOutOfBoundsException`. This is what killed
  `HeadlessJoinTimingProbe`, after its S3 assertion had already failed.
* **NON-EMPTY list, no match for `p`** - returns `aiPlayers.get(0)`, the wrapper
  belonging to a **different seat**. This is worse than the crash: it silently
  makes one player's decisions on another's behalf, with no error and no log.

Both were observed. The second is a latent wrong-player bug independent of the
crash and would have produced wrong games rather than failures.

## 2. The change

`getAI` now returns `null` when there is no match. All three call sites guard:

| Line | Site | Guard |
|---|---|---|
| 519 | `runTurn` | `ai == null` -> the seat passes |
| 625 | declaration pass | `declaringAi == null` -> seat skipped |
| 1272 | `openConflictParticipation` | `ai == null` -> seat abstains |

Every guard abstains rather than borrowing another seat's AI. That is the whole
point: the old fallback existed to avoid a null, and it avoided it by acting as
the wrong player.

A grep confirms those are the only three call sites (the fourth hit in
`HeadlessConformanceTest` is a comment).

## 3. Behaviour preserved where it was correct

Where a seat *did* have a registered `AIPlayer`, the loop matches exactly as
before and nothing changes. `HeadlessConformanceTest` - 904 checks, including
every AI-driven conflict scenario - passes unchanged, and
`HeadlessJoinTimingProbe` drops from a crash to its single genuine assertion
failure with the same 20 checks.

## 4. Fails without the change

`.agent/REPORTS/evidence/GATE-FINAL.txt`, recorded before this edit:

```
Caused by: java.lang.IndexOutOfBoundsException: Index 0 out of bounds for length 0
	at b5ccg.engine.GameController.getAI(GameController.java:1912)
```

The wrong-seat return has no preserved-run demonstration, because nothing
asserted against it. That is exactly why it survived: no probe covered it. Its
absence of evidence is not evidence of correctness, and it is called out here so
the next reader knows that leg rests on code reading, not on a failing test.

## 5. After

```
HeadlessJoinTimingProbe: IndexOutOfBoundsException count 0   (was: fatal)
  B5-4160 join-timing probe: 20 checks, 1 failures.
HeadlessConformanceTest: 904 checks PASSED  EXIT=0
```

Full gate, sole session, staged JDK 25.0.4.1, `--release 8`, lock released
clean:

```
Summary: 29 passed, 2 failed (of 31 gate classes run)
Total gate time: 237255ms
  - b5ccg.engine.HeadlessJoinTimingProbe
  - b5ccg.engine.CardSetManifestChecker
```

Preserved at `.agent/REPORTS/evidence/GATE-B5-0036-0037.txt`. This is **29/2,
down from 27/4** - B5-0036 cleared two classes and this cleared the crash.

Both remaining failures are being worked: `CardSetManifestChecker` is a stale
checker invariant, and `HeadlessJoinTimingProbe` is down to the single S3
assertion, which is an owner ruling on whether AI auto-join was removed.

## 6. Note on a prior misjudgement

An earlier guard for this same crash was reverted earlier in this session after
being judged "a net negative" against the S3 assertion. That judgement was
wrong in effect: the guard did not fix S3, but it did prevent this crash, and
the revert discarded a real fix for the wrong reason. The version landed here
fixes both halves - the crash and the wrong-seat return - rather than only the
empty-list case.