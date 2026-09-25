/**
 * Thrown by `Policy.from(...)` (or an equivalent construction entry point)
 * when a `PolicyDefinition` is structurally invalid -
 * a malformed rule tuple, a both-sides-wildcarded rule
 * carrying a Conditions element, or a rule
 * referencing an action/subject/custom-operator name outside a declared
 * `meta` catalog.
 */
export class PolicyLoadException extends Error {
  constructor(message: string) {
    super(message)
    this.name = "PolicyLoadException"
  }
}
