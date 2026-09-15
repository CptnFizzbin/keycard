import type { Condition } from "./condition.ts"
import { PolicyLoadException } from "../errors/index.ts"
import type { JsonValue } from "../lib/json.ts"
import { DefaultOperators } from "./operators/defaultOperators.ts"
import type { AnyOperator } from "./operators/operator.ts"

/** Every operator name {@link ConditionResolver} understands out of the box - the single source of truth for "is this name built-in". */
export const BUILTIN_OPERATOR_NAMES: ReadonlySet<string> = new Set(DefaultOperators.map((op) => op.name))

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

    this.evaluate = this.evaluate.bind(this)
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

  evaluate<TSubject>(subject: TSubject, condition: Condition<TSubject>): boolean {
    if (!condition) {
      return this.evaluateOperator(subject, "$eq", condition)
    }

    if (typeof condition === "object") {
      return Object.entries(condition).every(([key, value]) => {
        if (key.startsWith("$")) {
          return this.evaluateOperator(subject, key, value)
        }

        return this.evaluateOperator(subject, "$eq", value)
      })
    }

    return this.evaluateOperator(subject, "$eq", condition)
  }

  private evaluateOperator<TSubject>(subject: TSubject, operatorName: string, value: JsonValue): boolean {
    const operator = this.operatorRegistry.get(operatorName)
    if (!operator) return false

    return operator.resolve(subject, value, { resolveSubcondition: this.evaluate })
  }
}
