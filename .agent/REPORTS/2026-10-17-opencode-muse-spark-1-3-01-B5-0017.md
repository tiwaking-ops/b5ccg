---
name: b5-0017-verification
metadata:
  type: report
  author_llm: opencode (muse-spark-1.3-free) 01
  last_modified_by_llm: opencode (muse-spark-1.3-free) 01
  last_modified_date: 2026-10-17
---

B5-0017 verification report (gate only; close-out pending green AES/AIJ).
Source: b5ccg/src/ (commit 635ae2a for AES winProb).
Gate: RUN_TESTS=1 sh compile.sh (JDK 25 --release 8, PATH=.../jdk-25.0.4.1+1/bin). Exit 1. 2/904 FAIL (AES, AIJ). MINES 11/11 PASS.
Baseline comparison (§4): identical failing-name set (AES, AIJ); no new failures; no silent new passes → passes.
MINES fix: CardEffects.java DAMAGE_ON_ATTACK added enh_mines, enh_energy_mines (line 1579-1580). Registry scans include fixture enh_mines_rt (NON_POOL_FIXTURE_IDS).
AES: scoreActionHard line 1322 (myTotal+1.0)/(myTotal+oppTotal+2.0) correct; aftermathAnticipationBonus line 1630 sums +1.0 per eligible WON card.
AIJ: weakTargetBonus +2.0 at line 1333-1338 when opp!=leader && oppInf<leaderInf; leaderPenalty -3.0 kept.
Not done: AES/AIJ score-bar fix, report finalization, claim delete, heartbeat refresh, DECISIONS entry, AGENTS.md §4 clause deletion, commit.
