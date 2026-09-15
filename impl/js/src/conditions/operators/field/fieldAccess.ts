import type { Condition } from "../../condition.ts"

/**
 * §7.4.10, §7.3: true when `subject` is a non-null object carrying
 * `fieldName` - a missing field (or a non-object subject) is absence, not
 * a type issue, so this stays a plain predicate rather than throwing.
 * Shared by the bare-key field path (`ConditionResolver.fieldCheck`) and
 * the explicit `$field` operator (§7.4.11), which narrow the same way.
 */
export function hasField(subject: unknown, fieldName: string): subject is Record<string, unknown> {
  return subject !== null && typeof subject === "object" && fieldName in subject
}

/**
 * §7.3's `$ne`-on-a-missing-field carve-out: MUST be the exact negation of
 * `$eq` (§7.4.2) even when the field being tested is missing, since `$eq`
 * on a missing field is false - so `$ne` on a missing field is true,
 * unlike every other operator, which keeps the blanket `false`. Narrow by
 * design: only fires when `$ne` is itself the sole nested condition, not
 * when it's one key among several in a multi-key condition object (§7.5)
 * or nested deeper - `{ status: { $not: { $eq: "archived" } } }` on a
 * missing `status` still gets the blanket `false`, unlike a bare
 * `{ status: { $ne: "archived" } }`, even though `$not` has the same
 * "exact negation" contract `$ne` does (§7.4.9). Undecided whether that
 * should change; not addressed here.
 */
export function isBareNe(condition: Condition): boolean {
  return (
    typeof condition === "object"
    && condition !== null
    && !Array.isArray(condition)
    && Object.keys(condition).length === 1
    && "$ne" in condition
  )
}
