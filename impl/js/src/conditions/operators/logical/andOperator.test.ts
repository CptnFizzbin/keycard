import { describe, expect, test } from "vitest"

import { ConditionResolver } from "../../conditionResolver.ts"

describe("$and", () => {
  const resolver = new ConditionResolver()

  test.each([
    { subject: { left: 1, right: 1 }, condition: { $and: [{ left: 1 }, { right: 1 }] }, expected: true },
    { subject: { left: 1, right: 1 }, condition: { $and: [{ left: 1 }, { right: 2 }] }, expected: false },
    { subject: { left: 1, right: 1 }, condition: { $and: [{ left: 2 }, { right: 1 }] }, expected: false },
    { subject: { left: 1, right: 1 }, condition: { $and: [] }, expected: true },
  ])(`$condition with $subject -> $expected`, ({ subject, condition, expected }) => {
    expect(resolver.evaluate(subject, condition)).toBe(expected)
  })
})
