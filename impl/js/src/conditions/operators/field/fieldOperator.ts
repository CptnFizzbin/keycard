import { checkField } from "./fieldAccess.ts"
import { PolicyTypeMismatchError } from "../../../errors/policyTypeMismatchError.ts"
import type { AnyCondition } from "../../condition.ts"
import { createOperator } from "../operator.ts"

/**
 * `$field` - explicit field access, equivalent to the bare-key
 * field condition but with the field name given as a tuple
 * element. Exists so a policy can reach a subject field whose name itself
 * starts with `$` (which, as an object key, would otherwise be read as an
 * operator). Subject to the same top-level-only restriction as
 * the bare-key form - see `checkField`.
 */
export const FieldOperator = createOperator<object, [string, AnyCondition]>("$field", (subject, operand, ctx) => {
  if (!Array.isArray(operand) || operand.length !== 2 || typeof operand[0] !== "string") {
    throw new PolicyTypeMismatchError({
      value: { expected: "[name: string, Condition]", received: typeof operand },
    })
  }

  const [fieldName, condition] = operand
  return checkField(subject, fieldName, condition, ctx)
})
