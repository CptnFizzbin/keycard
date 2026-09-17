import { PolicyArgumentError } from "../errors/index.ts"

/**
 * Resolves a `KeycardConfig.actions`/`.subjects` entry (array or keyed
 * catalog) into the reverse `id -> catalog key` map `PolicyBuilder`/
 * `Policy` use to resolve a dynamic Action/Subject's random name into its
 * real, serializable one, plus the full list of names to fold into
 * `meta.actions`/`meta.subjects`.
 */
export interface CatalogResolution {
  /** Raw name (a dynamic Action/Subject's random id) -> catalog key. Empty when `entries` was a plain array - there are no keys to resolve from. */
  reverseMap: Map<string, string>
  /** Every resolved name: an array entry's own `.name`, or a keyed entry's catalog key. */
  names: string[]
}

const EMPTY_RESOLUTION: CatalogResolution = { reverseMap: new Map(), names: [] }

/**
 * @param kind used only to name the vocabulary ("action"/"subject") in a
 *   duplicate-registration error message.
 */
export function buildCatalog<T extends { name: string }>(
  entries: T[] | Record<string, T> | undefined,
  kind: string,
): CatalogResolution {
  if (entries === undefined) return EMPTY_RESOLUTION

  if (Array.isArray(entries)) {
    return { reverseMap: new Map(), names: entries.map((entry) => entry.name) }
  }

  const reverseMap = new Map<string, string>()
  const names: string[] = []
  for (const [key, entry] of Object.entries(entries)) {
    const existingKey = reverseMap.get(entry.name)
    if (existingKey !== undefined && existingKey !== key) {
      throw new PolicyArgumentError(
        `KeycardConfig ${kind} catalog error: the same ${kind} is registered under both "${existingKey}" and "${key}" - a single Action/Subject can only be registered under one catalog key.`,
      )
    }
    reverseMap.set(entry.name, key)
    names.push(key)
  }

  return { reverseMap, names }
}

/** Resolves `rawName` (an Action/Subject's `.name`, dynamic or not) to its catalog key, or returns it unchanged when it isn't a registered catalog entry. */
export function resolveName(reverseMap: Map<string, string>, rawName: string): string {
  return reverseMap.get(rawName) ?? rawName
}
