import type { JsonValue } from "./json.ts"
import { KeycardKnexError } from "./keycardKnexError.ts"
import type { KnexOperator, KnexOperatorContext, KnexOperatorsRecord } from "./knexOperator.ts"
import type { SqlComparisonOperator, SqlCondition, SqlScalar } from "./sqlCondition.ts"
import { Sql } from "./sqlCondition.ts"
import { LIKE_ESCAPE, parseSubstrPattern, toGlobPattern, toLikePattern } from "./substrPattern.ts"

const POSTGRES_DIALECTS = new Set(["postgresql", "pg", "pgnative", "cockroachdb", "redshift"])
const SQLITE_DIALECTS = new Set(["sqlite3", "better-sqlite3"])

function isScalar(value: JsonValue): value is SqlScalar {
  return typeof value === "string"
    || typeof value === "boolean"
    || (typeof value === "number" && !Number.isNaN(value))
}

/** `column = value` with the spec's `$eq` semantics: `NULL`-safe, and `FALSE` for values SQL can't compare (`NaN`, arrays, objects). */
function equals(value: JsonValue, ctx: KnexOperatorContext): SqlCondition {
  // The row itself is an object, which equals no scalar.
  if (ctx.column === null) return Sql.FALSE
  if (value === null) return Sql.isNull(ctx.column)
  if (isScalar(value)) return Sql.compare(ctx.column, "=", value)

  if (typeof value !== "number") {
    ctx.logger.warn(`$eq against ${JSON.stringify(value)} has no SQL translation; it translates to FALSE.`)
  }
  return Sql.FALSE
}

function numericCompare(operator: SqlComparisonOperator): KnexOperator {
  return (value, ctx) => {
    if (ctx.column === null) return Sql.FALSE
    if (typeof value !== "number" || Number.isNaN(value)) {
      ctx.logger.warn(`Numeric comparison against non-number ${JSON.stringify(value)} translates to FALSE.`)
      return Sql.FALSE
    }
    return Sql.compare(ctx.column, operator, value)
  }
}

function logicalList(kind: "$and" | "$or"): KnexOperator {
  return (value, ctx) => {
    if (!Array.isArray(value)) {
      ctx.logger.warn(`${kind} expects an array of conditions, received ${JSON.stringify(value)}; it translates to FALSE.`)
      return Sql.FALSE
    }
    const items = value.map((item) => ctx.translate(item))
    return kind === "$and" ? Sql.and(...items) : Sql.or(...items)
  }
}

const $in: KnexOperator = (value, ctx) => {
  if (!Array.isArray(value)) {
    ctx.logger.warn(`$in expects an array, received ${JSON.stringify(value)}; it translates to FALSE.`)
    return Sql.FALSE
  }
  if (ctx.column === null) return Sql.FALSE

  const column = ctx.column
  const scalars = value.filter(isScalar)
  return Sql.or(
    Sql.in(column, scalars),
    value.includes(null) ? Sql.isNull(column) : Sql.FALSE,
  )
}

const $has: KnexOperator = (value, ctx) => {
  if (ctx.column === null) return Sql.FALSE
  if (!POSTGRES_DIALECTS.has(ctx.dialect)) {
    throw new KeycardKnexError(
      `$has needs an array column, which the "${ctx.dialect}" dialect doesn't support - register a custom "$has" in KnexPolicyOptions.operators to translate it.`,
    )
  }
  if (value !== null && !isScalar(value)) return Sql.FALSE

  // array_position matches NULL elements too, and is NULL (so the IS NOT
  // NULL test is FALSE, never NULL) when the column itself is NULL.
  return Sql.raw("array_position(??, ?) IS NOT NULL", [ctx.column, value])
}

const $substr: KnexOperator = (value, ctx) => {
  if (ctx.column === null) return Sql.FALSE

  const pattern = typeof value === "string" ? parseSubstrPattern(value) : null
  if (!pattern) {
    ctx.logger.warn(`Malformed $substr pattern ${JSON.stringify(value)} translates to FALSE.`)
    return Sql.FALSE
  }

  const column = ctx.column
  // LIKE is case-insensitive for ASCII in SQLite; GLOB is case-sensitive,
  // as $substr requires.
  const match = SQLITE_DIALECTS.has(ctx.dialect)
    ? Sql.raw("?? GLOB ?", [column, toGlobPattern(pattern)])
    : Sql.raw(`?? LIKE ? ESCAPE '${LIKE_ESCAPE}'`, [column, toLikePattern(pattern)])

  return Sql.and(Sql.isNotNull(column), match)
}

function constant(name: "$always" | "$never", result: SqlCondition): KnexOperator {
  return (value, ctx) => {
    if (value !== true) {
      ctx.logger.warn(`${name} expects true, received ${JSON.stringify(value)}; it translates to FALSE.`)
      return Sql.FALSE
    }
    return result
  }
}

const $field: KnexOperator = (value, ctx) => {
  if (!Array.isArray(value) || value.length !== 2 || typeof value[0] !== "string") {
    ctx.logger.warn(`$field expects a [name, condition] tuple, received ${JSON.stringify(value)}; it translates to FALSE.`)
    return Sql.FALSE
  }
  return ctx.translateField(value[0], value[1])
}

/** The SQL translation of every built-in KeyCard operator. */
export const DefaultKnexOperators: Readonly<KnexOperatorsRecord> = Object.freeze({
  $eq: equals,
  // Through translate, so a custom $eq also changes $ne, which MUST stay its exact negation.
  $ne: (value, ctx) => Sql.not(ctx.translate({ $eq: value })),
  $gt: numericCompare(">"),
  $gte: numericCompare(">="),
  $lt: numericCompare("<"),
  $lte: numericCompare("<="),
  $in,
  $has,
  $substr,
  $or: logicalList("$or"),
  $and: logicalList("$and"),
  $not: (value, ctx) => Sql.not(ctx.translate(value)),
  $always: constant("$always", Sql.TRUE),
  $never: constant("$never", Sql.FALSE),
  $field,
})
