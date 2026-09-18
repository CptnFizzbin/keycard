---
title: Glossary
sidebar_label: Glossary
slug: /glossary
---

# Glossary

Term definitions used throughout the KeyCard docs and spec.

- **Claims** — an object of values used either for building a policy
  (**Policy Claims**) or for checking a subject (**Subject Claims**).
  - **Policy Claims** — the actor-side input a `PolicyBuilder` uses to
    decide which rules to generate (e.g. a JWT, or
    `{ ownerOf: number[] }`).
  - **Subject Claims** — the resource-side fields a `Subject` carries for
    Conditions to check against when a `Policy` is evaluated. Should be
    composable and scoped to only the fields a policy's Conditions
    actually need — not the raw entity (e.g.
    `{ ownerId: number, status: string }` for an `Article`).
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
