---
document:
  title: "b5ccg — WP-18 Rule-ID Scheme and Draft Rule Index"
  status: "Candidate proposal. Not truth until promoted per AGENTS.md §4. Applies nothing."
author: "Claude"
version: "v1"
document_version: "1.0"
author_llm: {name: "Claude", version: "Claude Sonnet 5.5 (claude-sonnet-5-5)"}
provenance:
  created_date: "2026-10-17"
  last_modified_date: "2026-10-17"
  basis: "A list of 92 heading lines (line number plus heading text) relayed from another model (Laguna S 2.1) that read the pinned BABYLON5_CCG_RULEBOOK.md. I have not seen the rulebook body, any heading's `#` depth, and I have not verified the list against the pinned hash."
---

# b5ccg — WP-18 Rule-ID Scheme and Draft Rule Index

**Author:** Claude · **Version:** v1 · **Date:** 2026-10-17

Evidence tags: [V] checked against the relayed heading list, [I] my inference from heading titles only, [?] needs the rulebook or owner.

## 1. Why the scheme looks like this

Three facts shape it.

1. **The rulebook body is never edited** (AGENTS.md §2, SHA-256 pinned). So rule IDs cannot be written into it as anchors. They have to live in a separate index file that maps each ID to a place in the pinned copy.
2. **Heading depth is unknown.** The relayed list gives line numbers and titles, not `#` levels. A scheme that needs parent-child nesting would rest on a guess. This one uses only the six numbered chapters (I to VI) plus the section slug, so depth is irrelevant.
3. **Chapter V, "Action Details", is 94 lines with no sub-headings** [V: lines 651 to 744]. Section-level citation is too coarse there, so the scheme includes a paragraph ordinal.

## 2. The scheme

**Form:** `RB:<chapter>/<slug>`, with an optional `#<n>` paragraph ordinal.

* **Chapter** is `I` to `VI` for the numbered chapters, `FM` for the front matter (lines 1 to 121), `GL` for the Glossary and `BM` for the back matter (lines 1216 to 1236). Chapter headings themselves are cited as `RB:I`, `RB:II` and so on.
* **Slug** is made from the heading text: lowercase; drop a leading "a", "an" or "the"; remove punctuation and apostrophes; "&" becomes "and"; spaces become hyphens. Example: "The League of Non-Aligned Worlds" becomes `league-of-non-aligned-worlds`.
* **Overrides:** a slug over about 40 characters may be shortened by hand, recorded in the index. Two cases here: `fast-and-loose` and `severed-dreams`.
* **Uniqueness:** a slug must be unique within its chapter. A collision gets a `-2` suffix. None occurs in this heading list [V]. The look-alike pairs (Votes and Voting, States and States (Dual Races), Participant and Participants) differ in slug or chapter.
* **Paragraph ordinal:** `#n` is the nth block after the heading, where a block is a maximal run of non-blank lines. Because the body is never edited, ordinals are stable against the pinned copy. Example: `RB:V#7`, or `RB:VI/marks#2`.
* **Never reused:** an ID is never renumbered or reassigned. If a repin removes a heading, its ID is retired in the index, not reused. A repin gets a new index version plus a migration table (old ID to new ID).
* **Legacy citations:** `RB:<digits>` (the interim line-number form) stays valid in history. A line number maps to an ID by range. Roman-numeral chapters cannot collide with digits. History is not rewritten; new text uses IDs.

**Division rules for lines that are not rules:**

* `RB:FM` covers lines 1 to 121 as one non-normative division (wrapper header, two title lines, Table of Contents).
* `RB:BM` covers lines 1216 to 1236 as one non-normative division (Contacting Us, Rules Questions and Support, Prec-Reps Demo-Tourney Program).
* `RB:GL` covers lines 1136 to 1215. Per-term IDs of the form `RB:GL/<term-slug>` come in a later pass; the heading list does not show the terms.

## 3. Draft index (84 rule entries, lines 122 to 1135)

The ID and heading columns follow the relayed list [V]. The line range runs from the heading to the line before the next heading [I, derived arithmetic]. The scope tag uses the S1 to S5 ladder from the first handoff and is [I] (provisional, from titles only). The notes flag items for the owner.

| ID | Heading | Lines | Scope |
|---|---|---|---|
| RB:I | I. Introduction | 122–123 | S1 |
| RB:I/game-overview | Game Overview | 124–129 | S1 |
| RB:I/cards-and-tokens | Cards and Tokens | 130–139 | S1 |
| RB:I/anatomy-of-a-card | Anatomy of a Card | 140–164 | S1 |
| RB:I/influence | Influence | 165–172 | S1 |
| RB:I/victory | Victory | 173–188 | S1 |
| RB:II | II. Preparing to Play | 189–190 | S1 |
| RB:II/customizing-your-game-deck | Customizing Your Game Deck | 191–209 | S1 |
| RB:II/choosing-your-race | Choosing Your Race | 210–225 | S1 |
| RB:II/your-starting-hand | Your Starting Hand | 226–257 | S1 |
| RB:II/shuffle-and-cut | Shuffle & Cut | 258–261 | S1 |
| RB:II/preparing-the-playing-field | Preparing the Playing Field | 262–273 | S1 |
| RB:II/set-tension-tokens | Set Tension Tokens | 274–285 | S1 |
| RB:II/set-influence-tokens | Set Influence Tokens | 286–291 | S1 |
| RB:II/influence-changes | Influence Changes | 292–299 | S1 |
| RB:II/beginning-play | Beginning Play | 300–303 | S1 |
| RB:II/fast-and-loose | Playing "Fast and Loose" and Resolving Timing Disputes | 304–307 | S1 |
| RB:III | III. Playing the Game | 308–309 | S1 |
| RB:III/overview | Overview | 310–331 | S1 |
| RB:III/narrating-the-story | Narrating the Story | 332–339 | S1 |
| RB:III/ready-round | The Ready Round | 340–355 | S1 |
| RB:III/sustained-actions | Sustained Actions | 356–359 | S1 |
| RB:III/ready-and-rotated-cards | Ready and Rotated Cards | 360–365 | S1 |
| RB:III/conflict-round | The Conflict Round | 366–375 | S1 |
| RB:III/sample-conflict-round | A Sample Conflict Round | 376–379 | S1 |
| RB:III/action-round | The Action Round | 380–403 | S1 |
| RB:III/resolution-round | The Resolution Round | 404–431 | S1 |
| RB:III/participants | Participants | 432–443 | S1 |
| RB:III/draw-round | The Draw Round | 444–469 | S1 |
| RB:IV | IV. Card Details | 470–471 | S1 |
| RB:IV/status-tracker-card | Status Tracker Card | 472–475 | S1 |
| RB:IV/character-cards | Character Cards | 476–481 | S1 |
| RB:IV/bringing-characters-into-play | Bringing Characters into Play | 482–485 | S1 |
| RB:IV/your-starting-ambassador | Your Starting Ambassador | 486–489 | S1 |
| RB:IV/your-ambassadors-assistant | Your Ambassador's Assistant | 490–493 | S1 |
| RB:IV/group-cards | Group Cards | 494–497 | S1 |
| RB:IV/fleet-cards | Fleet Cards | 498–501 | S1 |
| RB:IV/location-cards | Location Cards | 502–505 | S1 |
| RB:IV/enhancement-cards | Enhancement Cards | 506–519 | S1 |
| RB:IV/agenda-cards | Agenda Cards | 520–527 | S1 |
| RB:IV/event-cards | Event Cards | 528–535 | S1 |
| RB:IV/contingency-cards | Contingency Cards | 536–547 | S1 (see note 3) |
| RB:IV/conflict-cards | Conflict Cards | 548–585 | S1 |
| RB:IV/aftermath-cards | Aftermath Cards | 586–596 | S1 |
| RB:IV/type-of-conflict | Type of Conflict | 597–600 | S1 |
| RB:IV/participant | Participant | 601–610 | S1 |
| RB:IV/aftermath-example | Aftermath Example | 611–650 | S1 |
| RB:V | V. Action Details | 651–744 | S1 (see note 2) |
| RB:VI | VI. Additional Rules | 745–746 | S1 |
| RB:VI/sample-conflict | A Sample Conflict | 747–774 | S1 |
| RB:VI/triggered-effects | Triggered Effects | 775–780 | S1 |
| RB:VI/personalities | Personalities | 781–788 | S1 |
| RB:VI/votes | Votes | 789–798 | S1 (see note 4) |
| RB:VI/states | States | 799–804 | S1 (see note 4) |
| RB:VI/war | War | 805–818 | S1 |
| RB:VI/unconditional-surrender | Unconditional Surrender | 819–826 | S1 |
| RB:VI/legal-targets | Legal Targets | 827–833 | S1 |
| RB:VI/marks | Marks | 834–847 | S1 (see note 4) |
| RB:VI/discards | Discards | 848–853 | S1 |
| RB:VI/bonuses-and-blanking | Bonuses and Blanking | 854–859 | S1 |
| RB:VI/cumulative-effects | Cumulative Effects | 860–863 | S1 |
| RB:VI/rules-of-precedence | Rules of Precedence | 864–873 | S1 |
| RB:VI/league-of-non-aligned-worlds | The League of Non-Aligned Worlds | 874–917 | S2 |
| RB:VI/alternate-factions | Alternate Factions | 918–959 | S3 |
| RB:VI/dual-races | Dual Races | 960–971 | S3 |
| RB:VI/tension-and-unrest | Tension and Unrest | 972–979 | S3 |
| RB:VI/voting | Voting | 980–983 | S3 |
| RB:VI/joint-effects | Joint Effects | 984–989 | S3 |
| RB:VI/states-dual-races | States (Dual Races) | 990–993 | S3 |
| RB:VI/civil-war | Civil War | 994–1013 | S3 |
| RB:VI/captured-locations | Captured Locations | 1014–1017 | S3 |
| RB:VI/power-and-victory-optional-rule | Power & Victory (Optional Rule) | 1018–1021 | S3 (optional, see note 5) |
| RB:VI/more-factions | More Factions | 1022–1025 | S4 |
| RB:VI/psi-corps | Psi Corps | 1026–1037 | S4 |
| RB:VI/negative-power | Negative Power | 1038–1041 | S4 |
| RB:VI/severed-dreams | Severed Dreams New Rules and Clarifications | 1042–1045 | S4 |
| RB:VI/reducing-power-needed-to-win | Reducing Power Needed to Win | 1046–1051 | S4 |
| RB:VI/khari-characters | Kha'Ri Characters | 1052–1055 | S4 |
| RB:VI/cards-with-no-printed-cost | Cards With No Printed Cost | 1056–1065 | S4 (see note 6) |
| RB:VI/errata | Errata | 1066–1089 | S4 |
| RB:VI/wheel-of-fire | Wheel of Fire New Rules and Clarifications | 1090–1093 | S5 |
| RB:VI/drakh | The Drakh | 1094–1111 | S5 |
| RB:VI/interstellar-alliance | The Interstellar Alliance | 1112–1125 | S5 |
| RB:VI/additional-rules-and-clarifications | Additional Rules and Clarifications | 1126–1135 | S5 |

Counts [V against the list, arithmetic]: 92 heading lines = 4 front matter (lines 1, 25, 27, 29) + 84 rule entries above + 1 glossary (1136) + 3 back matter (1216, 1226, 1232). The 84 are 6 chapter headings plus 78 section headings.

**Notes**

1. **Where an entry nests** (for example whether "Participants" at line 432 sits under "The Resolution Round") is not needed by the scheme and is not recorded.
2. **Chapter V** is one 94-line entry. It is the only place the paragraph ordinal is needed for coverage; larger entries elsewhere top out at 44 lines (the League, 874 to 917).
3. **Contingency Cards** have a base-rules heading but no card data in the pool (risk R12, likely intentional). They are tagged S1 because the heading sits in chapter IV, but the owner may prefer S4.
4. **Votes, States and Marks** sit in base-rules territory [I]. The census taxonomy declares VOTE, STATE and MARK classes, and all three have 0 rows. So S1 coverage includes rules the census says no card uses, and the earlier suspicion that mark behaviour is filed under ONE_SHOT or TRIGGERED gains weight.
5. **Power & Victory** is labelled an optional rule. The rule-set profile stub (B5-0002) should list it by ID and default it off unless the owner says otherwise.
6. **Cards With No Printed Cost** (10 lines, 1056 to 1065) may answer open question Q5, whether the four cost-less characters are starting ambassadors. It sits inside the Severed Dreams section, so it may be expansion-specific. Reading those 10 lines settles it either way.

## 4. Coverage definition (for the Playable Slice target)

* **Denominator:** S1 entries that carry rules. The 62 S1 entries minus the five chapter-heading containers of 2 lines each (RB:I, RB:II, RB:III, RB:IV, RB:VI) is **57**. RB:V stays in because it has real content.
* **Covered:** cited by at least one conformance check or probe.
* **Target:** the Playable Slice asks for at least 90%, which is 52 of 57. The target applies from the first measured baseline; this proposal sets no number for the gate.
* **Entries over 40 lines:** only RB:V needs paragraph-level coverage, so for RB:V the unit is the paragraph, and the tool reports its paragraph count.

## 5. Tasks (ids are placeholders; the planner assigns the next free ids with `append-ledger.ps1`)

| Row | Task | Scope | Acceptance (binding) |
|---|---|---|---|
| WP-18a | Index builder. A tool reads the pinned rulebook and regenerates the ID, heading and line-range columns of `docs/RULE_INDEX.md`. The scope and notes columns are hand-kept and survive regeneration, keyed by ID | tooling | Refuses to run if the rulebook hash differs from the pin; heading set equals the 92 lines and numbers in this proposal or the tool lists each difference; every rule entry has exactly one unique ID; line ranges cover lines 1 to 1236 with no gap and no overlap; a second run produces no diff; `docs/RULE_INDEX.md` records the rulebook hash and an index version |
| WP-18b | Citation checker. Extend `check-citations.ps1` so `RB:<chapter>/<slug>` and `RB:…#n` must resolve in the index, and legacy `RB:<digits>` resolves by range | tooling | A citation of a missing ID fails (non-zero exit); a legacy line number resolves and the checker prints the ID it maps to; existing citations in DECISIONS and reports are not edited; the existing checks still pass |
| WP-18c | Coverage report. A convention for a conformance check or probe to name the IDs it exercises, plus a report listing S1 entries with zero citations | tooling | Report prints covered, uncovered and the percentage against the 57-entry denominator; records the first baseline; changes no gate verdict; no edits to the conformance suite beyond adding the citation annotations to checks that already exist |

WP-18a depends on nothing. WP-18b and WP-18c depend on WP-18a.

## 6. Text to apply when WP-18a lands

Replace the interim "Rule-IDs" paragraph in AGENTS.md §5 with the following. Needs a DECISIONS entry and owner approval.

> **Rule-IDs.** Rulebook sections are cited as `RB:<chapter>/<slug>` (for example `RB:III/conflict-round`), with an optional paragraph ordinal (`RB:V#7`). The table is `docs/RULE_INDEX.md`, generated from the pinned rulebook. IDs are never renumbered or reused. Legacy `RB:<line>` citations stay valid in history; new text uses IDs. Every DECISIONS interpretation cites at least one ID.

The rule-set profile (A9, stub from B5-0002) records the enabled optional and expansion sections by ID and replays carry it.

## 7. Owner decisions and checks

| # | Item | Why |
|---|---|---|
| 1 | Check the 92-line list against the pinned copy: the heading set must come out at 92 and the line numbers must match | Taken second-hand from another model; WP-18a's tool does this check, so it can wait for that |
| 2 | Confirm or change the scope tags, especially contingencies (S1 or S4), the S3 block given decision D1 (single human), and the placement of Errata (S4 here) | Titles only; I haven't read the text |
| 3 | Confirm FM, BM and GL are treated as non-normative divisions, with glossary terms indexed later | Keeps the first index small |
| 4 | Paste lines 1056 to 1065 of the rulebook | May settle Q5 |
| 5 | Paste a glossary term list when ready, for `RB:GL/<term>` | The heading list shows no terms |
