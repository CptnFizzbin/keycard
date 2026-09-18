---
title: "Vision: Quickstart"
sidebar_label: "Vision: Quickstart"
slug: /vision-quickstart
---

# Vision: Quickstart (Java)

:::info[Vision — not yet implemented]
This page is a design exploration for what version 0.1.0 of KeyCard may look 
like
:::

### `Main.java`

```java
Action create = new Action("create");
Action update = new Action("update");
Action delete = new Action("delete");

// Policy Claims: the only input this policy needs is who's asking
// KeycardConfig is optional here - PolicyBuilder just needs the Actions/
// Subjects used in .allow()/.deny() calls, not a full app-wide catalog
Policy policyForUser(User user) {
    return new PolicyBuilder()
        .allow(create, AppSubjects.Article)
        .allow(update, AppSubjects.Article, Condition.where(
            Condition.or(
                Condition.field(ArticleSubject.Claims::ownerId).eq(user.getId()),
                Condition.field(ArticleSubject.Claims::editorIds).has(user.getId())
            )
        ))
        .allow(delete, AppSubjects.Article, Condition.where(
            Condition.field(ArticleSubject.Claims::ownerId).eq(user.getId())
        ))
        .deny(delete, AppSubjects.Article, Condition.where(
            Condition.field(ArticleSubject.Claims::status).eq("published") // last match wins: locked once live
        ))
        .build();
}

Policy policy = policyForUser(currentUser);

// Subject Claims: a composable projection, scoped to just what Conditions need -
// from(...) builds the Claims and wraps in one call
ArticleSubject draft = AppSubjects.Article.from(draftArticle);
ArticleSubject published = AppSubjects.Article.from(publishedArticle);

policy.can(create, AppSubjects.Article); // true  - no conditions to satisfy
policy.can(delete, draft);               // true  - owns it, still a draft
policy.can(delete, published);           // false - deny rule matches last

policy.require(delete, published);
// throws PolicyException: "delete" is not allowed on this "article"
```

### `AppSubjects.java`

```java
class AppSubjects {
    static final SubjectCatalog catalog = new SubjectCatalog();

    // ArticleSubject already carries its own name - set(subject) reads it
    // off the object itself, no separate name argument needed
    static ArticleSubject Article = catalog.set(new ArticleSubject("article"));
}
```

### `ArticleSubject.java`

```java
class ArticleSubject extends Subject<ArticleSubject.Claims, ArticleSubject> {
    ArticleSubject(String name) { super(name); }
    private ArticleSubject(String id, Claims instance) { super(id, instance); }

    // wrap() delegates here - this is the only place that knows how to
    // rebuild an ArticleSubject, so wrap() can return ArticleSubject with
    // no cast anywhere
    @Override
    protected ArticleSubject copy(Claims instance) {
        return new ArticleSubject(id, instance);
    }

    ArticleSubject from(Article a) {
        return wrap(new Claims()
            .ownerId(a.getOwnerId())
            .editorIds(a.getEditorIds())
            .status(a.getStatus()));
    }

    @Data
    @Accessors(fluent = true, chain = true)
    static class Claims {
        private long ownerId;
        private List<Long> editorIds;
        private String status;
    }
}
```

`Subject<T>` is `final` in today's real implementation, so subclassing
it like this is aspirational, and it needs one more change to avoid a
cast in `from()`: a second, self-bounded type parameter,
`Subject<T, TSelf extends Subject<T, TSelf>>`, plus a
`protected abstract TSelf copy(T instance)` hook that `wrap()` delegates
to instead of constructing a plain `Subject<T>` itself. Each subclass's
`copy()` is two lines — call its own private constructor — and in
exchange `wrap()`/`from()` return the real subtype with zero casts
anywhere, in the library or in application code: unlike the self-type
trick used for e.g. `Enum<E extends Enum<E>>`, nothing here ever casts
`this` to `TSelf` and hopes; each subclass's `copy()` just calls its own
constructor directly, which really does return that exact type.
