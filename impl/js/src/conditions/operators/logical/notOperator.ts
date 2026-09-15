import { createOperator } from "../operator.ts"

/**
 * §7.4.9: `$not` - the exact negation of evaluating the sub-condition
 * against the same subject. No notion of a type mismatch of its own: any
 * type issue surfaces from evaluating the nested condition, not from
 * `$not` itself.
 */
export const NotOperator = createOperator("$not", (subject, condition, { resolveSubcondition }) =>
  !resolveSubcondition(subject, condition),
)
