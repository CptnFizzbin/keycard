---
title: "Vision: Quickstart"
sidebar_label: "Vision: Quickstart"
slug: /vision-quickstart
---

# Vision: Quickstart (JavaScript)

:::info[Vision — not yet implemented]
This page is a design exploration for what version 0.1.0 of KeyCard may look 
like
:::

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
