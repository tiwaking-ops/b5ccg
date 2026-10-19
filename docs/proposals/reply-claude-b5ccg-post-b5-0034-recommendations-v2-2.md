---
document:
  title: "b5ccg — Claude's Second Reply: Guard-Script Review, Coverage Map, Model Inventory, Exposure Plan and Updated Tasks"
  status: "Candidate recommendations. No authority over the repo; nothing here is applied."
  purpose: "Respond to report-reply-to-claude-post-b5-0034-recommendations.md: review the files it appended, accept its N2 correction, answer its open items, and update the task plan."
author: "Claude"
version: "v2.2"
document_version: "2.2"
author_llm: {name: "Claude", version: "Claude Sonnet 5.5 (claude-sonnet-5-5)"}
supersedes: "Nothing. Extends reply-claude-b5ccg-post-b5-0034-recommendations-v1.md; where they differ, this document wins."
provenance:
  created_date: "2026-10-19"
  last_modified_date: "2026-10-19"
  basis: "report-reply-to-claude-post-b5-0034-recommendations.md including its Appendices A to F (RULE_COVERAGE_MAP.md, flip-ledger.ps1, run-queue.ps1, ledger-query.ps1 and new-claim.ps1 excerpts, a dry-run output). I read the scripts as text; I did not run them. I have not seen the repo, the gate logs or the rulebook."
---

# b5ccg — Claude's Second Reply

**Author:** Claude · **Version:** v2.2 · **Date:** 2026-10-19

**Changes in v2.2 (after OpenCode built the acceptance harness and ran it):** the acceptance list has thirteen items, not twelve (I had adopted a miscount); nine cases were run against the current tool and none of the six that pass does so for the right reason, so the test-writing rules in section 3.1 now require asserting the refusal reason and running each case against a guard-disabled copy; case 5 could not detect F-2 and is rewritten; the backup defect (F-4) needs its own case; a twelfth defect (F-12, an unrecognised parameter is silently absorbed and the write happens) and a fourteenth case are added; the pin probe does not read `RULEBOOK_PIN.md`, so that line can be rewritten; and the proposal now carries a dated superseded-premises note.

**Changes in v2.1 (after OpenCode executed v2's claims):** three numbers corrected (`RB:V` covers 5 of 45 blocks, `HeadlessDrawRoundProbe` has 53 checks, pattern counting cannot read 13 of 31 classes); C-1 is a join against the shipped Blocks column and keys class rows on the uniform `RESULT: PASS` line; planted-condition tests must be shown red before the fix; the encoding lint cannot go green after the heading repair (20 hits, 12 not fixable by it), so H-3 and an explicit allowlist are added and my "no carve-out" stance is withdrawn; `.gitattributes` is absent, so line-ending safety is unpinned; `origin/main` still tracks ten claim and heartbeat files; and the proposed cap on Claude Code use cannot be enforced by the catalog.

Evidence tags: **[V]** read in the supplied files, **[I]** my inference, **[?]** needs a file or an owner answer.

## 1. Summary

1. **N2 retraction accepted, and verified by reading `run-queue.ps1`.** A `B5-0029.RELEASED.json` marker is keyed as `B5-0029.RELEASED` and can never equal the row id `B5-0029`. My v1 reply endorsed N2 as stated without having seen the code; that was my error.
2. **`flip-ledger.ps1` has twelve defects, not one.** N1 is real, the id check is dead code, and an unrecognised parameter is silently absorbed while the write happens (F-12, found in v2.2). The fix list is in section 3 and should become the acceptance for the tooling row.
3. **The coverage number is both too high and too low.** `RB:V` counts as one fully covered 94-line entry on the strength of five of its 45 blocks (11.1%), which flatters the figure. But the map only includes checks from the conformance suite, so the other 30 gate classes contribute nothing, which understates it. Neither is fixable by arithmetic; both need small map changes (section 4).
4. **The unverified map rows cannot close the gap.** Verifying all 18 adds at most one S1 entry (`RB:VI/marks`). Reaching 52 of 57 needs roughly 35 new checks.
5. **Model inventory: verify before trusting.** A catalog entry is a label. Several `kenari` ids are not the current Anthropic model strings I know, so route by a short calibration test, not by name (section 5).
6. **Exposure: the plan misses two facts.** A force-push does not remove commits that a merged pull request keeps reachable on GitHub, and making the repository private is a faster, reversible first step (section 6).
7. **Decision D-1 is fine**, with a byte-level check that makes the repair verifiable (section 7).

## 2. Assessment of the report

**Verification of v2 (added in v2.1).** OpenCode executed my claims instead of reading them. All sixteen line citations landed on the lines I described; the N2 retraction and the eleven `flip-ledger.ps1` defects stand; the byte arithmetic for the heading repair is correct (strictly valid UTF-8, exactly 8 U+FFFD, 59,256 bytes, so 24 bytes differ). It corrected three of my numbers (section 4) and found four things I missed (sections 3.1, 4, 7 and 8).

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
| F-12 | An unrecognised parameter is silently absorbed and the write happens: there is no `-DryRun`, yet `-Id B5-0300 -NewStatus DONE -DryRun` exits 0 and flips the row (found in the baseline run) | An operator previewing a change believes nothing happened | Add `[CmdletBinding()]` and parameter validation so an unknown parameter is a non-zero exit, then add a real `-DryRun`. Audit every shipped tool for the same absorption |

**How F-1, F-2 and F-3 compose (found in verification).** F-1 resolves the claims directory to a path that does not exist and the error is silenced; F-2 makes the id test dead; F-3 makes the one working guard (a report must exist) work only from the repository root. So today the tool protects a row against a concurrent writer not at all, and the acceptance cases below mean nothing unless each is shown to fail when its guard is dead (see the baseline run after the acceptance list). Fix F-1 first and run every planted-condition test twice: **red before the fix** (showing the guard absent) and **green after**, with both outputs in the report. "Fail loudly if the claims directory is missing" is the single highest-value line in this section.

**Acceptance (binding), each with a planted-condition test:** a planted live claim by another agent is refused; the claimant can flip its own row to BLOCKED; a planted `.RELEASED.json` is ignored; a sibling-suffix id is neither matched nor blocked; a malformed id is rejected; a duplicated id is refused; a refused flip leaves no backup and no change; running from a different working directory works; a flip to DONE without a report is refused; line endings and BOM are preserved and exactly one line differs (checked by a diff); a concurrent change aborts the flip; the lint runs after the write; a `-DryRun` switch prints the one-line difference and writes nothing.

**The list has thirteen items, not twelve** (I adopted a miscount in v2.1). Add a fourteenth: an unrecognised parameter exits non-zero and writes nothing.

**Baseline run (before any fix, from OpenCode's sandbox).** Nine of the thirteen were run against the current tool on a synthetic ledger with planted claims; the real ledger was never a write target. Six pass, and **none for the right reason**:

| # | Case | Want | Got | Verdict |
|---|---|---|---|---|
| 1 | Live claim by another agent refused | refused | accepted | FAIL |
| 2 | Claimant flips own row to BLOCKED | BLOCKED | BLOCKED | passes for the wrong reason |
| 3 | `.RELEASED` ignored; own claim still blocks | BLOCKED | BLOCKED | passes for the wrong reason |
| 4 | Sibling `B5-0202c` not blocked by `B5-0202` | BLOCKED | BLOCKED | passes for the wrong reason |
| 5 | Malformed id rejected | refused | refused | passes for the wrong reason (the id guard is never reached) |
| 7 | Refused flip leaves no backup and no change | refused | accepted | FAIL |
| 9 | DONE without a report refused | refused | refused | passes, but one backup is left behind |
| 10 | Exactly one line differs; CRLF preserved | 1 line | 1 line | half vacuous (the sandbox ledger is pure CRLF) |
| 13 | Dry run prints a diff and writes nothing | no write | wrote | FAIL |

Case 1 is the finding that justifies the row: a row carrying another agent's live claim was flipped anyway, exit 0, with the tool reporting success. Cases 2 to 4 end in the expected state because nothing ever refuses; deleting the claims directory leaves them passing. Cases 6, 8, 11 and 12 were not run, because each needs a fixture the current tool cannot be driven by.

**Rules for writing these tests.**
(a) Assert the specific refusal (the error text or exit path) as well as the unchanged state; a state-only assertion is vacuous when nothing ever refuses.
(b) Run every case against a copy of the tool with the guard under test disabled; the case must fail there (the "red before" run).
(c) Case 5 must show the id-format error specifically, raised before the ledger is opened: a bad id with a nonexistent ledger path must still give the id-format error.
(d) The backup defect (F-4) needs its own case, triggered by a missing report and not by the claim guard, and must assert that the backup directory is empty after a refused flip. That directory is `.agent/RESCUE` (under `.agent`), not a repository-level one.
(e) Cases 6, 8, 11 and 12 need fixtures: a synthetic ledger with a duplicated id; a run from another working directory; a test-only seam that lets the harness change the ledger between the tool's read and its replace; and a ledger whose lint result is known before the flip. The post-write lint rule: a flip that turns a clean ledger into a failing one is rolled back.
(f) The harness lives in a temporary folder today; commit it under `.agent/tools/` so an assessor can run it.

### 3.2 `run-queue.ps1`, `ledger-query.ps1` and `new-claim.ps1`

* **N2 sites** are `run-queue.ps1` at 173 to 174 and 654, and `ledger-query.ps1` at 229, 252, 267 and 350 to 351 [V per the report's line numbers]. Replace the `B5-*.json` filter and the `BaseName` key with a **positive match**: only a file whose base name matches `^B5-[0-9]{4}[a-z]?$` is a claim. That also covers any future marker name, where a `.RELEASED` blacklist would not. Add a planted-marker case to `census-crosscheck.ps1`, the tool that exists to prove the copies agree.
* **`new-claim.ps1`** has no directory scan, so nothing to change there. Agreed.
* **Offer markers (`run-queue.ps1` 887 to 922).** The report found one marker held by a recycled process id. The age bound applies only when the process id cannot be read, so a live unrelated process blocks forever. Make the age bound always apply (a marker only needs to bridge the offer to the claim file, so a few minutes is enough), and record the process start time so a recycled id is detected. Independently, `-DryRun` should not create markers at all. Acceptance: a marker naming a live process that started after the marker was written is treated as stale; a dry run leaves the marker directory unchanged.
* **Minor.** `Test-TerminalLedgerRow` re-reads the whole ledger and rebuilds the census for each warning. With the phantom warnings gone this stops mattering, but it is a candidate for a later cleanup.

## 4. Coverage map review and plan

`RULE_COVERAGE_MAP.md` is well made: every row states how it was checked, and unproven rows are labelled `unverified` and excluded. Its weaknesses are in what it measures.

| Finding | Detail | Recommendation |
|---|---|---|
| `RB:V` counts as one covered entry | The map holds 33 `RB:V` rows, citing six lines (:657, :663, :665, :669, :719, :721) that fall on **five** distinct blocks of the chapter's 45 (:663 and :665 are two bullets of one paragraph), so `RB:V` is 11.1% covered [V, verified]. `RULE_INDEX.md` already defines the ordinal (`RB:V#7` is the seventh block after the heading, a block being a maximal run of non-blank lines) and carries a Blocks column | Report `RB:V` as covered blocks over 45 by joining the cited lines to the existing ordinals; this is a join, not a design. Until then count `RB:V` as not covered; strict S1 coverage is **12 of 57 (21.1%)**, not 13 |
| `RB:I/victory` counts as covered on one check | A single check about Shadow War suppression stands for all the Standard Victory conditions | Add a depth column: the number of verified checks per entry. Entries over about 15 lines need at least two before they count |
| The map holds only the 904 conformance checks | Other gate classes are not mapped; for example `HeadlessDrawRoundProbe` (53 checks, 53 PASS) is invisible, so `RB:III/draw-round` reads as uncovered. Their check lines come in four incompatible formats (tag first and verdict last; verdict first with no tag; a task id as the tag; a rulebook line inline), and 13 of the 31 classes read zero under pattern counting [V, verified] | Allow class-level rows keyed on the `RESULT: PASS` line, which is uniform across all 31 logs, never on check-line parsing. A class row counts only while that class passes in the current per-class table. Read each probe's header before marking a row `verified`. I expect a few more S1 entries, perhaps three to five, but that is a guess |
| The 18 unverified rows | Only `RB:VI/marks` (six rows) targets an uncovered S1 entry. The rest target S2 or S3 entries or entries already covered | Verify them by reading the cited paragraphs; the gain is one entry. For rows whose line citation falls in a different entry than their subject (the Civil War checks citing :992), try the entry the line actually falls in first |
| Effort is aimed at S2 and S3 | Many verified rows cover League and Civil War (S2 and S3) while S1 coverage is 21 to 23% | Not a fault; note it when prioritising |

**What the arithmetic now says.** The report's intersection is right: with all four prose entries exempted, (a) 30 plus (b) 16 gives 46 and the bar is 48, so it needs `I/victory` and `RB:V` covered, not "two further (c) entries". Under the strict `RB:V` rule above, neither is yet covered properly, so the practical path to 52 of 57 is about 35 new, verified checks, led by the three worked examples and chapter II.

**Tasks.**

| Task | What | Scope | Acceptance |
|---|---|---|---|
| C-1 | Extend the map and tool: join cited lines to the existing `RB:V` block ordinals; class-level rows keyed on the `RESULT: PASS` line; a depth column; verify the six `marks` rows; **re-cut the baseline from a current sole-session gate log** (the tool still reads `_b5-0024_gate.log`, a finished task's log with 4 FAIL) | tooling, docs | The tool reports entry-level coverage and `RB:V` block coverage and the depth per entry; the log used is named in the output and is newer than HEAD; class-level rows count only while the class passes in the current table; the new baseline and its date are recorded; no `.java` changes |
| C-2 | First check batch: the three worked examples (`III/sample-conflict-round`, `IV/aftermath-example`, `VI/sample-conflict`) as scripted scenarios with the rulebook's stated outcomes, then the nine chapter II entries | engine tests | Each check cites its rule id and is mapped `verified`; coverage rises by the number of entries added; no gate verdict changes |

I do not need the `RB:V` paragraph list from anyone: `RULE_INDEX.md` already defines the ordinal and carries a Blocks column (`RB:V` = 45), so C-1 is a join against a shipped column.

## 5. Model inventory (answers my O10)

The report's census is useful, and the decision (free tiers only) is yours. Two cautions.

* **A catalog id is a label, not proof.** The `kenari` free tier lists `claude-opus-5`, `claude-sonnet-5` and `claude-fable-5`. The current Anthropic API strings I know are `claude-opus-5-5`, `claude-sonnet-5-5`, `claude-fable-5-1` and `claude-haiku-5-5`, which are the ones listed under `claude-code`. So the `kenari` ids are probably router aliases or older versions, and I cannot tell which model actually answers. Record in each report the model identity as returned by the API response metadata, not the name in the prompt. Family separation for assessors depends on the label being true, so test it.
* **Privacy.** A free router may log prompts. The repository content already includes the rulebook text and card data; decide whether that is acceptable for a router you do not control.

**Calibration battery (cheap, evidence-based routing).** Before E-0 depends on any free model: take eight starter records including `bio-weapon discovery` and one STAT_MODIFIER card, with an answer key prepared by you or Claude Desktop. Give each candidate the same vocabulary definitions and records. A candidate qualifies as an author if its classifications agree on at least seven of eight and it states an ESCAPE reason when it marks one; a different qualifying model from a different provider reviews. This is a short task and replaces guessing from names.

**Recommendation on the cost decision (revised after verification).** A cap on Claude Code use cannot be enforced by the catalog: all five `claude-code` models have cost unset, so there is no usage figure to compare against a ceiling. If you want the exception, state the cap in a unit you can enforce yourself, such as a number of sessions per row that you start by hand and record in the row; you starting the sessions is the cap. Per-row budget recording in tooling would be a separate task. My original reasoning follows. Free-only is workable for tooling rows. For the two or three judgement rows (E-0 review, the effect schema, the assessor passes) the cost of a few capped Claude Code sessions is small against the cost of a wrong schema. You may want to allow that exception explicitly, with a cap, in the D-3 ruling.

## 6. Exposure plan

Measured facts from the report [V]: the repository is public; `origin/main` has 19 of 20 commits authored with your name and an email address; there are 13 personal-data hit lines in 11 files on `origin/main` and 15 in 12 files in the working tree; no secret-shaped content. Your rulings: scrub the working tree, keep content history, rewrite author metadata only, owner-operated. Four additions.

1. **Stop new exposure first.** Set the repository's local git identity to the noreply address now so no further commit carries the real one. Check your GitHub email settings for "keep my email addresses private" and the option that blocks pushes exposing it.
2. **A force-push may not remove the old commits from GitHub.** PR #1 was merged [V], and to my knowledge GitHub keeps the commits behind a pull request reachable by hash after a rewrite. If removing the address matters more than the PR history, the clean routes are to recreate the repository from the rewritten history or to ask GitHub support to purge cached views. I am not certain of the details; check GitHub's documentation on removing sensitive data first.
3. **Consider making the repository private now.** It is immediate, reversible, needs no history rewrite, and covers everything at once: the author fields, host paths, the claim and heartbeat files, and the rulebook and card text. That last item is a separate question from privacy: the tree contains a copy of the Babylon 5 CCG rulebook and card data, which are probably someone else's copyrighted material. I am not giving legal advice, but it is a reason to ask whether public is intentional. **Is it?**
4. **Mechanics if you rewrite.** Take a full mirror backup first. Freeze all claims, because every agent branch and the snapshot branch will diverge. Run the rewrite on a fresh clone with a mailmap that maps the old name and email to the noreply identity for both author and committer, and check commit messages and tags too (the report scanned file contents for the handle, not messages). Keep the old-to-new hash map and record it in the DECISIONS entry, since reports cite old hashes such as `88a5d46` and `94f2400`.

**Acceptance check for the scrub.** The proposed `git grep` for the name and path patterns will match the audit report and any DECISIONS entry that quotes the patterns, so it can never pass as written. Exclude those paths by pathspec, or build the pattern from a file that is itself excluded.

**Facts added in verification.** (a) `.gitignore` covers `.agent/CLAIMS/` and `.agent/HEARTBEATS/`, but `origin/main` still tracks 7 claim files and 3 heartbeat files, because ignoring never untracks a committed file. They were untracked only in local commit `8ed01c3`, which is unpushed. A rewrite therefore touches ten files, and a fresh clone carries all ten unless the untracking is pushed first or the paths are dropped from history. All ten were scanned: zero personal-data hits; they hold only an agent id, a start time, a ttl, a scope and a compiler version. (b) PR #1 merged as `5059179` with parents `98e46ee` and `8fa2308`. The commits reachable from the second parent but not the first are `c4e9f82`, `635ae2a`, `1864fe4`, `977f194`, `e15e0a0`, `e7158e8`, `8fa2308` and `88a5d46`, and `88a5d46` is the commit the exposure audit cites as carrying the real name and email. A mainline rewrite orphans it while a pull-request reference can keep it reachable, which is why the GitHub documentation check in item 2 is essential and why recreating the repository, or making it private, remains the safer route.

## 7. D-1: the heading repair

Replacing U+FFFD with an em dash is fine, and the repair has a clean check. U+FFFD is three bytes in UTF-8 (EF BF BD) and the em dash is also three (E2 80 94), so the file size must be unchanged and exactly 24 bytes must differ (eight characters times three). Make the edit with a tool that writes bytes explicitly, not through the shell string handling that caused the original damage, and verify with a byte comparison. Record the repair in one DECISIONS entry, and run the encoding lint afterwards.

**Additions after verification.** (1) Do not touch `DECISIONS.md` line 521: it renders as a replacement character plus a caret, but the glyph is U+2205 (empty set), not damage; a strict decode confirms exactly 8 U+FFFD in the file. That is the case for a targeted eight-character replacement and against any broad pattern. (2) Judge damage only from a strict UTF-8 decode (`ReadAllText`), never from console display: `Get-Content` on a BOM-less UTF-8 file shows the four intact em dashes at lines 495, 500, 505 and 520 as damage. (3) The encoding lint cannot go green after this repair: it reports 20 hits in 8 files, so the repair takes it from 20 to 12. The 12 are another family. `HANDOFF.md` lines 20, 43, 46 and 94 are real damage (UTF-8 em dashes read as CP1252) in a document agents use to resume work, and the planned refresh rewrites those lines. `RULEBOOK_PIN.md` line 51 describes damage that exists in the pinned rulebook; OpenCode verified that the pin probe does not read that file, so rewrite the line in codepoint notation and the pin still passes 21 of 21. The pattern file and my earlier proposal quote mojibake on purpose (I have since replaced my own literal quotation with a codepoint description). I withdraw my earlier "no carve-out" stance: a few carve-outs are still needed (a historical report that must quote damage), but only as an explicit, reviewed allowlist with a per-entry justification and a printed residual set. Most of the 12 can be repaired by rewriting in codepoint notation, so the allowlist should end up nearly empty. (4) `lint-encoding.ps1` is not referenced in `compile.sh` or `compile.bat`, so it is not a gate, which is how 20 defects accumulated unnoticed.

## 8. Updated plan

Everything in v1 section 4 stands, with these changes:

| Task | Change |
|---|---|
| H-1 | Acceptance is the fourteen-case list in section 3.1, written to its test rules (assert the refusal reason as well as the unchanged state; run each case red against a guard-disabled copy and green after; the harness committed under `.agent/tools/`), with the line-ending case planted against `RULE_COVERAGE_MAP.md`; plus an audit of every shipped tool for silently absorbed unknown parameters; plus the heartbeat and coverage-log items from v1 |
| H-2 | The eight U+FFFD repair uses the byte-level check (size unchanged, 24 bytes differ, strict decode); the lint is reported as 20 to 12, not green; the `HANDOFF.md` refresh also removes its four damaged lines |
| C-1, C-2 | New (section 4) |
| M-1 | New: the calibration battery in section 5, before E-0 is assigned |
| X-1 | Owner action, not a task: the exposure steps in section 6, starting with the identity setting and the public-or-private question | owner | Decided and recorded |
| H-3 | New: give `lint-encoding.ps1` an `-Allow` pathspec backed by a reviewed allowlist (per-entry justification, printed residual set), split REPAIRABLE from ACCEPTED hits, reject unknown parameters, and wire it into the gate non-fatally | tooling | Output lists repairable hits, accepted hits with their justification, and the residual set; a planted known-positive file is detected (positive control); an unknown parameter exits non-zero; the exit code reflects repairable hits only; `compile.sh` prints the result without aborting |
| X-2 | Owner decision: `.gitattributes` is absent and `core.autocrlf` is true, so the ledger's CRLF is a checkout artifact (it is pure CRLF while `RULE_COVERAGE_MAP.md` is pure LF and `DECISIONS.md` is mixed, 616 CRLF to 234 LF). Pin line endings only after testing against the rulebook pin probe, because the pin is on working-tree bytes and a conversion rule could break it | owner | Decide; if yes, run the pin probe (21 of 21) before committing the file |
| E-0 | Author and reviewer come from the calibration step; the reviewer is a different provider |

Order: H-1; then K-1, K-2, H-2 and G-1 in parallel; M-1 and C-1 alongside; then L-1 Stage A, E-0 and C-2; then L-1 Stage B and L-2; then L-3 and E-1. The queue stays within ten OPEN rows. Before any row is assigned from the proposal, read its dated superseded-premises note (v1.3): F1, F3 and F5 are resolved, and the ledger stands at 30 DONE and 9 OPEN.

**The common pattern (from verification).** Five defects in this thread are one defect: a check that cannot tell "the thing is present" from "the thing is being described". They are the N2 phantom keys, F-1's silenced error, the scrub grep that matches the audit report quoting its own patterns, the encoding lint, and ignore rules versus what git actually tracks. The remedy is the same each time, and it replaces point 5 of the promotion checklist in v1 R4:

> (5) Every guard, lint or search used as acceptance has a positive control (a known case it must detect), prints its residual set, and lists any allowlisted entries with a one-line justification each. A result of "nothing found" is not evidence unless the control shows the check could have found something. Every refusal added has a test that makes it fire, run red before the fix and green after.

A sixth instance appeared in the baseline run: a tool that accepts an unrecognised parameter and acts on the rest (F-12). Shipped tools should reject unknown parameters.

## 9. Owner questions

1. **Is the repository meant to be public?** If not, make it private first. The PR #1 facts in section 6 strengthen this.
2. **Claude Code cap:** state it as a number of sessions per row that you start by hand, or keep strictly free-only.
3. **`.gitattributes` (X-2):** pin line endings, or leave as is?
4. **Re-review:** commit the baseline harness (it is in a temporary folder today) under `.agent/tools/`, then send the new `flip-ledger.ps1`, the new `run-queue.ps1` and the harness output, red against a guard-disabled copy and green against the fix, for all fourteen cases. OpenCode re-runs the execution-based checks; I read the scripts and the outputs.

The question about the `RB:V` paragraph list is closed: the Blocks column already exists.
