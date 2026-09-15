import type { Subject } from "./subject.ts"

function makeSubject<TData>(name: string, instance?: TData): Subject<TData> {
  return {
    name,
    __brand: "subject",
    instance,
    wrap(obj: TData): Subject<TData> {
      return makeSubject(name, obj)
    },
  }
}

/** Creates a bare Subject for `name` - no wrapped instance until `.wrap(obj)` is called. */
export function createSubject<TData = unknown>(name: string): Subject<TData> {
  return makeSubject<TData>(name)
}
