import * as fs from "fs"
import * as path from "path"

import { describe, expect, test } from "vitest"
import * as YAML from "yaml"

import { actionArgFor, listYamlFiles, subjectArgFor } from "./complianceFixtures.ts"
import type { Operator, PolicyDefinition } from "../../src/index.ts"
import { createOperator, Policy } from "../../src/index.ts"

/**
 * Metaprogrammed integration suite: every `*.yaml` fixture under
 * test/fixtures/policies (paired with its `*.test.yaml` companion) gets its
 * own generated describe/test block below. Dropping a new
 * `policy-XX.yaml` + `policy-XX.test.yaml` pair into that directory is
 * picked up automatically the next time this suite runs - no test code
 * needs to change.
 *
 * KeyCard itself never reads or writes policy.yaml text (that's an
 * application concern) - so YAML parsing here is done with the `yaml`
 * package (a devDependency of this test suite only) and handed to the
 * library as a plain PolicyDefinition via `Policy.from(...)`.
 */

const FIXTURES_DIR = path.join(__dirname, "../../../../test/fixtures/policies")

/**
 * Some fixture policies exercise a custom condition operator, which - per
 * SPEC_V1-0.md §7.4.12 - only the host application (here, this test
 * suite) can implement; declaring it in meta.operators documents it but
 * doesn't wire up behavior. Keyed by fixture file name.
 */
const CUSTOM_CHECKERS: Record<string, Operator[]> = {
  "policy-05-advanced.yaml": [
    createOperator("$startsWithUpper", (subject) => typeof subject === "string" && /^[A-Z]/.test(subject)),
  ],
}

interface TestCase {
  name: string
  action: string
  subject: string
  subjectData?: Record<string, unknown>
  expected: boolean
}

interface FixtureFile {
  policyName: string
  policyPath: string
  testPath: string
}

function discoverFixtures(): FixtureFile[] {
  return listYamlFiles(FIXTURES_DIR, (f) => !f.endsWith(".test.yaml")).map((policyPath) => {
    const policyFile = path.basename(policyPath)
    return {
      policyName: policyFile,
      policyPath,
      testPath: path.join(FIXTURES_DIR, policyFile.replace(/\.yaml$/, ".test.yaml")),
    }
  })
}

/** Parses a policy.yaml fixture's on-disk shape (the v1 rules/meta schema, per SPEC_V1-0.md §3) into a PolicyDefinition. */
function loadPolicyDef(rawYaml: string): PolicyDefinition {
  return YAML.parse(rawYaml) as PolicyDefinition
}

const fixtures = discoverFixtures()

// Sanity check on the discovery mechanism itself, so a misconfigured
// FIXTURES_DIR fails loudly instead of silently running zero tests.
test("discovers at least one policy fixture", () => {
  expect(fixtures.length).toBeGreaterThan(0)
})

describe.each(fixtures)("policy fixture: $policyName", ({ policyName, policyPath, testPath }) => {
  const rawYaml = fs.readFileSync(policyPath, "utf-8")
  const customConditions = CUSTOM_CHECKERS[policyName]

  test("successfully reads the policy.yaml file", () => {
    const policyDef = loadPolicyDef(rawYaml)

    expect(typeof policyDef.version).toBe("string")
    expect(Array.isArray(policyDef.rules)).toBe(true)
  })

  test("Policy.from(definition).def() deeply equals the parsed definition", () => {
    const policyDef = loadPolicyDef(rawYaml)
    const policy = Policy.from(policyDef, { operators: customConditions })

    expect(policy.def()).toEqual(policyDef)
  })

  if (!fs.existsSync(testPath)) {
    test.skip(`no companion ${path.basename(testPath)} found`, () => {
    })
    return
  }

  const { tests: cases } = YAML.parse(fs.readFileSync(testPath, "utf-8")) as { tests: TestCase[] }
  const policy = Policy.from(loadPolicyDef(rawYaml), { operators: customConditions })

  test.each(cases)("resolves test case: $name", (testCase) => {
    expect(policy.can(actionArgFor(testCase), subjectArgFor(testCase))).toBe(testCase.expected)
  })
})
