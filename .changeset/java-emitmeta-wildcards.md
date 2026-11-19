---
"@cptn-fizzbin/keycard-java-core": patch
---

`PolicyBuilder#buildDef()` keeps a configured `anyAction`/`anySubject` in
`meta` when `KeycardConfig#emitMeta(false)`, so a custom or disabled wildcard
token keeps working in the built `Policy` (#49). Only
`meta.actions`/`meta.subjects`/`meta.operators` are gated on `emitMeta`.
