import { createOperator } from "../operator.ts"
import { numericCompare } from "./numericCompare.ts"

/** §7.4.3: `$gte` - numeric greater-than-or-equal. */
export const GteOperator = createOperator("$gte", (subject, value) => numericCompare(subject, value, (a, b) => a >= b))
