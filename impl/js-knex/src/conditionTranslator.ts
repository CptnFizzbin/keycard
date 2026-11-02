import type { Logger } from "@cptn-fizzbin/keycard"

import { DefaultKnexOperators } from "./defaultOperators.ts"
import type { JsonValue } from "./json.ts"
import { KeycardKnexError } from "./keycardKnexError.ts"
import type { KnexOperatorContext, KnexOperatorsRecord } from "./knexOperator.ts"
import type { SqlCondition } from "./sqlCondition.ts"
import { Sql } from "./sqlCondition.ts"

/** Maps a subject field name to its column, or `undefined` when the subject has no such field. */
export type ColumnResolver = (field: string) => string | undefined

export interface ConditionTranslatorOptions {
  dialect: string
  columnFor: ColumnResolver
  operators: KnexOperatorsRecord
  logger: Logger
}

/** Returns `true` when `condition` is exactly `{ $ne: ... }` - the one condition that matches a missing field. */
function isBareNe(condition: JsonValue): boolean {
  return typeof condition === "object"
    && condition !== null
    && !Array.isArray(condition)
    && Object.keys(condition).length === 1
    && "$ne" in condition
}

/** Translates KeyCard conditions into {@link SqlCondition} trees evaluated against a table's rows. */
export class ConditionTranslator {
  private readonly operators: KnexOperatorsRecord

  constructor(private readonly options: ConditionTranslatorOptions) {
    this.operators = { ...DefaultKnexOperators, ...options.operators }
  }

  /** Translates a rule's condition, evaluated against the whole row. */
  translate(condition: JsonValue): SqlCondition {
    return this.translateAt(null, condition)
  }

  private translateAt(column: string | null, condition: JsonValue): SqlCondition {
    if (Array.isArray(condition)) {
      // Only scalars are shorthand for $eq; reading an array's indices as
      // field names would match by accident.
      this.options.logger.warn(`Malformed condition: expected an object or a scalar, received an array (${JSON.stringify(condition)}).`)
      return Sql.FALSE
    }

    if (typeof condition !== "object" || condition === null) {
      return this.applyOperator(column, "$eq", condition)
    }

    return Sql.and(...Object.entries(condition).map(([key, value]) => key.startsWith("$")
      ? this.applyOperator(column, key, value)
      : this.translateField(column, key, value)))
  }

  private translateField(column: string | null, field: string, condition: JsonValue): SqlCondition {
    if (column !== null) {
      this.options.logger.warn(
        `Field "${field}" is nested inside field condition on "${column}"; v0 supports only top-level field access, so it translates to FALSE.`,
      )
      return Sql.FALSE
    }

    const fieldColumn = this.options.columnFor(field)
    if (fieldColumn === undefined) {
      // A missing field fails every condition except a bare $ne, which MUST
      // be the exact negation of $eq.
      return isBareNe(condition) ? Sql.TRUE : Sql.FALSE
    }
    return this.translateAt(fieldColumn, condition)
  }

  private applyOperator(column: string | null, name: string, value: JsonValue): SqlCondition {
    const operator = this.operators[name as `$${string}`]
    if (!operator) {
      // Translating to FALSE (as the spec does for unknown operators) would
      // turn into TRUE under a $not or a deny rule, granting access.
      throw new KeycardKnexError(
        `No Knex translation is registered for operator "${name}" - add one to KnexPolicyOptions.operators.`,
      )
    }
    return operator(value, this.contextFor(column))
  }

  private contextFor(column: string | null): KnexOperatorContext {
    return {
      column,
      dialect: this.options.dialect,
      logger: this.options.logger,
      translate: (condition) => this.translateAt(column, condition),
      translateField: (field, condition) => this.translateField(column, field, condition),
    }
  }
}
