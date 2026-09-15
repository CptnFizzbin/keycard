import { createOperator } from "../operator.ts"
import { numericCompare } from "./numericCompare.ts"

/** §7.4.3: `$lte` - numeric less-than-or-equal. */
export const LteOperator = createOperator("$lte", (subject, value) => numericCompare(subject, value, (a, b) => a <= b))
