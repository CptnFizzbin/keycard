import { describe, expect, test } from "vitest"

import { ConditionResolver } from "../../conditionResolver.ts"

describe("$ne", () => {
  const resolver = new ConditionResolver()

  test.each([
    { subject: { name: "foobar" }, condition: { name: { $ne: "foobar" } }, expected: false },
    { subject: { name: "fizz" }, condition: { name: { $ne: "foobar" } }, expected: true },
    { subject: { name: "" }, condition: { name: { $ne: "foobar" } }, expected: true },
    { subject: { name: null }, condition: { name: { $ne: "foobar" } }, expected: true },
    { subject: {}, condition: { name: { $ne: "foobar" } }, expected: true },
  ])(`$condition with $subject -> $expected`, ({ subject, condition, expected }) => {
    expect(resolver.evaluate(subject, condition)).toBe(expected)
  })
})
