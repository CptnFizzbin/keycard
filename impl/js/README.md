# KeyCard - JavaScript

JavaScript access control library inspired by CASL.js. Provides strongly-typed,
composable authorization policies with compile-time safety for Actions and
Subjects. Runs in the browser as well as server-side (Node.js and other JS
runtimes).

## Features

- **Type-safe Actions & Subjects**: Branded types prevent typos and ensure
  type-safe refactoring
- **Composable**: Build complex policies from simple rules
- **Flexible conditions**: Support for comparison, pattern matching, and logical
  operators
- **Cross-language**: PolicyDefinitions serialize to JSON for cross-platform use
- **Extensible**: Custom condition operators support

## Installation

```bash
npm install @cptn-fizzbin/keycard
```

## Quick Start

```typescript
import {
  createAction,
  createSubject,
  PolicyBuilder,
  Policy
} from '@cptn-fizzbin/keycard';
import type {InferActions, InferSubjects} from '@cptn-fizzbin/keycard';

// Define your action and subject types
const Actions = {
  Create: createAction("Create"),
  Update: createAction("Update"),
  Delete: createAction("Delete"),
} as const;

const Subjects = {
  Article: createSubject<{
    id: number;
    owner_id: number;
    status: string
  }>("Article"),
} as const;

// InferActions/InferSubjects derive the union types PolicyBuilder
// and Policy expect, so you don't have to spell out
// `typeof Actions[keyof typeof Actions]` by hand.
type AppActions = InferActions<typeof Actions>;
type AppSubjects = InferSubjects<typeof Subjects>;

// Build a policy
const policyDef = new PolicyBuilder<AppActions, AppSubjects>()
  .allow(Actions.Create, Subjects.Article)
  .allow(Actions.Update, Subjects.Article, {owner_id: 1})
  .deny(Actions.Delete, Subjects.Article, {status: {$not: "archived"}})
  .buildDef();

// Create and use policy
const policy = new Policy<AppActions, AppSubjects>(policyDef);

// Check by subject definition
if (policy.can(Actions.Create, Subjects.Article)) {
  // Create article
}

// Check by subject instance
const article = Subjects.Article.wrap({
  id: 1,
  owner_id: 1,
  status: "published"
});
if (policy.can(Actions.Update, article)) {
  // Update article
}

// Require permission (throws if denied)
policy.require(Actions.Delete, article); // Throws PolicyError if not allowed
```

## Type Safety

KeyCard provides compile-time type safety:

- Actions can only be created with `createAction`
- Subjects must match their defined shape
- Policy methods only accept valid Action/Subject combinations
- Refactoring actions/subjects updates all policy rules

See [TYPE_SAFETY.md](../TYPE_SAFETY.md) for detailed examples.

## Condition Operators

- `$eq` - Equality
- `$gt` - Greater than
- `$gte` - Greater than or equal
- `$lt` - Less than
- `$lte` - Less than or equal
- `$in` - Value in array
- `$has` - Array contains value
- `$substr` - Substring pattern match (a small, non-regex pattern language - see
  SPEC_V1-0.md §7.4.6)
- `$or` - Logical OR
- `$and` - Logical AND
- `$not` - Logical NOT
- `$field` - Explicit field access, for a field whose name itself starts with "$
  "
- Field conditions - Check nested properties

## API

### createAction<T>(name: T)

Create a typed action.

### createSubject<TSubject>(name: string)

Create a typed subject definition.

### InferActions<T> / InferSubjects<T>

Derive the `Action` / `Subject` union types that `PolicyBuilder`
and `Policy` expect from an actions or subjects map, e.g.
`InferActions<typeof Actions>`, so callers don't have to write
`typeof Actions[keyof typeof Actions]` by hand.

### PolicyBuilder<TActions, TSubjects>

- `allow(action, subject, conditions?)` - Allow action
- `deny(action, subject, conditions?)` - Deny action
- `buildDef()` - Create PolicyDefinition
- `build()` - Create Policy instance (coming soon)

### Subject<T>

A single type covering both a bare subject (no instance) and a wrapped
instance - `instance` is `undefined` until `.wrap()` is called.

- `wrap(obj: T)` - Returns a new `Subject<T>` of the same name, with `instance`
  set
- `name` - Subject name
- `instance` - The wrapped object, if any

### Policy<TActions, TSubjects>

- `can(action, subject)` - Check if action is allowed
- `cannot(action, subject)` - Check if action is denied
- `require(action, subject)` - Throw if not allowed
- `def()` - Get underlying definition

## Examples

See `src/example.ts` for a complete working example.

## See Also

- [SPEC.md](../SPEC.md) - Complete specification
- [TYPE_SAFETY.md](../TYPE_SAFETY.md) - Type safety deep dive
- [Rust implementation](../rust)
