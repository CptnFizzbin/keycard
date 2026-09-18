---
title: Examples
sidebar_label: Examples
slug: /examples
---

# Examples (JavaScript)

Every example below builds on the same `Actions`/`Subjects` from the
[Quick start](./intro.md#quick-start):

```typescript
const Actions = {
  create: createAction("create"),
  read: createAction("read"),
  update: createAction("update"),
  delete: createAction("delete"),
} as const;

const Subjects = {
  article: createSubject<{ id: number; ownerId: number }>("article"),
  comment: createSubject<{ userId: number; articleId: number }>("comment"),
} as const;
```

...and shares one `KeycardConfig`, built once from that vocabulary and
handed to both `PolicyBuilder` and `Policy` instead of kept in sync by
hand — see [`KeycardConfig`](#keycardconfig) further down:

```typescript
const config: KeycardConfig = {
  actions: Object.values(Actions),
  subjects: Object.values(Subjects),
};
```

### Schema-only check

No conditions needed — this checks whether the action/subject pair is allowed at
all, ignoring any specific instance:

```typescript
policy.can(Actions.create, Subjects.article);
```

### Condition-based check

```typescript
const article = Subjects.article.wrap({ id: 1, ownerId: userId });
policy.can(Actions.update, article);
```

### Multiple conditions

```typescript
new PolicyBuilder({}, config)
  .allow(Actions.update, Subjects.article, {
    $and: [
      { ownerId: userId },
      { id: { $ne: 1 } },
    ],
  })
  .buildDef();
```

### Field mappers for renamed or computed fields

A `SubjectFieldMapper` resolves a condition field through an explicit
getter instead of property access — handy when a policy-facing field name
doesn't match the instance's own shape, like flattening a nested
`instance.author.name` into a single top-level `authorName` field (recall
conditions can only narrow one field deep — see
[Condition Operators](/docs/condition-operators#v1-supports-only-top-level-field-access)):

```typescript
interface Post { status: string; author: { name: string } }

const read = createAction("Read");
const post = createSubject<Post>("Post", {
  authorName: (instance) => instance.author.name,
});

const config: KeycardConfig = { actions: [read], subjects: [post] };

const policy = Policy.from(
  { version: "1.0", rules: [["allow", "Read", "Post", { authorName: "Alice" }]] },
  {},
  config,
);

policy.can(read, post.wrap({ status: "draft", author: { name: "Alice" } })); // true
```

A field the mapper doesn't define still falls back to ordinary property
access, and a mapped field's own value can still use any non-field
operator (`$substr`, `$in`, ...) — only narrowing is restricted to one
level, not operator use.

### `KeycardConfig`

Bundle actions/subjects/operators/field mappers into one object shared by
`PolicyBuilder` and `Policy`, instead of keeping each in sync by hand:

```typescript
const post = createSubject<Post>("Post");
const read = createAction("Read");

const mappers = new SubjectFieldMapperCatalog({
  Post: { authorName: (instance: Post) => instance.author.name },
});

const config = { subjects: [post], mapper: mappers };

// config.subjects widens the meta.subjects catalog, so "Post" is accepted
// here even though this raw definition declares no meta.subjects of its
// own (see Policy Definition's `meta` section for catalog enforcement):
const policy = Policy.from(
  { version: "1.0", rules: [["allow", "Read", "Post", { authorName: "Alice" }]] },
  {},
  config,
);
```

:::note
`PolicyBuilder.allow()`'s `conditions` parameter is typed against the
subject's real fields (`Condition<Post>` here) — a field that only exists
through a `SubjectFieldMapper`, like `authorName` above, has no static
type, so a mapped-only field can't be referenced through `.allow()`'s
typed API. Build that rule as a plain object instead, as shown above.
:::

### Custom error handling

```typescript
try {
  policy.require(Actions.delete, article);
} catch (err) {
  console.warn("Access denied:", err.message);
  sendError(403, "You do not have permission to delete this article");
}
```

### Cross-language usage

`PolicyDefinition`s are plain JSON, so a policy built in one language can be
evaluated in another:

```typescript
// Build & serialize in JavaScript
const json = JSON.stringify(policy.def());

// ...ship `json` to a Java service, a Rust service, a browser, etc.
// It can be loaded with any conformant KeyCard implementation.
```

See `src/example.ts` in the
[`impl/js`](https://github.com/CptnFizzbin/keycard/tree/main/impl/js)
package for a complete working example.
