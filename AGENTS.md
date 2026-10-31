---
document:
  title: "AGENTS.md — b5ccg autonomous development (lean reset)"
  status: "Governance"
provenance:
  author_llm: {name: "Muse Spark", version: "muse-spark-1.3-contributor-free"}
  created_date: "2026-10-16"
  last_modified_date: "2026-10-30"
  last_modified_by_llm: {name: "opencode (Nemotron 3 Ultra)", version: "nemotron-3-ultra-2026-10-30"}
  assessor_llm:
    - {name: "opencode (verifier)", version: "independent-verify-2026-10-16", passes: 1, date: "2026-10-16", note: "updated §2 build rule to reflect Hop-1 (--release 8 / JDK 25), owner-approved"}
  unassessed_revision:
    - {author_llm: {name: "opencode (Space Bunny Free)", version: "space-bunny-free-2026-10-19-01"}, date: "2026-10-19", change: "section 2 rulebook pin re-pinned to the canonical LF bytes under B5-0043 and B5-0047", note: "authored by this agent and NOT independently assessed; no assessor_llm entry is claimed for this pass"}
    - {author_llm: {name: "opencode (Space Bunny Free)", version: "space-bunny-free-2026-10-21-01"}, date: "2026-10-21", change: "section 1: added the reviewer-independence-by-model-family rule under B5-0045", note: "authored by this agent and NOT independently assessed; no assessor_llm entry is claimed for this pass. A same-family advisory review of the surrounding B5-0044 work was obtained and is recorded in the B5-0044 report's front matter under advisory_review, explicitly marked as not independent. A different-family assessment of this revision is outstanding."}
    - {author_llm: {name: "opencode (Nemotron 3 Ultra)", version: "nemotron-3-ultra-2026-10-30"}, date: "2026-10-30", change: "Added Authority Designation Rule (§3), Proposal Self-Identification (§3), Data-Authority Gate (§4), created docs/AUTHORITY_INDEX.md, created .agent/tools/check-data-authority.py, added _proposal marker to proposal files, added source_of_truth to deployed premiere.json, fixed deployed premiere.json stats to match authoritative-character-data.json", note: "authored by this agent and NOT independently assessed; no assessor_llm entry is claimed for this pass"}
---

# AGENTS.md — b5ccg (lean reset, Claude v1 §7 B1/B2)

Fork of `Babylon5CCGGameDesign` (now in hiatus, left untouched). Continues
from `docs/proposals/claude-b5ccggamedesign-system-improvements-v1.md`, which
is candidate-tier until claimed work lands it.

## 1. Provenance

Every LLM-created `.md` that bears authority (governance, proposals, reports
under `.agent/REPORTS/`) opens with `author_llm: <name> (<version>)`.
Original `author_llm` is never overwritten. Later editors append one
`assessor_llm` entry per agent (with `passes` count) and update
`last_modified_by_llm` + `last_modified_date`. Per-task scratch carries
provenance in the ledger row instead of front matter. Never list yourself as
both author and assessor in the same pass.

**Reviewer independence is by model family, not by agent id.** A different
agent id is not independence: two agent ids can be the same model, and the
same model reviewing its own work reproduces the author's blind spots by
construction. The assessing agent must run a *different model family* from the
authoring agent, and the assessor entry must name the family so a later
reader can check the independence rather than take it on trust.

* **Family** means the model lineage (Claude, GPT, Gemini, Gemma, Llama, Qwen,
  GLM, Kimi, DeepSeek, Mistral, Grok, Nemotron, Step, MiniMax, Mimo, ...), not
  the provider, not the vendor, and not the agent id. A `deepseek` served by
  `kenari` is a different family from a `claude` served by the same provider.
* **Free is sufficient.** Independence is not bought. As measured on
  2026-10-21, the free ($0 input and $0 output) catalogue holds **83 text
  LLMs across 35 families** (of 411 catalogued models, 97 priced at exactly
  $0). A different-family reviewer at $0 is always available, so a paid tier is
  never a reason to skip the check. Re-measure before citing the numbers; the
  catalogue moves.
* **An assessor is a different family, or it is not an assessor.** If no
  different-family agent is available, the work is recorded as
  `unassessed_revision` (author-written, not independently assessed) rather
  than given a nominal assessor. That is an honest label, not a failure.
* **Self-assessment stays prohibited.** Different family and different agent
  id are both required; a different family does not license the same agent to
  assess its own work, and a different agent id does not substitute for a
  different family.
* **A reviewer from the maker's own family is an unverified claim.** If the
  assessing model shares a family with the model that produced the work, the
  assessment carries the same blind spots as the work and must not be recorded
  as independent. The same reasoning already applies to advice that benefits
  the adviser's own maker (see `docs/DECISIONS.md`).


## 2. Build rules

* `b5ccg/src/` is stdlib-only Java. **Build: `javac --release 8` (Hop 1, per
  B5-0007/D2)** via `b5ccg/compile.bat` / `b5ccg/compile.sh`, run under JDK 9+
  — a JDK 25 LTS is staged at `b5ccg/scratch/jdk25-win/jdk-25.0.4.1+1` and is
  the intended compiler. `--release 8` emits Java 8-compatible bytecode, so the
  runtime target is unchanged from the fork point; the legacy JDK 8
  (`C:\Program Files\AdoptOpenJDK\jdk-8.0.292.10-hotspot`) is retained only as
  a reference and cannot drive the build (its `javac` rejects `--release`).
* **Policy (adopted at fork, Claude T1/D2): staged move to JDK 25 LTS.**
  Hop 1: `--release 8` under JDK 25. Hop 2: `--release 25`. No mass rewrite;
  modernise on touch. The Java 6 census (`.agent/tools/census-b50960.py`)
  runs one sentinel cycle after Hop 1, then retires.
* **Toolchain location.** `compile.bat` and `compile.sh` invoke `javac` from
  `PATH` and select no JDK themselves — they do **not** read `JAVA_HOME`. Hop 1
  needs a JDK 9+ compiler, because `--release` is not accepted by JDK 8 `javac`;
  put the staged JDK named above (or another JDK 9+) on `PATH` before building.
  The staged path is stated in exactly one place (§2 above); other documents
  refer to "the staged JDK". `b5ccg/scratch/`, `.agent/CLAIMS/` and
  `.agent/HEARTBEATS/` are machine-local state and are **not** covered by
  `.gitignore`, so check `git status` before staging anything from them.
* **Agent ids** use only letters, digits and hyphens (for example
  `opencode-muse-spark-1-3-01`). Report file names are built from the agent id,
  so spaces and parentheses are not allowed.
* No external libraries in the shipped game without explicit human approval.
  Test/tooling-scope dependencies are approved in principle only (D4), not now.
* `BABYLON5_CCG_RULEBOOK.md` is canonical — never edit the body. Pin: SHA-256
  `cc765af40e66c629ebb7caaf768cf1fbd64474be33b8d1bb044ac863a7f3eab7`,
  134,571 bytes of pure LF, 1,236 lines on disk (689 non-blank); full pin record
  and the 689-vs-1236 reconciliation live in `docs/RULEBOOK_PIN.md` (B5-0002,
  re-pinned B5-0043/B5-0047). The canonical form is the committed blob, and
  `.gitattributes` enforces it: pin the file's bytes, never a checkout
  artefact of one machine's `core.autocrlf` setting.
  Interpretations go in `docs/DECISIONS.md` with a rule-ID.
* No new `.md` files at the repo root. Proposals → `docs/proposals/`,
  observations → `.agent/REPORTS/`. One pattern file per lesson *family*
  (not per task), filed under `.agent/PATTERNS/<agent-id>/` only when the
  novelty check passes.

## 3. Statuses

* `b5ccg/src/` + root rulebook = current truth.
* `docs/proposals/` = candidates, never truth until merged + compiled.
* `.agent/REPORTS/` = observations, no authority.
* Copying or summarising never confers authority.

**Authority Designation Rule.** Exactly one file per data domain holds authoritative data. It must:
- Live at repo root or `docs/` (never `.agent/`, never `docs/proposals/`)
- Carry front matter or top-level field: `status: "Authoritative"` + `source: "HUMAN-SCANNED"` (or equivalent provenance)
- Be **referenced by name** in `docs/DECISIONS.md` with a rule-ID
- All other files in that domain must include a `source_of_truth` field pointing to it
- Any document that names a different file as "authoritative" is itself erroneous and must be corrected.

**Proposal Self-Identification.** Every file in `docs/proposals/` (JSON or Markdown) must carry a machine-readable marker:
```json
{
  "_proposal": true,
  "_status": "candidate",
  "_authority": "<path-to-authoritative-file>",
  ...
}
```
Tooling (compile gate, linter) fails if any proposal lacks this, or if any non-proposal file references a proposal as authoritative.

## 4. Promotion

A proposal becomes truth when: (1) claimed scope only; (2) the **verification
gate** passes: `RUN_TESTS=1 sh compile.sh` exits 0 — compile, conformance suite
and gate probes (this needs a JDK 9+ `javac` on `PATH`; see §2). **Baseline
rule.** The gate carries failures that predate this fork and are recorded in
`docs/DECISIONS.md`. A plain `compile.bat` is a fast compile-only check and is never sufficient for
promotion; (3) change logged in `docs/DECISIONS.md`; (4) claim released. A task
that adds behaviour must also add or extend a probe that fails without the
change. External libraries and toolchain hops need a DECISIONS entry. If in
doubt, log the ambiguity in the report and STOP that item only.

**Data-Authority Gate.** The verification gate additionally runs a data-authority check:
- For each data domain with an authoritative source (per `docs/AUTHORITY_INDEX.md`), the deployed file must match the authoritative source on all canonical fields.
- Failure to match = gate fails with a diff report.
- This check runs **before** promotion; a proposal that only updates the deployed file to match the authoritative source passes this check automatically.

## 5. Shared-files protocol (`.agent/`)

Boot: `.agent/00_BOOT.md`. Tasks: `.agent/TASK_LEDGER.md` (live ledger kept
small; DONE rows older than ~14 days rotate to monthly files). Claim with
`.agent/tools/new-claim.ps1`, heartbeat with
`.agent/tools/new-heartbeat.ps1`, append rows only with
`.agent/tools/append-ledger.ps1` (never `Add-Content` — see old AGENTS.md
§5 B5-2400 for why). Reports to `.agent/REPORTS/<date>-<agent-id>-<task>.md`.
One writer per scope (`engine/`, `model/`, `ai/`, `ui/`); 30-min TTL; small
diffs; never touch another agent's claim or heartbeat. Agents never push and
never commit to `main`/`master`; a task-branch commit is allowed only as D3 sets
out (see Backups below). Git is change-tracking only, not authority.

**Scopes.** Writer scopes are `engine/`, `model/`, `ai/`, `ui/`, `resources/`
(card JSON, census, decks), `tooling/` (`.agent/tools/`, compile scripts,
snapshot job) and `docs/` (proposals, DECISIONS, ledger). A task that must touch
more than one scope names all of them in its row and may claim only when no
other live claim holds any of them. Renames and other cross-scope refactors need
a freeze window: the planner marks it in the ledger, and no other claim is
created until the row is DONE.

**Backups.** A non-agent snapshot job (B5-0009) commits the working tree to a
snapshot branch on a schedule — built 2026-10-16, **not enabled** pending owner
review, so no snapshot branch exists yet. The planner checks at the start of each
session that the newest snapshot is under 24 hours old and records the result in
the ledger header, writing `none found` when there is none. A restore drill
(B5-0001 acceptance) is re-run after any change to the snapshot job. (D3 also
approves private task branches; the snapshot job is separate from those.)

**Rule-IDs.** Rulebook sections are cited as `RB:<chapter>/<slug>` (for example
`RB:III/conflict-round`), with an optional paragraph ordinal (`RB:V#7`). The
table is `docs/RULE_INDEX.md`, generated from the pinned rulebook by
`.agent/tools/build-rule-index.ps1`; regenerate it rather than hand-editing it.
IDs are never renumbered or reused. Legacy `RB:<line>` citations stay valid in
history; new text uses IDs. Every DECISIONS interpretation cites at least one ID.
Coverage of those IDs is measured against a hand-kept sidecar in
`docs/RULE_COVERAGE_MAP.md` and reported by `.agent/tools/rule-coverage.ps1`;
`.agent/tools/check-citations.ps1` fails on any cited ID that is not in the
index.
