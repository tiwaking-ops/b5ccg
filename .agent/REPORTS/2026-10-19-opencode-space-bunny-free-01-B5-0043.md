---
document:
  title: "B5-0043 - Rulebook pin reproducibility: re-pin to canonical LF bytes"
  status: "Report. Records work performed under a claim; carries no authority beyond the DECISIONS entry it produced (AGENTS.md section 3)."
  task: "B5-0043"
  scope: "engine,tooling"
author: "opencode (Space Bunny Free)"
agent_id: "opencode-space-bunny-free-01"
provenance:
  author_llm: {name: "opencode (Space Bunny Free)", version: "space-bunny-free-2026-10-19-01"}
  created_date: "2026-10-19"
  last_modified_date: "2026-10-19"
  last_modified_by_llm: {name: "opencode (Space Bunny Free)", version: "space-bunny-free-2026-10-19-01"}
  assessor_llm: none
  no_self_assessment: true
  javac_actually_recorded: "javac 25.0.4.1 (staged JDK at b5ccg/scratch/jdk25-win/jdk-25.0.4.1+1; the JDK 8 on the default PATH is a stop condition per AGENTS.md section 2 and was not used)"
---

# B5-0043 - the gate was green on one machine only

## The defect

The pin matched a checkout artefact of this machine, not the file.

| Form | Bytes | SHA-256 | Matched pin? |
|---|---|---|---|
| Working tree | 134,586 | `1c99c44b...716d0` | yes |
| Committed blob | 134,571 | `cc765af4...3eab7` | no |
| Fresh clone | 135,807 | `165d11dd...483250` | no |

CRLF sat on front-matter lines 1-14 and the last line 1236, produced by
`core.autocrlf=true` with no `.gitattributes` anywhere. `git status` read clean
either way. `HeadlessRulebookPinProbe` is a gate class, so 31/31 was producible
only here, and `build-rule-index.ps1 -Check` exited -1 on a clone with
`GUARD FAILURE`.

## The change

1. Working tree rulebook canonicalised to LF (134,571 B, `cc765af4...`).
2. `RuleSetProfile.RULEBOOK_SHA256` / `RULEBOOK_BYTES` (134586L to 134571L).
   The source comments said "pinned working-tree bytes", which named the defect.
3. `build-rule-index.ps1` `$pinSha` - it holds its own copy of the pin.
4. `.gitattributes`, one file only. Repo-wide `eol=lf` would rewrite the CRLF
   card decks and ledger, which are out of scope.

Commit `dcf470e` on the task branch. Not pushed, per D3.

## Verified, not assumed

* Fresh clone with `core.autocrlf=true`: 134,571 B, zero CRLF, `cc765af4...`.
* `HeadlessRulebookPinProbe` in that clone: **PASS 21/21**.
* `build-rule-index.ps1 -Check` in that clone: SHA guard passes.
* Full gate here: **exit 0, 31/31**, 189 sources, `javac 25.0.4.1`.

## Two findings worth keeping

**The rulebook never entered the commit.** After canonicalisation the file was
byte-identical to the HEAD blob and git's clean filter produced `5798ef4d...`,
exactly the committed blob. The commit has three files, not four. The pin was
describing a checkout artefact; no content was ever wrong.

**No citation was invalidated.** Regenerating the index differed on exactly one
line, the recorded hash. 87 rows, 84 rules, 6 chapters byte-identical, so every
`RB:` line range still resolves to the same line.

## Errors of mine on this row

* The claim was first written with `scope: ["engine,tooling"]` - one string, not
  two. `powershell -File` does not comma-split a `[string[]]`; in-process
  invocation with a real array does. Fixed before any work depended on it.
* Two probe runs appeared to fail 9/12. That was my harness: I compiled to the
  wrong directory and ran from the repo root, where `resources/cards/` does not
  resolve. `compile.sh` does `cd "$(dirname "$0")"`. **A 9/12 that turns out to be
  my own setup error is exactly the kind of result that gets reported as a
  regression if not checked.**
* `javac -version 2>&1` returns an array when `JAVA_TOOL_OPTIONS` prints to
  stderr, so `-replace` on it silently yields an array and `[string]$Javac` fails
  to bind. Coerce with `Select-Object -Last 1` first.

## Scope boundary

`build-rule-index.ps1 -Check` could not exit 0 under this row: its final
comparison is against `docs/RULE_INDEX.md`, which is a `docs` write and outside an
`engine,tooling` claim. The row was split into B5-0047 rather than the claim
widened. Both are now DONE and every acceptance line passes.

Evidence: `.agent/REPORTS/evidence/GATE-B5-0043.txt`.