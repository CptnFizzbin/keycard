import type { Action, Logger, Policy, Subject } from "@cptn-fizzbin/keycard"
import { createAction, createSubject } from "@cptn-fizzbin/keycard"
import type { Knex } from "knex"

import type { ColumnResolver } from "./conditionTranslator.ts"
import { ConditionTranslator } from "./conditionTranslator.ts"
import type { JsonValue } from "./json.ts"
import type { KnexOperatorsRecord } from "./knexOperator.ts"
import { renderSqlCondition } from "./renderSqlCondition.ts"
import type { SqlCondition } from "./sqlCondition.ts"

/** An Action, by name or as the object `createAction` returns. */
export type ActionArg = string | Action

/** A Subject, by name or as the object `createSubject` returns. */
// eslint-disable-next-line @typescript-eslint/no-explicit-any
export type SubjectArg = string | Subject<any, any>

/**
 * The claims type of `TSubject`: what `createSubject<TClaims>()` was given, or
 * `unknown` for a subject named by string or created without a claims type.
 */
export type ClaimsOf<TSubject> = TSubject extends string
  ? unknown
  : TSubject extends Subject<infer TClaims, infer _TArgs>
    ? [TClaims] extends [never] ? unknown : TClaims
    : unknown

/**
 * A field-to-column record for a subject with claims `TClaims`: its keys
 * MUST be fields of `TClaims`, and any of them may be left out. Any string
 * key is accepted when `TClaims` is `unknown`.
 */
export type ColumnMap<TClaims> = unknown extends TClaims
  ? Readonly<Record<string, string>>
  : { readonly [Field in Extract<keyof TClaims, string>]?: string }

/**
 * Options for {@link accessibleBy} and {@link policyToSqlCondition}, for a
 * subject with claims `TClaims`.
 */
export interface KnexPolicyOptions<TClaims = unknown> {
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
  columns?: ColumnMap<TClaims> | ColumnResolver

  /** Translations for custom operators, or overrides of the built-in ones (e.g. `$has` or `$substr` for a dialect). */
  operators?: KnexOperatorsRecord

  /** Receives non-fatal diagnostics, such as a malformed condition that translates to `FALSE`. Discarded when unset. */
  logger?: Logger

  /** The SQL dialect to translate for. Defaults to the dialect of the query builder the condition is applied to. */
  dialect?: string
}

const noopLogger: Logger = { info: () => {}, warn: () => {}, error: () => {} }

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
 * allow `action` on it - `policy.getCondition(action, subject)` translated
 * into a {@link SqlCondition} for the given `dialect`. A policy that never
 * allows the action translates to `Sql.FALSE`, and one that always does to
 * `Sql.TRUE`.
 *
 * @throws KeycardKnexError if a condition uses an operator with no Knex
 *   translation for `dialect`
 */
export function policyToSqlCondition<TSubject extends SubjectArg>(
  policy: Policy,
  action: ActionArg,
  subject: TSubject,
  options: KnexPolicyOptions<ClaimsOf<TSubject>> & { dialect: string },
): SqlCondition
export function policyToSqlCondition(
  policy: Policy,
  action: ActionArg,
  subject: SubjectArg,
  options: KnexPolicyOptions & { dialect: string },
): SqlCondition {
  const condition = policy.getCondition(
    typeof action === "string" ? createAction(action) : action,
    typeof subject === "string" ? createSubject(subject) : subject,
  )

  const translator = new ConditionTranslator({
    dialect: options.dialect,
    columnFor: columnResolver(options.columns),
    operators: options.operators ?? {},
    logger: options.logger ?? noopLogger,
  })
  return translator.translate(condition as JsonValue)
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
export function accessibleBy<TSubject extends SubjectArg>(
  policy: Policy,
  action: ActionArg,
  subject: TSubject,
  options?: KnexPolicyOptions<ClaimsOf<TSubject>>,
): (builder: Knex.QueryBuilder) => void
export function accessibleBy(
  policy: Policy,
  action: ActionArg,
  subject: SubjectArg,
  options: KnexPolicyOptions = {},
): (builder: Knex.QueryBuilder) => void {
  return (builder) => {
    const dialect = options.dialect ?? dialectOf(builder)
    renderSqlCondition(builder, policyToSqlCondition(policy, action, subject, { ...options, dialect }))
  }
}
