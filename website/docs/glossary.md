---
title: Glossary
sidebar_label: Glossary
slug: /glossary
---

# Glossary

Term definitions used throughout the KeyCard docs and spec.

- **Claims** — Object(s) used by a builder to create a Policy Definition
  (e.g. a JWT, or `{ ownerOf: number[] }`).
- **Action** — a string that indicates the user wants to do something to
  a subject (e.g. `Create`, `Read`, `Update`, `Delete`, `MarkDone`,
  `Archive`, ...).
- **Subject** — the value the user wants to do something with (e.g.
  `ToDoItem`, `Project`, ...).
- **PolicyBuilder** — takes in claims and produces a `Policy` or
  `PolicyDefinition`.
- **Rule** — an allowed or denied tuple of effect, action, subject, and
  conditions.
- **PolicyDefinition** (PolicyDef) — a text-based (JSON-encodable)
  encoding of what permissions a user is allowed or denied.
- **Policy** — an object used to perform checks against.
- **Building a Policy** — using a `PolicyBuilder` to create a
  `PolicyDefinition`.
- **Constructing a Policy** — reading a `PolicyDefinition` and converting
  it into a `Policy`.
- **Evaluating a Policy** — performing a check (an action and a subject)
  against a `Policy`.

See [Introduction](./intro.md) for how these fit together, and
[Policy Definition](./policy-definition.md) /
[Condition Operators](./condition-operators.md) for the detailed formats.
