import type { PolicyDefinition, RuleTuple } from "./policyDefinition.ts"
import type { AnyAction } from "../action/action.ts"
import type { Action } from "../action/index.ts"
import { allOf, anyOf, ALWAYS, negate, NEVER } from "../conditions/combineConditions.ts"
import type { AnyCondition, Condition } from "../conditions/condition.ts"
import { ConditionResolver } from "../conditions/index.ts"
import type { AnyOperator } from "../conditions/operators/operator.ts"
import { PolicyError } from "../errors/index.ts"
import type { KeycardConfig } from "../keycardConfig.ts"
import { PolicyValidator } from "./policyValidator.ts"
import type { DISABLED } from "./wildcards.ts"
import { effectiveAnyAction, effectiveAnySubject } from "./wildcards.ts"
import { KeycardContext } from "../keycardContext.ts"
import type { Catalog } from "../lib/catalog.ts"
import type { JsonObject } from "../lib/json.ts"
import type { Subject } from "../subject/index.ts"
import type { AnySubject, InferClaims } from "../subject/subject.ts"

/** Returns a deep copy of `definition`, except `meta.application`, which is kept by reference: it's opaque application data that may not be structured-cloneable. */
function cloneDefinition(definition: PolicyDefinition): PolicyDefinition {
  const { meta, rules, ...rest } = definition
  const clone: PolicyDefinition = {
    ...rest,
    rules: rules.map((rule) => structuredClone(rule)),
  }
  if (meta) {
    const { application, ...metaRest } = meta
    clone.meta = structuredClone(metaRest)
    if ("application" in meta) clone.meta.application = application
  }
  return clone
}

export class Policy<
  TActions extends AnyAction = AnyAction,
  TSubjects extends AnySubject = AnySubject,
> {
  private readonly ctx: KeycardContext<AnyOperator>
  private readonly definition: PolicyDefinition
  private readonly resolver: ConditionResolver
  private readonly anyAction: string | typeof DISABLED
  private readonly anySubject: string | typeof DISABLED
  private readonly warnedDynamicIds = new Set<string>()

  constructor(
    definition: PolicyDefinition | JsonObject,
    config: KeycardContext | KeycardConfig<AnyOperator> = {},
  ) {
    this.ctx = KeycardContext.from(config)

    const validator: PolicyValidator = new PolicyValidator(this.ctx)
    validator.validate(definition)

    this.definition = cloneDefinition(definition)
    this.anyAction = effectiveAnyAction(this.definition.meta)
    this.anySubject = effectiveAnySubject(this.definition.meta)
    this.resolver = new ConditionResolver(this.ctx)
  }

  static from<
    TActions extends Action = AnyAction,
    TSubjects extends Subject = AnySubject,
  >(
    definition: PolicyDefinition,
    config: KeycardConfig<AnyOperator> = {},
  ): Policy<TActions, TSubjects> {
    return new Policy(definition, config)
  }

  def(): PolicyDefinition {
    return cloneDefinition(this.definition)
  }

  can(action: TActions, subject: TSubjects): boolean {
    return this.checkPermission(action, subject)
  }

  cannot(action: TActions, subject: TSubjects): boolean {
    return !this.can(action, subject)
  }

  require(action: TActions, subject: TSubjects): void {
    if (!this.can(action, subject)) {
      throw new PolicyError(
        `"${this.nameOf(this.ctx.actions, action)}" is not allowed on this "${this.nameOf(this.ctx.subjects, subject)}"`)
    }
  }

  private nameOf(catalog: Catalog<{ id: string, name: string }>, item: { name: string }): string {
    return catalog.get(item.name)?.name ?? item.name
  }

  /**
   * Returns the condition a `subject` instance's claims must satisfy for
   * `action` to be allowed on it: evaluating it against `claims` gives the
   * same result as `can(action, subject.wrap(claims))`. Matching rules are
   * combined with `$and`/`$or`/`$not` so the last matching rule still wins.
   *
   * When the outcome doesn't depend on the instance, the result is
   * `{ $and: [] }` (always allowed) or `{ $or: [] }` (never allowed).
   *
   * ```ts
   * policy.getCondition(Read, Article)
   * // => { $or: [{ status: "published" }, { ownerId: 7 }] }
   * ```
   */
  getCondition<TSubject extends TSubjects>(action: TActions, subject: TSubject): Condition<InferClaims<TSubject>> {
    let allowed: AnyCondition = NEVER
    for (const [effect, , , ruleConditions] of this.matchingRules(action, subject)) {
      // Truthiness, as in checkPermission, decides whether a rule is conditional.
      const matches = ruleConditions ? ruleConditions : ALWAYS
      // Each rule overrides everything before it, but only for the instances
      // its condition matches - last-matching-rule-wins as one condition.
      allowed = effect === "allow"
        ? anyOf(allowed, matches)
        : allOf(allowed, negate(matches))
    }
    return allowed
  }

  private checkPermission(action: TActions, subject: TSubjects): boolean {
    const rules = this.matchingRules(action, subject)

    for (let i = rules.length - 1; i >= 0; i--) {
      const [effect, , , ruleConditions] = rules[i]

      if (ruleConditions) {
        // A bare check has no instance for the conditions to inspect; without
        // this guard, a condition such as { x: { $ne: 1 } } would match it.
        if (subject.claims === undefined) continue
        if (!this.resolver.evaluate(subject.claims, ruleConditions)) continue
      }

      return effect === "allow"
    }

    return false
  }

  /** Returns the rules whose action and subject match, wildcards included, in declaration order. */
  private matchingRules(action: TActions, subject: TSubjects): RuleTuple[] {
    this.warnIfUnregisteredDynamic(action, this.ctx.actions, "Action", "createAction")
    this.warnIfUnregisteredDynamic(subject, this.ctx.subjects, "Subject", "createSubject")

    const actionName = this.nameOf(this.ctx.actions, action)
    const subjectName = this.nameOf(this.ctx.subjects, subject)
    const { anyAction, anySubject } = this

    return this.definition.rules.filter(([, ruleAction, ruleSubject]) =>
      (ruleAction === actionName || ruleAction === anyAction)
      && (ruleSubject === subjectName || ruleSubject === anySubject))
  }

  private warnIfUnregisteredDynamic(
    value: { id: string, name: string, __dynamic?: true },
    catalog: Catalog<{ id: string, name: string }>,
    kind: string,
    factory: string,
  ): void {
    // An unregistered dynamic value can never match a non-wildcard rule, and
    // would otherwise fall through to default deny silently.
    if (!value.__dynamic || catalog.has(value.id) || this.warnedDynamicIds.has(value.id)) return
    this.warnedDynamicIds.add(value.id)
    this.ctx.logger.warn(
      `${kind} created via ${factory}() with no name was checked but never registered in any KeycardConfig catalog reachable from this Policy - it can never match a non-wildcard rule.`,
    )
  }
}
