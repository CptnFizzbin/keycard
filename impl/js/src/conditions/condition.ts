// eslint-disable-next-line @typescript-eslint/no-explicit-any
export type AnyCondition = Condition<any, any>

export type Condition<TSubject, TCustom = never> =
  | LogicCondition<TSubject, TCustom>
  | ValueCondition<TSubject, TCustom>
  | TCustom

export type LogicCondition<TSubject, TCustom> =
  | { $or: Condition<TSubject, TCustom>[] }
  | { $and: Condition<TSubject, TCustom>[] }
  | { $not: TSubject | Condition<TSubject, TCustom> }

export type ValueCondition<TSubject, TCustom> =
  TSubject extends unknown[] ? ArrayCondition<TSubject> // must be first, as arrays are objects
    : TSubject extends object ? ObjectCondition<TSubject, TCustom>
      : TSubject extends PrimativeValue ? PrimativeConditon<TSubject>
        : never

export type ArrayCondition<TSubject extends unknown[]> =
  | { $has: TSubject[number] }

export type FieldTuple<TSubject extends object, TCustom> = {
  [Key in keyof TSubject]: [
    Key, TSubject[Key] | FieldValueCondition<TSubject[Key], TCustom>,
  ]
}[keyof TSubject]

export type ObjectCondition<TSubject extends object, TCustom> =
  | { [Key in keyof TSubject]?: TSubject[Key] | FieldValueCondition<TSubject[Key], TCustom> }
  | { $field: FieldTuple<TSubject, TCustom> }

/**
 * v1 supports only top-level field access (SPEC_V1-0.md §5.4.10): once a
 * field condition (bare-key or `$field`) has narrowed once, its own
 * `Condition` MUST NOT narrow again. This is the same shape as
 * {@link Condition}, minus {@link ObjectCondition} - comparison,
 * collection, string, logical, and custom operators all still apply to the
 * narrowed value, but a further field condition would attempt a second
 * level of nesting, which isn't part of v1.
 */
export type FieldValueCondition<TSubject, TCustom = never> =
  | FieldLogicCondition<TSubject, TCustom>
  | FieldLeafCondition<TSubject>
  | TCustom

export type FieldLogicCondition<TSubject, TCustom> =
  | { $or: FieldValueCondition<TSubject, TCustom>[] }
  | { $and: FieldValueCondition<TSubject, TCustom>[] }
  | { $not: TSubject | FieldValueCondition<TSubject, TCustom> }

export type FieldLeafCondition<TSubject> =
  TSubject extends unknown[] ? ArrayCondition<TSubject>
    : TSubject extends PrimativeValue ? PrimativeConditon<TSubject>
      // an object-typed field value can no longer narrow further, but
      // equality against the whole value is still meaningful
      : { $eq: TSubject } | { $ne: TSubject }

export type PrimativeValue = number | string | boolean | null | undefined

export type PrimativeConditon<TSubject extends PrimativeValue> =
  | { $eq: TSubject }
  | { $ne: TSubject }
  | { $in: TSubject[] }
  | (
  TSubject extends number ? NumberCondition
    : TSubject extends string ? StringCondition
      : never
  )

export type NumberCondition =
  | { $gt: number }
  | { $gte: number }
  | { $lt: number }
  | { $lte: number }

export type StringCondition =
  | { $substr: string }
