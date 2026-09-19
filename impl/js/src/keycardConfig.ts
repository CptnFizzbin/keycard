import type { Action } from "./action/index.ts"
import type { AnyOperator } from "./conditions/operators/operator.ts"
import type { Logger } from "./lib/logger.ts"
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
  /**
   * Declared action vocabulary, additive to `meta.actions` (SPEC_V0.md
   * ). A plain array declares vocabulary only - each entry's own
   * `.name` is used as-is. A keyed object (`Record<string, Action>`) is
   * also a *catalog*: its key becomes the serialized name for that entry,
   * which is how a `createAction()` call with no name (see {@link
   * Action.__dynamic}) gets a real, stable name.
   */
  actions?: Action[] | Record<string, Action>
  /** Declared subject vocabulary, additive to `meta.subjects` - see `actions` for the keyed-catalog form. */
  subjects?: Subject[] | Record<string, Subject>
  /** Custom operators to register alongside the built-ins. */
  operators?: TOperators[]
  /** SubjectFieldMappers registered by subject name - consulted when the Subject in hand doesn't carry its own `fieldMapper`. */
  mapper?: SubjectFieldMapperCatalog
  /** Logger for non-fatal diagnostics (currently: an unregistered dynamic Action/Subject encountered at `.can()`/`.cannot()`/`.require()` time) - falls back to the module-level `getLogger()` when unset. */
  logger?: Logger
}
