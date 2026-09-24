# Glossary

Term definitions for KeyCard. See [`SPEC.md`](SPEC.md) for the informal
overview and [`SPEC_V0.md`](website/spec/SPEC_V0.md) for the normative v0
specification.

- **Claims** - an object of values used either for building a policy
  (**Policy Claims**) or for checking a subject (**Subject Claims**)
  - **Policy Claims** - the actor-side input a `PolicyBuilder` uses to
    decide which rules to generate
    - e.g. a JWT, or `{ ownerOf: number[] }`
  - **Subject Claims** - the resource-side fields a `Subject` carries for
    Conditions to check against when a `Policy` is evaluated
    - should be composable and scoped to only the fields a policy's
      Conditions actually need - not the raw entity
    - e.g. `{ ownerId: number, status: string }` for an `Article`
- **Action** - a string that indicates that the user would like to do
  something to a subject
  - e.g. `Create`, `Read`, `Update`, `Delete`, `MarkDone`, `Archive`, ...
- **Subject** - the thing the user wants to do something with
  - e.g. `ToDoItem`, `Project`, ...
  - a Subject with no instance data is a **SubjectDef**; one wrapping an
    instance's Subject Claims is a **SubjectRef**
- **Catalog** - a keyed collection of Actions or Subjects whose keys become
  the serialized names for their entries - what lets an Action or Subject
  be created without a name at all
- **PolicyBuilder** - takes in claims, and produces a Policy or
  PolicyDefinition
- **Rule** - a tuple of Effect, Action, Subject, and an optional Condition
- **Effect** - whether a matching Rule allows or denies: `allow` or `deny`
- **Condition** - the optional filter on a Rule, evaluated against a
  Subject's instance data
- **Operator** - a `$`-prefixed key inside a Condition, such as `$eq` or
  `$in`; host applications can register custom ones
- **Wildcard** - the token (`_ANY_` by default) that matches any Action or
  any Subject in a Rule
- **PolicyDefinition** - (PolicyDef) a JSON-encodable description of which
  permissions the user is allowed or denied
- **Test Suite** - a named group of Test Cases embedded in a
  `PolicyDefinition`'s optional `tests` field, used by tooling to assert
  the policy behaves as documented
- **Test Case** - a single expected `can` outcome within a Test Suite: a
  `check` (the `action`, `subject`, and optional `subjectClaims` to pass to
  `can`) plus the `expected` boolean result
- **Policy** - the object that evaluates checks (`can`/`cannot`/`require`)
- **Building a Policy** - using a `PolicyBuilder` to create a `Policy` or a
  `PolicyDefinition`
- **Constructing a Policy** - reading a `PolicyDefinition` and converting it
  into a `Policy`
- **Evaluating a Policy** - performing a check (an action and a subject)
  against a `Policy`
