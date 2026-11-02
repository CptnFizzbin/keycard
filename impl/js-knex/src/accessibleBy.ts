import type { Logger, Policy, PolicyDefinition } from "@cptn-fizzbin/keycard"
import { PolicyLoadException } from "@cptn-fizzbin/keycard"
import type { Knex } from "knex"

import type { ColumnResolver } from "./conditionTranslator.ts"
import { ConditionTranslator } from "./conditionTranslator.ts"
import type { JsonValue } from "./json.ts"
import type { KnexOperatorsRecord } from "./knexOperator.ts"
import { renderSqlCondition } from "./renderSqlCondition.ts"
import type { SqlCondition } from "./sqlCondition.ts"
import { Sql } from "./sqlCondition.ts"

/** An Action or Subject, by name or as the object `createAction`/`createSubject` returns. */
export type NameOrNamed = string | { readonly name: string }

/** Options for {@link accessibleBy} and {@link policyToSqlCondition}. */
export interface KnexPolicyOptions {
  /**
   * Maps subject field names to columns. A record lists every field the
   * table has: a field it doesn't list is treated as missing from the
   * subject, so conditions on it fail (except a bare `$ne`, which matches).
   * A function returns `undefined` for a missing field.
   *
   * Defaults to using each field name as its column name, with any field
   * containing a `.` treated as missing, so a policy can't reach into
   * another table by naming `other_table.column`.
   *
   * ```ts
   * { columns: { ownerId: "articles.owner_id", status: "articles.status" } }
   * ```
   */
  columns?: Readonly<Record<string, string>> | ColumnResolver

  /** Translations for custom operators, or overrides of the built-in ones (e.g. `$has` or `$substr` for a dialect). */
  operators?: KnexOperatorsRecord

  /** Receives non-fatal diagnostics, such as a malformed condition that translates to `FALSE`. Discarded when unset. */
  logger?: Logger

  /** The SQL dialect to translate for. Defaults to the dialect of the query builder the condition is applied to. */
  dialect?: string
}

const noopLogger: Logger = { info: () => {}, warn: () => {}, error: () => {} }

const DISABLED: unique symbol = Symbol("keycard-knex:wildcard-disabled")

function resolveWildcard(declared: unknown, field: string): string | typeof DISABLED {
  if (declared === undefined) return "_ANY_"
  if (declared === null || declared === false) return DISABLED
  if (typeof declared === "string") return declared
  throw new PolicyLoadException(`${field} must be a string, null, or false - got ${JSON.stringify(declared)}.`)
}

function nameOf(value: NameOrNamed): string {
  return typeof value === "string" ? value : value.name
}

function defaultColumnFor(field: string): string | undefined {
  return field.includes(".") ? undefined : field
}

function columnResolver(columns: KnexPolicyOptions["columns"]): ColumnResolver {
  if (columns === undefined) return defaultColumnFor
  if (typeof columns === "function") return columns
  return (field) => Object.hasOwn(columns, field) ? columns[field] : undefined
}

function dialectOf(builder: Knex.QueryBuilder): string {
  const client: unknown = builder.client
  const dialect = typeof client === "object" && client !== null && "dialect" in client ? client.dialect : undefined
  return typeof dialect === "string" ? dialect : "unknown"
}

/**
 * Returns the condition a row of `subject` must satisfy for `policy` to
 * allow `action` on it: the same last-matching-rule-wins outcome as
 * `policy.can(action, subject.wrap(row))`, as a {@link SqlCondition} for the
 * given `dialect`. A policy that never allows the action translates to
 * `Sql.FALSE`, and one that always does to `Sql.TRUE`.
 *
 * @throws KeycardKnexError if a condition uses an operator with no Knex
 *   translation for `dialect`
 */
export function policyToSqlCondition(
  policy: Policy | PolicyDefinition,
  action: NameOrNamed,
  subject: NameOrNamed,
  options: KnexPolicyOptions & { dialect: string },
): SqlCondition {
  // Duck-typed rather than instanceof, which fails when the app and this
  // package resolve different copies of @cptn-fizzbin/keycard.
  const definition = typeof policy.def === "function" ? (policy as Policy).def() : policy as PolicyDefinition
  const anyAction = resolveWildcard(definition.meta?.anyAction, "meta.anyAction")
  const anySubject = resolveWildcard(definition.meta?.anySubject, "meta.anySubject")
  const actionName = nameOf(action)
  const subjectName = nameOf(subject)

  const translator = new ConditionTranslator({
    dialect: options.dialect,
    columnFor: columnResolver(options.columns),
    operators: options.operators ?? {},
    logger: options.logger ?? noopLogger,
  })

  // Folding the rules in declaration order, each one overriding those
  // before it where its condition holds, gives last-matching-rule-wins.
  let allowed: SqlCondition = Sql.FALSE
  for (const [effect, ruleAction, ruleSubject, ruleCondition] of definition.rules) {
    if (ruleAction !== actionName && ruleAction !== anyAction) continue
    if (ruleSubject !== subjectName && ruleSubject !== anySubject) continue

    // Truthiness, not presence, matches how Policy.can decides whether a
    // rule is conditional.
    const matches = ruleCondition ? translator.translate(ruleCondition as JsonValue) : Sql.TRUE
    allowed = effect === "allow"
      ? Sql.or(allowed, matches)
      : Sql.and(allowed, Sql.not(matches))
  }

  return allowed
}

/**
 * Returns a knex `where` callback that limits a query to the rows of
 * `subject` that `policy` allows `action` on - the same rows for which
 * `policy.can(action, subject.wrap(row))` is `true`.
 *
 * ```ts
 * const articles = await knex("articles")
 *   .select("*")
 *   .where(accessibleBy(policy, Actions.read, Subjects.article))
 * ```
 *
 * @throws KeycardKnexError when the query is built, if a condition uses an
 *   operator with no Knex translation for the query's dialect
 */
export function accessibleBy(
  policy: Policy | PolicyDefinition,
  action: NameOrNamed,
  subject: NameOrNamed,
  options: KnexPolicyOptions = {},
): (builder: Knex.QueryBuilder) => void {
  return (builder) => {
    const dialect = options.dialect ?? dialectOf(builder)
    renderSqlCondition(builder, policyToSqlCondition(policy, action, subject, { ...options, dialect }))
  }
}
