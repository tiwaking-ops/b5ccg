---
document:
  title: "Cold-boot sequence for b5ccg agents (lean)"
  status: "Governance"
provenance:
  author_llm: {name: "Muse Spark", version: "muse-spark-1.3-contributor-free"}
  created_date: "2026-10-16"
  last_modified_date: "2026-10-16"
  last_modified_by_llm: {name: "opencode (tencent/hy4-preview)", version: "opencode-hy4-preview-01-2026-10-16"}
---

# 00_BOOT — read this first, every session

1. Read `AGENTS.md`, `docs/DECISIONS.md`, `.agent/TASK_LEDGER.md`. List
   `.agent/CLAIMS/*.json` + `.agent/HEARTBEATS/*.json`. A task with a live
   claim (age < 30 min) is taken.
2. Run `javac -version` and record it in your heartbeat. Expect JDK 9+ — in
   practice the staged JDK 25 named in `AGENTS.md` §2 or another JDK 9+ — **on
   `PATH`**. The scripts call bare `javac` and do not read `JAVA_HOME`, so
   having `JAVA_HOME` set changes nothing. The project compiles with
   `--release 8` (Hop 1) until Hop 2 is decided. A JDK 8 `javac` cannot build
   the project and is a stop condition. Fast gate: `b5ccg/compile.bat` or
   `compile.sh`. **Verification gate: `RUN_TESTS=1 sh compile.sh`.**
3. Write your heartbeat with the shipped writer, never by hand:

   ```
   powershell -NoProfile -ExecutionPolicy Bypass -File .agent/tools/new-heartbeat.ps1 -AgentId "<agent-id>" -State active -LiveClaims B5-NNNN -Javac <recorded-javac> -Notes "<prose>"
   ```

   Pass the version you ACTUALLY recorded in step 2 as `-Javac`; do not copy
   a version from any document. Use an agent id matching the pattern in
   `AGENTS.md` §2 (letters, digits and hyphens only), because report file
   names are built from it.

   Heartbeat schema: strict JSON with `schema_version`, `agent_id`, `utc`,
   `state`, `live_claims` (`[]` asserts you hold nothing). `agent_id` names
   your session, never reused from another agent; use the agent-id pattern in
   `AGENTS.md` §2. Never edit another agent's heartbeat. See
   `.agent/HEARTBEATS/README.md`.
4. OPEN-task census ONLY via the shared tool:

   ```
   powershell -NoProfile -ExecutionPolicy Bypass -File .agent/run-queue.ps1 -DryRun
   ```

   Do NOT hand-grep the ledger for OPEN rows. Claims-first: a row under a
   live claim reads `suppressed-live-claim` — re-census after release.
5. Claim atomically (never overwrite an existing claim file; non-zero exit
   means no claim — stop that item):

   ```
   powershell -NoProfile -ExecutionPolicy Bypass -File .agent/tools/new-claim.ps1 -TaskId <ID> -AgentId "<agent-id>" -Scope "<scope>" -Javac <recorded-javac>
   ```

   Re-read the row first: it must still read `OPEN`, or release the claim.
6. Work ONLY inside the claimed scope. Small diffs. Stdlib only.
7. Verify: the **verification gate** exits 0 — `RUN_TESTS=1 sh compile.sh`.
   On red, mark `BLOCKED` with the log excerpt and release your claim. A green
   fast gate (`compile.bat`/`compile.sh`) alone is NOT a pass. Where the gate is
   non-zero only for the failures recorded as pre-existing in `docs/DECISIONS.md`
   — the **four baseline checks named in `AGENTS.md` §4** (AES losing matchup,
   AIJ weak target, MINES fleet reactive return damage, MINES round-trip), never
   the four named checks — `[AES] HARD initiates the losing matchup when 6 aftermaths anticipate the win`; `[AIJ] HARD initiates against the weak target, never the leader`; `[MINES] reactive mines adds +1 return damage (fleet)`; `[MINES] round-trip: mines played then attack triggers +1 return damage` — compare by failing-name set against that baseline per `AGENTS.md` §4:
   identical failing-name set passes, any name outside it is red. Compare gate
   logs by verdict and failing-name set, never by byte equality.
8. Finish: update the ledger row, append a `docs/DECISIONS.md` entry if the
   change interprets rules or governance, write
   `.agent/REPORTS/<date>-<agent-id>-<task>.md`, delete your claim file,
   refresh your heartbeat. Append rows only with
   `.agent/tools/append-ledger.ps1`, then run
   `.agent/tools/run-dup-census.ps1` and
   `.agent/tools/check-citations.ps1` (exit codes are the contract).
9. Staleness: claims older than 30 min are stale ONLY after noting the
   reaping in the ledger. Never touch live claims or heartbeats.
