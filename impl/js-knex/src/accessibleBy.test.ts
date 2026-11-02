import type { PolicyDefinition, RuleTuple, Policy } from "@cptn-fizzbin/keycard"
import { createAction, createSubject, PolicyBuilder } from "@cptn-fizzbin/keycard"
import knex from "knex"
import { afterAll, describe, expect, test, vi } from "vitest"

import type { KnexPolicyOptions } from "./accessibleBy.ts"
import { accessibleBy, policyToSqlCondition } from "./accessibleBy.ts"
import { KeycardKnexError } from "./keycardKnexError.ts"
import { Sql } from "./sqlCondition.ts"

const pg = knex({ client: "pg" })
const sqlite = knex({ client: "sqlite3", useNullAsDefault: true })

afterAll(async () => {
  await pg.destroy()
  await sqlite.destroy()
})

function definition(...rules: RuleTuple[]): PolicyDefinition {
  return { version: "0.1", rules }
}

/** Returns the SQL inside the group `.where(accessibleBy(...))` adds, and its bindings. */
function whereSql(policy: Policy | PolicyDefinition, options: KnexPolicyOptions = {}, db = pg) {
  const { sql, bindings } = db("articles").where(accessibleBy(policy, "Read", "Article", options)).toSQL().toNative()
  const prefix = /^select \* from .articles. where \(/
  expect(sql).toMatch(prefix)
  expect(sql.endsWith(")")).toBe(true)
  return { sql: sql.replace(prefix, "").slice(0, -1), bindings }
}

describe("accessibleBy", () => {
  test("a policy with no matching rule selects nothing", () => {
    // Arrange
    const policy = definition(["allow", "Update", "Article"], ["allow", "Read", "Comment"])

    // Act
    const { sql } = whereSql(policy)

    // Assert
    expect(sql).toBe(`1 = 0`)
  })

  test("an unconditional allow selects everything", () => {
    const { sql } = whereSql(definition(["allow", "Read", "Article"]))

    expect(sql).toBe(`1 = 1`)
  })

  test("a later unconditional deny overrides every earlier rule", () => {
    const policy = definition(["allow", "Read", "Article", { ownerId: 1 }], ["deny", "Read", "_ANY_"])

    const { sql } = whereSql(policy)

    expect(sql).toBe(`1 = 0`)
  })

  test("translates conditions, guarding comparisons against NULL", () => {
    // Arrange
    const policy = definition(
      ["allow", "Read", "Article", { ownerId: 1 }],
      ["allow", "Read", "Article", { status: { $in: ["published", null] } }],
    )

    // Act
    const { sql, bindings } = whereSql(policy)

    // Assert
    expect(sql).toBe(
      `(("ownerId" is not null and "ownerId" = $1)`
      + ` or ("status" is not null and "status" in ($2)) or "status" is null)`,
    )
    expect(bindings).toEqual([1, "published"])
  })

  test("a deny rule excludes the rows its condition matches from earlier allows", () => {
    // Arrange
    const policy = definition(
      ["allow", "Read", "Article"],
      ["deny", "Read", "Article", { status: "archived" }],
      ["allow", "Read", "Article", { ownerId: 7 }],
    )

    // Act
    const { sql, bindings } = whereSql(policy)

    // Assert
    expect(sql).toBe(
      `(not ("status" is not null and "status" = $1)`
      + ` or ("ownerId" is not null and "ownerId" = $2))`,
    )
    expect(bindings).toEqual(["archived", 7])
  })

  test("accepts a Policy and Action/Subject objects", () => {
    // Arrange
    const Read = createAction("Read")
    const Article = createSubject<{ ownerId: number }>("Article")
    const policy = new PolicyBuilder<typeof Read, typeof Article>()
      .allow(Read, Article, { ownerId: 3 })
      .build()

    // Act
    const { sql, bindings } = pg("articles").where(accessibleBy(policy, Read, Article)).toSQL().toNative()

    // Assert
    expect(sql).toBe(`select * from "articles" where (("ownerId" is not null and "ownerId" = $1))`)
    expect(bindings).toEqual([3])
  })

  test("honors a custom subject wildcard and ignores a disabled action wildcard", () => {
    // Arrange
    const policy: PolicyDefinition = {
      version: "0.1",
      meta: { anyAction: null, anySubject: "*" },
      rules: [["allow", "_ANY_", "Article"], ["allow", "Read", "*", { ownerId: 1 }]],
    }

    // Act
    const { sql } = whereSql(policy)

    // Assert
    expect(sql).toBe(`("ownerId" is not null and "ownerId" = $1)`)
  })
})

describe("columns", () => {
  test("maps field names through a record", () => {
    const policy = definition(["allow", "Read", "Article", { ownerId: 1 }])

    const { sql } = whereSql(policy, { columns: { ownerId: "articles.owner_id" } })

    expect(sql).toBe(`("articles"."owner_id" is not null and "articles"."owner_id" = $1)`)
  })

  test("treats a field missing from the record as a missing field", () => {
    // Arrange
    const policy = definition(
      ["allow", "Read", "Article", { secret: 1 }],
      ["allow", "Read", "Article", { $field: ["secret", { $gt: 1 }] }],
    )

    // Act
    const { sql } = whereSql(policy, { columns: { ownerId: "owner_id" } })

    // Assert
    expect(sql).toBe(`1 = 0`)
  })

  test("a bare $ne on a missing field matches every row", () => {
    const policy = definition(["allow", "Read", "Article", { secret: { $ne: 1 } }])

    const { sql } = whereSql(policy, { columns: () => undefined })

    expect(sql).toBe(`1 = 1`)
  })

  test("by default, a field naming another table is a missing field", () => {
    const policy = definition(["allow", "Read", "Article", { "users.password": "x" }])

    const { sql } = whereSql(policy)

    expect(sql).toBe(`1 = 0`)
  })
})

describe("operators", () => {
  test("throws for an operator with no Knex translation", () => {
    const policy = definition(["allow", "Read", "Article", { $hasRole: "admin" }])

    expect(() => whereSql(policy)).toThrow(KeycardKnexError)
  })

  test("uses a custom operator translation", () => {
    // Arrange
    const policy = definition(["allow", "Read", "Article", { $hasRole: "admin" }])
    const $hasRole = vi.fn(() => Sql.raw("? = ANY(??)", ["admin", "roles"]))

    // Act
    const { sql, bindings } = whereSql(policy, { operators: { $hasRole } })

    // Assert
    expect(sql).toBe(`$1 = ANY("roles")`)
    expect(bindings).toEqual(["admin"])
    expect($hasRole).toHaveBeenCalledWith("admin", expect.objectContaining({ column: null, dialect: "postgresql" }))
  })

  test("$has translates to array_position on Postgres", () => {
    const policy = definition(["allow", "Read", "Article", { tags: { $has: "news" } }])

    const { sql } = whereSql(policy)

    expect(sql).toBe(`array_position("tags", $1) IS NOT NULL`)
  })

  test("$has throws on dialects without array columns, unless overridden", () => {
    // Arrange
    const policy = definition(["allow", "Read", "Article", { tags: { $has: "news" } }])
    const options: KnexPolicyOptions = {
      operators: { $has: (value, { column }) => Sql.raw("?? LIKE ?", [column!, `%,${String(value)},%`]) },
    }

    // Act
    const overridden = whereSql(policy, options, sqlite)

    // Assert
    expect(() => whereSql(policy, {}, sqlite)).toThrow(KeycardKnexError)
    expect(overridden.sql).toBe("`tags` LIKE ?")
  })

  test("$substr uses GLOB on SQLite and LIKE elsewhere", () => {
    const policy = definition(["allow", "Read", "Article", { title: { $substr: "^Draft:*" } }])

    const sqliteSql = whereSql(policy, {}, sqlite)
    const pgSql = whereSql(policy)

    expect(sqliteSql.sql).toBe("(`title` is not null and `title` GLOB ?)")
    expect(sqliteSql.bindings).toEqual(["Draft:*"])
    expect(pgSql.sql).toBe(`("title" is not null and "title" LIKE $1 ESCAPE '!')`)
    expect(pgSql.bindings).toEqual(["Draft:%"])
  })

  test("the dialect option overrides the query builder's dialect", () => {
    const policy = definition(["allow", "Read", "Article", { tags: { $has: "news" } }])

    const { sql } = whereSql(policy, { dialect: "postgresql" }, sqlite)

    expect(sql).toBe("array_position(`tags`, ?) IS NOT NULL")
  })

  test("a nested field condition translates to FALSE with a warning", () => {
    // Arrange
    const policy = definition(["allow", "Read", "Article", { author: { name: "Alice" } }])
    const logger = { info: vi.fn(), warn: vi.fn(), error: vi.fn() }

    // Act
    const { sql } = whereSql(policy, { logger })

    // Assert
    expect(sql).toBe(`1 = 0`)
    expect(logger.warn).toHaveBeenCalledOnce()
  })
})

describe("policyToSqlCondition", () => {
  test("returns Sql.TRUE and Sql.FALSE for policies that don't depend on the row", () => {
    const allowAll = definition(["allow", "Read", "Article"])
    const denyAll = definition(["allow", "Read", "Article"], ["deny", "Read", "Article"])

    expect(policyToSqlCondition(allowAll, "Read", "Article", { dialect: "postgresql" })).toBe(Sql.TRUE)
    expect(policyToSqlCondition(denyAll, "Read", "Article", { dialect: "postgresql" })).toBe(Sql.FALSE)
  })
})
