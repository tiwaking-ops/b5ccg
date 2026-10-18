---
document:
  title: "B5-0036 DONE - DAMAGE_ON_ATTACK dead literals removed"
  status: "Report. Proposes a change for owner verification; confers no authority on its own (AGENTS.md section 4)."
provenance:
  author_llm: {name: "opencode (Space Bunny Free)", version: "space-bunny-free"}
  created_date: "2026-10-19"
  last_modified_date: "2026-10-19"
  last_modified_by_llm: {name: "opencode (Space Bunny Free)", version: "space-bunny-free"}
  assessor_llm: none
  no_self_assessment: true
  basis: "Every number below was measured in this session on this tree. The 'before' figures are quoted from the preserved run at .agent/REPORTS/evidence/GATE-FINAL.txt; the 'after' figures were produced by the run recorded in section 5."
---

# B5-0036 - DAMAGE_ON_ATTACK dead literals

Scope `engine`. One file: `b5ccg/src/b5ccg/engine/CardEffects.java`. No other
source, no card JSON, no census file, no probe.

## 1. What was wrong

`DAMAGE_ON_ATTACK` held seven entries. Its javadoc claimed two of them were the
shipped data:

> The two pool ids (en_h_mines / en_h_energy_mines) are the shipped data

Neither string occurs anywhere in `b5ccg/resources`. The real ids are the
`enh_`/`de_enh_` forms listed alongside them. So the registry carried two dead
literals, and the comment described them backwards.

Two gate classes failed on this, and had been failing invisibly until the gate
was made to run every class (see the triage report, section 1):

```
[B5-0003] every behaviour literal is an exact pool id: FAIL -- 2 dangling: en_h_energy_mines | en_h_mines
[B5-0003] no unexplained non-canonical literal remains: FAIL -- 2: de_enh_energy_mines | de_enh_mines
[B5-0005] every live behaviour literal names a pool title: FAIL -- 2: [en_h_energy_mines, en_h_mines]
```

## 2. The change

Removed four entries:

| Removed | Why |
|---|---|
| `en_h_mines` | names no pool title; dead |
| `en_h_energy_mines` | names no pool title; dead |
| `de_enh_mines` | redundant twin - `lookupSet` canonicalises `de_` away |
| `de_enh_energy_mines` | redundant twin - same |

Kept `enh_mines`, `enh_energy_mines`, and the `enh_mines_rt` fixture.

The javadoc was rewritten to state what is actually true, including that the
javadoc previously asserted the opposite.

## 3. Why this is behaviour-preserving

The risk was `HeadlessConformanceTest`, which asserts the removed Deluxe id
still resolves:

```java
check("MINES", "energy_mines id registered for reactive damage",
    CardEffects.damageOnAttack("enh_energy_mines")
        && CardEffects.damageOnAttack("de_enh_energy_mines"));
```

`lookupSet` (`CardEffects.java:1715`) matches exact-first and then falls back to
`CardIdNamespaceGuard.canonicalKey(id)`, which strips the `de_` prefix. So
`damageOnAttack("de_enh_energy_mines")` still resolves through
`enh_energy_mines`. That was read in the source before the edit, not assumed,
and then confirmed by running the suite.

Removing the two dead ids removes behaviour that could never have fired: nothing
in the tree passes `en_h_mines` or `en_h_energy_mines`. The only caller,
`HeadlessConformanceTest:1918`, uses the `enh_`/`de_enh_` spellings.

## 4. Fails without the change

Recorded before the edit in `.agent/REPORTS/evidence/GATE-FINAL.txt`:
`HeadlessCanonicalParityProbe` 15/17 with exactly the two dangling literals, and
`EffectCensusChecker` 24/25 naming the same two. Those are the failing probes
this change turns green, so the mutation is demonstrated by the preserved run
rather than by a fresh synthetic revert.

## 5. After

```
HeadlessCanonicalParityProbe: 17/17 checks passed   EXIT=0   (was 15/17)
EffectCensusChecker:           25/25 checks passed   EXIT=0   (was 24/25)
HeadlessConformanceTest:       904 checks PASSED    EXIT=0   (unchanged)
  [MINES] reactive mines adds +1 return damage (fleet): PASS
  [MINES] defender fleet survives reactive mines (no severe): PASS
  B5-0528 mines reactive covered (MINES)
```

`compile.sh` exit 0, `--release 8` under the staged JDK 25.

Two of the four red gate classes are cleared. The full-gate verdict after this
change and after B5-0037 is in the companion run recorded in B5-0037's report.

## 6. Notes

`append-ledger.ps1` cannot place a row in the ledger's mid-file table - it
appends at EOF, where the table has already closed. Both new rows therefore had
to be moved into the table by hand after the tool verified them, and
`lint-ledger.ps1` caught the misplacement each time. That is a real tooling gap
worth a `tooling/` row: the sanctioned writer and the shipped lint disagree
about what a legal row looks like.

Scope note: B5-0025's freeze window holds `docs,tooling`. This row is `engine`
only, so there is no scope overlap; it was opened at the owner's direction
while the freeze is still formally in force.