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
