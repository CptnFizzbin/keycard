# KeyCard - Knex

Turns a KeyCard [`Policy`](../js/README.md) into a [Knex](https://knexjs.org)
`where` clause, so a query returns only the rows a policy allows an action on.
A row is selected exactly when `policy.can(action, subject.wrap(row))` would be
`true`: rules are matched by action and subject, the last matching rule wins,
and no match means deny.

## Installation

```bash
npm install @cptn-fizzbin/keycard @cptn-fizzbin/keycard-knex knex
```

## Usage

```typescript
import { createAction, createSubject, PolicyBuilder } from "@cptn-fizzbin/keycard"
import { accessibleBy } from "@cptn-fizzbin/keycard-knex"

const Read = createAction("Read")
const Article = createSubject<{ ownerId: number, status: string }>("Article")

const policy = new PolicyBuilder<typeof Read, typeof Article>()
  .allow(Read, Article, { status: "published" })
  .allow(Read, Article, { ownerId: currentUser.id })
  .deny(Read, Article, { status: "deleted" })
  .build()

const articles = await knex("articles")
  .select("*")
  .where(accessibleBy(policy, Read, Article, {
    columns: { ownerId: "articles.owner_id", status: "articles.status" },
  }))
```

On Postgres this generates:

```sql
select * from "articles" where ((
  (("articles"."status" is not null and "articles"."status" = $1)
    or ("articles"."owner_id" is not null and "articles"."owner_id" = $2))
  and not ("articles"."status" is not null and "articles"."status" = $3)
))
```

`accessibleBy` takes a `Policy`, and an action and subject either by name
(`"Read"`) or as `createAction`/`createSubject` objects. It translates
`policy.getCondition(action, subject)` - the policy's rules for that action
and subject, already combined into one condition - so rule matching and
ordering behave exactly as in `Policy.can`.
Always pass its result to `.where()` - it groups its own clauses, so it can be
combined with other `where`/`orWhere` calls safely.

A policy that never allows the action produces `where (1 = 0)`, and one that
always does produces `where (1 = 1)`. `policyToSqlCondition` returns the
untranslated condition tree instead (`Sql.FALSE`/`Sql.TRUE` in those cases), for
callers that want to short-circuit the query.

## Options

| Option      | Description                                                                                                                                                                              |
|-------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `columns`   | Maps subject field names to columns, as a record or a `(field) => column \| undefined` function. Defaults to the field name itself, with dotted names (`other_table.column`) rejected. |
| `operators` | Knex translations for custom operators, or overrides of the built-in ones.                                                                                                              |
| `logger`    | Receives warnings about malformed conditions, which translate to `FALSE`.                                                                                                                |
| `dialect`   | The SQL dialect to translate for. Defaults to the query builder's own dialect.                                                                                                           |

A `columns` record is type-checked against the subject: when the subject is
created with a claims type (`createSubject<{ ownerId: number }>("Article")`),
every key must be one of its fields, so a misspelled or stale field name is a
compile error. A subject passed by name, or created without a claims type,
accepts any key.

A field that `columns` doesn't map is treated as **missing** from the subject:
every condition on it fails, except a bare `{ $ne: value }`, which matches - the
same rule `Policy.can` applies to a missing field. List every field the policy
may reference.

## How conditions translate

| Condition                    | SQL                                                                                     |
|------------------------------|-----------------------------------------------------------------------------------------|
| `{ f: v }` / `{ f: { $eq } }` | `f is not null and f = v` (`f is null` for `v: null`)                                  |
| `$ne`                        | `not (...)` of `$eq`, so `NULL` rows match `{ $ne: v }`                                 |
| `$gt` `$gte` `$lt` `$lte`    | `f is not null and f > v` - a non-number `v` translates to `FALSE`                      |
| `$in`                        | `f is not null and f in (...)`, plus `or f is null` when the list contains `null`       |
| `$has`                       | Postgres only: `array_position(f, v) IS NOT NULL` on an array column                    |
| `$substr`                    | `GLOB` on SQLite, `LIKE ... ESCAPE '!'` elsewhere                                       |
| `$and` `$or` `$not` `$field` | the matching boolean combination                                                        |
| `$always` / `$never`         | `1 = 1` / `1 = 0`                                                                       |

Every translated condition evaluates to `TRUE` or `FALSE`, never SQL `NULL`, so
`not (...)` and deny rules behave exactly like `Policy.can` on rows with `NULL`
columns.

### Differences from `Policy.can`

- **Column types decide comparisons.** `Policy.can` never coerces types, but a
  database compares using the column's type: `{ count: { $gt: 5 } }` against a
  text column is coerced or rejected by the database, not `false`. Keep column
  types matching the values your policy compares against.
- **Case sensitivity of `$substr`.** `$substr` is case-sensitive. That holds on
  Postgres (`LIKE`) and SQLite (`GLOB`), but MySQL's and MSSQL's default
  collations make `LIKE` case-insensitive. Override `$substr` through
  `operators` if you need exact matching there.
- **`$has`** has no portable SQL translation, so it throws `KeycardKnexError` on
  dialects other than Postgres unless you supply your own.
- **Unknown operators throw** `KeycardKnexError` instead of evaluating to
  `false`, since a silent `FALSE` becomes `TRUE` under a `$not` or a deny rule.

## Custom operators

A custom operator registered with `Policy` needs a matching Knex translation.
It receives the operator's value and a context with the current `column`
(`null` at the top of a rule's condition, where it applies to the whole row),
and returns a condition built with `Sql`:

```typescript
import { accessibleBy, Sql } from "@cptn-fizzbin/keycard-knex"
import type { KnexOperator } from "@cptn-fizzbin/keycard-knex"

// { $hasRole: "admin" } - the row's `roles` array contains the role.
const $hasRole: KnexOperator = (role, ctx) =>
  ctx.column === null ? ctx.translateField("roles", { $has: role }) : Sql.FALSE

knex("articles").where(accessibleBy(policy, "Delete", "Article", { operators: { $hasRole } }))
```

`Sql.raw(sql, bindings)` and `Sql.callback(builder => ...)` embed arbitrary
SQL. A translation MUST evaluate to `TRUE` or `FALSE` and never `NULL` (guard
nullable columns with `Sql.isNotNull(column)`), or negating it with `$not` or a
deny rule will drop rows it should keep.
