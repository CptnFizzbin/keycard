---
title: "Vision: A Real Backend"
sidebar_label: "Vision: A Real Backend"
slug: /vision-real-backend
---

# Vision: A Real Backend (JavaScript)

:::info[Vision — not yet implemented]
This page is a design exploration for what version 0.1.0 of KeyCard may look 
like
:::
:::

### `policy/catalog.ts`

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

### `policy/claims.ts`

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
the [Java version](/java/vision-real-backend), so there's no standalone
`policy/subjects.ts` translating between an entity and its claims
anymore.

### `policy/buildPolicy.ts`

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

### `http/middleware.ts`

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

### `http/routes/tasks.ts`

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

### `client/policy.tsx`

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
