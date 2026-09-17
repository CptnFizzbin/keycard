---
title: JavaScript
sidebar_label: Introduction
slug: /intro
---

# KeyCard for JavaScript

JavaScript access-control library inspired by CASL.js, written in
TypeScript with full type definitions included. Provides strongly-typed,
composable authorization policies with compile-time safety for Actions
and Subjects. Runs in the browser as well as server-side (Node.js and
other JS runtimes).

New to KeyCard's concepts? Read the language-agnostic
**[Guide](/docs/intro)** first — Policy Definition, Rules, and the
condition language are the same across every implementation.

## Features

- **Type-safe Actions & Subjects** — branded types prevent typos and
  ensure type-safe refactoring
- **Composable** — build complex policies from simple rules
- **Flexible conditions** — comparison, pattern matching, and logical
  operators
- **Cross-language** — `PolicyDefinition`s serialize to JSON for
  cross-platform use
- **Extensible** — custom condition operators

## Installation

```bash
npm install @cptn-fizzbin/keycard
```

## Quick start

```typescript
import { createAction, createSubject, PolicyBuilder, Policy } from '@cptn-fizzbin/keycard';
import type { InferActions, InferSubjects } from '@cptn-fizzbin/keycard';

// Define your action and subject types
const Actions = {
  Create: createAction("Create"),
  Update: createAction("Update"),
  Delete: createAction("Delete"),
} as const;

const Subjects = {
  Article: createSubject<{ id: number; owner_id: number; status: string }>("Article"),
} as const;

// InferActions/InferSubjects derive the union types PolicyBuilder
// and Policy expect, so you don't have to spell out
// `typeof Actions[keyof typeof Actions]` by hand.
type AppActions = InferActions<typeof Actions>;
type AppSubjects = InferSubjects<typeof Subjects>;

// Build a policy
const policyDef = new PolicyBuilder<AppActions, AppSubjects>()
  .allow(Actions.Create, Subjects.Article)
  .allow(Actions.Update, Subjects.Article, { owner_id: 1 })
  .deny(Actions.Delete, Subjects.Article, { status: { $not: "archived" } })
  .buildDef();

// Create and use policy
const policy = new Policy<AppActions, AppSubjects>(policyDef);

// Check by subject definition
if (policy.can(Actions.Create, Subjects.Article)) {
  // Create article
}

// Check by subject instance
const article = Subjects.Article.wrap({ id: 1, owner_id: 1, status: "published" });
if (policy.can(Actions.Update, article)) {
  // Update article
}

// Require permission (throws if denied)
policy.require(Actions.Delete, article); // Throws PolicyError if not allowed
```

## Type safety

KeyCard provides compile-time type safety:

- Actions can only be created with `createAction`
- Subjects must match their defined shape
- Policy methods only accept valid Action/Subject combinations
- Refactoring actions/subjects updates all policy rules

Continue to the [API Reference](./api-reference.md), or see
[Examples](./examples.md) for more complete walkthroughs.
