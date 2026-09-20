import * as semver from "semver"

import type { PolicyDefinition, RuleTuple } from "./policyDefinition.ts"
import { DISABLED, effectiveAnyAction, effectiveAnySubject } from "./wildcards.ts"
import type { Action } from "../action/index.ts"
import type { AnyCondition } from "../conditions/condition.ts"
import { BUILTIN_OPERATOR_NAMES } from "../conditions/conditionResolver.ts"
import { ConditionResolver } from "../conditions/index.ts"
import type { AnyOperator } from "../conditions/operators/operator.ts"
import { normalizeOperators } from "../conditions/operators/operator.ts"
import { PolicyError, PolicyLoadException, PolicyVersionException } from "../errors/index.ts"
import type { KeycardConfig } from "../keycardConfig.ts"
import { buildCatalog, resolveName } from "../lib/catalog.ts"
import { getLogger } from "../lib/logger.ts"
import type { Subject } from "../subject/index.ts"
import type { SubjectFieldMapper } from "../subject/subjectFieldMapper.ts"
import { KEYCARD_POLICY_SUPPORTED_VERSIONS } from "../version.ts"

/**
 * Recursively collects every non-built-in, `$`-prefixed operator name used
 * anywhere in a Conditions tree - used to enforce `meta.operators`
 * coverage when loading a policy.
 */
function collectCustomOperators(condition: AnyCondition | undefined, out: Set<string>): void {
  if (condition === undefined || condition === null || typeof condition !== "object") return

  for (const [key, value] of Object.entries(condition)) {
    if (key.startsWith("$")) {
      if (key === "$or" || key === "$and") {
        if (Array.isArray(value)) value.forEach((c: AnyCondition) => collectCustomOperators(c, out))
      } else if (key === "$not") {
        collectCustomOperators(value, out)
      } else if (key === "$field" && Array.isArray(value) && value.length === 2) {
        collectCustomOperators(value[1], out)
      } else if (!BUILTIN_OPERATOR_NAMES.has(key)) {
        out.add(key)
      }
    } else {
      collectCustomOperators(value, out)
    }
  }
}

export class Policy<
  TActions extends Action = Action,
  TSubjects extends Subject = Subject,
  TOperators extends AnyOperator = never,
> {
  private readonly definition: PolicyDefinition
  private readonly resolver: ConditionResolver
  private readonly config: KeycardConfig<TOperators>
  private readonly actionCatalog: Map<string, string>
  private readonly subjectCatalog: Map<string, string>
  private readonly warnedDynamicIds = new Set<string>()

  /**
   * @param config shared, optional config also accepted by `PolicyBuilder`
   *   (SPEC_V0.md and the SubjectFieldMapper feature): `actions`/`subjects`
   *   widen the `meta.actions`/`meta.subjects` catalogs beyond what
   *   `definition.meta` itself declares, and double as a catalog resolving
   *   a dynamic (no-name) Action/Subject's random name to its key, built
   *   once here and cached (see `lib/catalog.ts`); `operators` - an
   *   `AnyOperator[]` or an `OperatorCatalog` - is normalized once here;
   *   `mapper` is consulted for a subject's fields whenever the `Subject`
   *   passed to {@link can} doesn't carry its own `fieldMapper`; `emitMeta`
   *   (default `true`) gates the eager catalog/operator validation below -
   *   see `KeycardConfig`'s doc.
   */
  constructor(
    definition: PolicyDefinition,
    config: KeycardConfig<TOperators> = {},
  ) {
    Policy.validateVersion(definition.version)

    const emitMeta = config.emitMeta ?? true
    const actions = buildCatalog(config.actions, "action", emitMeta)
    const subjects = buildCatalog(config.subjects, "subject", emitMeta)

    this.definition = definition
    this.config = config
    this.actionCatalog = actions.reverseMap
    this.subjectCatalog = subjects.reverseMap
    this.resolver = new ConditionResolver(normalizeOperators(config.operators))
    if (emitMeta) Policy.validateOperatorsRegistered(definition, this.resolver)
    Policy.validateRules(definition, actions.names, subjects.names, emitMeta)
  }

  /**
   * Builds a Policy from an already-parsed PolicyDefinition. KeyCard itself
   * never reads or writes policy.yaml text - an application (or a test, via
   * a YAML library of its own choosing) parses the file into a plain
   * PolicyDefinition object and hands it to KeyCard.
   */
  static from<
    TActions extends Action = Action,
    TSubjects extends Subject = Subject,
    TOperators extends AnyOperator = never,
  >(
    definition: PolicyDefinition,
    config: KeycardConfig<TOperators> = {},
  ): Policy<TActions, TSubjects, TOperators> {
    return new Policy(definition, config)
  }

  private static validateVersion(version: string): void {
    // PATCH (and MINOR) may be omitted - "1"/"1.0" are valid
    // shorthand for "1.0.0" - so coerce before comparing rather than
    // requiring a strict three-component string.
    const coerced = semver.coerce(version)
    if (!coerced || !semver.satisfies(coerced, KEYCARD_POLICY_SUPPORTED_VERSIONS)) {
      throw new PolicyVersionException(
        `Unsupported policy version "${coerced}": this implementation supports ${KEYCARD_POLICY_SUPPORTED_VERSIONS}.`,
      )
    }
  }

  /**
   * when `meta.operators` is declared, every
   * name it lists MUST already be registered on this Policy - built-in or
   * custom - checked once here when loading a policy, regardless of
   * whether any rule actually reaches that operator during evaluation.
   * Only run when `emitMeta` is true - see `KeycardConfig.emitMeta`'s doc.
   */
  private static validateOperatorsRegistered(definition: PolicyDefinition, resolver: ConditionResolver): void {
    const declared = definition.meta?.operators
    if (!declared) return

    resolver.assertAllRegistered(declared)
  }

  /**
   * @param configActionNames/@param configSubjectNames resolved catalog names (see `lib/catalog.ts`) that, when given, widen the `meta.actions`/`meta.subjects` catalogs below beyond what `definition.meta` declares.
   * @param emitMeta when false, skips only the catalog-coverage checks
   *   below (a rule action/subject/operator not covered by
   *   `meta.actions`/`meta.subjects`/`meta.operators`) - the structural
   *   checks (malformed rule tuples, EC-6) always run regardless, since
   *   those guard evaluation correctness rather than diagnostics.
   */
  private static validateRules(
    definition: PolicyDefinition,
    configActionNames: string[],
    configSubjectNames: string[],
    emitMeta: boolean,
  ): void {
    const meta = definition.meta
    const anyAction = effectiveAnyAction(meta)
    const anySubject = effectiveAnySubject(meta)

    const actionsCatalog = emitMeta && (meta?.actions || configActionNames.length > 0)
      ? new Set([...(meta?.actions ?? []), ...configActionNames])
      : undefined
    const subjectsCatalog = emitMeta && (meta?.subjects || configSubjectNames.length > 0)
      ? new Set([...(meta?.subjects ?? []), ...configSubjectNames])
      : undefined
    const operatorsCatalog = emitMeta && meta?.operators ? new Set(meta.operators) : undefined

    for (const rule of definition.rules as RuleTuple[]) {
      if (!Array.isArray(rule) || rule.length < 3) {
        throw new PolicyLoadException(
          `Malformed rule tuple (fewer than 3 elements): ${JSON.stringify(rule)}.`,
        )
      }

      const [effect, action, subjectName, conditions] = rule

      if (effect !== "allow" && effect !== "deny") {
        throw new PolicyLoadException(
          `Malformed rule tuple: effect must be "allow" or "deny", got ${JSON.stringify(effect)}.`,
        )
      }
      if (typeof action !== "string") {
        throw new PolicyLoadException(
          `Malformed rule tuple: action must be a string, got ${JSON.stringify(action)}.`,
        )
      }
      if (typeof subjectName !== "string") {
        throw new PolicyLoadException(
          `Malformed rule tuple: subject must be a string, got ${JSON.stringify(subjectName)}.`,
        )
      }

      const isWildcardAction = anyAction !== DISABLED && action === anyAction
      const isWildcardSubject = anySubject !== DISABLED && subjectName === anySubject

      if (isWildcardAction && isWildcardSubject && conditions) {
        throw new PolicyLoadException(
          `Rule [${effect}, ${action}, ${subjectName}] is wildcarded on both the action and the subject but carries a Conditions element - this MUST be unconditional (SPEC_V0.md property 5, EC-6).`,
        )
      }

      if (actionsCatalog && !isWildcardAction && !actionsCatalog.has(action)) {
        throw new PolicyLoadException(
          `Rule action "${action}" is not covered by meta.actions.`,
        )
      }
      if (subjectsCatalog && !isWildcardSubject && !subjectsCatalog.has(subjectName)) {
        throw new PolicyLoadException(
          `Rule subject "${subjectName}" is not covered by meta.subjects.`,
        )
      }

      if (operatorsCatalog && conditions) {
        const used = new Set<string>()
        collectCustomOperators(conditions, used)
        for (const op of used) {
          if (!operatorsCatalog.has(op)) {
            throw new PolicyLoadException(
              `Rule uses custom operator "${op}" not covered by meta.operators.`,
            )
          }
        }
      }
    }
  }

  def(): PolicyDefinition {
    return this.definition
  }

  can(action: TActions, subject: TSubjects): boolean {
    return this.checkPermission(action, subject)
  }

  cannot(action: TActions, subject: TSubjects): boolean {
    return !this.can(action, subject)
  }

  require(action: TActions, subject: TSubjects): void {
    if (!this.can(action, subject)) {
      const actionName = resolveName(this.actionCatalog, action.name)
      const subjectName = resolveName(this.subjectCatalog, subject.name)
      throw new PolicyError(`"${actionName}" is not allowed on this "${subjectName}"`)
    }
  }

  /**
   * SPEC_V0.md: reverse scan over `rules`, returning the effect of
   * the first (i.e. most-recently-declared) rule whose action, subject,
   * and (if present) conditions all match. There is no independent
   * "allow AND NOT deny" veto and no combination of multiple matching
   * rules: exactly one rule decides the outcome, or none does and the
   * result is default deny.
   */
  private checkPermission(action: TActions, subject: TSubjects): boolean {
    const meta = this.definition.meta
    const anyAction = effectiveAnyAction(meta)
    const anySubject = effectiveAnySubject(meta)
    const rules = this.definition.rules

    for (let i = rules.length - 1; i >= 0; i--) {
      const [effect, ruleAction, ruleSubject, ruleConditions] = rules[i]

      if (!this.matchesAction(action, ruleAction, anyAction)) continue
      if (!this.matchesSubject(subject, ruleSubject, anySubject)) continue

      if (ruleConditions) {
        // A conditional rule can never be satisfied by a bare-type/no-instance
        // check - there's no instance data for the condition to inspect (EC-7).
        if (subject.instance === undefined) continue
        if (!this.resolver.evaluate(subject.instance, ruleConditions, this.resolveFieldMapper(subject))) continue
        return effect === "allow"
      }

      return effect === "allow"
    }

    return false // EC-1, EC-2: default deny.
  }

  /** The subject's own `fieldMapper` (set via `createSubject`) takes precedence; `config.mapper`, keyed by the subject's resolved catalog name, is the fallback. */
  private resolveFieldMapper(subject: TSubjects): SubjectFieldMapper<unknown> | undefined {
    if (subject.fieldMapper) return subject.fieldMapper as SubjectFieldMapper<unknown>
    return this.config.mapper?.get(resolveName(this.subjectCatalog, subject.name))
  }

  private matchesAction(action: TActions, ruleAction: string, anyAction: string | typeof DISABLED): boolean {
    this.warnIfUnregisteredDynamic(action, this.actionCatalog, "Action", "createAction")
    const actionName = resolveName(this.actionCatalog, action.name)
    return actionName === ruleAction || (anyAction !== DISABLED && ruleAction === anyAction)
  }

  private matchesSubject(subject: TSubjects, ruleSubject: string, anySubject: string | typeof DISABLED): boolean {
    this.warnIfUnregisteredDynamic(subject, this.subjectCatalog, "Subject", "createSubject")
    const subjectName = resolveName(this.subjectCatalog, subject.name)
    return subjectName === ruleSubject || (anySubject !== DISABLED && ruleSubject === anySubject)
  }

  /**
   * A dynamic (no-name) Action/Subject never registered in any catalog
   * reachable from this Policy can't resolve to a real name - it falls
   * through to default-deny like any other non-match (unless a wildcard
   * rule catches it), but that's silent otherwise, so warn once per
   * distinct id rather than once per `.can()`/`.cannot()`/`.require()` call.
   */
  private warnIfUnregisteredDynamic(
    value: { name: string, __dynamic?: true },
    reverseMap: Map<string, string>,
    kind: string,
    factory: string,
  ): void {
    if (!value.__dynamic || reverseMap.has(value.name) || this.warnedDynamicIds.has(value.name)) return
    this.warnedDynamicIds.add(value.name)
    ;(this.config.logger ?? getLogger()).warn(
      `${kind} created via ${factory}() with no name was checked but never registered in any KeycardConfig catalog reachable from this Policy - it can never match a non-wildcard rule.`,
    )
  }
}
