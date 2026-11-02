/** A `$substr` pattern split into its anchors and a run of literal text and `*` wildcards. */
export interface SubstrPattern {
  readonly anchorStart: boolean
  readonly anchorEnd: boolean
  readonly segments: readonly (string | typeof WILDCARD)[]
}

export const WILDCARD: unique symbol = Symbol("keycard-knex:substr-wildcard")

/** Parses a `$substr` pattern, or returns `null` when it's malformed (an unescaped `^` not first, or `$` not last). */
export function parseSubstrPattern(pattern: string): SubstrPattern | null {
  const segments: (string | typeof WILDCARD)[] = []
  let literal = ""
  let anchorStart = false
  let anchorEnd = false

  for (let i = 0; i < pattern.length; i++) {
    const char = pattern[i]
    switch (char) {
      case "\\":
        // A trailing lone backslash is ignored, per the spec.
        if (i + 1 < pattern.length) literal += pattern[++i]
        break
      case "*":
        if (literal) segments.push(literal)
        literal = ""
        segments.push(WILDCARD)
        break
      case "^":
        if (i !== 0) return null
        anchorStart = true
        break
      case "$":
        if (i !== pattern.length - 1) return null
        anchorEnd = true
        break
      default:
        literal += char
    }
  }
  if (literal) segments.push(literal)

  return { anchorStart, anchorEnd, segments }
}

/** Renders `pattern` with `wildcard` for each `*` and for each unanchored end, without doubling a `*` already at that end. */
function render(pattern: SubstrPattern, wildcard: string, escape: (literal: string) => string): string {
  const { segments } = pattern
  const openStart = !pattern.anchorStart && segments[0] !== WILDCARD
  const openEnd = !pattern.anchorEnd && segments.at(-1) !== WILDCARD
  const body = segments.map((segment) => segment === WILDCARD ? wildcard : escape(segment)).join("")
  return (openStart ? wildcard : "") + body + (openEnd ? wildcard : "")
}

/** The escape character {@link toLikePattern} uses, for a `LIKE ... ESCAPE` clause. */
export const LIKE_ESCAPE = "!"

/**
 * Renders `pattern` as a SQL `LIKE` pattern escaped with {@link LIKE_ESCAPE}.
 * `[` is escaped too, since MSSQL's `LIKE` treats it as a character class.
 */
export function toLikePattern(pattern: SubstrPattern): string {
  return render(pattern, "%", (literal) => literal.replace(/[!%_[]/g, (c) => LIKE_ESCAPE + c))
}

/** Renders `pattern` as a SQLite `GLOB` pattern, which is case-sensitive and always matches the whole string. */
export function toGlobPattern(pattern: SubstrPattern): string {
  return render(pattern, "*", (literal) => literal.replace(/[*?[]/g, (c) => `[${c}]`))
}
