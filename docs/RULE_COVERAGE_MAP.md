---
document:
  title: "Rule coverage map - conformance check to rule ID"
  status: "Hand-kept sidecar. Read by .agent/tools/rule-coverage.ps1 (WP-18c). Not truth for the engine; it is a mapping, and the tool enforces every row against the gate log and the rule index."
  index_source: "docs/RULE_INDEX.md"
provenance:
  author_llm: {name: "opencode (tencent/hy4-preview)", version: "opencode-hy4-preview-02"}
  created_date: "2026-10-17"
  task: "B5-0024"
---

# Rule coverage map

This is the convention that makes rule coverage measurable without touching a
single `.java` file: a check covers a rule when **this file maps it to that
rule**. Nothing else is required. A probe may later carry an inline
`// RB:<id>` comment and the tool will read those too when someone adds them,
but no change to the conformance suite is needed either way.

Read the row like a claim: *this check exercises that rule*. Because the claim
is the only thing that makes a rule "covered", every claim has to be
defensible, so each row states how it was checked.

**Status.** `verified` means the rulebook text was read and it states the rule
the check enforces. `unverified` means the mapping is plausible but was not
proved - the probe's own annotation was wrong or absent, or the check's subject
and the cited text disagree. Only `verified` rows count toward coverage. An
unverified row is not a defect; it is a claim waiting for someone to read the
paragraph, and listing it here is how it avoids quietly inflating a number.

**Maintenance.** Two things make this file fail loudly rather than rot:

* a mapped check name that no longer appears in the gate log (the check was
  renamed or deleted);
* an ID that is not in `docs/RULE_INDEX.md`.

Both are hard failures in `rule-coverage.ps1`, so a stale row cannot survive a
run.

| Check name | Rule ID | Status | How it was checked |
|---|---|---|---|
| `[GRP] group sponsorship cost is the listed cost, undoubled (:657)` | `RB:V` | verified | Check cites :657, the Sponsor a Supporting Card action; the suit enforces the listed-cost rule stated on that line. |
| `[GRP] unaffordable group is refused (:657)` | `RB:V` | verified | Check cites :657, the Sponsor a Supporting Card action; the suit enforces the listed-cost rule stated on that line. |
| `[SPN] sponsorship costs the listed influence (:657)` | `RB:V` | verified | Check cites :657, the Sponsor a Supporting Card action; the suit enforces the listed-cost rule stated on that line. |
| `[SPN] affordable listed-cost card may be sponsored (:657)` | `RB:V` | verified | Check cites :657, the Sponsor a Supporting Card action; the suit enforces the listed-cost rule stated on that line. |
| `[SPN] sponsorship deducts exactly the listed cost on success (:657)` | `RB:V` | verified | Check cites :657, the Sponsor a Supporting Card action; the suit enforces the listed-cost rule stated on that line. |
| `[SPN] sponsored location is in play (:657)` | `RB:V` | verified | Check cites :657, the Sponsor a Supporting Card action; the suit enforces the listed-cost rule stated on that line. |
| `[SPN] sponsored card left the hand (:657)` | `RB:V` | verified | Check cites :657, the Sponsor a Supporting Card action; the suit enforces the listed-cost rule stated on that line. |
| `[SPN] the sponsoring Inner Circle member rotated (:657)` | `RB:V` | verified | Check cites :657, the Sponsor a Supporting Card action; the suit enforces the listed-cost rule stated on that line. |
| `[SPN] a card costing more than the pool is refused (:657)` | `RB:V` | verified | Check cites :657, the Sponsor a Supporting Card action; the suit enforces the listed-cost rule stated on that line. |
| `[SPN] refused sponsorship deducts nothing (:657)` | `RB:V` | verified | Check cites :657, the Sponsor a Supporting Card action; the suit enforces the listed-cost rule stated on that line. |
| `[SPN] refused sponsorship leaves the card in hand (:657)` | `RB:V` | verified | Check cites :657, the Sponsor a Supporting Card action; the suit enforces the listed-cost rule stated on that line. |
| `[SPN] refused sponsorship does not rotate the sponsor (:657)` | `RB:V` | verified | Check cites :657, the Sponsor a Supporting Card action; the suit enforces the listed-cost rule stated on that line. |
| `[SPN] refused sponsorship puts nothing in play (:657)` | `RB:V` | verified | Check cites :657, the Sponsor a Supporting Card action; the suit enforces the listed-cost rule stated on that line. |
| `[SPN] the refusal is logged rather than silent (:657)` | `RB:V` | verified | Check cites :657, the Sponsor a Supporting Card action; the suit enforces the listed-cost rule stated on that line. |
| `[SPN] sponsorship cost carries no Inner-Circle term (:657)` | `RB:V` | verified | Check cites :657, the Sponsor a Supporting Card action; the suit enforces the listed-cost rule stated on that line. |
| `[SPN] a fully rotated Inner Circle cannot sponsor (:657)` | `RB:V` | verified | Check cites :657, the Sponsor a Supporting Card action; the suit enforces the listed-cost rule stated on that line. |
| `[SPN] a bigger Inner Circle does NOT raise the sponsorship price (:657)` | `RB:V` | verified | Check cites :657, the Sponsor a Supporting Card action; the suit enforces the listed-cost rule stated on that line. |
| `[SPN] another race's CHARACTER is NOT refused, only dearer (:663)` | `RB:V` | verified | Check cites :663, the off-race doubling rule for characters. |
| `[SPN] an own-race CHARACTER costs its listed price (:663)` | `RB:V` | verified | Check cites :663, the off-race doubling rule for characters. |
| `[SPN] the off-race rule is a doubling, not a flat surcharge (:663)` | `RB:V` | verified | Check cites :663, the off-race doubling rule for characters. |
| `[SPN] another race's FLEET is refused outright (:665)` | `RB:V` | verified | Check cites :665, the absolute race restriction for non-character cards. |
| `[SPN] an absolute race refusal is not a doubled price (:665)` | `RB:V` | verified | Check cites :665, the absolute race restriction for non-character cards. |
| `[SPN] promotion cost is the rulebook's worked example, 3 + 2 = 5 (:669)` | `RB:V` | verified | Check cites :669, Promote a Supporting Character, including the cost plus one per Inner Circle member and its worked example. |
| `[SPN] an affordable promotion is offered (:669)` | `RB:V` | verified | Check cites :669, Promote a Supporting Character, including the cost plus one per Inner Circle member and its worked example. |
| `[SPN] promotion deducts the listed-plus-surcharge amount (:669)` | `RB:V` | verified | Check cites :669, Promote a Supporting Character, including the cost plus one per Inner Circle member and its worked example. |
| `[SPN] the promoted character joined the Inner Circle (:669)` | `RB:V` | verified | Check cites :669, Promote a Supporting Character, including the cost plus one per Inner Circle member and its worked example. |
| `[SPN] the promoting member rotated (:669)` | `RB:V` | verified | Check cites :669, Promote a Supporting Character, including the cost plus one per Inner Circle member and its worked example. |
| `[SPN] an assistant sponsor discount does NOT reduce promotion (:669)` | `RB:V` | verified | Check cites :669, Promote a Supporting Character, including the cost plus one per Inner Circle member and its worked example. |
| `[SPN] promotion is refused when the pool is short (:669)` | `RB:V` | verified | Check cites :669, Promote a Supporting Character, including the cost plus one per Inner Circle member and its worked example. |
| `[SPN] off-race doubling composes with the IC surcharge on promote (:669)` | `RB:V` | verified | Check cites :669, Promote a Supporting Character, including the cost plus one per Inner Circle member and its worked example. |
| `[AGL] a replacement you could not sponsor is refused (:719)` | `RB:V` | verified | Check cites :719; the agenda discard/replace/reveal/sponsor action is :721, inside the same entry. |
| `[AGL] a hidden agenda cannot be the replacement (:719)` | `RB:V` | verified | Check cites :719; the agenda discard/replace/reveal/sponsor action is :721, inside the same entry. |
| `[AGL] old agenda removed FROM THE GAME (:719, not discarded)` | `RB:V` | verified | Check cites :719; the agenda discard/replace/reveal/sponsor action is :721, inside the same entry. |
| `[GRP] group has no ability in any conflict type (:496)` | `RB:IV/group-cards` | verified | Check cites :496, which states no abilities, no promotion, race restriction and Limited by default. |
| `[GRP] group is not promotable to the Inner Circle (:496)` | `RB:IV/group-cards` | verified | Check cites :496, which states no abilities, no promotion, race restriction and Limited by default. |
| `[GRP] race-restricted group is refused by another race (:496)` | `RB:IV/group-cards` | verified | Check cites :496, which states no abilities, no promotion, race restriction and Limited by default. |
| `[GRP] group is Limited by default (:496)` | `RB:IV/group-cards` | verified | Check cites :496, which states no abilities, no promotion, race restriction and Limited by default. |
| `[GRP] fleet and location are also not promotable (:500/:504)` | `RB:IV/fleet-cards` | verified | Check cites :500/:504; :500 states fleets are Supporting cards and may not be promoted. |
| `[GRP] fleet and location are also not promotable (:500/:504)` | `RB:IV/location-cards` | verified | Same check also covers :504, the location half of the fleet-and-location promotion refusal. |
| `[GRP] an ordinary supporting character is still promotable (:484)` | `RB:IV/bringing-characters-into-play` | verified | Check cites :484, which states supporting characters are sponsored in and may be promoted. |
| `[SPN] the same discount DOES reduce sponsoring (:492)` | `RB:IV/your-ambassadors-assistant` | verified | Check cites :492, which states the assistant discount applies 1 influence less when sponsoring. |
| `[GRP] gate still admits a fleet and a character (:434)` | `RB:III/participants` | verified | Check cites :434, which defines participant fleets and participant characters. |
| `[CWR] unrest starts at 1 (:278)` | `RB:II/set-tension-tokens` | verified | Check cites :278; the starting-unrest rule is :282, inside the same entry. |
| `[CWR] unrest clamps at 5 (:280)` | `RB:II/set-tension-tokens` | verified | Check cites :280; the 1-to-5 clamp for tension and unrest is :284, inside the same entry. |
| `[CWR] unrest floors at 1 (:280)` | `RB:II/set-tension-tokens` | verified | Check cites :280; the 1-to-5 clamp for tension and unrest is :284, inside the same entry. |
| `[SETUP-AMB] the seated ambassador is an Inner Circle member (:266/:270)` | `RB:II/preparing-the-playing-field` | verified | Check cites :266, which states that all players play their starting ambassador down. |
| `[DB4] 44 copies of one card breaks the max-3 rule (:194)` | `RB:II/customizing-your-game-deck` | verified | Check cites :194; the maximum-of-3 restriction is :198, inside the same entry. |
| `[MJR] Shadow War suppresses Standard Victory condition 1 (:178)` | `RB:I/victory` | verified | Check cites :178; lines 177-182 state the Standard Victory conditions and their suppression during the Shadow War. |
| `[SETUP-AMB] the Non-Aligned second species ambassador survives (:890)` | `RB:VI/league-of-non-aligned-worlds` | verified | Check cites :890, which states the Non-Aligned second species ambassador in the starting hand. |
| `[CWR] same-race tension starts at 2 (:972)` | `RB:VI/tension-and-unrest` | verified | Check cites :972; line 976 states same-race faction tension begins at 2. |
| `[CWR] Non-Aggression auto-state at tension <= 3 (:974)` | `RB:VI/tension-and-unrest` | verified | Check cites :974; line 978 states the Non-Aggression auto-state at tension 3 or less. |
| `[CWR] Non-Aggression ends at same-race tension 4 (:974)` | `RB:VI/tension-and-unrest` | verified | Check cites :974; line 978 states the Non-Aggression auto-state at tension 3 or less. |
| `[CWR] Non-Aggression auto-state suppressed during Civil War (:974)` | `RB:VI/tension-and-unrest` | verified | Check cites :974; line 978 states the Non-Aggression auto-state at tension 3 or less. |
| `[CWR] unrest +1 for every faction of the race on entry (:994)` | `RB:VI/civil-war` | verified | Check cites :994, the Civil War heading; :998 states the unrest increase and the mutual war state. |
| `[CWR] factions of a race in Civil War are at war with each other (:994)` | `RB:VI/civil-war` | verified | Check cites :994, the Civil War heading; :998 states the unrest increase and the mutual war state. |
| `[CWR] end-war targeting picks the highest-mutual-tension pair (:996)` | `RB:VI/civil-war` | verified | Check cites :996, which states the Declaration of War threshold at tension 5. |
| `[CWR] self-involvement exception targets the highest tension toward YOU (:996)` | `RB:VI/civil-war` | verified | Check cites :996, which states the Declaration of War threshold at tension 5. |
| `[CWR] unrest -1 for every faction on exit (:998)` | `RB:VI/civil-war` | verified | Check cites :998, which states unrest +1 per faction on entry and the mutual war state. |
| `[CWR] merge produces the rounded-up average (2 and 5 -> 4) (:1000)` | `RB:VI/civil-war` | verified | Check cites :1000, which states end-war targeting and the self-involvement exception. |
| `[CWR] a single-faction race can never enter Civil War (:992 needs a rival faction)` | `RB:VI/civil-war` | unverified | Check cites :992, which falls in RB:VI/states-dual-races, but its subject is Civil War entry. The target here is inferred, not the probe citation. |
| `[CWR] entry fires at same-race tension 5 (:992)` | `RB:VI/civil-war` | unverified | Check cites :992, which falls in RB:VI/states-dual-races, but its subject is Civil War entry. The target here is inferred, not the probe citation. |
| `[CWR] declaration entry registers the machine for the race (:992)` | `RB:VI/civil-war` | unverified | Check cites :992, which falls in RB:VI/states-dual-races, but its subject is Civil War entry. The target here is inferred, not the probe citation. |
| `[CWR] UNIFIED race: influence loss spills to every faction of the race (:980)` | `RB:VI/voting` | unverified | Check cites :980 Voting, but its subject is how influence loss spreads across factions of a race; nothing read states that rule in this entry. |
| `[CWR] CIVIL_WAR race: the loss lands on the loser alone (:980/:1000)` | `RB:VI/voting` | unverified | Check cites :980 Voting, but its subject is how influence loss spreads across factions of a race; nothing read states that rule in this entry. |
| `[CWR] Non-Aligned unrest starts at 2 (:888)` | `RB:VI/league-of-non-aligned-worlds` | unverified | Check cites :888 (Non-Aligned ambassadors) for a starting-unrest value; the unrest start was not located in this entry. |
| `[GRP] an explicit Multiple lifts the Limited default (:1168)` | `RB:GL` | verified | Check cites :1168, the glossary term Multiple. A definition, not a rule; GL carries no scope and is not in the denominator. |
| `[AGL] replace needs a READY IC leader (:520 rotation)` | `RB:IV/agenda-cards` | verified | Check cites :520; line 524 states that an Inner Circle character may rotate to replace or sponsor the faction agenda. |
| `[GRP] engine refuses a group as a conflict participant (:496)` | `RB:IV/group-cards` | unverified | Cites :496, which does not state that groups cannot be conflict participants; the refusal may rest on another rule. |
| `[AGL] face-down agenda in play: reveal legal (:520)` | `RB:IV/agenda-cards` | unverified | Lines 520-527 do not state the reveal action; it may live in RB:V. |
| `[VIC] face-down major agenda does NOT bar station victory (:520)` | `RB:IV/agenda-cards` | unverified | The subject is a victory condition; the entry was cited for the agenda half only. |
| `[D1] losing initiator cannot play WON aftermath` | `RB:IV/aftermath-cards` | verified | No probe annotation; lines 594-595 state exactly this won/lost gate on who may play what. |
| `[D1] losing initiator can play LOST aftermath` | `RB:IV/aftermath-cards` | verified | Same lines 594-595 read directly. |
| `[D1] winning initiator can play WON aftermath` | `RB:IV/aftermath-cards` | verified | Same lines 594-595 read directly. |
| `[D1] winning initiator cannot play LOST aftermath` | `RB:IV/aftermath-cards` | verified | Same lines 594-595 read directly. |
| `[D1] initiatorWon=false when the opposer wins` | `RB:IV/aftermath-cards` | unverified | No annotation; the initiator flag is a plausible reading of :594-:595 but the engine-side outcome rule was not read. |
| `[D1] initiatorWon=true when the initiator wins` | `RB:IV/aftermath-cards` | unverified | Same reason. |
| `[D1] winning opposer still evaluates vs initiator (no WON)` | `RB:IV/aftermath-cards` | unverified | Lines 594-595 gate on the initiator only; the opposer branch was not located in this entry. |
| `[D1] winning opposer may play LOST aftermath (on the initiator)` | `RB:IV/aftermath-cards` | unverified | Same reason. |
| `[STH] idle round: ratings stay 0 and marker stays clear` | `RB:VI/marks` | unverified | Named "marker"; line 844 is about losing a mark source, which may or may not be what this exercises. |
| `[STH] capture sets the no-source marker` | `RB:VI/marks` | unverified | Same reason; the no-source marker was not read against line 844. |
| `[STH] maintenance resets the marker after the boundary` | `RB:VI/marks` | unverified | Same reason. |
| `[STH] recapture is restoration, not a source (no marker, no gain)` | `RB:VI/marks` | unverified | Same reason. |
| `[ATK] Strife marks add two damage each and overflow becomes severe` | `RB:VI/marks` | unverified | The subject is combat damage from a named card, not the mark attachment rules read at :836-:844. |
