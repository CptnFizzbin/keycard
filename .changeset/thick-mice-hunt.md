---
"@cptn-fizzbin/keycard-impl-java": minor
---

Add `Conditions.has`, `Conditions.field(String, Object)` (the `$field` explicit-name long form), and `Conditions.op` (a single-key helper for any registered operator, built-in or custom) to the Java condition-builder helpers, rounding out coverage of the remaining `$`-operators alongside the existing `eq`/`ne`/`gt`/`gte`/`lt`/`lte`/`in`/`substr`/`and`/`or`/`not`.
