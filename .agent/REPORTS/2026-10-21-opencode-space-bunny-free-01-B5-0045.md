---
document:
  title: "B5-0045 - reviewer independence by model family"
  status: "Report. Observation, no authority (AGENTS.md section 3)."
provenance:
  author_llm: {name: "opencode (Space Bunny Free)", version: "space-bunny-free-2026-10-21-01"}
  created_date: "2026-10-21"
  last_modified_date: "2026-10-21"
  last_modified_by_llm: {name: "opencode (Space Bunny Free)", version: "space-bunny-free-2026-10-21-01"}
  assessor_llm: none
  no_self_assessment: true
---

# B5-0045 - reviewer independence by model family

Claim: `docs`. Agent id `opencode-space-bunny-free-01`.
Ledger row: `B5-0045` (OPEN -> DONE).

## The row's acceptance, and how each clause was met

> the rule is stated in AGENTS.md and in a DECISIONS entry with the free-tier
> measurement; one worked example recorded; check-citations.ps1,
> run-dup-census.ps1 and lint-ledger.ps1 exit 0; gate exits 0 on javac 25.0.4.1

| Clause | Met by | Evidence |
|---|---|---|
| the rule is stated in AGENTS.md | `AGENTS.md` section 1, new paragraph "Reviewer independence is by model family, not by agent id" | `AGENTS.md` |
| ...and in a DECISIONS entry | `docs/DECISIONS.md`, "Reviewer independence is by model family, not by agent id (B5-0045)" | DECISIONS entry |
| ...with the free-tier measurement | the 2026-10-21 measurement, replacing the 2026-10-19 figure | DECISIONS entry |
| one worked example recorded | the same-family reviewer of B5-0044, recorded in the DECISIONS entry and in that row's report front matter under `advisory_review` | below |
| `check-citations.ps1` exits 0 | verified | below |
| `run-dup-census.ps1` exits 0 | verified | below |
| `lint-ledger.ps1` exits 0 | verified | below |
| gate exits 0 on javac 25.0.4.1 | `RUN_TESTS=1 sh compile.sh` -> 31/31 Java + 2 tooling probes, exit 0 | `.agent/REPORTS/evidence/GATE-B5-0045.txt` |

## The rule as written

Into `AGENTS.md` section 1, immediately after the existing provenance rules:

> **Reviewer independence is by model family, not by agent id.** A different
> agent id is not independence: two agent ids can be the same model, and the
> same model reviewing its own work reproduces the author's blind spots by
> construction. The assessing agent must run a *different model family* from the
> authoring agent, and the assessor entry must name the family so a later
> reader can check the independence rather than take it on trust.
>
> * **Family** means the model lineage (Claude, GPT, Gemini, Gemma, Llama, Qwen,
>   GLM, Kimi, DeepSeek, Mistral, Grok, Nemotron, Step, MiniMax, Mimo, ...), not
>   the provider, not the vendor, and not the agent id. A `deepseek` served by
>   `kenari` is a different family from a `claude` served by the same provider.
> * **Free is sufficient.** Independence is not bought. As measured on
>   2026-10-21, the free ($0 input and $0 output) catalogue holds **83 text
>   LLMs across 35 families** (of 411 catalogued models, 97 priced at exactly
>   $0). A different-family reviewer at $0 is always available, so a paid tier is
>   never a reason to skip the check. Re-measure before citing the numbers; the
>   catalogue moves.
> * **An assessor is a different family, or it is not an assessor.** If no
>   different-family agent is available, the work is recorded as
>   `unassessed_revision` (author-written, not independently assessed) rather
>   than given a nominal assessor. That is an honest label, not a failure.
> * **Self-assessment stays prohibited.** Different family and different agent
>   id are both required; a different family does not license the same agent to
>   assess its own work, and a different agent id does not substitute for a
>   different family.
> * **A reviewer from the maker's own family is an unverified claim.** If the
>   assessing model shares a family with the model that produced the work, the
>   assessment carries the same blind spots as the work and must not be recorded
>   as independent. The same reasoning already applies to advice that benefits
>   the adviser's own maker (see `docs/DECISIONS.md`).

## The free-tier measurement, re-measured

The row's text records the 2026-10-19 figure: "41 of the first 100
catalogued models are exactly 0 USD, spanning a dozen-plus families". That
figure was taken from the catalogue on 2026-10-19 and the catalogue has moved.
Re-measured on 2026-10-21 against the live catalogue:

| Measure | 2026-10-19 (row text) | 2026-10-21 (re-measured) |
|---|---|---|
| catalogued models | 390 | **411** |
| models priced at exactly $0 | 41 (of the first 100) | **97** |
| free models that are text LLMs | not counted | **83** |
| free text-LLM families | "a dozen-plus" | **35** |
| providers carrying free models | 4 (opencode, kenari, kilo, google) | **10** (opencode, kenari, kilo, google, nvidia, openrouter, orcarouter, commandcode, poolside, tokenrouter) |

The DECISIONS entry carries the 2026-10-21 numbers and says so explicitly, and
instructs the reader to re-measure before citing them. A governance document
carrying a stale number is the same defect as one carrying a wrong number, and
the row's own figure was already stale by the time the row was worked.

The free families include Claude, GPT, Gemini, Gemma, GLM, Kimi, DeepSeek,
Mistral, Grok, Nemotron, Step, MiniMax, Mimo, Qwen and Llama, among others. The
conclusion the rule rests on is therefore measured, not assumed: **a
different-family reviewer at $0 is always available**, and a paid tier is never
a reason to skip the independence check.

## The worked example

B5-0044 is the example, and it is a real one rather than a hypothetical.

After closing B5-0044, the work was put to a review by a subagent explicitly
instructed to verify by execution rather than by reading, and explicitly asked
to state its own model identity first. The subagent's first line of answer was:

> **I am `minimax-m3.1-flash` (provider `omniroute`), family `minimax`.**
>
> **The author of this work is also `minimax-m3.1-flash` (family minimax).**
> Under AGENTS.md section 1 ... **I am not a valid independent assessor of this
> commit.** My findings below are technically rigorous and independently
> measured, but they are **same-family review** and must be recorded as
> `unassessed_revision` / advisory - NOT as an `assessor_llm` entry.

The reviewer then did the work anyway, and the work was worth having. By
executing against the tree it found:

* three real defects in the B5-0044 guard - a prefix-matching claim filter
  that let a sibling id's live claim block the wrong row, a claim file with no
  readable `agent_id` treated as the caller's own, and a "the tool still
  works" assertion that a completely broken tool could satisfy;
* one **wrong measurement in the author's own report**: the exit codes for two
  of the five tools had been taken with the old tools running from a
  temporary directory, where their CWD-relative defaults resolved to nothing.
  Re-measured at their real path, three of the five exited 0 and printed a
  normal result - a worse defect than the author's table showed.

All four were fixed, and the fixes were themselves proved red-then-green: the
new probe cases 12 and 13 were shown to fail when the corresponding defect is
re-injected, and the strengthened "still works" assertion was shown to fail
when a tool is made unconditionally broken.

So the same-family review was **useful but not independent**, and the
repository now says which of the two it was. The B5-0044 report's front matter
carries it under `advisory_review` with `independence: "NONE - same family as
the author"`, and `assessor_llm: none`.

**What is still outstanding.** B5-0044 has no independent assessment, because
this fleet could not produce one that was independent. The row is closed on
its own merits - every acceptance clause is measured and met - but its
assessment is outstanding, and the honest label on it is
`unassessed_revision`. This is recorded rather than papered over. A
different-family assessment of B5-0044 remains open work, and it is the reason
this rule exists: without the rule, the same-family review would have been
recorded as an assessment and the row would have carried an independence it
did not have.

## Verification

    lint-ledger.ps1      -> exit 0   "=== LEDGER LINT PASS (45 rows in table) ==="
    check-citations.ps1  -> exit 0   "PASS: every RB: citation resolves in docs/RULE_INDEX.md."
    run-dup-census.ps1   -> exit 0   "duplicate-ID census: 0 duplicate task IDs"
    lint-encoding.ps1    -> exit 1   BAD count = 20  (the pre-existing repo baseline)
    RUN_TESTS=1 sh compile.sh -> 31/31 Java classes + 2 tooling probes, exit 0, javac 25.0.4.1

## Reports

`.agent/REPORTS/2026-10-21-opencode-space-bunny-free-01-B5-0044.md` (carries
the advisory review), `.agent/REPORTS/2026-10-21-opencode-space-bunny-free-01-B5-0045.md`.
Gate evidence in `.agent/REPORTS/evidence/GATE-B5-0045.txt`.
