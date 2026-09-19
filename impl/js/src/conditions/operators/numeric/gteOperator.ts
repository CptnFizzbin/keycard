import { createOperator } from "../operator.ts"
import { numericCompare } from "./numericCompare.ts"

/** `$gte` - numeric greater-than-or-equal. */
export const GteOperator = createOperator("$gte", (subject, value) => numericCompare(subject, value, (a, b) => a >= b))
