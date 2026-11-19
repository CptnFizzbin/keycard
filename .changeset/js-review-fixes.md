---
"@cptn-fizzbin/keycard-js-core": minor
---

Fixes and spec-conformance changes from a full review of the JS implementation:

- **Fix (#50):** a `Policy` built by `PolicyBuilder` no longer changes when more rules are added to the builder afterwards. `buildDef()` and `Policy` copy their rules, and `Policy.def()` returns a fresh copy on every call.
- **Fix:** `$and`/`$or` no longer short-circuit to `false` when the value is `null`. `{ f: { $and: [] } }` is now `true` and `{ f: { $or: [null] } }` now matches `{ f: null }`, as the spec requires.
- **Fix:** field conditions no longer treat members inherited from `Object.prototype` (`constructor`, `toString`, `hasOwnProperty`, ...) as subject fields, and a `SubjectFieldMapper` is consulted only for fields it defines itself.
- `meta.anyAction`/`meta.anySubject` now accept `false` (same as `null`), and any other non-string value throws `PolicyLoadException`.
- A malformed definition (not an object, missing/non-array `rules`, non-object `meta`, a rule tuple longer than 4 elements) now throws `PolicyLoadException` instead of a `TypeError`. A missing, non-string, or non-SemVer `version` throws `PolicyVersionException` naming the raw value.
- `meta.actions`/`meta.subjects`/`meta.operators` must be arrays of unique strings. `meta.operators` entries must be `$`-prefixed and must not name a built-in operator.
- Custom operator names must start with `$` (`PolicyArgumentError` otherwise).
- An array used as a condition now evaluates to `false` with a warning, instead of matching its indices as field names.
- `PolicyLoadException`, `PolicyVersionException`, `PolicyArgumentError` and `PolicyTypeMismatchError` now all extend `PolicyError`. `PolicyTypeMismatchError`, `setLogger` and the `Logger` type are now exported.
- `KeycardConfig.logger` now also receives condition type-mismatch warnings. `ConditionResolver` accepts an optional logger, and `OperatorContext` exposes `logger`.
- Performance: `can()` resolves the action/subject names once per check instead of once per rule, and `$substr` caches compiled patterns.
