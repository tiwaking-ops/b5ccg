#!/bin/bash
# Run the B5 CCG game
ROOT="$(cd "$(dirname "$0")" && pwd)"
OUT="$ROOT/out"

if [ ! -d "$OUT" ]; then
  echo "Project not built. Running compile.sh first…"
  bash "$ROOT/compile.sh"
fi

java -cp "$OUT" b5ccg.Main "$@"
