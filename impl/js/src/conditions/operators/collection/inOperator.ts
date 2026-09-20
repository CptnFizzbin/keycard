import { PolicyTypeMismatchError } from "../../../errors/policyTypeMismatchError.ts"
import { createOperator } from "../operator.ts"

/** `$in` - the operand MUST be an array; containment uses the same equality semantics as `$eq` per element. */
export const InOperator = createOperator("$in", (subject, value) => {
  if (!Array.isArray(value)) {
    throw new PolicyTypeMismatchError({ value: { expected: "array", received: typeof value } })
  }

  return value.some((v) => v === subject)
})
