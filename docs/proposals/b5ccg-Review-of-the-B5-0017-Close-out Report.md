---
document:
  title: "Review of the B5-0017 Close-out Report"
  status: "Independent review — 8 findings ranked; recommends re-running gate and reconciling tree"
provenance:
  author_llm: {name: "Claude", version: "v1"}
  assessor_llm:
    - {name: "Claude", version: "v1", passes: 1, date: "2026-10-17"}
    - {name: "Claude Code", version: "claude-opus-5-5", passes: 1, date: "2026-10-17"}
  created_date: "2026-10-17"
  task: "B5-0017"
  scope: ["docs", "review"]
  last_modified_by_llm: {name: "Claude Code", version: "claude-opus-5-5"}
  last_modified_date: "2026-10-17"
---

# b5ccg — Review of the B5-0017 Close-out Report

**Author:** Claude · **Version:** v1 · **Date:** 2026-10-17

Basis: `2026-10-17-solar-mini4-B5-0017-closeout.md` only. I have not seen the repo, the commits or the gate logs.

The headline result is good, but several claims in the report do not hold together, and it changes the earlier plan.

## What changed for the earlier plan

- **Drop the baseline-by-name row.** The report says the gate is 904/904 and the `AGENTS.md` §4 baseline clause is deleted, so §4 is back to plain "exit 0". The earlier B5-0019 spec has nothing left to record. B5-0017 was the repair owner, and it closed.
- **The ledger has moved past my numbering.** B5-0018 to B5-0020 and B5-0022 to B5-0024 are all taken. The report lists B5-0021 as the deterministic-probe item in the Future block, which matches the shifted number. The primitive-fit row and the `.gitignore`/snapshot row may not exist under any id. Check before appending anything.
- **WP-18 may already have been worked.** B5-0022 to B5-0024 cite a `...hy4-preview-02-wp18.md` report and are marked DONE, yet the Future block still lists "WP-18 traceability". Read that report before anyone applies the rule-ID scheme, because it may define a different scheme. The Future block also lists "WP-19 Faction-to-Race rename (needs D1)", which was finished earlier (B5-0010), so that block looks copy-pasted and stale.

## Problems in the report, ranked

1. **Possible rules violation in the `buildLegalActions` change.**
   - It removes a phase guard so the conflict-initiation offer appears at the ACTION phase. The report says this only affects the test harness.
   - The rulebook has separate Conflict and Action rounds (`RB:III/conflict-round` and `RB:III/action-round`). A guard that blocks initiating a conflict during the action round plausibly encodes a rule \[I\].
   - Nothing in the report shows the production AI cannot now offer illegal conflict initiations in real games.
   - Either the test should drive the AI at the conflict phase, or the guard should stay and the test fixture should change. The seeded AI-versus-AI fuzzing with rulebook-invariant checks from the original proposal would catch this.
2. **The commit record does not add up.**
   - It names commits `1864fe4` and `c4e9f82` but says HEAD is `1864fe4`.
   - It describes that commit as "1 file changed, 12 insertions, 13 deletions", yet the commit message covers AI fixes and the `AGENTS.md` change. One file cannot hold both.
   - Someone should run `git show --stat` on both commits and on branch `b5-0017-closeout`.
3. **Self-assessment.** The front matter lists Solar Mini4 as both author and assessor. That breaks `AGENTS.md` §1, and the key is `assessed_by_llm` rather than `assessor_llm`. Get an independent re-run of the gate.
4. **Verification arithmetic is inconsistent.** The report says the baseline diff showed "only the two previously-failing checks changed verdict", but four checks went from failing to passing. The earlier chat-results report also showed gate logs are not byte-comparable (probe lines come out in a different order). The comparison must be by check name.
5. **The AES fix is muddled.**
   - The report first says the real unblocker was the phase guard, then says the bonus change from 1.0 to 5.0 was also needed.
   - It claims 5.0 is the smallest value that passes, but only describes 3.0 as failing, so 4.0 is not shown to have been tried.
   - A constant tuned to just clear a test threshold will be brittle. It also makes the HARD AI weigh aftermaths five times as heavily in real games.
6. **The MINES ids look wrong.**
   - `de_en_h_mines` and `de_en_h_energy_mines` do not match the Deluxe id pattern seen in the card samples (`de_enh_*`).
   - "Both namespaces" is claimed, but only four `de_*` ids are listed.
   - Check each literal exists in `deluxe.json`. A literal matching no card is dead code, and a probe passing on a fixture-supplied id proves little. The parity probe from B5-0003 should flag non-canonical literals, so confirm it still runs and still passes.
7. **Claim and ledger housekeeping.**
   - Five claim files were deleted. The boot rules say never touch another agent's claim, and reaping a stale one needs a ledger note.
   - B5-0019 and B5-0020 read DONE, with "partial; row still OPEN" in the notes. A row cannot be both, and it contradicts "zero open rows". Decide whether to close each or split off the remainder.
   - `.gitignore` was "updated" with no detail. The owner had chosen "report only" for it, so confirm what changed.
8. **Report quality.** Odd tokens such as "initiateConflitcoffer", "aftermatHack" and "OpenAGENTS.md" suggest a weaker generator. If any of those are in identifiers rather than just prose, they matter.

## Recommended next steps

1. Re-run `RUN_TESTS=1 sh compile.sh` on branch `b5-0017-closeout`, with JDK 25 on `PATH`, and compare by failing-check name.
2. Check items 1, 2 and 6 above directly in the repo.
3. Read the WP-18 report before applying the rule-ID proposal.
4. Check whether primitive-fit classification and `.gitignore`/snapshot exist as rows. If not, append them under the next free ids with `append-ledger.ps1`.
5. Resolve the B5-0019 and B5-0020 status ambiguity.
