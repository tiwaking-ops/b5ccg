---
document:
  title: "b5ccg — Next Task Specs (post chat-results report)"
  status: "Candidate proposal. Not truth until rows are appended and claimed per AGENTS.md. Applies nothing."
author: "Claude"
version: "v1"
document_version: "1.0"
author_llm: {name: "Claude", version: "Claude Sonnet 5.5 (claude-sonnet-5-5)"}
provenance:
  created_date: "2026-10-17"
  last_modified_date: "2026-10-17"
  basis: "2026-10-16-opencode-hy4-preview-01-chat-results.md (its census table, gate results, ledger state and open items). I have not seen the repo, compile.sh, the census JSON, or the applied AGENTS.md."
---

# b5ccg — Next Task Specs

**Author:** Claude · **Version:** v1 · **Date:** 2026-10-17

Evidence tags: [V] stated in the chat-results report, [I] my inference, [?] needs a repo fact.

## 1. Numbering and queue size

The report used B5-0011 to B5-0016 (B5-0011 DONE, B5-0012 awaiting an assessor, B5-0013 to B5-0016 unclaimed). New rows therefore start at **B5-0017**. Open rows today are about seven [V: B5-0004, B5-0012 to B5-0016 plus B5-0008's divergence], which already meets the ledger's 5-to-10 guidance. So add only the rows that unblock a decision or protect work, and hold the rest.

| Row | Add now? | Why |
|---|---|---|
| B5-0017 primitive-fit classification | Yes | Settles the effect-work decision; gates WP-10 to WP-12 |
| B5-0018 baseline check names + repair owner | Yes | The §4 baseline exception has no exit without it |
| B5-0019 `.gitignore` and snapshot excludes | Yes, owner-gated | Snapshot job would absorb the 141 MB JDK and build output |
| B5-0020 deterministic probe output | Hold (Future block) | Do alongside B5-0008 or after B5-0013 |

## 2. B5-0017 — Primitive-fit classification (decides effect-work size)

**Scope:** docs (report only; reads `resources/census/effect-census.json` and card JSON, edits nothing).

**Why:** [V] 222 of 384 behaviour-bearing records are in primitive-friendly classes (58%), and 63 of 102 starter behaviour-bearing records. The 70% rule needs the fit of TRIGGERED, CONFLICT_VARIANT and RESTRICTION cards (156 records, 39 of them starter), and the census classes do not measure that.

**Task:**
1. Read the effective text of every starter-deck record in TRIGGERED, CONFLICT_VARIANT and RESTRICTION (39 records [V from the table: 20 + 15 + 4]). Deluxe text governs dual titles (D5); read the text with any `(Deluxe …)` suffix separated out, not trusted.
2. For each, record which primitives express it, or `ESCAPE` if none do. Build the primitive vocabulary as you go; target 25 or fewer names, each with a one-line definition and the first record that needed it.
3. Draw a seeded sample of 30 from the remaining 117 records in those three classes, stratified by class. Record the seed and the list of ids so the draw can be reproduced. Classify the same way.
4. Also classify every starter STAT_MODIFIER record (17 [V]) against the same vocabulary, since the Groups and Enhancements pilot starts there.
5. Compute: fit rate for the 39 starter records; fit rate for the 30-sample; the projected overall rate (the 222 friendly records counted as fitting, per-class sample rates applied to the rest). State whether the 70% rule is met, and the size call (small, medium, large).
6. A second agent re-classifies 10 randomly chosen records blind. Record agreement.

**Acceptance (binding):**
* All 39 starter records classified, each row naming card id, class, primitives or `ESCAPE`, and the sentence of text it relied on.
* Sample seed and ids recorded; the draw reproduces from the seed.
* Vocabulary is at most about 25 primitives, or the report states why it needs more.
* Reviewer agreement on the 10 blind records is at least 8 of 10; if lower, the vocabulary definitions are revised and the 10 are redone.
* Report states: the three rates, whether the 70% rule is met, the size call, and which records are `ESCAPE` (the escape-hatch backlog).
* Report lists any title whose class looks wrong in the census (for example cards granting marks but filed as ONE_SHOT or TRIGGERED, given MARK, STATE and VOTE hold 0 rows [V]), without editing the census.
* Report is `.agent/REPORTS/<date>-<agent-id>-B5-0017.md`; no `.java`, JSON or ledger files other than this row are changed.

**Not in scope:** designing the effect schema, or writing any card behaviour.

## 3. B5-0018 — Record baseline failures by name, and give them an owner

**Scope:** docs.

**Why:** [V] the gate is red at 4 of 904 (AES×1, AIJ×1, MINES×2). Those are category counts. A "subset of baseline" rule needs exact check names, and the exception needs an owner or it never expires. [V] the report notes a later B5-0008 run recorded 900/904, so the baseline may already differ from 4; the row must read the current state, not copy this number.

**Task:**
1. Run the verification gate (`RUN_TESTS=1 sh compile.sh`) on a clean tree state with no live engine claims, and extract the exact names of every failing check.
2. Add a `docs/DECISIONS.md` entry listing those names, the run time in UTC and the commit or tree state, replacing any count-only baseline wording.
3. Append one OPEN row owning the repair: "Repair baseline conformance failures, then close the DECISIONS baseline entry" with scope set by the failing areas (likely `ai/` and `engine/`; name them from step 1), acceptance: the named checks pass, gate exits 0, the baseline clause in AGENTS.md §4 is deleted in the same task.
4. Compare failing sets by name in the report. If the names differ from the recorded AES, AIJ and MINES categories, say so.

**Acceptance (binding):** DECISIONS lists every failing check by exact name; the repair row exists and is OPEN; two consecutive gate runs give the same failing-name set; report records both sets. Gate logs are compared by verdict and failing-name set, never byte equality [V: report §10].

## 4. B5-0019 — `.gitignore` and snapshot-job excludes (owner-gated)

**Scope:** tooling.

**Why:** [V] no entry exists for `out/`, `b5ccg/scratch/` (which holds a staged 141 MB JDK and backups), `.agent/CLAIMS/`, `.agent/HEARTBEATS/` or `b5ccg_compile_list.tmp`. The owner chose "report only" earlier, so this row needs a fresh go-ahead before claiming. [?] The snapshot script (B5-0009) may have its own excludes; the row reads it first.

**Task:**
1. Read the snapshot script and its Task Scheduler definition; record what it stages and whether it honours `.gitignore`.
2. With the owner's go-ahead, add the five paths to `.gitignore` (and any the script reads from a separate exclude file).
3. If `b5ccg/scratch/backup-*` should stay recoverable, say where else it is kept; do not delete anything.
4. Dry-run the snapshot job against a scratch repo copy or `git add --dry-run` and confirm none of the five paths would be staged.

**Acceptance (binding):** `git status --ignored` shows the five paths ignored; no tracked file was removed from the index by this task (a path already tracked is reported, not untracked, without the owner's instruction); the snapshot dry run stages none of the five paths; the B5-0001 restore drill is re-run and passes; DECISIONS gets a one-line entry.

## 5. B5-0020 — Deterministic probe output (hold in Future)

**Scope:** engine probes / tooling. Promote after B5-0008 is reconciled.

**Why:** [V] two gate logs of 569,399 bytes each differed first at offset 10118, with the same 10,782 lines in a different order, from HashMap or parallel iteration.

**Task:** make probe output order stable (sorted, or ordered collections) so verdict and failing-set comparison no longer needs special handling.

**Acceptance (binding):** two consecutive gate runs on an unchanged tree produce logs that differ only in timestamps and elapsed-time fields; no probe verdict changes; the 4-failure baseline set is unchanged.

## 6. Planner and owner actions (not tasks)

| # | Action | Why |
|---|---|---|
| A1 | Reconcile B5-0008: DECISIONS says DONE (900/904) but the ledger row is OPEN [V]. Do it in a quiet window, because a heartbeat write landed mid-repair last time [V]. | Ledger and DECISIONS disagree |
| A2 | Owner takes a manual commit of the current working tree now | There is no snapshot branch and several agents have uncommitted edits [V] |
| A3 | Choose a third agent, different from both Claude (proposal author) and hy4-preview (applier), for B5-0012's assessor pass. Check that final `AGENTS.md` §5 does not say both "agents never commit" and "private task-branch commits allowed" without reconciling them [V: report §7.1 mentions D3 alignment] | `AGENTS.md` §1 forbids self-assessment; the D3 wording may conflict |
| A4 | Move the three root `.md` files: the fixes proposal to `docs/proposals/`, the handoff and `b5-claude-files.md` to `.agent/REPORTS/` | They violate the no-root-`.md` rule [V]; your call since you chose report-only |
| A5 | Extract the rulebook section headings (heading lines only) for the WP-18 rule-ID scheme | Still blocked on this [V] |

## 7. Sequencing

1. A2 (commit), then B5-0019 if you approve, so the snapshot job is safe to enable.
2. B5-0018 and B5-0017 in parallel (both read-mostly; B5-0018 needs a quiet engine for a clean gate run).
3. B5-0013, B5-0014, B5-0015, B5-0016 as already specced; B5-0004 after B5-0014.
4. After B5-0017's size call: promote WP-10 (effect schema) and the Groups and Enhancements pilot from the Future block.
