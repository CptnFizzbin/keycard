---
title: Condition Operators
sidebar_label: Condition Operators
slug: /condition-operators
---

# Condition Operators

A **condition** filters *which instances* of a subject a rule applies to. It's
the fourth, optional slot of a [rule](./policy-definition.md#rules)
tuple, and it's only evaluated when you check a subject *instance*
(a wrapped object), not a bare subject name/type.

```yaml
- [ allow, Update, Article, { owner_id: 1 } ]
```

## Bare-value shorthand

A condition value that isn't itself an operator object is shorthand for
`$eq`:

```yaml
{ owner_id: 1 }
  # is shorthand for
  { owner_id: { $eq: 1 } }
```

## Comparison operators

| Operator | Matches when |
| --- | --- |
| `$eq` | `subject == value` |
| `$ne` | `subject != value` |
| `$gt` | `subject > value` |
| `$gte` | `subject >= value` |
| `$lt` | `subject < value` |
| `$lte` | `subject <= value` |
| `$in` | `value[]` contains `subject` |
| `$has` | `subject[]` contains `value` |

```yaml
{ status: { $in: [ "draft", "review" ] } }
  { tags: { $has: "featured" } }
```

:::note[Missing fields]
A field condition on a field the subject doesn't have evaluates to
`false`, whichever operator is nested inside it — **except `$ne`**. Since
`$ne` is the exact negation of `$eq` (and a missing field makes `$eq`
evaluate to `false`), `$ne` on a missing field evaluates to `true`
instead: `{ status: { $ne: "archived" } }` matches a subject with no
`status` key at all. This exception applies only when `$ne` is the *sole*
key of that condition object — in a multi-key object like
`{ author: { $ne: null, $eq: "Alice" } }`, a missing `author` field falls
back to the ordinary blanket `false`.
:::

## `$substr` — pattern matching

`{ $substr: pattern }` matches when `String(subject)` contains a substring
described by `pattern` — a small, deliberately **non-regex**
pattern language, so every implementation matches identically regardless of the
host language's regex engine.

| Token | Meaning |
| --- | --- |
| `^` | anchors the match to the start of the string (only valid as the first character) |
| `$` | anchors the match to the end of the string (only valid as the last character) |
| `*` | matches zero or more characters |
| `\` | escapes the next character, making it literal |

```yaml
{ slug: { $substr: "^draft-*" } }     # starts with "draft-"
  { slug: { $substr: "*-final$" } }     # ends with "-final"
  { title: { $substr: "*Q3*Report*" } } # contains "Q3", then "Report", in order
```

A structurally invalid pattern (a stray `^` or `$` not at a boundary)
evaluates to `false`. A `null`/`undefined` subject is an ordinary non-match, not
an error. See
[§5.4.6 of the v1.0 spec](https://github.com/CptnFizzbin/keycard/blob/main/docs/spec/SPEC_V1-0.md#546-substr)
for the exact decomposition rules.

## Logical operators

| Operator | Matches when                                        |
|----------|-----------------------------------------------------|
| `$or`    | at least one sub-condition in `Condition[]` matches |
| `$and`   | every sub-condition in `Condition[]` matches        |
| `$not`   | the wrapped `Condition` does **not** match          |

```yaml
- [ allow, Update, Article, {
  $and: [
    { owner_id: 1 },
    { status: { $not: "archived" } }
  ]
} ]
```

## Field access

By default, a key in a condition object that isn't an operator (doesn't start
with `$`) is treated as a **field name**, and its value is evaluated as a
condition against that field of the subject:

```yaml
{ owner_id: 1 }                  # subject.owner_id == 1
  { author: { $ne: null } }        # subject.author != null
{ tags: { $has: "featured" } }   # subject.tags contains "featured"
```

### v1 supports only top-level field access

A field condition may narrow into a subject's field **exactly once** — the
`Condition` it narrows into must not itself be another field condition
(bare-key or `$field`), whether directly or nested inside `$or`/`$and`/`$not`:

```yaml
{ author: { name: "Alice" } }   # invalid — { name: "Alice" } is itself
                                 # a field condition, a second level of narrowing
```

This evaluates to `false` and produces the usual diagnostic, the same as
any other malformed condition shape. Reaching into a nested object
(`subject.author.name`) — or a dot-path/array-index shorthand for it — is
out of scope for v1 and reserved for a future version. Comparison,
collection, string, logical, and custom operators are unaffected, since
they don't narrow the subject: `{ author: { $ne: null } }` and
`{ tags: { $has: "featured" } }` above are both ordinary, valid top-level
field conditions.

### `$field` — explicit field access

If a field's name itself starts with `$`, use `$field: [key, Condition]`
to disambiguate it from an operator. The same top-level-only restriction
applies: `Condition` here must not itself be a field condition.

```yaml
{ $field: [ "$special", { $eq: "value" } ] }   # subject["$special"] == "value"
```

## Custom operators

Implementations may support custom, application-defined operators (`$op`) in
addition to the built-ins above — see each language guide's API reference for
how to register one, and
[§5.5 of the v1.0 spec](https://github.com/CptnFizzbin/keycard/blob/main/docs/spec/SPEC_V1-0.md#55-custom-operators-op)
for the requirements a custom operator must satisfy.
