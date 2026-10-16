#!/usr/bin/env python3
# Standing Java 6 construct census (B5-0960 instrument), tracked per the DONE B5-1667 disposition.
# Copied byte-identically from tmp-scans/b50960/census.py by B5-1877 (Buffy (glm-5.3-flash) 16, 2026-10-01).
# Provenance: authored B5-0960; re-run byte-identical at B5-1012; the AGENTS.md section 2a standing
# command pointed at the git-ignored tmp-scans path, which a fresh clone cannot resolve (B5-1667).
# This tracked copy is the canonical instrument.
#
# B5-2719 EDIT (2026-10-06, pi (orcarouter-auto) 01a11): the file set was moved from
# `git ls-files` to an os.walk of the working tree, and a 14th family `qualifiedGetOrDefault`
# was added. Before this edit the instrument scanned 82 tracked .java paths under b5ccg/src
# while compile.sh compiled 138, so the API-level second look covered ~59 percent of what the
# build compiles. The tree set is exactly what `find src -name "*.java"` hands javac, so the
# census denominator and the build denominator are now the same set. The tracked/untracked
# split is still printed, because it is the evidence that the gap closed and not merely that
# the instrument stopped caring. tmp-scans/b50960/census.py is NOT updated: it stays in place
# as the byte-identical pre-B5-2719 snapshot and now DIVERGES from this canonical copy.
#
# Run: PYTHONIOENCODING=utf-8 py .agent/tools/census-b50960.py
# Gate: exit 0 = clean (code-lines 0 on every family under TREE JAVA b5ccg/src); the census is the
# record and the API-level second look, javac stays decisive on syntax (authority split per B5-1479).

import os, re, subprocess, json

OUT = 'tmp-scans/b50960'

def tracked(glob):
    out = subprocess.run(['git', 'ls-files', glob], capture_output=True, text=True)
    return sorted(x for x in out.stdout.strip().split('\n') if x)

CONSTRUCTS = [
    ('arrow',       re.compile(r'->')),
    ('methodref',   re.compile(r'::')),
    ('stream',      re.compile(r'\.stream\s*\(')),
    ('computeIfAbsent', re.compile(r'\bcomputeIfAbsent\s*\(')),
    ('computeIfPresent', re.compile(r'\bcomputeIfPresent\s*\(')),
    ('compute',     re.compile(r'\.compute\s*\(')),
    ('merge',       re.compile(r'\.merge\s*\(')),
    ('functionalInterface', re.compile(r'@FunctionalInterface')),
    ('tryWithResources', re.compile(r'\btry\s*\(')),
    ('diamond',     re.compile(r'<\s*>')),
    ('forEach',     re.compile(r'\bforEach\s*\(')),
    ('removeIf',    re.compile(r'\bremoveIf\s*\(')),
    ('getOrDefault', re.compile(r'\bgetOrDefault\s*\(')),
    # B5-2719: the API-level signal AGENTS.md section 2a already cites. The unqualified family
    # above cannot separate it, because the project's own DeckLoader.getOrDefault helper matches
    # the same \b...\s*\( shape; only a QUALIFIED call is a Map-default-API call, and only that
    # compiles fine at -source 6. Measured before it was a construct: 0 lines in b5ccg/src,
    # 14 in the frozen archive, so the archive contrast survives the addition.
    ('qualifiedGetOrDefault', re.compile(r'\.\s*getOrDefault\s*\(')),
]

def mask(src):
    """Blank line content inside /* */ and // comments and string literals; keep lines."""
    out = []
    i, n = 0, len(src)
    in_block = in_line = in_str = in_chr = False
    cur = []
    while i < n:
        c = src[i]
        nxt = src[i+1] if i+1 < n else ''
        if in_block:
            cur.append(' ' if c != '\n' else '\n')
            if c == '*' and nxt == '/':
                cur.append('  '); i += 2; in_block = False; continue
            i += 1; continue
        if in_line:
            if c == '\n':
                in_line = False; out.append(''.join(cur)); cur = []; continue
            cur.append(' '); i += 1; continue
        if in_str:
            if c == '\\':
                cur.append('  '); i += 2; continue
            if c == '"':
                in_str = False; cur.append(' '); i += 1; continue
            cur.append(' ' if c != '\n' else '\n'); i += 1; continue
        if in_chr:
            if c == '\\':
                cur.append('  '); i += 2; continue
            if c == "'":
                in_chr = False; cur.append(' '); i += 1; continue
            cur.append(' '); i += 1; continue
        if c == '/' and nxt == '*':
            in_block = True; cur.append('  '); i += 2; continue
        if c == '/' and nxt == '/':
            in_line = True; cur.append('  '); i += 2; continue
        if c == '"':
            in_str = True; cur.append(' '); i += 1; continue
        if c == "'":
            in_chr = True; cur.append(' '); i += 1; continue
        cur.append(c); i += 1
    out.append(''.join(cur))
    return ''.join(out)

def census(files, label):
    print('===', label, len(files), 'files ===')
    for name, rx in CONSTRUCTS:
        hits = []
        for f in files:
            if not os.path.exists(f):
                print('MISSING-FILE', f); continue
            src = open(f, encoding='utf-8', errors='replace').read()
            m = mask(src)
            sl = src.split('\n'); ml = m.split('\n')
            for ln, (a, b) in enumerate(zip(sl, ml), 1):
                occ = len(rx.findall(a))
                if occ:
                    code = bool(rx.search(b))
                    hits.append((f, ln, a.strip()[:110], code, occ))
        print('--', name, 'lines-with-hits', len(hits), 'occurrences', sum(h[4] for h in hits), 'code-lines', sum(1 for h in hits if h[3]))
        for h in hits:
            print('   %s | %s%d x%d | %s' % (h[0], '' if h[3] else 'PROSE ', h[1], h[4], h[2]))

def tree(root):
    """Every .java under root in the WORKING TREE. This is compile.sh's own file set
    (`find src -name "*.java"`), so the census and the build now share one denominator."""
    out = []
    for dirpath, _dirs, files in os.walk(root):
        for fn in files:
            if fn.endswith('.java'):
                out.append(os.path.join(dirpath, fn).replace(os.sep, '/'))
    return sorted(out)

def split_denominator(root, files):
    """Split a tree file list into the git-tracked part and the untracked part. Printed, not
    silently folded in: B5-2719 exists because an unprinted denominator drifted for a week."""
    idx = set(tracked(root + '/*.java')) | set(tracked(root + '/**/*.java'))
    tracked_on_disk = [f for f in files if f in idx]
    untracked = [f for f in files if f not in idx]
    return tracked_on_disk, untracked

src_files = tree('b5ccg/src')
arch_files = tree('b5ccg/src-java8-archive')
src_tracked, src_untracked = split_denominator('b5ccg/src', src_files)
arch_tracked, arch_untracked = split_denominator('b5ccg/src-java8-archive', arch_files)
by_pkg = {}
for f in src_files:
    parts = f.split('/')
    key = parts[3] if len(parts) > 4 else '(root)'
    by_pkg[key] = by_pkg.get(key, 0) + 1
print('DENOMINATOR b5ccg/src: %d .java on disk, %d tracked by git, %d untracked' %
      (len(src_files), len(src_tracked), len(src_untracked)))
for f in src_untracked:
    print('   untracked:', f)
print('DENOMINATOR src-java8-archive: %d .java on disk, %d tracked by git, %d untracked' %
      (len(arch_files), len(arch_tracked), len(arch_untracked)))
print('src file census:', len(src_files), by_pkg)
print('archive file census:', len(arch_files))
census(src_files, 'TREE JAVA b5ccg/src (gate set: every .java compile.sh compiles)')
census(arch_files, 'FROZEN JAVA src-java8-archive (read-only)')
