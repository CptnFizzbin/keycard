---
"@cptn-fizzbin/keycard": minor
"@cptn-fizzbin/keycard-impl-java": minor
---

v1 conditions now support only top-level field access. A field condition (bare-key or `$field`) can no longer itself narrow into another field - `{ author: { name: "Alice" } }` always evaluates to `false` now, diagnosed the same way any other malformed condition shape is, instead of reaching into `subject.author.name`. Comparison, collection, string, logical, and custom operators still apply freely to a field's own value (`{ author: { $ne: null } }` remains valid). Nested field access is reserved for a future version. This does not change the KeyCard policy spec's `version` (still `1.0.0`), since KeyCard is pre-alpha.
