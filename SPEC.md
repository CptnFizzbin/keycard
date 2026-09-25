KeyCard Spec
============

- Strongly inspired by CASL.js
- TypeSafe, Cross-Language
- Define server-side, resolve client-side

> This is an informal overview. For definitions of Claims, Action, Subject,
> PolicyBuilder, Rule, PolicyDefinition, and Policy, see
> [`GLOSSARY.md`](GLOSSARY.md). For the normative v0 specification — exact
> rule-evaluation semantics, the full condition-operator table, and required
> edge-case behavior — see [`SPEC_V0.md`](website/spec/SPEC_V0.md).

Builder
-------

- Methods:
  - allow(action, subject, conditions?) => Builder
  - deny(action, subject, conditions?) => Builder
  - build() => Policy
  - buildDef() => PolicyDefinition

Policy Definition
-----------------

- created by a builder, or by hand
- DTO that contains all the rules created by the builder
- used to create a usable policy
- JSON encodable

example:

```yaml
version: "0.1" # KeyCard policy spec version (SemVer)
meta:
  actions: [Create, Update, Delete]
  subjects: [Article]
rules:
  - [allow, Create, Article] # Allowed to create any article
  - [allow, Update, Article, { owner_id: 1 }] # Allowed to update articles they own
  - [deny, Delete, Article, { status: "archived" }] # Not allowed to delete archived articles
```

`rules` is a single, order-significant list: the *last* rule that matches
an action/subject/condition wins, not "any deny beats any allow." Every
policy has a default wildcard token, `_ANY_`, for both actions and
subjects (e.g. `[allow, _ANY_, _ANY_]` matches anything); a policy MAY
override either via `meta.anyAction`/`meta.anySubject`, or disable it with
`null` or `false`. See
[`SPEC_V0.md`](website/spec/SPEC_V0.md) for the exact algorithm, `meta`, and
the wildcards.

Condition
---------

- a filter to apply to a subject to check if the rule should be applied or not

Condition = 
  | EqCondition = TValue | { $eq: TValue } //=> TSubject == TValue
  | NeCondition = { $ne: TValue } //=> TSubject != TValue (also true when the field is missing)
  | GtCondition = { $gt: TValue } //=> TSubject > TValue
  | GteCondition = { $gte: TValue } //=> TSubject >= TValue
  | LtCondition = { $lt: TValue } //=> TSubject < TValue
  | LteCondition = { $lte: TValue } //=> TSubject <= TValue
  | InCondition = { $in: TValue[] } //=> TValue[].contains(TSubject)
  | HasCondition = { $has: TValue } //=> TSubject[].contains(TValue)
  | SubstrCondition = { $substr: TValue } //=> a small non-regex pattern language matches TSubject
  | OrCondition = { $or: Condition[] } //=> Condition[].any(TSubject)
  | AndCondition = { $and: Condition[] } //=> Condition[].all(TSubject)
  | NotCondition = { $not: Condition } //=> !Condition(TSubject)
  | FieldCondition = { [key]: Condition } //=> Condition(TSubject[key]), key MUST NOT start with "$"
  | ExplicitFieldCondition = { $field: [key, Condition] } //=> Condition(TSubject[key]), required when key starts with "$"

`FieldCondition`/`ExplicitFieldCondition` reach only one level deep in v0:
the nested `Condition` they narrow into MUST NOT itself be a field
condition, so `{ status: "archived" }` is valid but `{ author: { name: "Alice" } }`
is not — inspecting a subject's nested fields is out of scope for v0 and is
left for a future version.

See [`SPEC_V0.md`](website/spec/SPEC_V0.md) for full operator semantics,
including `$substr`'s pattern language and the top-level-only field
restriction.

Tests
-----

- a policy document MAY embed its own expected `can` outcomes, so they
  travel with the policy instead of living only in a separate test suite

example:

```yaml
tests:
  - name: "ownership"
    cases:
      - check: [Update, Article, { owner_id: 1 }] # action, subject, subjectClaims?
        expected: true
      - check: [Update, Article, { owner_id: 2 }]
        expected: false
```

`tests` plays no role in evaluation — it's purely for tooling (a CLI, a
test runner, a CI check) to load a `PolicyDefinition` and assert each
case's `expected` outcome against `can(...check)`. See
[`SPEC_V0.md`](website/spec/SPEC_V0.md) for the full field
requirements and for how an implementation that runs `tests` must
behave.

Policy
------

- Methods:
  - new<TActions, TSubjects>(PolicyDefinition, customOperators?) => Policy
  - def() => PolicyDefinition
  - can(TAction, TSubjectName | TSubject) => boolean
  - cannot(TAction, TSubjectName | TSubject) => boolean
  - require(TAction, TSubjectName | TSubject) => void throws PolicyError

`append` is not part of v0.
