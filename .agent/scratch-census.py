#!/usr/bin/env python3
"""Check census covered flags."""
import json
census = json.load(open('b5ccg/resources/census/effect-census.json'))
covered = [r for r in census['records'] if r.get('covered')]
print(f'Covered titles in census: {len(covered)}')
for r in covered:
    print(f'  {r["title"]} ({r.get("class","?")}) printing={r.get("printing","?")}')

# Also check the 5 changed ambassadors and 4 unchanged in the census
print('\n--- Key titles in census ---')
for title in ["Adira Tyree", "Delenn", "G'Kar", "Jeffrey Sinclair", "Londo Mollari",
              "Centauri Agent", "Centauri Aide", "Centauri Captain", "Intrigues Mature"]:
    for r in census['records']:
        if r['title'] == title:
            print(f'  {title}: class={r.get("class","?")}, printing={r.get("printing","?")}, covered={r.get("covered",False)}, starter={r.get("starter",False)}')
            break
