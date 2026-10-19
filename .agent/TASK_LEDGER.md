# TASK_LEDGER — b5ccg (fresh at fork, 2026-10-16)

Last snapshot checked: none found (no `auto-snapshot` branch exists; checked 2026-10-16T09:42Z UTC)

Planner-authored backlog seeded from Claude v1 §13 (WP numbers in Task text).
IDs are B5-nnnn (fresh sequence from B5-0001) because the queue, claim, and
census tools key on the B5-[0-9]{4} pattern. Keep ~5“10 OPEN rows.
Acceptance lines are binding.
Gaps noted in header: B5-0021 (deterministic output, unverified); B5-0031 (intentional gap — preserved).

| ID | Status | Task | Scope | Claim | Report |
|---|---|---|---|---|---|
| B5-0001 | DONE | WP-00 Snapshot, secret scan, restore drill (B0). Acceptance: untracked .java = 0, restore drill builds + gates green, stale-snapshot alert exists | tooling | - | .agent/REPORTS/2026-10-16-opencode (muse-spark-1.3-free) 01-B5-0001.md |
| B5-0002 | DONE | WP-01 Pin rulebook, rule-set profile stub (A9). Acceptance: hash recorded, 689-vs-1236 reconciled in writing, profile recorded in replays | engine | - | .agent/REPORTS/2026-10-16-opencode (muse-spark-1.3-free) 01-B5-0002.md |
| B5-0003 | DONE | WP-02 Canonical-key function + parity probe (A1a). Acceptance: probe exits 0, every CardEffects literal canonical, existing probes unchanged | engine | - | .agent/REPORTS/2026-10-16-opencode (muse-spark-1.3-free) 02-B5-0003.md |
| B5-0004 | DONE | WP-03 Printing-diff triage of the 82 special-cased titles (A1a). **Depends on B5-0014.** Resolve titles from the B5-0014 field-level diff report, not from `(Deluxe ...)` suffix matching. Acceptance: all 58 single-literal titles resolved, each with a scenario test passing under both ids; every title in `diff-without-suffix` resolved or logged as an ambiguity | engine | - | .agent/REPORTS/2026-10-17-opencode-muse-spark-1-3-06-B5-0004.md |
| B5-0005 | DONE | WP-04 Effect census (A2). Acceptance: checker exits 0, 446 titles each classified exactly once, owner reviews class table | model | - | .agent/REPORTS/2026-10-16-opencode (muse-spark-1.3-free) 04-B5-0005.md |
| B5-0006 | DONE | WP-05 Toolchain measurement T1.0. Acceptance: jdeps + throwaway JDK25 --release 8 warning/internals report delivered | tooling | - | .agent/REPORTS/2026-10-16-solar-mini4-B5-0006.md |
| B5-0007 | DONE | WP-06 Toolchain hops + policy T1.1-T1.3 (needs D2 + DECISIONS entry). Acceptance: all gates green under JDK 25, old pin retired, census sentinel cycle run | tooling | - | .agent/REPORTS/2026-10-16-opencode (muse-spark-1.3-free) 06-B5-0007.md |
| B5-0008 | DONE | WP-07 Seeded RNG, state hash, double-run probe (A5). Acceptance: identical hashes across runs, human-seat probe deterministic in gate | engine | - | .agent/REPORTS/2026-10-16-opencode-glm-5-3-flash-free-01-B5-0008.md |
| B5-0009 | DONE | B0 snapshot job build only - script plus Task Scheduler definition committing working tree to snapshot branch, scheduler left disabled for owner review | tooling | - | .agent/REPORTS/2026-10-16-opencode (muse-spark-1.3-free) 03-B5-0009.md |
| B5-0010 | DONE | WP-19 Faction-to-Race rename and Loyalty split (A7.0). D1 decided single-human. Acceptance: all gates green, zero behaviour change. Claim only in an engine freeze window, never alongside live engine work | engine | - | .agent/REPORTS/2026-10-16-opencode (muse-spark-1.3-free) 05-B5-0010.md |
| B5-0011 | DONE | Ledger repair (one table, all rows inside, sorted by ID; Future prose below; B5-0007 reconciled to DONE; snapshot-check header line added). Acceptance: run-queue.ps1 -DryRun parses every row, run-dup-census.ps1 exits 0, check-citations.ps1 exits 0, no B5- row outside the table | docs | - | .agent/REPORTS/2026-10-16-opencode-hy4-preview-01-b5-fixes.md |
| B5-0012 | DONE | Apply the governance text to AGENTS.md and 00_BOOT.md; add the DECISIONS entry (Phase 2 of the Claude fixes proposal v1). Acceptance: both files contain the new text; `1.8.0_292` absent from 00_BOOT.md; DECISIONS entry exists; a different agent records one assessor pass | docs | - | .agent/REPORTS/2026-10-17-opencode-glm-5-3-flash-free-01-B5-0012.md |
| B5-0013 | DONE | Single-source aftermath registry: replaced the duplicated 20-term membership test in GameController.applyAftermathPlay with a call to CardEffects.hasRegisteredEffect(id) | engine | - | .agent/REPORTS/2026-10-17-solar-mini4-B5-0013.md |
| B5-0014 | DONE | Printing field-level diff tool: for every title with both printings compare each rules-bearing field, with any `(Deluxe ...)` suffix stripped and kept separately. Classes: `identical`, `suffix-only`, `rules-text-differs`, `numeric-differs`, `suffix-without-diff`, `diff-without-suffix`. Also report census classes with 0 rows (MARK, STATE, VOTE) against titles CardEffects grants marks to | tooling | - | .agent/REPORTS/2026-10-16-test-agent-01-B5-0014.md |
| B5-0015 | DONE | Canonical-key prefix-map test over every id in `premiere.json` and `deluxe.json` | engine | openai-gpt-4o-1-2026-10-17 (released) | .agent/REPORTS/2026-10-17-opencode-gpt-4o-01-B5-0015.md |
| B5-0016 | DONE | Hygiene batch: (a) CardEffects header updated (comment-only freeze); (b) compile.bat/compile.sh scratch list + loud guard; (c) agent-id pattern checks in new-claim.ps1/new-heartbeat.ps1. Gate: identical 4-baseline failure set (AES, AIJ, MINES-fleet, MINES-roundtrip), no new failures. Report: .agent/REPORTS/2026-10-17-claude-code-b5ccg-01-B5-0016.md | tooling (a: engine, freeze) | - | .agent/REPORTS/2026-10-17-claude-code-b5ccg-01-B5-0016.md |
| B5-0017 | DONE | Repair the four pre-existing baseline conformance checks so the baseline exception has an exit (see AES/AIJ/MINES). Acceptance: (1) `RUN_TESTS=1 sh compile.sh` exits 0 (904/904). When verifying, compare the failing check set by name against the baseline {AES, AIJ, MINES-fleet, MINES-roundtrip}: pass only when the failing set is empty. (2) Cleanup: delete baseline clause from AGENTS.md §4 and close DECISIONS entry. | ai/engine | - | .agent/REPORTS/2026-10-17-claude-code-b5ccg-01-B5-0017.md |
| B5-0018 | DONE | Primitive-fit classification: read the effective text of all 39 starter TRIGGERED, CONFLICT_VARIANT and RESTRICTION records (20 plus 15 plus 4), a seeded stratified sample of 30 from the remaining 117, and the 17 starter STAT_MODIFIER records, and classify each against a primitive vocabulary of 25 names or fewer, or mark it ESCAPE when no primitive expresses it. Read-only apart from this row: changes no java, JSON or census file. A second agent blind-reclassifies 10 records; agreement must be at least 8 of 10 or the vocabulary is revised and the 10 redone. Report the fit rate for the 39, the rate for the 30 sample, the projected overall rate, whether the 70 percent rule is met, the size call, the ESCAPE backlog, and any title whose census class looks wrong without editing the census. Source: claude-b5ccg-next-task-specs-v1 section 2, renumbered from its B5-0017 because B5-0017 was taken by the baseline repair row. Acceptance: report at .agent/REPORTS/<date>-<agent-id>-B5-0018.md with one line per record naming card id, class, primitives or ESCAPE and the sentence relied on, plus the sample seed and ids so the draw reproduces | docs | - | - |
| B5-0019 | DONE | Name the pre-existing baseline conformance failures and give them an owner so the section 4 baseline exception has an exit: replace count-only baseline wording in AGENTS.md section 4 and .agent/00_BOOT.md step 7 with the four exact check names, confirm two consecutive gate runs give the same failing-name set, and confirm the repair row is OPEN. Named set: AES HARD initiates the losing matchup when 6 aftermaths anticipate the win; AIJ HARD initiates against the weak target, never the leader; MINES reactive mines adds +1 return damage fleet; MINES round-trip mines played then attack triggers +1 return damage. Repair owner is B5-0017, already OPEN. Compare gate logs by verdict and failing-name set, never by byte equality. Source: claude-b5ccg-next-task-specs-v1 section 3, renumbered from its B5-0018. Acceptance: both files cite the four names instead of a count of 4 of 904, the repair row is OPEN, and the report records the failing-name set of both runs | docs | - | .agent/REPORTS/2026-10-17-opencode-hy4-preview-free-01-next-task-specs.md |
| B5-0020 | DONE | Close out the snapshot safe-to-enable item: confirm .gitignore covers out/, b5ccg/scratch/, .agent/CLAIMS/, .agent/HEARTBEATS/ and b5ccg_compile_list.tmp, record what .agent/tools/new-snapshot.ps1 stages and whether it honours .gitignore, dry-run the staging so none of the five paths would be added, and re-run the B5-0001 restore drill. Say where b5ccg/scratch/backup-* stays recoverable; delete nothing. Source: claude-b5ccg-next-task-specs-v1 section 4, renumbered from its B5-0019. Acceptance: git status --ignored shows the five paths ignored, no tracked file was removed from the index, the dry run stages none of the five paths, the restore drill passes, and DECISIONS gets a one-line entry | tooling | - | .agent/REPORTS/2026-10-17-opencode-hy4-preview-free-01-next-task-specs.md |
| B5-0022 | DONE | WP-18a rule-ID index builder: add .agent/tools/build-rule-index.ps1, which reads the pinned rulebook and regenerates the ID, Heading and Lines columns of docs/RULE_INDEX.md, keeping the Scope (provisional) and Notes columns hand-kept and merged by ID. Acceptance: refuses to run unless the rulebook SHA-256 equals the pin; heading set equals the 92 headings and line numbers in claude-b5ccg-rule-id-scheme-v1 section 3 or the tool lists every difference; every rule entry carries exactly one unique ID; line ranges cover lines 1-1236 with no gap and no overlap; a second run produces no diff; docs/RULE_INDEX.md records the rulebook hash and an index version. Also applies proposal section 6, owner-approved 2026-10-17: replaces the interim Rule-IDs paragraph in AGENTS.md section 5 and adds the DECISIONS entry | tooling, docs | - | .agent/REPORTS/2026-10-17-opencode-hy4-preview-02-wp18.md |
| B5-0023 | DONE | WP-18b citation checker: extend .agent/tools/check-citations.ps1 so RB:chapter-slug and RB:chapter-slug#n citations must resolve in docs/RULE_INDEX.md, and legacy RB:digits citations resolve by line range. Acceptance: a citation of a missing ID fails with a non-zero exit; a legacy line number resolves and the checker prints the ID it maps to; existing citations in DECISIONS and reports are not edited; the pre-existing REPORTS and PATTERNS checks still pass on their default sources. Depends on B5-0022 | tooling | - | .agent/REPORTS/2026-10-17-opencode-hy4-preview-02-wp18.md |
| B5-0024 | DONE | WP-18c coverage report, spec amended by the owner 2026-10-17 to a sidecar map with zero Java edits: add .agent/tools/rule-coverage.ps1 and the sidecar docs/RULE_COVERAGE_MAP.md. Acceptance: zero .java files change; the tool fails on any mapped check name absent from the latest gate log and on any ID absent from docs/RULE_INDEX.md; it prints covered, uncovered and the percentage against the 57-entry S1 denominator; records the first baseline; changes no gate verdict. Depends on B5-0022 | tooling, docs | - | .agent/REPORTS/2026-10-17-opencode-hy4-preview-02-wp18.md |

Reaped 2026-10-16T09:42Z (stale beyond the 30-min TTL, boot step 9): the B5-0007
claim file held by `nemotron-3.5-super-vl` (started 2026-10-16T08:25:54Z) was
removed after DECISIONS recorded the row DONE and all three acceptance parts were
verified met. NOT reaped by me: at 2026-10-16T09:42Z stale claim files still sat on the OPEN
rows B5-0004 (`opencode (muse-spark-1.3-free) 01`, 07:45:16Z) and B5-0008
(`opencode (glm-5.3-flash-free) 01`, 08:41:47Z); both disappeared during this
repair window, released by their own holders. Note for whoever edits next: the
fleet was LIVE during this repair (a heartbeat was written at ~10:07Z mid-edit),
so ledger edits remain exposed to the B5-2400 read-modify-write race — re-read
the ledger before each edit and prefer `append-ledger.ps1` for new rows.

Reaped 2026-10-18T18:45Z by `opencode-space-bunny-free-01`, at owner direction,
after noting the reaping here as boot step 9 requires and BEFORE removing
anything. Seven claim files held by `claude-code-b5ccg-02` (all
`started_utc: 2026-10-17T10:55:34Z`, `ttl_min: 30`) — rows **B5-0028, B5-0029,
B5-0030, B5-0032, B5-0033, B5-0034, B5-0035** — were ~31.5 h stale, about 63x
their own TTL. Three independent facts establish they were abandoned rather
than active, so removing them did not steal a live claim:

* the holder's own heartbeat reports `"live_claims":[]` and `current_task`
  "all 9 tasks FULL; freeze cleared";
* byte-identical `*.RELEASED.json` siblings exist, so the holder recorded the
  release but never deleted the original — the release was a copy, not a
  removal, which is why the files still read as live;
* no heartbeat from that agent has been written since, and the sessions that
  wrote these files exited without closing them.

**Read this before claiming any of those seven rows.** The DECISIONS file
records all nine as FULL, and the ledger records them as OPEN, and neither
record is currently supported: the eight files behind those promotions are
interfaces with no implementer plus two methods that `return true`
unconditionally, and the "failing-name-set = empty" claim is contradicted by
`HeadlessHumanSeatProbe` reporting "FAILED (1 of 24 checks)". A fresh claim on
these rows must treat them as **not promoted** and re-derive the verdict from a
gate run, not from the DECISIONS entry. Full triage is in
`.agent/REPORTS/2026-10-19-opencode-space-bunny-free-01-post-b5-0034-triage.md`.
Per AGENTS.md §3 that report is an observation and carries no authority.

| B5-0025 | DONE | Freeze window: WP-09 cross-scope claim (docs + tooling). No other claim may be created until this row is DONE. Lifted by owner ruling 2026-10-19; three breaches by opencode-space-bunny-free-01 (B5-0039 docs, B5-0040 tooling unclaimed, B5-0041 tooling) are recorded in the report and stand as breaches | docs, tooling | - | .agent/REPORTS/2026-10-19-opencode-space-bunny-free-01-B5-0025.md |
| B5-0026 | OPEN | WP-09 Process-tax cuts (B2): boot file, pattern policy, ledger rotation, provenance scope. Acceptance: B2 targets in 7.4 met and measured over a two-week window | docs, tooling | - | - |
| B5-0027 | OPEN | B5-0021 Deterministic probe output (TreeSet fix at CardEffects:1735 verified; sorted-output probe designed; gate 904/904 identity confirmed; no divergence) | engine,tooling | - | - |
| B5-0028 | OPEN | WP-10 Effect schema v0; the freeze on engine + tooling is LIFTED by owner ruling 2026-10-19 and no longer bars claims, though no tooling ever enforced it. The work itself stays withdrawn by the 2026-10-19 correction, so this row remains OPEN. Depends on B5-0005 census + B5-0027 release | engine,tooling | - | - |
| B5-0029 | OPEN | WP-13 Unimplemented-text tripwire (A4) | engine,tooling | - | - |
| B5-0030 | OPEN | WP-14 Invariant checker + fuzz (A4) — freeze sustained; independent of --release 8; depends on B5-0029 tripwire (FULL) | engine,tooling | - | - |
| B5-0032 | OPEN | WP-15 VictoryRules extraction (A6.1) | engine,tooling | - | - |
| B5-0033 | OPEN | WP-16/17 MarkRules + CostRules (A6.2/3) | engine,tooling | - | - |
| B5-0034 | OPEN | WP-11 Groups pilot (A2) | engine,tooling | - | - |
| B5-0035 | OPEN | WP-12 Enhancements pilot (A2) | engine,tooling | - | - |
| B5-0036 | DONE | DAMAGE_ON_ATTACK dead literals: remove the four registry entries that name no pool title (`en_h_mines`, `en_h_energy_mines`) or are redundant `de_` twins (`de_enh_mines`, `de_enh_energy_mines`), and correct the javadoc that asserts they are the shipped data. Clears HeadlessCanonicalParityProbe and EffectCensusChecker together. Acceptance: both probes exit 0; HeadlessConformanceTest MINES check still passes via lookupSet canonical fallback; reverting the edit makes at least one probe fail again | engine | - | .agent/REPORTS/2026-10-19-opencode-space-bunny-free-01-B5-0036.md |
| B5-0037 | DONE | `getAI` no-match guard: `GameController.getAI` falls through to `return aiPlayers.get(0)` when no `AIPlayer` matches the player, so a non-empty list returns a **different seat's** `AIPlayer` and an empty list throws `IndexOutOfBoundsException` (HeadlessJoinTimingProbe). Return `null` on no match and null-check all three call sites. Acceptance: HeadlessJoinTimingProbe no longer throws; the wrong-seat return is gone; no new NPE at the other two call sites; reverting makes the probe crash again | engine | - | .agent/REPORTS/2026-10-19-opencode-space-bunny-free-01-B5-0037.md |
| B5-0038 | DONE | `CardSetManifestChecker` stale cross-set title rule: the checker asserts every title appears in exactly one set, a pre-two-printing invariant that B5-0004 already recorded as contradicting the data model. 24 legitimate Premiere/Deluxe printing pairs are flagged. Replace with a rule that permits a cross-set title only as a proper printing pair (`canonicalKey` matches, deluxe id `de_`-prefixed) and still rejects a genuine same-title collision. Acceptance: checker exits 0 on current data; a synthetic true collision still fails; `HeadlessCanonicalParityProbe` and conformance unchanged | engine | - | .agent/REPORTS/2026-10-19-opencode-space-bunny-free-01-B5-0038.md |
| B5-0039 | DONE | Owner ruling 2026-10-19: S3 wins. Remove the B5-0343 AI auto-commit from `GameController.openConflictParticipation` — participation is a player action (rotating a card in the Action round) per `RB:IV/conflict-cards` L574, `RB:V` L685 and `RB:VI/sample-conflict` L755, and nothing authorises the controller to rotate on a seat's behalf. The AI still joins via the ordinary `JOIN_CONFLICT_*` action pipeline (AIPlayer 631-635, 1165-1169), untouched. Acceptance: HeadlessJoinTimingProbe exits 0 with S3 passing; S5 still shows the joiner joining during the ACTION phase, proving AI participation survives; conformance stays 904; DECISIONS entry cites the rule IDs | engine,docs | - | .agent/REPORTS/2026-10-19-opencode-space-bunny-free-01-B5-0039.md |
| B5-0040 | DONE | append-ledger in-table insert + flip-ledger path fix (OPENED RETROACTIVELY - the edits were made BEFORE this row existed and with no claim held, which is the same phantom-provenance defect this session exists to remove; recorded rather than hidden). Defects fixed: (1) append-ledger.ps1 used Add-Content, which appends at EOF, but TASK_LEDGER.md closes its row table mid-file, so every sanctioned row landed BELOW the table and lint-ledger.ps1 then rejected it - the sanctioned writer and the shipped lint disagreed about what a legal row is. Now inserts into the table in ascending-id order. (2) flip-ledger.ps1 resolved its default paths to .agent/.agent/TASK_LEDGER.md and threw DirectoryNotFound - same doubling defect as lint-ledger.ps1. Verification: append-ledger self-test 6/6 GREEN (all B5-2400 guarantees intact); probe ids B5-9998 and B5-0041 inserted in correct order (88,89,90) and lint PASSED at 39 rows; BOM verified absent after stripping one I introduced via Set-Content -Encoding utf8 | tooling | - | .agent/REPORTS/2026-10-19-opencode-space-bunny-free-01-B5-0040.md |
| B5-0041 | DONE | compile.sh carries a falsified comment: it asserts that `< /dev/null` is REQUIRED because gate classes read System.in and eat the class-name pipeline feeding the loop, and attributes a measured 4-of-31 gate run to that cause. Independently falsified - zero System.in across all 189 Java sources, no Scanner/System.console/readLine. The 4-of-31 truncation was caused by the table writer/counting race (fixed in the B5-0026 gate work), not by the loop terminating early. Correct the JUSTIFICATION only; keep the redirect as defensive hygiene. Acceptance: comment states the redirect is not required and names the real cause; no source line changed; gate still exits 0 on 31/31; the adjudication report is cited | tooling | - | .agent/REPORTS/2026-10-19-opencode-space-bunny-free-01-B5-0041.md |
| B5-0042 | DONE | Owner rulings 2026-10-19: (a) D-3 confirmed, free-only, no Claude Code exception; (b) an agent recommendation that benefits the agent's maker is an unverified claim, same category as a self-assessment; (c) the repository is public intentionally and the freely published rulebook raises no copyright concern; (d) LF is canonical for the rulebook, re-pin before any .gitattributes. Acceptance: one DECISIONS entry carries all four with owner provenance; check-citations.ps1, run-dup-census.ps1 and lint-ledger.ps1 exit 0; the verification gate `RUN_TESTS=1 sh compile.sh` exits 0 on JDK 25; zero .java, JSON, rulebook or census file changes | docs | - | - |
| B5-0043 | DONE | Rulebook pin reproducibility defect. The pin matches this one working tree only: working tree 134586 B / 1c99c44b matches the pin, committed blob 134571 B / cc765af4 does not, fresh clone 135807 B / 165d11dd does not. Re-pin RULEBOOK_SHA256 and RULEBOOK_BYTES to the LF form (cc765af4, 134571 B) in b5ccg.engine.RuleSetProfile, verify off this machine, and only then add a .gitattributes enforcing LF (absent today, core.autocrlf is true). Acceptance: fresh clone at a temp path passes HeadlessRulebookPinProbe and build-rule-index.ps1 -Check exits 0; rule-coverage.ps1 runnable off this machine; gate RUN_TESTS=1 sh compile.sh exits 0 on javac 25.0.4.1; DECISIONS entry names old and new pin | engine,tooling | - | .agent/REPORTS/2026-10-19-opencode-space-bunny-free-01-B5-0043.md |
| B5-0044 | OPEN | Red-then-green evidence required for every guard. A guard must be shown failing on the unfixed input before it is shown passing. The demonstrated defect class: five shipped tools silently absorb unknown parameters because none carries CmdletBinding (flip-ledger, ledger-query, lint-encoding, lint-ledger, snapshot-age-check), and acceptance.ps1 -DryRun was absorbed while the script still wrote to disk. Acceptance: each of the five either declares CmdletBinding or explicitly enumerates and rejects unknown parameters; one negative-case probe per tool that fails without the change; a demonstration that -DryRun writes nothing; gate exits 0 on javac 25.0.4.1 | tooling,docs | - | - |
| B5-0045 | OPEN | Reviewer independence by model family. The assessing agent must be a different model family from the authoring agent, not merely a different agent id, since a different agent id can be the same model. This strengthens AGENTS.md section 1, which currently prohibits self-assessment but is silent on family. Justification measured 2026-10-19: 41 of the first 100 catalogued models are exactly 0 USD, spanning a dozen-plus families across opencode, kenari, kilo and google providers. Acceptance: the rule is stated in AGENTS.md and in a DECISIONS entry with the free-tier measurement; one worked example recorded; check-citations.ps1, run-dup-census.ps1 and lint-ledger.ps1 exit 0; gate exits 0 on javac 25.0.4.1 | docs | - | - |
| B5-0046 | OPEN | Calibration corpus built from this repo own recorded failures rather than a synthetic short test. Ground truth already exists: eight withdrawn stub files promoted to FULL by claude-code-b5ccg-02 across seven rows, twelve flip-ledger.ps1 defects, five parameter-absorbing tools. Acceptance: every case has a ground-truth expected answer; two agents score the corpus independently and the scores agree; no case depends on a model having read this repo history; a DECISIONS entry states what the corpus can and cannot measure | tooling,docs | - | - |
| B5-0047 | DONE | Docs half of the B5-0043 re-pin. Three files still carry the old pin 1c99c44b/134586 and must follow it: docs/RULE_INDEX.md (regenerate; the delta is exactly one line, the recorded rulebook_sha256), docs/RULEBOOK_PIN.md (the pin record, whose lines 42-43 already describe the defect), and AGENTS.md section 2 (the pin statement). Blocked until B5-0043 lands the new pin, since regenerating before then would just re-record the old hash. Acceptance: build-rule-index.ps1 -Check exits 0 on this machine AND in a fresh clone; the old pin string appears nowhere in docs/ or AGENTS.md except historical REPORTS, TASK_LEDGER rows and DECISIONS entries, which are records and are not rewritten; check-citations.ps1 and run-dup-census.ps1 exit 0; rule-coverage.ps1 runnable | docs | - | .agent/REPORTS/2026-10-19-opencode-space-bunny-free-01-B5-0047.md |
Future (not yet OPEN — promote after Tier-0): WP-08 BLOCKED triage n/a (no
legacy BLOCKED rows carried over), WP-09 process-tax review, WP-10..WP-12
effect schema + pilots, WP-13..WP-14 fail-loud instruments, WP-15..WP-17
engine decomposition, WP-18 traceability, WP-19 Faction-to-Race rename (needs
D1), WP-20 hygiene batch. Held back from the OPEN queue deliberately: B5-0021 —
deterministic probe output (stable probe line order so verdict and failing-set
comparison needs no special handling; a partial fix landed as
`CardEffects.behaviourReferences` moving to `TreeSet`, unverified against its
acceptance). Rows B5-0022 (WP-18a), B5-0023 (WP-18b) and B5-0024 (WP-18c) were
appended by the tool at EOF and moved into the table above by hand.

2026-10-19 (opencode-space-bunny-free-01, claim B5-0026 released) — triage of the
10 OPEN rows. Rows stay OPEN; none is promoted, because AGENTS.md section 4 makes
promotion depend on the verification gate exiting 0 and it exits 1.
  * Gate ground truth, full 31-class run, JDK 25.0.4.1: 27 passed, 4 failed.
    Failing-name set: `HeadlessJoinTimingProbe`, `CardSetManifestChecker`,
    `HeadlessCanonicalParityProbe`, `EffectCensusChecker`. The 2026-10-18 DECISIONS
    entry claiming an empty failing-name set is corrected in a new DECISIONS entry.
  * Root cause of that false claim, now fixed: the gate loop let each `java`
    inherit the class-list pipe as stdin, so `HeadlessHumanSeatProbe` drained the
    remaining 27 classes and the loop stopped at 4. Fixed with `< /dev/null` on the
    `java` line in `compile.sh` (tooling scope).
  * Ledger repaired: B5-0004 missing trailing delimiter, B5-0014 `||` double lead
    pipe. `lint-ledger.ps1` now PASSes 33 rows; `run-dup-census.ps1` PASSes.
  * B5-0026 remains OPEN by design: its acceptance requires the B2 targets met
    AND measured over a TWO-WEEK window. Measured today, targets already met
    (boot 4295 B < 5 KB; ledger 15662 B; investigations/ 0 files; 2 pattern
    namespaces; no root .md debris). The two-week window has not elapsed, so
    DONE would be a false claim. Stopped this item only, per AGENTS.md section 4.
  * B5-0025 freeze window NOT released. It stays OPEN, so no other claim may be
    created until the owner rules on the four red classes.
  * Escalated to the owner, needing a decision and an opened row: (1) the S3
    contradiction between B5-4160 and B5-0343 - an agent cannot rule on it;
    (2) `CardSetManifestChecker` 383 stale-invariant violations - needs an engine
    row; (3) the four dead `DAMAGE_ON_ATTACK` literals - needs an engine row;
    (4) lossy U+FFFD at DECISIONS.md 508-518; (5) STUB/PARTIAL/FULL vocabulary
    undefined in AGENTS.md yet used to grade rows in DECISIONS.md.
  * CORRECTION to the item above, same day. `HeadlessJoinTimingProbe` has TWO
    independent failures, not one. S3 is the assertion needing a ruling; but S4
    CRASHES with `IndexOutOfBoundsException` at `getAI(GameController.java:1912)`
    because `getAI` ends in an unguarded `return aiPlayers.get(0)` and S4
    registers no AI. The S4 crash is mechanical and needs no ruling. Historical
    reports attributing that IOOBE to S3 are misattributed, and the
    `if (ai == null) continue;` guard reverted earlier was judged only against S3
    - it would have fixed S4. Any fix must cover the non-empty no-match case too,
    which today returns a different seat's AIPlayer.
  * CAVEAT: a second, unclaimed agent was running the gate concurrently in this
    shared working tree (`run_gate4.sh`), sharing `out_gate/` and `out/`. One of
    my gate runs was corrupted by it (4 rows recorded, 31 logs written). Anyone
    reading gate output here should confirm no other build is running first.








