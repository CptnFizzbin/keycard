import type { Subject } from "./subject.ts"
import type { SubjectFieldMapper } from "./subjectFieldMapper.ts"

function makeSubject<TData>(name: string, instance?: TData, fieldMapper?: SubjectFieldMapper<TData>): Subject<TData> {
  return {
    name,
    __brand: "subject",
    instance,
    fieldMapper,
    wrap(obj: TData): Subject<TData> {
      return makeSubject(name, obj, fieldMapper)
    },
  }
}

/**
 * Creates a bare Subject for `name` - no wrapped instance until `.wrap(obj)`
 * is called. `fieldMapper`, when given, is carried through every `.wrap()`
 * call unchanged (SPEC_V1-0-0.md §7.4.10/§7.4.11's field access).
 */
export function createSubject<TData = unknown>(name: string, fieldMapper?: SubjectFieldMapper<TData>): Subject<TData> {
  return makeSubject<TData>(name, undefined, fieldMapper)
}
