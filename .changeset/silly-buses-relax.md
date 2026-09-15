---
"@cptn-fizzbin/keycard": minor
---

Type-safe conditions: `Condition` and `Operator` are now generic over the subject type, so a `Policy<TActions, TSubjects, TOperators>` type-checks conditions against each rule's declared subject shape instead of accepting arbitrary JSON.

Breaking changes:

- `new Policy(definition, operators)` / `Policy.from(definition, operators)` now take an options object (`{ operators }`) instead of a positional `Operator[]` array.
- `createOperator` and `Operator` are now generic (`Operator<TSubject, TValue>`); custom operators may need explicit type parameters.
- Removed the `Policy#toDefinition()`, `Policy.fromDto()`, and `Policy#toDto()` aliases - use `Policy#def()` and `Policy.from()`.

Also fixes a bug where a bare (non-`$`-prefixed) field key in a condition object evaluated the whole subject with `$eq` instead of narrowing into `subject[key]` first, causing most field and nested conditions to evaluate incorrectly.
