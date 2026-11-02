// The @ts-expect-error and expectTypeOf assertions here are checked by
// `yarn typecheck` (tsc), not at runtime: a directive whose line stops
// erroring fails the typecheck.
import type { PolicyDefinition } from "@cptn-fizzbin/keycard"
import { createSubject } from "@cptn-fizzbin/keycard"
import knex from "knex"
import { afterAll, describe, expect, expectTypeOf, test } from "vitest"

import type { ClaimsOf, ColumnMap, KnexPolicyOptions } from "./accessibleBy.ts"
import { accessibleBy, policyToSqlCondition } from "./accessibleBy.ts"

interface ArticleClaims {
  ownerId: number
  status: string
}

const Article = createSubject<ArticleClaims>("Article")
const Untyped = createSubject("Untyped")

const policy: PolicyDefinition = {
  version: "0.1",
  rules: [["allow", "Read", "Article", { ownerId: 1 }]],
}

const pg = knex({ client: "pg" })

afterAll(async () => {
  await pg.destroy()
})

describe("ClaimsOf", () => {
  test("is the claims type of a typed subject", () => {
    expectTypeOf<ClaimsOf<typeof Article>>().toEqualTypeOf<ArticleClaims>()
  })

  test("is unknown for a subject named by string or created without claims", () => {
    expectTypeOf<ClaimsOf<"Article">>().toBeUnknown()
    expectTypeOf<ClaimsOf<typeof Untyped>>().toBeUnknown()
  })
})

describe("ColumnMap", () => {
  test("has an optional string entry for each field of the claims", () => {
    expectTypeOf<ColumnMap<ArticleClaims>>().toEqualTypeOf<{ readonly ownerId?: string, readonly status?: string }>()
  })

  test("accepts any string key when the claims are unknown", () => {
    expectTypeOf<ColumnMap<unknown>>().toEqualTypeOf<Readonly<Record<string, string>>>()
  })
})

describe("accessibleBy columns", () => {
  test("accepts a record of the subject's fields, all or some of them", () => {
    accessibleBy(policy, "Read", Article, { columns: { ownerId: "owner_id", status: "status" } })
    accessibleBy(policy, "Read", Article, { columns: { ownerId: "owner_id" } })
  })

  test("rejects a field the subject doesn't have", () => {
    // @ts-expect-error - `authorId` isn't a field of ArticleClaims
    accessibleBy(policy, "Read", Article, { columns: { authorId: "author_id" } })
  })

  test("rejects a misspelled field alongside valid ones", () => {
    // @ts-expect-error - `owner_id` isn't a field of ArticleClaims (`ownerId` is)
    accessibleBy(policy, "Read", Article, { columns: { owner_id: "owner_id", status: "status" } })
  })

  test("rejects a column that isn't a string", () => {
    // @ts-expect-error - columns are named by string
    accessibleBy(policy, "Read", Article, { columns: { ownerId: 1 } })
  })

  test("rejects an options object typed for a different subject", () => {
    // Arrange
    const commentOptions: KnexPolicyOptions<{ body: string }> = { columns: { body: "body" } }

    // Assert
    // @ts-expect-error - `body` isn't a field of ArticleClaims
    accessibleBy(policy, "Read", Article, commentOptions)
  })

  test("accepts any field for a subject named by string or created without claims", () => {
    accessibleBy(policy, "Read", "Article", { columns: { anything: "anything" } })
    accessibleBy(policy, "Read", Untyped, { columns: { anything: "anything" } })
  })

  test("accepts a resolver function for any subject", () => {
    accessibleBy(policy, "Read", Article, { columns: (field) => `articles.${field}` })
  })

  test("applies the typed record at runtime", () => {
    // Act
    const { sql } = pg("articles")
      .where(accessibleBy(policy, "Read", Article, { columns: { ownerId: "articles.owner_id" } }))
      .toSQL()
      .toNative()

    // Assert
    expect(sql).toBe(`select * from "articles" where (("articles"."owner_id" is not null and "articles"."owner_id" = $1))`)
  })
})

describe("policyToSqlCondition columns", () => {
  test("accepts a record of the subject's fields", () => {
    policyToSqlCondition(policy, "Read", Article, { dialect: "postgresql", columns: { status: "status" } })
  })

  test("rejects a field the subject doesn't have", () => {
    // @ts-expect-error - `authorId` isn't a field of ArticleClaims
    policyToSqlCondition(policy, "Read", Article, { dialect: "postgresql", columns: { authorId: "author_id" } })
  })
})
