import { PolicyTypeMismatchError } from "../../../errors/policyTypeMismatchError.ts"

/**
 * §7.4.3: the numeric comparison shared by `$gt`/`$gte`/`$lt`/`$lte` -
 * `subject` and the operand MUST both be numbers (never coerced - no
 * numeric-string parsing, no lexicographic comparison) and comparison
 * MUST use IEEE-754 double semantics, so `NaN` never compares true here
 * even though some host languages treat `NaN` as equal to itself.
 */
export function numericCompare(
  subject: unknown,
  value: unknown,
  cmp: (a: number, b: number) => boolean,
): boolean {
  if (typeof subject !== "number") {
    throw new PolicyTypeMismatchError({ subject: { expected: "number", received: typeof subject } })
  }
  if (typeof value !== "number") {
    throw new PolicyTypeMismatchError({ value: { expected: "number", received: typeof value } })
  }

  return cmp(subject, value)
}
