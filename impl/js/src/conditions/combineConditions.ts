import type { AnyCondition } from "./condition.ts"

/** A condition every subject instance satisfies. */
export const ALWAYS: AnyCondition = Object.freeze({ $and: Object.freeze([]) })

/** A condition no subject instance satisfies. */
export const NEVER: AnyCondition = Object.freeze({ $or: Object.freeze([]) })

/** Returns the operand of `condition` when it's exactly `{ [operator]: [...] }`, otherwise `undefined`. */
function listOperand(condition: AnyCondition, operator: "$and" | "$or"): AnyCondition[] | undefined {
  if (typeof condition !== "object" || condition === null || Array.isArray(condition)) return undefined
  const keys = Object.keys(condition)
  if (keys.length !== 1 || keys[0] !== operator) return undefined
  const operand = condition[operator]
  return Array.isArray(operand) ? operand : undefined
}

export function isAlways(condition: AnyCondition): boolean {
  return listOperand(condition, "$and")?.length === 0
}

export function isNever(condition: AnyCondition): boolean {
  return listOperand(condition, "$or")?.length === 0
}

/** `{ $and: conditions }`, with {@link ALWAYS} items dropped, nested `$and`s flattened, and {@link NEVER} absorbing the rest. */
export function allOf(...conditions: AnyCondition[]): AnyCondition {
  const items: AnyCondition[] = []
  for (const condition of conditions) {
    if (isNever(condition)) return NEVER
    if (isAlways(condition)) continue
    items.push(...listOperand(condition, "$and") ?? [condition])
  }
  if (items.length === 0) return ALWAYS
  return items.length === 1 ? items[0] : { $and: items }
}

/** `{ $or: conditions }`, with {@link NEVER} items dropped, nested `$or`s flattened, and {@link ALWAYS} absorbing the rest. */
export function anyOf(...conditions: AnyCondition[]): AnyCondition {
  const items: AnyCondition[] = []
  for (const condition of conditions) {
    if (isAlways(condition)) return ALWAYS
    if (isNever(condition)) continue
    items.push(...listOperand(condition, "$or") ?? [condition])
  }
  if (items.length === 0) return NEVER
  return items.length === 1 ? items[0] : { $or: items }
}

/** `{ $not: condition }`, folding {@link ALWAYS} and {@link NEVER} into each other. */
export function negate(condition: AnyCondition): AnyCondition {
  if (isAlways(condition)) return NEVER
  if (isNever(condition)) return ALWAYS
  return { $not: condition }
}
