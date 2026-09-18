/**
 * Thrown by `Policy.from(...)` at construction time when a
 * `PolicyDefinition`'s `version` is incompatible with what this
 * implementation supports - a different MAJOR, or a MINOR higher than
 * what's understood within a supported MAJOR (SPEC_V0.md §2, EC-11).
 * `PATCH` never affects this decision.
 */
export class PolicyVersionException extends Error {
  constructor(message: string) {
    super(message)
    this.name = "PolicyVersionException"
  }
}
