KeyCard Spec
============

- Strongly inspired by CASL.js
- TypeSafe, Cross-Language
- Define server-side, resolve client-side

> This is an informal overview. For term definitions, see
> [`GLOSSARY.md`](GLOSSARY.md). For the normative v1 specification — exact
> rule-evaluation semantics, the full condition-operator table, and a
> catalogue of required edge-case behavior — see
> [`SPEC_V1-0-0.md`](SPEC_V1-0-0.md).

See [`GLOSSARY.md`](GLOSSARY.md) for definitions of Claims, Action,
Subject, PolicyBuilder, Rule, PolicyDefinition, and Policy.

Builder
-------

- Methods:
  - allow(action, subject, conditions?) => Builder
  - deny(action, subject, conditions?) => Builder
  - build() => Policy
  - buildDef() => PolicyDef

Policy Definition
-----------------

- created by a builder, or by hand
- DTO that contains all the rules created by the builder
- used to create a useable policy
- JSON encodeable

example:

```yaml
version: "1.0.0" # KeyCard policy spec version (SemVer)
meta:
  actions: [Create, Update, Delete]
  subjects: [Article]
rules:
  - [allow, Create, Article] # Allow to create any article
  - [allow, Update, Article, { owner_id: 1 }] # Allowed to update articles they own
  - [deny, Delete, Article, { status: { $not: "archived" } }] # Not allowed to delete archived articles
```

`rules` is a single, order-significant list: the *last* rule that matches
an action/subject/condition wins, not "any deny beats any allow." Every
policy has a default wildcard token, `_ANY_`, for both actions and
subjects (e.g. `[allow, _ANY_, _ANY_]` matches anything); a policy MAY
override either via `meta.anyAction`/`meta.anySubject`. See
[`SPEC_V1-0-0.md`](SPEC_V1-0-0.md) §6.2.2 for the exact algorithm, and
§4.2.1 for `meta` and the wildcards.

Condition
---------

- a filter to apply to a subject to check if the rule should be applied or not

Condition = 
  | EqCondition = TValue | { $eq: TValue } //=> TSubject == TValue
  | GtCondition = { $gt: TValue } //=> TSubject > TValue
  | GteCondition = { $gte: TValue } //=> TSubject >= TValue
  | LtCondition = { $lt: TValue } //=> TSubject < TValue
  | LteCondition = { $lte: TValue } //=> TSubject <= TValue
  | InCondition = { $in: TValue[] } //=> TValue[].contains(TSubject)
  | HasCondition = { $has: TValue } //=> TSubject[].contains(TValue)
  | SubstrOperator = { $substr: TValue } //=> a small non-regex pattern language matches TSubject
  | OrCondition = { $or: Condition[] } //=> Condition[].any(TSubject)
  | AndCondition = { $and: Condition[] } //=> Condition[].all(TSubject)
  | NotCondition = { $not: Condition } //=> !Condition(TSubject)
  | FieldCondition = { [key]: Condition } //=> Condition(TSubject[key]), key MUST NOT start with "$"
  | ExplicitFieldCondition = { $field: [key, Condition] } //=> Condition(TSubject[key]), required when key starts with "$"

See [`SPEC_V1-0-0.md`](SPEC_V1-0-0.md) §5 for full operator semantics,
including `$substr`'s pattern language (§5.4.6) and why regex matching
(`$rgx`) isn't part of v1.

Policy
------

- Methods:
  - new<TActions, TSubjects>(PolicyDef, customConditions?) => Policy
  - def() => PolicyDefinition
  - can(TAction, TSubjectName | TSubject) => boolean
  - cannot(TAction, TSubjectName | TSubject) => boolean
  - require(TAction, TSubjectName | TSubject) => void throws PolicyError

`append` is not part of v1 — see [`SPEC_V1-0-0.md`](SPEC_V1-0-0.md) §1.
