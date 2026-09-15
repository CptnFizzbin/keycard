import { PolicyTypeMismatchError } from "../../errors/policyTypeMismatchError.ts"
import type { JsonValue } from "../../lib/json.ts"
import { getLogger } from "../../lib/logger.ts"
import type { Condition } from "../condition.ts"

export interface OperatorContext {
  resolveSubcondition<TSubject>(subject: TSubject, condition: Condition<TSubject>): boolean
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
          // §7.1: "type issues are diagnosed, not silenced" - call
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
