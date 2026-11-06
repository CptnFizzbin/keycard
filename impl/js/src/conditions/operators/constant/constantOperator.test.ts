import { describe, expect, test } from "vitest"

import type { JsonObject } from "../../../lib/json.ts"
import { ConditionResolver } from "../../conditionResolver.ts"

describe("$always / $never", () => {
  const resolver = new ConditionResolver()

  test.each([
    { subject: { f: 1 }, condition: { $always: true }, expected: true },
    { subject: null, condition: { $always: true }, expected: true },
    { subject: { f: null }, condition: { f: { $always: true } }, expected: true },
    { subject: {}, condition: { f: { $always: true } }, expected: false },
    { subject: { f: 1 }, condition: { $not: { $always: true } }, expected: false },
    { subject: { f: 1 }, condition: { $always: true, f: 2 }, expected: false },
    { subject: { f: 1 }, condition: { $never: true }, expected: false },
    { subject: null, condition: { $never: true }, expected: false },
    { subject: { f: 1 }, condition: { $not: { $never: true } }, expected: true },
    { subject: { f: 1 }, condition: { $or: [{ $never: true }, { f: 1 }] }, expected: true },
  ])(`$condition with $subject -> $expected`, ({ subject, condition, expected }) => {
    expect(resolver.evaluate(subject, condition as JsonObject)).toBe(expected)
  })

  test.each([
    { condition: { $always: false } },
    { condition: { $always: "true" } },
    { condition: { $always: 1 } },
    { condition: { $always: null } },
    { condition: { $never: false } },
    { condition: { $never: "true" } },
    { condition: { $never: 1 } },
    { condition: { $never: null } },
  ])(`$condition is malformed and evaluates to false`, ({ condition }) => {
    expect(resolver.evaluate({ f: 1 }, condition as JsonObject)).toBe(false)
  })
})
