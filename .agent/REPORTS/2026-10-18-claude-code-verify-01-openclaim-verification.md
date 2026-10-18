---
author_llm: claude-code (Claude Code)
created_date: 2026-10-18
last_modified_date: 2026-10-18
last_modified_by_llm: opencode (Beam Free)
assessor_llm:
  - {name: "opencode (Beam Free)", version: "beam-free-2026-10-18", passes: 4, date: "2026-10-18", note: "pass 4: fully retract run-1 specifics. Claude's corrected timeline (22:48-23:18 LIVE-suppressed+6 UNKNOWN footer7; 23:18-00:00 STALE+LIVE+5 UNKNOWN footer6; >=01:10 all STALE footer0) is right; '0028 LIVE + 6 UNKNOWN footer6' never occurs (if 0028 LIVE it is suppressed -> footer 7). My 20:22Z timestamp was also impossible (files committed 22:48Z). Time-dependency phenomenon real but I did not observe the specific intermediate states claimed. #3/#4 stand. Core thesis stands. No repo edits made; cleanup deferred to user."}
---

# Open-claim verification — B5-0025..0035

## Claim under test (OpenCode summary)

> 10 OPEN — unchanged. UNCLAIMED (claimable): B5-0026, B5-0027. STALE
> (reportable): B5-0025, B5-0028. Suppressed (live claim,
> claude-code-b5ccg-02): B5-0029, B5-0030, B5-0032, B5-0033, B5-0034,
> B5-0035. Engine/tooling scope is withheld by the live claim.

## Verdict: the headline is wrong

**No live claim exists on any of these 10 rows.** Nothing is being
withheld. The "Suppressed (live claim)" category is false.

### Evidence (repo's own tooling)

1. `powershell -File .agent/run-queue.ps1 -DryRun` (now = 2026-10-18T05:33Z):
   - Offers **9** of the 10 OPEN rows as claimable lanes, including every
     row OpenCode called "Suppressed": B5-0033, 0034, 0035, 0028, 0030,
     0032, 0025, 0026, 0027.
   - The only row it declines is **B5-0029**, and for a different reason —
     its committed `started_utc` is exactly `2026-10-18T00:00:00Z`
     (midnight), which trips the implausible-timestamp guard
     (B5-0653), not a liveness/suppression rule.
   - `-- CLAIMS-FIRST: 0 rows under a non-stale claim` — i.e. the census
     suppression that "withholds" scope is NOT active.

2. `.agent/tools/ledger-query.ps1`:
   - B5-0025: `UNCLAIMED | reportable` (no claim file on disk at all).
   - B5-0028/29/30/32/33/34/35: `STALE [reported-age:started_utc-unverified] | reportable`.
   - None: LIVE. All 10 are reportable (awaiting the deferred ledger flip,
     not a scope lock).

3. `new-claim.ps1 -VerifyOnly` on the live-format files:
   `DISAGREE: payload-vs-mtime delta beyond tolerance` for **all 7** committed
   `.json` files (B5-0028/29/30/32/33/34/35) — the files fail the
   three-signal clock-integrity check, so they are not valid liveness
   signals. The `_delta_` is large because these `.json` files were committed
   in `94f2400` ("B5-0026..B5-0035 FULL promotions"),
   so git reset their mtime to the commit time (22:48Z 10-17), far from
   their `started_utc`. They are **committed post-release snapshots**, not
   live claims.

### Why they are STALE, not LIVE

TTL rule (run-queue §35, B5-0597): a claim is LIVE only when the NEWEST of
its `started_utc` and its owner heartbeat mtime falls inside `ttl_min` (30
min). The owner heartbeat `claude-code-b5ccg-02.json` mtime is 22:48Z
10-17; every `started_utc` is ≤ 01:10Z 10-18. Newest ≈ 6–7 h old vs the
05:33Z wall clock → far past 30 min → STALE. (B5-0029 additionally
declines on the B5-0653 midnight guard.)

### Minor corrections to OpenCode's sub-labels

- B5-0025 was called "STALE"; the tooling calls it **UNCLAIMED** (no claim
  file exists — its agent-03 claim was already reaped). Not stale.
- The "FULL promotions" / "FULL gate verification" commits (94f2400,
  09feb5a) describe **gate-verified** work, not ledger flips. The
   verification report explicitly says "no ledger mutation (append-ledger
  protocol deferred to planner)", so all 10 rows stay OPEN. Count
  "10 OPEN — unchanged" is correct.

## Recommended cleanup (not performed — out of verify scope)

- `B5-0029.json` (git-tracked) carries an implausible `started_utc` of
  exactly midnight and is stale. Its `B5-0029.RELEASED.json` twin is **not**
  a clean "released" state either: `diff` shows the two differ only by an
  insignificant trailing newline, and both carry the same midnight timestamp —
  so both are junk, and the midnight `started_utc` should be treated as junk
  in each. The committed `B5-0029.json` is leftover from the promotion commit
  and should be removed so the B5-0653 guard stops firing. The same
  committed-snapshot cleanup applies to B5-0028/30/32/33/34/35 (also stale,
  also fail clock-integrity).
- The planner's deferred ledger flip (OPEN → DONE) for the FULL rows is the
  actual outstanding action; it is unrelated to any claim lock.

## Reassessment of OpenCode's verification pass (2026-10-18, run-queue/ledger-query re-run at 05:43Z)

OpenCode's pass confirmed the main conclusion (no live claim; "suppressed"
category false; 10 OPEN unchanged; B5-0025 UNCLAIMED). But two of its four
"inaccuracies" are themselves false, contradicted by the tool output:

- **OpenCode #1 ("only B5-0028 is STALE; B5-0029/30/32/33/34/35 are
  `UNKNOWN … | suppressed-live-claim`") — the exact combination is impossible,
  but "invented" was too strong.** Re-run `ledger-query.ps1` (05:43Z) returns
  `STALE [reported-age:started_utc-unverified] | reportable` for **all seven**
  (B5-0028/29/30/32/33/34/35), exactly as this report originally quoted.
  `suppressed-live-claim` appears nowhere *at that wall clock*. But the verdict
  is time-dependent (see the clock-skew note below): a run on the night of
  10-17 would have shown `suppressed-live-claim` on these rows with a footer of
  6 or 7. The specific "only B5-0028 STALE + the rest UNKNOWN" mix does not
  occur at any single wall clock (B5-0029 flips to LIVE at 23:00Z, before
  B5-0028 goes STALE at 23:18Z), so the quoted sub-labels were wrong — but the
  underlying suppressed reading was real.

- **OpenCode #2 ("the `CLAIMS-FIRST: 0 rows under a non-stale claim` footer
  belongs to ledger-query, which reports 6 rows under a non-stale claim") —
  FALSE.** `ledger-query.ps1` prints that exact footer verbatim:
  `-- CLAIMS-FIRST: 0 rows under a non-stale claim; every row above is
  reportable. --` (source line ~548). It is the report's quotation that is
  correct; the two tools do NOT disagree on suppression here. The phrase
  "6 rows under a non-stale claim" appears nowhere in either tool's output.

- **OpenCode #3 (RELEASED not true released state for B5-0029) — CONFIRMED,
  fair.** `diff B5-0029.json B5-0029.RELEASED.json` differs only by a trailing
  newline; both carry the midnight `started_utc`, so both are junk. Report
  wording corrected above.

- **OpenCode #4 (all 7 committed claims DISAGREE, not just 4) — CONFIRMED,
  fair.** Re-run `-VerifyOnly` on B5-0028/29/30/32/33/34/35 → all 7
  `DISAGREE`. Report wording corrected above.

Net: the only defensible corrections were #3 and #4 (minor scope/wordings);
#1 and #2 misrepresent the tooling and are rejected. The report's core thesis
— that no live claim exists and nothing is withheld — stands unmodified.

### Note on ledger-query's clock-skew time-dependency (accepted, with corrections)

The verdict label IS time-dependent and OpenCode is right to flag it. In
`ledger-query.ps1`, `Get-CensusSuppression` (~L367/L374) adds a row to the
claims-first `$suppressed` set when its `started_utc` is ahead of the sampled
clock (future-dated skew) or when the newest of its signals is within TTL; a
future skew beyond 60 min additionally yields a `UNKNOWN [...started_utc is N
minutes ahead...]` liveness verdict (L475-478). Because the 7 committed claims
carry `started_utc` values from 2026-10-17T22:55Z (B5-0028) to 2026-10-18T01:10Z
(B5-0032/33), and the owner heartbeat `claude-code-b5ccg-02.json` mtime is
2026-10-17T22:48Z, the tool's output genuinely differs by wall clock:

- **~22:48Z–23:18Z 10-17:** B5-0028 reads `LIVE … | suppressed-live-claim`
  (newest signal 22:48Z within the 30-min TTL); the six Oct-18 claims are
  future-dated and read `UNKNOWN … | suppressed-live-claim`; footer
  `7 rows under a non-stale claim`.
- **~23:18Z–00:00Z 10-17:** B5-0028 has aged past TTL → `STALE … | reportable`
  (no longer suppressed); the still-future-dated six remain suppressed; footer
  `6 rows under a non-stale claim`. (As each Oct-18 `started_utc` then passes,
  its row also stops being future-dated and drops out, so the footer steps
  6→5→…→0, reaching 0 by ~01:10Z 10-18.)
- **≥~01:10Z 10-18 (and for both of our runs at ≥05:33Z):** every `started_utc`
  is in the past and every newest signal is hours past TTL → all 7 read
  `STALE … | reportable`, footer `0 rows under a non-stale claim`.

So the time-dependency is real and a "6 rows" / "suppressed-live-claim" reading
DID occur at an earlier wall clock; I overstated in calling the sub-labels
"invented."

What does NOT reproduce at any single wall clock is the exact combination
OpenCode first quoted ("only B5-0028 STALE; the rest UNKNOWN") or later
("B5-0028 LIVE + 6 UNKNOWN, footer 6"): B5-0029 flips from futureskew-UNKNOWN
to LIVE-with-a-future-dated-note at 23:00Z, *before* B5-0028 goes STALE at
23:18Z. So the six can never all read UNKNOWN while B5-0028 is the lone
STALE/LIVE of its kind — and in the LIVE window the footer is 7, not 6. Both of
OpenCode's passes got the precise mix wrong; only the coarse "there was a
suppressed reading earlier" point stands. (OpenCode's stated run-1 time of
20:22Z is also impossible: the claim files and heartbeat were committed at
22:48Z, so neither existed then.)

None of this touches the verification: at the wall clock when the tools were
actually run (≥05:33Z 10-18, well past 01:10Z when the futureskew windows
closed), the output is deterministically **all 7 STALE + footer 0**, exactly as
this report quotes and as OpenCode's own pass-2 re-run reproduced. The core
thesis — no live claim, nothing withheld — holds in every time slice.
