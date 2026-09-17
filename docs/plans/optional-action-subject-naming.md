# Optional naming for Action/Subject via a KeycardConfig catalog

> Working implementation plan/notes for an in-progress feature. Delete this
> file once the feature ships and its content has been folded into
> `docs/guidelines/keycard-api.md` (see "Docs" section below).

## Progress so far

- **JS: fully implemented and verified.** See the "JS implementation"
  section below for the exact files touched. `cd impl/js && npx vitest run`
  (377/377 passing) and `npx tsc --noEmit -p tsconfig.json` (clean) both
  verified locally via `npm install --no-workspaces` scoped to `impl/js`
  only (this repo's real package manager is Yarn 4/Berry via Corepack,
  pinned in root `package.json`'s `packageManager` field; Corepack
  couldn't fetch yarn 4.18.0 from `repo.yarnpkg.com` in that sandbox - blocked
  by an outbound proxy/firewall rule - so the scoped `npm install` was a
  local-verification-only workaround, **never run against the repo root**
  since `impl/js` is a Yarn workspace and the root `yarn.lock` is the
  shared, deduped dependency inventory across every workspace. A `yarn
  install`/`yarn workspace @cptn-fizzbin/keycard test` from the repo root
  is the correct way to install and verify in an environment with working
  network access).
  - One small deviation from the original plan below: `crypto.randomUUID()`
    isn't directly usable as a bare global reference under this project's
    `tsconfig.json` (`lib: ["ES2025"]` only, no `"dom"`, and no ambient
    global `crypto` declared by `@types/node` either) without triggering a
    `strict`-mode type error. Added `impl/js/src/lib/randomId.ts` - a tiny
    helper reaching `crypto.randomUUID()` via a narrow `globalThis` cast -
    and both `actionFactory.ts`/`subjectFactory.ts` call that instead of
    referencing `crypto` directly. Everything else matches the plan as
    written.
  - Files changed: `impl/js/src/action/action.ts`,
    `impl/js/src/action/actionFactory.ts`, `impl/js/src/subject/subject.ts`,
    `impl/js/src/subject/subjectFactory.ts`, `impl/js/src/keycardConfig.ts`,
    `impl/js/src/builder/policyBuilder.ts`, `impl/js/src/policy/policy.ts`,
    new `impl/js/src/lib/catalog.ts`, new `impl/js/src/lib/randomId.ts`.
  - Tests added/extended: new `impl/js/src/action/actionFactory.test.ts`,
    new `impl/js/src/subject/subjectFactory.test.ts`, extended
    `impl/js/src/builder/policyBuilder.test.ts` (new describe block:
    "PolicyBuilder: dynamic (no-name) Action/Subject resolved via a
    KeycardConfig catalog"), extended `impl/js/src/policy/policy.test.ts`
    (new describe block: "Policy: dynamic (no-name) Action/Subject resolved
    via a KeycardConfig catalog").
- **Java: not started.** See "Java implementation" below - mirrors the JS
  structure closely; nothing Java-specific has been written yet.
- **Docs touch-ups (GLOSSARY.md, guidelines status line): not started.**
- **Manual cross-language smoke test: not started.**

Once Java is done, verify with a real network connection:
`cd impl/java && mvn test` (this environment previously had a working
`~/.m2` cache from an earlier, unrelated session and could compile/run a
one-off snippet against it, but a *fresh* container should not assume that
cache exists - a real `mvn test` needs Maven Central reachable to resolve
`gson`/`junit`/`snakeyaml`/`lombok`).

## Context

`docs/guidelines/keycard-api.md` ("Dynamic (unnamed) Subjects/Actions and
the Catalog") already specifies this feature in detail, explicitly marked
"proposed extension, not yet implemented." The user's own sketch for the
canonical cross-language example (`new Action()`, `new Subject<T>()`, no
name argument, `config.actions = Actions`) depends on it, and the user has
confirmed the mechanism: **Action/Subject generate a random id when no
name is given; the catalog key (from a keyed map handed to
`KeycardConfig`) becomes the serialized name; `PolicyBuilder`/`Policy`
resolve id → name via a reverse map built once from that catalog.**

Today, `name` is a required, immutable field on `Action`/`Subject` in both
languages, read directly (`action.name` / `action.getName()`) at ~9-10
call sites across `PolicyBuilder` and `Policy` in each language (rule-tuple
construction, wildcard-token comparison, `meta.actions`/`meta.subjects`
catalog folding *and* enforcement, field-mapper lookup, error messages,
match logic). This plan makes naming optional at creation time without
disturbing any of the currently-shipped, already-tested behavior (plain
named `createAction("x")`/`ActionFactory.create("x")` usage, and the
existing array-based `KeycardConfig.actions`/`.subjects` vocabulary
declaration) — confirmed via two research passes: no existing JS or Java
test relies on Action/Subject *object* equality (only on resolved name
strings), and the v1 compliance fixtures only exercise the named-creation
path, so both are unaffected. (Now additionally confirmed on the JS side:
the full existing suite plus the new tests all pass unmodified.)

## Design decisions (filling gaps the guidelines doc leaves open)

1. **No new "id" field.** `createAction()`/`ActionFactory.create()` called
   with no name generates a random id (JS: `crypto.randomUUID()`; Java:
   `UUID.randomUUID().toString()`) and stores it in the *existing* `name`
   field — exactly as the guidelines doc says ("assigns it as the def's
   internal name"). No interface/shape change needed for the plain-usage
   case.
2. **A `dynamic` marker, internal only.** To know whether an Action/Subject
   *requires* catalog registration (vs. an explicitly-named one, which
   never does), add a hidden boolean:
   - JS: a non-enumerable-in-docs `__dynamic?: true` property alongside
     `__brand` on the plain object literal (still just a plain object,
     `Action`/`Subject` interfaces gain one optional readonly field).
   - Java: a second `private final boolean dynamic` field on `Action<T>`
     and `Subject<T>`. On `Action`, exclude it from Lombok's
     `@EqualsAndHashCode` (`@EqualsAndHashCode.Exclude`) so equality stays
     name-based, unchanged from today. `Subject` already has no
     equals/hashCode override (identity-based) — unaffected either way.
     `wrap()` must carry the flag through unchanged, same as `name`/
     `fieldMapper` today.
3. **Catalog key always wins when present.** For any Action/Subject that
   appears as a value in a keyed catalog, the catalog key is the name that
   gets serialized/matched — whether or not it was given an explicit name.
   This matches the user's framing ("if using a catalog, defining the name
   is optional") and keeps resolution uniform: `resolvedName = reverseMap.get(rawName) ?? rawName`
   for *every* Action/Subject, dynamic or not, with no special-casing.
   `dynamic` only decides whether *skipping* the catalog is an error.
4. **Reverse map, built once, cached.** `PolicyBuilder`/`Policy` build a
   `Map<string /* raw name/id */, string /* catalog key */>` once at
   construction from the config's keyed catalog(s), mirroring the existing
   one-time `validateRules`/`validateOperatorsRegistered` construction-time
   work. A small shared helper builds it in each language so
   `PolicyBuilder` and `Policy` don't duplicate the logic.
5. **Required registration, enforced loudly:**
   - `PolicyBuilder.allow()`/`.deny()`: if the action/subject passed is
     `dynamic` and its raw name isn't in the reverse map, throw immediately
     (JS: `PolicyArgumentError`; Java: `PolicyArgumentException` — the same
     types already used for the wildcarded-both-conditional check, same
     "catch at the call site, don't defer" spirit).
   - `Policy` construction: unchanged mechanism, reusing the *existing*
     EC-8 `meta.actions`/`meta.subjects` coverage check (`validateRules` /
     `unionNames`) — it already throws `PolicyLoadException`/
     `PolicyLoadException` when a rule's (already-resolved, already-a-
     plain-string) action/subject isn't covered. Nothing new needed here
     beyond feeding it the catalog-resolved names.
   - Duplicate names (two catalog entries resolving to the same raw
     id/name, i.e. the same object registered under two keys, or two
     independently-generated ids colliding) → throw when building the
     reverse map (construction time), not silently last-wins.
6. **Unregistered dynamic def encountered at `.can()`/`.cannot()`/
   `.require()` time:** not in the reverse map (never registered in any
   catalog reachable from this `Policy`) → log a warning, deduped per
   distinct raw id (not per call), and let it fall through the normal
   match loop using its raw (unresolvable) name — it can't equal any real
   catalog-key string, so it naturally lands on default-deny unless a
   wildcard rule matches (wildcard matching already only inspects the
   *rule's* declared token, never the incoming name, so it needs no
   changes).
7. **Logger lives on `KeycardConfig` itself** (this is the mechanism used
   for decision 6's warning, made explicit/configurable rather than always
   going through an ambient global):
   - JS: add `logger?: Logger` to `KeycardConfig`, reusing the existing
     `Logger` interface (`impl/js/src/lib/logger.ts`: `{info, warn,
     error}`). `PolicyBuilder`/`Policy` use `config.logger ?? getLogger()`
     — falls back to the existing module-level singleton when the config
     doesn't set one, so nothing breaks for callers not using this feature
     at all. **(Done.)**
   - Java: introduce a small `Logger` functional-style interface (e.g.
     `com.cptnfizzbin.keycard.lib.Logger` with `void warn(String message)`
     — mirroring the JS shape closely enough to keep the two
     implementations conceptually aligned; extend with `info`/`error` only
     if something else in this change ends up needing them) and add an
     optional `Logger logger` field to `KeycardConfig`. No existing
     logging facade was found in `impl/java/src/main` (confirmed: no
     slf4j/System.err usage), so `Policy`/`PolicyBuilder` fall back to a
     no-op logger when `config`/`config.getLogger()` is null — this is a
     new, minimal capability, not a rewiring of something existing.
     **(Not yet done.)**
8. **KeycardConfig shape:**
   - JS: widen the existing fields to a union —
     `actions?: Action[] | Record<string, Action>` and similarly for
     `subjects` — matching the sketch verbatim (`config.actions = Actions`,
     no `Object.values(...)` needed anymore). Existing array-based tests
     keep passing unchanged (array branch untouched). Add `logger?: Logger`
     (decision 7). **(Done.)**
   - Java: **add** two new optional Lombok fields, `Map<String, Action<?>>
     actionCatalog` and `Map<String, Subject<?>> subjectCatalog` (plain,
     not `@Singular`), alongside the existing `@Singular actions`/
     `subjects` lists — Java has no union types, and the existing
     list-based fields/tests must stay exactly as they are. Add `Logger
     logger` (decision 7). **(Not yet done.)**
9. **Fix a pre-existing bug while touching this file:** `impl/js/src/keycardConfig.ts`
   used to cite a nonexistent `SPEC_V1-0-0.md §3.2.2, EC-8` (wrong
   filename, wrong/nonexistent section — the real section is
   `SPEC_V1-0.md` §4.2.2, and the spec has no "EC-N" numbering at all
   as a literal string, though "EC-N" is still an established shorthand
   used pervasively and intentionally elsewhere in both codebases - do
   **not** touch those other citations, only this one file's specific
   wrong-filename/wrong-section bug). **(Done, on the JS side; there's no
   Java-side equivalent of this specific bug to fix.)**

## JS implementation — DONE, verified (see "Progress so far")

- `impl/js/src/action/action.ts` — add `readonly __dynamic?: true` to
  `Action`.
- `impl/js/src/action/actionFactory.ts` — `createAction<T extends string =
  string>(name?: T): Action<T>`; when `name` is omitted, generate a random
  id (via the new `impl/js/src/lib/randomId.ts` helper — see the deviation
  noted in "Progress so far") and set `__dynamic: true`.
- `impl/js/src/subject/subject.ts` / `subjectFactory.ts` — same treatment;
  `makeSubject`/`wrap()` must carry `__dynamic` through unchanged.
- `impl/js/src/keycardConfig.ts` — widen `actions`/`subjects` to `Action[]
  | Record<string, Action>` (resp. `Subject`); add `logger?: Logger`
  (import the type from `../lib/logger.ts`); fix the stale spec citation
  (design decision 9).
- `impl/js/src/lib/catalog.ts`: given a `KeycardConfig`'s `actions`/
  `subjects` (array or record), returns `{ reverseMap: Map<string,string>,
  names: string[] }` per kind — `names` is every resolved catalog name (for
  folding into `meta.actions`/`meta.subjects`); throws on duplicate
  resolved raw-name→key collisions (design decision 5, last bullet).
- `impl/js/src/builder/policyBuilder.ts`:
  - Constructor: builds the reverse maps once from `config`, stores on
    `this`.
  - `wildcardNameOf`: resolves `value.name` through the action/subject
    reverse map before returning.
  - `addRule`/`allow`/`deny`: resolves `action.name`/`subject.name` through
    the reverse map immediately; if `action.__dynamic` (or
    `subject.__dynamic`) and unresolved, throws `PolicyArgumentError`
    (design decision 5); uses the *resolved* name everywhere previously
    using the raw one (`actionsUsed`/`subjectsUsed`, the `RuleTuple`, the
    wildcard-conflict check).
  - `buildMeta`: folds in the catalog helper's `names` instead of
    `this.config.actions?.map(a => a.name)`.
- `impl/js/src/policy/policy.ts`:
  - Constructor: builds the reverse maps once from `config`, stores on
    `this`; passes resolved catalog names into `validateRules` (unchanged
    mechanism, just fed resolved names — design decision 5).
  - `validateRules`: uses the catalog helper's resolved names instead of
    `config.actions?.map(a => a.name)` (its signature changed from taking
    `config` directly to taking the two resolved-name arrays).
  - `matchesAction`/`matchesSubject`: resolve `action.name`/`subject.name`
    through the reverse map before comparing; if dynamic and unresolved,
    warn (deduped by raw id, via a new `warnIfUnregisteredDynamic` private
    helper) before falling through with the raw (unresolvable) name
    (design decision 6).
  - `resolveFieldMapper`: resolves `subject.name` through the reverse map
    before the `config.mapper?.get(...)` lookup.
  - `require`: uses the resolved name in the error message.
  - Warning path: `this.config.logger ?? getLogger()` (from
    `impl/js/src/lib/logger.ts`) for the dedup warning; a small
    per-Policy-instance `Set<string>` of already-warned raw ids (design
    decision 6, "dedup per distinct id").

## Java implementation (mirrors JS) — NOT STARTED

- `impl/java/src/main/java/com/cptnfizzbin/keycard/action/Action.java` —
  add `private final boolean dynamic` (Lombok `@EqualsAndHashCode.Exclude`);
  add `Action.create()` static factory (no-arg) using
  `UUID.randomUUID().toString()`, `dynamic = true`; existing
  `create(T name)` sets `dynamic = false`. `ActionFactory` gets a
  matching no-arg `create()` overload.
- `impl/java/src/main/java/com/cptnfizzbin/keycard/subject/Subject.java` —
  add `private final boolean dynamic` field, threaded through the private
  constructor and `wrap()`; add `Subject.create()` /
  `Subject.create(SubjectFieldMapper<T>)` no-arg overloads using
  `UUID.randomUUID().toString()`. `SubjectFactory` gets matching overloads.
- New `com.cptnfizzbin.keycard.lib.Logger` interface (`void warn(String
  message)`, extended later only if needed) plus a package-private no-op
  default implementation.
- `impl/java/src/main/java/com/cptnfizzbin/keycard/KeycardConfig.java` —
  add `Map<String, Action<?>> actionCatalog` and `Map<String, Subject<?>>
  subjectCatalog` fields (plain Lombok `@Builder` fields, not
  `@Singular`), alongside the existing `@Singular actions`/`subjects`
  lists; add `Logger logger` field.
- New small internal helper class (e.g. `com.cptnfizzbin.keycard.lib.Catalog`
  or similar, mirroring the JS helper) building `{Map<String,String>
  reverseMap, List<String> names}` from a `KeycardConfig`'s catalogs;
  throws `PolicyArgumentException` on duplicate collisions.
- `impl/java/src/main/java/com/cptnfizzbin/keycard/builder/PolicyBuilder.java`:
  - `KeycardConfig` constructor (lines 95-100 as of this writing): build
    the reverse maps.
  - `allow`/`deny` (lines 102-116): resolve names before calling
    `addRule`; throw `PolicyArgumentException` for an unresolved `dynamic`
    action/subject (mirrors JS design decision 5).
  - `buildMeta` (lines 126-149): fold in the catalog helper's names
    instead of `action.getNameStr()`/`subject.getName()` directly (lines
    130-133).
- `impl/java/src/main/java/com/cptnfizzbin/keycard/policy/Policy.java`:
  - `KeycardConfig` constructor (line 78-86): build the reverse maps.
  - `checkPermission` (lines 167-194): resolve `action.getName()`/
    `subject.getName()` (lines 172-173) through the reverse map; warn via
    `config.getLogger()` (falling back to the no-op default when `config`/
    `config.getLogger()` is null), deduped per raw id, if
    dynamic-and-unresolved.
  - `resolveFieldMapper` (lines 196-201): resolve `subject.getName()`
    first.
  - `require` (line 153-157): use the resolved name.
  - `validateRules` (lines 235-304): feed it catalog-resolved names
    (`unionNames`'s `Function<T,String> nameOf`, lines 244/247) instead of
    the raw `Action::getNameStr`/`Subject::getName` method references.

(Line numbers above are from the Java source as of this plan's writing —
re-check them, since nothing there has been touched yet and they may have
drifted if anything else changed the file in between.)

## Tests to add (mirroring existing style/location)

- JS **(done)**: `impl/js/src/action/actionFactory.test.ts` (new),
  `impl/js/src/subject/subjectFactory.test.ts` (new) — no-arg creation
  generates a usable, distinct id each call; `wrap()` preserves it.
  `impl/js/src/builder/policyBuilder.test.ts` (extended) — catalog
  resolution folds the *key* (not the raw id) into `meta.actions`/
  `meta.subjects`; a plain array config still behaves exactly as before;
  `.allow()` with an unregistered dynamic action/subject throws
  `PolicyArgumentError`; registering the same dynamic def under two
  different catalog keys throws at construction; an explicitly-named
  entry inside a keyed catalog still resolves to its catalog key.
  `impl/js/src/policy/policy.test.ts` (extended) — `Policy` built with a
  keyed catalog resolves `.can()` correctly; EC-8 coverage is still
  enforced using catalog-resolved names; an unregistered dynamic def
  passed to `.can()` warns once (deduped) via a `config.logger` spy and
  denies; it still falls through to a matching wildcard rule; omitting
  `config.logger` (so it falls back to the ambient `getLogger()`) doesn't
  throw.
- Java **(not started)**: mirror each of the above in
  `PolicyValidationTest.java` (or a new `DynamicCatalogTest.java` alongside
  it) and `SubjectFieldMapperTest.java` where relevant.
- Both: confirm `Subject.wrap()` on a dynamic subject preserves its
  identity (still resolves via the same catalog entry after wrapping) —
  **done on the JS side**, not yet on the Java side.

## Docs (small, scoped touch-ups only — not the website docs) — NOT STARTED

- `docs/guidelines/keycard-api.md` — once shipped, remove/update the
  "proposed extension, not yet implemented" callout for this section.
- `GLOSSARY.md` — add a short "Catalog" entry (currently undefined there).
- Explicitly **out of scope for this change**: `website/docs-js`,
  `website/docs-java`, and the two READMEs (already updated in an earlier
  session on this branch to use `Object.values(Actions)` against today's
  array-only config). Once this ships, a natural follow-up is simplifying
  those to pass `Actions`/`Subjects` maps directly — worth calling out to
  the user as a next step, not bundling into this change.

## Verification

- JS: `cd impl/js && npx vitest run` (full suite) plus `npx tsc --noEmit
  -p tsconfig.json` — **done, both clean** (see "Progress so far" for the
  install-tooling caveat).
- Java: `cd impl/java && mvn test` (full suite, includes the new tests and
  the existing compliance-fixture runner, confirming named-path parity is
  untouched) — **not yet run**.
- Manual smoke check (both languages) — **not yet done**: reproduce the
  user's original sketch verbatim — `Actions`/`Subjects` created with no
  names, one `KeycardConfig` built from the maps, a policy built and a
  permission checked — confirm the resolved rule/meta names in the
  serialized `PolicyDefinition` are the catalog keys, not the random ids.
