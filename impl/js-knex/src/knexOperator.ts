import type { Logger } from "@cptn-fizzbin/keycard"

import type { JsonValue } from "./json.ts"
import type { SqlCondition } from "./sqlCondition.ts"

/** What a {@link KnexOperator} is translating against, and how to recurse back into the condition language. */
export interface KnexOperatorContext {
  /**
   * The column the operator applies to, or `null` at the top of a rule's
   * condition, where the operator applies to the whole row.
   */
  readonly column: string | null

  /** The query's knex dialect, e.g. `"postgresql"`, `"sqlite3"`, `"mysql"`, `"mssql"`. */
  readonly dialect: string

  /** Receives non-fatal diagnostics, such as a malformed condition that translates to `FALSE`. */
  readonly logger: Logger

  /** Translates `condition` against the same column (or row) as this operator - used by `$and`/`$or`/`$not`. */
  translate(condition: JsonValue): SqlCondition

  /**
   * Translates `condition` against the row's `field`, as a bare-key field
   * condition does: `FALSE` when the field maps to no column (`TRUE` for a
   * bare `$ne`), and `FALSE` when already narrowed into a column, since v0
   * allows only one level of field access.
   */
  translateField(field: string, condition: JsonValue): SqlCondition
}

/**
 * Translates one `$`-prefixed condition operator into a {@link SqlCondition}.
 * The result MUST evaluate to `TRUE` or `FALSE`, never SQL `NULL`.
 *
 * ```ts
 * const $hasRole: KnexOperator = (role, { column }) =>
 *   column === null && typeof role === "string"
 *     ? Sql.raw("? = ANY(??)", [role, "roles"])
 *     : Sql.FALSE
 * ```
 */
export type KnexOperator = (value: JsonValue, ctx: KnexOperatorContext) => SqlCondition

/** Custom or overriding operator translations, keyed by `$`-prefixed operator name. */
export type KnexOperatorsRecord = Record<`$${string}`, KnexOperator>
