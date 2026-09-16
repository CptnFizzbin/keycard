// Actions
import { setLogger } from "./lib/logger.ts"

export type { Action, InferActions } from "./action"
export { createAction } from "./action/index.ts"

// Subjects
export type { Subject, InferSubjects, SubjectFieldMapper } from "./subject"
export { createSubject, SubjectFieldMapperCatalog } from "./subject/index.ts"

// Conditions
export type { Condition, Operator, OperatorContext } from "./conditions"
export { ConditionResolver, createOperator } from "./conditions/index.ts"

// Policy
export type { RuleTuple, Meta, Effect, PolicyDefinition } from "./policy"
export { Policy } from "./policy/index.ts"

// PolicyBuilder
export { PolicyBuilder } from "./builder/index.ts"

// Shared config
export type { KeycardConfig } from "./keycardConfig.ts"

// Errors
export { PolicyError, PolicyLoadException, PolicyVersionException, PolicyArgumentError } from "./errors/index.ts"

export const KeyCardConfig = {
  setLogger: setLogger,
}
