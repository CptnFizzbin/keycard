import { randomId } from "../lib/randomId.ts"
import type { Subject } from "./subject.ts"
import type { SubjectFieldMapper } from "./subjectFieldMapper.ts"

function makeSubject<TData>(
  name: string,
  dynamic: true | undefined,
  instance?: TData,
  fieldMapper?: SubjectFieldMapper<TData>,
): Subject<TData> {
  return {
    name,
    __brand: "subject",
    __dynamic: dynamic,
    instance,
    fieldMapper,
    wrap(obj: TData): Subject<TData> {
      return makeSubject(name, dynamic, obj, fieldMapper)
    },
  }
}

/**
 * Creates a bare Subject for `name` - no wrapped instance until `.wrap(obj)`
 * is called. `fieldMapper`, when given, is carried through every `.wrap()`
 * call unchanged (SPEC_V1-0-0.md field access).
 *
 * Called with no `name`, generates a random id in its place and marks the
 * Subject `__dynamic` - see {@link Subject.__dynamic} - so it must be
 * registered as a catalog value on a `KeycardConfig` before it's usable
 * with `PolicyBuilder`/`Policy`.
 */
export function createSubject<TData = unknown>(name?: string, fieldMapper?: SubjectFieldMapper<TData>): Subject<TData> {
  if (name === undefined) return makeSubject<TData>(randomId(), true, undefined, fieldMapper)
  return makeSubject<TData>(name, undefined, undefined, fieldMapper)
}
