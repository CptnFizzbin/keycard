---
id: spec-v0
slug: /v0
title: "V0"
---

# KeyCard Policy Specification — v0.1

This document is the authoritative definition of the `PolicyDefinition`
format and its evaluation semantics for the v0 line. `SPEC.md` at the
repository root is the informal overview, and `GLOSSARY.md` holds term
definitions. Where either of those disagrees with this document, this document
wins.

The key words **MUST**, **MUST NOT**, **SHOULD**, **SHOULD NOT**, and **MAY**
are to be interpreted as in [RFC 2119](https://www.ietf.org/rfc/rfc2119.txt).

## Overview

This document defines:

- The shape of a `PolicyDefinition` and its `version` field.
- What an action and a subject are, and how they're matched, including the
  policy-declared wildcard tokens.
- The rule-evaluation algorithm — `can`/`cannot`/`require`.
- The condition language and the evaluation semantics of every operator.
- The optional test-case format embedded in a policy document, `tests`.

It does not define the `PolicyBuilder`'s fluent API, the wire format (YAML
vs. JSON), or any language-specific type system.

## Versioning

`version` is a [SemVer](https://semver.org/) string, `MAJOR.MINOR.PATCH` (e.g.
`"0.1.0"`).

### What counts as a `MAJOR`, `MINOR`, or `PATCH` change to this spec

This subsection governs how *this specification* is versioned from release to
release — guidance for spec maintainers, not something an implementation checks
at runtime. It describes the general, post-`1.0` mapping; while `MAJOR` is
`0`, the pre-alpha rules below apply instead.

- **`MAJOR`**: any change that could alter the allow/deny outcome for some
  already-valid document under the current `MAJOR` version, or that makes a
  previously-valid document invalid (removing a field, operator, or matching
  guarantee; narrowing what was previously valid; changing a default).
- **`MINOR`**: any purely additive change — a new optional field, a new
  operator, new advisory (SHOULD/MAY) guidance — that cannot alter the outcome
  for any document that doesn't use the new feature.
- **`PATCH`**: wording-only changes with zero effect on any implementation's
  behavior (typo fixes, clarified examples, added cross-references). `PATCH`
  carries no compatibility meaning (see Envelope).

### Pre-Alpha Specification v0

While `MAJOR` is `0`, the entire `0.y.z` line is unstable and carries no
compatibility promise. A change that would otherwise count as `MINOR` lands
within the current `0.1` without a version bump.

## Terminology

### Implementation

An implementation is a library or package that loads a `PolicyDefinition` and
evaluates it as this document describes.

### Claims

Claims are an object of key/value pairs, used either for constructing a policy
(**Policy Claims**) or during permission checks (**Subject Claims**). Claims
**SHOULD** be scoped to the subset of values the policy actually needs.

### Action

An Action is something the user wants to do (`Read`, `Create`, `Update`,
`MarkDone`, ...). An Action is not tied to any particular Subject.

### Subject

A Subject is something the user wants to act upon (e.g. `Article`, `Comment`,
`User`). A Subject can optionally carry the Subject Claims of one specific
instance (e.g. an `Article` with `{ id: 1, ownerId: 5 }`). This document calls a
Subject without instance data a **`SubjectDef`** and one with instance data a
**`SubjectRef`**.

### Condition and operators

A **Condition** is the optional fourth element of a rule. It filters which
subject instances the rule applies to. A condition operator is a `$`-prefixed
key inside a Condition (e.g. `$eq`, `$gt`, `$hasRole`). Every implementation
**MUST** support all of the built-in operators defined in this document.

### Builder

A `PolicyBuilder` takes Policy Claims (arbitrary application data, e.g. a JWT or
`{ ownerOf: number[] }`) and produces a `Policy` and/or
`PolicyDefinition`.

This document does not define the builder's API surface. Implementations
**SHOULD** follow the pattern used by the existing implementations.

### Policy

A `Policy` is the object that evaluates checks (`can`/`cannot`/`require`). It
is constructed from a `PolicyDefinition` or directly by a `PolicyBuilder`.

### Rule

A rule combines an **Effect** (`allow` or `deny`), an Action, a Subject, and
optionally a Condition. For example, `[allow, Update, Article, { ownerId: 4 }]`
reads in English as "allow the user to Update an Article when the article's
`ownerId` is 4."

## Definition Structure

```yaml
version: "0.1"                        # required, SemVer string
name: string                          # optional, informational only
description: string                   # optional, informational only

meta: # optional
  anyAction: string | false | null    # optional, defaults to "_ANY_"
  anySubject: string | false | null   # optional, defaults to "_ANY_"
  actions: string[]                   # optional catalog
  subjects: string[]                  # optional catalog
  operators: string[]                 # optional catalog
  application: any                    # optional, opaque application data

rules:
  - [ Effect, Action, Subject, Condition? ]

tests: # optional
  - name: string
    description: string               # optional
    cases:
      - name: string                  # optional
        check: [ Action, Subject, SubjectClaims? ]
        expected: boolean
```

### Envelope

- `version` — **REQUIRED**. A SemVer string (`MAJOR.MINOR.PATCH`).
    - `PATCH` **MAY** be omitted from the version string (e.g. `"0.1"`). It
      has no effect on compatibility either way.
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
    - Serializing a `PolicyDefinition` obtained from an existing `Policy` (e.g.
      `Policy.toDefinition()`/`def()`), by contrast, **MUST** preserve that
      input definition's own `version` rather than overwrite it with the
      implementation's. A `Policy` round-tripped back to a definition **MUST
      NOT** claim a different version than the document it was constructed
      from.
- `name` — **OPTIONAL**. A human-readable name for the policy. Informational
  only; it plays no role in evaluation.
- `description` — **OPTIONAL**. A human-readable description of the policy.
  Informational only.

### Meta

`meta` is an **OPTIONAL** object that holds the policy's wildcard tokens, the
catalogs of Actions, Subjects, and custom operators its rules may use, and
opaque application data.

#### `meta.anyAction` / `meta.anySubject`

- The wildcard tokens for the policy's Action and Subject positions,
  respectively. When either is not declared, implementations **MUST** use the
  literal string `"_ANY_"`.
- A policy **MAY** override either to a different string, and **MAY**
  declare one without the other.
- Either **MAY** instead be explicitly set to `null` **or** `false` to disable
  the wildcard mechanism for that position entirely. When disabled, every rule's
  `Action` (or `Subject`) **MUST** be matched literally, and `"_ANY_"` becomes an
  ordinary name.
- A declared value that is not a string, `null`, or `false` (e.g. `true`, a
  number, a list) is invalid. Implementations **SHOULD** throw a `PolicyLoadException`
  immediately upon encountering one, rather than silently coercing it or passing
  it through as a raw value to be compared against later.
- Whatever string is currently `meta.anyAction`'s (or `meta.anySubject`'s)
  effective value **MUST NOT** be used as an ordinary action (or subject) name
  within that policy, because a rule could not distinguish that literal name
  from the wildcard.

#### `meta.actions` / `meta.subjects`

- Optional catalogs of the action names and subject names this policy's rules
  may use.
- When a catalog is declared, implementations **SHOULD** throw a
  `PolicyLoadException` when loading a `Policy` if a rule's action is neither the
  effective `anyAction` nor listed in `meta.actions` (and likewise for subjects
  and `meta.subjects`).
- Each entry **MUST** be unique.
- The wildcard token **SHOULD NOT** be listed in the catalog.
- A name listed in the catalog that no rule actually uses **SHOULD NOT** be
  treated as an issue. These catalogs describe the vocabulary a policy is
  allowed to use, not a requirement that every entry be exercised. For example,
  a superuser's policy consisting only of `[allow, _ANY_, _ANY_]` is valid even
  if `meta.actions`/`meta.subjects` list many names that rule doesn't mention.
  Validation only checks that rules don't reference undeclared names.
- Implementations **MAY** trim a declared catalog down to only the subset of
  names a policy's rules actually use (e.g. when a tool regenerates or
  re-serializes a `PolicyDefinition`).

#### `meta.operators`

- A catalog of the custom `$`-prefixed condition operator names (e.g.
  `"$hasRole"`) this policy's rules use.
- When declared, implementations **SHOULD** check every rule's `Condition` when
  loading a `Policy` and throw a `PolicyLoadException` if a rule references a
  custom operator not listed here.
- Declaring an operator here does not implement it. `PolicyDefinition` is JSON
  encodable and cannot carry executable code, so the host application **MUST**
  supply the operator's behavior separately, through whatever
  operator-registration mechanism the implementation provides.
- Every operator listed here **MUST** be registered when the `Policy` is
  constructed. If one is not, the implementation **MUST** throw a
  `PolicyLoadException` at construction, even if no rule uses it.
- Each entry **MUST** be unique and **MUST NOT** name a built-in operator.
- A policy **SHOULD** include `meta.operators` whenever any rule uses a custom
  operator.
- Implementations **MAY** trim a declared catalog as described for
  `meta.actions`/`meta.subjects`.

#### `meta.application`

- An open slot for a host application to embed its own custom data in the
  `PolicyDefinition` — this spec imposes no shape on it and gives it no meaning.
- Implementations **MAY** expose `meta.application` back to the application
  (e.g. via `def()`/`toDefinition()`), but **MUST NOT** raise an error or
  otherwise reject a definition merely because `meta.application` is present,
  regardless of its shape or contents.

### Rules

- `rules` **MUST** be present and **MAY** be an empty array (an empty policy —
  `rules: []` — is structurally valid and denies everything).
- It is a single, **ordered** list. An implementation **MUST NOT** treat
  `rules` as an unordered set.
- Each entry is a tuple of `[Effect, Action, Subject, Condition?]`.
    - `Effect` **MUST** be the literal string `"allow"` or `"deny"`.
    - `Action` and `Subject` **MUST** be strings.
    - `Condition` is optional; a three-element tuple
      `[Effect, Action, Subject]` is an unconditional rule.
- A rule wildcarded on both sides (its `Action` is the effective `anyAction`
  **and** its `Subject` is the effective `anySubject`) **MUST NOT** carry a
  `Condition`. `new Policy(...)` **MUST** throw a `PolicyLoadException` for such
  a rule, and a `PolicyBuilder`'s `allow()`/`deny()` (or equivalent) **MUST**
  throw an argument error as soon as it is called with one.
- A rule wildcarded on only one side **MAY** carry a `Condition`. With a concrete
  subject (`[effect, anyAction, Article, Condition]`), the `Condition` is
  evaluated against that one subject type's data. With a wildcard subject
  (`[effect, Update, anySubject, Condition]`), the rule is valid but **SHOULD
  NOT** be used: the `Condition` may be evaluated against many unrelated subject
  shapes, and those that lack its fields fail to match silently rather than
  raising an error.
- Conditions evaluate against the *subject's* data, not claims about the caller.
  An "admins can do anything" rule therefore can't be written as a
  both-sides-wildcarded rule with a role condition. Instead, generate a
  different `PolicyDefinition` for admins when building the policy.

### Tests

`tests` is an **OPTIONAL** top-level field that embeds test cases directly in a
`PolicyDefinition`, so a policy's expected `can` outcomes travel with the
policy. Tooling can then verify a policy's intended behavior without the same
unit tests being duplicated in every implementation language.

```yaml
tests:
  - name: string                      # required — the suite's name
    description: string               # optional
    cases:
      - name: string                  # optional — the case's name
        check: [ Action, Subject, SubjectClaims? ]
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
    - `check` — **REQUIRED**. A tuple `[ Action, Subject ]` or
      `[ Action, Subject, SubjectClaims ]`.
        - A two-element `check` **MUST** be evaluated as a check without
          instance data (a `SubjectDef`).
        - A three-element `check` **MUST** be evaluated with the third element
          as the subject's instance data (a `SubjectRef`).
    - `expected` — **REQUIRED**. A boolean: the expected result of calling
      `can` with the given `check`.

## Operators

A `Condition` filters *when* a rule applies. It is evaluated against the
subject's instance data.

### General requirements

- A condition that cannot be meaningfully evaluated against the given subject
  (e.g. a type mismatch) **SHOULD** evaluate to `false`. Implementations **MAY**
  log a diagnostic to alert the developer.
- Implementations **SHOULD NOT** convert values between types when evaluating a
  condition (`$substr` is the one exception). Implementations **MAY** log a
  diagnostic when a type mismatch is detected.
- A condition that is itself a string, number, boolean, or `null` **MUST** be
  treated as shorthand for `{ $eq: <that value> }`:

```yaml
{ status: archived }         # same as { status: { $eq: archived } }
```

### Missing fields vs. explicit `null`

These are two different things and **MUST** be distinguished:

- **Missing field** — the subject is an object/map that does not have the key at
  all (or the subject isn't an object/map-like value in the first place). The
  *entire field condition* evaluates to `false`, whatever is nested inside it,
  with one exception: when the field's condition is exactly `{ $ne: value }`,
  it evaluates to `true` (see `$ne`). Other negations, such as
  `{ field: { $not: { $eq: value } } }`, still evaluate to `false` on a missing
  field.
- **Explicit `null`** — the subject has the key, and its value is `null`. This
  is a real value and is compared like any other: `{ field: null }` matches only
  when `subject.field` is `null`; `{ field: { $ne: null } }` matches whenever
  `subject.field` is anything other than `null`, including when it is
  missing.
- `null` **MUST NOT** be treated as a wildcard that matches anything.

### Built-in operators

Every operator below receives the *current* subject value at its point in the
condition tree: initially the subject's full instance data, narrowed by each
enclosing field condition.

#### `$eq`

`{ $eq: value }`. Matches when `subject` equals `value`.

**Requirements:**

- Equality **MUST** use value equality (not reference/identity equality) for
  primitives.
- `NaN` **MUST NOT** equal anything, including itself, even where the host
  language's default equality says otherwise (e.g. Java's `Double.equals`
  treats `NaN` as equal to `NaN`).
- Behavior when either side is an array or object is not defined in v0.
  Policies **SHOULD** compare only strings, numbers, booleans, and `null`.

#### `$ne`

`{ $ne: value }`. Matches when `subject` does not equal `value`.

**Requirements:**

- **MUST** be the exact negation of `$eq` for the same `subject`/`value` pair,
  including when the field being tested is missing: because `$eq` on a missing
  field is `false`, `$ne` on a missing field **MUST** be `true`.

#### `$gt` / `$gte` / `$lt` / `$lte`

`{ $gt: number }` (and `$gte`/`$lt`/`$lte` identically shaped). Numeric
comparison.

**Requirements:**

- If the subject or the value is not a number, the condition **MUST** evaluate
  to `false`.
- Any comparison involving `NaN` **MUST** evaluate to `false`.

#### `$in`

`{ $in: value[] }`. Matches when the array `value` contains `subject`.

**Requirements:**

- If `value` is not an array, the condition **MUST** evaluate to `false`.
- Containment **MUST** use the same equality semantics as `$eq` per element.

#### `$has`

`{ $has: value }`. Matches when the array `subject` contains `value`.

**Requirements:**

- If `subject` is not an array, the condition **MUST** evaluate to `false`.
- Containment **MUST** use the same equality semantics as `$eq` per element.

#### `$substr`

`{ $substr: pattern }`. Matches when the subject, as a string, contains a
substring described by `pattern`. Matching is case-sensitive.

`pattern` is a string built from literal characters plus these special tokens:

Token `^`
: when it is the first character, anchors the match to the **start** of the
subject string.

Token `$`
: when it is the last character, anchors the match to the **end** of the
subject string.

Token `*`
: matches **zero or more** characters.

Token `\`
: escapes the next character, making it literal. For example, `\^`, `\$`,
`\*`, `\\`, and `\e` are the literals `^`, `$`, `*`, `\`, and `e`. A trailing
`\` with nothing after it **MUST** be ignored.

**Requirements:**

- A `null` or missing subject is an ordinary non-match: the condition evaluates
  to `false`.
- Any other non-string subject is matched against its string form. This is the
  one exception to the no-conversion rule. Because string forms can differ
  between languages, policies **SHOULD** apply `$substr` only to string fields.
- If `pattern` is not a string, or it contains an unescaped `^` anywhere but the
  start or an unescaped `$` anywhere but the end, the pattern is malformed and
  the condition **MUST** evaluate to `false`.

#### `$or`

`{ $or: Condition[] }`. Matches when at least one sub-condition matches.

**Requirements:**

- If the operand is not an array, the condition **MUST** evaluate to `false`.
- `{ $or: [] }` **MUST** evaluate to `false` (no alternative can be
  satisfied).

#### `$and`

`{ $and: Condition[] }`. Matches when every sub-condition matches.

**Requirements:**

- If the operand is not an array, the condition **MUST** evaluate to `false`.
- `{ $and: [] }` **MUST** evaluate to `true` (there is no unsatisfied
  conjunct).

#### `$not`

`{ $not: Condition }`. Matches when the sub-condition does **not** match.

**Requirements:**

- **MUST** be the exact negation of evaluating `Condition` against the same
  `subject`.

#### Field condition

`{ fieldName: Condition }` — any object key that does not start with `$`.

**Requirements:**

- **MUST** narrow the subject to `subject[fieldName]` and evaluate
  `Condition` against that narrowed value.
- When `fieldName` is missing from `subject`, the condition **MUST** evaluate to
  `false` (except for a bare `$ne`; see Missing fields vs. explicit `null`).
- In v0, field conditions reach only the subject's top-level fields. The
  `Condition` under a field key **MUST NOT** itself narrow into another field
  (so `{ author: { name: "Alice" } }` is not supported); if it does, the
  condition **MUST** evaluate to `false`. Operators such as `$ne`, `$or`, or
  `$gt` may still be used on the field's own value.

#### `$field` (explicit field access)

`{ $field: [name, Condition] }`. Equivalent to the bare-key field condition, but
the field name is given as a tuple element instead of as the object key.

**Requirements:**

- A field whose name starts with `$` **MUST** be accessed with `$field`, because
  a `$`-prefixed object key is always treated as an operator.
- The same top-level-only restriction applies as for a bare-key field condition.
- If the operand is not a two-element `[name, Condition]` array, the condition
  **MUST** evaluate to `false`.

### Custom operators (`$op`)

`{ $op: value }`, where `$op` is not a built-in operator.

The implementation delegates to an operator the host application registered when
constructing the `Policy`, through whatever registration mechanism it provides.
A custom operator receives the same `(subject, value)` pair a built-in does, plus
a context offering something equivalent to
`resolveSubcondition(subject, condition)`. This lets a custom operator recurse
into the condition language (e.g. to implement its own `$and`-like combinator)
the same way the built-in `$and`/`$or`/`$not` do.

**Requirements:**

- A `$`-prefixed key is always an operator, never a field name.
- An operator that is neither registered nor listed in `meta.operators`
  **MUST** evaluate to `false`. (One that is listed but not registered is a
  construction-time error; see `meta.operators`.)

### Multi-key condition objects

A condition object **MAY** contain more than one key. Every key in a condition
object **MUST** be evaluated, and the object matches only if all of them do.
Keys **MUST** be implicitly ANDed together, whether they are operators, field
names, or a mix of both:

```yaml
{ $ne: null, status: "open" }   # subject is not null, AND subject.status == "open"
```

- An operator key (`$eq`, `$gt`, `$or`, ...) **MUST NOT** "consume" the whole
  object or cause sibling keys to be ignored.

## Evaluating a check

### `can` / `cannot` / `require`

The last matching rule wins:

```js
function can (action, subject) {
  for (const [effect, ruleAction, ruleSubject, ruleCondition] of reverse(rules)) {
    if (!matchesAction(action, ruleAction)) continue
    if (!matchesSubject(subject.name, ruleSubject)) continue

    if (ruleCondition) {
      if (!hasInstance(subject)) continue
      if (!evaluate(getInstance(subject), ruleCondition)) continue
    }

    return effect == 'allow'
  }

  return false // nothing matched: default deny
}

function matchesAction (action, ruleAction) {
  return action == ruleAction || ruleAction == effectiveAnyAction(meta)
}

function matchesSubject (subjectName, ruleSubject) {
  return subjectName == ruleSubject || ruleSubject == effectiveAnySubject(meta)
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
`"_any_"` **MUST NOT** be treated as the wildcard. Field names inside conditions
are likewise matched exactly as written.

The `subject` argument can take three shapes:

- A bare string (`"Article"`). Implementations **MAY** support this; it is
  checked without instance data.
- A `SubjectDef` (a subject type with no instance). Implementations **MUST**
  support this; it is checked without instance data.
- A `SubjectRef` (a subject type plus a wrapped instance). Implementations
  **MUST** support this. `hasInstance` above is `true` only for this shape.

A check without instance data (a bare string or `SubjectDef`) still runs the
algorithm above, but it can never satisfy a rule that carries a `Condition`,
because `hasInstance(subject)` is `false`:

- `can("Update", "Article")` against the rule
  `[allow, Update, Article, { owner_id: 1 }]` is `false` (assuming no other rule
  matches). A condition that can't be checked without instance data counts as
  "does not match," not as an error or an automatic pass.
- However an implementation represents a `SubjectDef` internally, it **MUST
  NOT** expose fields that could make an ordinary condition (on `owner_id`,
  `status`, etc.) match by accident.

`cannot(action, subject)` **MUST** return the exact negation of
`can(action, subject)`. `require(action, subject)` **MUST** perform the same
evaluation as `can`, but instead of returning a boolean it returns normally when
the result is `true` and signals failure (e.g. by throwing) when it is `false`.
The exact error type is up to the implementation.

An implementation **MAY** implement this algorithm differently (an index by
action/subject, a compiled decision structure, etc.) as long as its observable
`can`/`cannot`/`require` results are identical to this reverse scan for every
input. Every implementation **MUST** verify that equivalence against the shared
conformance fixtures (`test/fixtures` in the KeyCard repository), not by
inspection alone.

Every v0-conformant implementation **MUST** satisfy these requirements:

1. **Default deny.** If the scan reaches the front of `rules` without a single
   match, the result **MUST** be `false`. An empty policy (`rules: []`) denies
   everything.
2. **Last-matching-rule-wins.** An implementation **MUST** scan `rules` from the
   last-declared entry back to the first, and **MUST** return as soon as it
   finds a rule whose action, subject, and (if present) condition all match.
   That rule's effect (`allow` or `deny`) *is* the answer; every earlier rule,
   however specific or unconditional, is irrelevant. There is no "any deny beats
   any allow" veto and no combining of several matching rules' effects: exactly
   one rule decides the outcome, or none does and default deny applies.
    - A rule with no `Condition` allows or denies every instance of its
      action/subject combination, *unless a rule declared after it also
      matches*. A later, more specific rule for the same action/subject can
      reopen or reclose it. To make a blanket rule final, declare it after the
      rules it should override.
    - Each rule's condition is evaluated independently against the same subject.
      Matching rules are not combined; only the last-declared one decides.
    - Policy authors **SHOULD** place general rules first and the rules that
      override them later, including a later `allow` that reopens something an
      earlier `deny` closed. Reordering two overlapping rules can flip `can`'s
      answer. Implementation documentation **SHOULD** explain this convention.
    - Implementations **MAY** warn about a blanket rule (no `Condition`) that is
      declared after other rules for the same action/subject, since it makes
      those earlier rules unreachable.
3. **Order is significant.** Implementations, builders, and parsers **MUST**
   preserve declaration order end to end: moving a rule can change `can`'s
   answer for the cases it overlaps with.
4. **Wildcard matching is policy-scoped.** `matchesAction`/`matchesSubject`
   use *this policy's own* effective `anyAction`/`anySubject`; there is no
   global wildcard token.
    - Both default to `"_ANY_"`, so `[allow, _ANY_, _ANY_]` is a valid rule
      matching every action on every subject, even in a policy with no `meta`.
      `[allow, _ANY_, Article]` matches every action on `Article`;
      `[allow, Delete, _ANY_]` matches `Delete` on every subject. Wildcards are
      plain string comparisons, not regex or glob patterns.
    - Either can be disabled by setting it to `null` or `false`. For a disabled
      position, no string has wildcard meaning, and `"_ANY_"` becomes an
      ordinary name.
5. **A rule wildcarded on both sides MUST be unconditional.** See Rules for the
   exact requirement and the errors implementations must raise.

### Running `tests`

- An implementation that can run a `PolicyDefinition`'s embedded `tests`
  (whatever it calls that operation, and whatever its report looks like)
  **MUST**, for each case, call `can` with that case's `check` elements as
  arguments, and **MUST** report the case as passing if and only if the result
  equals `expected`.
- Running `tests` **MUST NOT** mutate the `Policy` instance or otherwise affect
  its subsequent `can`/`cannot`/`require` behavior.
- This spec does not require a report format or a CLI, and it does not require
  `new Policy(...)` to reject an otherwise-valid `PolicyDefinition` because one
  of its test cases fails. Whether a failing case blocks construction, a build,
  or a CI job is up to the application or tooling.

## Prior work

KeyCard's condition language and rule-based `allow`/`deny` model draw on
[CASL](https://casl.js.org/), a JavaScript authorization library. In particular,
the last-matching-rule-wins combining behavior mirrors CASL's own approach to
resolving overlapping rules. KeyCard differs from CASL in scope: a declarative,
JSON/YAML-serializable `PolicyDefinition`, produced server-side and evaluated in
any language, rather than an in-process JavaScript ability builder. It also
differs in the details: the condition operators, the `meta` catalogs and
wildcard tokens, and the validation and exception behavior. This document
defines KeyCard's behavior in full and does not assume familiarity with CASL.

## Appendix: worked example

```yaml
version: "0.1"
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

The first rule uses the default subject wildcard, `_ANY_`, because this policy
declares no `meta.anySubject`. A rule wildcarded on only one side may carry a
condition, but this one doesn't need one.

The last rule is declared last on purpose. It is an `allow` whose condition
(`$hasRole: admin`) can still be satisfied for an archived article, so an admin
can delete an archived article even though the `deny` rule before it would
otherwise block that. This is last-rule-wins overriding an earlier rule.

`$hasRole` isn't a built-in operator. It is a custom operator that this policy
declares in `meta.operators`, so the host application must register it when
constructing the `Policy` (e.g. one that checks
`subject.roles.includes("admin")`). If the application forgets,
`new Policy(...)` throws a `PolicyLoadException` immediately, rather than every
admin-only check quietly failing later. Because conditions only see subject
data, the application must include `roles` in the Subject Claims it passes to
the check.

The `tests` block records this policy's intent as executable cases. The
"ownership" suite exercises the third and fourth rules, and the "archived
articles" suite pins down the last-rule-wins interaction described above, so an
edit that reorders or narrows those rules is caught by re-running `tests`.
