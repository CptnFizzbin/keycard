KeyCard Full Examples
=====================

> **Status: vision / design exploration, not implemented.** These
> examples sketch what a call site could look like once the ideas in
> [`keycard-api.md`](keycard-api.md) — Claims terminology, narrow Subject
> Claims, dynamic Actions/Subjects/operators, catalog types — are built
> out fully. **None of this compiles against the current `impl/js` or
> `impl/java` packages.** Treat it as a target to design toward, not a
> reference for today's shipped API. See [`GLOSSARY.md`](../../GLOSSARY.md)
> for term definitions.

Each language has two examples:

- **Quickstart** — the whole surface area for a single-file script: one
  `Action`/`Subject` catalog, one `PolicyBuilder` call, a couple of
  checks.
- **A real backend** — a multi-tenant project tracker. A policy is
  rebuilt per request from the caller's JWT (Policy Claims), one custom
  Condition operator handles a rule the built-in set can't express, and
  the server examples ship the same `PolicyDefinition` to a client for
  UI-only gating.

TypeScript
----------

### Quickstart

One builder call, two checks. `config` is optional on `PolicyBuilder` —
skip it and you lose catalog-backed validation and dynamic-subject name
resolution, neither of which a single-file script needs.

```ts
import { createAction, createSubject, PolicyBuilder } from "@cptn-fizzbin/keycard";

// One static catalog of Actions and Subjects for the whole app
const Actions = {
  create: createAction("create"),
  update: createAction("update"),
  delete: createAction("delete"),
} as const;

const Subjects = {
  article: createSubject<{ id: number; ownerId: number; status: string }>("article"),
} as const;

// Policy Claims: the only input this policy needs is who's asking
// config is optional here - PolicyBuilder just needs the Actions/Subjects
// used in .allow()/.deny() calls, not a full app-wide catalog
function policyForUser(user: { id: number }) {
  return new PolicyBuilder()
    .allow(Actions.create, Subjects.article)
    .allow(Actions.update, Subjects.article, { ownerId: user.id })
    .allow(Actions.delete, Subjects.article, { ownerId: user.id })
    .deny(Actions.delete, Subjects.article, { status: "published" }) // last match wins: locked once live
    .build();
}

const policy = policyForUser({ id: 42 });

// Subject Claims: just the fields the rules above actually check
const draft = Subjects.article.wrap({ id: 7, ownerId: 42, status: "draft" });
const published = Subjects.article.wrap({ id: 8, ownerId: 42, status: "published" });

policy.can(Actions.create, Subjects.article); // true  - no conditions to satisfy
policy.can(Actions.delete, draft);            // true  - owns it, still a draft
policy.can(Actions.delete, published);        // false - deny rule matches last

policy.require(Actions.delete, published);
// throws PolicyError: "delete" is not allowed on this "article"
```

### A real backend

Every `Action`/`Subject`/operator is dynamic — `createAction()` with no
name — because the catalog object's own key is already the thing that
gets serialized into a rule tuple. `Actions`/`Subjects`/`Operators`,
typed with the library's own `ActionCatalog`/`SubjectCatalog`/
`OperatorCatalog`, *are* the catalogs, so `config` can hand them straight
to `KeycardConfig` with no separate registration step — the same move
`AppActions`/`AppSubjects`/`AppOperators` make in the Java example below.
`createSubject({ from })` folds the claims mapping in too, so
`Subjects.Task.from(task, project)` replaces a standalone
`toTaskSubject()` helper. Keys are PascalCase (`Actions.Create`,
`Subjects.Task`) to match `AppActions`/`AppSubjects` in Java one-for-one
— except the operator catalog, where the object key *is* the wire name a
rule's Condition references (`$withinDays`), so it can't be recased the
way a static field name can.

#### `policy/catalog.ts`

```ts
// One static catalog per app - shared by every PolicyBuilder/Policy so
// Action/Subject names can never drift out of sync across the codebase.
// Every Action/Subject/operator below is dynamic - createAction() takes
// no name - because the catalog's own key is what actually gets
// serialized into a PolicyDefinition's rule tuples.
import { createAction, createSubject, type ActionCatalog, type KeycardConfig, type OperatorCatalog, type SubjectCatalog } from "@cptn-fizzbin/keycard";
import type { Project, Task } from "../db/models";

export const Actions: ActionCatalog = {
  Create: createAction(),
  Read: createAction(),
  Update: createAction(),
  Delete: createAction(),
  Invite: createAction(),
};

export const Subjects: SubjectCatalog = {
  Project: createSubject<{ id: string; orgId: string; ownerId: string; archived: boolean }>({
    from: (project: Project) => ({
      id: project.id,
      orgId: project.orgId,
      ownerId: project.ownerId,
      archived: project.archivedAt !== null,
    }),
  }),
  Task: createSubject<{ id: string; orgId: string; assigneeId: string | null; createdAt: string }>({
    // composed from two entities - a Task alone doesn't carry orgId, but
    // every Condition that scopes access to an org needs it on the subject
    from: (task: Task, project: Project) => ({
      id: task.id,
      orgId: project.orgId,
      assigneeId: task.assigneeId,
      createdAt: task.createdAt.toISOString(),
    }),
  }),
  // a plugin module can register its own dynamic Subject here too,
  // without touching anything that already shipped
  Comment: createSubject<{ orgId: string; authorId: string }>(),
};

// "$withinDays" compares a subject's ISO date field against a literal
// number of days, resolved at evaluation time
export const Operators: OperatorCatalog = {
  $withinDays: (subjectValue: string, days: number) =>
    Date.now() - new Date(subjectValue).getTime() <= days * 86_400_000,
};

export const config: KeycardConfig = {
  actions: Actions,
  subjects: Subjects,
  operators: Operators,
  // fail-fast catalog/operator validation, and the diagnostic meta a
  // PolicyDefinition carries alongside its rules - both cheap, both worth
  // it in dev, both dead weight in prod once CI has already run them once
  emitMeta: process.env.NODE_ENV !== "production",
};
```

`emitMeta` gates two things together: the eager catalog/operator
validation `PolicyBuilder`/`Policy` do at construction (an unregistered
dynamic Subject, a duplicate catalog key, a custom operator nobody
registered — all fail loudly, immediately), and the diagnostic `meta` a
built `PolicyDefinition` carries alongside its `rules`. Worth paying for
in dev, where the goal is catching a bad rule before it's reviewed. In
prod, a definition that already passed CI doesn't need to re-prove
itself on every boot.

#### `policy/claims.ts`

```ts
// Policy Claims: the actor-side data pulled from the request's JWT that
// decides which rules a policy even generates. Never carries resource data.
import type { JwtPayload } from "./auth";

export interface PolicyClaims {
  userId: string;
  orgId: string;
  role: "owner" | "admin" | "member";
}

export function policyClaimsFromJwt(payload: JwtPayload): PolicyClaims {
  return { userId: payload.sub, orgId: payload.org_id, role: payload.role };
}
```

Subject Claims mapping lives inline now, via `createSubject({ from: ... })`
— the same `TaskSubject.from(...)`/`ProjectSubject.from(...)` pattern as
the Java example, so there's no standalone `policy/subjects.ts`
translating between an entity and its claims anymore.

#### `policy/buildPolicy.ts`

```ts
import { PolicyBuilder } from "@cptn-fizzbin/keycard";
import { Actions, Subjects, config } from "./catalog";
import type { PolicyClaims } from "./claims";

export function buildPolicy(claims: PolicyClaims) {
  const builder = new PolicyBuilder(config)
    .allow(Actions.Read, Subjects.Project, { orgId: claims.orgId })
    .allow(Actions.Read, Subjects.Task, { orgId: claims.orgId });

  if (claims.role === "owner" || claims.role === "admin") {
    builder
      .allow(Actions.Create, Subjects.Project, { orgId: claims.orgId })
      .allow(Actions.Update, Subjects.Project, { orgId: claims.orgId })
      .allow(Actions.Invite, Subjects.Project, { orgId: claims.orgId })
      .allow(Actions.Delete, Subjects.Project, {
        $and: [{ orgId: claims.orgId }, { archived: true }],
      });
  } else {
    // members can only touch tasks assigned to them, and only recent ones
    builder.allow(Actions.Update, Subjects.Task, {
      $and: [
        { orgId: claims.orgId },
        { assigneeId: claims.userId },
        { createdAt: { $withinDays: 30 } },
      ],
    });
  }

  return builder.build();
}
```

#### `http/middleware.ts`

```ts
import type { Request, Response, NextFunction } from "express";
import { PolicyError, type Action, type Subject } from "@cptn-fizzbin/keycard";
import { verifyJwt } from "./auth";
import { policyClaimsFromJwt } from "../policy/claims";
import { buildPolicy } from "../policy/buildPolicy";

export function attachPolicy(req: Request, res: Response, next: NextFunction) {
  const claims = policyClaimsFromJwt(verifyJwt(req.headers.authorization));
  req.claims = claims;
  req.policy = buildPolicy(claims); // rebuilt per request - claims differ request to request
  next();
}

export function requirePermission(
  action: Action,
  loadSubject: (req: Request) => Subject<any> | Promise<Subject<any>>,
) {
  return async (req: Request, res: Response, next: NextFunction) => {
    try {
      req.policy.require(action, await loadSubject(req));
      next();
    } catch (err) {
      if (err instanceof PolicyError) {
        return res.status(403).json({ error: err.message });
      }
      next(err);
    }
  };
}
```

#### `http/routes/tasks.ts`

```ts
import { Router } from "express";
import { Actions, Subjects } from "../../policy/catalog";
import { attachPolicy, requirePermission } from "../middleware";
import { db } from "../../db";

export const tasks = Router();
tasks.use(attachPolicy);

tasks.patch(
  "/tasks/:id",
  requirePermission(Actions.Update, async (req) => {
    const task = await db.tasks.findOrThrow(req.params.id);
    const project = await db.projects.findOrThrow(task.projectId);
    return Subjects.Task.from(task, project);
  }),
  async (req, res) => {
    const updated = await db.tasks.update(req.params.id, req.body);
    res.json(updated);
  },
);
```

#### `client/policy.tsx`

```tsx
// Cross-language payoff: ship the same PolicyDefinition the server built
// to the browser, so the UI can gate controls without a round trip - the
// server still re-checks on every write, this is UX only.
import { Policy } from "@cptn-fizzbin/keycard";
import { config, Actions, Subjects } from "../policy/catalog";

export async function loadClientPolicy() {
  const def = await fetch("/api/me/policy").then((r) => r.json());
  return new Policy(def, config);
}

function SaveButton({ policy, task }: { policy: Policy; task: ReturnType<typeof Subjects.Task.from> }) {
  const allowed = policy.can(Actions.Update, task);
  return allowed
    ? <button onClick={saveTask}>Save</button>
    : <button disabled title="You don't have permission to edit this task">Save</button>;
}
```

The client copy of the policy is UX only — it hides a button before a
round trip. Middleware on the server is what actually enforces anything;
nothing on the client is trusted.

Java
----

### Quickstart

Same shape as the TypeScript version. `Action` is constructed directly —
`new Action(name)` — no factory class, and no generic; it's always just
a name. `ArticleSubject` folds its claims mapping into the `Subject`
itself, exactly the pattern the production example uses for
`TaskSubject`/`ProjectSubject`: a nested `Claims` type and a `from(...)`
that builds and wraps in one call. `AppSubjects` carries a small
`catalog` too — just enough to show `catalog.set(subject)`: when a
`Subject` already carries its own name (`new ArticleSubject("article")`,
not dynamic), `set()` reads it straight off the object instead of taking
a separate name argument, the same way `AppActions`/`AppSubjects` in the
production example take one when the Action/Subject is dynamic. A typed
`Condition<T>` builder stands in for TS's object-literal conditions:
Java has no structural typing, so a raw `Map<String, Object>` would let
any field-name typo compile — `Condition.field(...)` takes a getter
reference instead of a string (it's part of the library itself, not
application code, so it isn't shown as a file here). `config` is
optional on `PolicyBuilder` too — this constructor drops it.

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

Every `Action` and `Subject` here is dynamic — unnamed at construction,
named only by the catalog key it's registered under — and
`AppActions`/`AppSubjects`/`AppOperators` are their own classes, each
building its own `catalog` field inline: `catalog.set(name, new
Action())` registers and returns in the same expression, so there's no
separate method re-listing every name a second time, and a name
collision in one registry can never shadow an entry in the other. A bare
operator lambda has no name of its own to read, so `AppOperators` always
needs that two-arg form — unlike a manually-named `Subject` (the simple
example's `ArticleSubject("article")`), where `catalog.set(subject)` can
read the name straight off the object. `ProjectSubject`/`TaskSubject`
fold the claims mapping into the `Subject` itself, so a call site does
one `AppSubjects.Task.from(task, project)` instead of a
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
instead of a compile error — see "Subject shape: a narrow projection,
not the entity" in [`keycard-api.md`](keycard-api.md) for the full case
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
[`SPEC.md`](../../SPEC.md).

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
// TypeScript client above hydrates - one source of truth for what a user
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
