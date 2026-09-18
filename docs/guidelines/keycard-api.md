KeyCard API Guidelines
======================

> Status: design guidelines / proposal. Some of this describes the
> currently shipped `Subject`/`Action`/`Policy` API (see
> [`../../impl/js/README.md`](../../impl/js/README.md)); the catalog and
> dynamic-naming sections describe an extension to that API that is not
> yet implemented. Where the two differ, that's called out explicitly.

See [`GLOSSARY.md`](../../GLOSSARY.md) and [`SPEC.md`](../../SPEC.md) for
term definitions and the base spec these guidelines build on. See the
"vision" section at the end of each language's Examples page —
[`website/docs-js/examples.md`](../../website/docs-js/examples.md) and
[`website/docs-java/examples.md`](../../website/docs-java/examples.md)
— for complete quickstart and production-shape examples that put these
guidelines together end to end.

Subjects and Actions: one static catalog per app
-------------------------------------------------

Keep a single static map of Subjects, and a single static map of Actions,
per application:

```typescript
const Subjects = {
  Article: createSubject<{ id: number; owner_id: number; status: string }>("Article"),
} as const;

const Actions = {
  Create: createAction("Create"),
  Update: createAction("Update"),
} as const;

type AppSubjects = InferSubjects<typeof Subjects>;
type AppActions = InferActions<typeof Actions>;
```

One map per concept, not one `createSubject`/`createAction` call scattered
per file — it's the one place a reader (or a future refactor) can see
every Subject/Action the app has, and it's the natural place to register
with a `PolicyBuilder`/`Policy` config (see below).

Subject shape: a narrow projection, not the entity
---------------------------------------------------

`createSubject<TData>`'s `TData` should be a small, deliberately chosen
subset of fields — the ones Conditions actually need to compare against —
not the ORM/domain entity class itself. This projection is what
`GLOSSARY.md` calls **Subject Claims**: it should be composable (its own
named type, not inlined at every call site, so it can be built up or
reused across `toSubject()`/`for()`-style mappings) and scoped to only the
fields the policy's Conditions actually need.

```typescript
// Good: narrow, structural, no entity import needed
Subjects.Article = createSubject<{ id: number; owner_id: number; status: string }>("Article");

// Avoid: the raw entity
Subjects.Article = createSubject<ArticleEntity>("Article");
```

The same idea in Java — a dedicated, composable `*SubjectClaims` type,
scoped to just the fields `Article`'s Conditions need:

```java
@Data
@Accessors(fluent = true, chain = true)
public class ArticleSubjectClaims {
    public static final Subject<ArticleSubjectClaims> SUBJECT = new Subject<>("article");

    private long ownerId;
    private String status;

    public static Subject<ArticleSubjectClaims> of(Article article) {
        return SUBJECT.wrap(
            new ArticleSubjectClaims()
                .ownerId(article.getOwnerId())
                .status(article.getStatus())
        );
    }
}
```

Why this matters, concretely:

- **Cyclical imports.** TypeScript interfaces are structural — a domain
  entity class satisfies a narrow policy-facing shape without needing to
  import or implement it. If `TData` is the entity class itself, the
  entity module and the policy module can end up depending on each other.
- **Condition evaluation walks the object generically.** `ConditionResolver`
  does plain `subject[key]` property access, recursively, for every field
  a rule's Conditions mention (`impl/js/src/conditions/conditionResolver.ts`).
  Handing it a full ORM instance risks triggering lazy-loaded relation
  getters, circular references, or exposing fields nobody meant to make
  policy-visible.
- **Field names become a public contract.** Whatever key names appear in
  the mapped object are the same names a Condition's field-path matching
  keys off of. Renaming a DB column doesn't fail loudly — a Condition
  referencing the old name just stops matching, silently changing what a
  rule allows or denies. Treat these field names with the same care as a
  public API, and keep the mapping (`toSubject()` — see below) as the one
  place that translates between "however the entity happens to be shaped
  today" and "the stable name a policy rule depends on."

`toSubject()`: a pure, per-entity mapping method
--------------------------------------------------

Give each entity a `toSubject()` method that performs the mapping into
its `Subjects.X` shape:

```typescript
class Article {
  id: number;
  ownerId: number;
  status: string;

  toSubject() {
    return Subjects.Article.wrap({
      id: this.id,
      owner_id: this.ownerId,
      status: this.status,
    });
  }
}
```

- It should be a **pure mapping** — field selection and renaming only, no
  I/O, no side effects, and (absent an explicit actor-aware design — see
  "Open questions" below) no dependency on who's asking.
- Call it **right before** the permission check, not once and cached.
  `.wrap()` just closes over whatever object you hand it; it does not
  re-fetch or stay live. If the entity's relevant fields (e.g. `owner_id`
  after a transfer) change between when `toSubject()` was called and when
  `.can()` runs, the check sees stale data.
- Reference `Subjects.X` from the shared catalog inside `toSubject()`
  rather than re-typing the subject's name as a string literal — that way
  a rename of the catalog entry can't silently diverge from what
  `toSubject()` produces.

Type safety of `.can()` / `.cannot()` / `.require()`
------------------------------------------------------

`Policy.can(action, subject)` should only accept a valid `Action`/`Subject`
pair for the app's own `TActions`/`TSubjects` — that's what `InferActions`/
`InferSubjects` plus the `Policy<TActions, TSubjects>` generic parameters
give you today, and duck-typed/loosely-typed language ports need an
equivalent runtime check at the same boundary.

One thing this does **not** give you, in either the shipped TypeScript or
Java implementation: a compile-time distinction between a bare
type-only Subject (`Subjects.Article`, no `.wrap()`) and one carrying
instance data (`Subjects.Article.wrap(data)`). Both implementations
deliberately collapse these into one type (`instance?: TData` in TS,
`Optional<T> instance` in Java) — see `impl/js/src/subject/subject.ts`
and `impl/java/.../subject/Subject.java`. The distinction is enforced at
runtime instead: a conditional rule can never match a subject with no
instance (`subject.instance === undefined`), so it's silently skipped
during evaluation (`impl/js/src/policy/policy.ts`, the
`ruleConditions`/`subject.instance === undefined` branch). Passing a bare
subject where the only matching rule is conditional type-checks fine and
just falls through to default-deny — indistinguishable from a correctly
denied check. Know this failure mode exists; there's no compiler warning
for it today.

Dynamic (unnamed) Subjects/Actions and the Catalog
----------------------------------------------------

> Implemented in both `impl/js` and `impl/java`.

`createSubject`/`createAction` should also be callable with **no** name:

```typescript
const Subjects = {
  article: createSubject<{ id: string }>(),
} as const;

const config: KeycardConfig = { subjects: Subjects, actions: Actions };

const builder = new PolicyBuilder(config);
const def = builder.def();
const policy = new Policy(def, config);
```

**How naming works:**

- `createSubject()`/`createAction()` with no name generates a random id
  immediately and assigns it as the def's internal name — the object is
  fully usable (`.wrap()` works) right away, with no two-phase
  "unregistered" state to worry about.
- The catalog (`Record<string, SubjectDef>` / `Record<string, ActionDef>`)
  is what actually assigns the *externally visible* name: **the catalog
  key becomes the name serialized into the `PolicyDefinition`'s rule
  tuples**, not the random internal id. A `PolicyBuilder`/`Policy` builds
  a reverse map (random id → catalog key) once, from its `config`, and
  uses it to resolve a dynamic def's id into the stable name whenever it
  needs one — at `.allow()`/`.deny()` time in the builder, and at
  `.can()`/`.cannot()`/`.require()` time in the policy.
- Because matching is by the random id's *value*, not object reference
  identity, a structurally-cloned copy of a dynamic def (`{...someDef}`)
  still resolves correctly.
- The reverse map is **built once** (at `PolicyBuilder`/`Policy`
  construction) and cached — not rebuilt per `.allow()`/`.can()` call.
  This keeps evaluation as cheap as the existing one-time validation
  `Policy` already does in its constructor (`validateRules`,
  `validateOperatorsRegistered`).

**Why not skip the catalog and just use the random id as the name?**
Because the whole point of a `PolicyDefinition` is to serialize to
JSON/YAML for persistence and cross-language/cross-process use (see
`SPEC.md`). A random id generated fresh each process start would make a
previously-serialized definition unmatchable after any restart, and would
make two processes building "the same" policy independently produce
definitions that can never agree with each other. Routing the *catalog
key* — a stable, developer-chosen, source-committed string — into the
serialized form avoids that; the random id never has to leave the
process.

**Required registration, enforced loudly, not lazily:**

- If a dynamic def is passed to `PolicyBuilder.allow()`/`.deny()`, it
  **must already be registered** in the catalog the builder was
  constructed with. This is checked at the `.allow()`/`.deny()` call
  site, not deferred.
- If a catalog is provided when constructing a `Policy` from a
  `PolicyDefinition`, **every** subject and action name actually used in
  that definition's rules must be present in the catalog. This is checked
  once, at `Policy` construction — the same moment `Policy` already
  validates `meta.operators` coverage and rule-tuple shape today
  (`impl/js/src/policy/policy.ts`) — not lazily, the first time a
  particular rule happens to get exercised.
- `PolicyBuilder` and `Policy` are separate, independently-constructible
  classes. Passing them different catalogs (e.g. loading a definition
  elsewhere with a trimmed-down config) is a caller error, not something
  the library reconciles for you — but it still fails loudly at `Policy`
  construction per the previous bullet, rather than only surfacing when a
  specific under-covered rule is finally checked.
- Duplicate names — whether two dynamic defs collide on their generated
  id, or two catalog entries end up naming the same string — are an
  error, checked once at build/load time, not a silent overwrite.

**Unregistered defs encountered at check time** (a dynamic Subject/Action
that was never added to any catalog, so its id isn't in the reverse map,
and doesn't appear in `def.rules` either — so the construction-time sweep
above never saw it): these are **denied by default, with a warning
logged**, unless an applicable wildcard rule matches. This needs no
special-casing in the matcher: a wildcard rule (`_ANY_`/`meta.anyAction`/
`meta.anySubject`) already matches based on the *rule's* own declared
action/subject being the wildcard sentinel, never by resolving the
incoming action/subject's name — so it matches an unresolvable dynamic def
today, unmodified. A non-wildcard rule can never match an unresolved name
(it can't equal any concrete catalog-key string), so it falls through the
reverse scan like any other non-match, landing on default-deny if nothing
wildcard-shaped catches it first. The warning is the only new behavior:
without it, "correctly denied" and "this check was structurally
meaningless because the subject was never wired up" are indistinguishable
from the caller's side. Dedup the warning per distinct unregistered id
rather than per call, so a ghost def in a hot path logs once, not on every
invocation.

Resolved questions
------------------

- **"Claims" terminology.** `GLOSSARY.md` now distinguishes **Policy
  Claims** (actor-side data a `PolicyBuilder` uses to decide which rules
  to generate, e.g. a JWT or `{ ownerOf: number[] }`) from **Subject
  Claims** (the resource-side fields `Subject.instance`/`.wrap()` carries,
  checked by Conditions against literals in a rule — in the shipped
  example, `Article`'s own fields). "The object `toSubject()` produces" is
  always a Subject Claims projection; don't call it "claims" without that
  qualifier when the distinction from Policy Claims matters.

Open questions (not yet resolved)
------------------------------------

- **Actor-aware `toSubject()`.** If a mapping ever needs to precompute
  something like `isOwner: boolean` against the current actor, that moves
  authorization logic out of the declarative Condition and into
  application code your policy tests won't exercise. Prefer raw,
  comparable fields (`owner_id`) that a Condition can compare against a
  literal, and keep `toSubject()` actor-agnostic, unless there's a
  specific reason a computed, actor-aware claim is unavoidable.
