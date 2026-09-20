---
"@cptn-fizzbin/keycard-impl-java": minor
---

Align the Java API with the `vision-quickstart`/`vision-real-backend` design
docs:

- `Subject<T>` is now `Subject<T, TSelf extends Subject<T, TSelf>>` and no
  longer `final` - a dedicated subject subclass (e.g. `ArticleSubject extends
  Subject<ArticleSubject.Claims, ArticleSubject>`) can override `copy()` to
  call its own private constructor, so `wrap()`/a subclass's own `from(...)`
  return that exact subtype with no cast anywhere. Existing code using
  `Subject<T>` directly (no subclass) needs no changes beyond adding a second,
  usually-wildcard type argument (`Subject<T, ?>`).
- `ActionCatalog#set`/`SubjectCatalog#set`: register an Action/Subject and
  return it (its real, possibly subclassed type) instead of the catalog, so a
  dynamic Action/Subject can be declared and registered in one line (e.g.
  `static ProjectSubject Project = catalog.set("project", new ProjectSubject());`).
  `#add` is unchanged.
- `Condition.where(condition)`: an identity passthrough for readability at the
  top of an `allow`/`deny` call. `Condition.op(getter, operator, value)`: a
  getter-scoped counterpart to the existing top-level `Condition.op(operator, value)`,
  for a custom operator with no dedicated helper (e.g. `Condition.op(Claims::createdAt, "$withinDays", 30)`).
  (A fluent `Condition.field(getter).eq(value)` builder was tried and dropped -
  splitting a getter's type inference across two chained generic calls fails
  in javac when the getter is a Lombok-generated method compiled in the same
  round; the existing single-call `Condition.eq(getter, value)`-style statics
  don't have this problem.)
- `ConditionOperator`: a flat `(subjectValue, value) -> boolean` custom
  operator shape, registered via the new `OperatorCatalog#set(name, operator)`
  - a convenience adapter to `Operator` for a custom operator that doesn't
    need to recurse into the condition language.
- `KeycardConfig#emitMeta` (default `true`): gates the eager, fail-fast
  catalog/operator checks `PolicyBuilder`/`Policy` do at construction, and
  whether `PolicyBuilder#buildDef()` attaches the derived `meta` block at all.
  Set to `false` in production to skip re-validating a policy that already
  passed CI. `KeycardConfig#emitTests` is also added (default `false`),
  reserved for the spec's `tests` block - not yet wired to anything.
