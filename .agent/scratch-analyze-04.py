#!/usr/bin/env python3
"""WP-03 / B5-0004: Printing-diff triage analysis.
Extracts all CardEffects literals, cross-references the card pool,
identifies the 82 special-cased titles and 58 single-literal titles,
and compares premiere vs. deluxe text for each single-literal title.
"""
import json, re, os

ROOT = r"C:/temp/projects/b5ccg/b5ccg/resources/cards"
SRC = r"C:/temp/projects/b5ccg/b5ccg/src/b5ccg/engine/CardEffects.java"

premiere = json.load(open(os.path.join(ROOT, "premiere.json")))
deluxe = json.load(open(os.path.join(ROOT, "deluxe.json")))

# Build id -> card maps
prem_by_id = {c["id"]: c for c in premiere}
del_by_id = {c["id"]: c for c in deluxe}

# Build title -> {set: card} maps
prem_by_title = {}
for c in premiere:
    prem_by_title.setdefault(c["title"], c)
del_by_title = {}
for c in deluxe:
    del_by_title.setdefault(c["title"], c)

# All 829 pool ids
all_pool_ids = set(prem_by_id.keys()) | set(del_by_id.keys())
print(f"Total pool ids: {len(all_pool_ids)} (premiere={len(prem_by_id)}, deluxe={len(del_by_id)})")

# --- Extract all string literals from CardEffects.java that look like card ids ---
# We look for put("...") and add("...") calls
src = open(SRC, encoding="utf-8").read()

# Remove comments to avoid false positives from javadoc
# Simple approach: remove /* ... */ blocks
src_no_comments = re.sub(r'/\*.*?\*/', '', src, flags=re.DOTALL)
# Remove line comments
src_no_comments = re.sub(r'//.*$', '', src_no_comments, flags=re.MULTILINE)

# Find all string literals that look like card ids
# Card ids: start with lowercase, contain letters, digits, underscores
# Patterns: .put("id", ...) or .add("id")
literals = set()
for m in re.finditer(r'\.put\("([^"]+)"', src_no_comments):
    val = m.group(1)
    if re.match(r'^[a-z][a-z0-9_]*$', val) and not val.startswith("de_am_"):
        # Could be a card id or a map key. Filter to card-like ids.
        literals.add(val)
for m in re.finditer(r'\.add\("([^"]+)"', src_no_comments):
    val = m.group(1)
    if re.match(r'^[a-z][a-z0-9_]*$', val):
        literals.add(val)

# Also check equals() comparisons for specific ids like "de_am_negotiated_surrender"
for m in re.finditer(r'\.equals\("([^"]+)"\)', src_no_comments):
    val = m.group(1)
    if re.match(r'^[a-z][a-z0-9_]*$', val):
        literals.add(val)

# Also check Arrays.asList string arrays
for m in re.finditer(r'"([^"]+)"', src_no_comments):
    val = m.group(1)
    if re.match(r'^[a-z][a-z0-9_]*$', val):
        literals.add(val)

# Filter to only those that are actual pool ids (exist in premiere or deluxe)
behaviour_literals = literals & all_pool_ids
non_pool = literals - all_pool_ids

print(f"\nAll string literals matching card-id pattern in CardEffects: {len(literals)}")
print(f"  Resolvable to pool: {len(behaviour_literals)}")
print(f"  Non-pool (non-card, fixtures, etc.): {len(non_pool)}")

# Also check NON_POOL_FIXTURE_IDS (currently empty)
print(f"\nNON_POOL_FIXTURE_IDS: {len(set())}")

# behaviourReferences() returns these 10 character ids
behaviour_refs = {
    "char_jeffrey_sinclair", "char_gkar", "char_delenn", "char_londo_mollari",
    "char_adira_tyree", "char_ashan", "char_carn_mollari",
    "char_centauri_agent", "char_centauri_aide", "char_centauri_captain",
}
print(f"behaviourReferences() returns: {len(behaviour_refs)} ids")
print(f"Full behaviour literals in tables: {len(behaviour_literals)}")

# --- Categorize behaviour literals ---
bare_literals = {l for l in behaviour_literals if not l.startswith("de_")}
de_literals = {l for l in behaviour_literals if l.startswith("de_")}
print(f"\nBare (premiere) literals in tables: {len(bare_literals)}")
print(f"de_ (deluxe) literals in tables: {len(de_literals)}")

# --- Find special-cased titles: titles that appear in BOTH premiere and deluxe,
#     and where at least one printing has a behaviour literal ---
# A "special-cased title" = a title in both sets where at least one of:
#   - the premiere id (bare) has a CardEffects literal
#   - the deluxe id (de_) has a CardEffects literal

# Build title -> literals map
title_to_literals = {}
for lit in behaviour_literals:
    if lit in prem_by_id:
        title_to_literals.setdefault(prem_by_id[lit]["title"], {})["prem"] = lit
    elif lit in del_by_id:
        title_to_literals.setdefault(del_by_id[lit]["title"], {})["del"] = lit

# Special-cased titles: have at least one literal AND appear in both sets
both_set_titles = set(prem_by_title.keys()) & set(del_by_title.keys())
special_cased_titles = {t: v for t, v in title_to_literals.items() if t in both_set_titles}

print(f"\n=== SPECIAL-CASED TITLES (in CardEffects + both printings in pool) ===")
print(f"Count: {len(special_cased_titles)}")

# --- Identify single-literal titles ---
single_literal_titles = {t: v for t, v in special_cased_titles.items()
                         if len(v) == 1}
both_literal_titles = {t: v for t, v in special_cased_titles.items()
                       if len(v) == 2}

print(f"\nBoth-literal titles (both printings have effects): {len(both_literal_titles)}")
print(f"Single-literal titles (only one printing has an effect): {len(single_literal_titles)}")

# --- For single-literal titles, compare text ---
print(f"\n=== SINGLE-LITERAL TITLE TRIAGE ===")
unchanged = []
changed = []
for title in sorted(single_literal_titles.keys()):
    lit_map = single_literal_titles[title]
    has_prem = "prem" in lit_map
    has_del = "del" in lit_map

    prem_card = prem_by_title.get(title, {})
    del_card = del_by_title.get(title, {})

    prem_text = prem_card.get("text", "")
    del_text = del_card.get("text", "")

    is_aftermath = title.startswith("Aftermath") or "aftermath" in lit_map.get("prem", "") or "aftermath" in lit_map.get("del", "")

    text_changed = prem_text != del_text
    status = "CHANGED-D5" if text_changed else "UNIFIED"

    if text_changed:
        changed.append((title, lit_map, prem_text, del_text))
    else:
        unchanged.append((title, lit_map, prem_text, del_text))

    lit_str = lit_map.get("prem", "?") if has_prem else lit_map.get("del", "?")
    print(f"\n  {status}: {title}")
    print(f"    literal: {lit_str} (prem={has_prem}, del={has_del})")
    if has_prem:
        print(f"    prem text: {prem_text[:120]}")
        print(f"    del  text: {del_text[:120]}")
    if has_del:
        print(f"    del literal: {lit_map.get('del', '?')}")
        # Find the premiere counterpart
        prem_counterpart = prem_by_title.get(title, {})
        if prem_counterpart:
            print(f"    prem text: {prem_counterpart.get('text', '')[:120]}")
        else:
            print(f"    prem text: NO PREMIERE COUNTERPART")

print(f"\n\n=== SUMMARY ===")
print(f"Single-literal titles: {len(single_literal_titles)}")
print(f"  UNCHANGED text (canonical key complete): {len(unchanged)}")
print(f"  CHANGED text (D5 Deluxe governs): {len(changed)}")
print(f"\nBoth-literal titles: {len(both_literal_titles)}")

# Check the aftermath slug divergence issue
print(f"\n=== AFTERMATH SLUG DIVERGENCE ===")
# For aftermath cards: prem id = aftermath_X, del id = de_am_X
# canonicalKey(de_am_X) = am_X, but prem id = aftermath_X — they DON'T unify
aftermath_in_pool = [c for c in del_by_id.values() if c["id"].startswith("de_am_")]
aftermath_prem_in_pool = [c for c in prem_by_id.values() if c["id"].startswith("aftermath_")]
print(f"Deluxe aftermath cards in pool: {len(aftermath_in_pool)}")
print(f"Premiere aftermath cards in pool: {len(aftermath_prem_in_pool)}")

# Check which aftermath titles have literals
am_del_with_literal = set()
am_prem_with_literal = set()
for lit in behaviour_literals:
    if lit.startswith("de_am_"):
        am_del_with_literal.add(lit)
    elif lit.startswith("aftermath_"):
        am_prem_with_literal.add(lit)

print(f"Deluxe aftermath cards WITH CardEffects literal: {len(am_del_with_literal)}")
print(f"Premiere aftermath cards WITH CardEffects literal: {len(am_prem_with_literal)}")

# For each aftermath title, check if both or just one has a literal
am_prem_titles = {prem_by_id[l].get("title","") for l in am_prem_with_literal if l in prem_by_id}
am_del_titles = {del_by_id[l].get("title","") for l in am_del_with_literal if l in del_by_id}
am_both = am_prem_titles & am_del_titles
am_prem_only = am_prem_titles - am_del_titles
am_del_only = am_del_titles - am_prem_titles
print(f"\nAftermath with BOTH literals: {len(am_both)}")
print(f"Aftermath with PREMISE-only literal: {len(am_prem_only)}")
print(f"Aftermath with DELUXE-only literal: {len(am_del_only)}")
print(f"Total single-literal aftermath: {len(am_prem_only) + len(am_del_only)}")
