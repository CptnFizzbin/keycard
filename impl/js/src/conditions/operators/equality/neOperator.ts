import { createOperator } from "../operator.ts"

/** §7.4.2: `$ne` - the exact negation of `$eq` (eqOperator.ts) for the same subject/value pair, implemented directly in terms of it rather than duplicating its equality logic. No notion of a type mismatch, same as `$eq`. */
export const NeOperator = createOperator("$ne", (subject, value, { resolveSubcondition }) => !resolveSubcondition(subject, { $eq: value }))
