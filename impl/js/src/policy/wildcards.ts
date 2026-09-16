import type { Meta } from "./policyDefinition.ts"

/**
 * Returned by `effectiveAnyAction`/`effectiveAnySubject` when a policy
 * explicitly disables that wildcard position (`meta.anyAction`/
 * `meta.anySubject: null`). No ordinary string can ever equal this
 * sentinel, so the wildcard branch of `matchesAction`/`matchesSubject`
 * never succeeds for that position (SPEC_V1-0.md §3.2.1, §4, §5, §6).
 */
export const DISABLED: unique symbol = Symbol("keycard:wildcard-disabled")

const DEFAULT_WILDCARD = "_ANY_"

/** meta.anyAction: absent -> "_ANY_" default; explicit string -> that string; explicit null -> DISABLED. */
export function effectiveAnyAction(meta?: Meta): string | typeof DISABLED {
  if (!meta || meta.anyAction === undefined) return DEFAULT_WILDCARD
  if (meta.anyAction === null) return DISABLED
  return meta.anyAction
}

/** meta.anySubject: absent -> "_ANY_" default; explicit string -> that string; explicit null -> DISABLED. */
export function effectiveAnySubject(meta?: Meta): string | typeof DISABLED {
  if (!meta || meta.anySubject === undefined) return DEFAULT_WILDCARD
  if (meta.anySubject === null) return DISABLED
  return meta.anySubject
}
