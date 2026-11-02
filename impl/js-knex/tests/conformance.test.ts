import * as fs from "node:fs"
import * as path from "node:path"
import { DatabaseSync } from "node:sqlite"

import type { PolicyDefinition } from "@cptn-fizzbin/keycard"
import { createAction, createOperator, createSubject, Policy } from "@cptn-fizzbin/keycard"
import { PGlite } from "@electric-sql/pglite"
import knex from "knex"
import { afterAll, beforeAll, describe, expect, test } from "vitest"
import * as YAML from "yaml"

import type { JsonValue, KnexOperatorsRecord } from "../src/index.ts"
import { accessibleBy, KeycardKnexError, Sql } from "../src/index.ts"

const FIXTURES_DIR = path.resolve(import.meta.dirname, "../../../test/fixtures/v0")

type Claims = Record<string, JsonValue>

interface FixtureCase {
  [key: string]: JsonValue
  name?: string
  check: [string, string] | [string, string, JsonValue]
  expected: boolean
}

interface FixtureSuite extends PolicyDefinition {
  tests: FixtureCase[]
}

const jsOperators = [
  createOperator("$hasRole", (subject, value) => {
    const roles = subject && typeof subject === "object" ? (subject as Record<string, unknown>).roles : undefined
    return Array.isArray(roles) && roles.includes(value)
  }),
  createOperator("$startsWithUpper", (subject, expected) => {
    if (typeof subject !== "string" || typeof expected !== "boolean") return false
    return (subject[0].toUpperCase() === subject[0]) === expected
  }),
]

const knexOperators: KnexOperatorsRecord = {
  $hasRole: (role, ctx) => ctx.column === null ? ctx.translateField("roles", { $has: role }) : Sql.FALSE,
  $startsWithUpper: (expected, { column }) => {
    if (column === null || typeof expected !== "boolean") return Sql.FALSE
    return Sql.and(
      Sql.isNotNull(column),
      Sql.raw(`substr(??, 1, 1) ${expected ? "=" : "<>"} upper(substr(??, 1, 1))`, [column, column]),
    )
  },
}

function loadSuites(): { file: string, suite: FixtureSuite }[] {
  return fs.readdirSync(FIXTURES_DIR)
    .filter((file) => file.endsWith(".yaml"))
    .flatMap((file) => YAML.parseAllDocuments(fs.readFileSync(path.join(FIXTURES_DIR, file), "utf-8"))
      .map((doc) => ({ file, suite: doc.toJSON() as FixtureSuite })))
}

function isClaims(value: JsonValue): value is Claims {
  return typeof value === "object" && value !== null && !Array.isArray(value)
}

function scalarPgType(value: JsonValue): string | undefined {
  if (typeof value === "string") return "text"
  if (typeof value === "boolean") return "boolean"
  if (typeof value === "number") return "double precision"
  return undefined
}

function pgTypeOf(value: JsonValue): string | undefined {
  if (!Array.isArray(value)) return scalarPgType(value)
  const element = value.map(scalarPgType).find((type) => type !== undefined)
  return `${element ?? "text"}[]`
}

/** The Postgres column type for `key`: the type of the first non-null value any case in the suite gives it. */
function columnTypeFor(suite: FixtureSuite, key: string): string {
  for (const testCase of suite.tests) {
    const claims = testCase.check[2]
    if (!isClaims(claims) || !Object.hasOwn(claims, key)) continue
    const type = pgTypeOf(claims[key])
    if (type) return type
  }
  return "text"
}

/** Whether every claim fits its column's type - a SQL column holds one type, where a subject field can hold any. */
function fitsColumnTypes(suite: FixtureSuite, claims: Claims): boolean {
  return Object.entries(claims).every(([key, value]) => value === null || pgTypeOf(value) === columnTypeFor(suite, key))
}

interface Database {
  readonly dialect: string
  /** Inserts `claims` as the table's only row and returns whether `query` selects it, or `undefined` when the row can't be stored. */
  run(suite: FixtureSuite, claims: Claims, buildWhere: ReturnType<typeof accessibleBy>): Promise<boolean | undefined>
}

const sqliteKnex = knex({ client: "sqlite3", useNullAsDefault: true })
const pgKnex = knex({ client: "pg" })

function sqliteValue(value: JsonValue): JsonValue {
  return typeof value === "boolean" ? Number(value) : value
}

function createSqlite(): Database {
  const db = new DatabaseSync(":memory:")
  return {
    dialect: "sqlite3",
    run(_suite, claims, buildWhere) {
      if (Object.values(claims).some((value) => typeof value === "object" && value !== null)) return Promise.resolve(undefined)

      const keys = Object.keys(claims)
      db.exec("DROP TABLE IF EXISTS subjects")
      db.exec(`CREATE TABLE subjects (__row INTEGER${keys.map((key) => `, "${key}"`).join("")})`)
      db.prepare(`INSERT INTO subjects VALUES (1${keys.map(() => ", ?").join("")})`)
        .run(...keys.map((key) => sqliteValue(claims[key]) as string | number | null))

      const { sql, bindings } = sqliteKnex("subjects").select("__row").where(buildWhere).toSQL().toNative()
      const rows = db.prepare(sql).all(...bindings.map((value) => sqliteValue(value as JsonValue) as string | number | null))
      return Promise.resolve(rows.length === 1)
    },
  }
}

function createPostgres(pg: PGlite): Database {
  return {
    dialect: "postgresql",
    async run(suite, claims, buildWhere) {
      if (Object.values(claims).some((value) => isClaims(value))) return undefined

      const keys = Object.keys(claims)
      await pg.exec("DROP TABLE IF EXISTS subjects")
      await pg.exec(`CREATE TABLE subjects (__row integer${keys.map((key) => `, "${key}" ${columnTypeFor(suite, key)}`).join("")})`)
      await pg.query(
        `INSERT INTO subjects VALUES (1${keys.map((_, i) => `, $${i + 1}`).join("")})`,
        keys.map((key) => claims[key]),
      )

      const { sql, bindings } = pgKnex("subjects").select("__row").where(buildWhere).toSQL().toNative()
      try {
        const result = await pg.query(sql, [...bindings])
        return result.rows.length === 1
      } catch (e) {
        // Postgres rejects comparing mismatched types (text > integer) where
        // the spec evaluates to false; such cases aren't comparable here.
        if (e instanceof Error && "code" in e && ["42883", "42804", "22P02"].includes(String(e.code))) return undefined
        throw e
      }
    },
  }
}

const suites = loadSuites()
let pglite: PGlite

beforeAll(async () => {
  pglite = await PGlite.create()
})

afterAll(async () => {
  await pglite.close()
  await sqliteKnex.destroy()
  await pgKnex.destroy()
})

test("discovers the conformance fixtures", () => {
  expect(suites.length).toBeGreaterThan(0)
})

describe.each(["sqlite3", "postgresql"])("%s", (dialect) => {
  describe.each(suites)("$file: $suite.name", ({ suite }) => {
    const instanceCases = suite.tests.filter((testCase) => testCase.check.length === 3 && isClaims(testCase.check[2]))
    if (instanceCases.length === 0) {
      test.skip("no cases with object instance data")
      return
    }

    test.for(instanceCases)("$name", async (testCase, ctx) => {
      const [action, subject, claims] = testCase.check as [string, string, Claims]
      const policy = Policy.from(suite, { operators: jsOperators })
      const db = dialect === "sqlite3" ? createSqlite() : createPostgres(pglite)

      // Every claim key is a column, so a key the case leaves out is a
      // missing field, as it is for policy.can.
      const columns = Object.fromEntries(Object.keys(claims).map((key) => [key, key]))
      const where = accessibleBy(policy, action, subject, { columns, operators: knexOperators })

      if (!fitsColumnTypes(suite, claims)) ctx.skip("a claim's type differs from the type other cases give its column")

      let selected: boolean | undefined
      try {
        selected = await db.run(suite, claims, where)
      } catch (e) {
        if (e instanceof KeycardKnexError) ctx.skip(e.message)
        throw e
      }
      if (selected === undefined) ctx.skip(`${dialect} can't represent this case's data`)

      expect(policy.can(createAction(action), createSubject<Claims>(subject).wrap(claims))).toBe(testCase.expected)
      expect(selected).toBe(testCase.expected)
    })
  })
})
