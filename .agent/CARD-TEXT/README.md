---
document:
  title: "Card-pool diff: the method for transcribing a card from its scan"
  status: "Operating procedure (not governance; AGENTS.md and .agent/00_BOOT.md win on conflict)"
provenance:
  author_llm: {name: "opencode (Space Bunny Free)", version: "space-bunny-free-2026-10-22-04"}
  created_date: "2026-10-22"
  last_modified_date: "2026-10-22"
  last_modified_by_llm: {name: "opencode (Space Bunny Free)", version: "space-bunny-free-2026-10-22-04"}
---

# .agent/CARD-TEXT - the card-pool diff

**What this is.** The card pool in `b5ccg/resources/cards/` is a
**hand-authored paraphrase layer**, not the printed text of the physical
cards. Measured on 2026-10-22, **9 of 9 cards checked differ** from their
scans across all seven card types, while the identity fields (title, type,
cost) agree. The owner ruled on 2026-10-22 that **the printed cards are the
text of record**, and that the diff is to be run over all 446 titles.

This directory holds **one record per card**: what the card prints, what the
pool says, and the verdict. The pool itself is **not** edited by these rows -
the rewrite is a separate, gated row (the owner's own rule from the
pre-fork repository: *"Nothing is written to `b5ccg/resources/cards/`
without a separate claim."*).

## 1. Finding the scan

Do not construct a path. **Resolve by title**, through the owner's own
mapping, because two measured facts make path-construction wrong:

- The AFTERMATH cards carry **different id stems in the two printings** -
  `aftermath_approval_of_the_grey` in Premiere and
  `de_am_approval_of_the_grey` in Deluxe - for all 58 of them. So "the deluxe
  id is `de_` plus the premiere id" is false, and
  `CardIdNamespaceGuard.canonicalKey` cannot see that they are the same card.
- Twenty cards' **printed titles differ from the pool's** (the physical card
  reads "Vital Interest", "Empereror Turham", "Expenditionary Fleet (Human)",
  "Reserve Fleet (Human)"), and two were supplied as real JPEGs rather than
  GIFs.

So use the tool:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .agent/tools/find-card-scan.ps1 -CardId <premiere-id>
```

It prints the scan path, the pool record(s) for that card, and the fields to
compare. It resolves **446 of 446** titles; if it cannot find one, that is a
finding to report, not a reason to stop. (`-Json` for machine-readable
output.)

## 2. Reading the scan

**Look at the image.** Do not OCR it, do not guess it, and do not carry a
transcription over from another card. The images are 305x441 and the small
print often fails: the pre-fork store's own transcription of "Government
Opposition" came back **entirely illegible**, and that is a recorded verdict,
not a failure.

**If the print is not legible, say so.** Record the region and the reason.
A guess recorded as a fact is worse than a gap, because the gap is visible
and the guess is not. The pre-fork store's format for this is the model: it
names the region, the magnification and preprocessing tried, and the candidate
renderings it got, and it says which reading it adopted and why.

**Legible enough to read with effort is worth the effort.** The pre-fork
method upscaled (LANCZOS), sharpened and re-contrast each line at several
magnifications, and re-read each line independently. A card's rules text is
usually 3-8 short lines in a box; a crop of that box at 4-8x with a
contrast pass is usually readable. If you need to crop, say so in the
record's "reading method" line.

## 3. What to transcribe, and what to compare

The pool has **no `traits` field at all** (0 of 446 records), so the printed
traits - "Limited", "Unique", "Multiple", "Kha'Ri", "Species Ambassador",
"Ranger", "ISA", "Psi Corps", "Homeworld" - exist only on the cards. A3
(WP-10) requires them as structured data, so **a trait you can see is a
finding**, not a detail.

Record, for the card:

| What | From where on the card |
|---|---|
| title | the title line |
| type line | under the art ("Character Enhancement", "Group", "Event", "Narn Agenda", "Centauri Location") |
| printed numbers | every digit on the card: cost, influence, ability ratings, and any threshold in the rules text |
| printed traits | the word printed as a trait, usually a separate line |
| rules text | verbatim, and **separately from the flavour** |
| flavour | the smaller italic block, verbatim |
| any printed qualifier | "Multiple.", "Centauri only.", "Homeworld." |

Then compare **field by field** with the pool record the tool printed: title,
type, subtype, rarity, faction, cost, every stat, every structured field
(`winCondition`, `influencePerRound`, `triggerCondition`, `conflictType`,
`influenceReward`, `participation`, `isMajorAgenda`, the bonus fields), and
the text.

## 4. The verdict

- **MATCH** - the printed text and the pool record agree on every field.
  Say which fields you compared; a bare MATCH is not a finding.
- **CHANGED** - they differ. **Name every field that differs**, and give the
  printed value and the pool's value side by side. This is the deliverable:
  the row that applies the rewrite needs the differences, not a verdict.
- **INCONCLUSIVE** - the print could not be read. Name the region and what
  was tried. An INCONCLUSIVE is a legitimate result and is recorded as one.

**A CHANGED verdict must not stop at the text.** The pool carries behaviour
in structured fields, and those are wrong where the text is wrong - the
printed "Revenge" has **no win condition** while the pool carries
`winCondition: INFLUENCE_20`, and the printed "Centauri Prime" grants
"+1 additional influence" per conflict while the pool carries
`influencePerRound: 2`. Name those fields too.

## 5. The record's shape

One file per card, named `<premiere-id>.md` (the canonical id, so a title
with two printings gets one file covering both). The shape follows the
pre-fork store's two worked examples - `de_event_coordinated_fire.md` (a
CHANGED verdict) and `de_group_government_opposition.md` (an INCONCLUSIVE
one) - and adds the proposed corrected record, which the rewrite row needs:

```markdown
---
document:
  title: "Printed text of <premiere-id> read from its scan"
  status: "Advisory (.agent/CARD-TEXT/): an observation, not authority"
provenance:
  author_llm: {name: "<your agent id>", version: "<your version>"}
  source_image: "<the scan path the tool printed>"
  pool_record: {id: "<premiere-id>", file: "b5ccg/resources/cards/premiere.json"}
  extracted_date: "<today>"
---

# <Title>

## Printed text (transcribed verbatim)

Reading method: <what you did, including any cropping/upscaling>

Title: <...>
Type line: <...>
Printed numbers: <...>
Printed traits: <...>
Rules text:
<...>

Flavour text:
<...>

## Illegible regions

- <region>: ILLEGIBLE. <what was tried; what it might be; whether you adopted it>

## Pool record text (current, quoted)

> <the record, quoted verbatim>

## Verdict

CHANGED - <one line>

Differences (printed versus pool):

1. <field>: printed "<...>", pool "<...>".
2. ...

Checked and not a difference: <field> ("<...>") agrees.

## Proposed corrected record

```json
{ ...the pool record with the printed values, plus any traits the pool
cannot currently carry... }
```

The proposed record is a **proposal**. The rewrite row decides what is
applied, and it is a separate claim on `resources`.
```

## 6. The hard rules

1. **Do not edit `b5ccg/resources/cards/`.** Not the pool, not the census,
   not the effect data. This row produces observations only.
2. **Do not edit another card's record.** Your scope names your batch's
   files. Another agent's file is another agent's work.
3. **Do not guess.** An illegible region is recorded as illegible.
4. **Do not carry a reading over** from a card you read earlier in the same
   session, or from another card with a similar name. Read each scan.
5. **One record per card, even when the pool record is right.** A MATCH is a
   finding, and it is the cheapest possible outcome - the pre-fork store's
   own note says so, and the point of a diff is to know *how much* is
   different.
6. **The printed text is the specification.** A3 principle 3: "Text is the
   specification and the display string, never parsed at runtime." Where the
   printed text and the pool disagree, the printed text is what the card
   says.
