import type { Knex } from "knex"

/** A comparison operator a {@link SqlCompare} node renders between a column and a bound value. */
export type SqlComparisonOperator = "=" | "<>" | ">" | ">=" | "<" | "<="

/** A scalar a {@link SqlCondition} binds as a query parameter. */
export type SqlScalar = string | number | boolean

/** A constant `TRUE`/`FALSE`, rendered portably (`1 = 1` / `1 = 0`) since not every dialect has boolean literals. */
export interface SqlConstant {
  readonly kind: "constant"
  readonly value: boolean
}

/** Every item must hold. */
export interface SqlAnd {
  readonly kind: "and"
  readonly items: readonly SqlCondition[]
}

/** At least one item must hold. */
export interface SqlOr {
  readonly kind: "or"
  readonly items: readonly SqlCondition[]
}

/** The negation of `item`. */
export interface SqlNot {
  readonly kind: "not"
  readonly item: SqlCondition
}

/** `column <operator> value`. Use {@link Sql.compare}, which guards against `NULL` columns. */
export interface SqlCompare {
  readonly kind: "compare"
  readonly column: string
  readonly operator: SqlComparisonOperator
  readonly value: SqlScalar
}

/** `column IS NULL`. */
export interface SqlIsNull {
  readonly kind: "isNull"
  readonly column: string
}

/** `column IN (values)`. Use {@link Sql.in}, which guards against `NULL` columns. */
export interface SqlIn {
  readonly kind: "in"
  readonly column: string
  readonly values: readonly SqlScalar[]
}

/** A raw SQL fragment, with `?` value and `??` identifier placeholders as in `knex.whereRaw`. */
export interface SqlRaw {
  readonly kind: "raw"
  readonly sql: string
  readonly bindings: readonly Knex.RawBinding[]
}

/** An arbitrary knex `where` callback, applied inside its own parenthesized group. */
export interface SqlCallback {
  readonly kind: "callback"
  readonly apply: (builder: Knex.QueryBuilder) => void
}

/**
 * A boolean SQL expression tree that renders to knex `where` clauses.
 *
 * Every condition built from {@link Sql} evaluates to `TRUE` or `FALSE`,
 * never SQL `NULL`, so negating it with {@link Sql.not} is the exact
 * negation the KeyCard spec requires. `raw` and `callback` conditions MUST
 * keep that guarantee themselves.
 */
export type SqlCondition =
  | SqlConstant
  | SqlAnd
  | SqlOr
  | SqlNot
  | SqlCompare
  | SqlIsNull
  | SqlIn
  | SqlRaw
  | SqlCallback

const TRUE: SqlConstant = Object.freeze({ kind: "constant", value: true })
const FALSE: SqlConstant = Object.freeze({ kind: "constant", value: false })

function and(...items: SqlCondition[]): SqlCondition {
  const flat: SqlCondition[] = []
  for (const item of items) {
    if (item.kind === "constant") {
      if (!item.value) return FALSE
      continue
    }
    if (item.kind === "and") flat.push(...item.items)
    else flat.push(item)
  }

  if (flat.length === 0) return TRUE
  if (flat.length === 1) return flat[0]
  return { kind: "and", items: flat }
}

function or(...items: SqlCondition[]): SqlCondition {
  const flat: SqlCondition[] = []
  for (const item of items) {
    if (item.kind === "constant") {
      if (item.value) return TRUE
      continue
    }
    if (item.kind === "or") flat.push(...item.items)
    else flat.push(item)
  }

  if (flat.length === 0) return FALSE
  if (flat.length === 1) return flat[0]
  return { kind: "or", items: flat }
}

function not(item: SqlCondition): SqlCondition {
  if (item.kind === "constant") return item.value ? FALSE : TRUE
  if (item.kind === "not") return item.item
  return { kind: "not", item }
}

function isNull(column: string): SqlCondition {
  return { kind: "isNull", column }
}

function isNotNull(column: string): SqlCondition {
  return not(isNull(column))
}

function compare(column: string, operator: SqlComparisonOperator, value: SqlScalar): SqlCondition {
  // SQL compares NULL to anything as NULL, not FALSE; NOT NULL is still NULL,
  // so without this guard a negated comparison would drop NULL rows the
  // spec says should match (explicit null is a real value, unequal to v).
  return and(isNotNull(column), { kind: "compare", column, operator, value })
}

function inList(column: string, values: readonly SqlScalar[]): SqlCondition {
  if (values.length === 0) return FALSE
  return and(isNotNull(column), { kind: "in", column, values: [...values] })
}

function raw(sql: string, bindings: readonly Knex.RawBinding[] = []): SqlCondition {
  return { kind: "raw", sql, bindings: [...bindings] }
}

function callback(apply: (builder: Knex.QueryBuilder) => void): SqlCondition {
  return { kind: "callback", apply }
}

/**
 * Constructors for {@link SqlCondition} trees, for writing custom operator
 * translations. `and`/`or`/`not` fold constants and flatten nesting, so
 * `Sql.and(Sql.TRUE, x)` is just `x`.
 *
 * ```ts
 * const $startsWith: KnexOperator = ({ column, value }) =>
 *   column && typeof value === "string"
 *     ? Sql.raw("?? LIKE ?", [column, `${value}%`])
 *     : Sql.FALSE
 * ```
 */
export const Sql = Object.freeze({
  TRUE,
  FALSE,
  and,
  or,
  not,
  isNull,
  isNotNull,
  compare,
  in: inList,
  raw,
  callback,
})
