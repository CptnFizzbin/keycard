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
  | ConstantCondition

/** A condition that ignores the subject: `$always` matches everything, `$never` nothing. */
export type ConstantCondition =
  | { $always: true }
  | { $never: true }

export type ValueCondition<TSubject, TCustom> =
  TSubject extends unknown[] ? ArrayCondition<TSubject> // must be first, as arrays are objects
    : TSubject extends object ? ObjectCondition<TSubject, TCustom>
      : TSubject extends PrimitiveValue ? PrimitiveCondition<TSubject>
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
 * The condition applied to a field's value once a field condition (bare-key
 * or `$field`) has narrowed into it: the same shape as {@link Condition}
 * minus {@link ObjectCondition}, since the spec allows only one level of
 * field access.
 */
export type FieldValueCondition<TSubject, TCustom = never> =
  | FieldLogicCondition<TSubject, TCustom>
  | FieldLeafCondition<TSubject>
  | TCustom

export type FieldLogicCondition<TSubject, TCustom> =
  | { $or: FieldValueCondition<TSubject, TCustom>[] }
  | { $and: FieldValueCondition<TSubject, TCustom>[] }
  | { $not: TSubject | FieldValueCondition<TSubject, TCustom> }
  | ConstantCondition

export type FieldLeafCondition<TSubject> =
  TSubject extends unknown[] ? ArrayCondition<TSubject>
    : TSubject extends PrimitiveValue ? PrimitiveCondition<TSubject>
      // an object-typed field value can no longer narrow further, but
      // equality against the whole value is still meaningful
      : { $eq: TSubject } | { $ne: TSubject }

export type PrimitiveValue = number | string | boolean | null | undefined

export type PrimitiveCondition<TSubject extends PrimitiveValue> =
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
