---
document:
  title: "Verification task spec: the two competing root causes for the truncated b5ccg gate table"
  status: "Observation / task spec. No authority (AGENTS.md section 3). Nothing here is promoted."
provenance:
  author_llm: {name: "opencode (Space Bunny Free)", version: "space-bunny-free"}
  created_date: "2026-10-19"
  last_modified_date: "2026-10-19"
  last_modified_by_llm: {name: "opencode (Space Bunny Free)", version: "space-bunny-free"}
  assessor_llm: none
  no_self_assessment: true
  basis: "Written by the author of .agent/REPORTS/2026-10-19-opencode-space-bunny-free-01-post-b5-0034-triage.md, section 4.6. It asks an independent agent to ADJUDICATE the author, and is deliberately written to try to refute the author's own position as well as the opposing session's. Every claim it puts forward is checkable against the tree; nothing in it is asserted from a prior document."
  related:
    - ".agent/REPORTS/2026-10-19-opencode-space-bunny-free-01-post-b5-0034-triage.md"
    - ".agent/REPORTS/2026-10-19-opencode-space-bunny-free-01-open-census-and-gate-truth.md (written by the other session, under this session's agent id - see triage report section 4.6)"
---

# Verification task spec: truncated gate table - stdin inheritance or table-writer?

This is a prompt to hand to an independent verifying agent verbatim, starting at
the horizontal rule below. Nothing above the rule is part of the prompt.

Notes for whoever files or runs this, not for the verifier:

* **The logs this spec cites are now durable.** Originally the run-4 log lived
  only under the gitignored `.agent/tmp_evidence/`. The load-bearing artefacts
  were copied to `.agent/REPORTS/evidence/` and Q4 below now points there:
  `GATE-TRIAGE4.txt` (the run log) and `repro_table.txt` (all 31 run-4 console
  rows with run-4 timings). Q4 is answerable from a clean clone.
* **Paths use this host's layout** (`C:\temp\projects\b5ccg`, Git Bash at
  `C:\Program Files\Git\bin\bash.exe`). They are not portable.
* **The disputed comment is live in shipped tooling.** `b5ccg/compile.sh`
  carries the other session's causal claim ("HeadlessHumanSeatProbe reads
  stdin, consumed the remaining 27 class names") in a comment block attributed
  to B5-0026. If Q1 shows no class can read stdin, that comment is an
  unverified causal claim embedded in a build script every agent runs, and it
  should be corrected rather than left standing on the strength of a comment.

---

You are adjudicating a factual disagreement between two AI sessions that were both editing the same repo at the same time. Your job is to determine which explanation is correct, by testing, not by reading. Be adversarial toward BOTH claims.

REPO: C:\temp\projects\b5ccg  (Babylon 5 CCG, Java, stdlib only)
GATE SCRIPT: b5ccg\compile.sh
BUILD: JDK 25 staged at b5ccg\scratch\jdk25-win\jdk-25.0.4.1+1 — the scripts call bare
  `javac` from PATH and ignore JAVA_HOME, so you MUST prepend that bin dir to PATH.
  Run the gate with:  export PATH="/c/temp/projects/b5ccg/b5ccg/scratch/jdk25-win/jdk-25.0.4.1+1/bin:$PATH"
                     RUN_TESTS=1 sh compile.sh        (from b5ccg/)
  A full gate run takes ~4-5 minutes (HeadlessHumanSeatProbe alone is ~186s). Use background execution.
  There is a build lock at b5ccg\b5ccg_build.lock.dir. Do NOT run a second build concurrently.

THE DISPUTE
The gate ran 31 classes in a loop fed by a pipe:
    printf '%s\n' "$GATE_CLASSES" | grep . | while IFS= read -r cls; do
        java -cp "$OUT" "$cls" < /dev/null > "$LOG" 2>&1 || CODE=$?
        printf '%-6s %-8s %-10s %s\n' "$CODE" "${DURATION}ms" "$RES" "$cls" ...
    done

In one run the gate table ended up with only 4 rows. Session B (the OTHER session)
attributes this to STDIN INHERITANCE. Its exact claim, recorded as a comment now
in compile.sh lines ~241-246, is:

    "with stdin inherited the gate recorded only 4 of 31 classes ...
     HeadlessHumanSeatProbe reads stdin, consumed the remaining 27 class names,
     and the loop stopped after 4."

Session A (the session asking you) rejects that and claims the table rows were lost
by the TABLE WRITER, not the loop. Session A's supporting evidence:
  (a) No Java file in the tree reads System.in at all.
  (b) The run-4 CONSOLE table carries all 31 rows - HeadlessHumanSeatProbe
      PASSED at 185707ms, with 26 more classes completing after it - while the
      file-derived summary reports only 4.

CAUTION ON THE EVIDENCE, added after Session A got this leg wrong once:
Session A originally argued from "31 per-class .log files were present in
out_gate\". That was a mistake - compile.sh wipes GATE_LOG_DIR at the start of
every run, so those files belonged to a later run and were never run-4 evidence.
Argument (b) above is the correct and load-bearing one. Do not credit (a).

QUESTIONS TO ANSWER, each with a command and its observed output

Q1. Can ANY gate class read stdin? Search the whole source tree for System.in,
    new Scanner(...), Console, and readLine() whose source is not a FileReader /
    FileInputStream. Report every hit with file:line and say which stream it reads.
    Specifically inspect HeadlessHumanSeatProbe's "human driver" — is it a stdin
    reader or an in-process thread? Quote the mechanism.
    VERDICT: CAN / CANNOT a gate class consume the class-name pipe?

Q2. Reproduce the table-writer hypothesis directly. Build a throwaway shell script
    (in your temp dir, NOT in the repo) that pipes ~31 lines into `tee file.txt`
    and then IMMEDIATELY counts the file, in a loop, many times, on this host's
    Git Bash. Does `wc -l file.txt` ever come back short of 31 immediately after
    the pipeline completes? Report the miss rate over >= 50 iterations.
    This tests whether the flush race Session A describes is actually reproducible
    here, and how often.

Q3. Reproduce the mid-execution-rewrite hazard. In a temp dir: start a bash script
    that runs a loop printing one line per second for ~20s; WHILE IT RUNS, rewrite
    the script file on disk with different-length content; observe whether the
    running shell resumes at a garbage byte offset and terminates the loop early.
    Report exactly what happens. (Session A's alternative explanation for run 4 was
    that another session rewrote compile.sh while bash was executing it.)

Q4. Judge the evidence for run 4 specifically. The run-4 log is at
    .agent\REPORTS\evidence\GATE-TRIAGE4.txt . Its summary line reads
    "Summary: 4 passed, 0 failed (of 31 gate classes run)" and the total gate time
    was ~23 seconds. Count the CONSOLE table rows present in that log.
    NOTE: do NOT use the current contents of b5ccg\out_gate\ as run-4 evidence -
    compile.sh does `rm -rf "$GATE_LOG_DIR"` at the start of every run, so those
    files belong to the most recent run, not run 4. The reconstructed run-4
    console table with run-4's timings is preserved at
    .agent\REPORTS\evidence\repro_table.txt (31 rows).
    Does a console showing 31 rows with a file-derived summary showing 4
    discriminate between the two hypotheses, or could BOTH produce it?
    Be rigorous about this.

Q5. Which explanation is better supported, and what would falsify it? State your
    verdict as one of: SESSION B CORRECT / SESSION A CORRECT / BOTH PLAUSIBLE /
    NEITHER ESTABLISHED. Give the single strongest piece of evidence, and name the
    one experiment that would most cleanly distinguish the hypotheses if the
    evidence above is ambiguous.

CONSTRAINTS
- Do NOT edit any file under C:\temp\projects\b5ccg. Read-only investigation plus
  throwaway scripts in your own temp directory. If you need a build, run it, but
  do not modify sources, and do not run two builds at once.
- Do NOT trust either session's report. Re-derive everything.
- Distinguish clearly between what you OBSERVED and what you INFERRED.
- If the evidence does not settle it, say so plainly. "Not established" is a
  valuable answer here; the entire point of this repo's recent work is that
  unverified claims got recorded as fact.
- Note: compile.sh currently contains Session B's comment asserting the stdin cause
  with a measurement claim ("Measured 2026-10-19 (B5-0026)"). If Q1 shows no class
  can read stdin, that comment is an unverified causal claim embedded in shipped
  tooling, and flag it as such.

Report your answer as: Q1..Q5 verdicts, then a final verdict, then the single most
important caveat.