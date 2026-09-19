import { createOperator } from "../operator.ts"
import { numericCompare } from "./numericCompare.ts"

/** `$gt` - numeric greater-than. */
export const GtOperator = createOperator("$gt", (subject, value) => numericCompare(subject, value, (a, b) => a > b))
