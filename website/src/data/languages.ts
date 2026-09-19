/** Host languages a policy can be defined in via the PolicyBuilder API. */
export const PROGRAMMING_LANGUAGES = ["Java", "JavaScript", "TypeScript"] as const

/** Formats a PolicyDefinition can be serialized to. */
export const SERIALIZE_LANGUAGES = ["JSON", "YAML", "CBOR", "TOML", "ProtoBuf", "XML"] as const
