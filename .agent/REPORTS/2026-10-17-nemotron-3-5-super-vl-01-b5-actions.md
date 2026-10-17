---
document:
  title: "Actions on Claude's ranked items from chat-results report"
  status: "Observation only — no authority"
  placement: ".agent/REPORTS/ (observations per AGENTS.md §2)"
provenance:
  author_llm: {name: "opencode (nemotron-3.5-super-vl)", version: "nemotron-3.5-super-vl-01"}
  created_date: "2026-10-17"
  last_modified_date: "2026-10-17"
  last_modified_by_llm: {name: "opencode (nemotron-3.5-super-vl)", version: "nemotron-3-5-super-vl-01"}
---

# Actions taken on Claude's ranked items (from 2026-10-16-opencode-hy4-preview-01-chat-results.md)

This report records actions taken on Claude's ranked action items from the chat-results report. Nothing here carries authority beyond what `docs/DECISIONS.md` records.

## Items completed

### 1. `.gitignore` — root `out/` added (Claude item 1)

Added `out/` to `.gitignore` alongside the existing `b5ccg/out/`. The snapshot-relevant paths `b5ccg/scratch/`, `.agent/CLAIMS/`, `.agent/HEARTBEATS/` were already ignored (a prior edit added them). Root `out/` was the only remaining unignored build-output path from the report's list.

### 3. Baseline recorded by check name (Claude item 3)

Added the four exact failing check names to `docs/DECISIONS.md` in the governance-fixes entry (the baseline-relative promotion discussion):

* `[AES] HARD initiates the losing matchup when 6 aftermaths anticipate the win`
* `[AIJ] HARD initiates against the weak target, never the leader`
* `[MINES] reactive mines adds +1 return damage (fleet)`
* `[MINES] round-trip: mines played then attack triggers +1 return damage`

These match the recorded baseline "AES×1, AIJ×1, MINES×2 = 4 of 904". Verified by running `RUN_TESTS=1 sh compile.sh` under the staged JDK 25: identical failing set, no new failures, no newly-passing checks.

### 3b. New OPEN ledger row B5-0017 for baseline repair

Added `.agent/TASK_LEDGER.md` row B5-0017 (scope `engine`) owning repair of the four baseline checks, with acceptance: `RUN_TESTS=1 sh compile.sh` exits 0 (904/904) or the failing set is empty.

### 4. B5-0008 reconciled (Claude item 4)

Changed `.agent/TASK_LEDGER.md` row B5-0008 from `OPEN` to `DONE`, with report path `.agent/REPORTS/2026-10-16-opencode-glm-5-3-flash-free-01-B5-0008.md`. `docs/DECISIONS.md:299` already recorded it DONE; the ledger now matches.

### 5. §5 contradiction checked (Claude item 5)

`AGENTS.md` §5 lines 99-101 already correctly reflect D3: "no push, no commit to `main`/`master`; task-branch commits allowed as D3 sets out." There is no contradiction between "agents never commit to main/master" and "private task-branch commits allowed." Verified consistent with `docs/DECISIONS.md:284-288`. No edit needed.

### 6. Root `.md` files moved (Claude item 6)

* `claude-b5ccg-governance-and-ledger-fixes-v1.md` → `docs/proposals/claude-b5ccg-governance-and-ledger-fixes-v1.md`
* `b5ccg-claude-desktop-fixes-handoff-v1.md` → `.agent/REPORTS/b5ccg-claude-desktop-fixes-handoff-v1.md`
* `b5-claude-files.md` → `.agent/REPORTS/b5-claude-files.md`

No root `.md` files remain (other than the pre-existing `README.md`, `AGENTS.md`, `BABYLON5_CCG_RULEBOOK.md`).

### 7. Probe output made deterministic (Claude item 7)

Changed `CardEffects.behaviourReferences()` (`b5ccg/src/b5ccg/engine/CardEffects.java:1725`) from `new HashSet<String>()` to `new TreeSet<String>()`. This makes behaviour-reference iteration order deterministic (sorted) instead of HashMap/HashSet order, so gate probe lines come out in the same order across runs. `import java.util.*` already covers `TreeSet`.

Verified: recompiles clean with `javac --release 8` under JDK 25; conformance suite shows the identical 4 baseline failures (no new failures, no newly-passing checks).

## Items not acted on (owner actions or blocked)

### 2. Manual commit (Claude item 2)

Not done — AGENTS.md §5: agents never commit or push. The tree holds uncommitted edits from several agents; this is an owner action.

### Blocked — Rule-ID scheme / rulebook headings (Claude blocked item 1)

The WP-18 rule-ID scheme needs rulebook section headings extracted from `BABYLON5_CCG_RULEBOOK.md` (heading lines only). Still needed to resolve interim `RB:<line>` citations.

### Blocked — CardEffects canonical-id triage (Claude blocked item 2)

Needs `CardEffects.java` itself for the canonical-id triage review. Not blocked by this session's changes; the file is present and readable.

### B5-0012 assessor pass (Claude item 5)

Not done. `B5-0012` has a claim file (`.agent/CLAIMS/B5-0012.json`) held by `opencode-glm-5-3-flash-free-01`. Per AGENTS.md §5, never touch another agent's claim. The assessor pass must be recorded by a different agent than `opencode-hy4-preview-01` (who authored the B5-0012 work), and only after the claim is released.

## Verification gate

`RUN_TESTS=1 sh compile.sh` under staged JDK 25: `CONFORMANCE SUITE FAILED (4 of 904 checks)` — identical failing set to the recorded baseline (the 4 checks listed above). Baseline-relative pass per AGENTS.md §4.