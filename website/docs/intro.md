---
title: Introduction
sidebar_label: Introduction
slug: /intro
---

# KeyCard

**KeyCard** is a cross-language access-control library, strongly inspired
by [CASL.js](https://casl.js.org/). It lets you **define** an authorization
policy once — in one place, in one format — and **enforce** it anywhere:
server-side, client-side, in a different process, or in a completely
different programming language.

:::tip[Pick a language]
This guide covers the language-agnostic concepts: the policy format, the
condition language, and the evaluation rules every KeyCard implementation
must follow. For installation instructions and language-specific API docs,
use the **Language** selector in the navbar above, or jump straight to the
**[JavaScript / TypeScript](/js/intro)** or **[Java](/java/intro)** guide.
:::

## Why KeyCard

Most authorization libraries tie you to one language and one runtime.
KeyCard separates the two halves of the problem:

1. **Building** a policy — a fluent, type-safe `PolicyBuilder` API that
   turns a set of `allow` / `deny` rules into a `PolicyDefinition`.
2. **Evaluating** a policy — a `Policy` object that answers `can(action,
   subject)` questions against a `PolicyDefinition`.

A `PolicyDefinition` is a small, order-significant, JSON-encodable
document. Build it once in your backend of choice, hand the JSON to a
browser, a mobile client, or a service written in another language, and
every one of them evaluates the exact same rules the exact same way.

## Core concepts

| Term | Meaning |
| --- | --- |
| **Claims** | The input used to build a policy — a JWT, a user record, anything |
| **Action** | What the user wants to do — `Create`, `Read`, `Update`, `Delete`, ... |
| **Subject** | What the user wants to do it to — `Article`, `Project`, ... |
| **Rule** | An `[effect, action, subject, conditions?]` tuple |
| **PolicyBuilder** | Fluent API that turns claims into a `PolicyDefinition` |
| **PolicyDefinition** | The serializable, JSON-encodable output of a builder |
| **Policy** | The object you actually call `.can()` / `.cannot()` / `.require()` on |

See the [Glossary](./glossary.md) for the complete list of terms.

## A minimal policy

```yaml
version: "1.0.0" # KeyCard policy spec version (SemVer)
meta:
  actions: [Create, Update, Delete]
  subjects: [Article]
rules:
  - [allow, Create, Article]                                     # anyone may create an article
  - [allow, Update, Article, { owner_id: 1 }]                     # only the owner may update
  - [deny, Delete, Article, { status: { $not: "archived" } }]     # can't delete unless archived
```

- `rules` is a single, order-significant list. The **last** matching rule
  wins — it is not "any deny beats any allow."
- Every policy has an implicit wildcard token, `_ANY_`, for both actions
  and subjects (e.g. `[allow, _ANY_, _ANY_]` matches anything). A policy
  may rename or disable either wildcard via `meta.anyAction` /
  `meta.anySubject`.
- With no matching rule, the default is **deny**.

Continue to [Policy Definition](./policy-definition.md) for the full
structure, or [Condition Operators](./condition-operators.md) for the
condition language used in the `conditions` slot of a rule.

## Normative specification

This guide is an informal overview. The exact rule-evaluation algorithm,
the full condition-operator table, and the catalogue of required
edge-case behavior live in the normative
[**v1.0.0 specification**](https://github.com/CptnFizzbin/keycard/blob/main/docs/spec/SPEC_V1-0.md),
which every language implementation is validated against with a shared
conformance suite.
