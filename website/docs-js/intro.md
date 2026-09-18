---
title: JavaScript
sidebar_label: Introduction
slug: /intro
---

# KeyCard for JavaScript

:::warning[Pre-alpha]
KeyCard is in **pre-alpha**. The API and policy format are still subject to
breaking changes without notice. It is not yet recommended for production
use.
:::

JavaScript access-control library inspired by CASL.js, written in
TypeScript with full type definitions included. Provides strongly-typed,
composable authorization policies with compile-time safety for Actions and
Subjects. Runs in the browser as well as server-side (Node.js and other JS
runtimes).

New to KeyCard's concepts? Read the language-agnostic **[Guide](/docs/intro)**
first — Policy Definition, Rules, and the condition language are the same across
every implementation.

## Features

- **Type-safe Actions & Subjects** — branded types prevent typos and ensure
  type-safe refactoring
- **Composable** — build complex policies from simple rules
- **Flexible conditions** — comparison, pattern matching, and logical operators
- **Cross-language** — `PolicyDefinition`s serialize to JSON for cross-platform
  use
- **Extensible** — custom condition operators

## Installation

```bash
npm install @cptn-fizzbin/keycard
```

## Quick start

```typescript
import { createAction, createSubject, PolicyBuilder, Policy } from '@cptn-fizzbin/keycard';
import type { InferActions, InferSubjects, KeycardConfig } from '@cptn-fizzbin/keycard';

// Define your action and subject types
const Actions = {
  create: createAction("create"),
  update: createAction("update"),
  delete: createAction("delete"),
} as const;

const Subjects = {
  article: createSubject<{ id: number; ownerId: number }>("article"),
} as const;

// InferActions/InferSubjects derive the union types PolicyBuilder
// and Policy expect, so you don't have to spell out
// `typeof Actions[keyof typeof Actions]` by hand.
type AppActions = InferActions<typeof Actions>;
type AppSubjects = InferSubjects<typeof Subjects>;

// Bundle the action/subject vocabulary into one KeycardConfig, built once
// and shared by every PolicyBuilder/Policy instead of kept in sync by hand
const config: KeycardConfig = {
  actions: Object.values(Actions),
  subjects: Object.values(Subjects),
};

// Build a policy scoped to one user
function createUserPolicy(user: { id: number }): Policy<AppActions, AppSubjects> {
  return new PolicyBuilder<AppActions, AppSubjects>({}, config)
    .allow(Actions.create, Subjects.article)
    .allow(Actions.update, Subjects.article, { ownerId: user.id })
    .build();
}

const policy = createUserPolicy({ id: 1 });

// Check by subject definition
if (policy.can(Actions.create, Subjects.article)) {
  // Create article
}

// Check by subject instance
const article = Subjects.article.wrap({ id: 1, ownerId: 1 });
if (policy.can(Actions.update, article)) {
  // Update article
}

// Require permission (throws if denied)
policy.require(Actions.delete, article); // Throws PolicyError if not allowed
```

## Type safety

KeyCard provides compile-time type safety:

- Actions can only be created with `createAction`
- Subjects must match their defined shape
- Policy methods only accept valid Action/Subject combinations
- Refactoring actions/subjects updates all policy rules

Continue to the [API Reference](./api-reference.md), or see
[Examples](./examples.md) for more complete walkthroughs.
