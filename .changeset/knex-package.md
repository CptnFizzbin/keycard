---
"@cptn-fizzbin/keycard-knex": minor
---

New package: `@cptn-fizzbin/keycard-knex` turns a KeyCard policy into a Knex `where` clause by translating `Policy.getCondition`. `accessibleBy(policy, action, subject, options)` selects exactly the rows `policy.can(action, subject.wrap(row))` allows, translating every built-in operator (with `$has` on Postgres only), with a `columns` mapping for field names and `operators` for custom operator translations. A `columns` record is type-checked against the subject's claims type.
