#!/usr/bin/env bash
# Simple single-agent loop for b5ccg.
#
# Each iteration launches ONE opencode session that:
#   1. Reads .agent/AGENT_PROMPT.md and follows it exactly
#   2. Does its own census (STEP 5: ledger-query.ps1 -Status OPEN)
#   3. Claims the lowest-numbered OPEN task
#   4. Works it, verifies (RUN_TESTS=1 sh compile.sh with JDK 25)
#   5. Flips the row to DONE, writes a report, deletes its claim
#   6. Exits
#
# QUOTA-AWARE: Before each launch, the loop checks for claimable rows using
# PowerShell tools (zero opencode quota). If the queue is drained, it stops
# immediately — no wasted opencode session. This is the key difference from
# run-queue.ps1, which pre-selects a task per iteration for multi-agent lanes.
#
# Prerequisite: JDK 25 + Git Bash on PATH (see AGENT_PROMPT.md STEP 1).
#
# Usage:
#   sh .agent/loop-simple.sh            # default: 20 iterations max
#   sh .agent/loop-simple.sh 50         # 50 iterations max
#   RUN_TESTS=1 sh .agent/loop-simple.sh
#
# Stop conditions:
#   - No claimable rows (run-queue -DryRun reports 0) -> queue drained
#   - Max iterations reached -> stop (re-run to continue)
#   - Agent exits non-zero with "no claimable row" -> queue drained
#
set -euo pipefail

AGENT_PROMPT="Read .agent/AGENT_PROMPT.md and follow it exactly."
REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$REPO_ROOT"

MAX_ITER="${1:-20}"
ITER=0

while [ "$ITER" -lt "$MAX_ITER" ]; do
  ITER=$((ITER + 1))
  echo "=== Loop iteration $ITER / $MAX_ITER ==="

  # --- QUOTA GUARD: check for claimable rows WITHOUT launching opencode ---
  # run-queue.ps1 -DryRun censuses using only PowerShell tools (no opencode
  # quota consumed). We grep its output for the claimable count. If zero
  # claimable rows exist, the queue is drained and we stop immediately.
  DR_OUTPUT=$(powershell -NoProfile -ExecutionPolicy Bypass -File .agent/run-queue.ps1 -DryRun 2>&1 || true)
  CLAIMABLE=$(echo "$DR_OUTPUT" | grep -c "DRY RUN: would invoke" || true)

  if [ "$CLAIMABLE" -eq 0 ]; then
    echo "[$(date -u +%Y-%m-%dT%H:%M:%SZ)] No claimable rows — queue drained. Stopping."
    break
  fi
  echo "[$(date -u +%Y-%m-%dT%H:%M:%SZ)] $CLAIMABLE claimable task(s) found."

  # --- LAUNCH: one opencode session that reads & follows AGENT_PROMPT.md ---
  echo "[$(date -u +%Y-%m-%dT%H:%M:%SZ)] Launching opencode..."
  # opencode run --auto: one-shot, auto-approve permissions, exit on completion.
  # The agent reads AGENT_PROMPT.md, does its own census, claims a task, works it,
  # and exits with code 0 on success or non-zero if it found no claimable rows.
  if opencode run --auto "$AGENT_PROMPT"; then
    echo "[$(date -u +%Y-%m-%dT%H:%M:%SZ)] opencode exited 0."
  else
    EXIT_CODE=$?
    echo "[$(date -u +%Y-%m-%dT%H:%M:%SZ)] opencode exited with code $EXIT_CODE."
    # A non-zero exit with "no claimable row" means the queue is drained.
    # Any other non-zero exit is unexpected — print and continue.
    echo "Continuing to next iteration (sleep 2s)..."
    sleep 2
  fi

  # Brief cooldown between launches so the file system catches up
  sleep 1
done

echo "=== Loop complete: $ITER iteration(s) run ==="
