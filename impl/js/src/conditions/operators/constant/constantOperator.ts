import { PolicyTypeMismatchError } from "../../../errors/policyTypeMismatchError.ts"
import type { Operator, OperatorName } from "../operator.ts"
import { createOperator } from "../operator.ts"

/**
 * Creates an operator that ignores the subject and always evaluates to
 * `result`. Its only valid operand is `true`; any other operand is malformed
 * and evaluates to `false`.
 */
function createConstantOperator(name: OperatorName, result: boolean): Operator<unknown, true> {
  return createOperator<unknown, true>(name, (_subject, operand) => {
    if (operand !== true) throw new PolicyTypeMismatchError({
      value: {
        expected: "true",
        received: JSON.stringify(operand) ?? typeof operand,
      },
    })

    return result
  })
}

/** `$always` - `{ $always: true }` matches whatever subject value it is evaluated against, `null` included. */
export const AlwaysOperator = createConstantOperator("$always", true)

/** `$never` - `{ $never: true }` matches no subject. */
export const NeverOperator = createConstantOperator("$never", false)
