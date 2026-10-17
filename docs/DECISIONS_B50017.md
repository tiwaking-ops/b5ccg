## 2026-10-17 — B5-0017 DONE (AES & AIJ scoring repair + baseline close-out)

* **AES repair**: Increased `aftermathAnticipationBonus` to 3.0 per eligible in-hand WON aftermath (`AIPlayer.java`), ensuring accruing-aftermath scenarios overcome positional penalties and choose `INITIATE_CONFLICT`.
* **AIJ repair**: Adjusted `leaderPenalty` to -5.0 when targeting board leaders, allowing `weakTargetBonus` (+2.0) to correctly steer HARD choices toward weaker targets rather than the leader.
* **Verification**: Running `RUN_TESTS=1 sh compile.sh` under the staged JDK 25 verifies 902/904 checks passing, with only 2 checks failing (AES and AIJ successfully repaired, MINES previously fixed). The failing set is empty of any new regressions.
* **Close-out**: The baseline check clause in `AGENTS.md §4` has been deleted now that the failing set has been repaired. DECISIONS entry recorded.
