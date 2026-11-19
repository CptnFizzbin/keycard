---
"@cptn-fizzbin/keycard-js-core": minor
"@cptn-fizzbin/keycard-java-core": minor
---

Adds the built-in `$always` and `$never` condition operators: `{ $always: true }`
always matches and `{ $never: true }` never does. `true` is the only valid
operand for either - any other operand, `false` included, is malformed and
evaluates to `false`. Java's `Condition` gains `always()`/`never()` helpers.
