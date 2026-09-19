import { createOperator } from "../operator.ts"

/**
 * `$eq` - value equality for primitives, not reference/identity
 * equality. No notion of a type mismatch: unequal types are simply
 * unequal, never a type issue. A bare scalar condition is
 * resolved to this operator before evaluation ever reaches here.
 */
export const EqOperator = createOperator("$eq", (subject, value) => subject === value)
