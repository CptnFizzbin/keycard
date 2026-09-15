---
title: Examples
sidebar_label: Examples
slug: /examples
---

# Examples (Java)

### Schema-only check

```java
// Allow creating any article (no conditions needed)
policy.can(Actions.Create, Subjects.Article);
```

### Condition-based check

```java
// Check if the user can update THIS article (with conditions)
Article data = new Article(1, userId, "published");
Subject<Article> ref = article.wrap(data);
policy.can(Actions.Update, ref);
```

### Multiple conditions

```java
new PolicyBuilder()
    .allow(update, article, Map.of(
        "$and", List.of(
            Map.of("ownerId", userId),
            Map.of("status", Map.of("$not", "archived"))
        )
    ))
    .build();
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
Policy policy = new PolicyBuilder()
    .allow(create, article)
    .build();

// Serialize
Gson gson = new Gson();
String json = gson.toJson(policy.getDefinition());

// Can be loaded in TypeScript, Rust, etc.
```

## Building and testing

Build with Maven:

```bash
mvn clean package
mvn test
```

Run the example:

```bash
mvn exec:java -Dexec.mainClass="com.cptnfizzbin.keycard.Example"
```
