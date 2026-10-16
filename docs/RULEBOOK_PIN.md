---
document:
  title: "Rulebook pin — canonical bytes, line-count reconciliation, known damage"
  status: "Provenance record"
provenance:
  author_llm: {name: "Muse Spark", version: "muse-spark-1.3-contributor-free"}
  created_date: "2026-10-16"
  task: "B5-0002"
---

# RULEBOOK_PIN — `BABYLON5_CCG_RULEBOOK.md`

Enforced by `b5ccg.engine.HeadlessRulebookPinProbe` (exit code is the
contract). Re-measure, never hand-edit: the numbers below are outputs of
`Get-FileHash` and byte counts, reproduced independently by the probe.

## Pin (working-tree bytes)

* SHA-256 `1c99c44b7b64c8927f1e952a22a49d5886788da2d08e211092b5558e154716d0`
* 134,586 bytes; UTF-8 with BOM (`EF BB BF` — the BOM is part of the
  pinned bytes, the probe asserts its presence).
* Rulebook version 1.3 (consolidated: base + Great War + Psi Corps +
  Severed Dreams + Wheel of Fire).

## 689 vs 1,236 — reconciled

Both numbers describe the **same bytes**. Measured 2026-10-16:

* **1,236** = total LF-terminated lines (`bytes.count(b'\n')` = 1,236;
  `splitlines` = 1,236). This is what Claude's copy counted.
* **689** = **non-blank** lines (1,236 total − 547 blank). This is what the
  earlier report counted.

Correction to the fork-time note (AGENTS.md §2, DECISIONS.md genesis
entry): 689 was labelled a "CRLF count". It is not — the file holds only
15 CRLF line endings (front-matter lines 1–13 and the last line 1236).
689 is the non-blank count. Same bytes, different denominators; the probe
asserts both (1,236 total, 689 non-blank).

## Git-blob note (do not "fix")

The committed blob is 134,571 bytes of pure LF; the working tree is
134,586 bytes with 15 CRLF lines (`core.autocrlf=true` normalises on
commit). `git status` reads clean either way. **The pin is on the
working-tree bytes** — the exact bytes Claude hashed. A commit that
"normalises" the working tree to the blob would break the pin; update the
pin deliberately (new hash + probe constants + this note) or not at all.

## Known encoding damage (canonical file stays untouched)

Three lines carry `U+00C2 U+00A0` mojibake (`[emailÂ protected]` —
Cloudflare email-decode artefact): **1228, 1230, 1236**. All other
non-ASCII lines are legitimate UTF-8 (em-dashes in the header, en-dashes
in the League summary 912–916). Per A9 the damage is fixed only in a
derived copy (WP-18 rule-ID index), never here. The probe fails on any
deviation from exactly these three lines — a fix, a re-scrape, or new
damage all surface as a red gate, not a silent drift.

Claude estimated "five lines"; the measured count is three. Evidence
wins: the probe encodes three.

## Card JSON (probe scope, not pinned)

`premiere.json` (186,498 bytes, CRLF), `deluxe.json` (169,630 bytes,
CRLF), `index.json` (838 bytes, LF): strict-UTF-8 clean, no `U+00C2`, no
`U+FFFD`. Sizes and line counts are deliberately NOT pinned — card data
grows under later packages (A1b adds a key field) and a size pin here
would turn every data task red for no reason.
