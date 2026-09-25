# v0 conformance fixtures

The YAML files in this directory are a conformance test suite for
[`SPEC_V0.md`](../../../website/spec/SPEC_V0.md) — the authoritative v0 policy
spec. `test/fixtures/policies/` is a separate, format-agnostic fixture set
(shared between `impl/java` and `impl/js`'s own unit-style suites) that also
uses this same v1 `rules`/`meta` shape.

Every implementation **MUST** read these fixtures as part of its test suite
(`../../../impl/js/tests/integration/keycardDefinitionSupport.test.ts` and
`impl/java/.../integration/V1ConformanceFixtureTest.java` do so today). Both
`impl/java` and `impl/js` now implement the v1 schema natively, so each parsed
suite's `rules`/`meta` are handed straight to a real
`Policy`/`PolicyDefinition` - no adapter needed.

Discovery, subject-argument construction, and per-`version` filtering are
factored into small, reusable utility modules shared by every compliance suite —
not just this one — rather than duplicated per fixture format:
`../../../impl/js/tests/integration/fixtureUtils.ts` and
`impl/java/.../integration/ComplianceFixtures.java`. Each format-specific loader
(`policyFixtures.test.ts`/`keycardDefinitionSupport.test.ts` in JS,
`PolicyFixtures`/`Fixtures` in Java) only owns parsing its own document shape
into the shared `{ action, subject, subjectData?, expected }` case shape those
utilities work with.

## Format

Each `*.yaml` file is a sequence of one or more YAML documents (separated by
`---`), one per test suite:

```yaml
version: "0.1"          # required — a v0 PolicyDefinition
name: name of the test suite
description: string       # optional
meta:                      # optional
  anyAction: ...
  anySubject: ...
  actions: [...]
  subjects: [...]
  operators: [...]
rules:                     # list of [effect, action, subject, conditions?] tuples
  - [allow, Read, Article]
tests:
  - name: description of the case          # optional
    action: Read
    subject: Article
    subjectData:            # optional — omit for a bare/no-instance subject check
      owner_id: 1
    expected: allow                        # "allow" or "deny"
---
name: next suite
...
```

`subject` names the subject's type; `subjectData`, when present, is the wrapped
instance's value (a `SubjectRef`) and makes the check
conditional-rule-eligible. Omitting `subjectData` checks a bare type (no
instance — a `SubjectDef`-style check): any rule carrying a
`Conditions` element cannot match such a check.

## Filtering by version

Every fixture suite declares a SemVer `version`. Each compliance test suite
bakes in its own `COMPLIANT_VERSION` constant — the highest version its adapter
is actually written against (`keycardDefinitionSupport.test.ts`'s
`COMPLIANT_VERSION`,
`ConformanceFixtureTest`'s `COMPLIANT_VERSION`) — and a fixture whose declared
`version` exceeds it is skipped, not failed, mirroring the spec's compatibility rule
(same `MAJOR`, `MINOR` no higher than what's supported; `PATCH` never matters). This is automatic: once fixtures for a newer `MINOR` version are
added, a compliance suite whose adapter hasn't caught up yet skips them with no
configuration required, rather than failing on behavior it was never meant to
support. Bump a suite's `COMPLIANT_VERSION` only once its adapter has actually
been updated to handle whatever the newer version adds — not merely because such
fixtures now exist.

For a one-off run that deliberately narrows or widens that baked-in ceiling
without editing code:

- JS: set the `KEYCARD_FIXTURES_MAX_VERSION` env var, e.g.
  `KEYCARD_FIXTURES_MAX_VERSION=1.0.0 yarn test run`.
- Java: set the `keycard.fixtures.maxVersion` system property, e.g.
  `mvn test -Dkeycard.fixtures.maxVersion=1.0.0`.

Every suite here declares `"1.0.0"`, and both compliance suites currently bake
in `COMPLIANT_VERSION = "1.0.0"` too, so today this filtering is a no-op in
practice — it starts mattering the moment a fixture file declares something
newer than a given suite's baked-in ceiling.

## Scope

This format only expresses *evaluation* outcomes (`can` returning `allow`/
`deny`), so it covers action/subject matching, the
rule-evaluation algorithm, and the condition/operator language. It
does **not** cover the purely construction-time validation requirements —
malformed rule tuples, `version` incompatibility, a
both-sides-wildcarded rule carrying a `Conditions` element,
`meta.actions`/`meta.subjects`/`meta.operators`
catalog-coverage enforcement, a `meta.operators` entry with
nothing registered for it, or a duplicate operator name across the
built-ins and whatever custom operators were supplied
— since those are expected to *throw* at construction rather than resolve to an
`allow`/`deny` outcome. Those requirements should be covered separately, e.g. by
implementation-specific unit tests asserting the right exception type.

This format is also distinct from a `PolicyDefinition`'s own optional
`tests` field: that field is data a policy
document carries about *itself*, for an implementation's own test-runner to
execute; this directory's `tests:` sequences are the conformance
harness's fixtures, external to any one policy document, used to validate an
*implementation's* evaluation engine against the spec.
`12-tests-field.yaml` exercises both at once - see its own file list entry
below.

## Files

- `01-action-subject-matching.yaml` — exact action/subject matching, case
  sensitivity, default deny.
- `02-rule-ordering.yaml` — last-rule-wins, blanket rules being overridden or
  overriding.
- `03-wildcards.yaml` — `_ANY_` default, a custom wildcard token, and disabling
  the wildcard mechanism via `null`.
- `04-conditions-fields.yaml` — bare-value shorthand, missing field vs.
  explicit `null`, the top-level-only field restriction and `$field`.
- `05-operators-comparison.yaml` — `$eq`, `$ne`, `$gt`/`$gte`/`$lt`/`$lte`.
- `06-operators-collections.yaml` — `$in`, `$has`.
- `07-operators-substr.yaml` — `$substr`'s pattern language.
- `08-operators-logic.yaml` — `$or`, `$and`, `$not`, and multi-key AND.
- `09-custom-operators.yaml` — unregistered, uncataloged custom operators always
  evaluate to `false`. The cataloged-but-never-registered case is no
  longer expressible here now that it's a construction-time throw - see the
  file's own comment.
- `10-subject-shapes.yaml` — bare type vs. wrapped instance.
- `11-worked-example.yaml` — an end-to-end mirror of the spec's own Appendix
  policy.
- `12-tests-field.yaml` — a `1.1.0` policy whose own embedded
  `tests` field is evaluation-inert: this file's `cases` (the
  conformance-harness format below, unrelated to the policy's own `tests`)
  confirm `can`/`cannot`/`require` behave the same with or without it. Declares
  `"1.1.0"`, so it's skipped by a compliance suite whose
  `COMPLIANT_VERSION` hasn't caught up yet — see "Filtering by version"
  below.
