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
    Key, TSubject[Key] | Condition<TSubject[Key], TCustom>,
  ]
}[keyof TSubject]

export type ObjectCondition<TSubject extends object, TCustom> =
  | { [Key in keyof TSubject]?: TSubject[Key] | Condition<TSubject[Key], TCustom> }
  | { $field: FieldTuple<TSubject, TCustom> }

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
