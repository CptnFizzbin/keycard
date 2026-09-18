import * as fs from "fs"
import * as path from "path"

import semver from "semver"
import { describe, expect, test } from "vitest"
import * as YAML from "yaml"

import { actionArgFor, listYamlFiles, subjectArgFor } from "./fixtureUtils.ts"
import { KEYCARD_COMPLIANCE_FIXTURES } from "../../paths.ts"
import type { PolicyDefinition } from "../../src/index.ts"
import { createOperator, Policy } from "../../src/index.ts"
import type { JsonObject } from "../../src/lib/json.ts"
import { KEYCARD_POLICY_SUPPORTED_VERSIONS } from "../../src/version.ts"

interface V0Case {
  name?: string
  check:
    | [action: string, subject: string]
    | [action: string, subject: string, subjectClaims: JsonObject]
  expected: boolean
}

interface V0Suite extends PolicyDefinition {
  tests: V0Case[]
}

interface FixtureFile {
  fileName: string
  filePath: string
}

function discoverFixtureFiles(): FixtureFile[] {
  return listYamlFiles(KEYCARD_COMPLIANCE_FIXTURES).map((filePath) => ({
    fileName: path.basename(filePath),
    filePath,
  }))
}

function loadSuites(filePath: string): V0Suite[] {
  const raw = fs.readFileSync(filePath, "utf-8")
  const documents = YAML.parseAllDocuments(raw).map((doc) => doc.toJSON())
  if (Array.isArray(documents)) return documents
  return [documents]
}

const fixtureFiles = discoverFixtureFiles()

test("discovers at least one conformance fixture file", () => {
  expect(fixtureFiles.length).toBeGreaterThan(0)
})

describe.each(fixtureFiles)("conformance fixture: $fileName", ({ filePath }) => {
  const suites = loadSuites(filePath)

  test("every document in the file is a well-formed suite", () => {
    for (const suite of suites) {
      expect(typeof suite.name).toBe("string")
      expect(Array.isArray(suite.rules)).toBe(true)
      expect(Array.isArray(suite.tests)).toBe(true)
    }
  })

  describe.each(suites)("$name", (suite) => {
    const suiteVersion = semver.coerce(suite.version)!
    const included = semver.satisfies(suiteVersion, KEYCARD_POLICY_SUPPORTED_VERSIONS)
    if (!included) {
      test.skip(`Skipped: ${suiteVersion} is not supported (${KEYCARD_POLICY_SUPPORTED_VERSIONS})`)
    }

    test.each(suite.tests)("$name", (testCase) => {
      const policy = Policy.from(suite, {
        operators: [
          createOperator("$hasRole", (subject, value) => {
            const roles = subject && typeof subject === "object" ? (subject as Record<string, unknown>).roles : undefined
            return Array.isArray(roles) && roles.includes(value)
          }),
          createOperator("$startsWithUpper", (subject, expected) => {
            if (typeof subject !== "string") return false
            if (typeof expected !== "boolean") return false
            const isUpper = subject[0].toUpperCase() === subject[0]
            return isUpper === expected
          }),
        ],
      })

      const [action, subject, claims] = testCase.check

      const actionRef = actionArgFor(action)
      const subjectRef = subjectArgFor(subject, claims)

      expect(policy.can(actionRef, subjectRef)).toBe(testCase.expected)
    })
  })
})
