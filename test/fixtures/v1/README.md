# v1 conformance fixtures

The YAML files in this directory are a conformance test suite for
[`SPEC_V1-0-0.md`](../../../SPEC_V1-0-0.md) — the authoritative v1 policy
spec. `test/fixtures/policies/` is a separate, format-agnostic fixture set
(shared between `impl/java` and `impl/js`'s own unit-style suites) that
also uses this same v1 `rules`/`meta` shape.

Every implementation **MUST** read these fixtures as part of its test suite
(`impl/js/tests/integration/v1ConformanceFixtures.test.ts` and
`impl/java/.../integration/V1ConformanceFixtureTest.java` do so today). Both
`impl/java` and `impl/js` now implement the v1 schema natively, so each
parsed suite's `rules`/`meta` are handed straight to a real
`Policy`/`PolicyDefinition` - no adapter needed.

Discovery, subject-argument construction, and per-`version` filtering are
factored into small, reusable utility modules shared by every compliance
suite — not just this one — rather than duplicated per fixture format:
`impl/js/tests/integration/complianceFixtures.ts` and
`impl/java/.../integration/ComplianceFixtures.java`. Each format-specific
loader (`policyFixtures.test.ts`/`v1ConformanceFixtures.test.ts` in JS,
`PolicyFixtures`/`V1Fixtures` in Java) only owns parsing its own document
shape into the shared `{ action, subject, subjectData?, expected }` case
shape those utilities work with.

## Format

Each `*.yaml` file is a sequence of one or more YAML documents (separated by
`---`), one per test suite:

```yaml
version: "1.0.0"          # required — a v1 PolicyDefinition, per SPEC_V1-0-0.md §4
name: name of the test suite
description: string       # optional
meta:                      # optional — see SPEC_V1-0-0.md §4.2
  anyAction: ...
  anySubject: ...
  actions: [...]
  subjects: [...]
  operators: [...]
rules:                     # list of [effect, action, subject, conditions?] tuples
  - [allow, Read, Article]
cases:
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

`subject` names the subject's type; `subjectData`, when present, is the
wrapped instance's value (a `SubjectRef`, per §6.2.2) and makes the check
conditional-rule-eligible. Omitting `subjectData` checks a bare type (no
instance — a `SubjectDef`-style check, §6.2.2): any rule carrying a
`Conditions` element cannot match such a check.

## Filtering by version

Every fixture suite declares a SemVer `version`. Each compliance test suite
bakes in its own `COMPLIANT_VERSION` constant — the highest version its
adapter is actually written against
(`v1ConformanceFixtures.test.ts`'s `COMPLIANT_VERSION`,
`V1ConformanceFixtureTest`'s `COMPLIANT_VERSION`) — and a fixture whose
declared `version` exceeds it is skipped, not failed, mirroring the
compatibility rule in SPEC_V1-0-0.md §4.1 (same `MAJOR`, `MINOR` no higher
than what's supported; `PATCH` never matters). This is automatic: once
fixtures for a newer `MINOR` version are added, a compliance suite whose
adapter hasn't caught up yet skips them with no configuration required,
rather than failing on behavior it was never meant to support. Bump a
suite's `COMPLIANT_VERSION` only once its adapter has actually been updated
to handle whatever the newer version adds — not merely because such
fixtures now exist.

For a one-off run that deliberately narrows or widens that baked-in ceiling
without editing code:

- JS: set the `KEYCARD_FIXTURES_MAX_VERSION` env var, e.g.
  `KEYCARD_FIXTURES_MAX_VERSION=1.0.0 yarn test run`.
- Java: set the `keycard.fixtures.maxVersion` system property, e.g.
  `mvn test -Dkeycard.fixtures.maxVersion=1.0.0`.

Every suite here declares `"1.0.0"`, and both compliance suites currently
bake in `COMPLIANT_VERSION = "1.0.0"` too, so today this filtering is a
no-op in practice — it starts mattering the moment a fixture file declares
something newer than a given suite's baked-in ceiling.

## Scope

This format only expresses *evaluation* outcomes (`can` returning `allow`/
`deny`), so it covers action/subject matching (§4.2.1, §6.2.2), the
rule-evaluation algorithm (§6.2.2), and the condition/operator language
(§5). It does **not** cover the purely construction-time validation
requirements — malformed rule tuples (§4.3), `version` incompatibility
(§4.1), a both-sides-wildcarded rule carrying a `Conditions` element (§4.3,
§6.2.1), `meta.actions`/`meta.subjects`/`meta.operators`
catalog-coverage enforcement (§4.2.2, §4.2.3), a `meta.operators` entry
with nothing registered for it (§4.2.3), or a duplicate operator name
across the built-ins and whatever custom operators were supplied (§4.2.3)
— since those are expected to *throw* at construction rather than resolve
to an `allow`/`deny` outcome. Those requirements should be covered
separately, e.g. by implementation-specific unit tests asserting the right
exception type.

## Files

- `01-action-subject-matching.yaml` — exact action/subject matching, case
  sensitivity, default deny.
- `02-rule-ordering.yaml` — last-rule-wins, blanket rules being overridden or
  overriding.
- `03-wildcards.yaml` — `_ANY_` default, a custom wildcard token, and
  disabling the wildcard mechanism via `null` (§4.2.1, §6.2.2
  property 4).
- `04-conditions-fields.yaml` — bare-value shorthand (§5.2), missing field vs.
  explicit `null` (§5.3), nested field conditions and `$field` (§5.4.10,
  §5.4.11).
- `05-operators-comparison.yaml` — `$eq`, `$ne`, `$gt`/`$gte`/`$lt`/`$lte`
  (§5.4.1–§5.4.3).
- `06-operators-collections.yaml` — `$in`, `$has` (§5.4.4, §5.4.5).
- `07-operators-substr.yaml` — `$substr`'s pattern language (§5.4.6).
- `08-operators-logic.yaml` — `$or`, `$and`, `$not`, and multi-key AND
  (§5.4.7–§5.4.9, §5.6).
- `09-custom-operators.yaml` — unregistered, uncataloged custom operators
  always evaluate to `false` (§5.5). The cataloged-but-never-registered
  case is no longer expressible here now that it's a construction-time
  throw - see the file's own comment.
- `10-subject-shapes.yaml` — bare type vs. wrapped instance.
- `11-worked-example.yaml` — an end-to-end mirror of the spec's own Appendix
  policy.
