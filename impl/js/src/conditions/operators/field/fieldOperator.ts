import { PolicyTypeMismatchError } from "../../../errors/policyTypeMismatchError.ts"
import { createOperator } from "../operator.ts"
import { hasField, isBareNe } from "./fieldAccess.ts"

/**
 * §7.4.11: `$field` - explicit field access, equivalent to the bare-key
 * field condition (§7.4.10) but with the field name given as a tuple
 * element. Exists so a policy can reach a subject field whose name itself
 * starts with `$` (which, as an object key, would otherwise be read as an
 * operator - see §7.5).
 */
export const FieldOperator = createOperator("$field", (subject, operand, { resolveSubcondition }) => {
  if (!Array.isArray(operand) || operand.length !== 2 || typeof operand[0] !== "string") {
    throw new PolicyTypeMismatchError({
      value: { expected: "[name: string, Condition]", received: typeof operand },
    })
  }

  const [fieldName, condition] = operand
  return hasField(subject, fieldName) ? resolveSubcondition(subject[fieldName], condition) : isBareNe(condition)
})
