---
title: Examples
sidebar_label: Examples
slug: /examples
---

# Examples (JavaScript / TypeScript)

### Schema-only check

No conditions needed — this checks whether the action/subject pair is
allowed at all, ignoring any specific instance:

```typescript
policy.can(Actions.Create, Subjects.Article);
```

### Condition-based check

```typescript
const article = Subjects.Article.wrap({ id: 1, owner_id: userId, status: "published" });
policy.can(Actions.Update, article);
```

### Multiple conditions

```typescript
new PolicyBuilder()
  .allow(Actions.Update, Subjects.Article, {
    $and: [
      { owner_id: userId },
      { status: { $not: "archived" } },
    ],
  })
  .buildDef();
```

### Custom error handling

```typescript
try {
  policy.require(Actions.Delete, article);
} catch (err) {
  console.warn("Access denied:", err.message);
  sendError(403, "You do not have permission to delete this article");
}
```

### Cross-language usage

`PolicyDefinition`s are plain JSON, so a policy built in one language can
be evaluated in another:

```typescript
// Build & serialize in TypeScript
const json = JSON.stringify(policy.def());

// ...ship `json` to a Java service, a Rust service, a browser, etc.
// It can be loaded with any conformant KeyCard implementation.
```

See `src/example.ts` in the
[`impl/js`](https://github.com/CptnFizzbin/keycard/tree/main/impl/js)
package for a complete working example.
