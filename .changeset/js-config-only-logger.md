---
"@cptn-fizzbin/keycard-js-core": minor
---

**Breaking:** the module-level logger is gone. `setLogger()` is removed; diagnostics now go only to `KeycardConfig.logger` and are discarded when it is unset. Messages are no longer prefixed with `[KeyCard]`.
