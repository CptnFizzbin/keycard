/**
 * Thrown immediately by `PolicyBuilder`'s `allow()`/`deny()` when called
 * with a rule wildcarded on both the action and the subject that also
 * carries a Conditions element - invalid per SPEC_V1-0-0.md §6 property 5
 * (EC-6). Callers get this at the call site, rather than waiting for
 * `buildDef()`/`Policy.from(...)` to eventually catch it.
 */
export class PolicyArgumentError extends Error {
  constructor(message: string) {
    super(message)
    this.name = "PolicyArgumentError"
  }
}
