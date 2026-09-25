---
"@cptn-fizzbin/keycard-impl-java": minor
---

Follow-up to the Java code review: robustness fixes, a tighter public API,
and cleanup.

**Behavior**

- Field conditions now read a field declared on a superclass, and fall back
  to a public accessor method (`name()`, `getName()`, `isName()`) when there's
  no backing field. Lookups are cached per class. A member the module system
  won't open is treated as missing instead of throwing
  `InaccessibleObjectException` out of `can()`. `Object`'s own methods
  (`getClass()`, ...) never count as fields.
- `Condition` helpers keep a record component's name as-is, so
  `Flags::isActive` reads component `isActive` (it used to become `active`).
- Parsed `$substr` patterns are cached (bounded LRU).
- `PolicyDefinition.Rule` stores a deep, unmodifiable copy of its conditions
  and now has `equals`/`hashCode`/`toString`.
- `OperatorCatalog` rejects an operator name that doesn't start with `$`,
  since it could never be dispatched.
- Type-issue diagnostics go to `KeycardConfig#logger` (a `System.Logger`, at
  `ERROR`) instead of `System.err`. Custom operators can report their own via
  the new `OperatorContext#reportTypeIssue`.

**Breaking API changes**

- `ActionCatalog`, `SubjectCatalog` and `OperatorCatalog` no longer extend
  `LinkedHashMap`. Use `add`/`set`/`get`/`contains`, or `asMap()` for a
  read-only view. Registering a *different* Action/Subject under a key
  that's already taken now throws `PolicyArgumentException` instead of
  silently replacing it.
- `KeycardConfig#anyAction(null)`/`anySubject(null)` no longer mean
  "disable" - use the new `disableAnyAction()`/`disableAnySubject()`.
  `PolicyDefinition.Meta` gains the same two methods, and its
  `anyAction(Action)`/`anyAction(String)` overloads (and the `anySubject`
  ones) reject `null`.
- `PolicyDefinition#getRules()` is replaced by `rules()`, which returns an
  unmodifiable snapshot; `rules(list)` stores a copy of `list`.
- `PolicyBuilder#allow`/`deny` both take `Iterable<? extends Action>` (was
  `Collection` for `allow`), and `deny(actions, subject, condition)` is new.
- `Action#dynamic()`/`Subject#dynamic()` return `boolean` (was `Boolean`).
- `KeycardConfig` no longer has Lombok `@Data` `equals`/`hashCode`/`toString`.
- `lib.Logger` (unused) is removed.
- `lib.Catalog.build` takes just the catalog map (the always-null list
  parameter is gone).

**Build**

- Jackson versions come from `jackson-bom`, so the test-only YAML module
  matches `jackson-databind`.
- The compiler uses `<release>17</release>`, so JDK 18+ APIs are caught when
  building on a newer JDK.
