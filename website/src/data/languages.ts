/** Host languages a policy can be defined in via the PolicyBuilder API. */
export const DEFINE_LANGUAGES = ["Java", "JavaScript", "TypeScript"] as const

/** Formats a PolicyDefinition can be serialized to. */
export const SERIALIZE_LANGUAGES = ["JSON", "YAML"] as const

/** Languages a policy can be enforced in at runtime. */
export const ENFORCE_LANGUAGES = ["Java", "JavaScript", "TypeScript"] as const
