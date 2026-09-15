---
title: Condition Operators
sidebar_label: Condition Operators
slug: /condition-operators
---

# Condition Operators

A **condition** filters *which instances* of a subject a rule applies to.
It's the fourth, optional slot of a [rule](./policy-definition.md#rules)
tuple, and it's only evaluated when you check a subject *instance*
(a wrapped object), not a bare subject name/type.

```yaml
- [allow, Update, Article, { owner_id: 1 }]
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
{ status: { $in: ["draft", "review"] } }
{ tags: { $has: "featured" } }
```

## `$substr` — pattern matching

`{ $substr: pattern }` matches when `String(subject)` contains a
substring described by `pattern` — a small, deliberately **non-regex**
pattern language, so every implementation matches identically regardless
of the host language's regex engine.

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
evaluates to `false`. A `null`/`undefined` subject is an ordinary
non-match, not an error. See
[§5.4.6 of the v1.0.0 spec](https://github.com/CptnFizzbin/keycard/blob/main/SPEC_V1-0-0.md#546-substr)
for the exact decomposition rules.

## Logical operators

| Operator | Matches when |
| --- | --- |
| `$or` | at least one sub-condition in `Condition[]` matches |
| `$and` | every sub-condition in `Condition[]` matches |
| `$not` | the wrapped `Condition` does **not** match |

```yaml
- [allow, Update, Article, {
    $and: [
      { owner_id: 1 },
      { status: { $not: "archived" } }
    ]
  }]
```

## Field access

By default, a key in a condition object that isn't an operator (doesn't
start with `$`) is treated as a **field name**, and its value is
evaluated as a condition against that field of the subject:

```yaml
{ owner_id: 1 }                 # subject.owner_id == 1
{ address: { zip: "94107" } }   # subject.address.zip == "94107"
```

### `$field` — explicit field access

If a field's name itself starts with `$`, use `$field: [key, Condition]`
to disambiguate it from an operator:

```yaml
{ $field: ["$special", { $eq: "value" }] }   # subject["$special"] == "value"
```

## Custom operators

Implementations may support custom, application-defined operators
(`$op`) in addition to the built-ins above — see each language guide's
API reference for how to register one, and
[§5.5 of the v1.0.0 spec](https://github.com/CptnFizzbin/keycard/blob/main/SPEC_V1-0-0.md#55-custom-operators-op)
for the requirements a custom operator must satisfy.
