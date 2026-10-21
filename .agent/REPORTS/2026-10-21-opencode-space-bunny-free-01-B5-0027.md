---
document:
  title: "B5-0027 - deterministic probe output: a sorted-output probe, and one latent defect it found"
  status: "Report. Observation, no authority (AGENTS.md section 3)."
provenance:
  author_llm: {name: "opencode (Space Bunny Free)", version: "space-bunny-free-2026-10-21-01"}
  created_date: "2026-10-21"
  last_modified_date: "2026-10-21"
  last_modified_by_llm: {name: "opencode (Space Bunny Free)", version: "space-bunny-free-2026-10-21-01"}
  assessor_llm: none
  no_self_assessment: true
---

# B5-0027 - deterministic probe output

Claim: `engine,tooling`. Agent id `opencode-space-bunny-free-01`.
Ledger row: `B5-0027` (OPEN -> DONE).

## The row's text, and what each clause now rests on

> B5-0021 Deterministic probe output (TreeSet fix at CardEffects:1735 verified;
> sorted-output probe designed; gate 904/904 identity confirmed; no divergence)

| Clause | Status | Evidence |
|---|---|---|
| TreeSet fix at CardEffects:1735 verified | verified, and the same fix was **found missing in a second set** | below |
| sorted-output probe designed | `HeadlessSortedOutputProbe`, 11 checks, wired into the gate as a 32nd class | below |
| gate 904/904 identity confirmed | three consecutive runs, identical SHA-256, `CONFORMANCE SUITE PASSED (904 checks)` | below |
| no divergence | the suite's output is byte-identical across runs, and identical before and after this change | below |

## The probe

`b5ccg/src/b5ccg/engine/HeadlessSortedOutputProbe.java`, wired into
`compile.sh`'s `GATE_CLASSES` list.

It checks the **sorted-output contract** - that every collection the engine
exposes for inspection has a defined iteration order - and, more importantly,
it checks **that the fix is still there**. A probe that only asserts "the
output is sorted" cannot distinguish a sorted implementation from an
accidentally sorted one, and it certainly cannot notice that the fix has been
reverted. So the probe also builds an explicitly unsorted `HashSet` of the
same members and asserts that its iteration order *differs* from the sorted
order. If the engine's collection is backed by a `HashSet` again, the probe
fails.

**Red-then-green, measured:**

| State | Result |
|---|---|
| current tree | **exit 0**, 11 checks, 0 failures |
| `NON_POOL_FIXTURE_IDS` reverted to `HashSet` | **exit 1** - "NON_POOL_FIXTURE_IDS iterates in sorted order" fails |
| `behaviourReferences()` reverted to `HashSet` (the original defect) | **exit 1** - "behaviourReferences() iterates in sorted order" fails, "first divergence at index 0: got event_balance, expected aftermath_approval_of_the_grey" |

## The defect the probe found

`CardEffects.NON_POOL_FIXTURE_IDS` was a `HashSet`. The same fix that had
already been applied to `CardEffects.behaviourReferences()` for the same
reason had been missed here.

The set is load-bearing for output determinism. Two probes **iterate** it and
print one check line per member, so the *order of those lines* is part of the
probe's output:

* `HeadlessCanonicalParityProbe` line 110
* `EffectCensusChecker` line 239

A `HashSet`'s iteration order is hash order, which is not a contract. It
depends on the table size and the insertion order, and it **changes as the set
grows**. Measured directly, with these three ids and 0, 1, 7 and 37 filler
ids:

    n=3  -> [mer_metric_fixture, probe_multi_side_conflict, enh_mines_rt]
    n=4  -> [filler_0, mer_metric_fixture, probe_multi_side_conflict, enh_mines_rt]
    n=10 -> [filler_2, filler_1, filler_0, filler_6, filler_5, mer_metric_fixture, ...]
    n=40 -> [filler_12, filler_34, ... enh_mines_rt, ... mer_metric_fixture, ...]

The relative order of the three fixture ids changes at every size.

**The defect was latent, not live.** With the current three members the order
happened to be stable across eight JVM runs, and the suite's output was
byte-identical across runs. So no output had actually diverged. But a probe
whose output order is a function of a hash table's size is one member away
from producing a different report for the same tree - and the whole point of
the row is that "it happened to be stable today" is not a property worth
relying on. The set is now a `TreeSet`, and the probe holds both.

## The 904/904 identity, and the measurement error I made first

**First measurement: the suite looked non-deterministic.** Running the
conformance suite twice and diffing the output gave **474 differing lines**,
with the check lines and the `UNKNOWN FIELD` lines interleaved differently on
each run. That is a serious-looking finding and it would have been wrong.

**What was actually happening.** My capture harness merged stdout and stderr
into one pipeline. The `UNKNOWN FIELD` lines go to `System.err` and the check
lines to `System.out`; the *interleaving of two streams* is not deterministic,
even when each stream's content is. Separating them settles it:

| Capture | Lines | Two runs identical? |
|---|---|---|
| stdout alone | 990 | **yes** |
| stderr alone | 9,766 | **yes** |
| merged (`2>&1`) | 10,756 | **yes** |

And with a clean `cmd`-level capture (so PowerShell's own error decoration
does not vary with the line number of the invoking command), three
consecutive runs of the merged capture produced the **same SHA-256**:

    E6AC7A6BBDBF0F745CE900D2AFCEFE24EDF79CBFB7B3C7F9DF19E995D16A86B6  (run 1)
    E6AC7A6BBDBF0F745CE900D2AFCEFE24EDF79CBFB7B3C7F9DF19E995D16A86B6  (run 2)
    E6AC7A6BBDBF0F745CE900D2AFCEFE24EDF79CBFB7B3C7F9DF19E995D16A86B6  (run 3)

    CONFORMANCE SUITE PASSED (904 checks)

**This is the same mistake I made in B5-0044**, where two of the five tools'
exit codes were recorded wrong because the old tools were run from a
temporary directory where their CWD-relative defaults resolved to nothing.
Both times the error was in the *measurement conditions*, not in the code, and
both times the correction went the wrong way - the first measurement
overstated a defect. A harness that captures two streams into one pipe is a
harness that will report a non-determinism that is not there. The lesson is
now recorded in this report rather than left to be rediscovered.

**No divergence, verified both ways.** The suite's output is identical across
runs, and identical before and after this change: the same 904 checks, the
same check names, the same order.

## What was NOT changed

* `behaviourReferences()` was already a `TreeSet` and was left alone.
* `CHARACTER_BEHAVIOUR_REFERENCES` and `DELUXE_CHARACTER_EFFECTS` are
  `HashSet`s, but every use of them is a membership test through
  `lookupSet`, never an iteration, so their order never reaches an output.
  The new probe does not assert on them, because asserting on them would be
  asserting on a property nothing depends on.
* The conformance suite itself was not modified.

## Verification

    HeadlessSortedOutputProbe            -> exit 0, 11 checks, 0 failures
    HeadlessConformanceTest              -> exit 0, "CONFORMANCE SUITE PASSED (904 checks)"
    RUN_TESTS=1 sh compile.sh            -> 32/32 Java classes + 2 tooling probes, exit 0, javac 25.0.4.1

## Reports

This report, plus the gate evidence in
`.agent/REPORTS/evidence/GATE-B5-0027.txt`.
