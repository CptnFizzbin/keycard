---
title: API Reference
sidebar_label: API Reference
slug: /api-reference
---

# API Reference (JavaScript)

## `createAction<T>(name: T)`

Create a typed action.

## `createSubject<TSubject>(name: string)`

Create a typed subject definition.

## `InferActions<T>` / `InferSubjects<T>`

Derive the `Action` / `Subject` union types that `PolicyBuilder` and
`Policy` expect from an actions or subjects map, e.g.
`InferActions<typeof Actions>`, so callers don't have to write
`typeof Actions[keyof typeof Actions]` by hand.

## `PolicyBuilder<TActions, TSubjects>`

- `allow(action, subject, conditions?)` — allow action
- `deny(action, subject, conditions?)` — deny action
- `buildDef()` — create a `PolicyDefinition`
- `build()` — create a `Policy` instance (coming soon)

## `Subject<T>`

A single type covering both a bare subject (no instance) and a wrapped
instance — `instance` is `undefined` until `.wrap()` is called.

- `wrap(obj: T)` — returns a new `Subject<T>` of the same name, with
  `instance` set
- `name` — subject name
- `instance` — the wrapped object, if any

## `Policy<TActions, TSubjects>`

- `can(action, subject)` — check if action is allowed
- `cannot(action, subject)` — check if action is denied
- `require(action, subject)` — throw if not allowed
- `def()` — get the underlying definition

## Condition operators

`$eq`, `$gt`, `$gte`, `$lt`, `$lte`, `$in`, `$has`, `$substr`, `$or`,
`$and`, `$not`, `$field`, and plain field conditions are all supported — see the
language-agnostic [Condition Operators](/docs/condition-operators)
reference for the full semantics.

## See also

- [SPEC.md](https://github.com/CptnFizzbin/keycard/blob/main/SPEC.md) — complete
  specification
- [Guide](/docs/intro) — language-agnostic concepts
