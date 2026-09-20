import type { Action } from "../action/index.ts"
import type { AnyCondition } from "../conditions/condition.ts"
import type { Condition } from "../conditions/index.ts"
import type { AnyOperator, InferCondition } from "../conditions/operators/operator.ts"
import { PolicyArgumentError } from "../errors/index.ts"
import type { KeycardConfig } from "../keycardConfig.ts"
import { buildCatalog, resolveName } from "../lib/catalog.ts"
import { DEFAULT_WILDCARD } from "../lib/wildcard.ts"
import { Policy } from "../policy/policy.ts"
import type { Effect, Meta, PolicyDefinition, RuleTuple } from "../policy/policyDefinition.ts"
import { DISABLED, effectiveAnyAction, effectiveAnySubject } from "../policy/wildcards.ts"
import type { Subject } from "../subject/index.ts"
import { KEYCARD_POLICY_VERSION } from "../version.ts"

/** The v1 SemVer this builder implements - stamped onto every `buildDef()` output, per SPEC_V0.md */
export const BUILDER_VERSION = KEYCARD_POLICY_VERSION

/**
 * The only things a caller ever needs to declare explicitly - the
 * wildcard tokens themselves, since nothing about them can be
 * inferred from usage, plus the custom operators to register. `anyAction`/
 * `anySubject` accept a bare token string, an `Action`/`Subject` (its
 * `.name` is used), or `null` to disable that wildcard position entirely;
 * omitted means the "_ANY_" default applies. Deliberately typed
 * against the base `Action`/`Subject` (not `TActions`/`TSubjects`) -
 * a wildcard token isn't one of the policy's own declared actions/
 * subjects, and tying it to those generics would make passing e.g. a
 * wildcard `Action` here narrow what `allow`/`deny` accept everywhere else
 * on the same builder.
 */
export interface PolicyBuilderOptions<TOperators extends AnyOperator = never> {
  anyAction?: Action | string | null
  anySubject?: Subject | string | null
  operators?: TOperators[]
}

/** @param reverseMap resolves a dynamic Action/Subject's random name to its catalog key - see `lib/catalog.ts`. */
function wildcardNameOf(value: Action | Subject | string | undefined | null, reverseMap: Map<string, string>): string | null {
  if (value === null) return null
  if (typeof value === "undefined") return DEFAULT_WILDCARD
  if (typeof value === "string") return value
  return resolveName(reverseMap, value.name)
}

/**
 * Builds a {@link PolicyDefinition} rule by rule. `meta.actions`/
 * `meta.subjects`/`meta.operators` are never supplied directly by default -
 * {@link buildDef} fills them in automatically from what {@link allow}/
 * {@link deny} actually used and what `operators` actually registered, so
 * there's no separately hand-maintained catalog to keep in sync by hand.
 * `config.actions`/`config.subjects` (constructor param, optional) declare
 * additional vocabulary up front, folded in alongside whatever usage
 * derives.
 */
export class PolicyBuilder<
  TActions extends Action = Action,
  TSubjects extends Subject = Subject,
  TOperators extends AnyOperator = never,
> {
  private rules: RuleTuple[] = []
  private readonly anyAction: string | null
  private readonly anySubject: string | null
  private readonly operators: TOperators[]
  private readonly actionsUsed = new Set<string>()
  private readonly subjectsUsed = new Set<string>()
  private readonly config: KeycardConfig<TOperators>
  private readonly actionCatalog: Map<string, string>
  private readonly subjectCatalog: Map<string, string>
  private readonly configActionNames: string[]
  private readonly configSubjectNames: string[]

  /**
   * @param config shared, optional config also accepted by `Policy`:
   *   `actions`/`subjects` are folded into `meta.actions`/`meta.subjects`
   *   alongside whatever `allow`/`deny` actually used; a keyed
   *   (`Record<string, Action|Subject>`) `actions`/`subjects` is also a
   *   catalog resolving a dynamic (no-name) Action/Subject's random name
   *   to its key, built once here and cached (see `lib/catalog.ts`);
   *   `operators`, when given, is used instead of `options.operators`;
   *   `mapper` is carried through to the built `Policy` unchanged.
   */
  constructor(
    options: PolicyBuilderOptions<TOperators> = {},
    config: KeycardConfig<TOperators> = {},
  ) {
    const actions = buildCatalog(config.actions, "action")
    const subjects = buildCatalog(config.subjects, "subject")
    this.actionCatalog = actions.reverseMap
    this.subjectCatalog = subjects.reverseMap
    this.configActionNames = actions.names
    this.configSubjectNames = subjects.names

    this.anyAction = wildcardNameOf(options.anyAction, this.actionCatalog)
    this.anySubject = wildcardNameOf(options.anySubject, this.subjectCatalog)
    this.config = config
    this.operators = config.operators ?? options.operators ?? []
  }

  allow<TAction extends TActions, TSubject extends TSubjects>(
    actions: TAction | TActions[],
    subject: TSubject,
    conditions?: Condition<
      TSubject extends Subject<infer TData> ? TData : never,
      InferCondition<TOperators>
    >,
  ): this {
    if (!Array.isArray(actions)) return this.allow([actions], subject, conditions)

    for (const action of actions) {
      this.addRule("allow", action, subject, conditions)
    }

    return this
  }

  deny<TAction extends TActions, TSubject extends TSubjects>(
    actions: TAction | TActions[],
    subject: TSubject,
    conditions?: Condition<
      TSubject extends Subject<infer TData> ? TData : never,
      InferCondition<TOperators>
    >,
  ): this {
    if (!Array.isArray(actions)) return this.deny([actions], subject, conditions)

    for (const action of actions) {
      this.addRule("deny", action, subject, conditions)
    }

    return this
  }

  build(): Policy<TActions, TSubjects, TOperators> {
    return new Policy(this.buildDef(), { operators: this.operators }, this.config)
  }

  buildDef(options: { includeMeta?: boolean } = {}): PolicyDefinition {
    const def: PolicyDefinition = {
      version: BUILDER_VERSION,
      meta: {}, // placeholder to hold it's spot when converted to JSON
      rules: this.rules,
    }

    if (options.includeMeta ?? true) {
      def.meta = this.buildMeta()
    } else {
      delete def.meta
    }

    return def
  }

  /** derives `actions`/`subjects`/`operators` from what was actually used/registered, plus whatever `config.actions`/`config.subjects` additionally declare - see the class doc. */
  private buildMeta(): Meta {
    const meta: Meta = {
      actions: Array.from(new Set([...this.actionsUsed, ...this.configActionNames])),
      subjects: Array.from(new Set([...this.subjectsUsed, ...this.configSubjectNames])),
    }
    if (this.anyAction !== DEFAULT_WILDCARD) meta.anyAction = this.anyAction
    if (this.anySubject !== DEFAULT_WILDCARD) meta.anySubject = this.anySubject
    if (this.operators.length > 0) meta.operators = this.operators.map((op) => op.name)
    return meta
  }

  private addRule(effect: Effect, action: TActions, subject: TSubjects, conditions?: AnyCondition): this {
    if (action.__dynamic && !this.actionCatalog.has(action.name)) {
      throw new PolicyArgumentError(
        `This Action was created via createAction() with no name and must be registered as a catalog value on the KeycardConfig handed to this PolicyBuilder before use.`,
      )
    }
    if (subject.__dynamic && !this.subjectCatalog.has(subject.name)) {
      throw new PolicyArgumentError(
        `This Subject was created via createSubject() with no name and must be registered as a catalog value on the KeycardConfig handed to this PolicyBuilder before use.`,
      )
    }

    const actionName = resolveName(this.actionCatalog, action.name)
    const subjectName = resolveName(this.subjectCatalog, subject.name)

    if (conditions) {
      // SPEC_V0.md property 5, EC-6: a rule wildcarded on both the
      // action and the subject MUST NOT carry a Conditions element - the
      // builder MUST catch this immediately, rather than waiting for
      // eventual construction (Policy.from) to catch it.
      const anyAction = effectiveAnyAction({ anyAction: this.anyAction })
      const anySubject = effectiveAnySubject({ anySubject: this.anySubject })
      if (
        anyAction !== DISABLED && actionName === anyAction
        && anySubject !== DISABLED && subjectName === anySubject
      ) {
        throw new PolicyArgumentError(
          `A rule wildcarded on both the action ("${anyAction}") and the subject ("${anySubject}") MUST NOT carry a Conditions element (SPEC_V0.md property 5, EC-6).`,
        )
      }
    }

    this.actionsUsed.add(actionName)
    this.subjectsUsed.add(subjectName)

    const rule: RuleTuple = conditions !== undefined
      ? [effect, actionName, subjectName, conditions]
      : [effect, actionName, subjectName]
    this.rules.push(rule)
    return this
  }
}
