#!/usr/bin/env python3
"""Fixed WP-03 / B5-0004 triage (Unicode-safe)."""
import json, re, os

ROOT = r"C:/temp/projects/b5ccg/b5ccg/resources/cards"
SRC = r"C:/temp/projects/b5ccg/b5ccg/src/b5ccg/engine/CardEffects.java"

premiere = {c["id"]: c for c in json.load(open(os.path.join(ROOT, "premiere.json")))}
deluxe = {c["id"]: c for c in json.load(open(os.path.join(ROOT, "deluxe.json")))}

all_pool_ids = set(premiere.keys()) | set(deluxe.keys())

# Build title -> list of cards map
prem_by_title = {}
for c in premiere.values():
    prem_by_title.setdefault(c["title"], []).append(c)
del_by_title = {}
for c in deluxe.values():
    del_by_title.setdefault(c["title"], []).append(c)

src = open(SRC, encoding="utf-8").read()
src_nc = re.sub(r'/\*.*?\*/', '', src, flags=re.DOTALL)
src_nc = re.sub(r'//.*$', '', src_nc, flags=re.MULTILINE)

# Extract ALL string literals that look like card ids
literals = set()
for m in re.finditer(r'"([^"]+)"', src_nc):
    val = m.group(1)
    if re.match(r'^[a-z][a-z0-9_]*$', val):
        literals.add(val)

# Filter to pool ids only
behaviour_literals = literals & all_pool_ids
print(f"Total behaviour literals in CardEffects (pool ids): {len(behaviour_literals)}")

bare_lits = sorted(l for l in behaviour_literals if not l.startswith("de_"))
de_lits = sorted(l for l in behaviour_literals if l.startswith("de_"))
print(f"  Bare (premiere) literals: {len(bare_lits)}")
print(f"  de_ (deluxe) literals: {len(de_lits)}")

# Group literals by title
title_to_lits = {}
for lit in behaviour_literals:
    card = None
    if lit in premiere:
        card = premiere[lit]
    elif lit in deluxe:
        card = deluxe[lit]
    if card:
        title_to_lits.setdefault(card["title"], {})[lit] = lit

both_set_titles = set(prem_by_title.keys()) & set(del_by_title.keys())
special_cased = {t: v for t, v in title_to_lits.items() if t in both_set_titles}
print(f"\nSpecial-cased titles (both printings + at least one literal): {len(special_cased)}")

both_lit = {t: v for t, v in special_cased.items() if len(v) == 2}
single_lit = {t: v for t, v in special_cased.items() if len(v) == 1}
print(f"  Both-literal: {len(both_lit)}")
print(f"  Single-literal: {len(single_lit)}")

# Also count aftermath single-literals separately
aftermath_single = {t: v for t, v in single_lit.items() if t in prem_by_title or t in del_by_title}
am_titles_in_pool = set()
for c in premiere.values():
    if c["id"].startswith("aftermath_") or c["id"].startswith("de_am_"):
        am_titles_in_pool.add(c["title"])
for c in deluxe.values():
    if c["id"].startswith("aftermath_") or c["id"].startswith("de_am_"):
        am_titles_in_pool.add(c["title"])

am_single = {t: v for t, v in single_lit.items() if t in am_titles_in_pool}
non_am_single = {t: v for t, v in single_lit.items() if t not in am_titles_in_pool}

print(f"\n  Non-aftermath single-literal: {len(non_am_single)}")
print(f"  Aftermath single-literal: {len(am_single)}")

# Triage single-literal titles
print(f"\n{'='*80}")
print("SINGLE-LITERAL TITLE TRIAGE (B5-0004)")
print(f"{'='*80}")
unchanged = []
changed = []
premiere_only = []
for title in sorted(single_lit.keys()):
    lit_map = single_lit[title]
    is_am = title in am_titles_in_pool
    
    # Find the cards by title
    prem_card = prem_by_title.get(title, [None])[0]
    del_card = del_by_title.get(title, [None])[0]
    
    has_prem = "prem" not in lit_map or any(l in premiere for l in lit_map)
    has_del = any(l in deluxe for l in lit_map)
    
    # Determine if prem has a literal
    prem_lit = None
    del_lit = None
    for l in lit_map:
        if l in premiere:
            prem_lit = l
        elif l in deluxe:
            del_lit = l
    
    if prem_lit:
        prem_text = prem_card["text"] if prem_card else "NO PREMIERE CARD"
    if del_lit:
        del_text = del_card["text"] if del_card else "NO DELUXE CARD"
    
    # For single-literal: one side has a literal, the other doesn't
    # But the card may exist in both sets with same/different text
    if prem_lit and del_card:
        text_changed = prem_card["text"] != del_card["text"]
    elif del_lit and prem_card:
        text_changed = prem_card["text"] != del_card["text"]
    else:
        # Could be premiere-only or deluxe-only card
        text_changed = False
    
    category = "AFTERMATH" if is_am else "NON-AFTERMATH"
    if prem_lit and not del_lit:
        lit_side = "PREMIERE-LITERAL"
        # Need to compare texts
        if del_card and prem_card:
            text_changed = prem_card["text"] != del_card["text"]
    elif del_lit and not prem_lit:
        lit_side = "DELUXE-LITERAL"
    else:
        lit_side = "?"
    
    status = "D5-CHANGED" if text_changed else "UNIFIED"
    
    if text_changed and not is_am:
        changed.append(title)
    elif not text_changed and not is_am:
        unchanged.append(title)
    elif is_am:
        if text_changed:
            changed.append(title + " (AM)")
        else:
            unchanged.append(title + " (AM)")
    
    card = prem_card or del_card
    card_type = card.get("type", "?") if card else "?"
    
    prem_text_str = prem_card["text"][:80] if prem_card else "NO PREMIERE CARD"
    del_text_str = del_card["text"][:80] if del_card else "NO DELUXE CARD"
    
    print(f"\n  [{status}] {title} ({card_type}) [{category}]")
    print(f"    literal: {lit_map}")
    print(f"    PREM: id={prem_card['id'] if prem_card else 'NONE'}, text={prem_text_str}")
    print(f"    DEL:  id={del_card['id'] if del_card else 'NONE'}, text={del_text_str}")
    if text_changed:
        print(f"    -> D5: Deluxe governs, implement Deluxe behaviour for both")

print(f"\n{'='*80}")
print("TRIAGE SUMMARY")
print(f"{'='*80}")
print(f"Total single-literal titles: {len(single_lit)}")
print(f"  Non-aftermath unchanged (canonical key handles): {len([t for t in unchanged if '(AM)' not in t])}")
print(f"  Non-aftermath changed (D5): {len([t for t in changed if '(AM)' not in t])}")
print(f"  Aftermath: {len([t for t in unchanged if '(AM)' in t]) + len([t for t in changed if '(AM)' in t])}")
print(f"  Changed-text titles:")
for t in changed:
    print(f"  - {t}")
print(f"\n  Unchanged-text titles:")
for t in unchanged:
    print(f"  - {t}")
