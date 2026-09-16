import type { Condition } from "./condition.ts"
import { PolicyLoadException } from "../errors/index.ts"
import { DefaultOperators } from "./operators/defaultOperators.ts"
import { hasField, isBareNe } from "./operators/field/fieldAccess.ts"
import type { AnyOperator } from "./operators/operator.ts"
import type { SubjectFieldMapper } from "../subject/subjectFieldMapper.ts"

/** Every operator name {@link ConditionResolver} understands out of the box - the single source of truth for "is this name built-in". */
export const BUILTIN_OPERATOR_NAMES: ReadonlySet<string> = new Set(DefaultOperators.map((op) => op.name))

/** A bound `resolveSubcondition` - what every recursive step and every {@link Operator.resolve} call is handed. */
type Resolve = (subject: unknown, condition: unknown) => boolean

/**
 * Implements SPEC_V1-0-0.md §7: the condition language and its
 * evaluation semantics. Every operator's own behavior lives in
 * `./operators/**` - this class is just the dispatch loop: it looks a
 * `$`-prefixed key up in its registry (built-ins plus whatever custom
 * `Operator`s the caller registered) and delegates, or narrows into a
 * bare field name.
 */
export class ConditionResolver {
  private operatorRegistry = new Map<string, AnyOperator>()
  /** Plain (mapper-less) recursion, reused across every call so it never needs re-allocating. */
  private readonly plainResolve: Resolve = (subject, condition) => this.evaluateWith(subject, condition, this.plainResolve)

  /**
   * @param operators custom operators to register alongside the built-ins
   *   (§7.4.12) - built-in and custom operators share this one array-based
   *   entry point. Constructing this with a name collision (a custom
   *   operator sharing a `$name` with a built-in, or with another operator
   *   in `operators`) MUST throw a {@link PolicyLoadException} immediately
   *   - never a silent overwrite (SPEC_V1-0-0.md §3.2.3, EC-16).
   */
  constructor(operators: AnyOperator[] = []) {
    for (const operator of DefaultOperators) {
      this.operatorRegistry.set(operator.name, operator)
    }

    for (const operator of operators) {
      if (this.operatorRegistry.has(operator.name)) {
        throw new PolicyLoadException(
          `Duplicate operator "${operator.name}": an operator with this name is already registered (built-in or custom) - operator names MUST be unique (SPEC_V1-0-0.md §3.2.3, EC-16).`,
        )
      }
      this.operatorRegistry.set(operator.name, operator)
    }
  }

  /**
   * §3.2.3, EC-15 (promoted): throws if any name in `names` isn't
   * registered on this resolver - built-in or custom. Used by `Policy` to
   * enforce `meta.operators` registration coverage in full at construction
   * time, regardless of whether any rule actually reaches a given operator
   * during evaluation.
   */
  assertAllRegistered(names: Iterable<string>): void {
    for (const name of names) {
      if (!this.operatorRegistry.has(name)) {
        throw new PolicyLoadException(
          `meta.operators declares "${name}" but no operator with that name is registered.`,
        )
      }
    }
  }

  /**
   * @param fieldMapper when given, tried first for any field looked up
   *   directly on `subject` - anywhere in the condition tree that `subject`
   *   is still the object in scope (bare-key/`$field` access at the top
   *   level, and inside `$and`/`$or`/`$not`, none of which narrow into a
   *   different object). Never consulted for a value a field lookup has
   *   already narrowed into - those fall back to ordinary property access,
   *   same as a field the mapper doesn't define.
   */
  evaluate<TSubject>(subject: TSubject, condition: Condition<TSubject>, fieldMapper?: SubjectFieldMapper<TSubject>): boolean {
    if (!fieldMapper) return this.evaluateWith(subject, condition, this.plainResolve)

    const rootSubject: unknown = subject
    const mappedResolve: Resolve = (s, c) =>
      s === rootSubject
        ? this.evaluateWith(s, c, mappedResolve, fieldMapper as SubjectFieldMapper<unknown>)
        : this.evaluateWith(s, c, mappedResolve)

    return mappedResolve(subject, condition)
  }

  private evaluateWith(
    subject: unknown,
    condition: unknown,
    resolve: Resolve,
    fieldMapper?: SubjectFieldMapper<unknown>,
  ): boolean {
    if (!condition) {
      return this.evaluateOperator(subject, "$eq", condition, resolve)
    }

    if (typeof condition === "object") {
      return Object.entries(condition).every(([key, value]) => {
        if (key.startsWith("$")) {
          return this.evaluateOperator(subject, key, value, resolve)
        }

        if (fieldMapper && key in fieldMapper) {
          return resolve(fieldMapper[key](subject), value)
        }

        return hasField(subject, key) ? resolve(subject[key], value) : isBareNe(value)
      })
    }

    return this.evaluateOperator(subject, "$eq", condition, resolve)
  }

  private evaluateOperator(subject: unknown, operatorName: string, value: unknown, resolve: Resolve): boolean {
    const operator = this.operatorRegistry.get(operatorName)
    if (!operator) return false

    return operator.resolve(subject, value, { resolveSubcondition: resolve })
  }
}
