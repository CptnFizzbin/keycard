import type { Action, ActionCatalog } from "./action/index.ts"
import type { AnyOperator, OperatorCatalog } from "./conditions/operators/operator.ts"
import type { Logger } from "./lib/logger.ts"
import type { Subject, SubjectCatalog, SubjectFieldMapperCatalog } from "./subject/index.ts"

/**
 * Optional, shared config both `Policy` and `PolicyBuilder` accept as their
 * sole extra constructor argument - one object bundling the actions/
 * subjects a policy is written against, its custom operators, and the
 * SubjectFieldMappers its subjects need, built once and handed to both
 * rather than kept in sync by hand. Every field is independently optional.
 *
 * `actions`/`subjects`/`anyAction`/`anySubject` are deliberately typed
 * against the base `Action`/`Subject` (not a builder's `TActions`/
 * `TSubjects`) - tying them to those generics would infer `TActions`/
 * `TSubjects` from this config alone and narrow what `allow`/`deny` accept
 * everywhere else on the same builder.
 */
export interface KeycardConfig<TOperators extends AnyOperator = never> {
  /**
   * Declared action vocabulary, additive to `meta.actions` -
   * each key becomes the serialized name for its entry, which is how a
   * `createAction()` call with no name (see {@link Action.__dynamic}) gets
   * a real, stable name. A named entry may still be given its own key
   * (if using a catalog, defining the name is optional) - the
   * catalog key always wins over the entry's own name.
   */
  actions?: ActionCatalog
  /** Declared subject vocabulary, additive to `meta.subjects` - see `actions`, symmetric for Subjects. */
  subjects?: SubjectCatalog
  /**
   * Custom operators to register alongside the built-ins - either an
   * `AnyOperator[]` (built via `createOperator`) or an `OperatorCatalog`
   * (a bare `{ $name: resolver }` map, no `createOperator` call needed).
   */
  operators?: TOperators[] | OperatorCatalog
  /**
   * The action wildcard token - undeclared by default, in which case
   * `PolicyBuilder`'s built `meta.anyAction` comes out undeclared too (the
   * `"_ANY_"` default then applies). An explicit `null` disables the
   * action wildcard entirely, distinct from leaving this unset.
   */
  anyAction?: Action | string | null
  /** The subject wildcard token, symmetric with `anyAction`. */
  anySubject?: Subject | string | null
  /** SubjectFieldMappers registered by subject name - consulted when the Subject in hand doesn't carry its own `fieldMapper`. */
  mapper?: SubjectFieldMapperCatalog
  /** Logger for non-fatal diagnostics (currently: an unregistered dynamic Action/Subject encountered at `.can()`/`.cannot()`/`.require()` time) - falls back to the module-level `getLogger()` when unset. */
  logger?: Logger
  /**
   * Gates two things together, both worth paying for in dev and dead
   * weight in prod once CI has already run them once: the eager catalog/
   * operator validation `PolicyBuilder`/`Policy` do at construction (an
   * unregistered dynamic Action/Subject, a duplicate catalog key, a custom
   * operator `meta.operators` declares but nothing registered - all fail
   * loudly, immediately, when this is `true`) and the diagnostic
   * `meta.actions`/`meta.subjects`/`meta.operators` a built
   * `PolicyDefinition` carries alongside its `rules` (omitted when this is
   * `false` - `meta.anyAction`/`meta.anySubject`, being functionally
   * required for evaluation rather than diagnostic, are always emitted
   * when non-default). Defaults to `true`.
   */
  emitMeta?: boolean
}
