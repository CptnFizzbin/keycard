/**
 * A random id for a dynamic (no-name) Action/Subject - see
 * `action/action.ts`'s `__dynamic`. Reaches `crypto.randomUUID()` via
 * `globalThis` rather than a bare `crypto` reference or a `node:crypto`
 * import: this package runs in the browser as well as Node, and this
 * project's `tsconfig.json` targets `lib: ["ES2025"]` only (no "DOM"), so
 * neither environment's ambient types declare a global `crypto` binding
 * here even though both runtimes provide one.
 */
export function randomId(): string {
  return (globalThis as unknown as { crypto: { randomUUID(): string } }).crypto.randomUUID()
}
