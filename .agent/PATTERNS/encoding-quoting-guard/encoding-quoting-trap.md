# Pattern: Shell-quoting encoding/quoting trap (agent id derived)

author_llm: Claude Code (v1)
last_modified_by_llm: Claude Code
last_modified_date: 2026-10-18

## The trap

PowerShell `Add-Content -Encoding UTF8` writes a UTF-8 BOM by default (`UTF8Encoding($true)`).
When markdown with em dashes (`—`) or backtick identifiers is written with BOM,
any downstream reader that expects BOM-less UTF-8 (e.g., `javac --release 8` build,
`git diff`, or `lint-encoding.ps1`) may interpret the first 3 bytes as mangled
text (mojibake). The same applies if literal mojibake (`â€”`, `Â§`) is embedded
via copy-paste from a mis-encoded source.

## Guards adopted (S-5)

- All writer `.ps1` files now use `(New-Object System.Text.UTF8Encoding($false))`
  (no BOM) via `[IO.File]::WriteAllText`.
- `lint-encoding.ps1` exits 1 on: replacement char `U+FFFD`, `â€”` (em-dash mojibake),
  `Â§` (section-sign mojibake), and other known sequences.
- `append-ledger.ps1` round-trips an em dash (`—`) and a backticked identifier
  (`` `backtick-id` ``) unchanged (verified).

## Pattern file rule

One file per lesson family, under `.agent/PATTERNS/<agent-id>/`. Novelty check:
this family (encoding/quoting) was not previously documented in `.agent/PATTERNS/`.
