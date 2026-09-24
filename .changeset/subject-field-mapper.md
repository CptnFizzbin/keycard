---
"@cptn-fizzbin/keycard": minor
"@cptn-fizzbin/keycard-impl-java": minor
---

Add `SubjectFieldMapper`: an optional, per-field getter map for a subject's
wrapped instance, for a condition field whose name doesn't match the instance's
own shape (a rename, a computed/derived value) or an instance
reflection/property access can't reach directly. A field the mapper doesn't
define still falls back to the existing property access (JS) / reflection
(Java).

- `createSubject(name, fieldMapper?)` (JS) / `new Subject<>(name, fieldMapper)`
  (Java) attaches a mapper to a subject definition; it's carried through every
  `.wrap()` call unchanged.
- `SubjectFieldMapperCatalog` registers mappers by subject name for subjects
  created without one inline - consulted whenever the `Subject` passed to
  `Policy#can` doesn't carry its own.
- New `KeycardConfig`, optionally accepted by both `Policy` and `PolicyBuilder`
  alongside their existing options/constructors: bundles `anyAction`/
  `anySubject` (Java only - JS already has these on `PolicyBuilderOptions`),
  `actions`/`subjects` (additive to the `meta.actions`/`meta.subjects` catalogs),
  `operators`, and `mapper` (a
  `SubjectFieldMapperCatalog`) into one object built once and handed to both.
