/**
 * Thrown by `Policy.from(...)` when loading a policy when a
 * `PolicyDefinition`'s `version` is incompatible with what this
 * implementation supports - a different MAJOR, or a MINOR higher than
 * what's understood within a supported MAJOR.
 * `PATCH` never affects this decision.
 */
export class PolicyVersionException extends Error {
  constructor(message: string) {
    super(message)
    this.name = "PolicyVersionException"
  }
}
