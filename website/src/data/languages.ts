/** Languages a policy can be defined in (e.g. host-language DSLs or config formats). */
export const SOURCE_LANGUAGES = ["Java", "JavaScript", "TypeScript", "JSON", "YAML"] as const

/** Languages a policy can be enforced in at runtime. */
export const TARGET_LANGUAGES = ["Java", "JavaScript", "TypeScript"] as const
