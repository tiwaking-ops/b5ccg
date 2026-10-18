---
document:
  title: "B5-0041 DONE - compile.sh's falsified stdin justification corrected; redirect kept as defensive"
  status: "Report. Records a correction to a build-script comment; confers no authority on its own (AGENTS.md section 4)."
provenance:
  author_llm: {name: "opencode (Space Bunny Free)", version: "space-bunny-free"}
  created_date: "2026-10-19"
  last_modified_date: "2026-10-19"
  last_modified_by_llm: {name: "opencode (Space Bunny Free)", version: "space-bunny-free"}
  assessor_llm: none
  no_self_assessment: true
  basis: "Falsification re-verified independently in this session rather than taken from the adjudication report. The evidence below is a count over b5ccg/src, reproducible."
---

# B5-0041 - the falsified stdin comment in compile.sh

Scope `tooling`. One file: `b5ccg/compile.sh`. **Comments only - no executable
line changed.**

## 1. What the comment claimed

The gate loop carried this at lines 235-246:

> `< /dev/null` is REQUIRED, not hygiene. This loop's body runs with stdin still
> connected to the `printf | grep` pipe, so `java` inherits that pipe as its own
> stdin. Any class that reads a single byte from stdin consumes the remaining
> class names, and `read -r cls` then hits EOF and the loop stops early.
>
> Measured 2026-10-19 (B5-0026): with stdin inherited the gate recorded only 4 of
> 31 classes and exited via the "loop did not complete" guard, leaving
> HeadlessHumanSeatProbe.log empty. [...] Without this the gate cannot report its
> own verdict, which is the precondition AGENTS.md section 4 makes every
> promotion depend on.

Two separable claims: **(a)** the redirect is required, and **(b)** a measured
4-of-31 run is attributable to the stated cause.

## 2. Claim (a) is falsified

The mechanism requires a gate class that reads stdin. There is none:

```
java sources under b5ccg/src        189
System.in occurrences                 0
new Scanner / System.console          0
readLine(                              0
```

A class that never reads stdin cannot consume the pipeline, so the loop could
not have stopped early for that reason. The premise the whole argument rests on
is absent.

I re-counted this myself rather than accepting the adjudication report's
finding, because the report was produced by another agent and the claim is
cheap to check.

## 3. Claim (b) misattributes a real measurement

The 4-of-31 observation was real. Its cause was not stdin. The adjudication
(.agent/REPORTS/2026-10-19-opencode-big-pickle-01-gate-run4-adjudication.md)
found the loop ran to completion and the rows were lost downstream - and that
downstream defect was the **table writer/counting race**, which is precisely
what this session fixed: the `tee` flush race on `.row` writes, now synchronous,
plus the pre-verdict invariant that fails the gate if `ROWS != COUNT`.

So the comment credited a build fix to a stdin mechanism that cannot fire, and
implied the gate's trustworthiness rested on a redirect rather than on the race
fix that actually produced it.

## 4. What changed

The redirect **stays**. It is free, it is correct, and it forecloses a real class
of future surprise: if a class ever does read stdin it sees EOF instead of
eating the pipeline. What changed is the justification:

* "REQUIRED" becomes "DEFENSIVE";
* the false mechanism is quoted and marked FALSIFIED, with the counts that
  falsify it;
* the real cause - the writer/counting race - is named;
* the adjudication report is cited.

The false text is **recorded rather than silently deleted**. A comment deleted
without explanation invites the next reader to re-derive the same false cause;
a comment marked falsified stops that.

## 5. Verification

```
diff is comments only      added non-comment lines: 0
                          removed non-comment lines: 0
sh -n compile.sh          SYNTAX OK

full gate, sole session, staged JDK 25.0.4.1, --release 8, lock released clean:
  Verification passed (all 31 gate classes).
  Summary: 31 passed, 0 failed (of 31 gate classes run)
  Total gate time: 233346ms
  GATE_EXIT=0
  31 PASS rows, 0 FAIL rows
```

Preserved at `.agent/REPORTS/evidence/GATE-B5-0041.txt`. Unchanged from
`GATE-B5-0039.txt`, which is the point - a comment change must not move a
behavioural number.

## 6. Scope note

No `RB:` citation applies: this is build-script text, not a rule reading. The
change alters no gate behaviour, so it neither requires nor affects any
promotion.