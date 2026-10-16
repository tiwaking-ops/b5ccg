#!/usr/bin/env python3
"""Detailed analysis: pool literals vs census coverage."""
import json, os, re

ROOT = r'C:/temp/projects/b5ccg/b5ccg/resources/cards'
premiere = {c['id']: c for c in json.load(open(os.path.join(ROOT, 'premiere.json')))}
deluxe = {c['id']: c for c in json.load(open(os.path.join(ROOT, 'deluxe.json')))}
all_pool = dict(premiere, **deluxe)

with open(r'C:/temp/projects/b5ccg/b5ccg/src/b5ccg/engine/CardEffects.java', encoding='utf-8') as f:
    src = f.read()

# Remove comments
src_nc = re.sub(r'/\*.*?\*/', '', src, flags=re.DOTALL)
src_nc = re.sub(r'//.*$', '', src_nc, flags=re.MULTILINE)

# Extract all string literals that look like card ids
lits = set()
for m in re.finditer(r'"([^"]+)"', src_nc):
    val = m.group(1)
    if re.match(r'^[a-z][a-z0-9_]*$', val):
        lits.add(val)

# Filter to pool ids
pool_lits = sorted(l for l in lits if l in all_pool)
print(f'Pool literals in CardEffects: {len(pool_lits)}')

titles = sorted(set(all_pool[l]['title'] for l in pool_lits))
print(f'Unique titles from pool literals: {len(titles)}')

# Load census
census = json.load(open(r'C:/temp/projects/b5ccg/b5ccg/resources/census/effect-census.json', encoding='utf-8'))
covered_titles = set(r['title'] for r in census['records'] if r.get('covered'))
not_covered = set(titles) - covered_titles
extra_covered = covered_titles - set(titles)

print(f'Covered in census: {len(covered_titles)}')
print(f'Pool lit titles NOT covered in census: {len(not_covered)}')
for t in sorted(not_covered):
    print(f'  NOT covered: {t}')
print(f'Census covered but NOT in pool lit titles: {len(extra_covered)}')
for t in sorted(extra_covered):
    print(f'  Extra covered: {t}')

# Character IDs currently hardcoded in behaviourReferences()
chars = ['char_jeffrey_sinclair', 'char_gkar', 'char_delenn', 'char_londo_mollari',
         'char_adira_tyree', 'char_ashan', 'char_carn_mollari',
         'char_centauri_agent', 'char_centauri_aide', 'char_centauri_captain']
print(f'\nCharacter IDs in behaviourReferences(): {len(chars)}')
for c in chars:
    title = all_pool.get(c, {}).get('title', 'NOT IN POOL')
    print(f'  {c}: "{title}"')

# Changed-text ambassadors
print('\n--- Changed-text ambassadors ---')
changed = ['Adira Tyree', 'Delenn', "G'Kar", 'Jeffrey Sinclair', 'Londo Mollari']
for title in changed:
    p = next((c for c in premiere.values() if c['title'] == title), None)
    d = next((c for c in deluxe.values() if c['title'] == title), None)
    print(f'{title}:')
    print(f'  prem id={p["id"] if p else "NONE"}, text={p["text"][:120] if p else "NONE"}')
    print(f'  del  id={d["id"] if d else "NONE"}, text={d["text"][:120] if d else "NONE"}')
    print(f'  text same? {p["text"] == d["text"] if p and d else "N/A"}')

# Check if char_* ids are in pool literal set
print('\n--- Character ID presence in pool literals ---')
for c in chars:
    in_lits = c in pool_lits
    print(f'  {c}: in pool literals? {in_lits}')

# Check what the 9 single-literal titles look like
print('\n--- 9 Single-literal titles ---')
# The 58 pool literals minus the aftermath single-literals
for c in ['char_centauri_agent', 'char_centauri_aide', 'char_centauri_captain',
          'event_intrigues_mature', 'char_adira_tyree', 'char_delenn',
          'char_gkar', 'char_jeffrey_sinclair', 'char_londo_mollari']:
    p = all_pool.get(c)
    d_key = c.replace('char_', 'de_char_') if c.startswith('char_') else None
    d_key = c.replace('event_', 'de_event_') if c.startswith('event_') else (d_key if d_key else None)
    d = all_pool.get(d_key) if d_key else None
    print(f'  {c}: "{p["title"] if p else "NONE"}" -- deluxe twin: {d_key} = "{d["title"] if d else "NONE"}"')
