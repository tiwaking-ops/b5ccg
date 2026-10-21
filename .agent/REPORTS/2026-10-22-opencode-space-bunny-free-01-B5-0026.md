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

**The owner's rulings (2026-10-22), in two parts:**

1. *Wait, measure the three KPIs on 2026-10-30, and close the row then.*
2. *The "before" for all three readings is the OLD pre-fork project*, and the
   three readings are **defined** — per-task documents over all ledger rows
   with a target of 1.2, the boot file against a 70 % cut, and the
   gate-green change rate against the window's own end.

This row did not close the row. It built the instrument the reading needs,
took the baseline, **encoded the ruling in that instrument**, and left the
appointment.

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
| `.agent/tools/measure-process-tax.ps1` | the instrument: measures the three KPIs and the §7.4 target table, prints its denominators, writes a JSON snapshot. **Carries the owner's ruling as constants** — the baseline, the targets and the three definitions — so the 2026-10-30 reading cannot silently use another baseline |
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

**KPI (a) — process documents per ledger row.** *(As ruled by the owner on
2026-10-22.)* *Denominator* = **ALL ledger rows** — the acceptance says "per
ledger row", not "per completed row". *Numerator* = the **per-task
documents**: the reports scoped to a single ledger row, plus the pattern
files. The project-wide corpus (proposals, `DECISIONS.md`, the rule index) and
the fleet-wide audits are excluded, because §7.4's table is explicit that
B2 *keeps* authority-bearing documents and they are a standing investment
rather than a task's document. *Target* = **1.2**, because §7.4's own table
caps patterns at "at most 0.2 patterns per task", so a compliant fleet
averages 1 report + 0.2 patterns by design. Two context figures are printed
beside it — never instead of it.

**KPI (b) — boot read cost per task.** *(As ruled.)* The size of
`.agent/00_BOOT.md`, which every session reads once. *Before* = the old
project's 21 700 B. *Target* = §7.4's **measurable** ("boot read down about
70 %", ≤ 6 510 B), with §7.4's **design figure** ("a core of about 4 KB",
4 096 B) and a stated **10 % tolerance** — "about 4 KB" is a fuzzy figure,
and a strict byte comparison would turn a 4.9 % overshoot into a red verdict.
Bytes and lines are measured; the token figure is an **estimate** (bytes ÷ 4)
and is labelled as one, because the tokeniser's vocabulary is unknown.

**KPI (c) — gate-green merged changes per day.** Commits per day on the
working branch — in this repo a commit *is* the unit of
merged work, because AGENTS.md §5 forbids agents committing to `main`/`master`
and D3 permits private task branches — counted over the days that have at
least one GREEN gate capture. **There is no old-project "before" for this
one, and there does not need to be:** "stay flat or rise" is a statement about
the *window*, so the window's own end is the reference.

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

## 3. The measurement, as taken today — with the owner's ruling applied

```
KPI (a)  1.143 process documents per ledger row   (56 per-task documents / 49 rows)
         56 = 54 row-scoped reports + 2 pattern files
         target: 1.2 or less                          VERDICT: MET
         before (old project) 5.1 docs/task           ->  a 78% cut
         context only: all documents over all rows 1.959; over DONE rows 2.233

KPI (b)  4 295 B / 74 lines  (.agent/00_BOOT.md)   estimated 1 073.8 tokens
         before (old project) 21 700 B               ->  an 80% cut
         target: a 70% cut (<= 6 510 B); design figure "about 4 KB" (4 096 B),
                 tolerance 10%                        VERDICT: MET
         (4 295 B is 4.9% over the design figure, inside the tolerance)

KPI (c)  6.67 gate-green merged changes per day, over the 3 active days that
         have a GREEN capture; coverage 3 of 6 active days
         commits/day: min 2, max 10, mean 6.67 over 6 active days
         no old-project "before" exists: the window's own end is the reference
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

## 4. The three findings, and how the owner's ruling settled them

**The owner ruled on 2026-10-22: the old project is the "before" for all three
readings.** That ruling settled §4.1, settled §4.2's definition, and
dissolved §4.3's puzzle. Each is kept below with its resolution, because
the reasoning is the record and the resolution is only meaningful against it.

### 4.1 The "before" was ambiguous — **RULED: the old project**

The acceptance says "measure before and after on the same two-week window"
without saying what "before" is. Two candidates existed and they gave
different verdicts:

| KPI | "before" = the old repo (§7.4's own baseline) | "before" = the fork, 2026-10-16 |
|---|---|---|
| (a) process docs per row | 5.1/row | **UNDEFINED** — the fork had 0 ledger rows |
| (b) boot cost | 21.7 KB | **2 653 B** |
| (c) gate-green changes/day | not recorded | **UNDEFINED** — the fork had 0 gate captures |

The ruling is right on the substance, and for a reason worth recording:
**the B2 cuts WERE the fork.** The fork's own commit message reads "Fork
genesis: full game snapshot + **lean B-reset** workflow". The cuts landed
*at* the fork, so the fork is the *after* of the cuts and cannot be a
*before* of them. The old project's 5.1 and 21.7 KB are §7.4's own figures,
so they are the right comparison, and the window's job is to show the cuts
have **held** for a fortnight.

### 4.2 KPI (a)'s definition — **RULED: per-task documents over all ledger rows, target 1.2**

The numerator and the denominator were both open, and the acceptance settles
the denominator in its own words — "process documents per **ledger row**",
not per completed row — so the denominator is **all 49 rows**, not the 43
DONE ones. (Using DONE rows was this agent's error, and it inflated every
figure it produced.)

The numerator is the **per-task documents**: the 54 reports scoped to a
single ledger row, plus the 2 pattern files. §7.4's table is the list of
what B2 cut and is explicit about what survived — "Keep it on
authority-bearing documents (canonical, **proposals, reports**)" — so the
project-wide corpus (22 files) and the fleet-wide audits (18 files) are a
standing investment and substantive work, not a task's document.

The target is **1.2**, because §7.4's own table caps patterns at "at most
0.2 patterns per task", so a fleet complying with every other clause of B2
still averages 1 report + 0.2 patterns. "About 1 or less" has to tolerate
B2's own design point.

**Reading today: 1.143 — MET**, a 78 % cut from 5.1. The two context
figures (1.959 over all documents, 2.233 over DONE rows) are printed beside
it, never instead of it.

### 4.3 KPI (b) moved the wrong way against the fork — **dissolved by the ruling**

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

And the arithmetic said the fight was unwinnable: the only lever §7.4's own
remedy offers — moving the 764-character baseline clause into a module —
reaches 3 508 B, still **855 B above** the fork. No available action makes
KPI (b) fall against the fork.

**With the old project as the "before", this is not a puzzle.** 21 700 B →
4 295 B is an **80 % cut**, and §7.4's own measurable ("boot read down about
70 %") is met with room. What survives from the puzzle is a **tidy-up**: the
file is 4.9 % over §7.4's "about 4 KB" design figure, and moving the
baseline clause into a module would bring it to 3 508 B, inside 4 KB. That
is worth doing for tidiness, not because the metric demands it.

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

**The v0 vocabulary could express 16 of 26 group cards — 61.5%.** The pilot
gate's threshold is 70%, so **the schema as this row shipped it would have
FAILED the Groups pilot.** With the registry as this row left it, **ten of
the 26 group cards had at least one entry the vocabulary could not
express**: Damage Control Team (no unrotation), Imperial Telepaths, Military
Telepaths and Psi Corps Intelligence (the trigger set could not name a
conflict of the Psi type), ISN and Observers (no information primitives),
Motivated Leaders (no discarding the source card), Rangers Surveillance (no
reordering the draw pile), Religious Caste (no "at most" condition) and Spin
Doctors (the escape hatch published no handler, so the card could not be
written down at all). The four vocabulary gaps the pilot exposed — the Psi
trigger, the "at most" condition, the "may" against "must" operation shape,
and the three missing primitive families — are now closed, so the pilot's
96% expressibility figure is **the result of those additions, not a
measurement of what this row delivered**.

**This report first stated 19 of 26 (73 %) and concluded the schema cleared
the threshold.** That figure was taken from the Groups pilot's own report and
it is the count of cards with **at least one** expressible entry. The B5-0051
verification row re-measured it — rebuilding the v0 effect package from the
commit this row left it at, and loading each of the pilot document's 35
entries on its own against it — and the figure that answers the *gate* is
**16 of 26 = 61.5 %**, because the gate asks about a card and a card whose
Psi-triggered entry cannot be written down is not expressible: half of what
it prints is missing. The correction is the B5-0051 DECISIONS entry of
2026-10-22.

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
- **A lapse by this agent, recorded rather than smoothed over.** This row's
  claim was released after the first round of work (the instrument, the
  baseline, the first ruling and the report) and the **second** round - the
  owner's second ruling, the encoding of that ruling in the instrument, the
  probe, the DECISIONS entries and the re-taken baseline - was then carried
  out in the `docs` and `tooling` scopes **without a live claim**. AGENTS.md
  section 5 says to work only inside a claimed scope, and that is the rule
  the same section's freeze-guard gap fails to enforce for other agents; it
  binds this agent too. A claim was taken for the final verification and
  released, and the claim files for this row therefore show **two** releases
  (`B5-0026-opencode-space-bunny-free-01.RELEASED.json` and
  `B5-0026.RELEASED.json`) rather than one. The work is unaffected - the
  scope was B5-0026's own throughout, and no other agent's claim or heartbeat
  was touched - but the record of *when* the claim was held is incomplete, and
  that is the part an auditor would want.

---

## 9. The appointment — 2026-10-30

1. Run the instrument:
   `.agent/tools/measure-process-tax.ps1 -OutFile
   .agent/REPORTS/measurements/process-tax-2026-10-30.json`
2. Compare it against `process-tax-2026-10-22.json` — the same-window
   baseline. **One baseline, not two**: the old project is the "before" for
   KPIs (a) and (b) by the owner's ruling, and KPI (c) has no old-project
   "before" at all, because "stay flat or rise" is a statement about the
   window. `process-tax-2026-10-16-fork.json` remains as the record of the
   window's other end, not as a comparison.
3. Record the comparison in a report and in a DECISIONS entry.
4. **Only then** flip the row. The instrument now prints a verdict for each
   KPI against the ruled targets, so the flip is a matter of the verdicts
   being MET and the window having elapsed.

The tooling probe is in the gate, so the instrument is checked on every gate
run rather than on the morning of the reading.
