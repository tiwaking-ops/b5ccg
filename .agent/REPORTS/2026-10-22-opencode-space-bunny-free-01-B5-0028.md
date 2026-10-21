---
document:
  title: "B5-0028 — WP-10 Effect schema v0, JSON Schema, registry skeleton"
  status: "Report (observation, no authority)"
provenance:
  author_llm: {name: "opencode (Space Bunny Free)", version: "space-bunny-free-2026-10-22-02"}
  created_date: "2026-10-22"
  last_modified_date: "2026-10-22"
  last_modified_by_llm: {name: "opencode (Space Bunny Free)", version: "space-bunny-free-2026-10-22-02"}
  assessor_llm: none
  unassessed_revision:
    - {author_llm: {name: "opencode (Space Bunny Free)", version: "space-bunny-free-2026-10-22-02"}, date: "2026-10-22", change: "initial report for B5-0028", note: "authored by this agent and NOT independently assessed; no assessor_llm entry is claimed for this pass. A different-family assessment of this row is outstanding."}
---

# B5-0028 — WP-10 Effect schema v0, JSON Schema, registry skeleton

**Row.** B5-0028, OPEN at the start of this session. Scope `engine,tooling`
(widened to `engine,tooling,resources` — see §10). Depends on B5-0005 (the
census, DONE) and B5-0027 (released).

**Acceptance, verbatim from the proposal's work-package table (WP-10):**
"schema validates; an unknown effect kind fails the load; a renderer exists".

**Verdict.** All three clauses are checkable and checked. The gate is green
(37/37 Java gate classes, 3/3 tooling probes, exit 0) and the probe was shown
red without the change it guards, at both the probe level and the gate level.

**This report is not independently assessed.** It is recorded as an
`unassessed_revision` per AGENTS.md §1: a different-family reviewer has not
reviewed this row. The interpretations it records are logged in
`docs/DECISIONS.md` under "2026-10-22 — The effect schema v0, and what the
rulebook adds to the card text (B5-0028 / WP-10)".

---

## 1. What was delivered

| Artefact | Size | Scope |
|---|---|---|
| `b5ccg/src/b5ccg/engine/effect/` (39 files) | 3 075 lines | engine |
| `b5ccg/src/b5ccg/engine/HeadlessEffectSchemaProbe.java` | 384 lines, 43 checks | engine |
| `b5ccg/resources/effects/effects-v0.json` | 665 lines / 22 812 bytes, 29 entries, 17 cards | resources |
| `.agent/tools/schemas/effect-document.schema.json` | 210 lines | tooling |
| `.agent/tools/validate-effect-schema.py` | 207 lines, stdlib only | tooling |
| `.agent/tools/test-effect-schema.ps1` | 141 lines, 5 checks | tooling |
| `b5ccg/src/b5ccg/engine/MiniJson.java` | widened additively | engine |
| `b5ccg/compile.sh` | one gate class, one tooling probe | tooling |
| `.agent/TASK_LEDGER.md` | the row's scope cell and report cell | docs |

Both new probes are **wired into the gate**, so they are gates rather than
comments: `HeadlessEffectSchemaProbe` is in `compile.sh`'s `GATE_CLASSES`
here-doc, and `test-effect-schema.ps1` is in its `TOOL_PROBES` list.

---

## 2. Clause 1 — "schema validates"

**Two independent halves, in two languages, in two processes.** The point of
splitting them is that a mistake in one is not automatically a mistake in the
other: the Python validator shares no code with the engine.

**The tooling half.** `.agent/tools/validate-effect-schema.py` validates the
shipped document against a JSON Schema (draft 2020-12) at
`.agent/tools/schemas/effect-document.schema.json`.

```
EFFECT SCHEMA VALIDATION: PASS
  entries validated: 29
```

The project has no third-party Python dependencies and the build is
stdlib-only (AGENTS.md §2), so rather than depend on `jsonschema` the
validator implements the subset the schema uses (`$ref`, `type`, `enum`,
`const`, `required`, `properties`, `additionalProperties`, `items`,
`minItems`, `minLength`, `minimum`, `pattern`). **If the schema ever uses a
keyword the validator does not implement, the validator fails loudly rather
than ignoring it** — a validator that silently skips a keyword it does not
understand reports passes it never checked. That check is in the code, not in
a comment: the keyword walk treats `properties` and `$defs` maps as name
maps, and the "unsupported keyword" gate is exercised by the schema's own
vocabulary (it exits 0 only when the keyword set is fully covered).

**The engine half.** The shipped game's own loader refuses the same
document. The probe asserts:

- the document loads without error;
- its `schema_version` is 1;
- it holds 29 entries over 17 distinct cards;
- **every entry names a card that exists** in the card pool;
- **every entry's `textSha256` matches the SHA-256 of the governing printed
  text**, computed from the card files at probe time — the drift detector;
- **every entry's provenance cites at least one rule ID**;
- every primitive the data uses is registered;
- the v0 data uses the bespoke escape hatch **zero** times.

---

## 3. Clause 2 — "an unknown effect kind fails the load"

This is the sharp test, and it is the one the proposal calls out
("**Fail closed.** An unknown effect kind is a load error").

The loader refuses, and every refusal names **where** it happened and **what**
was wrong:

```
entries[0].kind: unknown effect kind 'NOT_A_KIND'; known kinds are ONE_SHOT,
ONGOING, TRIGGERED, REPLACEMENT, RESTRICTION, COST_MODIFIER, TRAIT
```

**One test would not have been enough.** A loader that rejected that one
string would pass a single test. So the probe attacks it from nine
directions, and each refusal must also *name* the offending thing — which is
what distinguishes "refused for the right reason" from "refused for some
reason":

| Attack | Refused with |
|---|---|
| unknown effect kind | `unknown effect kind 'NOT_A_KIND'` |
| unknown primitive | `unknown effect primitive 'NO_SUCH_PRIMITIVE'` |
| a non-integer parameter | `'count' is not an integer` |
| a parameter outside the primitive's closed set | `must be one of ...` |
| unknown window | `unknown effect window 'SOMETIMES'` |
| unknown field | `unknown field 'surprise'` |
| missing required field | `missing required field` |
| unknown `schema_version` | `schema version 99 is not supported` |
| an unpublished bespoke handler | `bespoke handler 'NOT_PUBLISHED' is not registered` |

**And the escape hatch is not a dead end.** A `BESPOKE` operation naming a
**published** handler loads; the probe proves that too, so the refusal above
is about the missing handler and not about the hatch being unusable.

**Why an unknown field is an error and not a comment.** A typo in a key name
would otherwise silently disable the effect it was meant to name — the card
would load, the effect would not happen, and nothing would say so. The
schema's `additionalProperties: false` and the loader's
`rejectUnknownKeys` are the same rule expressed in two languages.

**Why the exception is checked.** `EffectLoadException extends Exception`, not
`RuntimeException`. A checked exception makes "fail closed" a constraint the
compiler enforces: a caller that does not declare or catch it will not
compile, so "the loader swallowed the bad entry and carried on" cannot be
made by accident.

---

## 4. Clause 3 — "a renderer exists"

`EffectRenderer` produces one canonical line per entry:

```
enh_babylon5_unrest "Babylon 5 Unrest" [COST_MODIFIER] window=ACTION trigger=(none) target=GLOBAL/ctrl=ANY ops=[COST_MODIFIER(cost=INFLUENCE, amount=1)] duration=END_OF_ROUND stacking=ADDITIVE protection=NONE precedence=CARD_TEXT binding=NONE rules=RB:VI/cumulative-effects
```

The probe asserts that this exact line is the golden value (a golden string,
not a pattern — a pattern would not catch a renderer that drifted), that the
same entry renders identically twice, that the whole document renders, that
the render is stable across two runs, and that the render has one line per
entry.

**"Canonical" means stable, not pretty.** The target's filters are sorted,
the operation's parameters are sorted, and the **operations keep their
order** — because the operation order is data (A3 principle 6: the
rulebook's precedence rules are the evaluation order).

**The renderer never reads the card's text.** A3 principle 3 is "Text is the
specification and the display string, never parsed at runtime." A renderer
that folded the printed text in would make the later comparison pass compare
a thing with itself. The probe asserts the rendered line does **not** contain
the card's printed text.

---

## 5. The shape of the schema

A3's "Effect entry: shape" table gives **fourteen fields**, and all fourteen
are implemented: `kind`, `window`, `trigger`, `requirements`, `target`,
`conditions`, `operations`, `duration`, `stacking`, `protection`,
`precedenceLayer`, `sourceBinding`, `provenance` — plus the entry's
**identity** (`cardId`, `title`), which the table does not list because a
table of fields is not a data format. A3 calls its table "a hypothesis
derived from the rulebook — the census may revise it"; this is such a
revision, and it is recorded in `docs/DECISIONS.md`.

**A card may carry more than one entry.** "Contact with Shadows" draws cards
*and* provides a mark; "Babylon 5 Unrest" changes two different costs;
"Decisive Tactics" gains the winner influence and takes the loser's. The v0
data has 29 entries over 17 cards for exactly that reason.

**Three vocabularies were added to A3's lists, each because a shipped card
needs it.** They are interpretations, and they are logged as such:

1. **`duration` gains `END_OF_ROUND`.** "Babylon 5 Unrest" reads "Ongoing.
   All players' Influence costs for playing cards increase by 1. **Discard at
   end of round.**" — neither "until end of turn" nor "while source in play"
   (the card is in play right up until it is discarded).
2. **`window` gains `ANY`.** "Play any time." is a real printed window on a
   large part of the event pool, and an effect that is simply in force ("that
   character gains +2 to all stats") has no window at all.
3. **`trigger` and `precedenceLayer` and `sourceBinding` are closed enums**
   rather than free text, because A3 principle 2 asks for "typed enumerations
   and small named predicates" and a free string cannot be checked.

---

## 6. The finding that shaped the schema: the rulebook supplements the card text

**This is the most useful thing the row learned, and it is not in the
proposal.**

"Contact with Shadows" reads:

> "Play any time. Draw 2 cards. If you have Morden in play, draw 3 cards
> instead."

It says **nothing about a mark**. Yet rulebook :844 names it:

> "Many events, such as Contact with Shadows, provide a permanent mark and
> do not remain in play. A character with such a mark cannot be 'cut off'
> from its source."

The mark is real, and it is not in the card. The same is true of "Servants
of Order", whose Vorlon Mark rulebook :836 names outright ("E.g., the Agenda
'Servants of Order' provides the ambassador of the faction with 1 Vorlon Mark
while the agenda is in play").

**An effect entry written from the card text alone would be wrong.** A3
principle 3 says "Text is the specification and the display string" — but the
rulebook is also part of the specification, and for at least two cards it
supplies behaviour the card does not print. The schema has to be able to say
*where* an effect's authority comes from, and that is what the provenance
field's `ruleIds` are for: they point at the paragraph that supplies the
missing piece. The `textSha256` then pins the text the entry was written
against, so a later reader can see exactly which words were used.

---

## 7. The printing scope is a rules question, and D5 settles it

D5 (the owner ruling) makes the **Deluxe** printing govern a dual title. The
v0 data is therefore written against the Deluxe text, and every entry records
which text governed it in `printingScope` (`DELUXE_GOVERNS` or
`SINGLE_PRINTING`).

**This is not a formality — it changes the rule in six places**, and the v0
data encodes the Deluxe reading of all six:

| Card | Premiere | **Deluxe (governing)** |
|---|---|---|
| Wind Swords | +3 Military | **+4 Military** |
| Secret Police | +2 Intrigue | **+3 Intrigue** |
| Servants of Order | 3 characters in the Inner Circle | **2 characters** |
| War College | +2 Military | **+2 Military, and draw 1 card after the conflict resolves** |
| Commercial Telepaths | "choose a Character **not already affected by this Group**" | **"choose a Character"** — any Character, including opponents' |
| Babylon 5 Unrest | Influence cost +1 | **Influence cost +1, and conflict initiation cost +1** |

An entry keyed to the Premiere text of any of these would be wrong **on the
rule**, not merely on the wording. The `NOT_AFFECTED_BY_SOURCE` trait exists
in the schema's vocabulary for exactly the Premiere reading, and the v0 data
does not use it, because the governing text dropped the restriction.

---

## 8. The registry, and the god-class guard

A3 principle 5: "Keep the primitive registry modular (one class per primitive
family). Forge's ability factory itself grew past 1 200 lines, so the registry
is where the next god class would form."

The v0 registry holds **19 primitives across 12 families**, and the largest
family holds 3:

```
BESPOKE 1 · CONFLICT 3 · COST 1 · DAMAGE 2 · DISCARD 1 · DRAW 1
INFLUENCE 2 · MARK 2 · RESTRICTION 1 · STAT 1 · STATE 2 · TENSION 2
```

**That modularity is a checkable property, not a claim.** The gate asserts
that the registry holds at least 15 primitives, that it spans at least 10
families, and that **no family holds more than 3** — so a registry that
quietly became one class again fails the gate rather than a reader's
opinion. The per-family sizes are printed on every run.

**The escape hatch is keyed, and it is empty.** A `BESPOKE` operation names a
handler key; the registry holds the table of keys it will honour; a key that
is not in that table is a **load error**. The v0 registry publishes **zero**
handlers, so the escape hatch is *specified and empty*: a card that needs
bespoke behaviour cannot be written yet, and that is visible at load time
rather than discovered later. The v0 data uses it zero times, and the gate
asserts that too.

---

## 9. The v0 data — 29 entries over 17 cards

Deliberately chosen to exercise every primitive family, both pilots' subject
matter (the next two rows), and every printing-scope case:

| Card | Type | Entries | What it exercises |
|---|---|---|---|
| Babylon 5 Unrest | ENHANCEMENT | 2 | two cost modifiers, end-of-round duration |
| Vorlon Enhancement | ENHANCEMENT | 2 | `stat=ALL`, a participation grant |
| Triluminary | ENHANCEMENT | 2 | a race-restricted target, two stat modifiers |
| Exploitation | ENHANCEMENT | 1 | a location target, a round-start trigger |
| Commercial Telepaths | GROUP | 1 | a derived amount, a rotated-source condition |
| Secret Police | GROUP | 2 | a conflict-type trigger, a rotation cost, a target rotation |
| War College | GROUP | 2 | the Deluxe "also draw 1 card" change |
| Warrior Caste | GROUP | 1 | a race-scoped target |
| Wind Swords | GROUP | 1 | the Deluxe "+4" change |
| Affirm Alliance | EVENT | 2 | a one-shot gain and a round-long restriction |
| Balance | EVENT | 2 | a global gain and a global restriction |
| Decisive Tactics | EVENT | 2 | winner and loser as two targets |
| Diplomatic Blunder | EVENT | 1 | a loss of influence to an opponent |
| Shadow Strike | EVENT | 2 | a conflict bonus and a prohibition |
| Contact with Shadows | EVENT | 3 | a replacing draw, a conditional draw, a permanent mark |
| Chaos Reigns | EVENT | 1 | a discard |
| Servants of Order | AGENDA | 2 | a conditional influence gain, a cut-off-purge mark |

**Three families are exercised by the registry but not by the data**:
`DAMAGE`/`HEAL`, `TENSION`/`UNREST` and `STATE_ENTER`/`STATE_EXIT`. The
shipped card pool contains **no card that changes tension or unrest, no card
that purges a mark, and no card that names a rulebook state** — measured, not
assumed (a search of all 446 Premiere card texts). The v0 data is faithful to
the cards, so it does not use them; the probe exercises the whole vocabulary
with synthetic documents instead, so the unused primitives are still proven
to load.

---

## 10. Scope: the row was widened to name `resources`

The v0 effect data is **card data**, and it lives in
`b5ccg/resources/effects/`. AGENTS.md §5 requires a row to name every scope
it touches, so the B5-0028 row's scope cell was changed from `engine,tooling`
to `engine,tooling,resources` before the claim was taken. Nothing else
moved: no card JSON, no census and no deck file was touched, and the
`resources` scope was free of any live claim (all 15 claim files were
`*.RELEASED.json`).

The ledger edit preserved the ledger's **pure CRLF** (154 CR / 154 LF, 7
pipes on the row) and appended to the existing cell text rather than
rewriting it.

---

## 11. Red-then-green, measured

`out/` was deleted before every build so a stale class could not be mistaken
for a fresh one, and `javac`'s exit was recorded **separately** from the
probe's exit.

### Probe level

| Tree | javac | Probe | Failing checks |
|---|---|---|---|
| **Red A** — `EffectKind.parse` fails **open** (an unknown kind returns `ONE_SHOT` instead of throwing) | 0 | **exit 1** | 1 — "an unknown effect kind is refused" |
| **Red B** — the loader stops rejecting an unknown field | 0 | **exit 1** | 1 — "an unknown field is refused" |
| **Red C** — the renderer drops the source binding from its output | 0 | **exit 1** | 1 — "an entry with non-default fields renders to its second golden line" |
| the fixed tree | 0 | **exit 0** | 0 — 43/43 |

### Red C found a defect in the probe, and that is the interesting part

**The first attempt at Red C did not go red.** The renderer was changed to
print `binding=NONE` for every entry, and the probe stayed **green**.

The reason is that the probe's only golden value was for an entry whose
source binding *is* `NONE` — so a renderer that dropped the binding entirely
produced the **same string** for that entry, and the golden could not tell a
correct renderer from a broken one. **A golden value that cannot distinguish
the two is not evidence.**

The fix was a second golden value, for an entry whose fields are all
non-default: a non-`NONE` source binding (`PERMANENT_MARK`), a card-scoped
target with a card-type filter, a permanent duration and a mark operation.
That golden was **written out from the entry's own fields and the documented
render format, not copied from the renderer's output** — so when the probe
passed, it confirmed the format the documentation claims, rather than pinning
whatever the code happened to do. With the second golden in place, Red C goes
red on the second attempt.

This is the B5-0044 lesson applied to this row's own work: green output is
not evidence until something has tried to make it red, and the thing that
tried found a real hole.

### Gate level

With **Red A** applied (the acceptance clause itself — "an unknown effect kind
fails the load"), the full gate was run:

```
Summary: 36 passed, 1 failed (of 37 gate classes run)
FAILING-NAME SET (compare by name, never by count):
  - b5ccg.engine.HeadlessEffectSchemaProbe
GATE EXIT = 1
```

Evidence: `.agent/REPORTS/evidence/GATE-B5-0028-RED.txt`.

---

## 12. Gate evidence — green

```
RUN_TESTS=1 sh compile.sh      (from b5ccg/)
  javac 25.0.4.1, --release 8, 238 sources, 656 classes
  Summary: 37 passed, 0 failed (of 37 gate classes run)
  Total gate time: 251037ms
  Tooling probes: 3 passed, 0 failed
  GATE EXIT = 0
```

The gate class count went from 36 to 37 and the tooling probes from 2 to 3;
**every pre-existing class still passes, and the failing-name set is empty**,
so the baseline is unchanged (AGENTS.md §4).

Evidence: `.agent/REPORTS/evidence/GATE-B5-0028.txt`.

**The other gates, run per `00_BOOT.md` step 8:**

| Tool | Result |
|---|---|
| `.agent/tools/run-dup-census.ps1` | exit 0 — 0 duplicate task IDs |
| `.agent/tools/lint-encoding.ps1` | exit 1 — **20 hits, the documented repo baseline**, unchanged, and **none of them in a file this row created** |

---

## 13. What did NOT change

- **The engine's behaviour is unchanged.** No existing probe was edited, no
  conformance assertion was touched, and every card still resolves through
  `CardEffects`. The v0 data is a **skeleton**, not a migration.
- **The legacy strangler is not started.** The registry is consulted by the
  new loader only; the engine does not consult it. "The registry is consulted
  first and falls back to the existing `CardEffects` predicate", with a
  golden-master scenario recorded per legacy title *before* any port, is
  WP-11's and WP-12's work.
- **`MiniJson` was widened additively, not rewritten.** `parseObject` is
  unchanged and still package-private, so the replay path is the same code as
  before; the strictness is unchanged (trailing content is still an error,
  floating point is still refused); and the `B5-1975` error prefix is
  deliberately unchanged, because the reader's grammar did not change. The
  reason it was widened rather than a fourth JSON parser added: the tree
  already held three hand-written readers, and A3's principle 1 needs the
  shipped game to read declarative data records.
- **`CardIdNamespaceGuard`'s javadoc was corrected**, because it asserted
  that `MiniJson` "is package-private and refuses anything but a single
  top-level object" — a statement this row made false.
- **The two inert schema stubs are still inert.** `GroupEffectSchema` and
  `EnhancementEffectSchema` remain 7-to-10-line interfaces with zero
  implementers, as B5-0032 and B5-0033 left theirs. **They remain a standing
  defect and are not promoted by this row.** This row adds a *live* schema
  package beside them; it does not delete them, because deleting a public
  interface is a wider change than this row's scope.

---

## 14. OPEN ITEM for the owner — the "Vorlon Enhancement" mark

The engine grants a **Vorlon Mark** to the attached character while
"Vorlon Enhancement" is in play (`CardEffects.ENHANCEMENT_MARK_GRANTS`,
B5-1995). The governing printed text does not say so:

> "Attach to a Character. That character gains +2 to all stats and the
> ability to participate in any conflict type."

And the rulebook's only *named* example of a card providing a mark is the
agenda at :836, not an enhancement.

The v0 data therefore encodes the two abilities the text **does** state and
**does not** encode the mark. This row did not change the engine's
behaviour — that is not its place to decide, and the engine's mark tables
have their own owner (B5-1995). The open question is whether the mark is a
rule the rulebook supports. It is filed rather than resolved.

---

## 15. What the next rows inherit

**WP-11 (B5-0034, Groups pilot).** The schema is ready for it: a group entry
is an entry with `kind` ONGOING or TRIGGERED, a `target`, and an ordered
`operations` list. Five of the 17 v0 cards are Groups, and the three
printing-scope changes the Groups carry (Wind Swords, Secret Police, War
College, Commercial Telepaths) are already encoded. What WP-11 must still
do: cover the remaining 46 group titles, and record a golden-master scenario
per legacy title before porting.

**WP-12 (B5-0035, Enhancements pilot).** Four v0 cards are Enhancements, and
the `StatBonus` reuse and the "no two copies on one target" rule
(rulebook :518) map onto the `target` and `stacking` fields as they stand.

**A note on the title counts.** The proposal's "51 group titles" and "78
enhancement titles" are the two printings merged (26 + 25 = 51, 41 + 37 = 78).
The v0 data uses **canonical ids** (the Premiere id, `de_` stripped by
`CardIdNamespaceGuard.canonicalKey`), and the probe and the tooling probe
both resolve card ids that way, so a pilot can key on one id per title and
still record the governing printing.

**A suggested next row (not taken here).** The registry currently publishes
**zero** bespoke handlers, which is the strongest statement the schema can
make about the size of the bespoke tail — but it also means the first card
that genuinely needs bespoke behaviour will have nowhere to go. When the
pilots measure that tail, the row that publishes the first handler should
also record how many cards use the hatch, so the "small keyed escape hatch"
stays small.

---

## 16. Known limitations of v0, stated plainly

1. **The data covers 17 of 446 titles (3.8 %).** It is a skeleton whose
   purpose is to prove the three acceptance clauses, not to replace
   `CardEffects`.
2. **The `window` field is the fuzziest of the fourteen.** A3 calls the
   shape "a hypothesis"; the window's meaning ("the round or step in which
   the effect is applied or fires") is an interpretation this row made, and
   the pilots may revise it.
3. **`RESTRICT`'s `restriction` parameter and `STATE_*`'s `state` parameter
   are free text**, not closed enums, because the set of things a card can
   forbid or be is the set of things the rulebook allows, and the rulebook
   does not close it. The v0 data uses three restriction names
   (`NO_ATTACK_BETWEEN_THESE_PLAYERS`, `NO_CONFLICT_INITIATION`,
   `NO_HEALING_AFTER_CONFLICT`) and no states.
4. **The JSON-Schema validator implements a subset of draft 2020-12.** It
   says so in its own docstring, and it refuses to run rather than silently
   skipping a keyword it does not implement.
5. **The tooling probe skips loudly if no Python is on `PATH`.** It prints a
   `SKIPPED` banner and exits 0 rather than reporting a pass it did not
   earn; the engine's own fail-closed loader is still checked by the Java
   gate class in that case.
6. **This row is not independently assessed.** A different-family reviewer
   has not reviewed it. Per AGENTS.md §1, that is recorded rather than papered
   over, and a same-family review would not be an assessment.
