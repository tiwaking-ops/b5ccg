---
document:
  title: "Report — governance, boot and ledger fixes (Claude fixes proposal v1)"
  status: "Observation only — no authority"
provenance:
  author_llm: {name: "opencode (tencent/hy4-preview)", version: "opencode-hy4-preview-01-2026-10-16"}
  created_date: "2026-10-16"
  last_modified_date: "2026-10-16"
  last_modified_by_llm: {name: "opencode (tencent/hy4-preview)", version: "opencode-hy4-preview-01-2026-10-16"}
---

# Governance, boot and ledger fixes — implementation report

Agent id `opencode-hy4-preview-01`, 2026-10-16. Source material:
`claude-b5ccg-governance-and-ledger-fixes-v1.md` (Claude, v1) as delivered by
`b5ccg-claude-desktop-fixes-handoff-v1.md`. This report is an observation and
carries no authority; promotion follows AGENTS.md §4.

Evidence tags: **[V]** verified against the working tree in this session,
**[I]** my inference, **[?]** still needs an owner or repo fact.

## 1. Scope of what I did

| Phase | Work | Outcome |
|---|---|---|
| 0 | Resolve every `[?]` in the proposal from the repo, not from its guesses | Done — 5 material contradictions found (§2) |
| 1 | Back up the four files about to be edited | Done — `b5ccg/scratch/backup-2026-10-16/` |
| 2 | Apply the governance text to `AGENTS.md` and `.agent/00_BOOT.md`; add the `docs/DECISIONS.md` entry | Done after owner approval (§3) |
| 3 | Ledger repair, task B5-0011 | Done (§4) |
| 4 | Verification gate before and after the edits | Done (§5) |
| 5 | Acceptance tooling: `run-queue.ps1 -DryRun`, `run-dup-census.ps1`, `check-citations.ps1` | §4.1 |
| 6 | Effect-work sizing from `effect-census.json` (the proposal's open item §7) | Done (§6) |

Not done, by owner instruction: no `.gitignore` edit, no move of the root `.md`
files — both are reported only (§7).

## 2. Phase 0 — fact resolution

| # | Proposal says | Repo says | Tag |
|---|---|---|---|
| F1 | Ledger has "22 rows claimed, 10 shown" (P2, §5 item 4) | 10 data rows exist; nothing else in the file or in git is a ledger row. The 22 figure is wrong | [V] |
| F2 | `GameController:903–923` holds the duplicated 20-term aftermath test (P7) | Lines 903–923 are the `DISCARD_AGENDA` block. The duplicate is a 20-predicate `CardEffects.isXxx(amId)` OR-chain at **`GameController.java:1565–1584`**, mirroring `CardEffects.hasRegisteredEffect` at `CardEffects.java:1230` | [V] |
| F3 | §3 text: "Builds use `JAVA_HOME` when set, otherwise the staged JDK path named in `compile.bat`" | Neither script reads `JAVA_HOME` and neither names a JDK path: `compile.bat:76` and `compile.sh:43` call `javac --release 8` straight off `PATH` | [V] |
| F4 | §3 text: "The staged JDK, `.agent/CLAIMS/`, `.agent/HEARTBEATS/`, `out/` and `b5ccg_compile_list.tmp` are gitignored" | None of those match anything in `.gitignore`; `b5ccg/scratch/` is also untracked and unignored (AGENTS.md §2 already says so) | [V] |
| F5 | §5 item 3 [?]: B5-0007 needs a sentinel-cycle check before flipping to DONE | `docs/DECISIONS.md:213` records the census sentinel cycle run via `.agent/tools/census-b50960.py` CLEAN (exit 0), and `:220` records the Java 6 census retired. All three acceptance parts are met; no split row needed | [V] |
| F6 | O4 asks the owner to send `compile.sh`, the census flags, the rulebook headings and the D3 text | All four are readable from the repo; the census flags are tabulated in §6 | [V] |
| F7 | P8b: `compile.bat` writes `..\b5ccg_compile_list.tmp` outside `b5ccg/` and never deletes it | Confirmed: `compile.bat:49`. `compile.bat` also has no *executable* test branch — its four `RUN_TESTS` mentions (lines 148–155) are comments pointing at `compile.sh`, so a plain `compile.bat` cannot run the verification gate | [V] |

## 3. Phase 2 — governance edits, and the approval behind them

Owner answers to the three questions I raised (option labels as chosen):

* §4 promotion rule → **"Baseline-relative (Recommended)"**.
* §2 toolchain text → **"Correct the text only (Recommended)"**.
* `.gitignore` → **"Report only (Recommended)"**.

Applied to `AGENTS.md`:

* §2 **Toolchain location** — rewritten to match F3: "`compile.bat` and `compile.sh`
  invoke `javac` from `PATH` and select no JDK themselves — they do **not** read
  `JAVA_HOME`". The proposal's gitignore sentence was dropped (F4); the staged-JDK
  single-source rule was kept.
* §2 **Agent ids** — added as proposed (spaces and parentheses barred).
* §4 **Promotion** — baseline-relative form: the gate exits 0 *or* fails only on
  the recorded baseline set (currently 4 of 904: AES/AIJ AI ×2, MINES ×2), with
  the comparison recorded in the report; `compile.bat` alone is never sufficient.
* §5 **Scopes / Backups / Rule-IDs (interim)** — added as proposed, with one
  closing-sentence fix so the text agrees with D3 (D3 allows private task
  branches; the snapshot job is separate from them).
* `last_modified_by_llm` updated; original `author_llm` untouched.

Applied to `.agent/00_BOOT.md`: steps 2, 3, 5, 7 rewritten so the boot sequence
names the verification gate (`RUN_TESTS=1 sh compile.sh`) and no longer states a
JDK 8 expectation. Both `-Javac 1.8.0_292` literals became `-Javac
<recorded-javac>` with an instruction to pass the version actually recorded in
step 2; a grep for `1.8.0_292` in that file now returns 0 hits.

Added to `docs/DECISIONS.md`: `## 2026-10-16 — Governance fixes from Claude
fixes proposal v1`, recording the §4 baseline rule, the §2 toolchain wording, the
scope list, the interim `RB:<line>` rule-ID scheme, and the three owner-approved
deviations below.

## 4. Phase 3 — ledger repair (B5-0011)

One table, header once, all rows inside it and sorted by ID; the stray `||`
prefix on B5-0006 removed; B5-0009 and B5-0010 moved into the table; the Future
prose moved below the table; header line `Last snapshot checked: none found (no
`auto-snapshot` branch exists; ...)` added; B5-0007 flipped to DONE with its
stale claim cleared; B5-0004 rewritten to depend on B5-0014; new rows B5-0011 to
B5-0016 added from the proposal's §6. A reaping note records that the claim files
on B5-0004 and B5-0008 were released by their own holders during the repair, and
warns the next editor about the B5-2400 read-modify-write race.

Row-loss check: diffed the repaired file against the backup — **removed: none**.

### 4.1 Acceptance

| Tool | Result |
|---|---|
| `.agent/run-queue.ps1 -DryRun` | exit 0; all 8 OPEN rows parsed (B5-0004, B5-0008, B5-0011 to B5-0016) |
| `.agent/tools/run-dup-census.ps1` | exit 0 |
| `.agent/tools/check-citations.ps1` | exit 0 once this report exists (before it: exactly 2 dangling citations, `.agent/TASK_LEDGER.md:22` and `docs/DECISIONS.md:297`) |

All three green, so **B5-0011 is DONE** (row flipped after these runs). No claim
was taken for the `docs` scope — no live claim held it at the time, and the
ledger edit was made under the owner's go-ahead with a pre-edit backup.

**B5-0012 is deliberately left OPEN.** Its work is done (§3 above), but its
acceptance requires an assessor pass recorded by a *different* agent; marking it
DONE myself would be a self-assessment, which AGENTS.md §1 forbids.

## 5. Verification — the edits are build-neutral

Command actually used (no `sh` on `PATH`, no `JAVA_HOME`, default `javac` is the
JDK 8 that rejects `--release`):

```
$env:PATH="C:\temp\projects\b5ccg\b5ccg\scratch\jdk25-win\jdk-25.0.4.1+1\bin;$env:PATH"
$env:RUN_TESTS="1"; cd C:\temp\projects\b5ccg\b5ccg
& "C:\Program Files\Git\bin\sh.exe" compile.sh
```

| Run | When | Verdict | Exit |
|---|---|---|---|
| Baseline | 2026-10-16T09:44Z | `CONFORMANCE SUITE FAILED (4 of 904 checks)` | 1 |
| Post-edit | 2026-10-16T10:09Z | `CONFORMANCE SUITE FAILED (4 of 904 checks)` | 1 |

Failing set identical in both runs — `[AES] HARD initiates the losing matchup
when 6 aftermaths anticipate the win`, `[AIJ] HARD initiates against the weak
target, never the leader`, `[MINES] reactive mines adds +1 return damage
(fleet)`, `[MINES] round-trip: mines played then attack triggers +1 return
damage` — which is exactly the baseline recorded in `docs/DECISIONS.md`. Nothing
new failed and nothing newly passed.

**Lesson worth recording:** I first compared the two logs by size and hash. Both
are 569,399 bytes, but the SHA-256 values differ and the first byte difference is
at offset 10118: the probe section emits the same 10,782 lines in a different
order. Gate logs must be compared by verdict and failing-check set, never by
byte equality.

My changes touch only `.md` files (no `.java`, no resources), so build-neutrality
is structural as well as observed. Caveat: the fleet was live across the two
runs, so other agents' edits also sit between them; the identical failing set is
reported as observed, not as proof about their changes.

## 6. Phase 6 — effect-work sizing (the proposal's open item)

From `b5ccg/resources/census/effect-census.json` (446 records; 383 `DUAL`, 63
`PREMIERE_ONLY`), columns are records / `starter` / `covered`:

| Class | Records | Starter | Covered |
|---|---|---|---|
| VANILLA | 43 | 36 | 0 |
| TRAIT_ONLY | 19 | 8 | 2 |
| STAT_MODIFIER | 63 | 17 | 6 |
| ONE_SHOT | 93 | 28 | 24 |
| CONFLICT_VARIANT | 59 | 15 | 8 |
| TRIGGERED | 67 | 20 | 4 |
| RESTRICTION | 30 | 4 | 1 |
| COST_MODIFIER | 7 | 1 | 0 |
| MARK | 0 | 0 | 0 |
| STATE | 0 | 0 | 0 |
| VOTE | 0 | 0 | 0 |
| AGENDA_WIN | 26 | 9 | 5 |
| DECK_HAND | 33 | 8 | 8 |
| BESPOKE | 6 | 0 | 0 |
| **TOTAL** | **446** | **146** | **58** |

Reconciling the proposal's §7 figures:

* Its "384 behaviour-bearing records" = 446 − 43 VANILLA − 19 TRAIT_ONLY = **384
  exactly** [V]. Non-VANILLA alone is 403, so the two figures differ by the
  TRAIT_ONLY class; both readings are defensible and the class boundary should be
  stated whenever the number is quoted.
* Its "222 in primitive-friendly classes" does not reproduce. The natural
  primitive-friendly grouping (ONE_SHOT + STAT_MODIFIER + TRIGGERED) is **223**
  (194 dual-printing, 29 single). The one-record gap is unresolved [?] — I could
  not reconstruct which single record the original excluded.
* The decision rule in §7 — about 70% of behaviour-bearing cards fitting about 25
  primitives — can now be tested: `covered` is 58 of 403 behaviour-bearing
  records (14%), and 110 of the 146 starter records are behaviour-bearing. On
  those numbers the "medium" call is not yet supported; the per-primitive fit is
  the missing measurement, and B5-0014's mark finding (MARK/STATE/VOTE are all
  0-row classes while `CardEffects` holds mark tables, F-flagged as P11) is still
  open.

## 7. Deviations from the brief and from the proposal

1. **Agent id.** The brief asks for `claude-desktop-01`. This session is not
   Claude Desktop, and AGENTS.md §1 forbids fabricated provenance, so I used
   `opencode-hy4-preview-01` / `author_llm: {name: "opencode
   (tencent/hy4-preview)"}`. Every artefact uses that id consistently.
2. **§2 toolchain wording** — corrected against F3 rather than pasted (owner
   approved).
3. **gitignore sentence dropped** from the §2 text because it was false (F4);
   reported instead of "fixed" (owner approved).
4. **B5-0007** flipped to DONE rather than split, on the strength of
   `docs/DECISIONS.md:213` (F5).
5. **P7 line numbers** corrected to 1565–1584 in the B5-0013 row (F2).

## 8. Open items for the owner

* **O1 (still open).** The snapshot scheduler was never registered; no
  `auto-snapshot` branch exists, so the ledger header legitimately reads
  `none found`. Enabling it, and re-running the B5-0001 restore drill, are owner
  actions.
* **`.gitignore`** has no entry for `out/`, `b5ccg/scratch/`,
  `.agent/CLAIMS/`, `.agent/HEARTBEATS/` or `b5ccg_compile_list.tmp`. Reported
  only, per the owner's choice.
* **Root `.md` files** — `claude-b5ccg-governance-and-ledger-fixes-v1.md`,
  `b5ccg-claude-desktop-fixes-handoff-v1.md` and `b5-claude-files.md` sit at the
  repo root, contrary to AGENTS.md §2. Reported only, per the owner's choice;
  `docs/RULEBOOK_PIN.md` is already in the right place.
* **One-record discrepancy** in the 222-vs-223 primitive-friendly count (§6).

## 9. Files changed

* `AGENTS.md` — §2, §4, §5.
* `.agent/00_BOOT.md` — steps 2, 3, 5, 7; front matter.
* `docs/DECISIONS.md` — new governance entry.
* `.agent/TASK_LEDGER.md` — repair (B5-0011).
* `b5ccg/scratch/backup-2026-10-16/` — pre-edit copies of all four edited files
  (note: `scratch/` is not gitignored, so these are untracked working-tree files).
* This report.

Nothing is committed, staged or pushed — agents do not commit, per AGENTS.md §5.
