import { PolicyTypeMismatchError } from "../../../errors/policyTypeMismatchError.ts"
import type { AnyCondition } from "../../condition.ts"
import { createOperator } from "../operator.ts"

export const OrOperator = createOperator<unknown, AnyCondition[]>("$or", (subject, subConditions, { resolveSubcondition }) => {
  if (subject === null || subject === undefined) return false

  if (!Array.isArray(subConditions)) throw new PolicyTypeMismatchError({
    value: {
      expected: "array",
      received: typeof subConditions,
    },
  })

  return subConditions.some((condition) => {
    return resolveSubcondition(subject, condition)
  })
})
