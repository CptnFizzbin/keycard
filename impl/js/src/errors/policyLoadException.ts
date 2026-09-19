/**
 * Thrown by `Policy.from(...)` (or an equivalent construction entry point)
 * when a `PolicyDefinition` is structurally invalid per SPEC_V0.md -
 * a malformed rule tuple (§3.3, EC-10), a both-sides-wildcarded rule
 * carrying a Conditions element (§6 property 5, EC-6), or a rule
 * referencing an action/subject/custom-operator name outside a declared
 * `meta` catalog (§3.2.2, §3.2.3, EC-8, EC-13).
 */
export class PolicyLoadException extends Error {
  constructor(message: string) {
    super(message)
    this.name = "PolicyLoadException"
  }
}
