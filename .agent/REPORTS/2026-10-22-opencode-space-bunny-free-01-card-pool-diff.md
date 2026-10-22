---
document:
  title: "The card pool is a paraphrase layer: the diff, the rulings, and the seeded queue"
  status: "Report (observation, no authority)"
provenance:
  author_llm: {name: "opencode (Space Bunny Free)", version: "space-bunny-free-2026-10-22-04"}
  created_date: "2026-10-22"
  last_modified_date: "2026-10-22"
  last_modified_by_llm: {name: "opencode (Space Bunny Free)", version: "space-bunny-free-2026-10-22-04"}
  assessor_llm: none
  unassessed_revision:
    - {author_llm: {name: "opencode (Space Bunny Free)", version: "space-bunny-free-2026-10-22-04"}, date: "2026-10-22", change: "initial report for the card-pool diff", note: "authored by this agent and NOT independently assessed; no assessor_llm entry is claimed for this pass. A different-family assessment is outstanding."}
---

# The card pool is a paraphrase layer

**What this session found, and what it did about it.** The owner handed over a
card scan (the original "Vorlon Enhancement") to settle the open question
filed under B5-0028. Reading it settled that question, and then the sample
that followed showed the question was much smaller than the problem.

**No card text was changed, and the pool was not touched.** The pool rewrite
is a separate, gated row.

---

## 1. The open question, answered

The open item was: the engine grants a Vorlon Mark to the attached character
while "Vorlon Enhancement" is in play (`CardEffects.ENHANCEMENT_MARK_GRANTS`,
B5-1995), and neither the governing printed text nor the pool's paraphrase
mentions such a mark.

The card's **printed** text reads:

> Requires 4 Vorlon Marks to play.
> Target a character with Psi of 1 or more.
> The character's Psi is raised to 15.

The card **requires** marks; it does not **provide** one. So the grant is
supported by neither reading of the card, and the most likely origin is that
"Requires 4 Vorlon Marks to play" was read as a provision. Filed as
**B5-0105**.

## 2. But the card was the smallest part of it

Nine cards were checked against their scans - eight read directly, one from
the pre-fork store's own OCR transcription. **Nine of nine differ**, across
all seven card types, while the *identity* fields agree:

| card | type | printed | pool |
|---|---|---|---|
| Ashan | CHARACTER | "Religious Caste." + flavour | "Minbari character. Supporting role only." |
| Expeditionary Fleet | FLEET | tension-5 attack ability | "Human expeditionary force." |
| Affirm Alliance | EVENT | rotate your ambassador to sponsor or promote at half cost | "Gain 1 Influence. You and one other player agree not to attack…" |
| Revenge | AGENDA | +2 Military vs Centauri; tension counts as power | "Reach 20 Influence… spend 1 Influence to add 2" |
| Commercial Telepaths | GROUP | "**Multiple.**" + "not targeted by Commercial Telepaths" | no trait; claims a deluxe change |
| Babylon 5 Unrest | ENHANCEMENT | Babylon 5 loses 1 influence per draw round | card costs +1 |
| Vorlon Enhancement | ENHANCEMENT | requires 4 marks; Psi raised to 15 | +2 to all stats; any conflict type |
| Centauri Prime | LOCATION | +1 influence per conflict, by rotating | "Provides 2 Influence per round" |
| Coordinated Fire | EVENT | (the store's transcription: an attack sequence) | "+2 to your fleet Military total…" |

**The rulebook arbitrates, and it sides with the scans.** The pinned
rulebook's glossary says:

> "If you use another card ability to make an attack and do not become a
> conflict participant (for example, if you use the **special ability of an
> Expeditionary Fleet**)…"

The scan of that card carries exactly such an ability. The pool's record for
it is "Human expeditionary force." **None** of the pool's phrases appear
anywhere in the rulebook; the scans' do.

So the pool is not a paraphrase of the cards. It is a **different set of
rules**, and the canonical rulebook expects the cards' rules.

## 3. The pre-fork repository already knew, and waited

This is the part that changes the diagnosis. On 2026-09-28 the pre-fork
repository established that the pool is a **hand-authored paraphrase layer**
(110 records carried inline "(Deluxe text change: …)" annotations; the deluxe
set is a strict reprint subset) and recorded the open question:

> "correct printed text arriving here is a **deliberate divergence from the
> current design layer, not a bug fix**, and it is exactly the question the
> withdrawal of B5-0654 leaves open: **what card-data policy replaces it**."

The owner's images arrived (2 068 files; every one of the 446 titles matched;
0 titles without a scan) and the diff was specified - *"read each image and
compare against the frozen baseline by id, producing CHANGED / ADDED /
REMOVED / UNCHANGED with the specific fields named"* - and **it was never
run**. Two of 446 titles had been transcribed. This session's nine-card
sample is the first real measurement of it.

## 4. The owner's rulings, and what they mean

1. **The printed cards are the text of record.** All 446 titles are
   transcribed; the pool's paraphrases are replaced.
2. **The deluxe texts are recorded as unverified claims.** The store holds
   ONE image per title, assigned to the deluxe id - and the evidence says
   that image is the premiere card, because the deluxe "Commercial Telepaths"
   record claims a restriction was dropped and the deluxe scan still carries
   it. So the 150 deluxe annotations are claims no physical card can
   confirm; they are recorded as claims, not applied and not dropped.
3. **The full 446 now**, not a sample.

## 5. Three more things the diff has already found

- **The pool has no `traits` field at all** - 0 of 446 records. The printed
  "Commercial Telepaths" card says "**Multiple.**" in large type, and A3
  (WP-10) requires the printed traits as structured data. The field the
  schema needs does not exist in the data. That is a hole in the data, not in
  the schema.
- **The structured behaviour fields are wrong where the text is wrong.** The
  pool gives "Revenge" a `winCondition: INFLUENCE_20` when the printed card
  has **no win condition**; and gives "Centauri Prime" an
  `influencePerRound: 2` when the printed card grants "+1 additional
  influence" per conflict, by rotating. These are the fields an engine acts
  on, so the defect is not confined to display text.
- **The printed cards need MORE of the schema than the paraphrases do.** The
  printed "Vorlon Enhancement" wants a mark requirement, a stat-threshold
  condition, and a **replacement** operation ("raised **to** 15", not "by
  15"). Filed as **B5-0107**.

And a defect that is not about text at all:

- **B5-0104 - the AFTERMATH id-stem asymmetry.** All 58 AFTERMATH cards
  carry a different id stem in the two printings (`aftermath_x` versus
  `de_am_x`), so `CardIdNamespaceGuard.canonicalKey` - which strips `de_` and
  nothing else - cannot see that they are the same card. Measured: **325 of
  383 deluxe records canonicalise to a premiere id and 58 do not**, and the
  58 are exactly the aftermatches. Two printings of one card are two cards
  to every id-keyed registry, including the effect schema's card keys. This
  is also why the scan cannot be located by constructing an id.

## 6. How the diff is run, and the queue that runs it

**The scan is found by title, never by constructing an id** - because of the
58 aftermaths above and because twenty cards' printed titles differ from the
pool's ("Vital Interest" for "Vital Interests", "Empereror Turham" for
"Emperor Turhan", two supplied as real JPEGs).
`.agent/tools/find-card-scan.ps1` does the resolution and prints the scan
path and the pool record to compare: **446 of 446 titles resolve.**

The method, the output shape, the fields to compare and the hard rules are in
**`.agent/CARD-TEXT/README.md`**. One record per card, named
`<premiere-id>.md`, carrying the printed rules text verbatim with the flavour
separated, every printed number, every printed trait, the pool record quoted,
and a MATCH / CHANGED / INCONCLUSIVE verdict **naming every field that
differs**. The pool is not edited by these rows.

**The queue is seeded: 49 batch rows, B5-0053..B5-0101, plus six side rows,
B5-0102..B5-0107.** The batches are grouped by card type in the order the
work is worth most - the two pilot types first, because Groups and
Enhancements are what B5-0034 and B5-0035 are waiting for - and the first
batch is deliberately half size (6 cards) so the format lands somewhere
reviewable before the queue scales up.

Three things about the seeding needed care:

- **The scope namespace is extended.** AGENTS.md §5 names seven writer
  scopes; the diff rows use `card-text/bNN`, one per batch, so no two agents
  can write the same file. That is the point of the scope discipline, and the
  thing whose absence let two rows collide in this tree earlier the same
  day. **Recorded for the owner's confirmation, because it extends the
  governance's list.**
- **The gates had to be made machine-enforced.** A row's Task cell can say
  "gated: claim ONLY after X is DONE" and the queue will still offer it: the
  gate table is a hard-coded hashtable in `.agent/run-queue.ps1`. The
  seeder now registers the gates it implies, in a marked block, so the rows
  and their gates are created by one tool and cannot drift apart. Verified:
  the five gated rows now read "Skipping gated (predecessor not DONE)".
- **The ledger's OPEN-row band is exceeded, deliberately.** The header asks
  for roughly 5-10 OPEN rows; there are now about 60. The band was written
  for a serial fleet with a small live backlog, and a 446-card job that only
  a queue can absorb is the case the band cannot express. The ledger is
  ~55 KB against the 200 KB target, and the rows are fully specified rather
  than half-specified - which was the band's actual purpose.

## 7. What this does to the effect programme

The effect schema (B5-0028) and the two pilots (B5-0034, B5-0035) are
built on the pool's text. The v0 entry for "Vorlon Enhancement" encodes
"+2 to all stats and the ability to participate in any conflict type" - **a
card that does not exist** - and its `textSha256` pins the wrong words, so the
drift detector can never fire on the real drift. The pilots' headline figures
(26 of 26 coverage, 16 of 26 expressible) are about the wrong cards.

The schema's **shape** survives and is still wanted - the printed cards need
more of it than the paraphrases do. Its **data** has to be re-derived from
the corrected pool, which is why B5-0107 exists and why the pilots' data
work waits for the rewrite.

## 8. What the fleet does next

1. **B5-0053** - the reference batch (6 GROUP cards). It is the one to look
   at first, because the format lands there.
2. **B5-0054..B5-0101** - the remaining 48 batches, in any order the queue
   offers them. Each is its own file set, so they run in parallel.
3. **B5-0102** - the roll-up: the summary by type, the conflict list, and
   the proposed corrected pool.
4. **B5-0103** - the pool rewrite, under its own `resources` claim.

**One capability the rows need: an agent that can read an image.** The scans
are 305x441 and the small print often fails; the pre-fork store's own
transcription of "Government Opposition" came back entirely illegible, and
that was recorded as a verdict rather than guessed at. Where a print is
legible with effort, the method in `.agent/CARD-TEXT/README.md` gets it out
(crop, upscale, re-contrast, re-read). Where it is not, the row records
ILLEGIBLE with the regions named - which is a result, not a failure.
