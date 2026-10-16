---
document:
  title: "AGENTS.md — b5ccg autonomous development (lean reset)"
  status: "Governance"
provenance:
  author_llm: {name: "Muse Spark", version: "muse-spark-1.3-contributor-free"}
  created_date: "2026-10-16"
  last_modified_date: "2026-10-16"
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

* `b5ccg/src/` is stdlib-only Java. **Today: `javac -source 6 -target 6` on
  JDK 8** (`b5ccg/compile.bat`, `b5ccg/compile.sh`) — unchanged from the fork
  point so the tree stays green on the only JDK on this host.
* **Policy (adopted at fork, Claude T1/D2): staged move to JDK 25 LTS.**
  Hop 1: `--release 8` under JDK 25. Hop 2: `--release 25`. No mass rewrite;
  modernise on touch. The Java 6 census (`.agent/tools/census-b50960.py`)
  runs one sentinel cycle after Hop 1, then retires.
* No external libraries in the shipped game without explicit human approval.
  Test/tooling-scope dependencies are approved in principle only (D4), not now.
* `BABYLON5_CCG_RULEBOOK.md` is canonical — never edit the body. Pin: SHA-256
  `1c99c44b7b64c8927f1e952a22a49d5886788da2d08e211092b5558e154716d0`,
  689 lines on disk (CRLF count; Claude's copy counted 1,236 LF lines — same
  bytes). Interpretations go in `docs/DECISIONS.md` with a rule-ID.
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

A proposal becomes truth when: (1) claimed scope only, (2) `compile.bat/sh`
green, (3) change logged in `docs/DECISIONS.md`, (4) claim released. External
libraries and toolchain hops need a DECISIONS entry. If in doubt, log the
ambiguity in the report and STOP that item only.

## 5. Shared-files protocol (`.agent/`)

Boot: `.agent/00_BOOT.md`. Tasks: `.agent/TASK_LEDGER.md` (live ledger kept
small; DONE rows older than ~14 days rotate to monthly files). Claim with
`.agent/tools/new-claim.ps1`, heartbeat with
`.agent/tools/new-heartbeat.ps1`, append rows only with
`.agent/tools/append-ledger.ps1` (never `Add-Content` — see old AGENTS.md
§5 B5-2400 for why). Reports to `.agent/REPORTS/<date>-<agent-id>-<task>.md`.
One writer per scope (`engine/`, `model/`, `ai/`, `ui/`); 30-min TTL; small
diffs; never touch another agent's claim or heartbeat. Agents do not commit
or push. Git is change-tracking only, not authority.
