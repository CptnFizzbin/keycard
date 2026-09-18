---
title: Policy Definition
sidebar_label: Policy Definition
slug: /policy-definition
---

# Policy Definition

A `PolicyDefinition` (or "PolicyDef") is the JSON-encodable document produced by
a `PolicyBuilder`, or written by hand. It is the one artifact every KeyCard
implementation agrees on, which is what makes a policy built in one language
usable in another.

## Envelope

```yaml
version: "1.0"            # KeyCard policy spec version (SemVer)
meta: # optional
  actions: [ Create, Update, Delete ]
  subjects: [ Article ]
  anyAction: ALL         # optional override for the "_ANY_" action wildcard
  anySubject: ALL        # optional override for the "_ANY_" subject wildcard
rules:
  - [ allow, Create, Article ]
  - [ allow, Update, Article, { owner_id: 1 } ]
  - [ deny, Delete, Article, { status: { $not: "archived" } } ]
```

- **`version`** — the SemVer version of the KeyCard policy spec this document
  conforms to (currently `"1.0"`). `PATCH` never carries compatibility
  meaning, so it's optional and generally excluded — `"1.0"` is shorthand
  for `"1.0.0"`.
- **`meta`** — optional. Can rename or disable the default wildcard
  tokens (`anyAction` / `anySubject`), and — if `actions` / `subjects`
  are declared — **enforces** them: constructing a `Policy` throws if
  any rule's action or subject isn't the wildcard and isn't in the
  matching catalog. A custom-operator catalog (`meta.operators`) and an
  opaque `meta.application` slot for host-application data are also
  available; see
  [§4.2 of the v1.0 spec](https://github.com/CptnFizzbin/keycard/blob/main/docs/spec/SPEC_V0.md#42-meta)
  for the full `meta` shape.
- **`rules`** — required. An ordered list of rule tuples, evaluated as described
  below.

## Rules

Each rule is a tuple:

```
[effect, action, subject, conditions?]
```

- **`effect`** — `"allow"` or `"deny"`.
- **`action`** — an action name, or the wildcard (`_ANY_` by default).
- **`subject`** — a subject name, or the wildcard (`_ANY_` by default).
- **`conditions`** — optional. A [condition](./condition-operators.md)
  evaluated against the subject *instance* (not just its name/type). If omitted,
  the rule matches any instance of that subject.

## Evaluation order: last match wins

`rules` is a single, order-significant list. When checking `can(action,
subject)`, KeyCard walks the rule list **in reverse** and returns the effect of
the *first* rule (i.e. the *last* one written) whose action and subject match —
and whose conditions, if present, also match the subject instance. If nothing
matches, the result is `false` (default deny).

This is deliberately **not** "any `deny` beats any `allow`." A later
`allow` overrides an earlier `deny` for the same action/subject, exactly as
later CSS rules override earlier ones. Order your rules from general to
specific.

```yaml
rules:
  - [ deny, Update, Article ]                    # 1. nobody may update articles...
  - [ allow, Update, Article, { owner_id: 1 } ]   # 2. ...except the owner
```

Here, rule 2 is checked first (last-to-first) and wins for an article owned by
the current user; rule 1 is the fallback for everyone else.

## Wildcards

Every policy has a default wildcard token, `_ANY_`, usable in the
`action` or `subject` slot of a rule to match anything:

```yaml
rules:
  - [ allow, _ANY_, _ANY_ ]          # superuser: allow everything
  - [ deny, Delete, _ANY_ ]          # ...except deleting anything
```

A policy may rename or disable either wildcard via `meta.anyAction` /
`meta.anySubject`:

- Omitted → defaults to `"_ANY_"`.
- An explicit string → use that string as the wildcard token instead.
- `null` or `false` → disable the wildcard entirely; no action/subject string
  can ever match it.

## Building vs. constructing vs. evaluating

These three verbs mean specific, distinct things in KeyCard:

- **Building** a policy — using a `PolicyBuilder` to turn claims into a
  `PolicyDefinition`.
- **Constructing** a policy — reading a `PolicyDefinition` and turning it into a
  live `Policy` object.
- **Evaluating** a policy — calling `can()` / `cannot()` / `require()` on a
  constructed `Policy`.

```
Claims --[Builder]--> PolicyDefinition --[construct]--> Policy --[evaluate]--> boolean
```

Because a `PolicyDefinition` is plain JSON, the "build" step and the
"construct + evaluate" steps don't need to happen in the same language, process,
or even on the same continent.

For the exact matching algorithm (including case-sensitivity rules and how
missing/`null` fields are treated), see
[§6.2.2 of the v1.0 spec](https://github.com/CptnFizzbin/keycard/blob/main/docs/spec/SPEC_V0.md#622-can--cannot--require).
