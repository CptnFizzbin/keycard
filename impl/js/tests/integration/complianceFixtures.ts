import * as fs from "fs"
import * as path from "path"

import type { Action, Subject } from "../../src/index.ts"
import { createAction, createSubject } from "../../src/index.ts"

/**
 * Shared helpers for every compliance-fixture-driven integration suite -
 * policyFixtures.test.ts (the pre-v1 fixtures under test/fixtures/policies)
 * and v1ConformanceFixtures.test.ts (the spec-native fixtures under
 * test/fixtures/v1) today, and any future fixture set.
 *
 * Factors out the parts that don't depend on a fixture format's on-disk
 * shape: discovering `*.yaml` files, the subject argument every format's
 * cases boil down to once parsed, and filtering fixtures by the SemVer
 * `version` they declare - so each format-specific test file only owns
 * parsing its own document shape, not the discovery/resolution/filtering
 * mechanics around it.
 */

/** The bit of a parsed case every fixture format shares, regardless of how its `expected` field is spelled. */
export interface ComplianceCase {
  subject: string
  subjectData?: Record<string, unknown>
}

/** `*.yaml` files directly under `dir` for which `filter` holds (operators: all of them), sorted by name. */
export function listYamlFiles(dir: string, filter: (fileName: string) => boolean = () => true): string[] {
  return fs
    .readdirSync(dir)
    .filter((f) => f.endsWith(".yaml") && filter(f))
    .sort()
    .map((f) => path.join(dir, f))
}

/** The action argument every fixture-driven suite passes to `Policy.can`. */
export function actionArgFor(testCase: { action: string }): Action {
  return createAction(testCase.action)
}

/**
 * The subject argument every fixture-driven suite passes to `Policy.can`:
 * a bare Subject (no instance) when there's no instance data (EC-7/EC-9),
 * or one wrapping `subjectData` as its instance when there is.
 */
export function subjectArgFor(testCase: ComplianceCase): Subject {
  const subject = createSubject(testCase.subject)
  return testCase.subjectData ? subject.wrap(testCase.subjectData) : subject
}

/** A parsed MAJOR.MINOR.PATCH SemVer string, per SPEC_V1-0-0.md §2. */
export interface SemVer {
  major: number
  minor: number
  patch: number
}

export function parseSemVer(raw: string): SemVer {
  const [major, minor, patch] = raw.split(".").map((part) => parseInt(part, 10))
  return { major, minor: minor ?? 0, patch: patch ?? 0 }
}

/**
 * True when a fixture declaring `fixtureVersion` is compatible with an
 * implementation targeting `maxSupportedVersion`, per SPEC_V1-0-0.md §2:
 * the same MAJOR, and a MINOR no higher than what's supported. PATCH never
 * affects compatibility.
 */
export function isCompatible(fixtureVersion: string, maxSupportedVersion: string): boolean {
  const fixture = parseSemVer(fixtureVersion)
  const max = parseSemVer(maxSupportedVersion)
  return fixture.major === max.major && fixture.minor <= max.minor
}

/**
 * Env var that overrides a suite's baked-in `compliantVersion` for one run
 * (e.g. `KEYCARD_FIXTURES_MAX_VERSION=1.0.0 yarn test run`) - useful for
 * deliberately narrowing or widening the cap without editing code. Unset
 * (the common case) means "use whatever version the compliance suite
 * itself bakes in".
 */
export const MAX_VERSION_ENV_VAR = "KEYCARD_FIXTURES_MAX_VERSION"

/**
 * True when a fixture declaring `fixtureVersion` should run against a
 * compliance suite that bakes in `compliantVersion` as the highest version
 * its adapter is written against - see e.g. v1ConformanceFixtures.test.ts's
 * `COMPLIANT_VERSION`. Every compliance suite bakes in its own version
 * rather than defaulting to "run everything", so a suite whose adapter
 * hasn't caught up to a newer MINOR version's fixtures skips them
 * automatically, with no external configuration required;
 * `MAX_VERSION_ENV_VAR` overrides that baked-in operators when set.
 */
export function isIncluded(fixtureVersion: string, compliantVersion: string): boolean {
  const override = process.env[MAX_VERSION_ENV_VAR]
  return isCompatible(fixtureVersion, override || compliantVersion)
}
