import type { AnyCondition } from "../conditions/condition.ts"

/** SPEC_V0.md §3.3: a rule's effect - allow it, or deny it. */
export type Effect = "allow" | "deny"

/**
 * `[Effect, Action, Subject, Conditions?]` - SPEC_V0.md §3.3. Action
 * and Subject are always plain strings here: `PolicyDefinition` is the
 * wire format (JSON-serializable, shared across languages), never the
 * ergonomic `Action`/`Subject` objects `PolicyBuilder`/`Policy`'s public
 * API accepts - those are reduced to their `.name` before ever reaching a
 * `RuleTuple`. A three-element tuple is an unconditional rule; `rules` is
 * a single, ordered list of these (not split by effect) - declaration
 * order is significant (§6).
 */
export type RuleTuple =
  | [Effect, string, string]
  | [Effect, string, string, AnyCondition]

/** SPEC_V0.md §3.2: the optional `meta` object, grouping six independent, all-optional fields. */
export interface Meta {
  /**
   * The action wildcard token (§3.2.1, §4). Absent -> defaults to
   * `"_ANY_"`. Explicit `null` -> disables the action wildcard entirely
   * (no string, including `"_ANY_"`, has special meaning).
   */
  anyAction?: string | null
  /** The subject wildcard token (§3.2.1, §5), symmetric with `anyAction` in every respect. */
  anySubject?: string | null
  /** Declared action vocabulary; when present, enforced at construction (§3.2.2, EC-8). */
  actions?: string[]
  /** Declared subject vocabulary; when present, enforced at construction (§3.2.2, EC-8). */
  subjects?: string[]
  /** Declared custom `$`-operator vocabulary; when present, enforced at construction (§3.2.3, EC-13). */
  operators?: string[]
  /** Opaque application data - never validated, enforced, or cross-checked (§3.2.4). */
  application?: unknown
}

/** The `PolicyDefinition` document shape - SPEC_V0.md §3. */
export interface PolicyDefinition {
  /** Required SemVer string, e.g. `"1.0.0"` - see SPEC_V0.md §2. */
  version: string
  /** Informational only - plays no role in evaluation. */
  name?: string
  /** Informational only - plays no role in evaluation. */
  description?: string
  meta?: Meta
  /** Ordered; declaration order is significant (§3.3, §6). MAY be empty. */
  rules: RuleTuple[]
}
