import type { Knex } from "knex"

import type { SqlCondition } from "./sqlCondition.ts"

type Joiner = "and" | "or"

/**
 * Adds `condition` to `builder` as `where` clauses, joined to the clauses
 * already on it with `joiner`.
 */
export function renderSqlCondition(builder: Knex.QueryBuilder, condition: SqlCondition, joiner: Joiner = "and"): void {
  const useOr = joiner === "or"

  switch (condition.kind) {
    case "constant":
      // Not every dialect has TRUE/FALSE literals (MSSQL doesn't).
      if (useOr) builder.orWhereRaw(condition.value ? "1 = 1" : "1 = 0")
      else builder.whereRaw(condition.value ? "1 = 1" : "1 = 0")
      return

    case "and":
    case "or": {
      const inner: Joiner = condition.kind
      const group = (qb: Knex.QueryBuilder) => {
        for (const item of condition.items) renderSqlCondition(qb, item, inner)
      }
      if (useOr) builder.orWhere(group)
      else builder.where(group)
      return
    }

    case "not": {
      if (condition.item.kind === "isNull") {
        if (useOr) builder.orWhereNotNull(condition.item.column)
        else builder.whereNotNull(condition.item.column)
        return
      }

      const { item } = condition
      const group = (qb: Knex.QueryBuilder) => {
        // whereNot already parenthesizes, so an and/or item's own group would be redundant.
        if (item.kind === "and" || item.kind === "or") {
          for (const inner of item.items) renderSqlCondition(qb, inner, item.kind)
        } else {
          renderSqlCondition(qb, item, "and")
        }
      }
      if (useOr) builder.orWhereNot(group)
      else builder.whereNot(group)
      return
    }

    case "compare":
      if (useOr) builder.orWhere(condition.column, condition.operator, condition.value)
      else builder.where(condition.column, condition.operator, condition.value)
      return

    case "isNull":
      if (useOr) builder.orWhereNull(condition.column)
      else builder.whereNull(condition.column)
      return

    case "in":
      if (useOr) builder.orWhereIn(condition.column, [...condition.values])
      else builder.whereIn(condition.column, [...condition.values])
      return

    case "raw":
      if (useOr) builder.orWhereRaw(condition.sql, [...condition.bindings])
      else builder.whereRaw(condition.sql, [...condition.bindings])
      return

    case "callback": {
      const group = (qb: Knex.QueryBuilder) => {
        condition.apply(qb)
      }
      if (useOr) builder.orWhere(group)
      else builder.where(group)
      return
    }
  }
}
