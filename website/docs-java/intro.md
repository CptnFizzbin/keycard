---
title: Java
sidebar_label: Introduction
slug: /intro
---

# KeyCard for Java

Cross-language access-control library inspired by CASL.js. Provides
type-safe, composable authorization policies for the JVM.

New to KeyCard's concepts? Read the language-agnostic
**[Guide](/docs/intro)** first — Policy Definition, Rules, and the
condition language are the same across every implementation.

## Features

- **Type-safe** — generics ensure only valid actions and subjects are used
- **Composable** — build complex policies from simple rules using a
  fluent API
- **Flexible conditions** — comparison, pattern matching, and logical
  operators
- **Cross-platform** — `PolicyDefinition`s serialize to JSON for
  cross-language use
- **Zero runtime overhead** — type safety enforced at compile time via
  Java generics

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

## Quick start

```java
import com.cptnfizzbin.keycard.action.Action;
import com.cptnfizzbin.keycard.action.ActionFactory;
import com.cptnfizzbin.keycard.subject.Subject;
import com.cptnfizzbin.keycard.subject.SubjectFactory;
import com.cptnfizzbin.keycard.builder.PolicyBuilder;
import com.cptnfizzbin.keycard.policy.Policy;
import com.cptnfizzbin.keycard.errors.PolicyException;
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
            System.out.println("Can create articles");
        }

        // Check by subject instance
        Article data = new Article(1, 1, "published");
        Subject<Article> ref = article.wrap(data);

        if (policy.can(update, ref)) {
            System.out.println("Can update own article");
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

## Type safety

Java's generic type system enforces compile-time verification:

```java
Action<String> create = ActionFactory.create("Create");
Subject<Article> article = SubjectFactory.create("Article");

policy.can(create, article);        // OK
policy.can("Create", article);      // compiler error: action must be an Action<?>
policy.can(create, "Article");      // compiler error: subject must be a Subject<?>
```

## Beyond the basics

- **`Conditions`** — a type-safe condition builder using method references
  (`Conditions.eq(Article::getOwnerId, 1)`) instead of hand-written
  `Map.of(...)` literals.
- **`SubjectFieldMapper`** — resolve a condition field through an explicit
  getter instead of reflection, for a renamed or computed field.
- **`KeycardConfig`** — one object bundling actions/subjects/operators/field
  mappers, accepted by both `PolicyBuilder` and `Policy`.

See the [API Reference](./api-reference.md) for all three, or
[Examples](./examples.md) for more complete walkthroughs.
