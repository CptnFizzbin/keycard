---
"@cptn-fizzbin/keycard": minor
---

Align the JS API with the "Vision: Quickstart"/"Vision: A Real Backend" docs:

- `PolicyBuilder`/`Policy` now take `KeycardConfig` as their sole extra
  constructor argument - `new PolicyBuilder(config?)`,
  `new Policy(definition, config?)`/`Policy.from(definition, config?)`.
  `PolicyBuilderOptions`/`PolicyOptions` are gone; `anyAction`/`anySubject`
  moved onto `KeycardConfig` alongside `operators`.
- `KeycardConfig.actions`/`.subjects` are now always a keyed catalog
  (`ActionCatalog`/`SubjectCatalog`, i.e. `Record<string, Action | Subject>`)
  rather than accepting a plain array - pass the `Actions`/`Subjects` map
  itself instead of `Object.values(Actions)`.
- `KeycardConfig.operators` additionally accepts an `OperatorCatalog` - a
  bare `{ $name: resolver }` map, no `createOperator()` call needed - as
  well as the existing `AnyOperator[]`.
- New `KeycardConfig.emitMeta` (default `true`): set `false` to skip the
  eager catalog/operator validation `PolicyBuilder`/`Policy` do at
  construction and the diagnostic `meta.actions`/`meta.subjects`/
  `meta.operators` a built `PolicyDefinition` carries - worth paying for in
  dev, dead weight in prod once CI has already run them once.
- `createSubject` gains a dynamic (no-name) options form,
  `createSubject<TData, TArgs>({ from?, fieldMapper? })`: `from` maps one or
  more raw domain entities into this Subject's claims shape, and the new
  `Subject#from(...)` method builds a wrapped Subject from it directly
  (falls back to the identity mapping, behaving exactly like `.wrap()`, when
  no `from` was given).
- `Policy#require`'s thrown `PolicyError` message changed from
  `Access denied: cannot "x" on "y"` to `"x" is not allowed on this "y"`.

`ActionCatalog`, `SubjectCatalog`, `OperatorCatalog`, and `OperatorResolver`
are now exported types.
