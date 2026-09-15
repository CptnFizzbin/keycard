import type { Condition } from "./condition.ts"
import { PolicyLoadException } from "../errors/index.ts"
import type { JsonValue } from "../lib/json.ts"
import { DefaultOperators } from "./operators/defaultOperators.ts"
import { hasField, isBareNe } from "./operators/field/fieldAccess.ts"
import type { Operator } from "./operators/operator.ts"

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
  private operatorRegistry = new Map<string, Operator>()

  /**
   * @param operators custom operators to register alongside the built-ins
   *   (§7.4.12) - built-in and custom operators share this one array-based
   *   entry point. Constructing this with a name collision (a custom
   *   operator sharing a `$name` with a built-in, or with another operator
   *   in `operators`) MUST throw a {@link PolicyLoadException} immediately
   *   - never a silent overwrite (SPEC_V1-0-0.md §3.2.3, EC-16).
   */
  constructor(operators: Operator[] = []) {
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
          `meta.operators declares "${name}" but no operator with that name is registered (built-in or custom) (SPEC_V1-0-0.md §3.2.3, EC-15).`,
        )
      }
    }
  }

  evaluate(subject: unknown, condition: Condition): boolean {
    if (
      typeof condition === "string"
      || typeof condition === "number"
      || typeof condition === "boolean"
      || condition === null
    ) {
      condition = { $eq: condition }
    }

    if (typeof condition !== "object") {
      return false
    }

    // §7.5: every key MUST be evaluated and ANDed together - no key may
    // "consume" the whole object or cause sibling keys to be ignored.
    for (const [key, value] of Object.entries(condition)) {
      if (!this.evaluateKey(subject, key, value)) {
        return false
      }
    }

    return true
  }

  /**
   * §7.4.12, §7.5: any key starting with "$" is an operator lookup, never
   * a field name - built-in and custom operators are both resolved the
   * same way, by name, against the same registry.
   */
  private evaluateKey(subject: unknown, key: string, value: JsonValue): boolean {
    if (!key.startsWith("$")) {
      return this.fieldCheck(subject, key, value)
    }

    const operator = this.operatorRegistry.get(key)
    if (!operator) {
      // §7.4.12, EC-13: an operator with no checker registered (built-in or
      // custom) MUST evaluate to false - never a no-op true, and never
      // treated as a field name, and never itself a required §7.1
      // diagnostic (an unrecognized name is ordinary unmatched vocabulary,
      // not a type issue). A cataloged-but-unregistered name (EC-15) can no
      // longer even reach this branch: `Policy` now enforces meta.operators
      // registration in full at construction time, so any name still
      // unregistered here was never cataloged.
      return false
    }

    return operator.resolve(
      subject,
      value,
      {
        resolveSubcondition: this.evaluate,
      },
    )
  }

  /**
   * §7.4.10, §7.3: a missing field (or a non-object subject) makes the
   * whole field-condition false - absence, not a type issue - with one
   * exception: `$ne` (§7.4.2), which MUST evaluate to true on a missing
   * field instead. See {@link isBareNe}.
   */
  private fieldCheck(subject: unknown, fieldName: string, condition: Condition): boolean {
    return hasField(subject, fieldName) ? this.evaluate(subject[fieldName], condition) : isBareNe(condition)
  }
}
