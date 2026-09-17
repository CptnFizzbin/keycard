import type { SubjectFieldMapper } from "./subjectFieldMapper.ts"

/**
 * A named, type-safe subject - SPEC_V1-0.md §3.3's Subject position.
 * Unifies what used to be two separate shapes (a bare type token and a
 * wrapped instance reference) into one: `instance` is `undefined` for a
 * bare type-check (§5, EC-7/EC-9 - no instance data for a Conditions
 * element to inspect) and set once `.wrap(obj)` is called. `__brand` is a
 * runtime discriminant, symmetric with `Action`'s.
 */
export interface Subject<TData = unknown> {
  readonly name: string
  readonly instance?: TData
  /**
   * Optional per-field getters for `instance`, set via `createSubject` and
   * carried through `.wrap()` unchanged. Typed loosely here (vs.
   * `createSubject`'s `SubjectFieldMapper<TData>` parameter) so
   * `Subject<TData>` stays covariant in `TData` - the same escape hatch
   * `wrap`'s method shorthand gets implicitly.
   */
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  readonly fieldMapper?: SubjectFieldMapper<any>
  readonly __brand: "subject"
  /** Returns a new Subject of the same name, wrapping `obj` as its instance. */
  wrap(obj: TData): Subject<TData>
}
