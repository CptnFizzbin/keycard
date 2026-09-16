---
title: API Reference
sidebar_label: API Reference
slug: /api-reference
---

# API Reference (JavaScript / TypeScript)

## `createAction<T>(name: T)`

Create a typed action.

## `createSubject<TSubject>(name: string, fieldMapper?: SubjectFieldMapper<TSubject>)`

Create a typed subject definition. `fieldMapper`, when given, is carried
through every `.wrap()` call unchanged — see `SubjectFieldMapper` below.

## `InferActions<T>` / `InferSubjects<T>`

Derive the `Action` / `Subject` union types that `PolicyBuilder` and
`Policy` expect from an actions or subjects map, e.g.
`InferActions<typeof Actions>`, so callers don't have to write
`typeof Actions[keyof typeof Actions]` by hand.

## `PolicyBuilder<TActions, TSubjects, TOperators>`

`new PolicyBuilder(options?, config?)` — `config` is an optional
`KeycardConfig` (see below), shared with `Policy`.

- `allow(action, subject, conditions?)` — allow action
- `deny(action, subject, conditions?)` — deny action
- `buildDef()` — create a `PolicyDefinition`
- `build()` — create a `Policy` instance directly

## `Subject<T>`

A single type covering both a bare subject (no instance) and a wrapped
instance — `instance` is `undefined` until `.wrap()` is called.

- `wrap(obj: T)` — returns a new `Subject<T>` of the same name (and field
  mapper, unchanged), with `instance` set
- `name` — subject name
- `instance` — the wrapped object, if any
- `fieldMapper` — the `SubjectFieldMapper` attached via `createSubject`,
  if any

## `Policy<TActions, TSubjects, TOperators>`

`new Policy(definition, options?, config?)` — or the static
`Policy.from(definition, options?, config?)`. `config` is an optional
`KeycardConfig` (see below), shared with `PolicyBuilder`.

- `can(action, subject)` — check if action is allowed
- `cannot(action, subject)` — check if action is denied
- `require(action, subject)` — throw if not allowed
- `def()` — get the underlying definition

## Condition operators

`$eq`, `$ne`, `$gt`, `$gte`, `$lt`, `$lte`, `$in`, `$has`, `$substr`,
`$or`, `$and`, `$not`, `$field`, and plain field conditions are all supported —
see the language-agnostic [Condition Operators](/docs/condition-operators)
reference for the full semantics.

## `KeycardConfig<TOperators>`

Optional, shared config accepted as an extra constructor argument by both
`PolicyBuilder` and `Policy` — one object bundling the action/subject
vocabulary, custom operators, and field mappers a policy needs, built once
instead of kept in sync by hand across both. Every field is independently
optional:

```typescript
// Let TS infer the type here rather than annotating `: KeycardConfig` —
// that type's TOperators parameter defaults to `never`, which would
// reject the `operators` entry below.
const config = {
  actions: [createAction("Read")],      // additive to meta.actions
  subjects: [createSubject("Article")], // additive to meta.subjects
  operators: [hasRoleOperator],
  mapper: fieldMapperCatalog,
};

const policy = new Policy(policyDef, {}, config);
```

- `actions` / `subjects` — declared vocabulary, **additive** to whatever
  `PolicyBuilder.allow`/`.deny` actually used, or to `meta.actions`/
  `meta.subjects` already on a `PolicyDefinition` a `Policy` is
  constructed from (see [Policy Definition](/docs/policy-definition) for
  the catalog-enforcement behavior this widens).
- `operators` — custom operators, used instead of `options.operators` if
  both are given.
- `mapper` — a `SubjectFieldMapperCatalog`, consulted as a fallback for
  any subject that doesn't carry its own field mapper.

## `SubjectFieldMapper<TData>` / `SubjectFieldMapperCatalog`

Per-field getters for a subject's wrapped instance — the explicit
counterpart to KeyCard's default property access (`instance[fieldName]`).
Lets a condition reference a field whose name doesn't match the instance's
own shape (a rename, a computed/derived value), or an instance that isn't
a plain object. A field the mapper doesn't define still falls back to
ordinary property access — and the one-level-deep restriction (see
[Condition Operators](/docs/condition-operators#v1-supports-only-top-level-field-access))
still applies to the resolved value.

```typescript
interface Post { status: string; author: { name: string } }

const read = createAction("Read");
const post = createSubject<Post>("Post", {
  authorName: (instance) => instance.author.name,
});

const policy = Policy.from({
  version: "1.0.0",
  rules: [["allow", "Read", "Post", { authorName: "Alice" }]],
});

policy.can(read, post.wrap({ status: "draft", author: { name: "Alice" } })); // true
```

Note the rule above is a plain object, not built via `PolicyBuilder.allow()`:
a mapped-only field like `authorName` has no static type (`Condition<TSubject>`
only knows about `TSubject`'s real properties), so `.allow()`'s typed
`conditions` parameter can't reference one — build that rule as a plain
object instead, as above.

Attach a mapper directly via `createSubject`'s second parameter (carried
through every `.wrap()` unchanged), or register several centrally via a
`SubjectFieldMapperCatalog` and hand it to `Policy`/`PolicyBuilder`
through `KeycardConfig.mapper` — a subject's own mapper, if it has one,
always takes precedence over the catalog:

```typescript
const catalog = new SubjectFieldMapperCatalog({
  Post: { authorName: (instance: Post) => instance.author.name },
});

const policy = Policy.from(policyDef, {}, { mapper: catalog });
```

- `SubjectFieldMapper<TData>` — `Record<string, (instance: TData) => unknown>`
- `new SubjectFieldMapperCatalog(entries?: Record<string, SubjectFieldMapper>)`
- `.register(subjectName, mapper)` — register one more mapper
- `.get(subjectName)` — look up a registered mapper, or `undefined`

## See also

- [SPEC.md](https://github.com/CptnFizzbin/keycard/blob/main/SPEC.md) —
  complete specification
- [Guide](/docs/intro) — language-agnostic concepts
