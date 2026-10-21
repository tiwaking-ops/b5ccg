---
document:
  title: "B5-0051 - verification and close-out of B5-0034 (WP-11 Groups pilot)"
  status: "Report (observation, no authority)"
provenance:
  author_llm: {name: "opencode (MiniMax M3.1 Flash Free)", version: "minimax-m3.1-flash-free-2026-10-22-02"}
  created_date: "2026-10-22"
  last_modified_date: "2026-10-22"
  last_modified_by_llm: {name: "opencode (MiniMax M3.1 Flash Free)", version: "minimax-m3.1-flash-free-2026-10-22-02"}
  assessor_llm: none
  unassessed_revision:
    - {author_llm: {name: "opencode (MiniMax M3.1 Flash Free)", version: "minimax-m3.1-flash-free-2026-10-22-02"}, date: "2026-10-22", change: "initial verification report for B5-0051", note: "authored by this agent and NOT independently assessed; no assessor_llm entry is claimed for this pass. A different-family assessment of this row is outstanding."}
---

# B5-0051 - verification and close-out of B5-0034 (WP-11, the Groups pilot)

**Row.** B5-0051, opened by this agent for the verification pass. Scope
`docs`. B5-0034 itself was already `DONE` in the ledger before this row
existed; the verification is recorded against its own row so that the claim
is never a phantom (AGENTS.md section 5).

**What this session was asked to do.** "Claim and complete B5-0034." When
this session booted, B5-0034 was already claimed by a live sibling session
(`opencode-minimax-m3-1-flash-free-01`, the same model family), which was
mid-implementation. AGENTS.md section 5 bars a second writer from touching
a scope a live claim holds, and that claim covered `engine,tooling,
resources,docs` - every scope the row needed. The owner was asked and ruled
(2026-10-22): **verify and close out, do not steal the claim.** This report
is that verification.

**Verdict.** The row's acceptance is met on every clause that can be
measured: the pilot covers all 26 group titles, 25 of 26 (96.2%) need no
escape hatch, the engine's behaviour is unchanged, the gate is green, and
the red state is reproducible. **Three things are wrong, and two of them are
in the row's own artefacts:**

1. **One number in the row's report is wrong, in the direction that flatters
   the row.** Section 3 says "19 of the 26 cards (73%) could be expressed"
   and concludes that the v0 vocabulary would already have passed the
   pilot. The card-level answer is **16 of 26 (61.5%)**, so the v0
   vocabulary would have **failed** it. The report's own commit message and
   its DECISIONS entry already carry the correct figure; only the report
   body carries the wrong one. Corrected here and in a DECISIONS entry.
2. **Four entries state a broader rule than their card prints** - "Spin
   Doctors" carries no Diplomacy restriction, and three "if you win"
   clauses drop the conflict class that scopes them - and the reason is a
   **fifth vocabulary gap** the pilot did not close: "a conflict of type T
   that you won" is not expressible.
3. **The primitive registry's declared kind constraint is never enforced**,
   and one shipped entry already violates it. A3 principle 4 is "fail
   closed"; this constraint fails open.

Finding 2 is filed as row **B5-0052**, not fixed in this row: the fix needs
`engine` and `resources` scope, and this row's scope is `docs`. The pilot
data is inert - the engine does not execute effect entries - so the defect
has no runtime effect today.

**This report is not independently assessed.** It is recorded as an
`unassessed_revision` per AGENTS.md section 1. The verifier and the author
of the work share a model family (MiniMax), so a same-family review is
advisory and is not recorded as an `assessor_llm` entry. A different-family
assessment of B5-0034 is still outstanding.

---

## 1. What was verified, and how

The author shipped the work in commit `0f8960f` ("B5-0034: WP-11 Groups
pilot - 26 titles, one bespoke card, four vocabulary gaps"). Verification
means re-deriving the row's claims from the artefacts rather than reading
the report's own summary of them.

| Claim | How it was checked | Result |
|---|---|---|
| every group title has an effect entry | the pilot document parsed independently of the probe; the 26 canonical GROUP ids read out of the two card files | **26 / 26** confirmed, 35 entries, 9 cards carrying two entries |
| the pilot document describes group cards and nothing else | same | confirmed, no non-group id present |
| every entry's `textSha256` matches the governing printed text | the SHA-256 of the governing text recomputed from `premiere.json` and `deluxe.json` (Deluxe overwrites, per D5) | **0 mismatches** |
| every entry's title matches the pool | same | **0 mismatches** |
| at least 70% of pilot cards expressible without the escape hatch | the `BESPOKE` operations counted in the data | **25 of 26 = 96.2%** confirmed; 1 bespoke card, 1 bespoke operation |
| the bespoke card is the one the census classifies as BESPOKE | the census's GROUP records joined to the pool by title | confirmed: `group_spin_doctors` is the only bespoke card, and it is the census's only BESPOKE GROUP record |
| every cited rule ID resolves | the 16 distinct IDs in the data checked against `docs/RULE_INDEX.md` | **0 unknown IDs** |
| the two data documents do not disagree about a card | the five group cards that the v0 skeleton also carried | confirmed: `effects-v0.json` now holds 22 entries over 12 cards and **no** group id; the pilot document holds all 26 |
| the v0 probe's count constants track the data move | the constants read out of `HeadlessEffectSchemaProbe` against the document | confirmed: `EXPECTED_ENTRIES = 22`, `EXPECTED_CARDS = 12`, and the document is 22 over 12 |
| the engine's behaviour is unchanged | the commit's file list | confirmed: no file under `b5ccg/src/b5ccg/engine/CardEffects.java`, `GameController.java` or any other pre-existing probe is touched; the only edit to a pre-existing probe is the v0 probe's two count constants, and their diff is a comment plus the two numbers |
| the RED evidence the report cites is shipped | `git ls-tree` and `git show` on commit `0f8960f` | confirmed: `.agent/REPORTS/evidence/GATE-B5-0034-RED.txt` is in the commit and records `RESULT: FAIL (45 checks, 2 failures)` and `ERROR: 1 gate class(es) failed` |
| every entry states the rule its card prints | all 26 titles re-read against the governing printed text; a same-family advisory review's readings re-derived from the data before being recorded | **4 entries state a broader rule than they print** - see section 3 |
| the primitive registry's declared kind constraint is enforced | the kind sets parsed out of the 24 primitive classes' own Java sources, every operation in both documents checked, and the loader's resolution path read | **not enforced**; 59 operations checked, **1 violation** - see section 3.4 |

**A correction to my own earlier statement.** During this session I wrote
that the report cites a RED evidence file which the repository does not
ship. **That was wrong**, and the reason is worth recording because it is
the same failure mode the row's own report warns about: I read the evidence
directory at 10:05, the file was written at 10:08 and committed at 10:10:35,
and I treated "absent when I looked" as "absent". The file is there, it is
in the commit, and it shows the red state the report describes. My own
independent reproduction of the same red state is at
`.agent/REPORTS/evidence/GATE-B5-0034-RED-REPRO.txt`.

---

## 2. The finding: two artefacts report two different "what would the v0 vocabulary have done" figures, and they reach opposite conclusions

This is the one substantive defect the verification found.

* The **report** (`.agent/REPORTS/2026-10-22-opencode-minimax-m3-1-flash-free-01-B5-0034.md`,
  section 3) says: *"With the v0 registry exactly as B5-0028 left it,
  **19 of the 26 cards (73%)** could be expressed; the other seven could
  not"* - and concludes *"The 73% was already above the gate's 70%, but the
  seven gaps are exactly the thing the pilot exists to find, so the row
  closed them."*
* The **DECISIONS entry** (`docs/DECISIONS.md`, "2026-10-22 - The Groups
  pilot", item 2) says: *"only **16 of the 26 group cards (61.5%)** could be
  expressed at all ... Ten cards could not be expressed"* - and concludes
  *"**The v0 vocabulary would have failed the Groups pilot.**"*
* The **commit message** of `0f8960f` says: *"only 16 of the 26 group cards
  (61.5%) could be expressed, and the gate's threshold is 70%. Ten cards
  could not be expressed."*

Two artefacts of one row disagree, and the disagreement is not cosmetic: the
73% reading says the pilot would have **passed** on the v0 vocabulary, and
the 61.5% reading says it would have **failed**. Only one of those can be
right, and it decides what the pilot was worth.

### 2.1 The measurement

The question is answerable, so it was answered rather than argued about.

* The v0 effect package was taken from commit `a60fd27` (the tree as B5-0028
  left it: 12 primitive families, no `CONFLICT_TYPE_PSI`, no
  `DAMAGED_CHARACTERS_AT_MOST`, no `optional` field, and **zero** published
  bespoke handlers).
* The pilot document was taken from commit `0f8960f`, unchanged.
* Each of its 35 entries was loaded **on its own** against the v0 registry
  (`EffectLoader.parseEntry`), so that one bad entry cannot hide the others.
  The harness is not shipped; it lives in the temp directory and is not part
  of the repository.

```
=== V0 EXPRESSIBILITY MEASUREMENT ===
  pilot cards            : 26
  pilot entries          : 35
  entries that LOAD      : 25
  cards with ALL entries loading : 16 of 26  (61%)
  cards NOT fully expressible    : 10
```

The ten cards and the reason each one fails:

| Card | What the v0 vocabulary could not say |
|---|---|
| Damage Control Team | `unknown effect primitive 'UNROTATE_TARGET'` |
| Imperial Telepaths | `unknown effect trigger 'CONFLICT_TYPE_PSI'` |
| ISN | `unknown effect primitive 'REVEAL_HAND'` |
| Military Telepaths | `unknown effect trigger 'CONFLICT_TYPE_PSI'` |
| Motivated Leaders | `unknown effect primitive 'DISCARD_SOURCE'` |
| Observers | `unknown effect primitive 'VIEW_HAND'` |
| Psi Corps Intelligence | `unknown effect trigger 'CONFLICT_TYPE_PSI'` |
| Rangers Surveillance | `unknown effect primitive 'REORDER_DRAW_PILE'` |
| Religious Caste | `unknown condition type 'DAMAGED_CHARACTERS_AT_MOST'` |
| Spin Doctors | `bespoke handler 'SPIN_DOCTORS_PERCEIVED_WINNER' is not registered; the registry holds 0 handler(s)` |

The "may" gap was confirmed separately, because on "Observers" the loader
stops at the unknown primitive before it reaches the unknown field. With
`VIEW_HAND` replaced by a primitive the v0 registry holds, the same entry
fails at the next operation:

```
entries[14].operations[1]: unknown field 'optional'; this schema defines [primitive, params]
```

So the operation shape really could not say "may" either, and it is a
**separate** gap from the information primitive.

### 2.2 What the two figures actually are, and which one answers the pilot gate's question

The two numbers are not two measurements of the same thing. There are three
card-level counts, and the report quoted the third while calling it the
first:

| Measure | Value |
|---|---|
| cards whose **every** entry is expressible under the v0 vocabulary | **16 of 26 (61.5%)** |
| cards with **at least one** expressible entry | 19 of 26 (73%) |
| cards with **no** expressible entry at all | 7 of 26 |

The report's 19 is the second of those. It is a real number, and it is the
number of cards whose behaviour could be written down **in part**. The pilot
gate asks about a **card**: *"at least 70% of pilot cards expressible
without the escape hatch"*. A card is expressible or it is not; a card
whose Psi-triggered entry cannot be written down is not expressible, because
half of what it prints is missing. On that reading the answer is **16 of 26
= 61.5%**, which is below the 70% threshold - **the v0 vocabulary would
have failed the Groups pilot**, exactly as the DECISIONS entry says and
exactly as the commit message says.

The report's own section 4 names the three Psi cards ("Three group cards
rotate during a Psi conflict"), so the information was in the row; what
went wrong is that the seven-card table in section 3 was assembled by hand
and three cards fell out of it.

### 2.3 Why this is worth correcting rather than noting

The report's version says the pilot was **comfortably inside the gate** on
the v0 vocabulary (73% >= 70%) and that the four vocabulary gaps were
merely tidy-ups. The measured version says the v0 vocabulary **could not
have passed the pilot** (61.5% < 70%) and that the four vocabulary gaps
were the difference between a failed pilot and a passed one. The second
reading is the one the gate's threshold was written to catch, and it is the
one the row's own DECISIONS entry and commit message already carry. The
correction is therefore to the **report body**, and it is recorded as a
correction in `docs/DECISIONS.md` rather than as a rewrite: the report's
provenance is untouched, and its front matter carries an `advisory_review`
block naming this review as same-family and advisory.

---

## 3. What the data review found: four entries state a broader rule than their card prints

A same-family advisory review of the pilot data (a second, independent read
of all 26 cards against their printed text) was commissioned as part of this
verification. Its readings were then **re-derived from the artefacts** before
being recorded, because a reviewer's reading is evidence and a measurement is
proof. Every finding below was confirmed by reading the shipped data and, for
the last one, by parsing the registry's own kind declarations out of the
Java sources.

### 3.1 "Spin Doctors" carries no Diplomacy restriction

The card reads: *"Rotate this Group after any conflict. Change the perceived
winner of **a Diplomacy conflict** for Influence gain purposes."*

`effects-groups-v1.json` `entries[21]` encodes the second sentence as
`trigger: "CONFLICT_RESOLVED"`, `window: "RESOLUTION"`, `conditions: []` -
an effect on **any** conflict that resolves, of any type. The bespoke
handler `SpinDoctorsPerceivedWinner` takes `(controller, actualWinner)` and
has **no conflict-type parameter**, so the restriction is nowhere in the
data or in the code.

As data, the entry states a **stronger** rule than the card prints, and it
does so on the one card the escape hatch exists for. The narrower encoding
is available in the vocabulary the same file already uses - the War College
draw (`entries[23]`) keeps `trigger: "CONFLICT_TYPE_MILITARY"` with
`window: "RESOLUTION"` and `conditions: [{"type": "SOURCE_ROTATED"}]`.

### 3.2 Three "if you win" clauses drop the conflict type that scopes them

| Entry | Card | Encoded trigger | What that means as data |
|---|---|---|---|
| `entries[4]` Diplomatic Corps | "Rotate this Group during any **Diplomacy** conflict ... if you win, draw 1 card." | `CONFLICT_WON` | the draw fires after winning a conflict of any type |
| `entries[20]` Secret Police | "Rotate this Group during any **Intrigue** conflict ... If you win, the loser must also rotate one character." | `CONFLICT_WON` | the rotation fires after winning a conflict of any type |
| `entries[25]` Imperial Telepaths | "Rotate this Group during any **Psi** conflict ... If you win, the loser must discard 1 card." | `CONFLICT_WON` | the discard fires after winning a conflict of any type |

The same file keeps the scope for War College, so the inconsistency is
inside one document rather than between two.

### 3.3 The conjunction is not expressible, which is why 3.2 was written the broad way

**This is a fifth vocabulary gap, and the pilot closed four.** The condition
vocabulary has no predicate for "the initiator won"; the trigger vocabulary
has `CONFLICT_WON` and `CONFLICT_TYPE_*` but no way to combine them. A3's
own shape puts the conjunction in the trigger ("conflict resolved and
initiator won"); the pilot's own convention puts the conflict class in the
trigger and the step in the window. Either way, "a conflict of type T that
you won" has nowhere to go.

The gap is why 3.1 and 3.2 exist, and WP-12 will need it too.

### 3.4 The primitive registry's kind constraint is documented and not enforced

`EffectPrimitive.kinds()` declares *"the effect kinds this primitive may
appear under"*, and the v0 registry's javadoc says the kinds are part of
the contract. **No code path checks it**: `EffectLoader` never reads the
entry's kind, and `EffectRegistry.describe` resolves the primitive,
describes the operation and checks bespoke registration without consulting
`kinds()`.

Measured over both shipped documents - the kind sets were parsed out of the
24 primitive classes' own Java sources and every operation in both documents
checked against them:

```
TOTAL operations checked: 59
KIND/PRIMITIVE MISMATCHES: 1
  effects-groups-v1.json entries[13] group_motivated_leaders  STAT_MODIFY
      entry kind ONE_SHOT not in the primitive kind set {ONGOING,REPLACEMENT,TRIGGERED}
```

The document loads cleanly, so the constraint that fails open is invisible
to the loader. A3 principle 4 is "**Fail closed.** An unknown effect kind is
a load error"; this is a second kind of kind - a *known* kind used with a
primitive that does not allow it - and it also fails open.

### 3.5 What is NOT a defect

The "may" mapping is correct and is the only optional operation in the
file. All six play restrictions ("Centauri only.", "Minbari only.", "Narn
only.", "Human only.") are left as card data rather than turned into
effect-target race filters, and the two inside-sentence race restrictions
are correct. All fourteen titles whose Deluxe text differs from Premiere
reflect the **Deluxe** reading, and no Premiere-only number survives
anywhere. The bespoke card is unique and agrees with the census. Every
`textSha256` matches the governing printed text, and every cited rule ID
resolves.

### 3.6 Filed, not fixed

The findings are filed as ledger row **B5-0052** (scope
`engine,resources,docs`), which carries the conflict-type scope on the four
entries, the vocabulary gap behind it, and the loader's kind check. It
should land **before** WP-12 (B5-0035), which will need the same
conjunction.

They are not fixed in this row, and the reason is worth stating plainly:
this row's scope is `docs`, and a fix needs `engine` (a new condition
type, a loader check), `resources` (the data) and a probe that fails
without the change. The pilot data is **inert** - the engine does not
execute effect entries - so the defect has no runtime effect today, and a
rushed patch in a verification row would be worse than a filed row.

---

## 4. The gate, re-run in the real tree

The row's own capture is `.agent/REPORTS/evidence/GATE-B5-0034.txt`. The
verification gate was re-run independently in the shared working tree, from
`b5ccg/`, with the staged JDK 25 on `PATH`:

```
RUN_TESTS=1 sh compile.sh      (from b5ccg/, staged JDK 25.0.4.1 on PATH)
  javac 25.0.4.1, --release 8, 242 sources
  Summary: 38 passed, 0 failed (of 38 gate classes run)
  Total gate time: 320267ms
  Tooling probes: 4 passed, 0 failed
  GATE EXIT = 0
  (wall clock 393s)
```

Capture: `.agent/REPORTS/evidence/GATE-B5-0034-VERIFY.txt`.

The failing-name set is empty, so the baseline is unchanged (AGENTS.md
section 4). The gate class count is 38 and the tooling probes are 4 - the
fourth being the other row's process-tax probe, which is in the tree.

**The other gates, run per `00_BOOT.md` step 8:**

| Tool | Result |
|---|---|
| `.agent/tools/run-dup-census.ps1` | exit 0 - 0 duplicate task IDs |
| `.agent/tools/check-citations.ps1` | exit 0 - every cited rule ID resolves in `docs/RULE_INDEX.md`; every REPORTS/PATTERNS citation resolves to a file on disk |
| `.agent/tools/lint-encoding.ps1` | exit 1 - **20 hits, the documented repo baseline**, unchanged, and **none of them in a file this row created** |

---

## 5. Red-then-green, reproduced

The row's red state is committed (`GATE-B5-0034-RED.txt`: the "ISN" entry
block removed, `RESULT: FAIL (45 checks, 2 failures)`, gate exit 1, failing
set exactly `{b5ccg.engine.HeadlessGroupEffectProbe}`).

The same red state was reproduced independently, in an **isolated copy** of
the tree rather than the shared one, because B5-0026 was running the gate in
the shared working tree at the same time and a red tree there would have put
a false failure into another row's evidence file - which is what happened
once, to that row, from this row's in-flight changes.

```
Summary: 37 passed, 1 failed (of 38 gate classes run)
FAILING-NAME SET (compare by name, never by count):
  - b5ccg.engine.HeadlessGroupEffectProbe
  RESULT: FAIL (45 checks, 2 failures)
ERROR: 1 gate class(es) failed. Exit non-zero.
GATE EXIT = 1
```

which is the author's committed red state (`GATE-B5-0034-RED.txt`) reproduced
independently: the same failing-name set, the same two failing checks, the
same non-zero exit.

---

## 6. What is still open, and what this row does not claim

Nothing in this verification changes the row's honest limitation, and this
row does not paper over it:

1. **The pilot gate's third clause is still not met in its literal form.**
   "Zero UNIMPLEMENTED hits for pilot cards **in fuzz runs**" needs an engine
   that executes effect entries and a fuzz run in the gate. Neither exists.
   The engine does not read the pilot document; the document is inert. The
   data-level equivalent - every group title has an entry - is measured, and
   the tripwire's own baseline is unchanged, because the engine's behaviour
   is unchanged. This is the strangler's work, not this row's.
2. **No legacy golden master was needed, and none was recorded.** Groups
   have 0% legacy coverage: all 26 census records carry `covered: false`, so
   there is no legacy `CardEffects` predicate for a group card to reproduce.
   That is a measured fact, not an omission.
3. **The two inert schema stubs remain inert.** `GroupEffectSchema` and
   `EnhancementEffectSchema` still have zero implementers. A standing defect,
   and a standing defect is not promoted by a verification pass.

---

## 7. Governance notes

**A scope overlap, disclosed.** B5-0026 held a live claim on `docs,tooling`
(`opencode-space-bunny-free-01`) throughout this session, and this row
touches `docs` - the ledger row, the DECISIONS correction and this report.
`new-claim.ps1` has **no scope-overlap check** and no freeze references at
all, which is how the three recorded breaches (B5-0039/0040/0041) and this
one happened. The ledger row B5-0048 (filed by the B5-0034 author for
exactly this hole) covers it; this session adds the second recorded
instance. The edits were additive, the shared files were re-read immediately
before each write, and nothing in B5-0026's artefacts was touched.

**A claim is a claim.** The sibling's claim was released by its holder at
10:10:58 before this session wrote anything to the repository's shared
files. The first thing this session wrote to a shared file was the ledger
row B5-0051, and it took its own claim first.

**Reviewer independence.** The work under review was authored by a MiniMax
session; this verification was performed by a MiniMax session; and the
advisory review that surfaced the section-3 findings was performed by a
third MiniMax session. Under the B5-0045 rule (reviewer independence is by
model family, not agent id) none of these is an independent assessment, and
none is recorded as an `assessor_llm` entry. The advisory review is recorded
as an `advisory_review` block on the author's report, and its readings were
re-derived from the artefacts before being recorded here. A
different-family assessment of B5-0034 is still outstanding.

---

## 8. Provenance of this document

Authored by `opencode (MiniMax M3.1 Flash Free)`, version
`minimax-m3.1-flash-free-2026-10-22-02`, on 2026-10-22, from the
`opencode-minimax-m3-1-flash-free-02` session.

**This document is not independently assessed.** It is recorded as an
`unassessed_revision` in the front matter, per AGENTS.md section 1: it is
author-written and no different-family reviewer has reviewed it. A
different-family assessment of this row is outstanding.

Every measurement in sections 1, 2, 3 and 4 was taken in this session, from
the repository's own artefacts - the card files, the census, the data
documents, the rule index and the gate logs - and not copied from the report
under review. The two figures this row corrects were separated by building
the v0 effect package from its own commit and loading the pilot document
against it, entry by entry. The findings in section 3 were surfaced by a
same-family advisory review and were then re-derived here from the shipped
data, the primitive classes' own kind declarations and the loader's
resolution path; none of them is a reviewer's opinion carried forward
unchecked.
