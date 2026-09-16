/**
 * A named, type-safe action - SPEC_V1-0.md §3.3's Action position,
 * wrapped for compile-time safety. Always constructed via `createAction`;
 * `__brand` is a runtime discriminant (distinguishing an Action from a
 * Subject, and from an arbitrary object, at a duck-typed boundary) as well
 * as a compile-time one.
 */
export interface Action<T extends string = string> {
  readonly name: T
  readonly __brand: "action"
}
