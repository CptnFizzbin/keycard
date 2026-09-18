import * as semver from "semver"

import type { PolicyDefinition, RuleTuple } from "./policyDefinition.ts"
import { DISABLED, effectiveAnyAction, effectiveAnySubject } from "./wildcards.ts"
import type { Action } from "../action/index.ts"
import type { AnyCondition } from "../conditions/condition.ts"
import { BUILTIN_OPERATOR_NAMES } from "../conditions/conditionResolver.ts"
import { ConditionResolver } from "../conditions/index.ts"
import type { AnyOperator } from "../conditions/operators/operator.ts"
import { PolicyError, PolicyLoadException, PolicyVersionException } from "../errors/index.ts"
import type { KeycardConfig } from "../keycardConfig.ts"
import { buildCatalog, resolveName } from "../lib/catalog.ts"
import { getLogger } from "../lib/logger.ts"
import type { Subject } from "../subject/index.ts"
import type { SubjectFieldMapper } from "../subject/subjectFieldMapper.ts"
import { KEYCARD_POLICY_VERSION } from "../version.ts"

/** The highest version this implementation supports natively - SPEC_V1-0.md §2. PATCH never affects compatibility. Single-sourced from {@link KEYCARD_POLICY_VERSION}, alongside `PolicyBuilder`'s `BUILDER_VERSION`, so the two can never drift apart. */
const SUPPORTED_VERSION = KEYCARD_POLICY_VERSION
// §2.1: MINOR/PATCH may be omitted from KEYCARD_POLICY_VERSION itself
// ("1.0" is valid shorthand for "1.0.0"), so coerce here too rather than
// the strict major()/minor(), which throw on a non-three-component string.
const SUPPORTED_COERCED = semver.coerce(SUPPORTED_VERSION)
if (!SUPPORTED_COERCED) {
  throw new Error(`KEYCARD_POLICY_VERSION "${SUPPORTED_VERSION}" is not a valid SemVer version string.`)
}
const SUPPORTED_MAJOR = SUPPORTED_COERCED.major
const SUPPORTED_MINOR = SUPPORTED_COERCED.minor
/** Same MAJOR as SUPPORTED_VERSION, MINOR no higher - PATCH is irrelevant either way (§2). */
const COMPATIBLE_RANGE = `>=${SUPPORTED_MAJOR}.0.0 <${SUPPORTED_MAJOR}.${SUPPORTED_MINOR + 1}.0`

/**
 * Recursively collects every non-built-in, `$`-prefixed operator name used
 * anywhere in a Conditions tree - used to enforce `meta.operators`
 * coverage at construction time (§3.2.3, EC-13).
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

export interface PolicyOptions<TOperators extends AnyOperator = never> {
  operators?: TOperators[]
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
   *   (SPEC_V1-0.md §4.2.2/§7.4.12 and the SubjectFieldMapper feature):
   *   `actions`/`subjects` widen the `meta.actions`/`meta.subjects`
   *   catalogs beyond what `definition.meta` itself declares; a keyed
   *   (`Record<string, Action|Subject>`) `actions`/`subjects` is also a
   *   catalog resolving a dynamic (no-name) Action/Subject's random name
   *   to its key, built once here and cached (see `lib/catalog.ts`);
   *   `operators`, when given, is used instead of `options.operators`;
   *   `mapper` is consulted for a subject's fields whenever the `Subject`
   *   passed to {@link can} doesn't carry its own `fieldMapper`.
   */
  constructor(
    definition: PolicyDefinition,
    options: PolicyOptions<TOperators> = {},
    config: KeycardConfig<TOperators> = {},
  ) {
    Policy.validateVersion(definition.version)

    const actions = buildCatalog(config.actions, "action")
    const subjects = buildCatalog(config.subjects, "subject")

    this.definition = definition
    this.config = config
    this.actionCatalog = actions.reverseMap
    this.subjectCatalog = subjects.reverseMap
    this.resolver = new ConditionResolver(config.operators ?? options.operators)
    Policy.validateOperatorsRegistered(definition, this.resolver)
    Policy.validateRules(definition, actions.names, subjects.names)
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
    options: PolicyOptions<TOperators> = {},
    config: KeycardConfig<TOperators> = {},
  ): Policy<TActions, TSubjects, TOperators> {
    return new Policy(definition, options, config)
  }

  private static validateVersion(version: string): void {
    // §2.1: PATCH (and MINOR) may be omitted - "1"/"1.0" are valid
    // shorthand for "1.0.0" - so coerce before comparing rather than
    // requiring a strict three-component string.
    const coerced = semver.coerce(version)
    if (!coerced || !semver.satisfies(coerced, COMPATIBLE_RANGE)) {
      throw new PolicyVersionException(
        `Unsupported policy version "${version}": this implementation supports ${SUPPORTED_MAJOR}.0.0 through ${SUPPORTED_MAJOR}.${SUPPORTED_MINOR}.x (SPEC_V1-0.md §2).`,
      )
    }
  }

  /**
   * §3.2.3, EC-15 (promoted): when `meta.operators` is declared, every
   * name it lists MUST already be registered on this Policy - built-in or
   * custom - checked once here at construction time, regardless of
   * whether any rule actually reaches that operator during evaluation.
   * This replaces the previous behavior of deferring an unregistered-but-
   * cataloged name to a runtime-only diagnostic.
   */
  private static validateOperatorsRegistered(definition: PolicyDefinition, resolver: ConditionResolver): void {
    const declared = definition.meta?.operators
    if (!declared) return

    resolver.assertAllRegistered(declared)
  }

  /** @param configActionNames/@param configSubjectNames resolved catalog names (see `lib/catalog.ts`) that, when given, widen the `meta.actions`/`meta.subjects` catalogs below (§4.2.2) beyond what `definition.meta` declares. */
  private static validateRules(definition: PolicyDefinition, configActionNames: string[], configSubjectNames: string[]): void {
    const meta = definition.meta
    const anyAction = effectiveAnyAction(meta)
    const anySubject = effectiveAnySubject(meta)

    const actionsCatalog = meta?.actions || configActionNames.length > 0
      ? new Set([...(meta?.actions ?? []), ...configActionNames])
      : undefined
    const subjectsCatalog = meta?.subjects || configSubjectNames.length > 0
      ? new Set([...(meta?.subjects ?? []), ...configSubjectNames])
      : undefined
    const operatorsCatalog = meta?.operators ? new Set(meta.operators) : undefined

    for (const rule of definition.rules as RuleTuple[]) {
      if (!Array.isArray(rule) || rule.length < 3) {
        throw new PolicyLoadException(
          `Malformed rule tuple (fewer than 3 elements): ${JSON.stringify(rule)} (SPEC_V1-0.md §3.3, EC-10).`,
        )
      }

      const [effect, action, subjectName, conditions] = rule

      if (effect !== "allow" && effect !== "deny") {
        throw new PolicyLoadException(
          `Malformed rule tuple: effect must be "allow" or "deny", got ${JSON.stringify(effect)} (SPEC_V1-0.md §3.3, EC-10).`,
        )
      }
      if (typeof action !== "string") {
        throw new PolicyLoadException(
          `Malformed rule tuple: action must be a string, got ${JSON.stringify(action)} (SPEC_V1-0.md §3.3, EC-10).`,
        )
      }
      if (typeof subjectName !== "string") {
        throw new PolicyLoadException(
          `Malformed rule tuple: subject must be a string, got ${JSON.stringify(subjectName)} (SPEC_V1-0.md §3.3, EC-10).`,
        )
      }

      const isWildcardAction = anyAction !== DISABLED && action === anyAction
      const isWildcardSubject = anySubject !== DISABLED && subjectName === anySubject

      if (isWildcardAction && isWildcardSubject && conditions) {
        throw new PolicyLoadException(
          `Rule [${effect}, ${action}, ${subjectName}] is wildcarded on both the action and the subject but carries a Conditions element - this MUST be unconditional (SPEC_V1-0.md §6 property 5, EC-6).`,
        )
      }

      if (actionsCatalog && !isWildcardAction && !actionsCatalog.has(action)) {
        throw new PolicyLoadException(
          `Rule action "${action}" is not covered by meta.actions (SPEC_V1-0.md §3.2.2, EC-8).`,
        )
      }
      if (subjectsCatalog && !isWildcardSubject && !subjectsCatalog.has(subjectName)) {
        throw new PolicyLoadException(
          `Rule subject "${subjectName}" is not covered by meta.subjects (SPEC_V1-0.md §3.2.2, EC-8).`,
        )
      }

      if (operatorsCatalog && conditions) {
        const used = new Set<string>()
        collectCustomOperators(conditions, used)
        for (const op of used) {
          if (!operatorsCatalog.has(op)) {
            throw new PolicyLoadException(
              `Rule uses custom operator "${op}" not covered by meta.operators (SPEC_V1-0.md §3.2.3, EC-13).`,
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
      throw new PolicyError(`Access denied: cannot ${actionName} on ${subjectName}`)
    }
  }

  /**
   * SPEC_V1-0.md §6: reverse scan over `rules`, returning the effect of
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
