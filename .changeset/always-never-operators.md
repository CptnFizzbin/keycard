---
"@cptn-fizzbin/keycard": minor
"@cptn-fizzbin/keycard-impl-java": minor
---

Adds the built-in `$always` and `$never` condition operators: `{ $always: true }`
always matches and `{ $never: true }` never does. `true` is the only valid
operand for either - any other operand, `false` included, is malformed and
evaluates to `false`. Java's `Condition` gains `always()`/`never()` helpers.
