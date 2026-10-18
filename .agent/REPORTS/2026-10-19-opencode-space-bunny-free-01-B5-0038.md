---
document:
  title: "B5-0038 DONE - CardSetManifestChecker cross-set rule corrected to the two-printing model"
  status: "Report. Proposes a change for owner verification; confers no authority on its own (AGENTS.md section 4)."
provenance:
  author_llm: {name: "opencode (Space Bunny Free)", version: "space-bunny-free"}
  created_date: "2026-10-19"
  last_modified_date: "2026-10-19"
  last_modified_by_llm: {name: "opencode (Space Bunny Free)", version: "space-bunny-free"}
  assessor_llm: none
  no_self_assessment: true
  basis: "Owner-approved change (2026-10-19). All figures measured in this session. The stale rule's own history is quoted from the repo's own B5-0004 record, not inferred."
---

# B5-0038 - CardSetManifestChecker cross-set title rule

Scope `engine`. One file: `b5ccg/src/b5ccg/engine/CardSetManifestChecker.java`.

## 1. What was wrong

The checker asserted, in both the class javadoc (line 13) and the code
(line 61):

> Cross-set title uniqueness: every title appears in exactly one set

That is a **pre-two-printing invariant**. It is false by design now: a title
legitimately exists in both Premiere and Deluxe as two printings of one card.
That is the entire basis of the `de_` id namespace and of D5, under which
Deluxe governs where the printings differ. 24 printing pairs were flagged as
violations:

```
Title appears in multiple sets: 'Secret Police' (count=2)
Title appears in multiple sets: 'War College' (count=2)
... 22 more
```

The repo had already recorded the contradiction. B5-0004's report states that
the checker's title-uniqueness rule "contradicts the two-printing data model;
A1b resolves it structurally", and that resolution was never made.

Two things this was **not**:

* not bad data - `HeadlessCanonicalParityProbe` passes with 325 canonical pairs
  and asserts "every canonical collision is a same-title printing pair";
* not a new rule reading - the owner approved the correction on the basis of
  that existing record.

## 2. What replaced it

Banning every repeated title protects against nothing real. The failure worth
catching is **two different cards sharing a title**. So a repeated title is now
legal only as a printing pair:

* exactly two entries;
* exactly one bare id and one `de_`-prefixed id;
* the two canonicalise to the same key - i.e. they really are one card in two
  printings, not two cards that happen to share a title;
* **or** the documented aftermath slug divergence (`aftermath_X` against
  `de_am_X`), which `canonicalKey` deliberately does not collapse.

That last clause was a real false positive on the first attempt: my initial
version rejected all five aftermath reprints. The exception is the same one
`HeadlessCanonicalParityProbe` applies, and it is documented in
`CardEffects.behaviourReferences()`.

## 3. Verification

```
CardSetManifestChecker on real data: MANIFEST CHECK: PASS   EXIT=0   (was EXIT=1, 24 violations)

Controls on printingPairDefect:
  ACCEPT enh_mines / de_enh_mines
  ACCEPT char_gkar / de_char_gkar
  ACCEPT aftermath_glory / de_am_glory   (divergence, by design)
  REJECT char_a / char_b          -> two Premiere ids
  REJECT char_a / char_b2         -> two Premiere ids
  REJECT de_char_a / de_char_b    -> two Deluxe ids
  REJECT three copies             -> expected exactly 2 entries, found 3
```

The rule is narrower, not merely permissive: four distinct collision shapes are
still rejected.

Regressions - none:

```
HeadlessCanonicalParityProbe: 17/17 PASSED
HeadlessConformanceTest:      904 checks PASSED
CardSetManifestCrossCheck:    PASS - 0 violations across 829 cards in 2 files
```

## 4. Scope note

This changes what a gate class asserts about card data, so it is called out as
owner-approved rather than agent-decided. It interprets no rulebook paragraph:
the two-printing model and the aftermath divergence are already recorded, in
B5-0002 (D5) and B5-0004 respectively. A DECISIONS entry is still owed under
AGENTS.md section 4.

## 5. Effect on the gate

`CardSetManifestChecker` was one of the four red classes. With this change the
gate should stand at a single failure: `HeadlessJoinTimingProbe`, down to the
S3 assertion that needs an owner ruling on whether AI auto-join was removed.
The full-gate confirmation is recorded in the run preserved alongside this row.