import { describe, expect, test } from "vitest"

import { Policy } from "./policy.ts"
import { createAction } from "../action/index.ts"
import { createOperator } from "../conditions/index.ts"
import { PolicyLoadException, PolicyVersionException } from "../errors/index.ts"
import { createSubject } from "../subject/index.ts"

const Delete = createAction("Delete")
const Read = createAction("Read")

describe("Policy: last-rule-wins evaluation (SPEC_V1-0-0.md §6)", () => {
  test("a later-declared deny rule overrides an earlier allow for the same action/subject", () => {
    const policy = Policy.from({
      version: "1.0.0",
      rules: [
        ["allow", "Delete", "Article"],
        ["deny", "Delete", "Article"],
      ],
    })

    const article = createSubject("Article")

    expect(policy.can(Delete, article)).toBe(false)
    expect(policy.cannot(Delete, article)).toBe(true)
  })

  test("a conditional deny rule only overrides allow when its condition matches", () => {
    const policy = Policy.from({
      version: "1.0.0",
      rules: [
        ["allow", "Delete", "Article"],
        ["deny", "Delete", "Article", { status: "archived" }],
      ],
    })

    const article = createSubject<{ status: string }>("Article")
    const archived = article.wrap({ status: "archived" })
    const published = article.wrap({ status: "published" })

    expect(policy.can(Delete, archived)).toBe(false)
    expect(policy.can(Delete, published)).toBe(true)
  })

  test("a later allow reopens what an earlier deny closed", () => {
    const policy = Policy.from({
      version: "1.0.0",
      rules: [
        ["deny", "Delete", "User"],
        ["allow", "Delete", "User"],
      ],
    })

    expect(policy.can(Delete, createSubject("User"))).toBe(true)
  })

  test("an empty rule list denies everything (EC-1)", () => {
    const policy = Policy.from({ version: "1.0.0", rules: [] })

    expect(policy.can(Read, createSubject("Article"))).toBe(false)
  })
})

describe("Policy: construction-time validation", () => {
  test("throws PolicyVersionException for an unsupported MAJOR version", () => {
    expect(() => Policy.from({ version: "2.0.0", rules: [] })).toThrow(PolicyVersionException)
  })

  test("throws PolicyVersionException for a MINOR newer than what's supported", () => {
    expect(() => Policy.from({ version: "1.99.0", rules: [] })).toThrow(PolicyVersionException)
  })

  test("ignores PATCH when deciding compatibility", () => {
    expect(() => Policy.from({ version: "1.0.99", rules: [] })).not.toThrow()
  })

  test("throws PolicyLoadException for a malformed rule tuple (EC-10)", () => {
    expect(() =>
      // @ts-expect-error -- explicitly testing invalid types
      Policy.from({ version: "1.0.0", rules: [["allow", "Read"]] }),
    ).toThrow(PolicyLoadException)
  })

  test("throws PolicyLoadException for an effect that isn't allow/deny (EC-10)", () => {
    expect(() =>
      // @ts-expect-error -- explicitly testing invalid types
      Policy.from({ version: "1.0.0", rules: [["maybe", "Read", "Article"]] }),
    ).toThrow(PolicyLoadException)
  })

  test("throws PolicyLoadException for a rule wildcarded on both sides carrying a condition (EC-6)", () => {
    expect(() =>
      Policy.from({
        version: "1.0.0",
        rules: [["allow", "_ANY_", "_ANY_", { owner_id: 1 }]],
      }),
    ).toThrow(PolicyLoadException)
  })

  test("throws PolicyLoadException when a rule's action isn't covered by a declared meta.actions catalog (EC-8)", () => {
    expect(() =>
      Policy.from({
        version: "1.0.0",
        meta: { actions: ["Read"] },
        rules: [["allow", "Write", "Article"]],
      }),
    ).toThrow(PolicyLoadException)
  })

  test("throws PolicyLoadException when a rule uses a custom operator outside a declared meta.operators catalog (EC-13)", () => {
    expect(() =>
      Policy.from({
        version: "1.0.0",
        meta: { operators: ["$hasRole"] },
        rules: [["allow", "Read", "Article", { $isAdmin: true }]],
      }),
    ).toThrow(PolicyLoadException)
  })

  // --- operator registry collisions (SPEC_V1-0-0.md §3.2.3, EC-16) ---

  test("throws PolicyLoadException when a custom operator collides with a builtin", () => {
    expect(() =>
      Policy.from(
        { version: "1.0.0", rules: [] },
        {
          operators: [
            createOperator("$eq", () => true),
          ],
        },
      ),
    ).toThrow(PolicyLoadException)
  })

  test("throws PolicyLoadException when two custom operators collide with each other", () => {
    expect(() =>
      Policy.from(
        { version: "1.0.0", rules: [] },
        {
          operators: [
            createOperator("$hasRole", () => true),
            createOperator("$hasRole", () => false),
          ],
        },
      ),
    ).toThrow(PolicyLoadException)
  })

  // --- meta.operators promotes "cataloged but never registered" to a construction-time throw (EC-15) ---

  test("throws PolicyLoadException when meta.operators declares a name nothing is registered for", () => {
    // Unlike EC-13 above, this throws even though no rule references
    // $hasRole at all - meta.operators' registration requirement is
    // checked in full at construction time, not merely for names rules
    // actually use.
    expect(() =>
      Policy.from({
        version: "1.0.0",
        meta: { operators: ["$hasRole"] },
        rules: [],
      }),
    ).toThrow(PolicyLoadException)
  })

  test("meta.operators is satisfied by a builtin name", () => {
    expect(() =>
      Policy.from({ version: "1.0.0", meta: { operators: ["$eq"] }, rules: [] }),
    ).not.toThrow()
  })

  test("meta.operators is satisfied by a registered custom operator", () => {
    expect(() =>
      Policy.from(
        { version: "1.0.0", meta: { operators: ["$hasRole"] }, rules: [] },
        {
          operators: [
            createOperator("$hasRole", () => true),
          ],
        },
      ),
    ).not.toThrow()
  })
})
