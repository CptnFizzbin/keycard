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
import type {InferActions, InferSubjects, KeycardConfig} from '@cptn-fizzbin/keycard';

// Define your action and subject types
const Actions = {
  create: createAction("create"),
  update: createAction("update"),
  delete: createAction("delete"),
} as const;

const Subjects = {
  article: createSubject<{
    id: number;
    ownerId: number;
  }>("article"),
} as const;

// InferActions/InferSubjects derive the union types PolicyBuilder
// and Policy expect, so you don't have to spell out
// `typeof Actions[keyof typeof Actions]` by hand.
type AppActions = InferActions<typeof Actions>;
type AppSubjects = InferSubjects<typeof Subjects>;

// Bundle the action/subject vocabulary into one KeycardConfig, built once
// and shared by every PolicyBuilder/Policy instead of kept in sync by hand
const config: KeycardConfig = {
  actions: Actions,
  subjects: Subjects,
};

// Build a policy scoped to one user
function createUserPolicy(user: {id: number}): Policy<AppActions, AppSubjects> {
  return new PolicyBuilder<AppActions, AppSubjects>(config)
    .allow(Actions.create, Subjects.article)
    .allow(Actions.update, Subjects.article, {ownerId: user.id})
    .build();
}

const policy = createUserPolicy({id: 1});

// Check by subject definition
if (policy.can(Actions.create, Subjects.article)) {
  // Create article
}

// Check by subject instance
const article = Subjects.article.wrap({id: 1, ownerId: 1});
if (policy.can(Actions.update, article)) {
  // Update article
}

// Require permission (throws if denied)
policy.require(Actions.delete, article); // Throws PolicyError if not allowed
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
  SPEC_V0.md §7.4.6)
- `$or` - Logical OR
- `$and` - Logical AND
- `$not` - Logical NOT
- `$field` - Explicit field access, for a field whose name itself starts with "$
  "
- Field conditions - Check nested properties

## API

### createAction<T>(name?: T)

Create a typed action. Called with no `name`, generates a random id and
marks the Action dynamic - see `KeycardConfig` below.

### createSubject<TData>(name?: string, fieldMapper?) / createSubject<TData>(options?)

Create a typed subject definition. Called with a `name`, behaves as above.
Called with no `name` (options omitted, or an options object with no `name`
of its own - `{ from?, fieldMapper? }`), generates a random id and marks the
Subject dynamic. `options.from` maps one or more raw domain entities into
this Subject's claims shape for `.from(...)` - see `Subject<T>` below.

### InferActions<T> / InferSubjects<T>

Derive the `Action` / `Subject` union types that `PolicyBuilder`
and `Policy` expect from an actions or subjects map, e.g.
`InferActions<typeof Actions>`, so callers don't have to write
`typeof Actions[keyof typeof Actions]` by hand.

### PolicyBuilder<TActions, TSubjects, TOperators>

`new PolicyBuilder(config?)` - `config` is an optional `KeycardConfig`
(see below), shared with `Policy`.

- `allow(action, subject, conditions?)` - Allow action
- `deny(action, subject, conditions?)` - Deny action
- `buildDef()` - Create PolicyDefinition
- `build()` - Create Policy instance

### Subject<T>

A single type covering both a bare subject (no instance) and a wrapped
instance - `instance` is `undefined` until `.wrap()`/`.from()` is called.

- `wrap(obj: T)` - Returns a new `Subject<T>` of the same name, with `instance`
  set to `obj`
- `from(...args)` - Returns a new `Subject<T>` of the same name, with
  `instance` set to what `createSubject`'s `from` mapper computes from
  `args`; without a `from` mapper, behaves exactly like `wrap()`
- `name` - Subject name
- `instance` - The wrapped object, if any

### Policy<TActions, TSubjects, TOperators>

`new Policy(definition, config?)` - or the static `Policy.from(definition,
config?)`. `config` is an optional `KeycardConfig` (see below), shared with
`PolicyBuilder`.

- `can(action, subject)` - Check if action is allowed
- `cannot(action, subject)` - Check if action is denied
- `require(action, subject)` - Throw if not allowed
- `def()` - Get underlying definition

### KeycardConfig<TOperators>

Optional, shared config accepted as the sole extra constructor argument by
both `PolicyBuilder` and `Policy` - one object bundling the action/subject
vocabulary (`actions: ActionCatalog`, `subjects: SubjectCatalog` - keyed
`Record<string, Action | Subject>` catalogs, additive to `meta.actions`/
`meta.subjects`), custom operators (`operators: AnyOperator[] |
OperatorCatalog`, either built via `createOperator` or a bare
`{ $name: resolver }` map), the wildcard tokens (`anyAction`, `anySubject`),
and field mappers a policy needs, built once instead of kept in sync by
hand. `emitMeta` (default `true`) gates eager catalog/operator validation
at construction, plus the diagnostic `meta.actions`/`meta.subjects`/
`meta.operators` a built `PolicyDefinition` carries.

## Examples

See `src/example.ts` for a complete working example.

## See Also

- [SPEC.md](../SPEC.md) - Complete specification
- [TYPE_SAFETY.md](../TYPE_SAFETY.md) - Type safety deep dive
- [Rust implementation](../rust)
