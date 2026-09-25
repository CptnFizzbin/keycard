---
"@cptn-fizzbin/keycard-impl-java": patch
---

Fix correctness bugs found in a code review of the Java implementation:

- A built `Policy` is now isolated from later changes: `PolicyBuilder#buildDef()`
  copies its rules instead of sharing the builder's list, and `Policy`
  snapshots the rules and wildcard tokens it validated at construction.
  Previously, calling `allow()`/`deny()` after `build()`, or mutating the
  `PolicyDefinition` after load, silently changed an already-built policy.
- Structural rule validation (effect must be `allow`/`deny`, non-null
  action/subject, no conditions on a both-sides-wildcarded rule) now runs
  even with `KeycardConfig#emitMeta(false)`. Only the catalog checks stay
  gated. Previously a typo like `"Allow"` was accepted and silently denied,
  and a null action threw a `NullPointerException` from `can()`.
- `$eq`/`$ne`/`$in`/`$has` compare numbers by value: an `Integer` from a
  parsed JSON/YAML policy now matches a `long` claims field.
- `$has`/`$in` accept any `Collection` (e.g. a `Set<String>` claims field),
  not only a `List`.
- Condition helpers no longer mangle accessor names that merely start with
  `is`/`get` (`Claims::isbn` was read as field `bn`). Passing a lambda instead
  of a method reference now throws a clear `PolicyArgumentException`.
- The unused Gson dependency is removed, `org.jetbrains:annotations` is
  pinned to a fixed version, and `org.jspecify:jspecify` is declared
  explicitly.
