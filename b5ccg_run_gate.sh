#!/bin/bash
# Gate for B5-0017: compile + full RUN_TESTS=1 suite + failing-check report.
# JDK 25 (Hop 1) required on PATH; TMPDIR must point at a native-writable dir.
cd /c/temp/projects/b5ccg
export PATH=/c/temp/projects/b5ccg/b5ccg/scratch/jdk25-win/jdk-25.0.4.1+1/bin:$PATH
export TMPDIR=/c/Users/Tiwa\ Pene/AppData/Local/hermes/cache/scratch
rm -rf b5ccg/out
mkdir -p b5ccg/out
find b5ccg/src -name '*.java' > b5ccg/out/b5ccg_compile_list.tmp
javac --release 8 -encoding UTF-8 -Xmaxerrs 2000 -d b5ccg/out @b5ccg/out/b5ccg_compile_list.tmp
echo "javac_exit=$?"
echo "=== RUN_TESTS=1 gate ==="
RUN_TESTS=1 sh b5ccg/compile.sh 2>&1 | tail -40
echo "=== run.sh ==="
bash b5ccg/run.sh 2>&1 | tail -20
