# Glossary

Term definitions for KeyCard. See [`SPEC.md`](SPEC.md) for the informal
overview and [`SPEC_V1-0.md`](docs/spec/SPEC_V1-0.md) for the normative v1
specification.

- **Claims** - Object(s) that can be used by a builder to create a Policy
  Definition
  - e.g. a JWT, or `{ ownerOf: number[] }`
- **Action** - a string that indicates that the user would like to do
  something to a subject
  - e.g. `Create`, `Read`, `Update`, `Delete`, `MarkDone`, `Archive`, ...
- **Subject** - the value that the user wants to do something with
  - e.g. `ToDoItem`, `Project`, ...
- **Catalog** - a keyed collection of Actions or Subjects (a
  `Record<string, Action>` / `Map<String, Action<?>>`, and symmetrically for
  Subjects) handed to `KeycardConfig`, whose keys become the serialized
  names for their entries - what lets `createAction()`/`createSubject()`
  (JS) and `Action.create()`/`Subject.create()` (Java) be called with no
  name at all
- **PolicyBuilder** - takes in claims, and produces a Policy or
  PolicyDefinition
- **Rule** - an allowed or denied tuple of effect, action, subject, and
  conditions
- **PolicyDefinition** - (PolicyDef) a text based encoding of what
  permissions the user is allowed/denied
- **Policy** - an object that can be used to perform checks against/with
- **Building a Policy** - using a `PolicyBuilder` to create a
  `PolicyDefinition`
- **Constructing a Policy** - reading a `PolicyDefinition` and converting it
  into a `Policy`
- **Evaluating a Policy** - performing a check (an action and a subject)
  against a `Policy`
