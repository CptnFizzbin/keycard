/** A value a JSON/YAML `PolicyDefinition` can hold. */
export type JsonValue =
  | string
  | number
  | boolean
  | null
  | undefined
  | JsonValue[]
  | { [key: string]: JsonValue }
