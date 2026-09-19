import type { AnyCondition } from "../conditions/condition.ts"

/** SPEC_V0.md: a rule's effect - allow it, or deny it. */
export type Effect = "allow" | "deny"

/**
 * `[Effect, Action, Subject, Conditions?]` - SPEC_V0.md Action
 * and Subject are always plain strings here: `PolicyDefinition` is the
 * wire format (JSON-serializable, shared across languages), never the
 * ergonomic `Action`/`Subject` objects `PolicyBuilder`/`Policy`'s public
 * API accepts - those are reduced to their `.name` before ever reaching a
 * `RuleTuple`. A three-element tuple is an unconditional rule; `rules` is
 * a single, ordered list of these (not split by effect) - declaration
 * order is significant.
 */
export type RuleTuple =
  | [Effect, string, string]
  | [Effect, string, string, AnyCondition]

/** SPEC_V0.md: the optional `meta` object, grouping six independent, all-optional fields. */
export interface Meta {
  /**
   * The action wildcard token. Absent -> defaults to
   * `"_ANY_"`. Explicit `null` -> disables the action wildcard entirely
   * (no string, including `"_ANY_"`, has special meaning).
   */
  anyAction?: string | null
  /** The subject wildcard token, symmetric with `anyAction` in every respect. */
  anySubject?: string | null
  /** Declared action vocabulary; when present, enforced at construction. */
  actions?: string[]
  /** Declared subject vocabulary; when present, enforced at construction. */
  subjects?: string[]
  /** Declared custom `$`-operator vocabulary; when present, enforced at construction. */
  operators?: string[]
  /** Opaque application data - never validated, enforced, or cross-checked. */
  application?: unknown
}

/** The `PolicyDefinition` document shape - SPEC_V0.md */
export interface PolicyDefinition {
  /** Required SemVer string, e.g. `"1.0.0"` - see SPEC_V0.md */
  version: string
  /** Informational only - plays no role in evaluation. */
  name?: string
  /** Informational only - plays no role in evaluation. */
  description?: string
  meta?: Meta
  /** Ordered; declaration order is significant. MAY be empty. */
  rules: RuleTuple[]
}
