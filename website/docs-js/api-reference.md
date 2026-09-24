---
title: API Reference
sidebar_label: API Reference
slug: /api-reference
---

# API Reference (JavaScript)

## `createAction<T>(name?: T)`

Create a typed action. Called with no `name`, generates a random id in its
place and marks the Action *dynamic* — it must then be registered as a
catalog value on the `KeycardConfig` handed to any `PolicyBuilder`/`Policy`
that uses it, so its catalog key can resolve to a real, stable name (see
`KeycardConfig` below).

## `createSubject<TData, TArgs>(name: string, fieldMapper?: SubjectFieldMapper<TData>)`

Create a named (non-dynamic) subject definition. `fieldMapper`, when given, is
carried through every `.wrap()`/`.from()` call unchanged — see
`SubjectFieldMapper` below.

## `createSubject<TData, TArgs>(options?: { from?, fieldMapper? })`

Create a dynamic (no-name) subject definition — symmetric with `createAction()`
called with no name. `options.from`, when given, maps one or more raw domain
entities (`TArgs`) into `TData` — this Subject's claims shape — for
`.from(...)` to use; omitted, `.from(data)` falls back to the identity
mapping, behaving exactly like `.wrap(data)`.

## `InferActions<T>` / `InferSubjects<T>`

Derive the `Action` / `Subject` union types that `PolicyBuilder` and
`Policy` expect from an actions or subjects map, e.g.
`InferActions<typeof Actions>`, so callers don't have to write
`typeof Actions[keyof typeof Actions]` by hand.

## `PolicyBuilder<TActions, TSubjects, TOperators>`

`new PolicyBuilder(config?)` — `config` is an optional `KeycardConfig`
(see below), shared with `Policy`.

- `allow(action, subject, condition?)` — allow action
- `deny(action, subject, condition?)` — deny action
- `buildDef()` — create a `PolicyDefinition`
- `build()` — create a `Policy` instance directly

## `Subject<TData, TArgs>`

A single type covering both a bare subject (no instance) and a wrapped
instance — `instance` is `undefined` until `.wrap()`/`.from()` is called.

- `wrap(obj: TData)` — returns a new `Subject` of the same name (and field
  mapper, unchanged), with `instance` set to `obj`
- `from(...args: TArgs)` — returns a new `Subject` of the same name, with
  `instance` set to what `createSubject`'s `from` mapper computes from
  `args`; without a `from` mapper, behaves exactly like `wrap()`
- `name` — subject name
- `instance` — the wrapped object, if any
- `fieldMapper` — the `SubjectFieldMapper` attached via `createSubject`, if any

## `Policy<TActions, TSubjects, TOperators>`

`new Policy(definition, config?)` — or the static `Policy.from(definition,
config?)`. `config` is an optional `KeycardConfig` (see below), shared with
`PolicyBuilder`.

- `can(action, subject)` — check if action is allowed
- `cannot(action, subject)` — check if action is denied
- `require(action, subject)` — throw if not allowed (`PolicyError`)
- `def()` — get the underlying definition

## Condition operators

`$eq`, `$ne`, `$gt`, `$gte`, `$lt`, `$lte`, `$in`, `$has`, `$substr`,
`$or`, `$and`, `$not`, `$field`, and plain field condition are all supported —
see the language-agnostic [Condition Operators](/docs/condition-operators)
reference for the full semantics.

## `KeycardConfig<TOperators>`

Optional, shared config accepted as the sole extra constructor argument by
both `PolicyBuilder` and `Policy` — one object bundling the action/subject
vocabulary, custom operators, the wildcard tokens, and field mappers a
policy needs, built once instead of kept in sync by hand across both. Every
field is independently optional:

```typescript
// Let TS infer the type here rather than annotating `: KeycardConfig` —
// that type's TOperators parameter defaults to `never`, which would
// reject the `operators` entry below.
const config = {
  actions: { Read: createAction("Read") },      // additive to meta.actions
  subjects: { Article: createSubject("Article") }, // additive to meta.subjects
  operators: { $hasRole: hasRoleResolver },     // or [createOperator(...)]
  anyAction: "*",
  mapper: fieldMapperCatalog,
  emitMeta: process.env.NODE_ENV !== "production",
};

const policy = new Policy(policyDef, config);
```

- `actions: ActionCatalog` / `subjects: SubjectCatalog` — a keyed
  `Record<string, Action | Subject>` catalog: declared vocabulary,
  **additive** to whatever `PolicyBuilder.allow`/`.deny` actually used, or
  to `meta.actions`/`meta.subjects` already on a `PolicyDefinition` a
  `Policy` is constructed from (see [Policy Definition](/docs/policy-definition)
  for the catalog-enforcement behavior this widens); each key also becomes
  the serialized name for its entry, which is how a dynamic (no-name)
  `createAction()`/`createSubject()` result gets a real, stable name.
- `operators: AnyOperator[] | OperatorCatalog` — custom operators, either
  built via `createOperator` or given as a bare `{ $name: resolver }` map
  (no `createOperator` call needed).
- `anyAction` / `anySubject` — the wildcard tokens: a bare token string, an
  `Action`/`Subject` (its `.name` is used), or `null` to disable that
  wildcard position entirely; omitted means the `"_ANY_"` default applies.
- `mapper` — a `SubjectFieldMapperCatalog`, consulted as a fallback for any
  subject that doesn't carry its own field mapper.
- `emitMeta` (default `true`) — gates the eager catalog/operator
  validation `PolicyBuilder`/`Policy` do at construction (an unregistered
  dynamic Action/Subject, a duplicate catalog key, a custom operator
  `meta.operators` declares but nothing registered — all fail loudly,
  immediately, when `true`) together with the diagnostic
  `meta.actions`/`meta.subjects`/`meta.operators` a built
  `PolicyDefinition` carries (omitted when `false`; `meta.anyAction`/
  `meta.anySubject`, being functionally required for evaluation, are
  always emitted when non-default). Worth paying for in dev; dead weight
  in prod once CI has already run them once.

## `SubjectFieldMapper<TData>` / `SubjectFieldMapperCatalog`

Per-field getters for a subject's wrapped instance — the explicit counterpart to
KeyCard's default property access (`instance[fieldName]`). Lets a condition
reference a field whose name doesn't match the instance's own shape (a rename, a
computed/derived value), or an instance that isn't a plain object. A field the
mapper doesn't define still falls back to ordinary property access — and the
one-level-deep restriction (see
[Condition Operators](/docs/condition-operators#v1-supports-only-top-level-field-access))
still applies to the resolved value.

```typescript
interface Post {
  status: string;
  author: { name: string }
}

const read = createAction("Read");
const post = createSubject<Post>("Post", {
  authorName: (instance) => instance.author.name,
});

const policy = new Policy({
  version: "1.0",
  rules: [["allow", "Read", "Post", {authorName: "Alice"}]],
});

policy.can(read, post.wrap({status: "draft", author: {name: "Alice"}})); // true
```

Note the rule above is a plain object, not built via `PolicyBuilder.allow()`:
a mapped-only field like `authorName` has no static type (`Condition<TSubject>`
only knows about `TSubject`'s real properties), so `.allow()`'s typed
`condition` parameter can't reference one — build that rule as a plain object
instead, as above.

Attach a mapper directly via `createSubject`'s second parameter (carried through
every `.wrap()` unchanged), or register several centrally via a
`SubjectFieldMapperCatalog` and hand it to `Policy`/`PolicyBuilder`
through `KeycardConfig.mapper` — a subject's own mapper, if it has one, always
takes precedence over the catalog:

```typescript
const catalog = new SubjectFieldMapperCatalog({
  Post: {authorName: (instance: Post) => instance.author.name},
});

const policy = new Policy(policyDef, {mapper: catalog});
```

- `SubjectFieldMapper<TData>` — `Record<string, (instance: TData) => unknown>`
- `new SubjectFieldMapperCatalog(entries?: Record<string, SubjectFieldMapper>)`
- `.register(subjectName, mapper)` — register one more mapper
- `.get(subjectName)` — look up a registered mapper, or `undefined`

## See also

- [SPEC.md](https://github.com/CptnFizzbin/keycard/blob/main/SPEC.md) — complete
  specification
- [Guide](/docs/intro) — language-agnostic concepts
