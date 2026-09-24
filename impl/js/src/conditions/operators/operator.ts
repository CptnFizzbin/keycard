import { PolicyTypeMismatchError } from "../../errors/policyTypeMismatchError.ts"
import type { JsonValue } from "../../lib/json.ts"
import { getLogger } from "../../lib/logger.ts"
import type { Condition } from "../condition.ts"

export interface OperatorContext {
  /** Evaluates `condition` against `subject`, preserving whether this point in the tree may still narrow into a field - used by $and/$or/$not, which don't narrow. */
  resolveSubcondition<TSubject>(subject: TSubject, condition: Condition<TSubject>): boolean

  /** Evaluates `condition` against a subject already narrowed by one field access, disabling any further field narrowing beneath it - used by the bare-key field path and `$field`. */
  resolveFieldSubcondition<TSubject>(subject: TSubject, condition: Condition<TSubject>): boolean

  /** true if a field condition (bare-key or `$field`) is still allowed to narrow at this point in the tree - v1 permits exactly one level. */
  canNarrowField(): boolean
}

export interface Operator<TSubject, TValue = JsonValue> {
  name: `$${string}`
  resolve: (subject: TSubject, value: TValue, ctx: OperatorContext) => boolean
}

// eslint-disable-next-line @typescript-eslint/no-explicit-any
export type AnyOperator = Operator<any, any>

export type InferCondition<TOperator extends AnyOperator> =
  TOperator extends Operator<infer _, infer TValue>
    ? { [key in TOperator["name"]]: TValue }
    : never

export function createOperator<TSubject, TValue = JsonValue>(
  name: Operator<TSubject, TValue>["name"],
  resolver: Operator<TSubject, TValue>["resolve"],
): Operator<TSubject, TValue> {
  return {
    name,
    resolve: (subject, value, ctx) => {
      try {
        return resolver(subject, value, ctx)
      } catch (e) {
        if (e instanceof PolicyTypeMismatchError) {
          // "type issues are diagnosed, not silenced" - call
          // getLogger() fresh rather than caching it at module load, so
          // a consumer's setLogger() (almost always called after this
          // module has already been imported) still takes effect.
          getLogger().warn(e.message)
          return false
        }

        throw e
      }
    },
  }
}

/**
 * A bare resolver function, keyed by its own `$name` in an
 * {@link OperatorCatalog} rather than wrapped via `createOperator` - `ctx`
 * is optional since most operators (comparisons, pattern matching) never
 * need it.
 */
export type OperatorResolver<TSubject = unknown, TValue = JsonValue> = (
  subject: TSubject,
  value: TValue,
  ctx: OperatorContext,
) => boolean

/**
 * A keyed collection of custom operators - the catalog counterpart to
 * `AnyOperator[]` (built via `createOperator`), symmetric with
 * `ActionCatalog`/`SubjectCatalog`: each key is the operator's own
 * `$`-prefixed name, and its value is a bare resolver function rather than
 * an `Operator` object, since an operator (unlike an Action/Subject) has no
 * "dynamic, no name yet" state for a catalog key to resolve. Handed to
 * `PolicyBuilder`/`Policy` via `KeycardConfig.operators`.
 */
// eslint-disable-next-line @typescript-eslint/no-explicit-any
export type OperatorCatalog<TSubject = any, TValue = JsonValue> = Record<`$${string}`, OperatorResolver<TSubject, TValue>>

/** Normalizes `KeycardConfig.operators` (an `AnyOperator[]`, an `OperatorCatalog`, or neither) into the `AnyOperator[]` form `ConditionResolver` accepts. */
export function normalizeOperators<TOperators extends AnyOperator>(
  operators: TOperators[] | OperatorCatalog | undefined,
): AnyOperator[] {
  if (operators === undefined) return []
  if (Array.isArray(operators)) return operators
  return Object.entries(operators).map(([name, resolve]) => createOperator(name as `$${string}`, resolve))
}
