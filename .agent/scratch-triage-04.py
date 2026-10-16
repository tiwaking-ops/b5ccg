#!/usr/bin/env python3
"""WP-03 / B5-0004: Precise printing-diff triage.
Extracts CardEffects table literals, finds single-literal titles,
compares premiere vs deluxe text for each.
"""
import json, re, os

ROOT = r"C:/temp/projects/b5ccg/b5ccg/resources/cards"
SRC = r"C:/temp/projects/b5ccg/b5ccg/src/b5ccg/engine/CardEffects.java"

premiere = {c["id"]: c for c in json.load(open(os.path.join(ROOT, "premiere.json")))}
deluxe = {c["id"]: c for c in json.load(open(os.path.join(ROOT, "deluxe.json")))}

all_pool_ids = set(premiere.keys()) | set(deluxe.keys())

# Build title -> {id, set, text} map
prem_by_title = {}
for c in premiere.values():
    prem_by_title.setdefault(c["title"], []).append(c)
del_by_title = {}
for c in deluxe.values():
    del_by_title.setdefault(c["title"], []).append(c)

src = open(SRC, encoding="utf-8").read()
# Remove comments
src_nc = re.sub(r'/\*.*?\*/', '', src, flags=re.DOTALL)
src_nc = re.sub(r'//.*$', '', src_nc, flags=re.MULTILINE)

# Extract ALL put() and add() string arguments that look like card ids
literals = set()
for m in re.finditer(r'\.put\("([^"]+)"', src_nc):
    val = m.group(1)
    if re.match(r'^[a-z][a-z0-9_]*$', val):
        literals.add(val)
for m in re.finditer(r'\.add\("([^"]+)"', src_nc):
    val = m.group(1)
    if re.match(r'^[a-z][a-z0-9_]*$', val):
        literals.add(val)
for m in re.finditer(r'\.equals\("([^"]+)"\)', src_nc):
    val = m.group(1)
    if re.match(r'^[a-z][a-z0-9_]*$', val):
        literals.add(val)
for m in re.finditer(r'"([^"]+)"', src_nc):
    val = m.group(1)
    if re.match(r'^[a-z][a-z0-9_]*$', val):
        literals.add(val)

# Filter to pool ids only
behaviour_literals = literals & all_pool_ids
print(f"Total behaviour literals in CardEffects (pool ids): {len(behaviour_literals)}")

# Categorize: bare (premiere) vs de_ (deluxe)
bare_lits = sorted(l for l in behaviour_literals if not l.startswith("de_"))
de_lits = sorted(l for l in behaviour_literals if l.startswith("de_"))
print(f"  Bare (premiere) literals: {len(bare_lits)}")
print(f"  de_ (deluxe) literals: {len(de_lits)}")

# Special-cased titles: title in both sets, at least one printing has a literal
title_to_lits = {}
for lit in behaviour_literals:
    card = None
    set_name = "prem" if not lit.startswith("de_") else "del"
    if lit in premiere:
        card = premiere[lit]
        set_name = "prem"
    elif lit in deluxe:
        card = deluxe[lit]
        set_name = "del"
    if card:
        title_to_lits.setdefault(card["title"], {})[set_name] = lit

both_set_titles = set(prem_by_title.keys()) & set(del_by_title.keys())
special_cased = {t: v for t, v in title_to_lits.items() if t in both_set_titles}
print(f"\nSpecial-cased titles (both printings + at least one literal): {len(special_cased)}")

both_lit = {t: v for t, v in special_cased.items() if len(v) == 2}
single_lit = {t: v for t, v in special_cased.items() if len(v) == 1}
print(f"  Both-literal: {len(both_lit)}")
print(f"  Single-literal: {len(single_lit)}")

# Triage single-literal titles
print(f"\n{'='*80}")
print(f"SINGLE-LITERAL TITLE TRIAGE (B5-0004)")
print(f"{'='*80}")
unchanged = []
changed = []
for title in sorted(single_lit.keys()):
    lit_map = single_lit[title]
    has_prem = "prem" in lit_map
    has_del = "del" in lit_map
    
    prem_card = premiere.get(lit_map.get("prem", ""))
    del_card = deluxe.get(lit_map.get("del", ""))
    
    # If prem has literal, find deluxe counterpart by title
    if has_prem:
        prem_text = prem_card["text"]
        del_text = del_card["text"] if del_card else "NO DELUXE CARD"
    else:
        prem_text = prem_card["text"] if prem_card else "NO PREMIERE CARD"
        del_text = del_card["text"]
    
    text_changed = prem_text != del_text
    status = "D5-CHANGED" if text_changed else "UNIFIED"
    
    if text_changed:
        changed.append(title)
    else:
        unchanged.append(title)
    
    # Determine card type
    card = prem_card or del_card
    card_type = card.get("type", "?") if card else "?"
    
    print(f"\n  [{status}] {title} ({card_type})")
    print(f"    literal: {lit_map}")
    if text_changed:
        print(f"    PREM text: {prem_text}")
        print(f"    DEL  text: {del_text}")
        print(f"    → D5: Deluxe governs, implement Deluxe behaviour for both")

print(f"\n{'='*80}")
print(f"TRIAGE SUMMARY")
print(f"{'='*80}")
print(f"Total single-literal titles: {len(single_lit)}")
print(f"  Text unchanged (canonical key handles for non-aftermath): {len(unchanged)}")
print(f"  Text changed (D5 - Deluxe governs): {len(changed)}")
print(f"\nChanged-text titles:")
for t in changed:
    print(f"  - {t}")
print(f"\nUnchanged-text titles:")
for t in unchanged:
    print(f"  - {t}")

# Also check: which of the 58 from the proposal are NOT yet resolved?
# The 58 = 33 bare-only + 25 de_-only single-literal titles (original)
# B5-0003 resolved: Intrigues Mature (added canonical key), Assigning Blame (removed dead literal)
# Let's verify
print(f"\n{'='*80}")
print(f"CHECK: Titles specifically mentioned in B5-0003")
print(f"{'='*80}")
# Intrigues Mature: bare-only, text unchanged
if "Intrigues Mature" in prem_by_title and "Intrigues Mature" in del_by_title:
    p = prem_by_title["Intrigues Mature"][0]
    d = del_by_title["Intrigues Mature"][0]
    print(f"Intrigues Mature: prem text == del text? {p['text'] == d['text']}")
    print(f"  has bare literal? {'event_intrigues_mature' in behaviour_literals}")
    print(f"  has de_ literal? {'de_event_intrigues_mature' in behaviour_literals}")

# Assigning Blame
if "Assigning Blame" in prem_by_title:
    p = prem_by_title["Assigning Blame"][0]
    print(f"Assigning Blame: premiere id={p['id']}, has literal? {p['id'] in behaviour_literals}")
if "Assigning Blame" in del_by_title:
    print(f"Assigning Blame: has deluxe printing? Yes")
else:
    print(f"Assigning Blame: has deluxe printing? NO (premiere-only)")
