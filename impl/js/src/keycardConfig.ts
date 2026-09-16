import type { Action } from "./action/index.ts"
import type { AnyOperator } from "./conditions/operators/operator.ts"
import type { Subject, SubjectFieldMapperCatalog } from "./subject/index.ts"

/**
 * Optional, shared config both `Policy` and `PolicyBuilder` accept
 * alongside their existing options - one object bundling the actions/
 * subjects a policy is written against, its custom operators, and the
 * SubjectFieldMappers its subjects need, built once and handed to both
 * rather than kept in sync by hand. Every field is independently optional.
 *
 * `actions`/`subjects` are deliberately typed against the base `Action`/
 * `Subject` (not a builder's `TActions`/`TSubjects`) - same reasoning as
 * `PolicyBuilderOptions.anyAction`/`anySubject`: tying them to those
 * generics would infer `TActions`/`TSubjects` from this catalog alone and
 * narrow what `allow`/`deny` accept everywhere else on the same builder.
 */
export interface KeycardConfig<TOperators extends AnyOperator = never> {
  /** Declared action vocabulary, additive to `meta.actions` (SPEC_V1-0-0.md §3.2.2, EC-8). */
  actions?: Action[]
  /** Declared subject vocabulary, additive to `meta.subjects` (SPEC_V1-0-0.md §3.2.2, EC-8). */
  subjects?: Subject[]
  /** Custom operators to register alongside the built-ins (SPEC_V1-0-0.md §7.4.12). */
  operators?: TOperators[]
  /** SubjectFieldMappers registered by subject name - consulted when the Subject in hand doesn't carry its own `fieldMapper`. */
  mapper?: SubjectFieldMapperCatalog
}
