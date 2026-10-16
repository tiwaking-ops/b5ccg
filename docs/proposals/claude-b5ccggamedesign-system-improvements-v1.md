---
document:
  title: "Babylon 5 CCG — System Improvement Proposal (Game Engine and Development Process)"
  status: "Candidate proposal. No authority. Supersedes nothing."
  purpose: "Independent assessment of the 2026-10-16 current-state technical design report, with ranked, concrete recommendations for the game (System A) and the autonomous development process (System B), for the owner to decide on and then hand to the agent fleet."
author: "Claude"
version: "v1"
document_version: "1.0"
author_llm: {name: "Claude", version: "Claude Sonnet 5.5 (claude-sonnet-5-5)"}
provenance:
  created_date: "2026-10-16"
  last_modified_by_llm: {name: "Claude", version: "Claude Sonnet 5.5 (claude-sonnet-5-5)"}
  last_modified_date: "2026-10-16"
  inputs:
    - "2026-10-16-current-state-technical-design-report.md (author_llm opencode / space-bunny-free)"
    - "BABYLON5_CCG_RULEBOOK.md (copy supplied to me: 1,236 lines, 134,586 bytes, sha256 1c99c44b7b64c8927f1e952a22a49d5886788da2d08e211092b5558e154716d0)"
  method: "Document review plus external prior-art research. I had no access to the repository, the card JSON, AGENTS.md, the ledger or any source file. Every structural statement about the code is the report's, not mine. Evidence tags used throughout: [V] verified by me against the two supplied documents; [R] taken from external sources I consulted (listed in Appendix B); [I] my inference, not verified."
placement_note: "Governance in the report says no new .md files at the repo root. This file belongs in docs/proposals/ (candidate tier). Nothing here has been applied to the repository."
---

# Babylon 5 CCG — System Improvement Proposal

**Author:** Claude · **Version:** v1 (1.0) · **Date:** 2026-10-16 · **Status:** Candidate. It becomes tasks only after the owner approves it.

---

## 0. How to read this document

* Section 1 is the whole proposal on two pages. If you read nothing else, read it.
* Sections 3–4 say where I agree and disagree with the report, and the one scope finding I think matters most.
* Sections 5–7 hold the recommendations (System A, toolchain, System B). Each states what it touches, what it preserves, what it risks and how you would know it worked.
* Sections 8–9 give the sequencing, the A-versus-B call and the constraint ledger. Section 11 is the "do not do yet" list.
* Section 13 is the agent handoff: small work packages with acceptance checks.
* Per the report's own rules: no code anywhere (shapes only), no UI recommendations, and nothing here bypasses the authority model. Where a recommendation touches governance (commit policy, the Java rule), I say so and apply nothing.

---

## 1. Executive summary

### 1.1 Six headline judgments

**J1. The binding problem is aim, not capacity.** The fleet closed 944 tasks, yet card behaviour sits at 12% of records, the god classes are intact, and a silent id-namespace defect (R2) went unnoticed until a one-off report found it [V, report §12–§13]. The process measures its own activity (ledger rows, probes, patterns) rather than product progress. Define one product-level target and steer by it (section 8.3, "Playable Slice").

**J2. The rule surface is bigger than the card pool, and the report under-states this.** The rulebook is a consolidated v1.3 covering the base game plus the Great War, Psi Corps, Severed Dreams and Wheel of Fire material. The rulebook itself names Shadows, Great War and Psi Corps as separate expansions, each with "hundreds of new cards" (RB L136) [V]. The data holds 446 base titles in two printings and, per the report, zero Deluxe-only titles and zero contingency cards [V, report §6]. So much of what the report calls "unrepresentable Deluxe rules" cannot be exercised by any card in the pool. Build rules in the order the pool can exercise them (section 4).

**J3. Card behaviour must become data, but measure first, and fix identity before anything else.** R5 is the deepest architectural finding and I agree with it. Two preconditions make the migration cheap instead of risky: a one-day canonical-id fix that collapses the two-set split (R2), and a short "effect census" that sizes the real problem before a schema is frozen. The report's 77% "unimplemented" figure is probably overstated, because it counts vanilla cards in the denominator (section 5, A2).

**J4. Drop the Java 6 pin, stay on the JVM, do not rewrite.** The pin has real, quantifiable costs: the 128-comparison `if` chain in `CardEffects` is partly a language consequence, since string `switch` arrived in Java 7 [I]. It also forces an entire policing subsystem (the census, the governance essay in AGENTS.md §2a). The report's claim that JDK 8u292 is the only toolchain accepting `-source 6` is wrong: JDK 8 through 11 all accept it and JDK 12 removed it [R]. Move to JDK 25 LTS in staged hops. A rewrite in another language would throw away the two best assets, the 57k LOC and the 498 KB conformance suite, and would fix none of the real problems (section 6).

**J5. Make silent failure loud.** "A green build is not evidence that a rule works" (report §0.1) is the correct diagnosis, but the project has no instrument that turns it into a number. Three cheap instruments do it: an unimplemented-text tripwire, a rulebook-invariant checker run after every action, and seeded AI-versus-AI fuzzing that reports which engine paths are actually reachable. All three depend on deterministic seeding, which the report says is currently missing in `Deck` shuffling (section 5, A4–A5).

**J6. System B is currently the larger multiplier. Reset it briefly, then point it at System A.** About 5.1 process documents are produced per ledger row (5,629 documents against 1,113 rows [V, arithmetic on report §15]). External evidence says extra context files tend to raise agent cost by more than 20% without improving success [R]. Do a time-boxed reset (about one week) and then send the fleet at the work packages in section 13 (section 7 and section 8).

### 1.2 Ranked recommendation set

Effort key: **S** ≈ under a day of human-equivalent work; **M** ≈ 2–5 days; **L** ≈ 1–3 weeks. Ranked by leverage against "a playable, rule-accurate Deluxe edition," not by ease. "Unlocks" marks items that enable other work rather than being merely valuable.

| Rank | ID | Recommendation | System | Effort | Unlocks | Tier |
|---|---|---|---|---|---|---|
| 1 | B0 | Snapshot and back up the working tree without giving agents commit power (R1) | B | S | everything (protects it) | 0 |
| 2 | A9 | Pin the rulebook by hash, define a rule-set profile, record the first interpretations | A | S | A8, traceability | 0 |
| 3 | A1a | Canonicalise card identity at the comparison boundary and triage the 58 asymmetric titles (R2) | A | S–M | A1b, A3 | 0 |
| 4 | B1 | Re-aim the fleet: Playable Slice target, planner-authored backlog, BLOCKED triage | B | M | all fleet throughput | 1 |
| 5 | B2 | Cut the process tax (patterns, boot file, ledger rotation, provenance scope) | B | M | token cost, agent accuracy | 1 |
| 6 | A2 | Effect census: classify all 446 effective cards before designing the schema | A | S–M | A3, sizing of R4/R5 | 1 |
| 7 | T1 | Toolchain: JDK 25 LTS in staged hops; retire the Java 6 census | A | M | A3 ergonomics, tooling | 1 |
| 8 | A5 | Determinism: injected seeded RNG, state hash, double-run probe | A | S–M | A4, A6, A8 | 1 |
| 9 | A3 | Data-driven effect system v0, piloted on Groups then Enhancements (R5, R4) | A | L | R4, effective coverage | 2 |
| 10 | A4 | Fail-loud instruments: unimplemented-text tripwire, invariants, fuzz and reachability | A | M | trustworthy "green" | 2 |
| 11 | A6 | Engine decomposition behind delegates: Victory, Marks, Cost, then Influence, Conflict (R6, R9) | A | L | testability, agent context size | 2 |
| 12 | A8 | Rule-ID traceability and mechanical split of the 498 KB conformance suite (R8) | A | M | per-section coverage | 3 |
| 13 | A7 | Race/Faction split; faction features staged and conditional on owner decision D1 (R3) | A | M→L | multi-faction rules | 3 |
| 14 | B3–B5 | Evaluate transactional ledger and worktrees; fleet shape; hygiene batch | B | S–M | marginal | 3 |
| 15 | A10 | AI scores effect primitives instead of card ids | A | L | AI parity for new cards | 4 |

### 1.3 System A or System B first?

**B first, narrowly, in parallel with three tiny A items, then A.** Reasoning:

1. The Tier-0 items (B0, A9, A1a) cost days and unblock nothing else, so they run immediately and concurrently.
2. The fleet's current operating mode (grinding a historical backlog with one OPEN row, and writing about five process documents per task) would consume the budget of any A work. A one-week, time-boxed reset (B1, B2) is the cheapest way to make the next A weeks productive.
3. After that, System A is where all remaining effort goes. B is allowed to be "done enough" once its exit metrics in section 7.6 are hit; it must not become the product.

A defensible dissent: if you judge the fleet's throughput acceptable and cost not a concern, run A2 and A1a first and skip B2. I would still do B0 and B1.

### 1.4 Constraints at a glance (full ledger in section 9)

| Constraint | Verdict |
|---|---|
| Java 6 source level | **Change.** JDK 25 LTS, staged, no mass rewrite. |
| JDK 1.8.0_292 pin | **Change** (follows from the above). |
| JDK stdlib only, no external libraries | **Keep for the shipped game.** Allow approved test/tooling-scope dependencies later; not now. |
| Rulebook canonical, never edited | **Keep.** Add hash pinning and a machine-readable rule-ID index. |
| No commit, no push (agents) | **Keep for agents.** Add non-agent snapshot commits; consider task branches later (needs D3). |
| Two-set data model | **Keep storage, change semantics.** One effective pool keyed by canonical card identity. |
| Autonomous multi-agent fleet | **Keep, but smaller, aimed, and cheaper.** |
| Swing client | Not assessed (out of scope). Keep the `GameAction` boundary clean. |
| No new .md at repo root | **Keep.** Put this file in `docs/proposals/`. |

### 1.5 Decisions I need from you

| # | Decision | My recommendation |
|---|---|---|
| D1 | Product intent: single human versus AIs of different races, or same-race/multi-human games too? | If single human versus AI, treat the faction work (A7) as optional after the rename. |
| D2 | Approve JDK 25 LTS and "no rewrite." | Approve. |
| D3 | Approve a non-agent snapshot job, and decide whether agents may commit on private task branches later. | Approve the snapshot job now; defer task branches. |
| D4 | Approve test/tooling-scope dependencies in principle (not now). | Approve in principle. |
| D5 | Which printing's text governs a title that exists in both sets? | The Deluxe text, since the rulebook says errata were absorbed into it (RB L1068). Record it as an interpretation. |

---

## 2. Evidence base and confidence

**What I read:** the whole report (679 lines) and the whole rulebook, including all Deluxe-era sections (RB L874–L1135) and the glossary (RB L1136–L1215).

**What I researched** (Appendix B has the sources): the Forge and MetaStone/Spellsource designs for data-driven card engines; the OpenJDK record on `-source 6` removal and current Java LTS status; the "Why Do Multi-Agent LLM Systems Fail?" study (MAST); the ETH Zurich study on AGENTS.md-style context files; and Java's data-oriented-programming features (records, sealed types, pattern matching).

**What I could not see:** any source file, the card JSON, `AGENTS.md`, the ledger, `DECISIONS.md`, the probes. The consequences:

* Every claim about code structure is the report's. Where I build on one, I tag it [V] only if the rulebook or arithmetic confirms it, and otherwise cite the report.
* I have not seen a single card's text. Section 5 (A2) therefore proposes a measurement rather than a schema I cannot justify. The schema shape in A3 is derived from the rulebook's rules, not from card text, and is a hypothesis for the census to test.
* Effort sizes are rough. I do not know the fleet's real throughput. The report implies about 35 tasks per day, which I treat as an upper bound.

**Confidence by area:**

| Area | Confidence | Why |
|---|---|---|
| Scope mismatch (J2) | High | The rulebook states it directly. |
| Canonical-id fix (A1) | High on the direction, medium on exposure | Exposure depends on whether both printings can co-occur (open question). |
| Toolchain verdict (T1) | High on direction, medium on migration risk | I have not seen the code; the risk is measurable in a day. |
| Effect-system shape (A3) | Medium | Derived from rules, not cards. The census can overturn parts of it. |
| System B diagnosis | Medium | The studies I cite come from other settings; the ledger statistics are the report's. |
| Sizing and sequencing | Low–medium | I do not know the fleet's capacity. |

---

## 3. Independent assessment of the report

### 3.1 The risk register, re-rated

| # | Report severity | My rating | Verdict and reasoning |
|---|---|---|---|
| R1 | Critical | **Critical** | Agree. It is also the cheapest fix on the list. The problem is backup, not version-control philosophy; both can be satisfied (B0). |
| R2 | Critical, silent | **High as a present defect; Critical as a recurrence mechanism** | The report calls the exposure real "either way," but it is conditional on whether both printings can coexist in a deck or pool (the report's own open question 2). It is also bounded: 58 of the 82 special-cased titles are single-literal [V, arithmetic: 49 de_ + 57 bare − 24 paired = 82 titles]. What makes it Critical is that every future card behaviour must be written twice and nothing checks it. |
| R3 | High | **Medium–High, and re-scoped** | One missing concept, yes. But the rulebook marks alternate factions as optional (RB L920, L1018), Drakh and ISA need cards that are not in the pool, and the report mis-states the Civil War trigger (3.2). Priority depends on D1. |
| R4 | High | **High, but the metric is wrong** | "~77% unimplemented" counts vanilla characters and fleets in the denominator. The true size of the problem is unknown until the effect census (A2). |
| R5 | High | **High (deepest finding)** | Agree. See A3. |
| R6 | High | **Medium–High** | The engine works and has about 30 probes. The god classes hurt change cost and, more importantly, agent context: a 167 KB class cannot be read by an agent in one pass. It is an enabler to fix incrementally, not a blocker. |
| R7 | Medium | **Low; likely correct data** | The rulebook says agendas are sponsored by rotating a character, with no influence cost named, and cites "a sponsored agenda" as an example of a card with undefined cost (RB L524, L1064) [V]. The real obligation is that the engine must treat absent cost as UNDEFINED, never zero (A6, CostRules). |
| R8 | Medium | **Medium–High** | Raised: per-section traceability is what lets an agent run only the tests relevant to its change, rather than a 498 KB monolith. |
| R9 | Medium | **Medium; cheap fix** | Confirmed by RB L1048: more than one player can score a victory simultaneously. Fold into A6. |
| R10 | Medium | **High (System B)** | It multiplies the cost of every task. See B2. |
| R11 | Medium | **Medium; a symptom** | 150 BLOCKED rows are best explained by a handful of root causes. Triage in one batch (B1). |
| R12 | Low–Medium | **Low; probably intentional** | The rulebook says contingency cards were introduced by the Great War expansion (RB L538). They are not in the pool because the pool predates them [V against rulebook; I that the pool predates them]. Use test fixtures, not product data. |
| R13–R15 | Low | **Low** | Batch into B5. |

### 3.2 Corrections and additions to the report's facts

| # | Item | Evidence | Consequence |
|---|---|---|---|
| C1 | "JDK 1.8.0_292 is the only toolchain still accepting `-source 6`" | JDK 12 removed `-source/-target/--release 6`; the removal was deferred from JDK 11, so JDK 9–11 still accept it [R, OpenJDK JDK-8206877 and jdk-dev thread]. | Not the only option. Also, `--release 6` on JDK 9–11 checks against the Java 6 API [I, from the same thread], which would have covered the API-blindness gap the census exists for. Moot if T1 is adopted. |
| C2 | "`CardEffects` uses zero `switch` statements" presented as a style finding | String `switch` requires Java 7 [I, general Java knowledge]. At source level 6, 128 id comparisons can only be `if` chains. | Part of the "defect" is a language consequence. A3 removes the pattern anyway. |
| C3 | Civil War "trigger condition: same-race tension reaching 5 at the end of the turn" | RB L996: a faction at tension 5 "could play a Declaration of War," which then puts every faction of the race into Civil War. Any other effect that triggers a same-race war also starts it [V]. | It is an optional, card-mediated play with a tension precondition, not an automatic trigger. Model it as a precondition on a card. |
| C4 | Glossary "~50 defined terms" | The glossary has 37 entries [V, count]. | The data-driven mapping in A3 is smaller than feared. |
| C5 | Rulebook "689 lines" | The copy I received has 1,236 lines and 134,586 bytes [V]. | Either a different copy or a different counting method. Pin by hash (A9). The copy also contains five lines of encoding damage (for example "â€“") and a BOM [V]. Harmless to humans, a hazard to any parser. |
| C6 | "Deluxe-only rules" framing | The rulebook uses "Deluxe" for the product edition (RB L136) and for the errata-absorbing card revision (RB L1068). League, alternate factions, contingencies, Psi Corps, Drakh and ISA come from expansions (RB L876, L920, L538, L1026, L1094, L1112) [V]. | See section 4. |
| C7 | Possible internal tension in the rulebook | RB L878 says the v1.2 "second starting character" rule for the League was changed; RB L890 and L913 still give a second Species Ambassador in the starting hand [V]. | Likely consistent (second ambassador in hand, not in play). Record it as an interpretation and confirm the engine places it in hand (A9). |
| C8 | Cost data vs rulebook examples | RB L1060 and L1064 name three enhancements and one fleet (Shadow Tech Upgrade, Recalled, Forced Impairment, First United Fleet) as having no printed cost. The report says 100% of fleets and enhancements carry a cost [V]. | Either those titles are not in the pool (likely, as they look like expansion cards) or the data fills in a cost the card does not print. One query settles it (Appendix C, M3). |
| C9 | Process-ID gap | Highest id B5-4249 against 1,113 ledger rows: about 26% of issued ids have a row [V, arithmetic]. | Unexplained. Possibly claims that expired or were abandoned. Worth one question to whoever built the claim system (Q7). |

---

## 4. The scope finding: rule surface versus card pool

### 4.1 What "Deluxe" means in the rulebook [V]

* It is a **product edition**: 60-card race starter decks, and boosters of eight cards (RB L134–L136).
* It is also a **card revision**: "All previous errata have been absorbed into the Deluxe revision of the cards" (RB L1068).
* The expansions are **separate products**: Shadows, Great War and Psi Corps, "each feature[ing] hundreds of new cards" (RB L136), plus the Severed Dreams and Wheel of Fire rule additions (RB L1042, L1090).
* Rulebook v1.3 is a consolidated rules document that covers all of them.

### 4.2 What the data holds [V, report §6]

446 unique titles in 829 records. All 383 Deluxe records are revisions of existing Premiere titles; none is new. There are no contingency records. The report does not give a per-faction card count, so I do not know how many records are League cards (Appendix C, M2).

### 4.3 Rule families against the pool

| Rule family | Rulebook source | Cards needed that the pool lacks | Exercisable today |
|---|---|---|---|
| Rounds, conflicts, victory, marks, states, mercenaries | Base (RB III, V, VI) | none | **Yes** |
| League of Non-Aligned Worlds | Great War (L876) | unknown (M2) | Partly [I] |
| Alternate factions, dual races, Civil War, joint effects, optional Power and Victory | Great War (L920–L1020) | Home-faction ambassador cards; but "More Factions" (L1024) lets any character be designated an alternate ambassador | **Yes, with designated ambassadors** |
| Contingencies | Great War (L538) | all of them | Only with test fixtures |
| Psi Corps faction, Conspiracy-Mark sponsoring | Psi Corps (L1026) | Psi Corps cards | No |
| Reducing power needed to win; simultaneous victory | Severed Dreams (L1046) | the cards that reduce power [I] | The rule yes, the cards no |
| Drakh, Chaos Tokens, ISA | Wheel of Fire (L1094, L1112) | all | No |

### 4.4 Consequences

1. **A scope ladder replaces "implement Deluxe."** Each rung is independently shippable and testable:
   * **S1** Base rules complete over the 446-title effective pool.
   * **S2** League and the Deluxe text.
   * **S3** Multi-faction rules (same-race tension, non-aggression, voting exclusion, joint effects, Civil War, optional Power and Victory), tested with designated ambassadors.
   * **S4** Expansion-dependent rules (Psi Corps, contingencies), only if card data is imported.
   * **S5** Wheel of Fire (Drakh, ISA), only with cards.
2. **Do not build S4–S5 mechanics speculatively.** Each is a permanent "wired but not working" surface, which is exactly the hazard the report's §0.1 warns about, and nothing in the pool can exercise it.
3. **Coverage must be measured against the effective pool of 446 titles, restricted to behaviour-bearing cards.** Records and vanilla cards are the wrong denominator (A1, A2).
4. Importing expansion card data would be a separate data-acquisition project with its own questions. I have not assessed sources for it and make no recommendation.

---

## 5. System A recommendations

Each recommendation follows the same shape: why, what, **touches**, **preserves**, **risks**, **verified by**.

### A1. Canonical card identity (addresses R2)

**Why.** 383 titles exist twice, under a bare id and a `de_` id. `CardEffects` special-cases 82 distinct titles via 106 literals (49 `de_`, 57 bare): 24 titles are written both ways and 58 only one way (33 bare-only, 25 `de_`-only) [V, arithmetic on report §8.3]. A behaviour written for one printing silently does nothing for the other, with no build or test failure. Every future card behaviour has the same doubling hazard.

**A1a: the one-day fix (Tier 0).**

* Add one function that maps any card to a **canonical key** by removing the set prefix that `cards/index.json` already registers for that card's set. That file is already the enforced contract (`CardIdNamespaceGuard`).
* Route every id comparison in `CardEffects` through it. Literals become one per title.
* **Triage the 58 single-literal titles.** For each, compare the Premiere and Deluxe text. If the text is unchanged, the canonical key is the complete fix. If it changed (the base rate is 154 of 383, about 40%), the Deluxe text governs under D5; implement the Deluxe behaviour for both printings and record the choice as an interpretation, not as a rule change.
* Add a **parity probe** (exit 0/1) that fails if any behaviour reference does not resolve to exactly one pool entry, or if two pool entries share a canonical key without being declared printings of each other.

**A1b: the structural fix (inside A3).**

* Introduce `cardKey` in the data as the stable identity of a title, with the two records as its *printings*. Behaviour attaches to the key. A per-printing override exists only where text and behaviour genuinely differ.
* The loader builds **one effective pool**: Deluxe text overrides Premiere text per key, and the 63 Premiere-only titles fall through. Decks reference keys (with an optional printing preference). Both ids remain resolvable through an alias map so existing decks and replays keep loading.
* This answers the report's open question 2 by construction: a pool cannot contain "both records of one title" because the loader never produces two.

**Touches:** `CardEffects`, `DeckLoader`, `StarterDeckBuilder`, `CardIdNamespaceGuard`, the two data files (one generated field added). **Preserves:** the `index.json` namespace contract, existing ids in decks and replays, the existing probes. **Risks:** a title whose errata changed its behaviour could be unified wrongly. Mitigate with the triage above plus golden-master scenarios captured *before* the change (A5 helps). **Verified by:** parity probe passes; each of the 58 triaged titles has a scenario test that passes under both ids; coverage reports over 446 titles.

### A2. Effect census: measure before designing the schema (Tier 1)

**Why.** The report's 12% and 77% figures count records and include vanilla cards. The real denominator is the set of behaviour-bearing cards, and its size decides whether A3 is a three-week or a three-month project. This is the same "measure it, do not infer it" discipline the project already prizes.

**What.**

1. Classify every one of the 446 effective cards with a closed taxonomy. My proposed classes: *vanilla (stats only)*, *trait-only*, *stat modifier*, *one-shot event effect*, *conflict-rule variant*, *triggered or conditional*, *restriction or permission*, *cost modifier*, *mark manipulation*, *state/tension/unrest change*, *vote/council*, *agenda win-condition*, *deck/hand manipulation*, *bespoke*. One primary class per card plus up to a few secondary tags.
2. An LLM does the first pass. A deterministic checker proves every title is classified exactly once and the class counts sum to 446. A human spot-checks a stratified 10%.
3. Join the result with (a) starter-deck membership and (b) existing `CardEffects` coverage.
4. Output: a census file, per-class counts, the list of distinct operations each class needs (this becomes the primitive list for A3), and the long tail.

**Decision rule** (my heuristic [I], tune after seeing data): if at least about 70% of behaviour-bearing cards fit about 25 or fewer primitives, proceed with A3 as designed. If the bespoke tail exceeds about 40%, keep the hybrid but budget more for escape-hatch code and revisit scope.

**Touches:** nothing in code; produces a data artefact. **Preserves:** everything. **Risks:** LLM misclassification, mitigated by the checker and spot-check. **Verified by:** checker exits 0; owner reviews the class table.

### A3. Data-driven effect system v0 (addresses R5 and R4)

**Design principles** (each is supported by how Forge, MetaStone and Spellsource work [R]):

1. **Hybrid, not pure.** Declarative records reference a registry of named primitives implemented in Java. A small keyed escape hatch handles the genuinely bespoke tail. Forge's card scripts name ability types that Java implements; MetaStone and Spellsource card JSON names spell classes that Java implements. All MetaStone card files were authored by hand, because effects cannot be generated reliably from card text [R]. Expect the same here.
2. **No expression language, no scripting.** Parameters are typed enumerations and small named predicates. A general scripting layer is the classic trap.
3. **Text is the specification and the display string, never parsed at runtime.**
4. **Fail closed.** An unknown effect kind is a load error. A behaviour-bearing card with no effect entry is flagged UNIMPLEMENTED (A4).
5. **Keep the primitive registry modular** (one class per primitive family). Forge's ability factory itself grew past 1,200 lines [R, Forge developer forum], so the registry is where the next god class would form.
6. **The rulebook's own precedence rules are the evaluation order,** not an invention of the engine (see the table below).

**Effect entry: shape** (a hypothesis derived from the rulebook; the census may revise it):

| Field | Meaning | Rulebook anchor |
|---|---|---|
| kind | one-shot, ongoing, triggered, replacement, restriction, cost modifier, trait | RB L530, L775 |
| window | round or step in which it can be played or fire (Ready, Sustained, Conflict, Action, Resolution, Aftermath, Draw) | RB III |
| trigger | event type plus filter (conflict resolved and initiator won; attack made; influence gained; vote called) | RB L775–L779 |
| requirements | cost block: apply or lose influence, rotate an Inner Circle sponsor, purge marks, discard, take damage, control N marks or characters | RB L1192 |
| target | card type; race or loyalty filter (ANY is a filter, not a value); controller relation; row (Inner Circle or Supporting); status (ready, rotated, neutralized); mark or trait filter; scope (card, faction, race, global) | RB L827–L832, L1160 |
| conditions | predicates over state: tension or unrest thresholds, power or influence thresholds, a state exists, mark counts, ISA exists (later) | RB L799–L817 |
| operations | ordered list of typed primitives: modify ability, gain or lose influence, change tension or unrest, add or purge marks, damage or heal, neutralize, discard or return, draw, enter or exit a state, and so on | various |
| duration | instant, until end of turn, until end of Resolution Round, **while source in play** (expires on discard, replace or blank), **permanent** (the "when" class, which survives agenda loss), until a condition ends | RB L854–L858 |
| stacking | additive ("an additional card") or replacing ("draw 2 instead of 1", non-cumulative) | RB L860–L862 |
| protection | irrevocable or boldface: cannot be blanked, negated or reversed | RB L1142, L1164 |
| precedence layer | card text over rules, except ambassador departure; "cannot" overrides any enabling effect; multipliers before additive changes | RB L866–L870, L1146, L1156 |
| source binding | mark or bonus provision with cut-off purge when the source leaves | RB L844 |
| provenance | rule-IDs (A8), text hash, printing scope | n/a |

**Glossary mapping (37 terms, my grouping [I]):**

| Group | Terms | Where they live |
|---|---|---|
| Static card attributes (data fields, not effects) | Limited, Multiple, Unique, Boldface, Irrevocable, Ranger, Ranger One, Mundane and Telepath (derived from Psi), Global (a target scope) | structured `traits`, `loyalty`, derived predicates |
| Engine definitions already generic | Base ability, Controller, Owner, Influence Rating, Ready, Participant, Attack during a conflict, Uncontested, Won, Canceled, Damage, Damage Resistance, Psi, Purge, Shuffle your deck, Transfer, States | engine code, no per-card schema |
| Effect-schema combinators | Cannot, Cost Reductions, Double, Free, Negate, Replace and Removed from the Game, Requirements, Used As, You or Your | explicit schema fields or modifiers |

Roughly 10, 17 and 10 terms respectively (37 in total). Only the third group needs new schema vocabulary.

**Are `subtype` and `text` sufficient substrates? No.**

* The report's samples of `subtype` (CHARACTER_HUMAN, AGENDA, EVENT) show it encodes card type and race, not printed traits [V, report §6.3.2].
* Printed traits (Limited, Unique, Multiple, Kha'Ri, Species Ambassador, Ranger, ISA, Home Faction or Psi Corps loyalty) must become structured `traits[]` and `loyalty` fields. The *Personalities* rule (RB L783) also needs a `personality` key: Sinclair, Valen and Entil'zha are one personality, while Sinclair and Sheridan are not. `species` is needed for League ambassadors (RB L884, L902).
* `text` stays, as the display string and the specification the effects are checked against.
* So five or so new structured fields, generated during the census pass.

**Pilot and migration.**

* **Pilot 1: Groups** (51 titles, 0% legacy coverage). The rulebook says groups "have an effect on play" (RB L494–L496), so every one needs behaviour, which makes them a pure test of the ongoing, duration and restriction model with no legacy to conflict with.
* **Pilot 2: Enhancements** (78 titles). They reuse the existing `StatBonus` infrastructure and exercise targets, stacking, the "no two copies on one target" rule, attach and cascade-discard, and global enhancements (RB L506–L518).
* **Pilot gate:** both pilots done, at least 70% of pilot cards expressible without the escape hatch, zero UNIMPLEMENTED hits for pilot cards in fuzz runs, no regression in the existing probes. Only then roll out by type: Events (166), Aftermath (117), Conflicts (108), Agendas (47), Locations (21), then characters and fleets that carry text.
* **Legacy strangler.** The registry is consulted first and falls back to the existing `CardEffects` predicate. For each of the 82 legacy titles, record a golden-master scenario under a seed *before* porting; the data version must reproduce it; delete the predicate only after parity. Track a `legacy-remaining` counter to zero.
* **Validation.** Validate effect data against a JSON Schema in the dev tooling (Python, already used by the project) at gate time. The shipped game keeps `MiniJson` and the stdlib-only rule. A renderer produces a canonical description from each effect entry, and a separate pass compares it with the printed text: the data equivalent of a probe.

**Migration cost against 12% coverage.** `CardEffects` is 1,668 lines for 106 literals, roughly 16 lines per special-cased literal (an upper bound, since the class also holds infrastructure) [I, arithmetic on report §4.5]. About 344 titles remain. If every one needed hand-written Java at that rate, the class would grow by up to about 5,500 lines, with the doubling hazard intact and every addition requiring a rebuild. The data approach costs more per card at first, then drops: entries are small, diffable, schema-validated and checkable by the renderer, and migration is cheapest now, while only 82 titles are legacy.

**Touches:** `CardEffects` (shrinks), a new effect package, data files, `DeckLoader`, new probes. **Preserves:** every existing probe and the exit-code gate. **Risks:** over-fitting the schema to the pilot; interaction ordering between effects; a larger bespoke tail than hoped. The census and the pilot gate are the mitigations. **Verified by:** the pilot gate above, plus the effective-coverage dashboard reaching its targets.

### A4. Fail-loud instruments (turn "green is not evidence" into numbers)

1. **Unimplemented-text tripwire.** When a card flagged behaviour-bearing (by the census) is played or resolved with no matching effect entry, emit `UNIMPLEMENTED(cardKey)`. Fatal in test mode for decks in the Playable Slice; a counter in normal play.
2. **Rulebook-invariant checker.** After every action in test mode, assert state invariants, each tagged with its rule-ID. Examples straight from the rulebook: influence rating never below 3 (RB L288); tension and unrest always within 1–5 (RB L284); no faction holds both Shadow and Vorlon marks (RB L162, L842); one agenda in play (RB L524); at most one copy of a Limited card in play (RB L1166); one Homeworld per race (RB L1128); war excludes other states between the same pair (RB L807, L1196); every card is in exactly one zone; applied influence never exceeds the rating.
3. **Seeded AI-versus-AI fuzz and a reachability histogram.** Run a few hundred seeded games over every starter-deck pairing, count action types, rule-IDs and effect entries actually fired, and flag dead engine paths (the report's PLY column, automated), stalls and invariant violations.

**Depends on:** A5 for determinism. **Touches:** a new test-side package; one narrow hook in `GameController`. **Preserves:** the existing gates. **Verified by:** the Playable Slice criteria (section 8.3).

### A5. Determinism (Tier 1; cheap and load-bearing)

**Why.** The report says deck shuffling takes no `Random`, which is why the 80-second human-seat probe is excluded from the gate and a coverage gate was relaxed to soft [V, report §7.2]. Without determinism there are no reproducible fuzz runs, no golden-master refactoring (A6) and no replay verification.

**What.** Inject a seeded random source at game level and derive per-subsystem streams (shuffle, AI tie-breaks, any mercenary or random choice). Record the seed in the replay. Add a state hash. `StarterDeckBuilder.setRandomSeed` already exists and shows the pattern.

**Touches:** `Deck`, `GameState` or `GameController`, `AIPlayer` randomness (the report does not say where it uses randomness), `ReplayRecorder`. **Preserves:** all behaviour at fixed seeds. **Risks:** hidden non-determinism (`Math.random`, identity-hash-ordered collections). **Verified by:** a double-run probe: same seed and actions produce identical state hashes and byte-identical replays. The 80 s human-seat probe becomes deterministic and joins the gate.

---

### A6. Engine decomposition behind delegates (addresses R6 and R9)

**Pattern, taken from the project's own best examples.** `TieBreakCatalog` (pure, static, 437 lines, 15 named entry points) and `GameController` (12 public methods over private orchestration) show what works [V, report §4.4, §4.6]. For each extraction: build a pure module with no dependency on `GameState`, taking value parameters and returning a **decision object** (for example a result with a reason code). Leave the existing `can…`/`execute…` method on `RulesEngine` as a one-line delegate, so every probe keeps passing unchanged.

**Order, by value and risk:**

| Step | Module | Why this position |
|---|---|---|
| 1 | **VictoryRules** (R9) | Small, high-value. Model each victory path as a rule with a per-player eligibility predicate and an achieved predicate, and return a **set** of winners. This removes the order-sensitivity the source documents, and handles simultaneous victory (RB L1048), the Shadow War and Major Agenda ineligibility for Standard Victory (RB L182, L526), and the optional Power-and-Victory margin (RB L1020). Drakh-only victory fits later as one more rule. |
| 2 | **MarkRules** | The report's own example. A value object with the opposing-mark rule (RB L842) and the cut-off purge when a mark's source leaves (RB L844). The Conspiracy-Mark sponsorship restriction, if ever needed, plugs in here. |
| 3 | **CostRules** | Sponsor, recruit and promotion costs, the double-cost rule, waivers. **UNDEFINED is a distinct value, not zero** (RB L1058). The single source of truth for "can I?" and "why not?" (the report lists separate `can…` and `get…Reason` methods, which risks two copies of the logic). |
| 4 | **InfluenceRules** | The rating floor of 3, gain, loss, transfer, apply and restore, temporary influence (RB L288, L296, L1200). |
| 5 | **ConflictRules** | The largest piece, done last. It already leans on `TieBreakCatalog`. |
| 6 | `GameState` and `Player` split into sub-aggregates | For example tension and Civil War state, conflict state, mercenary state, victory state; and for `Player`, an influence ledger, rows, hand and deck, bonuses, marks. |

**Trend targets** (indicative [I]): `RulesEngine` from 3,317 lines and 115 public methods toward under 1,000 lines and about 30 methods; no extracted module above about 600 lines; every extracted module has at least one exit-code probe class.

**Touches:** `RulesEngine`, `GameState`, `Player`. **Preserves:** the `can`/`execute` taxonomy and the rulebook citations in source; all ~30 probes; `TieBreakCatalog`; `GameController`'s narrow interface. **Risks:** behaviour drift. Mitigate with **golden-master replays**: record action logs and state hashes from the current engine over many seeds, and require identical hashes after each extraction. This requires A5 first, which is the main reason A5 is ranked ahead of A6. **Verified by:** replay hashes match before and after; line and method counts trend down; the probes are unchanged.

### A7. Race/Faction split and staged faction features (addresses R3; conditional on D1)

**Why the enum is the problem.** `Faction` today carries three different concepts: a *player race* (HUMAN, MINBARI, CENTAURI, NARN), a *card loyalty* (NEUTRAL), and a *query wildcard* (ANY) [V, report §3.4]. VORLON and NON_ALIGNED sit in the same list. Every new use deepens the coupling.

**A7.0, the rename (cheap; do it even if D1 says single-player).** Rename `Faction` to `Race` (compile-guided, no behaviour change). Split the other two meanings out: `Loyalty` (a race or NEUTRAL) for card data, and a race-or-any filter inside target specifications. This also removes the stale `TensionMatrix` javadoc hazard (R15).

**A7.1, the entity model.**

* `Faction` becomes a new entity: id, race, designations (standard, Home, Psi Corps), ambassador, council seat, unrest. A Drakh designation would be an overlay (RB L1100: "your actual race remains as it was originally"), not a replacement for race.
* Introduce an **addressing entity** for tension, states and influence effects. Normally it is the race. During Civil War each breakaway faction becomes its own addressing entity (RB L1004: "treated as separate races"). The `TensionMatrix` is re-keyed by entity. Entering Civil War splits entities and copies tension; leaving it merges them with the average rounded up (RB L1004). States that would be cancelled by a war between the two are suppressed and restored (RB L1006–L1010).

**A7.2, staged features (each independently testable on the current pool through "More Factions," RB L1024):**

| Stage | Feature | Rulebook |
|---|---|---|
| F1 | Same-race faction tension (starts at 2); non-aggression at tension 3 or less | L976–L978 |
| F2 | Council seat only for actual ambassadors; the council head | L795, L982 |
| F3 | Joint effects; dual-race states (benefits per faction; averaged where influence-based) | L986–L992 |
| F4 | Civil War, with **Declaration of War as a card play** (C3) | L994–L1012 |
| F5 | Optional Power and Victory (minus 1 per other same-race faction; plus 3 margin over same-race rivals) | L1020 |
| F6 | Psi Corps and Conspiracy-Mark sponsorship counts | L1026–L1036; **needs cards; defer** |
| F7 | Drakh and ISA | L1094–L1124; **do not start** |

**Priority.** If D1 is "single human versus AIs of distinct races," do A7.0 and stop. If same-race play matters, do F1–F5 in order. Each stage ships behind the rule-set profile (A9).

**Touches:** a broad compile-guided rename across the engine, the AI and the UI package (mechanical, no UI design implications). **Preserves:** all behaviour. **Risks:** rename churn while the fleet edits; schedule it in a freeze window. **Verified by:** the existing probes pass after the rename; each stage adds its own probe tagged with rule-IDs.

### A8. Traceability and the 498 KB conformance suite (addresses R8)

**Goal:** per-rulebook-section traceability without discarding the exit-code discipline that works.

1. **Rule-ID index.** A machine-readable file beside the rulebook (the rulebook itself stays unedited): one stable id per paragraph or list item, with section, line range and the rulebook's SHA-256 (A9). It is regenerated only when the rulebook version changes.
2. **Mechanical split.** Split `HeadlessConformanceTest` by rulebook section into roughly 15–25 classes, each with its own exit 0/1. The old class becomes a runner that invokes them all, with the exit code as the OR of the children. Assertion text is identical. A codemod does the move; do not hand-rewrite.
3. **Coverage ledger.** Each check declares the rule-IDs it covers through one helper; the run writes a coverage file. A report shows, per rulebook section, the share of paragraphs with at least one covering check. Start as a report; later add a **ratchet** (a floor that may only rise), not a fixed target.
4. **Mutation sanity.** Seed about 20 known defects by script (for example, change the influence floor from 3 to 2). Each must fail at least one split class. This proves the split did not silently drop assertions.
5. **Interpretations register.** `DECISIONS.md` entries that interpret the rulebook gain a rule-ID field and an index, so a rule, its interpretation, its tests and its code can be joined.

**Preserves:** exit-code gates, probe naming, no external test library. **Risks:** shared fixtures broken by the split; mitigate by checking total assertion count before and after and running both layouts in parallel for a period. **Verified by:** assertion counts equal; mutation sanity passes; the coverage report renders.

### A9. Rulebook pinning and the rule-set profile (Tier 0)

1. **Pin the rulebook.** Record its SHA-256, size and line count in a provenance note, and reconcile the report's "689 lines" with the 1,236 I received (C5). Fix the five encoding-damaged lines in a derived copy only; never in the canonical file. Add an encoding probe for the card JSON too (M5).
2. **A rule-set profile** selected per game and recorded in replays: rulebook version (1.3), alternate factions on or off, optional Power and Victory on or off, tournament rules off, and which printing's text governs (D5). This makes "present but unreachable" explicit and testable.
3. **Record first interpretations:** which text governs (D5); the League's second Species Ambassador is in hand, not in play (C7); the duplicate-setup-card sweep (report open question 4: I found no rulebook rule for it in the starting-hand or setup sections, and "Removed from the Game" is defined for replacement, RB L1188–L1190, so treat the sweep as an engine decision to log).

### A10. The AI (deferred; Tier 4)

* Do not refactor `AIPlayer` internals until the effect schema exists. Scoring is coupled to effect semantics today and would be rewritten.
* After A3, have the AI score **effect primitives** (the value of "+2 Leadership until end of turn") instead of card ids, so new cards play sensibly with no AI code.
* Either fill the empty decision and memory seam (168 lines of getters and setters) or delete it.
* Use seeded AI-versus-AI runs as a regression and win-rate-by-difficulty check.

---

## 6. Toolchain and language assessment

### 6.1 What the Java 6 pin costs here

| Cost | Evidence | Tag |
|---|---|---|
| No string `switch` (Java 7): 128 id comparisons are forced into `if` chains | report §4.5 | [I] |
| No records, sealed types or pattern-matching `switch`, so the compiler cannot prove an effect handler exists for every effect kind. That is the exact "wired but not working" failure class | records final in Java 16, sealed types in 17, pattern `switch` and record patterns in 21 [R] | [R/I] |
| No try-with-resources, diamond, NIO.2 (Java 7) or lambdas (Java 8); LLM agents' default idioms are modern, so a policing subsystem exists: the census tool, its gate families and the AGENTS.md §2a essay | report §1, §12.3 | [V] |
| Compiled with a 2021 JDK build (1.8.0_292) while 8u501 shipped in July 2026 | report §1; Arehart blog | [R] |
| Modern analysis tooling generally assumes newer runtimes (coverage, mutation testing, `jdeps`) | not verified in this pass | [I] |

**Not costs:** runtime performance, Swing, or the 57k lines of existing code, which are valid at later source levels.

### 6.2 Options

| Option | Asset reuse (57k LOC, 498 KB suite) | Type-safe effect model | Rewrite risk | Toolchain effort | Verdict |
|---|---|---|---|---|---|
| A. Stay on Java 6 / JDK 8 | full | weak | none | none, but the policing tax continues | Reject |
| A′. Stay on Java 6, harden with `--release 6` on JDK 11 | full | weak | none | small | Fallback if D2 is "no" |
| **B. Modern Java in place (JDK 25 LTS), staged** | **full** | **strong** | **low** | **small–medium** | **Recommend** |
| C. Kotlin on the JVM | full interop | strong | low–medium | adds a second toolchain; gives little beyond modern Java | Reject for now |
| D. TypeScript rewrite | none; the 498 KB suite must be re-derived | strong | **very high** | large | Reject |
| E. Python rewrite | none | weak | very high | large; slow for AI simulation [I] | Reject |
| F. C# / .NET rewrite | none | strong | very high | large | Reject |
| G. Rust rewrite | none | very strong | very high | large | Reject |

**Why not a rewrite.** The conformance suite is the project's principal correctness asset (report §7.1) and it is Java. Porting it would be a re-verification effort comparable to the rule-accuracy work already done, and it would reset the 944-task investment. No language choice fixes the actual defects: the identity split, card behaviour held in code, god classes, and aim. **Exception worth preserving:** if you later want a browser or mobile front end, the `GameAction`/`GameController` boundary is already a clean headless interface; keep it free of Swing assumptions so a second front end or a port stays possible.

### 6.3 Staged migration (T1)

* **T1.0 Measure (S).** Run `jdeps --jdk-internals` or equivalent over the compiled classes. Build the tree with JDK 25 at `--release 8` on a throwaway branch. Record every warning and any use of removed or encapsulated APIs.
* **T1.1 Hop 1 (S–M).** Make `--release 8` under JDK 25 the official build. Java 6 source is almost always valid Java 8 source. Run all gates. Retire the 1.8.0_292 pin.
* **T1.2 Hop 2 (S–M).** Raise to `--release 25` (via 17 and 21 if you want smaller steps). Run all gates at each hop.
* **T1.3 Policy (S).** Replace the Java rule in `AGENTS.md` with one line (JDK 25 LTS, current release level, stdlib only, with D4 exceptions). New code may use records, sealed interfaces, switch expressions and `var`. **No mass rewrite:** modernise on touch. Run the census for one cycle as a sentinel, then retire it along with the §2a essay (this is a governance change; it needs a DECISIONS entry and I apply nothing).
* **T1.4** The frozen Java 8 archive can stay or go after hop 1; owner's call.

**Risks:** Swing behaviour differences on a newer JDK (for example high-DPI scaling changes since Java 9 [I]); removed or encapsulated internals; build-script assumptions on Windows. All are measurable in T1.0, and the rollback is "stay on the branch." **Verified by:** every gate class green under JDK 25; the same replay hashes before and after (A5); the census retired. **JDK vendor:** Oracle's page says JDK 25 receives updates under the NFTC until September 2028, and JDK 21's NFTC window ends September 2026 [R]. Other OpenJDK distributions have their own terms, which I did not check.

### 6.4 Relaxing "no external libraries"

* **Keep it for the shipped game.** It is a real simplicity benefit, and `MiniJson` is adequate for the loader.
* **Allow test or tooling-scope dependencies later, by explicit approval** (a standard test runner for reporting and test selection, property-based and mutation testing). Not now: the exit-code `main` pattern works, and A4 and A8 can be built without any of these [I].
* **Do schema validation in the Python tooling,** not in the game. The game stays stdlib-only; the data gets a strong validator at gate time.

---

## 7. System B recommendations (the development process)

### 7.1 Diagnosis

| Observation | Figure | Source |
|---|---|---|
| Ledger rows by status | 1,113 total: 944 DONE (84.8%), 150 BLOCKED (13.5%), 10 VOID, 8 SUPERSEDED, 1 OPEN | [V, report §12.1] |
| Process documents per ledger row | 5,629 documents (1,598 reports, 1,277 patterns, 83 proposals, 83 doc reports, 2,588 investigations) against 1,113 rows, about 5.1 per row | [V, arithmetic] |
| Pattern namespaces | 513 for 1,277 records, about 2.5 records per namespace | [V, arithmetic] |
| Unbounded documents | ledger 3.0 MB, decision log 1.55 MB, conformance suite 498 KB | [V, report §12.3] |
| Boot file | 21.7 KB, plus an AGENTS.md whose §2a is an essay on a miscounted number | [V, report §12] |
| Work queue | one OPEN row; the fleet is "grinding a historical backlog" | [V, report §12.3] |

**Reading.** The fleet is strong on **verification** (append safety, timestamp integrity, dead-blocker re-measurement, measured gate exclusions) and weak on **specification and aim**: nothing ties the work to a product target, and each task writes about five documents. External evidence points the same way:

* The MAST study (NeurIPS 2025) analysed 1,642 traces from seven multi-agent frameworks and organised failures into three categories: system design and specification issues, inter-agent misalignment, and task verification. It also reports that multi-agent gains over simple baselines are often minimal [R]. The fleet's verification layer is good; its specification layer is the gap.
* An ETH Zurich study found that repository context files (AGENTS.md and similar) tended to reduce task success and raised inference cost by more than 20%, and concluded they should contain only minimal requirements. Human-written files fared better than LLM-generated ones [R]. **Caveat:** those results come from SWE-bench-style Python tasks, so treat them as a strong prior to test locally, not as proof. For a fleet on free-tier capacity, a 21.7 KB boot file read on every task is a direct cost.

### 7.2 B0: snapshot without giving agents commit power (R1; Tier 0)

* **One-time human-ordered checkpoint** of all source and data, with scratch excluded through `.gitignore`. Run a secret scan first (agent trees sometimes hold keys) [I].
* **A scheduled snapshot job outside the agent loop** that commits the tree to a separate snapshot branch on a timer. Agents keep "no commit, no push"; git remains "change tracking, not authority" (so the stated rationale holds); the exposure to file loss ends.
* **An off-machine copy** (a second drive, a bare repo, a cloud drive), pushed by you or by the job.
* **A restore drill:** clone the snapshot to a temp directory, run `compile.bat` and the gates.

**Verified by:** untracked `.java` count is 0; the restore drill passes; an alert fires if the newest snapshot is older than 24 hours. **Risks:** committing secrets, noisy diffs from scratch files; both are handled by `.gitignore` and the scan.

### 7.3 B1: re-aim the fleet (Tier 1)

1. **A product target.** Adopt the **Playable Slice** (section 8.3) as the one milestone the fleet is steered by.
2. **A planner-authored backlog.** Seed it from section 13. Keep the OPEN queue small (about 5–10 tasks), each with a testable acceptance line.
3. **BLOCKED triage in one batch.** The project already has the right method: re-measure each blocker's named reason with its own instrument (report §12.2). Close the dead ones, then cluster the live ones by root cause. I expect a few recurring causes, such as faction identity, never-authored agenda-effect members and missing tension-to-war propagation [I, from report §10]. Those clusters become work packages rather than 150 separate rows.
4. **Product KPIs** replace activity counts: (a) effective coverage of behaviour-bearing cards in Playable Slice decks; (b) invariant violations per 1,000 fuzz games; (c) UNIMPLEMENTED hits per 1,000 games; (d) gate-green merged changes per day; (e) tokens per merged change; (f) process-document bytes per task.

### 7.4 B2: cut the process tax (Tier 1)

| Item | Change | Target |
|---|---|---|
| Pattern records | Require a novelty check; one file per *family*, not per task; merge the 513 namespaces to a few dozen; stop mandating one on every close-out | at most 0.2 patterns per task |
| Boot file | Split the 21.7 KB boot file into a core of about 4 KB plus modules loaded on demand; strip rationale prose | boot read down about 70% |
| Provenance front matter | Keep it on authority-bearing documents (canonical, proposals, reports). For per-task scratch (patterns, reports), carry provenance in the ledger row instead | removes the step from most of the roughly 5,600 process documents |
| Ledger | Rotate: archive DONE rows older than about 14 days into monthly files; keep the live ledger small (target under about 200 KB) | no agent reads a tail slice of 3 MB |
| `DECISIONS.md` | Add an index by area and rule-ID (A8); stop treating it as something to skim from the tail | queryable |
| `investigations/` (2,588 files) | Remove from the agent boot path; archive | not read at boot |
| Governance prose | Where a number moves, replace prose with a script (the denominator-guard idea, applied everywhere); retire §2a-style essays after T1 | shorter AGENTS.md |

**Verified by:** process documents per ledger row falls from about 5.1 to about 1 or less; boot tokens per task fall; gate-green merged changes per day stay flat or rise. Measure before and after on the same two-week window.

### 7.5 B3–B5 (Tier 3; evaluate, then act)

* **B3. Substrate (evaluate; do not do yet).** The append-verify tool *detects* lost updates after the fact; a transactional store (SQLite through Python's standard library, with a JSONL export for diffs) would *prevent* them by construction, and per-task git worktrees would stop agents sharing one working tree [I; I did not research either in this pass]. Both pull against "no commit" and the shared-tree model, so they need D3. **Adopt only if** lost updates or claim collisions recur after B2, or the number of concurrent agents grows beyond about four.
* **B4. Fleet shape.** Run the next milestone with 2–3 concurrent agents on disjoint scope directories (the "one writer per scope" rule already supports this) and compare against the current setup on KPIs (d) and (e) for one to two weeks. MAST's finding that more agents do not reliably mean more output is the reason to test rather than assume [R]. Fewer, better-specified tasks also suit free-tier rate limits.
* **B5. Hygiene (one batched task).** Remove the dead React/Vite stack to a tag or branch (governance requires a DECISIONS entry); sweep the 23 root scratch files and add them to `.gitignore`; fix or remove the dangling `quarantine/` reference in `compile.sh`; fix the stale `TensionMatrix` javadoc; reconcile ids issued against ledger rows (C9).

### 7.6 B exit criteria (when to stop doing System B)

B is "done enough" when: the boot read is under about 5 KB; process documents per row is at or below about 1; the live ledger is small; the BLOCKED triage is complete; and the Playable Slice backlog is loaded. Then the fleet works on System A. B work after that is limited to B3–B5 triggers.

---

## 8. Sequencing, the A-versus-B call and the milestone

### 8.1 Phases

Calendar estimates are rough [I]; the exit criteria are what count.

| Phase | Items | Exit criteria |
|---|---|---|
| **0 (week 0)** | B0 snapshot; A9 pin and profile; A1a canonical id and triage; decisions D1–D5 | Zero untracked `.java`; restore drill passes; parity probe green; decisions recorded |
| **1 (weeks 1–2)** | B1 and B2 reset; A2 census; T1 toolchain; A5 determinism | B exit criteria (7.6); census reviewed; gates green on JDK 25; double-run probe green |
| **2 (weeks 3–6)** | A3 schema v0 and the Groups and Enhancements pilots; A1b; A4 instruments; A6 steps 1–2 (Victory, Marks) | Pilot gate passed; first fuzz report; UNIMPLEMENTED counter live |
| **3 (weeks 7–12)** | A3 roll-out by type; A6 steps 3–5; A8 split and ratchet; A7 (conditional on D1) | Playable Slice (8.3) |
| **4 (later)** | A10 AI over effects; test-scope dependencies (D4); B3 if triggered | n/a |

### 8.2 Dependencies

| Item | Depends on | Why |
|---|---|---|
| A1b | A1a, A3 | the structural fix lives in the schema |
| A3 pilot | A2, T1 (preferably), A5 | census sizes it; sealed types help; golden masters need determinism |
| A4 | A5, A2 | needs seeds and the behaviour-bearing flag |
| A6 | A5 | golden-master replays |
| A8 | A9 | the pinned rulebook |
| A7 F1–F5 | A7.0, A9 | the rename and the profile |
| B1 | B0 | do not re-aim an unprotected tree |

### 8.3 The milestone: Playable Slice

A single, testable definition of "playable" to steer by. Targets are my proposals [I] for you to tune.

1. **Stability.** For every starter deck in `premiere-starter-decks.json`, play 200 seeded AI-versus-AI games across all pairings. Zero engine exceptions and zero invariant violations.
2. **Legitimate termination.** At least 95% of games end by a rulebook victory path or a legal surrender, not by a stall or the round cap.
3. **No silent text.** Zero UNIMPLEMENTED hits for any behaviour-bearing card in those decks.
4. **Deterministic human-seat game.** One scripted human-seat game, deterministic through its seed, runs in the gate tier in place of the 80 s probe.
5. **Traceability.** The rule-ID coverage report exists, and at least 90% of the core-rules sections (RB I–III, V, and VI through Marks) have at least one covering check.

The Playable Slice is deliberately narrow (base rules, starter decks). It is the first rung of the scope ladder (section 4.4), not the whole edition.

---

## 9. Constraint ledger

| Constraint | Cost it imposes | Verdict | Notes |
|---|---|---|---|
| Java 6 source level | Policing subsystem; no string `switch` or sealed types; modern agent idioms keep tripping it | **Change** (T1) | Keep the `GameAction` boundary clean |
| JDK 1.8.0_292 pin | A 2021 build; the "only toolchain" claim is wrong (C1) | **Change** | Follows T1 |
| JDK stdlib only | Hand-rolled JSON; no validators | **Keep for runtime; relax for tooling** (6.4) | D4 |
| Swing client | Not assessed | **Out of scope** | |
| Autonomous fleet | About 5.1 documents per task; aim drift; cost | **Keep, smaller and aimed** (B1, B2, B4) | |
| Rulebook canonical, never edited | None significant; the scraped copy has encoding damage and a line-count mismatch (C5) | **Keep; pin by hash; add a rule-ID index** | A8, A9 |
| No commit, no push | Half the Java source is untracked | **Keep for agents; add non-agent snapshots** | B0, D3 |
| Two-set data model | Silent id asymmetry; doubled effort per card | **Keep storage; change semantics** | A1 |
| Provenance front matter on every doc | Cost on about 5,600 process documents | **Narrow to authority-bearing docs** | B2 |
| No new `.md` at repo root | None | **Keep** | This file goes in `docs/proposals/` |
| Windows and bash/PowerShell/Python launcher forms | None observed | **Keep** | |

---

## 10. What is genuinely good (confirming or challenging the report's candidates)

**Confirmed, and worth building on:**

* **Exit-code gates with measured exclusions.** Retiring a flaky timing assertion rather than tolerating it, and the "a capped count is a floor, never a total" principle, are better practice than most projects show (report §7.2).
* **`TieBreakCatalog` and `GameController`'s narrow interface** are the templates for A6.
* **The `can…`/`execute…` pairing with rulebook citations in source** is a real strength; A6 keeps it.
* **The `cards/index.json` prefix contract and its guard.** It is what makes the R2 defect detectable at all, and A1a uses it as the canonical-key rule.
* **Append safety, timestamp integrity and dead-blocker re-measurement** show sound process engineering built from measured failures.

**Challenged:**

* **The Java 6 census.** The idea is honest and well built. It also exists only to police a constraint that T1 removes, so it should be retired after one sentinel cycle.
* **Append-then-verify.** Correct for a flat file shared by concurrent writers. A transactional store would remove the failure class instead of detecting it (B3), so the tool is worth keeping only until the substrate is reconsidered.
* **Mandatory provenance and pattern records on every task.** The auditability they buy is real; at about five documents per task the cost is higher than the benefit for scratch artefacts (B2).

---

## 11. What not to do yet

1. **Do not rewrite the engine in another language.** (Section 6.2.)
2. **Do not implement Drakh, Chaos Tokens or the ISA,** or Psi Corps sponsorship, until cards exist to exercise them (section 4).
3. **Do not author contingency card data.** Use test-only fixtures to cover the engine path.
4. **Do not build a scripting language or expression evaluator for effects.**
5. **Do not mass-convert all 446 cards** before the Groups and Enhancements pilot passes its gate.
6. **Do not refactor `GameController` or the internals of `AIPlayer`** now.
7. **Do not introduce Maven, Gradle or a test framework yet** (D4 is in principle only).
8. **Do not chase 100% coverage or 100% rule-ID coverage.** Use a ratchet.
9. **Do not add more fleet workers** until KPIs (d) and (e) say more helps (B4).
10. **Do not spend effort on the dead React stack** except to remove it.

---

## 12. Risks of this proposal

| Risk | Mitigation |
|---|---|
| The effect schema does not fit the card long tail | A2 census and the A3 pilot gate come before roll-out |
| Toolchain move breaks Swing or Windows build assumptions | T1.0 measures first; rollback is "stay on the branch" |
| The fleet reset costs a week of throughput | It is time-boxed with exit criteria (7.6) |
| Canonicalisation unifies printings wrongly | Triage, golden masters and owner ratification of D5 as an interpretation |
| I reviewed documents, not code | Appendix C lists the measurements that would confirm or refute my claims; run them before committing to A3 and T1 |
| Governance changes without authority | Commit policy, the Java rule and the census are flagged as governance changes. This proposal applies none of them; each needs a DECISIONS entry |
| Over-engineering for a solo, single-player product | D1 gates A7; the Playable Slice is deliberately narrow |

---

## 13. Handoff: work packages for the agent fleet

**Authority note.** This is a candidate-tier proposal. A work package becomes a task only after the owner approves it. Any package that settles a rules question (which printing's text governs, how the League's second ambassador is placed, what the duplicate-setup sweep should do) must record the outcome as an *interpretation* in `DECISIONS.md`, never as a change to the rulebook. Where possible, acceptance is an exit-code check, in the project's own idiom.

| ID | Package | Depends on | Scope | Acceptance | Size |
|---|---|---|---|---|---|
| WP-00 | Snapshot, secret scan, restore drill (B0) | none | tooling, `.gitignore`, a scheduled job outside the agent loop | untracked `.java` = 0; the restore drill builds and passes the gates; a stale-snapshot alert exists | S |
| WP-01 | Pin rulebook, add the rule-set profile stub (A9) | none | provenance note, profile object, encoding probe | hash recorded; "689 vs 1,236" reconciled in writing; profile recorded in replays | S |
| WP-02 | Canonical-key function and parity probe (A1a) | none | `CardEffects` comparisons, `CardIdNamespaceGuard` | probe exits 0; every literal is canonical; existing probes unchanged | S–M |
| WP-03 | Printing-diff triage of the 82 special-cased titles (A1a) | WP-02 | report plus one scenario test per asymmetric title | all 58 single-literal titles resolved as unchanged-text or interpretation-logged; each has a test passing under both ids | M |
| WP-04 | Effect census (A2) | none | a data artefact and a checker; no engine code | checker exits 0 (446 titles, each exactly once); owner reviews the class table | S–M |
| WP-05 | Toolchain measurement (T1.0) | none | `jdeps`, a throwaway JDK 25 build at `--release 8` | warning and internals report delivered | S |
| WP-06 | Toolchain hops and policy (T1.1–T1.3) | WP-05, owner D2 | build scripts, AGENTS.md Java line (needs a DECISIONS entry) | all gates green under JDK 25; the old pin retired; the census runs one sentinel cycle | M |
| WP-07 | Seeded RNG, state hash, double-run probe (A5) | none | `Deck`, game setup, AI randomness, replay | identical hashes across two runs; the human-seat probe becomes deterministic and joins the gate | S–M |
| WP-08 | BLOCKED triage batch (B1) | none | ledger rows only | all 150 rows re-measured; dead ones closed; live ones clustered by root cause and linked to work packages | M |
| WP-09 | Process-tax cuts (B2) | WP-08 (soft) | boot file, pattern policy, ledger rotation, provenance scope | the B2 targets in 7.4 met and measured over a two-week window | M |
| WP-10 | Effect schema v0, JSON Schema, registry skeleton (A3) | WP-04, WP-07 | new effect package, data validator in tooling | schema validates; an unknown effect kind fails the load; a renderer exists | M |
| WP-11 | Pilot: Groups (A3, A1b) | WP-10 | 51 group titles | pilot-gate criteria (A3) met for Groups | L |
| WP-12 | Pilot: Enhancements (A3) | WP-11 | 78 enhancement titles | pilot-gate criteria met; legacy golden masters reproduced | L |
| WP-13 | Unimplemented-text tripwire (A4) | WP-04 | a hook in `GameController` and `CardEffects` | a counter is produced; fatal in test mode for slice decks | S–M |
| WP-14 | Invariant checker and fuzz harness (A4) | WP-07 | test-side package | invariants tagged with rule-IDs; the reachability report is generated; zero violations in 200 seeded games or each violation is a filed defect | M |
| WP-15 | VictoryRules extraction (A6.1) | WP-07 | new module; `RulesEngine` delegate | golden-master hashes identical; a simultaneous-victory probe passes | M |
| WP-16 | MarkRules extraction (A6.2) | WP-15 | new module | the 7 mark probes pass unchanged | M |
| WP-17 | CostRules extraction with UNDEFINED cost semantics (A6.3) | WP-15 | new module | the cost-audit probe passes; absent cost never becomes zero | M |
| WP-18 | Rule-ID index, conformance split, mutation sanity (A8) | WP-01 | tooling and test classes | assertion count equal before and after; mutation sanity passes; the coverage report renders | M |
| WP-19 | `Faction` to `Race` rename and `Loyalty` split (A7.0) | owner D1, a freeze window | compile-guided rename | all gates green; zero behaviour change | M |
| WP-20 | Hygiene batch (B5) | none | React stack, root scratch, `quarantine/` reference, javadoc, id reconciliation | each item closed or explicitly deferred | S |

---

## 14. Open questions for the owner

| # | Question | Why it matters |
|---|---|---|
| Q1 | Single human versus AIs of distinct races, or same-race or multi-human games too? | Decides how far A7 goes (D1). |
| Q2 | Is Java 6 an external requirement (a runtime you must support) or inherited from earlier decisions? I found no reason stated in the report. | If it is external, T1 changes shape. |
| Q3 | Which printing's text governs when printings differ? | A1a triage (D5). |
| Q4 | Can both printings of one title appear in a single deck today? | The report's open question 2; moot after A1b but sizes the current exposure. |
| Q5 | Are the 4 cost-less characters starting ambassadors? | Confirms the cost-data picture ([I], from report §6.3.1). |
| Q6 | Is importing expansion card data (Shadows, Great War, Psi Corps, Severed Dreams, Wheel of Fire) ever in scope? | Gates S4 and S5 (section 4). |
| Q7 | Why were about 4,249 ids issued for 1,113 ledger rows? | Possible wasted claims (C9). |
| Q8 | Is a non-agent snapshot job acceptable, and might agents commit on private task branches later? | D3. |
| Q9 | Which models does the fleet use, and is the strongest one available for schema transcription? | A3's census and card transcription are accuracy-sensitive. |

### 14.1 My view on the report's five open questions (report §15)

| # | Report question | My view |
|---|---|---|
| 1 | Do agendas have no printed cost, or is it a data gap? | **Almost certainly correct data.** The rulebook has agendas sponsored by rotating a character (RB L524) and uses "a sponsored agenda" as an example of undefined cost (RB L1064) [V]. R7 should be closed; the obligation is UNDEFINED semantics (A6.3). |
| 2 | Can the deck builder place both records of one title in a deck? | Unresolved from documents. Answer with M1. After A1b the question disappears. |
| 3 | How many of the 47 agendas with `winCondition` are reachable? | Fold into the census (A2): classify each win condition and check it against the victory rules (A6.1). |
| 4 | Does the duplicate-setup-card sweep match "Removed from the Game"? | I found no rulebook rule that requires the sweep, and "Removed from the Game" is defined for replacement (RB L1188–L1190). Treat it as an engine decision and log it as an interpretation (A9). |
| 5 | Is the absence of contingency data intentional? | **Probably intentional.** Contingencies are a Great War card type (RB L538) and the pool predates it [V against the rulebook; I that the pool predates it]. Use test fixtures. |

---

## Appendix A. Rulebook cross-reference (line numbers refer to the supplied copy)

| Topic | Lines | Implication here |
|---|---|---|
| Deluxe edition, expansions | L134–L136, L1068 | Scope finding (section 4) |
| Victory, Shadow War, Major Agenda | L173–L187, L526 | VictoryRules (A6.1) |
| Simultaneous victory | L1046–L1050 | Result must be a set of winners |
| Tension and unrest bounds; influence floor | L284, L288 | Invariants (A4) |
| Marks, opposing marks, cut-off purge | L162, L834–L844 | MarkRules (A6.2) |
| Bonuses and blanking ("while" versus "when") | L854–L858 | Effect duration classes (A3) |
| Cumulative effects ("instead" versus "additional") | L860–L862 | Effect stacking (A3) |
| Precedence, "cannot" | L864–L870, L1146 | Evaluation order (A3) |
| Personalities | L781–L787 | `personality` key (A3) |
| Contingencies | L536–L546, L1134 | Test fixtures only (section 4) |
| League | L874–L916 | S2; second ambassador in hand (A9, C7) |
| Alternate factions, dual races, voting, joint effects, Civil War | L918–L1014 | A7 stages F1–F4 |
| Optional Power and Victory | L1018–L1020 | A7 stage F5 |
| Psi Corps | L1026–L1036 | Deferred (F6) |
| Cards with no printed cost | L1056–L1064 | UNDEFINED semantics (A6.3); C8 |
| Drakh, ISA | L1094–L1124 | Do not start (F7) |
| Glossary (37 terms) | L1136–L1212 | A3 mapping |

## Appendix B. Sources consulted

| Source | Used for |
|---|---|
| Forge AbilityFactory documentation (github.com/Card-Forge/forge/wiki/AbilityFactory) and a Forge developer forum thread on AbilityFactory size (slightlymagic.net) | Parameterised ability scripts with Java-implemented primitives; the registry itself growing past 1,200 lines |
| MetaStone (github.com/demilich1/metastone, issue 65) and Spellsource (github.com/carlhu/Spellsource-Server) | Card JSON naming Java-implemented spell classes; all MetaStone card JSON authored by hand |
| HearthstoneJSON documentation (hearthstonejson.com) | A flat card-data model with a `mechanics` tag list, for comparison |
| OpenJDK JDK-8206877 and the jdk-dev thread "support for javac -source/-target/--release 6 removed from JDK 12" | `-source 6` removal timeline (C1) |
| Oracle Java downloads page; Charlie Arehart's July 2026 Java update post; marcobehler.com Java versions guide | JDK 25 LTS and its NFTC window; JDK 21's window; Java 8 still receiving updates |
| OpenJDK JEP 443 text; Brian Goetz, "Data Oriented Programming in Java," via nipafx slides | Records, sealed types and pattern matching and the versions that finalised them |
| Cemri et al., "Why Do Multi-Agent LLM Systems Fail?" (arXiv 2503.13657; NeurIPS 2025) | MAST: three failure categories, 1,642 traces, limited multi-agent gains |
| Gloaguen et al., "Evaluating AGENTS.md" (arXiv 2602.11988; ICLR 2026) | Context files reducing success and raising cost by over 20% |

**Not researched in this pass** (so tagged [I] where used): SQLite concurrency behaviour, git worktree workflows, the specifics of mutation- and property-testing tools for Java, `jpackage`, and Swing behaviour under newer JDKs.

## Appendix C. What I could not see, and measurements that would confirm or refute me

I had no access to the card JSON, source, `AGENTS.md`, the ledger or `DECISIONS.md`. Each measurement below is cheap and could be a work package.

| # | Measurement | Confirms or refutes |
|---|---|---|
| M1 | Can a deck or pool contain both printings of one title? (deck builder, `loadBothSets`) | Exposure in A1 (Q4) |
| M2 | Count of records per faction value in both JSON files, especially NON_ALIGNED, NEUTRAL, VORLON | Scope of League work (section 4.3) |
| M3 | Are Shadow Tech Upgrade, Recalled, Forced Impairment and First United Fleet in the pool, and with what cost? | C8: whether the data invents costs |
| M4 | Every use of `Random`, `Math.random` and identity-hash-ordered collections in engine and AI | A5 risk |
| M5 | An encoding probe on both JSON files (the rulebook copy has encoding damage) | A9 |
| M6 | `jdeps --jdk-internals` and a JDK 25 build at `--release 8` | T1 risk |
| M7 | Starter-deck membership joined with existing `CardEffects` coverage | A2 prioritisation |
| M8 | Reconcile issued task ids against ledger rows | C9 |

---

*End of proposal. Author: Claude. Version: v1.*
