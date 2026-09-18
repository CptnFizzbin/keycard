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

## A look ahead

:::info[Vision — not yet implemented]
Everything below this point is a design exploration, not shipped API. It
doesn't compile against the current `impl/js` package — treat it as a
target to design toward, not a reference for what `createAction`,
`createSubject`, `PolicyBuilder`, and `KeycardConfig` do today. See
[`docs/guidelines/keycard-api.md`](https://github.com/CptnFizzbin/keycard/blob/main/docs/guidelines/keycard-api.md)
for the reasoning behind it.
:::

### Quickstart

One builder call, two checks. `config` is optional on `PolicyBuilder` —
skip it and you lose catalog-backed validation and dynamic-subject name
resolution, neither of which a single-file script needs.

```typescript
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

A multi-tenant project tracker: an Express API builds a policy from the
caller's JWT on every request, a React client hydrates the same
`PolicyDefinition` to gate the UI, and one custom Condition operator
handles a rule the built-in set can't express. Every
`Action`/`Subject`/operator is dynamic — `createAction()` with no name —
because the catalog object's own key is already the thing that gets
serialized into a rule tuple. `Actions`/`Subjects`/`Operators`, typed
with the library's own `ActionCatalog`/`SubjectCatalog`/
`OperatorCatalog`, *are* the catalogs, so `config` can hand them
straight to `KeycardConfig` with no separate registration step — the
same move `AppActions`/`AppSubjects`/`AppOperators` make in the
[Java examples](/java/examples#a-real-backend). `createSubject({ from })`
folds the claims mapping in too, so `Subjects.Task.from(task, project)`
replaces a standalone `toTaskSubject()` helper. Keys are PascalCase
(`Actions.Create`, `Subjects.Task`) to match `AppActions`/`AppSubjects`
in Java one-for-one — except the operator catalog, where the object key
*is* the wire name a rule's Condition references (`$withinDays`), so it
can't be recased the way a static field name can.

#### `policy/catalog.ts`

```typescript
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

```typescript
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
the Java examples, so there's no standalone `policy/subjects.ts`
translating between an entity and its claims anymore.

#### `policy/buildPolicy.ts`

```typescript
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

```typescript
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

```typescript
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
