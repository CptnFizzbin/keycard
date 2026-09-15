import type { Action } from "./action.ts"

export function createAction<T extends string>(name: T): Action<T> {
  return { name, __brand: "action" }
}
