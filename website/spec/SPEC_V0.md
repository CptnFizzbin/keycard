---
id: spec-v0
slug: /v0
title: "V0"
---

# KeyCard Policy Specification — v0.1

This document is the authoritative definition of the `PolicyDefinition`
format and its evaluation semantics for the v0 line. `../../SPEC.md` at the
repository root remains the informal overview; `GLOSSARY.md` holds term
definitions. Where either of those disagrees with this document, this document
wins.

The key words **MUST**, **MUST NOT**, **SHOULD**, **SHOULD NOT**, and **MAY**
are to be interpreted as in [RFC 2119](https://www.ietf.org/rfc/rfc2119.txt).

## 1. Overview

This document defines:

- The shape of a `PolicyDefinition` and its `version` field.
- What an action and a subject are, and how they're matched, including the
  policy-declared wildcard tokens.
- The rule-evaluation algorithm — `can`/`cannot`/`require`.
- The condition language and the evaluation semantics of every operator.
- The optional, embedded test-case format for a policy document, `tests`

It does not define the `PolicyBuilder`'s fluent API, wire format (YAML vs.
JSON), or any language-specific type system

## 2. Versioning

`version` is a [SemVer](https://semver.org/) string, `MAJOR.MINOR.PATCH` (e. g.
`"1.0.0"`).

### 2.1 What counts as a `MAJOR`, `MINOR`, or `PATCH` change to this spec

This subsection governs how *this specification* is versioned from release to
release — guidance for spec maintainers, not something an implementation checks
at runtime. It describes the general, post-`1.0` mapping; while `MAJOR` is
`0`, the carve-out above applies instead, and a change that would otherwise read
as `MINOR` here lands within the current `0.1` without a version bump.

- **`MAJOR`**: any change that could alter the allow/deny outcome for some
  already-valid document under the current `MAJOR` version, or that makes a
  previously-valid document invalid (removing a field, operator, or matching
  guarantee; narrowing what was previously valid; changing a default).
- **`MINOR`**: any purely additive change — a new optional field, a new
  operator, new advisory (SHOULD/MAY) guidance — that cannot alter the outcome
  for any document that doesn't use the new feature.
- **`PATCH`**: wording-only changes with zero effect on any implementation's
  behavior (typo fixes, clarified examples, added cross-references). Since
  `PATCH` carries no compatibility meaning, a policy document's `version`
  field can omit it — `"1.0"` is a valid shorthand for `"1.0.*"`.

### 2.2 Pre-Alpha Specification v0

While `MAJOR` is `0` the entire `0.y.z` line is unstable and carries no
compatibility promise.

## 3. Terminology

### 3.1 Implimentation

An Implimentation in this document refers to a library or package that is able
to understand the specification for a Policy Definition. Some

### 3.1 Claims

A Claim is a key/value pair used during permission checks (Subject Claim) or for
constructing a policy (Policy Claim). Claims are recommended to be scoped to the
specific subset of values needed for permission checks.

### 3.2 Action

An Action is something the user wants to do (`Read`, `Create`, `Update`,
`MarkDone`, ...). Actions are not tied explictly to a Subject.

### 3.3 Subject

A Subject is something the user wants to act upon (e.g. `Article`, `Comment`,
`User`). A Subject can optionally contain Subject Claims that are for a specific
instance of a object (e.g. `Article.wrap({ id: 1, ownerId: 5 })`)

### 3.3 Operators

A condition operator is a `$`-prefixed key inside a `Condition` value (e.g.
`$eq`, `$gt`, `$hasRole`). All implimenations **MUST** support the common set of
built in operators as specified in the spec document the implimentation supports

### 3.4 Builder

A `PolicyBuilder` takes Policy Claims (arbitrary application data, e.g. a JWT or
`{ ownerOf: number[] }`) and produces a `Policy` and/or
`PolicyDefinition`.

This document does not define the builder's API surface, but it is encuraged for
implimenations to follow a common pattern based on existing implimentations.

### 3.5 Policy

A `Policy` is an object used to perform checks (`can`/`cannot`/`require`)
against/with. It is constructed from a `PolicyDefinition` or directly from a
`PolicyBuilder`.

### 3.6 Rule

A rule is a combination of an effect, an Action, a Subject, and optionally a
Condition. For example `[allow, Update, Article, { ownerId: 4 }]` or in english,
"Allow the user to Update an Article when the article's ownerId is 4"

## 4. Definition Structure

```yaml
version: "0.1"                        # required, SemVer string
name: string                          # optional, informational only
description: string                   # optional, informational only

meta: # optional
  anyAction: string | boolean | null  # optional, defaults to "_ANY_"
  anySubject: string | boolean | null # optional, defaults to "_ANY_"
  actions: string[]                   # optional catalog
  subjects: string[]                  # optional catalog
  operators: string[]                 # optional catalog
  application: any                    # optional, opaque application data 

rules:
  - [ Effect, Action, Subject, Conditions? ]

tests: # optional
  - name: string
    description: string               # optional
    cases:
      - name: string                  # optional
        check: [ Action, Subject, SubjectData? ]
        expected: boolean
```

### 4.1 Envelope

- `version` — **REQUIRED**. A SemVer string (`MAJOR.MINOR.PATCH`).
    - `PATCH` **MAY** be omitted from the version string. If not given, it is
      assumed to be 0.
    - Implementations **MAY** support a different `MAJOR` version than the one
      they primarily target (e.g. a 2.x implementation **MAY** still understand
      1.x documents). If an implementation does not support a document's `MAJOR`
      version — whether older or newer than what it implements — it **MUST**
      throw a `PolicyVersionException` when loading a policy rather than guess
      at compatibility.
    - Implementations **MUST** support every `MINOR` version lower than or equal
      to the one they implement, within the same `MAJOR` version — an
      implementation of 1.5.0 **MUST** correctly evaluate a document declaring
      anywhere from 1.0.0 through 1.5.0. A document declaring a
      `MINOR` version the implementation doesn't know yet (higher than what it
      implements, within the same `MAJOR`) **MUST** cause a
      `PolicyVersionException` when loading a policy.
    - Implementations **MAY** provide an option to disable `MINOR` version
      checks (e.g. for a caller that wants to accept a document declaring a
      newer `MINOR` than the implementation knows, at its own risk), but this
      **MUST** be an explicit opt-in.
    - Implementations **MUST** ignore `PATCH` when deciding compatibility —
      `"1.0.0"` and `"1.0.7"` **MUST** be treated identically.
    - Serializing a `PolicyDefinition` that a `PolicyBuilder` assembled from
      scratch **MUST** stamp the builder's own implemented version.
    - Serializing a`PolicyDefinition` obtained from an existing `Policy` (e.g.
      `Policy.toDefinition()`/`def()`), by contrast, **MUST** preserve that
      input definition's own `version` rather than overwrite it with the
      implementation's. a `Policy` round-tripped back to a definition **MUST
      NOT** silently claim a different version than the document it was
      constructed from.
- `name` — **OPTIONAL**. A human-readable name for the policy. Informational
  only; It plays no role in evaluation.
- `description` — **OPTIONAL**. A human-readable description of the policy.
  Informational only.

### 4.2 Meta

`meta` is an **OPTIONAL** object grouping information that can be used for
performing checks against a list of registered Actions, Subjects, and Operators

#### 4.2.1 `meta.anyAction` / `meta.anySubject`

- The wildcard tokens for the policy's Action and Subject positions respectively
  Implementations **MUST** default to the literal string `"_ANY_"` when not
  declared or set to `true`.
- A policy **MAY** override either to a different string, and **MAY**
  declare one without the other.
- Either **MAY** instead be explicitly set to `null` **or** `false` to disable
  the wildcard mechanism for that position entirely. When disabled, every rule's
  `Action` or `Subject` **MUST** be tested literally, including the default
  wildcard string `"_ANY_"`.
- A declared value that is neither a string, `null`, nor `false` (e.g. a number,
  a list) is invalid. Implementations **SHOULD** throw a `PolicyLoadException`
  immediately upon encountering one, rather than silently coercing it or passing
  it through as a raw value to be compared against later.
- Whatever string is currently `meta.anyAction`'s (or `meta.anySubject`'s)
  effective value **MUST NOT** be used as an ordinary action (or subject) name
  within that policy: a rule meaning the literal value equal to that string is
  indistinguishable from the wildcard.

#### 4.2.2 `meta.actions` / `meta.subjects`

- The full set of action names and subject names this policy's rules use. When
  declared, they **SHOULD** be enforced when loading a
  `Policy`. Implementations **SHOULD** throw a `PolicyLoadException` if some
  rule's action isn't `meta.anyAction` and isn't listed in `meta.actions`
  (symmetrically for subjects/`meta.subjects`).
- Each item in the set **MUST** be unique
- A policy **MAY** declare its full set via `meta.actions`/`meta.subject`.
- The wildcard token **SHOULD** be excluded from the catalog.
- A name listed in the catalog that no rule actually uses **SHOULD NOT** be
  treated as an issue. These catalogs describe the vocabulary a policy is
  allowed to use, not a requirement that every entry be exercised. For example,
  a superuser's policy consisting only of `[allow, _ANY_, _ANY_]`
  is valid even if `meta.actions`/`meta.subjects` separately enumerate a long
  list of specific names that rule doesn't literally mention (this validation
  only runs in the "rule references an undeclared name"
  direction).
- Implementations **MAY** trim a declared catalog down to only the subset of
  names a policy's rules actually use (e.g. when a tool regenerates or
  re-serializes a `PolicyDefinition`).

#### 4.2.3 `meta.operators`

- A declarative catalog of the custom `$`-prefixed condition operator names
  (e.g. `"$hasRole"`) this policy's rules use.
- When declared, implementations **MAY** walk every rule's `Condition`
  at construction and throw a `PolicyLoadException` immediately if some rule
  references a custom operator not listed here.
- Declaring an operator here does not implement it. `PolicyDefinition` is JSON
  encodeable and cannot carry an executable checker. The operator's behavior
  must be supplied separately by the host application, through whatever runtime
  operator-registration mechanism the implementation exposes alongside the
  built-ins.
- An operator **MUST** be unique amongst the set and the set of built-in
  operators.
- A policy **SHOULD** include `meta.operators` whenever any rule uses a custom
  operator.
- Implementations **MAY** trim a declared catalog down to only the subset of
  names a policy's rules actually use (e.g. when a tool regenerates or
  re-serializes a `PolicyDefinition`).

#### 4.2.4 `meta.application`

- An open slot for a host application to embed its own custom data in the
  `PolicyDefinition` — this spec imposes no shape on it and gives it no meaning.
- Implementations **MAY** expose `meta.application` back to the application
  (e.g. via `def()`/`toDefinition()`), but **MUST NOT** raise an error or
  otherwise reject a definition merely because `meta.application` is present,
  regardless of its shape or contents.

### 4.3 Rules

- `rules` **MUST** be present and **MAY** be an empty array (an empty policy —
  `rules: []` — is structurally valid and denies everything).
- It is a single, **ordered** list. An implementation **MUST NOT** treat
  `rules` as an unordered set.
- Each entry is a tuple of `[Effect, Action, Subject, Conditions?]`.
    - `Effect` **MUST** be the literal string `"allow"` or `"deny"`.
    - `Action` and `Subject` **MUST** be a string value.
    - `Condition` is optional; a three-element tuple `[Effect, Action, Subject]
  ` is an unconditional rule.
- A rule whose `Action` is the policy's effective `anyAction` **and** whose
  `Subject` is its effective `anySubject` **MUST NOT** carry a `Condition`
  element — a rule wildcarded on both sides **MUST** be unconditional.

### 4.4 Tests

`tests` is an **OPTIONAL** top-level field that embeds test cases directly in a
`PolicyDefinition`, so a policy's expected `can` outcomes travel with the
policy. This allows for a policy to perform a self-check during development
without requiring a list of unit tests to be defined in both languages.

```yaml
tests:
  - name: string                      # required — the suite's name
    description: string               # optional
    cases:
      - name: string                  # optional — the case's name
        check: [ Action, Subject, SubjectData? ]
        expected: boolean
```

- When present, `tests` **MUST** be an array of **Test Suite** objects. It
  **MAY** be an empty array.
- A **Test Suite** groups related cases under a name:
    - `name` — **REQUIRED**. A human-readable identifier for the suite (e.g. for
      a test runner's output). Informational only.
    - `description` — **OPTIONAL**. Informational only.
    - `cases` — **REQUIRED**. An array of **Test Case** objects. **MAY** be
      empty.
- A **Test Case** is one expected `can` outcome:
    - `name` — **OPTIONAL**. A human-readable identifier for the case.
    - `check` — **REQUIRED**. A tuple `[ Action, Subject ]` or `[ Action,
    Subject, SubjectClaims ]`.
        - A two-element `check` **MUST** be evaluated as an unconditional rule.
        - A three-element `check` **MUST** be evaluated as a conditional with
          the third element as the wrapped Subject Claims.
    - `expected` — **REQUIRED**. A boolean: the result of calling `can`
      with the provided check.

## 5. Operators

A `Condition` value filters *when* a rule applies, evaluated against the
subject's **value**.

### 5.1 General requirements

- A condition that cannot be meaningfully evaluated against the given subject
  **SHOULD** evaluate to `false`. Implimentations **MAY** print a diagnostic log
  alerting the developer to the issue.
- Types **SHOULD NOT** be cast when performing an evaluation. Implimentations
  **MAY** print a diagnostic log when this is detected.
- A condition that is itself a string, number, boolean, or `null` is shorthand
  for `{ $eq: <that value> }`:

```yaml
{ status: archived }         # same as { status: { $eq: archived } }
```

### 5.2 Missing fields vs. explicit `null`

These are two different things and **MUST** be distinguished:

- **Missing field** — the subject is an object/map that does not have the key at
  all (or the subject isn't an object/map-like value in the first place). This
  makes the *entire field-condition* evaluate to `false`, regardless of which
  operator is nested inside it (excluding `$ne`, see below)
- **Explicit `null`** — the subject has the key, and its value is `null`. This
  is a real value and is compared like any other: `{ field: null }` matches only
  when `subject.field` is `null`; `{ field: { $ne: null } }` matches when
  `subject.field` is present and is anything other than `null`.
- `null` **MUST NOT** be treated as a wildcard that matches anything.

### 5.3 Built-in operators

Every operator below takes the *current* subject value at that point in the
condition tree (initially the subject's full value; narrowed by field
condition).

#### 5.3.1 `$eq`

`{ $eq: value }`. Matches when `subject === value`.

**Requirements:**

- A bare scalar condition **MUST** be treated as shorthand for `$eq`.
- Equality **MUST** use value equality (not reference/identity equality) for
  primitives.

#### 5.3.2 `$ne`

`{ $ne: value }`. Matches when `subject !== value`.

**Requirements:**

- **MUST** be the exact negation of `$eq` for the same `subject`/
  `value` pair including when the field being tested is missing:
  since a missing field makes `$eq` evaluate to `false`, `$ne` on a missing
  field **MUST** evaluate to `true`.

#### 5.3.3 `$gt` / `$gte` / `$lt` / `$lte`

`{ $gt: number }` (and `$gte`/`$lt`/`$lte` identically shaped). Numeric
comparison.

**Requirements:**

- If the subject or value is not a number, the condition **MUST** evaluate to
  `false`.
- Implementations **MUST** ensure `NaN` never equals itself even where the host
  language's default equality would say otherwise (e.g. Java's `Double.equals`
  treats `NaN` as equal to `NaN`).

#### 5.3.4 `$in`

`{ $in: value[] }`. Matches when the array `value` contains `subject`.

**Requirements:**

- The value **MUST** be an array.
- If it is not an array, the condition **MUST** evaluate to `false`.
- Containment **MUST** use the same equality semantics as `$eq` per element.

#### 5.3.5 `$has`

`{ $has: value }`. Matches when the array `subject` contains `value`.

**Requirements:**

- `subject` **MUST** be an array.
- If it is not, the condition **MUST** evaluate to `false`

#### 5.3.6 `$substr`

`{ $substr: pattern }`. Matches when `String(subject)` contains a substring
described by `pattern`.

`pattern` is a string built from literal characters plus these special tokens:

Token `^`
: anchors the match to the **start** of the subject string.

Token `$`
: anchors the match to the **end** of the subject string.

Token `*`
: matches **zero or more** characters

Token `\`
: escapes the next character, making it literal. For example `\^`, `\$`,
`\*`, `\\`, `\e` are the literals `^`, `$`, `*`, `\`, `e`). A trailing `\`
with nothing following it **SHOULD** be discarded.

#### 5.3.7 `$or`

`{ $or: Condition[] }`. Matches when at least one sub-condition matches.

**Requirements:**

- The operand **MUST** be an array.
- If it is not an array, the condition **MUST** evaluate to `false`
- `{ $or: [] }` **MUST** evaluate to `false` (no alternative can be satisfied)

#### 5.3.8 `$and`

`{ $and: Condition[] }`. Matches when every sub-condition matches.

**Requirements:**

- The operand **MUST** be an array.
- If it is not an array, the condition **MUST** evaluate to `false`
- `{ $and: [] }` **MUST** evaluate to `true` (there is no unsatisfied conjunct)

#### 5.3.9 `$not`

`{ $not: Condition }`. Matches when the sub-condition does **not** match.

**Requirements:**

- **MUST** be the exact negation of evaluating `Condition` against the same
  `subject`.

#### 5.3.10 Field condition

`{ fieldName: Condition }` — any object key that does not start with `$`.

**Requirements:**

- **MUST** narrow the subject to `subject[fieldName]` and evaluate
  `Condition` against that narrowed value.
- When `fieldName` is missing from `subject`, the condition evaluates to
  `false`.
- For v0, only a check against the top level fields is supported.

#### 5.3.11 `$field` (explicit field access)

`{ $field: [name, Condition] }`. Equivalent to the bare-key field condition but
with the field name given explicitly as a tuple element instead of as the object
key.

### 5.5 Custom operators (`$op`)

`{ $op: value }`, where `$op` is neither a built-in operator.

Delegates to an operator implementation registered on the
`Policy`/`ConditionResolver` instance when loading a policy, via whatever
registration mechanism the implementation exposes. A custom operator receives
the same `(subject, value)` pair a built-in does, plus a resolution context
exposing something equivalent to
`resolveSubcondition(subject, condition)` — this is what lets a custom operator
recurse into the condition language (e.g. implementing its own
`$and`-like combinator) exactly the way the built-in `$and`/`$or`/`$not` do.

**Requirements:**

- An unregistered `$op` (no operator registered for it on this instance)
  MUST evaluate to `false`.

### 5.6 Multi-key condition objects

A condition object **MAY** contain more than one key. Every key in a condition
object **MUST** be evaluated, and the object matches only if all of them do.
Keys **MUST** be implicitly ANDed together, whether they are operators, field
names, or a mix of both:

```yaml
{ $ne: null, status: "open" }   # subject is not null, AND subject.status == "open"
```

- An operator key (`$eq`, `$gt`, `$or`, ...) **MUST NOT** "consume" the whole
  object or cause sibling keys to be ignored.

## 6. Evaluating a check

#### 6.1 `can` / `cannot` / `require`

The last matching rule wins:

```js
function can (action, subject) {
  for (const [effect, ruleAction, ruleSubject, ruleConditions] in reverse(rules)) {
    if (!matchesAction(action, ruleAction)) continue
    if (!matchesSubject(subject, ruleSubject)) continue

    if (ruleConditions) {
      if (!hasInstance(subject)) continue

      const instance = getInstance(subject)
      if (!evaluate(instance, ruleConditions)) continue

      return effect == 'allow'
    }

    return effect == 'allow'
  }

  return false // nothing matched: default deny
}

function matchesAction (action, ruleAction) {
  return action == ruleAction || ruleAction == effectiveAnyAction(meta)
}

function matchesSubject (name, ruleSubject) {
  return name == ruleSubject || ruleSubject == effectiveAnySubject(meta)
}

// meta.anyAction/meta.anySubject: absent -> "_ANY_" default; an explicit
// string -> that string; explicit null or false -> DISABLED (a sentinel no
// rule's Action/Subject can ever equal, so the wildcard branch of
// matchesAction/matchesSubject above never succeeds); anything else -> throw.
function effectiveAnyAction (meta) {
  return resolveWildcard(meta?.anyAction)
}

function effectiveAnySubject (meta) {
  return resolveWildcard(meta?.anySubject)
}

function resolveWildcard (declared) {
  if (declared === undefined) return '_ANY_'
  if (declared === null || declared === false) return DISABLED
  if (typeof declared === 'string') return declared
  throw new PolicyLoadException('...') // anything else is invalid
}
```

`matchesAction`/`matchesSubject` perform exact, **case-sensitive** string
comparison: the string `"read"` **MUST NOT** match a rule written for
`"Read"`, and if the effective `anyAction` is `"_ANY_"`, the string
`"_any_"` **MUST NOT** be treated as the wildcard. Field names inside condition
are likewise matched exactly as written.

`subject` accepts three shapes:

- implementation **MAY** support a bare string (`"Article"`) as the subject —
  treated as an Unconditional Rule check.
- implementation **MUST** support `SubjectDef` (type token, no instance) —
  treated as an Unconditional Rule check.
- implementation **MUST** support `SubjectRef` (type token + wrapped instance) —
  treated as a Conditional Rule check; `hasInstance` above is
  `true` only for this shape.

Checking without instance data (a bare string or `SubjectDef`) still runs the
algorithm above, but can never satisfy a rule that carries a
`Condition` element, since `hasInstance(subject)` is `false`:

- `can("Update", "Article")` (no instance data) against a rule
  `[allow, Update, Article, { owner_id: 1 }]` is `false` (assuming no other rule
  matches) — the condition can't be satisfied without instance data to inspect,
  and that counts as "does not match," not as an error or an automatic pass.
- Whatever a `SubjectDef` check's implementation-defined internal "subject
  value" looks like, it **MUST NOT** expose fields that make ordinary domain
  condition (`owner_id`, `status`, etc.) accidentally match — a condition that
  inspects real domain fields is expected to evaluate to
  `false` in the absence of real instance data, the same as above.

`cannot(action, subject)` **MUST** return the exact negation of
`can(action, subject)`. `require(action, subject)` **MUST** perform the same
evaluation as `can` but, instead of returning a boolean, resolve successfully
when the result is `true` and signal failure (e.g. by throwing)
when it is `false`; the exact error type/shape is an implementation concern
outside this spec's scope.

An implementation **MAY** implement this algorithm differently (an index by
action/subject, a compiled decision structure, etc...) as long as its observable
`can`/`cannot`/`require` results are identical to this reverse scan for every
input. All Implementations **MUST** validate that equivalence against a shared
conformance test suite (the fixtures under
`../../test/fixtures`) rather than by inspection alone.

The requirements that **MUST** hold for any v0-conformant implementation are:

1. **Default deny.** If the scan reaches the front of `rules` without a single
   match, the result **MUST** be `false`. An empty policy (`rules:
   []`) denies everything.
2. **Last-matching-rule-wins.** An implementation **MUST** scan `rules` from the
   most-recently-declared entry backward to the first, and **MUST**
   return as soon as it finds one rule whose action, subject, and (if present)
   condition all match. That rule's effect (`allow` or `deny`)
   *is* the answer — every earlier-declared rule, however specific or however
   unconditional, is irrelevant once a later-declared rule also matches. There
   is no independent "any deny beats any allow" veto and no AND-combination of
   multiple matching rules' effects: exactly one rule decides the outcome (or
   none does, and default deny applies).
    - A rule with no fourth tuple element (`condition`) blocks/opens every
      instance of that action/subject combination — *as long as no rule declared
      after it also matches*. Under last-rule-wins, a blanket rule is not immune
      to being reopened or reclosed by a later, more specific rule for the same
      action/subject; if that's not the intent, the blanket rule **MUST** be
      declared last among the rules it's meant to override. Implementations
      **MAY** print a warning to the console for either of two suspicious
      rule-ordering patterns: a blanket **allow**
      rule declared *before* other rules that could match the same
      action/subject, or a blanket **deny** rule declared *after* a run of other
      rules for the same action/subject.
    - Each rule's own condition is evaluated independently against the same
      subject value, but only the *last-declared* rule among those whose
      condition are satisfied decides the outcome — the two matching rules are
      not combined or ANDed together, and it is not enough for a deny rule to
      simply have a matching condition somewhere earlier in the list.
    - Listing two rules that could both match the same action, subject, and
      instance is not harmless — whichever of them is declared *later* in
      `rules` is the one that decides the outcome, full stop, even if an earlier
      rule looks more specific. General rules **MUST** be placed first, with any
      rule meant to override them declared strictly later in the array;
      reordering two overlapping rules can silently flip `can`'s answer.
      Implementation documentation **SHOULD** encourage this
      "general rules first, specific rules later" convention explicitly.
3. **Order is significant.** Order **MUST** be preserved. Because the algorithm
   is a reverse scan for the first match, the position of a rule relative to the
   others that could match the same action/subject is meaningful: moving a rule
   later in `rules` can change `can`'s answer for cases it overlaps with. An
   implementation, a builder, and a parser all **MUST** preserve declaration
   order end to end.
4. **Wildcard matching is policy-scoped.** `matchesAction`/`matchesSubject`
   consult *this policy's own* effective `anyAction`/`anySubject` — there is no wildcard token independent of what a given policy
   declares or defaults to.
    - `meta.anyAction`/`meta.anySubject` each default to `"_ANY_"` when not
      declared, so `[allow, _ANY_, _ANY_]` (unconditional) is always a valid
      rule matching every action on every subject name, even in a policy that
      declares no `meta` at all. `[allow, _ANY_, Article]` matches every action
      on `Article`; `[allow, Delete, _ANY_]` matches `Delete`
      on every subject name. Wildcards are resolved purely by string comparison
      against the policy's effective `anyAction`/`anySubject` — they are not
      regex or glob patterns.
    - Either **MAY** be explicitly disabled by setting `meta.anyAction`/
      `meta.anySubject` to `null`; in a policy that disables one, no
      string carries wildcard meaning for that position, including
      `"_ANY_"` itself, and it becomes a legal, ordinary literal name.
5. **A rule wildcarded on both sides MUST be unconditional.** A rule whose
   `Action` is the policy's effective `anyAction` **and** whose `Subject` is its
   effective `anySubject` **MUST NOT** carry a `Condition` element.
   `Policy.from(...)` **MUST** throw a `PolicyLoadException` when given a
   definition violating this; a `PolicyBuilder`'s
   `allow()`/`deny()` methods (or equivalent) **MUST** throw an argument-error
   exception immediately when called this way, rather than waiting for
   eventual construction to catch it. A rule wildcarded on only *one* side
   **MAY** carry a condition: `[effect,
   anyAction, Subject, Conditions]` (wildcard action, concrete subject) is
   unrestricted, since `Condition` evaluates against that concretely-known
   subject type's data; `[effect, Action, anySubject,
   Conditions]` (concrete action, wildcard subject) is valid but **SHOULD NOT**
   be used, since `Condition` may then be evaluated against subjects of many
   unrelated shapes and silently fail to match some of them via the
   missing-field handling, rather than failing loudly. Conditions in KeyCard
   evaluate against the *target subject's* data, not against claims about
   the caller — an "admins can do anything" rule still isn't expressible as a
   doubly-wildcarded rule with a role condition attached; model it as a
   per-action rule with a condition instead (see the worked example at the end
   of this document), or select an entirely different
   `PolicyDefinition` at the application layer based on the caller's claims.

Broad rules are typically declared first, and later, more specific rules
override them for the cases they cover — including a later `allow`
reopening something an earlier `deny` closed. Because only one rule ever decides
the outcome, implementation documentation **SHOULD** encourage this convention
explicitly.

### 6 Running `tests`

- An implementation that exposes a way to run a `PolicyDefinition`'s embedded
  `tests` — whatever it calls that operation, and whatever shape its
  report takes — **MUST**, for each case, call `can`
  with that case's `check` elements as arguments, and **MUST** report the case
  as passing if and only if the boolean result equals `expected`.
- Running `tests` **MUST NOT** mutate the `Policy` instance or otherwise affect
  its subsequent `can`/`cannot`/`require` behavior.
- This spec does not mandate a report format, a CLI surface, or that
  `Policy.from(...)` reject an otherwise-valid `PolicyDefinition` merely because
  a declared test case would fail — whether and how a failing case blocks
  construction, a build, or a CI job is an application/tooling concern outside
  this spec's scope.

## 8. Prior work

KeyCard's condition language and rule-based `allow`/`deny` model draw on
[CASL](https://casl.js.org/), a JavaScript authorization library. In particular,
the last-matching-rule-wins combining behavior mirrors CASL's own
approach to resolving overlapping rules. KeyCard departs from CASL in scope (a
declarative, JSON/YAML-serializable `PolicyDefinition`
meant to be produced server-side and evaluated in any language, rather than an
in-process JavaScript ability builder) and in specifics (the condition operator
set, the `meta` catalogs and wildcard tokens, and the
validation/exception behavior throughout) — this document defines
KeyCard's own behavior in full; familiarity with CASL is not assumed anywhere
above.

## Appendix: worked example

```yaml
version: "1.0"
name: "Article Ownership Policy"
description: "Users can only modify their own articles"

meta:
  actions: [ Read, Create, Update, Delete ]
  subjects: [ Article ]
  operators: [ $hasRole ]

rules:
  - [ allow, Read, _ANY_ ]
  - [ allow, Create, Article ]
  - [ allow, Update, Article, { owner_id: 1 } ]
  - [ allow, Delete, Article, { owner_id: 1 } ]
  - [ deny, Delete, Article, { status: archived } ]
  - [ allow, Delete, Article, { $hasRole: admin } ]

tests:
  - name: "ownership"
    cases:
      - name: "owner can update their own article"
        check: [ Update, Article, { owner_id: 1 } ]
        expected: true
      - name: "non-owner cannot update someone else's article"
        check: [ Update, Article, { owner_id: 2 } ]
        expected: false
  - name: "archived articles"
    cases:
      - name: "owner cannot delete their own archived article"
        check: [ Delete, Article, { owner_id: 1, status: archived, roles: [ ] } ]
        expected: false
      - name: "admin can delete an archived article regardless of ownership"
        check: [ Delete, Article, { owner_id: 1, status: archived, roles: [ admin ] } ]
        expected: true
```

The first rule uses this policy's default wildcard-subject token, `_ANY_`
 — no `meta.anySubject` override is declared, so `_ANY_` is what
`matchesSubject` checks for; a rule wildcarded on only one side (here, the
subject) **MAY** carry a condition, but this one doesn't need to, so it's
unconditional.

The last rule is declared last on purpose: because it's an `allow` and its
condition (`$hasRole: admin`) can still be satisfied for an archived article, an
admin can delete an archived article even though the `deny`
rule above would otherwise block it — demonstrating last-rule-wins
overriding an earlier, more specific-looking rule. `$hasRole` isn't a built-in
operator — it's a custom one this policy declares in
`meta.operators` and requires the host application to have registered a checker
for at `Policy` construction time (e.g. one that checks
`subject.roles.includes("admin")`); if the application forgot to
register it, `Policy.from(...)` throws a `PolicyLoadException`
immediately, rather than silently letting every admin-only check fail closed
with no clue why once evaluation eventually reached it. Note this rule is
wildcarded on *neither* side (it names both a concrete action, `Delete`, and a
concrete subject, `Article`), so it isn't subject to the
both-sides-wildcarded restriction regardless.

The `tests` block documents this policy's own intent as executable cases:
the "ownership" suite exercises the third and fourth rules directly, and the
"archived articles" suite pins down exactly the last-rule-wins interaction the
two paragraphs above describe in prose — an admin deleting an archived article —
so a future edit that reorders or narrows those rules and breaks that guarantee
is caught by re-running `tests`, not just by re-reading the document.
