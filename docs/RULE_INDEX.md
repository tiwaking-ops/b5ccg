---
document:
  title: "Rule-ID index for BABYLON5_CCG_RULEBOOK.md"
  status: "Generated truth table. Regenerate with .agent/tools/build-rule-index.ps1 (WP-18a); do not hand-edit the generated columns."
  index_version: "1.0"
  rulebook_sha256: "1c99c44b7b64c8927f1e952a22a49d5886788da2d08e211092b5558e154716d0"
  rulebook_lines: 1236
  rows: 87
provenance:
  author_llm: {name: "opencode (tencent/hy4-preview)", version: "opencode-hy4-preview-02"}
  created_date: "2026-10-17"
  task: "B5-0022"
  generator: ".agent/tools/build-rule-index.ps1 (WP-18a)"
  hand_kept_columns: "Scope (provisional) and Notes; keyed by ID inside the generator"
---

# Rule-ID index

Every part of the pinned rulebook has one citation ID of the form
`RB:<chapter>/<slug>`, with an optional paragraph ordinal for long entries:
`RB:V#7` is the seventh block after heading RB:V, where a block is a maximal run
of non-blank lines. IDs are never renumbered and never reused. If a future re-pin
removes a heading, its ID is retired in place and a migration table is added,
because every citation already written has to keep resolving.

The rulebook body is never edited (AGENTS.md section 2), so IDs cannot be written
into it. This table is the mapping between an ID and the lines it means. It is
machine-derived from the pinned bytes: the generator refuses to run against any
other revision of that file, so an ID always names the same lines of the same
bytes.

Cite by ID in new text. A legacy `RB:<line>` citation stays valid and resolves by
range lookup against the Lines column below.

**Scope tags are inferred from heading titles, unconfirmed by the owner.**

## Columns

| Column | Kept by | Meaning |
|---|---|---|
| ID | generator | `RB:<chapter>/<slug>`. A chapter heading carries no slug: `RB:V`. |
| Heading | generator | The heading text in the pinned copy. |
| Lines | generator | Inclusive line range. The rows tile lines 1-1236 with no gap and no overlap. |
| Blocks | generator | Maximal runs of non-blank lines in the body, so an ordinal `RB:<id>#n` is checkable. The heading line itself is not a block. |
| Scope (provisional) | hand | Which rule-slice the section belongs to. `-` means the row is a non-normative division and has no scope. |
| Notes | hand | Open questions for the owner. |

## Divisions

Four divisions tile the file, and one row of the Index below belongs to each:

* `RB:FM`, lines 1-121: front matter - the wrapper header, two title lines and
  the Table of Contents. Non-normative.
* `RB:I` to `RB:VI`, lines 122-1135: the six numbered chapters, 84 entries.
* `RB:GL`, lines 1136-1215: the glossary. Per-term IDs (`RB:GL/<term>`) come in
  a later pass.
* `RB:BM`, lines 1216-1236: Contacting Us, Rules Questions and Support, and the
  Prec-Reps Demo-Tourney Program. Non-normative.

The Index below is the only table in this file whose rows begin with a citation
ID, so taking every row that starts with `RB:` yields exactly one complete,
non-overlapping tiling of the rulebook and nothing else.

## Regenerating

    powershell -NoProfile -File .agent/tools/build-rule-index.ps1

`-Check` asserts the file is already current, which is how "a second run produces
no diff" is demonstrated. Scope (provisional) and Notes are hand-kept inside the
generator, keyed by ID, so they survive every regeneration.

## Index

| ID | Heading | Lines | Blocks | Scope (provisional) | Notes |
|---|---|---|---|---|---|
| RB:FM | Front matter (title lines and Table of Contents) | 1–121 | 14 | - | Non-normative; four headings folded into one division. |
| RB:I | I. Introduction | 122–123 | 0 | S1 |  |
| RB:I/game-overview | Game Overview | 124–129 | 2 | S1 |  |
| RB:I/cards-and-tokens | Cards and Tokens | 130–139 | 4 | S1 |  |
| RB:I/anatomy-of-a-card | Anatomy of a Card | 140–164 | 4 | S1 |  |
| RB:I/influence | Influence | 165–172 | 3 | S1 |  |
| RB:I/victory | Victory | 173–188 | 6 | S1 |  |
| RB:II | II. Preparing to Play | 189–190 | 0 | S1 |  |
| RB:II/customizing-your-game-deck | Customizing Your Game Deck | 191–209 | 6 | S1 |  |
| RB:II/choosing-your-race | Choosing Your Race | 210–225 | 7 | S1 |  |
| RB:II/your-starting-hand | Your Starting Hand | 226–257 | 15 | S1 |  |
| RB:II/shuffle-and-cut | Shuffle & Cut | 258–261 | 1 | S1 |  |
| RB:II/preparing-the-playing-field | Preparing the Playing Field | 262–273 | 5 | S1 |  |
| RB:II/set-tension-tokens | Set Tension Tokens | 274–285 | 5 | S1 |  |
| RB:II/set-influence-tokens | Set Influence Tokens | 286–291 | 2 | S1 |  |
| RB:II/influence-changes | Influence Changes | 292–299 | 3 | S1 |  |
| RB:II/beginning-play | Beginning Play | 300–303 | 1 | S1 |  |
| RB:II/fast-and-loose | Playing "Fast and Loose" and Resolving Timing Disputes | 304–307 | 1 | S1 |  |
| RB:III | III. Playing the Game | 308–309 | 0 | S1 |  |
| RB:III/overview | Overview | 310–331 | 8 | S1 |  |
| RB:III/narrating-the-story | Narrating the Story | 332–339 | 3 | S1 |  |
| RB:III/ready-round | The Ready Round | 340–355 | 7 | S1 |  |
| RB:III/sustained-actions | Sustained Actions | 356–359 | 1 | S1 |  |
| RB:III/ready-and-rotated-cards | Ready and Rotated Cards | 360–365 | 2 | S1 |  |
| RB:III/conflict-round | The Conflict Round | 366–375 | 4 | S1 |  |
| RB:III/sample-conflict-round | A Sample Conflict Round | 376–379 | 1 | S1 |  |
| RB:III/action-round | The Action Round | 380–403 | 5 | S1 |  |
| RB:III/resolution-round | The Resolution Round | 404–431 | 13 | S1 |  |
| RB:III/participants | Participants | 432–443 | 5 | S1 |  |
| RB:III/draw-round | The Draw Round | 444–469 | 12 | S1 |  |
| RB:IV | IV. Card Details | 470–471 | 0 | S1 |  |
| RB:IV/status-tracker-card | Status Tracker Card | 472–475 | 1 | S1 |  |
| RB:IV/character-cards | Character Cards | 476–481 | 2 | S1 |  |
| RB:IV/bringing-characters-into-play | Bringing Characters into Play | 482–485 | 1 | S1 |  |
| RB:IV/your-starting-ambassador | Your Starting Ambassador | 486–489 | 1 | S1 |  |
| RB:IV/your-ambassadors-assistant | Your Ambassador's Assistant | 490–493 | 1 | S1 |  |
| RB:IV/group-cards | Group Cards | 494–497 | 1 | S1 |  |
| RB:IV/fleet-cards | Fleet Cards | 498–501 | 1 | S1 |  |
| RB:IV/location-cards | Location Cards | 502–505 | 1 | S1 |  |
| RB:IV/enhancement-cards | Enhancement Cards | 506–519 | 6 | S1 |  |
| RB:IV/agenda-cards | Agenda Cards | 520–527 | 3 | S1 |  |
| RB:IV/event-cards | Event Cards | 528–535 | 3 | S1 |  |
| RB:IV/contingency-cards | Contingency Cards | 536–547 | 5 | S1 | Base-rules heading, but no contingency card data is in the pool; the owner may prefer S4. |
| RB:IV/conflict-cards | Conflict Cards | 548–585 | 18 | S1 |  |
| RB:IV/aftermath-cards | Aftermath Cards | 586–596 | 4 | S1 |  |
| RB:IV/type-of-conflict | Type of Conflict | 597–600 | 1 | S1 |  |
| RB:IV/participant | Participant | 601–610 | 4 | S1 |  |
| RB:IV/aftermath-example | Aftermath Example | 611–650 | 19 | S1 |  |
| RB:V | V. Action Details | 651–744 | 45 | S1 | One 45-block entry with no sub-headings; cite a paragraph as RB:V#n. |
| RB:VI | VI. Additional Rules | 745–746 | 0 | S1 |  |
| RB:VI/sample-conflict | A Sample Conflict | 747–774 | 13 | S1 |  |
| RB:VI/triggered-effects | Triggered Effects | 775–780 | 2 | S1 |  |
| RB:VI/personalities | Personalities | 781–788 | 3 | S1 |  |
| RB:VI/votes | Votes | 789–798 | 4 | S1 | The census declares a VOTE class with 0 rows, so S1 here covers a rule no card uses. |
| RB:VI/states | States | 799–804 | 2 | S1 | The census declares a STATE class with 0 rows; see RB:VI/votes. |
| RB:VI/war | War | 805–818 | 6 | S1 |  |
| RB:VI/unconditional-surrender | Unconditional Surrender | 819–826 | 3 | S1 |  |
| RB:VI/legal-targets | Legal Targets | 827–833 | 2 | S1 |  |
| RB:VI/marks | Marks | 834–847 | 6 | S1 | The census declares a MARK class with 0 rows; mark behaviour may be filed under ONE_SHOT or TRIGGERED. |
| RB:VI/discards | Discards | 848–853 | 2 | S1 |  |
| RB:VI/bonuses-and-blanking | Bonuses and Blanking | 854–859 | 2 | S1 |  |
| RB:VI/cumulative-effects | Cumulative Effects | 860–863 | 1 | S1 |  |
| RB:VI/rules-of-precedence | Rules of Precedence | 864–873 | 4 | S1 |  |
| RB:VI/league-of-non-aligned-worlds | The League of Non-Aligned Worlds | 874–917 | 19 | S2 |  |
| RB:VI/alternate-factions | Alternate Factions | 918–959 | 20 | S3 |  |
| RB:VI/dual-races | Dual Races | 960–971 | 5 | S3 |  |
| RB:VI/tension-and-unrest | Tension and Unrest | 972–979 | 3 | S3 |  |
| RB:VI/voting | Voting | 980–983 | 1 | S3 |  |
| RB:VI/joint-effects | Joint Effects | 984–989 | 2 | S3 |  |
| RB:VI/states-dual-races | States (Dual Races) | 990–993 | 1 | S3 |  |
| RB:VI/civil-war | Civil War | 994–1013 | 9 | S3 |  |
| RB:VI/captured-locations | Captured Locations | 1014–1017 | 1 | S3 |  |
| RB:VI/power-and-victory-optional-rule | Power & Victory (Optional Rule) | 1018–1021 | 1 | S3 | Labelled optional; the rule-set profile should list it by ID and default it off unless the owner says otherwise. |
| RB:VI/more-factions | More Factions | 1022–1025 | 1 | S4 |  |
| RB:VI/psi-corps | Psi Corps | 1026–1037 | 5 | S4 |  |
| RB:VI/negative-power | Negative Power | 1038–1041 | 1 | S4 |  |
| RB:VI/severed-dreams | Severed Dreams New Rules and Clarifications | 1042–1045 | 1 | S4 |  |
| RB:VI/reducing-power-needed-to-win | Reducing Power Needed to Win | 1046–1051 | 2 | S4 |  |
| RB:VI/khari-characters | Kha'Ri Characters | 1052–1055 | 1 | S4 |  |
| RB:VI/cards-with-no-printed-cost | Cards With No Printed Cost | 1056–1065 | 4 | S4 | May settle open question Q5 (the four cost-less characters); whether it is expansion-specific is unconfirmed. |
| RB:VI/errata | Errata | 1066–1089 | 11 | S4 |  |
| RB:VI/wheel-of-fire | Wheel of Fire New Rules and Clarifications | 1090–1093 | 1 | S5 |  |
| RB:VI/drakh | The Drakh | 1094–1111 | 8 | S5 |  |
| RB:VI/interstellar-alliance | The Interstellar Alliance | 1112–1125 | 5 | S5 |  |
| RB:VI/additional-rules-and-clarifications | Additional Rules and Clarifications | 1126–1135 | 4 | S5 |  |
| RB:GL | Glossary | 1136–1215 | 40 | - | Per-term IDs of the form RB:GL/<term-slug> come in a later pass. |
| RB:BM | Back matter (Contacting Us, Rules Questions and Support, Prec-Reps Demo-Tourney Program) | 1216–1236 | 11 | - | Non-normative; three headings folded into one division. |
