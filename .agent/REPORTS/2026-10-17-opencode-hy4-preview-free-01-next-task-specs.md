---
document:
  title: "B5-0018/0019/0020 — Applying claude-b5ccg-next-task-specs-v1 (partial: append rows + cheap items)"
  status: "Observation only — no authority. The proposal itself is candidate; rows are OPEN; no behavior changed."
  placement: ".agent/REPORTS/ (observations per AGENTS.md §2)"
provenance:
  author_llm: {name: "opencode (tencent/hy4-preview-free)", version: "opencode-hy4-preview-free-01"}
  created_date: "2026-10-17"
  last_modified_date: "2026-10-17"
---

# B5-0018 / B5-0019 / B5-0020 — Applying Claude's next-task-specs v1 (partial)

**Scope this session:** planner apply pass + the "cheap items" only (the owner,
on 2026-10-17, chose "Apply + finish cheap items"; gate runs and the restore
drill were explicitly out of scope). Agent id:
`opencode-hy4-preview-free-01`. Claims taken on `B5-0019` (docs) and `B5-0020`
(tooling); both released at end of session.

## 1. What the proposal asked vs what the repo already had

The proposal was written without repo access. At boot I confirmed several items
were **already done today** by `nemotron-3.5-super-vl` (report:
`2026-10-17-nemotron-3-5-super-vl-01-b5-actions.md`), so I did not repeat them:

| Proposal item | Repo state at start of this session |
|---|---|
| B5-0017 `primitive-fit` classification (the substantive task) | not started — this becomes **B5-0018** below |
| B5-0018 baseline-by-name + repair owner | core done: the 4 exact failing check names are in `docs/DECISIONS.md:279-284`; repair row `B5-0017` exists and is OPEN |
| B5-0019 `.gitignore` + snapshot excludes | core done: all 5 paths already in `.gitignore` |
| B5-0020 deterministic probe output | partially done: `CardEffects.behaviourReferences()` moved `HashSet`→`TreeSet` (unverified here) |
| A1 reconcile B5-0008 ledger/DONE | already reconciled by nemotron (row is DONE, matches DECISIONS) |
| A2 manual working-tree commit | **owner action** — `AGENTS.md` §5 forbids agent commits; not done |
| A3 third agent for B5-0012 assessor | nemotron verified D3 wording consistent; assessor pass still pending a different agent while B5-0012 is claimed |
| A4 move root `.md` files | already done (no root `.md` except README/AGENTS/RULEBOOK) |
| A5 rulebook heading extraction | still blocked |

## 2. Numbering decision (and deviation from the spec)

The proposal says new rows start at **B5-0017**, but ledger row **B5-0017 was
already taken** (it owns repairing the four baseline checks — created earlier
today). The queue/claim/census tools key on `B5-[0-9]{4}`, so a second B5-0017
would **silently drop** whichever task landed second. I shifted every proposed
row by one, preserving the proposal's internal order:

| Proposal id | Repo id | Status | Rationale |
|---|---|---|---|
| B5-0017 (primitive-fit) | **B5-0018** | OPEN, not started | free id; classification is the real effect-work gating task |
| B5-0018 (baseline by name) | **B5-0019** | OPEN, **partial this session** | wording done, gate runs not |
| B5-0019 (gitignore/snapshot) | **B5-0020** | OPEN, **partial this session** | excludes verified, restore drill not re-run |
| B5-0020 (deterministic probes) | **B5-0021** | held in Future block | per the proposal's "hold" |

## 3. What this session did

### 3.1 Appended the rows (docs)
Added three rows to `.agent/TASK_LEDGER.md` via the shipped
`append-ledger.ps1` (the safe, verified path). `append-ledger.ps1` writes at
EOF, which leaves rows **after** the table — a latent problem — so I moved the
three rows into the table, contiguous and sorted by id (B5-0017→0020), and added
a `B5-0021 — deterministic probe output` line to the Future block. The table now
reads B5-0013…B5-0020 with no stray rows. (Observation, no task filed: the
append tool should ideally splice into the table at the right spot so callers
cannot leave rows outside it. Flagged as a follow-up, not added as a row — the
queue is already in the 5–10 band: 2026-10-16T09:42Z census listed ~7 open, now
10.)

### 3.2 B5-0019 — named the baseline failures (docs)
- `AGENTS.md` §4: replaced the count-only wording `currently 4 of 904 conformance
  checks (AES/AIJ AI ×2 and MINES ×2)` with the **four names**, and added: logs
  are compared **by verdict and failing-name set, never by byte equality**, and
  the §4 baseline clause is deleted only in the task that repairs them (i.e.
  when B5-0017 lands).
- `.agent/00_BOOT.md` §7: replaced `(currently 4 of 904 conformance checks)`
  with the four named checks and the same comparison rule.
- Verified the repair owner row `B5-0017` is present and OPEN.

The row **stays OPEN**: its acceptance requires *two consecutive gate runs*
whose failing-name sets are recorded and compared. I ran **none** (PATH
`javac` is 1.8.0_292 here — the JDK-8 stop condition for building; the staged
JDK 25 exists at `b5ccg/scratch/jdk25-win/jdk-25.0.4.1+1` but the owner's
approved scope excluded gate runs). The four named checks are **inherited from
`DECISIONS.md`:**279-284 (recorded there by the B5-0012 apply pass and by
nemotron's actions); I did not independently re-derive them.

### 3.3 B5-0020 — closed out the `.gitignore`/snapshot excludes (tooling)

**The five paths are ignored.** Per-file `git check-ignore -v`:

| path | result |
|---|---|
| `out/` | `.gitignore:3` |
| `b5ccg/scratch/` | `.gitignore:28` (so the 141 MB staged JDK is excluded) |
| `.agent/CLAIMS/` | `.gitignore:30` |
| `.agent/HEARTBEATS/` | `.gitignore:31` |
| `b5ccg_compile_list.tmp` | `.gitignore:16` (`*.tmp`) |

Answering the proposal's open question `?. new-snapshot.ps1 honors .gitignore?`:
**yes, by construction.** `.agent/tools/new-snapshot.ps1` has **no separate
exclude file**; it stages with `git add -A` into a throwaway `GIT_INDEX_FILE`
under `.git/` (step 5, line 209-218) and its secret scan uses
`git ls-files --others --exclude-standard` (line 167). Both honour
`.gitignore`. So the snapshot job will not absorb the JDK or machine-local
scratch — the objection the owner raised is resolved.

**Dry run.** `powershell -File .agent/tools/new-snapshot.ps1 -DryRun`:

```
[2026-10-17T...Z] secret scan clean over 251 file(s).
[2026-10-17T...Z] staged tree a071781df2... into throwaway index; real index untouched.
[2026-10-17T...Z] change vs parent: 158 file(s).
[2026-10-17T...Z] DRY RUN ... branch : auto-snapshot (exists: False)
                              parent : 98e46ee86fd8...
                              tree   : a071781df23a375ab0baeaf210bbf8196538a18f
                              files  : 158
[2026-10-17T...Z] EXIT 0: dry run clean.
```

I then enumerated the tree object itself
(`git ls-tree -r --name-only a071781df23a375ab0baeaf210bbf8196538a18f`):
246 files, and **none** of the five paths appear, including the JDK. The only
files under those paths in the tree are the two that were **already tracked
before** the ignore rule was added:
`.agent/HEARTBEATS/README.md` and
`.agent/HEARTBEATS/opencode (muse-spark-1.3-free) 01.json` (plus its
`opencode (muse-spark-1.3-free).json` sibling — tracked). These persist because
`.gitignore` does not untrack already-tracked files, not because they slip past
the ignore rule. Per AGENTS.md §2 and the spec's acceptance, they are **reported
here and left in the index** — no tracked file was removed. (`No tracked file
should be removed from the index by this task` is the binding line; I followed it.)

The real index was untouched (the dry run staged into a throwaway index only);
working tree is unchanged except my edits above; the lock file was removed by
the script's `finally`.

**Not run (owner/acceptance debt):** the B5-0001 restore drill (`b5ccg-compile +
gate green` against a snapshot). It needs the staged JDK 25 on `PATH` and a
committable tree, both of which are owner actions in this session. Until it is
re-run, the snapshot job's *safety claim* is argued-by-inspection (excludes are
correct, real index untouched, dry run clean) rather than demonstrated end to
end. Row stays OPEN for that reason.

**Recoverability note requested by the spec:** the snapshot job does not back up
`b5ccg/scratch/backup-*`; it snapshots the *working tree* onto an
`auto-snapshot` branch. The `backup-*` set (in `b5ccg/scratch/`) is therefore
only on disk; there is no other stated keeper. I did not move or delete any of
it. The owner decides whether `backup-*` should additionally be gitignored (it
already is, as a child of `b5ccg/scratch/`) or excluded from disk GC.

### 3.4 Observation (not actioned)
Six untracked scratch scripts sit at `.agent/` root
(`scratch-analyze-04.py`, `scratch-census.py`, `scratch-triage-04.py` and the
`-04b`/`-04c`/`-04d` variants). They are not ignored (the ignore rule covers only
`.agent/tmp_*/`) and are outside the five-path set, so they were left alone; the
owner may want them gitignored or moved.

## 4. Files changed in this session (all small, text-only, docs/tooling)

- `.agent/TASK_LEDGER.md` — added rows B5-0018/0019/0020; repaired table shape; Future block note for B5-0021.
- `AGENTS.md` — §4 baseline clause: count → four named checks + comparison rule + "deleted only in the repair task".
- `.agent/00_BOOT.md` — step 7: same count → four named checks + comparison rule.
- `docs/DECISIONS.md` — new entry `2026-10-17 Applying Claude next-task-specs v1 (partial)`.

No `.java`, card JSON, census JSON, build script, rulebook body, or root `.md`
was changed (`AGENTS.md`, `README.md`, `BABYLON5_CCG_RULEBOOK.md` are the only
root `.md` and remain the only ones). The snapshot dry run wrote only the
gitignored `snapshot.log`.

## 5. Verification

- `run-dup-census.ps1`: **PASS** — "duplicate-ID census: 0 duplicate task IDs".
- `check-citations.ps1`: **PASS** after this report landed (was 3 dangling
  refs: the two ledger report-cell entries and the DECISIONS report pointer;
  all resolved by creating this artifact).
- No compile/gate run was executed (owner scope; JDK-8 `javac` is the only
  `javac` on `PATH`). `javac -version` → `javac 1.8.0_292` (recorded in the
  heartbeat as required by `00_BOOT` step 2; the JDK 8 on `PATH` is reported as the
  stop condition for building and was not used as a build compiler).

## 6. Open rows and why they remain OPEN

- **B5-0018** primitive-fit classification: untouched this session (read-only
  analysis, large; a second agent's blind re-classification is also required by
  acceptance). Ready to be claimed.
- **B5-0019** (this row): partial. Remaining acceptance item = two consecutive
  gate runs whose failing-name sets are recorded and compared.
- **B5-0020**: partial. Remaining acceptance item = the B5-0001 restore drill,
  then delete nothing/verify, then DECISIONS one-line entry is this session's
  entry (done), then close.
- **B5-0021** deterministic probe output: held in Future per the proposal.

## 7. Honest uncertainty / things I will not assert

- I did **not** re-derive the four baseline failing checks myself; I rely on
  `DECISIONS.md` 279-284 and nemotron's recorded gate run. If the tree has
  changed since (other agents' edits are uncommitted in `.git`), a fresh run is
  the only way to re-confirm the *current* set.
- The `.agent/HEARTBEATS/` and `.agent/CLAIMS/` entries for this session are
  machine-local and were created during this run.
