import type { JsonValue } from "../lib/json.ts"

export type Condition =
  | JsonValue
  | { [key: string]: Condition }
