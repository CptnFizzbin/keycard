# KeyCard Policy Specification — v1.0.0

Status: Normative for `version` `1.x.x` policy documents (§2).

This document is the authoritative definition of the `PolicyDefinition`
format and its evaluation semantics for the v1 line. `SPEC.md` at the repository
root remains the informal overview; `GLOSSARY.md` holds term definitions. Where
either of those disagrees with this document, this document wins.

The key words **MUST**, **MUST NOT**, **SHOULD**, **SHOULD NOT**, and **MAY**
are to be interpreted as in [RFC 2119](https://www.ietf.org/rfc/rfc2119.txt).
Sections 1 ("Overview"), 2 ("Versioning"), and 3 ("Terminology") are
informational — they describe what things mean, not what an implementation
must do. Every normative requirement lives in §4 ("Definition Structure"),
§5 ("Operators"), or §6 ("Implementations"), attached to the concrete
field, operator, or method it governs.

## 1. Overview

An application (or a test, via whatever YAML/JSON library it chooses)
parses a policy document into a plain `PolicyDefinition` value and hands it
to `Policy.from(...)`; §6.2.1 covers what an implementation must do with
that value from there.

This document defines:

- The shape of a `PolicyDefinition` (§4) and its `version` field (§4.1).
- What an action and a subject are, and how they're matched, including the
  policy-declared wildcard tokens (§4.2.1, §6.2.2).
- The rule-evaluation algorithm — `can`/`cannot`/`require` (§6.2.2).
- The condition language and the evaluation semantics of every operator
  (§5).

It does not define the `PolicyBuilder`'s fluent API, wire format (YAML vs.
JSON), or any language-specific type system — those are implementation
concerns layered on top of this. One exception applies regardless: builder
`allow`/`deny` calls are still bound by the wildcard-condition constraint
in §6.1.1.

## 2. Versioning

`version` is a [SemVer](https://semver.org/) string, `MAJOR.MINOR.PATCH`
(e.g. `"1.0.0"`). This document specifies `1.0.0`. The full field
requirements — what's required, how compatibility is decided, and how
serialization must stamp or preserve it — live in §4.1; this section only
describes what the three components mean:

- **`MAJOR`** identifies a breaking change — one where a document valid and
  meaningful under the old `MAJOR` version could parse differently, mean
  something different, or become invalid under the new one. A
  non-backward-compatible change to a pre-packaged (built-in) operator's
  behavior (§5.4) is a `MAJOR` change for the same reason.
- **`MINOR`** identifies a backward-compatible addition (a new optional
  field, a new operator, a new default) that doesn't change the meaning of
  any document that didn't use the new feature.
- **`PATCH`** identifies changes to this specification document that don't
  affect normative behavior at all (wording clarifications, typo fixes,
  added examples). A bugfix that merely brings a previously-noncompliant
  *implementation* into alignment with already-published normative
  behavior can be released under that implementation's own `PATCH`
  version — it isn't a spec-level change at all, since the corrected
  behavior was already required.

### 2.1 What counts as a `MAJOR`, `MINOR`, or `PATCH` change to this spec

This subsection governs how *this specification* is versioned from release to
release — guidance for spec maintainers, not something an implementation
checks at runtime.

- **`MAJOR`**: any change that could alter the allow/deny outcome for some
  already-valid document under the current `MAJOR` version, or that makes a
  previously-valid document invalid (removing a field, operator, or matching
  guarantee; narrowing what was previously valid; changing a default).
- **`MINOR`**: any purely additive change — a new optional field, a new
  operator, new advisory (SHOULD/MAY) guidance — that cannot alter the
  outcome for any document that doesn't use the new feature.
- **`PATCH`**: wording-only changes with zero effect on any implementation's
  behavior (typo fixes, clarified examples, added cross-references). Since
  `PATCH` carries no compatibility meaning, a policy document's `version`
  field can omit it — `"1.0"` is a valid shorthand for `"1.0.0"`, with
  `PATCH` implicitly `0`.

## 3. Terminology

This section defines KeyCard's core vocabulary without imposing
requirements — the concrete field, operator, or method that gives each
term its teeth is specified in §4, §5, or §6.

### 3.1 Action

An action is a string naming what the caller wants to do (`Read`,
`Create`, `Update`, `MarkDone`, ...). A policy can declare one wildcard
token — `meta.anyAction`, `"_ANY_"` by default — that matches every action
when it appears in a rule's `Action` position, and can restrict the set of
action names its rules are allowed to use via a `meta.actions` catalog.
See §4.2.1 for how the wildcard resolves, §4.2.2 for catalog enforcement,
and §6.2.2 for how actions are matched during evaluation.

### 3.2 Subject

A subject has a **name** (its type, e.g. `Article`, `Comment`, `User`) and
optionally a **value** (a concrete instance's data, e.g.
`{ id: 1, owner_id: 1, status: "published" }`). Subject-name matching and
its wildcard token, `meta.anySubject`, work exactly like their action
counterparts (§3.1, §4.2.1, §4.2.2). §6.2.2 covers the shapes a subject
argument can take and how each interacts with condition evaluation.

### 3.3 Operators

A condition operator is a `$`-prefixed key inside a `Conditions` value
(e.g. `$eq`, `$gt`, `$hasRole`) — either one of the built-ins (§5.4) or a
custom operator registered on the `Policy`/`ConditionResolver` instance at
construction time (§5.5, §4.2.3). The full catalogue, evaluation
mechanics, and each built-in's own requirements are specified in §5.

### 3.4 Builder

A `PolicyBuilder` takes claims (arbitrary application data, e.g. a JWT or
`{ ownerOf: number[] }`) and produces a `Policy` or `PolicyDefinition`.
This spec does not define the builder's fluent API surface (§1) — method
names, chaining shape, and language-specific typing are implementation
concerns. §6.1 covers the behavioral requirements a conforming builder
must still meet.

### 3.5 Policy

A `Policy` is an object used to perform checks (`can`/`cannot`/`require`)
against/with. It is constructed from a `PolicyDefinition` via
`Policy.from(...)` (or an equivalent construction entry point); §6.2
covers the required construction and evaluation behavior.

### 3.6 Rule

A rule is an entry in `rules`: a tuple of `[Effect, Action, Subject,
Conditions?]`, where `Effect` is `"allow"` or `"deny"` and `Conditions` is
optional — a three-element tuple is an **unconditional rule**. §4.3 covers
the structural requirements governing a rule tuple, and §6.2.2 covers how
rules are evaluated.

## 4. Definition Structure

```yaml
version: "1.0.0"                    # required, SemVer string — see §4.1
name: string                        # optional, informational only
description: string                 # optional, informational only

meta: # optional
  anyAction: string | null          # optional, defaults to "_ANY_" — see §4.2.1
  anySubject: string | null         # optional, defaults to "_ANY_" — see §4.2.1
  actions: string[]                 # optional catalog — see §4.2.2
  subjects: string[]                # optional catalog — see §4.2.2
  operators: string[]                # optional catalog — see §4.2.3
  application: any                  # optional, opaque application data — see §4.2.4

rules:
  - [ Effect, Action, Subject, Conditions? ]
```

### 4.1 Envelope

- `version` — **REQUIRED**. A SemVer string (`MAJOR.MINOR.PATCH`; see §2
  for what each component conceptually means).
  - Implementations **MAY** support a different `MAJOR` version than the
    one they primarily target (e.g. a 2.x implementation **MAY** still
    understand 1.x documents). If an implementation does not support a
    document's `MAJOR` version — whether older or newer than what it
    implements — it **MUST** throw a `PolicyVersionException` at
    construction time rather than guess at compatibility.
  - Implementations **MUST** support every `MINOR` version lower than or
    equal to the one they implement, within the same `MAJOR` version — an
    implementation of 1.5.0 **MUST** correctly evaluate a document
    declaring anywhere from 1.0.0 through 1.5.0. A document declaring a
    `MINOR` version the implementation doesn't know yet (higher than what
    it implements, within the same `MAJOR`) **MUST** cause a
    `PolicyVersionException` at construction time. Implementations **MAY**
    provide an option to disable this `MINOR` check (e.g. for a caller
    that wants to accept a document declaring a newer `MINOR` than the
    implementation knows, at its own risk), but this **MUST** be an
    explicit opt-in — the default behavior is the check above.
  - Implementations **MUST** ignore `PATCH` when deciding compatibility —
    `"1.0.0"` and `"1.0.7"` **MUST** be treated identically.
  - Serializing a `PolicyDefinition` that a `PolicyBuilder` assembled from
    scratch **MUST** stamp the builder's own implemented version — not an
    arbitrary or inherited value — since no input document's version
    exists to preserve (e.g. `PolicyBuilder.buildDef()` **MUST** write the
    version the builder itself implements; see §6.1.3). Serializing a
    `PolicyDefinition` obtained from an existing `Policy` (e.g.
    `Policy.toDefinition()`/`def()`), by contrast, **MUST** preserve that
    input definition's own `version` rather than overwrite it with the
    implementation's — a `Policy` round-tripped back to a definition
    **MUST NOT** silently claim a different version than the document it
    was constructed from (see §6.2.1).
- `name` — **OPTIONAL**. A human-readable name for the policy. Informational
  only — it plays no role in evaluation.
- `description` — **OPTIONAL**. A human-readable description of the policy.
  Informational only — it plays no role in evaluation.

### 4.2 Meta

`meta` is an **OPTIONAL** object grouping six independent fields. All of the
following fields are **OPTIONAL** as well.

#### 4.2.1 `meta.anyAction` / `meta.anySubject`

- The wildcard tokens for the policy's Action and Subject positions
  respectively (§3.1, §3.2). Implementations **MUST** default to the
  literal string `"_ANY_"` when not declared.
- A policy **MAY** override either to a different string, and **MAY**
  declare one without the other.
- Either **MAY** instead be explicitly set to `null` **or** `false` to
  disable the wildcard mechanism for that position entirely — the two are
  equivalent. When disabled, every rule's `Action` or `Subject` **MUST** be
  tested literally, including the default wildcard string `"_ANY_"`.
- A declared value that is neither a string, `null`, nor `false` (e.g.
  `true`, a number, a list) is invalid. Implementations **MUST** throw a
  `PolicyLoadException` immediately upon encountering one, rather than
  silently coercing it or passing it through as a raw value to be compared
  against later. This four-way dispatch — absent (defaults to `"_ANY_"`),
  `null`/`false` (disabled), a string (that token), anything else (throw) —
  applies wherever a declared `anyAction`/`anySubject` value is resolved,
  not merely at the point `meta` is first parsed.
- Whatever string is currently `meta.anyAction`'s (or `meta.anySubject`'s)
  effective value — declared or defaulted — **MUST NOT** be used as an
  ordinary action (or subject) name within that policy: a rule meaning the
  literal value equal to that string is indistinguishable from the
  wildcard. This reservation is scoped to the declaring policy: a
  *different* policy that overrides `meta.anyAction`/`meta.anySubject`
  reserves a different string instead, with no conflict. The two positions
  are independent of each other and **MAY** even share the same literal
  string, since actions and subjects are never compared against each
  other.

#### 4.2.2 `meta.actions` / `meta.subjects`

- The full set of action names and subject names this policy's rules use.
  When declared, they are **enforced, not advisory**: when constructing a
  `Policy`, implementations **MUST** throw a `PolicyLoadException` if some
  rule's action isn't `meta.anyAction` and isn't listed in `meta.actions`
  (symmetrically for subjects/`meta.subjects`).
- There is no closed enum of valid action or subject names required by
  this spec beyond that catalog-coverage check: a policy **SHOULD**
  declare its full set via `meta.actions`/`meta.subjects`, and **MUST** do
  so if it wants unlisted names rejected at construction time. Absent a
  declared catalog, any string other than the policy's effective wildcard
  (§4.2.1) is a legal name to check, and one that never appears in any
  rule simply never matches — no error.
- The wildcard token **SHOULD** be excluded from the catalog;
  `meta.actions`/`meta.subjects` are for the concrete vocabulary a policy
  uses, not the wildcard mechanism itself.
- A name listed in the catalog that no rule actually uses **MUST NOT** be
  treated as an issue. These catalogs describe the vocabulary a policy is
  allowed to use, not a requirement that every entry be exercised. For
  example, a superuser's policy consisting only of `[allow, _ANY_, _ANY_]`
  is valid even if `meta.actions`/`meta.subjects` separately enumerate a
  long list of specific names that rule doesn't literally mention (this
  validation only runs in the "rule references an undeclared name"
  direction).
- Implementations **MAY** trim a declared catalog down to only the subset of
  names a policy's rules actually use (e.g. when a tool regenerates or
  re-serializes a `PolicyDefinition`).

#### 4.2.3 `meta.operators`

- A declarative catalog of the custom `$`-prefixed condition operator names
  (e.g. `"$hasRole"`) this policy's rules use.
- When declared, implementations **SHOULD** walk every rule's `Conditions`
  at construction and throw a `PolicyLoadException` immediately if some rule
  references a custom `$op` not listed here (§5.5). Unlike
  §4.2.2's enforcement of a rule's own top-level action/subject, which
  **MUST** be checked — that's a cheap, shallow check against two fields per
  rule — this is a **SHOULD**: eagerly walking every condition tree of
  every rule can be costly for a policy with many, deeply-nested conditions,
  so an implementation **MAY** skip this check — or expose skipping it as an
  explicit opt-out — without becoming non-conformant. Skipping it changes
  nothing at evaluation time: a `$op` a rule actually reaches that isn't
  registered still resolves to `false` per §5.5, whether or not this eager
  check ran.
- Declaring an operator here does not implement it — `PolicyDefinition` is
  JSON encodeable and cannot carry an executable checker. The operator's
  behavior must be supplied separately by the host application, through
  whatever runtime operator-registration mechanism the implementation
  exposes alongside the built-ins (§5.4).
- Beyond the coverage check above, `meta.operators` carries a second,
  stronger requirement: for every name it lists, an operator (built-in or
  custom) **MUST** actually be registered on the `Policy`/
  `ConditionResolver` instance being constructed. Implementations **MUST**
  throw a `PolicyLoadException` at construction time if some declared name
  has nothing registered for it — checked in full immediately, regardless of
  whether any rule actually reaches that name during evaluation (§5.5).
  Because this is checked eagerly at construction, the "cataloged but
  unregistered, and some rule reaches it during evaluation" runtime
  scenario can no longer occur: any `Policy` that successfully constructs
  already has every `meta.operators` name backed by a real registration.
- An operator's `$name` **MUST** be unique among the full set an
  implementation registers for one `Policy`/`ConditionResolver` instance —
  the built-ins (§5.4) plus whatever custom operators the host application
  supplies. Constructing an instance where a custom operator's name collides
  with a built-in, or with another custom operator in the same input,
  **MUST** throw a `PolicyLoadException` immediately; implementations
  **MUST NOT** silently let the later one shadow the earlier.
- A policy **SHOULD** include `meta.operators` whenever any rule uses a
  custom operator — unlike `meta.actions`/`meta.subjects`, this catalog is
  the only place a reader can discover which external checkers the host
  application needs to register, so declaring it matters more here than for
  the other two catalogs.

#### 4.2.4 `meta.application`

- An open slot for a host application to embed its own custom data in the
  `PolicyDefinition` — this spec imposes no shape on it and gives it no
  meaning.
- Implementations **MAY** expose `meta.application` back to the application
  (e.g. via `def()`/`toDefinition()`), but **MUST NOT** raise an error or
  otherwise reject a definition merely because `meta.application` is
  present, regardless of its shape or contents.
- It is exempt from every other `meta` field's rules in this section —
  unlike `meta.actions`/`meta.subjects`/`meta.operators`, it is never
  validated, enforced, or cross-checked against `rules`.

### 4.3 Rules

- `rules` **MUST** be present and **MAY** be an empty array (an empty
  policy — `rules: []` — is structurally valid; see §6.2.2 for why it denies
  everything).
- It is a single, **ordered** list — declaration order is significant
  (§6.2.2) and **MUST** be preserved by any parser or builder; an
  implementation **MUST NOT** treat `rules` as an unordered set.
- Each entry is a tuple of `[Effect, Action, Subject, Conditions?]` (§3.6).
  `Effect` **MUST** be the literal string `"allow"` or `"deny"`.
  `Conditions` is optional; a three-element tuple `[Effect, Action,
  Subject]` is an unconditional rule.
- A rule tuple with a missing `Effect`, `Action`, or `Subject` (fewer than 3
  elements), or an `Effect` that isn't `"allow"`/`"deny"`, is malformed.
  `Policy.from(...)` (or any equivalent construction entry point) **MUST**
  throw a `PolicyLoadException` when given a definition containing a
  malformed rule tuple — it **MUST NOT** silently drop or ignore it.
  Detecting this **MUST** be done during construction of the policy, not
  deferred to evaluation time (§6.2.1).
- A rule whose `Action` is the policy's effective `anyAction` **and** whose
  `Subject` is its effective `anySubject` **MUST NOT** carry a `Conditions`
  element — a rule wildcarded on both sides **MUST** be unconditional.
  `Policy.from(...)` **MUST** throw a `PolicyLoadException` when given a
  definition violating this (§6.2.1), and a `PolicyBuilder`'s `allow()`/
  `deny()` (or equivalent) **MUST** throw an argument-error exception
  immediately when called this way, rather than waiting for eventual
  construction to catch it (§6.1.1). A rule wildcarded on only *one* side
  **MAY** carry a condition; see §6.2.2 property 5 for the full rationale.

## 5. Operators

A `Conditions` value filters *when* a rule applies, evaluated against the
subject's **value** (§3.2) — never against the subject's name.

### 5.1 Evaluation of conditions

- `evaluate(subject, condition)` **MUST** always return a boolean.
- It **SHOULD NOT** throw for any well-formed `condition` value, regardless
  of what `subject` is (including `undefined`/`null`, a primitive, or a
  shape the condition doesn't expect).
- A condition that cannot be meaningfully evaluated against the given
  subject **SHOULD** evaluate to `false`.
- **Type issues are diagnosed, not silenced.** A subset of the false results
  above come from a genuine *type issue* — an operator receiving an operand
  or a subject of the wrong shape for what it does (§5.4 marks exactly which
  operators these are, in each operator's own Requirements list). When
  evaluation hits one of these, the implementation **MUST** write a
  human-readable, error-level diagnostic to the console (or equivalent
  host-language error/logger channel) identifying the operator and what went
  wrong, so the mistake is easy to find — but it still **MUST** resolve that
  condition to `false` and **SHOULD NOT** throw an exception. "Console error
  but no throw" is the required combination: silently returning `false` with
  no diagnostic hides real bugs (a `$gt` compared against a string, a
  malformed `$substr` pattern) as if they were ordinary non-matches;
  throwing would violate the invariant above and take down every `can()`
  call that happens to hit the bad rule, for every request, until someone
  reads a stack trace. A logged, non-fatal diagnostic gets the bug noticed
  without making it load-bearing.
- This diagnostic requirement is distinct from — and **MUST NOT** be
  conflated with — an ordinary non-match that isn't a type issue: a missing
  field (§5.3), an action/subject that simply doesn't match any rule, or an
  unregistered custom `$op` not covered by a declared catalog (§5.5) are not
  malformed input, so they **MUST NOT** produce a console diagnostic on
  their own.
- Implementations **MAY** go further than the per-call diagnostic above: at
  construction time, before any rule is ever evaluated, an implementation
  **MAY** eagerly walk every rule's `Conditions` looking for the same
  structurally-invalid shapes that would otherwise surface as a type issue
  at evaluation time (a non-array `$in`/`$has`/`$or`/`$and` operand, a
  malformed `$substr` pattern, a malformed `$field` tuple, etc.) and throw a
  `PolicyLoadException` immediately upon finding one, rather than waiting
  for evaluation to reach the offending rule. An implementation that doesn't
  perform this eager check **MUST** still honor the evaluation-time
  diagnose-then-`false` behavior above for whatever invalid conditions it
  didn't catch eagerly.

### 5.2 Bare-value shorthand

- A condition that is itself a string, number, boolean, or `null` (not
  wrapped in an operator object) is shorthand for `{ $eq: <that value> }`:

```yaml
{ status: archived }         # same as { status: { $eq: archived } }
```

### 5.3 Missing fields vs. explicit `null`

These are two different things and **MUST** be distinguished:

- **Missing field** — the subject is an object/map that does not have the
  key at all (or the subject isn't an object/map-like value in the first
  place). This makes the *entire field-condition* evaluate to `false`,
  regardless of which operator is nested inside it — **with one exception:
  `$ne` (§5.4.2)**. Because `$ne` **MUST** be the exact negation of `$eq`
  for the same subject/value pair, and a missing field makes `$eq` evaluate
  to `false` (there being no value present to equal anything), `$ne` on a
  missing field **MUST** evaluate to `true` instead — a missing `status`
  genuinely isn't equal to `"archived"`. `{ status: { $eq: "archived" } }`
  against a subject with no `status` key is `false`; `{ status: { $ne:
  "archived" } }` against that same subject is `true`. Every other operator
  keeps the blanket `false` (`$gt`, `$in`, `$has`, `$substr`, and so on
  aren't defined as an exact negation of anything, so they aren't exempted).
  This exception is narrow: it applies when `$ne` is itself the (sole)
  nested condition being evaluated against the missing field, per §5.4.2 —
  it does **NOT** propagate a "the field is missing" signal down into an
  arbitrary condition tree for every operator to interpret on its own (that
  would leak the §5.1 diagnostic into `$gt`/`$in`/`$has`/`$substr` being
  handed a non-number/non-array `undefined`, which §5.1's missing-field
  carve-out explicitly forbids). A `$ne` that is one key among several in a
  multi-key condition object (§5.6) still benefits from this exception for
  its own key, but the object as a whole is still ANDed with its sibling
  keys as normal — a sibling field key nested one level deeper than the
  missing field (e.g. `{ author: { $ne: null, name: "Alice" } }` against a
  subject with no `author` at all) still hits the general
  missing-field-is-`false` rule for that sibling, since there's no object
  there to narrow `name` out of. Either way this is absence, not a type
  issue — it **MUST NOT** trigger the §5.1 console diagnostic.
- **Explicit `null`** — the subject has the key, and its value is `null`.
  This is a real value and is compared like any other: `{ field: null }`
  (bare-value shorthand, §5.2) matches only when `subject.field` is `null`;
  `{ field: { $ne: null } }` matches when `subject.field` is present and is
  anything other than `null`.
- `null` **MUST NOT** be treated as a wildcard that matches anything.

### 5.4 Built-in operators

Every operator below takes the *current* subject value at that point in the
condition tree (initially the subject's full value; narrowed by field
conditions, §5.4.10/§5.6).

#### 5.4.1 `$eq`

`{ $eq: value }`. Matches when `subject === value`.

**Requirements:**

- A bare scalar condition (§5.2) **MUST** be treated as shorthand for `$eq`.
- Equality **MUST** use value equality (not reference/identity equality) for
  primitives.
- No notion of a type mismatch — unequal types are simply unequal, never a
  type issue (§5.1).

#### 5.4.2 `$ne`

`{ $ne: value }`. Matches when `subject !== value`.

**Requirements:**

- **MUST** be the exact negation of `$eq` (§5.4.1) for the same `subject`/
  `value` pair — including when the field being tested is missing (§5.3):
  since a missing field makes `$eq` evaluate to `false`, `$ne` on a missing
  field **MUST** evaluate to `true`, unlike every other operator, which
  keeps §5.3's blanket `false`.
- No notion of a type mismatch, same as `$eq`.

#### 5.4.3 `$gt` / `$gte` / `$lt` / `$lte`

`{ $gt: number }` (and `$gte`/`$lt`/`$lte` identically shaped). Numeric
comparison.

**Requirements:**

- `subject` and the operand **MUST** both be numbers for the comparison to
  proceed.
- If either is not a number, the condition **MUST** evaluate to `false` and
  MUST produce the §5.1 console diagnostic (type issue) — never coerced (no
  numeric-string parsing), never a lexicographic/string comparison.
- Comparison **MUST** use IEEE-754 double semantics. Implementations SHOULD
  ensure `NaN` never equals itself under this family (or under `$eq`/`$ne`)
  even where the host language's default equality would say otherwise (e.g.
  Java's `Double.equals` treats `NaN` as equal to `NaN`; that **MUST NOT**
  leak into `$eq`/`$ne`/`$gt`-family behavior here).

#### 5.4.4 `$in`

`{ $in: value[] }`. Matches when the array `value` contains `subject`.

**Requirements:**

- The operand **MUST** be an array.
- If it is not an array, the condition **MUST** evaluate to `false` and
  **MUST** produce the §5.1 console diagnostic (type issue).
- Containment **MUST** use the same equality semantics as `$eq` per element.

#### 5.4.5 `$has`

`{ $has: value }`. Matches when the array `subject` contains `value`.

**Requirements:**

- `subject` **MUST** be an array.
- If it is not, the condition **MUST** evaluate to `false` and **MUST**
  produce the §5.1 console diagnostic (type issue).

#### 5.4.6 `$substr`

`{ $substr: pattern }`. Matches when `String(subject)` contains a substring
described by `pattern`, a small, deliberately non-regex pattern language so
that every implementation matches identically regardless of host-language
regex engine differences.

`pattern` is a string built from literal characters plus these special
tokens:

Token `^`
: anchors the match to the **start** of the subject string. Only meaningful
as the pattern's first character.

Token `$`
: anchors the match to the **end** of the subject string. Only meaningful as
the pattern's last character.

Token `*`
: matches **zero or more** characters

Token `\`
: escapes the next character, making it literal. For example `\^`, `\$`,
`\*`, `\\`, `\e` are the literals `^`, `$`, `*`, `\`, `e`). A trailing `\`
with nothing following it **MUST** be ignored.

**Requirements:**

- After escape resolution, `pattern` **MUST** be interpreted as a sequence
  of literal segments separated by `*` wildcards, optionally anchored at the
  start (leading unescaped `^`) and/or the end (trailing unescaped `$`).
  `subject` (coerced via `String(subject)`) matches when it can be
  decomposed into those literal segments, each separated by a run of zero or
  more characters wherever a `*` sits between them, with the segment before
  an anchor required to touch that boundary exactly.
- Because `$substr` produces only a boolean result — v1 has no
  capture/extraction feature — a run of consecutive `*` tokens (e.g. `**`)
  is match-equivalent to a single `*`. Implementations **MAY** implement
  `$substr` however they like internally (including compiling it to the
  host language's native regex engine, e.g. translating `*` to `.*` and
  escaping literal segments) as long as the observable match/no-match result
  matches this specification for every subject and pattern — validate
  against the shared conformance suite (§6.2.2) if in doubt.
- An unescaped `^` appearing anywhere other than as the pattern's first
  character, or an unescaped `$` appearing anywhere other than as the
  pattern's last character, is an invalid pattern: the condition **MUST**
  evaluate to `false` and **MUST** produce the §5.1 console diagnostic (type
  issue).
- A `null`/`undefined` subject is an ordinary non-match (`false`), not a
  type issue — it **MUST NOT** itself produce the diagnostic (only a
  structurally invalid pattern does).

#### 5.4.7 `$or`

`{ $or: Condition[] }`. Matches when at least one sub-condition matches.

**Requirements:**

- The operand **MUST** be an array.
- If it is not an array, the condition **MUST** evaluate to `false` and
  **MUST** produce the §5.1 console diagnostic (type issue).
- `{ $or: [] }` **MUST** evaluate to `false` (vacuously — no alternative can
  be satisfied), and **SHOULD** produce the §5.1 diagnostic — an empty `$or`
  is well-formed, but is far more often an authoring mistake (e.g. a
  programmatically-built list that ended up empty) than a deliberate
  always-false condition.

#### 5.4.8 `$and`

`{ $and: Condition[] }`. Matches when every sub-condition matches.

**Requirements:**

- The operand **MUST** be an array.
- If it is not an array, the condition **MUST** evaluate to `false` and
  **MUST** produce the §5.1 console diagnostic (type issue).
- `{ $and: [] }` **MUST** evaluate to `true` (vacuously — there is no
  unsatisfied conjunct), and **SHOULD** produce the §5.1 diagnostic, for the
  same reason as `$or: []` above.

#### 5.4.9 `$not`

`{ $not: Condition }`. Matches when the sub-condition does **not** match.

**Requirements:**

- **MUST** be the exact negation of evaluating `Condition` against the same
  `subject`.
- No notion of a type mismatch — it always has exactly one well-formed
  nested condition to evaluate; any type issue surfaces from that nested
  evaluation itself, not from `$not`.

Across §5.4.1–§5.4.9: any type mismatch **MUST** always resolve to `false`,
with the sole exception of a missing or `null` field (§5.3 — absence is not
a mismatch, and **MUST NOT** itself produce a diagnostic). Every genuine
type mismatch **MUST** display the §5.1 diagnostic message.

#### 5.4.10 Field conditions

`{ fieldName: Condition }` — any object key that does not start with `$`.

**Requirements:**

- **MUST** narrow the subject to `subject[fieldName]` and recursively
  evaluate `Condition` against that narrowed value.
- §5.3 governs what happens when `fieldName` is missing from `subject` — the
  condition evaluates to `false`, and this **MUST NOT** be treated as a type
  issue (no console diagnostic).
- Field conditions **MAY** nest arbitrarily to reach into nested objects:
  `{ author: { name: "Alice" } }` requires `subject.author.name ===
  "Alice"`.
- There is no dot-path or array-index syntax (e.g. `"author.name"` or
  `"tags.0"`) in v1 — reach into nested structures only by nesting field
  conditions. A field name that happens to contain a literal `.` **MUST**
  be matched as a single key, not split into a path.
- Field-name matching is exact and case-sensitive, the same as action and
  subject matching (§6.2.2).

#### 5.4.11 `$field` (explicit field access)

`{ $field: [name, Condition] }`. Equivalent to the bare-key field condition
(§5.4.10), but with the field name given explicitly as a tuple element
instead of as the object key.

**Requirements:**

- Any object key that starts with `$` **MUST** always be treated as an
  operator (built-in or custom), never as a field name — see §5.6. When a
  policy needs to test a subject field whose name itself starts with `$`
  (e.g. a field literally named `$type`), it **MUST** use this long form:
  `{ $field: ["$type", Condition] }`, not `{ $type: Condition }` (which
  means the operator `$type`, per §5.5).
- `name` **MUST** be a string; `Condition` follows the same recursive
  evaluation rules as any nested condition, including §5.3's missing-field
  handling (`subject[name]` missing behaves identically to the bare-key
  form).
- If the operand isn't a well-formed 2-element `[name, Condition]` array
  (wrong length, or `name` not a string), the condition **MUST** evaluate to
  `false` and **MUST** produce the §5.1 console diagnostic (type issue) —
  this is a structurally malformed operand, not an ordinary non-match.

### 5.5 Custom operators (`$op`)

`{ $op: value }`, where `$op` is neither a built-in operator (§5.4.1–
§5.4.11) nor `$field`. Delegates to an operator implementation registered on
the `Policy`/`ConditionResolver` instance at construction time, via
whatever registration mechanism the implementation exposes. A custom
operator receives the same `(subject, value)` pair a built-in does, plus a
resolution context exposing something equivalent to
`resolveSubcondition(subject, condition)` — this is what lets a custom
operator recurse into the condition language (e.g. implementing its own
`$and`-like combinator) exactly the way the built-in `$and`/`$or`/`$not` do.

**Requirements:**

- An unregistered `$op` (no operator registered for it on this instance)
  MUST evaluate to `false`. It **MUST NOT** be treated as a field name
  (field names **MUST NOT** start with `$` — see §5.4.11, §5.6) and **MUST
  NOT** be silently ignored as a no-op `true`.
- When `meta.operators` either isn't declared or doesn't list `$op`, an
  unregistered `$op` is not itself a type issue (§5.1) — an unrecognized
  operator name is a different mistake than a recognized operator given the
  wrong operand type — so this case is exempt from the §5.1 diagnostic
  *requirement*. Implementations **SHOULD** still log a diagnostic warning
  that an unregistered operator was called, so a typo like `$eqq` is easy to
  notice.
- When `$op` *is* listed in `meta.operators`, `Policy.from(...)` has already
  validated — at construction, before any rule is ever evaluated — that an
  operator (built-in or custom) is actually registered for `$op` (§4.2.3,
  unconditional). A "cataloged but nothing registered" `$op` therefore
  **cannot** arise during evaluation — it's a construction-time
  `PolicyLoadException` instead. (Whether a rule's own reference to `$op`
  was itself eagerly checked against the catalog at construction is a
  separate question, governed by the SHOULD-level deep-condition walk of
  §4.2.3, and doesn't affect this guarantee, which comes from the
  registration requirement above alone.)
- Registering two operators — built-in and custom, or two custom operators —
  under the same `$name` on one instance is itself invalid; see §4.2.3.

### 5.6 Multi-key condition objects

A condition object **MAY** contain more than one key. Every key in a
condition object **MUST** be evaluated, and the object matches only if all
of them do. Keys **MUST** be implicitly ANDed together, whether they are
operators, field names, or a mix of both:

```yaml
{ $ne: null, status: "open" }   # subject is not null, AND subject.status == "open"
```

- An operator key (`$eq`, `$gt`, `$or`, ...) **MUST NOT** "consume" the
  whole object or cause sibling keys to be ignored.
- Any key that starts with `$` **MUST** be treated as an operator (built-in
  or custom, §5.4.1–§5.5) not as a field name, regardless of whether that
  operator name is recognized. A field whose name starts with `$` can be
  tested via the explicit `{ $field: [name, Condition] }` long form
  (§5.4.11).

## 6. Implementations

### 6.1 Builder

This spec does not define the `PolicyBuilder`'s fluent API (§1, §3.4) —
method names, chaining shape, and language-specific typing are
implementation concerns. The requirements below are the behavioral
constraints this spec places on whatever shape a conforming builder takes.

#### 6.1.1 `allow` / `deny`

- Adding a rule (however the builder exposes it — e.g. `allow(action,
  subject, conditions?)` / `deny(action, subject, conditions?)`) **MUST**
  validate eagerly that a rule wildcarded on both sides (its action is the
  policy's effective `anyAction` **and** its subject is its effective
  `anySubject`) does not also carry a condition (§4.3).
- When called this way, the builder **MUST** throw an argument-error
  exception immediately — at the point of the call — rather than waiting
  for eventual `build()`/serialization to catch it. This mirrors, but is
  stricter in timing than, the construction-time check `Policy.from(...)`
  performs on an already-assembled definition (§6.2.1).
- A rule wildcarded on only *one* side **MAY** carry a condition — see
  §6.2.2 property 5 for the underlying rationale.

#### 6.1.2 `build`

- Conceptually, a builder operation that produces a usable `Policy` directly
  from the rules accumulated so far, without the caller round-tripping
  through a serialized `PolicyDefinition` first.
- The resulting `Policy` **MUST** behave identically, for every `can`/
  `cannot`/`require` input, to constructing a `Policy` from the equivalent
  `PolicyDefinition` via `Policy.from(...)` (§6.2.1) — a builder is a
  convenience for assembling rules, not a second evaluation semantics.

#### 6.1.3 To definition

- Conceptually, a builder operation that serializes the accumulated rules
  into a `PolicyDefinition` (e.g. `buildDef()`/`toDefinition()`).
- **MUST** stamp the builder's own implemented `version` — not an arbitrary
  or inherited value — since no input document's version exists to preserve
  (§4.1).
- **MUST** preserve declaration order end to end: the order rules were added
  to the builder **MUST** be the order they appear in the resulting
  `rules` array (§4.3, §6.2.2 property 3).

### 6.2 Policy

#### 6.2.1 `from` / to definition

- `Policy.from(...)` (or any equivalent construction entry point) accepts
  an already-parsed `PolicyDefinition` value — implementations **MUST NOT**
  read or write policy files themselves; parsing a document (via whatever
  YAML/JSON library the caller chooses) is the calling application's
  responsibility. `Policy.from(...)` **MUST** perform all of the following
  validation at construction time — never deferred to evaluation time:
  - Reject a `version` whose `MAJOR`/`MINOR` this implementation doesn't
    support, throwing `PolicyVersionException` (§4.1).
  - Reject a malformed rule tuple (missing `Effect`/`Action`/`Subject`, or
    an `Effect` that isn't `"allow"`/`"deny"`), throwing
    `PolicyLoadException` (§4.3).
  - Reject a rule whose action isn't `meta.anyAction` and isn't listed in a
    declared `meta.actions` (symmetrically for subjects/`meta.subjects`),
    throwing `PolicyLoadException` (§4.2.2).
  - Reject a declared `meta.operators` entry with nothing actually
    registered for it, throwing `PolicyLoadException` (§4.2.3), and
    **SHOULD** reject a rule that references a custom `$op` not listed in a
    declared `meta.operators` (§4.2.3).
  - Reject two operators — built-in vs. custom, or two customs — registered
    under the same `$name`, throwing `PolicyLoadException` (§4.2.3).
  - Reject a rule wildcarded on both sides that also carries a `Conditions`
    element, throwing `PolicyLoadException` (§4.3, §6.1.1).
  - Reject an invalid `meta.anyAction`/`meta.anySubject` declaration (not a
    string, `null`, or `false`), throwing `PolicyLoadException` (§4.2.1).
- `Policy.toDefinition()`/`def()` (or equivalent) serializes a constructed
  `Policy` back into a `PolicyDefinition`, and **MUST** preserve that input
  definition's own `version` rather than overwrite it with the
  implementation's own (§4.1) — a `Policy` round-tripped back to a
  definition **MUST NOT** silently claim a different version than the
  document it was constructed from.

#### 6.2.2 `can` / `cannot` / `require`

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
  throw new PolicyLoadException('...') // anything else is invalid (§4.2.1)
}
```

`matchesAction`/`matchesSubject` perform exact, **case-sensitive** string
comparison: the string `"read"` **MUST NOT** match a rule written for
`"Read"`, and if the effective `anyAction` is `"_ANY_"`, the string
`"_any_"` **MUST NOT** be treated as the wildcard. Field names inside
conditions are likewise matched exactly as written (§5.4.10).

`subject` accepts three shapes:

- implementation **MAY** support a bare string (`"Article"`) as the
  subject — treated as an Unconditional Rule check.
- implementation **MUST** support `SubjectDef` (type token, no instance) —
  treated as an Unconditional Rule check.
- implementation **MUST** support `SubjectRef` (type token + wrapped
  instance) — treated as a Conditional Rule check; `hasInstance` above is
  `true` only for this shape.

Checking without instance data (a bare string or `SubjectDef`) still runs
the algorithm above, but can never satisfy a rule that carries a
`Conditions` element, since `hasInstance(subject)` is `false`:

- `can("Update", "Article")` (no instance data) against a rule
  `[allow, Update, Article, { owner_id: 1 }]` is `false` (assuming no other
  rule matches) — the condition can't be satisfied without instance data to
  inspect, and that counts as "does not match," not as an error or an
  automatic pass.
- Whatever a `SubjectDef` check's implementation-defined internal "subject
  value" looks like, it **MUST NOT** expose fields that make ordinary
  domain conditions (`owner_id`, `status`, etc.) accidentally match — a
  condition that inspects real domain fields is expected to evaluate to
  `false` in the absence of real instance data, the same as above.

`cannot(action, subject)` **MUST** return the exact negation of
`can(action, subject)`. `require(action, subject)` **MUST** perform the same
evaluation as `can` but, instead of returning a boolean, resolve
successfully when the result is `true` and signal failure (e.g. by throwing)
when it is `false`; the exact error type/shape is an implementation concern
outside this spec's scope (§1).

An implementation **MAY** implement this algorithm differently (an index by
action/subject, a compiled decision structure, etc...) as long as its
observable `can`/`cannot`/`require` results are identical to this reverse
scan for every input. All Implementations **MUST** validate that
equivalence against a shared conformance test suite (the fixtures under
`test/fixtures/`) rather than by inspection alone.

The requirements that **MUST** hold for any v1-conformant implementation
are:

1. **Default deny.** If the scan reaches the front of `rules` without a
   single match, the result **MUST** be `false`. An empty policy (`rules:
   []`) denies everything.
2. **Last-matching-rule-wins.** An implementation **MUST** scan `rules` from
   the most-recently-declared entry backward to the first, and **MUST**
   return as soon as it finds one rule whose action, subject, and (if
   present) conditions all match. That rule's effect (`allow` or `deny`)
   *is* the answer — every earlier-declared rule, however specific or
   however unconditional, is irrelevant once a later-declared rule also
   matches. There is no independent "any deny beats any allow" veto and no
   AND-combination of multiple matching rules' effects: exactly one rule
   decides the outcome (or none does, and default deny applies).
   - A rule with no fourth tuple element (`condition`) blocks/opens every
     instance of that action/subject combination — *as long as no rule
     declared after it also matches*. Under last-rule-wins, a blanket rule
     is not immune to being reopened or reclosed by a later, more specific
     rule for the same action/subject; if that's not the intent, the
     blanket rule **MUST** be declared last among the rules it's meant to
     override. Implementations **MAY** print a warning to the console for
     either of two suspicious rule-ordering patterns: a blanket **allow**
     rule declared *before* other rules that could match the same
     action/subject, or a blanket **deny** rule declared *after* a run of
     other rules for the same action/subject.
   - Each rule's own condition is evaluated independently against the same
     subject value, but only the *last-declared* rule among those whose
     conditions are satisfied decides the outcome — the two matching rules
     are not combined or ANDed together, and it is not enough for a deny
     rule to simply have a matching condition somewhere earlier in the list.
   - Listing two rules that could both match the same action, subject, and
     instance is not harmless — whichever of them is declared *later* in
     `rules` is the one that decides the outcome, full stop, even if an
     earlier rule looks more specific. General rules **MUST** be placed
     first, with any rule meant to override them declared strictly later in
     the array; reordering two overlapping rules can silently flip `can`'s
     answer. Implementation documentation **SHOULD** encourage this
     "general rules first, specific rules later" convention explicitly.
3. **Order is significant.** Order **MUST** be preserved. Because the
   algorithm is a reverse scan for the first match, the position of a rule
   relative to the others that could match the same action/subject is
   meaningful: moving a rule later in `rules` can change `can`'s answer for
   cases it overlaps with. An implementation, a builder, and a parser all
   **MUST** preserve declaration order end to end — see §4.3, §6.1.3.
4. **Wildcard matching is policy-scoped.** `matchesAction`/`matchesSubject`
   consult *this policy's own* effective `anyAction`/`anySubject` (§3.1,
   §3.2, §4.2.1) — there is no wildcard token independent of what a given
   policy declares or defaults to.
   - `meta.anyAction`/`meta.anySubject` each default to `"_ANY_"` when not
     declared, so `[allow, _ANY_, _ANY_]` (unconditional) is always a valid
     rule matching every action on every subject name, even in a policy
     that declares no `meta` at all. `[allow, _ANY_, Article]` matches
     every action on `Article`; `[allow, Delete, _ANY_]` matches `Delete`
     on every subject name. Wildcards are resolved purely by string
     comparison against the policy's effective `anyAction`/`anySubject` —
     they are not regex or glob patterns.
   - Either **MAY** be explicitly disabled by setting `meta.anyAction`/
     `meta.anySubject` to `null` (§4.2.1); in a policy that disables one, no
     string carries wildcard meaning for that position, including
     `"_ANY_"` itself, and it becomes a legal, ordinary literal name.
5. **A rule wildcarded on both sides MUST be unconditional.** A rule whose
   `Action` is the policy's effective `anyAction` **and** whose `Subject` is
   its effective `anySubject` **MUST NOT** carry a `Conditions` element
   (§4.3). `Policy.from(...)` **MUST** throw a `PolicyLoadException` when
   given a definition violating this (§6.2.1); a `PolicyBuilder`'s
   `allow()`/`deny()` methods (or equivalent) **MUST** throw an
   argument-error exception immediately when called this way (§6.1.1),
   rather than waiting for eventual construction to catch it. A rule
   wildcarded on only *one* side **MAY** carry a condition: `[effect,
   anyAction, Subject, Conditions]` (wildcard action, concrete subject) is
   unrestricted, since `Conditions` evaluates against that
   concretely-known subject type's data; `[effect, Action, anySubject,
   Conditions]` (concrete action, wildcard subject) is valid but **SHOULD
   NOT** be used, since `Conditions` may then be evaluated against subjects
   of many unrelated shapes and silently fail to match some of them via
   §5.3's missing-field handling, rather than failing loudly. Conditions in
   KeyCard evaluate against the *target subject's* data (§5), not against
   claims about the caller — an "admins can do anything" rule still isn't
   expressible as a doubly-wildcarded rule with a role condition attached;
   model it as a per-action rule with a condition instead (see the worked
   example at the end of this document), or select an entirely different
   `PolicyDefinition` at the application layer based on the caller's
   claims.

Broad rules are typically declared first, and later, more specific rules
override them for the cases they cover — including a later `allow`
reopening something an earlier `deny` closed. Because only one rule ever
decides the outcome, implementation documentation **SHOULD** encourage this
convention explicitly.

## 7. Prior work

KeyCard's condition language and rule-based `allow`/`deny` model draw on
[CASL](https://casl.js.org/), a JavaScript authorization library. In
particular, the last-matching-rule-wins combining behavior in §6.2.2 mirrors
CASL's own approach to resolving overlapping rules. KeyCard departs from
CASL in scope (a declarative, JSON/YAML-serializable `PolicyDefinition`
meant to be produced server-side and evaluated in any language, rather than
an in-process JavaScript ability builder) and in specifics (the condition
operator set in §5, the `meta` catalogs and wildcard tokens in §3, §4.2,
and the validation/exception behavior throughout §4 and §6) — this document
defines KeyCard's own behavior in full; familiarity with CASL is not
assumed anywhere above.

## Appendix: worked example

```yaml
version: "1.0.0"
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
```

The first rule uses this policy's default wildcard-subject token, `_ANY_`
(§4.2.1) — no `meta.anySubject` override is declared, so `_ANY_` is what
`matchesSubject` checks for; a rule wildcarded on only one side (here, the
subject) **MAY** carry a condition, but this one doesn't need to, so it's
unconditional.

The last rule is declared last on purpose: because it's an `allow` and its
condition (`$hasRole: admin`) can still be satisfied for an archived
article, an admin can delete an archived article even though the `deny`
rule above would otherwise block it — demonstrating last-rule-wins (§6.2.2)
overriding an earlier, more specific-looking rule. `$hasRole` isn't a
built-in operator (§5.5) — it's a custom one this policy declares in
`meta.operators` and requires the host application to have registered a
checker for at `Policy` construction time (e.g. one that checks
`subject.roles.includes("admin")`); per §4.2.3, if the application forgot
to register it, `Policy.from(...)` throws a `PolicyLoadException`
immediately, rather than silently letting every
admin-only check fail closed with no clue why once evaluation eventually
reached it. Note this rule is wildcarded on *neither* side (it names both a
concrete action, `Delete`, and a concrete subject, `Article`), so it isn't
subject to §6.2.2 property 5's both-sides-wildcarded restriction regardless.
