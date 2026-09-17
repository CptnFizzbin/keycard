import { randomId } from "../lib/randomId.ts"
import type { Action } from "./action.ts"

/**
 * Creates a named Action. Called with no `name`, generates a random id in
 * its place and marks the Action `__dynamic` - see {@link Action.__dynamic}
 * - so it must be registered as a catalog value on a `KeycardConfig` before
 * it's usable with `PolicyBuilder`/`Policy`.
 */
export function createAction<T extends string = string>(name?: T): Action<T> {
  if (name === undefined) return { name: randomId() as T, __brand: "action", __dynamic: true }
  return { name, __brand: "action" }
}
