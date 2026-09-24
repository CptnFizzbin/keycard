/**
 * Per-field getters for a Subject's wrapped instance - the explicit
 * counterpart to the default bare-key/`$field` access, which reads `instance[fieldName]` directly. Lets a
 * condition reference a field whose name doesn't match the instance's own
 * shape (a rename, a computed/derived value) or an instance that isn't a
 * plain object. Consulted per-field: a field this mapper doesn't define
 * still falls back to ordinary property access.
 */
export type SubjectFieldMapper<TData = unknown> = Record<string, (instance: TData) => unknown>
