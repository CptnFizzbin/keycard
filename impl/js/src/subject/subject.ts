/* eslint-disable @typescript-eslint/no-explicit-any */

/** A named subject - the Subject position of a rule. `instance` is `undefined` for a bare type check and set by `.wrap()`/`.from()`. */
export interface Subject<
  TClaims = never,
  TArgs extends unknown[] = [TClaims],
> {
  readonly id: string

  readonly name: string

  readonly claims?: TClaims

  readonly __brand: "subject"
  /** Set only by `createSubject()` with no name - see `Action.__dynamic`. */

  readonly __dynamic?: true

  /** Returns a new Subject of the same name, wrapping `obj` as its instance. */
  wrap(obj: TClaims): Subject<TClaims>

  /** Returns a Subject with claims generated from one or more objects */
  from(...args: TArgs): Subject<TClaims>
}

export type AnySubject = Subject<any, any>

/** A keyed collection of Subjects - see `ActionCatalog`'s doc, symmetric for Subjects. Handed to `PolicyBuilder`/`Policy` via `KeycardConfig.subjects`. */
export type SubjectsRecord = Record<string, AnySubject>

/** The claims type a Subject carries - what `createSubject<TClaims>()` was given. */
export type InferClaims<TSubject> = TSubject extends Subject<infer TClaims, infer _TArgs> ? TClaims : never
