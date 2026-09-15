import type { Action } from "./action"

/**
 * Infers the union of `Action` types expected by `PolicyBuilder`/`Policy`
 * from an actions map, so callers don't have to spell out
 * `typeof Actions[keyof typeof Actions]` by hand.
 *
 * @example
 * const Actions = {
 *   Create: createAction("Create"),
 *   Read: createAction("Read"),
 * } as const;
 *
 * type AppActions = InferActions<typeof Actions>;
 * new PolicyBuilder<AppActions, AppSubjects>()
 */
export type InferActions<T extends Record<string, Action>> = T[keyof T]
