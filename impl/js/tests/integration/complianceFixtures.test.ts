import * as path from "path"

import { describe, test, expect, afterEach } from "vitest"

import {
  parseSemVer,
  isCompatible,
  isIncluded,
  actionArgFor,
  subjectArgFor,
  listYamlFiles,
  MAX_VERSION_ENV_VAR,
} from "./complianceFixtures.ts"

describe("parseSemVer", () => {
  test("parses a full MAJOR.MINOR.PATCH string", () => {
    expect(parseSemVer("1.2.3")).toEqual({ major: 1, minor: 2, patch: 3 })
  })

  test("defaults MINOR and PATCH to 0 when omitted (§2.1's 1.0 shorthand)", () => {
    expect(parseSemVer("1")).toEqual({ major: 1, minor: 0, patch: 0 })
    expect(parseSemVer("1.5")).toEqual({ major: 1, minor: 5, patch: 0 })
  })
})

describe("isCompatible", () => {
  test("the same version is always compatible with itself", () => {
    expect(isCompatible("1.0.0", "1.0.0")).toBe(true)
  })

  test("a lower MINOR than what's supported is compatible", () => {
    expect(isCompatible("1.0.0", "1.5.0")).toBe(true)
  })

  test("a higher MINOR than what's supported is not compatible", () => {
    expect(isCompatible("1.5.0", "1.0.0")).toBe(false)
  })

  test("PATCH never affects compatibility", () => {
    expect(isCompatible("1.0.9", "1.0.0")).toBe(true)
    expect(isCompatible("1.0.0", "1.0.9")).toBe(true)
  })

  test("a different MAJOR is never compatible, in either direction", () => {
    expect(isCompatible("2.0.0", "1.9.0")).toBe(false)
    expect(isCompatible("1.0.0", "2.0.0")).toBe(false)
  })
})

describe("isIncluded", () => {
  afterEach(() => {
    delete process.env[MAX_VERSION_ENV_VAR]
  })

  test("a fixture at or below the baked-in compliant version is included", () => {
    expect(isIncluded("1.0.0", "1.0.0")).toBe(true)
    expect(isIncluded("1.0.0", "1.5.0")).toBe(true)
  })

  test("a fixture above the baked-in compliant version is excluded", () => {
    expect(isIncluded("1.5.0", "1.0.0")).toBe(false)
  })

  test("the env var overrides the baked-in compliant version", () => {
    process.env[MAX_VERSION_ENV_VAR] = "1.5.0"
    // Would be excluded against a baked-in "1.0.0", but the override widens it.
    expect(isIncluded("1.2.0", "1.0.0")).toBe(true)
  })

  test("the env var can narrow the baked-in compliant version too", () => {
    process.env[MAX_VERSION_ENV_VAR] = "1.0.0"
    // Would be included against a baked-in "1.5.0", but the override narrows it.
    expect(isIncluded("1.2.0", "1.5.0")).toBe(false)
  })
})

describe("actionArgFor", () => {
  test("wraps the raw action name into an Action", () => {
    expect(actionArgFor({ action: "Delete" })).toEqual({ name: "Delete", __brand: "action" })
  })
})

describe("subjectArgFor", () => {
  test("a bare Subject with no instance data", () => {
    const subject = subjectArgFor({ subject: "Article" })
    expect(subject.name).toBe("Article")
    expect(subject.instance).toBeUndefined()
  })

  test("an instance is wrapped for Policy.can to key off of", () => {
    const subject = subjectArgFor({ subject: "Article", subjectData: { owner_id: 1 } })
    expect(subject.name).toBe("Article")
    expect(subject.instance).toEqual({ owner_id: 1 })
  })
})

describe("listYamlFiles", () => {
  test("finds every *.yaml file under a real fixtures directory, sorted", () => {
    const dir = path.join(__dirname, "../../../../test/fixtures/v1")
    const files = listYamlFiles(dir)

    expect(files.length).toBeGreaterThan(0)
    expect(files).toEqual([...files].sort())
    for (const f of files) {
      expect(f.endsWith(".yaml")).toBe(true)
    }
  })

  test("applies the optional filter", () => {
    const dir = path.join(__dirname, "../../../../test/fixtures/policies")
    const withoutCompanions = listYamlFiles(dir, (f) => !f.endsWith(".test.yaml"))

    expect(withoutCompanions.length).toBeGreaterThan(0)
    for (const f of withoutCompanions) {
      expect(f.endsWith(".test.yaml")).toBe(false)
    }
  })
})
