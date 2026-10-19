---
document:
  title: "B5-0042 — Owner rulings recorded: model tiers, interested-party advice, repository visibility, and the line-ending pin"
  status: "Report. Records work performed under a claim; carries no authority beyond the DECISIONS entry it produced (AGENTS.md section 3)."
  task: "B5-0042"
  scope: "docs"
author: "opencode (Space Bunny Free)"
agent_id: "opencode-space-bunny-free-01"
provenance:
  author_llm: {name: "opencode (Space Bunny Free)", version: "space-bunny-free-2026-10-19-01"}
  created_date: "2026-10-19"
  last_modified_date: "2026-10-19"
  last_modified_by_llm: {name: "opencode (Space Bunny Free)", version: "space-bunny-free-2026-10-19-01"}
  assessor_llm: none
  no_self_assessment: true
  javac_actually_recorded: "javac 25.0.4.1 (staged JDK at b5ccg/scratch/jdk25-win/jdk-25.0.4.1+1, placed on PATH for this session; the JDK 8 on the default PATH is a stop condition per AGENTS.md section 2 and was not used)"
  basis: "Owner rulings taken in session on 2026-10-19 and approved for recording. Rulings 3 and 4 rest on measurements taken in this session and reproduced below; rulings 1 and 2 are the owner's decisions on their own facts."
---

# B5-0042 — four owner rulings, and one escalation

## What this row did

Recorded four owner rulings as a single `docs/DECISIONS.md` entry, and escalated
a fifth item that the rulings uncovered but did not resolve. No `.java`, JSON,
rulebook, census or ledger-semantics change was made. The row was claimed before
any edit (`new-claim.ps1`, docs scope) and released after.

## The four rulings

1. **Free models only, no Claude Code exception.** D-3 stands as written. Every
   agent invocation must name a $0 catalog model. The `claude-code` tier has five
   models all with cost unset, so no spending ceiling is measurable from the
   catalog at all, which is why a numeric cap could not have been enforced even
   if one had been wanted.

2. **Interested-party recommendations are unverified claims.** An agent
   recommending something that profits its maker carries no evidentiary weight,
   the same category as a self-assessment under `AGENTS.md` section 1. This was
   added because the recommendation in question was relayed here without that
   caveat, which was an omission on my part.

3. **The repository is public intentionally; the rulebook raises no copyright
   concern.** Closes the copyright question raised in the exposure plan. Commit
   metadata is a separate matter and no ruling is recorded on it.

4. **LF is canonical for the rulebook; re-pin before `.gitattributes`.** Ordered
   instructions are in the entry.

## The escalation: the pin is not reproducible off this machine

This was not a ruling. It is a defect the rulings ran into, and it is the most
consequential thing found in this session.

| Form of the rulebook | Bytes | SHA-256 | Matches pin? |
|---|---|---|---|
| Working tree, this machine | 134,586 | `1c99c44b...716d0` | **yes** |
| Committed git blob | 134,571 | `cc765af4...3eab7` | no |
| Fresh clone checkout | 135,807 | `165d11dd...483250` | no |

`RuleSetProfile` pins `1c99c44b...`, the hash of this machine's working-tree
file, and its own comment says so. The committed rulebook is pure LF across all
1,236 lines; this working tree carries CRLF on lines 1 to 14 and 1236 only.

Reproduced by execution:

* `build-rule-index.ps1 -Check` on a fresh clone exits **-1** with
  `GUARD FAILURE - rulebook SHA-256 165d11dd... differs from the pin`, and states
  that every line range below is void until consciously re-pinned.
* `HeadlessRulebookPinProbe` is a gate class in `compile.sh`, so the gate result
  of record holds on this machine only.
* Forcing the rulebook to all-LF, and separately to all-CRLF, both produce a
  different hash. No `.gitattributes` value both normalises line endings and
  preserves the current pin.

**Why this outranks the four rulings.** `AGENTS.md` section 4 makes the
verification gate the promotion rule. That gate currently passes on one machine
only. Every `RB:<chapter>/<slug>` citation resolves against a line table that
cannot be regenerated elsewhere, and `.agent/tools/rule-coverage.ps1` cannot be
run off this machine. Repairing `flip-ledger.ps1` (H-1) is worth doing; making the
gate reproducible is worth more.

**No row was opened for it under B5-0042**, whose acceptance covered the four
rulings only. It needs a tooling-scope row and an owner decision on whether to
proceed. Recorded in the DECISIONS entry so the next agent inherits the
measurement.

## Verification

| Check | Result |
|---|---|
| `check-citations.ps1` | exit 0; every `RB:` citation resolves |
| `run-dup-census.ps1` | exit 0; 0 duplicate task IDs |
| `lint-ledger.ps1` | exit 0; PASS, 40 rows in table |
| `lint-encoding.ps1` | 20 BAD repo-wide, unchanged from before this row; DECISIONS.md still shows exactly its 8 pre-existing U+FFFD and none in the new entry |
| `RUN_TESTS=1 sh compile.sh` | **exit 0** — 31 passed, 0 failed, of 31 gate classes; total gate time 240,118 ms; evidence in `.agent/REPORTS/evidence/GATE-B5-0042.txt` |

The gate ran on the staged JDK 25 (`javac 25.0.4.1`), 189 source files. This is
the verification `AGENTS.md` section 4 requires for promotion, and it passed. Note
the boundary recorded above: `HeadlessRulebookPinProbe` passed **on this machine**,
which is exactly the limitation the escalation describes. A green gate here is not
a reproducible green gate.

`check-citations.ps1` exited 1 on its first run against the new DECISIONS entry,
because that entry referenced this report before the report existed. That is the
checker doing its job; it passed once the report was written.

No `.java` file, JSON file, census file or rulebook byte was touched. The only
writes were the ledger row, this report, and the DECISIONS entry.

## A note on my own conduct this session

Two mistakes of mine are recorded here rather than left in the documents where
they happened, because both would otherwise be invisible to the next agent:

* I relayed Claude's recommendation to adopt a paid tier and endorsed it, without
  noting that Claude is recommending its own maker's product. Ruling 2 exists
  because of that omission.
* I asserted "five of the twelve acceptance cases pass vacuously" without having
  run them. Both numbers were wrong: the list has thirteen items, and six of the
  nine I could exercise pass, all for the wrong reason. Corrected in place in
  `reply-...-v3.md` and superseded by `v4`.

## State at release

* Row B5-0042 DONE, claim released, heartbeat refreshed.
* Ledger: 40 rows in table, 9 OPEN before this row, 10 after minus this row.
* Gate verdict of record unchanged by this row; a docs-only change cannot alter it.
* The pin escalation is **not** actioned by this row and remains open.