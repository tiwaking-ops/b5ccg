# HEARTBEATS — binding schema

Strict JSON. Fields: `schema_version` (integer, currently 1), `agent_id`
(string, your session id), `utc` (ISO-8601 UTC, stamped ONLY by
`new-heartbeat.ps1` — never hand-authored), `state` (active|idle|done),
`live_claims` (array, always present; `[]` asserts you hold nothing).
Optional: `javac`, `notes` (prose, never parsed).

Liveness (three-signal rule): a claim is LIVE while the NEWEST of its claim
age, its owner's heartbeat `utc`, and its report mtime falls inside the
30-min TTL. An absent signal is UNKNOWN, never healthy.
