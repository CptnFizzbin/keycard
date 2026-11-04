import { describe, expect, test } from "vitest"

import { Policy } from "./policy.ts"
import type { RuleTuple } from "./policyDefinition.ts"
import { createAction } from "../action/index.ts"
import { ConditionResolver } from "../conditions/index.ts"
import { createSubject } from "../subject/index.ts"

const Read = createAction("Read")
const Article = createSubject<{ ownerId?: number, status?: string }>("Article")

function policyOf(...rules: RuleTuple[]): Policy {
  return Policy.from({ version: "0.1", rules })
}

describe("Policy.getCondition", () => {
  test("is never-true when no rule matches", () => {
    const policy = policyOf(["allow", "Update", "Article"], ["allow", "Read", "Comment"])

    expect(policy.getCondition(Read, Article)).toEqual({ $or: [] })
  })

  test("is always-true for an unconditional allow", () => {
    const policy = policyOf(["allow", "Read", "Article"])

    expect(policy.getCondition(Read, Article)).toEqual({ $and: [] })
  })

  test("is a single matching rule's own condition", () => {
    const policy = policyOf(["allow", "Read", "Article", { ownerId: 1 }])

    expect(policy.getCondition(Read, Article)).toEqual({ ownerId: 1 })
  })

  test("ORs allow rules together", () => {
    const policy = policyOf(
      ["allow", "Read", "Article", { status: "published" }],
      ["allow", "Read", "Article", { ownerId: 7 }],
    )

    expect(policy.getCondition(Read, Article)).toEqual({ $or: [{ status: "published" }, { ownerId: 7 }] })
  })

  test("a deny rule negates its condition over the rules before it, but not after", () => {
    // Arrange
    const policy = policyOf(
      ["allow", "Read", "Article"],
      ["deny", "Read", "Article", { status: "archived" }],
      ["allow", "Read", "Article", { ownerId: 7 }],
    )

    // Act
    const condition = policy.getCondition(Read, Article)

    // Assert
    expect(condition).toEqual({ $or: [{ $not: { status: "archived" } }, { ownerId: 7 }] })
  })

  test("an unconditional rule discards every rule before it", () => {
    const policy = policyOf(
      ["allow", "Read", "Article", { ownerId: 7 }],
      ["deny", "Read", "_ANY_"],
      ["allow", "Read", "Article", { status: "published" }],
    )

    expect(policy.getCondition(Read, Article)).toEqual({ status: "published" })
  })

  test("includes wildcard rules and honors a disabled wildcard", () => {
    // Arrange
    const policy = Policy.from({
      version: "0.1",
      meta: { anySubject: null },
      rules: [["allow", "_ANY_", "Article", { ownerId: 1 }], ["allow", "Read", "_ANY_"]],
    })

    // Act
    const condition = policy.getCondition(Read, Article)

    // Assert
    expect(condition).toEqual({ ownerId: 1 })
  })

  test.each([
    [{ ownerId: 7, status: "archived" }, true],
    [{ ownerId: 1, status: "archived" }, false],
    [{ ownerId: 1, status: "published" }, true],
    [{ ownerId: 1 }, true],
    [{}, true],
  ])("evaluates like can() for %j", (claims, expected) => {
    // Arrange
    const policy = policyOf(
      ["allow", "Read", "Article"],
      ["deny", "Read", "Article", { status: "archived" }],
      ["allow", "Read", "Article", { ownerId: 7 }],
    )

    // Act
    const viaCondition = new ConditionResolver().evaluate(claims, policy.getCondition(Read, Article))

    // Assert
    expect(viaCondition).toBe(expected)
    expect(policy.can(Read, Article.wrap(claims))).toBe(expected)
  })
})
