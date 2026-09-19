import type { AnyCondition } from "../../condition.ts"
import { createOperator } from "../operator.ts"

/**
 * `$not` - the exact negation of evaluating the sub-condition
 * against the same subject. No notion of a type mismatch of its own: any
 * type issue surfaces from evaluating the nested condition, not from
 * `$not` itself.
 */
export const NotOperator = createOperator<unknown, AnyCondition>("$not", (subject, condition, { resolveSubcondition }) =>
  !resolveSubcondition(subject, condition),
)
