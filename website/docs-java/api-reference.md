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

// With a field mapper (see SubjectFieldMapper below)
Subject<Article> mapped = SubjectFactory.create("Article", articleFieldMapper);
```

## `Subject<T>`

A single type covering both a bare subject (no instance) and a wrapped
instance — `getInstance()` is empty until `.wrap()` is called.

- `getName()` — get subject name
- `getInstance()` — get the wrapped object, if any, as an `Optional<T>`
- `getFieldMapper()` — get the `SubjectFieldMapper<T>` attached at creation,
  if any, as an `Optional`
- `wrap(T obj)` — returns a new `Subject<T>` of the same name (and field
  mapper, unchanged), with its instance set

## `PolicyBuilder`

Build policies with a fluent API. Constructors: no-arg; `(Collection<Operator>)`;
`(Object anyAction, Object anySubject)`; `(Object anyAction, Object anySubject,
Collection<Operator>)`; and `(KeycardConfig)` (see below).

- `allow(action, subject)` — allow action
- `allow(action, subject, conditions)` — allow with conditions
- `deny(action, subject)` — deny action
- `deny(action, subject, conditions)` — deny with conditions
- `build()` — create a `Policy`
- `buildDef()` — create a `PolicyDefinition`

## `Policy`

Check permissions. Constructors/factories: `(PolicyDefinition)`;
`(PolicyDefinition, Collection<Operator>)`; `(PolicyDefinition, ConditionResolver)`;
`(PolicyDefinition, KeycardConfig)`; and the equivalent static
`from(...)`/`fromDto(...)` overloads.

- `can(action, subject)` — check if action is allowed
- `cannot(action, subject)` — check negation
- `require(action, subject)` — require permission (throws on denial)
- `getDefinition()` — get the underlying definition

## `ConditionResolver`

Evaluates conditions:

- `evaluate(subject, condition)` — evaluate a condition

## `PolicyDefinition`

Serializable policy, per
[SPEC_V1-0.md §3](https://github.com/CptnFizzbin/keycard/blob/main/docs/spec/SPEC_V1-0.md#3-terminology):

- `getVersion()` — get the SemVer spec version, e.g. `"1.0.0"`
- `getMeta()` — get the optional `meta` object (wildcard tokens,
  catalogues, application data)
- `getRules()` — get the ordered list of
  `[effect, action, subject, conditions?]` rules

## `PolicyException`

Thrown when a permission check fails with `require()`.

## Condition operators

`$eq`, `$ne`, `$gt`, `$gte`, `$lt`, `$lte`, `$in`, `$has`, `$substr`, `$or`,
`$and`, `$not`, `$field`, and plain field conditions are all supported —
see the language-agnostic [Condition Operators](/docs/condition-operators)
reference for the full semantics.

## `Conditions`

A type-safe condition builder for Java's `Map<String, Object>` condition
shape, using method references instead of hand-written `Map.of(...)`
literals. Each helper extracts the field name from the getter reference
(e.g. `Article::getOwnerId` → `"ownerId"`) — this relies on the reference
being a plain, serializable method reference to a real `getXxx()`/`isXxx()`
method (a lambda expression won't work), so the subject class needs an
actual getter for every field referenced this way — e.g. via Lombok's
`@Getter`, or hand-written. A field resolved only through a
`SubjectFieldMapper` (no real getter backing it) can't be referenced with
`Conditions`; build that one as a plain `Map` instead (see
[SubjectFieldMapper](#subjectfieldmappert--subjectfieldmappercatalog) below).

```java
Conditions.eq(Article::getOwnerId, 1);                    // { ownerId: { $eq: 1 } }
Conditions.ne(Article::getStatus, "archived");             // { status: { $ne: "archived" } }
Conditions.and(
    Conditions.eq(Article::getOwnerId, 1),
    Conditions.ne(Article::getStatus, "archived")
);
```

- `eq` / `ne` / `gt` / `gte` / `lt` / `lte` / `in` / `has` / `substr` —
  `Conditions.op(getter, value)` for the matching built-in operator
- `field(getter, value)` — bare-value field condition (shorthand for `$eq`)
- `field(String fieldName, Object condition)` — the `$field` long form, for
  a field name that itself starts with `$`
- `op(String operatorName, Object value)` — escape hatch for any registered
  operator with no dedicated helper (built-in or custom)
- `and(Map<String, Object>...)` / `or(Map<String, Object>...)` /
  `not(Map<String, Object>)` — logical combinators

## `KeycardConfig`

Optional, shared config accepted by both `PolicyBuilder` and `Policy`
(alongside their existing constructors) — one object bundling the wildcard
tokens, action/subject vocabulary, custom operators, and field mappers a
policy needs, built once instead of kept in sync by hand across both. Built
with Lombok's generated builder; every field is independently optional.

```java
KeycardConfig config = KeycardConfig.builder()
    .anyAction("*")                       // like the (Object, Object) constructors' anyAction
    .anySubject(false)                    // Boolean.FALSE disables the subject wildcard
    .action(ActionFactory.create("Read")) // .action(...)/.actions(List.of(...)) — additive
    .subject(SubjectFactory.create("Article"))
    .operator(hasRoleOperator)
    .mapper(fieldMapperCatalog)
    .build();

Policy policy = new PolicyBuilder(config)
    .allow(read, article)
    .build();
```

- `anyAction` / `anySubject` — the wildcard tokens (§4.2.1). Unlike the
  `(Object, Object)` constructors, leaving these unset here means "not
  declared" (`"_ANY_"` applies) rather than passing `null` through; use
  `Boolean.FALSE` to disable a wildcard explicitly.
- `actions` / `subjects` — declared vocabulary, **additive** to whatever
  `PolicyBuilder.allow`/`.deny` actually used, or to `meta.actions`/
  `meta.subjects` already on a `PolicyDefinition` a `Policy` is constructed
  from (§4.2.2's catalog enforcement — see [Policy Definition](/docs/policy-definition)).
- `operators` — custom operators, used instead of any separately-passed
  `Collection<Operator>`.
- `mapper` — a `SubjectFieldMapperCatalog`, consulted as a fallback for any
  subject that doesn't carry its own field mapper.

## `SubjectFieldMapper<T>` / `SubjectFieldMapperCatalog`

Per-field getters for a subject's wrapped instance — the explicit
counterpart to KeyCard's default reflection-based field access. Lets a
condition reference a field whose name doesn't match the instance's own
field names (a rename, a computed/derived value), or an instance whose
fields reflection can't reach. A field the mapper doesn't define still
falls back to ordinary reflection — and the one-level-deep restriction
(see [Condition Operators](/docs/condition-operators#v1-supports-only-top-level-field-access))
still applies to the resolved value.

```java
SubjectFieldMapper<Post> mapper = SubjectFieldMapper.<Post>builder()
    .field("authorName", (post) -> post.getAuthor().getName())
    .build();

Subject<Post> post = SubjectFactory.create("Post", mapper);
```

Attach a mapper directly (`SubjectFactory.create(name, mapper)`, carried
through every `.wrap()` unchanged), or register several centrally via a
catalog and hand it to `Policy`/`PolicyBuilder` through `KeycardConfig.mapper` —
a subject's own mapper, if it has one, always takes precedence over the
catalog:

```java
SubjectFieldMapperCatalog catalog = SubjectFieldMapperCatalog.builder()
    .register("Post", mapper)
    .build();

KeycardConfig config = KeycardConfig.builder().mapper(catalog).build();
Policy policy = Policy.from(policyDef, config);
```

- `SubjectFieldMapper.<T>builder().field(name, getter).build()` — build a
  mapper; `getter` is a `Function<T, Object>`
- `SubjectFieldMapperCatalog.builder().register(subjectName, mapper).build()` —
  build a catalog
- `SubjectFieldMapperCatalog#get(String subjectName)` — look up a
  registered mapper, as an `Optional`

## See also

- [SPEC.md](https://github.com/CptnFizzbin/keycard/blob/main/SPEC.md) —
  complete specification
- [Guide](/docs/intro) — language-agnostic concepts
- [JavaScript implementation](/js/intro)
