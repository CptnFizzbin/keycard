import { createOperator } from "../operator.ts"
import { numericCompare } from "./numericCompare.ts"

/** §7.4.3: `$lt` - numeric less-than. */
export const LtOperator = createOperator("$lt", (subject, value) => numericCompare(subject, value, (a, b) => a < b))
