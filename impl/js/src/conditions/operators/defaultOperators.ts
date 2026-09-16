import { HasOperator } from "./collection/hasOperator.ts"
import { InOperator } from "./collection/inOperator.ts"
import { EqOperator } from "./equality/eqOperator.ts"
import { NeOperator } from "./equality/neOperator.ts"
import { FieldOperator } from "./field/fieldOperator.ts"
import { AndOperator } from "./logical/andOperator.ts"
import { NotOperator } from "./logical/notOperator.ts"
import { OrOperator } from "./logical/orOperator.ts"
import { GtOperator } from "./numeric/gtOperator.ts"
import { GteOperator } from "./numeric/gteOperator.ts"
import { LtOperator } from "./numeric/ltOperator.ts"
import { LteOperator } from "./numeric/lteOperator.ts"
import type { AnyOperator } from "./operator.ts"
import { SubstrOperator } from "./string/substrOperator.ts"

/** Every operator {@link ConditionResolver} understands natively (SPEC_V1-0.md §7.4.1-§7.4.11). */
export const DefaultOperators: AnyOperator[] = [
  EqOperator,
  NeOperator,
  GtOperator,
  GteOperator,
  LtOperator,
  LteOperator,
  InOperator,
  HasOperator,
  SubstrOperator,
  OrOperator,
  AndOperator,
  NotOperator,
  FieldOperator,
]
