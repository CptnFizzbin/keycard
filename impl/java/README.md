# KeyCard - Java

Cross-language access control library inspired by CASL.js. Provides type-safe,
composable authorization policies.

## Features

- **Type-safe**: Generics ensure only valid actions and subjects are used
- **Composable**: Build complex policies from simple rules using fluent API
- **Flexible conditions**: Support for comparison, pattern matching, and logical
  operators
- **Cross-platform**: PolicyDefinitions serialize to JSON for cross-language use
- **Zero runtime overhead**: Type safety enforced at compile-time via Java
  generics

## Installation

Add to your `pom.xml`:

```xml

<dependency>
    <groupId>com.cptnfizzbin</groupId>
    <artifactId>keycard</artifactId>
    <version>0.0.4</version>
</dependency>
```

Or with Gradle:

```gradle
implementation 'com.cptnfizzbin:keycard:0.0.4'
```

## Quick Start

```java
import com.cptnfizzbin.keycard.*;
import java.util.Map;

class Article {
    public final int id;
    public final int ownerId;
    public final String status;

    public Article(int id, int ownerId, String status) {
        this.id = id;
        this.ownerId = ownerId;
        this.status = status;
    }
}

public class Main {
    public static void main(String[] args) {
        // Define your actions
        Action<String> create = ActionFactory.create("Create");
        Action<String> update = ActionFactory.create("Update");
        Action<String> delete = ActionFactory.create("Delete");

        // Define your subjects
        Subject<Article> article = SubjectFactory.create("Article");

        // Build a policy
        Policy policy = new PolicyBuilder()
            .allow(create, article)
            .allow(update, article, Map.of("ownerId", 1))
            .deny(delete, article, Map.of("status", Map.of("$not", "archived")))
            .build();

        // Check by subject type (no instance)
        if (policy.can(create, article)) {
            System.out.println("✓ Can create articles");
        }

        // Check by subject instance
        Article data = new Article(1, 1, "published");
        Subject<Article> ref = article.wrap(data);

        if (policy.can(update, ref)) {
            System.out.println("✓ Can update own article");
        }

        // Require permission (throws if denied)
        try {
            policy.require(delete, ref);
        } catch (PolicyException e) {
            System.out.println("Access denied: " + e.getMessage());
        }
    }
}
```

## Type Safety

Java's generic type system ensures compile-time verification:

```java
Action<String> create = ActionFactory.create("Create");
Subject<Article> article = SubjectFactory.create("Article");

policy.can(create, article);        // ✓ OK
policy.can("Create", article);      // ✗ Compiler error - action must be an Action<?>
policy.can(create, "Article");      // ✗ Compiler error - subject must be a Subject<?>
```

## Condition Operators

- `$eq` - Equality
- `$gt` - Greater than
- `$gte` - Greater than or equal
- `$lt` - Less than
- `$lte` - Less than or equal
- `$in` - Value in collection
- `$has` - Collection contains value
- `$substr` - Substring pattern match (a small, non-regex pattern language - see
  SPEC_V1-0-0.md §7.4.6)
- `$or` - Logical OR
- `$and` - Logical AND
- `$not` - Logical NOT
- `$field` - Explicit field access, for a field whose name itself starts with "$
  "
- Field conditions - Check nested properties

## API

### ActionFactory

Create type-safe actions:

```java
Action<String> create = ActionFactory.create("Create");
```

### SubjectFactory

Create type-safe subjects:

```java
Subject<Article> article = SubjectFactory.create("Article");
```

### Subject<T>

A single type covering both a bare subject (no instance) and a wrapped
instance - `getInstance()` is empty until `.wrap()` is called.

- `getName()` - Get subject name
- `getInstance()` - Get the wrapped object, if any, as an `Optional<T>`
- `wrap(T obj)` - Returns a new `Subject<T>` of the same name, with its instance
  set

### PolicyBuilder

Build policies with fluent API:

- `allow(action, subject)` - Allow action
- `allow(action, subject, conditions)` - Allow with conditions
- `deny(action, subject)` - Deny action
- `deny(action, subject, conditions)` - Deny with conditions
- `build()` - Create Policy
- `buildDefinition()` - Create PolicyDefinition

### Policy

Check permissions:

- `can(action, subject)` - Check if action is allowed
- `cannot(action, subject)` - Check negation
- `require(action, subject)` - Require permission (throws on denial)
- `getDefinition()` - Get underlying definition

### ConditionResolver

Evaluates conditions:

- `evaluate(subject, condition)` - Evaluate a condition

### PolicyDefinition

Serializable policy, per SPEC_V1-0.md §3:

- `getVersion()` - Get the SemVer spec version, e.g. `"1.0"`
- `getMeta()` - Get the optional `meta` object (wildcard tokens, catalogs,
  application data)
- `getRules()` - Get the ordered list of
  `[effect, action, subject, conditions?]` rules

### PolicyException

Thrown when permission check fails with `require()`.

## Examples

### Schema-Only Check

```java
// Allow creating any article (no conditions needed)
policy.can(Actions.Create, Subjects.Article);
```

### Condition-Based Check

```java
// Check if user can update THIS article (with conditions)
Article data = new Article(1, userId, "published");
Subject<Article> ref = article.wrap(data);
policy.can(Actions.Update, ref);
```

### Multiple Conditions

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

### Custom Error Handling

```java
try {
    policy.require(delete, article);
} catch (PolicyException e) {
    logger.warn("Access denied: {}", e.getMessage());
    sendError(403, "You do not have permission to delete this article");
}
```

## Cross-Language Usage

Policies can be serialized with Gson and shared across languages:

```java
// Build policy in Java
Policy policy = new PolicyBuilder()
    .allow(create, article)
    .build();

// Serialize
Gson gson = new Gson();
String json = gson.toJson(policy.getDefinition());

// Can be loaded in JavaScript, Rust, etc.
```

## Building and Testing

Build with Maven:

```bash
mvn clean package
mvn test
```

Run the example:

```bash
mvn exec:java -Dexec.mainClass="com.cptnfizzbin.keycard.Example"
```
