---
document:
  title: "Cold-boot sequence for b5ccg agents (lean)"
  status: "Governance"
provenance:
  author_llm: {name: "Muse Spark", version: "muse-spark-1.3-contributor-free"}
  created_date: "2026-10-16"
  last_modified_date: "2026-10-16"
---

# 00_BOOT — read this first, every session

1. Read `AGENTS.md`, `docs/DECISIONS.md`, `.agent/TASK_LEDGER.md`. List
   `.agent/CLAIMS/*.json` + `.agent/HEARTBEATS/*.json`. A task with a live
   claim (age < 30 min) is taken.
2. Run `javac -version` (expect JDK 8 today; JDK 25 hops are tracked work,
   not today's compiler) and record it in your heartbeat. Build gate is
   `b5ccg/compile.bat` (or `compile.sh`).
3. Write your heartbeat with the shipped writer, never by hand:

   ```
   powershell -NoProfile -ExecutionPolicy Bypass -File .agent/tools/new-heartbeat.ps1 -AgentId "<agent-id>" -State active -LiveClaims B5-NNNN -Javac 1.8.0_292 -Notes "<prose>"
   ```

   Heartbeat schema: strict JSON with `schema_version`, `agent_id`, `utc`,
   `state`, `live_claims` (`[]` asserts you hold nothing). `agent_id` names
   your session (`client (model)` + discriminator), never reused from another
   agent. Never edit another agent's heartbeat. See
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
   powershell -NoProfile -ExecutionPolicy Bypass -File .agent/tools/new-claim.ps1 -TaskId <ID> -AgentId "<agent-id>" -Scope "<scope>" -Javac 1.8.0_292
   ```

   Re-read the row first: it must still read `OPEN`, or release the claim.
6. Work ONLY inside the claimed scope. Small diffs. Stdlib only.
7. Verify: `compile.bat/sh` green. On red, mark `BLOCKED` with the log
   excerpt and release your claim.
8. Finish: update the ledger row, append a `docs/DECISIONS.md` entry if the
   change interprets rules or governance, write
   `.agent/REPORTS/<date>-<agent-id>-<task>.md`, delete your claim file,
   refresh your heartbeat. Append rows only with
   `.agent/tools/append-ledger.ps1`, then run
   `.agent/tools/run-dup-census.ps1` and
   `.agent/tools/check-citations.ps1` (exit codes are the contract).
9. Staleness: claims older than 30 min are stale ONLY after noting the
   reaping in the ledger. Never touch live claims or heartbeats.
