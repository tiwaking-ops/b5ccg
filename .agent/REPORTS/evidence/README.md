---
document:
  title: "Raw gate logs preserved from the 2026-10-19 triage, with a validity note per run"
  status: "Evidence. No authority (AGENTS.md section 3). Raw tool output, not a report."
provenance:
  author_llm: {name: "opencode (Space Bunny Free)", version: "space-bunny-free"}
  created_date: "2026-10-19"
  last_modified_date: "2026-10-19"
  last_modified_by_llm: {name: "opencode (Space Bunny Free)", version: "space-bunny-free"}
  assessor_llm: none
  no_self_assessment: true
---

# Gate evidence, 2026-10-19

Raw captures moved here from the gitignored `.agent/tmp_evidence/` so the
verification task spec can cite them from a clone. `.gitignore` excludes
`*.log`, so every file is renamed to `.txt`; the mapping is one-to-one and only
the extension differs.

**Read this before quoting any run.** Only three of these runs are valid, and two
of them were produced while a second session was concurrently building in the
same tree. The reports, not this directory, carry the findings.

| File | Verdict | Why |
|---|---|---|
| `GATE-FINAL.txt` | **VALID - verdict of record** | Sole session, final tree, every fix in place. 27 pass / 4 fail of 31, 242135ms |
| `GATE-TRIAGE3.txt` | VALID on verdicts, bad summary line | Sole session. Verdicts correct; its own summary said `26 passed, 4 failed`, which sums to 30. See triage report 4.5 |
| `GATE-TRIAGE.txt` | VALID | Sole session, before the dead-class-name fix. 26 pass / 5 fail of 31 - the fifth being `HeadlessFactionStateProbe` |
| `GATE-TRIAGE2.txt` | INVALID | Aborted: build lock refused it while a build held `out/` |
| `GATE-TRIAGE4.txt` | INVALID | `compile.sh` was rewritten by another session while bash was executing it. The disputed run |
| `GATE-TRIAGE5.txt` | INVALID | A concurrent build ran `rm -rf out/` during the last three gate classes; they died `ClassNotFoundException` |
| `gate_out.txt`, `gate2_out.txt` | prior session | Evidence for the original "gate is unverified" findings: human-seat probe failing 1 of 24, and the S3 join-timing failure |
| `jt2_out.txt`, `dr_out.txt`, `hs_out.txt`, `probe_out.txt`, `rem_out.txt` | prior session | Per-probe captures from the earlier session's manual runs |
| `hs_err.txt` | prior session | JVM fatal-error dump from the `NoClassDefFoundError: WarKind` crash the proposal describes as F1 |
| `repro_table.txt` | reconstructed from run 4 | All 31 run-4 console rows with run-4's exact timings. **Load-bearing** - see the correction below |
| `r3.txt` | reconstructed from run 3 | The same for run 3; used to diagnose the row-count anomaly |

Excluded on purpose: `gate_err.txt` (1.8 MB), `gate2_err.txt` (1.8 MB) and
`dr_err.txt` (1.3 MB) from the prior session. They are stderr duplicates of
captures already held here and were the bulk of the 6.5 MB that was sitting in
`tmp_evidence/`. They remain on disk in `.agent/tmp_evidence/` if anyone wants
them; they are simply not worth carrying in git.

**Correction to this file's first version.** It originally said `repro_table.txt`
and `r3.txt` were throwaway reconstructions not worth preserving, and they were
deliberately excluded. That was wrong. `repro_table.txt` is load-bearing: it is
the surviving record that run 4's **console** carried all 31 rows, which is the
fact that rules out the loop having stopped after four classes. Both are now
preserved. The independent adjudication at
`2026-10-19-opencode-big-pickle-01-gate-run4-adjudication.md` relies on
`repro_table.txt` for exactly this.