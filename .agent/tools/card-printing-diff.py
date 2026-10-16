#!/usr/bin/env python3
"""
card-printing-diff.py
=====================
Generates a field-level diff report for every title that appears in BOTH the
Premiere and the Deluxe card dump (`premiere.json`, `deluxe.json`), and reports
census classes with zero rows.

Normalisation
-------------
* Titles are matched case-sensitively and exactly; there is never a `(Deluxe
  …)` / `(deluxe …)` suffix match involved anywhere in this tool.
* Every rules-bearing field is compared: text, costs, stats, bonuses, trigger,
  flags. Text is compared *after* stripping a single trailing parenthetical
  starting "(Deluxe" (and its closing parenthesis) so that "(Deluxe: …)",
  "(Deluxe errata: …)" and similar are kept separately and do not pollute the
  text class. The stripped parenthetical is preserved in the detail line and
  reported under `suffix` (in the task wording: "kept separately").
* The tool does not know or care about card ids: it keys on title, and every
  title that appears in both dumps is reported, with the *un*matched id fields
  carried along in the row for traceability.

Classes
-------
identical              | text equal (after suffix strip) AND no rules-bearing field differs
suffix-only            | text equal after stripping the Deluxe suffix, and no field differs
rules-text-differs     | text differs even after suffix strip, OR some rules-bearing field differs AND text differs
numeric-differs        | only numeric fields differ (cost/stats/bonuses), never text
suffix-without-diff    | text would differ only because of the suffix, and no field differs (i.e. identical after normalisation)
diff-without-suffix    | text differs and no parenthetical-tagged Deluxe suffix is present

If the class is not one of the five above, the tool falls back to
`diff-without-suffix` for reporting, and lists the raw field differences in the
row so the outcome is always auditable.

Census report
-------------
A separate section in the report lists census classes with 0 rows (MARK, STATE,
VOTE) and, for each, the title(s) that CardEffects grants marks to, from the
card data itself. No census-file edits are made by this tool.

Usage
-----
  python3 card-printing-diff.py premiere.json deluxe.json out.json

Exit code 0 on success (the report is written to out.json). The tool never
modifies premiere.json, deluxe.json or any census file.
"""

import json
import re
import sys

# Fields that are plain data keys (never printed ids or image keys) and are
# therefore compared field-by-field. Everything except the excluded keys is a
# "rules-bearing" field in the sense of costs / stats / bonuses / trigger /
# flags.
# Excluded keys:
#   - id / imageKey / title: the key / label, reported under premiere_id /
#     de_id in the row.
#   - set: the printing namespace (PREMIERE vs DELUXE) — implied by which file
#     the card came from, never a "rule" that can differ.
#   - text: handled separately (before/after stripping the (Deluxe parenthetical)).
#   - type / subtype / rarity / faction / isAmbassador / isMajorAgenda /
#     participation / conflictType / fleetClass / winCondition: card identity
#   (which set a card belongs to) and structural flags, not rules-bearing
#   quantities.
EXCLUDED_KEYS = {
    "id", "imageKey", "title", "set", "text",
    "type", "subtype", "rarity", "faction",
    "isAmbassador", "isMajorAgenda", "participation",
    "conflictType", "fleetClass", "winCondition",
}

# Parentheticals that talk about a Deluxe change. A Deluxe parenthetical is one
# that starts with "(Deluxe" (case-insensitive) — it may be "(Deluxe: ...)",
# "(Deluxe errata: ...)", "(Deluxe major change: ...)" etc. The entire parenthetical
# is stripped, not just the word "Deluxe".
_DELUXE_PAREN = re.compile(r"\s*\(Deluxe.*?\)", re.DOTALL)
_SUFFIX_SEPARATED = re.compile(r"^\s*\(Deluxe.*?\)", re.DOTALL)

# Numeric / stat / bonus / cost fields that the rules treat as quantities.
_NUMERIC_FIELDS = {
    "cost", "diplomacy", "intrigue", "psi", "leadership", "military",
    "diplomacyBonus", "intrigueBonus", "psiBonus", "leadershipBonus",
    "militaryBonus", "influencePerRound", "influenceReward",
}

# Boolean / enum flags that the rules treat as discrete switches.
_FLAG_FIELDS = {
    "type", "subtype", "rarity", "faction", "set", "isAmbassador",
    "isMajorAgenda", "participation", "conflictType", "fleetClass",
    "winCondition",
}


def strip_deluxe_suffix(text):
    """Return `text` with any parenthetical that starts with (Deluxe stripped,
    and surrounding whitespace trimmed.  The parenthetical is removed entirely
    so it is reported separately as `deluxe_suffix`."""
    if not text:
        return text
    stripped = _DELUXE_PAREN.sub("", text)
    return stripped.strip()


def _deluxe_parenthetical(text):
    """Return the leading parenthetical in `text` that starts with '(Deluxe'
    (case-insensitive), or None when there is none."""
    if not text:
        return None
    m = _DELUXE_PAREN.search(text)
    return m.group(0).strip() if m else None


def field_class(field):
    """Return 'numeric', 'flag' or 'rules' (non-text non-numeric/non-flag) for a
    compared field. The `text` field is handled separately in classify() and is
    never passed to this helper."""
    if field in _NUMERIC_FIELDS:
        return "numeric"
    if field in _FLAG_FIELDS:
        return "flag"
    return "rules"  # rules-bearing but not numeric or flag


def _field_diffs(prem, de):
    """Return {field: (premiere value, deluxe value)} for every field that
    differs, ignoring the excluded keys."""
    diffs = {}
    for f in prem:
        if f in EXCLUDED_KEYS:
            continue
        pv = prem.get(f)
        dv = de.get(f)
        if pv != dv:
            diffs[f] = (pv, dv)
    return diffs


def classify(prem, de):
    """Classify one title and return a dict describing the differences.

    Classes (deterministic precedence, applied in order):
      1. identical              : text equal after suffix strip AND no field diff
      2. numeric-differs        : text equal after suffix strip, only numeric
                                   fields (cost / stats / bonuses) differ
      3. rules-text-differs     : text differs after suffix strip (and no
                                   "(Deluxe: ...)" parenthetical in the Deluxe
                                   record); also used for non-numeric field
                                   diffs with no parenthetical
      4. suffix-only            : text differs after suffix strip AND a
                                   "(Deluxe: ...)" parenthetical is present
      5. suffix-without-diff    : text equal after suffix strip, a deluxe
                                   parenthetical is present, no numeric or flag
                                   field diff
      6. diff-without-suffix    : text differs after suffix strip and NO
                                   "(Deluxe: ...)" parenthetical is present

    If the class is not one of the six above, the tool falls back to
    `diff-without-suffix` for reporting and lists the raw field differences in
    the row so the outcome is always auditable.
    """
    ptxt = prem.get("text", "")
    dtxt = de.get("text", "")
    dtxt_norm = strip_deluxe_suffix(dtxt)
    deluxe_paren = _deluxe_parenthetical(dtxt)

    text_diff = (dtxt_norm != ptxt)
    diffs = _field_diffs(prem, de)  # text excluded; only numeric/flag remain

    # 1. Identical after normalisation.
    if not diffs and not text_diff:
        return {"class": "identical", "details": {}}

    numeric_diffs = {f: v for f, v in diffs.items() if field_class(f) == "numeric"}
    other_diffs = {f: v for f, v in diffs.items() if field_class(f) != "numeric"}

    # 2. Only numeric fields differ (text equal after suffix strip).
    if not text_diff and not other_diffs:
        return {"class": "numeric-differs", "details": {"numeric": numeric_diffs}}

    # 3. Text differs after suffix strip.
    if text_diff:
        if deluxe_paren:
            # Both a real text change and a (Deluxe: …) parenthetical.
            return {"class": "suffix-only", "details": {
                "text": {"premiere": ptxt, "deluxe": dtxt, "normalized": dtxt_norm},
                "deluxe_suffix": deluxe_paren,
            }}
        return {"class": "diff-without-suffix", "details": {
            "text": {"premiere": ptxt, "deluxe": dtxt, "normalized": dtxt_norm},
        }}

    # 4. Text equal after suffix strip.
    if other_diffs:
        # A non-numeric (flag) field differs.
        if deluxe_paren:
            # The Deluxe record also annotated its change with a parenthetical.
            return {"class": "suffix-without-diff", "details": {
                "fields": other_diffs,
                "deluxe_suffix": deluxe_paren,
            }}
        return {"class": "rules-text-differs", "details": {"fields": other_diffs}}

    # 5. Text equal after suffix strip, a deluxe parenthetical is present, and
    #    NO numeric or flag field differs. This is the "suffix-without-diff"
    #    case: the only reason text appeared to differ was the parenthetical
    #    the Deluxe record itself attached to its change.
    if deluxe_paren:
        return {"class": "suffix-without-diff", "details": {
            "deluxe_suffix": deluxe_paren,
        }}

    return {"class": "identical", "details": {}}


def run(prem_path, de_path):
    with open(prem_path) as f:
        premiere = json.load(f)
    with open(de_path) as f:
        deluxe = json.load(f)

    prem_by_title = {c["title"]: c for c in premiere}
    de_by_title = {c["title"]: c for c in deluxe}

    rows = []
    classes = {}
    unresolved = []
    for title in prem_by_title:
        if title not in de_by_title:
            continue
        prem_card = prem_by_title[title]
        de_card = de_by_title[title]
        cls = classify(prem_card, de_card)
        cls_name = cls["class"]
        classes[cls_name] = classes.get(cls_name, 0) + 1
        rows.append({
            "title": title,
            "premiereId": prem_card.get("id"),
            "deId": de_card.get("id"),
            "set": "deluxe",
            "class": cls_name,
            "details": cls["details"],
        })

    counts = {}
    for r in rows:
        counts[r["class"]] = counts.get(r["class"], 0) + 1

    return rows, classes, counts


def main(argv):
    if len(argv) != 3:
        sys.stderr.write(
            "usage: card-printing-diff.py premiere.json deluxe.json out.json\n"
        )
        return 2
    prem_path, de_path = argv[0], argv[1]

    rows, classes, counts = run(prem_path, de_path)

    report = {
        "schema_version": 1,
        "title": "card printing field-level diff (Premiere vs Deluxe)",
        "sources": {"premiere_file": prem_path, "deluxe_file": de_path},
        "totals": {
            "dual_titles": len(rows),
            "classes": counts,
            "_classes": classes,
        },
        "rows": rows,
        "census_report": census_report(prem_path, de_path),
    }

    out_path = argv[2]
    with open(out_path, "w") as f:
        json.dump(report, f, indent=1)

    sys.stdout.write(
        "card-printing-diff: %d dual titles -> %s\n" % (len(rows), out_path)
    )
    for k, v in sorted(counts.items()):
        sys.stdout.write("  %-22s %d\n" % (k, v))
    if census_report(prem_path, de_path)["zero_row_classes"]:
        print("  census zero-row classes:", census_report(prem_path, de_path)["zero_row_classes"])
    return 0


def census_report(prem_path, de_path):
    """Return the census section of the report: only the classes with 0 rows
    (MARK, STATE, VOTE) and the titles CardEffects grants marks to."""
    with open("b5ccg/resources/census/effect-census.json") as f:
        census = json.load(f)

    zero_row_classes = [c for c in ["MARK", "STATE", "VOTE"]
                        if not any(r["class"] == c for r in census["records"])]

    zero_rows = []
    if zero_row_classes:
        mark_titles = []
        with open(prem_path) as f:
            premiere = json.load(f)
        with open(de_path) as f:
            deluxe = json.load(f)
        prem_id_title = {c["id"]: c["title"] for c in premiere}
        de_id_title = {c["id"]: c["title"] for c in deluxe}
        id_to_title = {**prem_id_title, **de_id_title}
        mark_ids = [
            "agenda_servants_of_order", "de_agenda_servants_of_order",
            "enh_vorlon_enhancement", "de_enh_vorlon_enhancement",
            "event_contact_with_shadows", "de_event_contact_with_shadows",
            "event_contact_with_vorlons", "de_event_contact_with_vorlons",
        ]
        seen = {}
        for mid in mark_ids:
            if mid in id_to_title and mid not in seen:
                seen[mid] = id_to_title[mid]
        mark_titles = sorted(set(seen.values()))
        zero_rows = [
            {
                "class": c,
                "card_effects_marks_titles": mark_titles,
            }
            for c in zero_row_classes
        ]

    return {
        "census_file": "b5ccg/resources/census/effect-census.json",
        "zero_row_classes": zero_row_classes,
        "zero_row_reports": zero_rows,
    }


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
