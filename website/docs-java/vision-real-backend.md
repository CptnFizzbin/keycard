---
title: "Vision: A Real Backend"
sidebar_label: "Vision: A Real Backend"
slug: /vision-real-backend
---

# Vision: A Real Backend (Java)

:::info[Vision — not yet implemented]
This page is a design exploration for what version 0.1.0 of KeyCard may look 
like
:::

### `policy/AppActions.java`

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

### `policy/AppSubjects.java`

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

### `policy/AppOperators.java`

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

### `policy/PolicyClaims.java`

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

### `policy/ProjectSubject.java`

```java
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

Subject Types are recommended to be minimal focused subsets or computable 
fields from one or more objects. It is strongly recommended not to use the
full object as the type.

Extending the Subject class is recommended as it provides both the ability to
focus a full object down into a set of Subject Claims, as well as provide a way
to map an object's properties and fields to predictable strings.

### `policy/TaskSubject.java`

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

### `policy/AppPolicyBuilder.java`

```java
@Component
public class AppPolicyBuilder {
    public static final KeycardConfig CONFIG = new KeycardConfig()
        .actions(AppActions.catalog)
        .subjects(AppSubjects.catalog)
        .operators(AppOperators.catalog)
        /* 
        Enables the ablity to perform fail-fast checks while loading a 
        policy during development. When added, the library is able to confirm 
        that all actions, subjects, and operators needed for the policy are 
        registered.
        */
        .emitMeta(Environment.isDevelopment()) // default: true
        /*
        Enables the ability for shared test cases to be added to the policy
        file to allow for confirmations that two or more languages are operating
        with the same permissions. The recommendation is to define test cases 
        on your server and save the resulting policy and tests as a fixture 
        that can be tested on other platforms via Policy.selfTest()
        */
        .emitTests(Environment.isDevelopment()) // default: false

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

### `policy/PermissionService.java`

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

### `web/TaskController.java`

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

### `web/PolicyDefinitionController.java`

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
