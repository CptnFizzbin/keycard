import { createOperator } from "../operator.ts"

/**
 * §7.4.1: `$eq` - value equality for primitives, not reference/identity
 * equality. No notion of a type mismatch: unequal types are simply
 * unequal, never a type issue (§7.1). A bare scalar condition (§7.2) is
 * resolved to this operator before evaluation ever reaches here.
 */
export const EqOperator = createOperator("$eq", (subject, value) => subject === value)
