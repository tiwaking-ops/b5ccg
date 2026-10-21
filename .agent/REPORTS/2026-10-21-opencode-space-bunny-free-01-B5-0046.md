---
document:
  title: "B5-0046 - calibration corpus: 33 cases, three independent scorers"
  status: "Report. Observation, no authority (AGENTS.md section 3)."
provenance:
  author_llm: {name: "opencode (Space Bunny Free)", version: "space-bunny-free-2026-10-21-01"}
  created_date: "2026-10-21"
  last_modified_date: "2026-10-21"
  last_modified_by_llm: {name: "opencode (Space Bunny Free)", version: "space-bunny-free-2026-10-21-01"}
  assessor_llm: none
  no_self_assessment: true
---

# B5-0046 - calibration corpus

Claim: `tooling,docs`. Agent id `opencode-space-bunny-free-01`.
Ledger row: `B5-0046` (OPEN -> DONE).

## The row's acceptance, and how each clause was met

> Calibration corpus built from this repo own recorded failures rather than a
> synthetic short test. Ground truth already exists: eight withdrawn stub files
> promoted to FULL by claude-code-b5ccg-02 across seven rows, twelve
> flip-ledger.ps1 defects, five parameter-absorbing tools. Acceptance: every
> case has a ground-truth expected answer; two agents score the corpus
> independently and the scores agree; no case depends on a model having read
> this repo history; a DECISIONS entry states what the corpus can and cannot
> measure

| Clause | Met by | Evidence |
|---|---|---|
| built from this repo's own recorded failures, not synthetic | 25 defect cases drawn from the three named sources, plus 8 control cases drawn from this repo's own fixes for those same defects | `docs/CALIBRATION_CORPUS.md` |
| every case has a ground-truth expected answer | the answer key **is** the corpus's family table, so key and corpus cannot drift; all 33 cases have exactly one defensible answer | the corpus file; `score-calibration-corpus.ps1` |
| two agents score the corpus independently and the scores agree | **three** agents from three different model families, none shown the key and none shown another scorer's answers | below |
| no case depends on a model having read this repo history | the scorer package is generated mechanically with every answer field stripped, and the leak check is automated | `build-scorer-package.ps1`; below |
| a DECISIONS entry states what the corpus can and cannot measure | `docs/DECISIONS.md`, "The calibration corpus, and what it can and cannot measure (B5-0046)" | the DECISIONS entry |

## The corpus

`docs/CALIBRATION_CORPUS.md` - 33 cases:

| Family | Cases | All are | Source |
|---|---|---|---|
| A. guards that are dead or mis-aimed | 12 | DEFECTIVE | the F-list (F-1..F-12) in `reply-claude-...-v2-1.md` and `-v2-2.md` |
| B. stubs that assert nothing | 8 | DEFECTIVE | the eight withdrawn stub files, across seven ledger rows |
| C. tools that absorb what they should reject | 5 | DEFECTIVE | the five tools that lacked `[CmdletBinding()]` |
| D. the fixes for those same defects | 8 | SOUND | this repository's own repairs, as shipped |

Family D exists because a corpus in which every answer is DEFECTIVE measures
nothing: a scorer that answers "DEFECTIVE" to all 33 cases scores 33/33 and
learns nothing.

Every case is a self-contained snippet, a stated **required behaviour**, and
one question of a single shape - *does the code achieve the required
behaviour?* - so the answer is always yes-means-SOUND, no-means-DEFECTIVE.

## The scores

Three scorers, three different model families, all free:

| Scorer | Model | Family | Matches the key |
|---|---|---|---|
| S1 | `nvidia/openai/gpt-oss-20b` | gpt | 22 / 33 |
| S2 | `nvidia/deepseek-ai/deepseek-v4.1-flash` | deepseek | **33 / 33** |
| S3 | `opencode/nemotron-3.5-super-vl-free` | nemotron | **33 / 33** |

* Unanimous agreement: **22 / 33**
* Majority vote against the key: **33 / 33**
* Cases where no scorer matched the key: **0**
* Cases where all three matched: 22

**S2 and S3 produced identical verdicts on all 33 cases.** S1 differed on 11,
and every one of its differences is the same error in the same direction: it
answered SOUND where the required behaviour was not achieved. Not one of S1's
answers was a false DEFECTIVE. The disagreement is a capability difference on
this instrument, not a disagreement about any case - which is the useful kind
of disagreement to have, because it measures something.

The 11 disputed cases: A-2, A-10, B-1, B-2, B-3, B-4, B-5, C-1, C-2, C-4,
C-5. Every one of them is a case where the code is *syntactically fine* and
the defect is in what the code does relative to the stated requirement.

## The disputed cases were adjudicated by execution

Not by argument. Before the key was accepted, each disputed case was run:

* **A-2** - `if (-not $Id -match '^B5-[0-9]{4}$')` with `-Id 'TOTAL-NONSENSE'`
  exits **0** and prints "ACCEPTED". The malformed id is *not* rejected. The
  operator parses as `(-not $Id) -match ...`, so the test is applied to the
  literal text "False". S1 is wrong; the key is right.
* **C-1/C-2/C-4/C-5** - a `param()`-only script invoked with an unknown
  named argument exits **0** and uses the default. A control with
  `[CmdletBinding()]` on the same code exits **1** and names the parameter.
  S1 is wrong; the key is right.
* **A-10** - `-Filter "B5-0202*"` matches `B5-0202c.json`. S1 is wrong; the
  key is right.
* **B-1..B-5** - the type has zero implementers and zero references, verified
  mechanically against the source tree. The key is right.

So the key is not an opinion about what a good reviewer would say. It is what
the code does.

## No case depends on this repository's history

The scorer package is generated mechanically by
`.agent/tools/build-scorer-package.ps1`, which strips the answer key and
rewrites the provenance. The leak check is automated and was run on the
package that the scorers actually read:

    expect:  0    ground_truth_source:  0    F-1..F-12:  0
    withdrawn: 0   claude-code-b5ccg:   0   ground truth: 0
    2026-10-19: 0  reply-claude:         0   all DEFECTIVE: 0

Two leaks were found and fixed during the build, and both are worth
recording because they were invisible to reading the file:

* **The case titles were the answer.** The first version titled A-1 "A guard
  that reads a path that does not exist" and B-1 "An interface with no
  implementer". A scorer could answer from the title alone, and S1's
  behaviour on the second version is consistent with a scorer that had done
  so. All 25 titles are now `Case A-1` and so on.
* **The family table was the answer.** It said how many cases in each family
  were DEFECTIVE and how many SOUND. The table is now stripped from the
  package by line range, and the family headers no longer say "all
  DEFECTIVE".

## What the corpus can and cannot measure

Stated at length in the DECISIONS entry. In short: it can measure whether a
model can tell that a guard is dead, that a file which asserts nothing has
been recorded as work, that a tool discarded what the caller asked for, and
that a repair differs from the defect it repairs. It cannot measure whether a
model would have found the defect in the original file, it cannot measure
general code review, it cannot measure severity or priority, and it says
nothing about the game.

## One methodological finding

Building the corpus took three attempts, and the first two were wrong in
instructive ways. The first put the defect in the case title. The second
asked descriptive questions ("what happens?") instead of stating a required
behaviour, and a scorer duly answered the question instead of judging the
code. The third states the required behaviour explicitly - and S1's score went
from 18/25 to 22/33 on the *same underlying defects*, purely because the
instrument was better specified.

A calibration corpus measures the instrument as much as the model. The
instrument has to be debugged the way a test suite is: by watching it
produce the wrong answer and asking why.

## Verification

    lint-ledger.ps1      -> exit 0   "=== LEDGER LINT PASS (45 rows in table) ==="
    check-citations.ps1  -> exit 0
    run-dup-census.ps1   -> exit 0   "duplicate-ID census: 0 duplicate task IDs"
    lint-encoding.ps1    -> exit 1   BAD count = 20  (the pre-existing repo baseline)
    RUN_TESTS=1 sh compile.sh -> 31/31 Java classes + 2 tooling probes, exit 0, javac 25.0.4.1

## Reports

`docs/CALIBRATION_CORPUS.md` (the corpus and the key), `docs/DECISIONS.md`
(what it can and cannot measure), and the evidence in
`.agent/REPORTS/evidence/`: `CALIB-S1-gpt.txt`, `CALIB-S2-deepseek.txt`,
`CALIB-S3-nemotron.txt`, `CALIB-SCORE.txt`.
