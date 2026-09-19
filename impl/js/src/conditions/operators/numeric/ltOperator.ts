import { createOperator } from "../operator.ts"
import { numericCompare } from "./numericCompare.ts"

/** `$lt` - numeric less-than. */
export const LtOperator = createOperator("$lt", (subject, value) => numericCompare(subject, value, (a, b) => a < b))
