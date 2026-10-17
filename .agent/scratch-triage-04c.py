#!/usr/bin/env python3
"""Show full text for changed-text ambassadors + check deluxe existence."""
import json, os

ROOT = r"C:/temp/projects/b5ccg/b5ccg/resources/cards"
premiere = {c["id"]: c for c in json.load(open(os.path.join(ROOT, "premiere.json")))}
deluxe = {c["id"]: c for c in json.load(open(os.path.join(ROOT, "deluxe.json")))}

changed = [
    "Adira Tyree",
    "Delenn",
    "G'Kar",
    "Jeffrey Sinclair",
    "Londo Mollari",
]

print("="*80)
print("CHANGED-TEXT AMBASSADORS — full text comparison")
print("="*80)
for title in changed:
    p = next((c for c in premiere.values() if c["title"] == title), None)
    d = next((c for c in deluxe.values() if c["title"] == title), None)
    print(f"\n--- {title} ---")
    print(f"  PREMIERE: id={p['id'] if p else 'NONE'}")
    print(f"  text: {p['text'] if p else 'NONE'}")
    print(f"  DELUXE: id={d['id'] if d else 'NONE'}")
    print(f"  text: {d['text'] if d else 'NONE'}")
    if p and d:
        print(f"  text same? {p['text'] == d['text']}")

# Also check: does de_char_adira_tyree, de_char_delenn etc exist in pool?
print("\n" + "="*80)
print("DELUXE AMBASSADOR POOL CHECK")
print("="*80)
for title in changed:
    p = next((c for c in premiere.values() if c["title"] == title), None)
    d = next((c for c in deluxe.values() if c["title"] == title), None)
    if p and d:
        print(f"  {title}: prem={p['id']}, del={d['id']} — both in pool")
    elif p and not d:
        print(f"  {title}: prem={p['id']}, del=NONE — PREMIERE ONLY")
    elif d and not p:
        print(f"  {title}: prem=NONE, del={d['id']} — DELUXE ONLY")

# Check what's in the de_ prefix for characters
print("\n" + "="*80)
print("ALL de_char_* in pool")
print("="*80)
for c in deluxe.values():
    if c["id"].startswith("de_char_"):
        print(f"  {c['id']}: {c['title']}")

# Check all char_* in pool (premiere)
print("\n  ALL char_* in pool (premiere)")
for c in premiere.values():
    if c["id"].startswith("char_"):
        print(f"  {c['id']}: {c['title']}")
