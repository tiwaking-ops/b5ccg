---
document:
  title: "B5-0007 report — WP-06 toolchain hops + policy T1.1-T1.3 (BLOCKED on JDK 25 availability)"
  status: "Observation"
provenance:
  author_llm: {name: "nemotron-3.5-super-vl", version: "free"}
  created_date: "2026-10-16"
  last_modified_date: "2026-10-16"
---

# B5-0007 — WP-06 Toolchain hops + policy T1.1–T1.3

## Claim

Claimed `B5-0007` (scope `tooling`) via `.agent/tools/new-claim.ps1`; agent_id
`opencode (muse-spark-1.3-free) 06`; heartbeat live with `live_claims [B5-0007]`.

## Preconditions

D2 (JDK 25 staged move, no rewrite) is **already APPROVED** by the owner
(2026-10-16, recorded in `docs/DECISIONS.md`), so the "needs D2" precondition is
satisfied. No new DECISIONS entry was required for D2 itself; this task records
the hop execution status below.

## Work performed

### Census sentinel cycle (census-b50960.py) — DONE, clean

Ran `PYTHONIOENCODING=utf-8 py .agent/tools/census-b50960.py` against the
working tree. Result:

- Denominator `b5ccg/src`: 178 `.java` on disk (172 tracked by git, 6 untracked
  — the 6 untracked are prior-task probe sources).
- Denominator `src-java8-archive`: 0 files (frozen archive is empty/retired).
- Every Java 8+ API family reports **0 code-lines**:
  arrow, methodref, stream, computeIfAbsent, computeIfPresent, compute, merge,
  functionalInterface, tryWithResources, diamond, forEach, removeIf — all matches
  are PROSE (inside comments/strings), not code.
- `getOrDefault`: 14 hits, all in `DeckLoader.java`, all calls to the project's
  **own** unqualified helper `DeckLoader.getOrDefault(Map,String,String)`; the
  `qualifiedGetOrDefault` family (`.getOrDefault(`, the `Map` default-method API)
  = 0. These compile fine under `-source 6` and do not block `--release 8`.

Conclusion: the tree contains **zero Java 8+ API usage**, so Hop 1
(`javac --release 8`) faces no source-level blocker. The census sentinel cycle
is recorded and clean (exit 0).

## Blocker: JDK 25 not installed on this host

This host has exactly one JDK: AdoptOpenJDK 8
(`C:\Program Files\AdoptOpenJDK\jdk-8.0.292.10-hotspot`, `javac 1.8.0_292`).
`C:\Program Files\Java`, `C:\Program Files (x86)\Java`, and `$USERPROFILE\.sdkman`
contain no JDK 25. Internet is reachable, but:

- AGENTS.md §2 pins today's compiler as `javac -source 6 -target 6` on JDK 8:
  "the tree stays green on the only JDK on this host," and "JDK 25 hops are
  tracked work, not today's compiler."
- Hop 1 is `--release 8` **under JDK 25**. JDK 8 `javac` does not recognize
  `--release`, so switching the build to `--release 8` here would break the
  JDK 8 build and leave the tree unbuildable/gated-red on this host — contrary
  to the stated build rule.
- No external libraries or toolchain changes may land without explicit human
  approval, and a toolchain hop is a DECISIONS-gated change.

Therefore:
- **"all gates green under JDK 25"** — cannot be satisfied on this host today.
  Blocked pending JDK 25 being installed on the build host.
- **"old pin retired"** — not done. Per AGENTS.md §2 the Java 6 census retires
  *after* Hop 1, which requires JDK 25. The census was run (one sentinel cycle,
  clean) but is intentionally NOT retired yet.
- **"census sentinel cycle run"** — DONE, clean (above).

## What remains to complete B5-0007

Once JDK 25 is available on this host:

1. Install JDK 25; verify `javac --release 8` works.
2. Hop 1: switch `b5ccg/compile.bat` and `b5ccg/compile.sh` from
   `-source 6 -target 6` to `--release 8` (no mass rewrite; stdlib only).
3. Run `compile.bat`/`compile.sh` under JDK 25; confirm all gates green
   (`RUN_TESTS=1 sh compile.sh`).
4. Log a DECISIONS.md entry for the toolchain hop (D2 already approved).
5. Retire the Java 6 census (`census-b50960.py`) after Hop 1 lands per AGENTS.md §2.
6. Release this claim; mark the ledger row DONE.

## Ledger row

`B5-0007` is left `BLOCKED` (JDK 25 unavailable on this host); claim released
after this report. The row references this report for the log excerpt.
