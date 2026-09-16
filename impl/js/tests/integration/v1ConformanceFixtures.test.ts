import * as fs from "fs"
import * as path from "path"

import { describe, expect, test } from "vitest"
import * as YAML from "yaml"

import { actionArgFor, isIncluded, listYamlFiles, subjectArgFor } from "./complianceFixtures.ts"
import type { AnyOperator } from "../../src/conditions/operators/operator.ts"
import type { PolicyDefinition } from "../../src/index.ts"
import { createOperator, Policy } from "../../src/index.ts"
import { KEYCARD_POLICY_VERSION } from "../../src/version.ts"

/**
 * Reads the v1 conformance suite under test/fixtures/v1 (see the README
 * there) - the shared, spec-derived fixtures every implementation MUST
 * read, per SPEC_V1-0-0.md §6. Each `*.yaml` file is a sequence of
 * `---`-separated documents; each document is one self-contained
 * `{ name, rules, cases }` test suite in the v1 `PolicyDefinition` shape.
 *
 * impl/js now natively implements the v1 `rules`/`meta` schema (see
 * ../../src/policy/policyDefinition.ts), so each parsed suite is a
 * PolicyDefinition already and is handed straight to `Policy.from(...)` -
 * no adapter needed.
 *
 * Discovery, subject-argument construction, and version filtering are
 * shared with every other compliance suite via ./complianceFixtures - see
 * that module for the KEYCARD_FIXTURES_MAX_VERSION knob that overrides
 * COMPLIANT_VERSION below for a single run.
 */

const FIXTURES_DIR = path.join(__dirname, "../../../../test/fixtures/v1")

/**
 * The highest v1 SemVer this suite (and the `Policy` implementation it
 * exercises) is written against - single-sourced from
 * KEYCARD_POLICY_VERSION alongside `Policy`'s internal
 * SUPPORTED_VERSION and `PolicyBuilder`'s BUILDER_VERSION, rather than a
 * separately hand-maintained literal, so "which version this runs
 * compliant with" can't quietly drift from what the implementation
 * actually supports. Should the two ever need to diverge (this suite's
 * adapter lagging a MINOR bump the rest of the implementation has already
 * picked up), replace this reference with an explicit, separately-tracked
 * literal.
 */
const COMPLIANT_VERSION = KEYCARD_POLICY_VERSION

interface V1Case {
  name?: string
  action: string
  subject: string
  subjectData?: Record<string, unknown>
  expected: "allow" | "deny"
}

interface V1Suite extends PolicyDefinition {
  name: string
  description?: string
  cases: V1Case[]
}

interface FixtureFile {
  fileName: string
  filePath: string
}

function discoverFixtureFiles(): FixtureFile[] {
  return listYamlFiles(FIXTURES_DIR).map((filePath) => ({ fileName: path.basename(filePath), filePath }))
}

/**
 * Some v1 conformance suites exercise a custom condition operator, which -
 * per SPEC_V1-0-0.md §7.4.12 - only the host application (here, this test
 * suite) can implement; declaring it in meta.operators documents it but
 * doesn't wire up behavior. Keyed by fixture file name.
 */
const CUSTOM_OPERATORS: Record<string, AnyOperator[]> = {
  "11-worked-example.yaml": [
    // Mirrors the spec Appendix's own suggested implementation: "one that
    // checks subject.roles.includes('admin')".
    createOperator("$hasRole", (subject, value) => {
      const roles = subject && typeof subject === "object" ? (subject as Record<string, unknown>).roles : undefined
      return Array.isArray(roles) && roles.includes(value)
    }),
  ],
}

function loadSuites(filePath: string): V1Suite[] {
  const raw = fs.readFileSync(filePath, "utf-8")
  return YAML.parseAllDocuments(raw).map((doc) => doc.toJSON() as V1Suite)
}

const fixtureFiles = discoverFixtureFiles()

test("discovers at least one v1 conformance fixture file", () => {
  expect(fixtureFiles.length).toBeGreaterThan(0)
})

describe.each(fixtureFiles)("v1 conformance fixture: $fileName", ({ fileName, filePath }) => {
  const suites = loadSuites(filePath)
  const operators = CUSTOM_OPERATORS[fileName] ?? []

  test("every document in the file is a well-formed suite", () => {
    for (const suite of suites) {
      expect(typeof suite.name).toBe("string")
      expect(Array.isArray(suite.rules)).toBe(true)
      expect(Array.isArray(suite.cases)).toBe(true)
    }
  })

  describe.each(suites)("$name", (suite) => {
    // Skips (rather than silently omitting) a suite whose declared version
    // exceeds this suite's own baked-in COMPLIANT_VERSION - see
    // complianceFixtures.ts's isIncluded.
    describe.skipIf(!isIncluded(suite.version, COMPLIANT_VERSION))(`version ${suite.version}`, () => {
      const policy = Policy.from(suite, { operators })
      const cases = suite.cases.map((c) => ({
        ...c,
        name: c.name ?? `${c.action} / ${c.subject} -> ${c.expected}`,
      }))

      test.each(cases)("$name", (testCase) => {
        expect(policy.can(actionArgFor(testCase), subjectArgFor(testCase))).toBe(testCase.expected === "allow")
      })
    })
  })
})
