import type { Subject } from "./subject.ts"

/**
 * Infers the union of `Subject` types expected by `PolicyBuilder`/`Policy`
 * from a subjects map, so callers don't have to spell out
 * `typeof Subjects[keyof typeof Subjects]` by hand.
 *
 * @example
 * const Subjects = {
 *   Article: createSubject<{ id: number }>("Article"),
 * } as const;
 *
 * type AppSubjects = InferSubjects<typeof Subjects>;
 * new PolicyBuilder<AppActions, AppSubjects>()
 */
export type InferSubjects<T extends Record<string, Subject>> = T[keyof T]
