import { createOperator } from "../operator.ts"
import { numericCompare } from "./numericCompare.ts"

/** `$lte` - numeric less-than-or-equal. */
export const LteOperator = createOperator("$lte", (subject, value) => numericCompare(subject, value, (a, b) => a <= b))
