---
document:
  title: "b5ccg — Claude's Second Reply: Guard-Script Review, Coverage Map, Model Inventory, Exposure Plan and Updated Tasks"
  status: "Candidate recommendations. No authority over the repo; nothing here is applied."
  purpose: "Respond to report-reply-to-claude-post-b5-0034-recommendations.md: review the files it appended, accept its N2 correction, answer its open items, and update the task plan."
author: "Claude"
version: "v2"
document_version: "2.0"
author_llm: {name: "Claude", version: "Claude Sonnet 5.5 (claude-sonnet-5-5)"}
supersedes: "Nothing. Extends reply-claude-b5ccg-post-b5-0034-recommendations-v1.md; where they differ, this document wins."
provenance:
  created_date: "2026-10-19"
  last_modified_date: "2026-10-19"
  basis: "report-reply-to-claude-post-b5-0034-recommendations.md including its Appendices A to F (RULE_COVERAGE_MAP.md, flip-ledger.ps1, run-queue.ps1, ledger-query.ps1 and new-claim.ps1 excerpts, a dry-run output). I read the scripts as text; I did not run them. I have not seen the repo, the gate logs or the rulebook."
---

# b5ccg — Claude's Second Reply

**Author:** Claude · **Version:** v2 · **Date:** 2026-10-19

Evidence tags: **[V]** read in the supplied files, **[I]** my inference, **[?]** needs a file or an owner answer.

## 1. Summary

1. **N2 retraction accepted, and verified by reading `run-queue.ps1`.** A `B5-0029.RELEASED.json` marker is keyed as `B5-0029.RELEASED` and can never equal the row id `B5-0029`. My v1 reply endorsed N2 as stated without having seen the code; that was my error.
2. **`flip-ledger.ps1` has eleven defects, not one.** N1 is real, and the id check is also dead code. The fix list is in section 3 and should become the acceptance for the tooling row.
3. **The coverage number is both too high and too low.** `RB:V` counts as one fully covered 94-line entry on the strength of about six paragraphs, which flatters the figure. But the map only includes checks from the conformance suite, so 29 other gate classes with hundreds of checks contribute nothing, which understates it. Neither is fixable by arithmetic; both need small map changes (section 4).
4. **The unverified map rows cannot close the gap.** Verifying all 18 adds at most one S1 entry (`RB:VI/marks`). Reaching 52 of 57 needs roughly 35 new checks.
5. **Model inventory: verify before trusting.** A catalog entry is a label. Several `kenari` ids are not the current Anthropic model strings I know, so route by a short calibration test, not by name (section 5).
6. **Exposure: the plan misses two facts.** A force-push does not remove commits that a merged pull request keeps reachable on GitHub, and making the repository private is a faster, reversible first step (section 6).
7. **Decision D-1 is fine**, with a byte-level check that makes the repair verifiable (section 7).

## 2. Assessment of the report

It does the thing I most want from these reports: it re-measures its own earlier claim, finds it wrong, says so, and shows the reproduction (nine rows offered including B5-0029, section 2.2). It also traces the warning text that misled it, and discloses that it cleared the offer-marker directory. Appendices are verbatim and unedited, which is what made the review in section 3 possible.

Two small points. First, the "all nine OPEN rows offered" result comes from a dry run with markers cleared, so it shows the code path is fine, not that the real queue was exercised. Second, the report says the marker mechanism is "the only real cross-run suppressor"; that is true of the offer path but not of claim files, which suppress by design.

My own reasoning needs one adjustment. In v1 I gave the stale `.RELEASED` files as one reason for fresh ids on the superseded rows. With N2 narrowed, that reason is weak. The other reason stands: an acceptance line is the row's contract, and rewriting it in place hides that the earlier claim of completion was false. I still recommend fresh ids with SUPERSEDED on the old rows.

## 3. Review of the guard scripts (the task H-1 acceptance)

### 3.1 `flip-ledger.ps1`

| # | Defect | Effect | Fix |
|---|---|---|---|
| F-1 | The claims directory is built as `.agent/.agent/CLAIMS` (N1) | The live-claim refusal never fires | Build every path from the `.agent` directory once, and fail loudly if the claims directory is missing instead of silencing the error |
| F-2 | `-not $Id -match '^B5-…'` parses as `(-not $Id) -match …`, so the test is on the text "False" | The id check never fires, for any non-empty value | Test `$Id -notmatch '^B5-[0-9]{4}[a-z]?$'` |
| F-3 | The report glob is relative to the caller's working directory, and `*B5-0202*` also matches `B5-0202c` reports | DONE refused from the wrong folder; a sibling id's report can satisfy DONE | Resolve from the `.agent` path; require the id to end at a boundary (not followed by a digit or letter) |
| F-4 | The backup is written before the refusal checks, with second-resolution names | Refused flips leave backups; two flips in one second overwrite one | Validate everything first, back up just before writing, use a unique suffix |
| F-5 | Any unreleased claim refuses, including the claimant's own | An agent cannot flip its own row to BLOCKED while it holds the claim | Add an `-AgentId` parameter. Refuse only if a claim exists whose `agent_id` differs; for DONE, require the claim already released. Print the owner and the claim age |
| F-6 | The first matching row wins | A duplicate id flips one row silently | Refuse when more than one row matches, as `new-claim.ps1` already does |
| F-7 | The file is rewritten with CRLF line endings throughout and a BOM-less encoding, with no check that only one line changed | A single flip can change every line of the file | Preserve the original line ending and BOM; before writing, confirm exactly one line differs |
| F-8 | Read, modify, write with no concurrency guard and no rollback | A concurrent append can be lost | Write to a temporary file, confirm the ledger is unchanged since it was read, replace atomically, run the lint, and restore the backup on a lint failure |
| F-9 | It assumes the status is the second cell | A malformed row could overwrite the wrong cell | Refuse unless the existing second cell is a valid status |
| F-10 | `-Filter "$Id*"` on the claims directory matches sibling ids | A claim on `B5-0202c` blocks `B5-0202` | Test the exact file name `<id>.json` |
| F-11 | Parameters are not mandatory, and a relative `-LedgerPath` resolves against the .NET working directory, not the shell's | Confusing failures | Make `Id` and `NewStatus` mandatory; resolve paths before use |

**Acceptance (binding), each with a planted-condition test:** a planted live claim by another agent is refused; the claimant can flip its own row to BLOCKED; a planted `.RELEASED.json` is ignored; a sibling-suffix id is neither matched nor blocked; a malformed id is rejected; a duplicated id is refused; a refused flip leaves no backup and no change; running from a different working directory works; a flip to DONE without a report is refused; line endings and BOM are preserved and exactly one line differs (checked by a diff); a concurrent change aborts the flip; the lint runs after the write; a `-DryRun` switch prints the one-line difference and writes nothing.

### 3.2 `run-queue.ps1`, `ledger-query.ps1` and `new-claim.ps1`

* **N2 sites** are `run-queue.ps1` at 173 to 174 and 654, and `ledger-query.ps1` at 229, 252, 267 and 350 to 351 [V per the report's line numbers]. Replace the `B5-*.json` filter and the `BaseName` key with a **positive match**: only a file whose base name matches `^B5-[0-9]{4}[a-z]?$` is a claim. That also covers any future marker name, where a `.RELEASED` blacklist would not. Add a planted-marker case to `census-crosscheck.ps1`, the tool that exists to prove the copies agree.
* **`new-claim.ps1`** has no directory scan, so nothing to change there. Agreed.
* **Offer markers (`run-queue.ps1` 887 to 922).** The report found one marker held by a recycled process id. The age bound applies only when the process id cannot be read, so a live unrelated process blocks forever. Make the age bound always apply (a marker only needs to bridge the offer to the claim file, so a few minutes is enough), and record the process start time so a recycled id is detected. Independently, `-DryRun` should not create markers at all. Acceptance: a marker naming a live process that started after the marker was written is treated as stale; a dry run leaves the marker directory unchanged.
* **Minor.** `Test-TerminalLedgerRow` re-reads the whole ledger and rebuilds the census for each warning. With the phantom warnings gone this stops mattering, but it is a candidate for a later cleanup.

## 4. Coverage map review and plan

`RULE_COVERAGE_MAP.md` is well made: every row states how it was checked, and unproven rows are labelled `unverified` and excluded. Its weaknesses are in what it measures.

| Finding | Detail | Recommendation |
|---|---|---|
| `RB:V` counts as one covered entry | About 30 checks map to it, but their line citations (:657, :663, :665, :669, :719, :721) fall on about six paragraphs of a 94-line chapter [V]. I defined a paragraph ordinal for exactly this case | Convert each cited line to a paragraph ordinal mechanically from the pinned rulebook, and report `RB:V` coverage as covered paragraphs over total. Until then, count `RB:V` as not covered; strict S1 coverage is **12 of 57 (21.1%)**, not 13 |
| `RB:I/victory` counts as covered on one check | A single check about Shadow War suppression stands for all the Standard Victory conditions | Add a depth column: the number of verified checks per entry. Entries over about 15 lines need at least two before they count |
| The map holds only the 904 conformance checks | Other gate classes (`HeadlessDrawRoundProbe` with 45 checks, `HeadlessWarConflictProbe`, `HeadlessConflictResolutionProbe`, `HeadlessBonusFloorProbe`, `HeadlessStationVictoryTest`, `HeadlessAftermathDetachProbe`, and more) are not mapped, so `RB:III/draw-round` reads as uncovered despite a dedicated probe [I from `compile.sh` comments] | Allow class-level rows (a probe class mapped to a rule id) that count only while the class passes in the current per-class gate table. Read each probe's header before marking a row `verified`. I expect a few more S1 entries, perhaps three to five, but that is a guess |
| The 18 unverified rows | Only `RB:VI/marks` (six rows) targets an uncovered S1 entry. The rest target S2 or S3 entries or entries already covered | Verify them by reading the cited paragraphs; the gain is one entry. For rows whose line citation falls in a different entry than their subject (the Civil War checks citing :992), try the entry the line actually falls in first |
| Effort is aimed at S2 and S3 | Many verified rows cover League and Civil War (S2 and S3) while S1 coverage is 21 to 23% | Not a fault; note it when prioritising |

**What the arithmetic now says.** The report's intersection is right: with all four prose entries exempted, (a) 30 plus (b) 16 gives 46 and the bar is 48, so it needs `I/victory` and `RB:V` covered, not "two further (c) entries". Under the strict `RB:V` rule above, neither is yet covered properly, so the practical path to 52 of 57 is about 35 new, verified checks, led by the three worked examples and chapter II.

**Tasks.**

| Task | What | Scope | Acceptance |
|---|---|---|---|
| C-1 | Extend the map and tool: class-level rows, paragraph-level `RB:V`, a depth column, verify the six `marks` rows | tooling, docs | The tool reports entry-level and `RB:V` paragraph-level coverage and the depth per entry; class-level rows count only while the class passes in the current gate table; the new baseline and its date are recorded; no `.java` changes |
| C-2 | First check batch: the three worked examples (`III/sample-conflict-round`, `IV/aftermath-example`, `VI/sample-conflict`) as scripted scenarios with the rulebook's stated outcomes, then the nine chapter II entries | engine tests | Each check cites its rule id and is mapped `verified`; coverage rises by the number of entries added; no gate verdict changes |

I need one extraction to finish this: the paragraph list for `RB:V` (ordinal, line, first eight words). It is read-only.

## 5. Model inventory (answers my O10)

The report's census is useful, and the decision (free tiers only) is yours. Two cautions.

* **A catalog id is a label, not proof.** The `kenari` free tier lists `claude-opus-5`, `claude-sonnet-5` and `claude-fable-5`. The current Anthropic API strings I know are `claude-opus-5-5`, `claude-sonnet-5-5`, `claude-fable-5-1` and `claude-haiku-5-5`, which are the ones listed under `claude-code`. So the `kenari` ids are probably router aliases or older versions, and I cannot tell which model actually answers. Record in each report the model identity as returned by the API response metadata, not the name in the prompt. Family separation for assessors depends on the label being true, so test it.
* **Privacy.** A free router may log prompts. The repository content already includes the rulebook text and card data; decide whether that is acceptable for a router you do not control.

**Calibration battery (cheap, evidence-based routing).** Before E-0 depends on any free model: take eight starter records including `bio-weapon discovery` and one STAT_MODIFIER card, with an answer key prepared by you or Claude Desktop. Give each candidate the same vocabulary definitions and records. A candidate qualifies as an author if its classifications agree on at least seven of eight and it states an ESCAPE reason when it marks one; a different qualifying model from a different provider reviews. This is a short task and replaces guessing from names.

**Recommendation on the cost decision.** Free-only is workable for tooling rows. For the two or three judgement rows (E-0 review, the effect schema, the assessor passes) the cost of a few capped Claude Code sessions is small against the cost of a wrong schema. You may want to allow that exception explicitly, with a cap, in the D-3 ruling.

## 6. Exposure plan

Measured facts from the report [V]: the repository is public; `origin/main` has 19 of 20 commits authored with your name and an email address; there are 13 personal-data hit lines in 11 files on `origin/main` and 15 in 12 files in the working tree; no secret-shaped content. Your rulings: scrub the working tree, keep content history, rewrite author metadata only, owner-operated. Four additions.

1. **Stop new exposure first.** Set the repository's local git identity to the noreply address now so no further commit carries the real one. Check your GitHub email settings for "keep my email addresses private" and the option that blocks pushes exposing it.
2. **A force-push may not remove the old commits from GitHub.** PR #1 was merged [V], and to my knowledge GitHub keeps the commits behind a pull request reachable by hash after a rewrite. If removing the address matters more than the PR history, the clean routes are to recreate the repository from the rewritten history or to ask GitHub support to purge cached views. I am not certain of the details; check GitHub's documentation on removing sensitive data first.
3. **Consider making the repository private now.** It is immediate, reversible, needs no history rewrite, and covers everything at once: the author fields, host paths, the claim and heartbeat files, and the rulebook and card text. That last item is a separate question from privacy: the tree contains a copy of the Babylon 5 CCG rulebook and card data, which are probably someone else's copyrighted material. I am not giving legal advice, but it is a reason to ask whether public is intentional. **Is it?**
4. **Mechanics if you rewrite.** Take a full mirror backup first. Freeze all claims, because every agent branch and the snapshot branch will diverge. Run the rewrite on a fresh clone with a mailmap that maps the old name and email to the noreply identity for both author and committer, and check commit messages and tags too (the report scanned file contents for the handle, not messages). Keep the old-to-new hash map and record it in the DECISIONS entry, since reports cite old hashes such as `88a5d46` and `94f2400`.

**Acceptance check for the scrub.** The proposed `git grep` for the name and path patterns will match the audit report and any DECISIONS entry that quotes the patterns, so it can never pass as written. Exclude those paths by pathspec, or build the pattern from a file that is itself excluded.

## 7. D-1: the heading repair

Replacing U+FFFD with an em dash is fine, and the repair has a clean check. U+FFFD is three bytes in UTF-8 (EF BF BD) and the em dash is also three (E2 80 94), so the file size must be unchanged and exactly 24 bytes must differ (eight characters times three). Make the edit with a tool that writes bytes explicitly, not through the shell string handling that caused the original damage, and verify with a byte comparison. Record the repair in one DECISIONS entry, and run the encoding lint afterwards.

## 8. Updated plan

Everything in v1 section 4 stands, with these changes:

| Task | Change |
|---|---|
| H-1 | Acceptance is now the section 3 list (flip tool, positive claim-file match, marker mechanism, plus the heartbeat and coverage-log items from v1) |
| H-2 | The U+FFFD repair uses the byte-level check in section 7 |
| C-1, C-2 | New (section 4) |
| M-1 | New: the calibration battery in section 5, before E-0 is assigned |
| X-1 | Owner action, not a task: the exposure steps in section 6, starting with the identity setting and the public-or-private question |
| E-0 | Author and reviewer come from the calibration step; the reviewer is a different provider |

Order: H-1; then K-1, K-2, H-2 and G-1 in parallel; M-1 and C-1 alongside; then L-1 Stage A, E-0 and C-2; then L-1 Stage B and L-2; then L-3 and E-1. The queue stays within ten OPEN rows.

## 9. Owner questions

1. **Is the repository meant to be public?** If not, make it private first.
2. **Allow a capped use of Claude Code sessions** for the judgement rows, or keep strictly free-only?
3. **Paragraph list for `RB:V`** (ordinal, line, first eight words): can OpenCode extract it?
4. **Do you want me to re-review `flip-ledger.ps1` and `run-queue.ps1` after the fixes are written?** I can, if you send the new versions.
