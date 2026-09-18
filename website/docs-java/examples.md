---
title: Examples
sidebar_label: Examples
slug: /examples
---

# Examples (Java)

Every example below shares one `KeycardConfig`, built once from the
actions/subjects in play and handed to both `PolicyBuilder` and `Policy`
instead of kept in sync by hand — see [`KeycardConfig`](#keycardconfig)
further down:

```java
KeycardConfig config = KeycardConfig.builder()
    .actions(List.of(create, update, delete))
    .subjects(List.of(article))
    .build();
```

### Schema-only check

```java
// Allow creating any article (no conditions needed)
policy.can(create, article);
```

### Condition-based check

```java
// Check if the user can update THIS article (with conditions)
Article data = new Article(1, userId);
Subject<Article> ref = article.wrap(data);
policy.can(update, ref);
```

### Multiple conditions

```java
new PolicyBuilder(config)
    .allow(update, article, Map.of(
        "$and", List.of(
            Map.of("ownerId", userId),
            Map.of("id", Map.of("$ne", 1))
        )
    ))
    .build();
```

### The same conditions, with `Conditions`

`Conditions` builds the same `Map<String, Object>` shape from method references,
so a typo in a field name is caught by the compiler instead of failing silently
at evaluation time:

```java
new PolicyBuilder(config)
    .allow(update, article, Conditions.and(
        Conditions.eq(Article::getOwnerId, userId),
        Conditions.ne(Article::getId, 1)
    ))
    .build();
```

### Field mappers for renamed or computed fields

A `SubjectFieldMapper` resolves a condition field through an explicit getter
instead of reflection — handy when a policy-facing field name doesn't match the
instance's own shape, like flattening a nested
`post.getAuthor().getName()` into a single top-level `authorName` field (recall
conditions can only narrow one field deep — see
[Condition Operators](/docs/condition-operators#v1-supports-only-top-level-field-access)):

```java
SubjectFieldMapper<Post> authorNameMapper = SubjectFieldMapper.<Post>builder()
    .field("authorName", (post) -> post.getAuthor().getName())
    .build();

Subject<Post> post = SubjectFactory.create("Post", authorNameMapper);
Action read = ActionFactory.create("Read");

KeycardConfig postConfig = KeycardConfig.builder()
    .action(read)
    .subject(post)
    .build();

// `Conditions`'s getter-based helpers can't reference "authorName" — it has
// no real Post.getAuthorName(), only a mapper entry — so build this one
// as a plain Map, keyed by the mapped field's synthetic name:
Policy policy = new PolicyBuilder(postConfig)
    .allow(read, post, Map.of("authorName", "Alice")) // resolved via the mapper
    .build();
```

A field the mapper doesn't define still falls back to ordinary reflection, and a
mapped field's own value can still use any non-field operator (`$substr`,
`$in`, ...) — only narrowing is restricted to one level, not operator use.

### `KeycardConfig`

Bundle actions/subjects/operators/field mappers into one object shared by
`PolicyBuilder` and `Policy`, instead of keeping each in sync by hand:

```java
SubjectFieldMapperCatalog mappers = SubjectFieldMapperCatalog.builder()
    .register("Post", authorNameMapper)
    .build();

KeycardConfig config = KeycardConfig.builder()
    .subject(post)
    .mapper(mappers)
    .build();

PolicyDefinition def = new PolicyBuilder(config)
    .allow(read, post, Map.of("authorName", "Alice"))
    .buildDef();

// def.getMeta().getSubjects() includes "Post" even though no allow()/deny()
// call above needed to declare it separately.
Policy policy = Policy.from(def, config);
```

### Custom error handling

```java
try {
    policy.require(delete, article);
} catch (PolicyException e) {
    logger.warn("Access denied: {}", e.getMessage());
    sendError(403, "You do not have permission to delete this article");
}
```

### Cross-language usage

Policies can be serialized with Gson and shared across languages:

```java
// Build policy in Java
Policy policy = new PolicyBuilder(config)
    .allow(create, article)
    .build();

// Serialize
Gson gson = new Gson();
String json = gson.toJson(policy.getDefinition());

// Can be loaded in JavaScript, Rust, etc.
```

## Building and testing

Build with Maven:

```bash
mvn clean package
mvn test
```

See [Vision: Quickstart](./vision-quickstart.md) and
[Vision: A Real Backend](./vision-real-backend.md) for a look at where this API
is headed — not shipped, not compiling against `impl/java`
today.
