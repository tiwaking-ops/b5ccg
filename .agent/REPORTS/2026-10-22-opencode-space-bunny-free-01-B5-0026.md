---
document:
  title: "B5-0026 — WP-09 process-tax measurement: the instrument, the baseline, and the appointment"
  status: "Report (observation, no authority)"
provenance:
  author_llm: {name: "opencode (Space Bunny Free)", version: "space-bunny-free-2026-10-22-03"}
  created_date: "2026-10-22"
  last_modified_date: "2026-10-22"
  last_modified_by_llm: {name: "opencode (Space Bunny Free)", version: "space-bunny-free-2026-10-22-03"}
  assessor_llm: none
  unassessed_revision:
    - {author_llm: {name: "opencode (Space Bunny Free)", version: "space-bunny-free-2026-10-22-03"}, date: "2026-10-22", change: "initial report for B5-0026", note: "authored by this agent and NOT independently assessed; no assessor_llm entry is claimed for this pass. A different-family assessment of this row is outstanding."}
---

# B5-0026 — WP-09 process-tax measurement

**Row.** B5-0026, **OPEN**, and it stays OPEN. Scope `docs, tooling`.

**The owner's ruling (2026-10-22):** *wait, measure the three KPIs on
2026-10-30, and close the row then.* This row did not close the row. It built
the instrument the reading needs, took the baseline, and left the appointment.

**Why the row could not simply be closed.** The acceptance reads "B2 targets
in 7.4 met and measured over a two-week window". The targets were already met
and measurable; the *window* was the part that could not elapse on demand. The
2026-10-19 UNATTRIBUTED audit had reached the same place and said so: "The
window has not elapsed, so marking it DONE would be a false claim. Stopped
this item only, per `AGENTS.md` section 4."

**This report is not independently assessed** — recorded as an
`unassessed_revision` per AGENTS.md §1.

---

## 1. What was delivered

| Artefact | What it is |
|---|---|
| `.agent/tools/measure-process-tax.ps1` | the instrument: measures the three KPIs and the §7.4 target table, prints its denominators, writes a JSON snapshot |
| `.agent/tools/test-process-tax-measurement.ps1` | 22-check tooling probe, **wired into `compile.sh`**, that proves the instrument still works |
| `.agent/REPORTS/measurements/process-tax-2026-10-22.json` | the baseline snapshot (the working tree today) |
| `.agent/REPORTS/measurements/process-tax-2026-10-16-fork.json` | the "before" end of the window, measured with the same instrument against the fork commit `94f0fa7` |
| `docs/DECISIONS.md` | the owner's ruling and the measurement plan, with the findings |

**Why an instrument at all.** The acceptance says "measure before and after on
the same two-week window". A before/after measured with two different
instruments is not a measurement — it is a comparison of two people's habits,
and the difference would be attributed to the process tax. The instrument
exists so that the 2026-10-22 baseline and the 2026-10-30 reading are
comparable *by construction*.

---

## 2. The definitions, which are the instrument's real content

A number without a definition is a rumour. Each definition is printed with
the reading and carried in the snapshot's `definitions` block.

**KPI (a) — process documents per DONE ledger row.**
*A process document* = a Markdown file under `.agent/REPORTS/`,
`.agent/PATTERNS/`, `docs/` or `investigations/`. *Denominator* = ledger rows
with status DONE. Two figures are printed, not one chosen silently: the
**primary** (all process documents) and the **narrow** (reports + patterns
only), because `docs/` mixes project-wide artefacts with per-task ones and
the proposal's 5.1/row figure counted both.

**KPI (b) — boot read cost per task.**
The size of `.agent/00_BOOT.md`, which every session reads once. Bytes and
lines are measured; the token figure is an **estimate** (bytes ÷ 4) and is
labelled as one, because the tokeniser's vocabulary is unknown.

**KPI (c) — gate-green merged changes per day.**
Commits per day on the working branch — in this repo a commit *is* the unit of
merged work, because AGENTS.md §5 forbids agents committing to `main`/`master`
and D3 permits private task branches — counted over the days that have at
least one GREEN gate capture.

The verdict is parsed from the capture, and the rule is strict: a capture is
**RED** if it reports a failed class, a failed tooling probe, **or** an ERROR
line. The tooling-probe clause is not decoration — `GATE-B5-0044-RED.txt`
reads "31 passed, 0 failed" for the Java classes and is nevertheless red,
because a tooling probe failed. A capture whose file name carries `-RED` is
**deliberate red-then-green evidence, not a failure**, and is counted
separately from a red capture with no `-RED` in its name.

**The coverage line is part of the measurement, not a footnote to it.** "Green"
is only evidenced where a capture exists, so the number of days with a
capture is printed next to the rate.

---

## 3. The measurement, as taken today

```
KPI (a)  2.209 process documents per DONE row   (95 documents / 43 DONE rows)
         95 = 71 reports + 2 patterns + 22 docs + 0 investigations
         narrow reading (reports + patterns only): 1.698
         target: "about 1 or less"

KPI (b)  4 295 B / 74 lines  (.agent/00_BOOT.md)   estimated 1 073.8 tokens

KPI (c)  6.33 gate-green merged changes per day, over the 3 active days that
         have a GREEN capture; coverage 3 of 6 active days
         commits/day: min 2, max 10, mean 6.5 over 6 active days
```

**§7.4 target table, measured today:**

| Target | Measured | Target | Met |
|---|---|---|---|
| boot file | 4 295 B | under ~5 KB | yes |
| live ledger | 31 902 B | under ~200 KB | yes |
| `investigations/` | 0 files | not read at boot | yes |
| pattern namespaces | 2 | a few dozen (from 513) | yes |
| root `.md` debris | none (AGENTS.md, the rulebook, README are required) | none | yes |
| DONE rows eligible to rotate | 0 | older than 14 days | yes (nothing is old enough yet) |

---

## 4. Three findings, and the one that needs the owner

### 4.1 The "before" baseline is ambiguous, and the two readings disagree

The acceptance says "measure before and after on the same two-week window"
without saying what "before" is. Two candidates exist and they give
different verdicts:

| KPI | "before" = the old repo (§7.4's own baseline) | "before" = the fork, 2026-10-16 |
|---|---|---|
| (a) process docs per row | 5.1/row | **UNDEFINED** — the fork had 0 DONE rows |
| (b) boot cost | 21.7 KB | **2 653 B** |
| (c) gate-green changes/day | not recorded | **UNDEFINED** — the fork had 0 gate captures |

Against the old repo's baseline, KPIs (a) and (b) are comfortably met. Against
the fork's baseline, (a) and (c) have no denominator or coverage, and **(b)
has moved the wrong way**. Both readings are in the snapshots and the
2026-10-30 reading will report against both.

### 4.2 KPI (a) is **not** met today on the primary definition

2.214 process documents per DONE row against a target of "about 1 or less";
1.69 on the narrow reading. The proposal's 5.1/row was the *old* repo's, and
this repo has never been near it — but 5.1 is the number the acceptance
compares against, and the honest reading is that the target is not yet
reached. That is the number the 2026-10-30 reading has to beat or match.

### 4.3 KPI (b) cannot be made to fall on the fork baseline — and that is a finding, not a task

The boot file grew 62 % since the fork (2 653 B → 4 295 B). The diff names
every line of the growth, and the growth is **required content**:

- the JDK 25 / `--release 8` toolchain and the "a JDK 8 `javac` is a stop
  condition" rule (the D2 ruling);
- the correction that `RUN_TESTS=1 sh compile.sh` is the verification gate and
  that a green fast gate is not (the B5-0026-era gate honesty work);
- the four-name baseline failing-set clause;
- the heartbeat and claim tool corrections.

Bringing the boot file back under the fork's 2 653 B means cutting those
lines, which would make the boot file **wrong**. A boot trim that satisfies a
number by deleting the instructions that keep the gate honest is the failure
this project has already recorded twice, so **it is not proposed**.

§7.4's own remedy — "a core of about 4 KB plus modules loaded on demand" —
would apply to the ~1.2 KB baseline clause, and it is the one lever left if
the owner wants KPI (b) met on the fork baseline. **That is an owner
decision, not an agent's**, and it is the one open question this row leaves
behind.

---

## 5. The instrument had five bugs, and the gate found the fifth

Recorded because the project's standing lesson is that a probe finds what
reading does not, and these are the kind of thing that would have made the
2026-10-30 reading wrong rather than absent.

1. **`git cat-file -blob` is not a flag.** `blob` is the *type* argument, so
   `-blob` parses as the switch `-b` and dies with "unknown switch `b'". The
   historical-tree measurement was therefore silently broken until the `-Ref`
   path was exercised against the fork.
2. **The instrument's own failure code was unreachable.** The catch block
   used `Write-Error`, which under `$ErrorActionPreference = 'Stop'` throws
   again and exits **1** — indistinguishable from a measurement that found
   nothing. It now writes to stderr and exits **2**, and the probe asserts
   the 2. A measurement that cannot be taken must never look like a clean
   one.
3. **A single-element array unrolled.** `return $series` on a one-element
   array hands back a scalar, and `.Count` on it is empty, so the KPI (c)
   summary printed "n =  active day(s)" and the coverage line printed
   "0 of  active day(s)". Fixed by forcing the array at the call site.
4. **The child's stderr became a terminating error in the probe.** The same
   trap `test-param-guard.ps1` already documents: under `Stop`, a child
   writing to stderr kills the parent before `$LASTEXITCODE` can be read. The
   probe now folds stderr into stdout and relaxes the preference for the call
   only, which is that file's existing pattern.
5. **A capture that was locked for writing killed the whole measurement —
   and the GATE found this one, not the author.** The first wired gate run
   came back **RED**: the instrument read the capture files with
   `ReadAllText`, and the capture the gate was *writing at that moment* —
   `GATE-B5-0026.txt` — was locked. The measurement died with "The process
   cannot access the file". The fix is a sharing-tolerant read
   (`FileShare.ReadWrite`), and a capture that is still unreadable is
   recorded as `UNREADABLE` and counted in the coverage gap rather than
   aborting the run.

   This is the bug most worth having found, because it is the *exact*
   condition of the reading day: a session on 2026-10-30 will run the gate
   and the measurement in the same breath, and the gate's own capture will
   be open. The probe now holds a capture open exclusively and asserts that
   the instrument survives it — 24 checks, up from 22.

   The lesson is the one this project keeps re-learning: **a probe that
   nothing runs is a comment.** The instrument had passed 22 checks from the
   repo root and still died in the place it was going to be used.

---

## 6. A scope collision, live, and what it cost

While this row was being worked, a second agent
(`opencode-minimax-m3-1-flash-free-01`) took a claim on **B5-0034** with scope
`engine,tooling,resources,docs` while this row's claim on **B5-0026**
(`docs,tooling`) was live — eight minutes later. The overlap is `tooling` and
`docs`, and **no tooling enforces it**: the freeze-guard gap this project
already records is exactly why the breaches of B5-0039/0040/0041 happened.
This is a fourth live instance of it.

**What it cost, measured.** The two rows touched the same files, and the
tree's gate went **RED**: the B5-0034 pilot moved the five Group entries out
of the shared v0 data file into a Groups file of its own — a defensible move,
"so that every card has exactly one home" — and the effect-schema probe,
which asserted the v0 file's entry count *exactly*, went red with it: 22
entries and 12 cards against the 29 and 17 it expected. The other agent
corrected the counts, and the gate went green (38 classes, 4 tooling probes,
exit 0) with both rows' work in it.

**The lesson is about the probe, not about the collision.** A probe that
asserts an exact count of a file another row is entitled to extend will
break every time the pilots land, and the fix that restores it by editing
the constant is a fix that will have to be repeated. The count should be a
**floor**: the data may grow, because the pilots add entries by design, but
it must not shrink, because a lost file is a defect. That is the B5-0044
pattern applied to a count.

It was **not** applied here, and that is a deliberate choice: the file was
under the other agent's active edit, and rewriting a file another agent is
working in is the one thing this collision had already demonstrated to be
expensive. It is recorded here as a recommendation for whoever next touches
the count.

**Neither agent's claim or heartbeat was touched, and no work of either was
reverted.** The collision was resolved by two agents cooperating inside a
tree that does not prevent them from colliding — which is the argument for
the freeze-guard row this project has been proposing since 2026-10-19.

**And what their row measured about *this* row's deliverable.** The B5-0034
Groups pilot is the first thing to use the v0 schema, so it is the first
measurement of it — and the number belongs here, because it describes the v0
vocabulary rather than the pilot:

**The v0 vocabulary could express 19 of 26 group cards — 73%.** The pilot
gate's threshold is 70%, so the schema cleared it, but by three points and
only before the pilot's own work: with the registry as this row shipped it,
**seven of the 26 group cards could not be expressed at all**. The four gaps
the pilot exposed were a trigger vocabulary that could not name a Psi
conflict (the rulebook names four conflict types; the v0 named three), a
condition vocabulary with no "at most" test, an operation shape that could
not tell "may" from "must", and three missing primitive families
(information, unrotation, discarding the source card). The pilot's 96%
expressibility figure is therefore **the result of those additions, not a
measurement of what this row delivered**.

One inconsistency in the v0 data was found and is recorded here: the skeleton
modelled "Add 4 to your total" ("Shadow Strike") with a FACTION-scoped target
but modelled the five group cards' equivalent rule with a CHARACTER-scoped
one — the same rule written two ways in the same file. The group entries have
moved to the pilot document, which uses the faction-scoped form throughout.

One statement in `docs/DECISIONS.md` §3 is now superseded: it says the
escape hatch is "specified and empty", which was the state at this row's
reading. The B5-0034 pilot has since published the first handler
(`SPIN_DOCTORS_PERCEIVED_WINNER`), so the hatch is now **specified and one
card wide**. The entry is left as written, dated, because that file's
convention is to append rather than to rewrite.

---

## 7. The gate

The tooling probe is **wired into `compile.sh`**, so a broken instrument is
caught on every gate run rather than on the morning of the reading. It
deliberately asserts that the instrument *runs* and that its output *carries
its definitions*, and it does **not** assert particular values — the values
move daily by design, and a probe that asserted them would have to be edited
every day and so would not be running on the day it mattered.

Per `00_BOOT.md` step 8, after appending to `docs/DECISIONS.md`:
`check-citations.ps1` was run and **failed** until this report was filed
(it resolves the report path this entry cites) and passes now; the
duplicate-ID census and the ledger lint pass; the encoding lint is unchanged
at its documented 20-hit baseline with no hit in any file this row created.

---

## 8. What is NOT done

- **The row is not closed.** The window has not elapsed. The 2026-10-30
  reading, the comparison against both baselines, and the flip are all still
  ahead. Per AGENTS.md §4, the ambiguity is logged and the item is stopped
  rather than resolved by assertion.
- **The boot file was not trimmed.** See §4.3. The growth is required
  content, and §4.3's one remaining lever is an owner decision.
- **No B2 target was re-cut.** The targets were already met; the row's
  remaining work was the measurement.

---

## 9. The appointment — 2026-10-30

1. Run the instrument:
   `.agent/tools/measure-process-tax.ps1 -OutFile
   .agent/REPORTS/measurements/process-tax-2026-10-30.json`
2. Compare it against **both** baselines: `process-tax-2026-10-22.json` (the
   same-window baseline) and `process-tax-2026-10-16-fork.json` (the window's
   other end).
3. Record the comparison in a report and in a DECISIONS entry.
4. **Only then** flip the row — and if KPI (b) is to be read on the fork
   baseline, get the owner's ruling on §4.3 first, because on that baseline it
   is not met and cannot be met by trimming.

The tooling probe is in the gate, so the instrument is checked on every gate
run rather than on the morning of the reading.
