---
document:
  title: "b5ccg — Governance, Boot, Ledger and Hygiene Fixes"
  status: "Candidate proposal. Not truth until promoted per AGENTS.md §4. Applies nothing."
author: "Claude"
version: "v1"
document_version: "1.0"
author_llm: {name: "Claude", version: "Claude Sonnet 5.5 (claude-sonnet-5-5)"}
provenance:
  created_date: "2026-10-16"
  last_modified_date: "2026-10-16"
  basis: "AGENTS.md, b5ccg-claude-project-handoff-v1.md and b5-claude-files.md as supplied. I have not seen compile.sh, GameController, the rulebook headings, the full ledger, DECISIONS.md bodies, or the census per-record flags."
---

# b5ccg — Governance, Boot, Ledger and Hygiene Fixes

**Author:** Claude · **Version:** v1 · **Date:** 2026-10-16

## 1. What this is, and what it is not

This turns the problems found in the last review into replacement text, task specs and acceptance checks. Evidence tags: [V] verified against supplied files, [I] my inference, [?] needs a fact only the owner or repo has.

Not fixable from here, and why:

* Enabling the snapshot scheduler (O1 below) is the owner's call; it was left disabled for review.
* Final sizing of the effect work needs the per-record `covered` and `starter` flags from `effect-census.json`.
* The rule-ID scheme needs the rulebook's section headings.

## 2. Problem-to-fix map

| # | Problem | Fix | Where | Needs |
|---|---|---|---|---|
| P1 | Promotion gate says "compile green"; `compile.bat` is compile-only, tests run only via `RUN_TESTS=1 sh compile.sh` [V] | New §4 text (section 3) | AGENTS.md, 00_BOOT step 7 | DECISIONS entry |
| P2 | Ledger: B5-0007 OPEN+claimed but recorded DONE elsewhere; stray `\|\|` on B5-0006; B5-0009/0010 outside the table; 22 rows claimed, 10 shown; only 2 truly open [V] | Ledger repair (section 5), task B5-0011 | TASK_LEDGER.md | B5-0007 acceptance check |
| P3 | `00_BOOT.md` says expect JDK 8 / `-Javac 1.8.0_292`, contradicting AGENTS.md §2 [V] | New boot steps 2, 3, 5, 7 (section 4) | 00_BOOT.md | none |
| P4 | Snapshot job built but scheduler disabled, so R1 is not closed [V] | Owner action O1; AGENTS.md backup rule (section 3) | scheduler, AGENTS.md | owner |
| P5 | Printing deltas can't be found by the `(Deluxe text change: …)` suffix [V] | B5-0014 field-level diff; B5-0004 rewritten | tooling, engine | none |
| P6 | Irregular id prefixes (`aftermath_*` vs `de_am_*`) [V] | B5-0015 prefix-map test | engine | none |
| P7 | 20-term aftermath test duplicated at `GameController:903–923` [V per file note] | B5-0013 single registry | engine, GameController | claim covering GameController |
| P8a | `CardEffects` header says "Java 6 only" [V] | B5-0016 | engine | freeze window |
| P8b | `compile.bat` writes `..\b5ccg_compile_list.tmp` outside `b5ccg/`, never deleted on success [V] | B5-0016 | tooling | none |
| P8c | Report path has spaces and parentheses [V] | Agent-id rule (section 3) and B5-0016 | AGENTS.md, tools | none |
| P9 | No scopes for `resources/` or tooling; cross-cutting work has no rule [V from handoff gap 4] | Scope rules (section 3) | AGENTS.md | none |
| P10 | No rule-ID scheme although DECISIONS requires rule-IDs [V] | Interim rule (section 3); full scheme is WP-18 | AGENTS.md | rulebook headings |
| P11 | Census says MARK/STATE/VOTE have 0 rows yet `CardEffects` has mark tables [V] | Census checker note in B5-0014 scope; owner review | census | none |

## 3. Replacement text for AGENTS.md

Apply only after a DECISIONS entry. Existing sections not mentioned stay as they are.

**§2 — add after the Policy bullet:**

> * **Toolchain location.** Builds use `JAVA_HOME` when set, otherwise the staged JDK path named in `compile.bat`. The staged JDK, `.agent/CLAIMS/`, `.agent/HEARTBEATS/`, `out/` and `b5ccg_compile_list.tmp` are gitignored. The staged path is stated in exactly one place (`compile.bat`); other documents refer to "the staged JDK".
> * **Agent ids** use only letters, digits and hyphens (for example `opencode-muse-spark-1-3-01`). Report file names are built from the agent id, so spaces and parentheses are not allowed.

**§4 Promotion — replace with:**

> A proposal becomes truth when: (1) claimed scope only; (2) the **verification gate** passes: `RUN_TESTS=1 sh compile.sh` exits 0 (compile, conformance suite and gate probes). A plain `compile.bat` is a fast compile-only check and is never sufficient for promotion; (3) change logged in `docs/DECISIONS.md`; (4) claim released. A task that adds behaviour must also add or extend a probe that fails without the change. External libraries and toolchain hops need a DECISIONS entry. If in doubt, log the ambiguity in the report and STOP that item only.

**§5 — add:**

> **Scopes.** Writer scopes are `engine/`, `model/`, `ai/`, `ui/`, `resources/` (card JSON, census, decks), `tooling/` (`.agent/tools/`, compile scripts, snapshot job) and `docs/` (proposals, DECISIONS, ledger). A task that must touch more than one scope names all of them in its row and may claim only when no other live claim holds any of them. Renames and other cross-scope refactors need a freeze window: the planner marks it in the ledger, and no other claim is created until the row is DONE.
>
> **Backups.** Agents never commit. A non-agent snapshot job (B5-0009) commits the working tree to a snapshot branch on a schedule. The planner checks at the start of each session that the newest snapshot is under 24 hours old and records the result in the ledger header. A restore drill (B5-0001 acceptance) is re-run after any change to the snapshot job.
>
> **Rule-IDs (interim).** Until WP-18 defines a scheme, a DECISIONS interpretation cites the rulebook as `RB:<line>` using line numbers of the pinned copy (SHA-256 in §2), with the section heading in words. WP-18 replaces this with stable IDs.

## 4. Replacement text for `00_BOOT.md`

Steps not listed are unchanged.

> 2. Run `javac -version` and record it in your heartbeat. Expect JDK 25 (the staged JDK or `JAVA_HOME`); the project compiles with `--release 8` (Hop 1) until Hop 2 is decided. A JDK 8 `javac` cannot build the project and is a stop condition. Fast gate: `b5ccg/compile.bat` or `compile.sh`. **Verification gate: `RUN_TESTS=1 sh compile.sh`.**
>
> 3. (heartbeat command) Pass the version you actually recorded in step 2 as `-Javac`; do not copy a version from this file.
>
> 5. (claim command) Same rule for `-Javac`. Use an agent id that matches the pattern in AGENTS.md §2.
>
> 7. Verify: the **verification gate** exits 0. On red, mark `BLOCKED` with the log excerpt and release your claim. A green fast gate alone is not a pass.

The two example commands in the current file carry a hard-coded `1.8.0_292`. Replace that literal with `<recorded-javac>` in both.

## 5. Ledger repair (task B5-0011)

Repair rules, in order:

1. One table, header once, every `B5-nnnn` row inside it, sorted by ID. Remove the stray extra `|` prefix on B5-0006. Move B5-0009 and B5-0010 into the table.
2. The "Future" prose goes below the table, not between rows.
3. **B5-0007:** `DECISIONS.md` heading 10 records it DONE and `AGENTS.md` §2 describes Hop 1 as in force. The ledger row is still OPEN with a claim by `nemotron-3.5-super-vl`. [?] Before flipping it, check the row's own acceptance: all gates green under JDK 25, old pin retired, **census sentinel cycle run** (AGENTS.md says the census runs one cycle after Hop 1). If the sentinel cycle has not run, split it: mark B5-0007 DONE for Hop 1 and open a new row "Census sentinel cycle, then retire `census-b50960.py`". Release the stale claim with a reaping note, per boot step 9.
4. [?] The bundle says 22 data rows and showed 10. Owner confirms whether 12 rows exist elsewhere or the count was wrong. Repair does not delete any row.
5. Add a ledger header line: `Last snapshot checked: <utc>` (backup rule, section 3).

**Acceptance:** `.agent/run-queue.ps1 -DryRun` parses every row; `.agent/tools/run-dup-census.ps1` and `.agent/tools/check-citations.ps1` exit 0; no `B5-` row sits outside the table; B5-0007 has either no claim or a live one.

## 6. Task specs (bring the OPEN queue to 8)

Existing OPEN rows are B5-0004 (rewritten here) and B5-0008 (unchanged). New rows follow. Scope names use section 3.

| ID | Task | Scope | Acceptance (binding) |
|---|---|---|---|
| B5-0011 | Ledger repair (section 5) | docs | As in section 5 |
| B5-0012 | Apply the section 3 and 4 text to AGENTS.md and 00_BOOT.md after owner approval; add the DECISIONS entry; update the `provenance` blocks per §1 | docs | Both files contain the new text; a grep for `1.8.0_292` in `.agent/00_BOOT.md` returns nothing; DECISIONS entry exists; a second agent (assessor) records one pass |
| B5-0013 | Single-source aftermath registry. Replace the duplicated 20-term test at `GameController:903–923` with a call to `CardEffects.hasRegisteredEffect(...)` | engine (names GameController explicitly) | Zero literal copies of the membership list remain outside `CardEffects`; a probe registers a synthetic 21st aftermath id and shows it dispatches on both the human and AI paths; existing probes unchanged; verification gate green |
| B5-0014 | Printing field-level diff tool. For every title with both printings, compare each rules-bearing field (text with any `(Deluxe …)` suffix stripped and kept separately, costs, stats, bonuses, trigger, flags). Output one row per title with a class: `identical`, `suffix-only`, `rules-text-differs`, `numeric-differs`, `suffix-without-diff`, `diff-without-suffix`. Also report census classes with 0 rows (MARK, STATE, VOTE) against titles that `CardEffects` grants marks to | tooling | Report lists all titles in both files; the Drazi Sunhawk and Spin Doctors cases land in `diff-without-suffix` and the Higher Calling case in `suffix-without-diff`; tool exits 0; no behaviour code reads the suffix |
| B5-0004 (rewrite) | Printing-diff triage of the 82 special-cased titles. **Depends on B5-0014.** Resolve titles from the tool's report, not from suffix matching | engine | All 58 single-literal titles resolved; each resolved title has a scenario test passing under both ids; titles in `diff-without-suffix` are each resolved or logged as an ambiguity |
| B5-0015 | Canonical-key prefix map test. Enumerate every id in `premiere.json` and `deluxe.json` | engine | Each id maps to exactly one canonical key; both printings of a title map to the same key; the `aftermath_*` / `de_am_*` pair and any other irregular prefix are covered by named test cases; unknown prefixes fail the probe |
| B5-0016 | Hygiene batch: (a) replace "Java 6 only" in the `CardEffects` header with the current rule (comment-only, in a freeze window); (b) `compile.bat` and `compile.sh` write the file list inside `b5ccg/` or the temp directory and delete it on success and failure; (c) agent-id pattern check in `new-claim.ps1` and `new-heartbeat.ps1` | tooling (a: engine, freeze window) | After a green and a red build, no `b5ccg_compile_list.tmp` exists anywhere; a claim with a space in the agent id is rejected; `CardEffects` diff is comment-only |
| B5-0008 | Unchanged (seeded RNG, state hash, double-run probe) | engine | As in ledger |

Held back on purpose: the unimplemented-text tripwire (A4), effect-schema pilots (WP-10 to WP-12) and engine decomposition. Promote them after B5-0013 and B5-0014 land, and after the effect-size decision in section 7.

## 7. Effect-work sizing (open item, not a fix)

Last turn's provisional call was "medium". It rests on 384 behaviour-bearing records, 222 of them in primitive-friendly classes. To finish it I need either the per-record `starter` and `covered` flags or just the starter-deck titles with their classes. B5-0014's mark finding also matters: if mark grants sit inside ONE_SHOT or TRIGGERED records, a single class per title understates how many primitives the schema needs. Decision rule is unchanged: about 70% of behaviour-bearing cards fitting about 25 primitives, otherwise a larger escape hatch.

## 8. Owner actions

| # | Action | Why |
|---|---|---|
| O1 | Review and enable the snapshot scheduler; run the restore drill once | Closes R1 only when live |
| O2 | Approve section 3 and 4 text; confirm the DECISIONS entry wording | Promotion rule change is governance |
| O3 | Check B5-0007's sentinel-cycle acceptance and the 22-vs-10 row count | Section 5 items 3 and 4 |
| O4 | Send census `starter` and `covered` flags, `compile.sh`, the rulebook section headings, and the D3 entry text | Section 7, rule-IDs, and to check the new §5 backup rule against what D3 approved |

## 9. Verification of this proposal

* Every row of section 2 maps to either text in section 3 or 4, a task in section 6, or an owner action in section 8.
* No task here reads or writes outside its stated scope, and none edits `BABYLON5_CCG_RULEBOOK.md`.
* After B5-0011 to B5-0016 are DONE, the review's items 1 to 8 should each be re-checkable by one command or grep named in the acceptance column. If any is not, the acceptance line is wrong and needs revising before the task is claimed.
