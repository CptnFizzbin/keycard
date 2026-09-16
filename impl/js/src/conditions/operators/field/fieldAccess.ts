import { PolicyTypeMismatchError } from "../../../errors/policyTypeMismatchError.ts"
import type { SubjectFieldMapper } from "../../../subject/subjectFieldMapper.ts"
import type { AnyCondition, Condition } from "../../condition.ts"
import type { OperatorContext } from "../operator.ts"

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
export function isBareNe<TSubject>(condition: Condition<TSubject>): boolean {
  return (
    typeof condition === "object"
    && condition !== null
    && !Array.isArray(condition)
    && Object.keys(condition).length === 1
    && "$ne" in condition
  )
}

/**
 * An {@link OperatorContext} additionally carrying a SubjectFieldMapper for
 * the subject currently in scope. Only ever attached to a context where
 * `canNarrowField()` is `true`: a field mapper is bound to the original
 * top-level subject, and that's the only point in the tree `subject` is
 * still guaranteed to be it (§7.4.10 permits only one level of field
 * narrowing, and only field access - never `$and`/`$or`/`$not` - narrows
 * `subject` at all). `ConditionResolver` is the only place that constructs
 * one.
 */
export interface FieldMapperContext extends OperatorContext {
  readonly fieldMapper: SubjectFieldMapper<unknown>
}

function hasFieldMapper(ctx: OperatorContext): ctx is FieldMapperContext {
  return "fieldMapper" in ctx
}

/**
 * §7.4.10, §7.4.11: shared narrowing logic for the bare-key field path and
 * the explicit `$field` operator - both resolve to "look up a named field on
 * the subject, then evaluate against it," and both are subject to v1's
 * top-level-only restriction: a field condition MUST NOT itself narrow into
 * another field, so `ctx.canNarrowField()` must still be `true` at this
 * point in the tree. When it isn't - a field condition already reached by
 * one narrowing step attempting a second - this is a structural problem,
 * not a data one, so it's diagnosed like any other malformed condition
 * shape (§7.1) rather than silently returning `false`.
 *
 * When `ctx` carries a SubjectFieldMapper, it's tried first for
 * `fieldName`; a field it doesn't define falls through to ordinary
 * property access, same as when no mapper is attached at all.
 */
export function checkField<TSubject>(subject: TSubject, fieldName: string, condition: AnyCondition, ctx: OperatorContext): boolean {
  if (!ctx.canNarrowField()) {
    throw new PolicyTypeMismatchError({
      value: {
        expected: `a leaf condition for field "${fieldName}" (v1 supports only top-level field access - no further nesting)`,
        received: "a nested field condition",
      },
    })
  }

  if (hasFieldMapper(ctx) && fieldName in ctx.fieldMapper) {
    return ctx.resolveFieldSubcondition(ctx.fieldMapper[fieldName](subject), condition)
  }

  return hasField(subject, fieldName) ? ctx.resolveFieldSubcondition(subject[fieldName], condition) : isBareNe(condition)
}
