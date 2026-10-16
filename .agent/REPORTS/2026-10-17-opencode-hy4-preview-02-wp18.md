---
document:
  title: "B5-0022 / B5-0023 / B5-0024 - WP-18 rule-ID scheme: index builder, citation checker, coverage baseline"
  status: "Observation only - no authority. Truth lives in b5ccg/src, the pinned rulebook, AGENTS.md and docs/DECISIONS.md."
  placement: ".agent/REPORTS/ (observations per AGENTS.md section 2)"
provenance:
  author_llm: {name: "opencode (tencent/hy4-preview)", version: "opencode-hy4-preview-02"}
  created_date: "2026-10-17"
  last_modified_date: "2026-10-17"
---

# WP-18 rule-ID scheme - index, checker and first coverage baseline

Agent id `opencode-hy4-preview-02`. Claims held: B5-0022 (tooling, docs),
B5-0023 (tooling) and B5-0024 (tooling, docs), all released at the end of the
session. Source proposal
`docs/proposals/claude-b5ccg-rule-id-scheme-v1.md`, applied with owner
approval on 2026-10-17. Nothing was committed; AGENTS.md section 5 forbids
agent commits.

## 0. What the owner decided, and how it changed the work

Two answers from the owner shaped this session and are recorded in
`docs/DECISIONS.md`:

* **WP-18c moved to a sidecar.** Coverage comes from
  `docs/RULE_COVERAGE_MAP.md`, not from annotations inside the conformance
  suite, so **zero `.java` files change**. A check covers a rule when the map
  says so. Inline `// RB:<id>` comments stay allowed for later and will be read
  too, but nothing requires them. This replaces the proposal's acceptance item
  "no edits to the conformance suite" with: the tool fails on any mapped check
  name absent from the latest gate log, and on any ID absent from
  `docs/RULE_INDEX.md`.
* **The scope column** is named `Scope (provisional)` with a single header line
  in the index reading *"Scope tags are inferred from heading titles,
  unconfirmed by the owner."* No per-row provisional markers. A later
  confirmation renames the column and drops that line in one edit.

## 1. Checks done before building anything

The proposal's input was relayed second-hand from another model, so its
central claim was re-measured rather than trusted:

* all **92 headings** and their line numbers match the pinned rulebook exactly;
* all **84** derived IDs, titles and line ranges match its section 3 table;
* the section 4 denominator, re-derived independently: 62 S1 rows minus the
  five two-line chapter containers = **57**.

Row ids B5-0022/0023/0024 were assigned because B5-0021 is deliberately held
by the ledger's Future block.

## 2. B5-0022 - the index builder

`.agent/tools/build-rule-index.ps1` regenerates `docs/RULE_INDEX.md` from the
pinned bytes. Refuses rather than adapts:

* rulebook SHA-256 must equal the pin in AGENTS.md section 2;
* total line count must be 1236;
* the heading set must equal the 92 golden headings in line, depth and title -
  otherwise every difference is listed;
* exactly one heading may carry non-ASCII text (line 1 has an em dash), which
  keeps future mojibake of the kind `docs/RULEBOOK_PIN.md` records from passing
  unnoticed;
* derived ranges must tile lines 1-1236 with no gap and no overlap;
* no two entries may derive the same ID, a long slug needs a hand-recorded
  override instead of a silent truncation, an unused override is a failure, and
  an ID with no scope tag is a failure.

Result: 87 rows (FM + 84 rule entries + GL + BM), `index_version 1.0`, hash and
line count recorded in the front matter. **Idempotent**: a second run rewrites
nothing and `-Check` exits 0 against an unchanged file.

Generated columns are ID, Heading, Lines and Blocks; Scope (provisional) and
Notes are hand-kept in the script, keyed by ID, and survive regeneration.

Three decisions worth stating plainly:

* **Blocks column added.** Not in the proposal's table, but an ordinal
  `RB:<id>#n` is only meaningful against a known block count, so the tool now
  publishes it: `RB:V` has 45 blocks, every other entry at most 20.
* **`## Divisions` table removed.** My first draft had a summary table whose
  rows also began `| RB:FM |`, which made the file ambiguous for any tool that
  greps for ID rows - my own verification script silently picked up the wrong
  rows. The divisions are prose now, and the index is the only table whose rows
  begin with an ID.
* **Pure ASCII scripts.** Every script in `.agent/tools/` is ASCII without a
  BOM and Windows PowerShell 5.1 mis-reads non-ASCII literals from BOM-less
  files, so the en dash in the Lines column is emitted as a character code and
  the one non-ASCII heading is folded before comparison. Verified: all three
  scripts contain zero bytes above 127.

## 3. B5-0023 - the citation checker

`.agent/tools/check-citations.ps1` gained a rule-ID phase alongside its
existing REPORTS/PATTERNS check:

* an ID absent from the index fails (exit 1), naming up to three IDs in the same
  division as likely intended targets;
* an ordinal past the entry's block count fails: `RB:V#99` reports "outside
  1-45";
* every legacy `RB:<line>` prints the ID and heading it maps to;
* a missing index is exit 2, never a quiet pass;
* a defect in either phase is exit 1 - neither can be silenced by the other
  passing.

Proven with a fixture carrying four planted defects (unknown ID, unknown slug,
out-of-range ordinal, out-of-range legacy line) and three legacy numbers. All
four were caught and all three legacy numbers resolved: `RB:834` to
`RB:VI/marks`, `RB:1` to `RB:FM`, `RB:1236` to `RB:BM`.

Default sources still pass clean. Note the existing `RB:` hits in DECISIONS,
the ledger and AGENTS.md are prose *describing* the form (`RB:<line>`,
`RB:chapter-slug`), not citations, and are correctly ignored - a wider pattern
would have turned the ledger row that specifies this tool into a defect.

**One real bug caught while testing.** The roman-numeral alternation was
written shortest-first (`I|II|III|IV|V|VI`), so `RB:III/conflict-round` matched
as `RB:I` and `RB:IV/nope` resolved against `RB:IV`: invalid IDs passed
silently, which is the one failure this phase exists to prevent. It is now
longest-first with a token boundary, and the fixture proves it.

## 4. B5-0024 - the coverage sidecar and the first baseline

`docs/RULE_COVERAGE_MAP.md` maps 83 rows: 65 `verified`, 18 `unverified`.
Only verified rows count. A row is verified when the rulebook paragraph was
read and states the rule the check enforces - the evidence is written into the
row. Unverified rows are mostly probes whose own annotation pointed at a
different rule than the check's subject describes; they are listed rather than
counted, so a guess can raise a follow-up row and never a percentage.

Where the first batch came from: 69 of the gate's 904 checks already carry a
`(:<line>)` annotation written by the probe authors (`HeadlessCivilWarProbe`
documents the convention), which makes those mappings checkable rather than
invented. Each was accepted only after reading the cited paragraph and its
neighbours - several annotations were off by a few lines but stayed inside the
same entry, which is noted in the row. The four `[D1]` aftermath checks were
added from the owner's suggestion and verified directly against lines 594-595.
Marks are mapped but deliberately **unverified**: the gate's "marker" checks
look like capture mechanics and a Strife damage rule, not the mark attachment
rules at lines 836-844.

`.agent/tools/rule-coverage.ps1` output, first baseline:

```
denominator: 57 S1 entries carrying rules (62 S1 rows minus 5 two-line chapter containers)
covered    : 13
uncovered  : 44
coverage   : 22.8%  (Playable Slice target 90% >= 52 of 57)
```

Covered: RB:I/victory, RB:II/customizing-your-game-deck,
RB:II/preparing-the-playing-field, RB:II/set-tension-tokens,
RB:III/participants, RB:IV/bringing-characters-into-play,
RB:IV/your-ambassadors-assistant, RB:IV/group-cards, RB:IV/fleet-cards,
RB:IV/location-cards, RB:IV/agenda-cards, RB:IV/aftermath-cards, RB:V.

Both hard failures were proven with planted rows: an unknown check name
("check not in the gate log"), an unknown ID, and an illegal status value all
exit 1 and suppress the figures entirely. Missing index, map or gate log exit
2. Auto-discovery finds the newest `*_gate.log` in the repo root.

## 5. Verification gate (AGENTS.md section 4)

`RUN_TESTS=1 sh compile.sh` with the staged JDK 25
(`b5ccg/scratch/jdk25-win/jdk-25.0.4.1+1/bin` prepended to PATH), log
`_b5-0024_gate.log` (gitignored via `*.log`):

* exit **1**, 904 check lines, **900 PASS / 4 FAIL**;
* failing set is **identical** to the recorded baseline and to my pre-change
  capture: `[AES] HARD initiates the losing matchup when 6 aftermaths
  anticipate the win`, `[AIJ] HARD initiates against the weak target, never the
  leader`, `[MINES] reactive mines adds +1 return damage (fleet)`,
  `[MINES] round-trip: mines played then attack triggers +1 return damage`.

Compared by verdict and failing-name set, not by byte equality. Nothing new
failed and nothing newly passed silently, so the change passes the gate under
the recorded clause. B5-0017 still owns repairing those four.

Also run clean at the end: `run-dup-census.ps1` (0 duplicate task ids) and
`check-citations.ps1` over the ledger, DECISIONS, AGENTS.md and both new docs
(186 `RB:` references, all resolving).

## 6. Files touched

* new: `.agent/tools/build-rule-index.ps1`, `.agent/tools/rule-coverage.ps1`,
  `docs/RULE_INDEX.md`, `docs/RULE_COVERAGE_MAP.md`
* modified: `.agent/tools/check-citations.ps1` (rule-ID phase added; existing
  phase untouched), `AGENTS.md` section 5, `docs/DECISIONS.md` (entry appended)
* **no `.java`, no rulebook, no card data, no build-script behaviour changed.**

## 7. Left for later

* The 18 unverified map rows need a human read each; every one is a claim about
  a specific check and lists what was unclear.
* Follow-up rows should add the inline annotations, one scope per row, in a
  quiet window, as the owner directed.
* `RB:VI/marks`, `RB:VI/votes` and `RB:VI/states` are tagged S1 and uncovered,
  and the census declares zero cards in those classes - the index notes this,
  and it is worth confirming whether the tags or the taxonomy are wrong.
* Proposal owner items still open: confirm the scope tags (especially
  contingencies, S1 or S4, and Errata at S4); the glossary per-term IDs
  `RB:GL/<term>`; lines 1056-1065 may settle question Q5.
* `RB:V` is one coarse 94-line entry covering many actions; when someone needs
  finer coverage, the unit becomes its 45 paragraphs.
