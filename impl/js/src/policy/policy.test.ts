import semver from "semver"
import { describe, expect, test, vi } from "vitest"

import { Policy } from "./policy.ts"
import { createAction } from "../action/index.ts"
import { createOperator } from "../conditions/index.ts"
import { PolicyError, PolicyLoadException, PolicyVersionException } from "../errors/index.ts"
import { createSubject } from "../subject/index.ts"
import { KEYCARD_POLICY_VERSION } from "../version.ts"

const Delete = createAction("Delete")
const Read = createAction("Read")

describe("Policy: last-rule-wins evaluation", () => {
  test("a later-declared deny rule overrides an earlier allow for the same action/subject", () => {
    const policy = Policy.from({
      version: "0.1",
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
      version: "0.1",
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
      version: "0.1",
      rules: [
        ["deny", "Delete", "User"],
        ["allow", "Delete", "User"],
      ],
    })

    expect(policy.can(Delete, createSubject("User"))).toBe(true)
  })

  test("an empty rule list denies everything (EC-1)", () => {
    const policy = Policy.from({ version: "0.1", rules: [] })

    expect(policy.can(Read, createSubject("Article"))).toBe(false)
  })
})

describe("Policy: construction-time validation", () => {
  const { major, minor, patch } = semver.parse(semver.coerce(KEYCARD_POLICY_VERSION))!

  test("throws PolicyVersionException for an unsupported MAJOR version", () => {
    const nextMajor = [major + 1, 0, 0].join(".")
    expect(() => Policy.from({ version: nextMajor, rules: [] })).toThrow(PolicyVersionException)
  })

  test("throws PolicyVersionException for a MINOR newer than what's supported", () => {
    const nextMinor = [major, minor + 1, 0].join(".")
    expect(() => Policy.from({ version: nextMinor, rules: [] })).toThrow(PolicyVersionException)
  })

  test("ignores PATCH when deciding compatibility", () => {
    const nextPatch = [major, minor, patch + 1].join(".")
    expect(() => Policy.from({ version: nextPatch, rules: [] })).not.toThrow()
  })

  test("throws PolicyLoadException for a malformed rule tuple (EC-10)", () => {
    expect(() =>
      // @ts-expect-error -- explicitly testing invalid types
      Policy.from({ version: "0.1", rules: [["allow", "Read"]] }),
    ).toThrow(PolicyLoadException)
  })

  test("throws PolicyLoadException for an effect that isn't allow/deny (EC-10)", () => {
    expect(() =>
      // @ts-expect-error -- explicitly testing invalid types
      Policy.from({ version: "0.1", rules: [["maybe", "Read", "Article"]] }),
    ).toThrow(PolicyLoadException)
  })

  test("throws PolicyLoadException for a rule wildcarded on both sides carrying a condition (EC-6)", () => {
    expect(() =>
      Policy.from({
        version: "0.1",
        rules: [["allow", "_ANY_", "_ANY_", { owner_id: 1 }]],
      }),
    ).toThrow(PolicyLoadException)
  })

  test("throws PolicyLoadException when a rule's action isn't covered by a declared meta.actions catalog (EC-8)", () => {
    expect(() =>
      Policy.from({
        version: "0.1",
        meta: { actions: ["Read"] },
        rules: [["allow", "Write", "Article"]],
      }),
    ).toThrow(PolicyLoadException)
  })

  test("throws PolicyLoadException when a rule uses a custom operator outside a declared meta.operators catalog (EC-13)", () => {
    expect(() =>
      Policy.from({
        version: "0.1",
        meta: { operators: ["$hasRole"] },
        rules: [["allow", "Read", "Article", { $isAdmin: true }]],
      }),
    ).toThrow(PolicyLoadException)
  })

  // --- operator registry collisions ---

  test("throws PolicyLoadException when a custom operator collides with a builtin", () => {
    expect(() =>
      Policy.from(
        { version: "0.1", rules: [] },
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
        { version: "0.1", rules: [] },
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
    // checked in full when loading a policy, not merely for names rules
    // actually use.
    expect(() =>
      Policy.from({
        version: "0.1",
        meta: { operators: ["$hasRole"] },
        rules: [],
      }),
    ).toThrow(PolicyLoadException)
  })

  test("meta.operators is satisfied by a builtin name", () => {
    expect(() =>
      Policy.from({ version: "0.1", meta: { operators: ["$eq"] }, rules: [] }),
    ).not.toThrow()
  })

  test("meta.operators is satisfied by a registered custom operator", () => {
    expect(() =>
      Policy.from(
        { version: "0.1", meta: { operators: ["$hasRole"] }, rules: [] },
        {
          operators: [
            createOperator("$hasRole", () => true),
          ],
        },
      ),
    ).not.toThrow()
  })
})

describe("Policy: dynamic (no-name) Action/Subject resolved via a KeycardConfig catalog", () => {
  test("a dynamic def resolves via its catalog key to match a rule written against that key", () => {
    const create = createAction()
    const article = createSubject()

    const policy = Policy.from(
      { version: "0.1", rules: [["allow", "create", "article"]] },
      { actions: { create }, subjects: { article } },
    )

    expect(policy.can(create, article)).toBe(true)
  })

  test("EC-8 coverage is still enforced using catalog-resolved names", () => {
    expect(() =>
      Policy.from(
        { version: "0.1", rules: [["allow", "write", "article"]] },
        { actions: { read: createAction() } },
      ),
    ).toThrow(PolicyLoadException)
  })

  test("an unregistered dynamic Action passed to .can() warns once (deduped) via config.logger and denies", () => {
    const logger = { info: vi.fn(), warn: vi.fn(), error: vi.fn() }
    const ghost = createAction()
    const article = createSubject("Article")

    const policy = Policy.from(
      { version: "0.1", rules: [["allow", "Read", "Article"]] },
      { logger },
    )

    expect(policy.can(ghost, article)).toBe(false)
    expect(policy.can(ghost, article)).toBe(false)
    expect(logger.warn).toHaveBeenCalledTimes(1)
  })

  test("an unregistered dynamic def still falls through to a matching wildcard rule", () => {
    const logger = { info: vi.fn(), warn: vi.fn(), error: vi.fn() }
    const ghost = createAction()

    const policy = Policy.from(
      { version: "0.1", meta: { anyAction: "_ANY_" }, rules: [["allow", "_ANY_", "Article"]] },
      { logger },
    )

    expect(policy.can(ghost, createSubject("Article"))).toBe(true)
  })

  test("omitting config.logger falls back to the module-level logger without throwing", () => {
    const ghost = createAction()
    const policy = Policy.from({ version: "0.1", rules: [] })

    expect(() => policy.can(ghost, createSubject("Article"))).not.toThrow()
  })
})

describe("Policy: require() error message", () => {
  test("names the action and subject", () => {
    const policy = Policy.from({ version: "0.1", rules: [] })

    expect(() => policy.require(Delete, createSubject("Article"))).toThrow(PolicyError)
    expect(() => policy.require(Delete, createSubject("Article"))).toThrow(
      "\"Delete\" is not allowed on this \"Article\"",
    )
  })
})

describe("Policy: KeycardConfig.operators accepts an OperatorCatalog", () => {
  test("a bare { $name: resolver } map is normalized the same as createOperator()", () => {
    const article = createSubject<{ id: number }>("Article")

    const policy = Policy.from(
      { version: "0.1", rules: [["allow", "Read", "Article", { $hasRole: "admin" }]] },
      { operators: { $hasRole: () => true } },
    )

    expect(policy.can(Read, article.wrap({ id: 1 }))).toBe(true)
  })
})

describe("Policy: emitMeta", () => {
  test("false: skips meta.actions/subjects coverage and meta.operators registration checks at construction", () => {
    expect(() =>
      Policy.from(
        { version: "0.1", meta: { actions: ["Read"], operators: ["$hasRole"] }, rules: [["allow", "Write", "Article"]] },
        { emitMeta: false },
      ),
    ).not.toThrow()
  })

  test("false: still runs the structural checks (malformed rule tuples, EC-6)", () => {
    expect(() =>
      Policy.from(
        { version: "0.1", rules: [["allow", "_ANY_", "_ANY_", { owner_id: 1 }]] },
        { emitMeta: false },
      ),
    ).toThrow(PolicyLoadException)
  })

  test("false: a duplicate KeycardConfig catalog key no longer throws at construction", () => {
    const create = createAction()

    expect(() =>
      Policy.from(
        { version: "0.1", rules: [] },
        { emitMeta: false, actions: { create, submit: create } },
      ),
    ).not.toThrow()
  })
})
