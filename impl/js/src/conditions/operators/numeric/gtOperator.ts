import { createOperator } from "../operator.ts"
import { numericCompare } from "./numericCompare.ts"

/** §7.4.3: `$gt` - numeric greater-than. */
export const GtOperator = createOperator("$gt", (subject, value) => numericCompare(subject, value, (a, b) => a > b))
