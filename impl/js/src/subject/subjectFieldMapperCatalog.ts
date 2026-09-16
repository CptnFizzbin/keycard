import type { SubjectFieldMapper } from "./subjectFieldMapper.ts"

/**
 * A {@link SubjectFieldMapper} registered per subject name - the
 * catalog-level counterpart to attaching one directly via `createSubject`.
 * Handed to `Policy`/`PolicyBuilder` via `KeycardConfig.mapper` so field
 * mappers can be registered centrally, for subjects created without one
 * inline.
 */
export class SubjectFieldMapperCatalog {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  private readonly mappers = new Map<string, SubjectFieldMapper<any>>()

  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  constructor(entries: Record<string, SubjectFieldMapper<any>> = {}) {
    for (const [subjectName, mapper] of Object.entries(entries)) {
      this.register(subjectName, mapper)
    }
  }

  register<TData>(subjectName: string, mapper: SubjectFieldMapper<TData>): this {
    this.mappers.set(subjectName, mapper)
    return this
  }

  get(subjectName: string): SubjectFieldMapper<unknown> | undefined {
    return this.mappers.get(subjectName)
  }
}
