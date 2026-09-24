import type { Subject } from "./subject.ts"
import type { SubjectFieldMapper } from "./subjectFieldMapper.ts"
import { randomId } from "../lib/randomId.ts"

/**
 * Options for the dynamic (no-name) form of `createSubject` - a catalog key
 * supplies the name, so this form has no `name` parameter of its own.
 */
export interface CreateSubjectOptions<TData, TArgs extends unknown[] = [TData]> {
  /**
   * Maps one or more raw domain entities into this Subject's claims shape,
   * so `.from(...)` can build a wrapped Subject straight from application
   * data instead of a caller pre-shaping it for `.wrap()`. Omitted ->
   * `.from(data)` falls back to the identity mapping, behaving exactly like
   * `.wrap(data)`.
   */
  from?: (...args: TArgs) => TData
  /** Carried through every `.wrap()`/`.from()` call unchanged - see `SubjectFieldMapper`. */
  fieldMapper?: SubjectFieldMapper<TData>
}

function makeSubject<TData, TArgs extends unknown[]>(
  name: string,
  dynamic: true | undefined,
  instance: TData | undefined,
  fieldMapper: SubjectFieldMapper<TData> | undefined,
  fromMapper: (...args: TArgs) => TData,
): Subject<TData, TArgs> {
  return {
    name,
    __brand: "subject",
    __dynamic: dynamic,
    instance,
    fieldMapper,
    wrap(obj: TData): Subject<TData, TArgs> {
      return makeSubject(name, dynamic, obj, fieldMapper, fromMapper)
    },
    from(...args: TArgs): Subject<TData, TArgs> {
      return makeSubject(name, dynamic, fromMapper(...args), fieldMapper, fromMapper)
    },
  }
}

/** Creates a named Subject for `name`, given a `SubjectFieldMapper` directly - the existing two-positional-argument form. */
export function createSubject<TData = unknown, TArgs extends unknown[] = [TData]>(
  name: string,
  fieldMapper?: SubjectFieldMapper<TData>,
): Subject<TData, TArgs>
/**
 * Creates a bare Subject - no wrapped instance until `.wrap(obj)`/`.from(...)`
 * is called. Called with no `name` (`options` omitted, or an options object
 * with no `name` of its own), generates a random id in its place and marks
 * the Subject `__dynamic` - see {@link Subject.__dynamic} - so it must be
 * registered as a catalog value on a `KeycardConfig` before it's usable
 * with `PolicyBuilder`/`Policy`.
 *
 * `TArgs` defaults to `any[]` here (not `[TData]`, unlike the named-form
 * overload above) - TypeScript can't infer a later type parameter (`TArgs`,
 * from `options.from`'s own parameter types) once an earlier one (`TData`)
 * is given explicitly, which every `{ from: ... }` usage does (the whole
 * point is mapping some *other*, differently-shaped entity into `TData`).
 * `any[]` keeps that common case assignable without also spelling out
 * `TArgs` by hand; give it explicitly (`createSubject<TData, [Project]>`)
 * for a fully type-checked `.from(...)`.
 */
// eslint-disable-next-line @typescript-eslint/no-explicit-any
export function createSubject<TData = unknown, TArgs extends unknown[] = any[]>(
  options?: CreateSubjectOptions<TData, TArgs>,
): Subject<TData, TArgs>
export function createSubject<TData = unknown, TArgs extends unknown[] = [TData]>(
  nameOrOptions?: string | CreateSubjectOptions<TData, TArgs>,
  fieldMapper?: SubjectFieldMapper<TData>,
): Subject<TData, TArgs> {
  // The fallback for a Subject created with no `from` mapper - `.from(data)`
  // then behaves exactly like `.wrap(data)`. TArgs is caller-chosen (default
  // [TData]) so this can't be verified structurally; asserted once here
  // rather than at every call site.
  const identityFrom = ((...args: TArgs) => args[0]) as (...args: TArgs) => TData

  if (typeof nameOrOptions === "string") {
    return makeSubject<TData, TArgs>(nameOrOptions, undefined, undefined, fieldMapper, identityFrom)
  }

  const options = nameOrOptions ?? {}
  return makeSubject<TData, TArgs>(randomId(), true, undefined, options.fieldMapper, options.from ?? identityFrom)
}
