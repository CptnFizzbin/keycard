import { PolicyTypeMismatchError } from "../../../errors/policyTypeMismatchError.ts"
import { createOperator } from "../operator.ts"

/** §7.4.5: `$has` - `subject` MUST be an array. */
export const HasOperator = createOperator("$has", (subject, value) => {
  if (!Array.isArray(subject)) {
    throw new PolicyTypeMismatchError({ subject: { expected: "array", received: typeof subject } })
  }

  return subject.some((v) => v === value)
})
