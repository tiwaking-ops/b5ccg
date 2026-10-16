---
document:
  title: "AGENTS.md — b5ccg autonomous development (lean reset)"
  status: "Governance"
provenance:
  author_llm: {name: "Muse Spark", version: "muse-spark-1.3-contributor-free"}
  created_date: "2026-10-16"
  last_modified_date: "2026-10-16"
  last_modified_by_llm: {name: "opencode (tencent/hy4-preview)", version: "opencode-hy4-preview-01-2026-10-16"}
  assessor_llm:
    - {name: "opencode (verifier)", version: "independent-verify-2026-10-16", passes: 1, date: "2026-10-16", note: "updated §2 build rule to reflect Hop-1 (--release 8 / JDK 25), owner-approved"}
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
  `1c99c44b7b64c8927f1e952a22a49d5886788da2d08e211092b5558e154716d0`,
  1,236 LF lines on disk (689 non-blank); full pin record and the
  689-vs-1236 reconciliation live in `docs/RULEBOOK_PIN.md` (B5-0002).
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

## 4. Promotion

A proposal becomes truth when: (1) claimed scope only; (2) the **verification
gate** passes: `RUN_TESTS=1 sh compile.sh` exits 0 — compile, conformance suite
and gate probes (this needs a JDK 9+ `javac` on `PATH`; see §2). **Baseline
rule.** The gate carries failures that predate this fork and are recorded in
`docs/DECISIONS.md` — **four named checks**, not a count: `[AES] HARD initiates
the losing matchup when 6 aftermaths anticipate the win`; `[AIJ] HARD initiates
against the weak target, never the leader`; `[MINES] reactive mines adds +1
return damage (fleet)`; `[MINES] round-trip: mines played then attack triggers
+1 return damage`. Where the gate is non-zero for that recorded reason alone, the
change still passes provided the failing set is **identical** to the recorded
baseline — same four named checks, nothing new, nothing newly silently passing.
Any check failing outside these four names is a red gate. Compare gate logs by
verdict and failing-name set, never by byte equality. Record the comparison in
the report. `B5-0017` owns repairing them; the clause above is deleted only in
the task that repairs them.
A plain `compile.bat` is a fast compile-only check and is never sufficient for
promotion; (3) change logged in `docs/DECISIONS.md`; (4) claim released. A task
that adds behaviour must also add or extend a probe that fails without the
change. External libraries and toolchain hops need a DECISIONS entry. If in
doubt, log the ambiguity in the report and STOP that item only.

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
