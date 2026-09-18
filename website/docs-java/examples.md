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

`Conditions` builds the same `Map<String, Object>` shape from method
references, so a typo in a field name is caught by the compiler instead of
failing silently at evaluation time:

```java
new PolicyBuilder(config)
    .allow(update, article, Conditions.and(
        Conditions.eq(Article::getOwnerId, userId),
        Conditions.ne(Article::getId, 1)
    ))
    .build();
```

### Field mappers for renamed or computed fields

A `SubjectFieldMapper` resolves a condition field through an explicit
getter instead of reflection — handy when a policy-facing field name
doesn't match the instance's own shape, like flattening a nested
`post.getAuthor().getName()` into a single top-level `authorName` field
(recall conditions can only narrow one field deep — see
[Condition Operators](/docs/condition-operators#v1-supports-only-top-level-field-access)):

```java
SubjectFieldMapper<Post> authorNameMapper = SubjectFieldMapper.<Post>builder()
    .field("authorName", (post) -> post.getAuthor().getName())
    .build();

Subject<Post> post = SubjectFactory.create("Post", authorNameMapper);
Action<String> read = ActionFactory.create("Read");

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

A field the mapper doesn't define still falls back to ordinary reflection,
and a mapped field's own value can still use any non-field operator
(`$substr`, `$in`, ...) — only narrowing is restricted to one level, not
operator use.

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

## A look ahead

:::info[Vision — not yet implemented]
Everything below this point is a design exploration, not shipped API. It
doesn't compile against the current `impl/java` package — treat it as a
target to design toward, not a reference for what `Action`, `Subject`,
`PolicyBuilder`, and `KeycardConfig` do today. See
[`docs/guidelines/keycard-api.md`](https://github.com/CptnFizzbin/keycard/blob/main/docs/guidelines/keycard-api.md)
for the reasoning behind it.
:::

### Quickstart

Same shape as everything above, once `Action` drops its factory class and
generic — `new Action(name)`, always just a name — and `Subject` gains a
composable `from(...)` that folds its claims mapping in, so
`ArticleSubject` builds and wraps in one call instead of a separate
`Conditions`-and-`wrap()` step. `AppSubjects` carries a small `catalog`
too — just enough to show `catalog.set(subject)`: when a `Subject`
already carries its own name (`new ArticleSubject("article")`, not
dynamic), `set()` reads it straight off the object instead of taking a
separate name argument, the same way `AppActions`/`AppSubjects` do below
when the Action/Subject is dynamic. A typed `Condition<T>` builder stands
in for today's `Conditions` helpers, but built on getter references
instead of static method calls — `Condition.field(...)` takes a getter
reference instead of a string, so it's part of the library itself, not
application code, and isn't shown as a file here. `config` is optional
on `PolicyBuilder` too — this constructor drops it.

#### `Main.java`

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

#### `AppSubjects.java`

```java
class AppSubjects {
    static final SubjectCatalog catalog = new SubjectCatalog();

    // ArticleSubject already carries its own name - set(subject) reads it
    // off the object itself, no separate name argument needed
    static ArticleSubject Article = catalog.set(new ArticleSubject("article"));
}
```

#### `ArticleSubject.java`

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

### A real backend

A multi-tenant project tracker, as a Spring service: `PolicyClaims`
comes off the Spring Security `Jwt` principal, a `PermissionService`
wraps `require()` into a 403, and one endpoint ships the raw
`PolicyDefinition` — the exact wire format the JavaScript client in the
[JavaScript examples](/js/examples#a-real-backend) hydrates. Every
`Action` and `Subject` here is dynamic — unnamed at construction, named
only by the catalog key it's registered under — and
`AppActions`/`AppSubjects`/`AppOperators` are their own classes, each
building its own `catalog` field inline: `catalog.set(name, new
Action())` registers and returns in the same expression, so there's no
separate method re-listing every name a second time, and a name
collision in one registry can never shadow an entry in the other. A bare
operator lambda has no name of its own to read, so `AppOperators` always
needs that two-arg form — unlike a manually-named `Subject` (the
quickstart's `ArticleSubject("article")`), where `catalog.set(subject)`
can read the name straight off the object. `ProjectSubject`/
`TaskSubject` fold the claims mapping into the `Subject` itself, so a
call site does one `AppSubjects.Task.from(task, project)` instead of a
claims-then-`wrap()` two-step.

#### `policy/AppActions.java`

```java
public class AppActions {
    // dynamic: no name baked into the Action itself - catalog.set(...)'s
    // key is what actually gets serialized into a PolicyDefinition's rule
    // tuples, and it registers the Action the same moment it names it
    public static final ActionCatalog catalog = new ActionCatalog();

    public static Action Create = catalog.set("create", new Action());
    public static Action Read = catalog.set("read", new Action());
    public static Action Update = catalog.set("update", new Action());
    public static Action Delete = catalog.set("delete", new Action());
    public static Action Invite = catalog.set("invite", new Action());
}
```

#### `policy/AppSubjects.java`

```java
public class AppSubjects {
    public static final SubjectCatalog catalog = new SubjectCatalog();

    public static ProjectSubject Project = catalog.set("project", new ProjectSubject());
    public static TaskSubject Task = catalog.set("task", new TaskSubject());
    // a plugin module can register its own dynamic Subject here too,
    // without touching anything that already shipped
    public static Subject<CommentSubjectClaims> Comment = catalog.set("comment", new Subject<>());
}
```

#### `policy/AppOperators.java`

```java
public class AppOperators {
    public static final OperatorCatalog catalog = new OperatorCatalog();

    // "$withinDays" compares a subject's Instant field against a literal
    // number of days, resolved at evaluation time. A bare lambda has no
    // name of its own, so - unlike AppSubjects.Article above - it always
    // needs the two-arg set(name, operator).
    public static ConditionOperator WithinDays = catalog.set("$withinDays", (subjectValue, days) ->
        Duration.between((Instant) subjectValue, Instant.now()).toDays() <= (long) days);
}
```

#### `policy/PolicyClaims.java`

```java
// Policy Claims: actor-side data pulled from the request's JWT that decides
// which rules a policy even generates. Never carries resource data.
public record PolicyClaims(String userId, String orgId, Role role) {
    public static PolicyClaims fromJwt(Jwt jwt) {
        return new PolicyClaims(
            jwt.getSubject(),
            jwt.getClaimAsString("org_id"),
            Role.valueOf(jwt.getClaimAsString("role"))
        );
    }
}
```

#### `policy/ProjectSubject.java`

```java
// Subject Claims: narrow, composable projections - never the raw entity.
// The claims mapping lives on the Subject itself: one from(...) call builds
// the Claims and wraps them, so a caller never sees the two-step.
public class ProjectSubject extends Subject<ProjectSubject.Claims, ProjectSubject> {
    public ProjectSubject() { super(); }
    private ProjectSubject(String id, Claims instance) { super(id, instance); }

    @Override
    protected ProjectSubject copy(Claims instance) {
        return new ProjectSubject(id, instance);
    }

    public ProjectSubject from(Project p) {
        return wrap(new Claims()
            .orgId(p.getOrgId())
            .ownerId(p.getOwnerId())
            .archived(p.getArchivedAt() != null));
    }

    @Data
    @Accessors(fluent = true, chain = true)
    public static class Claims {
        private String orgId;
        private String ownerId;
        private boolean archived;
    }
}
```

**Never the raw entity.** Handing `Subject.wrap(...)` a JPA/ORM entity
directly, instead of a narrow claims projection like these, risks
triggering lazy-loaded relation getters and circular references, and
turns a DB column rename into a Condition that silently stops matching
instead of a compile error — see
[`docs/guidelines/keycard-api.md`](https://github.com/CptnFizzbin/keycard/blob/main/docs/guidelines/keycard-api.md)'s
"Subject shape: a narrow projection, not the entity" for the full case
against it.

#### `policy/TaskSubject.java`

```java
public class TaskSubject extends Subject<TaskSubject.Claims, TaskSubject> {
    public TaskSubject() { super(); }
    private TaskSubject(String id, Claims instance) { super(id, instance); }

    @Override
    protected TaskSubject copy(Claims instance) {
        return new TaskSubject(id, instance);
    }

    // composed from two entities - a Task alone doesn't carry orgId, but every
    // Condition that scopes access to an org needs it on the subject
    public TaskSubject from(Task t, Project p) {
        return wrap(new Claims()
            .orgId(p.getOrgId())
            .assigneeId(t.getAssigneeId())
            .createdAt(t.getCreatedAt()));
    }

    @Data
    @Accessors(fluent = true, chain = true)
    public static class Claims {
        private String orgId;
        private String assigneeId;
        private Instant createdAt;
    }
}
```

`Condition<T>` (with its `where`/`and`/`or`/`field` helpers, used below)
is part of the library itself, not application code, so it isn't shown
as a file here. `field()` takes a getter reference, not a string —
renaming `TaskSubject.Claims.assigneeId()` is then a compile error at
every call site, not a policy rule that silently stops matching. The
property name comes off the getter's `SerializedLambda` once, at first
use, and is cached — not resolved per check. The wire format is
untouched: it still serializes to the same
`[effect, action, subject, conditions]` tuples from
[`SPEC.md`](https://github.com/CptnFizzbin/keycard/blob/main/SPEC.md).

#### `policy/AppPolicyBuilder.java`

```java
@Component
public class AppPolicyBuilder {
    public static final KeycardConfig CONFIG = new KeycardConfig()
        .actions(AppActions.catalog)
        .subjects(AppSubjects.catalog)
        .operators(AppOperators.catalog)
        // fail-fast catalog/operator validation, and the diagnostic meta a
        // PolicyDefinition carries alongside its rules - both cheap, both
        // worth it in dev, both dead weight in prod once CI has already run
        // them once
        .emitMeta(!"production".equals(System.getenv("APP_ENV")));

    public Policy buildFor(PolicyClaims claims) {
        PolicyBuilder builder = new PolicyBuilder(CONFIG)
            .allow(AppActions.Read, AppSubjects.Project, Condition.field(ProjectSubject.Claims::orgId).eq(claims.orgId()))
            .allow(AppActions.Read, AppSubjects.Task, Condition.field(TaskSubject.Claims::orgId).eq(claims.orgId()));

        if (claims.role() == Role.OWNER || claims.role() == Role.ADMIN) {
            builder
                .allow(AppActions.Create, AppSubjects.Project, Condition.field(ProjectSubject.Claims::orgId).eq(claims.orgId()))
                .allow(AppActions.Update, AppSubjects.Project, Condition.field(ProjectSubject.Claims::orgId).eq(claims.orgId()))
                .allow(AppActions.Invite, AppSubjects.Project, Condition.field(ProjectSubject.Claims::orgId).eq(claims.orgId()))
                .allow(AppActions.Delete, AppSubjects.Project, Condition.where(
                    Condition.and(
                        Condition.field(ProjectSubject.Claims::orgId).eq(claims.orgId()),
                        Condition.field(ProjectSubject.Claims::archived).eq(true)
                    )
                ));
        } else {
            // members can only touch tasks assigned to them, and only recent ones
            builder.allow(AppActions.Update, AppSubjects.Task, Condition.where(
                Condition.and(
                    Condition.field(TaskSubject.Claims::orgId).eq(claims.orgId()),
                    Condition.field(TaskSubject.Claims::assigneeId).eq(claims.userId()),
                    Condition.field(TaskSubject.Claims::createdAt).op("$withinDays", 30)
                )
            ));
        }

        return builder.build();
    }
}
```

`emitMeta` gates two things together: the eager catalog/operator
validation `PolicyBuilder`/`Policy` do at construction (an unregistered
dynamic Subject, a duplicate catalog key, a custom operator nobody
registered — all fail loudly, immediately), and the diagnostic `meta` a
built `PolicyDefinition` carries alongside its `rules`. Worth paying for
in dev, where the goal is catching a bad rule before it's reviewed. In
prod, a definition that already passed CI doesn't need to re-prove
itself on every boot.

#### `policy/PermissionService.java`

```java
@Service
@RequiredArgsConstructor
public class PermissionService {
    private final AppPolicyBuilder policyBuilder;

    public void require(PolicyClaims claims, Action action, Subject<?> subject) {
        try {
            policyBuilder.buildFor(claims).require(action, subject);
        } catch (PolicyException e) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, e.getMessage());
        }
    }
}
```

#### `web/TaskController.java`

```java
@RestController
@RequiredArgsConstructor
public class TaskController {
    private final PermissionService permissions;
    private final TaskRepository tasks;
    private final ProjectRepository projects;

    @PatchMapping("/tasks/{id}")
    public Task update(@PathVariable String id, @AuthenticationPrincipal Jwt jwt, @RequestBody TaskPatch patch) {
        PolicyClaims claims = PolicyClaims.fromJwt(jwt);
        Task task = tasks.findOrThrow(id);
        Project project = projects.findOrThrow(task.getProjectId());

        permissions.require(claims, AppActions.Update, AppSubjects.Task.from(task, project));

        return tasks.save(task.apply(patch));
    }
}
```

#### `web/PolicyDefinitionController.java`

```java
// Cross-language payoff: the same PolicyDefinition wire format the
// JavaScript client hydrates - one source of truth for what a user
// can do, built once, server-side, per request.
@RestController
@RequiredArgsConstructor
public class PolicyDefinitionController {
    private final AppPolicyBuilder policyBuilder;
    private final Gson gson = new Gson();

    @GetMapping(value = "/api/me/policy", produces = "application/json")
    public String myPolicy(@AuthenticationPrincipal Jwt jwt) {
        PolicyClaims claims = PolicyClaims.fromJwt(jwt);
        return gson.toJson(policyBuilder.buildFor(claims).getDefinition());
    }
}
```

`AppPolicyBuilder` rebuilds the policy on every call — Policy Claims
come from the request's own JWT, so nothing long-lived can safely cache
across users. Only the static `AppPolicyBuilder.CONFIG` is shared.
