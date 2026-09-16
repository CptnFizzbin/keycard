---
title: API Reference
sidebar_label: API Reference
slug: /api-reference
---

# API Reference (Java)

## `ActionFactory`

Create type-safe actions:

```java
Action<String> create = ActionFactory.create("Create");
```

## `SubjectFactory`

Create type-safe subjects:

```java
Subject<Article> article = SubjectFactory.create("Article");
```

## `Subject<T>`

A single type covering both a bare subject (no instance) and a wrapped
instance — `getInstance()` is empty until `.wrap()` is called.

- `getName()` — get subject name
- `getInstance()` — get the wrapped object, if any, as an `Optional<T>`
- `wrap(T obj)` — returns a new `Subject<T>` of the same name, with its instance
  set

## `PolicyBuilder`

Build policies with a fluent API:

- `allow(action, subject)` — allow action
- `allow(action, subject, conditions)` — allow with conditions
- `deny(action, subject)` — deny action
- `deny(action, subject, conditions)` — deny with conditions
- `build()` — create a `Policy`
- `buildDefinition()` — create a `PolicyDefinition`

## `Policy`

Check permissions:

- `can(action, subject)` — check if action is allowed
- `cannot(action, subject)` — check negation
- `require(action, subject)` — require permission (throws on denial)
- `getDefinition()` — get the underlying definition

## `ConditionResolver`

Evaluates conditions:

- `evaluate(subject, condition)` — evaluate a condition

## `PolicyDefinition`

Serializable policy, per
[SPEC_V1-0-0.md §3](https://github.com/CptnFizzbin/keycard/blob/main/SPEC_V1-0-0.md#3-terminology):

- `getVersion()` — get the SemVer spec version, e.g. `"1.0"`
- `getMeta()` — get the optional `meta` object (wildcard tokens, catalogues,
  application data)
- `getRules()` — get the ordered list of
  `[effect, action, subject, conditions?]` rules

## `PolicyException`

Thrown when a permission check fails with `require()`.

## Condition operators

`$eq`, `$gt`, `$gte`, `$lt`, `$lte`, `$in`, `$has`, `$substr`, `$or`,
`$and`, `$not`, `$field`, and plain field conditions are all supported — see the
language-agnostic [Condition Operators](/docs/condition-operators)
reference for the full semantics.

## See also

- [SPEC.md](https://github.com/CptnFizzbin/keycard/blob/main/SPEC.md) — complete
  specification
- [Guide](/docs/intro) — language-agnostic concepts
- [JavaScript implementation](/js/intro)
