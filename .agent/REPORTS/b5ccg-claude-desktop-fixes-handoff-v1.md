---
document:
  title: "b5ccg — Claude Desktop Handoff: Apply Governance, Boot and Ledger Fixes"
  status: "Handoff brief. Observation and instructions only; no authority over the repo."
  purpose: "Let a Claude Desktop session with repo access apply the approved-in-principle fixes from claude-b5ccg-governance-and-ledger-fixes-v1.md, resolve the open facts that needed repo access, and stop at the owner-approval gates."
author: "Claude"
version: "v1"
document_version: "1.0"
author_llm: {name: "Claude", version: "Claude Sonnet 5.5 (claude-sonnet-5-5)"}
provenance:
  created_date: "2026-10-16"
  last_modified_date: "2026-10-16"
  basis: "A chat session that could not touch the repo. It saw AGENTS.md, the earlier handoff, and a bundle of excerpts (census class counts, CardEffects header, 00_BOOT.md, compile.bat, a partial ledger, DECISIONS headings). It never saw compile.sh, GameController, the rulebook headings, full DECISIONS bodies, the full ledger, or per-record census flags."
---

# b5ccg — Claude Desktop Handoff

**Author:** Claude · **Version:** v1 · **Date:** 2026-10-16

You are a fresh Claude session with direct access to the repo at `C:\temp\projects\b5ccg`. You do not see the conversation that produced this. Everything you need is here or in the repo.

## 1. Mission

Apply a set of documentation, ledger and tooling fixes to the b5ccg repo, in the order below, stopping at each owner gate. Along the way, replace the guesses marked **[?]** with facts you read from the repo.

You are not building game features. Do not change engine behaviour, card data or the rulebook.

## 2. Ground rules (from `AGENTS.md`; re-read it first, it wins over this brief)

* `BABYLON5_CCG_RULEBOOK.md` is canonical. Never edit it. Pin: SHA-256 `1c99c44b7b64c8927f1e952a22a49d5886788da2d08e211092b5558e154716d0`.
* Agents do not commit or push. Leave all changes in the working tree for the owner to review with `git diff` and commit.
* Never touch another agent's claim file or heartbeat. A claim younger than 30 minutes is live.
* Append ledger rows only with `.agent/tools/append-ledger.ps1`, never `Add-Content`. A ledger *repair* (Phase 3) is a restructure, so it is the one exception, and it needs the owner's go-ahead and a backup first.
* Governance changes (AGENTS.md, 00_BOOT.md) need a `docs/DECISIONS.md` entry. Do not edit them before the owner approves.
* No new `.md` files at the repo root. Reports go to `.agent/REPORTS/<date>-<agent-id>-<task>.md`.
* Agent ids: letters, digits and hyphens only. Use `claude-desktop-01`.
* Provenance: do not overwrite any `author_llm`. When you edit an authority-bearing file, update `last_modified_by_llm` and `last_modified_date`. Do not add yourself as `assessor_llm` on text you wrote; leave assessment to a different agent.
* If in doubt, log the ambiguity in your report and stop that item only.

Evidence tags for your report: [V] verified in a repo file you read, [R] from an external source, [I] your inference.

## 3. Phase 0 — Read-only reconnaissance (do all before editing anything)

Read these and note anything that contradicts this brief:

1. `AGENTS.md`, `.agent/00_BOOT.md`, `docs/DECISIONS.md` (full bodies, especially the D2/D3/D4 "full approval" entry and the B5-0007 DONE entry), `.agent/TASK_LEDGER.md` (whole file).
2. `b5ccg/compile.bat` and `b5ccg/compile.sh`. Record exactly what `RUN_TESTS=1 sh compile.sh` runs and its exit-code contract.
3. `.agent/CLAIMS/*.json` and `.agent/HEARTBEATS/*.json`. Record which claims are live and who holds B5-0007.
4. `docs/proposals/` and `.agent/REPORTS/` listings. Check whether the proposal `claude-b5ccg-governance-and-ledger-fixes-v1.md` is already present; if not, ask the owner to place it in `docs/proposals/` (do not create it from memory).
5. `git status` and `git log -5 --oneline` for context. Do not stage or commit.
6. Resolve these open facts and write each answer into your report:

| Fact | Where to look |
|---|---|
| Real ledger row count (earlier bundle said 22 data rows but showed 10) | whole ledger |
| Whether B5-0007's acceptance is fully met: all gates green under JDK 25, old pin retired, **census sentinel cycle run** | its row, its report, DECISIONS B5-0007 entry |
| What D3 (snapshot and commit policy) actually approved | DECISIONS "Owner rulings D2 + D3 + D4" |
| Whether the B5-0009 snapshot scheduler is still disabled | the Task Scheduler definition / script from the B5-0009 report |
| Where `GameController` duplicates the aftermath membership test (earlier note said lines 903–923) | `GameController.java` |
| `starter` and `covered` counts per class in `resources/census/effect-census.json` | the JSON (see Phase 6) |
| Rulebook section headings | `BABYLON5_CCG_RULEBOOK.md` (read only) |
| How the plain `compile.bat` and `compile.sh` differ in what they gate | the two scripts |

Run the current gates once to establish a baseline: `RUN_TESTS=1 sh compile.sh` (Git Bash) and record the exit code. If it is red before you change anything, say so and stop; do not fix engine code.

**Gate 0:** Show the owner a short recon summary and any contradictions. Ask the owner to approve proceeding.

## 4. Phase 1 — Back up before editing

Copy `.agent/TASK_LEDGER.md`, `AGENTS.md` and `.agent/00_BOOT.md` to `b5ccg/scratch/backup-2026-10-16/` (gitignored scratch; confirm it is ignored, and if not, say so rather than editing `.gitignore` yourself). Do not proceed if the copy fails.

## 5. Phase 2 — Governance text (task B5-0012). **Needs owner approval of the text first.**

Show the owner the three replacement blocks below. On approval, apply them, then append a `docs/DECISIONS.md` entry (one new `##` section at the end: date, title "Governance fixes from Claude fixes proposal v1", the three changes in one line each, owner approval noted verbatim as the owner says it, and the D-numbers it extends). Update `last_modified_by_llm` and `last_modified_date` in each edited file's front matter.

### 5a. `AGENTS.md` §2 — add after the Policy bullet

> * **Toolchain location.** Builds use `JAVA_HOME` when set, otherwise the staged JDK path named in `compile.bat`. The staged JDK, `.agent/CLAIMS/`, `.agent/HEARTBEATS/`, `out/` and `b5ccg_compile_list.tmp` are gitignored. The staged path is stated in exactly one place (`compile.bat`); other documents refer to "the staged JDK".
> * **Agent ids** use only letters, digits and hyphens (for example `opencode-muse-spark-1-3-01`). Report file names are built from the agent id, so spaces and parentheses are not allowed.

If AGENTS.md §2 currently spells out the staged JDK path, keep it only if `compile.bat` is the single source; otherwise replace that spelling with "the staged JDK". Tell the owner which you did. Check `.gitignore` for the listed paths and report which are missing; do not edit `.gitignore` without the owner's say-so.

### 5b. `AGENTS.md` §4 Promotion — replace the whole section with

> A proposal becomes truth when: (1) claimed scope only; (2) the **verification gate** passes: `RUN_TESTS=1 sh compile.sh` exits 0 (compile, conformance suite and gate probes). A plain `compile.bat` is a fast compile-only check and is never sufficient for promotion; (3) change logged in `docs/DECISIONS.md`; (4) claim released. A task that adds behaviour must also add or extend a probe that fails without the change. External libraries and toolchain hops need a DECISIONS entry. If in doubt, log the ambiguity in the report and STOP that item only.

Before applying, check against what you read in Phase 0 that `RUN_TESTS=1 sh compile.sh` really is the verification gate and exits 0 on success. If `compile.sh` differs from the wording (different env var, different gate list), adjust the sentence to match the script and tell the owner.

### 5c. `AGENTS.md` §5 — add at the end

> **Scopes.** Writer scopes are `engine/`, `model/`, `ai/`, `ui/`, `resources/` (card JSON, census, decks), `tooling/` (`.agent/tools/`, compile scripts, snapshot job) and `docs/` (proposals, DECISIONS, ledger). A task that must touch more than one scope names all of them in its row and may claim only when no other live claim holds any of them. Renames and other cross-scope refactors need a freeze window: the planner marks it in the ledger, and no other claim is created until the row is DONE.
>
> **Backups.** Agents never commit. A non-agent snapshot job (B5-0009) commits the working tree to a snapshot branch on a schedule. The planner checks at the start of each session that the newest snapshot is under 24 hours old and records the result in the ledger header. A restore drill (B5-0001 acceptance) is re-run after any change to the snapshot job.
>
> **Rule-IDs (interim).** Until WP-18 defines a scheme, a DECISIONS interpretation cites the rulebook as `RB:<line>` using line numbers of the pinned copy (SHA-256 in §2), with the section heading in words. WP-18 replaces this with stable IDs.

If D3's approved text conflicts with the Backups paragraph, follow D3 and tell the owner what you changed.

### 5d. `.agent/00_BOOT.md` — replace steps 2, 3, 5 and 7 (others unchanged)

> 2. Run `javac -version` and record it in your heartbeat. Expect JDK 25 (the staged JDK or `JAVA_HOME`); the project compiles with `--release 8` (Hop 1) until Hop 2 is decided. A JDK 8 `javac` cannot build the project and is a stop condition. Fast gate: `b5ccg/compile.bat` or `compile.sh`. **Verification gate: `RUN_TESTS=1 sh compile.sh`.**
>
> 3. Write your heartbeat with the shipped writer, never by hand: (keep the existing command, but pass `-Javac <recorded-javac>`, the version you actually recorded in step 2; do not copy a version from this file). Keep the rest of step 3 unchanged.
>
> 5. Claim atomically (keep the existing command, with `-Javac <recorded-javac>` and an agent id that matches the pattern in AGENTS.md §2). Keep the rest of step 5 unchanged.
>
> 7. Verify: the **verification gate** exits 0. On red, mark `BLOCKED` with the log excerpt and release your claim. A green fast gate alone is not a pass.

**Check after applying:** a search for `1.8.0_292` in `.agent/00_BOOT.md` finds nothing, and the AGENTS.md §2 and 00_BOOT.md toolchain statements agree.

**Gate 2:** Show the owner `git diff` for the three edited files and the new DECISIONS entry. Wait for the owner to confirm before Phase 3.

## 6. Phase 3 — Ledger repair (task B5-0011). **Needs owner go-ahead.**

First confirm there are no live claims or heartbeats that name ledger edits. Then, working from the Phase 1 backup:

1. One table, header once, every `B5-nnnn` row inside it, sorted by ID. Remove the stray extra `|` prefix on the B5-0006 row. Move B5-0009 and B5-0010 into the table. Do not delete or reword any row's task text, report path or status except as listed here.
2. Put the "Future" prose below the table.
3. **B5-0007:** the row is OPEN with a claim by `nemotron-3.5-super-vl`, while DECISIONS records it DONE and AGENTS.md describes Hop 1 as in force.
   * Check its acceptance from Phase 0 facts. If all three parts are met, set DONE, clear the claim, and put the report path in the Report column.
   * If the census sentinel cycle has not run: set B5-0007 DONE for Hop 1 only, and add a new OPEN row "Census sentinel cycle, then retire `.agent/tools/census-b50960.py`" (scope `tooling`, acceptance: sentinel cycle report exists, script retired, DECISIONS note added).
   * If the claim file for B5-0007 exists and is older than 30 minutes, note the reaping in the ledger before removing it (boot step 9). If it is under 30 minutes old, do not touch it; leave B5-0007 as is and report that.
4. Add a header line below the title: `Last snapshot checked: <UTC time>` and fill it from the newest snapshot-branch commit time, or write `none found` if there is no snapshot branch.

**Acceptance (run all and record exit codes):**
* `powershell -NoProfile -ExecutionPolicy Bypass -File .agent/run-queue.ps1 -DryRun` parses every row.
* `.agent/tools/run-dup-census.ps1` exits 0.
* `.agent/tools/check-citations.ps1` exits 0.
* No `B5-` row sits outside the table.

If any check is red because of your edit, restore the backup and report. If red for a reason that predates your edit, report it and do not fix unrelated problems.

**Gate 3:** Show the owner the diff and the three exit codes.

## 7. Phase 4 — Add the new task rows (do not do the engine work)

Use `.agent/tools/append-ledger.ps1` (read its usage first). Add these as OPEN rows with no claim and no report, then run `run-dup-census.ps1` and `check-citations.ps1`. Take the next free IDs; the numbers below are the intended ones, so if B5-0011 was used for the ledger repair itself, keep the numbering below and make sure the repair row is B5-0011.

| ID | Task | Scope | Acceptance (binding) |
|---|---|---|---|
| B5-0011 | Ledger repair (Phase 3). Mark DONE once Gate 3 passes | docs | As in section 6 |
| B5-0012 | Apply governance text to AGENTS.md and 00_BOOT.md (Phase 2). Mark DONE once Gate 2 passes | docs | Both files contain the new text; `1.8.0_292` absent from 00_BOOT.md; DECISIONS entry exists; a different agent records one assessor pass |
| B5-0013 | Single-source aftermath registry: replace the duplicated 20-term test in `GameController` with a call to `CardEffects.hasRegisteredEffect(...)` | engine (names GameController explicitly) | No literal copy of the membership list remains outside `CardEffects`; a probe registers a synthetic 21st aftermath id and shows it dispatches on both human and AI paths; existing probes unchanged; verification gate green |
| B5-0014 | Printing field-level diff tool: for every title with both printings compare each rules-bearing field, with any `(Deluxe …)` suffix stripped and kept separately. Classes: `identical`, `suffix-only`, `rules-text-differs`, `numeric-differs`, `suffix-without-diff`, `diff-without-suffix`. Also report census classes with 0 rows (MARK, STATE, VOTE) against titles `CardEffects` grants marks to | tooling | Covers all titles in both card files; Drazi Sunhawk and Spin Doctors land in `diff-without-suffix`, Higher Calling in `suffix-without-diff`; exits 0; no behaviour code reads the suffix |
| B5-0004 (edit) | Printing-diff triage of the 82 special-cased titles. **Depends on B5-0014.** Resolve from the tool report, not suffix matching | engine | All 58 single-literal titles resolved; each has a scenario test passing under both ids; titles in `diff-without-suffix` are each resolved or logged as ambiguities |
| B5-0015 | Canonical-key prefix-map test over every id in `premiere.json` and `deluxe.json` | engine | Each id maps to exactly one canonical key; both printings of a title map to the same key; `aftermath_*`/`de_am_*` and any other irregular prefix have named test cases; unknown prefixes fail the probe |
| B5-0016 | Hygiene batch: (a) replace "Java 6 only" in the `CardEffects` header with the current rule (comment-only, freeze window); (b) `compile.bat` and `compile.sh` write the file list inside `b5ccg/` or the temp directory and delete it on success and failure; (c) agent-id pattern check in `new-claim.ps1` and `new-heartbeat.ps1` | tooling (a: engine, freeze window) | After a green and a red build, no `b5ccg_compile_list.tmp` exists anywhere; a claim with a space in the agent id is rejected; the `CardEffects` diff is comment-only |

B5-0004 already exists as an OPEN row. Do not append a duplicate; edit its Task and Acceptance text in place (this is a ledger edit, so do it in Phase 3 under the same go-ahead, and report that you did). B5-0008 stays unchanged.

Do not claim or work B5-0013 to B5-0016 yourself unless the owner asks in this session. If the owner does ask, claim through the normal tools, one task at a time, with the verification gate as the finish line.

## 8. Phase 5 — The snapshot job (owner action, read-only for you)

Report, without changing anything: the scheduler's current state (enabled or disabled), the snapshot branch name and newest snapshot time, and whether the B5-0001 restore drill has been run since the job was built. Offer the owner the exact steps to enable it; enable it only if the owner tells you to in this session, and re-run the restore drill afterwards if so.

## 9. Phase 6 — Effect-work sizing data

Read `resources/census/effect-census.json` and produce one table: for each of the 11 populated classes, the count of records, the count with `starter` true, and the count with `covered` true. Also list the starter-deck titles with their classes. Put the table in your report. Do not decide the sizing; hand it back (see section 11).

Known inputs from the earlier bundle, for cross-checking your numbers: 446 records; ONE_SHOT 93, TRIGGERED 67, STAT_MODIFIER 63, CONFLICT_VARIANT 59, VANILLA 43, DECK_HAND 33, RESTRICTION 30, AGENDA_WIN 26, TRAIT_ONLY 19, COST_MODIFIER 7, BESPOKE 6. MARK, STATE, VOTE: 0 rows. Report any mismatch.

## 10. Report

Write `.agent/REPORTS/2026-10-16-claude-desktop-01-b5-fixes.md`. Front matter: `author_llm` with your own name and version, `created_date`, status "Observation only". Contents:

1. Facts resolved in Phase 0 (the table), each with its evidence tag.
2. Baseline gate result (exit code) before your edits, and after.
3. Each file changed, with a one-line summary and the owner approval you got (quote the owner).
4. Ledger acceptance exit codes.
5. Anything that differed from this brief and how you handled it.
6. The Phase 6 table.
7. Open questions for the owner.

Refresh your heartbeat at the start and end of the session with `.agent/tools/new-heartbeat.ps1`, recording the `javac -version` you actually saw. Write a claim only if you are doing a task row from section 7.

## 11. What to bring back to the Claude chat project

Upload or paste the report and the Phase 6 table. The chat project will then (1) make the final call on effect-work size (the decision rule: about 70% of behaviour-bearing cards fitting about 25 primitives, otherwise a larger escape hatch), (2) draft the WP-18 rule-ID scheme from the rulebook headings you extracted, and (3) review `CardEffects` and the card samples for the canonical-id triage.

## 12. Stop conditions

Stop and ask the owner if: the baseline gate is red; any live claim covers `docs/` or the ledger; a file you are about to edit has changed since you read it; the owner's approval text for a gate is ambiguous; or any step would require editing the rulebook, card data or engine code.
