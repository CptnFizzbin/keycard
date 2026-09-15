import type { Action } from "../action/index.ts"
import type { Condition } from "../conditions/index.ts"
import type { Operator } from "../conditions/operators/operator.ts"
import { PolicyArgumentError } from "../errors/index.ts"
import { Policy } from "../policy/policy.ts"
import type { Effect, Meta, PolicyDefinition, RuleTuple } from "../policy/policyDefinition.ts"
import { DISABLED, effectiveAnyAction, effectiveAnySubject } from "../policy/wildcards.ts"
import type { Subject } from "../subject/index.ts"
import { KEYCARD_POLICY_VERSION } from "../version.ts"

/** The v1 SemVer this builder implements - stamped onto every `buildDef()` output, per SPEC_V1-0-0.md §2. */
export const BUILDER_VERSION = KEYCARD_POLICY_VERSION

/**
 * The only things a caller ever needs to declare explicitly - the
 * wildcard tokens themselves (§3.2.1), since nothing about them can be
 * inferred from usage, plus the custom operators to register. `anyAction`/
 * `anySubject` accept a bare token string, an `Action`/`Subject` (its
 * `.name` is used), or `null` to disable that wildcard position entirely;
 * omitted means the §3.2.1 "_ANY_" default applies. Deliberately typed
 * against the base `Action`/`Subject` (not `TActions`/`TSubjects`) -
 * a wildcard token isn't one of the policy's own declared actions/
 * subjects, and tying it to those generics would make passing e.g. a
 * wildcard `Action` here narrow what `allow`/`deny` accept everywhere else
 * on the same builder.
 */
export interface PolicyBuilderOptions {
  anyAction?: Action | string | null
  anySubject?: Subject | string | null
  operators?: Operator[]
}

function wildcardNameOf(value: Action | Subject | string | null | undefined): string | null | undefined {
  if (value === null || value === undefined || typeof value === "string") return value
  return value.name
}

/**
 * Builds a {@link PolicyDefinition} rule by rule. `meta.actions`/
 * `meta.subjects`/`meta.operators` are never supplied directly -
 * {@link buildDef} fills them in automatically from what {@link allow}/
 * {@link deny} actually used and what `operators` actually registered, so
 * there's no separately hand-maintained catalog to keep in sync by hand.
 */
export class PolicyBuilder<
  TActions extends Action = Action,
  TSubjects extends Subject = Subject,
> {
  private rules: RuleTuple[] = []
  private readonly anyAction: string | null | undefined
  private readonly anySubject: string | null | undefined
  private readonly operators: Operator[]
  private readonly actionsUsed = new Set<string>()
  private readonly subjectsUsed = new Set<string>()

  constructor(options: PolicyBuilderOptions = {}) {
    this.anyAction = wildcardNameOf(options.anyAction)
    this.anySubject = wildcardNameOf(options.anySubject)
    this.operators = options.operators ?? []
  }

  allow<T extends TActions>(action: T, subject: TSubjects, conditions?: Condition): this {
    return this.addRule("allow", action, subject, conditions)
  }

  deny<T extends TActions>(action: T, subject: TSubjects, conditions?: Condition): this {
    return this.addRule("deny", action, subject, conditions)
  }

  build(): Policy<TActions, TSubjects> {
    return new Policy(this.buildDef(), this.operators)
  }

  buildDef(): PolicyDefinition {
    return {
      version: BUILDER_VERSION,
      meta: this.buildMeta(),
      rules: this.rules,
    }
  }

  /** §3.2.2/§3.2.3: derives `actions`/`subjects`/`operators` from what was actually used/registered - see the class doc. */
  private buildMeta(): Meta {
    const meta: Meta = {
      actions: Array.from(this.actionsUsed),
      subjects: Array.from(this.subjectsUsed),
    }
    if (this.anyAction !== undefined) meta.anyAction = this.anyAction
    if (this.anySubject !== undefined) meta.anySubject = this.anySubject
    if (this.operators.length > 0) meta.operators = this.operators.map((op) => op.name)
    return meta
  }

  private addRule(effect: Effect, action: TActions, subject: TSubjects, conditions?: Condition): this {
    if (conditions) {
      // SPEC_V1-0-0.md §6 property 5, EC-6: a rule wildcarded on both the
      // action and the subject MUST NOT carry a Conditions element - the
      // builder MUST catch this immediately, rather than waiting for
      // eventual construction (Policy.from) to catch it.
      const anyAction = effectiveAnyAction({ anyAction: this.anyAction })
      const anySubject = effectiveAnySubject({ anySubject: this.anySubject })
      if (
        anyAction !== DISABLED && action.name === anyAction
        && anySubject !== DISABLED && subject.name === anySubject
      ) {
        throw new PolicyArgumentError(
          `A rule wildcarded on both the action ("${anyAction}") and the subject ("${anySubject}") MUST NOT carry a Conditions element (SPEC_V1-0-0.md §6 property 5, EC-6).`,
        )
      }
    }

    this.actionsUsed.add(action.name)
    this.subjectsUsed.add(subject.name)

    const rule: RuleTuple = conditions !== undefined
      ? [effect, action.name, subject.name, conditions]
      : [effect, action.name, subject.name]
    this.rules.push(rule)
    return this
  }
}
