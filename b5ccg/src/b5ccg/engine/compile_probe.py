import subprocess, os, sys

base = r"C:/temp/projects/Babylon5CCGGameDesign"
javac = "C:/Program Files/AdoptOpenJDK/jdk-8.0.292.10-hotspot/bin/javac.exe"
out = os.path.join(base, "b5ccg", "out")

# Collect all sources
sources = []
for root, dirs, files in os.walk(os.path.join(base, "b5ccg", "src")):
    for f in files:
        if f.endswith(".java"):
            sources.append(os.path.join(root, f))
sources.sort()
print("compiling", len(sources), "sources")
with open(r"C:\tmp\sources_5ccg.txt", "w") as fh:
    for s in sources:
        fh.write(s + "\n")

r = subprocess.run([javac, "-source", "6", "-target", "6", "-d", out] + sources,
                   capture_output=True, text=True, timeout=900)
print("javac rc:", r.returncode)
if r.stdout:
    print("--- stdout (first 2000) ---")
    print(r.stdout[:2000])
if r.stderr:
    print("--- stderr (first 3000) ---")
    print(r.stderr[:3000])
print("--- classes produced ---")
if os.path.isdir(out):
    n = 0
    for root, dirs, files in os.walk(out):
        for f in files:
            if f.endswith(".class"):
                n += 1
    print("class files:", n)
